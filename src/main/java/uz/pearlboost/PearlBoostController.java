package uz.pearlboost;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import uz.autotnt.AutoTntConfig;
import uz.pearlclutch.PearlClutchClient;
import uz.spearlunger.InvHelper;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Pearl Boost (H): qaragan tomoningizga ender pearl, keyin wind charge'ni AYNAN o'sha pearl'ga tegadigan qilib otadi -
 * wind charge pearl'ga tekkanda pearl "tushadi" va siz havodagi o'sha nuqtaga teleport bo'lasiz, keyin mace bilan
 * tushasiz. Ikkala snaryad ham otuvchining tezligini oladi (yurish, uchish, sakrash) - ikkalasida ham hisoblanadi.
 * Pearl Chase: dushman pearl otsa, uning tushadigan joyi yoniga o'zingiz ham pearl otasiz.
 */
public class PearlBoostController {
    private static final double PEARL_SPEED = 1.5, PEARL_GRAVITY = 0.03, DRAG = 0.99;
    /** Wind charge: tezlik 1.5, gravitatsiya va havo qarshiligi yo'q (to'g'ri chiziq). */
    private static final double WIND_SPEED = 1.5;

    private enum Phase { IDLE, WAIT_PEARL }

    private Phase phase = Phase.IDLE;
    private int ticks = 0;
    private EnderPearlEntity pearl = null;
    private Vec3d thrownFrom = null;
    private final java.util.List<int[]> borrowed = new java.util.ArrayList<>();
    private final Set<UUID> chased = new HashSet<>();

    public void tick(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (p == null || c.world == null || c.interactionManager == null) {
            phase = Phase.IDLE;
            chased.clear();
            return;
        }
        PearlBoostConfig cfg = PearlBoostConfig.get();
        boolean pressed = false;
        while (PearlBoostClient.keyBoost.wasPressed()) pressed = true;
        while (PearlBoostClient.keyChase.wasPressed()) {
            cfg.chase = !cfg.chase;
            PearlBoostConfig.save();
            p.sendMessage(Text.literal(cfg.chase ? "§aPearl Chase: ON" : "§cPearl Chase: OFF"), true);
        }
        if (InvHelper.screenOpen(c)) return;

        if (pressed) {
            if (phase != Phase.IDLE) {
                finish(c, p, "§ePearl Boost: bekor qilindi");
                return;
            }
            if (cfg.enabled) start(c, p, cfg);
            return;
        }
        if (phase == Phase.WAIT_PEARL) tickWait(c, p, cfg);
        else if (cfg.enabled && cfg.chase) tickChase(c, p, cfg);
    }

    // ------------------------------------------------------------ pearl
    private void start(MinecraftClient c, ClientPlayerEntity p, PearlBoostConfig cfg) {
        int pearlSlot = slot(c, p, Items.ENDER_PEARL, cfg);
        if (pearlSlot < 0) { say(p, "§cPearl Boost: ender pearl yo'q"); restore(c, p); return; }
        int windSlot = slot(c, p, Items.WIND_CHARGE, cfg);
        if (windSlot < 0) { say(p, "§cPearl Boost: wind charge yo'q"); restore(c, p); return; }
        if (p.getItemCooldownManager().isCoolingDown(p.getInventory().getStack(pearlSlot))) {
            say(p, "§ePearl Boost: pearl cooldown'da");
            restore(c, p);
            return;
        }
        // qaragan yo'nalishga: turib otgandagi boshlang'ich tezlik bilan bir xil bo'lishi uchun o'z tezligimizni ayiramiz
        Vec3d want = Vec3d.fromPolar(p.getPitch(), p.getYaw()).multiply(PEARL_SPEED);
        Vec3d dir = want.subtract(shooterVelocity(p));
        if (dir.lengthSquared() < 1e-4) dir = want;
        throwItem(c, p, pearlSlot, dir);
        phase = Phase.WAIT_PEARL;
        ticks = 0;
        pearl = null;
        thrownFrom = p.getEyePos();
        say(p, "§dPearl Boost: pearl");
    }

