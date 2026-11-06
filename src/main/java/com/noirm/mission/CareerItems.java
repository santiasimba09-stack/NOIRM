package com.noirm.mission;

import com.noirm.Noirm;
import com.noirm.economy.RyoPouchItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/** The blocks and items of the career systems: the mission board, ryo pouches, mission parcels and scrolls. */
public final class CareerItems {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Noirm.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Noirm.MOD_ID);

    /** The missions board: in every village's Kage plaza / town square / outpost; right-click it. Vanilla textures. */
    public static final RegistryObject<Block> MISSION_BOARD = BLOCKS.register("mission_board",
            () -> new MissionBoardBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f, 6f).sound(SoundType.WOOD)));
    public static final RegistryObject<Item> MISSION_BOARD_ITEM = ITEMS.register("mission_board",
            () -> new BlockItem(MISSION_BOARD.get(), new Item.Properties()));

    /** Ryo you can hold, drop and trade (the amount is in the item). */
    public static final RegistryObject<Item> RYO_POUCH = ITEMS.register("ryo_pouch",
            () -> new RyoPouchItem(new Item.Properties().stacksTo(1)));

    /** A delivery mission's sealed parcel. */
    public static final RegistryObject<Item> MISSION_PARCEL = ITEMS.register("mission_parcel",
            () -> new MissionItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    /** The scroll a retrieval mission sends you after. */
    public static final RegistryObject<Item> MISSION_SCROLL = ITEMS.register("mission_scroll",
            () -> new MissionItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    private CareerItems() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(CareerItems::creativeTabs);
        // Jutsu scrolls and other learning: B needs chūnin, A jōnin, S ANBU.
        com.noirm.nature.JutsuLearning.extraRankGate = PlayerRank::jutsuGate;
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(MISSION_BOARD_ITEM);
        }
    }

    /** A mission's item: says what it's for; useless once the mission is over. */
    public static class MissionItem extends Item {
        public MissionItem(Properties properties) {
            super(properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.noirm.mission_item.tip").withStyle(ChatFormatting.GRAY));
        }
    }
}
