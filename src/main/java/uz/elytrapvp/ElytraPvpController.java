package uz.elytrapvp;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import uz.autotnt.AutoTntConfig;
import uz.autotravel.TravelController;
import uz.pearlboost.PearlBoostClient;
import uz.pearlclutch.PearlClutchClient;
import uz.spearlunger.ElytraHelper;
import uz.spearlunger.InvHelper;
import uz.spearlunger.Rotations;

/**
 * Elytra PvP: eng yaqin o'yinchiga qarshi to'liq avtomatik jang.
 * <ul>
 *   <li>Uzoqda: elytra'ni o'zi kiyadi, sakraydi (firework bo'lmasa oyoq ostiga wind charge bilan otiladi), ochadi va
 *   nishondan {@code attackHeight} baland uchib boradi (nishon harakati hisoblanadi), ustiga yetgach sho'ng'iydi va
 *   yetib borishiga bir necha tick qolganda elytra'ni yechib (nagrudnik kiyib) mace bilan uradi. Qo'ngach yana
 *   uzoq bo'lsa - qaytadan kiyib uchadi.</li>
 *   <li>Yaqinda: oyoq ostiga wind charge (sakrab mace), o'rtacha masofada pearl + wind charge bilan tepaga teleport,
 *   yerda qilich/bolta; qalqon ushlasa stun slam (bolta + mace bir tickda).</li>
 *   <li>Jon kam qolsa: nishondan uzoqqa pearl otib qochadi va ovqat (avval oltin olma) yeydi.</li>
 *   <li>Yurish: siz harakat tugmalarini bossangiz bot ularga tegmaydi - sizniki ustun; bosmasangiz o'zi yuradi.</li>
 * </ul>
 */
public class ElytraPvpController {
    private enum Phase { GROUND, TAKEOFF, CRUISE, FALL_HIT, PEARL_UP, EAT }

    private Phase phase = Phase.GROUND;
    private PlayerEntity target = null;
    private Vec3d lastTargetPos = null, tvel = Vec3d.ZERO, pearlFrom = null;
    private int phaseTicks = 0, fwCd = 0, hitCd = 0, flyBan = 0, eatCount = -1;
    private boolean windTakeoff = false, hitThisFall = false;
    private String status = "", last = null;

    public void tick(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (p == null || c.world == null || c.interactionManager == null) {
            phase = Phase.GROUND;
            target = null;
            return;
        }
        ElytraPvpConfig cfg = ElytraPvpConfig.get();
        while (ElytraPvpClient.keyToggle.wasPressed()) {
            cfg.enabled = !cfg.enabled;
            ElytraPvpConfig.save();
            p.sendMessage(Text.literal(cfg.enabled ? "§aElytra PvP: ON" : "§cElytra PvP: OFF"), true);
            if (!cfg.enabled) reset(c);
        }
        if (!cfg.enabled) return;
        boolean user = userMoving(c);
        releaseOurs(c);
        if (InvHelper.screenOpen(c) || p.isSpectator() || !p.isAlive()) return;
        if (fwCd > 0) fwCd--;
        if (hitCd > 0) hitCd--;
        if (flyBan > 0) flyBan--;

        target = pick(c, p, cfg);
        if (target == null) {
            status = "nishon yo'q";
            lastTargetPos = null;
            if (phase != Phase.EAT && phase != Phase.CRUISE) phase = Phase.GROUND;
            if (phase == Phase.CRUISE && !p.isGliding()) phase = Phase.GROUND;
            if (phase == Phase.EAT) eat(c, p, cfg);
            return;
        }
        Vec3d tp = target.getEntityPos();
        if (lastTargetPos != null) tvel = tvel.multiply(0.5).add(tp.subtract(lastTargetPos).multiply(0.5));
        lastTargetPos = tp;

        // jon kam - qochib ovqat
        if (phase != Phase.EAT && phase != Phase.CRUISE && phase != Phase.FALL_HIT
                && p.getHealth() <= cfg.eatHealth && foodSlot(p) >= 0) {
            startEat(c, p, cfg);
        }
        phaseTicks++;
        switch (phase) {
            case GROUND -> ground(c, p, cfg, user);
            case TAKEOFF -> takeoff(c, p, cfg);
            case CRUISE -> cruise(c, p, cfg);
            case FALL_HIT -> fallHit(c, p, cfg, user);
            case PEARL_UP -> pearlUp(p);
            case EAT -> eat(c, p, cfg);
        }
    }

