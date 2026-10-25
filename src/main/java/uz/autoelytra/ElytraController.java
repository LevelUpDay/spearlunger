package uz.autoelytra;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import uz.spearlunger.InvHelper;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.ArrayDeque;

/**
 * Firework bosilganda: hotbar (yoki inventar) dan elytra'ni topib kiyadi, kerak bo'lsa sakrab elytra'ni ochadi
 * va firework'ni ishlatadi. Qo'nganda avvalgi nagrudnikni qaytarib kiyadi.
 */
public class ElytraController {
    private interface Step { boolean run(); }

    private final ArrayDeque<Step> steps = new ArrayDeque<>();
    private int cooldown = 0;
    /** Elytra kiyilganda yechilgan nagrudnik (qo'nganda qaytariladi). */
    private Item savedChest = null;
    private int groundTicks = 0;
    private boolean warned = false;

    // ------------------------------------------------------------ firework bosilganda
    public ActionResult onUseItem(PlayerEntity player, World world, Hand hand) {
        if (!(player instanceof ClientPlayerEntity p)) return ActionResult.PASS;
        return onFirework(p, hand) ? ActionResult.FAIL : ActionResult.PASS;
    }

    /** true = o'zimiz bajaramiz, vanilla firework ishlatilmasin. */
    public boolean onFirework(ClientPlayerEntity p, Hand hand) {
        ElytraConfig cfg = ElytraConfig.get();
        MinecraftClient c = MinecraftClient.getInstance();
        if (!cfg.enabled || !p.getStackInHand(hand).isOf(Items.FIREWORK_ROCKET)) return false;
        if (p.isGliding() || p.isTouchingWater() || p.hasVehicle()) return false;
        if (!steps.isEmpty()) return true;

        boolean wearing = usable(p.getEquippedStack(EquipmentSlot.CHEST));
        int[] src = wearing ? null : findElytra(p, cfg.fromInventory);
        if (!wearing && src == null) {
            if (hasAnyElytra(p)) say(p, "§cAuto Elytra: yaroqli elytra yo'q (durability kam).");
            return false;
        }
        if (p.isOnGround() && !cfg.autoJump && wearing) return false;
        if (!p.isOnGround() && !cfg.autoDeploy && wearing) return false;

        final int original = p.getInventory().getSelectedSlot();
        if (!wearing) addEquip(c, p, src[0], src[1], original, true);
        if (p.isOnGround()) {
            if (!cfg.autoJump) return true;   // faqat kiydik
            steps.add(() -> {
                if (p.isOnGround()) p.jump();
                return true;
            });
        }
        if (!cfg.autoDeploy) return true;
        final int[] waited = {0};
        steps.add(() -> {
            // yerdan uzilgach 2 tick kutamiz, keyin ochamiz
            if (p.isOnGround()) return ++waited[0] > 10;
            if (++waited[0] < 2) return false;
            if (!p.isGliding() && p.checkGliding()) {
                p.networkHandler.sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
            return true;
        });
        if (cfg.autoFirework) {
            steps.add(() -> {
                if (!p.isGliding()) {
                    say(p, "§eAuto Elytra: elytra ochilmadi.");
                    return true;
                }
                if (hand == Hand.MAIN_HAND) p.getInventory().setSelectedSlot(original);
                if (p.getStackInHand(hand).isOf(Items.FIREWORK_ROCKET)) {
                    c.interactionManager.interactItem(p, hand);
                }
                return true;
            });
        }
        return true;
    }

    // ------------------------------------------------------------ tick
    public void tick(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (p == null || c.world == null || c.interactionManager == null) {
            steps.clear();
            savedChest = null;
            return;
        }
        ElytraConfig cfg = ElytraConfig.get();
        while (AutoElytraClient.keyToggle.wasPressed()) {
            cfg.enabled = !cfg.enabled;
            ElytraConfig.save();
            p.sendMessage(Text.literal(cfg.enabled ? "§aAuto Elytra: ON" : "§cAuto Elytra: OFF"), true);
        }

        if (!steps.isEmpty()) {
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            int n = 0;
            while (!steps.isEmpty() && n++ < 20) {
                if (!steps.peek().run()) return;
                steps.poll();
                cooldown = Math.max(0, cfg.delayTicks);
                if (cooldown > 0) return;
            }
            return;
        }
        if (!cfg.enabled || c.currentScreen != null) return;

        ItemStack chest = p.getEquippedStack(EquipmentSlot.CHEST);
        if (p.isGliding()) {
            groundTicks = 0;
            if (chest.isOf(Items.ELYTRA)) {
                int left = chest.getMaxDamage() - chest.getDamage();
                if (cfg.autoReplace && left <= Math.max(2, cfg.minDurability)) {
                    int[] src = findElytra(p, cfg.fromInventory);
                    if (src != null) {
                        say(p, "§eAuto Elytra: elytra almashtirildi.");
                        addEquip(c, p, src[0], src[1], p.getInventory().getSelectedSlot(), false);
                        return;
                    }
                }
                if (cfg.warnDurability > 0 && left <= cfg.warnDurability && !warned) {
                    warned = true;
                    say(p, "§cElytra durability: " + left + " qoldi!");
                    p.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 0.6f);
                }
            }
            return;
        }
        warned = false;

        if (savedChest == null) return;
        if (!cfg.restoreChest || !chest.isOf(Items.ELYTRA)) {
            savedChest = null;
            return;
        }
        if (!p.isOnGround()) {
            groundTicks = 0;
            return;
        }
        if (++groundTicks < cfg.landDelayTicks) return;
        Item want = savedChest;
        savedChest = null;
        int[] src = find(p, s -> s.isOf(want), true);
        if (src != null) addEquip(c, p, src[0], src[1], p.getInventory().getSelectedSlot(), false);
    }

