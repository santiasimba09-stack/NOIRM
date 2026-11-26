package com.noirm.senjutsu;

import com.noirm.vfx.Vfx;
import com.noirm.vfx.VfxKind;
import com.noirm.vfx.VfxOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A restless spirit of the sanctuary: the Stone Hermit's test sends them at a meditating student
 * to break their focus (see {@link SageTrial}). A small flying wisp that passes through rock
 * (a vex's body, drawn with the vanilla vex for now), unarmed, weak, trailing grey-green motes,
 * and gone after {@link #LIFE_TICKS}. Never saved.
 */
public class NatureSpirit extends Vex {
    public static final int LIFE_TICKS = 40 * 20;

    public NatureSpirit(EntityType<? extends NatureSpirit> type, Level level) {
        super(type, level);
        xpReward = 0;
        setLimitedLife(LIFE_TICKS);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Vex.createAttributes().add(Attributes.MAX_HEALTH, 6.0).add(Attributes.ATTACK_DAMAGE, 2.0);
    }

    /** No sword: a spirit strikes with its touch. */
    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {}

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount % 3 == 0) {
            var random = getRandom();
            Vec3 at = position().add((random.nextDouble() - 0.5) * 0.5, 0.3 + random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 0.5);
            Vfx.one(level(), new VfxOptions(VfxKind.SMOKE, 0x9AA88A, 0x2A3024, 0.2f, 0.35f, 22, -0.003f, 0.94f), at, Vec3.ZERO);
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