    private void set(Phase ph) {
        phase = ph;
        phaseTicks = 0;
    }

    private void reset(MinecraftClient c) {
        releaseOurs(c);
        c.options.useKey.setPressed(false);
        phase = Phase.GROUND;
        target = null;
    }

    // ------------------------------------------------------------ yerda
    private void ground(MinecraftClient c, ClientPlayerEntity p, ElytraPvpConfig cfg, boolean user) {
        PlayerEntity t = target;
        double d = horizontal(p.getEntityPos(), t.getEntityPos());
        float yaw = Rotations.yawTo(p.getEntityPos(), t.getEntityPos());
        boolean grounded = p.isOnGround();
        status = String.format("jang %.0fm", d);

        // uzoq - elytra bilan uchamiz
        if (cfg.elytra && grounded && flyBan == 0 && d > cfg.flyDist && canFly(p, cfg)) {
            windTakeoff = false;
            set(Phase.TAKEOFF);
            return;
        }
        // yerda elytra kiyib qolgan - nagrudnik
        if (grounded && cfg.armorOnGround && ElytraHelper.wearingElytra(p) && ElytraHelper.chestplateSlot(p) >= 0) {
            ElytraHelper.unequipElytra(c, p);
            return;
        }
        // o'rtacha masofa: pearl + wind charge bilan tepaga teleport
        if (cfg.pearlUp && grounded && d >= 5 && d <= cfg.pearlUpDist && ready(p, Items.ENDER_PEARL)
                && ready(p, Items.WIND_CHARGE) && maceSlot(p, t) >= 0 && !PearlBoostClient.CONTROLLER.busy()) {
            if (PearlBoostClient.CONTROLLER.launch(c, p, yaw, -65.0f)) {
                pearlFrom = p.getEntityPos();
                say(p, "§dElytra PvP: pearl bilan tepaga");
                set(Phase.PEARL_UP);
                return;
            }
        }
        // yaqin: oyoq ostiga wind charge - sakrab mace
        int wind = hotbar(p, Items.WIND_CHARGE);
        if (cfg.windJump && grounded && d <= cfg.windJumpDist && wind >= 0 && maceSlot(p, t) >= 0) {
            windJump(c, p, wind, yaw);
            hitThisFall = false;
            set(Phase.FALL_HIT);
            return;
        }
        // yurish (siz yurmasangiz)
        Vec3d eye = p.getEyePos(), center = t.getBoundingBox().getCenter();
        if (!user) {
            Rotations.turn(p, Rotations.yawTo(eye, center), Rotations.pitchTo(eye, center), 4.0f);
            if (cfg.walk && p.distanceTo(t) > 2.6) {
                press(c.options.forwardKey);
                if (d > 4 && p.getHungerManager().getFoodLevel() > 6) press(c.options.sprintKey);
                if (grounded && p.horizontalCollision) p.jump();
            }
        }
        // yerda urish (cooldown to'lganda)
        if (hitCd == 0 && inReach(p, t, cfg) && p.getAttackCooldownProgress(0.5f) >= 0.92f) {
            strike(c, p, t, cfg, user);
            hitCd = 2;
        }
    }

    private static boolean canFly(ClientPlayerEntity p, ElytraPvpConfig cfg) {
        if (ElytraHelper.elytraSlot(p, cfg.minDurability) == -2) return false;
        return ElytraHelper.fireworks(p) > 0 || (cfg.windTakeoff && hotbar(p, Items.WIND_CHARGE) >= 0);
    }

