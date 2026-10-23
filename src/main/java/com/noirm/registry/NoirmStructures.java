package com.noirm.registry;

import com.noirm.Noirm;
import com.noirm.world.village.NinjaVillageStructure;
import com.noirm.world.village.VillagePiece;
import com.noirm.world.village.VillageWardStructure;
import com.noirm.world.village.WardPlacement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * NOIRM's world-generation structures. The <i>type</i> (code) is registered here; the actual
 * structures, how often they appear and in which biomes live in data:
 * {@code data/noirm/worldgen/structure/}, {@code worldgen/structure_set/} and
 * {@code tags/worldgen/biome/has_structure/}.
 */
public final class NoirmStructures {
    private static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Noirm.MOD_ID);
    private static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Noirm.MOD_ID);
    private static final DeferredRegister<StructurePlacementType<?>> PLACEMENTS = DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, Noirm.MOD_ID);

    /** Ninja villages (a hidden village's core, a town or an outpost). */
    public static final RegistryObject<StructureType<NinjaVillageStructure>> NINJA_VILLAGE = TYPES.register("ninja_village",
            () -> () -> NinjaVillageStructure.CODEC);

    /** One chunk of a hidden village (it's too big for one structure). */
    public static final RegistryObject<StructureType<VillageWardStructure>> VILLAGE_WARD = TYPES.register("village_ward",
            () -> () -> VillageWardStructure.CODEC);

    public static final RegistryObject<StructurePieceType> VILLAGE_PIECE = PIECES.register("ninja_village_piece",
            () -> (StructurePieceType.ContextlessType) VillagePiece::new);

    /** Places the wards: every chunk around a hidden village's anchor. */
    public static final RegistryObject<StructurePlacementType<WardPlacement>> WARD_PLACEMENT = PLACEMENTS.register("village_wards",
            () -> () -> WardPlacement.CODEC);

    private NoirmStructures() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        PIECES.register(modBus);
        PLACEMENTS.register(modBus);
    }
}
