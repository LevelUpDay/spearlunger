package uz.pearlclutch;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import uz.spearlunger.InvHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ender pearl: otilganda o'yinchining tezligini (yurish, yugurish, sakrash, yiqilish) o'ziga oladi, shuning uchun
 * qimirlab otsangiz pearl boshqa joyga tushadi. Bu modul shuni hisoblab, pearl aynan kerakli nuqtaga tushadigan
 * yo'nalishni topadi:
 *  - Aim: o'zingiz otsangiz - turib otgandagi tushish joyiga (qaragan joyingizga) tushadigan qilib to'g'rilaydi;
 *  - Clutch: baland joydan / void'ga yiqilayotganda o'zi xavfsiz blokka otadi (teleport yiqilishni bekor qiladi).
 * Yo'nalish faqat otish paketiga yoziladi - kamera qimirlamaydi.
 */
public class PearlController {
    /** Pearl fizikasi (vanilla): otish tezligi, gravitatsiya, havo qarshiligi. */
    private static final double SPEED = 1.5, GRAVITY = 0.03, DRAG = 0.99;
    private static final int MAX_TICKS = 160;

    /** {yaw, pitch, ticks}. */
    private record Aim(float yaw, float pitch, int ticks) {}

    private boolean internal = false;
    private boolean thrownThisFall = false;
    private int scanCooldown = 0;

    // ------------------------------------------------------------ o'zingiz otganda
    public ActionResult onUseItem(PlayerEntity player, World world, Hand hand) {
        if (internal || !(player instanceof ClientPlayerEntity p)) return ActionResult.PASS;
        PearlConfig cfg = PearlConfig.get();
        if (!cfg.enabled || !cfg.aimAssist || !p.getStackInHand(hand).isOf(Items.ENDER_PEARL)) return ActionResult.PASS;
        if (p.getItemCooldownManager().isCoolingDown(p.getStackInHand(hand))) return ActionResult.PASS;
        Vec3d add = shooterVelocity(p);
        if (add.lengthSquared() < 0.0025) return ActionResult.PASS;   // qimirlamayapti - to'g'rilash shart emas

        // turib otganda qayerga tushardi
        Vec3d start = spawnPos(p);
        Vec3d still = Vec3d.fromPolar(p.getPitch(), p.getYaw()).multiply(SPEED);
        HitResult landing = trace((ClientWorld) world, p, start, still);
        if (landing == null) return ActionResult.PASS;
        Aim aim = solve(start, landing.getPos(), add);
        if (aim == null || !verify((ClientWorld) world, p, start, aim, add, landing.getPos(), 1.2)) return ActionResult.PASS;
        throwPearl(MinecraftClient.getInstance(), p, hand, aim);
        return ActionResult.FAIL;   // o'zimiz otdik
    }

