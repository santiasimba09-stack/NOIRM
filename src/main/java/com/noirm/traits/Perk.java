package com.noirm.traits;

import java.util.Locale;

/**
 * One thing a clan's blood or a personal talent changes. Clans ({@code data/noirm/clans/*.json},
 * {@code "perks"}), perk packages for generated clans ({@code data/noirm/clan_perks/}) and talents
 * ({@code data/noirm/traits/}) all name these by key with an amount; a player's amounts are summed
 * ({@link Perks}).
 *
 * <p>Kinds: {@link Kind#FLAT} adds the amount (points, blocks, HP per second); {@link Kind#PERCENT}
 * is a fraction added to 100% ({@code 0.35} = +35%, {@code -0.2} = −20%); {@link Kind#CHANCE} is a
 * chance 0–1; {@link Kind#FLAG} is on when above 0. Where each one is used is noted on it.
 */
public enum Perk {
    /** Max chakra, flat (TrainingSystem.applyReserves). */
    MAX_CHAKRA(Kind.FLAT, false),
    /** The whole chakra pool, ×(1 + amount) (TrainingSystem.applyReserves). */
    CHAKRA_POOL(Kind.PERCENT, false),
    /** Chakra regeneration (ShinobiData.chakraRegenMultiplier). */
    CHAKRA_REGEN(Kind.PERCENT, false),
    /** Max stamina, flat (PerkEvents). */
    MAX_STAMINA(Kind.FLAT, false),
    /** Stamina regeneration (ShinobiData.staminaRegenMultiplier). */
    STAMINA_REGEN(Kind.PERCENT, false),
    /** Max health, flat (PerkEvents: an attribute modifier). */
    MAX_HEALTH(Kind.FLAT, false),
    /** Health regained every second while not starving, on top of vanilla's (PerkEvents). */
    HEALTH_REGEN(Kind.FLAT, false),
    /** Max posture, flat (PowerEvents). */
    POSTURE(Kind.FLAT, false),
    /** Perception (Perception.ocularMultiplier). */
    PERCEPTION(Kind.PERCENT, false),
    /** Senses chakra within this many blocks: a radar while crouching (client SensorRadar). */
    SENSOR(Kind.FLAT, false),
    /** Chakra cost of every jutsu (JutsuCaster.cost). */
    JUTSU_COST(Kind.PERCENT, true),
    /** Power of every jutsu (JutsuCaster.power). */
    JUTSU_POWER(Kind.PERCENT, false),
    /** Power of genjutsu (genjutsu, mirage, tree bind, Tsukuyomi). */
    GENJUTSU_POWER(Kind.PERCENT, false),
    /** Power of medical ninjutsu ({@code jutsu/medical/}, heals). */
    MEDICAL_POWER(Kind.PERCENT, false),
    /** Sealing jutsu power, sealing a beast into someone (bond), capturing hurt beasts (Perks.isSealing). */
    SEALING_POWER(Kind.PERCENT, false),
    /** Chakra cost of sealing jutsu. */
    SEALING_COST(Kind.PERCENT, true),
    /** Jutsu mastery XP per cast (JutsuCaster.afterCast). */
    MASTERY(Kind.PERCENT, false),
    /** XP for every trained stat (TrainingSystem.grant, Supplementary.grant). */
    STAT_XP(Kind.PERCENT, false),
    XP_TAIJUTSU(Kind.PERCENT, false),
    XP_SPEED(Kind.PERCENT, false),
    XP_VITALITY(Kind.PERCENT, false),
    XP_RESERVES(Kind.PERCENT, false),
    XP_DURABILITY(Kind.PERCENT, false),
    XP_CONTROL(Kind.PERCENT, false),
    XP_WILLPOWER(Kind.PERCENT, false),
    /** Starting stats, added once when the character is made (CharacterCreator.finalStats, ClanSystem.assign). */
    START_SPEED(Kind.FLAT, false),
    START_TAIJUTSU(Kind.FLAT, false),
    START_RESERVES(Kind.FLAT, false),
    START_CONTROL(Kind.FLAT, false),
    START_WILLPOWER(Kind.FLAT, false),
    START_VITALITY(Kind.FLAT, false),
    START_DURABILITY(Kind.FLAT, false),
    /** Melee damage dealt (PerkEvents). */
    MELEE_DAMAGE(Kind.PERCENT, false),
    /** Every kind of damage taken (PerkEvents). */
    DAMAGE_TAKEN(Kind.PERCENT, true),
    /** Less damage from poison, wither, burning and bleeding (PerkEvents). */
    DOT_RESIST(Kind.PERCENT, false),
    /** More damage from fire (insect hosts). */
    FIRE_WEAKNESS(Kind.PERCENT, true),
    /** Poison and wither don't take hold (PerkEvents). */
    POISON_IMMUNITY(Kind.FLAG, false),
    /** In or near water or rain, a physical hit can wash through a liquid body (PerkEvents). */
    WATER_DODGE(Kind.CHANCE, false),
    /** Anywhere, a physical hit can pass through a smoke body (PerkEvents). */
    PHASE_DODGE(Kind.CHANCE, false),
    /** Each melee hit on a chakra user drains this much of their chakra; you get half (PerkEvents). */
    CHAKRA_LEECH(Kind.FLAT, false),
    /** Less chance to be caught in a genjutsu (SpiritTechniques.genjutsuChance). */
    GENJUTSU_RESIST(Kind.PERCENT, false),
    /** Mangekyō strain gained (Mangekyo.addStrain). */
    STRAIN(Kind.PERCENT, true),
    /** Toxicity a pill adds (PillEffects.take). */
    PILL_TOXICITY(Kind.PERCENT, true),
    /** Strength of pills (PillEffects.take). */
    PILL_POWER(Kind.PERCENT, false),
    /** Strength of foods (PillEffects.eat). */
    FOOD_POWER(Kind.PERCENT, false),
    /** Pharmacology XP (Pharmacology.grant). */
    PHARMACOLOGY_XP(Kind.PERCENT, false),
    /** Bond gained with a sealed tailed beast (BeastBond.changeBond). */
    BEAST_BOND(Kind.PERCENT, false),
    /** Gentle Fist: chakra drained and points closed per hit (Byakugan.gentleFist). */
    GENTLE_FIST(Kind.PERCENT, false),
    /** Affinity added to a combined nature when it's unlocked (NatureSystem.grant). */
    COMBO_AFFINITY(Kind.FLAT, false),
    /** Resolve built in fights (AwakeningSystem.addResolve). */
    RESOLVE(Kind.PERCENT, false),
    /** Respect crew mates give you (AllyMind.respect). */
    CREW_RESPECT(Kind.PERCENT, false),
    /** Born with a ninja hound at your side (PerkEvents). */
    HOUND(Kind.FLAG, false),
    /** Slow below 30 in a stat (−25% XP), fast from 30 on (+60%) (TrainingSystem.grant). */
    LATE_BLOOMER(Kind.FLAG, false);

