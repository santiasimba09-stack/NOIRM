package com.noirm.aburame;

import com.noirm.entity.ShadowClone;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Insect Clone (Mushi Bunshin, docs/ABURAME.md technique 4): thousands of insects in your shape. It
 * forms at any of your swarms or at your feet, walks, guards and throws weak punches (2 damage); NPCs
 * target it like you. Struck for 4+ damage (6+ with the Stag Beetle strain), it <b>bursts into a swarm
 * that clings to the striker</b>. The Insect Legion's clones fight for real (60% of your damage).
 */
public class InsectClone extends ShadowClone {
    /** A blow this big bursts it; its punches hit for this much. */
    public static final float BURST_AT = 4f, BURST_AT_STAG = 6f, PUNCH = 2f;
    /** The Legion's clones: share of your melee damage, and how long they last. */
    public static final float LEGION_SHARE = 0.6f;
    public static final int LIFE = 600, LEGION_LIFE = 900;

    private int insects;
    private boolean legion;
    private boolean burst;

    public InsectClone(EntityType<? extends InsectClone> type, Level level) {
        super(type, level);
    }

    public void setInsects(int n) {
        insects = n;
    }

    public int insects() {
        return insects;
    }

    public void setLegion(boolean legion) {
        this.legion = legion;
        setLife(legion ? LEGION_LIFE : LIFE);
    }

    public boolean isLegion() {
        return legion;
    }

    /** How hard its punches land, given the damage {@code amount} it would have done. */
    public float punch(float amount) {
        return legion ? amount * LEGION_SHARE : Math.min(amount, PUNCH);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide() || burst || amount <= 0) {
            return false;
        }
        LivingEntity owner = ownerEntity();
        if (source.getEntity() instanceof LivingEntity attacker && owner != null && !com.noirm.ally.Crew.sameSide(this, attacker)) {
            float threshold = InsectSwarm.colonyHas(owner, WildKind.STAG_BEETLE) ? BURST_AT_STAG : BURST_AT;
            if (amount >= threshold) {
                burstOnto(attacker);
                return true;
            }
        }
        return super.hurt(source, amount);
    }

    /** It bursts back into a swarm that clings to whoever struck it. */
    public void burstOnto(LivingEntity striker) {
        LivingEntity owner = ownerEntity();
        burst = true;
        if (owner != null && level() instanceof ServerLevel level && insects > 0) {
            InsectSwarm swarm = InsectSwarm.create(level, owner, Species.KIKAICHU, insects, position().add(0, 1, 0), SwarmMode.TRAVEL);
            swarm.setTarget(striker, SwarmMode.CLING);
            level.addFreshEntity(swarm);
            insects = 0; // they're in the swarm now (still counted as out in the field)
        }
        discard();
    }

    @Override
    public void dispel() {
        LivingEntity owner = ownerEntity();
        if (!burst && owner != null && insects > 0) {
            Colony.returnHome(owner, Species.KIKAICHU, insects, 0f); // they fly back in
            insects = 0;
        }
        super.dispel();
    }

    @Override
    protected void dispelEffects(ServerLevel level, Vec3 at) {
        level.sendParticles(SwarmFx.DUST, at.x, at.y, at.z, 60, 0.3, 0.7, 0.3, 0.06);
    }

    @Override
    public void remove(RemovalReason reason) {
        LivingEntity owner = ownerEntity();
        if (!level().isClientSide() && !burst && owner != null && insects > 0) {
            Colony.returnHome(owner, Species.KIKAICHU, insects, 0f);
            insects = 0;
        }
        super.remove(reason);
    }
}
