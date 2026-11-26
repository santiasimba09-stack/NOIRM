package com.noirm.nature;

import com.noirm.Noirm;
import com.noirm.clan.Nature;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The chakra paper, nature scrolls (one per basic nature) and jutsu scrolls (one per rank). */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NatureItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Noirm.MOD_ID);

    public static final RegistryObject<Item> CHAKRA_PAPER = ITEMS.register("chakra_paper",
            () -> new ChakraPaperItem(new Item.Properties().stacksTo(16)));

    /** A nature scroll per basic nature ({@code nature_scroll_fire}…). */
    public static final Map<Nature, RegistryObject<Item>> NATURE_SCROLLS = new EnumMap<>(Nature.class);
    /** A jutsu scroll per rank ({@code jutsu_scroll_c}…). */
    public static final Map<String, RegistryObject<Item>> JUTSU_SCROLLS = new LinkedHashMap<>();

    static {
        for (Nature nature : NatureDrills.TRAINABLE) {
            NATURE_SCROLLS.put(nature, ITEMS.register("nature_scroll_" + nature.getSerializedName(),
                    () -> new NatureScrollItem(nature, new Item.Properties().stacksTo(1).rarity(Rarity.RARE))));
        }
        for (String rank : List.of("C", "B", "A", "S")) {
            Rarity rarity = switch (rank) {
                case "C" -> Rarity.COMMON;
                case "B" -> Rarity.UNCOMMON;
                case "A" -> Rarity.RARE;
                default -> Rarity.EPIC;
            };
            JUTSU_SCROLLS.put(rank, ITEMS.register("jutsu_scroll_" + rank.toLowerCase(java.util.Locale.ROOT),
                    () -> new JutsuScrollItem(rank, new Item.Properties().stacksTo(16).rarity(rarity))));
        }
    }

    private NatureItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        NatureLoot.register(modBus);
    }

    public static ItemStack natureScroll(Nature nature) {
        return new ItemStack(NATURE_SCROLLS.get(nature).get());
    }

    public static ItemStack jutsuScroll(String rank) {
        return new ItemStack(JUTSU_SCROLLS.get(rank).get());
    }

    /** A jutsu scroll that holds one named jutsu (rewards, masters, commands). */
    public static ItemStack jutsuScroll(String rank, ResourceLocation jutsu) {
        ItemStack stack = jutsuScroll(rank);
        stack.getOrCreateTag().putString(JutsuScrollItem.TAG, jutsu.toString());
        return stack;
    }

    @SubscribeEvent
    public static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(CHAKRA_PAPER);
            NATURE_SCROLLS.values().forEach(event::accept);
            JUTSU_SCROLLS.values().forEach(event::accept);
        }
    }

    private static ShinobiData data(Player player) {
        return ShinobiCapability.get(player).orElse(null);
    }

    // ================================================================ the items

    /** Hold use to channel chakra into it: the paper reacts to your natures and shows the hidden ones. */
    public static final class ChakraPaperItem extends Item {
        public static final int USE_TICKS = 24;

        public ChakraPaperItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(player.getItemInHand(hand));
        }

        @Override
        public int getUseDuration(ItemStack stack) {
            return USE_TICKS;
        }

        @Override
        public UseAnim getUseAnimation(ItemStack stack) {
            return UseAnim.BOW;
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            if (!level.isClientSide() && entity instanceof Player player) {
                ShinobiData data = data(player);
                if (data != null && NatureDrills.paperTest(player, data) && !player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            return stack;
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.noirm.chakra_paper.tip").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Starts the drill for its nature and puts a quarter of it behind you (never all of it). */
    public static final class NatureScrollItem extends Item {
        private final Nature nature;

        public NatureScrollItem(Nature nature, Properties properties) {
            super(properties);
            this.nature = nature;
        }

        public Nature nature() {
            return nature;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide()) {
                return InteractionResultHolder.success(stack);
            }
            ShinobiData data = data(player);
            if (data != null && NatureDrills.readScroll(player, data, nature)) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                return InteractionResultHolder.consume(stack);
            }
            return InteractionResultHolder.fail(stack);
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.noirm.nature_scroll.tip", NatureDrills.name(nature)).withStyle(ChatFormatting.GRAY));
        }
    }

    /** Teaches a jutsu of its rank (or the one it names) of a nature you own. */
    public static final class JutsuScrollItem extends Item {
        public static final String TAG = "Jutsu";
        private final String rank;

        public JutsuScrollItem(String rank, Properties properties) {
            super(properties);
            this.rank = rank;
        }

        public String rank() {
            return rank;
        }

        /** The jutsu this scroll names, or null for "a jutsu of its rank". */
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
            ShinobiData data = data(player);
            if (data != null && JutsuLearning.readScroll(player, data, rank, named(stack)) != null) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                return InteractionResultHolder.consume(stack);
            }
            return InteractionResultHolder.fail(stack);
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            ResourceLocation named = named(stack);
            if (named != null) {
                tooltip.add(Component.translatable("item.noirm.jutsu_scroll.tip_named", JutsuLearning.jutsuName(named)).withStyle(ChatFormatting.AQUA));
            }
            tooltip.add(Component.translatable("item.noirm.jutsu_scroll.tip", rank).withStyle(ChatFormatting.GRAY));
        }
    }
}
