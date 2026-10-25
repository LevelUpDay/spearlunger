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
    /** O'zimiz ochgan inventarni necha tickdan keyin yopamiz (-1 = ochmaganmiz). */
    private static int closeIn = -1;
    private static InventoryScreen ours = null;

    private InvHelper() {}

    /** invSlot: PlayerScreenHandler slot raqami (9-35 inventar), hotbar: 0-8. */
    public static void swap(MinecraftClient c, ClientPlayerEntity p, int invSlot, int hotbar) {
        click(c, p, invSlot, hotbar, SlotActionType.SWAP);
    }

    /** Shift + bosish (QUICK_MOVE) - masalan kiyilgan zirhni inventarga olish (slot 5-8 = zirh). */
    public static void quickMove(MinecraftClient c, ClientPlayerEntity p, int slot) {
        click(c, p, slot, 0, SlotActionType.QUICK_MOVE);
    }

    private static void click(MinecraftClient c, ClientPlayerEntity p, int slot, int button, SlotActionType type) {
        Config.AntiCheat ac = Config.get().ac();
        if (ac.openInventory && c.currentScreen == null) {
            // E: inventar ochiladi va bir necha tick ochiq turadi (ko'rinadi)
            ours = new InventoryScreen(p);
            c.setScreen(ours);
            closeIn = Math.max(0, ac.invShowTicks);
        } else if (ours != null && c.currentScreen == ours) {
            closeIn = Math.max(closeIn, Math.max(0, ac.invShowTicks));   // yana narsa olinyapti - ochiq tursin
        }
        c.interactionManager.clickSlot(p.playerScreenHandler.syncId, slot, button, type, p);
        if (closeIn == 0) close(c);
    }

    /** Haqiqiy (o'yinchi ochgan) oyna ochiqmi - modul o'zi ochgan inventar hisobga olinmaydi. */
    public static boolean screenOpen(MinecraftClient c) {
        return c.currentScreen != null && c.currentScreen != ours;
    }

    /** Har tick (END_CLIENT_TICK): vaqti kelsa o'zimiz ochgan inventarni yopamiz. */
    public static void tick(MinecraftClient c) {
        if (closeIn < 0) return;
        if (ours == null || c.currentScreen != ours) {   // o'yinchi o'zi yopdi yoki boshqa oyna ochildi
            ours = null;
            closeIn = -1;
            return;
        }
        if (--closeIn <= 0) close(c);
    }

    private static void close(MinecraftClient c) {
        // close() yopish paketini ham yuboradi (vanilla E bilan yopgandek)
        if (ours != null && c.currentScreen == ours) ours.close();
        ours = null;
        closeIn = -1;
    }

    /** Olingan narsani eski joyiga qaytarish kerakmi (Anti-cheat: "Joyiga qaytarish"). */
    public static boolean returnItems() {
        return Config.get().ac().returnItems;
    }
}
