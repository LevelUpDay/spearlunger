package uz.spearlunger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/** Dunyo nuqtasini HUD (ekran) koordinatasiga o'tkazish - kamera yo'nalishi va FOV bo'yicha. */
public final class Projector {
    private final Vec3d camPos, f, right, up;
    private final double focal, cx, cy;

    private Projector(Vec3d camPos, Vec3d f, Vec3d right, Vec3d up, double focal, double cx, double cy) {
        this.camPos = camPos;
        this.f = f;
        this.right = right;
        this.up = up;
        this.focal = focal;
        this.cx = cx;
        this.cy = cy;
    }

    /** Shu kadr uchun proyektor (w, h - HUD o'lchami). */
    public static Projector create(MinecraftClient c, float tickDelta, int w, int h) {
        Camera cam = c.gameRenderer.getCamera();
        Vec3d f = Vec3d.fromPolar(cam.getPitch(), cam.getYaw());
        Vec3d eye = c.player.getCameraPosVec(tickDelta);
        Vec3d camPos = cam.isThirdPerson() ? eye.subtract(f.multiply(4.0)) : eye;
        Vec3d right = new Vec3d(-f.z, 0, f.x).normalize();
        Vec3d up = right.crossProduct(f).normalize();
        double focal = (h / 2.0) / Math.tan(Math.toRadians(c.options.getFov().getValue()) / 2.0);
        return new Projector(camPos, f, right, up, focal, w / 2.0, h / 2.0);
    }

    /** {x, y} yoki null (kamera orqasida). */
    public double[] project(Vec3d world) {
        Vec3d rel = world.subtract(camPos);
        double z = rel.dotProduct(f);
        if (z < 0.1) return null;
        return new double[] { cx + rel.dotProduct(right) / z * focal, cy - rel.dotProduct(up) / z * focal };
    }

    public double distance(Vec3d world) {
        return world.distanceTo(camPos);
    }
}
