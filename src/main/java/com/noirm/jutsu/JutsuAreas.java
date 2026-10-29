package com.noirm.jutsu;

import com.noirm.Noirm;
import com.noirm.combat.CombatSystem;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Jutsu that linger: a mist or swamp that hurts whoever stands in it (<b>cloud</b>), strikes
 * falling over an area (<b>rain</b>), or an aura following someone (a bind's prison, an armor).
 * Ticked on the server; each one ends on its own.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class JutsuAreas {
    /** What happens every {@code interval} ticks inside an area. */
    /** BLAZE: burning oil on the ground (Flame Bomb): it burns and sets alight whoever stands in it. */
    /** SHACKLE: lightning arcs binding whoever it follows (Thunder Snare). */
    public enum Mode { CLOUD, RAIN, AURA, BLAZE, SHACKLE }

    /**
     * @param follow  if set, the area moves with this entity (auras)
     * @param stun    ticks of stun per hit (rain strikes)
     */
    public record Area(ServerLevel level, Vec3 center, Entity follow, Entity owner, Mode mode, float radius, long until,
                       int interval, float damage, String effect, int effectTicks, int stun, ParticleOptions particle) {
        Vec3 where() {
            return follow != null ? follow.position() : center;
        }
    }

    private static final List<Area> AREAS = new ArrayList<>();

    private JutsuAreas() {}

    public static void add(Area area) {
        AREAS.add(area);
    }

    public static int count() {
        return AREAS.size();
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || AREAS.isEmpty()) {
            return;
        }
        Iterator<Area> it = AREAS.iterator();
        while (it.hasNext()) {
            Area area = it.next();
            long now = area.level().getGameTime();
            if (now > area.until() || (area.follow() != null && !area.follow().isAlive())) {
                it.remove();
                continue;
            }
            tick(area, now);
        }
    }

    private static void tick(Area area, long now) {
        ServerLevel level = area.level();
        Vec3 at = area.where();
        float r = area.radius();
        switch (area.mode()) {
            case AURA -> level.sendParticles(area.particle(), at.x, at.y + 1, at.z, 3, Math.max(0.3, r * 0.5), 0.6, Math.max(0.3, r * 0.5), 0.02);
            case CLOUD -> level.sendParticles(area.particle(), at.x, at.y + 0.6, at.z, 6 + (int) (r * 3), r * 0.6, 0.5, r * 0.6, 0.01);
            case RAIN -> { }
            case SHACKLE -> {
                if (now % 3 == 0 && area.follow() != null) {
                    com.noirm.vfx.LightningVfx.shackle(level, area.follow(), (int) now);
                }
            }
            case BLAZE -> {
                if (now % 2 == 0) {
                    com.noirm.vfx.FireVfx.burningGround(level, at, r);
                }
            }
        }
        if (area.interval() <= 0 || now % area.interval() != 0) {
            return;
        }
        if (area.mode() == Mode.RAIN) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double dist = Math.sqrt(level.random.nextDouble()) * r;
            Vec3 strike = at.add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            for (double y = 0; y < 8; y += 0.5) {
                level.sendParticles(area.particle(), strike.x, strike.y + y, strike.z, 2, 0.1, 0.1, 0.1, 0);
            }
            com.noirm.vfx.LightningVfx.bolt(level, strike.add(0, 14, 0), strike, com.noirm.vfx.Vfx.LIGHTNING, 0.12f, 6, 3,
                    com.noirm.vfx.LightningVfx.FORKY | com.noirm.vfx.LightningVfx.IMPACT);
            level.sendParticles(ParticleTypes.FLASH, strike.x, strike.y + 0.3, strike.z, 1, 0, 0, 0, 0);
            level.playSound(null, strike.x, strike.y, strike.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5f, 1.6f);
            hitAround(area, strike, 1.6);
        } else if (area.damage() > 0 || !area.effect().isEmpty()) {
            hitAround(area, at, r);
        }
    }

    private static void hitAround(Area area, Vec3 at, double radius) {
        AABB box = new AABB(at, at).inflate(radius, 2, radius);
        for (LivingEntity victim : area.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e != area.owner() && e != area.follow() && !(area.owner() != null && JutsuEffects.isFriendly(area.owner(), e))
                        && e.position().distanceTo(at) <= radius + 0.5)) {
            if (area.damage() > 0) {
                JutsuEffects.hit(area.owner() != null ? area.owner() : victim, victim, area.damage(), 0, 0, 0, Vec3.ZERO);
            }
            JutsuEffects.applyEffects(victim, area.effect(), area.effectTicks());
            if (area.stun() > 0) {
                CombatSystem.stun(victim, area.stun());
            }
            if (area.mode() == Mode.BLAZE) {
                victim.setSecondsOnFire(3);
            }
        }
    }
}
