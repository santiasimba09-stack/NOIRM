package com.noirm.entity;

import com.noirm.combat.CombatSystem;
import com.noirm.weapon.NinjaArsenal;
import com.noirm.weapon.ThrowStats;
import com.noirm.weapon.Throwing;
import com.noirm.weapon.ThrowingWeaponItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

/**
 * Ninjas carrying throwables (off hand, or a kunai in hand) throw them from range while they
 * close in: kunai, shuriken (sometimes a fan of three), senbon, the odd fūma shuriken. Runs
 * alongside {@link NinjaFightGoal} (it takes no movement or look control). Their supply never
 * runs out, and what they throw can't be picked up. Crew mates never throw explosives.
 */
class NinjaThrowGoal extends Goal {
    static final double MIN_RANGE = 4.5;
    static final double MAX_RANGE = 16;
    /** Ninjas throw a lot less often than a weapon's cooldown would allow. */
    private static final int COOLDOWN_SCALE = 4;

    private final SparringNinja ninja;
    private int cooldown;

    NinjaThrowGoal(SparringNinja ninja) {
        this.ninja = ninja;
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = ninja.getTarget();
        return target != null && target.isAlive() && !NinjaArsenal.throwable(ninja).isEmpty();
    }

    @Override
    public void start() {
        cooldown = Math.max(cooldown, 15 + ninja.getRandom().nextInt(20));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        LivingEntity target = ninja.getTarget();
        ItemStack stack = NinjaArsenal.throwable(ninja);
        if (target == null || !(stack.getItem() instanceof ThrowingWeaponItem item)) {
            return;
        }
        ThrowStats stats = item.stats();
        if (stats.fuse() > 0 && ninja.inCrew()) {
            return;
        }
        double distance = ninja.distanceTo(target);
        if (distance < MIN_RANGE || distance > MAX_RANGE || !ninja.hasLineOfSight(target) || !CombatSystem.canAct(ninja)
                || com.noirm.ally.AllyMind.shouldRetreat(ninja) && distance < 6) {
            return;
        }
        if (ninja.getRandom().nextFloat() > 0.12f) {
            return; // not every chance: a throw now and then
        }
        ninja.getLookControl().setLookAt(target, 60f, 60f);
        boolean fan = stats.flat() && stats.pierce() == 0 && ninja.getRandom().nextFloat() < 0.3f;
        Throwing.npcThrow(ninja, target, stack, fan);
        cooldown = stats.cooldown() * COOLDOWN_SCALE + 10 + ninja.getRandom().nextInt(30);
    }
}
