package uz.autotravel;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Piyoda yo'l topish (A*): yurish, 1 blokka sakrash, tushish (maxFall gacha), suzish, blok sindirish (yo'lni
 * to'sgan bloklar), ko'prik qo'yish (oyoq ostida bo'shliq) va ustun qilib ko'tarilish (blok qo'yib). Nishon
 * yuklanmagan chunk'larda bo'lsa - unga eng yaqin yetib boriladigan joygacha (qism) yo'l qaytaradi.
 */
final class PathFinder {
    enum Kind { WALK, JUMP_UP, FALL, BRIDGE, PILLAR, DIG_DOWN, SWIM_UP, SWIM_DOWN }

    /** Bitta qadam: oyoq turadigan joy, turi, sindirilishi kerak bo'lgan bloklar, qo'yiladigan blok joyi. */
    record Step(BlockPos pos, Kind kind, List<BlockPos> breaks, BlockPos place) {}

    private static final class Node {
        final BlockPos pos;
        final Node parent;
        final Kind kind;
        final List<BlockPos> breaks;
        final BlockPos place;
        final double g, f;

        Node(BlockPos pos, Node parent, Kind kind, List<BlockPos> breaks, BlockPos place, double g, double h) {
            this.pos = pos;
            this.parent = parent;
            this.kind = kind;
            this.breaks = breaks;
            this.place = place;
            this.g = g;
            this.f = g + h;
        }
    }

    private static final double INF = Double.POSITIVE_INFINITY;
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private final ClientWorld w;
    private final boolean canBreak, canPlace;
    private final int maxFall;

    PathFinder(ClientWorld w, boolean canBreak, boolean canPlace, int maxFall) {
        this.w = w;
        this.canBreak = canBreak;
        this.canPlace = canPlace;
        this.maxFall = maxFall;
    }

    /** Yo'l (boshlanish nuqtasisiz) yoki null. goalHasY=false bo'lsa faqat x/z muhim. */
    List<Step> find(BlockPos start, BlockPos goal, boolean goalHasY, int maxNodes) {
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
        Map<Long, Double> best = new HashMap<>();
        Node first = new Node(start, null, Kind.WALK, List.of(), null, 0, h(start, goal, goalHasY));
        open.add(first);
        best.put(start.asLong(), 0.0);
        Node closest = first;
        double closestH = h(start, goal, goalHasY);
        int expanded = 0;
        while (!open.isEmpty() && expanded++ < maxNodes) {
            Node n = open.poll();
            Double known = best.get(n.pos.asLong());
            if (known != null && known < n.g) continue;
            double hn = h(n.pos, goal, goalHasY);
            if (reached(n.pos, goal, goalHasY)) return build(n);
            if (hn < closestH) {
                closestH = hn;
                closest = n;
            }
            for (Node m : neighbors(n, goal, goalHasY)) {
                Double old = best.get(m.pos.asLong());
                if (old != null && old <= m.g) continue;
                best.put(m.pos.asLong(), m.g);
                open.add(m);
            }
        }
        // to'liq yo'l topilmadi: nishonga eng yaqin joygacha (sezilarli yaqinlashsak)
        if (closest != first && h(start, goal, goalHasY) - closestH >= 2) return build(closest);
        return null;
    }

    private static boolean reached(BlockPos p, BlockPos goal, boolean goalHasY) {
        if (p.getX() != goal.getX() || p.getZ() != goal.getZ()) return false;
        return !goalHasY || p.getY() == goal.getY();
    }

    private static double h(BlockPos p, BlockPos goal, boolean goalHasY) {
        double dx = p.getX() - goal.getX(), dz = p.getZ() - goal.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (goalHasY) d += Math.abs(p.getY() - goal.getY()) * 1.5;
        return d * 1.05;
    }

    private static List<Step> build(Node n) {
        List<Step> out = new ArrayList<>();
        for (Node x = n; x.parent != null; x = x.parent) out.add(new Step(x.pos, x.kind, x.breaks, x.place));
        Collections.reverse(out);
        return out.isEmpty() ? null : out;
    }