    // ------------------------------------------------------------ clutch
    public void tick(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (p == null || c.world == null || c.interactionManager == null) return;
        PearlConfig cfg = PearlConfig.get();
        while (PearlClutchClient.keyToggle.wasPressed()) {
            cfg.enabled = !cfg.enabled;
            PearlConfig.save();
            p.sendMessage(Text.literal(cfg.enabled ? "§aPearl Clutch: ON" : "§cPearl Clutch: OFF"), true);
        }
        if (p.isOnGround() || p.isTouchingWater() || p.isClimbing() || p.fallDistance < 1) thrownThisFall = false;
        if (!cfg.enabled || !cfg.autoClutch || thrownThisFall || InvHelper.screenOpen(c)) return;
        if (p.isOnGround() || p.isGliding() || p.getAbilities().flying || p.isTouchingWater() || p.isSpectator()) return;
        if (p.getVelocity().y > -0.3) return;
        if (scanCooldown > 0) {
            scanCooldown--;
            return;
        }
        scanCooldown = 1;

        ClientWorld w = c.world;
        BlockPos feet = p.getBlockPos();
        BlockPos ground = null;
        for (int i = 1; i < 96 && feet.getY() - i >= w.getBottomY(); i++) {
            BlockPos b = feet.down(i);
            BlockState st = w.getBlockState(b);
            if (!st.getCollisionShape(w, b).isEmpty() || !st.getFluidState().isEmpty()) {
                ground = b;
                break;
            }
        }
        boolean voidFall = ground == null;
        if (voidFall && !cfg.voidClutch) return;
        if (!voidFall) {
            if (softLanding(w.getBlockState(ground))) return;   // suv, slime, pichan... - zarar yo'q
            double total = p.fallDistance + (p.getY() - (ground.getY() + 1));
            if (total < cfg.minFall) return;
        }

        int slot = pearlSlot(p, cfg);
        if (slot < 0) return;
        ItemStack pearl = p.getInventory().getStack(slot);
        if (p.getItemCooldownManager().isCoolingDown(pearl)) return;

        int fallTicks = voidFall ? Integer.MAX_VALUE : ticksToFall(p, ground.getY() + 1);
        Vec3d start = spawnPos(p);
        Vec3d add = shooterVelocity(p);
        Aim best = null;
        for (BlockPos target : candidates(w, p, cfg.radius)) {
            Vec3d top = Vec3d.ofCenter(target).add(0, 0.5, 0);
            Aim aim = solve(start, top, add);
            if (aim == null || aim.ticks + 1 >= fallTicks) continue;   // yerga bizdan oldin yetib borishi kerak
            if (!verify(w, p, start, aim, add, top, 0.9)) continue;
            best = aim;
            break;
        }
        if (best == null) return;

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
        p.getInventory().setSelectedSlot(hotbar);
        throwPearl(c, p, Hand.MAIN_HAND, best);
        p.getInventory().setSelectedSlot(original);
        if (borrowed != null && InvHelper.returnItems()) InvHelper.swap(c, p, borrowed[0], borrowed[1]);
        thrownThisFall = true;
        if (cfg.messages) p.sendMessage(Text.literal("§dPearl clutch!"), true);
    }

