package com.noirm.aburame;

import com.noirm.gore.Gore;
import com.noirm.jutsu.JutsuEffects;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bug Bite (Kidaichū: Mushikui, docs/ABURAME.md, 2.5 technique 15): kidaichū burrow in and grow over
 * 6 seconds, biting 1, 2, 3, 4, 5, 6 damage a second (21 in all) and eating 10 chakra, showing as
 * swelling bumps. Fire on the victim, a medic or Poison Draw ends it early. The <b>wrong dose</b>
 * (casting under 25% chakra or with Control under 30) has a 15% chance to turn them on the host.
 */
public final class BugBite {
    public static final int SECONDS = 6;
    public static final float CHAKRA_EATEN = 10f;
    public static final float HOST_BITE_CHANCE = 0.15f;
    public static final float HOST_BITE_CHAKRA = 0.25f;
    public static final float HOST_BITE_CONTROL = 30f;
    public static final float HOST_BITE_DAMAGE = 3f;
    public static final int HOST_BITE_SECONDS = 3;
    private static final DustParticleOptions BUMP = new DustParticleOptions(new Vector3f(0.55f, 0.35f, 0.25f), 1.1f);

    /** One victim's burrowed giants: who sent them, and how many seconds they've grown. */
    static final class Bite {
        final LivingEntity owner;
        int second;
        final boolean onHost;

        Bite(LivingEntity owner, boolean onHost) {
            this.owner = owner;
            this.onHost = onHost;
        }
    }

    private static final Map<LivingEntity, Bite> BITES = new WeakHashMap<>();

    private BugBite() {}

    public static void start(LivingEntity owner, LivingEntity victim) {
        BITES.put(victim, new Bite(owner, false));
    }

    /** The wrong dose: they bite their own host instead. */
    public static void biteHost(LivingEntity host) {
        BITES.put(host, new Bite(host, true));
    }

    public static boolean active(LivingEntity victim) {
        return BITES.containsKey(victim);
    }

    /** Fire, a medic or Poison Draw clears them out. */
    public static boolean cure(LivingEntity victim) {
        return BITES.remove(victim) != null;
    }

    /** Whether this cast is a wrong dose (2.3). */
    public static boolean wrongDose(LivingEntity caster) {
        if (!(caster instanceof net.minecraft.world.entity.player.Player player)) {
            return false;
        }
        float control = com.noirm.player.ShinobiCapability.get(player).map(d -> d.getControl()).orElse(0f);
        return Drain.chakra(caster) < Drain.maxChakra(caster) * HOST_BITE_CHAKRA || control < HOST_BITE_CONTROL;
    }

    /** Every second for every victim. */
    public static void tickSecond(LivingEntity victim) {
        Bite bite = BITES.get(victim);
        if (bite == null) {
            return;
        }
        if (!victim.isAlive() || victim.isOnFire()) {
            BITES.remove(victim); // burned out
            return;
        }
        bite.second++;
        if (bite.onHost) {
            victim.hurt(victim.damageSources().magic(), HOST_BITE_DAMAGE);
            if (bite.second >= HOST_BITE_SECONDS) {
                BITES.remove(victim);
            }
            return;
        }
        float damage = bite.second; // 1, 2, 3, 4, 5, 6
        boolean lethal = victim.getHealth() <= damage;
        JutsuEffects.hit(bite.owner, victim, damage, 0f, 0f, 0, Vec3.ZERO);
        Drain.take(victim, CHAKRA_EATEN / SECONDS);
        if (victim.level() instanceof ServerLevel level) {
            level.sendParticles(BUMP, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                    2 + bite.second, victim.getBbWidth() * 0.4, victim.getBbHeight() * 0.3, victim.getBbWidth() * 0.4, 0);
            Gore.addMark(victim, Gore.Mark.BRUISE, 0.12f); // swelling bumps
            if (lethal || !victim.isAlive()) {
                Gore.addMark(victim, Gore.Mark.BLOOD, 0.6f); // the hollowed death
                level.sendParticles(SwarmFx.DUST, victim.getX(), victim.getY() + 1, victim.getZ(), 40, 0.4, 0.6, 0.4, 0.08);
            }
        }
        if (bite.second >= SECONDS) {
            BITES.remove(victim);
        }
    }
}