    // ------------------------------------------------------------ uchish
    private void takeoff(MinecraftClient c, ClientPlayerEntity p, ElytraPvpConfig cfg) {
        status = "ko'tarilish";
        if (phaseTicks > 40 || p.isTouchingWater()) {
            flyBan = 100;
            set(Phase.GROUND);
            return;
        }
        if (!ElytraHelper.wearingElytra(p)) {
            int s = ElytraHelper.elytraSlot(p, cfg.minDurability);
            if (s < 0) {
                set(Phase.GROUND);
                return;
            }
            ElytraHelper.equip(c, p, s);
            return;
        }
        Vec3d tp = target.getEntityPos();
        float yaw = Rotations.yawTo(p.getEntityPos(), tp);
        if (p.isGliding()) {
            if (ElytraHelper.fireworks(p) > 0 && ElytraHelper.firework(c, p)) fwCd = cfg.fireworkDelay;
            set(Phase.CRUISE);
            return;
        }
        if (p.isOnGround()) {
            int wind = hotbar(p, Items.WIND_CHARGE);
            if (ElytraHelper.fireworks(p) == 0 && wind >= 0) {
                windJump(c, p, wind, yaw);   // firework yo'q - wind charge bilan tepaga
                windTakeoff = true;
            } else {
                Rotations.turn(p, yaw, -20.0f, 3.0f);
                p.jump();
            }
            return;
        }
        // havoda: wind bilan otilgan bo'lsak cho'qqiga yaqin, aks holda 2 tickdan keyin ochamiz
        if (windTakeoff ? p.getVelocity().y < 0.15 : phaseTicks >= 3) ElytraHelper.deploy(p);
    }

    private void cruise(MinecraftClient c, ClientPlayerEntity p, ElytraPvpConfig cfg) {
        PlayerEntity t = target;
        if (!p.isGliding()) {
            if (p.isOnGround() || p.isTouchingWater()) set(Phase.GROUND);
            else if (p.getVelocity().y < 0) ElytraHelper.deploy(p);
            return;
        }
        if (phaseTicks > 900) {   // juda uzoq - yerga tushib qaytadan
            ElytraHelper.unequipElytra(c, p);
            set(Phase.FALL_HIT);
            return;
        }
        Vec3d pos = p.getEntityPos(), v = p.getVelocity();
        double speed = Math.max(0.4, v.length()), hs = Math.sqrt(v.x * v.x + v.z * v.z);
        Vec3d tc = t.getBoundingBox().getCenter();
        double reachTicks = Math.min(30, tc.distanceTo(pos) / speed);
        Vec3d lead = tc.add(tvel.x * reachTicks, 0, tvel.z * reachTicks);
        double hd = horizontal(pos, lead);
        double alt = pos.y - t.getY();
        boolean fw = ElytraHelper.fireworks(p) > 0;
        double diveDist = Math.max(8, alt * 1.0);
        float yaw, pitch;
        boolean boost = false;
        if (alt < 4 && hd < 12) {
            // juda past - ko'tarilamiz yoki (firework yo'q) yechib urishga
            if (!fw) {
                ElytraHelper.unequipElytra(c, p);
                hitThisFall = false;
                set(Phase.FALL_HIT);
                return;
            }
            yaw = Rotations.yawTo(pos, lead);
            pitch = -50.0f;
            boost = true;
            status = "ko'tarilish";
        } else if (hd > diveDist) {
            Vec3d dir = new Vec3d(lead.x - pos.x, 0, lead.z - pos.z).normalize();
            double desired = Math.max(t.getY() + cfg.attackHeight,
                    TravelController.terrainAhead(c.world, pos, dir, Math.min(64, hd)) + 6);
            double dy = desired - pos.y;
            yaw = Rotations.yawTo(pos, lead);
            pitch = (float) MathHelper.clamp(-dy * 1.5, -40, 20);
            boost = hs < 1.1 || (dy > 6 && v.y < 0.3);
            if (TravelController.wallAhead(c.world, p, dir, Math.max(10, hs * 20))) {
                pitch = -55.0f;
                boost = true;
            }
            status = String.format("uchish %.0fm, +%.0f", hd, alt);
        } else {
            // sho'ng'ish: to'g'ri nishonga
            Vec3d eye = p.getEyePos();
            yaw = Rotations.yawTo(eye, lead);
            pitch = Math.max(15.0f, Rotations.pitchTo(eye, lead));
            Vec3d to = lead.subtract(pos.add(0, 0.9, 0));
            double eta = to.length() / speed;
            int leadT = cfg.unequipLead + (int) Math.round(Math.max(0, speed - 1.0) * 2);
            status = String.format("sho'ng'ish %.0f tick", eta);
            if (eta <= leadT && v.normalize().dotProduct(to.normalize()) >= 0.5) {
                ElytraHelper.unequipElytra(c, p);
                hitThisFall = false;
                set(Phase.FALL_HIT);
                if (inReach(p, t, cfg)) strike(c, p, t, cfg, false);
                return;
            }
        }
        if (boost && fw && fwCd == 0 && ElytraHelper.firework(c, p)) fwCd = cfg.fireworkDelay;
        Rotations.turn(p, yaw, pitch, 2.0f);
    }