    /** Pearl'ni kuzatamiz va wind charge pearl'ga tegadigan paytda otamiz. */
    private void tickWait(MinecraftClient c, ClientPlayerEntity p, PearlBoostConfig cfg) {
        ticks++;
        if (pearl == null || pearl.isRemoved()) {
            if (pearl != null) { finish(c, p, null); return; }   // allaqachon tushdi
            pearl = findOwnPearl(c.world, p);
            if (pearl == null) {
                if (ticks > 5) finish(c, p, "§ePearl Boost: pearl ko'rinmadi");
                return;
            }
        }
        if (ticks > 80) { finish(c, p, "§ePearl Boost: vaqt tugadi"); return; }

        // pearl kelajakdagi yo'li (bloklarga tegsa to'xtaydi)
        int n = 90;
        Vec3d[] path = new Vec3d[n + 1];
        Vec3d pos = pearl.getEntityPos(), v = pearl.getVelocity();
        path[0] = pos;
        int apex = -1, last = n;
        for (int k = 1; k <= n; k++) {
            Vec3d next = pos.add(v);
            BlockHitResult r = c.world.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, pearl));
            if (r.getType() == HitResult.Type.BLOCK) { last = k - 1; break; }
            if (apex < 0 && v.y <= 0) apex = k - 1;
            path[k] = next;
            pos = next;
            v = v.multiply(DRAG).add(0, -PEARL_GRAVITY, 0);
        }
        if (last < 1) { finish(c, p, null); return; }

        // hozir otsak wind charge pearl'ga qaysi tickda yetadi: |P(k) - start - s*k| / k = 1.5
        Vec3d start = windSpawn(p), s = shooterVelocity(p);
        double hitK = -1;
        double prev = Double.MAX_VALUE;
        for (int k = 1; k <= last; k++) {
            double need = path[k].subtract(start).subtract(s.multiply(k)).length() / k;
            if (need <= WIND_SPEED) {
                // oldingi tick bilan chiziqli aniqlashtiramiz
                hitK = prev == Double.MAX_VALUE ? k : (k - 1) + (prev - WIND_SPEED) / (prev - need);
                break;
            }
            prev = need;
        }
        if (hitK < 0) return;   // hali yetolmaydi - kutamiz
        // eng baland teleport uchun pearl cho'qqisiga yaqin uramiz
        if (cfg.timing == 0 && apex > 0 && hitK < apex - 1 && last > apex) return;

        int k0 = (int) Math.floor(hitK);
        double frac = hitK - k0;
        Vec3d at = path[Math.min(k0, last)].lerp(path[Math.min(k0 + 1, last)], frac);
        Vec3d vel = at.subtract(start).multiply(1.0 / hitK);          // kerakli haqiqiy tezlik
        Vec3d dir = vel.subtract(s);                                  // otish yo'nalishi (o'z tezligimizsiz)
        int windSlot = slot(c, p, Items.WIND_CHARGE, cfg);
        if (windSlot < 0) { finish(c, p, "§cPearl Boost: wind charge yo'q"); return; }
        throwItem(c, p, windSlot, dir);
        finish(c, p, String.format("§bPearl Boost: wind charge (%.0f blok balandda)", at.y - p.getY()));
    }

    // ------------------------------------------------------------ pearl chase
    private void tickChase(MinecraftClient c, ClientPlayerEntity p, PearlBoostConfig cfg) {
        for (EnderPearlEntity e : c.world.getEntitiesByClass(EnderPearlEntity.class, p.getBoundingBox().expand(64),
                x -> !chased.contains(x.getUuid()))) {
            Entity owner = e.getOwner();
            if (owner == null || owner == p) continue;
            if (owner instanceof PlayerEntity pl && cfg.ignoreFriends && isFriend(pl)) continue;
            chased.add(e.getUuid());
            Vec3d land = landing(c.world, e);
            if (land == null || land.distanceTo(p.getEntityPos()) > cfg.chaseRange) continue;
            Vec3d toMe = p.getEntityPos().subtract(land);
            Vec3d flat = new Vec3d(toMe.x, 0, toMe.z);
            Vec3d point = flat.lengthSquared() < 1e-3 ? land
                    : land.add(flat.normalize().multiply(cfg.chaseShortTenths / 10.0));
            if (PearlClutchClient.CONTROLLER.throwAt(c, p, point)) {
                say(p, "§dPearl Chase: " + owner.getName().getString());
                return;
            }
        }
        if (chased.size() > 256) chased.clear();
    }

    private static Vec3d landing(ClientWorld w, EnderPearlEntity e) {
        Vec3d pos = e.getEntityPos(), v = e.getVelocity();
        for (int i = 0; i < 300; i++) {
            Vec3d next = pos.add(v);
            BlockHitResult r = w.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, e));
            if (r.getType() == HitResult.Type.BLOCK) return r.getPos();
            pos = next;
            v = v.multiply(DRAG).add(0, -PEARL_GRAVITY, 0);
        }
        return null;
    }

    // ------------------------------------------------------------ yordamchilar
    /** Yo'nalishni faqat ishlatish paketiga yozib otadi va slot/qarashni darhol qaytaradi. */
    private static void throwItem(MinecraftClient c, ClientPlayerEntity p, int slot, Vec3d dir) {
        Vec3d d = dir.normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, d.y))));
        int original = p.getInventory().getSelectedSlot();
        float oy = p.getYaw(), op = p.getPitch();
        p.getInventory().setSelectedSlot(slot);
        p.setYaw(yaw);
        p.setPitch(pitch);
        c.interactionManager.interactItem(p, Hand.MAIN_HAND);
        p.swingHand(Hand.MAIN_HAND);
        p.setYaw(oy);
        p.setPitch(op);
        p.getInventory().setSelectedSlot(original);
    }

    /** Snaryadlar otuvchi tezligini oladi: x/z har doim, y - faqat havoda. */
    private static Vec3d shooterVelocity(ClientPlayerEntity p) {
        Vec3d v = p.getVelocity();
        return new Vec3d(v.x, p.isOnGround() ? 0 : v.y, v.z);
    }

    private static Vec3d windSpawn(ClientPlayerEntity p) {
        return p.getEyePos().add(0, -0.1, 0);
    }

    private EnderPearlEntity findOwnPearl(ClientWorld w, ClientPlayerEntity p) {
        EnderPearlEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (EnderPearlEntity e : w.getEntitiesByClass(EnderPearlEntity.class, p.getBoundingBox().expand(16), x -> true)) {
            if (e.getOwner() != null && e.getOwner() != p) continue;
            double d = e.getEntityPos().distanceTo(thrownFrom);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** Hotbar sloti; inventarda bo'lsa vaqtincha hotbar'ga olib keladi (keyin qaytariladi). */
    private int slot(MinecraftClient c, ClientPlayerEntity p, Item item, PearlBoostConfig cfg) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(item)) return i;
        if (!cfg.fromInventory) return -1;
        for (int i = 9; i < 36; i++) {
            if (!p.getInventory().getStack(i).isOf(item)) continue;
            int sel = p.getInventory().getSelectedSlot();
            int target = -1;
            for (int h = 8; h >= 0; h--) {
                if (h == sel || !p.getInventory().getStack(h).isEmpty()) continue;
                boolean used = false;
                for (int[] b : borrowed) if (b[1] == h) used = true;
                if (!used) { target = h; break; }
            }
            if (target < 0) target = sel == 8 ? 7 : 8;
            InvHelper.swap(c, p, i, target);
            borrowed.add(new int[] { i, target });
            return target;
        }
        return -1;
    }

    private void restore(MinecraftClient c, ClientPlayerEntity p) {
        if (InvHelper.returnItems()) {
            for (int i = borrowed.size() - 1; i >= 0; i--) InvHelper.swap(c, p, borrowed.get(i)[0], borrowed.get(i)[1]);
        }
        borrowed.clear();
    }

    private void finish(MinecraftClient c, ClientPlayerEntity p, String msg) {
        phase = Phase.IDLE;
        pearl = null;
        restore(c, p);
        if (msg != null) say(p, msg);
    }

    private static boolean isFriend(PlayerEntity t) {
        String name = t.getName().getString();
        for (String f : AutoTntConfig.get().friends) if (f.equalsIgnoreCase(name)) return true;
        return false;
    }

    private static void say(ClientPlayerEntity p, String msg) {
        if (PearlBoostConfig.get().messages) p.sendMessage(Text.literal(msg), true);
    }
}
