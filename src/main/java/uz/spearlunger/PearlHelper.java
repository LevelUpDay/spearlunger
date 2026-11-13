package uz.spearlunger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Ender pearl: otilganda o'yinchining tezligini (yurish, yugurish, sakrash, yiqilish) o'ziga oladi, shuning uchun
 * qimirlab otsangiz pearl boshqa joyga tushadi. Shuni hisoblab, pearl aynan kerakli nuqtaga tushadigan yo'nalishni
 * topadi (bir nechta modul - Auto PvP, Pearl Boost, Elytra PvP - shu yordamchidan foydalanadi).
 * Yo'nalish faqat otish paketiga yoziladi - kamera qimirlamaydi.
 */
public final class PearlHelper {
    private static final double SPEED = 1.5, GRAVITY = 0.03, DRAG = 0.99;
    private static final int MAX_TICKS = 160;

    private record Aim(float yaw, float pitch, int ticks) {}

    private PearlHelper() {}

    /** Hotbar'dagi pearl'ni target nuqtaga (harakatni hisoblab) otadi. Otilsa true. */
    public static boolean throwAt(MinecraftClient c, ClientPlayerEntity p, Vec3d target) {
        int slot = -1;
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(Items.ENDER_PEARL)) { slot = i; break; }
        if (slot < 0 || p.getItemCooldownManager().isCoolingDown(p.getInventory().getStack(slot))) return false;
        Vec3d start = spawnPos(p), add = shooterVelocity(p);
        Aim aim = solve(start, target, add);
        if (aim == null || !verify(c.world, p, start, aim, add, target, 2.5)) return false;
        int original = p.getInventory().getSelectedSlot();
        p.getInventory().setSelectedSlot(slot);
        throwPearl(c, p, Hand.MAIN_HAND, aim);
        p.getInventory().setSelectedSlot(original);
        return true;
    }

    // ------------------------------------------------------------ otish
    /** Yo'nalishni faqat otish paketiga yozib otadi va qarashni darhol qaytaradi. */
    private static void throwPearl(MinecraftClient c, ClientPlayerEntity p, Hand hand, Aim aim) {
        float yaw = p.getYaw(), pitch = p.getPitch();
        p.setYaw(aim.yaw);
        p.setPitch(aim.pitch);
        c.interactionManager.interactItem(p, hand);
        p.swingHand(hand);
        p.setYaw(yaw);
        p.setPitch(pitch);
    }

    // ------------------------------------------------------------ ballistika
    /** Pearl o'yinchi tezligini oladi: x/z har doim, y - faqat havoda bo'lsa. */
    private static Vec3d shooterVelocity(ClientPlayerEntity p) {
        Vec3d v = p.getVelocity();
        return new Vec3d(v.x, p.isOnGround() ? 0 : v.y, v.z);
    }

    private static Vec3d spawnPos(ClientPlayerEntity p) {
        return p.getEyePos().add(0, -0.1, 0);
    }

    /**
     * start'dan target'ga tushadigan yo'nalish (otuvchi tezligi add bilan). Gorizontal tekislikda: otish yo'nalishi
     * + add yig'indisi nishonga qarab yo'nalgan bo'lishi kerak - yaw shundan aniq hisoblanadi; pitch qidiriladi.
     * Bir nechta yechimdan eng tez yetib boradigani.
     */
    private static Aim solve(Vec3d start, Vec3d target, Vec3d add) {
        double dx = target.x - start.x, dz = target.z - start.z, dy = target.y - start.y;
        double dist = Math.sqrt(dx * dx + dz * dz);
        Aim best = null;
        double bestErr = Double.MAX_VALUE;
        if (dist < 0.05) {
            // to'g'ri ostimizda/ustimizda
            for (float pitch = -90.0f; pitch <= 90.0f; pitch += 180.0f) {
                double vy = -Math.sin(Math.toRadians(pitch)) * SPEED + add.y;
                int t = verticalTicks(vy, dy);
                if (t > 0 && (best == null || t < best.ticks)) best = new Aim(0, pitch, t);
            }
            return best;
        }
        double ux = dx / dist, uz = dz / dist;            // nishonga yo'nalish
        double px = -uz, pz = ux;                          // unga perpendikulyar
        double addPar = add.x * ux + add.z * uz, addPerp = add.x * px + add.z * pz;
        for (float pitch = -89.5f; pitch <= 89.5f; pitch += 0.25f) {
            double rad = Math.toRadians(pitch);
            double a = Math.cos(rad) * SPEED;
            if (a < 1e-3 || Math.abs(addPerp) > a) continue;
            double perp = -addPerp / a;                    // yon tezlikni bekor qilamiz
            double par = Math.sqrt(1 - perp * perp);
            double vh = a * par + addPar;
            if (vh <= 0.01) continue;
            double vy = -Math.sin(rad) * SPEED + add.y;
            double x = 0, y = 0;
            for (int i = 1; i <= MAX_TICKS; i++) {
                double nx = x + vh, ny = y + vy;
                if (nx >= dist) {
                    double hy = y + (ny - y) * ((dist - x) / (nx - x));
                    double err = Math.abs(hy - dy);
                    // yetarlicha aniq yechimlardan eng tez yetib boradigani
                    if (err < 0.35 && (best == null || i < best.ticks || (i == best.ticks && err < bestErr))) {
                        bestErr = err;
                        double wx = par * ux + perp * px, wz = par * uz + perp * pz;   // otish yo'nalishi (gorizontal)
                        float yaw = (float) Math.toDegrees(Math.atan2(-wx, wz));
                        best = new Aim(yaw, pitch, i);
                    }
                    break;
                }
                x = nx;
                y = ny;
                vh *= DRAG;
                vy = vy * DRAG - GRAVITY;
                if (y < dy - 80) break;
            }
        }
        return best;
    }

    /** Vertikal: dy ga yetib borish ticki (yo'q bo'lsa -1). */
    private static int verticalTicks(double vy, double dy) {
        double y = 0;
        for (int i = 1; i <= MAX_TICKS; i++) {
            double ny = y + vy;
            if ((dy <= 0 && ny <= dy) || (dy > 0 && ny >= dy)) return i;
            y = ny;
            vy = vy * DRAG - GRAVITY;
        }
        return -1;
    }

    /** To'liq 3D simulyatsiya (bloklar bilan): birinchi tegadigan joy kutilgan nuqtaga yaqinmi. */
    private static boolean verify(ClientWorld w, ClientPlayerEntity p, Vec3d start, Aim aim, Vec3d add, Vec3d expected,
                                  double tolerance) {
        Vec3d v = Vec3d.fromPolar(aim.pitch, aim.yaw).multiply(SPEED).add(add);
        HitResult hit = trace(w, p, start, v);
        return hit != null && hit.getPos().distanceTo(expected) <= tolerance;
    }

    private static HitResult trace(ClientWorld w, ClientPlayerEntity p, Vec3d start, Vec3d vel) {
        Vec3d pos = start, v = vel;
        for (int i = 0; i < MAX_TICKS; i++) {
            Vec3d next = pos.add(v);
            BlockHitResult r = w.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, p));
            if (r.getType() == HitResult.Type.BLOCK) return r;
            pos = next;
            v = v.multiply(DRAG).add(0, -GRAVITY, 0);
            if (pos.y < w.getBottomY() - 8) break;
        }
        return null;
    }
}
