package uz.autocobweb;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import uz.autotnt.AutoTntConfig;
import uz.spearlunger.Rotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cobweb'ni o'zi qo'yadi (ustuvorlik tartibida):
 *  1. MLG: baland joydan yiqilayotganda tagingizga (cobweb fall damage'ni yo'q qiladi);
 *  2. Anti-mace: ustingizdan tushayotgan dushman bo'lsa boshingiz ustiga (u ipga ilinib mace zarbasi bo'lmaydi);
 *  3. Joyida turgan / devorga tiqilgan dushman oyog'iga;
 *  4. Oldingizga yugurib yoki sakrab kelayotgan dushmanning tushadigan joyiga.
 */
public class CobwebController {
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    /** Qo'yiladigan joy: web paydo bo'ladigan blok + qaysi blokning qaysi yuziga bosiladi. */
    private record Placement(BlockPos pos, BlockPos base, Direction side, Vec3d hit, String why) {}

    private final Map<UUID, Vec3d> lastPos = new HashMap<>();
    private final Map<UUID, Vec3d> velocity = new HashMap<>();
    private final Map<UUID, Integer> stillFor = new HashMap<>();
    private int cooldown = 0;
    private int aimTicks = 0;
    private BlockPos aiming = null;
    /** Ikki qavat uchun: keyingi qo'yiladigan bosh balandligi. */
    private BlockPos followUp = null;

    public void tick(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (p == null || c.world == null || c.interactionManager == null) {
            lastPos.clear();
            velocity.clear();
            stillFor.clear();
            aiming = null;
            followUp = null;
            return;
        }
        CobwebConfig cfg = CobwebConfig.get();
        while (AutoCobwebClient.keyToggle.wasPressed()) {
            cfg.enabled = !cfg.enabled;
            CobwebConfig.save();
            p.sendMessage(Text.literal(cfg.enabled ? "§aAuto Cobweb: ON" : "§cAuto Cobweb: OFF"), true);
        }
        trackVelocities(c.world, p);

        if (!cfg.enabled || c.currentScreen != null || p.isSpectator()) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (webSlot(c, p, false) < 0) return;

        Placement pl = choose(c.world, p, cfg);
        if (pl == null) {
            aiming = null;
            return;
        }
        if (cfg.rotate && !pl.why.equals("MLG")) {
            // boshqa joyga o'tgan bo'lsa qaytadan sanaymiz
            if (!pl.pos.equals(aiming)) {
                aiming = pl.pos;
                aimTicks = 0;
            }
            Vec3d eye = p.getEyePos();
            boolean aimed = Rotations.turn(p, Rotations.yawTo(eye, pl.hit), Rotations.pitchTo(eye, pl.hit));
            if (!aimed && ++aimTicks < 8) return;
        } else if (cfg.rotate) {
            // yiqilayotganda vaqt yo'q: to'g'ri pastga qaraymiz
            p.setPitch(90.0f);
        }
        place(c, p, pl, cfg);
    }

