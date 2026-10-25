package uz.spearlunger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.PiercingWeaponComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import uz.autotnt.AutoTntConfig;
import uz.spearlunger.gui.ClickGuiScreen;

import java.util.List;
import java.util.Random;
import java.util.Set;

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
    private enum Phase { LAUNCH_AIM, LAUNCH_WAIT, FLY, MACE_SWAPPED, FINISH }

    private static final Set<Item> BAD_FOOD = Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO,
            Items.PUFFERFISH, Items.CHORUS_FRUIT, Items.SUSPICIOUS_STEW);
    private static final Random RNG = new Random();

    private Mode mode = Mode.NONE;
    private Phase phase = Phase.FLY;
    private int ticksRunning = 0;
    private int phaseTicks = 0;
    private int sinceReady = 0;
    private int nextExtraDelay = 0;
    private int lunges = 0;
    private int originalSlot = 0;
    private boolean toggled = false;
    // R: bir bosish = yoqish/o'chirish, bosib turish = qo'yib yuborguncha (rMode 0)
    private boolean rWasDown = false;
    private int rPressTicks = 0;
    private boolean rIgnoreUntilRelease = false;
    private String lastWarn = null;
    private LivingEntity target = null;

    // avto-ovqat
    private boolean eating = false;
    private boolean eatStarted = false;
    private int eatSlot = -1;
    private int eatTicks = 0;

    // ------------------------------------------------------------ tick
    public void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.interactionManager == null) {
            if (eating) client.options.useKey.setPressed(false);
            mode = Mode.NONE;
            target = null;
            eating = false;
            toggled = false;
            return;
        }

        boolean xPressed = false;
        while (SpearLungerClient.keyTarget.wasPressed()) xPressed = true;
        int holdPresses = 0;
        while (SpearLungerClient.keyHold.wasPressed()) holdPresses++;

        if (client.currentScreen != null) {
            if (mode != Mode.NONE) stop(client, player, null);
            toggled = false;
            return;
        }
        while (SpearLungerClient.keySettings.wasPressed()) {
            client.setScreen(new ClickGuiScreen(null));
            return;
        }
        while (SpearLungerClient.keyToggle.wasPressed()) {
            Config.get().enabled = !Config.get().enabled;
            Config.save();
            player.sendMessage(Text.literal(Config.get().enabled ? "§aSpear Lunger: ON" : "§cSpear Lunger: OFF"), true);
        }
        if (!Config.get().enabled) {
            if (mode != Mode.NONE) stop(client, player, null);
            toggled = false;
            return;
        }

        if (mode == Mode.TARGET) {
            if (xPressed) {
                stop(client, player, "§eX: bekor qilindi.");
                return;
            }
            tickTarget(client, player);
            return;
        }

        if (xPressed) {
            if (mode == Mode.HOLD) stop(client, player, null);
            toggled = false;
            startTarget(client, player);
            return;
        }

        boolean wantHold = wantHold(player, holdPresses);

        if (wantHold) {
            if (mode != Mode.HOLD) {
                int s = findLungeSpear(player);
                originalSlot = player.getInventory().getSelectedSlot();
                mode = Mode.HOLD;
                sinceReady = 0;
                lastWarn = null;
                if (s < 0) {
                    say(player, "§cHotbar'da Lunge enchantli (yaroqli) nayza topilmadi.");
                    if (toggled) stop(client, player, null);
                    // bosib turish rejimida tugma qo'yib yuborilguncha hech narsa qilmaymiz
                    return;
                }
                select(player, s);
            }
            tickHold(client, player);
        } else if (mode == Mode.HOLD) {
            stop(client, player, null);
        }
    }

    // ------------------------------------------------------------ R rejimi
    /** R holati: rMode 0 = avto (bir bosish yoqadi/o'chiradi, bosib turish - qo'yib yuborguncha), 1 = bosib turish, 2 = bosib yoqish. */
    private boolean wantHold(ClientPlayerEntity player, int presses) {
        int rMode = Math.floorMod(Config.get().rMode, 3);
        boolean down = SpearLungerClient.keyHold.isPressed();
        boolean result;
        if (rMode == 1) {
            result = down;
        } else if (rMode == 2) {
            if (presses % 2 == 1) setToggled(player, !toggled);
            result = toggled;
        } else {
            if (down && !rWasDown) {
                // bosish boshlandi
                rPressTicks = 0;
                if (toggled) {
                    setToggled(player, false);
                    rIgnoreUntilRelease = true;
                }
            } else if (!down && !rWasDown && presses > 0) {
                // juda tez bosildi (bitta tick ichida bosib qo'yib yuborildi)
                setToggled(player, !toggled);
            }
            if (down) rPressTicks++;
            if (!down && rWasDown) {
                // qisqa bosish = yoqish; uzoq bosib turilgan bo'lsa qo'yib yuborilganda o'chadi
                if (!rIgnoreUntilRelease && rPressTicks <= 6) setToggled(player, true);
                rIgnoreUntilRelease = false;
            }
            result = toggled || (down && !rIgnoreUntilRelease);
        }
        rWasDown = down;
        return result;
    }

    private void setToggled(ClientPlayerEntity player, boolean on) {
        if (toggled == on) return;
        toggled = on;
        player.sendMessage(Text.literal(on ? "§aAuto Lunge: ON §7(R - o'chirish)" : "§cAuto Lunge: OFF"), true);
    }

    private void tickHold(MinecraftClient client, ClientPlayerEntity player) {
        if (eating) {
            tickEat(client, player);
            return;
        }
        int s = findLungeSpear(player);
        if (s < 0) return;
        if (hungerLow(player) && Config.get().autoEat) {
            int f = findFood(player);
            if (f >= 0) {
                startEat(client, player, f);
                return;
            }
        }
        swapAndLunge(client, player, s);
    }

    /**
     * Nayzaga o'tish va jab BITTA tickda: qo'ldagi narsa almashganda cooldown player tick'ida nolga tushadi -
     * undan oldin (shu tickning boshida) urilsa, server ham, client ham avvalgi (to'lgan) cooldown'ni hisoblaydi.
     * Shuning uchun birinchi lunge darhol chiqadi, keyingilari esa nayzaning o'z cooldown'i bilan.
     */
    private void swapAndLunge(MinecraftClient client, ClientPlayerEntity player, int spear) {
        if (player.getInventory().getSelectedSlot() != spear) {
            select(player, spear);
            nextExtraDelay = 0;
            sinceReady = 1;   // qo'shimcha kechikishsiz - darhol
        }
        tryLunge(client, player);
    }

    // ------------------------------------------------------------ avto-ovqat
    private void startEat(MinecraftClient client, ClientPlayerEntity player, int slot) {
        eating = true;
        eatStarted = false;
        eatSlot = slot;
        eatTicks = 0;
        select(player, slot);
        client.options.useKey.setPressed(true);
        player.sendMessage(Text.literal("§6Auto Lunge: ovqat yeyapman..."), true);
    }

    private void tickEat(MinecraftClient client, ClientPlayerEntity player) {
        eatTicks++;
        if (player.getInventory().getSelectedSlot() != eatSlot) select(player, eatSlot);
        client.options.useKey.setPressed(true);
        if (player.isUsingItem()) eatStarted = true;
        boolean finished = (eatStarted && !player.isUsingItem()) || eatTicks > 80
                || (!eatStarted && eatTicks > 10);
        if (finished) {
            client.options.useKey.setPressed(false);
            eating = false;
            sinceReady = 0;
            int s = findLungeSpear(player);
            if (s >= 0) select(player, s);
        }
    }

    private int findFood(ClientPlayerEntity p) {
        int best = -1, bestNutrition = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            FoodComponent food = s.get(DataComponentTypes.FOOD);
            if (food == null || BAD_FOOD.contains(s.getItem())) continue;
            boolean golden = s.isOf(Items.GOLDEN_APPLE) || s.isOf(Items.ENCHANTED_GOLDEN_APPLE);
            if (golden && !Config.get().eatGoldenApples) continue;
            if (food.nutrition() > bestNutrition) {
                bestNutrition = food.nutrition();
                best = i;
            }
        }
        return best;
    }

    // ------------------------------------------------------------ X rejimi
    private void startTarget(MinecraftClient client, ClientPlayerEntity player) {
        Config cfg = Config.get();
        LivingEntity t = findTarget(client, player);
        if (t == null) {
            say(player, "§cX: atrofda (" + cfg.rangeHorizontal + "x" + cfg.rangeVertical
                    + " blok) mos nishon topilmadi.");
            return;
        }
        int s = findLungeSpear(player);
        if (s < 0) {
            say(player, "§cHotbar'da Lunge enchantli (yaroqli) nayza topilmadi.");
            return;
        }
        Phase start = Phase.FLY;
        if (cfg.targetRequireAir && player.isOnGround()) {
            if (cfg.windChargeJump && find(player, Items.WIND_CHARGE) >= 0) {
                start = Phase.LAUNCH_AIM;
            } else {
                say(player, "§cX: siz havoda emassiz (yerda turibsiz)"
                        + (cfg.windChargeJump ? " va hotbar'da wind charge yo'q." : "."));
                return;
            }
        }
        target = t;
        mode = Mode.TARGET;
        phase = start;
        phaseTicks = 0;
        ticksRunning = 0;
        sinceReady = 0;
        lunges = 0;
        lastWarn = null;
        originalSlot = player.getInventory().getSelectedSlot();
        // nayzani oldindan olmaymiz: jab tickida bir tickda almashtiramiz (cooldown nolga tushmaydi)
        say(player, "§aX: nishon — " + t.getName().getString());
    }

    private void tickTarget(MinecraftClient client, ClientPlayerEntity player) {
        Config cfg = Config.get();
        Config.AntiCheat ac = cfg.ac();
        ticksRunning++;
        phaseTicks++;
        if (phase == Phase.FINISH) {
            stop(client, player, "§aX: tugadi.");
            return;
        }
        if (target == null || !target.isAlive() || target.isRemoved()) {
            stop(client, player, "§eX: nishon yo'qoldi.");
            return;
        }
        if (ticksRunning > cfg.targetTimeoutTicks) {
            stop(client, player, "§eX: vaqt tugadi.");
            return;
        }

        Vec3d eye = player.getEyePos();
        Vec3d center = target.getBoundingBox().getCenter();
        float yawToTarget = Rotations.yawTo(eye, center);

        switch (phase) {
            case LAUNCH_AIM -> {
                // darhol: pastga qarash faqat ishlatish paketida (kamera qimirlamaydi), shu tickda eski slotga
                int w = find(player, Items.WIND_CHARGE);
                if (w < 0) { stop(client, player, "§cWind charge tugadi."); return; }
                int back = player.getInventory().getSelectedSlot();
                float oy = player.getYaw(), op = player.getPitch();
                select(player, w);
                player.setYaw(yawToTarget);
                player.setPitch(90.0f);
                client.interactionManager.interactItem(player, Hand.MAIN_HAND);
                player.swingHand(Hand.MAIN_HAND);
                player.setYaw(oy);
                player.setPitch(op);
                select(player, back);
                phase = Phase.LAUNCH_WAIT;
                phaseTicks = 0;
                return;
            }
            case LAUNCH_WAIT -> {
                Rotations.turn(player, yawToTarget, 0.0f);
                // yerdan uzilishi bilan uchamiz (cho'qqini kutmaymiz)
                if (!player.isOnGround() && phaseTicks >= 2) {
                    phase = Phase.FLY;
                    phaseTicks = 0;
                } else if (phaseTicks > 40) {
                    stop(client, player, "§eX: wind charge sakratmadi.");
                }
                return;
            }
            case MACE_SWAPPED -> {
                // sameTickSwap = OFF: mace oldingi tickda tanlangan, endi uramiz
                client.interactionManager.attackEntity(player, target);
                player.swingHand(Hand.MAIN_HAND);
                phase = Phase.FINISH;
                return;
            }
            default -> { }
        }

        if (player.isOnGround() && lunges > 0 && cfg.targetRequireAir) {
            stop(client, player, "§eX: yerga tushdingiz.");
            return;
        }

        double dist = eye.distanceTo(closestPoint(target, eye));
        boolean inMaceRange = dist <= cfg.maceReach;

        if (inMaceRange) {
            boolean aimed = Rotations.turn(player, yawToTarget, Rotations.pitchTo(eye, center));
            boolean looking = !ac.requireLookAt || looksAt(player, target, cfg.maceReach + 1.0);
            if (!aimed || !looking) return;
            int m = findMace(player);
            if (m < 0) {
                stop(client, player, "§cHotbar'da mace topilmadi.");
                return;
            }
            select(player, m);
            if (ac.sameTickSwap) {
                // Bir tickda almashtirib uramiz: server hujumni cooldown reset bo'lishidan oldin hisoblaydi.
                client.interactionManager.attackEntity(player, target);
                player.swingHand(Hand.MAIN_HAND);
                phase = Phase.FINISH;
            } else {
                phase = Phase.MACE_SWAPPED;
            }
            return;
        }

        // Uzoqda: pitch = 0 bo'lsa Lunge impulsi to'liq gorizontal bo'ladi.
        float pitch = cfg.flattenPitch ? 0.0f : Rotations.pitchTo(eye, center);
        boolean aimed = Rotations.turn(player, yawToTarget, pitch);

        int s = findLungeSpear(player);
        if (s < 0) {
            stop(client, player, "§cHotbar'da Lunge enchantli (yaroqli) nayza topilmadi.");
            return;
        }
        // nayzani faqat shu yerda (jab tickida) olamiz - cooldown yo'qolmaydi
        if (aimed) swapAndLunge(client, player, s);
    }

    // ------------------------------------------------------------ lunge
    /** Cooldown to'lgan bo'lsa jab qiladi (Lunge shu jab'dan keyin ishga tushadi). */
    private void tryLunge(MinecraftClient client, ClientPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        PiercingWeaponComponent piercing = stack.get(DataComponentTypes.PIERCING_WEAPON);
        if (piercing == null) return;

        String block = blockedReason(player, stack);
        if (block != null) {
            warnOnce(player, block);
            return;
        }
        lastWarn = null;

        if (player.isBelowMinimumAttackCharge(stack, 0) || player.getAttackCooldownProgress(0.0f) < 1.0f) {
            sinceReady = 0;
            return;
        }
        if (sinceReady == 0) {
            int r = Config.get().ac().randomDelayMax;
            nextExtraDelay = Config.get().extraDelayTicks + (r > 0 ? RNG.nextInt(r + 1) : 0);
        }
        if (sinceReady++ < nextExtraDelay) return;
        if (client.interactionManager.isFlyingLocked()) return;

        client.interactionManager.attackWithPiercingWeapon(piercing);
        player.swingHand(Hand.MAIN_HAND);
        lunges++;
        sinceReady = 0;
    }

    private boolean hungerLow(ClientPlayerEntity player) {
        int minFood = Config.get().minFood;
        return minFood > 0 && !player.getAbilities().creativeMode
                && player.getHungerManager().getFoodLevel() < minFood;
    }

    /** Lunge ishlamaydigan holatlar (enchantment talablari + ochlik/durability limiti). */
    private String blockedReason(ClientPlayerEntity player, ItemStack stack) {
        if (player.hasVehicle()) return "§eLunge: minib turganda ishlamaydi.";
        if (player.isGliding()) return "§eLunge: elytra bilan uchayotganda ishlamaydi.";
        if (player.isTouchingWater()) return "§eLunge: suvda ishlamaydi.";
        if (hungerLow(player)) {
            return "§eLunge: ochlik " + player.getHungerManager().getFoodLevel() + " < " + Config.get().minFood
                    + (Config.get().autoEat && mode == Mode.HOLD ? " va hotbar'da ovqat yo'q." : ".");
        }
        if (lowDurability(stack)) return "§eLunge: nayza buzilishiga oz qoldi.";
        return null;
    }

    private static boolean lowDurability(ItemStack s) {
        int min = Config.get().minDurability;
        return min > 0 && s.isDamageable() && s.getMaxDamage() - s.getDamage() <= min;
    }

    private void warnOnce(ClientPlayerEntity player, String msg) {
        if (!msg.equals(lastWarn)) {
            player.sendMessage(Text.literal(msg), true);
            lastWarn = msg;
        }
    }

    private void stop(MinecraftClient client, ClientPlayerEntity player, String msg) {
        if (eating) {
            client.options.useKey.setPressed(false);
            eating = false;
        }
        if (player != null) {
            select(player, originalSlot);
            if (msg != null) say(player, msg);
        }
        mode = Mode.NONE;
        target = null;
        toggled = false;
    }

    // ------------------------------------------------------------ HUD
    /** Ekranda ko'rsatiladigan qator, ko'rsatish kerak bo'lmasa null. */
    public String hudLine(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (p == null) return null;
        ItemStack st = p.getMainHandStack();
        boolean spear = st.contains(DataComponentTypes.PIERCING_WEAPON);
        if (mode == Mode.NONE && !spear) return null;
        StringBuilder sb = new StringBuilder("§bLunge ");
        if (spear) {
            float prog = p.getAttackCooldownProgress(0.0f);
            sb.append(prog >= 1.0f ? "§aTAYYOR" : "§e" + (int) (prog * 100) + "%");
            if (st.isDamageable()) sb.append(" §7| Dur: ").append(st.getMaxDamage() - st.getDamage());
        } else {
            sb.append("§7-");
        }
        sb.append(" §7| Ochlik: ").append(p.getHungerManager().getFoodLevel());
        if (mode == Mode.HOLD) sb.append(eating ? " §7| §6Yeyapti" : " §7| §aR ON");
        if (mode == Mode.TARGET && target != null) {
            sb.append(String.format(" §7| §cX: %s %.1fm", target.getName().getString(), p.distanceTo(target)));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------ yordamchilar
    private void select(ClientPlayerEntity player, int slot) {
        if (slot >= 0 && slot < 9) player.getInventory().setSelectedSlot(slot);
    }

    private void say(ClientPlayerEntity player, String msg) {
        player.sendMessage(Text.literal(msg), false);
    }

    private static boolean looksAt(ClientPlayerEntity p, LivingEntity t, double range) {
        Vec3d eye = p.getEyePos();
        Vec3d end = eye.add(p.getRotationVec(1.0f).multiply(range));
        return t.getBoundingBox().expand(0.1).raycast(eye, end).isPresent();
    }

    private static Vec3d closestPoint(LivingEntity t, Vec3d from) {
        var b = t.getBoundingBox();
        return new Vec3d(MathHelper.clamp(from.x, b.minX, b.maxX),
                MathHelper.clamp(from.y, b.minY, b.maxY),
                MathHelper.clamp(from.z, b.minZ, b.maxZ));
    }

    private static boolean isFriend(LivingEntity e) {
        if (!(e instanceof PlayerEntity)) return false;
        String name = e.getName().getString();
        for (String f : AutoTntConfig.get().friends) if (f.equalsIgnoreCase(name)) return true;
        return false;
    }

    private LivingEntity findTarget(MinecraftClient client, ClientPlayerEntity player) {
        Config c = Config.get();
        var box = player.getBoundingBox().expand(c.rangeHorizontal, c.rangeVertical, c.rangeHorizontal);
        List<LivingEntity> list = client.world.getEntitiesByClass(LivingEntity.class, box, e -> e != player
                && e.isAlive() && !e.isSpectator() && !isFriend(e)
                && (e instanceof PlayerEntity || (c.targetMobs && e instanceof Monster)));
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : list) {
            double score;
            if (c.targetSelect == 1) {
                Vec3d to = e.getBoundingBox().getCenter().subtract(eye);
                double cos = to.normalize().dotProduct(look);
                if (cos < 0.87) continue;   // ~30 gradus konus
                score = 1.0 - cos;
            } else {
                score = e.squaredDistanceTo(player);
            }
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    private static int find(ClientPlayerEntity p, Item item) {
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    private static int enchantLevel(ItemStack s, String name) {
        for (RegistryEntry<Enchantment> e : s.getEnchantments().getEnchantments()) {
            if (e.getKey().map(k -> k.getValue().getPath().equals(name)).orElse(false)) {
                return s.getEnchantments().getLevel(e);
            }
        }
        return 0;
    }

    /** Eng yuqori Lunge darajali, durability'si yetarli nayza (tenglikda hozir qo'ldagisi). */
    private int findLungeSpear(ClientPlayerEntity p) {
        int best = -1, bestLevel = 0;
        int sel = p.getInventory().getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isEmpty() || !s.contains(DataComponentTypes.PIERCING_WEAPON) || lowDurability(s)) continue;
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
