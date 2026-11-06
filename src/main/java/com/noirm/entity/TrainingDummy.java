package com.noirm.entity;

import com.noirm.combat.CombatSystem;
import com.noirm.registry.NoirmItems;
import com.noirm.training.TrainingSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * A straw training dummy. It never fights back, never dies (it patches itself up when beaten
 * down) and stays where it was placed. Hitting it trains taijutsu up to
 * {@link TrainingSystem#DRILL_CAP}, and it shows your combo and its damage above the hotbar.
 * Crouch and punch it outside combat mode to pick it back up.
 */
public class TrainingDummy extends Mob {
    /** Hits closer together than this (ticks) count as one combo. */
    public static final int COMBO_GAP = 30;

    private long lastHitTick = -100;
    private int combo;
    private float comboDamage;

    public TrainingDummy(EntityType<? extends TrainingDummy> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1)
                .add(Attributes.MOVEMENT_SPEED, 0);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide() || isRemoved()) {
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount); // /kill and the void still work
        }
        if (source.getEntity() instanceof Player player && player.isShiftKeyDown() && !CombatSystem.isApplyingHit()) {
            pickUp(player);
            return false;
        }
        // Never dies: the hit can bring it down to 1 health at most, then it patches itself up.
        float capped = Math.min(amount, getHealth() - 1f);
        boolean hurt = super.hurt(source, Math.max(0f, capped));
        if (hurt && getHealth() <= getMaxHealth() * 0.25f) {
            setHealth(getMaxHealth());
        }
        if (hurt && source.getEntity() instanceof ServerPlayer player) {
            showCombo(player, amount);
        }
        return hurt;
    }

    private void showCombo(ServerPlayer player, float damage) {
        long now = level().getGameTime();
        if (now - lastHitTick > COMBO_GAP) {
            combo = 0;
            comboDamage = 0;
        }
        lastHitTick = now;
        combo++;
        comboDamage += damage;
        Component text = Component.translatable("message.noirm.dummy.combo", combo,
                String.format(Locale.ROOT, "%.1f", comboDamage));
        if (TrainingSystem.drillFactor(player, TrainingSystem.Stat.TAIJUTSU) == 0f) {
            text = text.copy().append(Component.translatable("message.noirm.dummy.no_xp").withStyle(ChatFormatting.GRAY));
        }
        player.displayClientMessage(text, true);
    }

    private void pickUp(Player player) {
        if (!player.getAbilities().instabuild) {
            spawnAtLocation(new ItemStack(NoirmItems.TRAINING_DUMMY.get()));
        }
        playSound(SoundEvents.ARMOR_STAND_BREAK, 1f, 1f);
        discard();
    }

    /** How long ago it was last hit (ticks), for the wobble on the client. */
    public int ticksSinceHit() {
        return hurtTime;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // Planted in the ground: no sliding, no launching.
        Vec3 motion = getDeltaMovement();
        setDeltaMovement(0, Math.min(0, motion.y), 0);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ARMOR_STAND_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ARMOR_STAND_BREAK;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }
}
