package uz.playerchase;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
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
import uz.autotnt.AutoTntConfig;
import uz.autotravel.TravelController;
import uz.spearlunger.ElytraHelper;
import uz.spearlunger.InvHelper;
import uz.spearlunger.PearlHelper;
import uz.spearlunger.Rotations;

import java.util.List;

/**
 * Player Chase: tugmani bossangiz eng yaqin (yoki pritselga yaqin - sozlamadan tanlanadi) o'yinchiga
 * elytra + firework bilan tez uchib boradi (Elytra PvP'dagi uchish mantiqi bilan bir xil), yetib borgach
 * elytrani yechib mace bilan uradi va o'zi o'chadi. Elytra/firework bo'lmasa, nishon tomonga pearl otib
 * yaqinlashadi (keyin yana yurib boradi va mace bilan uradi).
 */
public class PlayerChaseController {
    private enum Phase { IDLE, TAKEOFF, CRUISE, PEARL_WAIT, APPROACH }

    private Phase phase = Phase.IDLE;
    private PlayerEntity target;
    private int phaseTicks = 0, fwCd = 0, ticksRunning = 0;
    private boolean windTakeoff = false;
    private Vec3d pearlFrom = null;
    private String status = null, lastWarn = null;

    // ------------------------------------------------------------ tick
    public void tick(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (p == null || c.world == null || c.interactionManager == null) {
            phase = Phase.IDLE;
            target = null;
            return;
        }
        PlayerChaseConfig cfg = PlayerChaseConfig.get();
        while (PlayerChaseClient.keyToggle.wasPressed()) {
            cfg.enabled = !cfg.enabled;
            PlayerChaseConfig.save();
            p.sendMessage(Text.literal(cfg.enabled ? "§aPlayer Chase: ON" : "§cPlayer Chase: OFF"), true);
            if (!cfg.enabled) stop(c, p, null);
        }
        boolean pressed = false;
        while (PlayerChaseClient.keyChase.wasPressed()) pressed = true;

        if (InvHelper.screenOpen(c)) {
            if (phase != Phase.IDLE) stop(c, p, null);
            return;
        }
        if (pressed) {
            if (phase != Phase.IDLE) {
                stop(c, p, "§eChase: bekor qilindi.");
                return;
            }
            if (!cfg.enabled) return;
            start(c, p, cfg);
            return;
        }
        if (phase == Phase.IDLE) return;
        if (target == null || !target.isAlive() || target.isRemoved()) {
            stop(c, p, "§eChase: nishon yo'qoldi.");
            return;
        }
        if (++ticksRunning > cfg.timeoutTicks) {
            stop(c, p, "§eChase: vaqt tugadi.");
            return;
        }
        phaseTicks++;
        if (fwCd > 0) fwCd--;
        switch (phase) {
            case TAKEOFF -> takeoff(c, p, cfg);
            case CRUISE -> cruise(c, p, cfg);
            case PEARL_WAIT -> pearlWait(c, p, cfg);
            case APPROACH -> approach(c, p, cfg);
            default -> { }
        }
    }

    private void set(Phase ph) {
        phase = ph;
        phaseTicks = 0;
    }

    private void stop(MinecraftClient c, ClientPlayerEntity p, String msg) {
        if (phase != Phase.IDLE && p != null) {
            c.options.useKey.setPressed(false);
            c.options.forwardKey.setPressed(false);
            c.options.sprintKey.setPressed(false);
            if (msg != null) say(p, msg);
        }
        phase = Phase.IDLE;
        target = null;
        ticksRunning = 0;
        status = null;
    }

    // ------------------------------------------------------------ boshlash
    private void start(MinecraftClient c, ClientPlayerEntity p, PlayerChaseConfig cfg) {
        PlayerEntity t = pick(c, p, cfg);
        if (t == null) {
            say(p, "§cChase: " + cfg.range + " blok ichida mos nishon topilmadi.");
            return;
        }
        target = t;
        ticksRunning = 0;
        lastWarn = null;
        if (canFly(p, cfg)) {
            windTakeoff = false;
            set(Phase.TAKEOFF);
            say(p, "§aChase: " + t.getName().getString() + " §7(elytra)");
            return;
        }
        if (cfg.pearlFallback && pearlSlot(p) >= 0) {
            Vec3d dest = t.getEntityPos();
            if (PearlHelper.throwAt(c, p, dest)) {
                pearlFrom = p.getEntityPos();
                set(Phase.PEARL_WAIT);
                say(p, "§aChase: " + t.getName().getString() + " §7(pearl)");
                return;
            }
        }
        say(p, "§cChase: elytra/firework yo'q" + (cfg.pearlFallback ? " va pearl ham otilmadi." : "."));
        target = null;
    }

