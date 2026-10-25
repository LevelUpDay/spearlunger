package uz.autoshield;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import uz.autotnt.AutoTntConfig;
import uz.spearlunger.Rotations;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import uz.spearlunger.InvHelper;

/**
 * Qo'lda (o'ng yoki chap) qalqon bo'lsa, xavf paydo bo'lganda o'zi ko'taradi.
 * Qalqon ko'tarilgach ~5 tick o'tib to'sa boshlaydi, shuning uchun xavfni zarbadan OLDIN aniqlaymiz:
 * yaqinda sizga qarab turgan o'yinchi/mob, sizni nishonga olgan kamon, uchib kelayotgan o'q, portlayotgan creeper.
 */
public class ShieldController {
    private record Threat(Vec3d pos, String why) {}

    private final Map<UUID, Vec3d> lastPos = new HashMap<>();
    private final Map<UUID, Vec3d> velocity = new HashMap<>();
    /** Qalqonni biz ko'tardik (foydalanuvchi o'zi ko'targan bo'lsa tegmaymiz). */
    private boolean ours = false;
    private int holdTimer = 0;
    private int lowered = 0;
    /** Qalqon necha tickdan beri ko'tarilgan va qayta ko'tarishgacha kutish. */
    private int upTicks = 0, rearm = 0;
    private int lastHurt = 0;
    private Vec3d threatPos = null;
    private String reason = null;

    public void tick(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (p == null || c.world == null || c.interactionManager == null) {
            if (ours) c.options.useKey.setPressed(false);
            ours = false;
            lastPos.clear();
            velocity.clear();
            return;
        }
        ShieldConfig cfg = ShieldConfig.get();
        while (AutoShieldClient.keyToggle.wasPressed()) {
            cfg.enabled = !cfg.enabled;
            ShieldConfig.save();
            p.sendMessage(Text.literal(cfg.enabled ? "§aAuto Shield: ON" : "§cAuto Shield: OFF"), true);
        }
        for (PlayerEntity t : c.world.getPlayers()) track(t);

        Hand hand = shieldHand(p);
        if (!cfg.enabled || hand == null || InvHelper.screenOpen(c) || p.isSpectator()) {
            lower(c, p);
            holdTimer = 0;
            return;
        }
        // foydalanuvchi boshqa narsa ishlatyapti (ovqat, kamon) yoki o'zi qalqon ushlab turibdi
        if (p.isUsingItem() && !ours) return;
        ItemStack shield = p.getStackInHand(hand);
        if (p.getItemCooldownManager().isCoolingDown(shield)) {   // bolta bilan o'chirilgan
            lower(c, p);
            return;
        }

        boolean hurtNow = p.hurtTime > lastHurt;
        lastHurt = p.hurtTime;
        if (rearm > 0) rearm--;
        Threat t = findThreat(c.world, p, cfg);
        // o'q va zarba - darhol; boshqa xavf - qisqa "puls" (doim ko'tarilib turmaydi)
        boolean urgent = t != null && t.why.equals("o'q");
        if (t != null && (rearm == 0 || urgent || ours)) {
            holdTimer = Math.max(holdTimer, cfg.holdAfterTicks + 1);
            threatPos = t.pos;
            reason = t.why;
        } else if (cfg.onHurt && hurtNow) {
            holdTimer = Math.max(holdTimer, cfg.holdAfterTicks + 4);
            rearm = 0;
            reason = "zarba";
        }
        if (ours && ++upTicks > cfg.maxUpTicks && !urgent) {
            // juda uzoq ko'tarildi: tushiramiz va biroz kutamiz
            lower(c, p);
            holdTimer = 0;
            rearm = cfg.rearmTicks;
            return;
        }

        // urish uchun tushiramiz (qalqon ko'tarilganda vanilla chap tugmani e'tiborsiz qoldiradi)
        if (cfg.lowerToAttack && c.options.attackKey.isPressed()) {
            lower(c, p);
            lowered = 3;
            return;
        }
        if (lowered > 0) {
            lowered--;
            return;
        }

        if (holdTimer <= 0) {
            lower(c, p);
            threatPos = null;
            return;
        }
        holdTimer--;
        if (cfg.autoFace && threatPos != null) {
            float yaw = Rotations.yawTo(p.getEyePos(), threatPos);
            if (Math.abs(MathHelper.wrapDegrees(yaw - p.getYaw())) > 50) Rotations.turn(p, yaw, p.getPitch(), 30);
        }
        if (!p.isUsingItem()) {
            c.interactionManager.interactItem(p, hand);
            ours = p.isUsingItem();
        }
        // bosib turmasak vanilla qalqonni darhol tushiradi
        if (ours) c.options.useKey.setPressed(true);
    }

    private void lower(MinecraftClient c, ClientPlayerEntity p) {
        upTicks = 0;
        if (!ours) return;
        ours = false;
        c.options.useKey.setPressed(false);
        if (p.isUsingItem()) c.interactionManager.stopUsingItem(p);
    }

    private static Hand shieldHand(ClientPlayerEntity p) {
        if (p.getOffHandStack().isOf(Items.SHIELD)) return Hand.OFF_HAND;
        if (p.getMainHandStack().isOf(Items.SHIELD)) return Hand.MAIN_HAND;
        return null;
    }

