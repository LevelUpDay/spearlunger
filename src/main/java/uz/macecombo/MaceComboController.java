package uz.macecombo;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import uz.autotnt.AutoTntConfig;
import uz.pearlclutch.PearlClutchClient;
import uz.spearlunger.InvHelper;
import uz.spearlunger.Rotations;

/**
 * Mace Combo (J): bitta bosishda kombinatsiya -
 *  1. nishon uzoq bo'lsa ender pearl uning oldiga (pearl yetib borguncha nishon qayerga yurishini va o'zingizning
 *     tezligingizni - yugurish, sakrash, uchish - hisoblab);
 *  2. yaqinlashgach oyog'ingiz ostiga wind charge (pastga qarash faqat paketda) - tepaga uchasiz;
 *  3. tushishda nishonga qarab turadi va urish masofasiga kirishi bilan mace'ga o'tib uradi
 *     (zirhli nishonga Breach, aks holda Density) - almashtirish va zarba bir tickda.
 */
public class MaceComboController {
    private enum Phase { IDLE, PEARL_WAIT, APPROACH, RISE }

    private Phase phase = Phase.IDLE;
    private PlayerEntity target = null;
    private Vec3d lastTargetPos = null, targetVel = Vec3d.ZERO, lastSelf = null;
    private int ticks = 0;

    public void tick(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (p == null || c.world == null || c.interactionManager == null) {
            phase = Phase.IDLE;
            target = null;
            return;
        }
        MaceComboConfig cfg = MaceComboConfig.get();
        boolean pressed = false;
        while (MaceComboClient.keyCombo.wasPressed()) pressed = true;
        if (pressed) {
            if (phase != Phase.IDLE) {
                stop(p, "§eMace Combo: bekor qilindi");
                return;
            }
            if (!cfg.enabled) return;
            target = pick(c.world, p, cfg);
            if (target == null) {
                say(p, "§cMace Combo: nishon yo'q");
                return;
            }
            if (mace(p, target) < 0) {
                say(p, "§cMace Combo: hotbar'da mace yo'q");
                target = null;
                return;
            }
            lastTargetPos = null;
            ticks = 0;
            phase = Phase.APPROACH;
            say(p, "§aMace Combo: " + target.getName().getString());
        }
        if (phase == Phase.IDLE) return;
        if (target == null || !target.isAlive() || target.isRemoved() || ++ticks > 200 || InvHelper.screenOpen(c)) {
            stop(p, null);
            return;
        }
        // nishon tezligi (o'rtacha)
        Vec3d tp = target.getEntityPos();
        if (lastTargetPos != null) targetVel = targetVel.multiply(0.4).add(tp.subtract(lastTargetPos).multiply(0.6));
        lastTargetPos = tp;

        Vec3d eye = p.getEyePos();
        Vec3d center = target.getBoundingBox().getCenter();
        double dist = p.distanceTo(target);

        switch (phase) {
            case PEARL_WAIT -> {
                // teleport bo'ldimi (bir tickda katta sakrash) yoki pearl tushib ketdi
                Vec3d self = p.getEntityPos();
                boolean teleported = lastSelf != null && self.distanceTo(lastSelf) > 4;
                lastSelf = self;
                if (teleported || ticks > 70) {
                    phase = Phase.APPROACH;
                    ticks = 0;
                }
            }
            case APPROACH -> {
                if (cfg.pearl && dist > cfg.pearlDist && p.isOnGround()) {
                    Vec3d point = landingNear(c.world, p, target, cfg);
                    if (point != null && PearlClutchClient.CONTROLLER.throwLead(c, p, point, targetVel)) {
                        phase = Phase.PEARL_WAIT;
                        lastSelf = p.getEntityPos();
                        ticks = 0;
                        say(p, "§dMace Combo: pearl");
                        return;
                    }
                }
                Rotations.turn(p, Rotations.yawTo(eye, center), p.getPitch());
                if (dist <= cfg.windDist && nearGround(c.world, p)) {
                    int wind = find(p);
                    if (wind < 0) {
                        stop(p, "§cMace Combo: wind charge yo'q");
                        return;
                    }
                    launch(c, p, wind, Rotations.yawTo(eye, center));
                    phase = Phase.RISE;
                    ticks = 0;
                    say(p, "§bMace Combo: wind charge");
                }
            }
            case RISE -> {
                Rotations.turn(p, Rotations.yawTo(eye, center), Rotations.pitchTo(eye, center));
                double reach = cfg.reachTenths / 10.0;
                boolean falling = p.getVelocity().y < 0 && p.fallDistance >= 1.5;
                if (falling && eye.distanceTo(closest(target, eye)) <= reach) {
                    int m = mace(p, target);
                    if (m >= 0) {
                        p.getInventory().setSelectedSlot(m);   // bir tickda almashtirib uramiz
                        c.interactionManager.attackEntity(p, target);
                        p.swingHand(Hand.MAIN_HAND);
                        stop(p, String.format("§aMace Combo: urildi (%.1f blok)", p.fallDistance));
                        return;
                    }
                }
                if (ticks > 8 && p.isOnGround()) stop(p, "§eMace Combo: yetmadi");
            }
            default -> { }
        }
    }

