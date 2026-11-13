package com.noirm.pills;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/**
 * A medicinal herb (or rice): four stages like beetroot. It grows wild on grass and dirt in its
 * biomes (worldgen: {@code data/noirm/worldgen/} + {@code forge/biome_modifier/}) and can be farmed:
 * on farmland it grows several times faster (vanilla crop rules). Ripe, it drops the herb and its
 * seeds (loot table {@code blocks/<id>.json}); bone meal works.
 */
public class HerbBlock extends CropBlock {
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    public static final int MAX_AGE = 3;
    private static final VoxelShape[] SHAPES = {
            Block.box(0, 0, 0, 16, 3, 16), Block.box(0, 0, 0, 16, 6, 16),
            Block.box(0, 0, 0, 16, 10, 16), Block.box(0, 0, 0, 16, 13, 16)};

    private final Supplier<? extends Item> seed;

    public HerbBlock(Supplier<? extends Item> seed, Properties properties) {
        super(properties);
        this.seed = seed;
    }

    @Override
    public IntegerProperty getAgeProperty() {
        return AGE;
    }

    @Override
    public int getMaxAge() {
        return MAX_AGE;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return seed.get();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[Math.min(MAX_AGE, state.getValue(AGE))];
    }

    /** Farmland, or wild ground: grass, dirt, podzol, moss, mud (the dirt tag), and sand (ashthorn in the badlands). */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.FARMLAND) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND);
    }

    /**
     * Only the ground matters (vanilla crops also want light, but light isn't known yet while the
     * world generates, and wild herbs must be placeable then). They still need light to grow.
     */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return mayPlaceOn(level.getBlockState(below), level, below);
    }
}
