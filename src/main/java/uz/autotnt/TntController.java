package uz.autotnt;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TntController {

    /** true = qadam tugadi, false = kutish (keyingi tickda yana urinadi). */
    private interface Step { boolean run(); }

    private final ArrayDeque<Step> steps = new ArrayDeque<>();
    private int cooldown = 0;

    // kamon tortilishini kuzatish
    private boolean wasUsingBow = false;
    private int lastUseTime = 0;
    private boolean lastFlame = false;
    private Vec3d lastLook = Vec3d.ZERO;

    // o'yinchilar tezligini kuzatish (nishonni oldindan hisoblash uchun)
    private final Map<UUID, Vec3d> lastPos = new HashMap<>();
    private final Map<UUID, Vec3d> velocity = new HashMap<>();

    // ------------------------------------------------------------ tick
    public void tick(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        if (p == null || client.world == null || client.interactionManager == null) {
            steps.clear();
            wasUsingBow = false;
            lastPos.clear();
            velocity.clear();
            return;
        }
        AutoTntConfig cfg = AutoTntConfig.get();

        for (AbstractClientPlayerEntity t : client.world.getPlayers()) {
            Vec3d cur = t.getEntityPos();
            Vec3d prev = lastPos.get(t.getUuid());
            if (prev != null) velocity.put(t.getUuid(), cur.subtract(prev));
            lastPos.put(t.getUuid(), cur);
        }

        while (AutoTntClient.keyBow.wasPressed()) {
            cfg.bowMode = !cfg.bowMode;
            AutoTntConfig.save();
            say(p, cfg.bowMode ? "§aBow auto-TNT: ON" : "§cBow auto-TNT: OFF", true);
        }
        while (AutoTntClient.keyProfile.wasPressed()) {
            cfg.active = (cfg.active + 1) % cfg.profiles.length;
            AutoTntConfig.save();
            say(p, "§bProfil: " + (cfg.active + 1) + "/" + cfg.profiles.length, true);
        }
        boolean crossPressed = false;
        while (AutoTntClient.keyCrossbow.wasPressed()) crossPressed = true;
        while (AutoTntClient.keySettings.wasPressed()) {
            if (client.currentScreen == null) client.setScreen(new TntSettingsScreen(null));
        }

        runSteps(cfg.p());

        ItemStack active = p.isUsingItem() ? p.getActiveItem() : ItemStack.EMPTY;
        boolean usingBow = active.isOf(Items.BOW);
        if (usingBow) {
            lastUseTime = p.getItemUseTime();
            lastFlame = hasEnchant(active, "flame");
            lastLook = p.getRotationVec(1.0f);
        } else if (wasUsingBow && cfg.bowMode && steps.isEmpty()) {
            onBowRelease(client, p);
        }
        wasUsingBow = usingBow;

        if (crossPressed && steps.isEmpty() && client.currentScreen == null) {
            onCrossbow(client, p);
        }
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

    // ------------------------------------------------------------ bow
    private void onBowRelease(MinecraftClient client, ClientPlayerEntity p) {
        float pull = BowItem.getPullProgress(lastUseTime);
        if (pull < 0.1f) return;
        Vec3d vel = lastLook.multiply(pull * 3.0);
        String err = plan(client, p, vel, lastFlame, -1);
        if (err != null) {
            say(p, "§cBow auto-TNT: " + err, false);
            bad(p);
        }
    }

    // ------------------------------------------------------------ crossbow
    private void onCrossbow(MinecraftClient client, ClientPlayerEntity p) {
        int cs = find(p, Items.CROSSBOW);
        if (cs < 0) { crossFail(p, "hotbar'da crossbow yo'q"); return; }
        ChargedProjectilesComponent cp = p.getInventory().getStack(cs).get(DataComponentTypes.CHARGED_PROJECTILES);
        if (cp == null || cp.isEmpty()) { crossFail(p, "crossbow o'qlanmagan"); return; }
        List<ItemStack> loaded = cp.getProjectiles();
        if (!loaded.isEmpty() && loaded.get(0).isOf(Items.FIREWORK_ROCKET)) {
            crossFail(p, "crossbow'da firework o'qlangan (faqat o'q qo'llab-quvvatlanadi)");
            return;
        }
        say(p, "§aCrossbow: ON", true);
        Vec3d vel = p.getRotationVec(1.0f).multiply(3.15);
        String err = plan(client, p, vel, false, cs);
        if (err != null) crossFail(p, err);
    }

    private void crossFail(ClientPlayerEntity p, String reason) {
        say(p, "§cCrossbow auto-TNT: " + reason, false);
        say(p, "§7Crossbow: OFF", true);
        bad(p);
    }

    // ------------------------------------------------------------ reja
    /** Xato bo'lsa sababini qaytaradi, hammasi joyida bo'lsa null. */
    private String plan(MinecraftClient c, ClientPlayerEntity p, Vec3d vel, boolean flame, int crossSlot) {
        AutoTntConfig cfg = AutoTntConfig.get();
        AutoTntConfig.Profile cf = cfg.p();
        ClientWorld w = c.world;
        final int mode = Math.floorMod(cf.ignition, 4);
        final boolean needFlint = (mode == 0 && !flame) || mode == 1;

        final int original = p.getInventory().getSelectedSlot();
        boolean[] reserved = new boolean[9];
        reserved[original] = true;
        if (crossSlot >= 0) reserved[crossSlot] = true;

        // ---- kerakli narsalarni hotbar'ga tayyorlaymiz (kerak bo'lsa inventardan olib keladi)
        final int railSlot = ensure(c, p, mode == 3 ? Items.ACTIVATOR_RAIL : Items.RAIL, reserved);
        if (railSlot < 0) return (mode == 3 ? "activator rail" : "rail") + " yo'q (hotbar/inventar)";
        final int cart0 = ensure(c, p, Items.TNT_MINECART, reserved);
        if (cart0 < 0) return "TNT minecart yo'q (hotbar/inventar)";
        int flintTmp = -1, fireTmp = -1, redTmp = -1;
        if (needFlint) {
            flintTmp = ensure(c, p, Items.FLINT_AND_STEEL, reserved);
            if (flintTmp < 0) return (mode == 0 ? "Flame yo'q va " : "") + "flint and steel yo'q (hotbar/inventar)";
        }
        if (mode == 2) {
            fireTmp = ensure(c, p, Items.FIRE_CHARGE, reserved);
            if (fireTmp < 0) return "fire charge yo'q (hotbar/inventar)";
        }
        if (mode == 3) {
            redTmp = ensure(c, p, Items.REDSTONE_BLOCK, reserved);
            if (redTmp < 0) return "redstone block yo'q (hotbar/inventar)";
        }
        final int flintSlot = flintTmp, fireSlot = fireTmp, redSlot = redTmp;

        List<Integer> cartSlots = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(Items.TNT_MINECART)) for (int k = 0; k < s.getCount(); k++) cartSlots.add(i);
        }

        // ---- nishon joyni aniqlaymiz
        Vec3d eye = p.getEyePos();
        BlockPos ground;
        Direction dir;
        PlayerEntity target = cf.predictTarget ? pickTarget(w, p, eye, vel) : null;
        if (target != null) {
            Vec3d v = velocity.getOrDefault(target.getUuid(), Vec3d.ZERO);
            Vec3d pred = target.getEntityPos().add(v.x * cf.lookaheadTicks, 0, v.z * cf.lookaheadTicks);
            ground = groundBelow(w, BlockPos.ofFloored(pred));
            if (ground == null) return "nishon ostida tekis yer topilmadi";
            dir = horizDir(pred.x - eye.x, pred.z - eye.z);
        } else {
            BlockHitResult hit = simulate(w, p, eye, vel);
            if (hit == null) return "o'q yo'li hech qaysi blokka tegmayapti";
            ground = findGround(w, hit);
            if (ground == null) return "o'q tushadigan joyda tekis yer topilmadi";
            dir = horizDir(vel.x, vel.z);
        }

        // ---- zanjir pozitsiyalari (tekshiruvlar bilan)
        double reach = cf.reachTenths / 10.0;
        double minSelf = cf.minSelfDistTenths / 10.0;
        int want = Math.min(Math.min(cf.cartCount, cartSlots.size()), p.getInventory().getStack(railSlot).getCount());
        want = Math.max(want, 1);
        List<BlockPos> grounds = new ArrayList<>();
        for (int i = 0; i < want; i++) {
            BlockPos g = ground.offset(dir, i);
            BlockPos r = g.up();
            String why = null;
            if (!w.getBlockState(g).isSideSolidFullSquare(w, g, Direction.UP)) {
                why = "ostida qattiq yer yo'q";
            } else if (!w.getBlockState(r).isIn(BlockTags.RAILS) && !w.getBlockState(r).isReplaceable()) {
                why = "rail qo'yadigan joy band";
            } else {
                double d = eye.distanceTo(Vec3d.ofCenter(r));
                if (d > reach) why = String.format("joy juda uzoq (%.1f blok, limit %.1f)", d, reach);
                else if (d < minSelf) why = String.format("joy sizga juda yaqin (%.1f blok, minimum %.1f)", d, minSelf);
            }
            if (why != null) {
                if (i == 0) return why;
                break;
            }
            grounds.add(g);
        }
        if (cf.friendRadius > 0) {
            for (BlockPos g : grounds) {
                if (nearFriend(w, Vec3d.ofCenter(g.up()), cf.friendRadius)) return "portlash joyi yaqinida do'st bor";
            }
        }

        // ---- qadamlar
        final int n = grounds.size();
        for (int i = 0; i < n; i++) {
            final BlockPos g = grounds.get(i);
            if (!w.getBlockState(g.up()).isIn(BlockTags.RAILS)) {
                steps.add(() -> { useOnBlock(c, p, railSlot, g); return true; });
            }
        }
        final List<BlockPos> railPositions = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int slot = cartSlots.get(i);
            final BlockPos rp = grounds.get(i).up();
            railPositions.add(rp);
            steps.add(() -> { useOnBlock(c, p, slot, rp); return true; });
        }

        BlockPos lastGround = grounds.get(n - 1);
        if ((mode == 0 && !flame) || mode == 2) {
            addFire(c, p, w, mode == 2 ? fireSlot : flintSlot, lastGround.offset(dir));
        } else if (mode == 1) {
            for (BlockPos rp : railPositions) steps.add(new WaitCartStep(c, p, rp, flintSlot));
        } else if (mode == 3) {
            BlockPos a = grounds.get(0).offset(dir.getOpposite());
            BlockPos b = lastGround.offset(dir);
            BlockPos cand = canPlaceOnTop(w, a) ? a : (canPlaceOnTop(w, b) ? b : null);
            if (cand != null) {
                final BlockPos cc = cand;
                steps.add(() -> { useOnBlock(c, p, redSlot, cc); return true; });
            } else {
                steps.add(() -> { say(p, "§eRedstone block qo'yib bo'lmadi (joy mos emas).", false); return true; });
            }
        }

        if (crossSlot >= 0) {
            final int cs = crossSlot;
            steps.add(() -> {
                p.getInventory().setSelectedSlot(cs);
                c.interactionManager.interactItem(p, Hand.MAIN_HAND);
                return true;
            });
        }

        final boolean cross = crossSlot >= 0;
        steps.add(() -> {
            p.getInventory().setSelectedSlot(original);
            if (cross) say(p, "§7Crossbow: OFF", true);
            return true;
        });

        say(p, "§aAuto TNT: " + n + " ta minecart", true);
        ok(p);
        return null;
    }

    private void addFire(MinecraftClient c, ClientPlayerEntity p, ClientWorld w, int slot, BlockPos ahead) {
        if (canPlaceOnTop(w, ahead)) {
            steps.add(() -> { useOnBlock(c, p, slot, ahead); return true; });
        } else {
            steps.add(() -> { say(p, "§eOlovni oldinga qo'yib bo'lmadi (joy mos emas).", false); return true; });
        }
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
            List<Entity> found = c.world.getEntitiesByClass(Entity.class, new Box(railPos).expand(0.8),
                    e -> e.getType() == EntityType.TNT_MINECART);
            if (found.isEmpty()) {
                if (++waited > 40) {
                    say(p, "§eMinecart paydo bo'lmadi, yoqilmadi.", false);
                    return true;
                }
                return false;
            }
            p.getInventory().setSelectedSlot(slot);
            c.interactionManager.interactEntity(p, found.get(0), Hand.MAIN_HAND);
            return true;
        }
    }

    // ------------------------------------------------------------ HUD uchun ko'rsatma
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

    // ------------------------------------------------------------ yordamchilar
    private void say(ClientPlayerEntity p, String msg, boolean actionBar) {
        if (AutoTntConfig.get().messages) p.sendMessage(Text.literal(msg), actionBar);
    }

    private void ok(ClientPlayerEntity p) {
        if (AutoTntConfig.get().sounds) p.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
    }

    private void bad(ClientPlayerEntity p) {
        if (AutoTntConfig.get().sounds) p.playSound(SoundEvents.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
    }

    private void useOnBlock(MinecraftClient c, ClientPlayerEntity p, int slot, BlockPos base) {
        p.getInventory().setSelectedSlot(slot);
        BlockHitResult r = new BlockHitResult(Vec3d.ofCenter(base).add(0, 0.5, 0), Direction.UP, base, false);
        c.interactionManager.interactBlock(p, Hand.MAIN_HAND, r);
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
        for (AbstractClientPlayerEntity t : w.getPlayers()) {
            if (isFriend(t) && t.getEntityPos().distanceTo(pos) <= radius) return true;
        }
        return false;
    }

    /** Qarash chizig'iga eng yaqin (do'st bo'lmagan) o'yinchi. */
    private PlayerEntity pickTarget(ClientWorld w, ClientPlayerEntity self, Vec3d eye, Vec3d dir) {
        PlayerEntity best = null;
        double bestCos = 0.985;
        Vec3d d = dir.normalize();
        for (AbstractClientPlayerEntity t : w.getPlayers()) {
            if (t == self || t.isSpectator() || !t.isAlive() || isFriend(t)) continue;
            Vec3d to = t.getEyePos().subtract(eye);
            double len = to.length();
            if (len < 1.0 || len > 80.0) continue;
            double cos = to.normalize().dotProduct(d);
            if (cos > bestCos) { bestCos = cos; best = t; }
        }
        return best;
    }

    private BlockPos groundBelow(ClientWorld w, BlockPos from) {
        for (int i = 0; i < 12; i++) {
            BlockPos g = from.down(i);
            if (w.getBlockState(g).isSideSolidFullSquare(w, g, Direction.UP)) return g;
        }
        return null;
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

    private static int find(ClientPlayerEntity p, Item item) {
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    /** Narsa hotbar'da bo'lsa slotini qaytaradi; yo'q bo'lsa inventardan hotbar'ga ko'chiradi. */
    private int ensure(MinecraftClient c, ClientPlayerEntity p, Item item, boolean[] reserved) {
        int s = find(p, item);
        if (s >= 0) { reserved[s] = true; return s; }
        if (!AutoTntConfig.get().autoRefill) return -1;
        for (int inv = 9; inv < 36; inv++) {
            if (!p.getInventory().getStack(inv).isOf(item)) continue;
            int target = -1;
            for (int h = 0; h < 9; h++) {
                if (!reserved[h] && p.getInventory().getStack(h).isEmpty()) { target = h; break; }
            }
            if (target < 0) {
                for (int h = 0; h < 9; h++) if (!reserved[h]) { target = h; break; }
            }
            if (target < 0) return -1;
            c.interactionManager.clickSlot(p.playerScreenHandler.syncId, inv, target, SlotActionType.SWAP, p);
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
