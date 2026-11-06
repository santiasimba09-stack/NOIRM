package com.noirm.weapon;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * An explosive tag (paper bomb). Right click a block to slap it on: it fizzes for 3 seconds,
 * then blows. Craft one onto a kunai for an explosive kunai. The blast only digs a crater when
 * the {@code noirmTerrainDamage} game rule allows it (and, for ninjas, {@code mobGriefing}).
 */
public class ExplosiveTagItem extends Item {
    public ExplosiveTagItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level instanceof ServerLevel server) {
            Vec3 normal = Vec3.atLowerCornerOf(context.getClickedFace().getNormal());
            Vec3 at = context.getClickLocation().add(normal.scale(0.3));
            ThrownWeapon tag = ThrownWeapon.placedTag(server, context.getPlayer(), at, normal.scale(-0.35));
            server.addFreshEntity(tag);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.CREEPER_PRIMED, SoundSource.PLAYERS, 0.6f, 1.6f);
            if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.noirm.explosive_tag").withStyle(ChatFormatting.GRAY));
    }
}
