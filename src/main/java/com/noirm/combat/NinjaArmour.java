package com.noirm.combat;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Vanilla armour against ninja attacks: it keeps only {@link ChakraReinforcement#armourShare()} (25%)
 * of what it would normally stop, and so do its enchantments (Protection, Fire, Blast and Projectile
 * Protection). The Resistance potion still counts in full. Vanilla attacks (zombies, arrows, lava…)
 * meet the full armour, exactly as in vanilla.
 *
 * <p>Vanilla works out armour in {@code LivingEntity.getDamageAfterArmorAbsorb} and the enchantments
 * (and Resistance) in {@code getDamageAfterMagicAbsorb}. {@code mixin.LivingEntityArmourMixin} notes
 * the amount going into each ({@link #armourIn}, {@link #magicIn}) and hands us vanilla's answer on the
 * way out ({@link #armourOut}, {@link #magicOut}); for a ninja attack we give back
 * {@code amount − share × (what vanilla would have stopped)}.
 *
 * <p>Jutsu use a damage type that skips armour in vanilla; against them the armour share applies
 * anyway (so diamond helps a little against jutsu too), without wearing the armour down.
 *
 * <pre>
 *   a 10-point hit on…           vanilla mob   ninja attack
 *   no armour                    10            10
 *   iron (15 armour)             6.0           9.0
 *   diamond (20 armour, 8 tough) 3.0           8.25
 *   diamond + Protection IV      1.1           6.9
 * </pre>
 */
public final class NinjaArmour {
    /** The amount going into the armour step and the enchantment step (set at their start, read at their end). */
    private static float armourIn;
    private static float magicIn;

    private NinjaArmour() {}

    public static void armourIn(float amount) {
        armourIn = amount;
    }

    public static void magicIn(float amount) {
        magicIn = amount;
    }

    /** The armour step's result for {@code entity}: vanilla's for vanilla attacks, mostly ignored for ninja attacks. */
    public static float armourOut(LivingEntity entity, DamageSource source, float vanilla) {
        if (entity.level().isClientSide() || !ChakraReinforcement.isNinjaAttack(entity, source)) {
            return vanilla;
        }
        float in = armourIn;
        float full = source.is(DamageTypeTags.BYPASSES_ARMOR)
                ? CombatRules.getDamageAfterAbsorb(in, entity.getArmorValue(), (float) entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS))
                : vanilla;
        return withShare(in, full);
    }

    /** The enchantment step's result: Resistance in full, the enchantments at the ninja-armour share. */
    public static float magicOut(LivingEntity entity, DamageSource source, float vanilla) {
        if (entity.level().isClientSide() || !ChakraReinforcement.isNinjaAttack(entity, source) || source.is(DamageTypeTags.BYPASSES_EFFECTS)) {
            return vanilla;
        }
        float afterResistance = magicIn;
        if (entity.hasEffect(MobEffects.DAMAGE_RESISTANCE) && !source.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
            int level = (entity.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() + 1) * 5;
            afterResistance = Math.max(magicIn * (25 - level) / 25f, 0f);
        }
        if (vanilla >= afterResistance) {
            return vanilla; // no enchantment stopped anything
        }
        return withShare(afterResistance, vanilla);
    }

    /** {@code in} less the share of what {@code full} (vanilla's result) took off. */
    public static float withShare(float in, float full) {
        float stopped = Math.max(0f, in - full);
        return in - stopped * ChakraReinforcement.armourShare();
    }
}
