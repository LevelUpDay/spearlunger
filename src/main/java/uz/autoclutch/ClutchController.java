package uz.autoclutch;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import uz.spearlunger.InvHelper;
import uz.spearlunger.Rotations;

/**
 * Auto Clutch (MLG): baland joydan yiqilayotganda yerga tegishdan oldin tagingizga yiqilish zararini yo'q qiladigan
 * narsani qo'yadi - inventar yoki hotbar'da nima bo'lsa (ustuvorlik): suv paqiri, powder snow, slime, cobweb,
 * scaffolding, pichan. Suvni qo'ngach paqirga qaytarib oladi va eski slotga qaytadi.
 * Qarash faqat paketlarda o'zgaradi - kamera qimirlamaydi.
 */
public class ClutchController {
    private enum Kind { BUCKET, BLOCK }

    private record Choice(Item item, Kind kind, String name) {}

    private static final Choice[] ORDER = {
            new Choice(Items.WATER_BUCKET, Kind.BUCKET, "suv"),
            new Choice(Items.POWDER_SNOW_BUCKET, Kind.BUCKET, "powder snow"),
            new Choice(Items.SLIME_BLOCK, Kind.BLOCK, "slime"),
            new Choice(Items.COBWEB, Kind.BLOCK, "cobweb"),
            new Choice(Items.SCAFFOLDING, Kind.BLOCK, "scaffolding"),
            new Choice(Items.HAY_BLOCK, Kind.BLOCK, "pichan"),
    };

    private boolean doneThisFall = false;
    /** Suvni qaytarib olish: qo'yilgan joy, paqir sloti, kutish. */
    private BlockPos waterAt = null;
    private int bucketSlot = -1, pickupTicks = 0;
    private int[] borrowed = null;

    public void tick(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (p == null || c.world == null || c.interactionManager == null) {
            waterAt = null;
            return;
        }
        ClutchConfig cfg = ClutchConfig.get();
        while (AutoClutchClient.keyToggle.wasPressed()) {
            cfg.enabled = !cfg.enabled;
            ClutchConfig.save();
            p.sendMessage(Text.literal(cfg.enabled ? "§aAuto Clutch: ON" : "§cAuto Clutch: OFF"), true);
        }
        if (waterAt != null) {
            tickPickup(c, p, cfg);
            return;
        }
        if (p.isOnGround() || p.isTouchingWater() || p.isClimbing()) doneThisFall = false;
        if (!cfg.enabled || doneThisFall || c.currentScreen != null || p.isSpectator()) return;
        if (p.isOnGround() || p.isGliding() || p.getAbilities().flying || p.isTouchingWater() || p.hasVehicle()) return;
        double vy = p.getVelocity().y;
        if (vy > -0.4) return;

        ClientWorld w = c.world;
        BlockPos feet = p.getBlockPos();
        BlockPos ground = null;
        for (int i = 1; i <= 64 && feet.getY() - i >= w.getBottomY(); i++) {
            BlockPos b = feet.down(i);
            BlockState st = w.getBlockState(b);
            if (!st.getCollisionShape(w, b).isEmpty() || !st.getFluidState().isEmpty()) {
                ground = b;
                break;
            }
        }
        if (ground == null) return;   // void - Pearl Clutch'ga qoldiramiz
        BlockState gs = w.getBlockState(ground);
        if (soft(gs)) return;
        double landY = ground.getY() + 1;
        double h = p.getY() - landY;
        if (p.fallDistance + h < cfg.minFall) return;

        // yerga yetguncha oxirgi imkoniyat: keyingi tickda tegamiz yoki qo'l yetadigan masofa
        double next = -(vy - 0.08) * 0.98;
        double reach = p.getBlockInteractionRange();
        boolean inReach = p.getEyeY() - landY <= reach - 0.3;
        if (!(inReach && (h <= 3.0 || h - next <= 0.6))) return;

        BlockPos place = ground.up();
        if (!w.getBlockState(place).isReplaceable()) return;
        boolean nether = w.getRegistryKey() == World.NETHER;
        for (Choice ch : ORDER) {
            if (!allowed(ch, cfg) || (nether && ch.item == Items.WATER_BUCKET)) continue;
            if (ch.kind == Kind.BLOCK && !gs.isSideSolidFullSquare(w, ground, Direction.UP)) continue;
            int slot = prepare(c, p, ch.item, cfg);
            if (slot < 0) continue;
            int original = p.getInventory().getSelectedSlot();
            Vec3d top = Vec3d.ofCenter(ground).add(0, 0.5, 0);
            if (ch.kind == Kind.BUCKET) {
                use(c, p, slot, top);
                if (ch.item == Items.WATER_BUCKET && cfg.pickupWater) {
                    waterAt = place;
                    bucketSlot = slot;
                    pickupTicks = 0;
                    p.getInventory().setSelectedSlot(original);
                    doneThisFall = true;
                    say(p, "§bAuto Clutch: " + ch.name);
                    return;   // borrowed qaytarish suvni olgandan keyin
                }
            } else {
                float yaw = p.getYaw(), pitch = p.getPitch();
                Rotations.sendLook(p, yaw, 90.0f);
                p.getInventory().setSelectedSlot(slot);
                c.interactionManager.interactBlock(p, Hand.MAIN_HAND,
                        new BlockHitResult(top, Direction.UP, ground, false));
                p.swingHand(Hand.MAIN_HAND);
                Rotations.sendLook(p, yaw, pitch);
            }
            p.getInventory().setSelectedSlot(original);
            restoreBorrowed(c, p);
            doneThisFall = true;
            say(p, "§bAuto Clutch: " + ch.name);
            return;
        }
    }

