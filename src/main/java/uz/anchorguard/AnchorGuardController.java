package uz.anchorguard;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Respawn anchor portlashidan himoya: anchor bilan siz orangizga (oyoq va bosh balandligida) blok qo'yadi.
 * Portlash zarari "ko'rinish" (exposure) ga bog'liq: oradagi to'liq blok nurlarni to'sadi va zarar keskin kamayadi.
 * Siz anchor'ni bosganingizda bosish bekor qilinmaydi: o'sha zahoti blok qo'yilib, keyin anchor'ning ustki tomoni
 * bosiladi (server blokni portlashdan oldin oladi), ya'ni portlash kechikmaydi.
 */
public class AnchorGuardController {
    private boolean internal = false;
    private int scanCooldown = 0;

    // ------------------------------------------------------------ siz portlatganda
    public ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        if (internal || !(player instanceof ClientPlayerEntity p)) return ActionResult.PASS;
        AnchorGuardConfig cfg = AnchorGuardConfig.get();
        if (!cfg.enabled || !cfg.onDetonate || p.isSneaking()) return ActionResult.PASS;
        BlockPos anchor = hit.getBlockPos();
        BlockState st = world.getBlockState(anchor);
        if (!explodes(world, st)) return ActionResult.PASS;
        // glowstone bilan zaryadlayapti, portlatmayapti
        if (p.getStackInHand(hand).isOf(Items.GLOWSTONE) && st.get(RespawnAnchorBlock.CHARGES) < 4) return ActionResult.PASS;

        MinecraftClient c = MinecraftClient.getInstance();
        List<BlockPos> need = missingGuards((ClientWorld) world, p, anchor, cfg);
        if (need.isEmpty()) return ActionResult.PASS;   // allaqachon to'silgan
        int placed = placeGuards(c, p, need, cfg);

