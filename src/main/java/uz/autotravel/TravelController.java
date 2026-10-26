package uz.autotravel;

import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.EmptyBlockView;
import uz.spearlunger.InvHelper;
import uz.spearlunger.Rotations;

import java.util.List;

/**
 * Auto Travel (/goto x z yoki /goto x y z): koordinataga o'zi boradi.
 * <ul>
 *   <li>Uzoq bo'lsa va elytra + firework bo'lsa - elytra'ni kiyib, sakrab, ochib uchadi. Yer va oldindagi
 *   tog'/devordan {@code cruiseHeight} baland uchadi (heightmap + oldinga raycast), sekinlashsa firework.
 *   Elytra durability tugay desa yoki firework qolmasa - qo'nadi, elytra'ni yechib nagrudnik kiyadi va piyoda.</li>
 *   <li>Piyoda: A* yo'l - yurish, sakrash, tushish, suzish; yo'lni to'sgan blokni eng yaxshi asbob bilan
 *   sindiradi, bo'shliqqa ko'prik / tepaga ustun qilib blok qo'yadi.</li>
 *   <li>Oldinda katta suv bo'lsa va kema bo'lsa - kemani suvga qo'yib o'tiradi, qirg'oqqa yetgach tushib,
 *   kemani sindirib oladi.</li>
 * </ul>
 */
public class TravelController {
    private enum Mode { IDLE, WALK, ELYTRA, BOAT }
    private enum Fly { EQUIP, JUMP, DEPLOY, FLY }
    private enum BoatPhase { PLACE, RIDE, PICKUP }

    private Mode mode = Mode.IDLE;
    private BlockPos goal = null;
    private boolean goalHasY = false;
    private String status = "";

    // piyoda
    private List<PathFinder.Step> path = null;
    private BlockPos pathStart = null;
    private int idx = 0, stuck = 0, mineTicks = 0, replanWait = 0, fails = 0;
    private boolean noPlace = false;

    // elytra
    private Fly fly = Fly.EQUIP;
    private int flyTicks = 0, fwCooldown = 0;
    private boolean noElytra = false, landing = false;

    // kema
    private BoatPhase boatPhase = BoatPhase.PLACE;
    private AbstractBoatEntity boat = null;
    private int boatTicks = 0, boatBan = 0, slowTicks = 0, hits = 0;

    public boolean active() {
        return mode != Mode.IDLE;
    }

    public void start(MinecraftClient c, BlockPos goal, boolean hasY) {
        ClientPlayerEntity p = c.player;
        if (p == null) return;
        TravelConfig cfg = TravelConfig.get();
        if (!cfg.enabled) {
            cfg.enabled = true;
            TravelConfig.save();
        }
        release(c);
        this.goal = goal;
        this.goalHasY = hasY;
        noElytra = false;
        noPlace = false;
        boatBan = 0;
        fails = 0;
        toWalk();
        say(p, String.format("§aAuto Travel: %d %s%d ga yo'l oldik (%.0f blok)", goal.getX(),
                hasY ? goal.getY() + " " : "", goal.getZ(), horizontal(p)));
    }

    public void stop(MinecraftClient c, String msg) {
        release(c);
        if (c.interactionManager != null) c.interactionManager.cancelBlockBreaking();
        mode = Mode.IDLE;
        goal = null;
        path = null;
        boat = null;
        if (msg != null && c.player != null) say(c.player, msg);
    }

    private void toWalk() {
        mode = Mode.WALK;
        path = null;
        replanWait = 0;
    }

