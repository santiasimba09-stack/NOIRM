package com.noirm.workshop;

import com.noirm.Noirm;
import com.noirm.clan.Nature;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
 * The workshop's scrolls: a <b>blank scroll</b> (crafted from paper and a stick) to write on, and a
 * <b>creation scroll</b>, written in the workshop, that teaches one exact self-made jutsu to whoever
 * reads it (if they own its nature). Give them away or sell them.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WorkshopItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Noirm.MOD_ID);

    public static final RegistryObject<Item> BLANK_SCROLL = ITEMS.register("blank_scroll",
            () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> CREATION_SCROLL = ITEMS.register("creation_scroll",
            () -> new CreationScrollItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    private WorkshopItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    /** A scroll holding this design. */
    public static ItemStack creationScroll(JutsuDesign design) {
        ItemStack stack = new ItemStack(CREATION_SCROLL.get());
        stack.getOrCreateTag().put(CreationScrollItem.TAG, design.save());
        return stack;
    }

    @SubscribeEvent
    public static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(BLANK_SCROLL);
        }
    }

    /** Teaches the self-made jutsu written on it. */
    public static final class CreationScrollItem extends Item {
        public static final String TAG = "Design";

        public CreationScrollItem(Properties properties) {
            super(properties);
        }

        /** The design written on the scroll, or null for a blank or broken one. */
        public static JutsuDesign design(ItemStack stack) {
            CompoundTag tag = stack.getTag();
            return tag == null || !tag.contains(TAG) ? null : JutsuDesign.load(tag.getCompound(TAG));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide()) {
                return InteractionResultHolder.success(stack);
            }
            ShinobiData data = ShinobiCapability.get(player).orElse(null);
            if (data != null && Workshop.readScroll(player, data, design(stack))) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                return InteractionResultHolder.consume(stack);
            }
            return InteractionResultHolder.fail(stack);
        }

        @Override
        public Component getName(ItemStack stack) {
            JutsuDesign d = design(stack);
            return d == null ? super.getName(stack) : Component.translatable("item.noirm.creation_scroll.named", d.name());
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            JutsuDesign d = design(stack);
            if (d == null) {
                tooltip.add(Component.translatable("item.noirm.creation_scroll.blank").withStyle(ChatFormatting.GRAY));
                return;
            }
            Workshop.Stats s = Workshop.stats(d);
            Nature n = d.nature();
            tooltip.add(Component.translatable("item.noirm.creation_scroll.kind", s.rank(),
                    Component.translatable(n.translationKey()).withStyle(st -> st.withColor(n.color())),
                    Component.translatable(d.shape().translationKey())).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.noirm.creation_scroll.cost", Math.round(s.cost()),
                    String.format(java.util.Locale.ROOT, "%.1f", s.cooldown() / 20f)).withStyle(ChatFormatting.DARK_AQUA));
            if (!d.authorName().isEmpty()) {
                tooltip.add(Component.translatable("item.noirm.creation_scroll.author", d.authorName()).withStyle(ChatFormatting.DARK_GRAY));
            }
            tooltip.add(Component.translatable("item.noirm.creation_scroll.tip").withStyle(ChatFormatting.GRAY));
        }
    }
}
