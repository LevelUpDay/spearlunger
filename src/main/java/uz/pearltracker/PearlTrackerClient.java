package uz.pearltracker;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;
import uz.autotnt.AutoTntConfig;
import uz.spearlunger.Projector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pearl Tracker: dushman otgan ender pearl qayerga tushishini oldindan hisoblaydi (gravitatsiya, havo qarshiligi,
 * bloklar) va o'sha joy atrofini qip-qizil qilib ajratadi, uchish yo'lini va necha soniya qolganini ko'rsatadi -
 * u yerga borib (mace bilan) kutib olasiz.
 */
public class PearlTrackerClient implements ClientModInitializer {
    private static final double GRAVITY = 0.03, DRAG = 0.99;
    public static KeyBinding keyToggle;

    /** Bitta uchayotgan pearl: tushish joyi, yo'li, qolgan ticklar, kimniki, o'zimizniki yoki yo'q. */
    private record Track(Vec3d landing, List<Vec3d> path, int ticks, String owner, boolean mine) {}

    private static final List<Track> TRACKS = new ArrayList<>();
    private static final Set<UUID> ANNOUNCED = new HashSet<>();

    @Override
    public void onInitializeClient() {
        PearlTrackerConfig.load();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("pearltracker", "main"));
        keyToggle = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.pearltracker.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
        ClientTickEvents.END_CLIENT_TICK.register(PearlTrackerClient::tick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of("pearltracker", "marks"),
                PearlTrackerClient::render);
    }

    private static void tick(MinecraftClient c) {
        TRACKS.clear();
        ClientPlayerEntity p = c.player;
        if (p == null || c.world == null) {
            ANNOUNCED.clear();
            return;
        }
        PearlTrackerConfig cfg = PearlTrackerConfig.get();
        while (keyToggle.wasPressed()) {
            cfg.enabled = !cfg.enabled;
            PearlTrackerConfig.save();
            p.sendMessage(Text.literal(cfg.enabled ? "§aPearl Tracker: ON" : "§cPearl Tracker: OFF"), true);
        }
        if (!cfg.enabled) return;
        for (EnderPearlEntity pearl : c.world.getEntitiesByClass(EnderPearlEntity.class, p.getBoundingBox().expand(128),
                e -> true)) {
            Entity owner = pearl.getOwner();
            boolean mine = owner == p;
            if (mine && !cfg.own) continue;
            if (owner instanceof PlayerEntity pl && cfg.ignoreFriends && isFriend(pl)) continue;
            Track t = simulate(c.world, pearl, owner == null ? "?" : owner.getName().getString(), mine);
            if (t == null) continue;
            TRACKS.add(t);
            if (cfg.chat && ANNOUNCED.add(pearl.getUuid())) {
                p.sendMessage(Text.literal(String.format("§d%s §7pearl otdi: §f%.0f %.0f %.0f",
                        t.owner, t.landing.x, t.landing.y, t.landing.z)), false);
            }
        }
    }

    private static Track simulate(ClientWorld w, EnderPearlEntity pearl, String owner, boolean mine) {
        Vec3d pos = pearl.getEntityPos(), v = pearl.getVelocity();
        if (v.lengthSquared() < 1e-4) return null;
        List<Vec3d> path = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            Vec3d next = pos.add(v);
            BlockHitResult r = w.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, pearl));
            if (r.getType() == HitResult.Type.BLOCK) {
                path.add(r.getPos());
                return new Track(r.getPos(), path, i + 1, owner, mine);
            }
            path.add(next);
            pos = next;
            v = v.multiply(DRAG).add(0, -GRAVITY, 0);
            if (pos.y < w.getBottomY() - 16) return new Track(pos, path, i + 1, owner, mine);   // void'ga
        }
        return null;
    }

    // ------------------------------------------------------------ chizish
    private static void render(DrawContext ctx, RenderTickCounter counter) {
        MinecraftClient c = MinecraftClient.getInstance();
        PearlTrackerConfig cfg = PearlTrackerConfig.get();
        if (!cfg.enabled || c.player == null || c.options.hudHidden || TRACKS.isEmpty()) return;
        Projector pr = Projector.create(c, counter.getTickProgress(true), ctx.getScaledWindowWidth(),
                ctx.getScaledWindowHeight());
        double r = cfg.radiusTenths / 10.0;
        for (Track t : TRACKS) {
            int ring = t.mine() ? ownMarkColor(cfg) : markColor(cfg);
            int fill = (ring & 0x00FFFFFF) | 0x70000000;
            if (cfg.trajectory) {
                for (int i = 0; i < t.path.size(); i += 2) {
                    double[] s = pr.project(t.path.get(i));
                    if (s != null) ctx.fill((int) s[0] - 1, (int) s[1] - 1, (int) s[0] + 1, (int) s[1] + 1, 0xC0FF55FF);
                }
            }
            // tushadigan joy: to'ldirilgan doira (nuqtalardan) + qalin chegara - rangi sozlanadigan
            Vec3d l = t.landing.add(0, 0.05, 0);
            for (double dx = -r; dx <= r; dx += 0.25) {
                for (double dz = -r; dz <= r; dz += 0.25) {
                    if (dx * dx + dz * dz > r * r) continue;
                    double[] s = pr.project(l.add(dx, 0, dz));
                    if (s == null) continue;
                    ctx.fill((int) s[0] - 2, (int) s[1] - 2, (int) s[0] + 2, (int) s[1] + 2, fill);
                }
            }
            for (int a = 0; a < 48; a++) {
                double ang = a * Math.PI * 2 / 48;
                double[] s = pr.project(l.add(Math.cos(ang) * r, 0, Math.sin(ang) * r));
                if (s != null) ctx.fill((int) s[0] - 1, (int) s[1] - 1, (int) s[0] + 2, (int) s[1] + 2, ring);
            }
            double[] top = pr.project(l.add(0, 1.2, 0));
            if (top != null) {
                String label = String.format("⬇ %s%s §f%.1fs §7%.0fm", t.mine() ? "(men) " : "", t.owner,
                        t.ticks / 20.0, pr.distance(t.landing));
                int tw = c.textRenderer.getWidth(label);
                ctx.fill((int) top[0] - tw / 2 - 3, (int) top[1] - 11, (int) top[0] + tw / 2 + 3, (int) top[1], 0x90000000);
                ctx.drawTextWithShadow(c.textRenderer, label, (int) top[0] - tw / 2, (int) top[1] - 10, ring);
            }
        }
    }

    /** Dushman pearl'ining tushish joyi rangi (ARGB). */
    private static int markColor(PearlTrackerConfig cfg) {
        return palette(cfg.markColor, cfg.customR, cfg.customG, cfg.customB);
    }

    /** O'zingizning pearl'ingizning tushish joyi rangi - dushmanikidan farqlanishi uchun. */
    private static int ownMarkColor(PearlTrackerConfig cfg) {
        return palette(cfg.ownMarkColor, cfg.ownCustomR, cfg.ownCustomG, cfg.ownCustomB);
    }

    private static int palette(int choice, int customR, int customG, int customB) {
        return switch (Math.floorMod(choice, 5)) {
            case 1 -> 0xFF40FF40;   // yashil
            case 2 -> 0xFFFFD020;   // sariq
            case 3 -> 0xFF4080FF;   // ko'k
            case 4 -> 0xFF000000 | (customR << 16) | (customG << 8) | customB;
            default -> 0xFFFF2020;   // qizil
        };
    }

    private static boolean isFriend(PlayerEntity t) {
        String name = t.getName().getString();
        for (String f : AutoTntConfig.get().friends) if (f.equalsIgnoreCase(name)) return true;
        return false;
    }
}
