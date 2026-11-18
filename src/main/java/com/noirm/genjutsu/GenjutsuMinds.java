package com.noirm.genjutsu;

import com.noirm.Noirm;
import com.noirm.combat.AttackInput;
import com.noirm.combat.CombatSystem;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuEffects;
import com.noirm.jutsu.SpiritTechniques;
import com.noirm.network.NoirmNetwork;
import com.noirm.vfx.SpiritVfx;
import com.noirm.vfx.Vfx;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Who is inside a genjutsu scene right now (server side), and what that does to them.
 * <ul>
 *   <li><b>A player</b> gets the whole scene on their screen ({@link GenjutsuPackets.Scene}): the
 *       false sky, the ground, the figures, the sounds. A strong mind may tear free early.</li>
 *   <li><b>A creature or NPC</b> visibly acts on what it sees ({@link GenjutsuScene.Reaction}):
 *       it freezes in terror, wanders lost, swings at phantoms or turns on its own side. A faint
 *       swirl turns over its head so the caster knows it worked.</li>
 * </ul>
 * The counterplay of the spirit natures ({@link SpiritTechniques}) still applies: Kai, a friend's
 * touch, a strong mind, an open Sharingan; breaking out shatters the scene like glass.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class GenjutsuMinds {
    /** Every this many ticks a strong-minded victim may tear free. */
    public static final int MIND_CHECK_TICKS = 20;
    /** The chance per check for each 10% their mind is stronger than the caster's (players and NPCs). */
    public static final float MIND_BREAK_PER_TENTH = 0.03f;
    /** Creatures caught in a scene are stunned only this long, then they act on what they see. */
    public static final int MOB_STUN_TICKS = 10;

    private static final class Caught {
        final LivingEntity caster;
        final ResourceLocation sceneId;
        final GenjutsuScene scene;
        final long start;
        final long until;
        final Vec3 decoy;
        Vec3 phantom;
        long nextMove;

        Caught(LivingEntity caster, ResourceLocation sceneId, GenjutsuScene scene, long start, long until, Vec3 decoy) {
            this.caster = caster;
            this.sceneId = sceneId;
            this.scene = scene;
            this.start = start;
            this.until = until;
            this.decoy = decoy;
        }
    }

    private static final Map<LivingEntity, Caught> MINDS = new WeakHashMap<>();

    private GenjutsuMinds() {}

    /**
     * Puts {@code victim} in the scene of {@code jutsu} for {@code ticks} (no resist roll: the jutsu
     * rolls first). {@code decoy}: where a false image stands, or null. Returns false if the jutsu
     * has no scene (the caller falls back to its old look).
     */
    public static boolean enter(LivingEntity caster, LivingEntity victim, ResourceLocation jutsu, int ticks, Vec3 decoy) {
        ResourceLocation sceneId = GenjutsuScenes.sceneFor(jutsu);
        return sceneId != null && enterScene(caster, victim, sceneId, ticks, decoy);
    }

    /** As {@link #enter}, by scene id ({@code /noirm genjutsu test}). */
    public static boolean enterScene(LivingEntity caster, LivingEntity victim, ResourceLocation sceneId, int ticks, Vec3 decoy) {
        GenjutsuScene scene = GenjutsuScenes.get(sceneId);
        if (scene == null || !(victim.level() instanceof ServerLevel level)) {
            return false;
        }
        long now = level.getGameTime();
        Caught caught = new Caught(caster, sceneId, scene, now, now + Math.max(1, ticks), decoy);
        MINDS.put(victim, caught);
        if (victim instanceof ServerPlayer player) {
            int[] allies = scene.swapAllies() ? allies(player) : new int[0];
            Vec3 d = decoy == null ? new Vec3(Double.NaN, Double.NaN, Double.NaN) : decoy;
            NoirmNetwork.sendToPlayer(player, new GenjutsuPackets.Scene(sceneId.toString(), GenjutsuScenes.toJson(scene), ticks,
                    caster == null ? -1 : caster.getId(), victim.getX(), victim.getY(), victim.getZ(), d.x, d.y, d.z, allies));
        } else if (victim instanceof Mob mob) {
            CombatSystem.stun(mob, MOB_STUN_TICKS);
            if (scene.reaction() != GenjutsuScene.Reaction.ATTACK_PHANTOMS || mob.getTarget() == caster) {
                mob.setTarget(null);
            }
            mob.getNavigation().stop();
        }
        swirl(level, victim, 1.4f);
        return true;
    }

    /** The ids of {@code player}'s allies within 32 blocks (their crew, other players on their side). */
    private static int[] allies(ServerPlayer player) {
        return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(32),
                        e -> e != player && e.isAlive() && JutsuEffects.isFriendly(player, e))
                .stream().limit(64).mapToInt(LivingEntity::getId).toArray();
    }

    public static boolean inScene(LivingEntity entity) {
        return MINDS.containsKey(entity);
    }

    /** The scene {@code entity} is in, or null. */
    public static ResourceLocation sceneOf(LivingEntity entity) {
        Caught caught = MINDS.get(entity);
        return caught == null ? null : caught.sceneId;
    }

    /** What a caught creature is doing, or null if it isn't caught. */
    public static GenjutsuScene.Reaction reactionOf(LivingEntity entity) {
        Caught caught = MINDS.get(entity);
        return caught == null ? null : caught.scene.reaction();
    }

    /** Where a caught creature thinks its enemy is (attack_phantoms), or null. */
    public static Vec3 phantomOf(LivingEntity entity) {
        Caught caught = MINDS.get(entity);
        return caught == null ? null : caught.phantom;
    }

    /**
     * Ends {@code victim}'s scene. {@code shattered}: broken from inside or outside (it breaks like
     * glass); otherwise it simply fades.
     */
    public static boolean release(LivingEntity victim, boolean shattered) {
        Caught caught = MINDS.remove(victim);
        if (caught == null) {
            return false;
        }
        if (victim instanceof ServerPlayer player) {
            NoirmNetwork.sendToPlayer(player, new GenjutsuPackets.End(shattered ? GenjutsuPackets.END_SHATTER : GenjutsuPackets.END_FADE));
        }
        if (victim instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        if (shattered && victim.level() instanceof ServerLevel level) {
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), com.noirm.sound.NoirmSounds.GENJUTSU_SHATTER.get(),
                    SoundSource.PLAYERS, 0.9f, 1f);
        }
        return true;
    }

    // ---------------------------------------------------------------- ticking

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (Map.Entry<LivingEntity, Caught> entry : new ArrayList<>(MINDS.entrySet())) {
            tick(entry.getKey(), entry.getValue());
        }
    }

    private static void tick(LivingEntity victim, Caught caught) {
        if (!(victim.level() instanceof ServerLevel level) || !victim.isAlive()) {
            MINDS.remove(victim);
            return;
        }
        long now = level.getGameTime();
        if (now >= caught.until) {
            release(victim, false);
            return;
        }
        long age = now - caught.start;
        // A mind stronger than the caster's may tear its way out.
        if (age > 0 && age % MIND_CHECK_TICKS == 0 && caught.caster != null && caught.caster.isAlive()) {
            float ratio = SpiritTechniques.mind(victim) / Math.max(1f, SpiritTechniques.mind(caught.caster));
            float chance = Math.max(0f, (ratio - 1f) * 10f * MIND_BREAK_PER_TENTH);
            if (chance > 0f && victim.getRandom().nextFloat() < chance) {
                SpiritTechniques.releaseFrom(victim);
                release(victim, true);
                if (caught.caster != null) {
                    message(caught.caster, Component.translatable("message.noirm.genjutsu.woken", victim.getDisplayName()));
                }
                return;
            }
        }
        if (age % 5 == 0) {
            swirl(level, victim, 0.8f);
        }
        if (victim instanceof Mob mob && age >= MOB_STUN_TICKS) {
            react(level, mob, caught, now);
        }
    }

    /** A faint swirl over the head of whoever is caught: the caster can see it worked. */
    private static void swirl(ServerLevel level, LivingEntity victim, float strength) {
        Vec3 head = victim.position().add(0, victim.getBbHeight() + 0.3, 0);
        float t = (victim.tickCount % 40) / 40f * Mth.TWO_PI;
        // A small spiral of ink and violet light turning over the head (the caster can see it worked).
        for (int i = 0; i < 4; i++) {
            float a = t + i * Mth.TWO_PI / 4;
            float r = 0.22f + 0.08f * i;
            Vec3 at = head.add(Math.cos(a) * r, 0.04 * i, Math.sin(a) * r);
            Vfx.one(level, Vfx.glow(SpiritVfx.YIN.mid(), SpiritVfx.YIN.edge(), 0.2f * strength, 0.05f, 10), at,
                    new Vec3(-Math.sin(a) * 0.03, 0.004, Math.cos(a) * 0.03));
            Vfx.one(level, Vfx.smoke(0x120818, 0.16f * strength, 12), at.add(0, -0.05, 0), new Vec3(-Math.sin(a) * 0.02, 0.01, Math.cos(a) * 0.02));
        }
        if (victim.tickCount % 10 == 0) {
            Vfx.burst(level, Vfx.ring(SpiritVfx.YIN.mid(), 0.7f * strength, 10), head, 1, 0, 0);
        }
    }

    /** What a caught creature does about what it sees. */
    private static void react(ServerLevel level, Mob mob, Caught caught, long now) {
        var random = mob.getRandom();
        switch (caught.scene.reaction()) {
            case FREEZE -> {
                // Rooted, trembling, looking wildly about.
                mob.setTarget(null);
                mob.getNavigation().stop();
                mob.setDeltaMovement(0, Math.min(0, mob.getDeltaMovement().y), 0);
                if (now % 3 == 0) {
                    float yaw = mob.getYRot() + (random.nextFloat() - 0.5f) * 50f;
                    mob.setYRot(yaw);
                    mob.setYHeadRot(yaw + (random.nextFloat() - 0.5f) * 60f);
                    mob.setXRot((random.nextFloat() - 0.3f) * 40f);
                }
            }
            case WANDER -> {
                mob.setTarget(null);
                if (now >= caught.nextMove || mob.getNavigation().isDone()) {
                    caught.nextMove = now + 30 + random.nextInt(30);
                    double angle = random.nextDouble() * Math.PI * 2;
                    Vec3 to = mob.position().add(Math.cos(angle) * 5, 0, Math.sin(angle) * 5);
                    mob.getNavigation().moveTo(to.x, to.y, to.z, 0.7);
                }
                if (now % 4 == 0) {
                    mob.setYHeadRot(mob.getYRot() + (random.nextFloat() - 0.5f) * 90f);
                }
            }
            case ATTACK_PHANTOMS -> {
                LivingEntity current = mob.getTarget();
                if (current != null && current != caught.caster && current.isAlive()) {
                    return; // it's busy with a real enemy (False Surroundings turned it on someone)
                }
                mob.setTarget(null);
                if (caught.phantom == null || now >= caught.nextMove) {
                    caught.nextMove = now + 25 + random.nextInt(20);
                    if (caught.decoy != null) {
                        caught.phantom = caught.decoy.add((random.nextDouble() - 0.5) * 1.5, 0, (random.nextDouble() - 0.5) * 1.5);
                    } else {
                        double angle = random.nextDouble() * Math.PI * 2;
                        caught.phantom = mob.position().add(Math.cos(angle) * 2.5, 0, Math.sin(angle) * 2.5);
                    }
                }
                Vec3 p = caught.phantom;
                mob.getLookControl().setLookAt(p.x, p.y + 1.2, p.z);
                if (mob.position().distanceTo(p) > 1.6) {
                    mob.getNavigation().moveTo(p.x, p.y, p.z, 1.0);
                } else {
                    mob.getNavigation().stop();
                    float yaw = (float) (Mth.atan2(p.z - mob.getZ(), p.x - mob.getX()) * Mth.RAD_TO_DEG) - 90f;
                    mob.setYRot(yaw);
                    mob.setYBodyRot(yaw);
                    if (now % 12 == 0) {
                        if (mob instanceof SparringNinja ninja) {
                            CombatSystem.requestAttack(ninja, random.nextBoolean() ? AttackInput.LIGHT : AttackInput.HEAVY);
                        } else {
                            mob.swing(InteractionHand.MAIN_HAND, true);
                        }
                    }
                }
            }
            case TURN_ON_ALLIES -> {
                LivingEntity current = mob.getTarget();
                if (current == null || !current.isAlive() || current == caught.caster) {
                    LivingEntity caster = caught.caster;
                    LivingEntity best = level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(12),
                                    e -> e != mob && e != caster && e.isAlive() && !e.isSpectator()
                                            && (caster == null || !JutsuEffects.isFriendly(caster, e)))
                            .stream()
                            .min(Comparator.<LivingEntity>comparingInt(e -> JutsuEffects.isFriendly(mob, e) || e.getType() == mob.getType() ? 0 : 1)
                                    .thenComparingDouble(mob::distanceTo))
                            .orElse(null);
                    mob.setTarget(best);
                }
            }
        }
    }

    private static void message(LivingEntity to, Component text) {
        if (to instanceof net.minecraft.world.entity.player.Player player) {
            player.displayClientMessage(text, true);
        }
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        MINDS.clear();
    }
}