    // ------------------------------------------------------------ boshqa modullar uchun
    /** Hotbar'dagi pearl'ni target nuqtaga (harakatni hisoblab) otadi. Otilsa true. */
    public boolean throwAt(MinecraftClient c, ClientPlayerEntity p, Vec3d target) {
        int slot = -1;
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(Items.ENDER_PEARL)) { slot = i; break; }
        if (slot < 0 || p.getItemCooldownManager().isCoolingDown(p.getInventory().getStack(slot))) return false;
        Vec3d start = spawnPos(p), add = shooterVelocity(p);
        Aim aim = solve(start, target, add);
        if (aim == null || !verify(c.world, p, start, aim, add, target, 2.5)) return false;
        int original = p.getInventory().getSelectedSlot();
        p.getInventory().setSelectedSlot(slot);
        throwPearl(c, p, Hand.MAIN_HAND, aim);
        p.getInventory().setSelectedSlot(original);
        return true;
    }

    // ------------------------------------------------------------ otish
    /** Yo'nalishni faqat otish paketiga yozib otadi va qarashni darhol qaytaradi. */
    private void throwPearl(MinecraftClient c, ClientPlayerEntity p, Hand hand, Aim aim) {
        float yaw = p.getYaw(), pitch = p.getPitch();
        internal = true;
        try {
            p.setYaw(aim.yaw);
            p.setPitch(aim.pitch);
            c.interactionManager.interactItem(p, hand);
            p.swingHand(hand);
        } finally {
            internal = false;
            p.setYaw(yaw);
            p.setPitch(pitch);
        }
    }

    // ------------------------------------------------------------ ballistika
    /** Pearl o'yinchi tezligini oladi: x/z har doim, y - faqat havoda bo'lsa. */
    private static Vec3d shooterVelocity(ClientPlayerEntity p) {
        Vec3d v = p.getVelocity();
        return new Vec3d(v.x, p.isOnGround() ? 0 : v.y, v.z);
    }

    private static Vec3d spawnPos(ClientPlayerEntity p) {
        return p.getEyePos().add(0, -0.1, 0);
    }

    /**
     * start'dan target'ga tushadigan yo'nalish (otuvchi tezligi add bilan). Gorizontal tekislikda: otish yo'nalishi
     * + add yig'indisi nishonga qarab yo'nalgan bo'lishi kerak - yaw shundan aniq hisoblanadi; pitch qidiriladi.
     * Bir nechta yechimdan eng tez yetib boradigani.
     */
    private static Aim solve(Vec3d start, Vec3d target, Vec3d add) {
        double dx = target.x - start.x, dz = target.z - start.z, dy = target.y - start.y;
        double dist = Math.sqrt(dx * dx + dz * dz);
        Aim best = null;
        double bestErr = Double.MAX_VALUE;
        if (dist < 0.05) {
            // to'g'ri ostimizda/ustimizda
            for (float pitch = -90.0f; pitch <= 90.0f; pitch += 180.0f) {
                double vy = -Math.sin(Math.toRadians(pitch)) * SPEED + add.y;
                int t = verticalTicks(vy, dy);
                if (t > 0 && (best == null || t < best.ticks)) best = new Aim(0, pitch, t);
            }
            return best;
        }
        double ux = dx / dist, uz = dz / dist;            // nishonga yo'nalish
        double px = -uz, pz = ux;                          // unga perpendikulyar
        double addPar = add.x * ux + add.z * uz, addPerp = add.x * px + add.z * pz;
        for (float pitch = -89.5f; pitch <= 89.5f; pitch += 0.25f) {
            double rad = Math.toRadians(pitch);
            double a = Math.cos(rad) * SPEED;
            if (a < 1e-3 || Math.abs(addPerp) > a) continue;
            double perp = -addPerp / a;                    // yon tezlikni bekor qilamiz
            double par = Math.sqrt(1 - perp * perp);
            double vh = a * par + addPar;
            if (vh <= 0.01) continue;
            double vy = -Math.sin(rad) * SPEED + add.y;
            double x = 0, y = 0;
            for (int i = 1; i <= MAX_TICKS; i++) {
                double nx = x + vh, ny = y + vy;
                if (nx >= dist) {
                    double hy = y + (ny - y) * ((dist - x) / (nx - x));
                    double err = Math.abs(hy - dy);
                    // yetarlicha aniq yechimlardan eng tez yetib boradigani
                    if (err < 0.35 && (best == null || i < best.ticks || (i == best.ticks && err < bestErr))) {
                        bestErr = err;
                        double wx = par * ux + perp * px, wz = par * uz + perp * pz;   // otish yo'nalishi (gorizontal)
                        float yaw = (float) Math.toDegrees(Math.atan2(-wx, wz));
                        best = new Aim(yaw, pitch, i);
                    }
                    break;
                }
                x = nx;
                y = ny;
                vh *= DRAG;
                vy = vy * DRAG - GRAVITY;
                if (y < dy - 80) break;
            }
        }
        return best;
    }

    /** Vertikal: dy ga yetib borish ticki (yo'q bo'lsa -1). */
    private static int verticalTicks(double vy, double dy) {
        double y = 0;
        for (int i = 1; i <= MAX_TICKS; i++) {
            double ny = y + vy;
            if ((dy <= 0 && ny <= dy) || (dy > 0 && ny >= dy)) return i;
            y = ny;
            vy = vy * DRAG - GRAVITY;
        }
        return -1;
    }

    /** To'liq 3D simulyatsiya (bloklar bilan): birinchi tegadigan joy kutilgan nuqtaga yaqinmi. */
    private static boolean verify(ClientWorld w, ClientPlayerEntity p, Vec3d start, Aim aim, Vec3d add, Vec3d expected,
                                  double tolerance) {
        Vec3d v = Vec3d.fromPolar(aim.pitch, aim.yaw).multiply(SPEED).add(add);
        HitResult hit = trace(w, p, start, v);
        return hit != null && hit.getPos().distanceTo(expected) <= tolerance;
    }

    private static HitResult trace(ClientWorld w, ClientPlayerEntity p, Vec3d start, Vec3d vel) {
        Vec3d pos = start, v = vel;
        for (int i = 0; i < MAX_TICKS; i++) {
            Vec3d next = pos.add(v);
            BlockHitResult r = w.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, p));
            if (r.getType() == HitResult.Type.BLOCK) return r;
            pos = next;
            v = v.multiply(DRAG).add(0, -GRAVITY, 0);
            if (pos.y < w.getBottomY() - 8) break;
        }
        return null;
    }

    /** O'yinchi qancha tickda shu balandlikka tushadi. */
    private static int ticksToFall(ClientPlayerEntity p, double groundY) {
        double y = p.getY(), vy = p.getVelocity().y;
        for (int i = 1; i < 400; i++) {
            y += vy;
            if (y <= groundY) return i;
            vy = (vy - 0.08) * 0.98;
        }
        return 400;
    }

    // ------------------------------------------------------------ qo'nish joyi
    /** Ustiga turib bo'ladigan bloklar: tepasi qattiq, ustida 2 bo'sh joy, xavfsiz. Yaqinidan uzoqqa. */
    private static List<BlockPos> candidates(ClientWorld w, ClientPlayerEntity p, int radius) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos feet = p.getBlockPos();
        int r = Math.max(2, radius);
        for (BlockPos b : BlockPos.iterate(feet.add(-r, -40, -r), feet.add(r, 4, r))) {
            if (b.getY() < w.getBottomY()) continue;
            BlockState st = w.getBlockState(b);
            if (!st.isSideSolidFullSquare(w, b, Direction.UP) || dangerous(st)) continue;
            BlockPos a1 = b.up(), a2 = b.up(2);
            if (!w.getBlockState(a1).getCollisionShape(w, a1).isEmpty()) continue;
            if (!w.getBlockState(a2).getCollisionShape(w, a2).isEmpty()) continue;
            if (dangerous(w.getBlockState(a1))) continue;
            out.add(b.toImmutable());
        }
        Vec3d me = p.getEntityPos();
        out.sort(Comparator.comparingDouble(b -> {
            double hx = b.getX() + 0.5 - me.x, hz = b.getZ() + 0.5 - me.z;
            // gorizontal yaqinlik muhimroq, keyin balandroq (tezroq yetadi)
            return (hx * hx + hz * hz) - (b.getY() - me.y) * 0.5;
        }));
        return out.size() > 60 ? out.subList(0, 60) : out;
    }

    private static boolean dangerous(BlockState st) {
        return st.isOf(Blocks.LAVA) || st.isOf(Blocks.MAGMA_BLOCK) || st.isOf(Blocks.CACTUS)
                || st.isIn(BlockTags.FIRE) || st.isOf(Blocks.SWEET_BERRY_BUSH) || st.isOf(Blocks.POWDER_SNOW)
                || st.isOf(Blocks.POINTED_DRIPSTONE) || st.isOf(Blocks.CAMPFIRE) || st.isOf(Blocks.SOUL_CAMPFIRE);
    }

    private static boolean softLanding(BlockState st) {
        return !st.getFluidState().isEmpty() || st.isOf(Blocks.SLIME_BLOCK) || st.isOf(Blocks.HAY_BLOCK)
                || st.isOf(Blocks.COBWEB) || st.isOf(Blocks.POWDER_SNOW) || st.isIn(BlockTags.BEDS);
    }

    /** Pearl sloti: hotbar 0-8, inventar 9-35 (fromInventory bo'lsa), yo'q - -1. */
    private static int pearlSlot(ClientPlayerEntity p, PearlConfig cfg) {
        int limit = cfg.fromInventory ? 36 : 9;
        for (int i = 0; i < limit; i++) {
            if (p.getInventory().getStack(i).isOf(Items.ENDER_PEARL)) return i;
        }
        return -1;
    }
}
