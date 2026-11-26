package com.noirm.creator;

import com.noirm.Noirm;
import com.noirm.biju.BijuSystem;
import com.noirm.clan.Clan;
import com.noirm.clan.ClanSystem;
import com.noirm.clan.ClanTrait;
import com.noirm.clan.WorldClans;
import com.noirm.network.NoirmNetwork;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * The rules of character creation (blueprint §6). The server decides everything; the client's
 * screen only shows the state and asks.
 *
 * <ul>
 *   <li><b>First join:</b> a newcomer is held in place (frozen, can't be hurt, can't act) and the
 *       creation screen opens. Their first genetics roll is applied straight away, so they always
 *       have a clan even while the screen is open.</li>
 *   <li><b>Genetics:</b> each roll is a clan from this world's clan list
 *       ({@link WorldClans#rollForNewcomer}, the same one villages and NPCs use) plus a blank
 *       stat spread ({@link #STAT_MIN}–{@link #STAT_MAX} each for RES, TAI, SPD, CTL and WIL) that
 *       the clan's traits then raise. You see up to {@link #MAX_ROLLS} rolls (the first, then
 *       {@link #MAX_REROLLS} rerolls); every roll you've seen stays on the list and you keep whichever
 *       one you pick (so a reroll is never a loss).</li>
 *   <li><b>Talents</b> can be rerolled on their own, {@link #MAX_TALENT_REROLLS} times: the chosen roll
 *       keeps its clan and stats and gets a new set of talents. Every set seen stays, and you can go
 *       back to any of them.</li>
 *   <li><b>Finishing</b> saves the look, applies the chosen roll, sets your height (the hitbox
 *       follows) and lets you go.</li>
 * </ul>
 */
public final class CharacterCreator {
    /** Clan rolls a new character sees in all: the first one plus {@link #MAX_REROLLS} rerolls. */
    public static final int MAX_ROLLS = 5;
    /** Rerolls allowed after the first roll (so up to {@link #MAX_ROLLS} rolls are seen). */
    public static final int MAX_REROLLS = MAX_ROLLS - 1;
    /**
     * Talent rerolls, apart from the clan rolls: each gives the chosen roll a fresh set of talents
     * (same clan, same stats). Five is enough to shake off a bad negative talent most of the time
     * without fishing for a perfect set.
     */
    public static final int MAX_TALENT_REROLLS = 5;
    /** The blank stats are rolled between these (inclusive) before traits. */
    public static final int STAT_MIN = 8;
    public static final int STAT_MAX = 12;
    /** What clan traits add on top of the rolled stats (SWIFT and FIERCE keep their old +3). */
    public static final int TRAIT_STAT_BONUS = 2;
    public static final int SWIFT_BONUS = 3;
    public static final int FIERCE_BONUS = 3;
    /** Held players drifting further than this from their spot are put back. */
    private static final double HOLD_SLACK = 0.05;

    public static GameRules.Key<GameRules.BooleanValue> CREATION_RULE;

    private CharacterCreator() {}

    /** Called once while the mod loads. */
    public static void registerGameRules() {
        CREATION_RULE = GameRules.register("noirmCharacterCreation", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true));
    }

    /** Whether newcomers get the creation screen (gamerule {@code noirmCharacterCreation}, on by default). */
    public static boolean enabled(MinecraftServer server) {
        return CREATION_RULE == null || server.getGameRules().getBoolean(CREATION_RULE);
    }

    // ------------------------------------------------------------ reading

    public static Optional<CreatorData> data(Player player) {
        return ShinobiCapability.get(player).map(ShinobiData::creator);
    }

    /** Held players can't move, be hurt or act. Safe to call for any entity, on either side. */
    public static boolean isHeld(Entity entity) {
        return entity instanceof Player player && data(player).map(CreatorData::held).orElse(false);
    }

    /** The player's height multiplier (1 if they have no look). */
    public static float height(Player player) {
        return data(player).map(CreatorData::height).orElse(1f);
    }

    // ------------------------------------------------------------ the flow

    /**
     * A newcomer arrives: roll their first genetics, apply it, hold them and open the screen.
     * Called instead of the old random clan when the gamerule is on.
     */
    public static void beginFirstJoin(ServerPlayer player, ShinobiData data) {
        CreatorData creator = data.creator();
        creator.clearRolls();
        GeneticRoll first = roll(player);
        creator.addRoll(first);
        creator.setChosen(0);
        apply(player, data, first);
        if (!creator.hasLook()) {
            creator.setLook(PlayerLook.random(player.getRandom()));
        }
        creator.setMode(CreatorData.Mode.FIRST_JOIN);
        creator.hold(player.getX(), player.getY(), player.getZ());
        sendState(player, data);
    }

    /** A player logs back in: still creating? Hold them where they are now and reopen. */
    public static void resume(ServerPlayer player, ShinobiData data) {
        CreatorData creator = data.creator();
        creator.fillMissingTalents(r -> clanOf(player.server, r).map(Clan::talentBias).orElse(java.util.Map.of()), player.getRandom());
        if (creator.held()) {
            creator.hold(player.getX(), player.getY(), player.getZ());
        }
        if (creator.mode() != CreatorData.Mode.NONE) {
            sendState(player, data);
        }
    }

    /** {@code /noirm creator}: appearance only (or full with fresh rolls, {@code genetics}). Never holds. */
    public static void open(ServerPlayer player, ShinobiData data, boolean genetics) {
        CreatorData creator = data.creator();
        if (creator.mode() == CreatorData.Mode.FIRST_JOIN) {
            sendState(player, data); // already creating: just show it again
            return;
        }
        if (genetics) {
            creator.clearRolls();
            creator.addRoll(roll(player));
            creator.setChosen(0);
        }
        if (!creator.hasLook()) {
            creator.setLook(PlayerLook.random(player.getRandom()));
        }
        creator.setMode(genetics ? CreatorData.Mode.FULL : CreatorData.Mode.APPEARANCE);
        sendState(player, data);
    }

    /** One more roll, if any are left. Returns whether it happened. */
    public static boolean reroll(ServerPlayer player, ShinobiData data) {
        CreatorData creator = data.creator();
        if (!creator.mode().genetics() || creator.rerollsLeft() <= 0) {
            return false;
        }
        creator.useReroll();
        creator.addRoll(roll(player));
        creator.setChosen(creator.rolls().size() - 1);
        sendState(player, data);
        return true;
    }

    /** New talents for the chosen roll (its clan and stats stay), if any talent rerolls are left. */
    public static boolean rerollTalents(ServerPlayer player, ShinobiData data) {
        CreatorData creator = data.creator();
        GeneticRoll roll = creator.chosenRoll();
        if (!creator.mode().genetics() || roll == null || creator.talentRerollsLeft() <= 0) {
            return false;
        }
        java.util.Map<String, Float> bias = clanOf(player.server, roll).map(Clan::talentBias).orElse(java.util.Map.of());
        creator.useTalentReroll();
        creator.addTalentSet(com.noirm.traits.Talents.roll(player.getRandom(), bias));
        sendState(player, data);
        return true;
    }

    /** The chosen roll goes back to one of the talent sets it has seen (free). */
    public static boolean chooseTalents(ServerPlayer player, ShinobiData data, int set) {
        CreatorData creator = data.creator();
        if (!creator.mode().genetics() || !creator.selectTalentSet(set)) {
            return false;
        }
        sendState(player, data);
        return true;
    }

    /** Picks one of the rolls already seen. */
    public static boolean choose(ServerPlayer player, ShinobiData data, int index) {
        CreatorData creator = data.creator();
        if (!creator.mode().genetics() || index < 0 || index >= creator.rolls().size()) {
            return false;
        }
        creator.setChosen(index);
        sendState(player, data);
        return true;
    }

    /** Closes the screen without changes (not allowed for a newcomer: they must finish). */
    public static boolean cancel(ServerPlayer player, ShinobiData data) {
        CreatorData creator = data.creator();
        if (creator.mode() == CreatorData.Mode.FIRST_JOIN || creator.mode() == CreatorData.Mode.NONE) {
            return false;
        }
        creator.setMode(CreatorData.Mode.NONE);
        sendState(player, data);
        return true;
    }

    /**
     * Confirms the screen: saves {@code look}, applies the chosen roll (if this mode rolls
     * genetics), releases the hold and tells everyone who can see the player how they look.
     */
    public static boolean finish(ServerPlayer player, ShinobiData data, PlayerLook look) {
        CreatorData creator = data.creator();
        CreatorData.Mode mode = creator.mode();
        if (mode == CreatorData.Mode.NONE) {
            return false;
        }
        setLook(player, data, look == null ? creator.look() : look);
        if (mode.genetics()) {
            GeneticRoll roll = creator.chosenRoll();
            if (roll != null) {
                apply(player, data, roll);
            }
            player.sendSystemMessage(ClanSystem.describe(data.identity()));
        }
        if (mode == CreatorData.Mode.FIRST_JOIN) {
            BijuSystem.maybeBornJinchuriki(player, data, player.getRandom().nextFloat(), player.getRandom());
            player.sendSystemMessage(Component.translatable("message.noirm.clan.hint"));
            player.sendSystemMessage(Component.translatable("message.noirm.creator.done").withStyle(ChatFormatting.GRAY));
        }
        creator.release();
        creator.setMode(CreatorData.Mode.NONE);
        creator.clearRolls();
        com.noirm.progress.BeginningsTrigger.created(player); // "Path of the Shinobi"
        data.markDirty();
        data.markPowersDirty();
        sendState(player, data);
        return true;
    }

    /** {@code /noirm creator skip}: finish now with the look so far (random if none) and the chosen roll. */
    public static boolean skip(ServerPlayer player, ShinobiData data) {
        CreatorData creator = data.creator();
        if (creator.mode() == CreatorData.Mode.NONE) {
            return false;
        }
        return finish(player, data, creator.hasLook() ? creator.look() : PlayerLook.random(player.getRandom()));
    }

    /** Saves a look, resizes the player to its height and shows it to everyone near. */
    public static void setLook(Player player, ShinobiData data, PlayerLook look) {
        data.creator().setLook(look);
        player.refreshDimensions();
        if (player instanceof ServerPlayer server) {
            broadcastLook(server);
        }
    }

    // ------------------------------------------------------------ genetics

    /** A fresh roll: a clan from the world's list and a blank stat spread. */
    public static GeneticRoll roll(ServerPlayer player) {
        RandomSource random = player.getRandom();
        Clan clan = WorldClans.get(player.server).rollForNewcomer(random);
        return new GeneticRoll(clan.id(), stat(random), stat(random), stat(random), stat(random), stat(random),
                com.noirm.traits.Talents.roll(random, clan.talentBias())); // personal talents, biased by the clan
    }

    private static int stat(RandomSource random) {
        return STAT_MIN + random.nextInt(STAT_MAX - STAT_MIN + 1);
    }

    /** The clan of a roll, if this world still knows it. */
    public static Optional<Clan> clanOf(MinecraftServer server, GeneticRoll roll) {
        return WorldClans.get(server).find(roll.clanId());
    }

    /**
     * The stats a roll gives with its clan: RES, TAI, SPD, CTL, WIL. The rolled base plus the
     * clan's speed/taijutsu bonus and its traits (Deep Reserves → RES, Fierce → TAI, Swift → SPD,
     * Chakra Flow → CTL, Iron Will → WIL).
     */
    public static float[] finalStats(GeneticRoll roll, Clan clan) {
        java.util.List<ClanTrait> traits = clan == null ? java.util.List.of() : clan.traits();
        java.util.Map<com.noirm.traits.Perk, Float> perks = com.noirm.traits.Perks.resolve(
                clan == null ? java.util.Map.of() : clan.perks(), roll.talents()); // the blood's and the talents' starting stats
        return new float[]{
                Math.max(1f, roll.reserves() + (traits.contains(ClanTrait.DEEP_RESERVES) ? TRAIT_STAT_BONUS : 0)
                        + perks.getOrDefault(com.noirm.traits.Perk.START_RESERVES, 0f)),
                Math.max(1f, roll.taijutsu() + (clan == null ? 0 : clan.taijutsuBonus()) + (traits.contains(ClanTrait.FIERCE) ? FIERCE_BONUS : 0)
                        + perks.getOrDefault(com.noirm.traits.Perk.START_TAIJUTSU, 0f)),
                Math.max(1f, roll.speed() + (clan == null ? 0 : clan.speedBonus()) + (traits.contains(ClanTrait.SWIFT) ? SWIFT_BONUS : 0)
                        + perks.getOrDefault(com.noirm.traits.Perk.START_SPEED, 0f)),
                Math.max(1f, roll.control() + (traits.contains(ClanTrait.CHAKRA_FLOW) ? TRAIT_STAT_BONUS : 0)
                        + perks.getOrDefault(com.noirm.traits.Perk.START_CONTROL, 0f)),
                Math.max(1f, roll.willpower() + (traits.contains(ClanTrait.IRON_WILL) ? TRAIT_STAT_BONUS : 0)
                        + perks.getOrDefault(com.noirm.traits.Perk.START_WILLPOWER, 0f))};
    }

    /** Joins the roll's clan (natures, starter jutsu, kekkei genkai) and sets the rolled stats. */
    public static void apply(Player player, ShinobiData data, GeneticRoll roll) {
        MinecraftServer server = player.getServer();
        Clan clan = server == null ? null : clanOf(server, roll).orElse(null);
        if (clan == null) {
            Noirm.LOGGER.warn("Character creation: unknown clan {} for {}", roll.clanId(), player.getName().getString());
            return;
        }
        ClanSystem.assign(player, data, clan);
        float[] stats = finalStats(roll, clan);
        data.setReserves(stats[0]);
        data.setTaijutsu(stats[1]);
        data.setSpeed(stats[2]);
        data.setControl(stats[3]);
        data.setWillpower(stats[4]);
        java.util.Map<com.noirm.traits.Perk, Float> perks = com.noirm.traits.Perks.resolve(clan.perks(), roll.talents());
        data.setVitality(ShinobiData.DEFAULT_STAT + perks.getOrDefault(com.noirm.traits.Perk.START_VITALITY, 0f));
        data.setDurability(ShinobiData.DEFAULT_STAT + perks.getOrDefault(com.noirm.traits.Perk.START_DURABILITY, 0f));
        data.talents().set(roll.talents());
        data.talents().setVersion(com.noirm.traits.TalentData.CURRENT);
        data.refill();
        data.markDirty();
        data.markPowersDirty();
    }

    // ------------------------------------------------------------ holding

    /** Every server tick for a held player: back to their spot, no falling, no momentum. */
    public static void enforceHold(Player player, CreatorData creator) {
        if (!creator.held()) {
            return;
        }
        player.fallDistance = 0f;
        player.setDeltaMovement(Vec3.ZERO);
        player.clearFire();
        if (player.distanceToSqr(creator.holdX(), creator.holdY(), creator.holdZ()) > HOLD_SLACK * HOLD_SLACK) {
            player.teleportTo(creator.holdX(), creator.holdY(), creator.holdZ());
            if (player.distanceToSqr(creator.holdX(), creator.holdY(), creator.holdZ()) > HOLD_SLACK * HOLD_SLACK) {
                player.setPos(creator.holdX(), creator.holdY(), creator.holdZ()); // fake players ignore teleports
            }
        }
    }

    // ------------------------------------------------------------ network

    public static void sendState(ServerPlayer player, ShinobiData data) {
        NoirmNetwork.sendToPlayer(player, CreatorPackets.State.of(player, data));
    }

    public static void broadcastLook(ServerPlayer player) {
        NoirmNetwork.sendToTrackingAndSelf(player, CreatorPackets.LookSync.of(player));
    }
}
