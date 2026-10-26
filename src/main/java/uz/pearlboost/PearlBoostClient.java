package uz.pearlboost;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class PearlBoostClient implements ClientModInitializer {
    public static KeyBinding keyBoost;    // H - pearl + wind charge (yana bossangiz bekor)
    public static KeyBinding keyChase;    // Pearl Chase yoqish/o'chirish (tugma berilmagan)
    public static final PearlBoostController CONTROLLER = new PearlBoostController();

    @Override
    public void onInitializeClient() {
        PearlBoostConfig.load();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("pearlboost", "main"));
        keyBoost = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.pearlboost.boost", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, cat));
        keyChase = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.pearlboost.chase", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
        ClientTickEvents.START_CLIENT_TICK.register(CONTROLLER::tick);
    }
}
