package com.noirm.entity;

import com.noirm.combat.CombatSystem;
import com.noirm.jutsu.JutsuEffects;
import com.noirm.vfx.WoodVfx;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/**
 * Wood Clone (木分身): a clone of grown wood, carved in its owner's likeness (rough grey-green bark
 * with their look laid faintly over the grain; client/WoodReleaseRenderers). It fights like a
 * shadow clone but costs no share of your chakra, and it's a trap: whoever breaks it is caught by
 * the roots it bursts into. While one stands, your Substitution swaps you with it instead of a log
 * (see {@link com.noirm.jutsu.Substitution}).
 */
public class WoodClone extends ShadowClone {
    /** How long the roots hold whoever breaks a wood clone. */
    public static final int TRAP_STUN = 30;
    public static final float TRAP_DAMAGE = 3f;
    private static final ResourceLocation WOOD_CLONE_JUTSU = com.noirm.Noirm.id("mokuton/wood_clone");

    private Entity lastAttacker;

    public WoodClone(EntityType<? extends WoodClone> type, Level level) {
        super(type, level);
    }

    @Override
    public void setOwner(LivingEntity owner) {
        super.setOwner(owner);
        knownJutsu().remove(WOOD_CLONE_JUTSU); // wood clones don't grow wood clones
        // Carved, not dressed: the armour is part of the wood (the weapon in hand stays real).
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() != null) {
            lastAttacker = source.getEntity();
        }
        return super.hurt(source, amount);
    }

    /** It splinters, and roots burst out to catch whoever broke it. */
    @Override
    protected void dispelEffects(ServerLevel level, Vec3 at) {
        WoodVfx.crumble(level, at, 1f);
        WoodVfx.emerge(level, position(), 1.2f);
        level.playSound(null, getX(), getY(), getZ(), com.noirm.sound.NoirmSounds.WOOD_BURST.get(), getSoundSource(), 0.9f, 0.89f);
        if (lastAttacker instanceof LivingEntity attacker && attacker.isAlive() && attacker.distanceTo(this) < 6
                && (ownerEntity() == null || JutsuEffects.isTarget(ownerEntity(), attacker))) {
            trap(level, attacker);
        }
    }

    /** Roots shoot from the broken clone to {@code victim} and hold them. */
    public void trap(ServerLevel level, LivingEntity victim) {
        WoodVfx.rootLine(level, position(), victim.position());
        WoodVfx.emerge(level, victim.position(), 1f);
        CombatSystem.stun(victim, TRAP_STUN);
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, TRAP_STUN, 9, false, false));
        victim.setDeltaMovement(Vec3.ZERO);
        victim.hurtMarked = true;
        if (ownerEntity() != null) {
            JutsuEffects.hit(ownerEntity(), victim, TRAP_DAMAGE, 0, 10, 0, Vec3.ZERO);
        }
    }

    /** Takes its owner's place under a hit (Substitution): it's moved to {@code spot}, breaks and roots the attacker. */
    public void standIn(Vec3 spot, Entity attacker) {
        teleportTo(spot.x, spot.y, spot.z);
        lastAttacker = attacker;
        dispel();
    }

    /** The owner's nearest wood clone within {@code reach}, or null. */
    public static WoodClone nearestOf(LivingEntity owner, double reach) {
        return owner.level().getEntitiesOfClass(WoodClone.class, owner.getBoundingBox().inflate(reach), c -> c.isOwnedBy(owner) && c.isAlive())
                .stream().min(Comparator.comparingDouble(c -> c.distanceToSqr(owner))).orElse(null);
    }
}