    // ------------------------------------------------------------ tushib urish
    private void fallHit(MinecraftClient c, ClientPlayerEntity p, ElytraPvpConfig cfg, boolean user) {
        PlayerEntity t = target;
        status = String.format("tushish %.1f", p.fallDistance);
        if (p.isGliding()) {
            set(Phase.CRUISE);
            return;
        }
        Vec3d eye = p.getEyePos(), center = t.getBoundingBox().getCenter();
        if (!user) {
            Rotations.turn(p, Rotations.yawTo(eye, center), Rotations.pitchTo(eye, center), 30.0f);
            if (!p.isOnGround() && horizontal(p.getEntityPos(), t.getEntityPos()) > 0.8) press(c.options.forwardKey);
        }
        boolean falling = p.getVelocity().y < 0 && (p.fallDistance >= 1.5 || phaseTicks > 3);
        if (!hitThisFall && hitCd == 0 && falling && inReach(p, t, cfg)) {
            strike(c, p, t, cfg, user);
            hitThisFall = true;
            hitCd = 4;
        }
        if ((p.isOnGround() || p.isTouchingWater()) && phaseTicks > 2 || phaseTicks > 120) set(Phase.GROUND);
    }

    private void pearlUp(ClientPlayerEntity p) {
        status = "pearl";
        boolean teleported = pearlFrom != null && p.getEntityPos().distanceTo(pearlFrom) > 4 && !p.isOnGround();
        if (teleported) {
            hitThisFall = false;
            set(Phase.FALL_HIT);
        } else if (!PearlBoostClient.CONTROLLER.busy() && phaseTicks > 3 || phaseTicks > 100) {
            flyBan = Math.max(flyBan, 20);
            set(Phase.GROUND);
        }
    }

    /** Zarba: qalqon bo'lsa stun slam (bolta + mace bir tickda), tushayotgan bo'lsak mace, aks holda qilich/bolta. */
    private void strike(MinecraftClient c, ClientPlayerEntity p, PlayerEntity t, ElytraPvpConfig cfg, boolean user) {
        float oy = p.getYaw(), op = p.getPitch();
        Vec3d eye = p.getEyePos(), center = t.getBoundingBox().getCenter();
        Rotations.sendLook(p, Rotations.yawTo(eye, center), Rotations.pitchTo(eye, center));
        boolean slam = false;
        if (cfg.stunSlam && t.isBlocking()) {
            int axe = weapon(p, "_axe");
            if (axe >= 0) {
                p.getInventory().setSelectedSlot(axe);
                c.interactionManager.attackEntity(p, t);
                p.swingHand(Hand.MAIN_HAND);
                slam = true;
            }
        }
        int w = p.fallDistance >= 1.5 ? maceSlot(p, t) : -1;
        if (w < 0) w = melee(p);
        if (w >= 0) p.getInventory().setSelectedSlot(w);
        c.interactionManager.attackEntity(p, t);
        p.swingHand(Hand.MAIN_HAND);
        last = String.format("%s%s %.1f", slam ? "stun slam, " : "", p.getMainHandStack().isOf(Items.MACE) ? "mace" : "zarba",
                p.fallDistance);
        if (user) {   // siz boshqarayotgan bo'lsangiz qarashingizni qaytaramiz
            p.setYaw(oy);
            p.setPitch(op);
        }
    }

