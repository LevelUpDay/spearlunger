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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EmptyBlockView;
import uz.spearlunger.InvHelper;
import uz.spearlunger.Rotations;

import java.util.Random;

/**
 * Yoqilganda (tugma yoki ClickGUI) o'zi harakatga tushadi: hozir qarab turgan tomonni (4 tomondan eng yaqinini)
 * yo'nalish qilib oladi va shu tomonga devorga yetguncha ko'prik quradi - keyin o'zi o'chadi. Qo'lda o'chirsangiz:
 * yerda bo'lsangiz darhol to'xtaydi, havoda (ko'prik ustida sakrab/tushib turgan paytda) bo'lsangiz avval
 * tagingizga (topilgan birinchi mos tomonga qarab) blok qo'yib, xavfsiz tushgandan keyin to'xtaydi.
 * Harakatni haqiqiy klaviatura tugmalarini (forward/back/sneak/jump) bosib turish orqali boshqaramiz -
 * xuddi odam o'zi bosgandek (uz.autotravel'dagi kabi usul).
 */
public class BridgeController {
    private enum State { IDLE, RUNNING, SAFE_STOP }

    private static final Random RNG = new Random();

    private State state = State.IDLE;
    private Direction dir = Direction.NORTH;
    private int blocksPlaced = 0;
    private int jumpEvery = 6;
    private boolean jumpPending = false;
    private int sneakReleaseTicks = 0;
    private int witchPauseTicks = 0;
    private int safeStopTicks = 0;
    private String status = null;
    private String lastWarn = null;

    public void tick(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        BridgeConfig cfg = BridgeConfig.get();
        while (AutoBridgeClient.keyToggle.wasPressed()) {
            cfg.enabled = !cfg.enabled;
            BridgeConfig.save();
            if (p != null) p.sendMessage(Text.literal(cfg.enabled ? "§aAuto Bridge: ON" : "§cAuto Bridge: OFF"), true);
        }
        if (p == null || c.world == null || c.interactionManager == null) {
            state = State.IDLE;
            status = null;
            return;
        }
        if (InvHelper.screenOpen(c)) {
            if (state != State.IDLE) release(c);
            state = State.IDLE;
            status = null;
            return;
        }

        if (cfg.enabled && state == State.IDLE) start(p);
        if (!cfg.enabled && state == State.RUNNING) requestStop(c, p);

        switch (state) {
            case RUNNING -> tickRunning(c, p, cfg);
            case SAFE_STOP -> tickSafeStop(c, p, cfg);
            default -> status = null;
        }
    }

    // ------------------------------------------------------------ boshlash / to'xtash
    private void start(ClientPlayerEntity p) {
        dir = lockedDirection(p);
        state = State.RUNNING;
        blocksPlaced = 0;
        jumpPending = false;
        sneakReleaseTicks = 0;
        witchPauseTicks = 0;
        lastWarn = null;
        BridgeStyle st = style(BridgeConfig.get());
        jumpEvery = switch (st) {
            case SPEED, JUMP -> 1;
            case DOUBLE_GOD -> 2;
            default -> 6;
        };
        status = "§6" + st.title + " boshlandi";
    }

    /** O'chirilganda: yerda bo'lsangiz darhol, havoda bo'lsangiz xavfsiz to'xtashga o'tadi. */
    private void requestStop(MinecraftClient c, ClientPlayerEntity p) {
        if (p.isOnGround()) {
            state = State.IDLE;
            release(c);
            status = null;
        } else {
            state = State.SAFE_STOP;
            safeStopTicks = 0;
            status = "§eto'xtash (havoda)...";
        }
    }

    private void finishAtWall(MinecraftClient c, ClientPlayerEntity p) {
        BridgeConfig cfg = BridgeConfig.get();
        cfg.enabled = false;
        BridgeConfig.save();
        if (cfg.messages) p.sendMessage(Text.literal("§aAuto Bridge: devorga yetdi - to'xtadi."), true);
        state = State.IDLE;
        release(c);
        status = "§7devor - tugadi";
    }

