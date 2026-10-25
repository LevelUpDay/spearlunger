package uz.cheststealer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.AbstractChestBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Sandiq (yoki shulker) ochilganda faqat tanlangan itemlarni o'zi oladi (shift + bosish kabi). */
public class ChestStealerClient implements ClientModInitializer {
    public static KeyBinding keyToggle;

    /** Sandiq bosilgandan keyin oyna shuncha tick ichida ochilsa haqiqiy sandiq. */
    private static final int OPEN_WINDOW = 40;

    private static Screen current = null;
    private static long ticks = 0;
    private static long lastContainerUse = Long.MIN_VALUE / 2;
    private static BlockPos lastContainerPos = null;
    private static int timer = 0;
    private static boolean paused = false;

    @Override
    public void onInitializeClient() {
        StealerConfig.load();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("cheststealer", "main"));
        keyToggle = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.cheststealer.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
        ClientTickEvents.END_CLIENT_TICK.register(ChestStealerClient::tick);
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (player instanceof ClientPlayerEntity && isContainer(world.getBlockState(hit.getBlockPos()).getBlock())) {
                lastContainerUse = ticks;
                lastContainerPos = hit.getBlockPos();
            }
            return ActionResult.PASS;
        });
    }

    public static boolean matches(ItemStack s) {
        if (s.isEmpty()) return false;
        StealerConfig cfg = StealerConfig.get();
        int mode = Math.floorMod(cfg.mode, 3);
        if (mode == 1) return true;
        boolean listed = cfg.items.contains(Registries.ITEM.getId(s.getItem()).toString());
        return mode == 0 ? listed : !listed;
    }

    private static boolean isContainer(Block b) {
        return b instanceof AbstractChestBlock || b instanceof BarrelBlock || b instanceof ShulkerBoxBlock;
    }

    /** Oyna haqiqiy sandiqdanmi: yaqinda sandiq bloki bosilgan va u hali yonimizda. */
    private static boolean fromRealContainer(ClientPlayerEntity p) {
        return lastContainerPos != null && ticks - lastContainerUse <= OPEN_WINDOW
                && p.squaredDistanceTo(Vec3d.ofCenter(lastContainerPos)) <= 8 * 8;
    }

    private static void tick(MinecraftClient c) {
        ticks++;
        ClientPlayerEntity p = c.player;
        if (p == null || c.interactionManager == null) return;
        StealerConfig cfg = StealerConfig.get();
        while (keyToggle.wasPressed()) {
            cfg.enabled = !cfg.enabled;
            StealerConfig.save();
            p.sendMessage(Text.literal(cfg.enabled ? "§aChest Stealer: ON" : "§cChest Stealer: OFF"), true);
        }
        if (!(c.currentScreen instanceof HandledScreen<?> hs) || !cfg.enabled) {
            current = null;
            return;
        }
        ScreenHandler h = hs.getScreenHandler();
        boolean chest = h instanceof GenericContainerScreenHandler || (cfg.shulkers && h instanceof ShulkerBoxScreenHandler);
        if (!chest) return;
        if (current != hs) {
            // yangi oyna: server menyusi bo'lsa (sandiq bosilmagan) tegmaymiz
            current = hs;
            timer = Math.max(0, cfg.openDelay);
            paused = (cfg.shiftPause && InputUtil.isKeyPressed(c.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT))
                    || (cfg.realOnly && !fromRealContainer(p));
            lastContainerPos = null;   // bitta bosish - bitta oyna
        }
        if (paused) return;
        if (timer > 0) {
            timer--;
            return;
        }
        boolean took = false;
        for (Slot slot : h.slots) {
            if (slot.inventory == p.getInventory() || !matches(slot.getStack())) continue;
            int before = slot.getStack().getCount();
            c.interactionManager.clickSlot(h.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, p);
            if (slot.getStack().getCount() == before) continue;   // inventar to'la
            took = true;
            if (cfg.delay > 0) {
                timer = cfg.delay;
                return;
            }
        }
        if (!took && cfg.autoClose) hs.close();
        if (!took) paused = true;   // olinadigan narsa qolmadi
    }
}
