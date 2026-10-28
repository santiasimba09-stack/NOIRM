package com.noirm.nature;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.noirm.Noirm;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.function.Supplier;

/**
 * Adds chakra paper, jutsu scrolls and rare nature scrolls to chests in ruins, camps, temples and
 * dungeons. Data: {@code data/noirm/loot_modifiers/nature_loot.json} lists the loot tables and, per
 * entry, the items (one is picked), the chance and the count.
 */
public final class NatureLoot extends LootModifier {
    private static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Noirm.MOD_ID);

    /** One roll: one of {@code items}, with {@code chance}, {@code min}–{@code max} of it. */
    public record Entry(List<ResourceLocation> items, float chance, int min, int max) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.listOf().fieldOf("items").forGetter(Entry::items),
                Codec.floatRange(0, 1).fieldOf("chance").forGetter(Entry::chance),
                Codec.intRange(1, 64).optionalFieldOf("min", 1).forGetter(Entry::min),
                Codec.intRange(1, 64).optionalFieldOf("max", 1).forGetter(Entry::max)
        ).apply(i, Entry::new));
    }

    public static final Supplier<Codec<NatureLoot>> CODEC = Suppliers.memoize(() -> RecordCodecBuilder.create(inst -> codecStart(inst).and(inst.group(
            ResourceLocation.CODEC.listOf().fieldOf("tables").forGetter(m -> m.tables),
            Entry.CODEC.listOf().fieldOf("entries").forGetter(m -> m.entries)
    )).apply(inst, NatureLoot::new)));

    public static final RegistryObject<Codec<NatureLoot>> NATURE_LOOT = SERIALIZERS.register("nature_loot", CODEC);

    private final List<ResourceLocation> tables;
    private final List<Entry> entries;

    public NatureLoot(LootItemCondition[] conditions, List<ResourceLocation> tables, List<Entry> entries) {
        super(conditions);
        this.tables = tables;
        this.entries = entries;
    }

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (!tables.contains(context.getQueriedLootTableId())) {
            return loot;
        }
        for (Entry entry : entries) {
            if (entry.items().isEmpty() || context.getRandom().nextFloat() >= entry.chance()) {
                continue;
            }
            Item item = ForgeRegistries.ITEMS.getValue(entry.items().get(context.getRandom().nextInt(entry.items().size())));
            if (item != null) {
                int count = entry.min() + (entry.max() > entry.min() ? context.getRandom().nextInt(entry.max() - entry.min() + 1) : 0);
                loot.add(new ItemStack(item, count));
            }
        }
        return loot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
