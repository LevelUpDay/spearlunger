package uz.norender;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class NoRenderClient implements ClientModInitializer {
    public static KeyBinding keyToggle;

    @Override
    public void onInitializeClient() {
        NoRenderConfig.load();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("norender", "main"));
        keyToggle = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.norender.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            while (keyToggle.wasPressed()) {
                NoRenderConfig cfg = NoRenderConfig.get();
                cfg.enabled = !cfg.enabled;
                NoRenderConfig.save();
                if (c.player != null) c.player.sendMessage(Text.literal(cfg.enabled ? "§aNoRender: ON" : "§cNoRender: OFF"), true);
            }
        });
    }
}
