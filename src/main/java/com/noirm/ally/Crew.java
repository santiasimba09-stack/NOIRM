package com.noirm.ally;

import com.noirm.entity.ShadowClone;
import com.noirm.entity.SparringNinja;
import com.noirm.player.ShinobiCapability;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Your crew: ninjas you've beaten who agreed to travel with you. Beat a ninja down to
 * {@link #YIELD_HEALTH} of their health and they yield; right-click them to invite them (some
 * ask to join on their own). Crew mates follow you, fight what you fight, and are never hit by
 * your attacks or jutsu (nor you by theirs). Sneak + right-click: wait here / follow me.
 */
public final class Crew {
    /** A ninja yields when a player brings them below this fraction of their health. */
    public static final float YIELD_HEALTH = 0.25f;
    /** How long a yielded ninja waits for an answer before leaving the fight behind. */
    public static final int YIELD_TICKS = 20 * 30;
    /** Chance a beaten ninja asks to join you themselves. */
    public static final float ASKS_TO_JOIN = 0.3f;
    public static final int BASE_SIZE = 3;
    /** Crew mates out of combat heal this much per second. */
    public static final float REGEN_PER_SECOND = 0.5f;
    /** Further than this from their leader, crew mates catch up by jumping to them. */
    public static final double TELEPORT_DISTANCE = 32;

    private Crew() {}

    /** Victories together: a kill by the leader or a crew mate earns respect from the crew around. */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.noirm.Noirm.MOD_ID)
    public static final class CrewEvents {
        private CrewEvents() {}

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void death(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
            if (!(event.getSource().getEntity() instanceof LivingEntity killer) || killer.level().isClientSide()
                    || event.getEntity() instanceof SparringNinja dead && dead.leaderId() != null) {
                return;
            }
            UUID side = sideOf(killer);
            for (SparringNinja mate : killer.level().getEntitiesOfClass(SparringNinja.class, event.getEntity().getBoundingBox().inflate(24),
                    m -> side.equals(m.leaderId()))) {
                AllyMind.respect(mate, 1.5f, null);
            }
        }
    }

    /** How many crew mates {@code player} can lead: 3, +1 per 20 taijutsu above 10. */
    public static int maxSize(Player player) {
        float taijutsu = ShinobiCapability.get(player).map(d -> d.getTaijutsu()).orElse(10f);
        return BASE_SIZE + (int) Math.max(0, (taijutsu - 10) / 20);
    }

    /**
     * Whose side is {@code entity} on? A player is their own side; a crew mate is on their
     * leader's; a shadow clone is on its owner's side.
     */
    public static UUID sideOf(LivingEntity entity) {
        if (entity instanceof ShadowClone clone) {
            UUID owner = clone.ownerId().orElse(null);
            if (owner == null) {
                return entity.getUUID();
            }
            Entity ownerEntity = entity.level() instanceof ServerLevel level ? level.getEntity(owner) : null;
            return ownerEntity instanceof LivingEntity living && living != entity ? sideOf(living) : owner;
        }
        if (entity instanceof SparringNinja ninja && ninja.leaderId() != null) {
            return ninja.leaderId();
        }
        return entity.getUUID();
    }

    public static boolean sameSide(LivingEntity a, LivingEntity b) {
        return a == b || sideOf(a).equals(sideOf(b));
    }

    /** A player brought {@code ninja} low: they stop fighting and wait to be invited. */
    public static void yieldTo(SparringNinja ninja, Player victor) {
        ninja.setYielded(victor.getUUID(), ninja.level().getGameTime() + YIELD_TICKS);
        ninja.setTarget(null);
        com.noirm.combat.CombatSystem.setGuard(ninja, false);
        ninja.level().playSound(null, ninja.blockPosition(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.HOSTILE, 1f, 0.6f);
        boolean asks = ninja.getRandom().nextFloat() < ASKS_TO_JOIN;
        victor.sendSystemMessage(Component.translatable(asks ? "message.noirm.crew.asks" : "message.noirm.crew.yields",
                ninja.getDisplayName()).withStyle(asks ? ChatFormatting.AQUA : ChatFormatting.GRAY));
    }

    /** {@code player} invites a yielded {@code ninja}. */
    public static boolean recruit(Player player, SparringNinja ninja) {
        var data = ShinobiCapability.get(player).orElse(null);
        if (data == null) {
            return false;
        }
        data.crew().removeIf(id -> !(stillAlive(player, id)));
        if (data.crew().size() >= maxSize(player)) {
            player.displayClientMessage(Component.translatable("message.noirm.crew.full", maxSize(player)), true);
            return false;
        }
        ninja.joinCrew(player.getUUID());
        ninja.setRespect(AllyMind.respectFromPower(player, ninja));
        ninja.setHealth(Math.max(ninja.getHealth(), ninja.getMaxHealth() * 0.5f));
        data.crew().add(ninja.getUUID());
        player.sendSystemMessage(Component.translatable("message.noirm.crew.joined", ninja.getDisplayName())
                .withStyle(ChatFormatting.GREEN));
        player.level().playSound(null, ninja.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.6f);
        return true;
    }

    private static boolean stillAlive(Player player, UUID id) {
        if (!(player.level() instanceof ServerLevel level)) {
            return true;
        }
        Entity entity = level.getEntity(id);
        return entity == null || entity.isAlive(); // unloaded crew mates still count
    }

    /** A crew mate died: they leave the crew, and their leader hears of it. */
    public static void onDeath(SparringNinja ninja) {
        UUID leader = ninja.leaderId();
        if (leader == null || !(ninja.level() instanceof ServerLevel level)) {
            return;
        }
        Player player = level.getPlayerByUUID(leader);
        // The rest of the crew feels it.
        for (SparringNinja mate : level.getEntitiesOfClass(SparringNinja.class, ninja.getBoundingBox().inflate(48),
                m -> m != ninja && leader.equals(m.leaderId()))) {
            AllyMind.respect(mate, -5f, null);
        }
        if (player != null) {
            ShinobiCapability.get(player).ifPresent(d -> d.crew().remove(ninja.getUUID()));
            player.sendSystemMessage(Component.translatable("message.noirm.crew.fallen", ninja.getDisplayName())
                    .withStyle(ChatFormatting.RED));
        }
    }
}