    // ------------------------------------------------------------ ovqat
    private void startEat(MinecraftClient c, ClientPlayerEntity p, ElytraPvpConfig cfg) {
        PlayerEntity t = target;
        if (cfg.retreatPearl && t != null && p.distanceTo(t) < 10) {
            Vec3d away = new Vec3d(p.getX() - t.getX(), 0, p.getZ() - t.getZ());
            if (away.lengthSquared() < 1e-3) away = new Vec3d(1, 0, 0);
            Vec3d spot = p.getEntityPos().add(away.normalize().multiply(cfg.retreatDist));
            int gy = TravelController.groundY(c.world, spot.x, spot.z);
            if (PearlClutchClient.CONTROLLER.throwAt(c, p, new Vec3d(spot.x, gy, spot.z))) {
                say(p, "§eElytra PvP: qochib ovqat yeyapmiz");
            }
        }
        eatCount = -1;
        set(Phase.EAT);
    }

    private void eat(MinecraftClient c, ClientPlayerEntity p, ElytraPvpConfig cfg) {
        status = "ovqat";
        int slot = foodSlot(p);
        ItemStack s = slot >= 0 ? p.getInventory().getStack(slot) : ItemStack.EMPTY;
        boolean eaten = eatCount >= 0 && (slot < 0 || s.getCount() < eatCount);
        if (slot < 0 || eaten || phaseTicks > 100) {
            c.options.useKey.setPressed(false);
            set(Phase.GROUND);
            return;
        }
        if (slot >= 9) {   // inventardan hotbar'ga (E orqali)
            InvHelper.swap(c, p, slot, p.getInventory().getSelectedSlot());
            return;
        }
        if (eatCount < 0) eatCount = s.getCount();
        p.getInventory().setSelectedSlot(slot);
        c.options.useKey.setPressed(true);
    }

    /** Yeyish mumkin bo'lgan eng yaxshi ovqat (avval oltin olma). Hotbar yoki inventar. */
    private static int foodSlot(ClientPlayerEntity p) {
        int best = -1, bestScore = -1;
        boolean hungry = p.getHungerManager().isNotFull();
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            FoodComponent f = s.get(DataComponentTypes.FOOD);
            if (f == null || (!hungry && !f.canAlwaysEat())) continue;
            String id = Registries.ITEM.getId(s.getItem()).getPath();
            if (id.contains("rotten") || id.contains("spider_eye") || id.contains("pufferfish") || id.contains("chorus")
                    || id.contains("suspicious")) continue;
            int score = (s.isOf(Items.ENCHANTED_GOLDEN_APPLE) ? 1000 : s.isOf(Items.GOLDEN_APPLE) ? 500 : 0)
                    + f.nutrition() * 10 + (i < 9 ? 5 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    // ------------------------------------------------------------ yordamchi
    /** Oyoq ostiga wind charge (qarash faqat paketda), shu tickda eski slotga. */
    private static void windJump(MinecraftClient c, ClientPlayerEntity p, int wind, float yaw) {
        int original = p.getInventory().getSelectedSlot();
        float oy = p.getYaw(), op = p.getPitch();
        p.getInventory().setSelectedSlot(wind);
        p.setYaw(yaw);
        p.setPitch(90.0f);
        c.interactionManager.interactItem(p, Hand.MAIN_HAND);
        p.swingHand(Hand.MAIN_HAND);
        p.setYaw(oy);
        p.setPitch(op);
        p.getInventory().setSelectedSlot(original);
    }

    private static int hotbar(ClientPlayerEntity p, net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(item) && !p.getItemCooldownManager().isCoolingDown(s)) return i;
        }
        return -1;
    }

    private static boolean ready(ClientPlayerEntity p, net.minecraft.item.Item item) {
        return hotbar(p, item) >= 0;
    }

    /** Zirhli nishonga Breach, aks holda Density. */
    private static int maceSlot(ClientPlayerEntity p, LivingEntity target) {
        boolean armored = target.getArmor() >= 12;
        int best = -1, bestScore = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isOf(Items.MACE)) continue;
            int score = 1 + (armored ? level(s, "breach") * 3 + level(s, "density")
                    : level(s, "density") * 3 + level(s, "breach"));
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    /** Yerda: qilich, bo'lmasa bolta, bo'lmasa mace. */
    private static int melee(ClientPlayerEntity p) {
        int s = weapon(p, "_sword");
        if (s < 0) s = weapon(p, "_axe");
        if (s < 0) s = weapon(p, "mace");
        return s;
    }

