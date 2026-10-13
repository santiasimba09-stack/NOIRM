package com.noirm.world.village;

import com.noirm.world.village.VillagePlan.Lot;
import com.noirm.world.village.VillagePlan.LotType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.List;

import static com.noirm.world.village.Buildings.HANGING_LANTERN;
import static com.noirm.world.village.Buildings.slab;
import static com.noirm.world.village.Buildings.stairs;
import static com.noirm.world.village.VillagePlan.STOREY;

/**
 * The buildings that make a village recognisable: the Kage tower, the Academy, the hospital, a
 * clan's walled district, the shrine, the training grounds and the hot spring. Each is drawn in a
 * {@link LotFrame} (front to the south) and scaled from its lot, so the same code draws a big
 * one in a capital and a smaller one in a town.
 */
final class Landmarks {
    private Landmarks() {}

    static void draw(LotFrame f) {
        switch (f.lot.type()) {
            case KAGE_TOWER -> kageTower(f, true);
            case ACADEMY -> academy(f);
            case HOSPITAL -> hospital(f);
            case CLAN_COMPOUND -> clanCompound(f);
            case SHRINE -> shrine(f);
            case TRAINING_GROUND -> trainingGround(f);
            case ONSEN -> onsen(f);
            default -> Buildings.house(f);
        }
    }

    // ================================================================ the Kage tower

