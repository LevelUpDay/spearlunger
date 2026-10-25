package uz.autopvp;

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

public class AutoPvpClient implements ClientModInitializer {
    public static KeyBinding keyToggle;   // K - yoqish/o'chirish
    public static final PvpController CONTROLLER = new PvpController();

    @Override
    public void onInitializeClient() {
        PvpConfig.load();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("autopvp", "main"));
        keyToggle = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.autopvp.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, cat));

        ClientTickEvents.START_CLIENT_TICK.register(CONTROLLER::tick);
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                Identifier.of("autopvp", "status"), AutoPvpClient::renderHud);
    }

    private static void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.options.hudHidden) return;
        String line = CONTROLLER.hudLine(c);
        if (line == null) return;
        int x = ctx.getScaledWindowWidth() - c.textRenderer.getWidth(line) - 6;
        ctx.drawTextWithShadow(c.textRenderer, Text.literal(line), x, 90, 0xFFFFFFFF);
    }
}