    // ------------------------------------------------------------ xavf
    private Threat findThreat(ClientWorld w, ClientPlayerEntity p, ShieldConfig cfg) {
        Vec3d me = p.getBoundingBox().getCenter();
        Vec3d eye = p.getEyePos();
        double cosFace = Math.cos(Math.toRadians(cfg.faceAngle));

        if (cfg.projectiles) {
            for (ProjectileEntity pr : w.getEntitiesByClass(ProjectileEntity.class, p.getBoundingBox().expand(24),
                    e -> e.getOwner() != p)) {
                Vec3d v = pr.getVelocity();
                double vv = v.lengthSquared();
                if (vv < 0.04) continue;   // yerga qadalgan yoki to'xtagan
                Vec3d rel = me.subtract(pr.getEntityPos());
                double t = MathHelper.clamp(rel.dotProduct(v) / vv, 0, 15);
                if (t <= 0) continue;      // bizdan uzoqlashyapti
                Vec3d closest = pr.getEntityPos().add(v.multiply(t));
                if (closest.distanceTo(me) < 1.6) return new Threat(pr.getEntityPos(), "o'q");
            }
        }

        Threat best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(32), e -> e != p && e.isAlive())) {
            double d = e.distanceTo(p);
            String why = null;
            if (e instanceof PlayerEntity pl) {
                if (pl.isSpectator() || (cfg.ignoreFriends && isFriend(pl))) continue;
                why = playerThreat(pl, p, eye, d, cosFace, cfg);
            } else if (e instanceof CreeperEntity cr) {
                if (cfg.creepers && d < 7 && (cr.getFuseSpeed() > 0 || cr.isIgnited() || cr.getLerpedFuseTime(1.0f) > 0)) why = "creeper";
            } else if (e instanceof Monster && cfg.mobs) {
                boolean facing = facing(e, eye, Math.cos(Math.toRadians(50)));
                if (d <= cfg.mobRangeTenths / 10.0 && facing) why = "mob";
                else if (cfg.mobRanged && d < 28 && e.isUsingItem() && facing(e, eye, Math.cos(Math.toRadians(15)))) {
                    why = "mob o'q tortyapti";
                }
            }
            if (why != null && d < bestD) {
                bestD = d;
                best = new Threat(e.getBoundingBox().getCenter(), why);
            }
        }
        return best;
    }

    private String playerThreat(PlayerEntity pl, ClientPlayerEntity p, Vec3d eye, double d, double cosFace,
                                ShieldConfig cfg) {
        if (!cfg.players && !cfg.playerRanged && !cfg.maceFall) return null;
        Vec3d v = velocity.getOrDefault(pl.getUuid(), Vec3d.ZERO);
        double range = cfg.playerRange / 10.0;

        if (cfg.maceFall) {
            double dy = pl.getY() - p.getY();
            double hd = Math.hypot(pl.getX() - p.getX(), pl.getZ() - p.getZ());
            if (dy > 1.5 && hd < 3.5 && v.y < -0.25) return "mace";
        }
        if (cfg.playerRanged && d < 48) {
            ItemStack active = pl.isUsingItem() ? pl.getActiveItem() : ItemStack.EMPTY;
            boolean drawing = active.isOf(Items.BOW) || active.isOf(Items.TRIDENT);
            boolean loaded = false;
            for (ItemStack s : new ItemStack[] { pl.getMainHandStack(), pl.getOffHandStack() }) {
                if (!s.isOf(Items.CROSSBOW)) continue;
                ChargedProjectilesComponent cp = s.get(DataComponentTypes.CHARGED_PROJECTILES);
                if (cp != null && !cp.isEmpty()) loaded = true;
            }
            // uzoqda kichik burchak ham yetadi
            double cos = Math.cos(Math.toRadians(Math.max(4, 12 - d / 6)));
            if ((drawing || loaded) && d > 2 && facing(pl, eye, cos)) return drawing ? "kamon" : "arbalet";
        }
        if (cfg.players) {
            if (d <= range && facing(pl, eye, cosFace)) return "o'yinchi";
            // tez yugurib kelyapti: keyingi 3-4 tickda masofaga kiradi
            Vec3d toMe = p.getEntityPos().subtract(pl.getEntityPos());
            double closing = new Vec3d(v.x, 0, v.z).dotProduct(new Vec3d(toMe.x, 0, toMe.z).normalize());
            if (d <= range + 3 && closing > 0.2 && d - closing * 4 <= range && facing(pl, eye, cosFace)) {
                return "yugurib kelyapti";
            }
        }
        return null;
    }

    private static boolean facing(LivingEntity e, Vec3d target, double cos) {
        Vec3d look = e.getRotationVec(1.0f);
        Vec3d to = target.subtract(e.getEyePos());
        if (to.lengthSquared() < 1e-4) return true;
        return look.dotProduct(to.normalize()) >= cos;
    }

    private void track(Entity t) {
        Vec3d cur = t.getEntityPos();
        Vec3d prev = lastPos.get(t.getUuid());
        if (prev != null) velocity.put(t.getUuid(), cur.subtract(prev));
        lastPos.put(t.getUuid(), cur);
    }

    private static boolean isFriend(PlayerEntity t) {
        String name = t.getName().getString();
        for (String f : AutoTntConfig.get().friends) if (f.equalsIgnoreCase(name)) return true;
        return false;
    }

    public String hudLine(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        ShieldConfig cfg = ShieldConfig.get();
        if (p == null || !cfg.enabled || !cfg.hud || shieldHand(p) == null) return null;
        if (ours && p.isUsingItem()) return "§9Shield: §aUP §7(" + reason + ")";
        if (p.getItemCooldownManager().isCoolingDown(p.getStackInHand(shieldHand(p)))) return "§9Shield: §cbuzilgan";
        return "§9Shield: §7tayyor";
    }
}