    private static boolean canFly(ClientPlayerEntity p, PlayerChaseConfig cfg) {
        if (ElytraHelper.elytraSlot(p, cfg.minDurability) == -2) return false;
        return ElytraHelper.fireworks(p) > 0 || (cfg.windTakeoff && hotbar(p, Items.WIND_CHARGE) >= 0);
    }

    // ------------------------------------------------------------ uchish (Elytra PvP bilan bir xil mantiq)
    private void takeoff(MinecraftClient c, ClientPlayerEntity p, PlayerChaseConfig cfg) {
        status = "ko'tarilish";
        if (phaseTicks > 40 || p.isTouchingWater()) {
            stop(c, p, "§eChase: ko'tarilib bo'lmadi.");
            return;
        }
        if (!ElytraHelper.wearingElytra(p)) {
            int s = ElytraHelper.elytraSlot(p, cfg.minDurability);
            if (s < 0) {
                stop(c, p, "§cChase: elytra yo'q.");
                return;
            }
            ElytraHelper.equip(c, p, s);
            return;
        }
        float yaw = Rotations.yawTo(p.getEntityPos(), target.getEntityPos());
        if (p.isGliding()) {
            if (ElytraHelper.fireworks(p) > 0 && ElytraHelper.firework(c, p)) fwCd = cfg.fireworkDelay;
            set(Phase.CRUISE);
            return;
        }
        if (p.isOnGround()) {
            int wind = hotbar(p, Items.WIND_CHARGE);
            if (ElytraHelper.fireworks(p) == 0 && wind >= 0) {
                windJump(c, p, wind, yaw);
                windTakeoff = true;
            } else {
                Rotations.turn(p, yaw, -20.0f, 3.0f);
                p.jump();
            }
            return;
        }
        if (windTakeoff ? p.getVelocity().y < 0.15 : phaseTicks >= 3) ElytraHelper.deploy(p);
    }

    private void cruise(MinecraftClient c, ClientPlayerEntity p, PlayerChaseConfig cfg) {
        PlayerEntity t = target;
        if (!p.isGliding()) {
            if (p.isOnGround() || p.isTouchingWater()) {
                set(Phase.APPROACH);
            } else if (p.getVelocity().y < 0) {
                ElytraHelper.deploy(p);
            }
            return;
        }
        Vec3d pos = p.getEntityPos(), v = p.getVelocity();
        double speed = Math.max(0.4, v.length()), hs = Math.sqrt(v.x * v.x + v.z * v.z);
        Vec3d tc = t.getEntityPos();
        double hd = horizontal(pos, tc);
        double alt = pos.y - t.getY();
        boolean fw = ElytraHelper.fireworks(p) > 0;
        double diveDist = Math.max(8, alt);
        float yaw, pitch;
        boolean boost = false;
        if (alt < 4 && hd < 12) {
            if (!fw) {
                ElytraHelper.unequipElytra(c, p);
                set(Phase.APPROACH);
                return;
            }
            yaw = Rotations.yawTo(pos, tc);
            pitch = -50.0f;
            boost = true;
            status = "ko'tarilish";
        } else if (hd > diveDist) {
            Vec3d dir = new Vec3d(tc.x - pos.x, 0, tc.z - pos.z).normalize();
            double desired = Math.max(t.getY() + cfg.attackHeight,
                    TravelController.terrainAhead(c.world, pos, dir, Math.min(64, hd)) + 6);
            double dy = desired - pos.y;
            yaw = Rotations.yawTo(pos, tc);
            pitch = (float) MathHelper.clamp(-dy * 1.5, -40, 20);
            boost = hs < 1.1 || (dy > 6 && v.y < 0.3);
            if (TravelController.wallAhead(c.world, p, dir, Math.max(10, hs * 20))) {
                pitch = -55.0f;
                boost = true;
            }
            status = String.format("uchish %.0fm, +%.0f", hd, alt);
        } else {
            Vec3d eye = p.getEyePos();
            yaw = Rotations.yawTo(eye, tc);
            pitch = Math.max(15.0f, Rotations.pitchTo(eye, tc));
            double eta = tc.subtract(pos.add(0, 0.9, 0)).length() / speed;
            status = String.format("sho'ng'ish %.0f tick", eta);
            if (eta <= cfg.unequipLead) {
                ElytraHelper.unequipElytra(c, p);
                set(Phase.APPROACH);
                return;
            }
        }
        if (boost && fw && fwCd == 0 && ElytraHelper.firework(c, p)) fwCd = cfg.fireworkDelay;
        Rotations.turn(p, yaw, pitch, 2.0f);
    }

