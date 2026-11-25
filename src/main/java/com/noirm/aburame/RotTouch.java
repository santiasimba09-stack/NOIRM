package com.noirm.aburame;

import com.noirm.entity.SparringNinja;
import com.noirm.gore.Gore;
import com.noirm.gore.SeveredLimb;
import com.noirm.player.ShinobiCapability;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The rinkaichū (docs/ABURAME.md, 2.3 and technique 18): nano-poison insects of the Torune line.
 * <ul>
 *   <li><b>Cell Rot</b>: 1 damage a second per stack (up to {@value #MAX_STACKS}); it spreads to anyone
 *   touching the victim. Rinkaichū hosts are immune.</li>
 *   <li><b>Rot Touch</b>: a rinkaichū host's bare-handed hits add a stack, unless they wear Aburame gloves.
 *   At Academy rank they can't control them at all: any touch leaks, gloves or not.</li>
 *   <li>At {@value #SEVER_STACKS} stacks the victim must <b>cut the limb off or die</b> (Tobi's arm): an NPC
 *   does it at once; a player has {@value #SEVER_SECONDS} s to sneak and use a blade.</li>
 *   <li>Creatures that die rotting are remembered for the Jar of Poison.</li>
 * </ul>
 */
public final class RotTouch {
    public static final int MAX_STACKS = 10;
    public static final int SEVER_STACKS = 8;
    public static final int SEVER_SECONDS = 10;
    public static final int EFFECT_TICKS = 20 * 20;
    /** Spreads to anyone this close every 2 seconds. */
    public static final double SPREAD_RANGE = 0.9;
    /** Rot deaths count for the Jar of Poison for 10 minutes. */
    public static final long CORPSE_TICKS = 20 * 600;

    private static final Map<LivingEntity, Integer> SEVER_TIMER = new WeakHashMap<>();
    private static final List<Corpse> CORPSES = new ArrayList<>();

    record Corpse(String dim, Vec3 at, long time) {}

    private RotTouch() {}

    public static int stacks(LivingEntity e) {
        MobEffectInstance rot = e.getEffect(AburameRegistry.CELL_ROT.get());
        return rot == null ? 0 : rot.getAmplifier() + 1;
    }

    /** Is {@code e} a rinkaichū host (immune, carries the antidote)? */
    public static boolean host(LivingEntity e) {
        ColonyData c = Colony.of(e);
        return c != null && c.rinkaichuBorn();
    }

    /** Adds {@code n} stacks of Cell Rot (hosts are immune). */
    public static void infect(LivingEntity e, int n) {
        if (!e.isAlive() || host(e) || n <= 0) {
            return;
        }
        int s = Math.min(MAX_STACKS, stacks(e) + n);
        e.addEffect(new MobEffectInstance(AburameRegistry.CELL_ROT.get(), EFFECT_TICKS, s - 1, false, true, true));
    }

    public static boolean cure(LivingEntity e) {
        SEVER_TIMER.remove(e);
        return e.removeEffect(AburameRegistry.CELL_ROT.get());
    }

    /** Whether {@code player}'s rinkaichū are contained right now (gloves on, and old enough to control them). */
    public static boolean contained(Player player) {
        boolean child = ShinobiCapability.get(player).map(d -> d.career().rank() == com.noirm.mission.PlayerRank.ACADEMY).orElse(false);
        if (child) {
            return false; // a child can't hold them in (Torune couldn't attend the Academy)
        }
        return AburameGlovesItem.wearing(player);
    }

    /** A melee hit landed: a rinkaichū host's bare hand rots what it touches. */
    public static void onMelee(LivingEntity attacker, LivingEntity victim) {
        if (!host(attacker)) {
            return;
        }
        ItemStack hand = attacker.getMainHandItem();
        if (attacker instanceof Player player) {
            boolean child = ShinobiCapability.get(player).map(d -> d.career().rank() == com.noirm.mission.PlayerRank.ACADEMY).orElse(false);
            if (!child && (!hand.isEmpty() || AburameGlovesItem.wearing(player))) {
                return; // a weapon in hand or gloves on: nothing leaks
            }
        } else if (!hand.isEmpty()) {
            return;
        }
        infect(victim, 1);
    }

    /** Every second: the rot bites, spreads by touch, and at 8 stacks demands a limb. */
    public static void tickSecond(LivingEntity e) {
        int s = stacks(e);
        if (s <= 0) {
            SEVER_TIMER.remove(e);
            return;
        }
        if (host(e)) {
            cure(e);
            return;
        }
        e.hurt(e.damageSources().magic(), s);
        if (e.level() instanceof ServerLevel level) {
            level.sendParticles(SwarmFx.PURPLE, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 2 + s, 0.25, 0.3, 0.25, 0.01);
            if (e.tickCount % 40 < 20) {
                for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(SPREAD_RANGE),
                        o -> o != e && o.isAlive())) {
                    infect(other, 1); // it spreads by touch, through clothes
                }
            }
        }
        if (s >= SEVER_STACKS && e.isAlive()) {
            if (e instanceof SparringNinja || !(e instanceof Player)) {
                if (e instanceof SparringNinja) {
                    sever(e); // an NPC tears the arm off at once
                }
            } else {
                int t = SEVER_TIMER.merge(e, 1, Integer::sum);
                if (t == 1) {
                    ((Player) e).displayClientMessage(Component.translatable("message.noirm.colony.sever_or_die", SEVER_SECONDS)
                            .withStyle(ChatFormatting.DARK_PURPLE), false);
                }
                if (t > SEVER_SECONDS) {
                    e.hurt(e.damageSources().magic(), Float.MAX_VALUE); // it reached the heart
                }
            }
        }
    }

    /** Cuts off the rotting arm: the rot goes with it, at a price. */
    public static void sever(LivingEntity e) {
        if (e.level() instanceof ServerLevel level) {
            Gore.sever(level, e, SeveredLimb.Part.LEFT_ARM, null);
        }
        cure(e);
        e.hurt(e.damageSources().generic(), 6f);
    }

    /** A player at 8 stacks sneak-uses a blade: they cut it off. */
    public static boolean trySelfSever(Player player) {
        if (stacks(player) >= SEVER_STACKS && player.isShiftKeyDown() && Gore.isBlade(player.getMainHandItem())) {
            sever(player);
            return true;
        }
        return false;
    }

    /** Something died: if it was rotting, it counts for the Jar of Poison. */
    public static void onDeath(LivingEntity e) {
        if (stacks(e) > 0) {
            CORPSES.add(new Corpse(e.level().dimension().location().toString(), e.position(), e.level().getGameTime()));
            if (CORPSES.size() > 64) {
                CORPSES.remove(0);
            }
        }
    }

    /** Rot deaths within {@code radius} of {@code at} in the last 10 minutes. */
    public static int corpsesNear(ServerLevel level, Vec3 at, double radius) {
        long now = level.getGameTime();
        String dim = level.dimension().location().toString();
        int n = 0;
        for (Iterator<Corpse> it = CORPSES.iterator(); it.hasNext(); ) {
            Corpse c = it.next();
            if (now - c.time() > CORPSE_TICKS) {
                it.remove();
            } else if (c.dim().equals(dim) && c.at().distanceToSqr(at) <= radius * radius) {
                n++;
            }
        }
        return n;
    }

    /** For tests: records a rot death at {@code pos}. */
    public static void addCorpse(ServerLevel level, BlockPos pos) {
        CORPSES.add(new Corpse(level.dimension().location().toString(), Vec3.atCenterOf(pos), level.getGameTime()));
    }
}