        // endi o'zimiz portlatamiz (o'yinchi bosgan qo'l bilan)
        internal = true;
        try {
            Vec3d top = Vec3d.ofCenter(anchor).add(0, 0.5, 0);
            c.interactionManager.interactBlock(p, hand, new BlockHitResult(top, Direction.UP, anchor, false));
            p.swingHand(hand);
        } finally {
            internal = false;
        }
        if (placed > 0) say(p, "§aAnchor guard: " + placed + " blok");
        return ActionResult.FAIL;   // vanilla bosishi bekor (biz bajardik)
    }

    // ------------------------------------------------------------ proaktiv
    public void tick(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (p == null || c.world == null || c.interactionManager == null) return;
        AnchorGuardConfig cfg = AnchorGuardConfig.get();
        while (AnchorGuardClient.keyToggle.wasPressed()) {
            cfg.enabled = !cfg.enabled;
            AnchorGuardConfig.save();
            p.sendMessage(Text.literal(cfg.enabled ? "§aAnchor Guard: ON" : "§cAnchor Guard: OFF"), true);
        }
        if (!cfg.enabled || !cfg.proactive || c.currentScreen != null || p.isSpectator()) return;
        if (scanCooldown > 0) {
            scanCooldown--;
            return;
        }
        scanCooldown = 1;
        BlockPos me = p.getBlockPos();
        int r = Math.max(2, cfg.radius);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.iterate(me.add(-r, -3, -r), me.add(r, 3, r))) {
            if (!explodes(c.world, c.world.getBlockState(pos))) continue;
            double d = p.squaredDistanceTo(Vec3d.ofCenter(pos));
            if (d < bestD) {
                bestD = d;
                best = pos.toImmutable();
            }
        }
        if (best == null) return;
        List<BlockPos> need = missingGuards(c.world, p, best, cfg);
        if (need.isEmpty()) return;
        int placed = placeGuards(c, p, need, cfg);
        if (placed > 0) {
            say(p, "§eAnchor guard: zaryadlangan anchor yaqinda!");
            scanCooldown = 4;
        }
    }

    // ------------------------------------------------------------ joylar
    private static boolean explodes(World w, BlockState st) {
        return st.isOf(Blocks.RESPAWN_ANCHOR) && st.get(RespawnAnchorBlock.CHARGES) > 0
                && w.getRegistryKey() != World.NETHER;
    }

    /** Anchor va o'yinchi orasidagi bo'sh (to'silmagan) joylar. */
    private List<BlockPos> missingGuards(ClientWorld w, ClientPlayerEntity p, BlockPos anchor, AnchorGuardConfig cfg) {
        List<BlockPos> out = new ArrayList<>();
        Vec3d me = p.getBoundingBox().getCenter();
        Vec3d a = Vec3d.ofCenter(anchor);
        double dx = me.x - a.x, dz = me.z - a.z;
        if (Math.hypot(dx, dz) < 0.9) return out;   // anchor ustida turibmiz - to'sib bo'lmaydi
        Direction dir = Math.abs(dx) > Math.abs(dz)
                ? (dx > 0 ? Direction.EAST : Direction.WEST)
                : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        BlockPos low = anchor.offset(dir);
        List<BlockPos> cand = new ArrayList<>();
        cand.add(low);
        if (cfg.twoHigh) cand.add(low.up());
        // o'yinchi ancha balandda bo'lsa yana bir qavat
        if (cfg.twoHigh && p.getEyeY() > anchor.getY() + 2.5) cand.add(low.up(2));
        Box self = p.getBoundingBox();
        for (BlockPos g : cand) {
            BlockState st = w.getBlockState(g);
            if (st.isFullCube(w, g)) continue;          // allaqachon to'siq bor
            if (!st.isReplaceable()) continue;
            if (self.intersects(new Box(g))) continue;  // o'zimiz turgan joy
            out.add(g);
        }
        return out;
    }

    /** Pastdan yuqoriga qo'yadi; har biri oldingisining (yoki yerning) ustiga. Qo'yilgan bloklar soni. */
    private int placeGuards(MinecraftClient c, ClientPlayerEntity p, List<BlockPos> need, AnchorGuardConfig cfg) {
        int slot = blockSlot(c, p, cfg);
        if (slot < 0) {
            say(p, "§cAnchor guard: blok yo'q");
            return 0;
        }
        int original = p.getInventory().getSelectedSlot();
        int placed = 0;
        internal = true;
        try {
            p.getInventory().setSelectedSlot(slot);
            for (BlockPos g : need) {
                if (!(p.getInventory().getStack(slot).getItem() instanceof BlockItem)) break;
                BlockHitResult hit = support(c.world, g);
                if (hit == null) continue;
                c.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
                placed++;
            }
            if (placed > 0) p.swingHand(Hand.MAIN_HAND);
        } finally {
            internal = false;
            p.getInventory().setSelectedSlot(original);
        }
        return placed;
    }

    /** Qaysi blokning qaysi yuziga bosib qo'yish mumkin (anchor'ning o'ziga emas - u bosilsa portlaydi). */
    private static BlockHitResult support(ClientWorld w, BlockPos g) {
        BlockPos down = g.down();
        if (solid(w, down, Direction.UP)) {
            return new BlockHitResult(Vec3d.ofCenter(down).add(0, 0.5, 0), Direction.UP, down, false);
        }
        for (Direction d : Direction.Type.HORIZONTAL) {
            BlockPos n = g.offset(d);
            Direction face = d.getOpposite();
            if (!solid(w, n, face)) continue;
            Vec3d hit = Vec3d.ofCenter(n).add(face.getOffsetX() * 0.5, 0, face.getOffsetZ() * 0.5);
            return new BlockHitResult(hit, face, n, false);
        }
        BlockPos up = g.up();
        if (solid(w, up, Direction.DOWN)) {
            return new BlockHitResult(Vec3d.ofCenter(up).add(0, -0.5, 0), Direction.DOWN, up, false);
        }
        return null;
    }

    private static boolean solid(ClientWorld w, BlockPos pos, Direction face) {
        BlockState st = w.getBlockState(pos);
        if (st.isOf(Blocks.RESPAWN_ANCHOR) || st.hasBlockEntity()) return false;   // bosilsa ochiladi/portlaydi
        return st.isSideSolidFullSquare(w, pos, face);
    }

    private int blockSlot(MinecraftClient c, ClientPlayerEntity p, AnchorGuardConfig cfg) {
        int best = -1, bestScore = 0;
        int limit = cfg.fromInventory ? 36 : 9;
        for (int i = 0; i < limit; i++) {
            int s = score(p.getInventory().getStack(i), cfg);
            if (s == 0) continue;
            if (i < 9) s += 10;   // hotbar'dagini afzal ko'ramiz
            if (s > bestScore) {
                bestScore = s;
                best = i;
            }
        }
        if (best < 9) return best;
        int sel = p.getInventory().getSelectedSlot();
        int target = -1;
        for (int h = 0; h < 9; h++) if (h != sel && p.getInventory().getStack(h).isEmpty()) { target = h; break; }
        if (target < 0) target = sel == 8 ? 7 : 8;
        c.interactionManager.clickSlot(p.playerScreenHandler.syncId, best, target, SlotActionType.SWAP, p);
        return target;
    }

    /** 0 = yaramaydi; obsidian eng yaxshisi (portlashda buzilmaydi). */
    private static int score(ItemStack s, AnchorGuardConfig cfg) {
        if (!(s.getItem() instanceof BlockItem bi)) return 0;
        Block b = bi.getBlock();
        if (b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN) return 5;
        if (cfg.obsidianOnly) return 0;
        if (b == Blocks.GLOWSTONE || b == Blocks.RESPAWN_ANCHOR || b == Blocks.TNT || b instanceof FallingBlock) return 0;
        BlockState st = b.getDefaultState();
        if (st.hasBlockEntity() || !st.isOpaqueFullCube()) return 0;
        return 1;
    }

    private void say(ClientPlayerEntity p, String msg) {
        if (AnchorGuardConfig.get().messages) p.sendMessage(Text.literal(msg), true);
    }
}
