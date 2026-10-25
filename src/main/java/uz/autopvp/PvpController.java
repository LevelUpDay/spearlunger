package uz.autopvp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PiercingWeaponComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
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
import uz.pearlclutch.PearlClutchClient;
import uz.spearlunger.Rotations;

/**
 * Auto PvP: tugma bilan yoqiladi, o'chirguningizcha eng yaqin dushman bilan o'zi jang qiladi (siz faqat yurasiz):
 *  - uzoqda: Lunge nayza bilan uchib boradi (nayza cooldown'i to'lishi bilan - vanilla bundan tez ruxsat bermaydi),
 *    yerda bo'lsangiz wind charge bilan sakraydi (nayza cooldown'i yo'qolmasligi uchun bir tickda almashtirib),
 *    juda uzoq bo'lsa nishon oldiga ender pearl otadi;
 *  - yaqinda: qilich (bo'lmasa bolta, oxiri nayza) bilan uradi; nishon qalqon ushlasa bolta bilan o'chiradi;
 *  - tushayotganda: mace (zirhli nishonga Breach, aks holda Density) - almashtirib bir tickda uradi.
 */
public class PvpController {
    private LivingEntity target = null;
    private String state = "-";
    private int windCooldown = 0, pearlCooldown = 0;

    public void tick(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (p == null || c.world == null || c.interactionManager == null) {
            target = null;
            return;
        }
        PvpConfig cfg = PvpConfig.get();
        while (AutoPvpClient.keyToggle.wasPressed()) {
            cfg.enabled = !cfg.enabled;
            PvpConfig.save();
            target = null;
            p.sendMessage(Text.literal(cfg.enabled ? "§aAuto PvP: ON" : "§cAuto PvP: OFF"), true);
        }
        if (windCooldown > 0) windCooldown--;
        if (pearlCooldown > 0) pearlCooldown--;
        if (!cfg.enabled || c.currentScreen != null || p.isSpectator() || !p.isAlive()) return;

        if (!valid(p, target, cfg)) target = pick(c.world, p, cfg);
        if (target == null) {
            state = "nishon yo'q";
            return;
        }
        Vec3d eye = p.getEyePos();
        Vec3d center = target.getBoundingBox().getCenter();
        double reachDist = eye.distanceTo(closest(target, eye));
        double melee = cfg.meleeTenths / 10.0;

        // ---- 1. tushayotganda mace
        int mace = cfg.mace ? maceSlot(p, target) : -1;
        if (mace >= 0 && !p.isOnGround() && p.fallDistance >= cfg.maceMinFall && p.getVelocity().y < 0
                && reachDist <= melee + 0.3) {
            if (Rotations.turn(p, Rotations.yawTo(eye, center), Rotations.pitchTo(eye, center))) {
                p.getInventory().setSelectedSlot(mace);   // bir tickda almashtirib uramiz - zarba kuchsizlanmaydi
                c.interactionManager.attackEntity(p, target);
                p.swingHand(Hand.MAIN_HAND);
                state = "mace";
            }
            return;
        }

        // ---- 2. yaqinda: qilich / bolta
        if (cfg.melee && reachDist <= melee) {
            boolean aimed = Rotations.turn(p, Rotations.yawTo(eye, center), Rotations.pitchTo(eye, center));
            int axe = find(p, "_axe");
            int weapon = cfg.axeShield && target.isBlocking() && axe >= 0 ? axe : find(p, "_sword");
            if (weapon < 0) weapon = axe;
            if (weapon < 0) weapon = spearSlot(p);
            if (weapon < 0) return;
            if (p.getInventory().getSelectedSlot() != weapon) {
                p.getInventory().setSelectedSlot(weapon);
                state = "qurol";
                return;   // cooldown yangi qurol bilan to'lsin
            }
            if (aimed && p.getAttackCooldownProgress(0.5f) >= 0.92f) {
                c.interactionManager.attackEntity(p, target);
                p.swingHand(Hand.MAIN_HAND);
                state = target.isBlocking() ? "bolta (qalqon)" : "urish";
            }
            return;
        }

        // ---- 3. uzoqda: yaqinlashish
        int spear = cfg.lunge ? spearSlot(p) : -1;
        if (p.isOnGround()) {
            // juda uzoq - pearl
            if (cfg.pearl && pearlCooldown == 0 && reachDist >= cfg.pearlDist
                    && PearlClutchClient.CONTROLLER.throwAt(c, p, target.getEntityPos().add(0, 0.05, 0))) {
                pearlCooldown = 40;
                state = "pearl";
                return;
            }
            // wind charge bilan sakrash (nayza cooldown'i yarmidan ko'p bo'lsa - cho'qqida tayyor)
            int wind = find(p, Items.WIND_CHARGE);
            if (cfg.windJump && wind >= 0 && windCooldown == 0 && reachDist >= cfg.windMinDist
                    && (spear < 0 || p.getAttackCooldownProgress(0.0f) >= 0.55f)
                    && !p.getItemCooldownManager().isCoolingDown(p.getInventory().getStack(wind))) {
                launch(c, p, wind, Rotations.yawTo(eye, center));
                windCooldown = 25;
                state = "wind charge";
                return;
            }
        }
        if (spear < 0) {
            Rotations.turn(p, Rotations.yawTo(eye, center), Rotations.pitchTo(eye, center));
            state = "yaqinlashish";
            return;
        }
        float pitch = cfg.flatten && reachDist > 6 ? 0.0f : Rotations.pitchTo(eye, center);
        boolean aimed = Rotations.turn(p, Rotations.yawTo(eye, center), pitch);
        // nayzani jab tickining o'zida olamiz - cooldown nolga tushmaydi (1 tick swap)
        if (aimed) p.getInventory().setSelectedSlot(spear);
        if (aimed && lungeReady(c, p, cfg)) {
            PiercingWeaponComponent pw = p.getMainHandStack().get(DataComponentTypes.PIERCING_WEAPON);
            c.interactionManager.attackWithPiercingWeapon(pw);
            p.swingHand(Hand.MAIN_HAND);
            state = "lunge";
        } else {
            state = "lunge kutish";
        }
    }

