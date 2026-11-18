package com.noirm.traits;

import com.noirm.clan.Clan;
import com.noirm.clan.WorldClans;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.training.TrainingSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Everything a character's blood and talents add up to: the clan's perks plus each talent's
 * modifiers, summed per {@link Perk}. Players read theirs from {@code ShinobiData}; NPC ninjas from
 * their clan and the talents saved on them ({@link NpcTalents}). Cached until something changes.
 */
public final class Perks {
    /** No multiplier goes below this (a pile of −% can't make something free or useless). */
    public static final float MIN_MULTIPLIER = 0.1f;
    /** Dodges through a liquid or smoke body never pass this chance. */
    public static final float MAX_DODGE = 0.8f;
    /** Late Bloomer: XP below this stat value ×(1 − {@link #BLOOMER_SLOW}), at or above it ×(1 + {@link #BLOOMER_FAST}). */
    public static final float BLOOMER_STAT = 30f;
    public static final float BLOOMER_SLOW = 0.25f;
    public static final float BLOOMER_FAST = 0.6f;
    /** NPC totals are rebuilt at most this often (ticks). */
    private static final int NPC_CACHE_TICKS = 40;

    /** Behaviours whose jutsu count as genjutsu, and medical techniques. */
    public static final Set<String> GENJUTSU_BEHAVIORS = Set.of("genjutsu", "mirage", "tree_bind", "tsukuyomi");
    public static final Set<String> MEDICAL_BEHAVIORS = Set.of("heal", "medic_palm", "healing_field", "hundred_healings", "regen_burst");

    private static final Map<LivingEntity, NpcCache> NPC = new WeakHashMap<>();

    private record NpcCache(int tick, EnumMap<Perk, Float> totals) {}

    private Perks() {}

    // ---------------------------------------------------------------- totals

    /** Sums clan perks and talents' modifiers. */
    public static EnumMap<Perk, Float> resolve(Map<String, Float> clanPerks, List<String> talents) {
        EnumMap<Perk, Float> totals = new EnumMap<>(Perk.class);
        add(totals, clanPerks);
        for (String id : talents) {
            Talent talent = Talents.get(id);
            if (talent != null) {
                add(totals, talent.modifiers());
            }
        }
        return totals;
    }

    private static void add(EnumMap<Perk, Float> totals, Map<String, Float> modifiers) {
        modifiers.forEach((key, amount) -> {
            Perk perk = Perk.byKey(key);
            if (perk != null) {
                totals.merge(perk, amount, perk.kind() == Perk.Kind.FLAG ? Math::max : Float::sum);
            }
        });
    }

    /** A player's totals. */
    public static EnumMap<Perk, Float> totals(ShinobiData data) {
        TalentData talents = data.talents();
        long key = ((long) data.identity().version() << 40) ^ ((long) talents.changes() << 16) ^ Talents.version();
        EnumMap<Perk, Float> cached = talents.cached(key);
        if (cached == null) {
            cached = resolve(data.identity().perks(), talents.talents());
            talents.cache(key, cached);
        }
        return cached;
    }

    /** Any living thing's totals: players, NPC ninjas, nothing for everything else. */
    public static Map<Perk, Float> totals(LivingEntity entity) {
        if (entity instanceof Player player) {
            ShinobiData data = ShinobiCapability.get(player).orElse(null);
            return data == null ? Map.of() : totals(data);
        }
        if (entity instanceof SparringNinja ninja && !ninja.level().isClientSide()) {
            NpcCache cache = NPC.get(ninja);
            if (cache == null || ninja.tickCount - cache.tick >= NPC_CACHE_TICKS || ninja.tickCount < cache.tick) {
                cache = new NpcCache(ninja.tickCount, resolve(clanPerks(ninja), NpcTalents.of(ninja)));
                NPC.put(ninja, cache);
            }
            return cache.totals;
        }
        return Map.of();
    }

    /** Rebuilds an NPC's totals next time (its clan or talents changed). */
    public static void forget(LivingEntity entity) {
        NPC.remove(entity);
    }

    private static Map<String, Float> clanPerks(SparringNinja ninja) {
        if (ninja.clanId().isEmpty() || ninja.getServer() == null) {
            return Map.of();
        }
        return WorldClans.get(ninja.getServer()).find(ninja.clanId()).map(Clan::perks).orElse(Map.of());
    }

    public static float get(ShinobiData data, Perk perk) {
        return totals(data).getOrDefault(perk, 0f);
    }

    public static float get(LivingEntity entity, Perk perk) {
        return totals(entity).getOrDefault(perk, 0f);
    }

    public static boolean has(LivingEntity entity, Perk perk) {
        return get(entity, perk) > 0f;
    }

    /** 1 + the amount, for PERCENT perks (never below {@link #MIN_MULTIPLIER}). */
    public static float mult(ShinobiData data, Perk perk) {
        return Math.max(MIN_MULTIPLIER, 1f + get(data, perk));
    }

    public static float mult(LivingEntity entity, Perk perk) {
        return Math.max(MIN_MULTIPLIER, 1f + get(entity, perk));
    }

    // ---------------------------------------------------------------- the rules other systems ask for

    /** Jutsu with sealing in their bones: clan sealing jutsu and anything with "seal" in its id. */
    public static boolean isSealing(ResourceLocation id) {
        return id != null && (id.getPath().contains("seal") || ClanJutsu.SEALING.contains(id));
    }

