package uz.automace;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import uz.autotnt.AutoTntConfig;
import uz.spearlunger.InvHelper;
import uz.spearlunger.Rotations;

/**
 * Auto Mace: tushayotganda (minFall+) nishon urish masofasiga kirishi bilan mace'ga o'tib uradi - almashtirish va
 * zarba BITTA tickda (cooldown yo'qolmaydi). "Eng kuchli" rejimida yerga/nishonga tegishdan oldingi oxirgi tickda
 * uradi (tushish masofasi va zarar maksimal). Nishon qalqon ushlasa avval bolta bilan o'chiradi.
 */
public class MaceController {
    private int cooldown = 0, returnIn = -1, returnSlot = -1;
    private String last = null;

    public void tick(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (p == null || c.world == null || c.interactionManager == null) return;
        MaceConfig cfg = MaceConfig.get();
        while (AutoMaceClient.keyToggle.wasPressed()) {
            cfg.enabled = !cfg.enabled;
            MaceConfig.save();
            p.sendMessage(Text.literal(cfg.enabled ? "§aAuto Mace: ON" : "§cAuto Mace: OFF"), true);
        }
        // eski slotga qaytish (kechikish bilan)
        if (returnIn >= 0 && returnIn-- == 0) {
            if (returnSlot >= 0) p.getInventory().setSelectedSlot(returnSlot);
            returnSlot = -1;
        }
        if (cooldown > 0) cooldown--;
        if (!cfg.enabled || cooldown > 0 || InvHelper.screenOpen(c) || p.isSpectator()) return;
        if (p.isOnGround() || p.isTouchingWater() || p.isGliding() || p.getVelocity().y >= 0) return;
        if (p.fallDistance < cfg.minFall) return;

        LivingEntity target = pick(c, p, cfg);
        if (target == null) return;
        Vec3d eye = p.getEyePos();
        double reach = cfg.reachTenths / 10.0;
        double d = eye.distanceTo(closest(target, eye));
        if (d > reach) return;

        if (!cfg.requireCrosshair) {
            Vec3d center = target.getBoundingBox().getCenter();
            Rotations.turn(p, Rotations.yawTo(eye, center), Rotations.pitchTo(eye, center), 30);
        }
        if (cfg.timing == 0 && !lastChance(c.world, p, target, reach)) return;   // keyingi tickda ham yetamiz - kutamiz

        int original = p.getInventory().getSelectedSlot();
        // qalqon: avval bolta (qalqon 5 soniya ishlamaydi), mace keyingi tickda
        if (cfg.axeShield && target.isBlocking()) {
            int axe = weapon(p, "_axe");
            if (axe >= 0) {
                p.getInventory().setSelectedSlot(axe);
                c.interactionManager.attackEntity(p, target);
                p.swingHand(Hand.MAIN_HAND);
                cooldown = 1;
                last = "bolta (qalqon)";
                return;
            }
        }
        int mace = cfg.autoSwitch ? mace(p, target, cfg) : (p.getMainHandStack().isOf(Items.MACE) ? original : -1);
        if (mace < 0) return;
        p.getInventory().setSelectedSlot(mace);   // bir tickda almashtirib uramiz
        c.interactionManager.attackEntity(p, target);
        p.swingHand(Hand.MAIN_HAND);
        last = String.format("%s %.1f blok", target.getName().getString(), p.fallDistance);
        cooldown = Math.max(1, cfg.delay);
        if (cfg.after == 1 && mace != original) {
            if (cfg.returnDelay <= 0) p.getInventory().setSelectedSlot(original);
            else {
                returnSlot = original;
                returnIn = cfg.returnDelay;
            }
        }
        if (cfg.messages) p.sendMessage(Text.literal("§6Auto Mace: " + last), true);
    }

