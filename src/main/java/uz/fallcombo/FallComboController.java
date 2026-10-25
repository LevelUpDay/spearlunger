package uz.fallcombo;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import uz.autotnt.AutoTntConfig;
import uz.spearlunger.Rotations;

/**
 * Tepadan o'yinchi ustiga tushayotganda kombinatsiya:
 *  1. spear bilan zaryad (o'ng tugma bosib turiladi) - tushish tezligi kinetik zararni oshiradi;
 *  2. spear tegishi bilan (yoki yetarlicha yaqinlashganda) qilichga (bo'lmasa boltaga) o'tib uradi,
 *     nishon qalqon ushlab tursa avval bolta bilan qalqonini o'chiradi;
 *  3. mace'ga o'tib turadi - mace zarbasini X rejimi yoki o'zingiz berasiz.
 */
public class FallComboController {
    private enum Phase { IDLE, SPEAR, HIT, SWORD_AFTER_AXE, DONE }

    private Phase phase = Phase.IDLE;
    private LivingEntity target = null;
    private int originalSlot = -1;
    private int ticks = 0;
    private int lastHurt = 0;
    private int pendingAttackSlot = -1;
    private boolean holdingUse = false;

    public void tick(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (p == null || c.world == null || c.interactionManager == null) {
            releaseUse(c);
            phase = Phase.IDLE;
            target = null;
            return;
        }
        FallComboConfig cfg = FallComboConfig.get();
        while (FallComboClient.keyToggle.wasPressed()) {
            cfg.enabled = !cfg.enabled;
            FallComboConfig.save();
            p.sendMessage(Text.literal(cfg.enabled ? "§aFall Combo: ON" : "§cFall Combo: OFF"), true);
        }
        if (!cfg.enabled || c.currentScreen != null) {
            if (phase != Phase.IDLE) finish(c, p, false);
            return;
        }

        switch (phase) {
            case IDLE -> tryStart(c, p, cfg);
            case SPEAR -> tickSpear(c, p, cfg);
            case HIT -> tickHit(c, p, cfg);
            case SWORD_AFTER_AXE -> {
                // qalqon o'chdi, endi qilich bilan
                if (pendingAttackSlot >= 0 && valid(p, target)) attack(c, p, pendingAttackSlot, cfg);
                pendingAttackSlot = -1;
                finish(c, p, true);
            }
            case DONE -> {
                // yerga tushguncha qaytadan boshlamaymiz
                if (p.isOnGround() || p.isTouchingWater()) phase = Phase.IDLE;
            }
        }
    }

    // ------------------------------------------------------------ boshlash
    private void tryStart(MinecraftClient c, ClientPlayerEntity p, FallComboConfig cfg) {
        if (p.isOnGround() || p.isGliding() || p.isTouchingWater() || p.hasVehicle()) return;
        if (p.getVelocity().y > -0.2 || p.fallDistance < cfg.minFall) return;
        LivingEntity t = findTarget(c.world, p, cfg);
        if (t == null) return;
        target = t;
        lastHurt = t.hurtTime;
        originalSlot = p.getInventory().getSelectedSlot();
        ticks = 0;
        int spear = cfg.useSpear ? find(p, "_spear") : -1;
        if (spear >= 0 && !p.isUsingItem()) {
            p.getInventory().setSelectedSlot(spear);
            c.interactionManager.interactItem(p, Hand.MAIN_HAND);
            c.options.useKey.setPressed(true);   // bosib turmasak vanilla zaryadni darhol to'xtatadi
            holdingUse = true;
            phase = Phase.SPEAR;
            say(p, "§bFall Combo: spear → " + t.getName().getString());
        } else {
            phase = Phase.HIT;
        }
    }

    private void tickSpear(MinecraftClient c, ClientPlayerEntity p, FallComboConfig cfg) {
        if (!valid(p, target) || ++ticks > 80) {
            finish(c, p, false);
            return;
        }
        if (cfg.aim) aimAt(p, target);
        boolean hit = target.hurtTime > lastHurt;   // spear tegdi
        lastHurt = target.hurtTime;
        double d = p.distanceTo(target);
        if (hit || p.isOnGround() || d <= cfg.fallbackTenths / 10.0) {
            releaseUse(c);
            if (p.isUsingItem()) c.interactionManager.stopUsingItem(p);
            phase = Phase.HIT;
            ticks = 0;
            tickHit(c, p, cfg);   // shu tickning o'zida qilich
        }
    }

