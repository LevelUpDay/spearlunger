package uz.spearlunger.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2fStack;
import uz.spearlunger.Config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Meteor kabi: ekran chetida yoqilgan modullar ro'yxati (uzunidan qisqasiga). */
public final class ArrayListHud {
    public static final String[] POSITIONS = {"Yuqori chap", "Yuqori o'ng", "Pastki o'ng"};

    private ArrayListHud() {}

    /** O'lchov (hudScale) qo'llangan qator balandligi. */
    public static int lineHeight() {
        return Math.round(11 * scale());
    }

    public static float scale() {
        return MathHelper.clamp(Config.get().hudScale, 25, 300) / 100.0f;
    }

    /** Ro'yxatning (chap yoki o'ng qirrasiga qaramay) chap-yuqori burchagi - ClickGUI'da sudrash uchun ham ishlatiladi. */
    public static int[] anchor(MinecraftClient c, int w, int h, int rowCount, int maxTw) {
        Config cfg = Config.get();
        if (cfg.arraylistCustomPos) return new int[] { cfg.arraylistX, cfg.arraylistY };
        int pos = Math.floorMod(cfg.arraylistPos, POSITIONS.length);
        int lineH = lineHeight();
        int y = pos == 2 ? h - 4 - rowCount * lineH - 22 : (pos == 0 ? 32 : 102);
        int x = pos == 0 ? 6 : w - maxTw - 6;
        return new int[] { x, y };
    }

    public static void render(DrawContext ctx, RenderTickCounter counter) {
        MinecraftClient c = MinecraftClient.getInstance();
        Config cfg = Config.get();
        if (!cfg.arraylist || c.player == null || c.options.hudHidden || c.currentScreen instanceof ClickGuiScreen) return;
        List<String> names = new ArrayList<>();
        for (Module m : Modules.all()) if (m.toggleable() && m.isEnabled()) names.add(m.name);
        if (names.isEmpty()) return;
        names.sort(Comparator.comparingInt((String n) -> c.textRenderer.getWidth(n)).reversed());

        float scale = scale();
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        int lineH = lineHeight();
        int maxTw = 0;
        for (String n : names) maxTw = Math.max(maxTw, Math.round(c.textRenderer.getWidth(n) * scale));
        boolean right = !cfg.arraylistCustomPos && Math.floorMod(cfg.arraylistPos, POSITIONS.length) != 0;
        int[] a = anchor(c, w, h, names.size(), maxTw);
        int x0 = a[0], y = a[1];

        long now = System.currentTimeMillis();
        Matrix3x2fStack m = ctx.getMatrices();
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i);
            int tw = Math.round(c.textRenderer.getWidth(n) * scale);
            int color = cfg.arraylistRainbow
                    ? 0xFF000000 | MathHelper.hsvToRgb(((now / 20 + i * 18) % 360) / 360.0f, 0.6f, 1.0f)
                    : Theme.accent();
            int x = right ? x0 + maxTw - tw : x0;
            m.pushMatrix();
            m.translate(x, y);
            m.scale(scale, scale);
            int lw = c.textRenderer.getWidth(n);
            ctx.fill(-3, 0, lw + 3, 11, 0x90101014);
            // rangli chiziq chetida
            if (right) ctx.fill(lw + 3, 0, lw + 5, 11, color);
            else ctx.fill(-5, 0, -3, 11, color);
            ctx.drawTextWithShadow(c.textRenderer, n, 0, 2, color);
            m.popMatrix();
            y += lineH;
        }
    }
}
