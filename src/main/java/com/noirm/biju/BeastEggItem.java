package com.noirm.biju;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeSpawnEggItem;

/**
 * A spawn egg for one tailed beast (1–9 tails). It's a normal spawn egg that writes {@code EntityTag {Tails:N}} on
 * itself before it spawns, so the beast comes out with the right number of tails (and its name, size and strength).
 */
public class BeastEggItem extends ForgeSpawnEggItem {
    private final int tails;

    public BeastEggItem(int tails, int background, int highlight, Properties props) {
        super(com.noirm.registry.NoirmEntities.TAILED_BEAST, background, highlight, props);
        this.tails = tails;
    }

    public int tails() {
        return tails;
    }

    /** Makes sure the stack carries this beast's tail count (eggs from /give or crafting start without it). */
    public ItemStack stamp(ItemStack stack) {
        CompoundTag entity = stack.getOrCreateTagElement("EntityTag");
        if (entity.getInt("Tails") != tails) {
            entity.putInt("Tails", tails);
        }
        return stack;
    }

    @Override
    public ItemStack getDefaultInstance() {
        return stamp(super.getDefaultInstance());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        stamp(context.getItemInHand());
        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        stamp(player.getItemInHand(hand));
        return super.use(level, player, hand);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        if (!level.isClientSide() && (stack.getTag() == null || !stack.getTag().contains("EntityTag"))) {
            stamp(stack); // so a dispenser loaded from your inventory spawns the right beast too
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.noirm.beast_spawn_egg", BijuSystem.beastName(tails));
    }
}
