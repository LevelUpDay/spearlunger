package uz.spearlunger;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class SpearLungerClient implements ClientModInitializer {
    public static KeyBinding keyHold;      // R - bosib turilganda auto lunge
    public static KeyBinding keyTarget;    // X - eng yaqin o'yinchiga lunge + mace
    public static KeyBinding keySettings;  // O - sozlamalar oynasi
    public static final LungeController CONTROLLER = new LungeController();

    @Override
    public void onInitializeClient() {
        Config.load();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("spearlunger", "main"));
        keyHold = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.spearlunger.hold", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, cat));
        keyTarget = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.spearlunger.target", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, cat));
        keySettings = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.spearlunger.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, cat));
        ClientTickEvents.START_CLIENT_TICK.register(CONTROLLER::tick);
    }
}
