package uz.autobridge;

import net.minecraft.block.Block;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EmptyBlockView;
import uz.spearlunger.InvHelper;
import uz.spearlunger.Rotations;

/**
 * Shift + yurish (W/A/S/D) tutilgan - "ko'prik qiladigan" turishda - oldingi qadam ostida joy yo'q bo'lsa,
 * hozirgi tik turgan blokning yon yuzasiga qarab yangi blok qo'yadi (klassik "ninja bridge"). Shift tufayli
 * vanilla o'zi chekkadan yiqilib ketishga yo'l qo'ymaydi, shuning uchun mod faqat kerakli blokni qo'yishga ulguradi.
 * Siz qo'yib yuborsangiz (shift/yurish to'xtasa) yoki oldinda devor (to'siq) bo'lsa - to'xtaydi.
 */
public class BridgeController {
    private enum Phase { WATCH, AIM }

    private Phase phase = Phase.WATCH;
    private BlockPos aimSupport = null;
    private Direction aimDir = null;
    private String status = null;
    private String lastWarn = null;

    public void tick(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (p == null || c.world == null || c.interactionManager == null) {
            phase = Phase.WATCH;
            return;
        }
        BridgeConfig cfg = BridgeConfig.get();
        while (AutoBridgeClient.keyToggle.wasPressed()) {
            cfg.enabled = !cfg.enabled;
            BridgeConfig.save();
            p.sendMessage(Text.literal(cfg.enabled ? "§aAuto Bridge: ON" : "§cAuto Bridge: OFF"), true);
        }
        if (!cfg.enabled || InvHelper.screenOpen(c) || !active(c)) {
            phase = Phase.WATCH;
            status = null;
            return;
        }

        Direction dir = travelDirection(c, p);
        if (dir == null) {
            status = null;
            return;
        }

        ClientWorld w = c.world;
        BlockPos feet = p.getBlockPos();
        BlockPos support = feet.down();
        BlockPos nextFoot = feet.offset(dir);
        BlockPos nextSupport = nextFoot.down();

        if (!solid(w, support)) {   // o'zimiz ham havodamiz - qila olmaymiz
            phase = Phase.WATCH;
            status = null;
            return;
        }
        if (blocked(w, nextFoot)) {   // devor - oldinda to'siq, o'tib bo'lmaydi
            phase = Phase.WATCH;
            if (cfg.messages) warnOnce(p, "§eAuto Bridge: oldinda devor - to'xtadi.");
            status = "§7to'siq (devor)";
            return;
        }
        if (solid(w, nextSupport)) {   // joy allaqachon bor - hech narsa kerak emas
            phase = Phase.WATCH;
            lastWarn = null;
            status = "§atayyor";
            return;
        }

        switch (Math.floorMod(cfg.mode, 3)) {
            case 1 -> modeSimple(c, p, support, dir, cfg);
            case 2 -> modeClassic(c, p, support, dir, cfg);
            default -> modeSilent(c, p, support, dir, cfg);
        }
    }

    // ------------------------------------------------------------ turlar
    private void modeSilent(MinecraftClient c, ClientPlayerEntity p, BlockPos support, Direction dir, BridgeConfig cfg) {
        if (place(c, p, support, dir, cfg, true)) status = "§aqo'yildi §7(tez)";
    }

    private void modeSimple(MinecraftClient c, ClientPlayerEntity p, BlockPos support, Direction dir, BridgeConfig cfg) {
        if (!looksBridgeStance(p, dir)) {
            status = "§7orqaga-pastga qarang...";
            return;
        }
        if (place(c, p, support, dir, cfg, false)) status = "§aqo'yildi §7(oddiy)";
    }

    private void modeClassic(MinecraftClient c, ClientPlayerEntity p, BlockPos support, Direction dir, BridgeConfig cfg) {
        if (phase == Phase.WATCH) {
            phase = Phase.AIM;
            aimSupport = support;
            aimDir = dir;
        }
        Vec3d hit = hitPoint(aimSupport, aimDir);
        Vec3d eye = p.getEyePos();
        boolean aimed = Rotations.turn(p, Rotations.yawTo(eye, hit), Rotations.pitchTo(eye, hit));
        if (!aimed) {
            status = "§7burilyapman...";
            return;
        }
        if (place(c, p, aimSupport, aimDir, cfg, false)) status = "§aqo'yildi §7(klassik)";
        phase = Phase.WATCH;
    }

    /** "Orqaga-pastga" turishda: pastga qaragan (pitch) va yo'nalishga qarama-qarshi tomonga qaragan (yaw). */
    private static boolean looksBridgeStance(ClientPlayerEntity p, Direction dir) {
        if (p.getPitch() < 40.0f) return false;
        Direction back = dir.getOpposite();
        float wantYaw = Rotations.yawTo(Vec3d.ZERO, new Vec3d(back.getOffsetX(), 0, back.getOffsetZ()));
        return Math.abs(MathHelper.wrapDegrees(wantYaw - p.getYaw())) < 70.0f;
    }

    // ------------------------------------------------------------ qo'yish
    private static Vec3d hitPoint(BlockPos against, Direction face) {
        return Vec3d.ofCenter(against).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
    }