    /** Keyingi tickda nishon masofadan chiqadimi yoki yerga tegamizmi - unda hozir uramiz (eng ko'p tushish). */
    private static boolean lastChance(ClientWorld w, ClientPlayerEntity p, LivingEntity t, double reach) {
        Vec3d v = p.getVelocity();
        Vec3d nextEye = p.getEyePos().add(v.x, (v.y - 0.08) * 0.98, v.z);
        double nextD = nextEye.distanceTo(closest(t, nextEye));
        if (nextD > reach) return true;
        // oyog'imiz keyingi tickda nishon tepasidan yoki yerdan o'tib ketadimi
        double nextFeet = p.getY() + (v.y - 0.08) * 0.98;
        if (nextFeet <= t.getBoundingBox().maxY + 0.1) return true;
        Vec3d feet = p.getEntityPos();
        return w.raycast(new RaycastContext(feet, feet.add(0, (v.y - 0.08) * 0.98 - 0.1, 0),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, p)).getType() != HitResult.Type.MISS;
    }

    private LivingEntity pick(MinecraftClient c, ClientPlayerEntity p, MaceConfig cfg) {
        if (cfg.requireCrosshair) {
            if (c.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le
                    && valid(p, le, cfg)) return le;
            return null;
        }
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : c.world.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(6),
                x -> valid(p, x, cfg))) {
            double d = e.squaredDistanceTo(p);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    private static boolean valid(ClientPlayerEntity p, Entity e, MaceConfig cfg) {
        if (e == p || !e.isAlive() || e.isRemoved()) return false;
        if (e instanceof PlayerEntity pl) {
            if (!cfg.players || pl.isSpectator() || pl.isCreative()) return false;
            return !(cfg.ignoreFriends && isFriend(pl));
        }
        return cfg.mobs && e instanceof Monster;
    }

    /** prefer: 0 avto (zirhliga Breach), 1 Density, 2 Breach, 3 Wind Burst. */
    private static int mace(ClientPlayerEntity p, LivingEntity target, MaceConfig cfg) {
        String want = switch (Math.floorMod(cfg.prefer, 4)) {
            case 1 -> "density";
            case 2 -> "breach";
            case 3 -> "wind_burst";
            default -> target.getArmor() >= 12 ? "breach" : "density";
        };
        int best = -1, bestScore = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isOf(Items.MACE)) continue;
            int score = 1 + level(s, want) * 10 + level(s, "density") + level(s, "breach") + level(s, "wind_burst");
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private static int weapon(ClientPlayerEntity p, String suffix) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isEmpty() && Registries.ITEM.getId(s.getItem()).getPath().endsWith(suffix)) return i;
        }
        return -1;
    }

    private static int level(ItemStack s, String name) {
        for (RegistryEntry<Enchantment> e : s.getEnchantments().getEnchantments()) {
            if (e.getKey().map(k -> k.getValue().getPath().equals(name)).orElse(false)) {
                return s.getEnchantments().getLevel(e);
            }
        }
        return 0;
    }

    private static Vec3d closest(LivingEntity t, Vec3d from) {
        var b = t.getBoundingBox();
        return new Vec3d(MathHelper.clamp(from.x, b.minX, b.maxX), MathHelper.clamp(from.y, b.minY, b.maxY),
                MathHelper.clamp(from.z, b.minZ, b.maxZ));
    }

    private static boolean isFriend(PlayerEntity t) {
        String name = t.getName().getString();
        for (String f : AutoTntConfig.get().friends) if (f.equalsIgnoreCase(name)) return true;
        return false;
    }

    public String hudLine(MinecraftClient c) {
        MaceConfig cfg = MaceConfig.get();
        ClientPlayerEntity p = c.player;
        if (p == null || !cfg.enabled || !cfg.hud) return null;
        if (!p.isOnGround() && p.fallDistance >= cfg.minFall) {
            return String.format("§6Mace: §f%.1f blok tushish", p.fallDistance);
        }
        return last == null ? null : "§6Mace: §7oxirgi - " + last;
    }
}
