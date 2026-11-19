package com.noirm.hound;

import com.noirm.Noirm;
import com.noirm.entity.MasterNinja;
import com.noirm.entity.ShadowClone;
import com.noirm.entity.SparringNinja;
import com.noirm.mission.PlayerRank;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.traits.Perk;
import com.noirm.traits.Perks;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The hound's hooks into the rest of the game: it follows you across dimensions and logins, learns
 * when you train beside it, earns XP and bond from fights together, a marked target takes more
 * from you, the Two-Headed Wolf shields you, Inuzuka NPC ninjas bring their own hounds, and an
 * Inuzuka friend may give you a puppy.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class HoundEvents {
    /** How often (ticks) an owner's hound is checked on. */
    public static final int OWNER_CHECK_TICKS = 40;
    /** NPC ninjas: their hound's level follows their power (at least this level, at most the other). */
    public static final int NPC_MIN_LEVEL = 6;
    public static final int NPC_MAX_LEVEL = 40;
    /** The persistent tag that says an NPC's hound was handed out (and, in tests, that one may be). */
    public static final String NPC_DONE = "noirm_hound_done";
    public static final String NPC_TEST_OK = "noirm_hound_test";

    private HoundEvents() {}

    // ---------------------------------------------------------------- owners

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player instanceof FakePlayer
                || player.tickCount % OWNER_CHECK_TICKS != 11) {
            return;
        }
        ShinobiCapability.get(player).ifPresent(data -> {
            Hounds.tickOwner(player, data);
            if (player.tickCount % 600 == 11) {
                friendsLitter(player, data);
            }
        });
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            ShinobiCapability.get(player).ifPresent(data -> Hounds.tickOwner(player, data));
        }
    }

    @SubscribeEvent
    public static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            ShinobiCapability.get(player).ifPresent(data -> Hounds.tickOwner(player, data));
        }
    }

    /** Training at your side: the hound learns too (from TrainingSystem.grant). */
    public static void ownerTrained(Player player, float xp) {
        if (player.level().isClientSide() || xp <= 0f) {
            return;
        }
        NinjaHound hound = Hounds.find(player);
        if (hound != null && hound.level() == player.level() && !hound.isDown()
                && hound.distanceToSqr(player) < Hounds.TOGETHER_RANGE * Hounds.TOGETHER_RANGE) {
            Hounds.addXp(hound, xp * Hounds.TRAINING_SHARE);
        }
    }

    // ---------------------------------------------------------------- fights

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) {
            return;
        }
        if (dead instanceof NinjaHound hound) {
            Hounds.lost(hound); // only the void or /kill get here: a fight only knocks it out
            return;
        }
        LivingEntity killer = event.getSource().getEntity() instanceof LivingEntity l ? l : null;
        if (killer == null) {
            return;
        }
        NinjaHound hound = killer instanceof NinjaHound h ? h : Hounds.houndOf(killer);
        if (hound == null || hound.isDown() || hound.level() != dead.level() || hound.distanceToSqr(dead) > 24 * 24) {
            return;
        }
        float xp = Hounds.XP_PER_KILL + dead.getMaxHealth() * Hounds.XP_PER_KILL_HEALTH;
        Hounds.addXp(hound, killer == hound ? xp : xp * 0.5f);
        Hounds.addBond(hound, Hounds.BOND_PER_KILL);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void hurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        if (event.getSource().getEntity() instanceof LivingEntity attacker && !(attacker instanceof NinjaHound)) {
            event.setAmount(HoundTechniques.ownerDamageDealt(attacker, victim, event.getAmount()));
        }
        if (!(victim instanceof NinjaHound)) {
            event.setAmount(HoundTechniques.ownerDamageTaken(victim, event.getAmount()));
        }
    }

    // ---------------------------------------------------------------- NPC ninjas

    @SubscribeEvent
    public static void ninjaTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof SparringNinja ninja && !ninja.level().isClientSide() && ninja.tickCount % 40 == 13) {
            npcHound(ninja);
        }
    }

    /** An Inuzuka ninja brings a hound of its own, sized to the ninja's strength. Returns it (or null). */
    public static NinjaHound npcHound(SparringNinja ninja) {
        if (ninja instanceof ShadowClone || ninja instanceof MasterNinja || ninja.clanId().isEmpty()
                || !(ninja.level() instanceof ServerLevel level) || !ninja.isAlive()) {
            return null;
        }
        CompoundTag pd = ninja.getPersistentData();
        if (pd.getBoolean(NPC_DONE)) {
            return null;
        }
        if (com.noirm.gametest.TestWorld.isTestServer(level.getServer()) && !pd.getBoolean(NPC_TEST_OK)) {
            return null; // tests ask for hounds explicitly (so they don't crowd other tests)
        }
        pd.putBoolean(NPC_DONE, true);
        if (Perks.totals(ninja).getOrDefault(Perk.HOUND, 0f) <= 0f) {
            return null;
        }
        NinjaHound hound = HoundRegistry.NINJA_HOUND.get().create(level);
        if (hound == null) {
            return null;
        }
        float power = com.noirm.combat.Perception.basePower(ninja);
        int houndLevel = NPC_MIN_LEVEL;
        while (houndLevel < NPC_MAX_LEVEL && HoundGrowth.powerFor(houndLevel + 1) <= power) {
            houndLevel++;
        }
        hound.moveTo(ninja.getX() + 0.8, ninja.getY(), ninja.getZ() + 0.8, ninja.getYRot(), 0f);
        hound.bondTo(ninja);
        hound.setTraits(HoundTraits.roll(ninja.getRandom()));
        hound.setLook(HoundLook.roll(ninja.getRandom()));
        hound.setHoundLevel(houndLevel);
        hound.setBond(40f + ninja.getRandom().nextFloat() * 40f);
        hound.setHealth(hound.getMaxHealth());
        level.addFreshEntity(hound);
        return hound;
    }

    /** At high standing (chūnin or above), an Inuzuka crew mate who trusts you may give you a pup from their hound's litter. */
    static void friendsLitter(ServerPlayer player, ShinobiData data) {
        if (data.hound().has() || !PlayerRank.atLeast(player, PlayerRank.CHUNIN)) {
            return;
        }
        for (SparringNinja mate : com.noirm.ally.CrewPackets.loaded(player)) {
            if (mate.level() != player.level() || mate.distanceToSqr(player) > 16 * 16 || mate.respect() < Hounds.GIFT_RESPECT
                    || Perks.totals(mate).getOrDefault(Perk.HOUND, 0f) <= 0f || Hounds.houndOf(mate) == null) {
                continue;
            }
            player.sendSystemMessage(Component.translatable("message.noirm.hound.litter", mate.getDisplayName()).withStyle(ChatFormatting.GOLD));
            Hounds.grantPuppy(player, null, false);
            return;
        }
    }
}
