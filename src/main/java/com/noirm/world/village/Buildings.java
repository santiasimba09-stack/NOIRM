package com.noirm.world.village;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.List;

import static com.noirm.world.village.VillagePlan.STOREY;

/**
 * The village's ordinary buildings, each drawn into a {@link LotFrame} with its front to the
 * south (+v). Every building has an inside: kitchens, beds, tables, ladders between floors, shop
 * counters and shelves, so walking into one feels like walking into someone's home.
 *
 * <p>Storeys are {@link VillagePlan#STOREY} blocks: walls from {@code 4k} to {@code 4k + 2}, the
 * next floor (or the roof deck) at {@code 4k + 3}. A flat roof's deck is at {@code 4n - 1}, so a
 * rooftop runner stands at {@code 4n} ({@link VillagePlan.Lot#roofTop()}).
 */
final class Buildings {
    static final BlockState HANGING_LANTERN = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
    private static final List<Block> FLOWERS = List.of(Blocks.POTTED_POPPY, Blocks.POTTED_DANDELION, Blocks.POTTED_AZURE_BLUET,
            Blocks.POTTED_RED_TULIP, Blocks.POTTED_OXEYE_DAISY, Blocks.POTTED_FERN, Blocks.POTTED_CORNFLOWER, Blocks.POTTED_ORANGE_TULIP);
    private static final List<Block> BEDS = List.of(Blocks.WHITE_BED, Blocks.BLUE_BED, Blocks.RED_BED, Blocks.LIGHT_GRAY_BED,
            Blocks.CYAN_BED, Blocks.ORANGE_BED, Blocks.GREEN_BED);
    private static final List<Block> CARPETS = List.of(Blocks.RED_CARPET, Blocks.BLUE_CARPET, Blocks.GREEN_CARPET, Blocks.BROWN_CARPET,
            Blocks.LIGHT_GRAY_CARPET, Blocks.ORANGE_CARPET);

    private Buildings() {}

    static void draw(LotFrame f) {
        switch (f.lot.type()) {
            case HOUSE -> house(f);
            case ROUND_HOUSE -> roundHouse(f);
            case APARTMENT -> apartment(f);
            case ROUND_TOWER -> roundTower(f);
            case SHOP -> shop(f);
            case RESTAURANT -> restaurant(f);
            case STALL -> stall(f, 1, f.d - 4);
            case RAMEN_SHOP -> ramenShop(f);
            case WAREHOUSE -> warehouse(f);
            case GARDEN -> garden(f);
            case PARK -> park(f);
            case INN -> inn(f);
            case CHIEF_HOUSE -> traditionalHouse(f, 1, 1, f.w - 2, f.d - 6, true);
            case CLAN_HOUSE -> traditionalHouse(f, 1, 1, f.w - 2, f.d - 3, false);
            case GUARD_POST -> guardPost(f);
            case WATCHTOWER -> watchtower(f, f.w / 2, f.d / 2);
            default -> Landmarks.draw(f);
        }
    }

    // ================================================================ homes

