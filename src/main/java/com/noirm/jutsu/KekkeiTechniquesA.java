package com.noirm.jutsu;

import com.noirm.Noirm;
import com.noirm.clan.Nature;
import com.noirm.combat.CombatSystem;
import com.noirm.vfx.KekkeiVfxA;
import com.noirm.vfx.Vfx;
import com.noirm.world.Destruction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The chakra wave's combined natures, share A: Ice, Lava, Boil, Storm and Explosion. This class
 * holds Ice and what the five share (NPC use, the charge-up look, Lava Armour's burn); Lava and
 * Boil live in {@link KekkeiTechniquesALavaBoil}, Storm and Explosion in
 * {@link KekkeiTechniquesAStormBlast}. Their look is {@link KekkeiVfxA}.
 * <ul>
 *   <li><b>frost_field</b> (Freezing Field): frost races out from your feet to {@code radius} over
 *       {@code duration} ticks; whoever it reaches is hurt, slowed by {@code effect}, frozen for
 *       {@code stun} ticks, and crystals burst up around them.</li>
 *   <li><b>ice_mirrors</b> (Demonic Mirroring Ice Crystals): {@code count} mirrors of ice rise in a
 *       dome of {@code radius} around the enemy you look at (within {@code range}). For
 *       {@code duration} ticks a figure flashes from mirror to mirror and throws a senbon each time
 *       ({@code damage}, {@code stun}); the enemy is pulled back if it tries to leave. At the end
 *       every mirror shatters in one last volley.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class KekkeiTechniquesA {
    /** Behaviours NPC ninjas use from range (NinjaFightGoal reads these). */
    public static final Set<String> NPC_RANGED = Set.of("ice_mirrors", "quicklime", "volcano", "geyser", "steam_charge", "pressure_dome",
            "storm_cloud", "laser", "storm_tide", "bending_laser", "clay_bomb");
    /** Behaviours NPC ninjas use up close. The self-centred mega blast is left to players: its backlash would kill an NPC. */
    public static final Set<String> NPC_CLOSE = Set.of("frost_field", "acid_breath", "blast_fist");

    /** Ticks between two senbon throws inside the mirror dome. */
    public static final int MIRROR_THROW_INTERVAL = 5;
    /** The last volley: each mirror's senbon hits for this share of {@code damage}. */
    public static final float MIRROR_VOLLEY_SHARE = 1f;
    /** Lava Armour: an attacker within this many blocks is burned for this many seconds. */
    public static final double LAVA_ARMOR_REACH = 4;
    public static final int LAVA_ARMOR_BURN = 3;

    private KekkeiTechniquesA() {}

    // ================================================================ shared

    /** Living targets of {@code caster} within {@code radius} of {@code center} (bodies' middles). */
    static List<LivingEntity> around(LivingEntity caster, ServerLevel level, Vec3 center, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius + 1),
                e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator()
                        && e.position().add(0, e.getBbHeight() * 0.5, 0).distanceTo(center) <= radius + e.getBbWidth() * 0.5);
    }

    /** Where a ground-targeted jutsu lands: the enemy you look at, else the block, else {@code range} ahead, dropped to the floor. */
    static Vec3 groundAim(LivingEntity caster, ServerLevel level, double range) {
        return ElementalBehaviors.ground(level, ElementalBehaviors.aim(caster, range));
    }

    static Vec3 hands(LivingEntity caster) {
        return caster.getEyePosition().add(caster.getViewVector(1f).scale(0.7)).add(0, -0.4, 0);
    }

    static void playAt(ServerLevel level, Vec3 at, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /**
     * While a CHARGE jutsu of these natures fills (called every other tick by the caster): the
     * Storm laser gathers a dark cloud in the hands, the volcano makes the ground glow and rumble,
     * the self-centred blast cracks the body open with light.
     */
    static void charging(LivingEntity caster, JutsuDefinition def, float progress) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return;
        }
        switch (def.behavior()) {
            case "laser" -> KekkeiVfxA.laserCharge(level, hands(caster), progress);
            case "volcano" -> {
                Vec3 at = groundAim(caster, level, def.params().range());
                KekkeiVfxA.moltenGround(level, at, 1f + progress * def.params().radius() * 0.5f);
                KekkeiVfxA.groundLight(level, at, 0x8A2A06, 0x200400, 1.5f + progress * def.params().radius(), 6);
                if (caster.tickCount % 10 == 0) {
                    Vfx.quake(level, at, 0.15f + 0.25f * progress, 14);
                    playAt(level, at, SoundEvents.LAVA_AMBIENT, 1f, 0.6f);
                }
            }
            case "mega_blast" -> {
                KekkeiVfxA.bodyCracks(level, caster, progress);
                if (caster.tickCount % 8 == 0) {
                    Vfx.quake(level, caster.position(), 0.1f + 0.4f * progress, 16);
                    caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.CREEPER_PRIMED, SoundSource.PLAYERS,
                            0.6f + progress * 0.6f, 0.5f + progress * 0.5f);
                }
            }
            default -> { }
        }
    }

    /** Lava Armour: whoever strikes its wearer up close is burned by the magma. */
    @SubscribeEvent
    public static void lavaArmorBurns(LivingHurtEvent event) {
        LivingEntity wearer = event.getEntity();
        if (!(wearer.level() instanceof ServerLevel level) || !(event.getSource().getDirectEntity() instanceof LivingEntity attacker)
                || attacker == wearer || attacker.distanceTo(wearer) > LAVA_ARMOR_REACH) {
            return;
        }
        ResourceLocation mode = ModeSystem.activeMode(wearer);
        JutsuDefinition def = mode == null ? null : JutsuData.get(mode);
        if (def == null || def.nature().orElse(null) != Nature.LAVA || attacker.fireImmune()) {
            return;
        }
        attacker.setSecondsOnFire(LAVA_ARMOR_BURN);
        KekkeiVfxA.lavaSplash(level, attacker.position().add(0, attacker.getBbHeight() * 0.5, 0), 0.7f);
        playAt(level, attacker.position(), com.noirm.sound.NoirmSounds.LAVA_HISS.get(), 1f, 0.89f);
    }

    // ================================================================ Freezing Field

    static void frostField(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        float radius = p.radius() * (float) Math.sqrt(Math.max(0.5f, power));
        int duration = Math.max(4, p.duration());
        Vec3 center = caster.position();
        Set<LivingEntity> hit = new HashSet<>();
        int[] age = {0};
        // The first frame: a burst of cold light at the feet and a crack of ice.
        Vfx.burst(level, Vfx.glow(0xFFFFFF, KekkeiVfxA.ICE_PALE, 2.4f, 0.6f, 5), center.add(0, 0.4, 0), 1, 0, 0);
        KekkeiVfxA.groundLight(level, center, 0x6AB8E8, 0x0A2040, radius * 1.2f, 30);
        Vfx.quake(level, center, 0.35f, radius + 8);
        playAt(level, center, com.noirm.sound.NoirmSounds.ICE_SHATTER.get(), 1.2f, 0.77f);
        playAt(level, center, SoundEvents.POWDER_SNOW_STEP, 1.5f, 0.5f);
        GreatTechniques.later(now -> {
            age[0]++;
            float before = radius * (age[0] - 1) / duration;
            float reach = radius * Math.min(1f, age[0] / (float) duration);
            KekkeiVfxA.frostRing(level, center, before, reach);
            if (age[0] % 3 == 0) {
                Destruction.freeze(level, center, reach);
            }
            for (LivingEntity victim : around(caster, level, center.add(0, 0.9, 0), reach)) {
                if (!hit.add(victim)) {
                    continue;
                }
                JutsuEffects.hit(caster, victim, p.damage() * power, 0, p.posture() * power, 0, Vec3.ZERO);
                JutsuEffects.applyEffects(victim, p.effect(), p.effectTicks());
                if (p.stun() > 0) {
                    CombatSystem.stun(victim, Math.round(p.stun() * Math.min(1.5f, power)));
                }
                victim.setDeltaMovement(Vec3.ZERO);
                victim.hurtMarked = true;
                victim.setTicksFrozen(Math.max(victim.getTicksFrozen(), 140)); // the frost creeps over the screen too
                Vec3 feet = victim.position();
                KekkeiVfxA.iceShatter(level, feet.add(0, 0.6, 0), 1.0f);
                Destruction.spikes(level, feet, 1.4f, Blocks.PACKED_ICE.defaultBlockState(), 2);
                playAt(level, feet, com.noirm.sound.NoirmSounds.ICE_SHATTER.get(), 0.8f, 1.14f);
            }
            return age[0] >= duration;
        });
    }

    // ================================================================ Demonic Mirroring Ice Crystals

    static void iceMirrors(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), 20);
        if (target == null) {
            JutsuBehaviors.message(caster, Component.translatable("message.noirm.jutsu.no_target"));
            return;
        }
        float power = ctx.power();
        Vec3 center = target.position();
        float radius = Math.max(2f, p.radius());
        int count = Math.max(4, p.count());
        int duration = Math.max(20, p.duration());
        List<Vec3> mirrors = new ArrayList<>();
        List<Vec3> normals = new ArrayList<>();
        List<BlockPos> placed = new ArrayList<>();
        BlockState ice = Blocks.ICE.defaultBlockState();
        double turn = level.random.nextDouble() * Math.PI;
        for (int i = 0; i < count; i++) {
            double a = turn + i * Math.PI * 2 / count;
            Vec3 base = new Vec3(center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius);
            mirrors.add(base);
            normals.add(new Vec3(-Math.cos(a), 0, -Math.sin(a)));
            // The panes are real (see-through) ice: they hold the enemy in, and melt away after.
            BlockPos foot = BlockPos.containing(base);
            for (int h = 0; h < 3; h++) {
                BlockPos at = foot.above(h);
                if (level.getBlockState(at).isAir() && level.getEntities(null, new AABB(at)).isEmpty()
                        && Destruction.temporary(level, at, ice, 0.1f)) {
                    placed.add(at.immutable());
                }
            }
            KekkeiVfxA.iceShatter(level, base.add(0, 1.2, 0), 0.8f);
        }
        // The dome closes: a flash, a ring of frost, the enemy held for a breath.
        KekkeiVfxA.flashCloud(level, center.add(0, 1.2, 0), 0xFFFFFF, KekkeiVfxA.ICE_PALE, radius * 1.2f, 5);
        KekkeiVfxA.frostRing(level, center, 0, radius);
        Vfx.quake(level, center, 0.5f, 18);
        CombatSystem.stun(target, 10);
        playAt(level, center, SoundEvents.AMETHYST_BLOCK_RESONATE, 2f, 0.6f);
        playAt(level, center, SoundEvents.GLASS_PLACE, 2f, 0.5f);
        int[] age = {0};
        GreatTechniques.later(now -> {
            age[0]++;
            boolean alive = target.isAlive() && target.level() == level;
            for (int i = 0; i < mirrors.size(); i++) {
                if ((i + age[0]) % 2 == 0 || age[0] < 10) {
                    KekkeiVfxA.mirror(level, mirrors.get(i), normals.get(i), 1.1f, 2.8f, false);
                }
            }
            if (alive && target.position().distanceTo(center) > radius - 0.6) {
                // Trying to break out: the mirrors drag them back to the middle.
                Vec3 back = center.subtract(target.position()).normalize().scale(0.35);
                target.setDeltaMovement(back.x, Math.min(0.1, target.getDeltaMovement().y), back.z);
                target.hurtMarked = true;
            }
            if (alive && age[0] < duration && age[0] % MIRROR_THROW_INTERVAL == 0) {
                int i = level.random.nextInt(mirrors.size());
                throwSenbon(caster, level, mirrors.get(i), normals.get(i), target, p, power, 1f);
                KekkeiVfxA.mirror(level, mirrors.get(i), normals.get(i), 1.1f, 2.8f, true);
            }
            if (age[0] < duration && alive) {
                return false;
            }
            // The end: every mirror throws at once, then they all shatter.
            for (int i = 0; i < mirrors.size(); i++) {
                if (alive) {
                    throwSenbon(caster, level, mirrors.get(i), normals.get(i), target, p, power, MIRROR_VOLLEY_SHARE / mirrors.size() * 4f);
                }
                KekkeiVfxA.iceShatter(level, mirrors.get(i).add(0, 1.4, 0), 1.3f);
            }
            for (BlockPos at : placed) {
                if (level.getBlockState(at).is(Blocks.ICE)) {
                    level.levelEvent(2001, at, net.minecraft.world.level.block.Block.getId(ice));
                    level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            Vfx.quake(level, center, 0.6f, 18);
            playAt(level, center, com.noirm.sound.NoirmSounds.ICE_SHATTER.get(), 2f, 0.71f);
            return true;
        });
    }

    /** One senbon from a mirror into the enemy: a white streak, a glint where it lands. */
    private static void throwSenbon(LivingEntity caster, ServerLevel level, Vec3 mirror, Vec3 normal, LivingEntity target,
                                    JutsuDefinition.Params p, float power, float share) {
        Vec3 from = mirror.add(0, 1.3, 0).add(normal.scale(0.3));
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);
        Vec3 dir = to.subtract(from).normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        for (int k = -1; k <= 1; k++) { // a fan of three senbon
            Vec3 start = from.add(side.scale(k * 0.25)).add(0, k * 0.15, 0);
            Vec3 path = to.subtract(start);
            double length = path.length();
            Vec3 step = path.scale(1 / length);
            for (double d = 0; d < length; d += 0.3) {
                Vfx.one(level, Vfx.glow(0xFFFFFF, KekkeiVfxA.ICE_BLUE, 0.22f, 0.06f, 5), start.add(step.scale(d)), step.scale(0.05));
            }
            Vfx.one(level, KekkeiVfxA.crystal(0.35f, 0.3f, 4, 0f), start.add(step.scale(length * 0.5)), step.scale(0.3));
        }
        Vfx.burst(level, KekkeiVfxA.crystal(0.3f, 0.1f, 14, 0.04f), to, 3, 0.2, 0.06);
        Vfx.burst(level, KekkeiVfxA.glint(0.2f, 6), to, 2, 0.1, 0.1);
        JutsuEffects.hit(caster, target, p.damage() * power * share, 0, p.posture() * power * share, 0, Vec3.ZERO);
        JutsuEffects.applyEffects(target, p.effect(), p.effectTicks());
        if (p.stun() > 0) {
            CombatSystem.stun(target, p.stun());
        }
        playAt(level, to, SoundEvents.ARROW_HIT, 0.6f, 1.6f);
    }
}
