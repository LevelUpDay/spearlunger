package uz.spearlunger;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import static uz.spearlunger.Widgets.button;
import static uz.spearlunger.Widgets.onoff;

public class SettingsScreen extends Screen {
    private static final String[] TARGET = {"Eng yaqin", "Qarash bo'yicha"};
    private final Screen parent;

    public SettingsScreen(Screen parent) {
        super(Text.literal("Spear Lunger"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Config c = Config.get();
        int w = 150, s = 22;
        int x1 = width / 2 - 155, x2 = width / 2 + 5;
        int y = height / 2 - 105;

        // chap ustun: raqamlar
        addDrawableChild(new Widgets.IntSlider(x1, y, w, 0, 40, c.extraDelayTicks,
                v -> "Qo'sh. kechikish: " + v + " tick", v -> c.extraDelayTicks = v));
        addDrawableChild(new Widgets.IntSlider(x1, y + s, w, 0, 20, c.minFood,
                v -> v == 0 ? "Min ochlik: o'chiq" : "Min ochlik: " + v, v -> c.minFood = v));
        addDrawableChild(new Widgets.IntSlider(x1, y + 2 * s, w, 0, 100, c.minDurability,
                v -> v == 0 ? "Min durability: o'chiq" : "Min durability: " + v, v -> c.minDurability = v));
        addDrawableChild(new Widgets.IntSlider(x1, y + 3 * s, w, 5, 200, c.rangeHorizontal,
                v -> "X gorizontal: " + v, v -> c.rangeHorizontal = v));
        addDrawableChild(new Widgets.IntSlider(x1, y + 4 * s, w, 5, 400, c.rangeVertical,
                v -> "X vertikal: " + v, v -> c.rangeVertical = v));
        addDrawableChild(new Widgets.IntSlider(x1, y + 5 * s, w, 20, 60, (int) Math.round(c.maceReach * 10),
                v -> "X mace masofasi: " + (v / 10.0), v -> c.maceReach = v / 10.0));
        addDrawableChild(new Widgets.IntSlider(x1, y + 6 * s, w, 40, 600, c.targetTimeoutTicks,
                v -> "X vaqt limiti: " + v + " tick", v -> c.targetTimeoutTicks = v));

        // o'ng ustun: tugmalar
        addDrawableChild(button(x2, y, w, () -> "R: " + (c.holdToggle ? "bosib yoqish" : "bosib turish"),
                () -> c.holdToggle = !c.holdToggle));
        addDrawableChild(button(x2, y + s, w, () -> "Avto-ovqat: " + onoff(c.autoEat), () -> c.autoEat = !c.autoEat));
        addDrawableChild(button(x2, y + 2 * s, w, () -> "Oltin olma yeyish: " + onoff(c.eatGoldenApples),
                () -> c.eatGoldenApples = !c.eatGoldenApples));
        addDrawableChild(button(x2, y + 3 * s, w, () -> "HUD: " + onoff(c.hud), () -> c.hud = !c.hud));
        addDrawableChild(button(x2, y + 4 * s, w, () -> "X nishon: " + TARGET[Math.floorMod(c.targetSelect, 2)],
                () -> c.targetSelect = (c.targetSelect + 1) % 2));
        addDrawableChild(button(x2, y + 5 * s, w, () -> "X moblar ham: " + onoff(c.targetMobs),
                () -> c.targetMobs = !c.targetMobs));
        addDrawableChild(button(x2, y + 6 * s, w, () -> "X faqat havoda: " + onoff(c.targetRequireAir),
                () -> c.targetRequireAir = !c.targetRequireAir));
        addDrawableChild(button(x2, y + 7 * s, w, () -> "X wind charge: " + onoff(c.windChargeJump),
                () -> c.windChargeJump = !c.windChargeJump));
        addDrawableChild(button(x2, y + 8 * s, w, () -> "X gorizontal qarash: " + onoff(c.flattenPitch),
                () -> c.flattenPitch = !c.flattenPitch));

        addDrawableChild(ButtonWidget.builder(Text.literal("Tayyor"), b -> close())
                .dimensions(width / 2 - 100, y + 9 * s + 4, 200, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 120, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        Config.save();
        client.setScreen(parent);
    }
}
