package com.noirm.registry;

import com.noirm.Noirm;
import com.noirm.entity.ShadowClone;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.TailedBeast;
import com.noirm.entity.TrainingDummy;
import com.noirm.jutsu.JutsuProjectile;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Every entity NOIRM adds. */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NoirmEntities {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Noirm.MOD_ID);

    public static final RegistryObject<EntityType<SparringNinja>> SPARRING_NINJA = ENTITY_TYPES.register("sparring_ninja",
            () -> EntityType.Builder.of(SparringNinja::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .clientTrackingRange(10)
                    .build(Noirm.id("sparring_ninja").toString()));

    public static final RegistryObject<EntityType<JutsuProjectile>> JUTSU_PROJECTILE = ENTITY_TYPES.register("jutsu_projectile",
            () -> EntityType.Builder.<JutsuProjectile>of(JutsuProjectile::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build(Noirm.id("jutsu_projectile").toString()));

    public static final RegistryObject<EntityType<TrainingDummy>> TRAINING_DUMMY = ENTITY_TYPES.register("training_dummy",
            () -> EntityType.Builder.of(TrainingDummy::new, MobCategory.MISC)
                    .sized(0.6f, 1.9f)
                    .clientTrackingRange(10)
                    .build(Noirm.id("training_dummy").toString()));

    /** Base size of a one-tailed beast; more tails scale it up (see {@link TailedBeast#scaleFor}). */
    public static final RegistryObject<EntityType<TailedBeast>> TAILED_BEAST = ENTITY_TYPES.register("tailed_beast",
            () -> EntityType.Builder.of(TailedBeast::new, MobCategory.MONSTER)
                    .sized(1.4f, 1.4f)
                    .clientTrackingRange(12)
                    .build(Noirm.id("tailed_beast").toString()));

    public static final RegistryObject<EntityType<ShadowClone>> SHADOW_CLONE = ENTITY_TYPES.register("shadow_clone",
            () -> EntityType.Builder.<ShadowClone>of(ShadowClone::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .clientTrackingRange(10)
                    .build(Noirm.id("shadow_clone").toString()));

    /** A shopkeeper in a ninja village (see {@code com.noirm.world.village}). */
    public static final RegistryObject<EntityType<com.noirm.entity.VillageMerchant>> VILLAGE_MERCHANT = ENTITY_TYPES.register("village_merchant",
            () -> EntityType.Builder.of(com.noirm.entity.VillageMerchant::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .clientTrackingRange(10)
                    .build(Noirm.id("village_merchant").toString()));

    /** An invisible seat a villager sits on (a bench, a stool, a step). */
    public static final RegistryObject<EntityType<com.noirm.entity.SeatEntity>> SEAT = ENTITY_TYPES.register("seat",
            () -> EntityType.Builder.<com.noirm.entity.SeatEntity>of(com.noirm.entity.SeatEntity::new, MobCategory.MISC)
                    .sized(0.2f, 0.2f)
                    .noSummon()
                    .clientTrackingRange(8)
                    .build(Noirm.id("seat").toString()));

    private NoirmEntities() {}

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(SPARRING_NINJA.get(), SparringNinja.createAttributes().build());
        event.put(SHADOW_CLONE.get(), SparringNinja.createAttributes().build());
        event.put(TRAINING_DUMMY.get(), TrainingDummy.createAttributes().build());
        event.put(TAILED_BEAST.get(), TailedBeast.createAttributes().build());
        event.put(VILLAGE_MERCHANT.get(), com.noirm.entity.VillageMerchant.createAttributes().build());
    }
}
