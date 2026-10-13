package com.noirm.world.village;

import com.noirm.entity.SparringNinja;
import com.noirm.entity.TrainingDummy;
import com.noirm.entity.VillageMerchant;
import com.noirm.registry.NoirmEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * The people who live in a ninja village: who they are, how they're placed when the village is
 * built, and how they defend it.
 *
 * <p>Residents are ordinary {@link SparringNinja}s with the resident flag on (they never challenge
 * a passing player just for being in combat mode) and {@link VillageMerchant}s. Hurting any of
 * them sounds the alarm: every resident ninja nearby turns on the attacker.
 */
public final class VillageResidents {
    /** How far an alarm carries (blocks). */
    public static final double ALARM_RANGE = 40;

    private VillageResidents() {}

    /** Whether {@code entity} lives in a village (resident ninja or merchant). */
    public static boolean isResident(Entity entity) {
        return entity instanceof VillageMerchant || entity instanceof SparringNinja ninja && ninja.isResident();
    }

    /** Someone hurt a villager: the resident ninjas nearby fight them. */
    public static void raiseAlarm(LivingEntity victim, Entity attacker) {
        if (!(attacker instanceof LivingEntity enemy) || !enemy.isAlive() || isResident(enemy) || enemy == victim) {
            return;
        }
        if (enemy instanceof net.minecraft.world.entity.player.Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }
        for (SparringNinja ninja : victim.level().getEntitiesOfClass(SparringNinja.class, victim.getBoundingBox().inflate(ALARM_RANGE),
                n -> n.isResident() && n.isAlive() && n != enemy)) {
            LivingEntity current = ninja.getTarget();
            if (current == null || !current.isAlive()) {
                ninja.setTarget(enemy);
            }
        }
    }

    // ------------------------------------------------------------ placing residents at generation

    /**
     * A ninja who lives here, with {@code speed}/{@code taijutsu} stats, staying within
     * {@code radius} blocks of {@code home}.
     */
    public static SparringNinja ninja(ServerLevelAccessor level, BlockPos pos, BlockPos home, int radius, float speed, float taijutsu) {
        SparringNinja ninja = create(level, pos, home, radius, speed, taijutsu);
        return ninja == null ? null : place(level, ninja);
    }

    /** The village leader: a far stronger ninja with a title for a name ("Hokage"…). */
    public static SparringNinja kage(ServerLevelAccessor level, BlockPos pos, float speed, float taijutsu, String titleKey) {
        SparringNinja kage = create(level, pos, pos, 5, speed, taijutsu);
        if (kage == null) {
            return null;
        }
        // Set everything before placing: during world generation the entity is saved the moment
        // it's added, and later changes would be lost.
        kage.setCustomName(Component.translatable(titleKey));
        kage.setCustomNameVisible(true);
        return place(level, kage);
    }

    private static SparringNinja create(ServerLevelAccessor level, BlockPos pos, BlockPos home, int radius, float speed, float taijutsu) {
        SparringNinja ninja = NoirmEntities.SPARRING_NINJA.get().create(level.getLevel());
        if (ninja == null) {
            return null;
        }
        ninja.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
        ninja.setCombatStats(speed, taijutsu);
        ninja.makeResident(home, radius);
        return ninja;
    }

    public static VillageMerchant merchant(ServerLevelAccessor level, BlockPos pos, int radius) {
        VillageMerchant merchant = NoirmEntities.VILLAGE_MERCHANT.get().create(level.getLevel());
        if (merchant == null) {
            return null;
        }
        merchant.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
        merchant.setHome(pos, radius);
        return place(level, merchant);
    }

    public static TrainingDummy dummy(ServerLevelAccessor level, BlockPos pos) {
        TrainingDummy dummy = NoirmEntities.TRAINING_DUMMY.get().create(level.getLevel());
        if (dummy == null) {
            return null;
        }
        dummy.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
        return place(level, dummy);
    }

    /** Adds a mob that's already positioned and set up to the world (persistent: it never despawns). */
    private static <T extends Mob> T place(ServerLevelAccessor level, T mob) {
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.STRUCTURE, null, null);
        mob.setPersistenceRequired();
        level.addFreshEntityWithPassengers(mob);
        return mob;
    }
}
