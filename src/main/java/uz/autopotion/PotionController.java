package uz.autopotion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

/**
 * Jon kam qolganda hotbar'dagi Healing splash damlamasini oyoq ostiga otadi va darhol eski slot/qarashga qaytadi.
 * "Silent" rejimda kamera umuman qimirlamaydi: pastga qarash faqat otish paketida serverga ketadi.
 * Qo'shimcha: Regeneration zaxira, buff damlamalar (Strength, Speed, yonganda Fire Resistance), inventardan to'ldirish.
 */
public class PotionController {
    private int cooldown = 0;
    /** Ko'rinadigan rejim: avval pastga qaraymiz, keyingi tickda otamiz. */
    private int pendingSlot = -1;
    private float savedPitch;
    private boolean warned = false;

    public void tick(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (p == null || c.world == null || c.interactionManager == null) {
            pendingSlot = -1;
            return;
        }
        PotionConfig cfg = PotionConfig.get();
        while (AutoPotionClient.keyToggle.wasPressed()) {
            cfg.enabled = !cfg.enabled;
            PotionConfig.save();
            p.sendMessage(Text.literal(cfg.enabled ? "§aAuto Potion: ON" : "§cAuto Potion: OFF"), true);
        }
        if (pendingSlot >= 0) {
            int s = pendingSlot;
            pendingSlot = -1;
            if (cfg.enabled && c.currentScreen == null) throwFrom(c, p, s, cfg);
            p.setPitch(savedPitch);
            return;
        }
        if (!cfg.enabled || c.currentScreen != null || p.isSpectator() || p.getAbilities().creativeMode || !p.isAlive()) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        float hp = p.getHealth() + (cfg.countAbsorption ? p.getAbsorptionAmount() : 0);
        if (hp <= cfg.health) {
            int s = find(c, p, cfg, StatusEffects.INSTANT_HEALTH);
            if (s < 0 && cfg.useRegen && !p.hasStatusEffect(StatusEffects.REGENERATION)) {
                s = find(c, p, cfg, StatusEffects.REGENERATION);
            }
            if (s >= 0) {
                warned = false;
                use(c, p, s, cfg);
                return;
            }
            if (!warned && cfg.messages) {
                warned = true;
                p.sendMessage(Text.literal("§cAuto Potion: healing damlama qolmadi!"), true);
                p.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 1.0f, 0.5f);
            }
            return;
        }
        warned = false;

        // buff'lar (faqat hech narsa ishlatmayotganda)
        if (p.isUsingItem()) return;
        if (cfg.buffFireRes && (p.isOnFire() || p.isInLava()) && !p.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)) {
            if (tryBuff(c, p, cfg, StatusEffects.FIRE_RESISTANCE)) return;
        }
        if (cfg.buffStrength && !p.hasStatusEffect(StatusEffects.STRENGTH)) {
            if (tryBuff(c, p, cfg, StatusEffects.STRENGTH)) return;
        }
        if (cfg.buffSpeed && !p.hasStatusEffect(StatusEffects.SPEED)) {
            tryBuff(c, p, cfg, StatusEffects.SPEED);
        }
    }

    private boolean tryBuff(MinecraftClient c, ClientPlayerEntity p, PotionConfig cfg, RegistryEntry<StatusEffect> eff) {
        int s = find(c, p, cfg, eff);
        if (s < 0) return false;
        use(c, p, s, cfg);
        return true;
    }

    private void use(MinecraftClient c, ClientPlayerEntity p, int slot, PotionConfig cfg) {
        if (p.isUsingItem()) {
            if (!cfg.interrupt) return;
            c.options.useKey.setPressed(false);
            c.interactionManager.stopUsingItem(p);
        }
        if (cfg.silent) {
            float pitch = p.getPitch();
            p.setPitch(90.0f);
            throwFrom(c, p, slot, cfg);
            p.setPitch(pitch);
        } else {
            savedPitch = p.getPitch();
            p.setPitch(90.0f);
            pendingSlot = slot;
        }
    }

    /** Slotni tanlab otadi va darhol eski slotga qaytadi (yaw/pitch otish paketiga yoziladi). */
    private void throwFrom(MinecraftClient c, ClientPlayerEntity p, int slot, PotionConfig cfg) {
        ItemStack stack = p.getInventory().getStack(slot);
        if (!isThrowable(stack, cfg)) return;
        RegistryEntry<StatusEffect> kind = mainEffect(stack);
        int original = p.getInventory().getSelectedSlot();
        p.getInventory().setSelectedSlot(slot);
        c.interactionManager.interactItem(p, Hand.MAIN_HAND);
        p.getInventory().setSelectedSlot(original);
        cooldown = Math.max(1, cfg.cooldownTicks);
        if (cfg.refill && kind != null && p.getInventory().getStack(slot).isEmpty()) refill(c, p, slot, kind, cfg);
    }

    /** Hotbar slot; hotbar'da bo'lmasa (refill yoqilgan bo'lsa) inventardan olib keladi. */
    private int find(MinecraftClient c, ClientPlayerEntity p, PotionConfig cfg, RegistryEntry<StatusEffect> eff) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (isThrowable(s, cfg) && has(s, eff)) return i;
        }
        if (!cfg.refill) return -1;
        for (int i = 9; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!isThrowable(s, cfg) || !has(s, eff)) continue;
            int target = freeHotbar(p);
            c.interactionManager.clickSlot(p.playerScreenHandler.syncId, i, target, SlotActionType.SWAP, p);
            return target;
        }
        return -1;
    }

    private void refill(MinecraftClient c, ClientPlayerEntity p, int slot, RegistryEntry<StatusEffect> eff, PotionConfig cfg) {
        for (int i = 9; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (isThrowable(s, cfg) && has(s, eff)) {
                c.interactionManager.clickSlot(p.playerScreenHandler.syncId, i, slot, SlotActionType.SWAP, p);
                return;
            }
        }
    }

    private static int freeHotbar(ClientPlayerEntity p) {
        int sel = p.getInventory().getSelectedSlot();
        for (int h = 0; h < 9; h++) if (h != sel && p.getInventory().getStack(h).isEmpty()) return h;
        return sel == 8 ? 7 : 8;
    }

    private static boolean isThrowable(ItemStack s, PotionConfig cfg) {
        return s.isOf(Items.SPLASH_POTION) || (cfg.useLingering && s.isOf(Items.LINGERING_POTION));
    }

    private static boolean has(ItemStack s, RegistryEntry<StatusEffect> eff) {
        PotionContentsComponent pc = s.get(DataComponentTypes.POTION_CONTENTS);
        if (pc == null) return false;
        for (StatusEffectInstance e : pc.getEffects()) {
            if (e.getEffectType().value() == eff.value()) return true;
        }
        return false;
    }

    private static RegistryEntry<StatusEffect> mainEffect(ItemStack s) {
        PotionContentsComponent pc = s.get(DataComponentTypes.POTION_CONTENTS);
        if (pc == null) return null;
        for (StatusEffectInstance e : pc.getEffects()) return e.getEffectType();
        return null;
    }

    public String hudLine(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        PotionConfig cfg = PotionConfig.get();
        if (p == null || !cfg.enabled || !cfg.hud) return null;
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (isThrowable(s, cfg) && has(s, StatusEffects.INSTANT_HEALTH)) n += s.getCount();
        }
        return "§dHeal: " + (n == 0 ? "§c0" : "§f" + n);
    }
}
