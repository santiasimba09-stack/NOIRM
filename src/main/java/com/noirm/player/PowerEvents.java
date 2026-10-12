package com.noirm.player;

import com.noirm.Noirm;
import com.noirm.awakening.AwakeningSystem;
import com.noirm.biju.BijuSystem;
import com.noirm.clan.Clan;
import com.noirm.clan.ClanSystem;
import com.noirm.clan.ClanTrait;
import com.noirm.clan.WorldClans;
import com.noirm.combat.CombatStates;
import com.noirm.dojutsu.Sharingan;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.network.NoirmNetwork;
import com.noirm.network.PowerPackets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Hooks the powers (clan, jutsu, Sharingan, awakening, biju) into the game. */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class PowerEvents {
    private PowerEvents() {}

    /** A newcomer is born into a clan: rarely canon, otherwise one this world knows or a new one. */
    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ShinobiCapability.get(player).ifPresent(data -> {
            if (!data.identity().isAssigned()) {
                Clan clan = WorldClans.get(player.server).rollForNewcomer(player.getRandom());
                ClanSystem.assign(player, data, clan);
                player.sendSystemMessage(ClanSystem.describe(data.identity()));
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.noirm.clan.hint"));
            }
            data.markPowersDirty();
        });
        PowerVisuals.broadcast(player);
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof Player target) {
            PowerVisuals.sendTo(viewer, target);
        }
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        ShinobiCapability.get(player).ifPresent(data -> {
            Sharingan.tick(player, data);
            AwakeningSystem.tick(player, data);
            BijuSystem.tick(player, data);
            CombatStates.get(player).setMaxPosture(com.noirm.combat.CombatState.DEFAULT_MAX_POSTURE
                    + (data.identity().has(ClanTrait.IRON_BODY) ? 30 : 0));
            if (JutsuCaster.casting(player) != null && player.tickCount % 5 == 0) {
                data.markPowersDirty(); // keep the charge/channel bar moving
            }
            if (data.consumePowersDirty()) {
                NoirmNetwork.sendToPlayer(player, PowerPackets.SyncPowers.of(player, data));
            }
        });
    }

    /** Charging and channeling, for players and NPCs alike. */
    @SubscribeEvent
    public static void livingTick(LivingEvent.LivingTickEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            JutsuCaster.tick(event.getEntity());
        }
    }

    /** Runs after guard and armor have reduced the hit: the awakening may save the player. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isCanceled()) {
            event.setAmount(AwakeningSystem.onIncomingDamage(player, event.getAmount(), player.getRandom()));
        }
    }

    /** Resolve: builds from damage dealt and taken in a fight. */
    @SubscribeEvent
    public static void damage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || event.getAmount() <= 0) {
            return;
        }
        if (victim instanceof Player player && event.getSource().getEntity() != null) {
            AwakeningSystem.onDamageTaken(player, event.getAmount());
        }
        if (event.getSource().getEntity() instanceof Player attacker && attacker != victim) {
            AwakeningSystem.onDamageDealt(attacker, victim, event.getAmount());
        }
    }
}
