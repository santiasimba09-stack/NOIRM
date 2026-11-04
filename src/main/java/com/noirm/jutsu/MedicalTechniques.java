package com.noirm.jutsu;

import com.noirm.Noirm;
import com.noirm.combat.CombatSystem;
import com.noirm.network.SpiritPackets;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.vfx.SpiritVfx;
import com.noirm.vfx.Vfx;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
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
 * Medical ninjutsu (chakra wave, part 1), the calm, precise side of chakra control. Soft green,
 * slow motion; the poisons are sickly violet.
 * <ul>
 *   <li><b>medic_palm</b> (Deep Mend, channel): heal the friend you look at (or yourself) for
 *       {@code damage} a second, drawing out one harmful effect each second.</li>
 *   <li><b>scalpel_strike</b>: a short lunge and a cut under the skin: {@code damage} that ignores
 *       armour, posture damage, and cut muscles ({@code effect}).</li>
 *   <li><b>poison_mist</b>: a violet cloud drifts out {@code range} blocks and hangs there for
 *       {@code duration} ticks, poisoning enemies inside ({@code effect}).</li>
 *   <li><b>extraction</b> (the antidote): draw every harmful effect out of a friend or yourself,
 *       and heal {@code damage}.</li>
 *   <li><b>healing_field</b>: a circle of {@code radius} on the ground for {@code duration} ticks;
 *       you and your side inside heal {@code damage} a second.</li>
 *   <li><b>derangement</b> (Body Pathway Derangement): a palm to the body scrambles its signals for
 *       {@code duration} ticks. A player's movement keys turn around (forward is back, left is
 *       right; a strong will shortens it); an NPC stumbles and loses track of its target.</li>
 *   <li><b>hundred_healings</b> (charge): a long charge opens the seal on the forehead: for
 *       {@code duration} ticks the body heals {@code damage} a second, and once, when it drops low,
 *       it snaps back by {@link #SEAL_SNAP_HEAL} of max health.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class MedicalTechniques {
    /** The Hundred Healings seal snaps the body back once, below this share of max health... */
    public static final float SEAL_SNAP_BELOW = 0.35f;
    /** ...by this share of max health. */
    public static final float SEAL_SNAP_HEAL = 0.25f;
    /** Derangement on a player: a strong will shortens it, down to this share. */
    public static final float DERANGE_MIN_SHARE = 0.4f;
    /** WIL above the default takes this share off per point. */
    public static final float DERANGE_WIL_CUT = 0.012f;

    private record Seal(long until, float perTick, boolean[] snapped) {}

    private static final Map<LivingEntity, Seal> SEALS = new WeakHashMap<>();
    private static final Map<LivingEntity, Long> DERANGED = new WeakHashMap<>();

    private MedicalTechniques() {}

    /** The friend (crew mate, clone) you look at within {@code range}, or null. */
    static LivingEntity friendInView(LivingEntity caster, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1f);
        return caster.level().getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(range),
                        e -> e != caster && e.isAlive() && !e.isSpectator() && JutsuEffects.isFriendly(caster, e) && caster.hasLineOfSight(e)
                                && e.distanceTo(caster) <= range)
                .stream()
                .filter(e -> look.dot(e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye).normalize()) > Math.cos(Math.toRadians(30)))
                .min(Comparator.comparingDouble(caster::distanceTo))
                .orElse(null);
    }

    private static Vec3 hands(LivingEntity caster) {
        return caster.getEyePosition().add(caster.getViewVector(1f).scale(0.7)).add(0, -0.45, 0);
    }

    /** Removes the first harmful effect found; true if one went. */
    private static boolean drawOutOne(LivingEntity patient) {
        for (MobEffectInstance effect : new ArrayList<>(patient.getActiveEffects())) {
            if (!effect.getEffect().isBeneficial()) {
                patient.removeEffect(effect.getEffect());
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- Deep Mend

    static final JutsuBehavior MEDIC_PALM = new JutsuBehavior() {
        @Override
        public void cast(JutsuContext ctx) {
            JutsuBehaviors.sound(ctx.caster(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.6f);
        }

        @Override
        public void channel(JutsuContext ctx, int tick) {
            LivingEntity caster = ctx.caster();
            LivingEntity friend = friendInView(caster, ctx.params().range());
            LivingEntity patient = friend != null ? friend : caster;
            patient.heal(ctx.params().damage() * ctx.power() / 20f);
            if (tick % 20 == 0 && drawOutOne(patient)) {
                Vfx.burst(caster.level(), Vfx.glow(SpiritVfx.POISON.mid(), SpiritVfx.POISON.edge(), 0.25f, 0.05f, 12),
                        patient.position().add(0, patient.getBbHeight() * 0.6, 0), 4, 0.2, 0.03);
            }
            if (tick % 2 == 0) {
                SpiritVfx.mend(caster.level(), patient, hands(caster), 2);
            }
            if (tick % 20 == 1) {
                SpiritVfx.pulse(caster.level(), patient.position(), 1.2f);
            }
        }
    };

    // ---------------------------------------------------------------- Scalpel Strike

    static void scalpelStrike(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        Vec3 dir = ctx.flatLook();
        Vec3 start = caster.position();
        Vec3 end = start;
        LivingEntity victim = null;
        for (double d = 0.5; d <= p.range(); d += 0.5) {
            Vec3 next = start.add(dir.scale(d));
            AABB box = caster.getBoundingBox().move(next.subtract(start));
            if (!level.noCollision(caster, box)) {
                break;
            }
            var hits = level.getEntitiesOfClass(LivingEntity.class, box.inflate(0.5), e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator());
            if (!hits.isEmpty()) {
                victim = hits.get(0);
                break;
            }
            end = next;
        }
        caster.teleportTo(end.x, end.y, end.z);
        Vfx.streak(level, start, end, SpiritVfx.MEDICAL);
        // The cut: a thin, clean arc of chakra across the front.
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        Vec3 front = end.add(0, caster.getBbHeight() * 0.6, 0).add(dir.scale(0.9));
        for (int i = -5; i <= 5; i++) {
            Vec3 at = front.add(side.scale(i * 0.12)).add(0, -i * 0.06, 0).subtract(dir.scale(Math.abs(i) * 0.04));
            Vfx.one(level, Vfx.glow(SpiritVfx.MEDICAL.core(), SpiritVfx.MEDICAL.mid(), 0.12f, 0.02f, 5), at, side.scale(0.02));
        }
        JutsuBehaviors.sound(caster, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.9f);
        if (victim == null) {
            return;
        }
        JutsuEffects.hit(caster, victim, p.damage() * ctx.power(), p.knockback(), p.posture() * ctx.power(), 0, dir);
        JutsuEffects.applyEffects(victim, p.effect(), p.effectTicks());
        CombatSystem.stun(victim, p.stun());
        Vec3 contact = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
        Vfx.radial(level, Vfx.shard(SpiritVfx.MEDICAL.mid(), 0.12f, 8), contact, 8, 0.3);
        Vfx.burst(level, Vfx.glow(0xFFFFFF, SpiritVfx.MEDICAL.mid(), 0.5f, 0.1f, 4), contact, 1, 0, 0);
        SpiritTechniques.hitstop(caster, 3);
        SpiritTechniques.hitstop(victim, 3);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.8f, 1.8f);
    }

    // ---------------------------------------------------------------- Poison Mist

    static void poisonMist(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        Vec3 dir = ctx.look();
        Vec3 origin = caster.getEyePosition().add(dir.scale(1.2)).add(0, -0.4, 0);
        float radius = p.radius() * (float) Math.sqrt(Math.max(0.5f, ctx.power()));
        int drift = 14;
        int life = drift + Math.max(20, p.duration());
        double reach = Math.max(1.5, p.range() * 0.7);
        int[] age = {0};
        JutsuBehaviors.sound(caster, SoundEvents.FIRE_EXTINGUISH, 1f, 0.6f);
        GreatTechniques.later(now -> {
            age[0]++;
            Vec3 center = origin.add(dir.scale(reach * Math.min(1.0, age[0] / (double) drift)));
            float grown = radius * Math.min(1f, 0.4f + age[0] / (float) drift);
            if (age[0] % 2 == 0) {
                SpiritVfx.mist(level, center, grown, age[0] <= drift ? 6 : 3);
            }
            if (age[0] % 10 == 0) {
                for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(grown + 1),
                        e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator()
                                && e.position().add(0, e.getBbHeight() * 0.5, 0).distanceTo(center) <= grown + e.getBbWidth())) {
                    JutsuEffects.applyEffects(victim, p.effect(), p.effectTicks());
                    if (p.damage() > 0) {
                        JutsuEffects.hit(caster, victim, p.damage() * ctx.power(), 0, 0, 0, Vec3.ZERO);
                    }
                }
            }
            return age[0] >= life;
        });
    }

    // ---------------------------------------------------------------- Poison Extraction

    static void extraction(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        LivingEntity friend = friendInView(caster, ctx.params().range());
        LivingEntity patient = friend != null ? friend : caster;
        int drawn = 0;
        while (drawOutOne(patient)) {
            drawn++;
        }
        patient.heal(ctx.params().damage() * ctx.power());
        Vec3 body = patient.position().add(0, patient.getBbHeight() * 0.6, 0);
        Vec3 hand = hands(caster);
        // The poison is drawn out as a dark violet bead that gathers in the palm, then bursts.
        for (double d = 0; d < body.distanceTo(hand); d += 0.3) {
            Vec3 at = body.add(hand.subtract(body).normalize().scale(d));
            Vfx.one(level, Vfx.glow(SpiritVfx.POISON.mid(), SpiritVfx.POISON.edge(), 0.14f, 0.06f, 8), at, hand.subtract(at).normalize().scale(0.08));
        }
        Vfx.burst(level, Vfx.glow(SpiritVfx.POISON.accent(), SpiritVfx.POISON.mid(), 0.3f + 0.05f * drawn, 0.05f, 10), hand, 1, 0, 0);
        Vfx.burst(level, new com.noirm.vfx.VfxOptions(com.noirm.vfx.VfxKind.SMOKE, SpiritVfx.POISON.mid(), 0x201028, 0.3f, 0.8f, 20, -0.004f, 0.9f), hand, 4, 0.1, 0.02);
        SpiritVfx.mend(level, patient, null, 8);
        SpiritVfx.pulse(level, patient.position(), 1.4f);
        JutsuBehaviors.sound(caster, SoundEvents.BOTTLE_FILL_DRAGONBREATH, 1f, 1.2f);
        if (drawn > 0) {
            JutsuBehaviors.message(caster, Component.translatable("message.noirm.medical.cleansed"));
        }
    }

    // ---------------------------------------------------------------- Healing Field

    static void healingField(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        Vec3 center = caster.position();
        float radius = p.radius() * (float) Math.sqrt(Math.max(0.5f, ctx.power()));
        float perPulse = p.damage() * ctx.power() * 0.5f;
        int life = Math.max(20, p.duration());
        int[] age = {0};
        SpiritVfx.pulse(level, center, radius);
        JutsuBehaviors.sound(caster, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.4f);
        GreatTechniques.later(now -> {
            age[0]++;
            if (age[0] % 2 == 0) {
                SpiritVfx.field(level, center, radius, 22);
            }
            if (age[0] % 10 == 0) {
                for (LivingEntity friend : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius, 2, radius),
                        e -> e.isAlive() && !e.isSpectator() && JutsuEffects.isFriendly(caster, e) && e.position().distanceTo(center) <= radius + 0.5)) {
                    if (friend.getHealth() < friend.getMaxHealth()) {
                        friend.heal(perPulse);
                        SpiritVfx.mend(level, friend, null, 3);
                    }
                }
            }
            if (age[0] % 20 == 0) {
                SpiritVfx.pulse(level, center, radius);
            }
            return age[0] >= life;
        });
    }

    // ---------------------------------------------------------------- Body Pathway Derangement

    static void derangement(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), 25);
        Vec3 hand = hands(caster);
        Vfx.burst(level, Vfx.glow(SpiritVfx.MEDICAL.core(), SpiritVfx.MEDICAL.mid(), 0.6f, 0.1f, 5), hand, 1, 0, 0);
        if (target == null) {
            JutsuBehaviors.message(caster, Component.translatable("message.noirm.jutsu.no_target"));
            return;
        }
        JutsuEffects.hit(caster, target, p.damage() * ctx.power(), p.knockback(), p.posture() * ctx.power(), 0, target.position().subtract(caster.position()));
        int ticks = derangeTicks(target, Math.round(p.duration() * Math.min(1.3f, ctx.power())));
        derange(target, ticks);
        // Signals crossing: thin green threads crackle over the body.
        Vfx.arcs(level, target, 6, SpiritVfx.MEDICAL.mid());
        Vfx.burst(level, Vfx.ring(SpiritVfx.MEDICAL.mid(), 1.4f, 8), target.position().add(0, target.getBbHeight() * 0.6, 0), 1, 0, 0);
        JutsuBehaviors.sound(target, SoundEvents.ELDER_GUARDIAN_CURSE, 0.4f, 1.8f);
        JutsuBehaviors.message(caster, Component.translatable("message.noirm.medical.deranged", target.getDisplayName()));
    }

    /** How long derangement lasts on {@code target}: players with a strong will shake it off sooner. */
    public static int derangeTicks(LivingEntity target, int base) {
        if (target instanceof Player player) {
            float will = ShinobiCapability.get(player).map(ShinobiData::getWillpower).orElse(ShinobiData.DEFAULT_STAT);
            float share = Mth.clamp(1f - (will - ShinobiData.DEFAULT_STAT) * DERANGE_WIL_CUT, DERANGE_MIN_SHARE, 1f);
            return Math.max(10, Math.round(base * share));
        }
        return base;
    }

    public static void derange(LivingEntity target, int ticks) {
        DERANGED.put(target, target.level().getGameTime() + ticks);
        SpiritTechniques.vision(target, SpiritPackets.DERANGE, ticks);
    }

    public static boolean isDeranged(LivingEntity entity) {
        Long until = DERANGED.get(entity);
        return until != null && entity.level().getGameTime() < until;
    }

    // ---------------------------------------------------------------- Hundred Healings

    static void hundredHealings(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        JutsuDefinition.Params p = ctx.params();
        float strength = ctx.power() * (0.6f + 0.4f * ctx.charge());
        SEALS.put(caster, new Seal(caster.level().getGameTime() + Math.max(20, p.duration()), p.damage() * strength / 20f, new boolean[1]));
        SpiritVfx.seal(ctx.level(), caster, true);
        SpiritVfx.pulse(ctx.level(), caster.position(), 2.4f);
        Vfx.flash(ctx.level(), caster.getEyePosition(), SpiritVfx.MEDICAL, 1.2f);
        JutsuBehaviors.sound(caster, SoundEvents.BEACON_POWER_SELECT, 1f, 1.5f);
    }

    public static boolean hasSeal(LivingEntity entity) {
        Seal seal = SEALS.get(entity);
        return seal != null && entity.level().getGameTime() < seal.until();
    }

    // ---------------------------------------------------------------- ticking

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (Map.Entry<LivingEntity, Seal> entry : new ArrayList<>(SEALS.entrySet())) {
            LivingEntity body = entry.getKey();
            Seal seal = entry.getValue();
            if (!body.isAlive() || body.level().getGameTime() >= seal.until()) {
                SEALS.remove(body);
                continue;
            }
            if (body.getHealth() < body.getMaxHealth()) {
                body.heal(seal.perTick());
            }
            if (!seal.snapped()[0] && body.getHealth() < body.getMaxHealth() * SEAL_SNAP_BELOW) {
                seal.snapped()[0] = true;
                body.heal(body.getMaxHealth() * SEAL_SNAP_HEAL);
                SpiritVfx.seal(body.level(), body, true);
                Vfx.flash(body.level(), body.position().add(0, body.getBbHeight() * 0.6, 0), SpiritVfx.MEDICAL, 1.4f);
                body.level().playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.6f);
            }
            if (body.tickCount % 4 == 0) {
                SpiritVfx.seal(body.level(), body, false);
            }
            if (body.tickCount % 10 == 0) {
                SpiritVfx.mend(body.level(), body, null, 2);
            }
        }
        for (Map.Entry<LivingEntity, Long> entry : new ArrayList<>(DERANGED.entrySet())) {
            LivingEntity body = entry.getKey();
            if (!body.isAlive() || body.level().getGameTime() >= entry.getValue()) {
                DERANGED.remove(body);
                continue;
            }
            if (body.tickCount % 6 == 0 && body.level() instanceof ServerLevel level) {
                Vfx.arcs(level, body, 1, SpiritVfx.MEDICAL.mid());
            }
            // NPCs: the legs don't go where they're told and the eyes lose their target.
            if (!(body instanceof Player) && body.tickCount % 8 == 0) {
                double a = body.getRandom().nextDouble() * Math.PI * 2;
                body.setDeltaMovement(body.getDeltaMovement().add(Math.cos(a) * 0.22, 0, Math.sin(a) * 0.22));
                body.hurtMarked = true;
                if (body instanceof Mob mob) {
                    mob.getNavigation().stop();
                    if (body.getRandom().nextBoolean()) {
                        mob.setTarget(null);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        SEALS.clear();
        DERANGED.clear();
    }
}
