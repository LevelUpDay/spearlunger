package uz.bowaim;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import uz.autotnt.AutoTntConfig;
import uz.spearlunger.Rotations;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kamon/arbalet/trident bilan pritselga eng yaqin nishonni o'zi oladi.
 * O'q tushishi (gravitatsiya + havo qarshiligi), nishon qayerga yurayotgani / sakrayotgani va
 * o'zingizning tezligingiz hisobga olinadi. Xohlasangiz o'zi otadi.
 */
public class BowAimController {
    /** Snaryad fizikasi: tezlik, gravitatsiya, havo qarshiligi. */
    private record Ballistics(double speed, double gravity, double drag) {}

    private final Map<UUID, Vec3d> lastPos = new HashMap<>();
    private final Map<UUID, Vec3d> velocity = new HashMap<>();
    private LivingEntity target = null;
    private double targetDist = 0;
    private int crossCooldown = 0;

    public void tick(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (p == null || c.world == null || c.interactionManager == null) {
            target = null;
            lastPos.clear();
            velocity.clear();
            return;
        }
        BowAimConfig cfg = BowAimConfig.get();
        while (BowAimClient.keyToggle.wasPressed()) {
            cfg.enabled = !cfg.enabled;
            BowAimConfig.save();
            p.sendMessage(Text.literal(cfg.enabled ? "§aBow Aim: ON" : "§cBow Aim: OFF"), true);
        }
        trackVelocities(c.world, p, cfg);
        if (crossCooldown > 0) crossCooldown--;

        if (!cfg.enabled || c.currentScreen != null || p.isSpectator()) {
            target = null;
            return;
        }

        // qaysi qurol va qanday snaryad
        Ballistics b = null;
        Hand crossHand = null;
        boolean bow = false, tridentUse = false;
        if (p.isUsingItem() && p.getActiveItem().isOf(Items.BOW)) {
            float pull = BowItem.getPullProgress(p.getItemUseTime());
            b = new Ballistics(Math.max(0.3f, pull) * 3.0, 0.05, 0.99);
            bow = true;
        } else if (cfg.trident && p.isUsingItem() && p.getActiveItem().isOf(Items.TRIDENT)) {
            b = new Ballistics(2.5, 0.05, 0.99);
            tridentUse = true;
        } else {
            for (Hand h : Hand.values()) {
                ItemStack s = p.getStackInHand(h);
                if (!s.isOf(Items.CROSSBOW)) continue;
                ChargedProjectilesComponent cp = s.get(DataComponentTypes.CHARGED_PROJECTILES);
                if (cp == null || cp.isEmpty()) continue;
                boolean firework = !cp.getProjectiles().isEmpty() && cp.getProjectiles().get(0).isOf(Items.FIREWORK_ROCKET);
                b = firework ? new Ballistics(1.6, 0.0, 1.0) : new Ballistics(3.15, 0.05, 0.99);
                crossHand = h;
                break;
            }
        }
        if (b == null) {
            target = null;
            return;
        }

        Vec3d eye = p.getEyePos();
        target = pickTarget(c.world, p, eye, cfg);
        if (target == null) return;
        targetDist = target.distanceTo(p);

        float[] aim = solve(p, target, eye, b, cfg);
        if (aim == null) return;
        boolean aimed;
        if (cfg.smooth) {
            aimed = Rotations.turn(p, aim[0], aim[1], 1.0f);
        } else {
            p.setYaw(aim[0]);
            p.setPitch(MathHelper.clamp(aim[1], -90.0f, 90.0f));
            aimed = true;
        }
        if (!cfg.autoShoot || !aimed) return;

        if (bow && BowItem.getPullProgress(p.getItemUseTime()) >= cfg.shootCharge / 100.0f - 1e-3) {
            // o'ng tugmani bosib tursangiz vanilla keyingi tickda yana tortishni boshlaydi
            c.interactionManager.stopUsingItem(p);
        } else if (tridentUse && p.getItemUseTime() >= 10) {
            c.interactionManager.stopUsingItem(p);
        } else if (crossHand != null && cfg.crossbowShoot && crossCooldown == 0 && !p.isUsingItem()) {
            c.interactionManager.interactItem(p, crossHand);
            crossCooldown = 5;
        }
    }

