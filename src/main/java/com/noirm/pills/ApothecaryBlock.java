package com.noirm.pills;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

/**
 * The apothecary: a stone mortar on a wooden stump, with a pestle. Right-click to open its screen
 * ({@link ApothecaryMenu}): put a recipe's herbs in the mortar and grind them into pills. It keeps
 * nothing: what you leave in it comes back to you when you close it (like a crafting table).
 */
public class ApothecaryBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 7, 14), Block.box(3, 7, 3, 13, 12, 13));

    public ApothecaryBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer server) {
            open(server, pos);
        }
        return InteractionResult.CONSUME;
    }

    /** Opens the apothecary screen at {@code pos} (public for commands and tests). */
    public static void open(ServerPlayer player, BlockPos pos) {
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inventory, p) -> new ApothecaryMenu(id, inventory, pos),
                Component.translatable("screen.noirm.apothecary")), buf -> buf.writeBlockPos(pos));
    }
}
