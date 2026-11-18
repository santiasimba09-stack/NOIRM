package com.noirm.traits;

import com.noirm.clan.Clan;
import com.noirm.clan.WorldClans;
import com.noirm.entity.SparringNinja;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * NPC ninjas' personal talents, kept in the entity's Forge persistent data (so old ninjas simply
 * roll theirs the first time it matters). Rolled like a player's, biased by their clan.
 *
 * <p>On the game-test server ninjas don't roll talents by themselves (tests use fixed clans and
 * stats, like {@code TestClans}); tests give them with {@link #set}.
 */
public final class NpcTalents {
    static final String KEY = "NoirmTalents";
    static final String ROLLED = "NoirmTalentsRolled";

    private NpcTalents() {}

    /** The ninja's talents, rolling them the first time (once it has a clan). */
    public static List<String> of(SparringNinja ninja) {
        CompoundTag data = ninja.getPersistentData();
        if (!data.getBoolean(ROLLED) && !ninja.clanId().isEmpty() && ninja.getServer() != null && rollsHere(ninja.getServer())) {
            Map<String, Float> bias = WorldClans.get(ninja.getServer()).find(ninja.clanId()).map(Clan::talentBias).orElse(Map.of());
            set(ninja, Talents.roll(ninja.getRandom(), bias));
        }
        List<String> out = new ArrayList<>();
        for (Tag t : data.getList(KEY, Tag.TAG_STRING)) {
            out.add(t.getAsString());
        }
        return out;
    }

    public static void set(SparringNinja ninja, List<String> talents) {
        ListTag list = new ListTag();
        talents.forEach(t -> list.add(StringTag.valueOf(t)));
        ninja.getPersistentData().put(KEY, list);
        ninja.getPersistentData().putBoolean(ROLLED, true);
        Perks.forget(ninja);
    }

    /** Ninjas roll their own talents everywhere but on the game-test server. */
    static boolean rollsHere(MinecraftServer server) {
        return !(server instanceof net.minecraft.gametest.framework.GameTestServer);
    }
}