    // ------------------------------------------------------------ nishon
    private LivingEntity pickTarget(ClientWorld w, ClientPlayerEntity p, Vec3d eye, BowAimConfig cfg) {
        Vec3d look = p.getRotationVec(1.0f);
        if (cfg.stickyTarget && target != null && valid(w, p, target, eye, cfg)
                && angleTo(look, eye, aimPoint(target, cfg)) <= cfg.fov * 1.5) {
            return target;
        }
        LivingEntity best = null;
        double bestAngle = cfg.fov;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(cfg.range),
                e -> e != p && valid(w, p, e, eye, cfg))) {
            double a = angleTo(look, eye, aimPoint(e, cfg));
            if (a <= bestAngle) {
                bestAngle = a;
                best = e;
            }
        }
        return best;
    }

    private boolean valid(ClientWorld w, ClientPlayerEntity p, LivingEntity e, Vec3d eye, BowAimConfig cfg) {
        if (!e.isAlive() || e.isRemoved() || e.distanceTo(p) > cfg.range) return false;
        if (e instanceof PlayerEntity pl) {
            if (!cfg.players || pl.isSpectator() || pl.isCreative()) return false;
            if (cfg.ignoreFriends && isFriend(pl)) return false;
        } else if (!(cfg.mobs && e instanceof Monster)) {
            return false;
        }
        if (cfg.visibleOnly) {
            Vec3d to = aimPoint(e, cfg);
            HitResult r = w.raycast(new RaycastContext(eye, to, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, p));
            if (r.getType() == HitResult.Type.BLOCK && r.getPos().distanceTo(eye) < eye.distanceTo(to) - 0.5) {
                // gavdasi ko'rinmasa boshini tekshiramiz
                Vec3d head = e.getEyePos();
                HitResult r2 = w.raycast(new RaycastContext(eye, head, RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE, p));
                if (r2.getType() == HitResult.Type.BLOCK && r2.getPos().distanceTo(eye) < eye.distanceTo(head) - 0.5) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Vec3d aimPoint(LivingEntity e, BowAimConfig cfg) {
        return switch (Math.floorMod(cfg.aimPoint, 3)) {
            case 1 -> e.getEyePos().add(0, -0.1, 0);
            case 2 -> e.getEntityPos().add(0, 0.3, 0);
            default -> e.getBoundingBox().getCenter();
        };
    }

    private static double angleTo(Vec3d look, Vec3d eye, Vec3d point) {
        Vec3d to = point.subtract(eye).normalize();
        return Math.toDegrees(Math.acos(MathHelper.clamp(look.dotProduct(to), -1.0, 1.0)));
    }

    // ------------------------------------------------------------ ballistika
    /** {yaw, pitch}: nishon o'q yetib borguncha qayerda bo'lishini bir necha marta aniqlashtiradi. */
    private float[] solve(ClientPlayerEntity p, LivingEntity e, Vec3d eye, Ballistics b, BowAimConfig cfg) {
        Vec3d start = eye.add(0, -0.1, 0);
        Vec3d base = aimPoint(e, cfg);
        Vec3d v = cfg.predict ? velocity.getOrDefault(e.getUuid(), Vec3d.ZERO) : Vec3d.ZERO;
        Vec3d self = p.getVelocity();
        Vec3d selfAdd = cfg.selfVelocity ? new Vec3d(self.x, p.isOnGround() ? 0 : self.y, self.z) : Vec3d.ZERO;
        double ticks = base.distanceTo(start) / b.speed;
        float[] res = null;
        Vec3d pred = base;
        for (int iter = 0; iter < 4; iter++) {
            double t = Math.min(ticks, 60);
            pred = base.add(v.x * t, 0, v.z * t);
            if (!e.isOnGround() && cfg.predict) {
                // havoda: gravitatsiya bilan tushishi
                double y = 0, vy = v.y;
                for (int i = 0; i < (int) t; i++) {
                    vy = (vy - 0.08) * 0.98;
                    y += vy;
                }
                pred = pred.add(0, Math.max(y, -6), 0);
            }
            // o'q bizning tezligimizni ham oladi, u ham havo qarshiligi bilan so'nadi
            double decay = b.drag >= 1.0 ? t : (1 - Math.pow(b.drag, t)) / (1 - b.drag);
            pred = pred.subtract(selfAdd.multiply(decay));
            res = pitchFor(start, pred, b);
            if (res == null) return null;
            ticks = res[2];
        }
        return new float[] { Rotations.yawTo(eye, pred), res[1] };
    }

    /** {_, pitch, ticks} yoki null (yetib bormaydi). Ikki yechimdan pastroq yoyni tanlaydi. */
    private static float[] pitchFor(Vec3d start, Vec3d target, Ballistics b) {
        double dx = target.x - start.x, dz = target.z - start.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double dy = target.y - start.y;
        float straight = (float) -Math.toDegrees(Math.atan2(dy, dist));
        if (b.gravity == 0) return new float[] { 0, straight, (float) (Math.hypot(dist, dy) / b.speed) };
        if (dist < 0.5) return new float[] { 0, straight, 1 };
        float best = Float.NaN;
        int bestTicks = 0;
        double bestErr = 1.0;   // 1 blokdan ko'p xato bo'lsa yechim yo'q
        for (float pitch = -80.0f; pitch <= 80.0f; pitch += 0.2f) {
            double rad = Math.toRadians(pitch);
            double vh = Math.cos(rad) * b.speed, vy = -Math.sin(rad) * b.speed;
            double x = 0, y = 0;
            for (int i = 1; i <= 200 && vh > 1e-4; i++) {
                double nx = x + vh, ny = y + vy;
                if (nx >= dist) {
                    double hy = y + (ny - y) * ((dist - x) / (nx - x));
                    double err = Math.abs(hy - dy) + Math.abs(pitch - straight) * 0.001;
                    if (err < bestErr) {
                        bestErr = err;
                        best = pitch;
                        bestTicks = i;
                    }
                    break;
                }
                x = nx;
                y = ny;
                vh *= b.drag;
                vy = vy * b.drag - b.gravity;
                if (y < dy - 60) break;
            }
        }
        if (Float.isNaN(best)) return null;
        return new float[] { 0, best, bestTicks };
    }

    // ------------------------------------------------------------ yordamchilar
    private void trackVelocities(ClientWorld w, ClientPlayerEntity self, BowAimConfig cfg) {
        for (PlayerEntity t : w.getPlayers()) track(t);
        if (cfg.mobs) {
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, self.getBoundingBox().expand(cfg.range),
                    e -> e instanceof Monster)) track(e);
        }
    }

    private void track(LivingEntity t) {
        Vec3d cur = t.getEntityPos();
        Vec3d prev = lastPos.get(t.getUuid());
        if (prev != null) {
            Vec3d now = cur.subtract(prev);
            // keskin o'zgarishlarni (strafe) yumshatamiz
            Vec3d old = velocity.getOrDefault(t.getUuid(), now);
            velocity.put(t.getUuid(), now.multiply(0.6).add(old.multiply(0.4)));
        }
        lastPos.put(t.getUuid(), cur);
    }

    private static boolean isFriend(PlayerEntity t) {
        String name = t.getName().getString();
        for (String f : AutoTntConfig.get().friends) if (f.equalsIgnoreCase(name)) return true;
        return false;
    }

    public String hudLine(MinecraftClient c) {
        BowAimConfig cfg = BowAimConfig.get();
        if (c.player == null || !cfg.enabled || !cfg.hud || target == null) return null;
        return String.format("§cAim: §f%s §7(%.1f m)", target.getName().getString(), targetDist);
    }
}