    // ------------------------------------------------------------ asosiy tsikl
    private void tickRunning(MinecraftClient c, ClientPlayerEntity p, BridgeConfig cfg) {
        BridgeStyle st = style(cfg);
        if (st == BridgeStyle.JUMP) {
            tickJump(c, p, cfg);
            return;
        }

        ClientWorld w = c.world;
        BlockPos feet = p.getBlockPos();
        BlockPos support = feet.down();
        BlockPos nextFoot = feet.offset(dir);
        BlockPos nextSupport = nextFoot.down();

        drive(c, p, st);

        if (!solid(w, support)) {
            status = "§7havoda...";
            return;
        }
        if (blocked(w, nextFoot)) {
            finishAtWall(c, p);
            return;
        }
        if (solid(w, nextSupport)) {
            status = "§atayyor";
            lastWarn = null;
            return;
        }

        styleBehavior(c, p, cfg, st, support);
    }

    private void tickJump(MinecraftClient c, ClientPlayerEntity p, BridgeConfig cfg) {
        ClientWorld w = c.world;
        BlockPos nextFoot = p.getBlockPos().offset(dir);
        if (blocked(w, nextFoot)) {
            finishAtWall(c, p);
            return;
        }
        GameOptions o = c.options;
        o.forwardKey.setPressed(true);
        p.setSprinting(true);
        maybeJump(c, p);
        Rotations.turn(p, yawOf(dir), 35.0f);
        if (placeBelowSelf(c, p, cfg)) placed(cfg);
        status = p.isOnGround() ? "§atayyor (sakrab)" : "§7havoda...";
    }

    /** Qarash va blok qo'yish: uslubga qarab oldinga-pastga yoki orqaga-pastga qaraydi. */
    private void styleBehavior(MinecraftClient c, ClientPlayerEntity p, BridgeConfig cfg, BridgeStyle st, BlockPos support) {
        switch (st) {
            case CLASSIC, NINJA -> {
                boolean aimed = Rotations.turn(p, yawOf(dir.getOpposite()), 75.0f);
                if (aimed && place(c, p, support, dir, cfg, false)) placed(cfg);
            }
            case ANDROMEDA -> {
                if (place(c, p, support, dir, cfg, true)) placed(cfg);
            }
            default -> {
                Vec3d eye = p.getEyePos();
                Vec3d hit = hitPoint(support, dir);
                boolean aimed = Rotations.turn(p, yawOf(dir), Rotations.pitchTo(eye, hit));
                if (aimed && place(c, p, support, dir, cfg, false)) placed(cfg);
            }
        }
    }

    private void placed(BridgeConfig cfg) {
        lastWarn = null;
        status = "§aqo'yildi";
        blocksPlaced++;
        if (blocksPlaced % jumpEvery == 0) jumpPending = true;
        if (style(cfg) == BridgeStyle.NINJA) sneakReleaseTicks = 3;
    }

    /** Har tick bosiladigan tugmalar (yurish/sneak/sprint/sakrash) - uslubga qarab. */
    private void drive(MinecraftClient c, ClientPlayerEntity p, BridgeStyle st) {
        GameOptions o = c.options;
        switch (st) {
            case CLASSIC -> {
                o.sneakKey.setPressed(true);
                o.backKey.setPressed(true);
            }
            case NINJA -> {
                o.backKey.setPressed(true);
                if (sneakReleaseTicks > 0) {
                    o.sneakKey.setPressed(false);
                    sneakReleaseTicks--;
                } else {
                    o.sneakKey.setPressed(true);
                }
            }
            case WITCHLY -> {
                if (witchPauseTicks > 0) {
                    witchPauseTicks--;
                    o.forwardKey.setPressed(false);
                } else {
                    o.forwardKey.setPressed(true);
                    if (RNG.nextInt(140) == 0) witchPauseTicks = 2 + RNG.nextInt(4);
                }
            }
            case BREEZY -> o.forwardKey.setPressed(true);
            case ANDROMEDA -> {
                o.forwardKey.setPressed(true);
                p.setSprinting(true);
            }
            case GOD, DOUBLE_GOD, SPEED -> {
                o.forwardKey.setPressed(true);
                p.setSprinting(true);
                maybeJump(c, p);
            }
            case JUMP -> { /* tickJump() ichida */ }
        }
    }

