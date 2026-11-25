package com.noirm.aburame;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * The Breeding Jar (docs/ABURAME.md, 2.6): glass, clay and a hive frame. Use it to open its screen:
 * choose an adaptation to breed (twice as fast), run the kikaichū × rinkaichū cross toward Antivenom,
 * breed a wild insect into a strain, raise a Kidaichū Egg or a Queen.
 */
public class BreedingJarBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 14, 13);

    public BreedingJarBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide() && placer instanceof Player player && level.getBlockEntity(pos) instanceof BreedingJarBlockEntity jar) {
            jar.setOwner(player.getUUID());
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.noirm.client.aburame.ClientColony.openJar(pos));
        } else if (level.getBlockEntity(pos) instanceof BreedingJarBlockEntity jar && jar.owner() == null) {
            jar.setOwner(player.getUUID());
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BreedingJarBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, AburameRegistry.JAR_ENTITY.get(), BreedingJarBlockEntity::serverTick);
    }
}
