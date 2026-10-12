package com.noirm.jutsu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Earth raised by Doton: Earth Wall. Crumbles by itself when its scheduled tick fires (the jutsu
 * schedules it). Drops nothing.
 */
public class EarthWallBlock extends Block {
    public EarthWallBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.removeBlock(pos, false);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5,
                pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.05);
        if (random.nextInt(3) == 0) {
            level.playSound(null, pos, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 0.6f, 0.8f);
        }
    }
}
