package uz.crystaldefense;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import uz.autotnt.AutoTntConfig;
import uz.pearlclutch.PearlClutchClient;
import uz.spearlunger.InvHelper;
import uz.spearlunger.Rotations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Crystal PvP himoyasi. Xavf: yaqinda dushman bor va (atrofda end crystal bor yoki yaqinda zarba yedingiz).
 *  - Qochish: dushman pastda (chuqurda) turib crystal qilyapti yoki jon kam - pearl'ni darhol tanlab dushman
 *    ko'rmaydigan xavfsiz joyga otadi (topilmasa qaragan tomonga);
 *  - Surround: oyog'ingiz atrofiga 4 tomondan obsidian;
 *  - Crystal joylarini yopish: yoningizdagi obsidian/bedrock ustiga oddiy blok - u yerga crystal qo'yib bo'lmaydi.
 */
public class CrystalDefenseController {
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private int lastHurt = 0, hurtAgo = 999, pearlCooldown = 0;
    private String status = null;
    private final List<int[]> borrowed = new ArrayList<>();

    public void tick(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        status = null;
        if (p == null || c.world == null || c.interactionManager == null) return;
        CrystalDefenseConfig cfg = CrystalDefenseConfig.get();
        while (CrystalDefenseClient.keyToggle.wasPressed()) {
            cfg.enabled = !cfg.enabled;
            CrystalDefenseConfig.save();
            p.sendMessage(Text.literal(cfg.enabled ? "§aCrystal Defense: ON" : "§cCrystal Defense: OFF"), true);
        }
        if (p.hurtTime > lastHurt) hurtAgo = 0; else hurtAgo++;
        lastHurt = p.hurtTime;
        if (pearlCooldown > 0) pearlCooldown--;
        if (!cfg.enabled || InvHelper.screenOpen(c) || p.isSpectator()) return;

        ClientWorld w = c.world;
        PlayerEntity enemy = nearestEnemy(w, p, cfg);
        if (enemy == null) return;
        boolean crystals = !w.getEntitiesByClass(EndCrystalEntity.class, p.getBoundingBox().expand(7), e -> true).isEmpty();
        boolean threat = crystals || hurtAgo < 30;
        if (!threat) return;
        boolean low = enemyLow(w, p, enemy);
        status = low ? "§cCRYSTAL: dushman pastda!" : "§6CRYSTAL xavf";

        // 1. qochish
        float hp = p.getHealth() + p.getAbsorptionAmount();
        if (cfg.escapePearl && pearlCooldown == 0 && (low || hp <= cfg.escapeHealth) && escape(c, p, enemy, cfg)) {
            pearlCooldown = 30;
            say(p, "§dCrystal Defense: pearl bilan qochdim");
            return;
        }
        // 2-3. bloklar
        int budget = Math.max(1, cfg.blocksPerTick);
        if (cfg.surround && p.isOnGround()) budget -= surround(c, p, cfg, budget);
        if (cfg.sealSpots && budget > 0) seal(c, p, cfg, budget);
        restore(c, p);
    }