    private void tickHit(MinecraftClient c, ClientPlayerEntity p, FallComboConfig cfg) {
        if (!cfg.followHit) {
            finish(c, p, true);
            return;
        }
        if (!valid(p, target) || ++ticks > 30) {
            finish(c, p, false);
            return;
        }
        if (cfg.aim) aimAt(p, target);
        if (p.distanceTo(target) > cfg.hitReachTenths / 10.0) {
            if (p.isOnGround()) finish(c, p, false);
            return;   // hali yetib kelmadik
        }
        int sword = find(p, "_sword");
        int axe = find(p, "_axe");
        if (cfg.breakShield && target.isBlocking() && axe >= 0) {
            attack(c, p, axe, cfg);
            pendingAttackSlot = sword;
            phase = Phase.SWORD_AFTER_AXE;
            say(p, "§eFall Combo: qalqon o'chirildi");
            return;
        }
        int weapon = sword >= 0 ? sword : axe;
        if (weapon >= 0 && !cfg.sameTickSwap) {
            // avval almashtirib, keyingi tickda uramiz
            p.getInventory().setSelectedSlot(weapon);
            pendingAttackSlot = weapon;
            phase = Phase.SWORD_AFTER_AXE;
            return;
        }
        if (weapon >= 0) attack(c, p, weapon, cfg);
        finish(c, p, true);
    }

    private void attack(MinecraftClient c, ClientPlayerEntity p, int slot, FallComboConfig cfg) {
        p.getInventory().setSelectedSlot(slot);
        // attackEntity tanlangan slotni o'zi yuboradi: server almashtirishni zarba bilan bir tickda oladi
        c.interactionManager.attackEntity(p, target);
        p.swingHand(Hand.MAIN_HAND);
    }

    private void finish(MinecraftClient c, ClientPlayerEntity p, boolean ok) {
        releaseUse(c);
        if (p.isUsingItem() && Registries.ITEM.getId(p.getActiveItem().getItem()).getPath().endsWith("_spear")) {
            c.interactionManager.stopUsingItem(p);
        }
        int mace = FallComboConfig.get().endOnMace ? find(p, "mace") : -1;
        if (ok && mace >= 0) p.getInventory().setSelectedSlot(mace);
        else if (originalSlot >= 0) p.getInventory().setSelectedSlot(originalSlot);
        originalSlot = -1;
        target = null;
        phase = p.isOnGround() ? Phase.IDLE : Phase.DONE;
    }

    private void releaseUse(MinecraftClient c) {
        if (holdingUse) c.options.useKey.setPressed(false);
        holdingUse = false;
    }

    // ------------------------------------------------------------ yordamchilar
    private LivingEntity findTarget(ClientWorld w, ClientPlayerEntity p, FallComboConfig cfg) {
        double range = cfg.rangeTenths / 10.0;
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(range, 32, range),
                e -> e != p && e.isAlive())) {
            if (e instanceof PlayerEntity pl) {
                if (!cfg.players || pl.isSpectator() || pl.isCreative()) continue;
                if (cfg.ignoreFriends && isFriend(pl)) continue;
            } else if (!(cfg.mobs && e instanceof Monster)) {
                continue;
            }
            double dy = p.getY() - e.getY();
            double hd = Math.hypot(e.getX() - p.getX(), e.getZ() - p.getZ());
            if (dy < 0.5 || dy > 32 || hd > range) continue;
            if (hd < bestD) {
                bestD = hd;
                best = e;
            }
        }
        return best;
    }

    private static boolean valid(ClientPlayerEntity p, LivingEntity t) {
        return t != null && t.isAlive() && !t.isRemoved() && p.distanceTo(t) < 40;
    }

    private static void aimAt(ClientPlayerEntity p, LivingEntity t) {
        Vec3d eye = p.getEyePos(), to = t.getBoundingBox().getCenter();
        Rotations.turn(p, Rotations.yawTo(eye, to), Rotations.pitchTo(eye, to));
    }

    /** Hotbar'dagi eng yaxshi (material bo'yicha) item: id oxiri bo'yicha ("_sword", "_axe", "_spear", "mace"). */
    private static int find(ClientPlayerEntity p, String suffix) {
        int best = -1, bestRank = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isEmpty() || (s.isDamageable() && s.willBreakNextUse())) continue;
            String path = Registries.ITEM.getId(s.getItem()).getPath();
            boolean match = suffix.equals("mace") ? s.isOf(Items.MACE) : path.endsWith(suffix);
            if (!match) continue;
            int r = rank(path);
            if (r > bestRank) {
                bestRank = r;
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

    private static boolean isFriend(PlayerEntity t) {
        String name = t.getName().getString();
        for (String f : AutoTntConfig.get().friends) if (f.equalsIgnoreCase(name)) return true;
        return false;
    }

    private void say(ClientPlayerEntity p, String msg) {
        if (FallComboConfig.get().messages) p.sendMessage(Text.literal(msg), true);
    }
}