    /**
     * A square house of 1–3 storeys: plastered walls on a timber frame, windows on every side, a
     * front door with a lantern and a step, a kitchen and table downstairs, bedrooms upstairs, a
     * ladder between floors. The roof is tiled (pitched) or flat with a rooftop water tank, a
     * chimney or a little roof garden.
     */
    static void house(LotFrame f) {
        int u1 = 1, u2 = f.w - 2, v1 = 1, v2 = f.d - 3;
        if (v2 - v1 < 4) {
            v2 = f.d - 2;
        }
        int floors = Math.max(1, f.lot.floors());
        Block wall = f.pick(f.p.houseWalls);
        int roofKind = f.lot.style() % 3;
        boolean chimney = f.rnd.nextInt(3) == 0;
        shell(f, u1, v1, u2, v2, floors, wall);
        int mid = (u1 + u2) / 2;
        frontDoor(f, mid, v2, f.d - 1);
        // Inside: the kitchen and table downstairs; beds upstairs (or in the corner of a cottage).
        kitchen(f, u1 + 1, v1 + 1, u2 - 2, 0);
        table(f, u1 + 2, v2 - 2, 0);
        if (floors == 1) {
            bed(f, u2 - 1, v2 - 2, 0);
        }
        for (int k = 1; k < floors; k++) {
            int y = k * STOREY;
            bed(f, u1 + 1, v1 + 2, y);
            if (u2 - u1 >= 6) {
                bed(f, u1 + 3, v1 + 2, y);
            }
            f.set(u2 - 2, y, v2 - 1, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.NORTH));
            f.set(u1 + 1, y, v2 - 1, Blocks.BOOKSHELF);
            f.set(u1 + 2, y, v2 - 1, f.pick(FLOWERS));
            rug(f, u1 + 2, v1 + 4, u2 - 2, v2 - 2, y);
        }
        ladder(f, u2 - 1, v1 + 1, 0, floors * STOREY - 1, floors);
        int top = floors * STOREY;
        switch (roofKind) {
            case 0 -> pitchedRoof(f, u1, v1, u2, v2, top - 1, wall);
            case 1 -> {
                flatRoof(f, u1, v1, u2, v2, top - 1);
                if (f.p != VillagePalette.SAND) {
                    waterTank(f, u2 - 2, top, v1 + 2);
                }
            }
            default -> {
                flatRoof(f, u1, v1, u2, v2, top - 1);
                f.set(u1 + 1, top, v1 + 1, f.pick(FLOWERS));
                f.set(u1 + 2, top, v1 + 1, f.pick(FLOWERS));
                f.set(u1 + 1, top, v1 + 2, Blocks.COMPOSTER);
            }
        }
        if (chimney) {
            chimney(f, u1, v1, roofKind == 0 ? top + (v2 - v1) / 2 + 2 : top + 1);
        }
        // A balcony over the front door on taller houses.
        if (floors >= 2 && f.lot.style() % 2 == 0 && v2 + 1 < f.d) {
            int y = STOREY;
            f.fill(mid - 1, y - 1, v2 + 1, mid + 1, y - 1, v2 + 1, slab(f.p.slab, SlabType.TOP));
            f.fill(mid - 1, y, v2 + 1, mid + 1, y, v2 + 1, f.p.fence);
            f.set(mid, y, v2, Blocks.AIR);
            f.set(mid, y + 1, v2, Blocks.AIR);
        }
        frontYard(f, u1, u2, v2);
    }

    /** A round house: a plastered drum, windows all round, a flat overhanging roof (domed in the desert). */
    static void roundHouse(LotFrame f) {
        int r = Math.max(3, Math.min(f.w, f.d - 1) / 2 - 1);
        int cu = (f.w - 1) / 2;
        int cv = (f.d - 2) / 2;
        int floors = Math.max(1, f.lot.floors());
        int h = floors * STOREY;
        Block wall = f.pick(f.p.houseWalls);
        Block roof = f.pick(f.p.roofs);
        for (int du = -r - 1; du <= r + 1; du++) {
            for (int dv = -r - 1; dv <= r + 1; dv++) {
                int u = cu + du;
                int v = cv + dv;
                boolean in = VillageBuilder.inDisk(du, dv, r);
                boolean ring = in && !VillageBuilder.inDisk(du, dv, r - 1);
                if (in) {
                    f.set(u, -1, v, f.p.planks);
                    if (ring) {
                        f.fill(u, 0, v, u, h - 1, v, wall);
                        for (int k = 1; k < floors; k++) {
                            f.set(u, k * STOREY - 1, v, roof); // a coloured band at each floor
                        }
                    } else {
                        f.fill(u, 0, v, u, h - 2, v, Blocks.AIR);
                        for (int k = 1; k < floors; k++) {
                            f.set(u, k * STOREY - 1, v, f.p.planks);
                        }
                    }
                }
            }
        }
        // Windows on every floor, all round.
        for (int k = 0; k < floors; k++) {
            int y = k * STOREY + 1;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (side == Direction.SOUTH && k == 0) {
                    continue;
                }
                f.set(cu + side.getStepX() * r, y, cv + side.getStepZ() * r, Blocks.GLASS_PANE);
            }
            int diag = Math.max(1, (int) Math.round(r * 0.7));
            for (int[] s : new int[][] {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
                f.set(cu + s[0] * diag, y, cv + s[1] * diag, Blocks.GLASS_PANE);
            }
        }
        frontDoor(f, cu, cv + r, f.d - 1);
        // Inside.
        bed(f, cu - r + 2, cv, 0);
        f.set(cu + r - 2, 0, cv - 1, Blocks.FURNACE);
        f.set(cu + r - 2, 0, cv, Blocks.BARREL);
        f.set(cu, 0, cv - r + 1, Blocks.CRAFTING_TABLE);
        f.set(cu, h - 2, cv, HANGING_LANTERN);
        if (floors > 1) {
            ladder(f, cu + 1, cv - r + 1, 0, h - 1, floors);
            bed(f, cu - 1, cv + 1, STOREY);
        }
        // The roof.
        if (f.p == VillagePalette.SAND) {
            dome(f, cu, h - 1, cv, r, roof);
        } else if (f.p == VillagePalette.SNOW) {
            cone(f, cu, h - 1, cv, r + 1, roof);
        } else {
            for (int du = -r - 1; du <= r + 1; du++) {
                for (int dv = -r - 1; dv <= r + 1; dv++) {
                    if (VillageBuilder.inDisk(du, dv, r + 1)) {
                        f.set(cu + du, h - 1, cv + dv, roof);
                    }
                    if (VillageBuilder.inDisk(du, dv, r - 1)) {
                        f.set(cu + du, h, cv + dv, roof);
                    }
                }
            }
            if (f.lot.style() % 2 == 0) {
                waterTank(f, cu, h + 1, cv);
            }
        }
    }

    /**
     * An apartment block, 4–6 storeys: an open gallery with a railing along the front of every
     * floor, a door and a window for each flat, a ladder up the end of the gallery, beds and tables
     * inside, water tanks and an aerial on the roof.
     */
    static void apartment(LotFrame f) {
        int u1 = 1, u2 = f.w - 2, v1 = 1, v2 = f.d - 4;
        int floors = Math.max(3, f.lot.floors());
        Block wall = f.pick(f.p.houseWalls);
        Block band = f.pick(f.p.roofs);
        shell(f, u1, v1, u2, v2, floors, wall);
        for (int k = 1; k < floors; k++) {
            f.fill(u1, k * STOREY - 1, v1, u2, k * STOREY - 1, v1, band);
            f.fill(u1, k * STOREY - 1, v2, u2, k * STOREY - 1, v2, band);
        }
        int ladderU = u2 - 1;
        for (int k = 0; k < floors; k++) {
            int y = k * STOREY;
            // The gallery and its railing.
            if (k > 0) {
                f.fill(u1, y - 1, v2 + 1, u2, y - 1, v2 + 1, slab(f.p.slab, SlabType.TOP));
                f.fill(u1, y, v2 + 2, u2, y, v2 + 2, f.p.fence);
                f.set(ladderU, y - 1, v2 + 1, Blocks.AIR);
                f.set((u1 + u2) / 2, y + 2, v2 + 1, HANGING_LANTERN);
            }
            for (int u = u1 + 2; u <= u2 - 2; u += 4) {
                if (u == ladderU) {
                    continue;
                }
                door(f, u, y, v2, Direction.SOUTH);
                if (u + 2 < u2) {
                    f.set(u + 2, y + 1, v2, Blocks.GLASS_PANE);
                }
                f.set(u, y + 1, v1, Blocks.GLASS_PANE);
                bed(f, u, v1 + 2, y);
                f.set(u + 1, y, v1 + 1, k % 2 == 0 ? Blocks.BARREL : Blocks.CRAFTING_TABLE);
                f.set(u, y + 2, v2 - 2, HANGING_LANTERN);
            }
            if (k > 0) {
                f.fill(u1 + 1, y - 1, v1 + 1, u2 - 1, y - 1, v2 - 1, f.p.planks);
            }
        }
        // The ladder up the gallery (against the front wall).
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
        for (int y = 0; y < floors * STOREY; y++) {
            f.set(ladderU, y, v2 + 1, ladder);
        }
        int top = floors * STOREY;
        flatRoof(f, u1, v1, u2, v2, top - 1);
        f.set(ladderU, top, v2, Blocks.AIR); // step off the ladder onto the roof
        if (f.p != VillagePalette.SAND) {
            waterTank(f, u1 + 2, top, v1 + 2);
            if (u2 - u1 > 9) {
                waterTank(f, u2 - 2, top, v1 + 2);
            }
        }
        f.fill(u1 + 1, top, v2 - 1, u1 + 1, top + 3, v2 - 1, Blocks.IRON_BARS);
        f.set(u1 + 1, top + 4, v2 - 1, Blocks.LIGHTNING_ROD);
    }

    /** A tall round tower of flats (the Hidden Leaf skyline is full of them). */
    static void roundTower(LotFrame f) {
        int r = Math.max(4, Math.min(f.w, f.d - 1) / 2 - 1);
        int cu = (f.w - 1) / 2;
        int cv = (f.d - 2) / 2;
        int floors = Math.max(4, f.lot.floors());
        int h = floors * STOREY;
        Block wall = f.pick(f.p.houseWalls);
        Block band = f.pick(f.p.roofs);
        for (int du = -r - 1; du <= r + 1; du++) {
            for (int dv = -r - 1; dv <= r + 1; dv++) {
                boolean in = VillageBuilder.inDisk(du, dv, r);
                boolean ring = in && !VillageBuilder.inDisk(du, dv, r - 1);
                int u = cu + du, v = cv + dv;
                if (in) {
                    f.set(u, -1, v, f.p.planks);
                }
                if (ring) {
                    for (int y = 0; y < h; y++) {
                        boolean window = y % STOREY == 1 && ((du * 7 + dv * 3) & 3) == 0;
                        f.set(u, y, v, y % STOREY == 3 ? band : window ? Blocks.GLASS_PANE : wall);
                    }
                } else if (in) {
                    f.fill(u, 0, v, u, h - 1, v, Blocks.AIR);
                    for (int k = 1; k < floors; k++) {
                        f.set(u, k * STOREY - 1, v, f.p.planks);
                    }
                }
                if (VillageBuilder.inDisk(du, dv, r + 1)) {
                    f.set(u, h - 1, v, band);
                }
            }
        }
        frontDoor(f, cu, cv + r, f.d - 1);
        ladder(f, cu, cv - r + 1, 0, h - 1, floors);
        for (int k = 0; k < floors; k++) {
            int y = k * STOREY;
            bed(f, cu - r + 2, cv, y);
            bed(f, cu + r - 2, cv + 1, y);
            f.set(cu, y + 2, cv, HANGING_LANTERN);
            f.set(cu + 1, y, cv - r + 2, k % 2 == 0 ? Blocks.BARREL : Blocks.BOOKSHELF);
        }
        f.set(cu, h - 1, cv - r + 1, slab(f.p.slab, SlabType.BOTTOM));
        if (f.p != VillagePalette.SAND) {
            waterTank(f, cu, h, cv);
        } else {
            dome(f, cu, h - 1, cv, r - 1, band);
        }
    }

    /** A wide traditional house: a raised veranda, paper screens, a big tiled roof (clan homes, the chief's house). */
    static void traditionalHouse(LotFrame f, int u1, int v1, int u2, int v2, boolean garden) {
        if (u2 - u1 < 6 || v2 - v1 < 5) {
            house(f);
            return;
        }
        Block frame = f.p == VillagePalette.SAND ? Blocks.STRIPPED_ACACIA_LOG : Blocks.STRIPPED_DARK_OAK_LOG;
        Block wall = f.p == VillagePalette.SAND ? Blocks.SMOOTH_SANDSTONE : Blocks.WHITE_TERRACOTTA;
        f.fill(u1, -1, v1, u2, -1, v2, f.p.planks);
        f.fill(u1, 0, v1, u2, 0, v2, f.p.planks); // a raised floor
        f.walls(u1, 1, v1, u2, 3, v2, wall);
        for (int u = u1; u <= u2; u += 3) {
            f.fill(u, 1, v1, u, 3, v1, frame);
            f.fill(u, 1, v2, u, 3, v2, frame);
        }
        for (int v = v1; v <= v2; v += 3) {
            f.fill(u1, 1, v, u1, 3, v, frame);
            f.fill(u2, 1, v, u2, 3, v, frame);
        }
        f.fill(u1 + 1, 1, v1 + 1, u2 - 1, 3, v2 - 1, Blocks.AIR);
        // Paper sliding screens along the front, one left open as the doorway.
        for (int u = u1 + 1; u < u2; u++) {
            if (u % 3 != u1 % 3) {
                f.fill(u, 1, v2, u, 2, v2, Blocks.WHITE_STAINED_GLASS_PANE);
            }
        }
        int mid = (u1 + u2) / 2;
        f.fill(mid, 1, v2, mid, 2, v2, Blocks.AIR);
        // The veranda (engawa) and a step up.
        f.fill(u1 - 1 < 0 ? u1 : u1 - 1, 0, v2 + 1, u2, 0, v2 + 1, slab(f.p.slab, SlabType.TOP));
        f.set(mid, 0, v2 + 2, stairs(f.p.stairs, Direction.NORTH));
        // Inside: tatami-like carpets, low tables, futons (beds), a scroll shelf.
        rug(f, u1 + 1, v1 + 1, u2 - 1, v2 - 1, 1, Blocks.LIME_CARPET);
        f.set(mid, 1, (v1 + v2) / 2, Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, Half.TOP));
        bed(f, u1 + 1, v1 + 2, 1);
        bed(f, u2 - 1, v1 + 2, 1);
        f.set(mid, 1, v1 + 1, Blocks.BOOKSHELF);
        f.set(mid - 1, 1, v1 + 1, f.pick(FLOWERS));
        f.set(mid, 3, (v1 + v2) / 2, HANGING_LANTERN);
        // The roof: wide eaves.
        List<Block> stairs = f.p.roofStairs();
        int i = f.rnd.nextInt(stairs.size());
        pitchedRoofOf(f, u1 - 1, v1 - 1, u2 + 1, v2 + 2, 4, wall, stairs.get(i), f.p.roofSlabs().get(i));
        f.set(u1 - 1, 3, v2 + 2, HANGING_LANTERN);
        f.set(u2 + 1, 3, v2 + 2, HANGING_LANTERN);
        if (garden && f.d - v2 > 5) {
            // A little pond garden in front.
            int gv = v2 + 4;
            for (int u = mid - 3; u <= mid + 3; u++) {
                if (Math.abs(u - mid) > 0) {
                    f.set(u, -1, gv, Blocks.WATER);
                    f.set(u, -2, gv, Blocks.CLAY);
                }
            }
            stoneLantern(f, mid - 5, gv);
            stoneLantern(f, mid + 5, gv);
        }
    }

    // ================================================================ shops and food

    /**
     * A shop: an open front with a counter, cloth curtains (noren) over it and an awning; inside,
     * the wares (weapons, scrolls or flowers); the family lives upstairs.
     */
    static void shop(LotFrame f) {
        int u1 = 1, u2 = f.w - 2, v1 = 1, v2 = f.d - 3;
        int kind = f.lot.style() % 3; // 0 weapons, 1 scrolls, 2 flowers
        Block wall = f.pick(f.p.houseWalls);
        int floors = Math.max(2, f.lot.floors());
        shell(f, u1, v1, u2, v2, floors, wall);
        // The open shop front and its counter.
        f.fill(u1 + 1, 0, v2, u2 - 1, 1, v2, Blocks.AIR);
        f.fill(u1 + 1, 0, v2 - 1, u2 - 2, 0, v2 - 1, slab(f.p.slab, SlabType.TOP));
        Block cloth = kind == 0 ? Blocks.BLACK_WALL_BANNER : kind == 1 ? Blocks.BLUE_WALL_BANNER : Blocks.PINK_WALL_BANNER;
        noren(f, u1 + 1, u2 - 1, v2, cloth);
        awning(f, u1, u2, v2 + 1, 3, kind == 2 ? Blocks.PINK_WOOL : f.p.awningA, f.p.awningB);
        // The wares.
        for (int u = u1 + 1; u <= u2 - 1; u++) {
            Block shelf = switch (kind) {
                case 0 -> u % 3 == 0 ? Blocks.ANVIL : u % 3 == 1 ? Blocks.GRINDSTONE : Blocks.SMITHING_TABLE;
                case 1 -> Blocks.BOOKSHELF;
                default -> f.pick(FLOWERS);
            };
            f.set(u, 0, v1 + 1, shelf);
            if (kind == 1) {
                f.set(u, 1, v1 + 1, Blocks.BOOKSHELF);
            } else if (kind == 2) {
                f.set(u, 0, v1 + 2, u % 2 == 0 ? Blocks.FLOWERING_AZALEA : Blocks.AZALEA);
            }
        }
        if (kind == 1) {
            f.set((u1 + u2) / 2, 0, v1 + 3, Blocks.LECTERN);
        }
        f.set((u1 + u2) / 2, 2, (v1 + v2) / 2, HANGING_LANTERN);
        // Upstairs: home.
        ladder(f, u2 - 1, v1 + 1, 0, floors * STOREY - 1, floors);
        bed(f, u1 + 1, v1 + 2, STOREY);
        f.set(u1 + 2, STOREY, v2 - 1, f.pick(FLOWERS));
        f.set(u1 + 1, STOREY, v2 - 1, Blocks.CHEST);
        roof(f, u1, v1, u2, v2, floors, wall);
    }

    /** A restaurant or teahouse: tables and stools, a kitchen at the back, curtains and lanterns at the door. */
    static void restaurant(LotFrame f) {
        int u1 = 1, u2 = f.w - 2, v1 = 1, v2 = f.d - 3;
        boolean ramen = f.lot.style() % 2 == 0;
        Block wall = f.pick(f.p.houseWalls);
        int floors = Math.max(2, f.lot.floors());
        shell(f, u1, v1, u2, v2, floors, wall);
        int mid = (u1 + u2) / 2;
        frontDoor(f, mid, v2, f.d - 1);
        for (int u = u1 + 1; u < u2; u++) {
            if (Math.abs(u - mid) > 1) {
                f.set(u, 1, v2, Blocks.GLASS_PANE);
            }
        }
        noren(f, mid - 1, mid + 1, v2, ramen ? Blocks.RED_WALL_BANNER : Blocks.LIGHT_BLUE_WALL_BANNER);
        // Red paper lanterns either side of the door.
        f.set(mid - 2, 2, v2 + 1, HANGING_LANTERN);
        f.set(mid + 2, 2, v2 + 1, HANGING_LANTERN);
        f.set(mid - 2, 3, v2 + 1, slab(f.p.slab, SlabType.TOP));
        f.set(mid + 2, 3, v2 + 1, slab(f.p.slab, SlabType.TOP));
        // The kitchen counter.
        f.set(u1 + 1, 0, v1 + 1, Blocks.SMOKER.defaultBlockState().setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.FACING, Direction.SOUTH));
        f.set(u1 + 2, 0, v1 + 1, Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
        f.set(u1 + 3, 0, v1 + 1, Blocks.BARREL);
        f.fill(u1 + 1, 0, v1 + 2, u2 - 1, 0, v1 + 2, slab(f.p.slab, SlabType.TOP));
        // Tables with stools.
        for (int u = u1 + 2; u <= u2 - 2; u += 3) {
            table(f, u, v2 - 2, 0);
        }
        f.set(mid, 2, (v1 + v2) / 2 + 1, HANGING_LANTERN);
        ladder(f, u2 - 1, v1 + 1, 0, floors * STOREY - 1, floors);
        bed(f, u1 + 1, v1 + 2, STOREY);
        roof(f, u1, v1, u2, v2, floors, wall);
    }

    /**
     * A food stall: a counter facing the street, stools, a striped awning, a smoker and barrels
     * behind the counter, and a lantern. Drawn on the back {@code depth} rows from {@code v0}.
     */
    static void stall(LotFrame f, int v0, int vFront) {
        int u1 = 1, u2 = Math.max(4, f.w - 2);
        int front = Math.min(f.d - 2, Math.max(v0 + 3, vFront));
        int back = front - 2;
        f.fill(u1, -1, back, u2, -1, front, f.p.planks);
        for (int u = u1; u <= u2; u++) {
            boolean post = u == u1 || u == u2;
            f.set(u, 0, front, post ? f.p.log.defaultBlockState() : f.p.planks.defaultBlockState());
            if (post) {
                f.fill(u, 1, front, u, 2, front, f.p.fence);
                f.fill(u, 0, back, u, 2, back, f.p.fence);
            }
            f.set(u, 3, front + 1, (u & 1) == 0 ? f.p.awningA : f.p.awningB);
            f.set(u, 3, front, (u & 1) == 0 ? f.p.awningA : f.p.awningB);
            f.set(u, 3, back + 1, (u & 1) == 0 ? f.p.awningA : f.p.awningB);
            f.set(u, 3, back, (u & 1) == 0 ? f.p.awningA : f.p.awningB);
        }
        int mid = (u1 + u2) / 2;
        f.set(mid - 1, 0, back, Blocks.BARREL);
        f.set(mid, 0, back, Blocks.SMOKER.defaultBlockState().setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.FACING, Direction.SOUTH));
        f.set(mid + 1, 0, back, Blocks.BARREL);
        for (int u = u1 + 1; u <= u2 - 1; u++) {
            if (front + 1 < f.d) {
                f.set(u, 0, front + 1, stairs(f.p.stairs, Direction.SOUTH));
            }
        }
        f.set(mid, 2, back + 1, HANGING_LANTERN);
    }

    /** The ramen shop: a long counter with stools, white curtains, a big kitchen and a tiled roof. */
    static void ramenShop(LotFrame f) {
        int u1 = 1, u2 = f.w - 2, v1 = 1, v2 = f.d - 3;
        f.fill(u1, -1, v1, u2, -1, v2, f.p.planks);
        f.walls(u1, 0, v1, u2, 2, v2, f.p.planks);
        for (int u : new int[] {u1, u2}) {
            for (int v : new int[] {v1, v2}) {
                f.fill(u, 0, v, u, 3, v, f.p.log);
            }
        }
        f.fill(u1 + 1, 0, v2, u2 - 1, 2, v2, Blocks.AIR);
        f.fill(u1 + 1, 0, v2 - 1, u2 - 1, 0, v2 - 1, slab(Blocks.SMOOTH_STONE_SLAB, SlabType.TOP));
        f.fill(u1 + 1, 3, v2, u2 - 1, 3, v2, f.p.log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        noren(f, u1 + 1, u2 - 1, v2, Blocks.WHITE_WALL_BANNER);
        for (int u = u1 + 1; u <= u2 - 1; u++) {
            f.set(u, 0, v2 + 1, stairs(f.p.stairs, Direction.SOUTH));
        }
        // The kitchen: two stoves, a stock pot (cauldron), barrels of noodles.
        f.set(u1 + 1, 0, v1 + 1, Blocks.SMOKER.defaultBlockState().setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.FACING, Direction.SOUTH));
        f.set(u1 + 2, 0, v1 + 1, Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
        f.set(u1 + 3, 0, v1 + 1, Blocks.SMOKER.defaultBlockState().setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.FACING, Direction.SOUTH));
        f.set(u2 - 1, 0, v1 + 1, Blocks.BARREL);
        f.set(u2 - 1, 1, v1 + 1, Blocks.BARREL);
        f.set(u2 - 2, 0, v1 + 1, Blocks.CRAFTING_TABLE);
        f.set((u1 + u2) / 2, 2, (v1 + v2) / 2, HANGING_LANTERN);
        List<Block> stairs = f.p.roofStairs();
        int i = f.rnd.nextInt(stairs.size());
        pitchedRoofOf(f, u1 - 1, v1 - 1, u2 + 1, v2 + 1, 4, f.p.planks, stairs.get(i), f.p.roofSlabs().get(i));
        f.set((u1 + u2) / 2, 4, v2 + 2, Blocks.RED_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH));
    }

    /** The inn: a long two-storey house with a veranda, many beds upstairs and a dining room below. */
    static void inn(LotFrame f) {
        int u1 = 1, u2 = f.w - 2, v1 = 1, v2 = f.d - 4;
        Block wall = f.pick(f.p.houseWalls);
        shell(f, u1, v1, u2, v2, 2, wall);
        int mid = (u1 + u2) / 2;
        frontDoor(f, mid, v2, f.d - 1);
        noren(f, mid - 1, mid + 1, v2, Blocks.BLUE_WALL_BANNER);
        f.fill(u1, 0, v2 + 1, u2, 0, v2 + 1, slab(f.p.slab, SlabType.BOTTOM));
        for (int u = u1; u <= u2; u += 4) {
            f.fill(u, 0, v2 + 2, u, 2, v2 + 2, f.p.fence);
        }
        f.fill(u1, 3, v2 + 1, u2, 3, v2 + 2, slab(f.p.slab, SlabType.BOTTOM));
        for (int u = u1 + 2; u <= u2 - 2; u += 3) {
            table(f, u, (v1 + v2) / 2 + 1, 0);
            if (u + 1 < u2) {
                bed(f, u, v1 + 2, STOREY);
            }
            f.set(u, 1, v1, Blocks.GLASS_PANE);
            f.set(u, STOREY + 1, v1, Blocks.GLASS_PANE);
            f.set(u, STOREY + 1, v2, Blocks.GLASS_PANE);
        }
        f.set(u1 + 1, 0, v1 + 1, Blocks.SMOKER.defaultBlockState().setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.FACING, Direction.SOUTH));
        f.set(u1 + 2, 0, v1 + 1, Blocks.BARREL);
        ladder(f, u2 - 1, v1 + 1, 0, 2 * STOREY - 1, 2);
        roof(f, u1, v1, u2, v2, 2, wall);
    }

    static void warehouse(LotFrame f) {
        int u1 = 1, u2 = f.w - 2, v1 = 1, v2 = f.d - 3;
        f.fill(u1, -1, v1, u2, -1, v2, f.p.planks);
        f.walls(u1, 0, v1, u2, 2, v2, f.p.planks);
        for (int u : new int[] {u1, u2}) {
            for (int v : new int[] {v1, v2}) {
                f.fill(u, 0, v, u, 2, v, f.p.log);
            }
        }
        f.fill(u1 + 1, 0, v1 + 1, u2 - 1, 2, v2 - 1, Blocks.AIR);
        int mid = (u1 + u2) / 2;
        f.fill(mid - 1, 0, v2, mid + 1, 2, v2, Blocks.AIR);
        for (int u = u1 + 1; u <= u2 - 1; u++) {
            for (int v = v1 + 1; v <= v1 + 2; v++) {
                int stack = 1 + f.rnd.nextInt(3);
                Block crate = f.rnd.nextInt(4) == 0 ? Blocks.HAY_BLOCK : f.rnd.nextInt(3) == 0 ? Blocks.CHEST : Blocks.BARREL;
                if (crate == Blocks.CHEST) {
                    f.set(u, 0, v, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.SOUTH));
                } else {
                    f.fill(u, 0, v, u, stack - 1, v, crate);
                }
            }
        }
        f.set(mid, 2, (v1 + v2) / 2, HANGING_LANTERN);
        roof(f, u1, v1, u2, v2, 1, f.p.planks);
    }

    // ================================================================ green spaces

    /** A walled garden: a hedge, flower beds, a small tree, a stone lantern and a bench. */
    static void garden(LotFrame f) {
        int u1 = 0, u2 = f.w - 1, v1 = 0, v2 = f.d - 1;
        BlockState hedge = leaves(f.p.tree(f.rnd.nextInt(4))[1]);
        int gate = (u1 + u2) / 2;
        for (int u = u1; u <= u2; u++) {
            for (int v = v1; v <= v2; v++) {
                boolean edge = u == u1 || u == u2 || v == v1 || v == v2;
                if (edge && !(v == v2 && Math.abs(u - gate) <= 1)) {
                    f.set(u, 0, v, hedge);
                } else if (!edge && (u + v) % 3 == 0 && f.rnd.nextInt(3) == 0) {
                    f.set(u, 0, v, f.rnd.nextBoolean() ? Blocks.POPPY : Blocks.OXEYE_DAISY);
                }
            }
        }
        f.fill(gate, -1, v1 + 1, gate, -1, v2, Blocks.GRAVEL);
        tree(f, u1 + 2, v1 + 2, 4 + f.rnd.nextInt(2), f.rnd.nextInt(4));
        stoneLantern(f, u2 - 2, v2 - 2);
        f.set(u2 - 2, 0, v1 + 2, stairs(f.p.stairs, Direction.SOUTH));
        f.set(u2 - 3, 0, v1 + 2, stairs(f.p.stairs, Direction.SOUTH));
        if (f.w > 9 && f.d > 9) {
            for (int u = u1 + 2; u <= u1 + 4; u++) {
                for (int v = v2 - 4; v <= v2 - 2; v++) {
                    f.set(u, -1, v, Blocks.WATER);
                    f.set(u, -2, v, Blocks.CLAY);
                }
            }
            f.set(u1 + 3, 0, v2 - 3, Blocks.LILY_PAD);
        }
    }

    /** A park: trees, a gravel path across, benches, flowers, sometimes a pond. */
    static void park(LotFrame f) {
        int mu = f.w / 2;
        int mv = f.d / 2;
        f.fill(mu, -1, 0, mu, -1, f.d - 1, Blocks.GRAVEL);
        f.fill(0, -1, mv, f.w - 1, -1, mv, Blocks.GRAVEL);
        int trees = Math.max(1, f.w * f.d / 40);
        for (int i = 0; i < trees; i++) {
            int u = 1 + f.rnd.nextInt(Math.max(1, f.w - 2));
            int v = 1 + f.rnd.nextInt(Math.max(1, f.d - 2));
            if (Math.abs(u - mu) > 1 && Math.abs(v - mv) > 1) {
                tree(f, u, v, 4 + f.rnd.nextInt(3), f.rnd.nextInt(4));
            }
        }
        f.set(mu + 1, 0, mv + 2, stairs(f.p.stairs, Direction.WEST));
        f.set(mu - 1, 0, mv - 2, stairs(f.p.stairs, Direction.EAST));
        f.set(mu + 2, 0, mv + 1, Blocks.LANTERN);
        f.set(mu + 2, -1, mv + 1, Blocks.STONE_BRICKS);
        for (int i = 0; i < 6; i++) {
            int u = f.rnd.nextInt(f.w);
            int v = f.rnd.nextInt(f.d);
            if (Math.abs(u - mu) > 1 && Math.abs(v - mv) > 1 && f.get(u, 0, v).isAir()) {
                f.set(u, 0, v, f.rnd.nextBoolean() ? Blocks.CORNFLOWER : Blocks.ALLIUM);
            }
        }
    }

    // ================================================================ small buildings

    /** A gate booth: a counter with two seats under a little roof (the gate guards' desk). */
    static void guardPost(LotFrame f) {
        int u1 = 1, u2 = f.w - 2, v1 = 1, v2 = f.d - 2;
        f.fill(u1, -1, v1, u2, -1, v2, f.p.planks);
        for (int u : new int[] {u1, u2}) {
            for (int v : new int[] {v1, v2}) {
                f.fill(u, 0, v, u, 3, v, f.p.log);
            }
        }
        f.fill(u1 + 1, 0, v2, u2 - 1, 0, v2, f.p.planks);
        f.fill(u1 + 1, 1, v2, u2 - 1, 1, v2, slab(f.p.slab, SlabType.BOTTOM));
        f.fill(u1 + 1, 0, v1, u2 - 1, 2, v1, f.p.planks);
        f.set(u1 + 1, 0, v2 - 1, stairs(f.p.stairs, Direction.NORTH));
        f.set(u2 - 1, 0, v2 - 1, stairs(f.p.stairs, Direction.NORTH));
        f.set((u1 + u2) / 2, 1, v2, Blocks.LANTERN);
        f.fill(u1 - 1, 4, v1 - 1, u2 + 1, 4, v2 + 1, slab(f.p.slab, SlabType.BOTTOM));
        f.set((u1 + u2) / 2, 3, (v1 + v2) / 2, HANGING_LANTERN);
    }

    /** A wooden lookout tower on four legs, with a ladder, a railed platform and a roof. */
    static void watchtower(LotFrame f, int cu, int cv) {
        int top = 9;
        for (int du : new int[] {-2, 2}) {
            for (int dv : new int[] {-2, 2}) {
                f.fill(cu + du, 0, cv + dv, cu + du, top + 2, cv + dv, f.p.log);
            }
        }
        f.fill(cu, 0, cv, cu, top - 1, cv, f.p.log);
        f.fill(cu - 2, top, cv - 2, cu + 2, top, cv + 2, f.p.planks);
        for (int du = -2; du <= 2; du++) {
            for (int dv = -2; dv <= 2; dv++) {
                if ((Math.abs(du) == 2 || Math.abs(dv) == 2) && !(Math.abs(du) == 2 && Math.abs(dv) == 2)) {
                    f.set(cu + du, top + 1, cv + dv, f.p.fence);
                }
            }
        }
        f.fill(cu - 3, top + 3, cv - 3, cu + 3, top + 3, cv + 3, f.p.slab);
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
        for (int y = 0; y <= top; y++) {
            f.set(cu, y, cv + 1, ladder);
        }
    }

    // ================================================================ parts

    /** Floors, outer walls with a timber frame, and the floor/ceiling slabs for {@code floors} storeys. */
    static void shell(LotFrame f, int u1, int v1, int u2, int v2, int floors, Block wall) {
        int h = floors * STOREY;
        f.fill(u1, -1, v1, u2, -1, v2, f.p.planks);
        f.walls(u1, 0, v1, u2, h - 1, v2, wall);
        f.fill(u1 + 1, 0, v1 + 1, u2 - 1, h - 2, v2 - 1, Blocks.AIR);
        for (int u : new int[] {u1, u2}) {
            for (int v : new int[] {v1, v2}) {
                f.fill(u, 0, v, u, h - 1, v, f.p.trim());
            }
        }
        for (int k = 1; k < floors; k++) {
            int y = k * STOREY - 1;
            f.fill(u1 + 1, y, v1 + 1, u2 - 1, y, v2 - 1, f.p.planks);
            f.walls(u1, y, v1, u2, y, v2, f.p.planks); // a timber band between floors
        }
        // Windows on every floor: sides and back, every other block.
        for (int k = 0; k < floors; k++) {
            int y = k * STOREY + 1;
            for (int u = u1 + 2; u <= u2 - 2; u += 2) {
                f.set(u, y, v1, Blocks.GLASS_PANE);
                if (k > 0) {
                    f.set(u, y, v2, Blocks.GLASS_PANE);
                }
            }
            for (int v = v1 + 2; v <= v2 - 2; v += 2) {
                f.set(u1, y, v, Blocks.GLASS_PANE);
                f.set(u2, y, v, Blocks.GLASS_PANE);
            }
            f.set((u1 + u2) / 2, k * STOREY + 2, (v1 + v2) / 2, HANGING_LANTERN);
        }
    }

    /** The lot's roof, pitched or flat by the lot's style (the same rule as {@link VillagePlan.Lot#roofTop()}). */
    static void roof(LotFrame f, int u1, int v1, int u2, int v2, int floors, Block wall) {
        int top = floors * STOREY;
        if (f.lot.style() % 3 == 0) {
            pitchedRoof(f, u1, v1, u2, v2, top - 1, wall);
        } else {
            flatRoof(f, u1, v1, u2, v2, top - 1);
            if (f.p != VillagePalette.SAND && f.lot.style() % 3 == 1) {
                waterTank(f, u2 - 2, top, v1 + 2);
            }
        }
    }

    static void flatRoof(LotFrame f, int u1, int v1, int u2, int v2, int y) {
        Block deck = f.pick(f.p.roofs);
        f.fill(u1, y, v1, u2, y, v2, deck);
        for (int u = u1; u <= u2; u++) {
            f.set(u, y + 1, v1, slab(f.p.slab, SlabType.BOTTOM));
            f.set(u, y + 1, v2, slab(f.p.slab, SlabType.BOTTOM));
        }
        for (int v = v1; v <= v2; v++) {
            f.set(u1, y + 1, v, slab(f.p.slab, SlabType.BOTTOM));
            f.set(u2, y + 1, v, slab(f.p.slab, SlabType.BOTTOM));
        }
    }

    /** A tiled gable roof over (u1, v1)–(u2, v2), ridge running across the front, eaves overhanging by one. */
    static void pitchedRoof(LotFrame f, int u1, int v1, int u2, int v2, int y, Block gable) {
        if (f.p == VillagePalette.SAND) {
            vault(f, u1, v1, u2, v2, y);
            return;
        }
        List<Block> stairs = f.p.roofStairs();
        int i = f.rnd.nextInt(stairs.size());
        f.fill(u1, y, v1, u2, y, v2, f.p.planks);
        pitchedRoofOf(f, u1 - 1, v1 - 1, u2 + 1, v2 + 1, y + 1, gable, stairs.get(i), f.p.roofSlabs().get(i));
    }

    /** Stairs climbing from the front and back eaves to a ridge; the gable ends filled with {@code gable}. */
    static void pitchedRoofOf(LotFrame f, int u1, int v1, int u2, int v2, int y, Block gable, Block stairsBlock, Block slabBlock) {
        int depth = v2 - v1 + 1;
        int layers = depth / 2;
        for (int k = 0; k < layers; k++) {
            int yy = y + k;
            for (int u = u1; u <= u2; u++) {
                f.set(u, yy, v2 - k, stairs(stairsBlock, Direction.NORTH));
                f.set(u, yy, v1 + k, stairs(stairsBlock, Direction.SOUTH));
            }
            if (k > 0) {
                f.fill(u1 + 1, yy - 1, v1 + k, u1 + 1, yy - 1, v2 - k, gable);
                f.fill(u2 - 1, yy - 1, v1 + k, u2 - 1, yy - 1, v2 - k, gable);
            }
        }
        if (depth % 2 == 1) {
            int v = v1 + layers;
            f.fill(u1, y + layers, v, u2, y + layers, v, slab(slabBlock, SlabType.BOTTOM));
            f.fill(u1 + 1, y + layers - 1, v, u2 - 1, y + layers - 1, v, gable);
        }
    }

    /** A desert roof: a low rounded vault of smooth sandstone over a flat deck. */
    static void vault(LotFrame f, int u1, int v1, int u2, int v2, int y) {
        f.fill(u1, y, v1, u2, y, v2, f.p.planks);
        double half = (v2 - v1) / 2.0 + 0.5;
        double mid = (v1 + v2) / 2.0;
        for (int v = v1; v <= v2; v++) {
            double off = Math.abs(v - mid);
            int rise = (int) Math.round(Math.sqrt(Math.max(0, half * half - off * off)) * 0.6);
            for (int u = u1; u <= u2; u++) {
                f.fill(u, y + 1, v, u, y + rise, v, Blocks.SMOOTH_SANDSTONE);
            }
        }
    }

    static void dome(LotFrame f, int cu, int y, int cv, int r, Block block) {
        for (int du = -r; du <= r; du++) {
            for (int dv = -r; dv <= r; dv++) {
                for (int dy = 0; dy <= r; dy++) {
                    double dist = Math.sqrt(du * du + dv * dv + dy * dy * 1.6);
                    if (dist <= r + 0.3 && dist > r - 1.2) {
                        f.set(cu + du, y + dy, cv + dv, block);
                    }
                }
            }
        }
    }

    static void cone(LotFrame f, int cu, int y, int cv, int r, Block block) {
        for (int k = 0; k <= r; k++) {
            for (int du = -r; du <= r; du++) {
                for (int dv = -r; dv <= r; dv++) {
                    if (VillageBuilder.inDisk(du, dv, r - k) && !VillageBuilder.inDisk(du, dv, r - k - 2)) {
                        f.set(cu + du, y + k, cv + dv, block);
                    }
                }
            }
        }
    }

    /** A round rooftop water tank on short legs (the Hidden Leaf's rooftops are full of them). */
    static void waterTank(LotFrame f, int u, int y, int v) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            f.set(u + side.getStepX(), y, v + side.getStepZ(), f.p.fence);
            f.set(u + side.getStepX(), y + 1, v + side.getStepZ(), f.p.tank);
            f.set(u + side.getStepX(), y + 2, v + side.getStepZ(), f.p.tank);
        }
        f.set(u, y, v, f.p.fence);
        f.set(u, y + 1, v, f.p.tank);
        f.set(u, y + 2, v, f.p.tank);
        f.set(u, y + 3, v, Blocks.SMOOTH_STONE_SLAB);
    }

    /** A brick chimney with a lit campfire on top, so smoke rises from the house. */
    static void chimney(LotFrame f, int u, int v, int top) {
        Block brick = f.p == VillagePalette.SAND ? Blocks.SANDSTONE : f.p == VillagePalette.SNOW ? Blocks.STONE_BRICKS : Blocks.BRICKS;
        f.fill(u, 0, v, u, top, v, brick);
        f.set(u, top + 1, v, Blocks.CAMPFIRE);
    }

    static void frontDoor(LotFrame f, int u, int v, int street) {
        door(f, u, 0, v, Direction.SOUTH);
        f.set(u - 1, 2, v + 1, HANGING_LANTERN);
        f.fill(u - 1, 3, v + 1, u + 1, 3, v + 1, slab(f.p.slab, SlabType.TOP));
        for (int k = v + 1; k <= street; k++) {
            f.set(u, -1, k, f.p.road);
        }
    }

    /** A door in a wall at (u, y, v), opening towards {@code outward}. */
    static void door(LotFrame f, int u, int y, int v, Direction outward) {
        BlockState lower = f.p.door.defaultBlockState().setValue(DoorBlock.FACING, outward.getOpposite()).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        f.set(u, y, v, lower);
        f.set(u, y + 1, v, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** Cloth shop curtains (noren) hanging from the lintel above the front opening. */
    static void noren(LotFrame f, int u1, int u2, int v, Block banner) {
        for (int u = u1; u <= u2; u++) {
            f.set(u, 2, v, f.p.planks);
            f.set(u, 2, v + 1, banner.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH));
        }
    }

    /** A striped awning sticking out over the street at height {@code y}. */
    static void awning(LotFrame f, int u1, int u2, int v, int y, Block a, Block b) {
        for (int u = u1; u <= u2; u++) {
            f.set(u, y, v, (u & 1) == 0 ? a : b);
        }
    }

    /** A kitchen along the back wall: stove, barrel, a cauldron of water, a crafting table. */
    static void kitchen(LotFrame f, int u1, int v, int u2, int y) {
        Block[] row = {Blocks.SMOKER, Blocks.BARREL, Blocks.WATER_CAULDRON, Blocks.CRAFTING_TABLE};
        for (int i = 0; i < row.length && u1 + i <= u2; i++) {
            BlockState state = row[i].defaultBlockState();
            if (row[i] == Blocks.SMOKER) {
                state = state.setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.FACING, Direction.SOUTH);
            } else if (row[i] == Blocks.WATER_CAULDRON) {
                state = state.setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3);
            }
            f.set(u1 + i, y, v, state);
        }
    }

    /** A little table (a fence post with a pressure plate) with a stool on each side. */
    static void table(LotFrame f, int u, int v, int y) {
        f.set(u, y, v, f.p.fence);
        f.set(u, y + 1, v, Blocks.SPRUCE_PRESSURE_PLATE);
        f.set(u - 1, y, v, stairs(f.p.stairs, Direction.WEST));
        f.set(u + 1, y, v, stairs(f.p.stairs, Direction.EAST));
    }

    /** A bed with its head against the back wall: foot at (u, v), head at (u, v - 1). */
    static void bed(LotFrame f, int u, int v, int y) {
        Block bed = f.pick(BEDS);
        BlockState foot = bed.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT);
        f.set(u, y, v, foot);
        f.set(u, y, v - 1, foot.setValue(BedBlock.PART, BedPart.HEAD));
    }

    static void rug(LotFrame f, int u1, int v1, int u2, int v2, int y) {
        rug(f, u1, v1, u2, v2, y, f.pick(CARPETS));
    }

    static void rug(LotFrame f, int u1, int v1, int u2, int v2, int y, Block carpet) {
        for (int u = u1; u <= u2; u++) {
            for (int v = v1; v <= v2; v++) {
                if (f.get(u, y, v).isAir()) {
                    f.set(u, y, v, carpet);
                }
            }
        }
    }

    /** A ladder up from {@code y1} to {@code y2}, cutting through the floors in between. */
    static void ladder(LotFrame f, int u, int v, int y1, int y2, int floors) {
        if (floors < 2) {
            return;
        }
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
        for (int y = y1; y < y2; y++) {
            f.set(u, y, v, ladder);
        }
    }

    /** Flower pots and a path in front of a house. */
    static void frontYard(LotFrame f, int u1, int u2, int v2) {
        for (int v = v2 + 1; v < f.d; v++) {
            if (f.rnd.nextInt(3) == 0) {
                f.set(u1, 0, v, f.pick(FLOWERS));
            }
            if (f.rnd.nextInt(3) == 0) {
                f.set(u2, 0, v, f.pick(FLOWERS));
            }
        }
    }

    /** A stone lantern (tōrō): a stone post with a lantern and a little cap. */
    static void stoneLantern(LotFrame f, int u, int v) {
        f.set(u, 0, v, Blocks.STONE_BRICK_WALL);
        f.set(u, 1, v, Blocks.LANTERN);
        f.set(u, 2, v, Blocks.STONE_BRICK_SLAB);
    }

    /** A simple tree: a trunk and a round crown of (never-decaying) leaves. */
    static void tree(LotFrame f, int u, int v, int height, int variant) {
        Block[] kind = f.p.tree(variant);
        BlockState leaves = leaves(kind[1]);
        int crown = height >= 6 ? 3 : 2;
        for (int du = -crown; du <= crown; du++) {
            for (int dv = -crown; dv <= crown; dv++) {
                for (int dy = -crown + 1; dy <= crown - (kind[1] == Blocks.SPRUCE_LEAVES ? 0 : 1); dy++) {
                    int r2 = du * du + dv * dv + dy * dy * (kind[1] == Blocks.SPRUCE_LEAVES ? 0 : 1);
                    int width = kind[1] == Blocks.SPRUCE_LEAVES ? Math.max(1, crown - (dy + crown) / 2) : crown;
                    if (r2 <= width * width + 1 && f.get(u + du, height + dy, v + dv).isAir()) {
                        f.set(u + du, height + dy, v + dv, leaves);
                    }
                }
            }
        }
        f.fill(u, 0, v, u, height, v, kind[0]);
    }

    static BlockState leaves(Block block) {
        BlockState state = block.defaultBlockState();
        return state.hasProperty(LeavesBlock.PERSISTENT) ? state.setValue(LeavesBlock.PERSISTENT, true) : state;
    }

    static BlockState stairs(Block block, Direction facing) {
        return block.defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    static BlockState slab(Block block, SlabType type) {
        BlockState state = block.defaultBlockState();
        return state.hasProperty(SlabBlock.TYPE) ? state.setValue(SlabBlock.TYPE, type) : state;
    }
}
