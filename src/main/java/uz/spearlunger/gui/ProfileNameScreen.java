package uz.spearlunger.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** "+" bosilganda: yangi profil nomini so'raydi (Enter - yaratish, Esc - bekor qilish). */
public class ProfileNameScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget field;

    public ProfileNameScreen(Screen parent) {
        super(Text.literal("Yangi profil"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        field = new TextFieldWidget(textRenderer, width / 2 - 100, height / 2 - 10, 200, 20, Text.literal("Nomi"));
        field.setPlaceholder(Text.literal("§7Masalan: Bedwars, PvP..."));
        field.setMaxLength(24);
        addDrawableChild(field);
        setInitialFocus(field);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Yangi profil nomi"), width / 2, height / 2 - 30,
                Theme.TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§7Enter - yaratish (bo'sh, hammasi o'chiq)  •  Esc - bekor"),
                width / 2, height / 2 + 16, Theme.TEXT_DIM);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int code = input.getKeycode();
        if (code == GLFW.GLFW_KEY_ENTER || code == GLFW.GLFW_KEY_KP_ENTER) {
            create();
            return true;
        }
        return super.keyPressed(input);
    }

    private void create() {
        String name = field.getText().trim();
        if (!name.isEmpty()) Modules.createProfile(name);
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
