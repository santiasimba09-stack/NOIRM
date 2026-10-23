package com.noirm.jutsu;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.noirm.Noirm;
import com.noirm.clan.Nature;
import com.noirm.data.CodecJsonLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Canon elemental clashes: two jutsu projectiles from different sides meeting mid-air. The nature
 * wheel (water beats fire, fire beats wind, wind beats lightning, lightning beats earth, earth
 * beats water) multiplies the advantaged side's power; then the stronger one flies on, weakened,
 * and the other is destroyed. Near-equal power: both burst in a cross-element explosion.
 *
 * <p>The wheel and the numbers come from {@code data/<ns>/jutsu_clash/*.json} (the last file
 * loaded wins; {@code noirm:rules} ships with the mod).
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class JutsuClash {
    /** How a projectile's clash ended (for tests and effects). */
    public enum Outcome { NONE, WON, LOST, BURST }

    /**
     * @param beats            nature -> the natures it beats
     * @param advantage        power multiplier for the nature with the advantage
     * @param equalMargin      powers closer than this fraction of the stronger count as equal: both burst
     * @param winnerMinRemaining the winner keeps at least this fraction of its power
     * @param reach            extra distance (blocks) beyond the two balls' radii that counts as meeting
     * @param checkInterval    ticks between checks (each check looks that far ahead, so fast ones can't pass through)
     * @param burstRadius      radius of the cross-element explosion
     * @param burstDamage      its damage, as a fraction of the two projectiles' average damage
     */
    public record Rules(Map<Nature, List<Nature>> beats, float advantage, float equalMargin, float winnerMinRemaining,
                        float reach, int checkInterval, float burstRadius, float burstDamage) {
        public static final Codec<Rules> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(Nature.CODEC, Nature.CODEC.listOf()).optionalFieldOf("beats", Map.of()).forGetter(Rules::beats),
                Codec.floatRange(1, 10).optionalFieldOf("advantage", 1.5f).forGetter(Rules::advantage),
                Codec.floatRange(0, 1).optionalFieldOf("equal_margin", 0.15f).forGetter(Rules::equalMargin),
                Codec.floatRange(0, 1).optionalFieldOf("winner_min_remaining", 0.25f).forGetter(Rules::winnerMinRemaining),
                Codec.floatRange(0, 4).optionalFieldOf("reach", 0.5f).forGetter(Rules::reach),
                Codec.intRange(1, 10).optionalFieldOf("check_interval", 2).forGetter(Rules::checkInterval),
                Codec.floatRange(0, 16).optionalFieldOf("burst_radius", 2.5f).forGetter(Rules::burstRadius),
                Codec.floatRange(0, 4).optionalFieldOf("burst_damage", 0.6f).forGetter(Rules::burstDamage)
        ).apply(i, Rules::new));

        public static final Rules DEFAULT = new Rules(Map.of(
                Nature.WATER, List.of(Nature.FIRE), Nature.FIRE, List.of(Nature.WIND), Nature.WIND, List.of(Nature.LIGHTNING),
                Nature.LIGHTNING, List.of(Nature.EARTH), Nature.EARTH, List.of(Nature.WATER)),
                1.5f, 0.15f, 0.25f, 0.5f, 2, 2.5f, 0.6f);
    }

    private static Rules rules = Rules.DEFAULT;
    /** {@code BEATS[a][b]}: nature {@code a} has the advantage over {@code b}. Built once per load. */
    private static boolean[][] beatsTable = table(Rules.DEFAULT);
    /** Every jutsu projectile in a server world, so a check walks a short list instead of querying the world. */
    private static final List<JutsuProjectile> LIVE = new ArrayList<>();

    private JutsuClash() {}

    public static Rules rules() {
        return rules;
    }

    /** Does {@code a} beat {@code b} on the wheel? */
    public static boolean beats(Nature a, Nature b) {
        return a != null && b != null && beatsTable[a.ordinal()][b.ordinal()];
    }

    static void track(JutsuProjectile projectile) {
        if (!LIVE.contains(projectile)) {
            LIVE.add(projectile);
        }
    }

    static void untrack(JutsuProjectile projectile) {
        LIVE.remove(projectile);
    }

    /**
     * Called by a projectile every {@link Rules#checkInterval} ticks: does it meet an enemy
     * projectile before the next check? Resolves the clash if so. Returns true if {@code self} is gone.
     * Walks the live list by index: nothing is allocated unless a clash happens.
     */
    static boolean check(JutsuProjectile self) {
        Rules r = rules;
        double ahead = r.checkInterval();
        Vec3 va = self.getDeltaMovement();
        for (int i = 0; i < LIVE.size(); i++) {
            JutsuProjectile other = LIVE.get(i);
            if (other == self || other.isRemoved() || other.level() != self.level()) {
                continue;
            }
            // Closest approach over the next `ahead` ticks (both fly straight in that time).
            double dx = other.getX() - self.getX(), dy = other.getY() - self.getY(), dz = other.getZ() - self.getZ();
            Vec3 vb = other.getDeltaMovement();
            double vx = vb.x - va.x, vy = vb.y - va.y, vz = vb.z - va.z;
            double vv = vx * vx + vy * vy + vz * vz;
            double t = vv < 1e-9 ? 0 : Math.max(0, Math.min(ahead, -(dx * vx + dy * vy + dz * vz) / vv));
            double cx = dx + vx * t, cy = dy + vy * t, cz = dz + vz * t;
            double meet = (self.size() + other.size()) * 0.5 + r.reach();
            if (cx * cx + cy * cy + cz * cz > meet * meet || !opposed(self, other)) {
                continue;
            }
            resolve(self, other, r);
            return self.isRemoved();
        }
        return false;
    }

    /** Different casters, not on the same side. */
    private static boolean opposed(JutsuProjectile a, JutsuProjectile b) {
        Entity oa = a.getOwner();
        Entity ob = b.getOwner();
        return oa == null || ob == null || (oa != ob && !JutsuEffects.isFriendly(oa, ob));
    }

    /** The clash: the wheel scales each side's power, then the stronger wins, or both burst. */
    public static Outcome resolve(JutsuProjectile a, JutsuProjectile b, Rules r) {
        float ea = a.clashPower();
        float eb = b.clashPower();
        if (beats(a.nature(), b.nature())) {
            ea *= r.advantage();
        }
        if (beats(b.nature(), a.nature())) {
            eb *= r.advantage();
        }
        float high = Math.max(ea, eb);
        if (high <= 0 || Math.abs(ea - eb) <= r.equalMargin() * high) {
            burstTogether(a, b, r);
            return Outcome.BURST;
        }
        JutsuProjectile winner = ea > eb ? a : b;
        JutsuProjectile loser = winner == a ? b : a;
        float remaining = Math.max(r.winnerMinRemaining(), (high - Math.min(ea, eb)) / high);
        Vec3 contact = a.position().add(b.position()).scale(0.5);
        com.noirm.vfx.Vfx.flash(winner.level(), contact, winner.palette(), 0.6f + winner.size());
        winner.clashWon(remaining);
        loser.clashLost(winner.nature());
        return winner == a ? Outcome.WON : Outcome.LOST;
    }

    /** Both blow up where they meet: an explosion in both colours that hurts whoever stands near. */
    private static void burstTogether(JutsuProjectile a, JutsuProjectile b, Rules r) {
        if (!(a.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 at = a.position().add(b.position()).scale(0.5);
        float radius = r.burstRadius() + 0.25f * (a.blastRadius() + b.blastRadius());
        float damage = (a.damage() + b.damage()) * 0.5f * r.burstDamage();
        Entity ownerA = a.getOwner();
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                e -> e.isAlive() && e.distanceToSqr(at) <= radius * radius)) {
            // Hurt by whichever side isn't theirs (no friendly fire).
            JutsuProjectile source = ownerA != null && JutsuEffects.isFriendly(ownerA, victim) ? b : a;
            float falloff = 1f - (float) Math.min(1, Math.sqrt(victim.distanceToSqr(at)) / (radius + 1));
            JutsuEffects.hit(source, victim, damage * (0.5f + 0.5f * falloff), 0.8f, damage, 0, victim.position().subtract(at));
        }
        com.noirm.vfx.Vfx.explosion(level, at, a.palette(), radius);
        com.noirm.vfx.Vfx.explosion(level, at, b.palette(), radius * 0.8f);
        float destruction = Math.max(0.5f, damage / 8f);
        // The ground takes what each nature does to it (Destruction honours noirmTerrainDamage).
        com.noirm.world.Destruction.natureImpact(level, at, a.nature(), radius * 0.5f, destruction);
        if (b.nature() != a.nature()) {
            com.noirm.world.Destruction.natureImpact(level, at, b.nature(), radius * 0.5f, destruction);
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1f + radius * 0.1f, 0.9f);
        a.clashBurst();
        b.clashBurst();
    }

    private static boolean[][] table(Rules r) {
        int n = Nature.values().length;
        boolean[][] out = new boolean[n][n];
        r.beats().forEach((winner, losers) -> losers.forEach(loser -> out[winner.ordinal()][loser.ordinal()] = true));
        return out;
    }

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CodecJsonLoader<>("jutsu_clash", Rules.CODEC, loaded -> {
            Rules chosen = loaded.getOrDefault(Noirm.id("rules"), loaded.isEmpty() ? Rules.DEFAULT : loaded.values().iterator().next());
            rules = chosen;
            beatsTable = table(chosen);
        }));
    }
}
