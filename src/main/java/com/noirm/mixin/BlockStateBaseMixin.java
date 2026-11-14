package com.noirm.mixin;

import com.noirm.chakra.WaterWalking;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Water walking: gives the top block of water a thin solid skin for anyone who can walk on it
 * right now (all the rules are in {@link WaterWalking}). A "mixin" patches a vanilla method when
 * the game loads; this one adds to the result of {@code BlockState#getCollisionShape}.
 *
 * <p>The method has two names: {@code getCollisionShape} while developing, {@code m_60742_} in
 * the real game. Both are listed (with {@code remap = false}), so the mixin needs no refmap and
 * no remapping setup; whichever name exists is the one patched.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
    @Inject(method = {
            "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            "m_60742_(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;"
    }, at = @At("RETURN"), cancellable = true, remap = false)
    private void noirm$waterSurface(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> result) {
        VoxelShape surface = WaterWalking.surfaceShape((BlockBehaviour.BlockStateBase) (Object) this, level, pos, context);
        if (surface != null) {
            VoxelShape own = result.getReturnValue();
            result.setReturnValue(own.isEmpty() ? surface : Shapes.or(own, surface));
        }
    }
}
