package com.noirm.aburame;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
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

/**
 * The Insect Hive (docs/ABURAME.md, 2.6): a dark clay jar-house full of holes that stores 60,000 of
 * your insects at home and breeds them for free. Up to 3 per player.
 * <ul>
 *   <li>use: feed it (10 chakra → +300) and see how it's doing;</li>
 *   <li>sneak + use: move 5,000 insects from your body into it;</li>
 *   <li>use with a honey bottle: ×1.25 breeding for a day;</li>
 *   <li>sleep within 16 blocks: your body refills from it in the morning.</li>
 * </ul>
 */
public class InsectHiveBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 15, 14);

    public InsectHiveBlock(Properties properties) {
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
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (player != null && !context.getLevel().isClientSide()) {
            ColonyData colony = Colony.of(player);
            if (colony != null && colony.hives().size() >= ColonyGrowth.MAX_HIVES && !player.getAbilities().instabuild) {
                player.displayClientMessage(Component.translatable("message.noirm.colony.hive_limit", ColonyGrowth.MAX_HIVES), true);
                return null;
            }
        }
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide() && placer instanceof Player player && level.getBlockEntity(pos) instanceof InsectHiveBlockEntity hive) {
            hive.setOwner(player.getUUID());
            Colony.noteHive(player, level.dimension().location().toString(), pos, 0);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof InsectHiveBlockEntity hive)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.HONEY_BOTTLE)) {
            hive.addHoney();
            held.shrink(1);
            player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));
            player.displayClientMessage(Component.translatable("message.noirm.colony.hive_honey"), true);
            return InteractionResult.CONSUME;
        }
        hive.interact(player, player.isShiftKeyDown());
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide() && level.getBlockEntity(pos) instanceof InsectHiveBlockEntity hive) {
            hive.forgetFromOwner();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InsectHiveBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, AburameRegistry.HIVE_ENTITY.get(), InsectHiveBlockEntity::serverTick);
    }
}
