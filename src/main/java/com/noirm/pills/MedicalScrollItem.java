package com.noirm.pills;

import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A medical scroll, found in old chests and on strong rogues: a medic's notes. Reading it
 * (right-click) teaches one pill recipe you don't know yet (any that isn't a starter, even one a
 * master would teach). If you know them all, its notes give pharmacology XP instead.
 */
public class MedicalScrollItem extends Item {
    /** XP when there's nothing left to learn from it. */
    public static final float SPARE_XP = 40f;

    public MedicalScrollItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (data == null) {
            return InteractionResultHolder.fail(stack);
        }
        read(player, data, player.getRandom());
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.9f, 0.9f);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.consume(stack);
    }

    /** Reads a scroll: teaches a recipe (returned) or, with nothing left, gives XP (returns null). Public for tests. */
    public static ResourceLocation read(Player player, ShinobiData data, RandomSource random) {
        List<ResourceLocation> unknown = new ArrayList<>();
        for (var e : PillRegistry.recipes()) {
            if (!Apothecary.knows(data, e.getKey(), e.getValue().recipe().get())) {
                unknown.add(e.getKey());
            }
        }
        if (unknown.isEmpty()) {
            Pharmacology.grant(player, data, SPARE_XP);
            player.sendSystemMessage(Component.translatable("message.noirm.medical_scroll.nothing_new").withStyle(ChatFormatting.GRAY));
            return null;
        }
        ResourceLocation id = unknown.get(random.nextInt(unknown.size()));
        data.pills().learn(id);
        PillDefinition def = PillRegistry.pill(id);
        Item item = def == null ? null : ForgeRegistries.ITEMS.getValue(def.item());
        player.sendSystemMessage(Component.translatable("message.noirm.medical_scroll.learned",
                item == null ? Component.literal(id.toString()) : Component.translatable(item.getDescriptionId())).withStyle(ChatFormatting.GREEN));
        data.markDirty();
        return id;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noirm.medical_scroll.tip").withStyle(ChatFormatting.GRAY));
    }
}
