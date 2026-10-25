package uz.autototem;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import uz.spearlunger.Widgets;

import static uz.spearlunger.Widgets.button;
import static uz.spearlunger.Widgets.onoff;

public class TotemScreen extends Screen {
    private static final String[] MODE = {"Inventar (E + F)", "Tezkor (ochmasdan)", "Hotbar (F)"};
    private final Screen parent;

    public TotemScreen(Screen parent) {
        super(Text.literal("Auto Totem"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        TotemConfig c = TotemConfig.get();
        int w = 150, s = 22;
        int x1 = width / 2 - 155, x2 = width / 2 + 5;
        int y = height / 2 - 75;

        addDrawableChild(button(x1, y, w, () -> "Auto Totem: " + onoff(c.enabled), () -> c.enabled = !c.enabled));
        addDrawableChild(button(x1, y + s, w, () -> "Turi: " + MODE[Math.floorMod(c.mode, 3)],
                () -> c.mode = (Math.floorMod(c.mode, 3) + 1) % 3));
        addDrawableChild(new Widgets.IntSlider(x1, y + 2 * s, w, 0, 20, c.health,
                v -> v == 0 ? "Jon: har doim" : "Jon <= " + (v / 2.0) + " yurak", v -> c.health = v));
        addDrawableChild(button(x1, y + 3 * s, w, () -> "Hotbar'dan ham: " + onoff(c.fromHotbar),
                () -> c.fromHotbar = !c.fromHotbar));
        addDrawableChild(button(x1, y + 4 * s, w, () -> "Pop xabari: " + onoff(c.popMessage),
                () -> c.popMessage = !c.popMessage));
        addDrawableChild(button(x1, y + 5 * s, w, () -> "HUD: " + onoff(c.hud), () -> c.hud = !c.hud));

        addDrawableChild(new Widgets.IntSlider(x2, y, w, 0, 10, c.delayMin,
                v -> "Kechikish min: " + v + " tick", v -> c.delayMin = v));
        addDrawableChild(new Widgets.IntSlider(x2, y + s, w, 0, 10, c.delayMax,
                v -> "Kechikish max: " + v + " tick", v -> c.delayMax = v));
        addDrawableChild(new Widgets.IntSlider(x2, y + 2 * s, w, 0, 10, c.moveTicks,
                v -> v == 0 ? "Sichqoncha: bir zumda" : "Sichqoncha: " + v + " tick", v -> c.moveTicks = v));
        addDrawableChild(new Widgets.IntSlider(x2, y + 3 * s, w, 0, 10, c.closeDelay,
                v -> "Yopishdan oldin: " + v + " tick", v -> c.closeDelay = v));
        addDrawableChild(button(x2, y + 4 * s, w, () -> "Xabarlar: " + onoff(c.messages), () -> c.messages = !c.messages));

        addDrawableChild(ButtonWidget.builder(Text.literal("Tayyor"), b -> close())
                .dimensions(width / 2 - 100, y + 6 * s + 6, 200, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 100, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§7Chap qo'lda totem bo'lmasa qo'yadi. Totem yo'q bo'lsa inventar ochilmaydi."),
                width / 2, height / 2 - 89, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        TotemConfig.save();
        client.setScreen(parent);
    }
}
