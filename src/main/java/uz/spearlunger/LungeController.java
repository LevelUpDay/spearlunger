package uz.spearlunger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PiercingWeaponComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;

/**
 * 1.21.11 nayza mexanikasi:
 *  - nayzada minimum_attack_charge = 1.0, ya'ni jab (chap tugma) faqat attack cooldown TO'LIQ bo'lganda ishlaydi;
 *  - qo'ldagi item almashsa cooldown 0 ga tushadi (PlayerEntity.resetTicksSince);
 *  - Lunge impulsi faqat gorizontal: qarash yo'nalishi * cos(pitch), shuning uchun pastga qarasangiz kuchsiz uchadi;
 *  - suvda, elytra'da va minib turganda Lunge ishlamaydi.
 * Shuning uchun nayzani qo'lda ushlab turamiz va cooldown to'lishi bilan jab qilamiz.
 */
public class LungeController {
    private enum Mode { NONE, HOLD, TARGET }

    private Mode mode = Mode.NONE;
    private int ticksRunning = 0;
    private int sinceReady = 0;
    private int lunges = 0;
    private int originalSlot = 0;
    private boolean finishNextTick = false;
    private String lastWarn = null;
    private PlayerEntity target = null;

    // ------------------------------------------------------------ tick
    public void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.interactionManager == null) {
            mode = Mode.NONE;
            target = null;
            return;
        }

        boolean xPressed = false;
        while (SpearLungerClient.keyTarget.wasPressed()) xPressed = true;
        while (SpearLungerClient.keyHold.wasPressed()) { /* bosishlarni tozalaymiz */ }

        if (client.currentScreen != null) {
            if (mode != Mode.NONE) stop(player, null);
            return;
        }
        while (SpearLungerClient.keySettings.wasPressed()) {
            client.setScreen(new SettingsScreen(null));
            return;
        }

        if (mode == Mode.TARGET) {
            if (xPressed) {
                stop(player, "§eX: bekor qilindi.");
                return;
            }
            tickTarget(client, player);
            return;
        }

        if (xPressed) {
            if (mode == Mode.HOLD) stop(player, null);
            startTarget(client, player);
            return;
        }

        if (SpearLungerClient.keyHold.isPressed()) {
            if (mode != Mode.HOLD) {
                int s = findLungeSpear(player);
                if (s < 0) {
                    say(player, "§cHotbar'da Lunge enchantli nayza topilmadi.");
                    // tugma qo'yib yuborilguncha qayta-qayta yozmaslik uchun HOLD'ga o'tamiz, lekin hech narsa qilmaymiz
                    mode = Mode.HOLD;
                    originalSlot = player.getInventory().getSelectedSlot();
                    return;
                }
                mode = Mode.HOLD;
                originalSlot = player.getInventory().getSelectedSlot();
                sinceReady = 0;
                lastWarn = null;
                select(player, s);
            }
            tickHold(client, player);
        } else if (mode == Mode.HOLD) {
            stop(player, null);
        }
    }

    // ------------------------------------------------------------ R rejimi
    private void tickHold(MinecraftClient client, ClientPlayerEntity player) {
        int s = findLungeSpear(player);
        if (s < 0) return;
        if (player.getInventory().getSelectedSlot() != s) {
            select(player, s);
            sinceReady = 0;
            return;
        }
        tryLunge(client, player);
    }

    // ------------------------------------------------------------ X rejimi
    private void startTarget(MinecraftClient client, ClientPlayerEntity player) {
        PlayerEntity t = findNearestPlayer(client, player);
        if (t == null) {
            say(player, "§cX: atrofda (" + Config.get().rangeHorizontal + "x" + Config.get().rangeVertical
                    + " blok) boshqa o'yinchi topilmadi.");
            return;
        }
        if (Config.get().targetRequireAir && player.isOnGround()) {
            say(player, "§cX: siz havoda emassiz (yerda turibsiz).");
            return;
        }
        int s = findLungeSpear(player);
        if (s < 0) {
            say(player, "§cHotbar'da Lunge enchantli nayza topilmadi.");
            return;
        }
        target = t;
        mode = Mode.TARGET;
        ticksRunning = 0;
        sinceReady = 0;
        lunges = 0;
        finishNextTick = false;
        lastWarn = null;
        originalSlot = player.getInventory().getSelectedSlot();
        select(player, s);
        say(player, "§aX: nishon — " + t.getName().getString());
    }

    private void tickTarget(MinecraftClient client, ClientPlayerEntity player) {
        if (finishNextTick) {
            stop(player, "§aX: tugadi.");
            return;
        }
        ticksRunning++;
        if (target == null || !target.isAlive() || target.isRemoved()) {
            stop(player, "§eX: nishon yo'qoldi.");
            return;
        }
        if (ticksRunning > Config.get().targetTimeoutTicks) {
            stop(player, "§eX: vaqt tugadi.");
            return;
        }
        if (player.isOnGround() && lunges > 0 && Config.get().targetRequireAir) {
            stop(player, "§eX: yerga tushdingiz.");
            return;
        }

        double dist = player.getEyePos().distanceTo(closestPoint(target, player.getEyePos()));
        boolean inMaceRange = dist <= Config.get().maceReach;

        // Yaqin bo'lsa to'liq nishonga olamiz, uzoqda esa pitch = 0, shunda Lunge impulsi to'liq gorizontal bo'ladi.
        aim(player, target, !inMaceRange && Config.get().flattenPitch);

        if (inMaceRange) {
            int m = findMace(player);
            if (m < 0) {
                stop(player, "§cHotbar'da mace topilmadi.");
                return;
            }
            // Bir tickda almashtirib uramiz: server slot paketini oladi va cooldown reset bo'lishidan oldin hujumni hisoblaydi.
            select(player, m);
            client.interactionManager.attackEntity(player, target);
            player.swingHand(Hand.MAIN_HAND);
            finishNextTick = true;
            return;
        }

        int s = findLungeSpear(player);
        if (s < 0) {
            stop(player, "§cHotbar'da Lunge enchantli nayza topilmadi.");
            return;
        }
        if (player.getInventory().getSelectedSlot() != s) {
            select(player, s);
            sinceReady = 0;
            return;
        }
        tryLunge(client, player);
    }

    // ------------------------------------------------------------ lunge
    /** Cooldown to'lgan bo'lsa jab qiladi (Lunge shu jab'dan keyin ishga tushadi). */
    private void tryLunge(MinecraftClient client, ClientPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        PiercingWeaponComponent piercing = stack.get(DataComponentTypes.PIERCING_WEAPON);
        if (piercing == null) return;

        String block = blockedReason(player);
        if (block != null) {
            warnOnce(player, block);
            return;
        }
        lastWarn = null;

        if (player.isBelowMinimumAttackCharge(stack, 0) || player.getAttackCooldownProgress(0.0f) < 1.0f) {
            sinceReady = 0;
            return;
        }
        if (sinceReady++ < Config.get().extraDelayTicks) return;
        if (client.interactionManager.isFlyingLocked()) return;

        client.interactionManager.attackWithPiercingWeapon(piercing);
        player.swingHand(Hand.MAIN_HAND);
        lunges++;
        sinceReady = 0;
    }

    /** Lunge ishlamaydigan holatlar (enchantment talablari + ochlik limiti). */
    private String blockedReason(ClientPlayerEntity player) {
        if (player.hasVehicle()) return "§eLunge: minib turganda ishlamaydi.";
        if (player.isGliding()) return "§eLunge: elytra bilan uchayotganda ishlamaydi.";
        if (player.isTouchingWater()) return "§eLunge: suvda ishlamaydi.";
        int minFood = Config.get().minFood;
        if (minFood > 0 && !player.getAbilities().creativeMode && player.getHungerManager().getFoodLevel() < minFood) {
            return "§eLunge: ochlik " + player.getHungerManager().getFoodLevel() + " < " + minFood + " (sozlamada o'zgartiring).";
        }
        return null;
    }

    private void warnOnce(ClientPlayerEntity player, String msg) {
        if (!msg.equals(lastWarn)) {
            player.sendMessage(Text.literal(msg), true);
            lastWarn = msg;
        }
    }

    private void stop(ClientPlayerEntity player, String msg) {
        if (player != null) {
            select(player, originalSlot);
            if (msg != null) say(player, msg);
        }
        mode = Mode.NONE;
        target = null;
        finishNextTick = false;
    }

    // ------------------------------------------------------------ yordamchilar
    private void select(ClientPlayerEntity player, int slot) {
        if (slot >= 0 && slot < 9) player.getInventory().setSelectedSlot(slot);
    }

    private void say(ClientPlayerEntity player, String msg) {
        player.sendMessage(Text.literal(msg), false);
    }

    private void aim(ClientPlayerEntity player, PlayerEntity t, boolean flat) {
        Vec3d eye = player.getEyePos();
        Vec3d to = t.getBoundingBox().getCenter();
        double dx = to.x - eye.x, dy = to.y - eye.y, dz = to.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (MathHelper.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float pitch = flat ? 0.0f : (float) -(MathHelper.atan2(dy, horiz) * 180.0 / Math.PI);
        player.setYaw(yaw);
        player.setPitch(pitch);
    }

    private static Vec3d closestPoint(PlayerEntity t, Vec3d from) {
        var b = t.getBoundingBox();
        return new Vec3d(MathHelper.clamp(from.x, b.minX, b.maxX),
                MathHelper.clamp(from.y, b.minY, b.maxY),
                MathHelper.clamp(from.z, b.minZ, b.maxZ));
    }

    private PlayerEntity findNearestPlayer(MinecraftClient client, ClientPlayerEntity player) {
        Config c = Config.get();
        var box = player.getBoundingBox().expand(c.rangeHorizontal, c.rangeVertical, c.rangeHorizontal);
        List<PlayerEntity> list = client.world.getEntitiesByClass(PlayerEntity.class, box,
                p -> p != player && p.isAlive() && !p.isSpectator());
        return list.stream().min(Comparator.comparingDouble(p -> p.squaredDistanceTo(player))).orElse(null);
    }

    private static int enchantLevel(ItemStack s, String name) {
        for (RegistryEntry<Enchantment> e : s.getEnchantments().getEnchantments()) {
            if (e.getKey().map(k -> k.getValue().getPath().equals(name)).orElse(false)) {
                return s.getEnchantments().getLevel(e);
            }
        }
        return 0;
    }

    /** Eng yuqori Lunge darajali nayza (tenglikda hozir qo'ldagisi). */
    private int findLungeSpear(ClientPlayerEntity p) {
        int best = -1, bestLevel = 0;
        int sel = p.getInventory().getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isEmpty() || !s.contains(DataComponentTypes.PIERCING_WEAPON)) continue;
            int lvl = enchantLevel(s, "lunge");
            if (lvl > bestLevel || (lvl == bestLevel && lvl > 0 && i == sel)) {
                bestLevel = lvl;
                best = i;
            }
        }
        return best;
    }

    /** Wind Burst > Breach > Density > oddiy mace. */
    private int findMace(ClientPlayerEntity p) {
        int best = -1, bestScore = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(Items.MACE)) {
                int score = enchantLevel(s, "wind_burst") > 0 ? 4
                        : enchantLevel(s, "breach") > 0 ? 3
                        : enchantLevel(s, "density") > 0 ? 2 : 1;
                if (score > bestScore) { bestScore = score; best = i; }
            }
        }
        return best;
    }
}
