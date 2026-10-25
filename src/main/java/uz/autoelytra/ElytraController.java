package uz.autoelytra;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import uz.autotnt.AutoTntConfig;
import uz.spearlunger.InvHelper;

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
    private boolean unequippedThisFlight = false;

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
            // har tickda bitta qadam: tanlash, kiyish, qaytish ko'rinib turadi
            if (steps.peek().run()) {
                steps.poll();
                cooldown = Math.max(0, cfg.delayTicks);
            }
            return;
        }
        if (!cfg.enabled || c.currentScreen != null) return;

        ItemStack chest = p.getEquippedStack(EquipmentSlot.CHEST);
        if (p.isOnGround() || p.isTouchingWater()) unequippedThisFlight = false;
        if (p.isGliding()) {
            groundTicks = 0;
            if (chest.isOf(Items.ELYTRA) && tryUnequip(c, p, cfg)) return;
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
        // 1-tick: (kerak bo'lsa inventardan olib kelib) elytra slotini tanlaymiz
        steps.add(() -> {
            if (invSlot >= 9) InvHelper.swap(c, p, invSlot, hotbar);
            p.getInventory().setSelectedSlot(hotbar);
            return true;
        });
        // 2-tick: o'ng tugma - kiyamiz
        steps.add(() -> {
            ItemStack before = p.getEquippedStack(EquipmentSlot.CHEST);
            Item prev = before.isEmpty() ? null : before.getItem();
            p.getInventory().setSelectedSlot(hotbar);
            c.interactionManager.interactItem(p, Hand.MAIN_HAND);
            p.swingHand(Hand.MAIN_HAND);
            if (rememberChest && ElytraConfig.get().restoreChest && prev != null && prev != Items.ELYTRA) {
                savedChest = prev;
                groundTicks = 0;
            }
            return true;
        });
        // 3-tick: yechilgan narsani joyiga, o'zimiz eski slotga (firework)
        steps.add(() -> {
            if (invSlot >= 9) InvHelper.swap(c, p, invSlot, hotbar);
            p.getInventory().setSelectedSlot(original);
            return true;
        });
    }

    // ------------------------------------------------------------ dushman oldida yechish (mace)
    /** Uchib kelib dushman ustiga yetganda elytra'ni yechadi (nagrudnik kiyadi), pastga tushasiz va mace qo'lda. */
    private boolean tryUnequip(MinecraftClient c, ClientPlayerEntity p, ElytraConfig cfg) {
        if (!cfg.unequipNearEnemy || unequippedThisFlight) return false;
        PlayerEntity enemy = null;
        double best = cfg.unequipDist;
        for (PlayerEntity e : c.world.getPlayers()) {
            if (e == p || !e.isAlive() || e.isSpectator() || e.isCreative() || isFriend(e)) continue;
            double d = p.distanceTo(e);
            if (d <= best && p.getY() - e.getY() >= cfg.unequipMinAbove) {
                best = d;
                enemy = e;
            }
        }
        if (enemy == null) return false;
        unequippedThisFlight = true;
        int original = p.getInventory().getSelectedSlot();
        int[] plate = findChestplate(p, cfg.fromInventory);
        if (plate != null) {
            addEquip(c, p, plate[0], plate[1], original, false);
        } else {
            // nagrudnik yo'q: elytra'ni inventarga olamiz (zirh sloti 6 = ko'krak)
            steps.add(() -> {
                InvHelper.quickMove(c, p, 6);
                return true;
            });
        }
        if (cfg.unequipToMace) {
            steps.add(() -> {
                int mace = -1;
                for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(Items.MACE)) { mace = i; break; }
                if (mace >= 0) p.getInventory().setSelectedSlot(mace);
                return true;
            });
        }
        say(p, "§cAuto Elytra: " + enemy.getName().getString() + " - elytra yechildi");
        return true;
    }

    /** Eng yaxshi nagrudnik (elytra emas): {hotbar slot, inventar slot yoki -1}. */
    private int[] findChestplate(ClientPlayerEntity p, boolean fromInventory) {
        int best = -1, bestRank = -1;
        int limit = fromInventory ? 36 : 9;
        for (int i = 0; i < limit; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isEmpty() || s.isOf(Items.ELYTRA)) continue;
            EquippableComponent eq = s.get(DataComponentTypes.EQUIPPABLE);
            if (eq == null || eq.slot() != EquipmentSlot.CHEST) continue;
            String path = Registries.ITEM.getId(s.getItem()).getPath();
            int r = path.startsWith("netherite") ? 6 : path.startsWith("diamond") ? 5 : path.startsWith("iron") ? 4
                    : path.startsWith("copper") ? 3 : path.startsWith("chainmail") ? 2 : 1;
            if (i < 9) r += 10;   // hotbar'dagi tezroq
            if (r > bestRank) { bestRank = r; best = i; }
        }
        if (best < 0) return null;
        if (best < 9) return new int[] { best, -1 };
        return new int[] { freeHotbar(p), best };
    }

    private static boolean isFriend(PlayerEntity t) {
        String name = t.getName().getString();
        for (String f : AutoTntConfig.get().friends) if (f.equalsIgnoreCase(name)) return true;
        return false;
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