    // ------------------------------------------------------------ qochish
    private boolean escape(MinecraftClient c, ClientPlayerEntity p, PlayerEntity enemy, CrystalDefenseConfig cfg) {
        if (cfg.escapeMode == 0) {
            for (Vec3d spot : safeSpots(c.world, p, enemy)) {
                if (PearlClutchClient.CONTROLLER.throwAt(c, p, spot)) return true;
            }
        }
        // qaragan tomonga (o'z tezligimizni ayirib - turib otgandek)
        int slot = -1;
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(Items.ENDER_PEARL)) { slot = i; break; }
        if (slot < 0 || p.getItemCooldownManager().isCoolingDown(p.getInventory().getStack(slot))) return false;
        Vec3d s = p.getVelocity();
        Vec3d dir = Vec3d.fromPolar(p.getPitch(), p.getYaw()).multiply(1.5).subtract(s.x, p.isOnGround() ? 0 : s.y, s.z)
                .normalize();
        int original = p.getInventory().getSelectedSlot();
        float oy = p.getYaw(), op = p.getPitch();
        p.getInventory().setSelectedSlot(slot);
        p.setYaw((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        p.setPitch((float) Math.toDegrees(-Math.asin(dir.y)));
        c.interactionManager.interactItem(p, Hand.MAIN_HAND);
        p.swingHand(Hand.MAIN_HAND);
        p.setYaw(oy);
        p.setPitch(op);
        p.getInventory().setSelectedSlot(original);
        return true;
    }

    /** Dushmandan uzoq (9+ blok), uning ko'zidan ko'rinmaydigan, ustida 2 bo'sh joy bo'lgan bloklar - eng yaxshisidan. */
    private static List<Vec3d> safeSpots(ClientWorld w, ClientPlayerEntity p, PlayerEntity enemy) {
        record Spot(Vec3d pos, double score) {}
        List<Spot> out = new ArrayList<>();
        BlockPos me = p.getBlockPos();
        Vec3d eEye = enemy.getEyePos();
        for (BlockPos b : BlockPos.iterate(me.add(-14, -6, -14), me.add(14, 4, 14))) {
            double hd = Math.hypot(b.getX() - me.getX(), b.getZ() - me.getZ());
            if (hd < 6 || hd > 14) continue;
            BlockState st = w.getBlockState(b);
            if (!st.isSideSolidFullSquare(w, b, Direction.UP) || st.isOf(Blocks.LAVA) || st.isOf(Blocks.MAGMA_BLOCK)) continue;
            BlockPos a1 = b.up(), a2 = b.up(2);
            if (!w.getBlockState(a1).getCollisionShape(w, a1).isEmpty()) continue;
            if (!w.getBlockState(a2).getCollisionShape(w, a2).isEmpty()) continue;
            Vec3d top = Vec3d.ofCenter(b).add(0, 0.5, 0);
            double fromEnemy = top.distanceTo(enemy.getEntityPos());
            if (fromEnemy < 9) continue;
            HitResult los = w.raycast(new RaycastContext(eEye, top.add(0, 1.2, 0), RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, p));
            boolean hidden = los.getType() == HitResult.Type.BLOCK;
            out.add(new Spot(top, fromEnemy + (hidden ? 20 : 0) - hd * 0.3));
        }
        out.sort(Comparator.comparingDouble(s -> -s.score));
        List<Vec3d> res = new ArrayList<>();
        for (int i = 0; i < out.size() && i < 25; i++) res.add(out.get(i).pos);
        return res;
    }

    // ------------------------------------------------------------ bloklar
    private int surround(MinecraftClient c, ClientPlayerEntity p, CrystalDefenseConfig cfg, int budget) {
        int placed = 0;
        BlockPos feet = p.getBlockPos();
        for (Direction d : SIDES) {
            if (placed >= budget) break;
            BlockPos pos = feet.offset(d);
            if (!c.world.getBlockState(pos).isReplaceable()) continue;
            if (p.getBoundingBox().intersects(new Box(pos))) continue;
            if (place(c, p, pos, cfg, true)) placed++;
        }
        return placed;
    }

    private void seal(MinecraftClient c, ClientPlayerEntity p, CrystalDefenseConfig cfg, int budget) {
        ClientWorld w = c.world;
        BlockPos feet = p.getBlockPos();
        int placed = 0;
        for (BlockPos b : BlockPos.iterate(feet.add(-2, -2, -2), feet.add(2, 1, 2))) {
            if (placed >= budget) return;
            BlockState st = w.getBlockState(b);
            if (!st.isOf(Blocks.OBSIDIAN) && !st.isOf(Blocks.BEDROCK)) continue;
            BlockPos up = b.up();
            if (!w.getBlockState(up).isAir() || !w.getBlockState(up.up()).isAir()) continue;
            if (p.getBoundingBox().intersects(new Box(up))) continue;
            if (!w.getEntitiesByClass(EndCrystalEntity.class, new Box(up), e -> true).isEmpty()) continue;
            if (place(c, p, up.toImmutable(), cfg, false)) placed++;
        }
    }

    /** Blok qo'yadi (o'sha yuzga qaraydi - look paketi - va qaytadi). obsidian=true: obsidian afzal, aks holda oddiy blok. */
    private boolean place(MinecraftClient c, ClientPlayerEntity p, BlockPos pos, CrystalDefenseConfig cfg, boolean obsidian) {
        BlockHitResult hit = support(c.world, pos);
        if (hit == null || p.getEyePos().distanceTo(hit.getPos()) > p.getBlockInteractionRange()) return false;
        int slot = blockSlot(c, p, cfg, obsidian);
        if (slot < 0) return false;
        int original = p.getInventory().getSelectedSlot();
        float yaw = p.getYaw(), pitch = p.getPitch();
        Vec3d eye = p.getEyePos();
        Rotations.sendLook(p, Rotations.yawTo(eye, hit.getPos()), Rotations.pitchTo(eye, hit.getPos()));
        p.getInventory().setSelectedSlot(slot);
        c.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
        p.swingHand(Hand.MAIN_HAND);
        p.getInventory().setSelectedSlot(original);
        Rotations.sendLook(p, yaw, pitch);
        return true;
    }

    private static BlockHitResult support(ClientWorld w, BlockPos g) {
        BlockPos down = g.down();
        if (solid(w, down, Direction.UP)) return new BlockHitResult(Vec3d.ofCenter(down).add(0, 0.5, 0), Direction.UP, down, false);
        for (Direction d : SIDES) {
            BlockPos n = g.offset(d);
            Direction face = d.getOpposite();
            if (!solid(w, n, face)) continue;
            return new BlockHitResult(Vec3d.ofCenter(n).add(face.getOffsetX() * 0.5, 0, face.getOffsetZ() * 0.5), face, n, false);
        }
        return null;
    }

    private static boolean solid(ClientWorld w, BlockPos pos, Direction face) {
        BlockState st = w.getBlockState(pos);
        if (st.hasBlockEntity() || st.isOf(Blocks.RESPAWN_ANCHOR)) return false;   // bosilsa ochiladi/portlaydi
        return st.isSideSolidFullSquare(w, pos, face);
    }

    /** obsidian=true: obsidian/crying obsidian; false: obsidian EMAS oddiy to'liq blok (yangi crystal joyi yaratmaslik uchun). */
    private int blockSlot(MinecraftClient c, ClientPlayerEntity p, CrystalDefenseConfig cfg, boolean obsidian) {
        int limit = cfg.fromInventory ? 36 : 9;
        int best = -1;
        for (int i = 0; i < limit && best < 0; i++) if (ok(p.getInventory().getStack(i), obsidian)) best = i;
        if (best < 9) return best;
        int sel = p.getInventory().getSelectedSlot();
        int target = sel == 8 ? 7 : 8;
        for (int h = 8; h >= 0; h--) if (h != sel && p.getInventory().getStack(h).isEmpty()) { target = h; break; }
        InvHelper.swap(c, p, best, target);
        borrowed.add(new int[] { best, target });
        return target;
    }

    private static boolean ok(ItemStack s, boolean obsidian) {
        if (!(s.getItem() instanceof BlockItem bi)) return false;
        Block b = bi.getBlock();
        boolean obs = b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN;
        if (obsidian) return obs;
        if (obs || b instanceof FallingBlock || b == Blocks.TNT || b == Blocks.RESPAWN_ANCHOR || b == Blocks.GLOWSTONE) return false;
        BlockState st = b.getDefaultState();
        return !st.hasBlockEntity() && st.isOpaqueFullCube();
    }

    private void restore(MinecraftClient c, ClientPlayerEntity p) {
        if (InvHelper.returnItems()) {
            for (int i = borrowed.size() - 1; i >= 0; i--) InvHelper.swap(c, p, borrowed.get(i)[0], borrowed.get(i)[1]);
        }
        borrowed.clear();
    }

    // ------------------------------------------------------------ xavf
    private static PlayerEntity nearestEnemy(ClientWorld w, ClientPlayerEntity p, CrystalDefenseConfig cfg) {
        PlayerEntity best = null;
        double bestD = cfg.range;
        for (PlayerEntity e : w.getPlayers()) {
            if (e == p || !e.isAlive() || e.isSpectator() || e.isCreative()) continue;
            if (cfg.ignoreFriends && isFriend(e)) continue;
            double d = e.distanceTo(p);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** Dushman pastda yoki chuqurda (4 tomoni qattiq blok) turibdimi. */
    private static boolean enemyLow(ClientWorld w, ClientPlayerEntity p, PlayerEntity e) {
        if (e.getY() <= p.getY() - 0.9) return true;
        BlockPos f = e.getBlockPos();
        int walls = 0;
        for (Direction d : SIDES) {
            BlockPos n = f.offset(d);
            if (w.getBlockState(n).isSideSolidFullSquare(w, n, d.getOpposite())) walls++;
        }
        return walls >= 3;
    }

    private static boolean isFriend(PlayerEntity t) {
        String name = t.getName().getString();
        for (String f : AutoTntConfig.get().friends) if (f.equalsIgnoreCase(name)) return true;
        return false;
    }

    public String hudLine(MinecraftClient c) {
        CrystalDefenseConfig cfg = CrystalDefenseConfig.get();
        return cfg.enabled && cfg.hud ? status : null;
    }

    private static void say(ClientPlayerEntity p, String msg) {
        if (CrystalDefenseConfig.get().messages) p.sendMessage(Text.literal(msg), true);
    }
}
