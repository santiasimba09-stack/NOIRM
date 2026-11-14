package com.noirm.pills;

import com.noirm.Noirm;
import com.noirm.data.CodecJsonLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every pill ({@code data/<ns>/pills/}) and food ({@code data/<ns>/foods/}) definition, loaded when a
 * world starts and on {@code /reload}. Looked up by the item they belong to.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class PillRegistry {
    private static Map<ResourceLocation, PillDefinition> pills = Map.of();
    private static Map<ResourceLocation, PillDefinition> foods = Map.of();
    private static Map<Item, PillDefinition> pillByItem = Map.of();
    private static Map<Item, PillDefinition> foodByItem = Map.of();
    private static Map<Item, ResourceLocation> pillIdByItem = Map.of();

    private PillRegistry() {}

    public static Map<ResourceLocation, PillDefinition> pills() {
        return pills;
    }

    public static Map<ResourceLocation, PillDefinition> foods() {
        return foods;
    }

    public static PillDefinition pill(ResourceLocation id) {
        return id == null ? null : pills.get(id);
    }

    public static PillDefinition pill(Item item) {
        return pillByItem.get(item);
    }

    /** The pill definition's id for an item (null if it isn't a pill). */
    public static ResourceLocation pillId(Item item) {
        return pillIdByItem.get(item);
    }

    public static PillDefinition food(Item item) {
        return foodByItem.get(item);
    }

    /** Pills with a recipe, in a stable order: easiest first. */
    public static List<Map.Entry<ResourceLocation, PillDefinition>> recipes() {
        return pills.entrySet().stream().filter(e -> e.getValue().recipe().isPresent())
                .sorted(Comparator.<Map.Entry<ResourceLocation, PillDefinition>>comparingDouble(
                                e -> e.getValue().recipe().get().minPharmacology())
                        .thenComparingDouble(e -> e.getValue().recipe().get().difficulty())
                        .thenComparing(e -> e.getKey().toString()))
                .toList();
    }

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CodecJsonLoader<>("pills", PillDefinition.CODEC, loaded -> {
            pills = loaded;
            Map<Item, PillDefinition> byItem = new HashMap<>();
            Map<Item, ResourceLocation> ids = new HashMap<>();
            loaded.forEach((id, def) -> {
                Item item = ForgeRegistries.ITEMS.getValue(def.item());
                if (item != null) {
                    byItem.put(item, def);
                    ids.put(item, id);
                }
            });
            pillByItem = Map.copyOf(byItem);
            pillIdByItem = Map.copyOf(ids);
        }));
        event.addListener(new CodecJsonLoader<>("foods", PillDefinition.CODEC, loaded -> {
            foods = loaded;
            Map<Item, PillDefinition> byItem = new HashMap<>();
            loaded.forEach((id, def) -> {
                Item item = ForgeRegistries.ITEMS.getValue(def.item());
                if (item != null) {
                    byItem.put(item, def);
                }
            });
            foodByItem = Map.copyOf(byItem);
        }));
    }
}
