package com.noirm.combat;

import com.noirm.Noirm;
import com.noirm.jutsu.JutsuCaster;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * No melee while forming hand seals (owner): your hands are busy. Light, heavy, launcher and grab
 * starts are refused in {@link CombatSystem#requestAttack}, and a plain (vanilla) punch or sword
 * swing is cancelled here. Players and NPCs alike. Once the seals are done (the jutsu fired, or a
 * charge/channel jutsu moved past its seals), you can strike again.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class SealLock {
    private SealLock() {}

    /** Is this fighter forming hand seals right now? */
    public static boolean blocksMelee(LivingEntity entity) {
        JutsuCaster.Casting casting = JutsuCaster.casting(entity);
        return casting != null && casting.sealing();
    }

    /** Vanilla attacks (combat mode off): the server refuses them mid-seal. The client also skips the swing. */
    @SubscribeEvent
    public static void attack(AttackEntityEvent event) {
        if (!event.getEntity().level().isClientSide() && blocksMelee(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
