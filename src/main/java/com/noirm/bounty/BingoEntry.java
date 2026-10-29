package com.noirm.bounty;

import com.noirm.clan.Nature;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One page of the bingo book: a dangerous rogue somewhere in the world. Everything needed to bring
 * them back exactly as they were is kept here (name, clan, strength, look, jutsu seed, eyes, beast),
 * so the rogue only exists as an entity while a player is near their last-seen area.
 */
public final class BingoEntry {
    public enum Status { ACTIVE, CLAIMED }

    public int id;
    /** Their own name (a proper noun: the same in every language). */
    public String given = "";
    /** A legendary's epithet ({@code bingo.noirm.epithet.<key>}); empty for the others. */
    public String epithet = "";
    public String clanId = "";
    public String clanName = "";
    /** Their speed and taijutsu (the other stats follow, like any ninja). */
    public float strength;
    public boolean legendary;
    /** A legendary's kind: their aura, signature and gifts. */
    public Legendary.Kind kind = Legendary.Kind.SHADOW;
    /** A nature on top of their clan's (null = none). */
    public Nature extraNature;
    public int tails;
    public boolean mangekyo;
    /** Seeds their jutsu set, clothes and eyes: the same rogue every time. */
    public long seed;
    /** Last seen around here (y is a guess until they're met). */
    public BlockPos area = BlockPos.ZERO;
    public int bounty;
    public Status status = Status.ACTIVE;
    public String claimedBy = "";
    public long claimedAt;
    /** Their skin look, remembered the first time they're brought into the world. */
    public String look = "";
    /** The body in the world right now (null = none). */
    public UUID entity;
    /** Players who have seen a legendary's entrance. */
    public final Set<UUID> met = new HashSet<>();
    /** When the page was added (game time). */
    public long added;

    public boolean active() {
        return status == Status.ACTIVE;
    }

    /** Their class code ({@link NinjaClass#LEGENDARY_CODE} for a legend). */
    public int code() {
        return legendary ? NinjaClass.LEGENDARY_CODE : NinjaClass.ofPower(strength).ordinal();
    }

    /** "Raizen of the Kurogane clan", or "Raizen, the Crimson Storm". */
    public Component name() {
        return legendary ? Component.translatable("bingo.noirm.name.legend", given, Component.translatable("bingo.noirm.epithet." + epithet))
                : Component.translatable("entity.noirm.sparring_ninja.of_clan", given, clanName);
    }

    static Nature nature(String name) {
        for (Nature n : Nature.values()) {
            if (n.getSerializedName().equals(name)) {
                return n;
            }
        }
        return null;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", id);
        tag.putString("Given", given);
        tag.putString("Epithet", epithet);
        tag.putString("Clan", clanId);
        tag.putString("ClanName", clanName);
        tag.putFloat("Strength", strength);
        tag.putBoolean("Legendary", legendary);
        tag.putString("Kind", kind.name());
        if (extraNature != null) {
            tag.putString("Nature", extraNature.getSerializedName());
        }
        tag.putInt("Tails", tails);
        tag.putBoolean("Mangekyo", mangekyo);
        tag.putLong("Seed", seed);
        tag.put("Area", NbtUtils.writeBlockPos(area));
        tag.putInt("Bounty", bounty);
        tag.putString("Status", status.name());
        tag.putString("ClaimedBy", claimedBy);
        tag.putLong("ClaimedAt", claimedAt);
        tag.putString("Look", look);
        if (entity != null) {
            tag.putUUID("Entity", entity);
        }
        ListTag metTag = new ListTag();
        met.forEach(u -> metTag.add(NbtUtils.createUUID(u)));
        tag.put("Met", metTag);
        tag.putLong("Added", added);
        return tag;
    }

    public static BingoEntry load(CompoundTag tag) {
        BingoEntry e = new BingoEntry();
        e.id = tag.getInt("Id");
        e.given = tag.getString("Given");
        e.epithet = tag.getString("Epithet");
        e.clanId = tag.getString("Clan");
        e.clanName = tag.getString("ClanName");
        e.strength = tag.getFloat("Strength");
        e.legendary = tag.getBoolean("Legendary");
        try {
            e.kind = Legendary.Kind.valueOf(tag.getString("Kind"));
        } catch (IllegalArgumentException ignored) {
            e.kind = Legendary.Kind.SHADOW;
        }
        e.extraNature = tag.contains("Nature") ? nature(tag.getString("Nature")) : null;
        e.tails = tag.getInt("Tails");
        e.mangekyo = tag.getBoolean("Mangekyo");
        e.seed = tag.getLong("Seed");
        e.area = NbtUtils.readBlockPos(tag.getCompound("Area"));
        e.bounty = tag.getInt("Bounty");
        try {
            e.status = Status.valueOf(tag.getString("Status"));
        } catch (IllegalArgumentException ignored) {
            e.status = Status.ACTIVE;
        }
        e.claimedBy = tag.getString("ClaimedBy");
        e.claimedAt = tag.getLong("ClaimedAt");
        e.look = tag.getString("Look");
        e.entity = tag.hasUUID("Entity") ? tag.getUUID("Entity") : null;
        for (Tag t : tag.getList("Met", Tag.TAG_INT_ARRAY)) {
            e.met.add(NbtUtils.loadUUID(t));
        }
        e.added = tag.getLong("Added");
        return e;
    }
}
