package com.noirm.combat;

import com.noirm.Noirm;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * The style scroll: a fighting style written down. Found as chest loot ({@code loot_modifiers/style_loot.json});
 * reading it teaches a style you don't know yet (the one it names, or one picked by the styles'
 * {@code learn.scroll} weights). Masters and commands can hand out a named one ({@link #named}).
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class StyleItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Noirm.MOD_ID);

    public static final RegistryObject<Item> STYLE_SCROLL = ITEMS.register("style_scroll",
            () -> new StyleScrollItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));

    private StyleItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    /** A scroll that teaches exactly {@code style}. */
    public static ItemStack named(ResourceLocation style) {
        ItemStack stack = new ItemStack(STYLE_SCROLL.get());
        stack.getOrCreateTag().putString(StyleScrollItem.TAG, style.toString());
        return stack;
    }

    @SubscribeEvent
    public static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(STYLE_SCROLL);
        }
    }

    /** Reading it teaches a style (see the class comment). */
    public static final class StyleScrollItem extends Item {
        public static final String TAG = "Style";

        public StyleScrollItem(Properties properties) {
            super(properties);
        }

        /** The style this scroll names, or null for "a style you don't know". */
        public static ResourceLocation named(ItemStack stack) {
            CompoundTag tag = stack.getTag();
            return tag == null || !tag.contains(TAG) ? null : ResourceLocation.tryParse(tag.getString(TAG));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide()) {
                return InteractionResultHolder.success(stack);
            }
            if (read(player, stack)) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                return InteractionResultHolder.consume(stack);
            }
            return InteractionResultHolder.fail(stack);
        }

        /** Teaches the scroll's style (or a random unknown one). False if there was nothing to learn. */
        public static boolean read(Player player, ItemStack stack) {
            ShinobiData data = ShinobiCapability.get(player).orElse(null);
            if (data == null) {
                return false;
            }
            ResourceLocation style = named(stack);
            if (style == null) {
                style = StyleSystem.scrollStyle(data, player.getRandom());
            }
            if (style == null || CombatData.style(style) == null) {
                player.displayClientMessage(Component.translatable("message.noirm.style.scroll_nothing").withStyle(ChatFormatting.GRAY), true);
                return false;
            }
            if (StyleSystem.knows(data, style)) {
                player.displayClientMessage(Component.translatable("message.noirm.style.scroll_known",
                        Component.translatable(StyleSystem.translationKey(style))).withStyle(ChatFormatting.GRAY), true);
                return false;
            }
            return StyleSystem.grant(player, style, StyleSystem.SCROLL);
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            ResourceLocation style = named(stack);
            tooltip.add(style == null
                    ? Component.translatable("item.noirm.style_scroll.tip").withStyle(ChatFormatting.GRAY)
                    : Component.translatable("item.noirm.style_scroll.tip_named", Component.translatable(StyleSystem.translationKey(style)))
                            .withStyle(ChatFormatting.AQUA));
        }
    }
}
