package uz.nametags;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;
import uz.autotnt.AutoTntConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Nametags+: o'yinchi ustida ism, jon, masofa, totem pop soni, zirh (protection darajasi, durability) va qo'lidagilar.
 * Vanilla ism va health indicator modlaridan yuqoriroqda (sozlanadi) HUD qatlamida chiziladi.
 */
public class NametagsClient implements ClientModInitializer {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET};
    public static KeyBinding keyToggle;

    @Override
    public void onInitializeClient() {
        NametagsConfig.load();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("nametags", "main"));
        keyToggle = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.nametags.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (c.world == null) TotemPops.clear();
            while (keyToggle.wasPressed()) {
                NametagsConfig cfg = NametagsConfig.get();
                cfg.enabled = !cfg.enabled;
                NametagsConfig.save();
                if (c.player != null) {
                    c.player.sendMessage(Text.literal(cfg.enabled ? "§aNametags+: ON" : "§cNametags+: OFF"), true);
                }
            }
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of("nametags", "tags"),
                NametagsClient::render);
    }

    // ------------------------------------------------------------ chizish
    private static void render(DrawContext ctx, RenderTickCounter counter) {
        MinecraftClient c = MinecraftClient.getInstance();
        NametagsConfig cfg = NametagsConfig.get();
        if (!cfg.enabled || c.player == null || c.world == null || c.options.hudHidden) return;
        float td = counter.getTickProgress(true);

        Camera cam = c.gameRenderer.getCamera();
        float yaw = cam.getYaw(), pitch = cam.getPitch();
        Vec3d f = Vec3d.fromPolar(pitch, yaw);
        Vec3d eye = c.player.getCameraPosVec(td);
        Vec3d camPos = cam.isThirdPerson() ? eye.subtract(f.multiply(4.0)) : eye;
        Vec3d right = new Vec3d(-f.z, 0, f.x).normalize();
        Vec3d up = right.crossProduct(f).normalize();
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        double focal = (h / 2.0) / Math.tan(Math.toRadians(c.options.getFov().getValue()) / 2.0);

        List<AbstractClientPlayerEntity> players = new ArrayList<>();
        for (AbstractClientPlayerEntity pl : c.world.getPlayers()) {
            if (pl == c.player || !pl.isAlive() || (pl.isInvisible() && !cfg.invisible)) continue;
            if (pl.distanceTo(c.player) > cfg.range) continue;
            players.add(pl);
        }
        // uzoqdagilar avval, yaqindagilar ustidan chiziladi
        players.sort(Comparator.comparingDouble(pl -> -pl.squaredDistanceTo(c.player)));

        for (AbstractClientPlayerEntity pl : players) {
            Vec3d pos = pl.getLerpedPos(td).add(0, pl.getHeight() + cfg.heightTenths / 10.0, 0);
            Vec3d rel = pos.subtract(camPos);
            double z = rel.dotProduct(f);
            if (z < 0.2) continue;   // orqamizda
            double sx = w / 2.0 + rel.dotProduct(right) / z * focal;
            double sy = h / 2.0 - rel.dotProduct(up) / z * focal;
            if (sx < -100 || sx > w + 100 || sy < -100 || sy > h + 100) continue;
            double dist = pl.distanceTo(c.player);
            float s = (float) (cfg.scale / 100.0 * MathHelper.clamp(1.3 - dist / 40.0, 0.6, 1.0));
            drawTag(ctx, c, pl, (float) sx, (float) sy, s, dist, cfg);
        }
    }

    private static void drawTag(DrawContext ctx, MinecraftClient c, AbstractClientPlayerEntity pl, float x, float y,
                                float s, double dist, NametagsConfig cfg) {
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(s, s);

        // ---- matn qatori
        StringBuilder line = new StringBuilder();
        if (cfg.name) line.append(isFriend(pl) ? "§a" : "§f").append(pl.getName().getString());
        if (cfg.health) {
            float hp = pl.getHealth() + pl.getAbsorptionAmount();
            String col = hp > 14 ? "§a" : hp > 7 ? "§e" : "§c";
            line.append(" ").append(col).append(String.format("%.1f❤", hp / 2.0f));
        }
        if (cfg.distance) line.append(String.format(" §7%.0fm", dist));
        int pops = TotemPops.get(pl.getUuid());
        if (cfg.totemPops && pops > 0) line.append(" §6-").append(pops).append(" totem");
        String text = line.toString().trim();
        int tw = c.textRenderer.getWidth(text);
        if (!text.isEmpty()) {
            ctx.fill(-tw / 2 - 3, -11, tw / 2 + 3, 0, 0x90000000);
            ctx.drawTextWithShadow(c.textRenderer, text, -tw / 2, -10, 0xFFFFFFFF);
        }

        // ---- itemlar qatori: asosiy qo'l, zirh (boshdan oyoqqa), chap qo'l
        List<ItemStack> row = new ArrayList<>();
        if (cfg.hands) row.add(pl.getMainHandStack());
        if (cfg.armor) for (EquipmentSlot slot : ARMOR) row.add(pl.getEquippedStack(slot));
        if (cfg.hands) row.add(pl.getOffHandStack());
        row.removeIf(ItemStack::isEmpty);
        if (!row.isEmpty()) {
            int rw = row.size() * 18;
            int ix = -rw / 2 + 1, iy = text.isEmpty() ? -18 : -30;
            for (ItemStack st : row) {
                ctx.drawItem(st, ix, iy);
                if (cfg.durability) ctx.drawStackOverlay(c.textRenderer, st, ix, iy);
                String ench = cfg.protection ? enchLabel(st) : null;
                if (ench != null) drawSmall(ctx, c, ench, ix + 8, iy - 6, 0xFFFFFFFF);
                if (cfg.durability && st.isDamageable()) {
                    int pct = (int) Math.round(100.0 * (st.getMaxDamage() - st.getDamage()) / st.getMaxDamage());
                    int col = pct > 50 ? 0xFF55FF55 : pct > 20 ? 0xFFFFFF55 : 0xFFFF5555;
                    drawSmall(ctx, c, pct + "%", ix + 8, iy + 17, col);
                }
                ix += 18;
            }
        }
        m.popMatrix();
    }

    /** Yarim o'lchamda, markazlashtirilgan matn. */
    private static void drawSmall(DrawContext ctx, MinecraftClient c, String s, int cx, int y, int color) {
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx, y);
        m.scale(0.5f, 0.5f);
        ctx.drawTextWithShadow(c.textRenderer, s, -c.textRenderer.getWidth(s) / 2, 0, color);
        m.popMatrix();
    }

    /** Zirh: eng kuchli himoya (P4, B4, F4, PP4); qurol: Sharpness/Density/Breach kabi asosiylari. */
    private static String enchLabel(ItemStack st) {
        ItemEnchantmentsComponent comp = st.getEnchantments();
        String best = null;
        int bestLvl = 0;
        for (RegistryEntry<Enchantment> e : comp.getEnchantments()) {
            String path = e.getKey().map(k -> k.getValue().getPath()).orElse("");
            String tag = switch (path) {
                case "protection" -> "P";
                case "blast_protection" -> "B";
                case "fire_protection" -> "F";
                case "projectile_protection" -> "PP";
                case "sharpness" -> "S";
                case "density" -> "D";
                case "breach" -> "Br";
                case "power" -> "Pw";
                case "lunge" -> "L";
                default -> null;
            };
            if (tag == null) continue;
            int lvl = comp.getLevel(e);
            // oddiy protection'ni afzal ko'ramiz
            int score = lvl * 10 + (tag.equals("P") || tag.equals("S") ? 5 : 0);
            if (score > bestLvl) {
                bestLvl = score;
                best = tag + lvl;
            }
        }
        return best;
    }

    private static boolean isFriend(AbstractClientPlayerEntity pl) {
        String name = pl.getName().getString();
        for (String f : AutoTntConfig.get().friends) if (f.equalsIgnoreCase(name)) return true;
        return false;
    }
}