    private void maybeJump(MinecraftClient c, ClientPlayerEntity p) {
        if (jumpPending && p.isOnGround()) {
            c.options.jumpKey.setPressed(true);
            jumpPending = false;
        } else {
            c.options.jumpKey.setPressed(false);
        }
    }

    // ------------------------------------------------------------ xavfsiz to'xtash
    private void tickSafeStop(MinecraftClient c, ClientPlayerEntity p, BridgeConfig cfg) {
        release(c);
        if (p.isOnGround()) {
            state = State.IDLE;
            status = null;
            safeStopTicks = 0;
            return;
        }
        if (placeBelowSelf(c, p, cfg)) {
            status = "§axavfsiz to'xtadi";
        } else if (!solid(c.world, p.getBlockPos().down())) {
            warnOnce(p, "§cAuto Bridge: tagida blok yo'q - ehtiyot bo'ling, yiqilyapsiz!");
            status = "§cblok yo'q!";
        }
        if (++safeStopTicks > 100) {
            state = State.IDLE;
            status = null;
            safeStopTicks = 0;
        }
    }

    /** Hozir turgan joy tagiga, atrofdagi birinchi mos (solid) tomonga qarab blok qo'yadi (havoda ham ishlaydi). */
    private boolean placeBelowSelf(MinecraftClient c, ClientPlayerEntity p, BridgeConfig cfg) {
        ClientWorld w = c.world;
        BlockPos target = p.getBlockPos().down();
        if (solid(w, target)) return false;
        Direction[] faces = { dir.getOpposite(), dir, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };
        for (Direction d : faces) {
            BlockPos against = target.offset(d);
            if (solid(w, against)) {
                return place(c, p, against, d.getOpposite(), cfg, true);
            }
        }
        return false;
    }

    // ------------------------------------------------------------ qo'yish
    private static Vec3d hitPoint(BlockPos against, Direction face) {
        return Vec3d.ofCenter(against).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
    }

    private static float yawOf(Direction d) {
        return Rotations.yawTo(Vec3d.ZERO, new Vec3d(d.getOffsetX(), 0, d.getOffsetZ()));
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
    private static BridgeStyle style(BridgeConfig cfg) {
        return BridgeStyle.of(cfg.style);
    }

    /** Hozirgi qarashga (yaw) eng yaqin gorizontal tomon - shu tomonga devorga yetguncha boradi. */
    private static Direction lockedDirection(ClientPlayerEntity p) {
        double rad = Math.toRadians(p.getYaw());
        double dx = -Math.sin(rad), dz = Math.cos(rad);
        return Direction.getFacing(dx, 0, dz);
    }

    private static boolean solid(ClientWorld w, BlockPos pos) {
        return !w.getBlockState(pos).getCollisionShape(w, pos).isEmpty();
    }

    /** Devor: oyoq balandligida yoki bosh balandligida to'siq bo'lsa o'tib bo'lmaydi. */
    private static boolean blocked(ClientWorld w, BlockPos nextFoot) {
        return solid(w, nextFoot) || solid(w, nextFoot.up());
    }

    /** Hamma harakat tugmalarini qo'yib yuboradi (modul to'xtaganda). */
    private static void release(MinecraftClient c) {
        GameOptions o = c.options;
        o.forwardKey.setPressed(false);
        o.backKey.setPressed(false);
        o.leftKey.setPressed(false);
        o.rightKey.setPressed(false);
        o.jumpKey.setPressed(false);
        o.sneakKey.setPressed(false);
        o.sprintKey.setPressed(false);
        if (c.player != null) c.player.setSprinting(false);
    }

    private void warnOnce(ClientPlayerEntity p, String msg) {
        if (!msg.equals(lastWarn)) {
            p.sendMessage(Text.literal(msg), true);
            lastWarn = msg;
        }
    }

    public String hudLine(MinecraftClient c) {
        BridgeConfig cfg = BridgeConfig.get();
        if (!cfg.hud || (!cfg.enabled && state == State.IDLE)) return null;
        return status == null ? null : "§6Auto Bridge: " + status;
    }
}
