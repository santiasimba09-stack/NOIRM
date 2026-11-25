package com.noirm.aburame;

import com.noirm.entity.SparringNinja;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Chakra, for draining (docs/ABURAME.md, 2.5): read and take anyone's chakra (players and ninjas),
 * and the <b>Drained</b> and <b>Exhausted</b> states a target falls into at 0 chakra.
 * <ul>
 *   <li>Drained, 4 s: slowness, no sprinting, no jutsu, no dodging ("can't move", ch. 395);</li>
 *   <li>then Exhausted: slowness I and 30% less damage, until it's back to 20% chakra.</li>
 * </ul>
 */
public final class Drain {
    private Drain() {}

    /** Does {@code entity} have chakra at all (players and ninjas do; wolves don't)? */
    public static boolean hasChakra(LivingEntity entity) {
        return entity instanceof Player || entity instanceof SparringNinja;
    }

    public static float chakra(LivingEntity entity) {
        if (entity instanceof Player player) {
            return ShinobiCapability.get(player).map(ShinobiData::getChakra).orElse(0f);
        }
        return entity instanceof SparringNinja ninja ? ninja.chakra() : 0f;
    }

    public static float maxChakra(LivingEntity entity) {
        if (entity instanceof Player player) {
            return ShinobiCapability.get(player).map(ShinobiData::getMaxChakra).orElse(0f);
        }
        return entity instanceof SparringNinja ninja ? ninja.maxChakra() : 0f;
    }

    public static void setChakra(LivingEntity entity, float value) {
        if (entity instanceof Player player) {
            ShinobiCapability.get(player).ifPresent(d -> d.setChakra(Math.max(0f, Math.min(d.getMaxChakra(), value))));
        } else if (entity instanceof SparringNinja ninja) {
            ninja.setChakra(Math.max(0f, Math.min(ninja.maxChakra(), value)));
        }
    }

    /** Takes up to {@code amount} chakra from {@code target}; returns what was taken. At 0 the target is Drained. */
    public static float take(LivingEntity target, float amount) {
        float have = chakra(target);
        float took = Math.min(have, amount);
        if (took <= 0f) {
            return 0f;
        }
        setChakra(target, have - took);
        if (chakra(target) <= 0.01f) {
            drain(target);
        }
        return took;
    }

    /** Gives {@code amount} chakra (players and ninjas). */
    public static void give(LivingEntity target, float amount) {
        if (amount > 0f) {
            setChakra(target, chakra(target) + amount);
        }
    }

    /** Puts {@code target} in the Drained state (once; it can't be renewed while it lasts). */
    public static void drain(LivingEntity target) {
        if (target.hasEffect(AburameRegistry.DRAINED.get()) || target.hasEffect(AburameRegistry.EXHAUSTED.get())) {
            return;
        }
        target.addEffect(new MobEffectInstance(AburameRegistry.DRAINED.get(), ColonyGrowth.DRAINED_TICKS, 0, false, true, true));
        target.setSprinting(false);
        if (target instanceof Player player) {
            player.displayClientMessage(Component.translatable("message.noirm.colony.drained"), true);
        }
    }

    public static boolean isDrained(LivingEntity entity) {
        return AburameRegistry.DRAINED.isPresent() && entity.hasEffect(AburameRegistry.DRAINED.get());
    }

    public static boolean isExhausted(LivingEntity entity) {
        return AburameRegistry.EXHAUSTED.isPresent() && entity.hasEffect(AburameRegistry.EXHAUSTED.get());
    }

    /** Every second: Drained turns into Exhausted when it ends; Exhausted ends at 20% chakra. */
    public static void tickSecond(LivingEntity entity) {
        MobEffectInstance drained = entity.getEffect(AburameRegistry.DRAINED.get());
        if (drained != null) {
            entity.setSprinting(false);
            if (drained.getDuration() <= 20) {
                entity.addEffect(new MobEffectInstance(AburameRegistry.EXHAUSTED.get(), 20 * 600, 0, false, true, true));
            }
        } else if (isExhausted(entity)) {
            float max = maxChakra(entity);
            if (max <= 0 || chakra(entity) >= max * ColonyGrowth.EXHAUSTED_UNTIL) {
                entity.removeEffect(AburameRegistry.EXHAUSTED.get());
            }
        }
    }

    /** Lifts both states (Chakra Gift revives a Drained ally). */
    public static void revive(LivingEntity entity) {
        entity.removeEffect(AburameRegistry.DRAINED.get());
        entity.removeEffect(AburameRegistry.EXHAUSTED.get());
    }
}
