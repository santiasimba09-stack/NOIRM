package com.noirm.mission;

import com.noirm.Noirm;
import com.noirm.economy.Ryo;
import com.noirm.economy.RyoPouchItem;
import com.noirm.entity.SparringNinja;
import com.noirm.player.ShinobiCapability;
import com.noirm.world.wild.WildNinjas;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps careers moving: missions and exams every half second, the HUD's copy in sync, mission
 * deaths, ryo dropped by rogues and picked up in pouches, and your rank shown with your name.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class CareerEvents {
    /** How often missions and exams are checked. */
    public static final int UPDATE_TICKS = 10;
    /** A defeated rogue drops a pouch of this much ryo, plus up to {@link #ROGUE_RYO_BONUS} and a bit for their strength. */
    public static final int ROGUE_RYO = 5, ROGUE_RYO_BONUS = 15;

    private CareerEvents() {}

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % UPDATE_TICKS != 0) {
            return;
        }
        Missions.update(player);
        Exams.update(player);
        ShinobiCapability.get(player).ifPresent(data -> {
            CareerData career = data.career();
            // The clock and arrow change all the time: refresh a mission's HUD every second regardless.
            boolean live = career.mission() != null || career.exam() != null;
            if (career.consumeDirty() || live && player.tickCount % 20 == 0) {
                CareerPackets.sync(player, career);
            }
        });
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        ShinobiCapability.get(event.getEntity()).ifPresent(d -> d.career().markDirty());
        event.getEntity().refreshDisplayName();
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        ShinobiCapability.get(event.getEntity()).ifPresent(d -> d.career().markDirty());
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        Entity dead = event.getEntity();
        if (dead.level().isClientSide()) {
            return;
        }
        Entity killer = event.getSource().getEntity();
        if (dead.getTags().contains(Missions.MISSION_TAG)) {
            Missions.onDeath(dead, killer);
        }
        if (dead instanceof Player player) {
            CareerData career = ShinobiCapability.get(player).map(d -> d.career()).orElse(null);
            if (career != null && career.exam() != null && career.exam().stage == CareerData.Exam.Stage.FIGHT) {
                Exams.fail(player, "exam.noirm.failed.fell");
            }
        }
    }

    /** A defeated rogue carries a little ryo. */
    @SubscribeEvent
    public static void drops(LivingDropsEvent event) {
        if (event.getEntity() instanceof SparringNinja ninja && !ninja.level().isClientSide() && WildNinjas.isRogue(ninja)
                && (event.getSource().getEntity() instanceof Player || ninja.getKillCredit() instanceof Player)) {
            int amount = ROGUE_RYO + ninja.getRandom().nextInt(ROGUE_RYO_BONUS + 1) + Math.round(ninja.combatTaijutsu() / 5f);
            event.getDrops().add(new ItemEntity(ninja.level(), ninja.getX(), ninja.getY() + 0.5, ninja.getZ(), RyoPouchItem.of(amount)));
        }
    }

    /** A ryo pouch picked up off the ground goes straight into the wallet. */
    @SubscribeEvent
    public static void pickup(EntityItemPickupEvent event) {
        ItemEntity item = event.getItem();
        Player player = event.getEntity();
        if (player.level().isClientSide() || !Ryo.isPouch(item.getItem())) {
            return;
        }
        int added = RyoPouchItem.deposit(player, item.getItem());
        player.take(item, item.getItem().getCount());
        item.discard();
        event.setCanceled(true);
        player.displayClientMessage(Component.translatable("message.noirm.ryo.picked", Ryo.format(added), Ryo.format(Ryo.balance(player)))
                .withStyle(ChatFormatting.GOLD), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 0.6f, 1.6f);
    }

    /** Your rank with your name (chat, the player list): "[Chūnin] Name". */
    @SubscribeEvent
    public static void nameFormat(PlayerEvent.NameFormat event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        PlayerRank rank = PlayerRank.of(player);
        event.setDisplayname(Component.translatable("rank.noirm.name", rank.title(), event.getDisplayname()));
    }
}
