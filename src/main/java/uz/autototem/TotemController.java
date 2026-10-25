package uz.autototem;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.Window;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;
import uz.autototem.mixin.HandledScreenAccessor;

import java.util.Random;

/**
 * Chap qo'lda totem bo'lmasa, inventardagi totemni chap qo'lga qo'yadi.
 *  0 - inventar: E bosilgandek inventarni ochadi, sichqonchani totemga olib boradi, F bosadi va yopadi;
 *  1 - tezkor: inventar ochilmaydi, bir zumda almashtiradi;
 *  2 - hotbar: hotbar'dagi totemni tanlab F bosadi (hotbar'da bo'lmasa 0-rejim).
 * Totem umuman bo'lmasa hech narsa qilmaydi (inventar ham ochilmaydi).
 */
public class TotemController {
    /** SWAP tugmasi 40 = chap qo'l (inventarda F). */
    private static final int OFFHAND_BUTTON = 40;
    private static final Random RNG = new Random();

    private enum Phase { IDLE, DELAY, OPEN, MOVE, CLOSE, HOTBAR_SWAP }

    private Phase phase = Phase.IDLE;
    private int timer = 0;
    private int cooldown = 0;
    private int moveStep = 0;
    private boolean openedByUs = false;
    private double startX, startY;
    private int originalSlot = -1;
    private boolean hadOffhandTotem = false;

    public void tick(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (p == null || c.world == null || c.interactionManager == null) {
            phase = Phase.IDLE;
            hadOffhandTotem = false;
            return;
        }
        TotemConfig cfg = TotemConfig.get();
        while (AutoTotemClient.keyToggle.wasPressed()) {
            cfg.enabled = !cfg.enabled;
            TotemConfig.save();
            p.sendMessage(Text.literal(cfg.enabled ? "§aAuto Totem: ON" : "§cAuto Totem: OFF"), true);
        }

        boolean offTotem = p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        if (hadOffhandTotem && !offTotem && phase == Phase.IDLE && cfg.popMessage && p.isAlive()) {
            say(p, "§6Totem ishlatildi! §7Qoldi: §f" + count(p));
        }
        hadOffhandTotem = offTotem;

        if (!cfg.enabled) {
            if (phase != Phase.IDLE) finish(c);
            return;
        }

        switch (phase) {
            case IDLE -> {
                if (cooldown > 0) { cooldown--; return; }
                if (offTotem || p.isSpectator() || !p.isAlive()) return;
                if (cfg.health > 0 && p.getHealth() + p.getAbsorptionAmount() > cfg.health) return;
                if (findTotem(p, cfg) < 0) return;   // totem yo'q - hech narsa qilmaymiz
                int lo = Math.max(0, Math.min(cfg.delayMin, cfg.delayMax));
                int hi = Math.max(cfg.delayMin, cfg.delayMax);
                timer = lo + (hi > lo ? RNG.nextInt(hi - lo + 1) : 0);
                phase = Phase.DELAY;
                tick(c);   // kechikish 0 bo'lsa shu tickda boshlaymiz
            }
            case DELAY -> {
                if (timer-- > 0) return;
                start(c, p, cfg);
            }
            case OPEN -> {
                if (!(c.currentScreen instanceof InventoryScreen)) { finish(c); return; }
                startX = c.mouse.getX();
                startY = c.mouse.getY();
                moveStep = 0;
                phase = Phase.MOVE;
                tick(c);
            }
            case MOVE -> tickMove(c, p, cfg);
            case CLOSE -> {
                if (timer-- > 0) return;
                if (openedByUs && c.currentScreen instanceof InventoryScreen) c.currentScreen.close();
                finish(c);
            }
            case HOTBAR_SWAP -> {
                // tanlangan slot oldingi tickda serverga yuborildi, endi F
                p.networkHandler.sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                if (originalSlot >= 0) p.getInventory().setSelectedSlot(originalSlot);
                originalSlot = -1;
                finish(c);
            }
        }
    }

    private void start(MinecraftClient c, ClientPlayerEntity p, TotemConfig cfg) {
        if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) { finish(c); return; }
        int slot = findTotem(p, cfg);
        if (slot < 0) { finish(c); return; }
        boolean free = c.currentScreen == null || c.currentScreen instanceof InventoryScreen;
        int mode = Math.floorMod(cfg.mode, 3);

