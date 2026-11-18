package com.noirm.hiraishin;

import com.noirm.Noirm;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The space-time keys, server side: mark (Y), recall (sneak + Y), jump (X), cycle (U), and the
 * lock-on target the client tells us about (the Body Flicker and the Flying Raijin aim at it).
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class SpaceTimeInput {
    /** Lock-on is client-side: the server keeps what each player last locked on to, for aim. */
    private static final Map<Player, Integer> LOCKED = new WeakHashMap<>();

    private SpaceTimeInput() {}

    static void handle(ServerPlayer player, int action, int arg) {
        switch (action) {
            case HiraishinPackets.MARK -> HiraishinMarking.markFromKey(player);
            case HiraishinPackets.RECALL -> HiraishinMarking.recall(player);
            case HiraishinPackets.JUMP -> HiraishinJump.fromKey(player);
            case HiraishinPackets.CYCLE -> HiraishinMarking.cycle(player);
            case HiraishinPackets.LOCK -> {
                if (arg < 0) {
                    LOCKED.remove(player);
                } else {
                    LOCKED.put(player, arg);
                }
            }
            default -> { }
        }
    }

    /** Sets a player's lock-on target by hand (tests). */
    public static void setLockTarget(Player player, LivingEntity target) {
        if (target == null) {
            LOCKED.remove(player);
        } else {
            LOCKED.put(player, target.getId());
        }
    }

    /** Who {@code entity} is locked on to: a player's lock-on, or a creature's target. Null if none. */
    public static LivingEntity lockTarget(LivingEntity entity) {
        if (entity instanceof Player player) {
            Integer id = LOCKED.get(player);
            if (id == null) {
                return null;
            }
            Entity e = player.level().getEntity(id);
            return e instanceof LivingEntity living && living.isAlive() ? living : null;
        }
        if (entity instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            return target != null && target.isAlive() ? target : null;
        }
        return null;
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LOCKED.remove(event.getEntity());
    }
}
