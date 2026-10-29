package com.noirm.bounty;

import com.noirm.Noirm;
import com.noirm.entity.SparringNinja;
import com.noirm.network.NoirmNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps clients told of the classes they need for name tags and the HUD: every
 * {@link #CHECK_TICKS} each player's and ninja's class is worked out again, and sent to whoever
 * can see them (and the player themself) when it changed; a player who starts seeing someone gets
 * their class straight away.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class ClassTracker {
    public static final int CHECK_TICKS = 40;
    private static final Map<LivingEntity, Integer> SENT = new WeakHashMap<>();

    private ClassTracker() {}

    static boolean tracked(LivingEntity entity) {
        return entity instanceof Player || entity instanceof SparringNinja;
    }

    @SubscribeEvent
    public static void livingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !tracked(entity) || (entity.tickCount + entity.getId()) % CHECK_TICKS != 0) {
            return;
        }
        int code = NinjaClass.code(entity);
        Integer sent = SENT.get(entity);
        if (sent == null || sent != code) {
            SENT.put(entity, code);
            NoirmNetwork.sendToTrackingAndSelf(entity, new BountyPackets.ClassInfo(entity.getId(), code));
        }
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof LivingEntity target && tracked(target)) {
            NoirmNetwork.sendToPlayer(player, new BountyPackets.ClassInfo(target.getId(), NinjaClass.code(target)));
        }
    }
}
