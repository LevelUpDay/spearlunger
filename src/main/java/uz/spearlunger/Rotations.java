package uz.spearlunger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/** Anti-cheat sozlamalariga mos (silliq, GCD'ga moslangan) kamera burish. */
public final class Rotations {
    private static final Random RNG = new Random();

    private Rotations() {}

    public static float yawTo(Vec3d from, Vec3d to) {
        return (float) (MathHelper.atan2(to.z - from.z, to.x - from.x) * 180.0 / Math.PI) - 90.0f;
    }

    public static float pitchTo(Vec3d from, Vec3d to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        return (float) -(MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0 / Math.PI);
    }

    /** Shu tickda nishonga qarab buriladi. true = aimTolerance ichida yetib keldi. */
    public static boolean turn(ClientPlayerEntity p, float yaw, float pitch) {
        return turn(p, yaw, pitch, Config.get().ac().aimTolerance);
    }

    public static boolean turn(ClientPlayerEntity p, float yaw, float pitch, float tolerance) {
        Config.AntiCheat ac = Config.get().ac();
        pitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
        float dy = MathHelper.wrapDegrees(yaw - p.getYaw());
        float dp = pitch - p.getPitch();
        if (ac.smoothRotation) {
            int jitter = ac.turnJitter > 0 ? RNG.nextInt(ac.turnJitter * 2 + 1) - ac.turnJitter : 0;
            float max = Math.max(1, ac.maxTurnPerTick + jitter);
            float len = (float) Math.sqrt(dy * dy + dp * dp);
            if (len > max) {
                // ikkala o'q bir vaqtda yetib kelishi uchun proporsional kamaytiramiz
                dy *= max / len;
                dp *= max / len;
            }
        }
        if (ac.gcdFix) {
            float g = gcd();
            dy = Math.round(dy / g) * g;
            dp = Math.round(dp / g) * g;
        }
        p.setYaw(p.getYaw() + dy);
        p.setPitch(MathHelper.clamp(p.getPitch() + dp, -90.0f, 90.0f));
        return isAimed(p, yaw, pitch, Math.max(tolerance, 0.5f));
    }

    public static boolean isAimed(ClientPlayerEntity p, float yaw, float pitch, float tolerance) {
        return Math.abs(MathHelper.wrapDegrees(yaw - p.getYaw())) <= tolerance
                && Math.abs(pitch - p.getPitch()) <= tolerance;
    }

    /** Vanilla sichqoncha harakatining eng kichik qadami (Mouse.updateMouse formulasi). */
    private static float gcd() {
        double s = MinecraftClient.getInstance().options.getMouseSensitivity().getValue();
        double f = s * 0.6 + 0.2;
        return (float) Math.max(f * f * f * 8.0 * 0.15, 0.01);
    }
}