    /** Qo'ngach (yoki suvga tushgach) suvni paqirga qaytarib olamiz. */
    private void tickPickup(MinecraftClient c, ClientPlayerEntity p, ClutchConfig cfg) {
        pickupTicks++;
        boolean landed = p.isOnGround() || p.isTouchingWater();
        BlockPos src = source(c.world, waterAt);
        if (landed && src != null && pickupTicks >= 2 && p.getInventory().getStack(bucketSlot).isOf(Items.BUCKET)) {
            int original = p.getInventory().getSelectedSlot();
            use(c, p, bucketSlot, Vec3d.ofCenter(src));
            p.getInventory().setSelectedSlot(original);
            waterAt = null;
        } else if (pickupTicks > 30) {
            waterAt = null;
        }
        if (waterAt == null) restoreBorrowed(c, p);
    }

    /** Slotni tanlab, o'sha nuqtaga qarab ishlatadi (yo'nalish ishlatish paketida), qarashni qaytaradi. */
    private static void use(MinecraftClient c, ClientPlayerEntity p, int slot, Vec3d at) {
        float yaw = p.getYaw(), pitch = p.getPitch();
        Vec3d eye = p.getEyePos();
        p.getInventory().setSelectedSlot(slot);
        p.setYaw(Rotations.yawTo(eye, at));
        p.setPitch(Rotations.pitchTo(eye, at));
        c.interactionManager.interactItem(p, Hand.MAIN_HAND);
        p.swingHand(Hand.MAIN_HAND);
        p.setYaw(yaw);
        p.setPitch(pitch);
    }

    private static boolean allowed(Choice ch, ClutchConfig cfg) {
        if (ch.item == Items.WATER_BUCKET) return cfg.water;
        if (ch.item == Items.POWDER_SNOW_BUCKET) return cfg.powderSnow;
        if (ch.item == Items.SLIME_BLOCK) return cfg.slime;
        if (ch.item == Items.COBWEB) return cfg.cobweb;
        if (ch.item == Items.SCAFFOLDING) return cfg.scaffolding;
        return cfg.hay;
    }

    /** Yiqilish zararini o'zi yo'q qiladigan yer. */
    private static boolean soft(BlockState st) {
        return !st.getFluidState().isEmpty() || st.isOf(Blocks.SLIME_BLOCK) || st.isOf(Blocks.COBWEB)
                || st.isOf(Blocks.POWDER_SNOW) || st.isOf(Blocks.HONEY_BLOCK) || st.isIn(BlockTags.BEDS)
                || st.isOf(Blocks.SCAFFOLDING) || st.isOf(Blocks.SWEET_BERRY_BUSH);
    }

    private static BlockPos source(ClientWorld w, BlockPos around) {
        if (isSource(w, around)) return around;
        for (Direction d : Direction.values()) if (isSource(w, around.offset(d))) return around.offset(d);
        return null;
    }

    private static boolean isSource(ClientWorld w, BlockPos pos) {
        FluidState fs = w.getFluidState(pos);
        return fs.isStill() && fs.isOf(Fluids.WATER);
    }

    /** Hotbar sloti; inventarda bo'lsa vaqtincha hotbar'ga olib keladi. */
    private int prepare(MinecraftClient c, ClientPlayerEntity p, Item item, ClutchConfig cfg) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(item)) return i;
        if (!cfg.fromInventory) return -1;
        for (int i = 9; i < 36; i++) {
            if (!p.getInventory().getStack(i).isOf(item)) continue;
            int sel = p.getInventory().getSelectedSlot();
            int target = sel == 8 ? 7 : 8;
            for (int h = 0; h < 9; h++) if (h != sel && p.getInventory().getStack(h).isEmpty()) { target = h; break; }
            InvHelper.swap(c, p, i, target);
            borrowed = new int[] { i, target };
            return target;
        }
        return -1;
    }

    private void restoreBorrowed(MinecraftClient c, ClientPlayerEntity p) {
        if (borrowed != null && InvHelper.returnItems()) InvHelper.swap(c, p, borrowed[0], borrowed[1]);
        borrowed = null;
    }

    private static void say(ClientPlayerEntity p, String msg) {
        if (ClutchConfig.get().messages) p.sendMessage(Text.literal(msg), true);
    }
}