    // ------------------------------------------------------------ tick
    public void tick(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (p == null || c.world == null || c.interactionManager == null) {
            mode = Mode.IDLE;
            return;
        }
        TravelConfig cfg = TravelConfig.get();
        while (AutoTravelClient.keyToggle.wasPressed()) {
            cfg.enabled = !cfg.enabled;
            TravelConfig.save();
            if (!cfg.enabled && active()) stop(c, null);
            p.sendMessage(Text.literal(cfg.enabled ? "§aAuto Travel: ON" : "§cAuto Travel: OFF"), true);
        }
        if (mode == Mode.IDLE) return;
        if (!cfg.enabled || !p.isAlive()) {
            stop(c, "§eAuto Travel: to'xtadi");
            return;
        }
        if (InvHelper.screenOpen(c)) {
            release(c);
            return;
        }
        if (fwCooldown > 0) fwCooldown--;
        if (boatBan > 0) boatBan--;
        release(c);   // har tick: faqat kerakli tugmalar bosiladi
        double hd = horizontal(p);
        switch (mode) {
            case WALK -> walk(c, p, cfg, hd);
            case ELYTRA -> elytra(c, p, cfg, hd);
            case BOAT -> boat(c, p, cfg, hd);
            default -> { }
        }
    }

    // ------------------------------------------------------------ piyoda
    private void walk(MinecraftClient c, ClientPlayerEntity p, TravelConfig cfg, double hd) {
        if (hd <= 0.7 && (!goalHasY || Math.abs(p.getY() - goal.getY()) < 1)) {
            stop(c, "§aAuto Travel: yetib keldik!");
            return;
        }
        if (canFly(p, cfg, hd)) {
            mode = Mode.ELYTRA;
            fly = Fly.EQUIP;
            flyTicks = 0;
            landing = false;
            say(p, "§bAuto Travel: elytra bilan uchamiz");
            return;
        }
        ClientWorld w = c.world;
        BlockPos feet = p.getBlockPos();
        if (cfg.boat && boatBan == 0 && boatSlot(p) >= 0 && (p.isTouchingWater() || nextIsWater())
                && waterAhead(w, p) >= cfg.boatMinWater) {
            mode = Mode.BOAT;
            boatPhase = BoatPhase.PLACE;
            boatTicks = 0;
            boat = null;
            say(p, "§bAuto Travel: kemada suzamiz");
            return;
        }
        if (path == null || idx >= path.size()) {
            if (replanWait > 0) {
                replanWait--;
                status = "yo'l qidirilmoqda";
                return;
            }
            if (!p.isOnGround() && !p.isTouchingWater()) return;   // havoda - tushishini kutamiz
            boolean place = cfg.placeBlocks && !noPlace && blockSlot(p) >= 0;
            path = new PathFinder(w, cfg.breakBlocks, place, cfg.maxFall)
                    .find(feet, goal, goalHasY, Math.max(1000, cfg.searchNodes));
            pathStart = feet;
            idx = 0;
            stuck = 0;
            if (path == null) {
                status = "yo'l topilmadi";
                replanWait = 20;
                if (++fails > 15) stop(c, "§cAuto Travel: yo'l topilmadi");
                return;
            }
            fails = 0;
        }
        // qaysi qadamga yetdik (tushib/sakrab oldinga o'tgan bo'lishimiz mumkin)
        boolean settled = p.isOnGround() || p.isTouchingWater();
        if (settled) {
            for (int i = idx; i < Math.min(path.size(), idx + 8); i++) {
                if (path.get(i).pos().equals(feet)) {
                    idx = i + 1;
                    stuck = 0;
                    break;
                }
            }
        }
        if (idx >= path.size()) return;
        PathFinder.Step s = path.get(idx);
        BlockPos from = idx == 0 ? pathStart : path.get(idx - 1).pos();
        status = String.format("piyoda %.0fm", hd);

        // 1) to'sgan bloklarni sindirish
        for (BlockPos b : s.breaks()) {
            if (w.getBlockState(b).getCollisionShape(w, b).isEmpty()) continue;
            if (++mineTicks > 400) {   // sindirib bo'lmayapti - boshqa yo'l
                mineTicks = 0;
                path = null;
                return;
            }
            mine(c, p, b);
            status = "blok sindirilmoqda";
            return;
        }
        mineTicks = 0;
        if (++stuck > 120) {   // tiqilib qoldik - qaytadan
            path = null;
            fails++;
            return;
        }
        // 2) blok qo'yish (ko'prik / ustun)
        if (s.place() != null && w.getBlockState(s.place()).isReplaceable()) {
            if (s.kind() == PathFinder.Kind.PILLAR) {
                status = "ustun";
                if (p.isOnGround()) c.options.jumpKey.setPressed(true);
                else if (p.getY() >= s.place().getY() + 1.0) {
                    place(c, p, s.place().down(), Direction.UP);
                }
                return;
            }
            Direction d = Direction.getFacing(s.pos().getX() - from.getX(), 0, s.pos().getZ() - from.getZ());
            BlockPos against = s.place().offset(d.getOpposite());
            if (w.getBlockState(against).getCollisionShape(w, against).isEmpty()) {
                path = null;
                return;
            }
            c.options.sneakKey.setPressed(true);
            if (p.isOnGround()) place(c, p, against, d);
            status = "ko'prik";
            return;
        }
        // 3) yurish
        Vec3d target = Vec3d.ofBottomCenter(s.pos());
        Vec3d me = p.getEntityPos();
        double horiz = Math.sqrt(sq(target.x - me.x) + sq(target.z - me.z));
        Rotations.turn(p, Rotations.yawTo(me, target), 12.0f, 3.0f);
        GameOptions o = c.options;
        if (horiz > 0.2) o.forwardKey.setPressed(true);
        boolean water = p.isTouchingWater();
        if (s.kind() == PathFinder.Kind.JUMP_UP && p.isOnGround() && horiz < 1.5) o.jumpKey.setPressed(true);
        if (p.isOnGround() && p.horizontalCollision && s.pos().getY() >= feet.getY()) o.jumpKey.setPressed(true);
        if (water && s.kind() != PathFinder.Kind.SWIM_DOWN && s.pos().getY() >= feet.getY()) o.jumpKey.setPressed(true);
        if (s.kind() == PathFinder.Kind.SWIM_DOWN) o.sneakKey.setPressed(true);
        if (s.kind() == PathFinder.Kind.BRIDGE) o.sneakKey.setPressed(true);
        if (cfg.sprint && !water && horiz > 0.8 && p.getHungerManager().getFoodLevel() > 6
                && (s.kind() == PathFinder.Kind.WALK || s.kind() == PathFinder.Kind.FALL)) {
            o.sprintKey.setPressed(true);
        }
    }

