package uz.fastplace;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.BlockItem;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;
import uz.spearlunger.mixin.MinecraftClientAccessor;

/**
 * Fast Place: o'ng tugmani bosib turganda bloklar vanilla'dagi 4 tick o'rniga har 1..4 tickda qo'yiladi,
 * xohlasangiz bir tickda bir nechta (wind charge bilan tepaga ko'tarilish, bedwars ko'prik).
 */
public class FastPlaceClient implements ClientModInitializer {
    /** Vanilla doItemUse() dan keyin qo'yadigan kechikish. */
    private static final int VANILLA_COOLDOWN = 4;
    public static KeyBinding keyToggle;

    @Override
    public void onInitializeClient() {
        FastPlaceConfig.load();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("fastplace", "main"));
        keyToggle = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.fastplace.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
        // vanilla shu tickda ishlatib bo'lgach (handleInputEvents) ishlaymiz
        ClientTickEvents.END_CLIENT_TICK.register(FastPlaceClient::tick);
    }

    private static void tick(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (p == null || c.interactionManager == null) return;
        FastPlaceConfig cfg = FastPlaceConfig.get();
        while (keyToggle.wasPressed()) {
            cfg.enabled = !cfg.enabled;
            FastPlaceConfig.save();
            p.sendMessage(Text.literal(cfg.enabled ? "§aFast Place: ON" : "§cFast Place: OFF"), true);
        }
        if (!cfg.enabled || c.currentScreen != null || !c.options.useKey.isPressed() || p.isUsingItem()) return;
        Hand hand = blockHand(p);
        if (cfg.onlyBlocks && hand == null) return;

        MinecraftClientAccessor acc = (MinecraftClientAccessor) c;
        // vanilla aynan shu tickda ishlatdi (kechikishni 4 ga qo'ydi)
        if (acc.fastplace$getItemUseCooldown() != VANILLA_COOLDOWN) return;

        if (hand != null) {
            for (int i = 1; i < Math.max(1, cfg.perTick); i++) {
                HitResult hr = p.raycast(p.getBlockInteractionRange(), 1.0f, false);
                if (hr.getType() != HitResult.Type.BLOCK || !(p.getStackInHand(hand).getItem() instanceof BlockItem)) break;
                ActionResult r = c.interactionManager.interactBlock(p, hand, (BlockHitResult) hr);
                if (!r.isAccepted()) break;
                p.swingHand(hand);
            }
        }
        acc.fastplace$setItemUseCooldown(Math.max(1, Math.min(VANILLA_COOLDOWN, cfg.delay)));
    }

    private static Hand blockHand(ClientPlayerEntity p) {
        if (p.getMainHandStack().getItem() instanceof BlockItem) return Hand.MAIN_HAND;
        if (p.getOffHandStack().getItem() instanceof BlockItem) return Hand.OFF_HAND;
        return null;
    }
}
