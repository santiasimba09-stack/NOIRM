package com.noirm.jutsu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wood grown by Wood Release (grey-green, ink-grained: {@code tools/woodgen/gen_wood.py}). The
 * Deep Forest's trunks are placed through {@link com.noirm.world.Destruction}, so they regrow
 * away with the terrain; a Wood Dome schedules a tick on each of its blocks and they splinter
 * away when it fires. Drops nothing.
 */
public class WoodReleaseBlock extends RotatedPillarBlock {
    public WoodReleaseBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.removeBlock(pos, false);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5,
                pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
        if (random.nextInt(4) == 0) {
            level.playSound(null, pos, com.noirm.sound.NoirmSounds.WOOD_BURST.get(), SoundSource.BLOCKS, 0.7f, 0.77f);
        }
    }
}
