package uz.spearlunger.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import uz.spearlunger.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Meteor uslubidagi menyu (O): har bir kategoriya - alohida panel.
 * Modul ustida chap tugma - yoqish/o'chirish, o'ng tugma - sozlamalar. Panel sarlavhasini sudrab ko'chirish mumkin.
 */
public class ClickGuiScreen extends Screen {
    static final int PANEL_W = 116;
    static final int HEADER_H = 18;
    static final int ROW_H = 15;

    private final Screen parent;
    private Module.Category dragging = null;
    private double dragDx, dragDy;

    public ClickGuiScreen(Screen parent) {
        super(Text.literal("ClickGUI"));
        this.parent = parent;
    }

    private List<Module> modules(Module.Category cat) {
        List<Module> l = new ArrayList<>();
        for (Module m : Modules.all()) if (m.category == cat) l.add(m);
        return l;
    }

    private int[] pos(Module.Category cat) {
        int[] p = Config.get().guiPanels.get(cat.name());
        if (p == null) {
            int i = cat.ordinal();
            int total = Module.Category.values().length * (PANEL_W + 10) - 10;
            p = new int[] { Math.max(4, (width - total) / 2) + i * (PANEL_W + 10), 30 };
        }
        // ekrandan chiqib ketmasin
        p[0] = Math.max(0, Math.min(width - PANEL_W, p[0]));
        p[1] = Math.max(0, Math.min(height - HEADER_H, p[1]));
        return p;
    }

    private double mouseX() { return client.mouse.getScaledX(client.getWindow()); }

    private double mouseY() { return client.mouse.getScaledY(client.getWindow()); }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int accent = Theme.accent();
        Module hovered = null;

        for (Module.Category cat : Module.Category.values()) {
            List<Module> mods = modules(cat);
            if (mods.isEmpty()) continue;
            int[] p = pos(cat);
            int x = p[0], y = p[1];
            int bodyH = mods.size() * ROW_H;

            // soya + fon
            ctx.fill(x + 2, y + 2, x + PANEL_W + 2, y + HEADER_H + bodyH + 4, 0x40000000);
            ctx.fill(x, y, x + PANEL_W, y + HEADER_H, accent);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(cat.title), x + PANEL_W / 2, y + 5, Theme.TEXT);
            ctx.fill(x, y + HEADER_H, x + PANEL_W, y + HEADER_H + bodyH + 2, Theme.PANEL_BG);

            int ry = y + HEADER_H + 1;
            for (Module m : mods) {
                boolean hover = mouseX >= x && mouseX < x + PANEL_W && mouseY >= ry && mouseY < ry + ROW_H;
                if (hover) hovered = m;
                if (m.isEnabled()) {
                    ctx.fill(x, ry, x + PANEL_W, ry + ROW_H, Theme.accent(0x55));
                    ctx.fill(x, ry, x + 2, ry + ROW_H, accent);
                }
                if (hover) ctx.fill(x, ry, x + PANEL_W, ry + ROW_H, Theme.ROW_HOVER);
                int color = m.toggleable() ? (m.isEnabled() ? Theme.TEXT : Theme.TEXT_OFF) : Theme.TEXT;
                ctx.drawTextWithShadow(textRenderer, m.name, x + 6, ry + 4, color);
                String arrow = m.toggleable() ? "..." : "›";
                ctx.drawTextWithShadow(textRenderer, arrow, x + PANEL_W - 6 - textRenderer.getWidth(arrow), ry + 4,
                        Theme.TEXT_DIM);
                ry += ROW_H;
            }
            ctx.fill(x, y + HEADER_H + bodyH + 2, x + PANEL_W, y + HEADER_H + bodyH + 3, accent);
        }

        int by = height - 14;
        if (hovered != null) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(hovered.desc), width / 2, by - 12, Theme.TEXT);
        }
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Chap tugma: yoqish/o'chirish  •  O'ng tugma: sozlamalar  •  Sarlavhani sudrang"),
                width / 2, by, Theme.TEXT_DIM);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = mouseX(), my = mouseY();
        int button = click.button();
        // ustma-ust tushsa eng ustidagi (oxirgi chizilgan) panel
        Module.Category[] cats = Module.Category.values();
        for (int i = cats.length - 1; i >= 0; i--) {
            Module.Category cat = cats[i];
            List<Module> mods = modules(cat);
            if (mods.isEmpty()) continue;
            int[] p = pos(cat);
            int x = p[0], y = p[1];
            if (mx < x || mx >= x + PANEL_W) continue;
            if (my >= y && my < y + HEADER_H) {
                if (button == 0) {
                    dragging = cat;
                    dragDx = mx - x;
                    dragDy = my - y;
                }
                return true;
            }
            int idx = (int) Math.floor((my - y - HEADER_H - 1) / ROW_H);
            if (my >= y + HEADER_H + 1 && idx >= 0 && idx < mods.size()) {
                Module m = mods.get(idx);
                if (button == 0 && m.toggleable()) m.toggle();
                else if (button == 0 || button == 1) client.setScreen(new ModuleScreen(m, this));
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragging != null) {
            Config.get().guiPanels.put(dragging.name(),
                    new int[] { (int) Math.round(mouseX() - dragDx), (int) Math.round(mouseY() - dragDy) });
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging != null) {
            dragging = null;
            Config.save();
            return true;
        }
        return super.mouseReleased(click);
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
