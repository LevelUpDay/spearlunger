package uz.autoclutch;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class AutoClutchClient implements ClientModInitializer {
    public static KeyBinding keyToggle;   // tugma berilmagan, ClickGUI'da sozlanadi
    public static final ClutchController CONTROLLER = new ClutchController();

    @Override
    public void onInitializeClient() {
        ClutchConfig.load();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("autoclutch", "main"));
        keyToggle = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.autoclutch.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
        ClientTickEvents.START_CLIENT_TICK.register(CONTROLLER::tick);
    }
}
