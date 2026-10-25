package uz.autoelytra;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class AutoElytraClient implements ClientModInitializer {
    public static KeyBinding keyToggle;   // tugma berilmagan, Controls'da sozlanadi
    public static final ElytraController CONTROLLER = new ElytraController();

    @Override
    public void onInitializeClient() {
        ElytraConfig.load();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("autoelytra", "main"));
        keyToggle = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.autoelytra.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));

        ClientTickEvents.START_CLIENT_TICK.register(CONTROLLER::tick);
        UseItemCallback.EVENT.register(CONTROLLER::onUseItem);
        // blokka qarab firework bossangiz ham (aks holda firework yerga otilib ketadi)
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> CONTROLLER.onUseItem(player, world, hand));

        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                Identifier.of("autoelytra", "status"), AutoElytraClient::renderHud);
    }

    private static void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.options.hudHidden) return;
        String line = CONTROLLER.hudLine(c);
        if (line == null) return;
        int x = ctx.getScaledWindowWidth() - c.textRenderer.getWidth(line) - 6;
        ctx.drawTextWithShadow(c.textRenderer, Text.literal(line), x, 18, 0xFFFFFFFF);
    }
}
