package uz.spearlunger.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Bitta modulning sozlamalari (o'ng tugma bilan ochiladi): yoqish, tugma tanlash va boshqa sozlamalar. */
public class ModuleScreen extends Screen {
    private static final int W = 250;
    private static final int HEADER_H = 20;
    private static final int ROW_H = 16;
    private static final int SLIDER_H = 24;
    private static final int GROUP_H = 15;

    private record Hit(Setting s, int x, int y, int w, int h) {}

    private final Module module;
    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private Setting.Key listening = null;
    private Setting.Int sliding = null;
    private int slideX, slideW;
    private double scroll = 0;
    private int maxScroll = 0;
    private int bodyTop, bodyBottom;

    public ModuleScreen(Module module, Screen parent) {
        super(Text.literal(module.name));
        this.module = module;
        this.parent = parent;
    }

    private int left() { return (width - W) / 2; }

    private int top() { return Math.max(10, height / 2 - 130); }

    private double mouseX() { return client.mouse.getScaledX(client.getWindow()); }

    private double mouseY() { return client.mouse.getScaledY(client.getWindow()); }

    private static int rowHeight(Setting s) {
        if (s instanceof Setting.Int) return SLIDER_H;
        if (s instanceof Setting.Group) return GROUP_H;
        return ROW_H;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int accent = Theme.accent();
        int x = left(), top = top();
        List<Setting> settings = module.settings();

        int content = 4;
        for (Setting s : settings) content += rowHeight(s);
        bodyTop = top + HEADER_H;
        int maxBody = height - 34 - bodyTop;
        int bodyH = Math.min(content, maxBody);
        bodyBottom = bodyTop + bodyH;
        maxScroll = Math.max(0, content - bodyH);
        scroll = MathHelper.clamp(scroll, 0, maxScroll);

        // oyna
        ctx.fill(x + 3, top + 3, x + W + 3, bodyBottom + 4, 0x40000000);
        ctx.fill(x, top, x + W, bodyTop, accent);
        ctx.drawTextWithShadow(textRenderer, "‹ Orqaga", x + 6, top + 6,
                inside(mouseX, mouseY, x, top, 54, HEADER_H) ? Theme.TEXT : 0xDDFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(module.name), x + W / 2, top + 6, Theme.TEXT);
        if (module.toggleable()) drawSwitch(ctx, x + W - 28, top + 5, module.isEnabled(), true);
        ctx.fill(x, bodyTop, x + W, bodyBottom + 1, Theme.PANEL_BG);
        ctx.fill(x, bodyBottom + 1, x + W, bodyBottom + 2, accent);

        hits.clear();
        Setting hovered = null;
        ctx.enableScissor(x, bodyTop, x + W, bodyBottom);
        int y = bodyTop + 2 - (int) scroll;
        for (Setting s : settings) {
            int h = rowHeight(s);
            boolean visible = y + h > bodyTop && y < bodyBottom;
            boolean hover = visible && inside(mouseX, mouseY, x, y, W, h) && mouseY >= bodyTop && mouseY < bodyBottom;
            if (hover && !(s instanceof Setting.Group)) {
                hovered = s;
                ctx.fill(x, y, x + W, y + h, Theme.ROW_HOVER);
            }
            if (visible) drawSetting(ctx, s, x, y, h, accent);
            hits.add(new Hit(s, x, y, W, h));
            y += h;
        }
        ctx.disableScissor();

        // aylantirish chizig'i
        if (maxScroll > 0) {
            int trackH = bodyBottom - bodyTop;
            int barH = Math.max(12, trackH * trackH / (trackH + maxScroll));
            int barY = bodyTop + (int) ((trackH - barH) * (scroll / maxScroll));
            ctx.fill(x + W - 2, barY, x + W, barY + barH, Theme.accent(0xAA));
        }

        String hint;
        if (listening != null) hint = "§eTugmani bosing §7(Esc - bekor, Backspace - tugmani o'chirish)";
        else if (hovered != null) hint = "§f" + hovered.desc;
        else hint = "Esc - orqaga";
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(hint), width / 2, bodyBottom + 8, Theme.TEXT_DIM);
    }

    private void drawSetting(DrawContext ctx, Setting s, int x, int y, int h, int accent) {
        int ty = y + (ROW_H - 8) / 2;
        int right = x + W - 8;
        if (s instanceof Setting.Group) {
            ctx.drawTextWithShadow(textRenderer, s.name.toUpperCase(), x + 6, y + 5, Theme.accent());
            int lx = x + 10 + textRenderer.getWidth(s.name.toUpperCase());
            ctx.fill(lx, y + 9, x + W - 6, y + 10, Theme.accent(0x60));
        } else if (s instanceof Setting.Bool b) {
            ctx.drawTextWithShadow(textRenderer, s.name, x + 8, ty, Theme.TEXT);
            drawSwitch(ctx, right - 18, y + 4, b.get.getAsBoolean(), false);
        } else if (s instanceof Setting.Int n) {
            int v = MathHelper.clamp(n.get.getAsInt(), n.min, n.max);
            ctx.drawTextWithShadow(textRenderer, s.name, x + 8, y + 3, Theme.TEXT);
            String val = n.fmt.apply(v);
            ctx.drawTextWithShadow(textRenderer, val, right - textRenderer.getWidth(val), y + 3, Theme.accent());
            int bx = x + 8, bw = W - 16, by = y + 15;
            double t = n.max == n.min ? 0 : (v - n.min) / (double) (n.max - n.min);
            int fx = bx + (int) Math.round(bw * t);
            ctx.fill(bx, by, bx + bw, by + 3, Theme.WIDGET_BG);
            ctx.fill(bx, by, fx, by + 3, accent);
            ctx.fill(fx - 2, by - 2, fx + 2, by + 5, Theme.TEXT);
        } else if (s instanceof Setting.Choice c) {
            ctx.drawTextWithShadow(textRenderer, s.name, x + 8, ty, Theme.TEXT);
            String val = "‹ " + c.values[Math.floorMod(c.get.getAsInt(), c.values.length)] + " ›";
            ctx.drawTextWithShadow(textRenderer, val, right - textRenderer.getWidth(val), ty, Theme.accent());
        } else if (s instanceof Setting.Key k) {
            ctx.drawTextWithShadow(textRenderer, s.name, x + 8, ty, Theme.TEXT);
            String val = k == listening ? "..." : keyName(k.kb);
            int vw = textRenderer.getWidth(val) + 8;
            ctx.fill(right - vw, y + 2, right, y + h - 2, k == listening ? Theme.accent(0x90) : Theme.WIDGET_BG);
            ctx.drawTextWithShadow(textRenderer, val, right - vw + 4, ty, k == listening ? Theme.TEXT : 0xFFE0E0E0);
        } else if (s instanceof Setting.Action) {
            ctx.fill(x + 8, y + 2, x + W - 8, y + h - 2, Theme.WIDGET_BG);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(s.name), x + W / 2, ty, Theme.TEXT);
        }
    }

    private void drawSwitch(DrawContext ctx, int x, int y, boolean on, boolean onAccent) {
        int bg = on ? (onAccent ? 0xFFFFFFFF : Theme.accent()) : Theme.WIDGET_BG;
        ctx.fill(x, y, x + 18, y + 9, bg);
        int kx = on ? x + 10 : x + 1;
        ctx.fill(kx, y + 1, kx + 7, y + 8, on && onAccent ? Theme.accent() : 0xFFE8E8E8);
    }

    private static String keyName(KeyBinding kb) {
        return kb.isUnbound() ? "Yo'q" : kb.getBoundKeyLocalizedText().getString();
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------ sichqoncha
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = mouseX(), my = mouseY();
        int button = click.button();
        int x = left(), top = top();
        if (listening != null) {
            listening = null;
            return true;
        }
        if (inside(mx, my, x, top, 60, HEADER_H)) {
            close();
            return true;
        }
        if (module.toggleable() && inside(mx, my, x + W - 34, top, 34, HEADER_H)) {
            module.toggle();
            return true;
        }
        if (my < bodyTop || my >= bodyBottom) return super.mouseClicked(click, doubled);
        for (Hit hit : hits) {
            if (!inside(mx, my, hit.x, hit.y, hit.w, hit.h)) continue;
            Setting s = hit.s;
            if (s instanceof Setting.Bool b) {
                b.set.accept(!b.get.getAsBoolean());
            } else if (s instanceof Setting.Int n) {
                sliding = n;
                slideX = hit.x + 8;
                slideW = W - 16;
                slide(mx);
            } else if (s instanceof Setting.Choice c) {
                int d = button == 1 ? -1 : 1;
                c.set.accept(Math.floorMod(c.get.getAsInt() + d, c.values.length));
            } else if (s instanceof Setting.Key k) {
                if (button == 1) bind(k.kb, InputUtil.UNKNOWN_KEY);
                else listening = k;
            } else if (s instanceof Setting.Action a) {
                a.run.run();
            } else {
                return true;
            }
            Modules.saveAll();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    private void slide(double mx) {
        if (sliding == null) return;
        double t = MathHelper.clamp((mx - slideX) / slideW, 0.0, 1.0);
        sliding.set.accept(sliding.min + (int) Math.round(t * (sliding.max - sliding.min)));
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (sliding != null) {
            slide(mouseX());
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (sliding != null) {
            sliding = null;
            Modules.saveAll();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = MathHelper.clamp(scroll - vertical * 18, 0, maxScroll);
        return true;
    }

    // ------------------------------------------------------------ klaviatura
    @Override
    public boolean keyPressed(KeyInput input) {
        if (listening != null) {
            int code = input.getKeycode();
            if (code == GLFW.GLFW_KEY_BACKSPACE || code == GLFW.GLFW_KEY_DELETE) bind(listening.kb, InputUtil.UNKNOWN_KEY);
            else if (code != GLFW.GLFW_KEY_ESCAPE) bind(listening.kb, InputUtil.fromKeyCode(input));
            listening = null;
            return true;
        }
        return super.keyPressed(input);
    }

    private void bind(KeyBinding kb, InputUtil.Key key) {
        kb.setBoundKey(key);
        KeyBinding.updateKeysByCode();
        client.options.write();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        Modules.saveAll();
        client.setScreen(parent);
    }
}
