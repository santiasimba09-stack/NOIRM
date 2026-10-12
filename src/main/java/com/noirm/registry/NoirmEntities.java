package com.noirm.registry;

import com.noirm.Noirm;
import com.noirm.entity.SparringNinja;
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

    private NoirmEntities() {}

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(SPARRING_NINJA.get(), SparringNinja.createAttributes().build());
    }
}
