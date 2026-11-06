package com.noirm.combat;

import com.noirm.Noirm;
import com.noirm.network.CombatClientPackets;
import com.noirm.network.NoirmNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Connects the combat rules to the game: ticking fighters, and dodge/guard/parry for all damage. */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class CombatEvents {
    private CombatEvents() {}

    @SubscribeEvent
    public static void livingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (entity instanceof ServerPlayer player) {
            CombatState state = CombatStates.get(player);
            CombatSystem.tick(player);
            if (state.consumeDirty()) {
                NoirmNetwork.sendToPlayer(player, new CombatClientPackets.SyncState(
                        state.isCombatMode(), state.isGuarding(), state.posture(), state.maxPosture()));
            }
        } else {
            CombatSystem.tick(entity);
        }
    }

    /** Before damage: dodges and parries avoid the hit completely. */
    @SubscribeEvent
    public static void livingAttack(LivingAttackEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getSource().getEntity() != null
                && com.noirm.jutsu.CanonTechniques.deflect(event.getEntity(), event.getSource().getEntity())) {
            event.setCanceled(true); // behind a Water Formation Wall
            return;
        }
        if (!event.getEntity().level().isClientSide() && CombatSystem.onIncomingAttack(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
        }
    }

    /** NOIRM hits apply their own knockback; skip vanilla's extra push. */
    @SubscribeEvent
    public static void knockback(LivingKnockBackEvent event) {
        if (CombatSystem.shouldCancelVanillaKnockback()) {
            event.setCanceled(true);
        }
    }

    /** During damage: a raised guard reduces it and costs posture. */
    @SubscribeEvent
    public static void livingHurt(LivingHurtEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            float amount = event.getAmount() * com.noirm.jutsu.ModeSystem.defenseMultiplier(event.getEntity())
                    * com.noirm.biju.BijuSystem.defenseMultiplier(event.getEntity())
                    / Perception.durability(event.getEntity());
            event.setAmount(CombatSystem.onIncomingDamage(event.getEntity(), event.getSource(), amount));
        }
    }
}
