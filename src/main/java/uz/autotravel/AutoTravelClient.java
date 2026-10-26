package uz.autotravel;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

public class AutoTravelClient implements ClientModInitializer {
    public static KeyBinding keyToggle;   // tugma berilmagan, ClickGUI'da sozlanadi
    public static final TravelController CONTROLLER = new TravelController();

    @Override
    public void onInitializeClient() {
        TravelConfig.load();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("autotravel", "main"));
        keyToggle = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.autotravel.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));

        // /goto <x> <z> | /goto <x> <y> <z> | /goto stop
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("goto")
                        .then(ClientCommandManager.literal("stop").executes(ctx -> {
                            CONTROLLER.stop(MinecraftClient.getInstance(), "§eAuto Travel: to'xtatildi");
                            return 1;
                        }))
                        .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                                .then(ClientCommandManager.argument("y_yoki_z", IntegerArgumentType.integer())
                                        .executes(ctx -> {
                                            int x = IntegerArgumentType.getInteger(ctx, "x");
                                            int z = IntegerArgumentType.getInteger(ctx, "y_yoki_z");
                                            go(new BlockPos(x, 0, z), false);
                                            return 1;
                                        })
                                        .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> {
                                                    int x = IntegerArgumentType.getInteger(ctx, "x");
                                                    int y = IntegerArgumentType.getInteger(ctx, "y_yoki_z");
                                                    int z = IntegerArgumentType.getInteger(ctx, "z");
                                                    go(new BlockPos(x, y, z), true);
                                                    return 1;
                                                }))))));

        ClientTickEvents.START_CLIENT_TICK.register(CONTROLLER::tick);
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                Identifier.of("autotravel", "status"), AutoTravelClient::renderHud);
    }

    /** Buyruq chat ochiq paytida bajariladi - keyingi tickda (chat yopilgach) boshlaymiz. */
    private static void go(BlockPos goal, boolean hasY) {
        MinecraftClient c = MinecraftClient.getInstance();
        c.execute(() -> CONTROLLER.start(c, goal, hasY));
    }

    private static void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.options.hudHidden) return;
        String line = CONTROLLER.hudLine();
        if (line == null) return;
        int x = ctx.getScaledWindowWidth() - c.textRenderer.getWidth(line) - 6;
        ctx.drawTextWithShadow(c.textRenderer, Text.literal(line), x, 126, 0xFFFFFFFF);
    }
}