    /** against blokining face tomoniga blok qo'yadi. silent = true: qarashni sendLook orqali yashirincha o'zgartirib qo'yadi. */
    private boolean place(MinecraftClient c, ClientPlayerEntity p, BlockPos against, Direction face, BridgeConfig cfg,
                          boolean silent) {
        int slot = blockSlot(p, cfg);
        if (slot < 0) {
            if (cfg.messages) warnOnce(p, "§cAuto Bridge: qurilish bloki tugadi.");
            status = "§cblok tugadi";
            return false;
        }
        lastWarn = null;
        Vec3d hit = hitPoint(against, face);
        int hotbar = slot;
        int[] borrowed = null;
        if (slot >= 9) {
            int sel = p.getInventory().getSelectedSlot();
            hotbar = sel == 8 ? 7 : 8;
            for (int h = 0; h < 9; h++) if (h != sel && p.getInventory().getStack(h).isEmpty()) { hotbar = h; break; }
            InvHelper.swap(c, p, slot, hotbar);
            borrowed = new int[] { slot, hotbar };
        }
        int original = p.getInventory().getSelectedSlot();
        float oy = p.getYaw(), op = p.getPitch();
        if (silent) {
            Vec3d eye = p.getEyePos();
            Rotations.sendLook(p, Rotations.yawTo(eye, hit), Rotations.pitchTo(eye, hit));
        }
        p.getInventory().setSelectedSlot(hotbar);
        c.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(hit, face, against, false));
        p.swingHand(Hand.MAIN_HAND);
        p.getInventory().setSelectedSlot(original);
        if (silent) Rotations.sendLook(p, oy, op);
        if (borrowed != null && InvHelper.returnItems()) InvHelper.swap(c, p, borrowed[0], borrowed[1]);
        return true;
    }

    /** Qurilish uchun blok: avval arzon bloklar, keyin har qanday to'liq oddiy blok (qimmatbaholarisiz). */
    private static int blockSlot(ClientPlayerEntity p, BridgeConfig cfg) {
        int limit = cfg.fromInventory ? 36 : 9;
        int any = -1;
        for (int i = 0; i < limit; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!(s.getItem() instanceof BlockItem bi)) continue;
            String id = Registries.ITEM.getId(s.getItem()).getPath();
            if (id.matches("cobblestone|cobbled_deepslate|dirt|netherrack|stone|andesite|diorite|granite|tuff"
                    + "|blackstone|end_stone|deepslate|.*_planks")) return i;
            if (any >= 0) continue;
            Block b = bi.getBlock();
            if (b instanceof BlockWithEntity || b instanceof FallingBlock) continue;
            if (id.matches(".*(diamond|emerald|netherite|gold|iron|obsidian|tnt|ancient|beacon|lapis|redstone"
                    + "|copper|amethyst|anchor|shulker|spawner).*")) continue;
            if (!b.getDefaultState().isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)) continue;
            any = i;
        }
        return any;
    }

    // ------------------------------------------------------------ yo'nalish va holat
    /** Shift bosilgan va yurish tugmalaridan kamida bittasi bosilgan - "ko'prik qilaman" turishi. */
    private static boolean active(MinecraftClient c) {
        GameOptions o = c.options;
        return o.sneakKey.isPressed()
                && (o.forwardKey.isPressed() || o.backKey.isPressed() || o.leftKey.isPressed() || o.rightKey.isPressed());
    }

    /** Bosilgan tugmalar + qarash (yaw) bo'yicha gorizontal harakat yo'nalishi (eng yaqin 4 tomondan biri). */
    private static Direction travelDirection(MinecraftClient c, ClientPlayerEntity p) {
        GameOptions o = c.options;
        double f = (o.forwardKey.isPressed() ? 1 : 0) - (o.backKey.isPressed() ? 1 : 0);
        double s = (o.rightKey.isPressed() ? 1 : 0) - (o.leftKey.isPressed() ? 1 : 0);
        if (f == 0 && s == 0) return null;
        double rad = Math.toRadians(p.getYaw());
        double sinYaw = Math.sin(rad), cosYaw = Math.cos(rad);
        double mx = f * -sinYaw + s * cosYaw;
        double mz = f * cosYaw + s * sinYaw;
        return Direction.getFacing(mx, 0, mz);
    }

    private static boolean solid(ClientWorld w, BlockPos pos) {
        return !w.getBlockState(pos).getCollisionShape(w, pos).isEmpty();
    }

    /** Devor: oyoq balandligida yoki bosh balandligida to'siq bo'lsa o'tib bo'lmaydi. */
    private static boolean blocked(ClientWorld w, BlockPos nextFoot) {
        return solid(w, nextFoot) || solid(w, nextFoot.up());
    }

    private void warnOnce(ClientPlayerEntity p, String msg) {
        if (!msg.equals(lastWarn)) {
            p.sendMessage(Text.literal(msg), true);
            lastWarn = msg;
        }
    }

    public String hudLine(MinecraftClient c) {
        BridgeConfig cfg = BridgeConfig.get();
        if (!cfg.enabled || !cfg.hud) return null;
        return status == null ? null : "§6Auto Bridge: " + status;
    }
}
