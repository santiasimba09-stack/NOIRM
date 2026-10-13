package com.noirm.ally;

import com.noirm.combat.Perception;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.vfx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * How a crew mate thinks: respect for their leader, and the choices they make in a fight.
 *
 * <p><b>Respect</b> (0–100) starts from how strong you are next to them and keeps drifting with
 * that comparison. Fighting and winning together, teaching them and equipping them raise it;
 * hitting them, letting them get beaten down and losing crew mates lower it. Their personality
 * ({@link Trait}) scales every change. At 70+ they fight harder for you; below 20 they may ignore
 * orders; below 5 they leave.
 */
public final class AllyMind {
    public static final float HIGH_RESPECT = 70f;
    public static final float LOW_RESPECT = 20f;
    public static final float LEAVES_BELOW = 5f;
    /** Ticks between supporting heals. */
    public static final int HEAL_INTERVAL = 60;

    private AllyMind() {}

    /** Where respect settles from power alone: 50 for equals, +15 per doubling of your strength over theirs. */
    public static float respectFromPower(Player leader, SparringNinja ally) {
        float ratio = Perception.basePower(leader) / Math.max(1f, Perception.basePower(ally));
        return Math.max(5f, Math.min(90f, 50f + 15f * (float) (Math.log(ratio) / Math.log(2))));
    }

    /** Changes respect by {@code amount} (scaled by their personality) and reacts to thresholds. */
    public static void respect(SparringNinja ally, float amount, String reasonKey) {
        if (ally.leaderId() == null) {
            return;
        }
        float before = ally.respect();
        ally.setRespect(before + amount * ally.trait().respectGain);
        Player leader = ally.level().getPlayerByUUID(ally.leaderId());
        if (leader != null && reasonKey != null && Math.abs(amount) >= 3) {
            leader.displayClientMessage(Component.translatable(reasonKey, ally.getDisplayName()), true);
        }
        if (ally.respect() >= HIGH_RESPECT && before < HIGH_RESPECT && leader != null) {
            leader.sendSystemMessage(Component.translatable("message.noirm.ally.devoted", ally.getDisplayName()).withStyle(ChatFormatting.GOLD));
        } else if (ally.respect() < LEAVES_BELOW) {
            if (leader != null) {
                leader.sendSystemMessage(Component.translatable("message.noirm.ally.leaves", ally.getDisplayName()).withStyle(ChatFormatting.RED));
                com.noirm.player.ShinobiCapability.get(leader).ifPresent(d -> d.crew().remove(ally.getUUID()));
            }
            ally.leaveCrew();
        }
    }

    /** Does this crew mate do what they're told? Low respect: not always. */
    public static boolean obeys(SparringNinja ally) {
        return ally.respect() >= LOW_RESPECT || ally.getRandom().nextFloat() < 0.5f;
    }

    /** Damage bonus from devotion: +15% at high respect. */
    public static float morale(SparringNinja ally) {
        return ally.leaderId() != null && ally.respect() >= HIGH_RESPECT ? 1.15f : 1f;
    }

    /** Badly hurt: time to back off and recover (when, depends on personality). */
    public static boolean shouldRetreat(SparringNinja ally) {
        return ally.leaderId() != null && ally.getHealth() < ally.getMaxHealth() * ally.trait().retreatAt;
    }

    /** Every 100 ticks for a crew mate: respect drifts toward what your strength commands. */
    public static void drift(SparringNinja ally, Player leader) {
        float target = respectFromPower(leader, ally);
        float step = Math.signum(target - ally.respect()) * Math.min(0.5f, Math.abs(target - ally.respect()));
        ally.setRespect(ally.respect() + step);
    }

    /** A crew mate who knows a healing jutsu patches up their leader (or themselves). */
    public static void support(SparringNinja ally, Player leader) {
        if (ally.tickCount % HEAL_INTERVAL != 0) {
            return;
        }
        ResourceLocation heal = null;
        for (ResourceLocation id : ally.castableJutsu()) {
            JutsuDefinition def = JutsuData.get(id);
            if (def != null && "heal".equals(def.behavior())) {
                heal = id;
                break;
            }
        }
        if (heal == null) {
            return;
        }
        LivingEntity patient = leader.getHealth() < leader.getMaxHealth() * 0.6f && leader.distanceTo(ally) < 6 ? leader
                : ally.getHealth() < ally.getMaxHealth() * 0.6f ? ally : null;
        if (patient == null) {
            return;
        }
        JutsuDefinition def = JutsuData.get(heal);
        float amount = def.params().damage() * com.noirm.jutsu.JutsuCaster.power(ally, heal, def) * 2f;
        patient.heal(amount);
        ally.addJutsuXp(heal, 1);
        ally.getLookControl().setLookAt(patient);
        Vfx.Palette palette = Vfx.palette(com.noirm.clan.Nature.YANG);
        Vfx.aura(ally.level(), patient.position(), patient.getBbWidth(), patient.getBbHeight(), palette, 18);
        ally.level().playSound(null, patient.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1f, 1.4f);
    }
}
