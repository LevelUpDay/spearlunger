package uz.autotnt;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;
import uz.spearlunger.Config;
import uz.spearlunger.InvHelper;
import uz.spearlunger.Rotations;
import uz.spearlunger.gui.Module;
import uz.spearlunger.gui.ModuleScreen;
import uz.spearlunger.gui.Modules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Yagona rejim: rail (sariq/oddiy/detector - farqi yo'q) qo'lda, o'ng tugmani yerga qarab bosib turasiz -
 * rail qo'yilgani bilanoq (vanilla UseBlockCallback) o'zi TNT minecart qo'yadi, kerak bo'lsa yo'liga olov
 * qo'yadi va (kamonda Flame bo'lmasa) kamondan otib yoqadi - hammasi portlagunga qadar. O'ng tugmani
 * qo'yib yuborsangiz, hali bajarilmagan qadamlar (joylashtirish/otish) bekor qilinadi - faqat
 * qarz olingan narsalar joyiga qaytariladi.
 */
public class TntController {

    /** true = qadam tugadi, false = kutish (keyingi tickda yana urinadi). */
    private interface Step { boolean run(); }

    private final ArrayDeque<Step> steps = new ArrayDeque<>();
    private int cooldown = 0;
    /** true bo'lganda qolgan qadamlar haqiqiy amal (qo'yish/otish) bajarmasdan, faqat tozalab o'tadi. */
    private boolean aborting = false;

    // qo'lda qo'yilgan rail
    private boolean internal = false;     // o'zimiz blok qo'yayotganimizda callback'ni e'tiborsiz qoldiramiz
    private BlockPos pendingRail = null;
    private int pendingTicks = 0;
    private boolean drawing = false;      // kamonni o'zimiz tortib turibmiz (useKey bosilgan, ignition uchun)

    // ------------------------------------------------------------ tick
    public void tick(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        if (p == null || client.world == null || client.interactionManager == null) {
            if (drawing) client.options.useKey.setPressed(false);
            drawing = false;
            pendingRail = null;
            steps.clear();
            aborting = false;
            return;
        }
        AutoTntConfig cfg = AutoTntConfig.get();

        while (AutoTntClient.keyProfile.wasPressed()) {
            cfg.active = (cfg.active + 1) % cfg.profiles.length;
            AutoTntConfig.save();
            say(p, "§bProfil: " + (cfg.active + 1) + "/" + cfg.profiles.length, true);
        }
        while (AutoTntClient.keySettings.wasPressed()) {
            Module m = Modules.byName("Auto TNT Cart");
            if (client.currentScreen == null && m != null) client.setScreen(new ModuleScreen(m, null));
        }
        while (AutoTntClient.keyToggle.wasPressed()) {
            cfg.enabled = !cfg.enabled;
            AutoTntConfig.save();
            say(p, cfg.enabled ? "§aAuto TNT Cart: ON" : "§cAuto TNT Cart: OFF", true);
        }
        if (!cfg.enabled) {
            pendingRail = null;
            if (!steps.isEmpty()) cancelSteps();
            if (drawing) {
                client.options.useKey.setPressed(false);
                drawing = false;
            }
        }

        // O'ng tugma qo'yib yuborilsa - hali bajarilmagan qadamlar (joylashtirish/otish) bekor qilinadi.
        // Haqiqiy (fizik) sichqoncha holatini tekshiramiz - o'zimiz useKey'ni mantiqan false/true qilib
        // qo'yganimiz (flint/kamonni avtomatik boshqarish uchun) bunga ta'sir qilmaydi.
        boolean rightDown = rightMouseHeld(client) || drawing;
        if (!rightDown) {
            if (pendingRail != null) pendingRail = null;
            if (!steps.isEmpty()) abortSteps(p);
        }

        if (pendingRail != null && steps.isEmpty()) {
            if (client.world.getBlockState(pendingRail).isIn(BlockTags.RAILS)) {
                BlockPos rp = pendingRail;
                pendingRail = null;
                String err = planRail(client, p, rp);
                if (err != null) {
                    say(p, "§cAuto TNT: " + err, false);
                    bad(p);
                }
            } else if (++pendingTicks > 10) {
                pendingRail = null;
            }
        }

        runSteps(cfg.p());
    }

