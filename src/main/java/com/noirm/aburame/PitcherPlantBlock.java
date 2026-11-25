package com.noirm.aburame;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Pitcher Plant (docs/ABURAME.md, 2.7): an anti-Aburame trap. It eats 40 insects a second from any
 * swarm within 3 blocks, and an Aburame standing near it loses Vigour.
 */
public class PitcherPlantBlock extends BushBlock {
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 13, 12);

    public PitcherPlantBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isSolidRender(level, pos);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return mayPlaceOn(level.getBlockState(pos.below()), level, pos.below());
    }

    /** Is there a pitcher plant within {@code range} blocks of {@code pos}? */
    public static boolean near(Level level, BlockPos pos, int range) {
        if (!AburameRegistry.PITCHER_PLANT.isPresent()) {
            return false;
        }
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-range, -range, -range), pos.offset(range, range, range))) {
            if (level.isLoaded(p) && level.getBlockState(p).is(AburameRegistry.PITCHER_PLANT.get())) {
                return true;
            }
        }
        return false;
    }
}
