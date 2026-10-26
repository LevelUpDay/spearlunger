package uz.spearlunger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;

/** Elytra / nagrudnik kiyish, firework va elytra ochish - Auto Travel va Elytra PvP uchun umumiy. */
public final class ElytraHelper {
    private ElytraHelper() {}

    /** Slotdagi kiyimni o'ng tugma bilan kiyadi (almashtiradi); inventardan bo'lsa E orqali. Hammasi shu tickda. */
    public static void equip(MinecraftClient c, ClientPlayerEntity p, int slot) {
        int original = p.getInventory().getSelectedSlot();
        int hb = slot < 9 ? slot : original;
        if (slot >= 9) InvHelper.swap(c, p, slot, hb);
        p.getInventory().setSelectedSlot(hb);
        c.interactionManager.interactItem(p, Hand.MAIN_HAND);
        p.swingHand(Hand.MAIN_HAND);
        if (slot >= 9) InvHelper.swap(c, p, slot, hb);
        p.getInventory().setSelectedSlot(original);
    }

    public static int durability(ItemStack s) {
        return s.getMaxDamage() - s.getDamage();
    }

    public static boolean wearingElytra(ClientPlayerEntity p) {
        return p.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    /** -1 = yaroqli elytra kiyilgan, 0..35 = inventardagi eng yaxshi elytra sloti, -2 = yo'q. */
    public static int elytraSlot(ClientPlayerEntity p, int minDurability) {
        ItemStack chest = p.getEquippedStack(EquipmentSlot.CHEST);
        if (chest.isOf(Items.ELYTRA) && durability(chest) > minDurability) return -1;
        int best = -2, bestDur = minDurability;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isOf(Items.ELYTRA)) continue;
            if (durability(s) > bestDur) {
                bestDur = durability(s);
                best = i;
            }
        }
        return best;
    }

    /** Eng yaxshi nagrudnik sloti (0..35) yoki -1. */
    public static int chestplateSlot(ClientPlayerEntity p) {
        int best = -1, bestRank = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            String id = Registries.ITEM.getId(s.getItem()).getPath();
            if (!id.endsWith("_chestplate")) continue;
            int rank = id.startsWith("netherite") ? 6 : id.startsWith("diamond") ? 5 : id.startsWith("iron") ? 4
                    : id.startsWith("chainmail") ? 3 : id.startsWith("golden") ? 2 : 1;
            if (rank > bestRank) {
                bestRank = rank;
                best = i;
            }
        }
        return best;
    }

    /** Elytra'ni yechadi: nagrudnik bo'lsa kiyadi, bo'lmasa elytra'ni inventarga oladi. Shu tickda. */
    public static void unequipElytra(MinecraftClient c, ClientPlayerEntity p) {
        if (!wearingElytra(p)) return;
        int plate = chestplateSlot(p);
        if (plate >= 0) equip(c, p, plate);
        else InvHelper.quickMove(c, p, 6);   // zirh sloti 6 = ko'krak
    }

    public static int count(ClientPlayerEntity p, net.minecraft.item.Item item) {
        int n = p.getOffHandStack().isOf(item) ? p.getOffHandStack().getCount() : 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(item)) n += s.getCount();
        }
        return n;
    }

    public static int fireworks(ClientPlayerEntity p) {
        return count(p, Items.FIREWORK_ROCKET);
    }

    /** Firework ishlatadi (chap qo'l, hotbar yoki inventardan) va qo'lni joyiga qaytaradi. */
    public static boolean firework(MinecraftClient c, ClientPlayerEntity p) {
        if (p.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
            c.interactionManager.interactItem(p, Hand.OFF_HAND);
            return true;
        }
        int slot = -1;
        for (int i = 0; i < 36; i++) {
            if (p.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) return false;
        int original = p.getInventory().getSelectedSlot();
        int hb = slot < 9 ? slot : original;
        if (slot >= 9) InvHelper.swap(c, p, slot, hb);
        p.getInventory().setSelectedSlot(hb);
        c.interactionManager.interactItem(p, Hand.MAIN_HAND);
        p.swingHand(Hand.MAIN_HAND);
        if (slot >= 9) InvHelper.swap(c, p, slot, hb);
        p.getInventory().setSelectedSlot(original);
        return true;
    }

    /** Havoda elytra'ni ochadi (vanilla kabi). Ochilsa true. */
    public static boolean deploy(ClientPlayerEntity p) {
        if (p.isGliding()) return true;
        if (!p.isOnGround() && p.checkGliding()) {
            p.networkHandler.sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            return true;
        }
        return false;
    }
}