    /**
     * The Kage's tower in the middle of the lot: a great drum of four floors (reception, archives,
     * the council room), the village symbol on a big plaque over the door, a wide balcony on top
     * and the Kage's office under a pointed roof (a dome in the desert).
     */
    static void kageTower(LotFrame f, boolean withKage) {
        int cu = f.w / 2, cv = f.d / 2;
        int r = VillageBuilder.TOWER_RADIUS;
        int h = VillageBuilder.TOWER_HEIGHT;
        int floor = VillageBuilder.TOWER_FLOOR;
        VillagePalette p = f.p;
        for (int du = -r - 3; du <= r + 3; du++) {
            for (int dv = -r - 3; dv <= r + 3; dv++) {
                int u = cu + du, v = cv + dv;
                boolean in = VillageBuilder.inDisk(du, dv, r);
                boolean ring = in && !VillageBuilder.inDisk(du, dv, r - 1);
                if (in) {
                    f.set(u, -1, v, Blocks.POLISHED_ANDESITE);
                }
                if (ring) {
                    f.fill(u, 0, v, u, h - 1, v, p.towerWall);
                    for (int band = floor; band < h; band += floor) {
                        f.set(u, band, v, p.towerRoof); // coloured bands at each floor
                    }
                } else if (in) {
                    f.fill(u, 0, v, u, h - 1, v, Blocks.AIR);
                    for (int y = floor; y < h; y += floor) {
                        f.set(u, y, v, p.planks);
                    }
                }
                if (VillageBuilder.inDisk(du, dv, r + 3)) {
                    f.set(u, h, v, p.planks); // the balcony floor
                }
                if (VillageBuilder.inDisk(du, dv, r + 3) && !VillageBuilder.inDisk(du, dv, r + 2)) {
                    f.set(u, h + 1, v, p.fence); // balcony railing
                }
            }
        }
        // Front door (three wide, three high) with steps, and windows on every floor.
        f.fill(cu - 1, 0, cv + r, cu + 1, 2, cv + r, Blocks.AIR);
        f.fill(cu - 1, 0, cv + r - 1, cu + 1, 2, cv + r - 1, Blocks.AIR);
        f.fill(cu - 2, -1, cv + r + 1, cu + 2, -1, cv + r + 2, Blocks.POLISHED_ANDESITE);
        f.set(cu - 2, 0, cv + r + 1, Blocks.LANTERN);
        f.set(cu + 2, 0, cv + r + 1, Blocks.LANTERN);
        for (int y = 3; y < h; y += floor) {
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8;
                int du = (int) Math.round(Math.sin(a) * r);
                int dv = (int) Math.round(Math.cos(a) * r);
                if (dv > r - 3 && Math.abs(du) < 8) {
                    continue; // the front carries the door and the symbol
                }
                f.set(cu + du, y, cv + dv, Blocks.GLASS_PANE);
                f.set(cu + du, y + 1, cv + dv, Blocks.GLASS_PANE);
            }
        }
        // Inside: a red carpet to the reception desk, bookshelves, archives, the council table.
        for (int v = cv - 2; v <= cv + r - 1; v++) {
            f.set(cu, 0, v, Blocks.RED_CARPET);
        }
        f.fill(cu - 3, 0, cv - 3, cu + 3, 0, cv - 3, slab(p.slab, SlabType.TOP));
        f.set(cu, 0, cv - 4, Blocks.LECTERN);
        for (int k = 0; k < 24; k++) {
            double a = k * Math.PI / 12;
            int du = (int) Math.round(Math.sin(a) * (r - 2));
            int dv = (int) Math.round(Math.cos(a) * (r - 2));
            if (dv > r - 6 && Math.abs(du) < 4) {
                continue;
            }
            for (int y = floor + 1; y < h; y += floor) {
                f.fill(cu + du, y, cv + dv, cu + du, y + 1, cv + dv, Blocks.BOOKSHELF);
            }
            if (k % 3 == 0) {
                f.set(cu + du, 0, cv + dv, Blocks.POTTED_FERN);
            }
        }
        int council = 2 * floor + 1;
        for (int du = -3; du <= 3; du++) {
            f.set(cu + du, council, cv, p.fence);
            f.set(cu + du, council + 1, cv, Blocks.DARK_OAK_PRESSURE_PLATE);
            f.set(cu + du, council, cv - 1, stairs(p.stairs, Direction.NORTH));
            f.set(cu + du, council, cv + 1, stairs(p.stairs, Direction.SOUTH));
        }
        // Ladder up the back wall through every floor, onto the balcony.
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
        for (int y = 0; y <= h; y++) {
            f.set(cu, y, cv - (r - 1), ladder);
        }
        for (int y = 0; y < h; y += floor) {
            f.set(cu, Math.min(y + floor, h) - 1, cv, HANGING_LANTERN);
            f.set(cu + 4, Math.min(y + floor, h) - 1, cv + 4, HANGING_LANTERN);
            f.set(cu - 4, Math.min(y + floor, h) - 1, cv - 4, HANGING_LANTERN);
        }
        // The village symbol, twice the size of a block-pixel, on a plaque over the door.
        int top = h - 3;
        for (int gx = -1; gx <= 10; gx++) {
            for (int gy = -1; gy <= 10; gy++) {
                boolean border = gx < 0 || gy < 0 || gx > 9 || gy > 9;
                boolean painted = !border && p.glyph.painted(gx / 2, gy / 2);
                f.set(cu - 5 + gx, top - gy, cv + r + 1, border ? p.towerRoof : painted ? p.paint : p.plaque);
            }
        }
        // The Kage's office on the balcony.
        int office = 7;
        for (int du = -office - 2; du <= office + 2; du++) {
            for (int dv = -office - 2; dv <= office + 2; dv++) {
                if (VillageBuilder.inDisk(du, dv, office) && !VillageBuilder.inDisk(du, dv, office - 1)) {
                    f.fill(cu + du, h + 1, cv + dv, cu + du, h + 6, cv + dv, p.towerWall);
                    if (((du + dv) & 1) == 0) {
                        f.set(cu + du, h + 3, cv + dv, Blocks.GLASS_PANE);
                        f.set(cu + du, h + 4, cv + dv, Blocks.GLASS_PANE);
                    }
                }
            }
        }
        if (p == VillagePalette.SAND) {
            Buildings.dome(f, cu, h + 6, cv, office + 1, p.towerRoof);
        } else {
            for (int k = 0; k <= office + 1; k++) {
                for (int du = -office - 2; du <= office + 2; du++) {
                    for (int dv = -office - 2; dv <= office + 2; dv++) {
                        if (VillageBuilder.inDisk(du, dv, office + 2 - k) && !VillageBuilder.inDisk(du, dv, office - k)) {
                            f.set(cu + du, h + 7 + k, cv + dv, p.towerRoof);
                        }
                    }
                }
            }
            f.set(cu, h + 7 + office + 3, cv, Blocks.LIGHTNING_ROD);
        }
        f.fill(cu - 1, h + 1, cv + office, cu + 1, h + 3, cv + office, Blocks.AIR); // office door, facing the gate
        f.set(cu, h + 1, cv - 3, Blocks.LECTERN);
        f.fill(cu - 2, h + 1, cv - 2, cu + 2, h + 1, cv - 2, slab(Blocks.DARK_OAK_SLAB, SlabType.TOP)); // the Kage's desk
        f.set(cu - 4, h + 1, cv - 3, Blocks.BOOKSHELF);
        f.set(cu + 4, h + 1, cv - 3, Blocks.BOOKSHELF);
        f.set(cu - 4, h + 2, cv - 3, Blocks.BOOKSHELF);
        f.set(cu + 4, h + 2, cv - 3, Blocks.BOOKSHELF);
        f.set(cu - 3, h + 1, cv + 3, Blocks.POTTED_BAMBOO);
        f.set(cu, h + 6, cv, HANGING_LANTERN);
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            f.set(cu + (int) Math.round(Math.sin(a) * (r + 2)), h + 1, cv + (int) Math.round(Math.cos(a) * (r + 2)), Blocks.LANTERN);
        }
    }

    // ================================================================ the Academy

    /**
     * The ninja Academy: a long two-storey school with a big tiled roof and classrooms (desks, a
     * blackboard, the teacher's lectern); in the yard, the swing tree, a sandpit and practice
     * posts and targets for the pupils.
     */
    static void academy(LotFrame f) {
        int u1 = 3, u2 = f.w - 4, v1 = 2, v2 = Math.min(f.d - 15, 17);
        Block wall = f.p.houseWalls.get(0);
        Buildings.shell(f, u1, v1, u2, v2, 2, wall);
        int mid = (u1 + u2) / 2;
        // A wide entrance with double doors and steps.
        Buildings.door(f, mid, 0, v2, Direction.SOUTH);
        Buildings.door(f, mid + 1, 0, v2, Direction.SOUTH);
        f.fill(mid - 2, 3, v2 + 1, mid + 3, 3, v2 + 2, slab(f.p.slab, SlabType.BOTTOM));
        f.fill(mid - 2, 0, v2 + 2, mid - 2, 2, v2 + 2, f.p.fence);
        f.fill(mid + 3, 0, v2 + 2, mid + 3, 2, v2 + 2, f.p.fence);
        for (int u = u1 + 2; u <= u2 - 2; u += 2) {
            if (Math.abs(u - mid) > 2) {
                f.set(u, 1, v2, Blocks.GLASS_PANE);
            }
        }
        // Classrooms on both floors, both sides of the middle hall.
        for (int k = 0; k < 2; k++) {
            int y = k * STOREY;
            f.fill(mid - 1, y, v1 + 1, mid - 1, y + 2, v2 - 1, wall);
            f.fill(mid + 2, y, v1 + 1, mid + 2, y + 2, v2 - 1, wall);
            f.fill(mid - 1, y, v2 - 2, mid - 1, y + 1, v2 - 2, Blocks.AIR);
            f.fill(mid + 2, y, v2 - 2, mid + 2, y + 1, v2 - 2, Blocks.AIR);
            for (int side = -1; side <= 1; side += 2) {
                int from = side < 0 ? u1 + 1 : mid + 3;
                int to = side < 0 ? mid - 2 : u2 - 1;
                f.fill(from, y + 1, v1, to, y + 2, v1, Blocks.BLACK_CONCRETE); // the blackboard
                f.set((from + to) / 2, y, v1 + 2, Blocks.LECTERN);
                for (int u = from + 1; u <= to - 1; u += 2) {
                    for (int v = v1 + 4; v <= v2 - 3; v += 2) {
                        f.set(u, y, v, slab(f.p.slab, SlabType.TOP));                // desk
                        f.set(u, y, v + 1, stairs(f.p.stairs, Direction.SOUTH)); // chair
                    }
                }
            }
        }
        Buildings.ladder(f, mid, v1 + 1, 0, 2 * STOREY - 1, 2);
        Buildings.pitchedRoof(f, u1, v1, u2, v2, 2 * STOREY - 1, wall);
        // The village symbol on the front.
        for (int gx = 0; gx < 5; gx++) {
            for (int gy = 0; gy < 5; gy++) {
                f.set(mid - 2 + gx, STOREY + 3 - gy + 1, v2 + 1, f.p.glyph.painted(gx, gy) ? f.p.paint : f.p.plaque);
            }
        }
        // The yard: the swing tree, a sandpit, practice posts and targets.
        int yard = v2 + 4;
        if (yard + 8 < f.d) {
            int tu = u1 + 4, tv = yard + 4;
            Buildings.tree(f, tu, tv, 7, 2);
            BlockState branch = f.p.log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
            f.fill(tu + 1, 6, tv, tu + 5, 6, tv, branch);
            BlockState chain = Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y);
            f.fill(tu + 3, 3, tv, tu + 3, 5, tv, chain);
            f.fill(tu + 5, 3, tv, tu + 5, 5, tv, chain);
            f.fill(tu + 3, 2, tv, tu + 5, 2, tv, slab(f.p.slab, SlabType.BOTTOM)); // the swing
            for (int u = u2 - 9; u <= u2 - 4; u++) {
                for (int v = yard + 1; v <= yard + 5; v++) {
                    boolean edge = u == u2 - 9 || u == u2 - 4 || v == yard + 1 || v == yard + 5;
                    f.set(u, -1, v, edge ? f.p.planks : Blocks.SAND);
                }
            }
            for (int u = mid - 4; u <= mid + 4; u += 4) {
                f.fill(u, 0, f.d - 4, u, 1, f.d - 4, f.p.log);
                f.set(u, 2, f.d - 4, Blocks.TARGET);
            }
            for (int u = mid - 6; u <= mid + 6; u += 3) {
                f.fill(u, 0, yard + 2, u, 1, yard + 2, f.p.log); // short posts for the little ones
            }
        }
    }

    // ================================================================ the hospital

    /** The hospital: a big white three-storey block with a red cross, wards of beds, medicine stands. */
    static void hospital(LotFrame f) {
        int u1 = 2, u2 = f.w - 3, v1 = 2, v2 = Math.min(f.d - 8, 26);
        int floors = Math.max(3, f.lot.floors());
        Block wall = f.p == VillagePalette.SAND ? Blocks.SMOOTH_SANDSTONE : Blocks.WHITE_CONCRETE;
        Buildings.shell(f, u1, v1, u2, v2, floors, wall);
        int mid = (u1 + u2) / 2;
        Buildings.door(f, mid, 0, v2, Direction.SOUTH);
        Buildings.door(f, mid + 1, 0, v2, Direction.SOUTH);
        f.fill(mid - 2, 3, v2 + 1, mid + 3, 3, v2 + 2, slab(f.p.slab, SlabType.BOTTOM));
        for (int u = u1 + 2; u <= u2 - 2; u += 2) {
            if (Math.abs(u - mid) > 3) {
                f.set(u, 1, v2, Blocks.GLASS_PANE);
            }
        }
        // A red cross on the front, high up.
        int cy = floors * STOREY - 5;
        f.fill(mid - 1, cy - 3, v2 + 1, mid + 2, cy + 3, v2 + 1, Blocks.RED_CONCRETE);
        f.fill(mid - 3, cy - 1, v2 + 1, mid + 4, cy + 1, v2 + 1, Blocks.RED_CONCRETE);
        // Reception downstairs, wards upstairs.
        f.fill(mid - 3, 0, v2 - 4, mid + 4, 0, v2 - 4, slab(Blocks.QUARTZ_SLAB, SlabType.TOP));
        f.set(mid - 4, 0, v1 + 1, Blocks.BREWING_STAND);
        f.set(mid - 3, 0, v1 + 1, Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
        f.set(mid - 2, 0, v1 + 1, Blocks.BREWING_STAND);
        for (int k = 1; k < floors; k++) {
            int y = k * STOREY;
            for (int u = u1 + 2; u <= u2 - 2; u += 3) {
                if (Math.abs(u - mid) <= 1) {
                    continue;
                }
                whiteBed(f, u, v1 + 2, y);
                whiteBed(f, u, v2 - 1, y, true);
            }
            f.set(mid, y, (v1 + v2) / 2, Blocks.BREWING_STAND);
        }
        Buildings.ladder(f, mid - 1, v1 + 1, 0, floors * STOREY - 1, floors);
        Buildings.flatRoof(f, u1, v1, u2, v2, floors * STOREY - 1);
        if (f.p != VillagePalette.SAND) {
            Buildings.waterTank(f, u1 + 3, floors * STOREY, v1 + 3);
            Buildings.waterTank(f, u2 - 3, floors * STOREY, v1 + 3);
        }
        // A little garden with benches in front.
        for (int u = u1; u <= u2; u += 6) {
            if (Math.abs(u - mid) > 3 && v2 + 4 < f.d) {
                Buildings.tree(f, u, v2 + 4, 4, u / 6);
            }
        }
    }

    private static void whiteBed(LotFrame f, int u, int v, int y) {
        whiteBed(f, u, v, y, false);
    }

    private static void whiteBed(LotFrame f, int u, int v, int y, boolean flipped) {
        BlockState foot = Blocks.WHITE_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, flipped ? Direction.SOUTH : Direction.NORTH)
                .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT);
        f.set(u, y, v, foot);
        f.set(u, y, flipped ? v + 1 : v - 1, foot.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
    }

    // ================================================================ a clan district

    /**
     * A clan's walled district: a white wall with a tiled cap, a gate under the clan crest, a
     * courtyard with a koi pond and a cherry tree, and traditional houses and a dojo all round.
     */
    static void clanCompound(LotFrame f) {
        int w = f.w, d = f.d;
        Block wall = f.p == VillagePalette.SAND ? Blocks.SMOOTH_SANDSTONE : Blocks.WHITE_TERRACOTTA;
        Block post = f.p == VillagePalette.SAND ? Blocks.ACACIA_LOG : Blocks.DARK_OAK_LOG;
        Block cap = f.p.roofSlabs().get(0);
        int gate = w / 2;
        for (int u = 0; u < w; u++) {
            for (int v = 0; v < d; v++) {
                boolean edge = u == 0 || u == w - 1 || v == 0 || v == d - 1;
                if (!edge) {
                    continue;
                }
                if (v == d - 1 && Math.abs(u - gate) <= 2) {
                    continue;
                }
                boolean isPost = (u % 6 == 0 && (v == 0 || v == d - 1)) || (v % 6 == 0 && (u == 0 || u == w - 1));
                f.fill(u, 0, v, u, 3, v, isPost ? post : wall);
                f.set(u, 4, v, slab(cap, SlabType.BOTTOM));
            }
        }
        // The gate: two posts, a little roof, and the crest.
        for (int du : new int[] {-3, 3}) {
            f.fill(gate + du, 0, d - 1, gate + du, 5, d - 1, post);
        }
        f.fill(gate - 4, 6, d - 2, gate + 4, 6, d, slab(cap, SlabType.BOTTOM));
        f.fill(gate - 3, 5, d - 1, gate + 3, 5, d - 1, post);
        crest(f, gate, 4, d, "uchiha".equals(clanOf(f)));
        f.set(gate - 4, 2, d, Blocks.LANTERN);
        f.set(gate + 4, 2, d, Blocks.LANTERN);
        // The courtyard: a koi pond with stepping stones, stone lanterns, a cherry tree.
        int pu = gate, pv = d / 2 + 1;
        for (int du = -4; du <= 4; du++) {
            for (int dv = -3; dv <= 3; dv++) {
                if (du * du * 9 + dv * dv * 16 <= 144 + 10) {
                    f.set(pu + du, -2, pv + dv, Blocks.CLAY);
                    f.set(pu + du, -1, pv + dv, Blocks.WATER);
                }
            }
        }
        f.set(pu - 2, 0, pv, Blocks.LILY_PAD);
        f.set(pu + 2, 0, pv + 1, Blocks.LILY_PAD);
        f.set(pu, -1, pv - 1, Blocks.STONE);
        Buildings.stoneLantern(f, pu - 6, pv - 4);
        Buildings.stoneLantern(f, pu + 6, pv - 4);
        Buildings.tree(f, pu + 6, pv + 4, 5, 0);
        f.fill(gate, -1, pv + 4, gate, -1, d - 1, Blocks.GRAVEL);
        // The houses and the dojo, facing the courtyard.
        int back = Math.max(9, d / 3);
        draw(f, f.sub(LotType.CLAN_HOUSE, 2, 2, gate - 2, back, Direction.SOUTH, 1, 0));
        draw(f, f.sub(LotType.CLAN_HOUSE, gate + 2, 2, w - 3, back, Direction.SOUTH, 1, 1));
        int side = Math.max(8, (w - 13) / 2);
        draw(f, f.sub(LotType.CLAN_HOUSE, 2, back + 3, side, d - 4, Direction.EAST, 1, 2));
        draw(f, f.sub(LotType.CLAN_HOUSE, w - 1 - side, back + 3, w - 3, d - 4, Direction.WEST, 1, 3));
    }

    private static void draw(LotFrame parent, Lot lot) {
        LotFrame frame = new LotFrame(parent.c, parent.p, lot);
        Buildings.draw(frame);
    }

    /** Which clan a compound belongs to (set on the plan; read back through the lot's style). */
    private static String clanOf(LotFrame f) {
        return f.lot.style() == 0 ? "uchiha" : "hyuga";
    }

    /**
     * The clan crest on a plaque above the gate, facing out: the Uchiha's fan (red over white with a
     * handle) or, for the Hyūga, a pale swirl on dark blue.
     */
    private static void crest(LotFrame f, int cu, int y, int v, boolean uchiha) {
        int[][] fan = {
                {0, 1, 1, 1, 0},
                {1, 1, 1, 1, 1},
                {2, 2, 2, 2, 2},
                {0, 2, 2, 2, 0},
                {0, 0, 3, 0, 0}};
        int[][] swirl = {
                {0, 1, 1, 1, 0},
                {1, 0, 0, 0, 1},
                {1, 0, 1, 0, 1},
                {1, 0, 1, 1, 0},
                {0, 1, 0, 0, 0}};
        int[][] grid = uchiha ? fan : swirl;
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                Block block = uchiha
                        ? switch (grid[r][c]) {
                            case 1 -> Blocks.RED_CONCRETE;
                            case 2 -> Blocks.WHITE_CONCRETE;
                            case 3 -> Blocks.BROWN_CONCRETE;
                            default -> Blocks.BLACK_CONCRETE;
                        }
                        : grid[r][c] == 1 ? Blocks.WHITE_CONCRETE : Blocks.BLUE_TERRACOTTA;
                f.set(cu - 2 + c, y + 6 - r, v, block);
            }
        }
    }

    // ================================================================ the shrine

    /**
     * A shrine in a grove: a stone path through red torii gates between stone lanterns, up the
     * steps of a raised hall with red pillars, an offering box and a bell, under trees.
     */
    static void shrine(LotFrame f) {
        int w = f.w, d = f.d;
        int mid = w / 2;
        int hallFront = Math.max(8, d / 3);
        int hu1 = Math.max(2, mid - Math.max(5, w / 4)), hu2 = Math.min(w - 3, mid + Math.max(5, w / 4));
        int hv1 = 3;
        // The approach.
        for (int v = hallFront + 1; v < d; v++) {
            f.fill(mid - 1, -1, v, mid + 1, -1, v, Blocks.STONE_BRICKS);
            if ((v - hallFront) % 4 == 2) {
                Buildings.stoneLantern(f, mid - 3, v);
                Buildings.stoneLantern(f, mid + 3, v);
            }
        }
        for (int v = d - 3; v > hallFront + 2; v -= 6) {
            torii(f, mid, v, 3);
        }
        // The raised hall.
        f.fill(hu1, -1, hv1, hu2, 0, hallFront, Blocks.STONE_BRICKS);
        f.fill(mid - 1, 0, hallFront, mid + 1, 0, hallFront, stairs(Blocks.STONE_BRICK_STAIRS, Direction.NORTH));
        f.fill(hu1 + 1, 1, hv1 + 1, hu2 - 1, 1, hallFront - 2, Blocks.DARK_OAK_PLANKS);
        for (int u = hu1 + 1; u <= hu2 - 1; u += 3) {
            f.fill(u, 1, hallFront - 1, u, 5, hallFront - 1, Blocks.RED_CONCRETE);
            f.fill(u, 1, hv1 + 1, u, 5, hv1 + 1, Blocks.RED_CONCRETE);
        }
        f.fill(hu1 + 1, 2, hv1 + 1, hu2 - 1, 5, hv1 + 1, Blocks.DARK_OAK_PLANKS);
        for (int v = hv1 + 1; v <= hallFront - 1; v++) {
            f.fill(hu1 + 1, 2, v, hu1 + 1, 5, v, Blocks.DARK_OAK_PLANKS);
            f.fill(hu2 - 1, 2, v, hu2 - 1, 5, v, Blocks.DARK_OAK_PLANKS);
        }
        f.set(mid, 2, hallFront - 2, Blocks.BARREL);                       // the offering box
        f.set(mid, 5, hallFront - 1, Blocks.BELL.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BellBlock.ATTACHMENT, net.minecraft.world.level.block.state.properties.BellAttachType.CEILING));
        f.set(mid, 2, hv1 + 2, Blocks.LECTERN);
        f.set(mid - 2, 4, (hv1 + hallFront) / 2, HANGING_LANTERN);
        f.set(mid + 2, 4, (hv1 + hallFront) / 2, HANGING_LANTERN);
        List<Block> roofs = f.p.roofStairs();
        Buildings.pitchedRoofOf(f, hu1 - 1, hv1 - 1, hu2 + 1, hallFront + 1, 6, Blocks.DARK_OAK_PLANKS,
                roofs.get(roofs.size() > 3 ? 3 : 0), f.p.roofSlabs().get(roofs.size() > 3 ? 3 : 0));
        // The grove.
        for (int i = 0; i < Math.max(4, w * d / 70); i++) {
            int u = f.rnd.nextInt(w);
            int v = f.rnd.nextInt(d);
            boolean clear = Math.abs(u - mid) < 5 || (u >= hu1 - 2 && u <= hu2 + 2 && v <= hallFront + 2);
            if (!clear && u > 1 && u < w - 2 && v > 1 && v < d - 2) {
                Buildings.tree(f, u, v, 5 + f.rnd.nextInt(3), 0);
            }
        }
    }

    /** A red shrine gate across a path. */
    static void torii(LotFrame f, int cu, int v, int half) {
        f.fill(cu - half, 0, v, cu - half, 5, v, Blocks.RED_CONCRETE);
        f.fill(cu + half, 0, v, cu + half, 5, v, Blocks.RED_CONCRETE);
        f.fill(cu - half - 1, 4, v, cu + half + 1, 4, v, Blocks.RED_CONCRETE);
        f.fill(cu - half - 2, 6, v, cu + half + 2, 6, v, Blocks.BLACK_CONCRETE);
        f.set(cu - half - 2, 7, v, slab(Blocks.BLACKSTONE_SLAB, SlabType.BOTTOM));
        f.set(cu + half + 2, 7, v, slab(Blocks.BLACKSTONE_SLAB, SlabType.BOTTOM));
        f.set(cu, 5, v, f.p.plaque);
    }

    // ================================================================ training grounds

    /**
     * A training ground in the woods: three wooden posts, targets, a log bench, the memorial stone,
     * trees for climbing practice, and (style 1) a lake with posts to practise water walking on.
     */
    static void trainingGround(LotFrame f) {
        int w = f.w, d = f.d;
        for (int u = 0; u < w; u++) {
            for (int v = 0; v < d; v++) {
                f.set(u, -1, v, f.rnd.nextInt(3) == 0 ? Blocks.GRASS_BLOCK : Blocks.COARSE_DIRT);
            }
        }
        int mid = w / 2;
        // The three posts (the bell test), near the front.
        for (int k = -1; k <= 1; k++) {
            f.fill(mid + k * 4, 0, d - 6, mid + k * 4, 2, d - 6, f.p.log);
        }
        // Targets.
        for (int u = 2; u < w - 2; u += 4) {
            f.fill(u, 0, 2, u, 1, 2, f.p.fence);
            f.set(u, 2, 2, Blocks.TARGET);
        }
        // A log bench and the memorial stone.
        BlockState bench = f.p.log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        f.fill(2, 0, d - 3, 5, 0, d - 3, bench);
        if (f.lot.style() != 2) {
            f.fill(w - 4, 0, d - 4, w - 4, 2, d - 4, Blocks.POLISHED_DEEPSLATE);
            f.set(w - 4, 3, d - 4, Blocks.CHISELED_DEEPSLATE);
            f.set(w - 5, 0, d - 4, Blocks.POTTED_WHITE_TULIP);
        }
        if (f.lot.style() == 1) {
            // The lake, with posts sticking out of the water.
            int lu = w / 2, lv = d / 3 + 2, rx = Math.max(4, w / 3), rz = Math.max(3, d / 5);
            for (int u = lu - rx; u <= lu + rx; u++) {
                for (int v = lv - rz; v <= lv + rz; v++) {
                    double e = Math.pow((u - lu) / (double) rx, 2) + Math.pow((v - lv) / (double) rz, 2);
                    if (e <= 1) {
                        f.set(u, -3, v, Blocks.CLAY);
                        f.set(u, -2, v, Blocks.WATER);
                        f.set(u, -1, v, Blocks.WATER);
                        f.set(u, 0, v, Blocks.AIR);
                    }
                }
            }
            for (int k = -2; k <= 2; k++) {
                f.fill(lu + k * 3, -2, lv, lu + k * 3, 0, lv, f.p.log);
            }
        }
        // Trees round the edges (the training grounds sit in the woods).
        for (int i = 0; i < (w + d) / 4; i++) {
            int u, v;
            if (f.rnd.nextBoolean()) {
                u = f.rnd.nextBoolean() ? 1 : w - 2;
                v = 4 + f.rnd.nextInt(Math.max(1, d - 8));
            } else {
                u = 4 + f.rnd.nextInt(Math.max(1, w - 8));
                v = 0;
            }
            Buildings.tree(f, u, v, 5 + f.rnd.nextInt(3), 2 + f.rnd.nextInt(2));
        }
    }

    // ================================================================ the hot spring

    /** A bathhouse: a wooden front building with curtains, and two steaming outdoor pools behind a bamboo screen. */
    static void onsen(LotFrame f) {
        int w = f.w, d = f.d;
        int front = d - 3;
        int hv1 = Math.max(front - 8, d / 2);
        Block wall = f.p.houseWalls.get(0);
        Buildings.shell(f, 2, hv1, w - 3, front, 1, wall);
        int mid = w / 2;
        Buildings.door(f, mid - 2, 0, front, Direction.SOUTH);
        Buildings.door(f, mid + 2, 0, front, Direction.SOUTH);
        Buildings.noren(f, mid - 3, mid - 1, front, Blocks.RED_WALL_BANNER);
        Buildings.noren(f, mid + 1, mid + 3, front, Blocks.BLUE_WALL_BANNER);
        Buildings.pitchedRoof(f, 2, hv1, w - 3, front, STOREY - 1, wall);
        f.fill(mid - 1, 0, hv1, mid + 1, 1, hv1, Blocks.AIR);
        // The pools.
        int pv1 = 2, pv2 = hv1 - 3;
        int split = mid;
        for (int u = 2; u <= w - 3; u++) {
            for (int v = pv1; v <= pv2; v++) {
                boolean rim = u == 2 || u == w - 3 || v == pv1 || v == pv2 || u == split;
                if (rim) {
                    f.set(u, -1, v, f.rnd.nextBoolean() ? Blocks.MOSSY_COBBLESTONE : Blocks.STONE_BRICKS);
                } else {
                    f.set(u, -3, v, Blocks.STONE_BRICKS);
                    f.set(u, -2, v, Blocks.WATER);
                    f.set(u, -1, v, Blocks.WATER);
                }
            }
        }
        // Bamboo screens round the pools and between them.
        for (int u = 1; u <= w - 2; u++) {
            f.fill(u, 0, 1, u, 2, 1, Blocks.BAMBOO_FENCE);
        }
        for (int v = 1; v < hv1; v++) {
            f.fill(1, 0, v, 1, 2, v, Blocks.BAMBOO_FENCE);
            f.fill(w - 2, 0, v, w - 2, 2, v, Blocks.BAMBOO_FENCE);
            f.fill(split, 0, v, split, 2, v, Blocks.BAMBOO_FENCE);
        }
        Buildings.stoneLantern(f, 3, pv2 + 1);
        Buildings.stoneLantern(f, w - 4, pv2 + 1);
        f.set(3, 0, pv1 + 1, Blocks.MOSSY_COBBLESTONE);
        f.set(w - 4, 0, pv1 + 1, Blocks.MOSSY_COBBLESTONE);
        // Steam: campfires under grates beside the pools.
        f.set(4, -1, pv1 + 1, Blocks.CAMPFIRE);
        f.set(4, 0, pv1 + 1, Blocks.IRON_BARS);
        f.set(w - 5, -1, pv1 + 1, Blocks.CAMPFIRE);
        f.set(w - 5, 0, pv1 + 1, Blocks.IRON_BARS);
    }
}
