package uz.spearlunger;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import static uz.spearlunger.Widgets.button;
import static uz.spearlunger.Widgets.onoff;

/** Kamerani keskin burmaslik va boshqa "odamga o'xshash" sozlamalar (ikkala mod uchun). */
public class AntiCheatScreen extends Screen {
    private final Screen parent;

    public AntiCheatScreen(Screen parent) {
        super(Text.literal("Anti-cheat"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Config.AntiCheat ac = Config.get().ac();
        int w = 150, s = 22;
        int x1 = width / 2 - 155, x2 = width / 2 + 5;
        int y = height / 2 - 70;

        addDrawableChild(button(x1, y, w, () -> "Silliq burilish: " + onoff(ac.smoothRotation),
                () -> ac.smoothRotation = !ac.smoothRotation));
        addDrawableChild(new Widgets.IntSlider(x1, y + s, w, 5, 180, ac.maxTurnPerTick,
                v -> "Max burilish: " + v + "°/tick", v -> ac.maxTurnPerTick = v));
        addDrawableChild(new Widgets.IntSlider(x1, y + 2 * s, w, 0, 30, ac.turnJitter,
                v -> "Tasodifiy ±: " + v + "°", v -> ac.turnJitter = v));
        addDrawableChild(new Widgets.IntSlider(x1, y + 3 * s, w, 1, 30, ac.aimTolerance,
                v -> "Nishon aniqligi: " + v + "°", v -> ac.aimTolerance = v));
        addDrawableChild(button(x1, y + 4 * s, w, () -> "GCD (sichqoncha) fix: " + onoff(ac.gcdFix),
                () -> ac.gcdFix = !ac.gcdFix));

        addDrawableChild(new Widgets.IntSlider(x2, y, w, 0, 10, ac.randomDelayMax,
                v -> "Tasodifiy kechikish: 0-" + v + " t", v -> ac.randomDelayMax = v));
        addDrawableChild(button(x2, y + s, w, () -> "Mace: qarab urish: " + onoff(ac.requireLookAt),
                () -> ac.requireLookAt = !ac.requireLookAt));
        addDrawableChild(button(x2, y + 2 * s, w, () -> "Mace: 1 tick swap: " + onoff(ac.sameTickSwap),
                () -> ac.sameTickSwap = !ac.sameTickSwap));
        addDrawableChild(button(x2, y + 3 * s, w, () -> "TNT: blokka qarash: " + onoff(ac.rotateToBlocks),
                () -> ac.rotateToBlocks = !ac.rotateToBlocks));
        addDrawableChild(button(x2, y + 4 * s, w, () -> "TNT: kamerani qaytarish: " + onoff(ac.restoreView),
                () -> ac.restoreView = !ac.restoreView));

        addDrawableChild(ButtonWidget.builder(Text.literal("Tayyor"), b -> close())
                .dimensions(width / 2 - 100, y + 5 * s + 8, 200, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 95, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§7Kamera tez burilmasligi uchun: Silliq burilish ON, Max burilish 20-40°"),
                width / 2, height / 2 - 84, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        Config.save();
        client.setScreen(parent);
    }
}