    public enum Kind { FLAT, PERCENT, CHANCE, FLAG }

    private final Kind kind;
    private final boolean lowerIsBetter;
    private final String key;

    Perk(Kind kind, boolean lowerIsBetter) {
        this.kind = kind;
        this.lowerIsBetter = lowerIsBetter;
        this.key = name().toLowerCase(Locale.ROOT);
    }

    public Kind kind() {
        return kind;
    }

    /** The JSON key, e.g. {@code "max_chakra"}. */
    public String key() {
        return key;
    }

    public String translationKey() {
        return "perk.noirm." + key;
    }

    /** Whether this amount helps (green) or hurts (red). */
    public boolean good(float amount) {
        return lowerIsBetter ? amount < 0 : amount > 0;
    }

    /** A perk by JSON key, or null. */
    public static Perk byKey(String key) {
        for (Perk perk : values()) {
            if (perk.key.equals(key)) {
                return perk;
            }
        }
        return null;
    }

    /** The amount as players read it: "+60", "+35%", "60%", "" (flags). */
    public String format(float amount) {
        return switch (kind) {
            case FLAT -> (amount >= 0 ? "+" : "") + trim(amount);
            case PERCENT -> (amount >= 0 ? "+" : "") + Math.round(amount * 100) + "%";
            case CHANCE -> Math.round(amount * 100) + "%";
            case FLAG -> "";
        };
    }

    private static String trim(float amount) {
        return Math.abs(amount - Math.round(amount)) < 0.001f ? Integer.toString(Math.round(amount))
                : String.format(Locale.ROOT, "%.2f", amount).replaceAll("0+$", "");
    }
}