    private static int weapon(ClientPlayerEntity p, String suffix) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isEmpty() && Registries.ITEM.getId(s.getItem()).getPath().endsWith(suffix)) return i;
        }
        return -1;
    }

    private static int level(ItemStack s, String name) {
        for (RegistryEntry<Enchantment> e : s.getEnchantments().getEnchantments()) {
            if (e.getKey().map(k -> k.getValue().getPath().equals(name)).orElse(false)) {
                return s.getEnchantments().getLevel(e);
            }
        }
        return 0;
    }

    private static boolean inReach(ClientPlayerEntity p, LivingEntity t, ElytraPvpConfig cfg) {
        Vec3d eye = p.getEyePos();
        var b = t.getBoundingBox();
        Vec3d cl = new Vec3d(MathHelper.clamp(eye.x, b.minX, b.maxX), MathHelper.clamp(eye.y, b.minY, b.maxY),
                MathHelper.clamp(eye.z, b.minZ, b.maxZ));
        return eye.distanceTo(cl) <= cfg.reachTenths / 10.0;
    }

    private PlayerEntity pick(MinecraftClient c, ClientPlayerEntity p, ElytraPvpConfig cfg) {
        if (target != null && valid(p, target, cfg) && target.distanceTo(p) <= cfg.range * 1.3) return target;
        PlayerEntity best = null;
        double bestD = cfg.range;
        for (PlayerEntity e : c.world.getPlayers()) {
            if (!valid(p, e, cfg)) continue;
            double d = e.distanceTo(p);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    private static boolean valid(ClientPlayerEntity p, PlayerEntity e, ElytraPvpConfig cfg) {
        if (e == p || !e.isAlive() || e.isRemoved() || e.isSpectator() || e.isCreative()) return false;
        if (!cfg.ignoreFriends) return true;
        String name = e.getName().getString();
        for (String f : AutoTntConfig.get().friends) if (f.equalsIgnoreCase(name)) return false;
        return true;
    }

    private static double horizontal(Vec3d a, Vec3d b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ------------------------------------------------------------ tugmalar (sizniki ustun)
    /** Haqiqatan (klaviatura/sichqonchada) bosib turilganmi. */
    private static boolean held(MinecraftClient c, KeyBinding kb) {
        InputUtil.Key k = KeyBindingHelper.getBoundKeyOf(kb);
        if (k.getCode() < 0) return false;
        if (k.getCategory() == InputUtil.Type.KEYSYM) return InputUtil.isKeyPressed(c.getWindow(), k.getCode());
        if (k.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(c.getWindow().getHandle(), k.getCode()) == GLFW.GLFW_PRESS;
        }
        return false;
    }

    private static boolean userMoving(MinecraftClient c) {
        GameOptions o = c.options;
        return held(c, o.forwardKey) || held(c, o.backKey) || held(c, o.leftKey) || held(c, o.rightKey)
                || held(c, o.jumpKey);
    }

    /** Biz tugma bosganmizmi (boshqa modullarning tugmalariga tegmaslik uchun). */
    private static boolean ours = false;

    private static void press(KeyBinding kb) {
        kb.setPressed(true);
        ours = true;
    }

    /** O'tgan tickda biz bosgan tugmalarni qo'yib yuboramiz - faqat siz bosib turmaganlarini. */
    private static void releaseOurs(MinecraftClient c) {
        if (!ours) return;
        ours = false;
        GameOptions o = c.options;
        for (KeyBinding kb : new KeyBinding[] {o.forwardKey, o.sprintKey}) {
            if (kb.isPressed() && !held(c, kb)) kb.setPressed(false);
        }
    }

    private static void say(ClientPlayerEntity p, String msg) {
        if (ElytraPvpConfig.get().messages) p.sendMessage(Text.literal(msg), true);
    }

    public String hudLine() {
        ElytraPvpConfig cfg = ElytraPvpConfig.get();
        if (!cfg.enabled || !cfg.hud) return null;
        String t = target == null ? "§7-" : "§f" + target.getName().getString();
        return "§cElytra PvP: " + t + " §7" + status + (last != null ? " §8| " + last : "");
    }
}
