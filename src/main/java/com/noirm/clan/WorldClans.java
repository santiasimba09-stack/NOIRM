package com.noirm.clan;

import com.noirm.Noirm;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The clans known in this world. It starts empty and grows: every time someone (a player now,
 * villages and NPCs later) needs a clan, it's either a canon clan, a custom clan already known
 * here, or a brand-new generated one. Saved with the world.
 */
public final class WorldClans extends SavedData {
    private static final String NAME = "noirm_clans";
    /** Chance a newcomer belongs to a canon clan. */
    public static final float CANON_CHANCE = 0.10f;
    /** Otherwise, chance they join a custom clan this world already knows (vs a new one). */
    public static final float KNOWN_CLAN_CHANCE = 0.5f;

    private final List<Clan> custom = new ArrayList<>();

    public static WorldClans get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(WorldClans::load, WorldClans::new, NAME);
    }

    /** Looks up any clan, canon or custom. */
    public Optional<Clan> find(String id) {
        return ClanData.canon(id).or(() -> custom.stream().filter(c -> c.id().equals(id)).findFirst());
    }

    public List<Clan> customClans() {
        return List.copyOf(custom);
    }

    /** All clans: canon first, then custom in the order they were discovered. */
    public List<Clan> allClans() {
        return Stream.concat(ClanData.canonClans().stream(), custom.stream()).toList();
    }

    /** Invents a new custom clan and remembers it. */
    public Clan generate(RandomSource random) {
        Clan clan = ClanGenerator.generate(random, allClans().stream().map(Clan::name).toList());
        custom.add(clan);
        setDirty();
        Noirm.LOGGER.info("A new clan was discovered: {} ({})", clan.name(), clan.id());
        return clan;
    }

    /** Picks the clan for a newcomer: rarely canon, otherwise a known or brand-new custom clan. */
    public Clan rollForNewcomer(RandomSource random) {
        List<Clan> canon = ClanData.canonClans();
        if (!canon.isEmpty() && random.nextFloat() < CANON_CHANCE) {
            int total = canon.stream().mapToInt(c -> Math.max(1, c.weight())).sum();
            int roll = random.nextInt(total);
            for (Clan clan : canon) {
                roll -= Math.max(1, clan.weight());
                if (roll < 0) {
                    return clan;
                }
            }
        }
        if (!custom.isEmpty() && random.nextFloat() < KNOWN_CLAN_CHANCE) {
            return custom.get(random.nextInt(custom.size()));
        }
        return generate(random);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Clan clan : custom) {
            Clan.CODEC.encodeStart(NbtOps.INSTANCE, clan).result().ifPresent(list::add);
        }
        tag.put("Custom", list);
        return tag;
    }

    /** Reads saved clans (also used by tests). */
    public static WorldClans load(CompoundTag tag) {
        WorldClans clans = new WorldClans();
        for (Tag entry : tag.getList("Custom", Tag.TAG_COMPOUND)) {
            Clan.CODEC.parse(NbtOps.INSTANCE, entry).resultOrPartial(e -> Noirm.LOGGER.error("Bad saved clan: {}", e))
                    .ifPresent(clans.custom::add);
        }
        return clans;
    }
}
