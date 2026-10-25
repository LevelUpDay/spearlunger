package uz.macecombo;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class MaceComboClient implements ClientModInitializer {
    public static KeyBinding keyCombo;    // J - bitta kombinatsiya (yana bossangiz bekor)
    public static final MaceComboController CONTROLLER = new MaceComboController();

    @Override
    public void onInitializeClient() {
        MaceComboConfig.load();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("macecombo", "main"));
        keyCombo = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.macecombo.combo", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, cat));
        ClientTickEvents.START_CLIENT_TICK.register(CONTROLLER::tick);
    }
}
