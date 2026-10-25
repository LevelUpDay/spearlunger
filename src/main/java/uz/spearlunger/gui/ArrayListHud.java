package uz.spearlunger.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;
import uz.spearlunger.Config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Meteor kabi: ekran chetida yoqilgan modullar ro'yxati (uzunidan qisqasiga). */
public final class ArrayListHud {
    public static final String[] POSITIONS = {"Yuqori chap", "Yuqori o'ng", "Pastki o'ng"};

    private ArrayListHud() {}

    public static void render(DrawContext ctx, RenderTickCounter counter) {
        MinecraftClient c = MinecraftClient.getInstance();
        Config cfg = Config.get();
        if (!cfg.arraylist || c.player == null || c.options.hudHidden || c.currentScreen instanceof ClickGuiScreen) return;
        List<String> names = new ArrayList<>();
        for (Module m : Modules.all()) if (m.toggleable() && m.isEnabled()) names.add(m.name);
        if (names.isEmpty()) return;
        names.sort(Comparator.comparingInt((String n) -> c.textRenderer.getWidth(n)).reversed());

        int pos = Math.floorMod(cfg.arraylistPos, POSITIONS.length);
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        int lineH = 11;
        int y = pos == 2 ? h - 4 - names.size() * lineH - 22 : (pos == 0 ? 32 : 102);
        long now = System.currentTimeMillis();
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i);
            int tw = c.textRenderer.getWidth(n);
            int color = cfg.arraylistRainbow
                    ? 0xFF000000 | MathHelper.hsvToRgb(((now / 20 + i * 18) % 360) / 360.0f, 0.6f, 1.0f)
                    : Theme.accent();
            boolean right = pos != 0;
            int x = right ? w - tw - 6 : 6;
            ctx.fill(x - 3, y, x + tw + 3, y + lineH, 0x90101014);
            // rangli chiziq chetida
            if (right) ctx.fill(x + tw + 3, y, x + tw + 5, y + lineH, color);
            else ctx.fill(x - 5, y, x - 3, y + lineH, color);
            ctx.drawTextWithShadow(c.textRenderer, n, x, y + 2, color);
            y += lineH;
        }
    }
}
