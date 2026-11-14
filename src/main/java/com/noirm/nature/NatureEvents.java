package com.noirm.nature;

import com.noirm.Noirm;
import com.noirm.clan.Nature;
import com.noirm.entity.MasterNinja;
import com.noirm.entity.SparringNinja;
import com.noirm.player.ShinobiCapability;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Ticks nature drills, keeps the client's copy fresh, tells old saves about the chakra paper, and drops scrolls from beaten ninjas. */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class NatureEvents {
    /** A beaten ninja (not a crew mate or master) drops a jutsu scroll this often… */
    public static final float JUTSU_SCROLL_DROP = 0.04f;
    /** …and a nature scroll this often. */
    public static final float NATURE_SCROLL_DROP = 0.012f;
    /** How often (ticks) to check whether the player should hear about the chakra paper. */
    private static final int HINT_CHECK = 100;

    private NatureEvents() {}

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        ShinobiCapability.get(player).ifPresent(data -> {
            NatureDrills.tick(player, data);
            NatureTraining t = data.natureTraining();
            // Old saves and new ninjas: roll the hidden natures once a clan is known, and say how to see them.
            if (!t.hinted() && player.tickCount % HINT_CHECK == 0 && data.identity().isAssigned()) {
                NatureDrills.ensureRolled(player, data);
                t.setHinted(true);
                player.sendSystemMessage(Component.translatable("message.noirm.nature.hint").withStyle(ChatFormatting.DARK_AQUA));
            }
            if (t.consumeDirty()) {
                NaturePackets.send(player, data);
            }
        });
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        markDirty(event.getEntity());
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        markDirty(event.getEntity());
    }

    @SubscribeEvent
    public static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        markDirty(event.getEntity());
    }

    private static void markDirty(Player player) {
        ShinobiCapability.get(player).ifPresent(d -> d.natureTraining().markDirty());
    }

    /** Ninjas you beat (not crew mates, not masters) sometimes carry a scroll. */
    @SubscribeEvent
    public static void drops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof SparringNinja ninja) || ninja instanceof MasterNinja || ninja.leaderId() != null
                || !(event.getSource().getEntity() instanceof Player)) {
            return;
        }
        var random = ninja.getRandom();
        ItemStack drop = ItemStack.EMPTY;
        if (random.nextFloat() < NATURE_SCROLL_DROP) {
            Nature nature = NatureDrills.TRAINABLE.get(random.nextInt(NatureDrills.TRAINABLE.size()));
            drop = NatureItems.natureScroll(nature);
        } else if (random.nextFloat() < JUTSU_SCROLL_DROP) {
            float roll = random.nextFloat();
            drop = NatureItems.jutsuScroll(roll < 0.65f ? "C" : roll < 0.92f ? "B" : "A");
        }
        if (!drop.isEmpty()) {
            event.getDrops().add(new ItemEntity(ninja.level(), ninja.getX(), ninja.getY() + 0.5, ninja.getZ(), drop));
        }
    }
}
