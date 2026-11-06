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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import com.noirm.clan.ClanData;

import java.util.ArrayList;
import java.util.List;

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
     * Puts a planned resident into the world (when the chunk they live in is built). Their stats
     * come from {@code seed}, so the same village always has the same people.
     */
    public static void spawn(VillageCanvas c, VillagePlan plan, VillagePlan.Spawn s, long seed) {
        ServerLevelAccessor level = c.level;
        RandomSource r = RandomSource.create(seed);
        BlockPos pos = c.world(s.x(), s.y(), s.z());
        BlockPos home = c.world(s.homeX(), s.homeY(), s.homeZ());
        BlockPos work = c.world(s.workX(), 0, s.workZ());
        switch (s.role()) {
            case KAGE -> kage(level, pos, 120 + r.nextFloat() * 80, 120 + r.nextFloat() * 80, plan.palette.kageTitle, plan.palette);
            case GUARD -> resident(level, pos, home, s.radius(), 20 + r.nextFloat() * 15, 20 + r.nextFloat() * 15, "GUARD", null, null, plan.palette);
            case TRAINEE -> resident(level, pos, home, s.radius(), 12 + r.nextFloat() * 15, 12 + r.nextFloat() * 15, "TRAINEE", null, null);
            case NINJA -> resident(level, pos, home, 24, 15 + r.nextFloat() * 15, 15 + r.nextFloat() * 15, "", null, null);
            case CLAN_NINJA -> resident(level, pos, home, s.radius(), 20 + r.nextFloat() * 20, 20 + r.nextFloat() * 20, "", plan.compoundClan, null);
            case TEACHER -> {
                SparringNinja teacher = create(level, pos, home, s.radius(), 25 + r.nextFloat() * 10, 25 + r.nextFloat() * 10);
                if (teacher != null) {
                    teacher.setCustomName(Component.translatable("entity.noirm.villager.academy_teacher"));
                    place(level, teacher);
                }
            }
            case PATROL, RUNNER -> {
                List<BlockPos> route = new ArrayList<>();
                for (int[] point : s.route()) {
                    route.add(c.world(point[0], point[1], point[2]));
                }
                boolean runner = s.role() == VillagePlan.Role.RUNNER;
                float speed = runner ? 25 + r.nextFloat() * 20 : 18 + r.nextFloat() * 12;
                resident(level, pos, home, 128, speed, 18 + r.nextFloat() * 15, s.role().name(), null, route, plan.palette);
            }
            case MERCHANT, CIVILIAN, CHILD, CHIEF -> villager(level, pos, s, home, work);
            case DUMMY -> dummy(level, pos);
            case CAT -> animal(level, net.minecraft.world.entity.EntityType.CAT, pos, s.radius());
            case CHICKEN -> animal(level, net.minecraft.world.entity.EntityType.CHICKEN, pos, s.radius());
            case KOI -> animal(level, net.minecraft.world.entity.EntityType.TROPICAL_FISH, pos, s.radius());
            case MASTER -> master(level, pos, home, s.radius(), com.noirm.master.MasterData.MIGHT_GUY);
            case SCHOLAR -> master(level, pos, home, s.radius(), com.noirm.master.MasterData.SCHOLAR);
            case MEDIC -> master(level, pos, home, s.radius(), com.noirm.master.MasterData.MEDIC);
        }
    }

    /** A master at their post (Might Guy at the training ground…), if their data is loaded: stays near it, never despawns. */
    private static void master(ServerLevelAccessor level, BlockPos pos, BlockPos home, int radius, net.minecraft.resources.ResourceLocation id) {
        if (com.noirm.master.MasterData.get(id) == null) {
            return;
        }
        com.noirm.entity.MasterNinja master = NoirmEntities.MASTER.get().create(level.getLevel());
        if (master == null) {
            return;
        }
        master.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
        master.setMaster(id);
        master.restrictTo(home, Math.max(4, radius));
        place(level, master);
    }

    /** A village animal that stays put (persistent, kept near where it was placed). */
    private static void animal(ServerLevelAccessor level, net.minecraft.world.entity.EntityType<? extends Mob> type, BlockPos pos, int radius) {
        Mob mob = type.create(level.getLevel());
        if (mob == null) {
            return;
        }
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
        if (mob instanceof net.minecraft.world.entity.PathfinderMob pathfinder) {
            pathfinder.restrictTo(pos, Math.max(3, radius));
        }
        place(level, mob);
    }

    private static void resident(ServerLevelAccessor level, BlockPos pos, BlockPos home, int radius, float speed, float taijutsu,
                                 String role, String clan, List<BlockPos> route) {
        resident(level, pos, home, radius, speed, taijutsu, role, clan, route, null);
    }

    private static void resident(ServerLevelAccessor level, BlockPos pos, BlockPos home, int radius, float speed, float taijutsu,
                                 String role, String clan, List<BlockPos> route, VillagePalette palette) {
        SparringNinja ninja = create(level, pos, home, radius, speed, taijutsu);
        if (ninja == null) {
            return;
        }
        ninja.setVillageRole(role);
        if ("GUARD".equals(role) || "PATROL".equals(role)) {
            com.noirm.weapon.NinjaArsenal.armGuard(ninja, level.getRandom());
            if (palette != null) {
                com.noirm.gear.ClanOutfits.dressResident(ninja, role, palette, level.getRandom()); // the village's look
            }
        }
        if (route != null) {
            ninja.setRoute(route);
        }
        if (clan != null && !clan.isEmpty()) {
            ClanData.canon(clan).ifPresent(ninja::joinClan);
        }
        place(level, ninja);
    }

    private static void villager(ServerLevelAccessor level, BlockPos pos, VillagePlan.Spawn s, BlockPos home, BlockPos work) {
        VillageMerchant villager = NoirmEntities.VILLAGE_MERCHANT.get().create(level.getLevel());
        if (villager == null) {
            return;
        }
        VillageMerchant.Role role = switch (s.role()) {
            case CIVILIAN -> VillageMerchant.Role.CIVILIAN;
            case CHILD -> VillageMerchant.Role.CHILD;
            case CHIEF -> VillageMerchant.Role.CHIEF;
            default -> VillageMerchant.Role.MERCHANT;
        };
        villager.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
        villager.setup(role, s.shop(), home, work, role == VillageMerchant.Role.MERCHANT ? s.radius() : 10);
        if (role == VillageMerchant.Role.CHIEF) {
            villager.setCustomName(Component.translatable("entity.noirm.villager.village_chief"));
        }
        place(level, villager);
    }

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

    /** The Kage, dressed in the robe and the hat of the village's look. */
    public static SparringNinja kage(ServerLevelAccessor level, BlockPos pos, float speed, float taijutsu, String titleKey, VillagePalette palette) {
        SparringNinja kage = create(level, pos, pos, 5, speed, taijutsu);
        if (kage == null) {
            return null;
        }
        kage.setCustomName(Component.translatable(titleKey));
        kage.setCustomNameVisible(true);
        com.noirm.gear.ClanOutfits.dressResident(kage, "KAGE", palette, level.getRandom());
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
