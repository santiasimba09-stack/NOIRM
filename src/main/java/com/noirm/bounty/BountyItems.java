package com.noirm.bounty;

import com.noirm.Noirm;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/** The bingo book item: use it to read the book (dangerous rogues, their bounties, where they were last seen). */
public final class BountyItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Noirm.MOD_ID);

    public static final RegistryObject<Item> BINGO_BOOK = ITEMS.register("bingo_book",
            () -> new BingoBookItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    private BountyItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(BountyItems::creativeTabs);
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(BINGO_BOOK);
        }
    }

    /** Right-click to open the book. */
    public static class BingoBookItem extends Item {
        public BingoBookItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer serverPlayer) {
                BountyPackets.openBook(serverPlayer);
                level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.9f);
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.noirm.bingo_book.tip").withStyle(ChatFormatting.GRAY));
        }
    }
}