        if (mode == 1) {
            if (free) swap(c, p, slot);
            finish(c);
            return;
        }
        if (mode == 2 && c.currentScreen == null) {
            int h = hotbarTotem(p);
            if (h >= 0) {
                originalSlot = p.getInventory().getSelectedSlot();
                p.getInventory().setSelectedSlot(h);
                phase = Phase.HOTBAR_SWAP;
                return;
            }
        }
        // 0-rejim: inventar orqali
        if (c.currentScreen == null) {
            c.setScreen(new InventoryScreen(p));
            openedByUs = true;
            phase = Phase.OPEN;
        } else if (c.currentScreen instanceof InventoryScreen) {
            openedByUs = false;
            phase = Phase.OPEN;
            tick(c);
        } else {
            // boshqa oyna ochiq (sandiq, chat...) - keyinroq urinamiz
            phase = Phase.IDLE;
            cooldown = 10;
        }
    }

    private void tickMove(MinecraftClient c, ClientPlayerEntity p, TotemConfig cfg) {
        if (!(c.currentScreen instanceof InventoryScreen screen)) { finish(c); return; }
        if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) { phase = Phase.CLOSE; timer = cfg.closeDelay; return; }
        int id = findTotem(p, cfg);
        if (id < 0) { phase = Phase.CLOSE; timer = 0; return; }
        Slot slot = screen.getScreenHandler().getSlot(id);
        HandledScreenAccessor acc = (HandledScreenAccessor) screen;
        double gx = acc.autototem$getX() + slot.x + 8;
        double gy = acc.autototem$getY() + slot.y + 8;

        Window win = c.getWindow();
        double sx = win.getWidth() / (double) win.getScaledWidth();
        double sy = win.getHeight() / (double) win.getScaledHeight();
        double tx = gx * sx, ty = gy * sy;
        int total = Math.max(0, cfg.moveTicks);
        moveStep++;
        double t = total == 0 ? 1.0 : Math.min(1.0, moveStep / (double) total);
        double e = t * t * (3 - 2 * t);   // silliq (smoothstep)
        double jx = t < 1.0 ? (RNG.nextDouble() - 0.5) * 4 * sx : 0;
        double jy = t < 1.0 ? (RNG.nextDouble() - 0.5) * 4 * sy : 0;
        GLFW.glfwSetCursorPos(win.getHandle(), startX + (tx - startX) * e + jx, startY + (ty - startY) * e + jy);
        if (t < 1.0) return;

        swap(c, p, id);
        timer = Math.max(0, cfg.closeDelay);
        phase = Phase.CLOSE;
        if (timer == 0) tick(c);
    }

    /** Inventardagi slotni chap qo'l bilan almashtiradi (inventarda F bosgandek). */
    private void swap(MinecraftClient c, ClientPlayerEntity p, int handlerSlot) {
        c.interactionManager.clickSlot(p.playerScreenHandler.syncId, handlerSlot, OFFHAND_BUTTON, SlotActionType.SWAP, p);
    }

    private void finish(MinecraftClient c) {
        if (originalSlot >= 0 && c.player != null) c.player.getInventory().setSelectedSlot(originalSlot);
        originalSlot = -1;
        phase = Phase.IDLE;
        openedByUs = false;
        cooldown = 5;
    }

    /** PlayerScreenHandler'dagi slot raqami: avval asosiy inventar (9-35), keyin hotbar (36-44). */
    private static int findTotem(ClientPlayerEntity p, TotemConfig cfg) {
        for (int i = 9; i < 36; i++) {
            if (p.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i;
        }
        if (cfg.fromHotbar) {
            int h = hotbarTotem(p);
            if (h >= 0) return 36 + h;
        }
        return -1;
    }

    private static int hotbarTotem(ClientPlayerEntity p) {
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i;
        }
        return -1;
    }

    static int count(ClientPlayerEntity p) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(Items.TOTEM_OF_UNDYING)) n += s.getCount();
        }
        if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) n++;
        return n;
    }

    public String hudLine(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        TotemConfig cfg = TotemConfig.get();
        if (p == null || !cfg.enabled || !cfg.hud) return null;
        int n = count(p);
        return "§6Totem: " + (n == 0 ? "§c0" : "§f" + n);
    }

    private void say(ClientPlayerEntity p, String msg) {
        if (TotemConfig.get().messages) p.sendMessage(Text.literal(msg), false);
    }
}