    /** Pastga qarab wind charge (qarash faqat paketda) va shu tickning o'zida eski slotga qaytish. */
    private static void launch(MinecraftClient c, ClientPlayerEntity p, int wind, float yaw) {
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

    private static boolean lungeReady(MinecraftClient c, ClientPlayerEntity p, PvpConfig cfg) {
        ItemStack st = p.getMainHandStack();
        if (!st.contains(DataComponentTypes.PIERCING_WEAPON)) return false;
        if (p.hasVehicle() || p.isGliding() || p.isTouchingWater()) return false;
        if (!p.getAbilities().creativeMode && p.getHungerManager().getFoodLevel() < cfg.minFood) return false;
        if (p.isBelowMinimumAttackCharge(st, 0) || p.getAttackCooldownProgress(0.0f) < 1.0f) return false;
        return !c.interactionManager.isFlyingLocked();
    }

    // ------------------------------------------------------------ nishon
    private LivingEntity pick(ClientWorld w, ClientPlayerEntity p, PvpConfig cfg) {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(cfg.range),
                e -> valid(p, e, cfg))) {
            double d = e.squaredDistanceTo(p);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        if (best != null) p.sendMessage(Text.literal("§cAuto PvP: " + best.getName().getString()), true);
        return best;
    }

    private static boolean valid(ClientPlayerEntity p, LivingEntity e, PvpConfig cfg) {
        if (e == null || e == p || !e.isAlive() || e.isRemoved() || e.distanceTo(p) > cfg.range) return false;
        if (e instanceof PlayerEntity pl) {
            if (!cfg.players || pl.isSpectator() || pl.isCreative()) return false;
            return !(cfg.ignoreFriends && isFriend(pl));
        }
        return cfg.mobs && e instanceof Monster;
    }

    private static boolean isFriend(PlayerEntity t) {
        String name = t.getName().getString();
        for (String f : AutoTntConfig.get().friends) if (f.equalsIgnoreCase(name)) return true;
        return false;
    }

    private static Vec3d closest(LivingEntity t, Vec3d from) {
        var b = t.getBoundingBox();
        return new Vec3d(MathHelper.clamp(from.x, b.minX, b.maxX), MathHelper.clamp(from.y, b.minY, b.maxY),
                MathHelper.clamp(from.z, b.minZ, b.maxZ));
    }

    // ------------------------------------------------------------ qurollar
    private static int find(ClientPlayerEntity p, net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

    /** Eng yaxshi (material bo'yicha) qurol: id oxiri "_sword" yoki "_axe". */
    private static int find(ClientPlayerEntity p, String suffix) {
        int best = -1, bestRank = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isEmpty() || (s.isDamageable() && s.willBreakNextUse())) continue;
            String path = Registries.ITEM.getId(s.getItem()).getPath();
            if (!path.endsWith(suffix)) continue;
            int r = rank(path) * 10 + level(s, "sharpness");
            if (r > bestRank) {
                bestRank = r;
                best = i;
            }
        }
        return best;
    }

    /** Eng yuqori Lunge darajali nayza. */
    private static int spearSlot(ClientPlayerEntity p) {
        int best = -1, bestLvl = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.contains(DataComponentTypes.PIERCING_WEAPON) || (s.isDamageable() && s.willBreakNextUse())) continue;
            int lvl = level(s, "lunge");
            if (lvl > bestLvl) {
                bestLvl = lvl;
                best = i;
            }
        }
        return best;
    }

    /** Zirhli nishonga Breach, aks holda Density; bo'lmasa istalgan mace. */
    private static int maceSlot(ClientPlayerEntity p, LivingEntity target) {
        boolean armored = target.getArmor() >= 12;
        int best = -1, bestScore = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isOf(Items.MACE)) continue;
            int score = 1 + (armored ? level(s, "breach") * 3 + level(s, "density") : level(s, "density") * 3 + level(s, "breach"));
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private static int rank(String path) {
        if (path.startsWith("netherite")) return 6;
        if (path.startsWith("diamond")) return 5;
        if (path.startsWith("iron")) return 4;
        if (path.startsWith("copper")) return 3;
        if (path.startsWith("stone")) return 2;
        if (path.startsWith("golden")) return 1;
        return 0;
    }

    private static int level(ItemStack s, String name) {
        for (RegistryEntry<Enchantment> e : s.getEnchantments().getEnchantments()) {
            if (e.getKey().map(k -> k.getValue().getPath().equals(name)).orElse(false)) {
                return s.getEnchantments().getLevel(e);
            }
        }
        return 0;
    }

    public String hudLine(MinecraftClient c) {
        PvpConfig cfg = PvpConfig.get();
        if (c.player == null || !cfg.enabled || !cfg.hud) return null;
        if (target == null) return "§cAuto PvP §7| " + state;
        return String.format("§cAuto PvP §7| §f%s §7%.1fm | %s", target.getName().getString(),
                c.player.distanceTo(target), state);
    }
}
