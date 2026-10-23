package uz.spearlunger;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import uz.autotnt.AutoTntConfig;
import uz.autotnt.TntSettingsScreen;

/** Ikkala mod va anti-cheat sozlamalari uchun umumiy menyu (O tugmasi). */
public class MainScreen extends Screen {
    private final Screen parent;

    public MainScreen(Screen parent) {
        super(Text.literal("Spear Lunger + Auto TNT"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 50;
        addDrawableChild(ButtonWidget.builder(Text.literal("Spear Lunger"), b -> client.setScreen(new SettingsScreen(this)))
                .dimensions(x, y, 200, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Auto TNT Cart"), b -> client.setScreen(new TntSettingsScreen(this)))
                .dimensions(x, y + 24, 200, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Anti-cheat"), b -> client.setScreen(new AntiCheatScreen(this)))
                .dimensions(x, y + 48, 200, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Tayyor"), b -> close())
                .dimensions(x, y + 80, 200, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 70, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        Config.save();
        AutoTntConfig.save();
        client.setScreen(parent);
    }
}