    // ------------------------------------------------------------ pearl fallback
    private void pearlWait(MinecraftClient c, ClientPlayerEntity p, PlayerChaseConfig cfg) {
        status = "pearl";
        boolean teleported = pearlFrom != null && p.getEntityPos().distanceTo(pearlFrom) > 4;
        if (teleported || phaseTicks > 100) {
            set(Phase.APPROACH);
        }
    }

    // ------------------------------------------------------------ oxirgi yaqinlashish + zarba
    private void approach(MinecraftClient c, ClientPlayerEntity p, PlayerChaseConfig cfg) {
        PlayerEntity t = target;
        if (p.isGliding()) {
            set(Phase.CRUISE);
            return;
        }
        Vec3d eye = p.getEyePos(), center = t.getBoundingBox().getCenter();
        float yaw = Rotations.yawTo(eye, center);
        Rotations.turn(p, yaw, Rotations.pitchTo(eye, center), 20.0f);
        status = String.format("yaqinlashish %.1fm", p.distanceTo(t));
        if (inReach(p, t)) {
            strike(c, p, t);
            if (cfg.autoOffAfterHit) {
                cfg.enabled = false;
                PlayerChaseConfig.save();
            }
            stop(c, p, "§aChase: " + t.getName().getString() + " ga yetib urdi.");
            return;
        }
        if (!p.isOnGround() && p.getVelocity().y < -0.3) return;   // tushayotganda yurishga urinmaymiz
        c.options.forwardKey.setPressed(true);
        if (p.distanceTo(t) > 4 && p.getHungerManager().getFoodLevel() > 6) c.options.sprintKey.setPressed(true);
        if (p.isOnGround() && p.horizontalCollision) p.jump();
    }

    /** Mace bo'lsa mace, bo'lmasa qilich/bolta bilan bir zarba. */
    private void strike(MinecraftClient c, ClientPlayerEntity p, PlayerEntity t) {
        float oy = p.getYaw(), op = p.getPitch();
        Vec3d eye = p.getEyePos(), center = t.getBoundingBox().getCenter();
        Rotations.sendLook(p, Rotations.yawTo(eye, center), Rotations.pitchTo(eye, center));
        int w = maceSlot(p, t);
        if (w < 0) w = melee(p);
        if (w >= 0) p.getInventory().setSelectedSlot(w);
        c.interactionManager.attackEntity(p, t);
        p.swingHand(Hand.MAIN_HAND);
        p.setYaw(oy);
        p.setPitch(op);
    }

    // ------------------------------------------------------------ yordamchi
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

    private static int pearlSlot(ClientPlayerEntity p) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(Items.ENDER_PEARL) && !p.getItemCooldownManager().isCoolingDown(s)) return i;
        }
        return -1;
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

    private static boolean inReach(ClientPlayerEntity p, LivingEntity t) {
        Vec3d eye = p.getEyePos();
        var b = t.getBoundingBox();
        Vec3d cl = new Vec3d(MathHelper.clamp(eye.x, b.minX, b.maxX), MathHelper.clamp(eye.y, b.minY, b.maxY),
                MathHelper.clamp(eye.z, b.minZ, b.maxZ));
        return eye.distanceTo(cl) <= 3.5;
    }

    /** Nishon tanlash: 0 = eng yaqin, 1 = qarash (pritsel) bo'yicha eng yaqin. */
    private PlayerEntity pick(MinecraftClient c, ClientPlayerEntity p, PlayerChaseConfig cfg) {
        List<PlayerEntity> candidates = c.world.getPlayers().stream()
                .map(PlayerEntity.class::cast)
                .filter(e -> valid(p, e, cfg))
                .toList();
        PlayerEntity best = null;
        if (cfg.targetSelect == 1) {
            Vec3d eye = p.getEyePos(), look = p.getRotationVec(1.0f);
            double bestCos = 0.85;
            for (PlayerEntity e : candidates) {
                if (e.squaredDistanceTo(p) > (double) cfg.range * cfg.range) continue;
                Vec3d to = e.getBoundingBox().getCenter().subtract(eye);
                double cos = to.normalize().dotProduct(look);
                if (cos > bestCos) {
                    bestCos = cos;
                    best = e;
                }
            }
        } else {
            double bestD = cfg.range;
            for (PlayerEntity e : candidates) {
                double d = e.distanceTo(p);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
        }
        return best;
    }

    private static boolean valid(ClientPlayerEntity p, PlayerEntity e, PlayerChaseConfig cfg) {
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

    private void say(ClientPlayerEntity p, String msg) {
        if (PlayerChaseConfig.get().messages) p.sendMessage(Text.literal(msg), false);
    }

    public String hudLine() {
        PlayerChaseConfig cfg = PlayerChaseConfig.get();
        if (!cfg.hud || phase == Phase.IDLE) return null;
        return "§dChase: §f" + (target != null ? target.getName().getString() : "-") + " §7" + status;
    }
}