    private boolean nextIsWater() {
        if (path == null || idx >= path.size()) return false;
        MinecraftClient c = MinecraftClient.getInstance();
        return c.world != null && c.world.getFluidState(path.get(idx).pos()).isIn(FluidTags.WATER);
    }

    /** Blokni eng yaxshi asbob bilan sindiradi (vanilla kabi: qaraydi va chap tugmani bosib turadi). */
    private static void mine(MinecraftClient c, ClientPlayerEntity p, BlockPos b) {
        BlockState st = c.world.getBlockState(b);
        int tool = -1;
        float best = 1.0f;
        for (int i = 0; i < 9; i++) {
            float sp = p.getInventory().getStack(i).getMiningSpeedMultiplier(st);
            if (sp > best) {
                best = sp;
                tool = i;
            }
        }
        if (tool >= 0) p.getInventory().setSelectedSlot(tool);
        Vec3d eye = p.getEyePos(), center = Vec3d.ofCenter(b);
        if (Rotations.turn(p, Rotations.yawTo(eye, center), Rotations.pitchTo(eye, center), 6.0f)) {
            c.options.attackKey.setPressed(true);
        }
    }

    /** against blokining face tomoniga blok qo'yadi. */
    private void place(MinecraftClient c, ClientPlayerEntity p, BlockPos against, Direction face) {
        int slot = blockSlot(p);
        if (slot < 0) {
            noPlace = true;
            path = null;
            return;
        }
        int hb = slot;
        if (slot >= 9) {
            hb = 8;
            for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isEmpty()) { hb = i; break; }
            InvHelper.swap(c, p, slot, hb);
        }
        p.getInventory().setSelectedSlot(hb);
        Vec3d hit = Vec3d.ofCenter(against).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
        Vec3d eye = p.getEyePos();
        Rotations.sendLook(p, Rotations.yawTo(eye, hit), Rotations.pitchTo(eye, hit));
        c.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(hit, face, against, false));
        p.swingHand(Hand.MAIN_HAND);
    }

    /** Qurilish uchun blok: avval arzon bloklar, keyin har qanday to'liq oddiy blok (qimmatbaholarisiz). */
    private static int blockSlot(ClientPlayerEntity p) {
        int any = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!(s.getItem() instanceof BlockItem bi)) continue;
            String id = Registries.ITEM.getId(s.getItem()).getPath();
            if (id.matches("cobblestone|cobbled_deepslate|dirt|netherrack|stone|andesite|diorite|granite|tuff"
                    + "|blackstone|end_stone|deepslate|.*_planks")) return i;
            if (any >= 0) continue;
            if (bi.getBlock() instanceof BlockWithEntity || bi.getBlock() instanceof FallingBlock) continue;
            if (id.matches(".*(diamond|emerald|netherite|gold|iron|obsidian|tnt|ancient|beacon|lapis|redstone"
                    + "|copper|amethyst|anchor|shulker|spawner).*")) continue;
            if (!bi.getBlock().getDefaultState().isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)) continue;
            any = i;
        }
        return any;
    }

    // ------------------------------------------------------------ elytra
    private boolean canFly(ClientPlayerEntity p, TravelConfig cfg, double hd) {
        return cfg.elytra && !noElytra && hd >= cfg.elytraMinDist && elytraSlot(p, cfg) != -2
                && fireworks(p) > 0 && p.isOnGround() && !p.isTouchingWater();
    }

    private void elytra(MinecraftClient c, ClientPlayerEntity p, TravelConfig cfg, double hd) {
        flyTicks++;
        switch (fly) {
            case EQUIP -> {
                int slot = elytraSlot(p, cfg);
                if (slot == -2) {
                    noElytra = true;
                    toWalk();
                    return;
                }
                if (slot >= 0) equip(c, p, slot);
                fly = Fly.JUMP;
                flyTicks = 0;
            }
            case JUMP -> {
                if (p.isTouchingWater() || flyTicks > 20) {
                    abortFly(p, "§eAuto Travel: uchib bo'lmadi, piyoda");
                    return;
                }
                if (!p.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) return;   // kiyilishini kutamiz
                Rotations.turn(p, Rotations.yawTo(p.getEntityPos(), goalCenter(p)), -20.0f, 3.0f);
                if (p.isOnGround()) {
                    c.options.jumpKey.setPressed(true);
                } else {
                    fly = Fly.DEPLOY;
                    flyTicks = 0;
                }
            }
            case DEPLOY -> {
                if (p.isGliding()) {
                    firework(c, p);
                    fwCooldown = cfg.fireworkDelay;
                    fly = Fly.FLY;
                    flyTicks = 0;
                    return;
                }
                if (p.isOnGround() && flyTicks > 3 || flyTicks > 30) {
                    abortFly(p, "§eAuto Travel: elytra ochilmadi (tepada joy yo'qmi?), piyoda");
                    return;
                }
                if (!p.isOnGround() && flyTicks >= 2 && p.checkGliding()) {
                    p.networkHandler.sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                }
            }
            case FLY -> flyTick(c, p, cfg, hd);
        }
    }

    private void abortFly(ClientPlayerEntity p, String msg) {
        noElytra = true;
        say(p, msg);
        toWalk();
    }

    private void flyTick(MinecraftClient c, ClientPlayerEntity p, TravelConfig cfg, double hd) {
        ClientWorld w = c.world;
        if (!p.isGliding()) {
            if (p.isOnGround() || p.isTouchingWater()) {
                landed(c, p, cfg);
                return;
            }
            // havoda yopilib qoldi - qayta ochamiz
            if (!landing && p.getVelocity().y < 0 && p.checkGliding()) {
                p.networkHandler.sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
            return;
        }
        ItemStack chest = p.getEquippedStack(EquipmentSlot.CHEST);
        int dur = chest.getMaxDamage() - chest.getDamage();
        Vec3d pos = p.getEntityPos(), v = p.getVelocity();
        double hs = Math.sqrt(v.x * v.x + v.z * v.z);
        int ground = groundY(w, pos.x, pos.z);
        double alt = pos.y - ground;
        if (!landing) {
            String why = null;
            if (hd < Math.max(40, alt * 1.6)) why = "yetib keldik, qo'nyapmiz";
            else if (dur <= cfg.minDurability) why = "elytra tugayapti, qo'nyapmiz";
            else if (fireworks(p) == 0) why = "firework tugadi, qo'nyapmiz";
            if (why != null) {
                landing = true;
                if (hd >= Math.max(40, alt * 1.6)) noElytra = true;
                say(p, "§eAuto Travel: " + why);
            }
        }
        Vec3d dir = new Vec3d(goal.getX() + 0.5 - pos.x, 0, goal.getZ() + 0.5 - pos.z);
        dir = dir.lengthSquared() < 1e-4 ? new Vec3d(1, 0, 0) : dir.normalize();
        float yaw = Rotations.yawTo(pos, goalCenter(p));
        float pitch;
        if (landing) {
            pitch = alt > 25 ? 40.0f : alt > 8 ? 20.0f : 6.0f;
            // qo'nishda ham devorga urilmaslik
            if (wallAhead(w, p, dir, Math.max(6, hs * 10))) pitch = -15.0f;
            status = String.format("qo'nish %.0fm", alt);
        } else {
            double desired = terrainAhead(w, pos, dir, Math.min(96, hd)) + cfg.cruiseHeight;
            double dy = desired - pos.y;
            pitch = (float) MathHelper.clamp(-dy * 1.5, -40, 25);
            boolean boost = hs < cfg.fireworkSpeedTenths / 10.0 || (dy > 8 && v.y < 0.3);
            if (wallAhead(w, p, dir, Math.max(12, hs * 25))) {   // oldinda devor/tog' - keskin tepaga
                pitch = -55.0f;
                boost = true;
            }
            if (boost && fwCooldown == 0 && firework(c, p)) fwCooldown = Math.max(5, cfg.fireworkDelay);
            status = String.format("elytra %.0fm, %.0f blok baland, dur %d", hd, alt, dur);
        }
        Rotations.turn(p, yaw, pitch, 2.0f);
    }

    /** Qo'ndik: elytra'ni yechib nagrudnikni kiyamiz va piyoda davom etamiz. */
    private void landed(MinecraftClient c, ClientPlayerEntity p, TravelConfig cfg) {
        if (cfg.restoreChest && p.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
            int plate = chestplateSlot(p);
            if (plate >= 0) equip(c, p, plate);
        }
        say(p, "§aAuto Travel: qo'ndik, piyoda davom etamiz");
        landing = false;
        toWalk();
    }

    /** Ko'krak kiyimini (slotdagi narsani) kiyadi - o'ng tugma bilan almashtirish; inventardan bo'lsa E orqali. */
    private static void equip(MinecraftClient c, ClientPlayerEntity p, int slot) {
        int original = p.getInventory().getSelectedSlot();
        int hb = slot < 9 ? slot : original;
        if (slot >= 9) InvHelper.swap(c, p, slot, hb);
        p.getInventory().setSelectedSlot(hb);
        c.interactionManager.interactItem(p, Hand.MAIN_HAND);
        p.swingHand(Hand.MAIN_HAND);
        if (slot >= 9) InvHelper.swap(c, p, slot, hb);
        p.getInventory().setSelectedSlot(original);
    }

    /** -1 = yaroqli elytra kiyilgan, 0..35 = inventardagi slot, -2 = yo'q. */
    private static int elytraSlot(ClientPlayerEntity p, TravelConfig cfg) {
        ItemStack chest = p.getEquippedStack(EquipmentSlot.CHEST);
        if (chest.isOf(Items.ELYTRA) && chest.getMaxDamage() - chest.getDamage() > cfg.minDurability) return -1;
        int best = -2, bestDur = cfg.minDurability;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isOf(Items.ELYTRA)) continue;
            int dur = s.getMaxDamage() - s.getDamage();
            if (dur > bestDur) {
                bestDur = dur;
                best = i;
            }
        }
        return best;
    }

    private static int chestplateSlot(ClientPlayerEntity p) {
        int best = -1, bestRank = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            String id = Registries.ITEM.getId(s.getItem()).getPath();
            if (!id.endsWith("_chestplate")) continue;
            int rank = id.startsWith("netherite") ? 6 : id.startsWith("diamond") ? 5 : id.startsWith("iron") ? 4
                    : id.startsWith("chainmail") ? 3 : id.startsWith("golden") ? 2 : 1;
            if (rank > bestRank) {
                bestRank = rank;
                best = i;
            }
        }
        return best;
    }

    private static int fireworks(ClientPlayerEntity p) {
        int n = p.getOffHandStack().isOf(Items.FIREWORK_ROCKET) ? p.getOffHandStack().getCount() : 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(Items.FIREWORK_ROCKET)) n += s.getCount();
        }
        return n;
    }

    /** Firework ishlatadi (chap qo'l, hotbar yoki inventardan) va qo'lni joyiga qaytaradi. */
    private static boolean firework(MinecraftClient c, ClientPlayerEntity p) {
        if (p.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
            c.interactionManager.interactItem(p, Hand.OFF_HAND);
            return true;
        }
        int original = p.getInventory().getSelectedSlot();
        int slot = -1;
        for (int i = 0; i < 36; i++) {
            if (p.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) return false;
        int hb = slot < 9 ? slot : original;
        if (slot >= 9) InvHelper.swap(c, p, slot, hb);
        p.getInventory().setSelectedSlot(hb);
        c.interactionManager.interactItem(p, Hand.MAIN_HAND);
        p.swingHand(Hand.MAIN_HAND);
        if (slot >= 9) InvHelper.swap(c, p, slot, hb);
        p.getInventory().setSelectedSlot(original);
        return true;
    }

    /** Oldindagi yo'lda (dist blokgacha) eng baland yer/devor (yuklanmagan chunk'lar hisobga olinmaydi). */
    private static double terrainAhead(ClientWorld w, Vec3d pos, Vec3d dir, double dist) {
        double max = groundY(w, pos.x, pos.z);
        for (double i = 4; i <= dist; i += 4) {
            double x = pos.x + dir.x * i, z = pos.z + dir.z * i;
            if (!w.isChunkLoaded(BlockPos.ofFloored(x, pos.y, z))) break;
            max = Math.max(max, groundY(w, x, z));
        }
        return max;
    }

    private static int groundY(ClientWorld w, double x, double z) {
        return w.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));
    }

    /** Uchish yo'nalishida (tana balandligida) devor bormi. */
    private static boolean wallAhead(ClientWorld w, ClientPlayerEntity p, Vec3d dir, double dist) {
        Vec3d v = p.getVelocity();
        Vec3d look = v.lengthSquared() > 0.04 ? v.normalize() : dir;
        for (double off : new double[] {0.2, 1.0}) {
            Vec3d from = p.getEntityPos().add(0, off, 0);
            HitResult r = w.raycast(new RaycastContext(from, from.add(look.multiply(dist)),
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
            if (r.getType() == HitResult.Type.BLOCK) return true;
        }
        return false;
    }

    // ------------------------------------------------------------ kema
    private void boat(MinecraftClient c, ClientPlayerEntity p, TravelConfig cfg, double hd) {
        boatTicks++;
        ClientWorld w = c.world;
        if (p.getVehicle() instanceof AbstractBoatEntity b && boatPhase != BoatPhase.PICKUP) {
            boatPhase = BoatPhase.RIDE;
            boat = b;
        }
        switch (boatPhase) {
            case PLACE -> {
                status = "kema qo'yilmoqda";
                if (boatTicks > 80) {
                    boatBan = 400;
                    say(p, "§eAuto Travel: kemaga o'tirib bo'lmadi, suzib boramiz");
                    toWalk();
                    return;
                }
                if (boatTicks % 5 != 1) return;
                // yaqinda bo'sh kema bo'lsa - o'tiramiz
                for (AbstractBoatEntity e : w.getEntitiesByClass(AbstractBoatEntity.class, p.getBoundingBox().expand(4),
                        x -> !x.hasPassengers())) {
                    c.interactionManager.interactEntity(p, e, Hand.MAIN_HAND);
                    p.swingHand(Hand.MAIN_HAND);
                    return;
                }
                int slot = boatSlot(p);
                if (slot < 0) {
                    boatBan = 400;
                    toWalk();
                    return;
                }
                Vec3d spot = waterSpot(w, p);
                if (spot == null) return;
                int original = p.getInventory().getSelectedSlot();
                int hb = slot < 9 ? slot : original;
                if (slot >= 9) InvHelper.swap(c, p, slot, hb);
                p.getInventory().setSelectedSlot(hb);
                Vec3d eye = p.getEyePos();
                Rotations.sendLook(p, Rotations.yawTo(eye, spot), Rotations.pitchTo(eye, spot));
                c.interactionManager.interactItem(p, Hand.MAIN_HAND);
                p.swingHand(Hand.MAIN_HAND);
                if (slot >= 9) InvHelper.swap(c, p, slot, hb);
                p.getInventory().setSelectedSlot(original);
            }
            case RIDE -> {
                AbstractBoatEntity b = boat;
                if (b == null || !p.hasVehicle()) {
                    boatPhase = BoatPhase.PICKUP;
                    hits = 0;
                    boatTicks = 0;
                    return;
                }
                Vec3d bp = b.getEntityPos();
                float want = Rotations.yawTo(bp, goalCenter(p));
                float diff = MathHelper.wrapDegrees(want - b.getYaw());
                GameOptions o = c.options;
                if (diff > 5) o.rightKey.setPressed(true);
                else if (diff < -5) o.leftKey.setPressed(true);
                if (Math.abs(diff) < 60) o.forwardKey.setPressed(true);
                p.setYaw(want);
                Vec3d bv = b.getVelocity();
                double speed = Math.sqrt(bv.x * bv.x + bv.z * bv.z);
                slowTicks = (Math.abs(diff) < 60 && speed < 0.05) ? slowTicks + 1 : 0;
                // oldinda qirg'oq, nishonga yetdik yoki tiqildik - tushamiz
                float by = b.getYaw() * MathHelper.RADIANS_PER_DEGREE;
                Vec3d ahead = bp.add(-MathHelper.sin(by) * 2.2, 0, MathHelper.cos(by) * 2.2);
                BlockPos a = BlockPos.ofFloored(ahead.x, bp.y + 0.3, ahead.z);
                boolean shore = !w.getFluidState(a).isIn(FluidTags.WATER) && !w.getFluidState(a.down()).isIn(FluidTags.WATER);
                status = String.format("kema %.0fm", hd);
                if (hd < 3 || (shore && Math.abs(diff) < 60) || slowTicks > 40) {
                    if (slowTicks > 40) boatBan = 200;
                    boatPhase = BoatPhase.PICKUP;
                    hits = 0;
                    boatTicks = 0;
                }
            }
            case PICKUP -> {
                status = "kemadan tushish";
                if (p.hasVehicle()) {
                    c.options.sneakKey.setPressed(true);
                    if (boatTicks > 20) toWalk();
                    return;
                }
                if (boatBan == 0) boatBan = 60;
                AbstractBoatEntity b = boat;
                if (!cfg.pickupBoat || b == null || b.isRemoved() || hits >= 12 || p.distanceTo(b) > 4.5) {
                    toWalk();
                    return;
                }
                Vec3d eye = p.getEyePos(), center = b.getBoundingBox().getCenter();
                Rotations.turn(p, Rotations.yawTo(eye, center), Rotations.pitchTo(eye, center), 10.0f);
                if (boatTicks % 4 == 0) {
                    c.interactionManager.attackEntity(p, b);
                    p.swingHand(Hand.MAIN_HAND);
                    hits++;
                }
            }
        }
    }

    private static int boatSlot(ClientPlayerEntity p) {
        for (int i = 0; i < 36; i++) {
            String id = Registries.ITEM.getId(p.getInventory().getStack(i).getItem()).getPath();
            if (id.endsWith("_boat") || id.endsWith("_raft")) return i;
        }
        return -1;
    }

    /** Nishon tomonda ketma-ket necha blok suv bor (boshida 2 blokgacha quruqlik bo'lishi mumkin). */
    private int waterAhead(ClientWorld w, ClientPlayerEntity p) {
        Vec3d pos = p.getEntityPos();
        Vec3d dir = new Vec3d(goal.getX() + 0.5 - pos.x, 0, goal.getZ() + 0.5 - pos.z);
        if (dir.lengthSquared() < 1e-4) return 0;
        dir = dir.normalize();
        int n = 0;
        for (int i = 1; i <= 64; i++) {
            double x = pos.x + dir.x * i, z = pos.z + dir.z * i;
            if (!w.isChunkLoaded(BlockPos.ofFloored(x, pos.y, z))) break;
            BlockPos top = BlockPos.ofFloored(x, groundY(w, x, z) - 1, z);
            if (w.getFluidState(top).isIn(FluidTags.WATER)) n++;
            else if (i > 2 || n > 0) break;
        }
        return n;
    }

    /** Kema qo'yiladigan suv yuzasi: nishon tomonda 1-3 blok oldinda. */
    private Vec3d waterSpot(ClientWorld w, ClientPlayerEntity p) {
        Vec3d pos = p.getEntityPos();
        Vec3d dir = new Vec3d(goal.getX() + 0.5 - pos.x, 0, goal.getZ() + 0.5 - pos.z).normalize();
        for (double i = 1.5; i <= 3.5; i += 0.5) {
            double x = pos.x + dir.x * i, z = pos.z + dir.z * i;
            int top = groundY(w, x, z);
            BlockPos b = BlockPos.ofFloored(x, top - 1, z);
            if (w.getFluidState(b).isIn(FluidTags.WATER)) return new Vec3d(x, top - 0.1, z);
        }
        return null;
    }

    // ------------------------------------------------------------ yordamchi
    private Vec3d goalCenter(ClientPlayerEntity p) {
        return new Vec3d(goal.getX() + 0.5, goalHasY ? goal.getY() : p.getY(), goal.getZ() + 0.5);
    }

    private double horizontal(ClientPlayerEntity p) {
        if (goal == null) return 0;
        return Math.sqrt(sq(goal.getX() + 0.5 - p.getX()) + sq(goal.getZ() + 0.5 - p.getZ()));
    }

    private static double sq(double v) {
        return v * v;
    }

    private static void release(MinecraftClient c) {
        GameOptions o = c.options;
        o.forwardKey.setPressed(false);
        o.backKey.setPressed(false);
        o.leftKey.setPressed(false);
        o.rightKey.setPressed(false);
        o.jumpKey.setPressed(false);
        o.sneakKey.setPressed(false);
        o.sprintKey.setPressed(false);
        o.attackKey.setPressed(false);
    }

    private static void say(ClientPlayerEntity p, String msg) {
        if (TravelConfig.get().messages) p.sendMessage(Text.literal(msg), false);
    }

    public String hudLine() {
        if (mode == Mode.IDLE || goal == null || !TravelConfig.get().hud) return null;
        return "§bTravel: §f" + status + " §7→ " + goal.getX() + " " + (goalHasY ? goal.getY() + " " : "") + goal.getZ();
    }
}
