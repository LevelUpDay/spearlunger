package uz.crystalhud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.ExplosionImpl;
import org.lwjgl.glfw.GLFW;
import uz.autotnt.AutoTntConfig;
import uz.spearlunger.InvHelper;
import uz.spearlunger.Projector;
import uz.spearlunger.Rotations;

import java.util.ArrayList;
import java.util.List;

/**
 * Crystal Placer HUD: yaqindagi dushmanga eng ko'p zarar beradigan end crystal joylarini (obsidian/bedrock,
 * ustida bo'sh joy, qo'yish masofasida) hisoblab ekranda ko'rsatadi - crystal'ni o'zingiz qo'yasiz.
 * Zarar vanilla formulasi bilan (portlash kuchi 6, to'siqlar - exposure, zirh va toughness), o'zingizga
 * keladigan zarar ham yoziladi; o'zingizni o'ldiradigan joylar ko'rsatilmaydi.
 */
public class CrystalHudClient implements ClientModInitializer {
    public static KeyBinding keyToggle;

    private record Spot(BlockPos pos, float enemy, float self) {}

    private static final List<Spot> SPOTS = new ArrayList<>();
    private static String targetName = null;

    // ------------------------------------------------------------ avto-qo'yish (yashil joyga)
    /** Crystal qo'yilgan - shu yerda (obsidian ustida) paydo bo'lishini kutamiz, keyin uramiz (portlatamiz). */
    private static BlockPos pendingPos = null;
    private static int pendingWait = 0;
    private static int borrowedInv = -1, borrowedHotbar = -1;
    private static int autoCooldown = 0;

