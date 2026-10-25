package uz.pearlclutch;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class PearlClutchClient implements ClientModInitializer {
    public static KeyBinding keyToggle;   // tugma berilmagan, ClickGUI'da sozlanadi
    public static final PearlController CONTROLLER = new PearlController();

    @Override
    public void onInitializeClient() {
        PearlConfig.load();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("pearlclutch", "main"));
        keyToggle = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.pearlclutch.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
        ClientTickEvents.START_CLIENT_TICK.register(CONTROLLER::tick);
        UseItemCallback.EVENT.register(CONTROLLER::onUseItem);
    }
}
