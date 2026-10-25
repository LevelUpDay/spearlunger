package uz.anchorguard;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class AnchorGuardClient implements ClientModInitializer {
    public static KeyBinding keyToggle;   // tugma berilmagan, ClickGUI'da sozlanadi
    public static final AnchorGuardController CONTROLLER = new AnchorGuardController();

    @Override
    public void onInitializeClient() {
        AnchorGuardConfig.load();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("anchorguard", "main"));
        keyToggle = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.anchorguard.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
        ClientTickEvents.START_CLIENT_TICK.register(CONTROLLER::tick);
        UseBlockCallback.EVENT.register(CONTROLLER::onUseBlock);
    }
}
