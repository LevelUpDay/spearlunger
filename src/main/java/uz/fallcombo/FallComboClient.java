package uz.fallcombo;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class FallComboClient implements ClientModInitializer {
    public static KeyBinding keyToggle;   // tugma berilmagan, ClickGUI'da sozlanadi
    public static final FallComboController CONTROLLER = new FallComboController();

    @Override
    public void onInitializeClient() {
        FallComboConfig.load();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("fallcombo", "main"));
        keyToggle = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.fallcombo.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));

        ClientTickEvents.START_CLIENT_TICK.register(CONTROLLER::tick);
    }
}
