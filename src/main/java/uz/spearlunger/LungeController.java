package uz.spearlunger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;

public class LungeController {
    private enum Mode { NONE, HOLD, TARGET }
    private enum Phase { SELECT_SPEAR, ATTACK, SELECT_OTHER, WAIT, SELECT_MACE, MACE_ATTACK, FINISH }

    private Mode mode = Mode.NONE;
    private Phase phase = Phase.SELECT_SPEAR;
    private int wait = 0;
    private int ticksRunning = 0;
    private int lunges = 0;
    private int originalSlot = 0;
    private PlayerEntity target = null;

    // ------------------------------------------------------------ tick
    public void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.interactionManager == null) {
            mode = Mode.NONE;
            return;
        }

        boolean xPressed = false;
        while (SpearLungerClient.keyTarget.wasPressed()) xPressed = true;
        while (SpearLungerClient.keyHold.wasPressed()) { /* bosishlarni tozalaymiz */ }

        if (client.currentScreen != null) {
            if (mode != Mode.NONE) stop(client, player, null);
            return;
        }
        while (SpearLungerClient.keySettings.wasPressed()) {
            client.setScreen(new SettingsScreen(null));
            return;
        }

        if (mode == Mode.TARGET) {
            tickTarget(client, player);
            return;
        }

        if (xPressed) {
            if (mode == Mode.HOLD) stop(client, player, null);
            startTarget(client, player);
            return;
        }

        if (SpearLungerClient.keyHold.isPressed()) {
            if (mode != Mode.HOLD) {
                mode = Mode.HOLD;
                phase = Phase.SELECT_SPEAR;
                wait = 0;
                originalSlot = player.getInventory().getSelectedSlot();
            }
            cycle(client, player);
        } else if (mode == Mode.HOLD) {
            stop(client, player, null);
        }
    }

    // ------------------------------------------------------------ X rejimi
    private void startTarget(MinecraftClient client, ClientPlayerEntity player) {
        PlayerEntity t = findNearestPlayer(client, player);
        if (t == null) {
            say(player, "§cX: atrofda (" + Config.get().rangeHorizontal + "x" + Config.get().rangeVertical
                    + " blok) boshqa o'yinchi topilmadi.");
            return;
        }
        if (player.isOnGround()) {
            say(player, "§cX: siz havoda emassiz (yerda turibsiz).");
            return;
        }
        target = t;
        mode = Mode.TARGET;
        phase = Phase.SELECT_SPEAR;
        ticksRunning = 0;
        lunges = 0;
        wait = 0;
        originalSlot = player.getInventory().getSelectedSlot();
        say(player, "§aX: nishon — " + t.getName().getString());
    }

    private void tickTarget(MinecraftClient client, ClientPlayerEntity player) {
        ticksRunning++;
        if (target == null || !target.isAlive() || target.isRemoved()) {
            stop(client, player, "§eX: nishon yo'qoldi.");
            return;
        }
        if (ticksRunning > 200) {
            stop(client, player, "§eX: vaqt tugadi.");
            return;
        }
        aim(player, target);

        switch (phase) {
            case SELECT_MACE -> {
                int s = findMace(player);
                if (s < 0) { stop(client, player, "§cHotbar'da mace topilmadi."); return; }
                select(player, s);
                phase = Phase.MACE_ATTACK;
                return;
            }
            case MACE_ATTACK -> {
                pressAttack(client);
                phase = Phase.FINISH;
                return;
            }
            case FINISH -> {
                stop(client, player, "§aX: tugadi.");
                return;
            }
            default -> { }
        }

        if (player.isOnGround() && lunges > 0) {
            stop(client, player, "§eX: yerga tushdingiz.");
            return;
        }

        boolean arrived = player.distanceTo(target) <= 3.5f;
        boolean stalled = lunges > 0 && phase == Phase.WAIT
                && player.getVelocity().horizontalLength() < 0.05;
        if (arrived || stalled) {
            phase = Phase.SELECT_MACE;
            return;
        }
        cycle(client, player);
    }

    // ------------------------------------------------------------ lunge sikli
    private void cycle(MinecraftClient client, ClientPlayerEntity player) {
        switch (phase) {
            case SELECT_SPEAR -> {
                int s = findLungeSpear(player);
                if (s < 0) {
                    stop(client, player, "§cHotbar'da Lunge enchantli nayza topilmadi.");
                    return;
                }
                select(player, s);
                phase = Phase.ATTACK;
            }
            case ATTACK -> {
                pressAttack(client);
                lunges++;
                phase = Phase.SELECT_OTHER;
            }
            case SELECT_OTHER -> {
                int s = findOtherItem(player);
                if (s >= 0) select(player, s);
                wait = Config.get().lungeDelayTicks;
                phase = Phase.WAIT;
            }
            case WAIT -> {
                if (wait-- <= 0) phase = Phase.SELECT_SPEAR;
            }
            default -> { }
        }
    }

    private void stop(MinecraftClient client, ClientPlayerEntity player, String msg) {
        if (player != null) {
            select(player, originalSlot);
            if (msg != null) say(player, msg);
        }
        mode = Mode.NONE;
        target = null;
    }

    // ------------------------------------------------------------ yordamchilar
    private void pressAttack(MinecraftClient client) {
        KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(client.options.attackKey.getBoundKeyTranslationKey()));
    }

    private void select(ClientPlayerEntity player, int slot) {
        if (slot >= 0 && slot < 9) player.getInventory().setSelectedSlot(slot);
    }

    private void say(ClientPlayerEntity player, String msg) {
        player.sendMessage(Text.literal(msg), false);
    }

    private void aim(ClientPlayerEntity player, PlayerEntity t) {
        Vec3d eye = player.getEyePos();
        Vec3d to = t.getBoundingBox().getCenter();
        double dx = to.x - eye.x, dy = to.y - eye.y, dz = to.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (MathHelper.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) -(MathHelper.atan2(dy, flat) * 180.0 / Math.PI);
        player.setYaw(yaw);
        player.setPitch(pitch);
    }

    private PlayerEntity findNearestPlayer(MinecraftClient client, ClientPlayerEntity player) {
        Config c = Config.get();
        var box = player.getBoundingBox().expand(c.rangeHorizontal, c.rangeVertical, c.rangeHorizontal);
        List<PlayerEntity> list = client.world.getEntitiesByClass(PlayerEntity.class, box,
                p -> p != player && p.isAlive() && !p.isSpectator());
        return list.stream().min(Comparator.comparingDouble(p -> p.squaredDistanceTo(player))).orElse(null);
    }

    private static boolean isSpear(ItemStack s) {
        return !s.isEmpty() && Registries.ITEM.getId(s.getItem()).getPath().endsWith("_spear");
    }

    private static boolean hasEnchant(ItemStack s, String name) {
        for (RegistryEntry<Enchantment> e : s.getEnchantments().getEnchantments()) {
            if (e.getKey().map(k -> k.getValue().getPath().equals(name)).orElse(false)) return true;
        }
        return false;
    }

    private int findLungeSpear(ClientPlayerEntity p) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (isSpear(s) && hasEnchant(s, "lunge")) return i;
        }
        return -1;
    }

    /** Wind Burst > Breach > oddiy mace. */
    private int findMace(ClientPlayerEntity p) {
        int best = -1, bestScore = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(Items.MACE)) {
                int score = hasEnchant(s, "wind_burst") ? 3 : hasEnchant(s, "breach") ? 2 : 1;
                if (score > bestScore) { bestScore = score; best = i; }
            }
        }
        return best;
    }

    /** Avval ovqat, bo'lmasa lungega xalaqit bermaydigan boshqa slot. */
    private int findOtherItem(ClientPlayerEntity p) {
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).contains(DataComponentTypes.FOOD)) return i;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!isSpear(s) && !s.isOf(Items.MACE)) return i;
        }
        return -1;
    }
}
