package com.noirm.jutsu;

import com.noirm.Noirm;
import com.noirm.ally.Crew;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Jutsu leave the world dangerous: burning ground, craters, raised spikes and walls. None of
 * that should hurt the side that cast it. Every cast marks a zone around the caster for a while;
 * inside a zone of their own side, a player, their clones and their crew take no damage from the
 * environment (fire, lava, falls, being stuck in a wall, lightning).
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class FriendlyZones {
    public static final double RADIUS = 48;
    public static final int TICKS = 20 * 15;

    private record Zone(ResourceKey<Level> level, Vec3 center, long until) {}

    private static final Map<UUID, List<Zone>> ZONES = new HashMap<>();

    private FriendlyZones() {}

    /** {@code caster} just used a jutsu: their side is safe from its aftermath around here. */
    public static void mark(LivingEntity caster) {
        List<Zone> zones = ZONES.computeIfAbsent(Crew.sideOf(caster), k -> new ArrayList<>());
        long now = caster.level().getGameTime();
        zones.removeIf(z -> z.until() < now
                || z.level() == caster.level().dimension() && z.center().distanceToSqr(caster.position()) < 16); // refreshed below
        if (zones.size() < 32) {
            zones.add(new Zone(caster.level().dimension(), caster.position(), now + TICKS));
        }
    }

    /** Is {@code entity} standing in a zone its own side made? */
    public static boolean isSafe(LivingEntity entity) {
        List<Zone> zones = ZONES.get(Crew.sideOf(entity));
        if (zones == null) {
            return false;
        }
        long now = entity.level().getGameTime();
        for (Zone zone : zones) {
            if (zone.until() >= now && zone.level() == entity.level().dimension()
                    && zone.center().distanceToSqr(entity.position()) <= RADIUS * RADIUS) {
                return true;
            }
        }
        return false;
    }

    static boolean isEnvironmental(DamageSource source) {
        return source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_LIGHTNING)
                || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.FLY_INTO_WALL)
                || source.is(DamageTypes.HOT_FLOOR);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void attack(LivingAttackEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !isEnvironmental(event.getSource()) || !isSafe(entity)) {
            return;
        }
        event.setCanceled(true);
        if (event.getSource().is(DamageTypeTags.IS_FIRE)) {
            entity.clearFire();
        }
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        ZONES.clear();
    }
}
