package com.noirm.pills;

import com.noirm.Noirm;
import com.noirm.combat.CombatState;
import com.noirm.combat.CombatStates;
import com.noirm.config.NoirmConfig;
import com.noirm.network.NoirmNetwork;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.progress.PillTrigger;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import java.util.Iterator;
import java.util.List;

/**
 * What foods and pills do to a player (the server side; see {@code docs/PILLS.md}).
 *
 * <p><b>A pill</b> works at once ({@link PillDefinition.Instant}: chakra, stamina, health, a cure),
 * then its effects run for their seconds, then its <b>crash</b> starts (slowness, weakness, slower
 * regen...). How strong it is: its {@link Grade}'s potency × the {@code medicine.pillStrength}
 * config × how clean your body is: every pill adds <b>toxicity</b>, and a pill taken on top of
 * others works at {@code 1 − toxicity × }{@value #TOLERANCE_WEAKEN}{@code /100} of its strength.
 * Past {@value #NAUSEA_AT}, {@value #SICK_AT} and {@value #OVERDOSE_AT} toxicity you get sick.
 * Toxicity wears off by {@value #TOXICITY_DECAY_PER_SECOND} a second. Every pill also has its own
 * cooldown.
 *
 * <p><b>A food</b> has no toxicity, cooldown or crash: eating it again refreshes its effects.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class PillEffects {
    // ---------------------------------------------------------------- tuning
    /** Toxicity lost per second (6 a minute: a soldier pill's 35 is gone in about 6 minutes). */
    public static final float TOXICITY_DECAY_PER_SECOND = 0.1f;
    /** At 100 toxicity a pill works at 1 − this of its strength. */
    public static final float TOLERANCE_WEAKEN = 0.6f;
    /** Toxicity after a pill at or past which you feel sick: nausea… */
    public static final float NAUSEA_AT = 50f;
    /** …hunger and weakness… */
    public static final float SICK_AT = 75f;
    /** …and poison (an overdose). */
    public static final float OVERDOSE_AT = 95f;
    public static final int NAUSEA_TICKS = 140, SICK_TICKS = 300, OVERDOSE_TICKS = 100;
    /** Share of toxicity kept through death. */
    public static final float TOXICITY_KEPT_ON_DEATH = 0.5f;
    /** A crash multiplier never goes below this (regen at 5%). */
    public static final float MIN_MULTIPLIER = 0.05f;
    /** What a good antidote clears (as the antidote item). */
    public static final List<MobEffect> CURES = List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.CONFUSION, MobEffects.HUNGER);

    public enum Result { TAKEN, COOLDOWN, UNKNOWN }

    private PillEffects() {}

    // ================================================================ taking a pill

    /** How strong a pill of {@code grade} is for this body right now. */
    public static float potency(PillData pills, Grade grade) {
        float tolerance = 1f - Mth.clamp(pills.toxicity() / PillData.MAX_TOXICITY, 0f, 1f) * TOLERANCE_WEAKEN;
        return grade.potency * NoirmConfig.f(NoirmConfig.PILL_STRENGTH) * tolerance;
    }

    /**
     * Swallows pill {@code id} of {@code grade}: its instant effects, its effects, its crash later,
     * toxicity and the cooldown. Returns {@link Result#COOLDOWN} (nothing happens) while the last one's
     * cooldown runs.
     */
    public static Result take(Player player, ShinobiData data, ResourceLocation id, Grade grade) {
        PillDefinition def = PillRegistry.pill(id);
        if (def == null) {
            return Result.UNKNOWN;
        }
        PillData pills = data.pills();
        int cooldown = pills.cooldown(id);
        if (cooldown > 0 && !player.getAbilities().instabuild) {
            player.displayClientMessage(Component.translatable("message.noirm.pill.cooldown", (cooldown + 19) / 20)
                    .withStyle(ChatFormatting.GRAY), true);
            return Result.COOLDOWN;
        }
        float potency = potency(pills, grade);
        applyInstant(player, data, def.instant(), potency);
        // A pill taken again before its crash replaces the old one (no stacking the same pill).
        removeSource(player, data, id);
        for (PillDefinition.EffectSpec spec : def.effects()) {
            addBoost(player, data, spec, id, potency, false, 0, 1f);
        }
        int crashDelay = def.effectTicks();
        for (PillDefinition.EffectSpec spec : def.crash()) {
            addBoost(player, data, spec, id, 1f, true, crashDelay, grade.crash);
        }
        pills.setCooldown(id, def.cooldown() * 20);
        float before = pills.toxicity();
        pills.setToxicity(before + def.toxicity() * grade.toxicity);
        sideEffects(player, before, pills.toxicity());
        pills.addSwallowed();
        pills.markDirty();
        data.markDirty();
        puff(player, def.rgb(), 6);
        player.level().playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.6f, 1.7f);
        player.level().playSound(null, player.blockPosition(), def.instant().chakra() > 0 || def.instant().stamina() > 0
                ? com.noirm.sound.NoirmSounds.CHAKRA_CHARGE.get() : com.noirm.sound.NoirmSounds.MEDICAL_CHIME.get(), SoundSource.PLAYERS, 0.5f, 1.3f);
        PillTrigger.swallowed(player, id.getPath());
        PillTrigger.toxicity(player, Math.round(pills.toxicity()));
        return Result.TAKEN;
    }

    /** Eats food {@code source}'s NOIRM effects (its hunger is vanilla's). Eating it again refreshes them. */
    public static void eat(Player player, ShinobiData data, ResourceLocation source, PillDefinition food) {
        applyInstant(player, data, food.instant(), 1f);
        removeSource(player, data, source);
        for (PillDefinition.EffectSpec spec : food.effects()) {
            addBoost(player, data, spec, source, 1f, false, 0, 1f);
        }
        int crashDelay = food.effectTicks();
        for (PillDefinition.EffectSpec spec : food.crash()) {
            addBoost(player, data, spec, source, 1f, true, crashDelay, 1f);
        }
        data.pills().addEaten();
        data.pills().markDirty();
        data.markDirty();
        PillTrigger.ate(player, source.getPath());
    }

    private static void applyInstant(Player player, ShinobiData data, PillDefinition.Instant instant, float potency) {
        if (instant.chakra() > 0) {
            data.setChakra(data.getChakra() + data.getMaxChakra() * Math.min(1f, instant.chakra() * potency));
        }
        if (instant.stamina() > 0) {
            data.setStamina(data.getStamina() + data.getMaxStamina() * Math.min(1f, instant.stamina() * potency));
        }
        if (instant.heal() > 0) {
            player.heal(instant.heal() * potency);
        }
        if (instant.cure()) {
            CURES.forEach(player::removeEffect);
        }
    }

    /**
     * Adds one effect. {@code potency} scales an effect's strength; a crash ({@code crash}) is scaled
     * by {@code crashScale} instead (strength and length) and waits {@code delay} ticks.
     */
    private static void addBoost(Player player, ShinobiData data, PillDefinition.EffectSpec spec, ResourceLocation source, float potency,
                                 boolean crash, int delay, float crashScale) {
        Boost.Kind kind = spec.kind();
        if (kind == null) {
            Noirm.LOGGER.warn("Unknown pill effect type {} in {}", spec.type(), source);
            return;
        }
        float amount = spec.amount();
        int ticks = spec.seconds() * 20;
        if (crash) {
            ticks = Math.max(20, Math.round(ticks * crashScale));
            amount = switch (kind.unit()) {
                case MULTIPLIER -> Math.max(MIN_MULTIPLIER, 1f - (1f - amount) * crashScale);
                case PER_SECOND, STAT_SHARE -> amount * crashScale;
                case NONE -> amount;
            };
        } else {
            amount = switch (kind.unit()) {
                case MULTIPLIER -> Math.max(MIN_MULTIPLIER, 1f + (amount - 1f) * potency);
                case PER_SECOND, STAT_SHARE -> amount * potency;
                case NONE -> amount;
            };
        }
        Boost boost = new Boost(kind, amount, ticks, delay, source, crash, spec.effect().orElse(null), spec.amplifier());
        data.pills().boosts().add(boost);
        if (boost.active()) {
            start(player, data, boost);
        }
    }

    /** What happens as a pill's toxicity climbs past each line. */
    private static void sideEffects(Player player, float before, float after) {
        if (after >= NAUSEA_AT) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, NAUSEA_TICKS, 0));
        }
        if (after >= SICK_AT) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, SICK_TICKS, 1));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SICK_TICKS, 0));
        }
        if (after >= OVERDOSE_AT) {
            player.addEffect(new MobEffectInstance(MobEffects.POISON, OVERDOSE_TICKS, 1));
        }
        String key = after >= OVERDOSE_AT ? "message.noirm.pill.overdose" : after >= SICK_AT ? "message.noirm.pill.sick"
                : after >= NAUSEA_AT ? "message.noirm.pill.nausea" : null;
        if (key != null) {
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
        }
    }

    // ================================================================ every tick

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            ShinobiCapability.get(player).ifPresent(data -> tick(player, data));
        }
    }

    /** One tick of a player's foods and pills (public so tests can drive it). */
    public static void tick(Player player, ShinobiData data) {
        PillData pills = data.pills();
        pills.tickCooldowns();
        if (pills.toxicity() > 0f && player.tickCount % 20 == 0) {
            pills.setToxicity(pills.toxicity() - TOXICITY_DECAY_PER_SECOND);
        }
        boolean second = player.tickCount % 20 == 0;
        CombatState combat = null;
        Iterator<Boost> it = pills.boosts().iterator();
        while (it.hasNext()) {
            Boost b = it.next();
            if (b.delay > 0) {
                if (--b.delay == 0) {
                    start(player, data, b);
                    pills.markDirty();
                }
                continue;
            }
            switch (b.kind) {
                case HEALTH_REGEN -> {
                    if (second && player.getHealth() < player.getMaxHealth()) {
                        player.heal(b.amount);
                    }
                }
                case POSTURE_RECOVERY -> {
                    if (combat == null) {
                        combat = CombatStates.getIfPresent(player);
                    }
                    if (combat != null) {
                        combat.recoverPosture(b.amount / 20f);
                    }
                }
                case POISON_WARD -> {
                    player.removeEffect(MobEffects.POISON);
                    player.removeEffect(MobEffects.WITHER);
                }
                default -> { }
            }
            if (--b.ticksLeft <= 0) {
                end(player, data, b);
                it.remove();
                pills.markDirty();
                if (b.crash && pills.boosts().stream().noneMatch(o -> o.crash && o.source.equals(b.source))) {
                    PillTrigger.recovered(player, b.source.getPath());
                }
            }
        }
        if (pills.consumeDirty() && player instanceof ServerPlayer server) {
            NoirmNetwork.sendToPlayer(server, PillPackets.Sync.of(data));
        }
    }

    /** A boost begins: stat points go on, a vanilla effect is applied. */
    private static void start(Player player, ShinobiData data, Boost b) {
        if (b.started) {
            return;
        }
        b.started = true;
        if (b.kind.isStat()) {
            float base = stat(data, b.kind);
            float delta = Math.round(base * b.amount);
            if (delta == 0f && b.amount != 0f) {
                delta = Math.signum(b.amount);
            }
            float next = Mth.clamp(base + delta, 1f, ShinobiData.MAX_STAT);
            b.applied = next - base;
            setStat(data, b.kind, next);
        } else if (b.kind == Boost.Kind.EFFECT && b.effect != null) {
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(b.effect);
            if (effect != null) {
                player.addEffect(new MobEffectInstance(effect, b.ticksLeft, b.amplifier, false, true));
            }
        }
        if (b.crash) {
            player.displayClientMessage(Component.translatable("message.noirm.pill.crash",
                    Component.translatable(itemKey(b.source))).withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    /** A boost ends: stat points come back off (exactly what went on). */
    private static void end(Player player, ShinobiData data, Boost b) {
        if (b.started && b.kind.isStat() && b.applied != 0f) {
            setStat(data, b.kind, stat(data, b.kind) - b.applied);
            b.applied = 0f;
        }
    }

    /** Ends every boost from {@code source} at once (taking it again, or clearing). */
    static void removeSource(Player player, ShinobiData data, ResourceLocation source) {
        Iterator<Boost> it = data.pills().boosts().iterator();
        while (it.hasNext()) {
            Boost b = it.next();
            if (b.source.equals(source)) {
                end(player, data, b);
                if (b.started && b.kind == Boost.Kind.EFFECT && b.effect != null) {
                    MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(b.effect);
                    if (effect != null) {
                        player.removeEffect(effect);
                    }
                }
                it.remove();
            }
        }
        data.pills().markDirty();
    }

    /** Ends everything (death, {@code /noirm pill clear}): stat points come back off. */
    public static void clearAll(Player player, ShinobiData data) {
        for (Boost b : data.pills().boosts()) {
            end(player, data, b);
        }
        data.pills().boosts().clear();
        data.pills().markDirty();
        data.markDirty();
    }

    /** Dying ends every effect and crash; half the toxicity stays. */
    @SubscribeEvent
    public static void died(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide()) {
            ShinobiCapability.get(player).ifPresent(data -> {
                clearAll(player, data);
                data.pills().setToxicity(data.pills().toxicity() * TOXICITY_KEPT_ON_DEATH);
            });
        }
    }

    // ================================================================ hooks (one each)

    /** Chakra regeneration × this ({@code ShinobiData.chakraRegenMultiplier}). */
    public static float chakraRegenMultiplier(ShinobiData data) {
        return product(data, Boost.Kind.CHAKRA_REGEN);
    }

    /** Stamina regeneration × this ({@code ShinobiData.staminaRegenMultiplier}). */
    public static float staminaRegenMultiplier(ShinobiData data) {
        return product(data, Boost.Kind.STAMINA_REGEN);
    }

    /** Stat XP × this ({@code TrainingSystem.grant}). */
    public static float trainingXpMultiplier(Player player) {
        return ShinobiCapability.get(player).map(PillEffects::trainingXpMultiplier).orElse(1f);
    }

    public static float trainingXpMultiplier(ShinobiData data) {
        return product(data, Boost.Kind.TRAINING_XP);
    }

    /** Perception × this ({@code Perception}): a focus pill. Players only (ninjas use vanilla effects). */
    public static float perceptionMultiplier(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return 1f;
        }
        return ShinobiCapability.get(player).map(d -> product(d, Boost.Kind.PERCEPTION)).orElse(1f);
    }

    private static float product(ShinobiData data, Boost.Kind kind) {
        List<Boost> boosts = data.pills().boosts();
        if (boosts.isEmpty()) {
            return 1f;
        }
        float m = 1f;
        for (int i = 0; i < boosts.size(); i++) {
            Boost b = boosts.get(i);
            if (b.kind == kind && b.active()) {
                m *= b.amount;
            }
        }
        return m;
    }

    /** Points a running stat boost adds right now (for "base" stat readers like advancements). */
    public static float statBonus(ShinobiData data, Boost.Kind kind) {
        float sum = 0f;
        for (Boost b : data.pills().boosts()) {
            if (b.kind == kind && b.started) {
                sum += b.applied;
            }
        }
        return sum;
    }

    /** {@link #statBonus(ShinobiData, Boost.Kind)} for a core stat. */
    public static float statBonus(ShinobiData data, com.noirm.training.TrainingSystem.Stat stat) {
        return statBonus(data, switch (stat) {
            case SPEED -> Boost.Kind.SPEED;
            case TAIJUTSU -> Boost.Kind.TAIJUTSU;
            case VITALITY -> Boost.Kind.VITALITY;
            case RESERVES -> Boost.Kind.RESERVES;
            case DURABILITY -> Boost.Kind.DURABILITY;
        });
    }

    // ================================================================ helpers

    static float stat(ShinobiData data, Boost.Kind kind) {
        return switch (kind) {
            case SPEED -> data.getSpeed();
            case TAIJUTSU -> data.getTaijutsu();
            case VITALITY -> data.getVitality();
            case RESERVES -> data.getReserves();
            case CONTROL -> data.getControl();
            case WILLPOWER -> data.getWillpower();
            case DURABILITY -> data.getDurability();
            default -> 0f;
        };
    }

    static void setStat(ShinobiData data, Boost.Kind kind, float value) {
        switch (kind) {
            case SPEED -> data.setSpeed(value);
            case TAIJUTSU -> data.setTaijutsu(value);
            case VITALITY -> data.setVitality(value);
            case RESERVES -> data.setReserves(value);
            case CONTROL -> data.setControl(value);
            case WILLPOWER -> data.setWillpower(value);
            case DURABILITY -> data.setDurability(value);
            default -> { }
        }
    }

    /** "item.noirm.soldier_pill". */
    public static String itemKey(ResourceLocation item) {
        return "item." + item.getNamespace() + "." + item.getPath();
    }

    /** A puff of colour over a block (the apothecary after a brew). */
    public static void puffAt(ServerLevel level, net.minecraft.core.BlockPos pos, int rgb) {
        Vector3f color = new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
        level.sendParticles(new DustParticleOptions(color, 1.2f), pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 12, 0.2, 0.15, 0.2, 0.02);
    }

    /** A small puff of the pill's colour around the swallower's head. */
    public static void puff(LivingEntity entity, int rgb, int count) {
        if (entity.level() instanceof ServerLevel level) {
            Vector3f color = new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
            float size = entity instanceof Player ? 0.8f : 1.5f;
            level.sendParticles(new DustParticleOptions(color, size), entity.getX(), entity.getY() + entity.getBbHeight() * 0.6,
                    entity.getZ(), count, 0.3, 0.3, 0.3, 0.02);
        }
    }
}
