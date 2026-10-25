package uz.autocobweb;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import uz.spearlunger.InvHelper;
import uz.spearlunger.Rotations;

/**
 * Anti-web: o'zingiz cobweb ichida qolsangiz - suv paqirini tanlab ipga suv quyadi (ip yo'q bo'ladi), keyingi tickda
 * suvni paqirga qaytarib oladi va eski slotga qaytadi. Suv bo'lmasa (yoki Nether'da) qaychi/qilich bilan kesadi.
 * Qarash faqat paketlarda o'zgaradi - kamera qimirlamaydi.
 */
class AntiWeb {
    private enum Phase { IDLE, PICKUP, CUT }

    private Phase phase = Phase.IDLE;
    private BlockPos web = null;
    private int slot = -1, original = -1, ticks = 0;
    private int[] borrowed = null;

    /** true = shu tick band (ipdan chiqyapmiz), boshqa amal qilinmasin. */
    boolean tick(MinecraftClient c, ClientPlayerEntity p, CobwebConfig cfg) {
        ClientWorld w = c.world;
        switch (phase) {
            case PICKUP -> {
                ticks++;
                BlockPos src = waterSource(w, web);
                if (src != null && p.getInventory().getStack(slot).isOf(Items.BUCKET)) {
                    use(c, p, slot, Vec3d.ofCenter(src));
                    finish(c, p);
                } else if (ticks > 10) {
                    finish(c, p);
                }
                return true;
            }
            case CUT -> {
                ticks++;
                if (!isWeb(w, web) || ticks > 40) {
                    c.interactionManager.cancelBlockBreaking();
                    finish(c, p);
                    return true;
                }
                p.getInventory().setSelectedSlot(slot);
                c.interactionManager.updateBlockBreakingProgress(web, Direction.UP);
                p.swingHand(Hand.MAIN_HAND);
                return true;
            }
            default -> { }
        }

        BlockPos feet = p.getBlockPos();
        BlockPos target = isWeb(w, feet) ? feet : isWeb(w, feet.up()) ? feet.up() : null;
        if (target == null) return false;
        web = target;
        original = p.getInventory().getSelectedSlot();
        ticks = 0;

        boolean nether = w.getRegistryKey() == World.NETHER;   // u yerda suv bug'lanadi
        int bucket = nether ? -1 : prepare(c, p, Items.WATER_BUCKET);
        if (bucket >= 0) {
            slot = bucket;
            use(c, p, bucket, Vec3d.ofCenter(target));
            phase = Phase.PICKUP;
            say(p, "§bAnti-web: suv");
            return true;
        }
        if (!cfg.antiWebCut) return false;
        int tool = prepare(c, p, Items.SHEARS);
        if (tool < 0) tool = swordSlot(p);
        if (tool < 0) return false;
        slot = tool;
        p.getInventory().setSelectedSlot(tool);
        Vec3d eye = p.getEyePos(), to = Vec3d.ofCenter(target);
        float yaw = p.getYaw(), pitch = p.getPitch();
        Rotations.sendLook(p, Rotations.yawTo(eye, to), Rotations.pitchTo(eye, to));
        c.interactionManager.attackBlock(target, Direction.UP);
        p.swingHand(Hand.MAIN_HAND);
        Rotations.sendLook(p, yaw, pitch);
        phase = Phase.CUT;
        say(p, "§bAnti-web: kesyapman");
        return true;
    }

    /** Slotni tanlab, o'sha nuqtaga qarab ishlatadi (yo'nalish otish paketida), qarashni qaytaradi. */
    private void use(MinecraftClient c, ClientPlayerEntity p, int s, Vec3d at) {
        float yaw = p.getYaw(), pitch = p.getPitch();
        Vec3d eye = p.getEyePos();
        p.getInventory().setSelectedSlot(s);
        p.setYaw(Rotations.yawTo(eye, at));
        p.setPitch(Rotations.pitchTo(eye, at));
        c.interactionManager.interactItem(p, Hand.MAIN_HAND);
        p.swingHand(Hand.MAIN_HAND);
        p.setYaw(yaw);
        p.setPitch(pitch);
    }

    private void finish(MinecraftClient c, ClientPlayerEntity p) {
        if (original >= 0) p.getInventory().setSelectedSlot(original);
        if (borrowed != null && InvHelper.returnItems()) InvHelper.swap(c, p, borrowed[0], borrowed[1]);
        borrowed = null;
        phase = Phase.IDLE;
        web = null;
        slot = -1;
        original = -1;
    }

    /** Hotbar sloti; inventarda bo'lsa vaqtincha hotbar'ga olib keladi. */
    private int prepare(MinecraftClient c, ClientPlayerEntity p, Item item) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(item)) return i;
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

    private static int swordSlot(ClientPlayerEntity p) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isEmpty() && Registries.ITEM.getId(s.getItem()).getPath().endsWith("_sword")) return i;
        }
        return -1;
    }

    private static BlockPos waterSource(ClientWorld w, BlockPos around) {
        if (isSource(w, around)) return around;
        for (Direction d : Direction.values()) {
            if (isSource(w, around.offset(d))) return around.offset(d);
        }
        return null;
    }

    private static boolean isSource(ClientWorld w, BlockPos pos) {
        FluidState fs = w.getFluidState(pos);
        return fs.isStill() && fs.isOf(Fluids.WATER);
    }

    private static boolean isWeb(ClientWorld w, BlockPos pos) {
        return w.getBlockState(pos).isOf(Blocks.COBWEB);
    }

    private static void say(ClientPlayerEntity p, String msg) {
        if (CobwebConfig.get().messages) p.sendMessage(Text.literal(msg), true);
    }
}
