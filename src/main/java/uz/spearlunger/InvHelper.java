package uz.spearlunger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Inventar <-> hotbar almashtirish. Ko'p serverlar inventar ochilmasdan qilingan bosishlarni qaytaradi, shuning
 * uchun (Anti-cheat: "Inventarni ochib olish") vanilla'dagidek qilinadi: E (inventar ochiladi), raqam tugmasi
 * (SWAP), inventar yopiladi - yopish paketi serverga ketadi.
 */
public final class InvHelper {
    private InvHelper() {}

    /** invSlot: PlayerScreenHandler slot raqami (9-35 inventar), hotbar: 0-8. */
    public static void swap(MinecraftClient c, ClientPlayerEntity p, int invSlot, int hotbar) {
        boolean open = Config.get().ac().openInventory && c.currentScreen == null;
        if (open) c.setScreen(new InventoryScreen(p));
        c.interactionManager.clickSlot(p.playerScreenHandler.syncId, invSlot, hotbar, SlotActionType.SWAP, p);
        // close() yopish paketini ham yuboradi (vanilla E bilan yopgandek)
        if (open && c.currentScreen instanceof InventoryScreen s) s.close();
    }

    /** Shift + bosish (QUICK_MOVE) - masalan kiyilgan zirhni inventarga olish (slot 5-8 = zirh). */
    public static void quickMove(MinecraftClient c, ClientPlayerEntity p, int slot) {
        boolean open = Config.get().ac().openInventory && c.currentScreen == null;
        if (open) c.setScreen(new InventoryScreen(p));
        c.interactionManager.clickSlot(p.playerScreenHandler.syncId, slot, 0, SlotActionType.QUICK_MOVE, p);
        if (open && c.currentScreen instanceof InventoryScreen s) s.close();
    }

    /** Olingan narsani eski joyiga qaytarish kerakmi (Anti-cheat: "Joyiga qaytarish"). */
    public static boolean returnItems() {
        return Config.get().ac().returnItems;
    }
}