    /** Everything that changes a jutsu's power: all jutsu, genjutsu, medical, sealing. */
    public static float jutsuPowerMultiplier(LivingEntity caster, ResourceLocation id, JutsuDefinition def) {
        Map<Perk, Float> t = totals(caster);
        if (t.isEmpty()) {
            return 1f;
        }
        float bonus = t.getOrDefault(Perk.JUTSU_POWER, 0f);
        if (GENJUTSU_BEHAVIORS.contains(def.behavior())) {
            bonus += t.getOrDefault(Perk.GENJUTSU_POWER, 0f);
        }
        if (id.getPath().startsWith("medical/") || MEDICAL_BEHAVIORS.contains(def.behavior())) {
            bonus += t.getOrDefault(Perk.MEDICAL_POWER, 0f);
        }
        if (isSealing(id)) {
            bonus += t.getOrDefault(Perk.SEALING_POWER, 0f);
        }
        return Math.max(MIN_MULTIPLIER, 1f + bonus);
    }

    /** Everything that changes a jutsu's chakra cost. */
    public static float jutsuCostMultiplier(LivingEntity caster, ResourceLocation id) {
        Map<Perk, Float> t = totals(caster);
        if (t.isEmpty()) {
            return 1f;
        }
        float bonus = t.getOrDefault(Perk.JUTSU_COST, 0f) + (isSealing(id) ? t.getOrDefault(Perk.SEALING_COST, 0f) : 0f);
        return Math.max(MIN_MULTIPLIER, 1f + bonus);
    }

    /** XP multiplier for a trained stat: all stats, that stat, Late Bloomer. */
    public static float statXpMultiplier(ShinobiData data, String stat, float value) {
        EnumMap<Perk, Float> t = totals(data);
        if (t.isEmpty()) {
            return 1f;
        }
        Perk own = switch (stat) {
            case "taijutsu" -> Perk.XP_TAIJUTSU;
            case "speed" -> Perk.XP_SPEED;
            case "vitality" -> Perk.XP_VITALITY;
            case "reserves" -> Perk.XP_RESERVES;
            case "durability" -> Perk.XP_DURABILITY;
            case "control" -> Perk.XP_CONTROL;
            case "willpower" -> Perk.XP_WILLPOWER;
            default -> null;
        };
        float bonus = t.getOrDefault(Perk.STAT_XP, 0f) + (own == null ? 0f : t.getOrDefault(own, 0f));
        if (t.getOrDefault(Perk.LATE_BLOOMER, 0f) > 0f) {
            bonus += value < BLOOMER_STAT ? -BLOOMER_SLOW : BLOOMER_FAST;
        }
        return Math.max(MIN_MULTIPLIER, 1f + bonus);
    }

    public static float statXpMultiplier(ShinobiData data, TrainingSystem.Stat stat) {
        return statXpMultiplier(data, stat.key().toLowerCase(java.util.Locale.ROOT), TrainingSystem.stat(data, stat));
    }

    /** Less chance to be caught in a genjutsu: × this (0.2..1). */
    public static float genjutsuTakeMultiplier(LivingEntity target) {
        return Math.max(0.2f, 1f - get(target, Perk.GENJUTSU_RESIST));
    }

    /** The starting-stat bonus of some perks and talents (creation). */
    public static float startBonus(Map<String, Float> clanPerks, List<String> talents, Perk perk) {
        return resolve(clanPerks, talents).getOrDefault(perk, 0f);
    }

    // ---------------------------------------------------------------- describing

    /** One line per perk, "Chakra pool +35%", green when it helps and red when it hurts. */
    public static List<Component> lines(Map<String, Float> modifiers) {
        List<Component> out = new ArrayList<>();
        for (Perk perk : Perk.values()) {
            Float amount = modifiers.get(perk.key());
            if (amount == null || amount == 0f) {
                continue;
            }
            out.add(line(perk, amount));
        }
        return out;
    }

    /** All perks on one line, comma-separated. */
    public static MutableComponent inline(Map<String, Float> modifiers) {
        MutableComponent text = Component.empty();
        List<Component> lines = lines(modifiers);
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                text.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            text.append(lines.get(i));
        }
        return text;
    }

    /** Jutsu names, comma-separated. */
    public static MutableComponent jutsuNames(List<ResourceLocation> ids) {
        MutableComponent text = Component.empty();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(Component.translatable("jutsu." + ids.get(i).getNamespace() + "." + ids.get(i).getPath().replace('/', '.'))
                    .withStyle(ChatFormatting.GOLD));
        }
        return text;
    }

    public static MutableComponent line(Perk perk, float amount) {
        return Component.translatable(perk.translationKey(), perk.format(amount))
                .withStyle(perk.good(amount) ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    /** "Deep Reserves, Prodigy, Cursed Luck" coloured by kind, with each description on hover. */
    public static MutableComponent talentNames(List<String> ids) {
        MutableComponent text = Component.empty();
        boolean first = true;
        for (String id : ids) {
            if (!first) {
                text.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            Talent t = Talents.get(id);
            int color = t == null ? 0xAAAAAA : t.kind().color();
            MutableComponent hover = Component.translatable(Talent.descriptionKey(id));
            if (t != null) {
                for (Component l : lines(t.modifiers())) {
                    hover.append("\n").append(l);
                }
            }
            text.append(Component.translatable(Talent.translationKey(id)).withStyle(s -> s.withColor(color)
                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, hover))));
            first = false;
        }
        if (first) {
            text.append(Component.translatable("talent.noirm.none").withStyle(ChatFormatting.GRAY));
        }
        return text;
    }
}