    /** Modul o'chirilganda: qolgan qadamlarni (tayyor bo'lmasa ham) bitta-bitta ishga tushirib tugatamiz -
     *  shunda ularning oxiridagi "inventarni joyiga qaytarish" qadami ham ishlaydi, borrow qilingan narsa
     *  hotbar'da osilib qolmaydi. Haqiqiy portlatish/joylashtirish bajarilmasligi mumkin - bu kutilgan holat. */
    private void cancelSteps() {
        int guard = 0;
        while (!steps.isEmpty() && guard++ < 200) {
            steps.poll().run();
        }
    }

    /** O'ng tugma qo'yib yuborilganda: haqiqiy amal (qo'yish/otish) bajarmasdan, faqat qarz olingan
     *  narsalarni joyiga qaytaramiz va tanlangan slotni qaytaramiz - "aborting" flagini qadamlarning o'zi
     *  tekshiradi (addUse/addFire/WaitCartStep/BowShotStep). */
    private void abortSteps(ClientPlayerEntity p) {
        aborting = true;
        try {
            cancelSteps();
        } finally {
            aborting = false;
        }
        say(p, "§7Auto TNT: to'xtatildi (mishka qo'yib yuborildi).", true);
    }

    private void runSteps(AutoTntConfig.Profile cf) {
        if (steps.isEmpty()) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        int n = 0;
        while (!steps.isEmpty() && n++ < 60) {
            if (!steps.peek().run()) return;   // kutish kerak
            steps.poll();
            cooldown = Math.max(0, cf.delayTicks);
            if (cooldown > 0) break;
        }
    }

