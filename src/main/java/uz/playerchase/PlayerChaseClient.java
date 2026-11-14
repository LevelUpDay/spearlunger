package uz.playerchase;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class PlayerChaseClient implements ClientModInitializer {
    public static KeyBinding keyChase;    // eng yaqin/pritseldagi o'yinchiga uchib boradi
    public static KeyBinding keyToggle;   // modulni yoqish/o'chirish (tugma berilmagan)
    public static final PlayerChaseController CONTROLLER = new PlayerChaseController();

    @Override
    public void onInitializeClient() {
        PlayerChaseConfig.load();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("playerchase", "main"));
        keyChase = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.playerchase.chase", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
        keyToggle = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.playerchase.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));

        ClientTickEvents.START_CLIENT_TICK.register(CONTROLLER::tick);
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                Identifier.of("playerchase", "status"), PlayerChaseClient::renderHud);
    }

    private static void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.options.hudHidden) return;
        String line = CONTROLLER.hudLine();
        if (line == null) return;
        int x = ctx.getScaledWindowWidth() - c.textRenderer.getWidth(line) - 6;
        ctx.drawTextWithShadow(c.textRenderer, Text.literal(line), x, 64, 0xFFFFFFFF);
    }
}