    private void stop(ClientPlayerEntity p, String msg) {
        phase = Phase.IDLE;
        target = null;
        if (msg != null) say(p, msg);
    }

    /** Pastga wind charge (qarash faqat paketda) va shu tickda eski slotga. */
    private static void launch(MinecraftClient c, ClientPlayerEntity p, int wind, float yaw) {
        int original = p.getInventory().getSelectedSlot();
        float oy = p.getYaw(), op = p.getPitch();
        p.getInventory().setSelectedSlot(wind);
        p.setYaw(yaw);
        p.setPitch(90.0f);
        c.interactionManager.interactItem(p, Hand.MAIN_HAND);
        p.swingHand(Hand.MAIN_HAND);
        p.setYaw(oy);
        p.setPitch(op);
        p.getInventory().setSelectedSlot(original);
    }

    /** Pearl tushadigan joy: nishondan biroz oldinda (bizga tomon), qattiq yer ustida. */
    private static Vec3d landingNear(ClientWorld w, ClientPlayerEntity p, PlayerEntity t, MaceComboConfig cfg) {
        Vec3d toMe = p.getEntityPos().subtract(t.getEntityPos());
        Vec3d flat = new Vec3d(toMe.x, 0, toMe.z);
        if (flat.lengthSquared() < 1e-3) flat = new Vec3d(1, 0, 0);
        Vec3d at = t.getEntityPos().add(flat.normalize().multiply(cfg.pearlShortTenths / 10.0));
        BlockPos col = BlockPos.ofFloored(at.x, t.getY() + 2, at.z);
        for (int i = 0; i < 8; i++) {
            BlockPos b = col.down(i);
            BlockState st = w.getBlockState(b);
            if (st.isSideSolidFullSquare(w, b, Direction.UP)) return Vec3d.ofCenter(b).add(0, 0.5, 0);
        }
        return null;
    }

    private static boolean nearGround(ClientWorld w, ClientPlayerEntity p) {
        if (p.isOnGround()) return true;
        BlockPos b = p.getBlockPos();
        return !w.getBlockState(b.down()).getCollisionShape(w, b.down()).isEmpty();
    }

    private static PlayerEntity pick(ClientWorld w, ClientPlayerEntity p, MaceComboConfig cfg) {
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

    private static int find(ClientPlayerEntity p) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(Items.WIND_CHARGE) && !p.getItemCooldownManager().isCoolingDown(s)) return i;
        }
        return -1;
    }

    /** Zirhli nishonga Breach, aks holda Density. */
    private static int mace(ClientPlayerEntity p, PlayerEntity target) {
        boolean armored = target.getArmor() >= 12;
        int best = -1, bestScore = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isOf(Items.MACE)) continue;
            int score = 1 + (armored ? level(s, "breach") * 3 + level(s, "density")
                    : level(s, "density") * 3 + level(s, "breach"));
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private static int level(ItemStack s, String name) {
        for (RegistryEntry<Enchantment> e : s.getEnchantments().getEnchantments()) {
            if (e.getKey().map(k -> k.getValue().getPath().equals(name)).orElse(false)) {
                return s.getEnchantments().getLevel(e);
            }
        }
        return 0;
    }

    private static Vec3d closest(PlayerEntity t, Vec3d from) {
        var b = t.getBoundingBox();
        return new Vec3d(MathHelper.clamp(from.x, b.minX, b.maxX), MathHelper.clamp(from.y, b.minY, b.maxY),
                MathHelper.clamp(from.z, b.minZ, b.maxZ));
    }

    private static boolean isFriend(PlayerEntity t) {
        String name = t.getName().getString();
        for (String f : AutoTntConfig.get().friends) if (f.equalsIgnoreCase(name)) return true;
        return false;
    }

    private static void say(ClientPlayerEntity p, String msg) {
        if (MaceComboConfig.get().messages) p.sendMessage(Text.literal(msg), true);
    }
}
