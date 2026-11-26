package com.noirm.dojutsu;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.noirm.Noirm;
import com.noirm.combat.CombatState;
import com.noirm.combat.CombatStates;
import com.noirm.combat.CombatSystem;
import com.noirm.data.CodecJsonLoader;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.player.ShinobiCapability;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Gentle Fist's own arts (wave "Hyūga", the owner's exception to "nothing new"). Numbers live in
 * {@code data/noirm/gentle_fist/tuning.json} ({@link Tuning}; {@code /reload} applies changes).
 * <ol>
 *   <li><b>Targeted tenketsu strikes.</b> With the Byakugan open, where your palm lands picks the points it closes:
 *       aim low for the <b>legs</b> (Slowness, stacking), high for the <b>arms</b> (Weakness: their blows hit softer),
 *       the middle for the <b>chest</b> (more points and chakra). Players aim with their view; NPCs pick a region.</li>
 *   <li><b>Organ strikes.</b> A heavy Gentle Fist hit leaves the organs bleeding inside: a share of the hit again as
 *       internal damage (ignores armour) every second for a few seconds.</li>
 *   <li><b>Chakra burst.</b> Pushing chakra into someone weaving hand seals bursts their flow: any Gentle Fist hit
 *       breaks a jutsu in its seal phase (it fizzles); a heavy one also cancels a charge or a channel.</li>
 *   <li><b>Reopening tenketsu.</b> A Byakugan user sees their own network and forces closed points back open
 *       (as Neji did): their closed points reopen every second and a seal on them wears off faster.</li>
 *   <li><b>Palm parry.</b> Raise your guard with the eye open and a projectile that reaches you in the (wider) parry
 *       window is turned back at its thrower by a chakra palm.</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class GentleFistArts {
    /** Where a palm landed. */
    public enum Region { LEGS, CHEST, ARMS }

    /**
     * Every number of the arts.
     *
     * @param legsBelow         a hit below this share of the target's height strikes the legs…
     * @param armsAbove         …above this one the arms (shoulders, upper arms); in between the chest
     * @param regionTicks       how long the leg (Slowness) and arm (Weakness) effects last, ticks
     * @param legsMaxLevel      highest Slowness level stacked by leg strikes (0 = I)
     * @param armsMaxLevel      highest Weakness level stacked by arm strikes
     * @param chestPoints       chest strikes close this many times the points and drain as much more chakra
     * @param bleedShare        organ strike: this share of the heavy hit's damage again every second…
     * @param bleedSeconds      …for this many seconds
     * @param burstHeavyOnly    true = only heavy hits break seals (default false: any Gentle Fist hit does)
     * @param reopenPerSecond   own closed points a Byakugan user reopens per second
     * @param reopenSealSpeed   a seal on a Byakugan user wears off this many times as fast
     * @param palmParryWindow   the palm parry's window, in multiples of the usual parry window
     * @param palmParrySpeed    the turned-back projectile's speed, in multiples of its own
     */
    public record Tuning(float legsBelow, float armsAbove, int regionTicks, int legsMaxLevel, int armsMaxLevel, float chestPoints,
                         float bleedShare, int bleedSeconds, boolean burstHeavyOnly, int reopenPerSecond, float reopenSealSpeed,
                         float palmParryWindow, float palmParrySpeed) {
        public static final Tuning DEFAULT = new Tuning(0.42f, 0.72f, 100, 2, 1, 1.5f, 0.25f, 4, false, 2, 2f, 2f, 1.2f);
        public static final Codec<Tuning> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, 1).optionalFieldOf("legs_below", DEFAULT.legsBelow).forGetter(Tuning::legsBelow),
                Codec.floatRange(0, 1).optionalFieldOf("arms_above", DEFAULT.armsAbove).forGetter(Tuning::armsAbove),
                Codec.intRange(0, 2400).optionalFieldOf("region_ticks", DEFAULT.regionTicks).forGetter(Tuning::regionTicks),
                Codec.intRange(0, 4).optionalFieldOf("legs_max_level", DEFAULT.legsMaxLevel).forGetter(Tuning::legsMaxLevel),
                Codec.intRange(0, 4).optionalFieldOf("arms_max_level", DEFAULT.armsMaxLevel).forGetter(Tuning::armsMaxLevel),
                Codec.floatRange(0, 10).optionalFieldOf("chest_points", DEFAULT.chestPoints).forGetter(Tuning::chestPoints),
                Codec.floatRange(0, 5).optionalFieldOf("bleed_share", DEFAULT.bleedShare).forGetter(Tuning::bleedShare),
                Codec.intRange(0, 60).optionalFieldOf("bleed_seconds", DEFAULT.bleedSeconds).forGetter(Tuning::bleedSeconds),
                Codec.BOOL.optionalFieldOf("burst_heavy_only", DEFAULT.burstHeavyOnly).forGetter(Tuning::burstHeavyOnly),
                Codec.intRange(0, 100).optionalFieldOf("reopen_per_second", DEFAULT.reopenPerSecond).forGetter(Tuning::reopenPerSecond),
                Codec.floatRange(1, 20).optionalFieldOf("reopen_seal_speed", DEFAULT.reopenSealSpeed).forGetter(Tuning::reopenSealSpeed),
                Codec.floatRange(0, 10).optionalFieldOf("palm_parry_window", DEFAULT.palmParryWindow).forGetter(Tuning::palmParryWindow),
                Codec.floatRange(0, 5).optionalFieldOf("palm_parry_speed", DEFAULT.palmParrySpeed).forGetter(Tuning::palmParrySpeed)
        ).apply(i, Tuning::new));
    }

    private static Tuning tuning = Tuning.DEFAULT;
    private static final Map<LivingEntity, Bleed> BLEEDS = new WeakHashMap<>();

    private GentleFistArts() {}

    public static Tuning tuning() {
        return tuning;
    }

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CodecJsonLoader<>("gentle_fist", Tuning.CODEC,
                loaded -> tuning = loaded.getOrDefault(Noirm.id("tuning"), Tuning.DEFAULT)));
    }

    // ---------------------------------------------------------------- 1. targeted tenketsu strikes

    /** Where {@code attacker}'s palm lands on {@code target}: players by where they look, NPCs by choice. */
    public static Region regionOf(LivingEntity attacker, LivingEntity target) {
        if (!(attacker instanceof Player)) {
            return Region.values()[attacker.getRandom().nextInt(Region.values().length)];
        }
        AABB box = target.getBoundingBox().inflate(0.15);
        Vec3 eye = attacker.getEyePosition();
        Vec3 end = eye.add(attacker.getViewVector(1f).scale(6));
        double y = box.clip(eye, end).map(v -> v.y).orElse(eye.y);
        return regionAt((float) ((y - target.getY()) / Math.max(0.1, target.getBbHeight())));
    }

    /** The region at this share of the body's height (0 = feet, 1 = top of the head). */
    public static Region regionAt(float height) {
        return height < tuning.legsBelow ? Region.LEGS : height > tuning.armsAbove ? Region.ARMS : Region.CHEST;
    }

    /** Points multiplier for a region (the chest closes more). */
    public static float pointsFor(Region region) {
        return region == Region.CHEST ? tuning.chestPoints : 1f;
    }

    /** The region's lasting effect: Slowness on the legs, Weakness on the arms, stacking a level per strike. */
    public static void strikeRegion(LivingEntity attacker, LivingEntity target, Region region) {
        if (region == Region.CHEST) {
            return; // the chest's gift is in the points (pointsFor)
        }
        MobEffect effect = region == Region.LEGS ? MobEffects.MOVEMENT_SLOWDOWN : MobEffects.WEAKNESS;
        int max = region == Region.LEGS ? tuning.legsMaxLevel : tuning.armsMaxLevel;
        MobEffectInstance now = target.getEffect(effect);
        int level = now == null ? 0 : Math.min(max, now.getAmplifier() + 1);
        target.addEffect(new MobEffectInstance(effect, tuning.regionTicks, level, false, true, true), attacker);
        if (attacker instanceof Player player) {
            player.displayClientMessage(Component.translatable("message.noirm.gentle_fist." + region.name().toLowerCase(java.util.Locale.ROOT)), true);
        }
    }

    // ---------------------------------------------------------------- the hook from Byakugan.gentleFist

    /**
     * A Gentle Fist hit landed (called by {@link Byakugan#gentleFist}). Returns the points multiplier for where it
     * landed; applies the region, the organ strike and the chakra burst.
     */
    public static float onGentleFist(LivingEntity attacker, LivingEntity target, boolean heavy, float damage) {
        Region region = regionOf(attacker, target);
        strikeRegion(attacker, target, region);
        if (heavy && damage > 0f) {
            organStrike(attacker, target, damage);
        }
        chakraBurst(attacker, target, heavy);
        return pointsFor(region);
    }

    // ---------------------------------------------------------------- 2. organ strikes

    /** Starts (or refreshes, keeping the stronger) internal bleeding from a heavy hit of {@code damage}. */
    public static void organStrike(LivingEntity attacker, LivingEntity target, float damage) {
        float perSecond = damage * tuning.bleedShare;
        if (perSecond <= 0f || tuning.bleedSeconds <= 0) {
            return;
        }
        long until = target.level().getGameTime() + 20L * tuning.bleedSeconds;
        Bleed old = BLEEDS.get(target);
        BLEEDS.put(target, new Bleed(attacker, Math.max(perSecond, old == null ? 0f : old.perSecond), until));
    }

    /** True while {@code entity} bleeds inside. */
    public static boolean bleeding(LivingEntity entity) {
        Bleed bleed = BLEEDS.get(entity);
        return bleed != null && entity.level().getGameTime() <= bleed.until;
    }

    // ---------------------------------------------------------------- 3. chakra burst

    /** Breaks a jutsu in its seal phase (and, on a heavy hit, a charge or channel). True if something was broken. */
    public static boolean chakraBurst(LivingEntity attacker, LivingEntity target, boolean heavy) {
        JutsuCaster.Casting casting = JutsuCaster.casting(target);
        if (casting == null || (tuning.burstHeavyOnly && !heavy) || (!casting.sealing() && !heavy)) {
            return false;
        }
        JutsuCaster.fizzle(target, casting);
        if (attacker instanceof Player player) {
            player.displayClientMessage(Component.translatable("message.noirm.gentle_fist.burst"), true);
        }
        return true;
    }

    // ---------------------------------------------------------------- 4. reopening tenketsu

    /** Can {@code entity} see and reopen its own points (a Byakugan bearer: players born with it, NPCs with it open)? */
    public static boolean canReopen(LivingEntity entity) {
        if (entity instanceof Player player) {
            return ShinobiCapability.get(player).map(d -> d.eyes().hasByakugan()).orElse(false);
        }
        return Byakugan.isActive(entity);
    }

    // ---------------------------------------------------------------- ticking (from HyugaTechniques.tick)

    /** Every server tick for every living entity: bleeding, reopening points. */
    public static void tick(LivingEntity entity) {
        Bleed bleed = BLEEDS.get(entity);
        if (bleed != null) {
            long now = entity.level().getGameTime();
            if (now > bleed.until || !entity.isAlive()) {
                BLEEDS.remove(entity);
            } else if ((bleed.until - now) % 20 == 0) {
                LivingEntity source = bleed.attacker.isAlive() ? bleed.attacker : entity;
                Byakugan.internalDamage(source, entity, bleed.perSecond);
            }
        }
        if (canReopen(entity)) {
            Byakugan.reopen(entity, entity.tickCount % 20 == 0 ? tuning.reopenPerSecond : 0, tuning.reopenSealSpeed);
        }
    }

    // ---------------------------------------------------------------- 5. palm parry

    /** Would {@code defender} turn this projectile back with a chakra palm right now? */
    public static boolean palmParries(LivingEntity defender, Projectile projectile) {
        if (!Byakugan.isActive(defender)) {
            return false;
        }
        CombatState state = CombatStates.getIfPresent(defender);
        if (state == null || !state.isGuarding()) {
            return false;
        }
        Entity thrower = projectile.getOwner();
        if (thrower != null && Byakugan.inBlindSpot(defender, thrower)) {
            return false; // the eye has to see it coming
        }
        long since = defender.level().getGameTime() - state.guardStartTick();
        return since <= Math.round(CombatSystem.parryWindow(defender) * tuning.palmParryWindow);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void projectile(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide() || !(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity defender) || projectile.getOwner() == defender
                || !palmParries(defender, projectile)) {
            return;
        }
        turnBack(defender, projectile);
        event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
    }

    /** Sends {@code projectile} back where it came from, now the defender's. */
    public static void turnBack(LivingEntity defender, Projectile projectile) {
        Vec3 motion = projectile.getDeltaMovement();
        Entity thrower = projectile.getOwner();
        Vec3 back = thrower != null ? thrower.getEyePosition().subtract(projectile.position()).normalize() : motion.normalize().scale(-1);
        projectile.setDeltaMovement(back.scale(Math.max(0.6, motion.length()) * tuning.palmParrySpeed));
        projectile.setOwner(defender);
        projectile.hurtMarked = true;
        defender.level().playSound(null, defender.getX(), defender.getY(), defender.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.7f);
        if (defender.level() instanceof ServerLevel level) {
            com.noirm.vfx.Vfx.Palette chakra = new com.noirm.vfx.Vfx.Palette(0xFFFFFF, 0x9FE0FF, 0x0A2A6A, 0xE0F8FF);
            com.noirm.vfx.Vfx.burst(level, com.noirm.vfx.Vfx.glow(chakra.mid(), chakra.edge(), 0.5f, 0.15f, 6), projectile.position(), 8, 0.2, 0.05);
        }
        if (defender instanceof Player player) {
            player.displayClientMessage(Component.translatable("message.noirm.gentle_fist.palm_parry"), true);
        }
    }

    private record Bleed(LivingEntity attacker, float perSecond, long until) {}
}
