package com.noirm.aburame;

import com.noirm.bounty.NinjaClass;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * What a swarm does in its bigger shapes (docs/ABURAME.md, 2.5): the wall, the dome, the scout (its
 * report, its marking and the map it draws), jamming, the tornado's pull, the Black Sea, the
 * rinkaichū's Poison Cloud and the forbidden Jar of Poison.
 */
final class SwarmShapes {
    /** Scouts see this far around where they land. */
    static final double SCOUT_SIGHT = 24;
    /** Map rows drawn per tick (128 rows in about 3 seconds). */
    static final int MAP_ROWS_PER_TICK = 2;
    /** Jamming: radius, and how long after you attack an NPC can find you again. */
    static final float JAM_RADIUS = 24f;
    static final int JAM_GRACE_TICKS = 60;
    /** The Black Sea's radius, and its most drain per target per second. */
    static final float SEA_RADIUS = 28f;
    static final float SEA_MAX_DRAIN = 25f;
    /** Poison Cloud: grows from 8 to 12 blocks; Cell Rot stacks a second inside. */
    static final float POISON_START = 8f, POISON_END = 12f;
    static final int POISON_STACKS = 3;
    /** Jar of Poison: the barrier, the ritual, the storm. */
    static final float JAR_BARRIER = 9f, JAR_STORM = 48f;
    static final int JAR_RITUAL_TICKS = 400, JAR_STORM_TICKS = 1200;
    static final float JAR_RESPECT = -40f;

    private SwarmShapes() {}

    // ================================================================ the wall

    static boolean inWall(InsectSwarm swarm, Vec3 pos, double margin) {
        Vec3 c = swarm.center();
        Vec3 facing = Vec3.directionFromRotation(0, swarm.shape());
        Vec3 right = new Vec3(-facing.z, 0, facing.x);
        Vec3 d = pos.subtract(c);
        return Math.abs(d.dot(right)) <= InsectSwarm.WALL_WIDTH / 2 + margin && Math.abs(d.dot(facing)) <= 0.5 + margin
                && Math.abs(d.y) <= InsectSwarm.WALL_HEIGHT / 2 + margin;
    }

