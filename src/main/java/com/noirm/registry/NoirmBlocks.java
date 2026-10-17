package com.noirm.registry;

import com.noirm.Noirm;
import com.noirm.jutsu.EarthWallBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Every block NOIRM adds. */
public final class NoirmBlocks {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Noirm.MOD_ID);

    /** Raised by Doton: Earth Wall; crumbles by itself. Uses the vanilla packed-mud texture. */
    public static final RegistryObject<Block> EARTH_WALL = BLOCKS.register("earth_wall",
            () -> new EarthWallBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(2f, 6f)
                    .sound(SoundType.PACKED_MUD).noLootTable()));

    private NoirmBlocks() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
