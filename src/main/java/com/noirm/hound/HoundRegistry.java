package com.noirm.hound;

import com.noirm.Noirm;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The ninja hound entity and its two items: the summoning contract and the hound ration pill. */
public final class HoundRegistry {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Noirm.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Noirm.MOD_ID);

    /** The size here is the puppy's; each stage sets its own ({@link NinjaHound#getDimensions}). */
    public static final RegistryObject<EntityType<NinjaHound>> NINJA_HOUND = ENTITIES.register("ninja_hound",
            () -> EntityType.Builder.<NinjaHound>of(NinjaHound::new, MobCategory.CREATURE)
                    .sized(0.45f, 0.5f)
                    .clientTrackingRange(10)
                    .build(Noirm.id("ninja_hound").toString()));

    /** A ninken summoning contract (the Hatake's start): sign it for a puppy, then summon or dismiss your hound. */
    public static final RegistryObject<Item> NINKEN_CONTRACT = ITEMS.register("ninken_contract",
            () -> new NinkenContractItem(new Item.Properties().stacksTo(1)));
    /** A hound ration pill: a frenzy, a little bond and XP for your hound (its fur turns red for a while). */
    public static final RegistryObject<Item> HOUND_PILL = ITEMS.register("hound_pill",
            () -> new Item(new Item.Properties().stacksTo(16)) {
                @Override
                public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.level.Level level,
                                            java.util.List<net.minecraft.network.chat.Component> tips, net.minecraft.world.item.TooltipFlag flag) {
                    tips.add(net.minecraft.network.chat.Component.translatable("item.noirm.hound_pill.desc")
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
                }
            });

    private HoundRegistry() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(HoundRegistry::attributes);
        modBus.addListener(HoundRegistry::creativeTabs);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(NINJA_HOUND.get(), NinjaHound.createAttributes().build());
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(NINKEN_CONTRACT);
        } else if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(HOUND_PILL);
        }
    }
}