    // ------------------------------------------------------------ qo'shnilar
    private List<Node> neighbors(Node n, BlockPos goal, boolean goalHasY) {
        List<Node> out = new ArrayList<>(10);
        BlockPos p = n.pos;
        boolean inWater = water(p);
        boolean grounded = ground(p.down());
        for (Direction d : HORIZONTAL) {
            BlockPos t = p.offset(d);
            if (!loaded(t)) continue;
            List<BlockPos> br = new ArrayList<>(3);
            double clear = clear(t, br) + clear(t.up(), br);
            if (clear < INF) {
                if (ground(t.down()) || water(t)) {
                    add(out, n, t, Kind.WALK, br, null, 1 + clear + (water(t) ? 1.5 : 0), goal, goalHasY);
                } else if (passable(t.down())) {
                    // tushish: pastda maxFall gacha qattiq yer (yoki suv)
                    BlockPos land = null;
                    for (int k = 1; k <= Math.max(maxFall, 20); k++) {
                        BlockPos f = t.down(k);
                        if (water(f)) {
                            land = f;
                            break;
                        }
                        if (!passable(f)) break;
                        if (ground(f.down())) {
                            if (k <= maxFall) land = f;
                            break;
                        }
                    }
                    if (land != null) {
                        add(out, n, land, Kind.FALL, br, null, 1 + clear + (p.getY() - land.getY()) * 0.3, goal, goalHasY);
                    } else if (canPlace && grounded && placeable(t.down())) {
                        add(out, n, t, Kind.BRIDGE, br, t.down(), 1 + clear + 4, goal, goalHasY);
                    }
                }
            }
            // bir blok tepaga sakrash
            if ((grounded || inWater) && ground(t) && !passable(t)) {
                List<BlockPos> bu = new ArrayList<>(3);
                double c2 = clear(p.up(2), bu) + clear(t.up(), bu) + clear(t.up(2), bu);
                if (c2 < INF) add(out, n, t.up(), Kind.JUMP_UP, bu, null, 2 + c2, goal, goalHasY);
            }
        }
        // blok qo'yib ustun bo'lib ko'tarilish
        if (canPlace && grounded && !inWater) {
            List<BlockPos> bu = new ArrayList<>(1);
            double c2 = clear(p.up(2), bu);
            if (c2 < INF) add(out, n, p.up(), Kind.PILLAR, bu, p, 5 + c2, goal, goalHasY);
        }
        // pastga qazish
        if (canBreak && grounded && ground(p.down(2))) {
            double b = breakCost(p.down());
            if (b < INF) add(out, n, p.down(), Kind.DIG_DOWN, List.of(p.down()), null, 2 + b, goal, goalHasY);
        }
        // suzish: tepaga / pastga
        if (inWater && (water(p.up()) || passable(p.up())) && loaded(p.up())) {
            add(out, n, p.up(), Kind.SWIM_UP, List.of(), null, 1.5, goal, goalHasY);
        }
        if (inWater && water(p.down())) add(out, n, p.down(), Kind.SWIM_DOWN, List.of(), null, 2, goal, goalHasY);
        return out;
    }

    private static void add(List<Node> out, Node n, BlockPos t, Kind kind, List<BlockPos> breaks, BlockPos place,
                            double cost, BlockPos goal, boolean goalHasY) {
        out.add(new Node(t, n, kind, breaks, place, n.g + cost, h(t, goal, goalHasY)));
    }

    /** O'tish narxi: bo'sh = 0, sindirsa bo'ladi = sindirish narxi (br'ga qo'shiladi), aks holda INF. */
    private double clear(BlockPos pos, List<BlockPos> br) {
        if (passable(pos)) return 0;
        double b = breakCost(pos);
        if (b < INF) br.add(pos);
        return b;
    }

    // ------------------------------------------------------------ bloklar
    boolean loaded(BlockPos pos) {
        return w.isChunkLoaded(pos) && pos.getY() > w.getBottomY() && pos.getY() < w.getTopYInclusive();
    }

    static boolean danger(BlockState s) {
        return s.isOf(Blocks.LAVA) || s.isOf(Blocks.FIRE) || s.isOf(Blocks.SOUL_FIRE) || s.isOf(Blocks.MAGMA_BLOCK)
                || s.isOf(Blocks.CACTUS) || s.isOf(Blocks.SWEET_BERRY_BUSH) || s.isOf(Blocks.COBWEB)
                || s.isOf(Blocks.POWDER_SNOW) || s.isOf(Blocks.WITHER_ROSE) || s.isOf(Blocks.CAMPFIRE)
                || s.isOf(Blocks.SOUL_CAMPFIRE);
    }

    boolean passable(BlockPos pos) {
        BlockState s = w.getBlockState(pos);
        if (danger(s) || w.getFluidState(pos).isIn(FluidTags.LAVA)) return false;
        return s.getCollisionShape(w, pos).isEmpty();
    }

    boolean water(BlockPos pos) {
        return w.getFluidState(pos).isIn(FluidTags.WATER);
    }

    /** Ustida tursa bo'ladigan blok (to'liq yoki yarim balandlikda, xavfsiz). */
    boolean ground(BlockPos pos) {
        BlockState s = w.getBlockState(pos);
        if (danger(s)) return false;
        VoxelShape shape = s.getCollisionShape(w, pos);
        if (shape.isEmpty()) return false;
        double top = shape.getMax(Direction.Axis.Y);
        return top >= 0.4 && top <= 1.0;
    }

    /** Bo'sh joy - blok qo'ysa bo'ladi (suyuqlik yoki havo). */
    boolean placeable(BlockPos pos) {
        BlockState s = w.getBlockState(pos);
        return s.isReplaceable() && !w.getFluidState(pos).isIn(FluidTags.LAVA);
    }

    /** Sindirish narxi yoki INF (bedrock, juda qattiq, yonida lava, ustida suyuqlik). */
    double breakCost(BlockPos pos) {
        if (!canBreak || !loaded(pos)) return INF;
        BlockState s = w.getBlockState(pos);
        float hard = s.getHardness(w, pos);
        if (hard < 0 || hard > 30 || danger(s)) return INF;
        for (Direction d : Direction.values()) {
            if (d == Direction.DOWN) continue;
            if (w.getFluidState(pos.offset(d)).isIn(FluidTags.LAVA)) return INF;   // lava oqib keladi
        }
        if (!w.getFluidState(pos.up()).isEmpty()) return INF;   // suv/lava bosib keladi
        return 2 + hard * 3;
    }
}
