package uz.spearlunger;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import uz.spearlunger.gui.ArrayListHud;

public class SpearLungerClient implements ClientModInitializer {
    public static KeyBinding keyHold;      // R - auto lunge (bosib turish yoki yoqish/o'chirish)
    public static KeyBinding keyTarget;    // X - eng yaqin o'yinchiga lunge + mace
    public static KeyBinding keySettings;  // O - ClickGUI menyusi
    public static KeyBinding keyToggle;    // modulni yoqish/o'chirish (tugma berilmagan)
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
        keyToggle = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.spearlunger.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
        ClientTickEvents.START_CLIENT_TICK.register(CONTROLLER::tick);
        ClientTickEvents.END_CLIENT_TICK.register(InvHelper::tick);
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                Identifier.of("spearlunger", "status"), SpearHud::render);
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                Identifier.of("spearlunger", "arraylist"), ArrayListHud::render);
    }
}
