package com.noirm.traits;

import com.noirm.Noirm;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Status effects that come with clan techniques. {@code noirm:chakra_seal}: a sealing formula on
 * the body; while it lasts no jutsu can be cast ({@code JutsuCaster.checkRequirements} answers
 * "sealed"). The Uzumaki's Adamantine Sealing Chains put it on whoever they bind.
 */
public final class PerkEffects {
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Noirm.MOD_ID);

    public static final RegistryObject<MobEffect> CHAKRA_SEAL = EFFECTS.register("chakra_seal",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0xC8A040) {});

    private PerkEffects() {}

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }

    /** Whether a sealing formula stops {@code entity} from moulding chakra right now. */
    public static boolean chakraSealed(LivingEntity entity) {
        return CHAKRA_SEAL.isPresent() && entity.hasEffect(CHAKRA_SEAL.get());
    }
}
