package uz.spearlunger.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Itemlarni tanlash: qidiruv + barcha itemlar to'ri, bosilgani ro'yxatga qo'shiladi/olib tashlanadi. */
public class ItemPickerScreen extends Screen {
    private static final int CELL = 20;

    private final List<String> ids;
    private final Screen parent;
    private TextFieldWidget search;
    private boolean onlySelected = false;
    private double scroll = 0;
    private int maxScroll = 0;
    private List<Item> shown = new ArrayList<>();
    private int gridX, gridY, cols, rows;

    public ItemPickerScreen(String title, List<String> ids, Screen parent) {
        super(Text.literal(title));
        this.ids = ids;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = Math.min(width - 20, 360);
        cols = w / CELL;
        gridX = (width - cols * CELL) / 2;
        gridY = 56;
        rows = Math.max(1, (height - gridY - 30) / CELL);
        search = new TextFieldWidget(textRenderer, gridX, 30, cols * CELL - 150, 18, Text.literal("Qidirish"));
        search.setPlaceholder(Text.literal("§7Qidirish..."));
        search.setChangedListener(s -> scroll = 0);
        addDrawableChild(search);
        setInitialFocus(search);
    }

    private void refresh() {
        String q = search == null ? "" : search.getText().toLowerCase(Locale.ROOT).trim();
        List<Item> l = new ArrayList<>();
        for (Item it : Registries.ITEM) {
            if (it == Items.AIR) continue;
            String id = Registries.ITEM.getId(it).toString();
            if (onlySelected && !ids.contains(id)) continue;
            if (!q.isEmpty() && !id.contains(q) && !new ItemStack(it).getName().getString().toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            l.add(it);
        }
        shown = l;
        int totalRows = (l.size() + cols - 1) / cols;
        maxScroll = Math.max(0, totalRows - rows);
        scroll = MathHelper.clamp(scroll, 0, maxScroll);
    }

    private double mouseX() { return client.mouse.getScaledX(client.getWindow()); }

    private double mouseY() { return client.mouse.getScaledY(client.getWindow()); }

    private int btnX() { return gridX + cols * CELL - 146; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        refresh();
        super.render(ctx, mouseX, mouseY, delta);
        int accent = Theme.accent();
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(title.getString() + " §7(" + ids.size() + " ta tanlangan)"),
                width / 2, 12, Theme.TEXT);

        // tugmalar: faqat tanlanganlar / tozalash
        int bx = btnX();
        ctx.fill(bx, 30, bx + 90, 48, onlySelected ? accent : Theme.WIDGET_BG);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Tanlanganlar"), bx + 45, 35, Theme.TEXT);
        ctx.fill(bx + 94, 30, bx + 146, 48, Theme.WIDGET_BG);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Tozalash"), bx + 120, 35, Theme.TEXT);

        ctx.fill(gridX - 2, gridY - 2, gridX + cols * CELL + 2, gridY + rows * CELL + 2, Theme.PANEL_BG);
        Item hovered = null;
        int start = (int) scroll * cols;
        for (int i = 0; i < rows * cols && start + i < shown.size(); i++) {
            Item it = shown.get(start + i);
            int cx = gridX + (i % cols) * CELL, cy = gridY + (i / cols) * CELL;
            boolean sel = ids.contains(Registries.ITEM.getId(it).toString());
            boolean hover = mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL;
            if (sel) ctx.fill(cx, cy, cx + CELL, cy + CELL, Theme.accent(0x90));
            if (hover) {
                ctx.fill(cx, cy, cx + CELL, cy + CELL, Theme.ROW_HOVER);
                hovered = it;
            }
            ctx.drawItem(new ItemStack(it), cx + 2, cy + 2);
        }
        String hint = hovered != null
                ? new ItemStack(hovered).getName().getString() + " §7" + Registries.ITEM.getId(hovered)
                : "§7Bosing - tanlash/olib tashlash  •  G'ildirak - aylantirish  •  Esc - orqaga";
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(hint), width / 2, gridY + rows * CELL + 8, Theme.TEXT);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = mouseX(), my = mouseY();
        int bx = btnX();
        if (my >= 30 && my < 48) {
            if (mx >= bx && mx < bx + 90) {
                onlySelected = !onlySelected;
                scroll = 0;
                return true;
            }
            if (mx >= bx + 94 && mx < bx + 146) {
                ids.clear();
                Modules.saveAll();
                return true;
            }
        }
        if (mx >= gridX && mx < gridX + cols * CELL && my >= gridY && my < gridY + rows * CELL) {
            int idx = (int) scroll * cols + (int) ((my - gridY) / CELL) * cols + (int) ((mx - gridX) / CELL);
            if (idx < shown.size()) {
                String id = Registries.ITEM.getId(shown.get(idx)).toString();
                if (!ids.remove(id)) ids.add(id);
                Modules.saveAll();
            }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = MathHelper.clamp(scroll - Math.signum(vertical), 0, maxScroll);
        return true;
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
