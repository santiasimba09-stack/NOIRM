package com.noirm.gear;

import com.noirm.Noirm;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * What gear perks do (server side; the name tag is hidden by {@code client/MaskedNameTags}).
 * The numbers are here so they're easy to tune.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class GearPerks {
    /** Cloaks: +15% movement speed while sprinting. */
    public static final double SWIFT_SPEED = 0.15;
    /** Tabi and straw sandals: falls hurt as if 3 blocks shorter. */
    public static final float SOFT_STEP_BLOCKS = 3f;
    /** Hand wraps' attack damage. */
    public static final float HAND_WRAPS_DAMAGE = 1.5f;
    /** Arm guards' attack damage (they armour you more instead). */
    public static final float ARM_GUARDS_DAMAGE = 1f;

    private static final UUID SWIFT_ID = UUID.fromString("5d0c8a3e-21b4-4c7f-9e86-3f1a2b4c5d61");
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private GearPerks() {}

    /** Is {@code entity} wearing a piece with this perk? */
    public static boolean wears(LivingEntity entity, GearPerk perk) {
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.getItem() instanceof GearItem gear && gear.perk() == perk) {
                return true;
            }
        }
        return false;
    }

    /** True if a mask hides this entity's name tag. */
    public static boolean hidesName(Entity entity) {
        return entity instanceof LivingEntity living && wears(living, GearPerk.HIDE_NAME);
    }

    /** Adds or removes the cloak's sprint bonus. */
    public static void updateSwift(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean want = entity.isSprinting() && wears(entity, GearPerk.SWIFT);
        boolean has = speed.getModifier(SWIFT_ID) != null;
        if (want && !has) {
            speed.addTransientModifier(new AttributeModifier(SWIFT_ID, "Cloak sprint", SWIFT_SPEED, AttributeModifier.Operation.MULTIPLY_BASE));
        } else if (!want && has) {
            speed.removeModifier(SWIFT_ID);
        }
    }

    public static boolean hasSwiftBonus(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.getModifier(SWIFT_ID) != null;
    }

    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide() && (entity.tickCount & 3) == 0) {
            updateSwift(entity);
        }
    }

    @SubscribeEvent
    public static void fall(LivingFallEvent event) {
        if (wears(event.getEntity(), GearPerk.SOFT_STEP)) {
            event.setDistance(Math.max(0f, event.getDistance() - SOFT_STEP_BLOCKS));
        }
    }
}