    static void wall(InsectSwarm swarm, ServerLevel level, LivingEntity owner) {
        if (swarm.park() != null) {
            swarm.moveCenter(swarm.park());
        }
        // Creatures can't walk through: enemies are pushed back to the side they came from.
        Vec3 facing = Vec3.directionFromRotation(0, swarm.shape());
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, swarm.getBoundingBox().inflate(1),
                e -> e.isAlive() && JutsuEffects.isTarget(owner, e) && inWall(swarm, e.position().add(0, e.getBbHeight() / 2, 0), e.getBbWidth() / 2))) {
            double side = Math.signum(e.position().subtract(swarm.center()).dot(facing));
            Vec3 push = facing.scale(side == 0 ? 1 : side).scale(0.5);
            e.setDeltaMovement(push.x, Math.max(0.05, e.getDeltaMovement().y), push.z);
            e.hurtMarked = true;
        }
    }

    // ================================================================ the dome

    static void dome(InsectSwarm swarm, ServerLevel level, LivingEntity owner) {
        swarm.moveCenter(owner.position().add(0, 1.0, 0));
    }

    // ================================================================ scouting

    static void scout(InsectSwarm swarm, ServerLevel level, LivingEntity owner) {
        if (swarm.park() != null) {
            swarm.fly(swarm.park().add(0, Mth.sin(swarm.tickCount * 0.1f) * 0.3, 0), 4f, null);
        }
        List<LivingEntity> seen = level.getEntitiesOfClass(LivingEntity.class, new AABB(swarm.center(), swarm.center()).inflate(SCOUT_SIGHT),
                e -> e.isAlive() && e != owner && !(e instanceof WildInsect) && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand));
        if (swarm.tickCount % 20 == 0) {
            boolean firefly = InsectSwarm.colonyHas(owner, WildKind.FIREFLY);
            for (LivingEntity e : seen) {
                // Marked through walls while the scout watches (fireflies leave a glow for 30 s).
                if (e instanceof SparringNinja || e instanceof Player || e instanceof net.minecraft.world.entity.monster.Enemy) {
                    e.addEffect(new MobEffectInstance(MobEffects.GLOWING, firefly ? 600 : 30, 0, false, false));
                }
            }
        }
        if (!swarm.reported() && owner instanceof Player player) {
            swarm.setReported(true);
            report(player, swarm, seen);
            ColonyData colony = Colony.of(owner);
            if (colony != null) {
                colony.setScoutsFlown(colony.scoutsFlown() + 1);
            }
            startMap(player, swarm, level);
        }
        drawMap(swarm, level);
    }

    /** The scout's report: who's there, by class and nature, beasts, civilians and chests. */
    static void report(Player player, InsectSwarm swarm, List<LivingEntity> seen) {
        int ninjas = 0, beasts = 0, civilians = 0;
        java.util.Map<NinjaClass, Integer> classes = new java.util.EnumMap<>(NinjaClass.class);
        java.util.Set<String> natures = new java.util.TreeSet<>();
        SparringNinja spied = null;
        for (LivingEntity e : seen) {
            if (e instanceof SparringNinja ninja) {
                ninjas++;
                classes.merge(NinjaClass.of(ninja), 1, Integer::sum);
                ninja.natures().keySet().forEach(n -> natures.add(n.displayName()));
                if (ninja.tails() > 0) {
                    beasts++;
                }
                if (ninja.distanceToSqr(swarm.center()) < 16 && spied == null) {
                    spied = ninja;
                }
            } else if (e instanceof Player) {
                ninjas++;
            } else if (e instanceof net.minecraft.world.entity.npc.AbstractVillager || e instanceof com.noirm.entity.VillageMerchant) {
                civilians++;
            }
        }
        int chests = 0;
        BlockPos c = BlockPos.containing(swarm.center());
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-12, -6, -12), c.offset(12, 6, 12))) {
            if (player.level().getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity) {
                chests++;
            }
        }
        StringBuilder cls = new StringBuilder();
        classes.forEach((k, v) -> cls.append(cls.length() > 0 ? ", " : "").append(v).append(" ").append(k.name()));
        player.sendSystemMessage(Component.translatable("message.noirm.colony.scout_report", ninjas,
                cls.length() == 0 ? "-" : cls.toString(), natures.isEmpty() ? "-" : String.join(", ", natures), beasts, civilians, chests)
                .withStyle(ChatFormatting.DARK_AQUA));
        if (spied != null) {
            // Spy mode: a scout landed on a ninja reads it.
            List<String> jutsu = spied.knownJutsu().stream().limit(4).map(id -> Component.translatable("jutsu.noirm." + id.getPath().replace('/', '.')).getString()).toList();
            player.sendSystemMessage(Component.translatable("message.noirm.colony.scout_spy", spied.getName(), NinjaClass.of(spied).name(),
                    jutsu.isEmpty() ? "-" : String.join(", ", jutsu)).withStyle(ChatFormatting.GRAY));
        }
    }

    /** If the owner holds a map, the scout draws the area around it onto a fresh one. */
    static void startMap(Player player, InsectSwarm swarm, ServerLevel level) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.is(Items.MAP) || held.is(Items.FILLED_MAP)) {
                ItemStack map = MapItem.create(level, Mth.floor(swarm.getX()), Mth.floor(swarm.getZ()), (byte) 0, true, false);
                map.setHoverName(Component.translatable("item.noirm.scout_map"));
                if (held.is(Items.MAP)) {
                    held.shrink(1);
                }
                if (!player.getInventory().add(map)) {
                    player.drop(map, false);
                }
                swarm.setMapStack(map);
                swarm.setMapRow(0);
                return;
            }
        }
    }

    static void drawMap(InsectSwarm swarm, ServerLevel level) {
        int row = swarm.mapRow();
        if (row < 0 || row >= 128) {
            return;
        }
        MapItemSavedData data = MapItem.getSavedData(swarm.mapStack(), level);
        if (data == null) {
            swarm.setMapRow(-1);
            return;
        }
        for (int r = row; r < Math.min(128, row + MAP_ROWS_PER_TICK); r++) {
            int z = data.centerZ - 64 + r;
            int prev = Integer.MIN_VALUE;
            for (int px = 0; px < 128; px++) {
                int x = data.centerX - 64 + px;
                int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                BlockPos top = new BlockPos(x, h - 1, z);
                MapColor color = level.getBlockState(top).getMapColor(level, top);
                int north = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - 1);
                MapColor.Brightness b = h > north ? MapColor.Brightness.HIGH : h < north ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                data.setColor(px, r, color == MapColor.NONE ? 0 : color.getPackedId(b));
                prev = h;
            }
        }
        swarm.setMapRow(row + MAP_ROWS_PER_TICK);
        data.setDirty();
    }

    // ================================================================ jamming

    static void jam(InsectSwarm swarm, ServerLevel level, LivingEntity owner) {
        if (swarm.park() != null) {
            swarm.moveCenter(swarm.park());
        }
        if (swarm.tickCount % 20 != 0) {
            return;
        }
        float r = swarm.radius();
        boolean justAttacked = owner.getLastHurtMobTimestamp() > 0 && owner.tickCount - owner.getLastHurtMobTimestamp() < JAM_GRACE_TICKS;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(swarm.center(), swarm.center()).inflate(r + 16))) {
            LivingEntity target = mob.getTarget();
            if (target == null || target.distanceToSqr(swarm.center()) > r * r) {
                continue;
            }
            if ((target == owner && !justAttacked) || target != owner && JutsuEffects.isFriendly(owner, target)) {
                mob.setTarget(null); // a dozen false chakra signals: they've lost you
                mob.getNavigation().stop();
            }
        }
    }

    /** Is {@code entity} hidden inside an ally's jamming field? */
    static boolean jammedFor(LivingEntity entity) {
        for (InsectSwarm s : InsectSwarm.near(entity.level(), entity.position(), JAM_RADIUS + 2)) {
            LivingEntity owner = s.owner();
            if (s.mode() == SwarmMode.JAM && owner != null && (owner == entity || JutsuEffects.isFriendly(owner, entity))
                    && entity.distanceToSqr(s.center()) < s.radius() * s.radius()) {
                return true;
            }
        }
        return false;
    }

    // ================================================================ the tornado

    static void tornadoPull(InsectSwarm swarm, ServerLevel level, LivingEntity owner, LivingEntity held) {
        double reach = InsectSwarm.colonyHas(owner, WildKind.LOCUST) ? 6 : 4;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, held.getBoundingBox().inflate(reach),
                e -> e != held && e.isAlive() && JutsuEffects.isTarget(owner, e))) {
            Vec3 pull = held.position().subtract(e.position()).normalize().scale(0.25);
            e.setDeltaMovement(e.getDeltaMovement().add(pull.x, 0.05, pull.z));
            e.hurtMarked = true;
        }
    }

    // ================================================================ the Black Sea

    static void sea(InsectSwarm swarm, ServerLevel level, LivingEntity owner) {
        if (swarm.park() != null) {
            swarm.moveCenter(swarm.park());
        }
        if (swarm.tickCount % 20 != 0) {
            return;
        }
        float r = swarm.radius();
        List<LivingEntity> inside = level.getEntitiesOfClass(LivingEntity.class, new AABB(swarm.center(), swarm.center()).inflate(r),
                e -> e.isAlive() && e != owner && JutsuEffects.isTarget(owner, e) && e.distanceToSqr(swarm.center()) < r * r
                        && !(e instanceof WildInsect));
        if (inside.isEmpty()) {
            return;
        }
        float each = Math.min(SEA_MAX_DRAIN, ColonyGrowth.DRAIN_PER_1K * swarm.count() / 1000f * 2f / inside.size());
        for (LivingEntity e : inside) {
            e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false));
            if (!Drain.hasChakra(e)) {
                e.hurt(level.damageSources().indirectMagic(swarm, owner), Math.min(6f, each * 0.3f));
                continue;
            }
            if (InsectSwarm.overfed(owner, e)) {
                swarm.harm(Harm.JUTSU, swarm.count() * 0.02f / Colony.killRate(owner, Harm.JUTSU));
                continue;
            }
            float took = Drain.take(e, each);
            Drain.give(owner, took * ColonyGrowth.DRAIN_TO_OWNER);
            ColonyData c = Colony.of(owner);
            if (c != null) {
                c.addDrained(took);
            }
            if (Drain.chakra(e) <= 0.01f) {
                e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0); // held still
                e.hurtMarked = true;
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 6, false, false));
            }
        }
    }

    // ================================================================ rinkaichū

    static void poisonCloud(InsectSwarm swarm, ServerLevel level, LivingEntity owner) {
        float t = Math.min(1f, swarm.life() / 120f);
        float range = Mth.lerp(t, POISON_START, POISON_END);
        swarm.setShape(range);
        Vec3 origin = owner.getEyePosition();
        Vec3 dir = Vec3.directionFromRotation(0, swarm.getYRot());
        swarm.moveCenter(origin.add(dir.scale(range * 0.5)));
        if (swarm.tickCount % 20 != 0) {
            return;
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(origin, origin).inflate(range),
                e -> e.isAlive() && e != owner)) {
            Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(origin);
            if (to.length() <= range && dir.dot(to.normalize()) > Math.cos(Math.toRadians(35))) {
                RotTouch.infect(e, POISON_STACKS); // it hits allies too
            }
        }
    }

    static void jarOfPoison(InsectSwarm swarm, ServerLevel level, LivingEntity owner) {
        if (swarm.park() != null) {
            swarm.moveCenter(swarm.park());
        }
        boolean storm = swarm.shape() >= JAR_STORM - 0.5f;
        if (!storm) {
            if (swarm.tickCount % 5 == 0) {
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI * 2 / 24 + swarm.tickCount * 0.05;
                    level.sendParticles(SwarmFx.PURPLE, swarm.getX() + Math.cos(a) * JAR_BARRIER, swarm.getY() + 0.2 + (i % 3),
                            swarm.getZ() + Math.sin(a) * JAR_BARRIER, 1, 0, 0.3, 0, 0);
                }
            }
            return;
        }
        if (swarm.tickCount % 40 == 0) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(swarm.center(), swarm.center()).inflate(JAR_STORM),
                    e -> e.isAlive() && e.distanceToSqr(swarm.center()) < JAR_STORM * JAR_STORM)) {
                RotTouch.infect(e, 1); // friend and foe alike
            }
            level.sendParticles(SwarmFx.PURPLE, swarm.getX(), swarm.getY() + 4, swarm.getZ(), 120, JAR_STORM / 3, 4, JAR_STORM / 3, 0.02);
        }
    }

    /** The ritual's end: the barrier bursts into a 48-block poison storm, and the village never forgets. */
    static void jarBurst(InsectSwarm swarm, ServerLevel level, LivingEntity owner) {
        if (swarm.shape() >= JAR_STORM - 0.5f) {
            swarm.setMode(SwarmMode.RETURN);
            return;
        }
        swarm.setShape(JAR_STORM);
        swarm.setLife(JAR_STORM_TICKS);
        level.playSound(null, swarm.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.PLAYERS, 2f, 0.6f);
        if (owner instanceof Player player) {
            for (SparringNinja mate : level.getEntitiesOfClass(SparringNinja.class, player.getBoundingBox().inflate(JAR_STORM),
                    n -> n.inCrew() && player.getUUID().equals(n.leaderId()))) {
                com.noirm.ally.AllyMind.respect(mate, JAR_RESPECT, "ally.noirm.reason.jar_of_poison");
            }
            player.sendSystemMessage(Component.translatable("message.noirm.colony.jar_of_poison").withStyle(ChatFormatting.DARK_PURPLE));
        }
    }
}
