package uz.autotnt;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class AutoTntClient implements ClientModInitializer {
    public static KeyBinding keyBow;       // B - bow rejimi on/off
    public static KeyBinding keyCrossbow;  // Z - crossbow: bir marta hammasini qiladi
    public static KeyBinding keySettings;  // P - sozlamalar
    public static KeyBinding keyProfile;   // V - profilni almashtirish
    public static final TntController CONTROLLER = new TntController();

    @Override
    public void onInitializeClient() {
        AutoTntConfig.load();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("autotnt", "main"));
        keyBow = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.autotnt.bow", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, cat));
        keyCrossbow = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.autotnt.crossbow", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, cat));
        keySettings = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.autotnt.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, cat));
        keyProfile = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.autotnt.profile", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, cat));

        ClientTickEvents.START_CLIENT_TICK.register(CONTROLLER::tick);

        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                Identifier.of("autotnt", "status"), TntHud::render);

        // /tntfriend add|remove|list
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("tntfriend")
                        .then(ClientCommandManager.literal("add")
                                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String n = StringArgumentType.getString(ctx, "name");
                                            AutoTntConfig cfg = AutoTntConfig.get();
                                            if (cfg.friends.stream().noneMatch(f -> f.equalsIgnoreCase(n))) cfg.friends.add(n);
                                            AutoTntConfig.save();
                                            ctx.getSource().sendFeedback(Text.literal("§aDo'st qo'shildi: " + n));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String n = StringArgumentType.getString(ctx, "name");
                                            AutoTntConfig cfg = AutoTntConfig.get();
                                            cfg.friends.removeIf(f -> f.equalsIgnoreCase(n));
                                            AutoTntConfig.save();
                                            ctx.getSource().sendFeedback(Text.literal("§eDo'st o'chirildi: " + n));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("list")
                                .executes(ctx -> {
                                    AutoTntConfig cfg = AutoTntConfig.get();
                                    ctx.getSource().sendFeedback(Text.literal("§7Do'stlar: "
                                            + (cfg.friends.isEmpty() ? "(yo'q)" : String.join(", ", cfg.friends))));
                                    return 1;
                                }))));
    }
}