    // ------------------------------------------------------------ rail rejimi
    /** O'yinchi rail qo'yganda (UseBlockCallback, o'ng tugmani yerga qarab bosib turganda). Rail haqiqatan
     *  paydo bo'lgach tick() ishga tushiradi. Qanaqa rail bo'lishi farqi yo'q (oddiy/powered/detector). */
    public ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        if (internal || !(player instanceof ClientPlayerEntity p)) return ActionResult.PASS;
        AutoTntConfig cfg = AutoTntConfig.get();
        if (!cfg.enabled || !steps.isEmpty()) return ActionResult.PASS;
        if (!p.getStackInHand(hand).isIn(ItemTags.RAILS)) return ActionResult.PASS;
        BlockPos pos = hit.getBlockPos();
        if (!world.getBlockState(pos).isReplaceable()) pos = pos.offset(hit.getSide());
        pendingRail = pos;
        pendingTicks = 0;
        return ActionResult.PASS;
    }

    private String planRail(MinecraftClient c, ClientPlayerEntity p, BlockPos rail) {
        List<int[]> swaps = new ArrayList<>();
        String err = planRailInner(c, p, rail, swaps);
        if (err != null && AutoTntConfig.get().restoreInventory) restoreSwaps(c, p, swaps);
        return err;
    }

    /**
     * Qo'yilgan rail ustiga TNT minecart qo'yadi, keyin:
     *  - kamonda Flame bo'lsa: shunchaki cartga otadi (yonayotgan o'q cartni darhol portlatadi);
     *  - Flame bo'lmasa: cart oldiga (siz tomonga) flint bilan olov qo'yadi, o'q olovdan o'tib yonadi va cartga tegadi;
     *  - kamon/o'q bo'lmasa: cartni flint bilan yoqadi.
     */
    private String planRailInner(MinecraftClient c, ClientPlayerEntity p, BlockPos rail, List<int[]> swaps) {
        AutoTntConfig cfg = AutoTntConfig.get();
        AutoTntConfig.Profile cf = cfg.p();
        final Config.AntiCheat ac = Config.get().ac();
        ClientWorld w = c.world;
        final float origYaw = p.getYaw(), origPitch = p.getPitch();
        final int mode = Math.floorMod(cf.ignition, 4);
        final int original = p.getInventory().getSelectedSlot();
        boolean[] reserved = new boolean[9];
        reserved[original] = true;
        // o'ng tugma bosib turilsa vanilla boshqa narsani (flint, kamon) ishlatib yubormasin
        c.options.useKey.setPressed(false);

        if (cf.friendRadius > 0 && nearFriend(w, Vec3d.ofCenter(rail), cf.friendRadius)) {
            return "portlash joyi yaqinida do'st bor";
        }
        if (mode == 3 && !w.getBlockState(rail).isOf(Blocks.ACTIVATOR_RAIL)) {
            return "olov rejimi Activator: activator rail qo'ying";
        }
        final int cartSlot = ensure(c, p, swaps, Items.TNT_MINECART, reserved);
        if (cartSlot < 0) return "TNT minecart yo'q (hotbar/inventar)";

        int bowSlot = -1;
        boolean flame = false;
        if (cfg.railBow && mode != 3) {
            bowSlot = ensure(c, p, swaps, Items.BOW, reserved);
            if (bowSlot >= 0) {
                ItemStack bow = p.getInventory().getStack(bowSlot);
                if (p.getProjectileType(bow).isEmpty() && !p.getAbilities().creativeMode) {
                    say(p, "§eAuto TNT: o'q yo'q, kamonsiz yoqaman.", false);
                    bowSlot = -1;
                } else {
                    flame = hasEnchant(bow, "flame");
                }
            }
        }
        final boolean useBow = bowSlot >= 0;

        Item igniter = null;
        if ((mode == 0 || mode == 2) && !(useBow && flame)) igniter = mode == 2 ? Items.FIRE_CHARGE : Items.FLINT_AND_STEEL;
        else if (mode == 1) igniter = Items.FLINT_AND_STEEL;
        int ignSlot = -1;
        if (igniter != null) {
            ignSlot = ensure(c, p, swaps, igniter, reserved);
            if (ignSlot < 0) {
                return (igniter == Items.FIRE_CHARGE ? "fire charge" : "flint and steel") + " yo'q (hotbar/inventar)"
                        + (useBow ? ", kamonda Flame ham yo'q" : "");
            }
        }
        int redSlot = -1;
        if (mode == 3) {
            redSlot = ensure(c, p, swaps, Items.REDSTONE_BLOCK, reserved);
            if (redSlot < 0) return "redstone block yo'q (hotbar/inventar)";
        }

        // olovni o'q yo'liga (cart va siz orasidagi blokka) qo'yamiz
        Vec3d eye = p.getEyePos();
        Direction toMe = horizDir(eye.x - (rail.getX() + 0.5), eye.z - (rail.getZ() + 0.5));
        BlockPos fireBase = rail.down().offset(toMe);
        boolean onPath = igniter != null && mode != 1 && useBow
                && canPlaceOnTop(w, fireBase)
                && !p.getBoundingBox().intersects(new Box(fireBase.up()))
                && eye.distanceTo(Vec3d.ofCenter(fireBase.up())) <= cf.reachTenths / 10.0;

        // ---- qadamlar
        addUse(c, p, cartSlot, rail);
        if (igniter != null) {
            if (onPath) addUse(c, p, ignSlot, fireBase);
            else steps.add(new WaitCartStep(c, p, rail, ignSlot));
        }
        if (mode == 3) {
            BlockPos cand = null;
            for (Direction d : new Direction[] { toMe.getOpposite(), toMe.rotateYClockwise(), toMe.rotateYCounterclockwise(), toMe }) {
                if (canPlaceOnTop(w, rail.down().offset(d))) { cand = rail.down().offset(d); break; }
            }
            if (cand != null) addUse(c, p, redSlot, cand);
            else steps.add(() -> { say(p, "§eRedstone block qo'yib bo'lmadi (joy mos emas).", false); return true; });
        }
        if (useBow) steps.add(new BowShotStep(c, p, bowSlot, rail, Math.max(4, cfg.bowPullTicks)));

        if (ac.restoreView && (ac.rotateToBlocks || useBow)) {
            steps.add(() -> Rotations.turn(p, origYaw, origPitch));
        }
        steps.add(() -> {
            if (cfg.restoreInventory) restoreSwaps(c, p, swaps);
            p.getInventory().setSelectedSlot(original);
            return true;
        });

        say(p, "§aAuto TNT" + (useBow ? (flame ? " (Flame kamon)" : onPath ? " (olov + kamon)" : " (flint + kamon)") : ""), true);
        ok(p);
        return null;
    }

    /** Cart paydo bo'lishini kutadi, unga qarab kamonni qisqa tortadi va qo'yib yuboradi. */
    private class BowShotStep implements Step {
        private final MinecraftClient c;
        private final ClientPlayerEntity p;
        private final int slot;
        private final BlockPos railPos;
        private final int pull;
        private int phase = 0;
        private int t = 0;
        private Vec3d target = null;

        BowShotStep(MinecraftClient c, ClientPlayerEntity p, int slot, BlockPos railPos, int pull) {
            this.c = c; this.p = p; this.slot = slot; this.railPos = railPos; this.pull = pull;
        }

        @Override
        public boolean run() {
            if (aborting) { release(); return true; }
            List<Entity> found = c.world.getEntitiesByClass(Entity.class, new Box(railPos).expand(0.8),
                    e -> e.getType() == EntityType.TNT_MINECART);
            if (!found.isEmpty()) target = found.get(0).getBoundingBox().getCenter().add(0, -0.1, 0);
            if (target == null) {
                if (++t > 40) {
                    say(p, "§eMinecart paydo bo'lmadi, otilmadi.", false);
                    return true;
                }
                return false;
            }
            int use = phase == 1 ? Math.max(pull, p.getItemUseTime()) : pull;
            float[] aim = bowAim(p.getEyePos(), target, BowItem.getPullProgress(use) * 3.0);
            boolean aimed = Rotations.turn(p, aim[0], aim[1], 1.5f);
            if (phase == 0) {
                if (!aimed && ++t < 60) return false;
                p.getInventory().setSelectedSlot(slot);
                c.interactionManager.interactItem(p, Hand.MAIN_HAND);
                c.options.useKey.setPressed(true);   // aks holda vanilla kamonni darhol qo'yib yuboradi
                drawing = true;
                phase = 1;
                t = 0;
                return false;
            }
            if (!p.isUsingItem() || !p.getActiveItem().isOf(Items.BOW)) {
                if (++t <= 3) return false;
                release();
                say(p, "§eKamon tortilmadi.", false);
                return true;
            }
            if (p.getItemUseTime() < pull || (!aimed && p.getItemUseTime() < pull + 20)) return false;
            release();
            c.interactionManager.stopUsingItem(p);
            return true;
        }

        private void release() {
            c.options.useKey.setPressed(false);
            drawing = false;
        }
    }

    /** O'qning uchish yo'lini (gravitatsiya + qarshilik) hisoblab, tegadigan blokni topadi - faqat HUD preview uchun. */
    public String preview(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (p == null || c.world == null) return null;
        Vec3d vel = null;
        if (p.isUsingItem() && p.getActiveItem().isOf(Items.BOW)) {
            vel = p.getRotationVec(1.0f).multiply(BowItem.getPullProgress(p.getItemUseTime()) * 3.0);
        } else if (p.getMainHandStack().isOf(Items.CROSSBOW)) {
            ChargedProjectilesComponent cp = p.getMainHandStack().get(DataComponentTypes.CHARGED_PROJECTILES);
            if (cp != null && !cp.isEmpty()) vel = p.getRotationVec(1.0f).multiply(3.15);
        }
        if (vel == null || vel.lengthSquared() < 0.01) return null;
        Vec3d eye = p.getEyePos();
        BlockHitResult hit = simulate(c.world, p, eye, vel);
        if (hit == null) return "§7O'q tushadigan joy: yo'q";
        BlockPos g = findGround(c.world, hit);
        if (g == null) return "§7O'q tushadigan joy: yer yo'q";
        AutoTntConfig.Profile cf = AutoTntConfig.get().p();
        double d = eye.distanceTo(Vec3d.ofCenter(g.up()));
        String tag = d > cf.reachTenths / 10.0 ? "§c(uzoq)" : d < cf.minSelfDistTenths / 10.0 ? "§c(yaqin)" : "§a(OK)";
        return String.format("§7O'q tushadi: %.1f blok %s", d, tag);
    }

    /** O'qning uchish yo'lini (gravitatsiya + qarshilik) hisoblab, tegadigan blokni topadi. */
    private BlockHitResult simulate(ClientWorld w, ClientPlayerEntity p, Vec3d start, Vec3d vel) {
        Vec3d pos = start, v = vel;
        for (int i = 0; i < 200; i++) {
            Vec3d next = pos.add(v);
            BlockHitResult r = w.raycast(new RaycastContext(pos, next,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
            if (r.getType() == HitResult.Type.BLOCK) return r;
            pos = next;
            v = v.multiply(0.99).add(0, -0.05, 0);
            if (pos.y < w.getBottomY()) break;
        }
        return null;
    }

    private BlockPos findGround(ClientWorld w, BlockHitResult hit) {
        BlockPos b = hit.getBlockPos();
        if (hit.getSide() == Direction.UP) return b;
        BlockPos start = b.offset(hit.getSide());
        for (int i = 1; i <= 4; i++) {
            BlockPos g = start.down(i);
            if (w.getBlockState(g).isSideSolidFullSquare(w, g, Direction.UP)) return g;
        }
        return null;
    }

    /** O'qning tushadigan nuqtasiga qarab {yaw, pitch}ni hisoblaydi (ikki yechimdan pastroq yoyni tanlaydi). */
    private static float[] bowAim(Vec3d eye, Vec3d target, double speed) {
        float yaw = Rotations.yawTo(eye, target);
        float straight = Rotations.pitchTo(eye, target);
        double dx = target.x - eye.x, dz = target.z - eye.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double dy = target.y - (eye.y - 0.1);   // o'q ko'zdan 0.1 pastda paydo bo'ladi
        if (dist < 0.5 || speed < 0.05) return new float[] { yaw, straight };
        float best = straight;
        double bestErr = Double.MAX_VALUE;
        for (float pitch = -60.0f; pitch <= 85.0f; pitch += 0.25f) {
            double rad = Math.toRadians(pitch);
            double vh = Math.cos(rad) * speed, vy = -Math.sin(rad) * speed;
            double x = 0, y = 0;
            for (int i = 0; i < 100 && vh > 1e-4; i++) {
                double nx = x + vh, ny = y + vy;
                if (nx >= dist) {
                    double hy = y + (ny - y) * ((dist - x) / (nx - x));
                    double err = Math.abs(hy - dy) + Math.abs(pitch - straight) * 0.002;
                    if (err < bestErr) { bestErr = err; best = pitch; }
                    break;
                }
                x = nx;
                y = ny;
                vh *= 0.99;
                vy = vy * 0.99 - 0.05;
                if (y < dy - 30) break;
            }
        }
        return new float[] { yaw, best };
    }

    // ------------------------------------------------------------ yordamchilar
    /** Sichqonchaning o'ng tugmasi haqiqatan (fizik) bosilib turgan-turmaganini tekshiradi - useKey.isPressed()
     *  biz ustidan mantiqan o'zgartirib qo'yganimiz uchun ishonchli emas. */
    private static boolean rightMouseHeld(MinecraftClient c) {
        InputUtil.Key k = KeyBindingHelper.getBoundKeyOf(c.options.useKey);
        if (k.getCode() < 0) return false;
        if (k.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(c.getWindow().getHandle(), k.getCode()) == GLFW.GLFW_PRESS;
        }
        if (k.getCategory() == InputUtil.Type.KEYSYM) {
            return InputUtil.isKeyPressed(c.getWindow(), k.getCode());
        }
        return false;
    }

    private void say(ClientPlayerEntity p, String msg, boolean actionBar) {
        if (AutoTntConfig.get().messages) p.sendMessage(Text.literal(msg), actionBar);
    }

    private void ok(ClientPlayerEntity p) {
        if (AutoTntConfig.get().sounds) p.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
    }

    private void bad(ClientPlayerEntity p) {
        if (AutoTntConfig.get().sounds) p.playSound(SoundEvents.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
    }

    /** Blokka (kerak bo'lsa avval unga silliq qarab) item ishlatish qadamlarini qo'shadi. aborting bo'lsa o'tkazib yuboradi. */
    private void addUse(MinecraftClient c, ClientPlayerEntity p, int slot, BlockPos base) {
        if (Config.get().ac().rotateToBlocks) {
            Vec3d point = Vec3d.ofCenter(base).add(0, 0.5, 0);
            steps.add(() -> {
                if (aborting) return true;
                Vec3d eye = p.getEyePos();
                return Rotations.turn(p, Rotations.yawTo(eye, point), Rotations.pitchTo(eye, point));
            });
        }
        steps.add(() -> {
            if (aborting) return true;
            useOnBlock(c, p, slot, base);
            return true;
        });
    }

    /** Minecart paydo bo'lishini kutib, unga flint and steel ishlatadi. */
    private class WaitCartStep implements Step {
        private final MinecraftClient c;
        private final ClientPlayerEntity p;
        private final BlockPos railPos;
        private final int slot;
        private int waited = 0;

        WaitCartStep(MinecraftClient c, ClientPlayerEntity p, BlockPos railPos, int slot) {
            this.c = c; this.p = p; this.railPos = railPos; this.slot = slot;
        }

        @Override
        public boolean run() {
            if (aborting) return true;
            List<Entity> found = c.world.getEntitiesByClass(Entity.class, new Box(railPos).expand(0.8),
                    e -> e.getType() == EntityType.TNT_MINECART);
            if (found.isEmpty()) {
                if (++waited > 40) {
                    say(p, "§eMinecart paydo bo'lmadi, yoqilmadi.", false);
                    return true;
                }
                return false;
            }
            Entity cart = found.get(0);
            if (Config.get().ac().rotateToBlocks) {
                Vec3d eye = p.getEyePos(), to = cart.getBoundingBox().getCenter();
                if (!Rotations.turn(p, Rotations.yawTo(eye, to), Rotations.pitchTo(eye, to)) && ++waited <= 60) return false;
            }
            p.getInventory().setSelectedSlot(slot);
            c.interactionManager.interactEntity(p, cart, Hand.MAIN_HAND);
            return true;
        }
    }

    /** ensure() inventardan hotbar'ga ko'chirgan narsalarni joyiga qaytaradi (SWAP simmetrik). */
    private void restoreSwaps(MinecraftClient c, ClientPlayerEntity p, List<int[]> swaps) {
        for (int i = swaps.size() - 1; i >= 0; i--) {
            int[] sw = swaps.get(i);
            InvHelper.swap(c, p, sw[0], sw[1]);
        }
        swaps.clear();
    }

    private void useOnBlock(MinecraftClient c, ClientPlayerEntity p, int slot, BlockPos base) {
        p.getInventory().setSelectedSlot(slot);
        BlockHitResult r = new BlockHitResult(Vec3d.ofCenter(base).add(0, 0.5, 0), Direction.UP, base, false);
        internal = true;
        try {
            c.interactionManager.interactBlock(p, Hand.MAIN_HAND, r);
        } finally {
            internal = false;
        }
    }

    private static boolean canPlaceOnTop(ClientWorld w, BlockPos base) {
        return w.getBlockState(base).isSideSolidFullSquare(w, base, Direction.UP)
                && w.getBlockState(base.up()).isAir();
    }

    private static Direction horizDir(double x, double z) {
        return Math.abs(x) > Math.abs(z)
                ? (x > 0 ? Direction.EAST : Direction.WEST)
                : (z > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    private boolean isFriend(PlayerEntity t) {
        String name = t.getName().getString();
        for (String f : AutoTntConfig.get().friends) if (f.equalsIgnoreCase(name)) return true;
        return false;
    }

    private boolean nearFriend(ClientWorld w, Vec3d pos, double radius) {
        for (var t : w.getPlayers()) {
            if (isFriend(t) && t.getEntityPos().distanceTo(pos) <= radius) return true;
        }
        return false;
    }

    private static int find(ClientPlayerEntity p, Predicate<ItemStack> item) {
        for (int i = 0; i < 9; i++) {
            if (item.test(p.getInventory().getStack(i))) return i;
        }
        return -1;
    }

    /** Narsa hotbar'da bo'lsa slotini qaytaradi; yo'q bo'lsa inventardan hotbar'ga ko'chiradi. */
    private int ensure(MinecraftClient c, ClientPlayerEntity p, List<int[]> swaps, Item item, boolean[] reserved) {
        return ensure(c, p, swaps, st -> st.isOf(item), reserved);
    }

    private int ensure(MinecraftClient c, ClientPlayerEntity p, List<int[]> swaps, Predicate<ItemStack> item,
                       boolean[] reserved) {
        int s = find(p, item);
        if (s >= 0) { reserved[s] = true; return s; }
        if (!AutoTntConfig.get().autoRefill) return -1;
        for (int inv = 9; inv < 36; inv++) {
            if (!item.test(p.getInventory().getStack(inv))) continue;
            int target = -1;
            for (int h = 0; h < 9; h++) {
                if (!reserved[h] && p.getInventory().getStack(h).isEmpty()) { target = h; break; }
            }
            if (target < 0) {
                for (int h = 0; h < 9; h++) if (!reserved[h]) { target = h; break; }
            }
            if (target < 0) return -1;
            InvHelper.swap(c, p, inv, target);
            swaps.add(new int[] { inv, target });
            reserved[target] = true;
            return target;
        }
        return -1;
    }

    private static boolean hasEnchant(ItemStack s, String name) {
        for (RegistryEntry<Enchantment> e : s.getEnchantments().getEnchantments()) {
            if (e.getKey().map(k -> k.getValue().getPath().equals(name)).orElse(false)) return true;
        }
        return false;
    }
}