    // ------------------------------------------------------------ joy tanlash
    private Placement choose(ClientWorld w, ClientPlayerEntity p, CobwebConfig cfg) {
        double reach = cfg.reachTenths / 10.0;
        Vec3d eye = p.getEyePos();

        if (followUp != null) {
            BlockPos f = followUp;
            followUp = null;
            Placement pl = placement(w, p, f, eye, reach, "2-qavat");
            if (pl != null) return pl;
        }

        // 1. MLG
        if (cfg.selfMlg && !p.isOnGround() && !p.isGliding() && !p.getAbilities().flying && !p.isTouchingWater()
                && p.fallDistance >= cfg.minFall && p.getVelocity().y < -0.4) {
            BlockPos feet = p.getBlockPos();
            for (int i = 0; i <= 5; i++) {
                BlockPos g = feet.down(i + 1);
                BlockState gs = w.getBlockState(g);
                if (gs.isAir() || gs.isReplaceable()) continue;
                BlockPos web = g.up();
                double h = p.getY() - web.getY();
                // keyingi tickda yetib kelamizmi
                if (h <= Math.max(2.5, -p.getVelocity().y * 1.5) && !isWeb(w, web) && w.getBlockState(web).isReplaceable()
                        && w.getBlockState(g).isSideSolidFullSquare(w, g, Direction.UP)) {
                    Vec3d hit = Vec3d.ofCenter(g).add(0, 0.5, 0);
                    if (eye.distanceTo(hit) <= reach + 1.0) return new Placement(web, g, Direction.UP, hit, "MLG");
                }
                break;
            }
        }

        // 2. Anti-mace: tepada tushayotgan dushman
        if (cfg.antiMace) {
            for (LivingEntity e : enemies(w, p, cfg, 12)) {
                double dy = e.getY() - p.getY();
                double hd = Math.hypot(e.getX() - p.getX(), e.getZ() - p.getZ());
                Vec3d v = velocity.getOrDefault(e.getUuid(), Vec3d.ZERO);
                if (dy > 2.5 && hd < 2.5 && v.y < -0.3) {
                    BlockPos above = p.getBlockPos().up(2);
                    Placement pl = placement(w, p, above, eye, reach, "Anti-mace");
                    if (pl != null) return pl;
                }
            }
        }

        // 3-4. dushmanlar
        Placement best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : enemies(w, p, cfg, reach + cfg.approachDist + 2)) {
            if (inWeb(w, e)) continue;
            double d = e.distanceTo(p);
            Placement pl = null;
            int still = stillFor.getOrDefault(e.getUuid(), 0);
            if (cfg.trapStill && still >= cfg.stillTicks && e.isOnGround()) {
                pl = placement(w, p, feetOf(e), eye, reach, "Turibdi");
            }
            if (pl == null && cfg.trapApproach && d <= cfg.approachDist + 1) {
                pl = approach(w, p, e, eye, reach, cfg);
            }
            if (pl != null && d < bestD) {
                bestD = d;
                best = pl;
            }
        }
        if (best != null && cfg.doubleWeb && !best.why.equals("Anti-mace")) followUp = best.pos.up();
        return best;
    }

    /** Kelayotgan dushman: bir necha tickdan keyin qayerda bo'lishini hisoblab, o'sha yerning yeriga. */
    private Placement approach(ClientWorld w, ClientPlayerEntity p, LivingEntity e, Vec3d eye, double reach,
                               CobwebConfig cfg) {
        Vec3d v = velocity.getOrDefault(e.getUuid(), Vec3d.ZERO);
        Vec3d toMe = p.getEntityPos().subtract(e.getEntityPos());
        Vec3d flat = new Vec3d(toMe.x, 0, toMe.z);
        double hv = Math.hypot(v.x, v.z);
        if (flat.lengthSquared() < 1e-4 || hv < 0.08) return null;
        // tezligi bizga qarab yo'nalganmi
        if (new Vec3d(v.x, 0, v.z).normalize().dotProduct(flat.normalize()) < 0.5) return null;

        Vec3d pos = e.getEntityPos();
        double vy = v.y;
        for (int i = 0; i < Math.max(1, cfg.lookahead); i++) {
            pos = pos.add(v.x, vy, v.z);
            if (!e.isOnGround()) vy = (vy - 0.08) * 0.98;
        }
        // bizdan o'tib ketmasin: kamida 1.3 blok narida to'xtatamiz
        Vec3d mine = p.getEntityPos();
        Vec3d off = new Vec3d(pos.x - mine.x, 0, pos.z - mine.z);
        if (off.length() < 1.3 || off.dotProduct(flat) > 0) {
            Vec3d dir = flat.normalize().multiply(-1);
            pos = new Vec3d(mine.x + dir.x * 1.5, pos.y, mine.z + dir.z * 1.5);
        }
        BlockPos col = BlockPos.ofFloored(pos.x, Math.max(pos.y, e.getY()) + 0.2, pos.z);
        BlockPos ground = null;
        for (int i = 0; i < 6; i++) {
            BlockPos g = col.down(i + 1);
            if (w.getBlockState(g).isSideSolidFullSquare(w, g, Direction.UP)) { ground = g; break; }
        }
        if (ground == null) return null;
        return placement(w, p, ground.up(), eye, reach, e.isOnGround() ? "Kelyapti" : "Sakrab kelyapti");
    }

    /** Shu joyga web qo'yib bo'ladimi va qaysi blokka bosish kerak. */
    private Placement placement(ClientWorld w, ClientPlayerEntity p, BlockPos pos, Vec3d eye, double reach, String why) {
        BlockState st = w.getBlockState(pos);
        if (!st.isReplaceable() || isWeb(w, pos)) return null;
        // o'zimizni ilintirib qo'ymaylik
        if (p.getBoundingBox().expand(0.2, 0, 0.2).intersects(new Box(pos))) return null;
        Placement best = null;
        double bestD = reach;
        BlockPos down = pos.down();
        if (w.getBlockState(down).isSideSolidFullSquare(w, down, Direction.UP)) {
            Vec3d hit = Vec3d.ofCenter(down).add(0, 0.5, 0);
            double d = eye.distanceTo(hit);
            if (d <= bestD) { bestD = d; best = new Placement(pos, down, Direction.UP, hit, why); }
        }
        for (Direction dir : HORIZONTAL) {
            BlockPos n = pos.offset(dir);
            Direction face = dir.getOpposite();
            if (!w.getBlockState(n).isSideSolidFullSquare(w, n, face)) continue;
            Vec3d hit = Vec3d.ofCenter(n).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            double d = eye.distanceTo(hit);
            if (d <= bestD) { bestD = d; best = new Placement(pos, n, face, hit, why); }
        }
        BlockPos up = pos.up();
        if (w.getBlockState(up).isSideSolidFullSquare(w, up, Direction.DOWN)) {
            Vec3d hit = Vec3d.ofCenter(up).add(0, -0.5, 0);
            double d = eye.distanceTo(hit);
            if (d <= bestD) best = new Placement(pos, up, Direction.DOWN, hit, why);
        }
        return best;
    }

    // ------------------------------------------------------------ qo'yish
    private void place(MinecraftClient c, ClientPlayerEntity p, Placement pl, CobwebConfig cfg) {
        int slot = webSlot(c, p, cfg.fromInventory);
        if (slot < 0) return;
        int original = p.getInventory().getSelectedSlot();
        p.getInventory().setSelectedSlot(slot);
        c.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(pl.hit, pl.side, pl.base, false));
        p.swingHand(Hand.MAIN_HAND);
        p.getInventory().setSelectedSlot(original);
        cooldown = Math.max(0, cfg.placeDelay);
        aiming = null;
        if (cfg.messages) p.sendMessage(Text.literal("§7Cobweb: §f" + pl.why), true);
    }

    /** Hotbar'dagi cobweb sloti; kerak bo'lsa (move=true) inventardan hotbar'ga olib keladi. */
    private int webSlot(MinecraftClient c, ClientPlayerEntity p, boolean move) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(Items.COBWEB)) return i;
        if (!CobwebConfig.get().fromInventory) return -1;
        for (int i = 9; i < 36; i++) {
            if (!p.getInventory().getStack(i).isOf(Items.COBWEB)) continue;
            if (!move) return 9;   // bor, lekin hali olib kelmadik
            int sel = p.getInventory().getSelectedSlot();
            int target = -1;
            for (int h = 0; h < 9; h++) if (h != sel && p.getInventory().getStack(h).isEmpty()) { target = h; break; }
            if (target < 0) target = sel == 8 ? 7 : 8;
            c.interactionManager.clickSlot(p.playerScreenHandler.syncId, i, target, SlotActionType.SWAP, p);
            return target;
        }
        return -1;
    }

    // ------------------------------------------------------------ yordamchilar
    private void trackVelocities(ClientWorld w, ClientPlayerEntity self) {
        for (PlayerEntity t : w.getPlayers()) track(t);
        if (CobwebConfig.get().targetMobs) {
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, self.getBoundingBox().expand(16),
                    e -> e instanceof Monster)) track(e);
        }
    }

    private void track(LivingEntity t) {
        Vec3d cur = t.getEntityPos();
        Vec3d prev = lastPos.get(t.getUuid());
        if (prev != null) {
            Vec3d v = cur.subtract(prev);
            velocity.put(t.getUuid(), v);
            boolean still = Math.hypot(v.x, v.z) < 0.03;
            stillFor.put(t.getUuid(), still ? stillFor.getOrDefault(t.getUuid(), 0) + 1 : 0);
        }
        lastPos.put(t.getUuid(), cur);
    }

    private List<LivingEntity> enemies(ClientWorld w, ClientPlayerEntity self, CobwebConfig cfg, double range) {
        return w.getEntitiesByClass(LivingEntity.class, self.getBoundingBox().expand(range), e -> {
            if (e == self || !e.isAlive() || e.distanceTo(self) > range) return false;
            if (e instanceof PlayerEntity pl) {
                return !pl.isSpectator() && !pl.isCreative() && !(cfg.ignoreFriends && isFriend(pl));
            }
            return cfg.targetMobs && e instanceof Monster;
        });
    }

    private static boolean isFriend(PlayerEntity t) {
        String name = t.getName().getString();
        for (String f : AutoTntConfig.get().friends) if (f.equalsIgnoreCase(name)) return true;
        return false;
    }

    private static BlockPos feetOf(LivingEntity e) {
        return BlockPos.ofFloored(e.getX(), e.getY() + 0.2, e.getZ());
    }

    private static boolean isWeb(ClientWorld w, BlockPos pos) {
        return w.getBlockState(pos).isOf(Blocks.COBWEB);
    }

    private static boolean inWeb(ClientWorld w, LivingEntity e) {
        BlockPos f = feetOf(e);
        return isWeb(w, f) || isWeb(w, f.up());
    }

    public String hudLine(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        CobwebConfig cfg = CobwebConfig.get();
        if (p == null || !cfg.enabled || !cfg.hud) return null;
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(Items.COBWEB)) n += s.getCount();
        }
        return "§fCobweb: " + (n == 0 ? "§c0" : "§f" + n);
    }
}