    @Override
    public void onInitializeClient() {
        CrystalHudConfig.load();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("crystalhud", "main"));
        keyToggle = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.crystalhud.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
        ClientTickEvents.END_CLIENT_TICK.register(CrystalHudClient::tick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of("crystalhud", "spots"),
                CrystalHudClient::render);
    }

    private static void tick(MinecraftClient c) {
        SPOTS.clear();
        targetName = null;
        ClientPlayerEntity p = c.player;
        if (p == null || c.world == null) return;
        if (autoCooldown > 0) autoCooldown--;
        // yoqilmasa/nishon topilmasa ham, allaqachon qo'yilgan crystal'ni portlatib tugatamiz (osilib qolmasin)
        if (pendingPos != null) tickPending(c, p);
        CrystalHudConfig cfg = CrystalHudConfig.get();
        while (keyToggle.wasPressed()) {
            cfg.enabled = !cfg.enabled;
            CrystalHudConfig.save();
            p.sendMessage(Text.literal(cfg.enabled ? "§aCrystal HUD: ON" : "§cCrystal HUD: OFF"), true);
        }
        if (!cfg.enabled) return;
        if (cfg.onlyHolding && !p.getMainHandStack().isOf(Items.END_CRYSTAL) && !p.getOffHandStack().isOf(Items.END_CRYSTAL)) {
            return;
        }
        PlayerEntity target = null;
        double best = cfg.range;
        for (PlayerEntity e : c.world.getPlayers()) {
            if (e == p || !e.isAlive() || e.isSpectator() || e.isCreative()) continue;
            if (cfg.ignoreFriends && isFriend(e)) continue;
            double d = e.distanceTo(p);
            if (d < best) {
                best = d;
                target = e;
            }
        }
        if (target == null) return;
        targetName = target.getName().getString();
        ClientWorld w = c.world;
        double reach = cfg.placeRangeTenths / 10.0;
        int r = MathHelper.ceil(reach);
        Vec3d eye = p.getEyePos();
        BlockPos center = p.getBlockPos();
        float myHp = p.getHealth() + p.getAbsorptionAmount();
        List<Spot> all = new ArrayList<>();
        for (BlockPos b : BlockPos.iterate(center.add(-r, -r, -r), center.add(r, r, r))) {
            BlockState st = w.getBlockState(b);
            if (!st.isOf(Blocks.OBSIDIAN) && !st.isOf(Blocks.BEDROCK)) continue;
            if (eye.squaredDistanceTo(Vec3d.ofCenter(b)) > reach * reach) continue;
            BlockPos up = b.up();
            if (!w.getBlockState(up).isAir()) continue;
            Box box = new Box(up.getX(), up.getY(), up.getZ(), up.getX() + 1, up.getY() + 2, up.getZ() + 1);
            if (!w.getOtherEntities(null, box).isEmpty()) continue;
            Vec3d boom = new Vec3d(b.getX() + 0.5, b.getY() + 1, b.getZ() + 0.5);
            float enemy = damage(boom, target);
            if (enemy < cfg.minDamage) continue;
            float self = damage(boom, p);
            if (self > cfg.maxSelf || self >= myHp) continue;
            all.add(new Spot(b.toImmutable(), enemy, self));
        }
        all.sort((a, b2) -> Float.compare(b2.enemy - b2.self * 0.5f, a.enemy - a.self * 0.5f));
        for (int i = 0; i < Math.min(Math.max(1, cfg.count), all.size()); i++) SPOTS.add(all.get(i));

        if (pendingPos == null && cfg.autoPlace && autoCooldown == 0 && !SPOTS.isEmpty()) {
            // faqat yashil (eng yaxshi, SPOTS.get(0)) joyga - sariq (qolgan) joylarga qo'ymaymiz
            tryAutoPlace(c, p, SPOTS.get(0), cfg);
        }
    }

    /** Yashil joyga crystal qo'yadi (qarab, slot almashtirib - keyin darhol qaytib). Entity paydo bo'lishini kutib uramiz. */
    private static void tryAutoPlace(MinecraftClient c, ClientPlayerEntity p, Spot spot, CrystalHudConfig cfg) {
        if (InvHelper.screenOpen(c)) return;
        BlockPos ground = spot.pos();
        Vec3d hitVec = new Vec3d(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5);
        if (p.getEyePos().distanceTo(hitVec) > p.getBlockInteractionRange()) return;
        int slot = crystalSlot(c, p, cfg);
        if (slot < 0) return;

        int original = p.getInventory().getSelectedSlot();
        float yaw = p.getYaw(), pitch = p.getPitch();
        Vec3d eye = p.getEyePos();
        Rotations.sendLook(p, Rotations.yawTo(eye, hitVec), Rotations.pitchTo(eye, hitVec));
        p.getInventory().setSelectedSlot(slot);
        c.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(hitVec, Direction.UP, ground, false));
        p.swingHand(Hand.MAIN_HAND);
        p.getInventory().setSelectedSlot(original);
        Rotations.sendLook(p, yaw, pitch);
        if (borrowedInv >= 0 && InvHelper.returnItems()) {
            InvHelper.swap(c, p, borrowedInv, borrowedHotbar);
        }
        borrowedInv = -1;
        borrowedHotbar = -1;

        pendingPos = ground.up().toImmutable();
        pendingWait = 0;
    }

    /** Server crystal'ni dunyoga qo'shishini kutamiz (bitta tickda emas - ping bo'yicha), keyin uning o'ziga urib portlatamiz. */
    private static void tickPending(MinecraftClient c, ClientPlayerEntity p) {
        if (++pendingWait > 10) { pendingPos = null; autoCooldown = CrystalHudConfig.get().autoPlaceDelay; return; }
        List<EndCrystalEntity> found = c.world.getEntitiesByClass(EndCrystalEntity.class, new Box(pendingPos), e -> true);
        if (found.isEmpty()) return;
        EndCrystalEntity crystal = found.get(0);
        float yaw = p.getYaw(), pitch = p.getPitch();
        Vec3d eye = p.getEyePos();
        Vec3d center = crystal.getBoundingBox().getCenter();
        Rotations.sendLook(p, Rotations.yawTo(eye, center), Rotations.pitchTo(eye, center));
        c.interactionManager.attackEntity(p, crystal);
        p.swingHand(Hand.MAIN_HAND);
        Rotations.sendLook(p, yaw, pitch);
        pendingPos = null;
        autoCooldown = CrystalHudConfig.get().autoPlaceDelay;
    }

    /** Hotbar'dagi end crystal slot'i; bo'lmasa (ruxsat bo'lsa) inventardan bo'sh/eng yaqin hotbar slotiga almashtirib oladi. */
    private static int crystalSlot(MinecraftClient c, ClientPlayerEntity p, CrystalHudConfig cfg) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(Items.END_CRYSTAL)) return i;
        if (!cfg.fromInventory) return -1;
        int invSlot = -1;
        for (int i = 9; i < 36; i++) if (p.getInventory().getStack(i).isOf(Items.END_CRYSTAL)) { invSlot = i; break; }
        if (invSlot < 0) return -1;
        int sel = p.getInventory().getSelectedSlot();
        int target = -1;
        for (int h = 8; h >= 0; h--) if (h != sel && p.getInventory().getStack(h).isEmpty()) { target = h; break; }
        if (target < 0) target = sel == 8 ? 7 : 8;
        InvHelper.swap(c, p, invSlot, target);
        borrowedInv = invSlot;
        borrowedHotbar = target;
        return target;
    }

    /** Vanilla portlash zarari (kuch 6) + zirh kamaytirishi. */
    private static float damage(Vec3d boom, LivingEntity e) {
        double q = 12.0;
        double dist = Math.sqrt(e.squaredDistanceTo(boom)) / q;
        if (dist > 1.0) return 0;
        double impact = (1.0 - dist) * ExplosionImpl.calculateReceivedDamage(boom, e);
        float dmg = (float) ((impact * impact + impact) / 2.0 * 7.0 * q + 1.0);
        float armor = e.getArmor();
        float tough = (float) e.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        float f = 2.0f + tough / 4.0f;
        float g = MathHelper.clamp(armor - dmg / f, armor * 0.2f, 20.0f);
        return dmg * (1.0f - g / 25.0f);
    }

    private static void render(DrawContext ctx, RenderTickCounter counter) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.options.hudHidden || SPOTS.isEmpty()) return;
        Projector pr = Projector.create(c, counter.getTickProgress(true), ctx.getScaledWindowWidth(),
                ctx.getScaledWindowHeight());
        for (int i = 0; i < SPOTS.size(); i++) {
            Spot s = SPOTS.get(i);
            int color = i == 0 ? 0xFF33FF66 : 0xC0FFDD33;
            double y = s.pos.getY() + 1.01;
            double x0 = s.pos.getX(), z0 = s.pos.getZ();
            // blokning yuqori yuzasi chegarasi (nuqtalar bilan)
            for (int k = 0; k <= 10; k++) {
                double t = k / 10.0;
                dot(ctx, pr, new Vec3d(x0 + t, y, z0), color);
                dot(ctx, pr, new Vec3d(x0 + t, y, z0 + 1), color);
                dot(ctx, pr, new Vec3d(x0, y, z0 + t), color);
                dot(ctx, pr, new Vec3d(x0 + 1, y, z0 + t), color);
            }
            if (i == 0) {
                for (double a = 0.2; a < 0.85; a += 0.2) {
                    for (double b = 0.2; b < 0.85; b += 0.2) dot(ctx, pr, new Vec3d(x0 + a, y, z0 + b), 0x6033FF66);
                }
            }
            double[] top = pr.project(new Vec3d(x0 + 0.5, y + 0.6, z0 + 0.5));
            if (top == null) continue;
            String label = String.format("§c%.1f §7/ §a%.1f", s.enemy, s.self);
            int tw = c.textRenderer.getWidth(label);
            ctx.fill((int) top[0] - tw / 2 - 2, (int) top[1] - 10, (int) top[0] + tw / 2 + 2, (int) top[1], 0x90000000);
            ctx.drawTextWithShadow(c.textRenderer, label, (int) top[0] - tw / 2, (int) top[1] - 9, 0xFFFFFFFF);
        }
        if (targetName != null) {
            String line = "§dCrystal: §f" + targetName + " §7(§c" + String.format("%.1f", SPOTS.get(0).enemy) + "§7)";
            ctx.drawTextWithShadow(c.textRenderer, line,
                    ctx.getScaledWindowWidth() / 2 - c.textRenderer.getWidth(line) / 2,
                    ctx.getScaledWindowHeight() / 2 + 14, 0xFFFFFFFF);
        }
    }

    private static void dot(DrawContext ctx, Projector pr, Vec3d v, int color) {
        double[] s = pr.project(v);
        if (s != null) ctx.fill((int) s[0] - 1, (int) s[1] - 1, (int) s[0] + 1, (int) s[1] + 1, color);
    }

    private static boolean isFriend(PlayerEntity t) {
        String name = t.getName().getString();
        for (String f : AutoTntConfig.get().friends) if (f.equalsIgnoreCase(name)) return true;
        return false;
    }
}