    // ------------------------------------------------------------ kiyish
    /**
     * Elytra/nagrudnikni kiyadi. invSlot >= 9 bo'lsa avval hotbar'ga olib keladi, kiygach yechilgan narsani
     * o'sha inventar slotiga qaytaradi (SWAP simmetrik).
     */
    private void addEquip(MinecraftClient c, ClientPlayerEntity p, int hotbar, int invSlot, int original,
                          boolean rememberChest) {
        steps.add(() -> {
            ItemStack before = p.getEquippedStack(EquipmentSlot.CHEST);
            if (invSlot >= 9) InvHelper.swap(c, p, invSlot, hotbar);
            Item prev = before.isEmpty() ? null : before.getItem();
            p.getInventory().setSelectedSlot(hotbar);
            c.interactionManager.interactItem(p, Hand.MAIN_HAND);
            if (invSlot >= 9) InvHelper.swap(c, p, invSlot, hotbar);
            p.getInventory().setSelectedSlot(original);
            if (rememberChest && ElytraConfig.get().restoreChest && prev != null && prev != Items.ELYTRA) {
                savedChest = prev;
                groundTicks = 0;
            }
            return true;
        });
    }

    private interface Match { boolean test(ItemStack s); }

    /** Eng ko'p durability qolgan yaroqli elytra: {hotbar slot, inventar slot yoki -1}. */
    private int[] findElytra(ClientPlayerEntity p, boolean fromInventory) {
        int best = -1, bestLeft = -1;
        int limit = fromInventory ? 36 : 9;
        for (int i = 0; i < limit; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!usable(s)) continue;
            int left = s.getMaxDamage() - s.getDamage();
            // hotbar'dagini afzal ko'ramiz
            if (i < 9) left += 100000;
            if (left > bestLeft) { bestLeft = left; best = i; }
        }
        if (best < 0) return null;
        if (best < 9) return new int[] { best, -1 };
        return new int[] { freeHotbar(p), best };
    }

    private int[] find(ClientPlayerEntity p, Match m, boolean fromInventory) {
        for (int i = 0; i < 9; i++) {
            if (m.test(p.getInventory().getStack(i))) return new int[] { i, -1 };
        }
        if (!fromInventory) return null;
        for (int i = 9; i < 36; i++) {
            if (m.test(p.getInventory().getStack(i))) return new int[] { freeHotbar(p), i };
        }
        return null;
    }

    /** Inventardan olib kelish uchun hotbar sloti: bo'sh slot, bo'lmasa tanlanmagan istalgan slot. */
    private static int freeHotbar(ClientPlayerEntity p) {
        int sel = p.getInventory().getSelectedSlot();
        for (int h = 0; h < 9; h++) if (h != sel && p.getInventory().getStack(h).isEmpty()) return h;
        return sel == 8 ? 7 : 8;
    }

    private boolean usable(ItemStack s) {
        if (!s.isOf(Items.ELYTRA) || s.willBreakNextUse()) return false;
        return s.getMaxDamage() - s.getDamage() > ElytraConfig.get().minDurability;
    }

    private static boolean hasAnyElytra(ClientPlayerEntity p) {
        for (int i = 0; i < 36; i++) if (p.getInventory().getStack(i).isOf(Items.ELYTRA)) return true;
        return false;
    }

    // ------------------------------------------------------------ HUD
    public String hudLine(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (p == null || !ElytraConfig.get().hud || !p.isGliding()) return null;
        ItemStack chest = p.getEquippedStack(EquipmentSlot.CHEST);
        if (!chest.isOf(Items.ELYTRA)) return null;
        int left = chest.getMaxDamage() - chest.getDamage();
        int fw = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(Items.FIREWORK_ROCKET)) fw += s.getCount();
        }
        if (p.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) fw += p.getOffHandStack().getCount();
        String col = left <= ElytraConfig.get().warnDurability ? "§c" : "§a";
        return "§bElytra: " + col + left + "§7/" + chest.getMaxDamage() + " §7| Firework: §f" + fw;
    }

    private void say(ClientPlayerEntity p, String msg) {
        if (ElytraConfig.get().messages) p.sendMessage(Text.literal(msg), true);
    }
}
