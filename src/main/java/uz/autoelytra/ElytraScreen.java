package uz.autoelytra;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import uz.spearlunger.Widgets;

import static uz.spearlunger.Widgets.button;
import static uz.spearlunger.Widgets.onoff;

public class ElytraScreen extends Screen {
    private final Screen parent;

    public ElytraScreen(Screen parent) {
        super(Text.literal("Auto Elytra"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ElytraConfig c = ElytraConfig.get();
        int w = 150, s = 22;
        int x1 = width / 2 - 155, x2 = width / 2 + 5;
        int y = height / 2 - 80;

        addDrawableChild(button(x1, y, w, () -> "Auto Elytra: " + onoff(c.enabled), () -> c.enabled = !c.enabled));
        addDrawableChild(button(x1, y + s, w, () -> "Inventardan olish: " + onoff(c.fromInventory),
                () -> c.fromInventory = !c.fromInventory));
        addDrawableChild(button(x1, y + 2 * s, w, () -> "Yerda sakrash: " + onoff(c.autoJump),
                () -> c.autoJump = !c.autoJump));
        addDrawableChild(button(x1, y + 3 * s, w, () -> "Elytra'ni ochish: " + onoff(c.autoDeploy),
                () -> c.autoDeploy = !c.autoDeploy));
        addDrawableChild(button(x1, y + 4 * s, w, () -> "Firework ishlatish: " + onoff(c.autoFirework),
                () -> c.autoFirework = !c.autoFirework));
        addDrawableChild(button(x1, y + 5 * s, w, () -> "Xabarlar: " + onoff(c.messages), () -> c.messages = !c.messages));

        addDrawableChild(button(x2, y, w, () -> "Qo'nganda nagrudnik: " + onoff(c.restoreChest),
                () -> c.restoreChest = !c.restoreChest));
        addDrawableChild(new Widgets.IntSlider(x2, y + s, w, 0, 40, c.landDelayTicks,
                v -> "Qo'ngach kutish: " + v + " tick", v -> c.landDelayTicks = v));
        addDrawableChild(button(x2, y + 2 * s, w, () -> "Sinayotganini almashtirish: " + onoff(c.autoReplace),
                () -> c.autoReplace = !c.autoReplace));
        addDrawableChild(new Widgets.IntSlider(x2, y + 3 * s, w, 0, 100, c.minDurability,
                v -> "Min durability: " + v, v -> c.minDurability = v));
        addDrawableChild(new Widgets.IntSlider(x2, y + 4 * s, w, 0, 100, c.warnDurability,
                v -> v == 0 ? "Ogohlantirish: o'chiq" : "Ogohlantirish: " + v, v -> c.warnDurability = v));
        addDrawableChild(new Widgets.IntSlider(x2, y + 5 * s, w, 0, 10, c.delayTicks,
                v -> "Kechikish: " + v + " tick", v -> c.delayTicks = v));
        addDrawableChild(button(x2, y + 6 * s, w, () -> "HUD: " + onoff(c.hud), () -> c.hud = !c.hud));

        addDrawableChild(ButtonWidget.builder(Text.literal("Tayyor"), b -> close())
                .dimensions(width / 2 - 100, y + 7 * s + 6, 200, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 105, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§7Firework'ni bosing: elytra'ni o'zi kiyadi, ochadi va firework'ga qaytadi"),
                width / 2, height / 2 - 94, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        ElytraConfig.save();
        client.setScreen(parent);
    }
}
