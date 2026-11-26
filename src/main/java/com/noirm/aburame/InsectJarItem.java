package com.noirm.aburame;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * A belt jar (5,000) or the gourd (25,000): a store of dormant insects that works from anywhere in
 * your inventory (owner's decision 5: no armour slot). The count lives on the item, so a dropped
 * gourd takes its insects with it. Two jars and one gourd count; extra ones are just containers.
 * <ul>
 *   <li>use: fill it from your body (up to half of what's in your body);</li>
 *   <li>sneak + use: empty it back into your body (up to its capacity).</li>
 * </ul>
 * Dormant insects cost no chakra and don't breed. A jar can hold rinkaichū (a "rinkaichū jar"),
 * which the Breeding Jar uses for the Antivenom cross.
 */
public class InsectJarItem extends Item {
    public static final String COUNT = "Insects";
    public static final String SPECIES = "Species";
    private final int capacity;
    private final boolean gourd;

    public InsectJarItem(Properties properties, int capacity, boolean gourd) {
        super(properties);
        this.capacity = capacity;
        this.gourd = gourd;
    }

    public int capacity() {
        return capacity;
    }

    public boolean isGourd() {
        return gourd;
    }

    public static int count(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt(COUNT) : 0;
    }

    public static Species species(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(SPECIES) ? Species.byKey(stack.getTag().getString(SPECIES)) : Species.KIKAICHU;
    }

    public static void set(ItemStack stack, int count, Species species) {
        int cap = stack.getItem() instanceof InsectJarItem jar ? jar.capacity : 0;
        int n = Math.max(0, Math.min(cap, count));
        stack.getOrCreateTag().putInt(COUNT, n);
        if (species != null && species != Species.KIKAICHU) {
            stack.getTag().putString(SPECIES, species.key());
        } else {
            stack.getTag().remove(SPECIES);
        }
    }

    public static int room(ItemStack stack) {
        return stack.getItem() instanceof InsectJarItem jar ? jar.capacity - count(stack) : 0;
    }

    /** The jars and gourd that count for {@code player}: the first two jars and the first gourd found. */
    public static List<ItemStack> worn(Player player) {
        List<ItemStack> out = new ArrayList<>();
        int jars = 0;
        boolean gourd = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof InsectJarItem jar) {
                if (jar.gourd && !gourd) {
                    gourd = true;
                    out.add(stack);
                } else if (!jar.gourd && jars < ColonyGrowth.JAR_SLOTS) {
                    jars++;
                    out.add(stack);
                }
            }
        }
        return out;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        ColonyData data = Colony.of(player);
        if (data == null) {
            player.displayClientMessage(Component.translatable("message.noirm.colony.none"), true);
            return InteractionResultHolder.fail(stack);
        }
        Species species = species(stack);
        if (player.isShiftKeyDown()) {
            int room = Math.max(0, Colony.capacity(player) - data.bodyTotal());
            int move = Math.min(room, count(stack));
            set(stack, count(stack) - move, species);
            data.addBody(species, move);
            player.displayClientMessage(Component.translatable("message.noirm.colony.jar_emptied", move), true);
        } else {
            if (count(stack) > 0 && species != Species.KIKAICHU) {
                return InteractionResultHolder.fail(stack); // one species per jar
            }
            int move = Math.min(room(stack), data.body(Species.KIKAICHU) / 2);
            data.addBody(Species.KIKAICHU, -move);
            set(stack, count(stack) + move, Species.KIKAICHU);
            player.displayClientMessage(Component.translatable("message.noirm.colony.jar_filled", move), true);
        }
        Colony.changed(player);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
        tips.add(Component.translatable("item.noirm.insect_jar.count", count(stack), capacity,
                Component.translatable(species(stack).translationKey())).withStyle(ChatFormatting.GRAY));
        tips.add(Component.translatable(gourd ? "item.noirm.insect_gourd.desc" : "item.noirm.insect_jar.desc").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return count(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * count(stack) / capacity);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x3FA8A8;
    }
}
