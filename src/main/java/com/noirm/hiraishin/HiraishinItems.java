package com.noirm.hiraishin;

import com.noirm.Noirm;
import com.noirm.weapon.Moveset;
import com.noirm.weapon.ThrowStats;
import com.noirm.weapon.ThrowingWeaponItem;
import net.minecraft.ChatFormatting;
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
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * The Flying Raijin's tools: the three-pronged marked kunai and the Space-Time Formula scroll.
 * Textures come from {@code tools/weapongen/gen_weapons.py} (our own designs).
 */
public final class HiraishinItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Noirm.MOD_ID);

    /** Flies a little truer than a kunai and carries the thrower's formula (if they know it). */
    public static final ThrowStats KUNAI_STATS = new ThrowStats(5f, 2.3f, 0.032f, 6, 0f, false, 0, 0, 0, ThrowStats.THROW_KUNAI, 0.85f);

    public static final RegistryObject<HiraishinKunaiItem> KUNAI = ITEMS.register("hiraishin_kunai",
            () -> new HiraishinKunaiItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<FormulaScrollItem> FORMULA_SCROLL = ITEMS.register("space_time_scroll",
            () -> new FormulaScrollItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    private HiraishinItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(HiraishinItems::creativeTabs);
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(KUNAI);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(FORMULA_SCROLL);
        }
    }

    /** The three-pronged kunai with the formula wrapped round its grip. A dagger too (the kunai's moveset). */
    public static final class HiraishinKunaiItem extends ThrowingWeaponItem {
        public HiraishinKunaiItem(Properties properties) {
            super(properties, KUNAI_STATS, 3.5f, -2.0f, Moveset.KUNAI, false);
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, level, tooltip, flag);
            tooltip.add(Component.translatable("tooltip.noirm.hiraishin_kunai").withStyle(ChatFormatting.GOLD));
        }
    }

    /** Reading it teaches the Space-Time Formula, if your chakra control and reserves can hold it. */
    public static final class FormulaScrollItem extends Item {
        public FormulaScrollItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide()) {
                return InteractionResultHolder.success(stack);
            }
            if (HiraishinLearning.knowsFormula(player)) {
                player.displayClientMessage(Component.translatable("message.noirm.hiraishin.formula_known"), true);
                return InteractionResultHolder.fail(stack);
            }
            Component blocker = HiraishinLearning.formulaBlocker(player);
            if (blocker != null) {
                player.displayClientMessage(blocker, false);
                return InteractionResultHolder.fail(stack);
            }
            HiraishinLearning.teachFormula(player);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return InteractionResultHolder.consume(stack);
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.noirm.space_time_scroll", Math.round(HiraishinLearning.FORMULA_CTL),
                    Math.round(HiraishinLearning.FORMULA_RESERVES)).withStyle(ChatFormatting.GRAY));
        }
    }
}
