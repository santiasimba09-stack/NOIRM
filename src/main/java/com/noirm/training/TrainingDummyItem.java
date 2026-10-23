package com.noirm.training;

import com.noirm.entity.TrainingDummy;
import com.noirm.registry.NoirmEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Places a {@link TrainingDummy} on the clicked block, facing whoever placed it. */
public class TrainingDummyItem extends Item {
    public TrainingDummyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        TrainingDummy dummy = NoirmEntities.TRAINING_DUMMY.get().create(level);
        if (dummy == null || !level.noCollision(NoirmEntities.TRAINING_DUMMY.get().getAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) {
            return InteractionResult.FAIL;
        }
        float yaw = context.getPlayer() == null ? 0f : Mth.wrapDegrees(context.getPlayer().getYRot() + 180f);
        yaw = Math.round(yaw / 45f) * 45f;
        dummy.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0f);
        dummy.setYHeadRot(yaw);
        dummy.setYBodyRot(yaw);
        level.addFreshEntity(dummy);
        level.playSound(null, pos, SoundEvents.ARMOR_STAND_PLACE, SoundSource.BLOCKS, 0.75f, 0.8f);
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
