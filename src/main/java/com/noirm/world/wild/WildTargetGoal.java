package com.noirm.world.wild;

import com.noirm.entity.SparringNinja;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

/**
 * Who a wild ninja goes after on its own (see {@link WildNinjas#wouldAttack}): rogues attack
 * players they can see, squads attack rogues. Only wild ninjas that ever pick fights run it, so
 * village residents and crew mates pay nothing for it.
 */
public final class WildTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {
    private final SparringNinja ninja;

    public WildTargetGoal(SparringNinja ninja) {
        super(ninja, LivingEntity.class, 10, true, false, target -> WildNinjas.wouldAttack(ninja, target));
        this.ninja = ninja;
    }

    @Override
    public boolean canUse() {
        return WildNinjas.seeksFights(ninja) && super.canUse();
    }
}
