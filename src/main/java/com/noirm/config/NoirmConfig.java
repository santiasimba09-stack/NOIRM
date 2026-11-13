package com.noirm.config;

import com.noirm.Noirm;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The survival tuning file, {@code config/noirm-common.toml}. Every default is today's behaviour, so
 * a fresh file plays exactly like the mod did before it existed. Forge re-reads the file when it
 * changes, even while the game runs; {@code /noirm config} shows the values in use.
 *
 * <p><b>Config and gamerules.</b> The file is for the whole install (every world); the gamerules are
 * per world. Where both exist, both must agree:
 * <ul>
 *   <li>Terrain: jutsu break terrain only if {@code world.terrainDestruction} is on <b>and</b> the
 *       world's {@code noirmTerrainDamage} rule is true. Regrowth takes {@code noirmTerrainRegen}
 *       seconds × {@code world.terrainRegenScale} (a rule of 0 still means "never grows back").</li>
 *   <li>Gore: only if {@code gore.enabled} <b>and</b> the {@code noirmGore} rule are on; each
 *       player's own {@code goreLevel} (client file) can still turn it down on their screen.</li>
 *   <li>Wild ninjas: the {@code noirmWildNinjas} rule (percent) × {@code wildNinjas.ratePercent} / 100.</li>
 * </ul>
 *
 * <p>The values are kept in plain fields (read for free by hot code) and refreshed when Forge loads
 * the file. Tests change them with {@link Value#override} and put them back with {@link Value#reset},
 * which never touches the file.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NoirmConfig {
    /** One option: its file entry, its default and the value in use. */
    public static final class Value<T> {
        private final String path;
        private final T fallback;
        private ForgeConfigSpec.ConfigValue<T> spec;
        private volatile T current;
        private volatile T override;

        Value(String path, T fallback) {
            this.path = path;
            this.fallback = fallback;
            this.current = fallback;
        }

        /** The value in use: a test's override, else the file's, else the default. */
        public T get() {
            T o = override;
            return o != null ? o : current;
        }

        /** The default (today's behaviour). */
        public T defaultValue() {
            return fallback;
        }

        /** "difficulty.npcDamage". */
        public String path() {
            return path;
        }

        /** For tests: use {@code value} until {@link #reset}. The file is not changed. */
        public void override(T value) {
            override = value;
        }

        public void reset() {
            override = null;
        }

        void read() {
            if (spec != null) {
                current = spec.get();
            }
        }
    }

    private static final List<Value<?>> ALL = new ArrayList<>();

    // ---------------------------------------------------------------- difficulty
    public static final Value<Double> NPC_DAMAGE = value("difficulty.npcDamage", 1.0);
    public static final Value<Double> NPC_HEALTH = value("difficulty.npcHealth", 1.0);
    public static final Value<Double> CHAKRA_COST = value("difficulty.chakraCost", 1.0);
    // ---------------------------------------------------------------- wild ninjas
    public static final Value<Integer> WILD_RATE = value("wildNinjas.ratePercent", 100);
    public static final Value<Integer> WILD_MAX_GROUPS = value("wildNinjas.maxGroupsNear", 2);
    public static final Value<Integer> WILD_MAX_NINJAS = value("wildNinjas.maxNinjasNear", 6);
    public static final Value<Integer> WILD_SAFE_MINUTES = value("wildNinjas.safeStartMinutes", 20);
    // ---------------------------------------------------------------- legends
    public static final Value<Integer> LEGEND_COUNT = value("legends.perWorld", 4);
    public static final Value<Integer> LEGEND_SAFE_RADIUS = value("legends.safeRadius", 1000);
    public static final Value<Integer> LEGEND_GRACE_MINUTES = value("legends.graceMinutes", 120);
    // ---------------------------------------------------------------- world
    public static final Value<Boolean> TERRAIN_DESTRUCTION = value("world.terrainDestruction", true);
    public static final Value<Double> TERRAIN_REGEN_SCALE = value("world.terrainRegenScale", 1.0);
    // ---------------------------------------------------------------- gore
    public static final Value<Boolean> GORE_ENABLED = value("gore.enabled", true);
    public static final Value<Double> GORE_INTENSITY = value("gore.intensity", 1.0);
    // ---------------------------------------------------------------- progression
    public static final Value<Double> TRAINING_SPEED = value("progression.trainingSpeed", 1.0);
    public static final Value<Double> DRILL_TIME = value("progression.drillTime", 1.0);
    public static final Value<Double> RYO_REWARDS = value("progression.ryoRewards", 1.0);
    // ---------------------------------------------------------------- medicine (foods and pills)
    public static final Value<Double> PILL_STRENGTH = value("medicine.pillStrength", 1.0);

    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Difficulty. 1.0 = normal.").push("difficulty");
        NPC_DAMAGE.spec = b.comment("How hard ninjas and tailed beasts hit you (players only; crew mates are unaffected).")
                .defineInRange("npcDamage", 1.0, 0.1, 10.0);
        NPC_HEALTH.spec = b.comment("Health of enemy ninjas (not your crew) and tailed beasts.")
                .defineInRange("npcHealth", 1.0, 0.1, 10.0);
        CHAKRA_COST.spec = b.comment("Chakra cost of the jutsu players cast (NPCs pay the normal price).")
                .defineInRange("chakraCost", 1.0, 0.1, 10.0);
        b.pop();

        b.comment("Wild ninjas: groups that turn up around you in the wild.").push("wildNinjas");
        WILD_RATE.spec = b.comment("How often groups turn up, in percent of normal (0 = never). Multiplied with the world's noirmWildNinjas gamerule.")
                .defineInRange("ratePercent", 100, 0, 1000);
        WILD_MAX_GROUPS.spec = b.comment("At most this many wild groups within 128 blocks of a player.")
                .defineInRange("maxGroupsNear", 2, 0, 16);
        WILD_MAX_NINJAS.spec = b.comment("At most this many wild ninjas within 128 blocks of a player.")
                .defineInRange("maxNinjasNear", 6, 0, 64);
        WILD_SAFE_MINUTES.spec = b.comment("Near the world spawn, nothing turns up until a player has played this many minutes.")
                .defineInRange("safeStartMinutes", 20, 0, 10000);
        b.pop();

        b.comment("Legendary ninjas from the bingo book.").push("legends");
        LEGEND_COUNT.spec = b.comment("How many legends a new world's bingo book lists (read when the book is first filled).")
                .defineInRange("perWorld", 4, 0, 6);
        LEGEND_SAFE_RADIUS.spec = b.comment("Legends are never seen closer than this many blocks to the world spawn.")
                .defineInRange("safeRadius", 1000, 0, 100000);
        LEGEND_GRACE_MINUTES.spec = b.comment("Legends never come for a player in their first this-many minutes of play.")
                .defineInRange("graceMinutes", 120, 0, 100000);
        b.pop();

        b.comment("Terrain. The world's gamerules still apply: destruction needs both this and noirmTerrainDamage.").push("world");
        TERRAIN_DESTRUCTION.spec = b.comment("Whether jutsu and big hits break terrain at all.")
                .define("terrainDestruction", true);
        TERRAIN_REGEN_SCALE.spec = b.comment("Regrowth time = the noirmTerrainRegen gamerule (seconds) × this. 0.5 = twice as fast.")
                .defineInRange("terrainRegenScale", 1.0, 0.05, 100.0);
        b.pop();

        b.comment("Gore. Needs both this and the noirmGore gamerule; each player's goreLevel (noirm-client.toml) can turn it down further.").push("gore");
        GORE_ENABLED.spec = b.comment("Wounds, blood and severed limbs at all.")
                .define("enabled", true);
        GORE_INTENSITY.spec = b.comment("How fast wounds build up and how often a killing blow severs a limb (1.0 = normal).")
                .defineInRange("intensity", 1.0, 0.0, 3.0);
        b.pop();

        b.comment("Progression speed. 1.0 = normal.").push("progression");
        TRAINING_SPEED.spec = b.comment("Stat XP from training and fights (2.0 = stats grow twice as fast).")
                .defineInRange("trainingSpeed", 1.0, 0.1, 10.0);
        DRILL_TIME.spec = b.comment("How long a nature drill takes (0.5 = half as long).")
                .defineInRange("drillTime", 1.0, 0.05, 10.0);
        RYO_REWARDS.spec = b.comment("Ryo paid by missions and bounties.")
                .defineInRange("ryoRewards", 1.0, 0.0, 10.0);
        b.pop();

        b.comment("Medicine: pills from the apothecary, shops and loot (docs/PILLS.md).").push("medicine");
        PILL_STRENGTH.spec = b.comment("How strong pills are: their boosts and refills (1.0 = normal). Crashes and toxicity are not scaled.")
                .defineInRange("pillStrength", 1.0, 0.1, 5.0);
        b.pop();
        SPEC = b.build();
    }

    private NoirmConfig() {}

    private static <T> Value<T> value(String path, T fallback) {
        Value<T> v = new Value<>(path, fallback);
        ALL.add(v);
        return v;
    }

    /** Called once while the mod loads. */
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "noirm-common.toml");
    }

    /** Every option, in file order. */
    public static List<Value<?>> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** Clears every test override. */
    public static void resetAll() {
        ALL.forEach(Value::reset);
    }

    // ---------------------------------------------------------------- helpers for the game code

    /** {@code amount} ryo after the reward multiplier (missions, bounties). */
    public static int ryoReward(int amount) {
        return (int) Math.round(amount * RYO_REWARDS.get());
    }

    public static float f(Value<Double> value) {
        return value.get().floatValue();
    }

    @SubscribeEvent
    public static void loaded(ModConfigEvent.Loading event) {
        read(event.getConfig());
    }

    @SubscribeEvent
    public static void reloaded(ModConfigEvent.Reloading event) {
        read(event.getConfig());
    }

    private static void read(ModConfig config) {
        if (config.getSpec() == SPEC) {
            ALL.forEach(Value::read);
        }
    }
}
