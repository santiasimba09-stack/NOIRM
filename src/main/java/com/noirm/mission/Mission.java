package com.noirm.mission;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The mission a player is on: which template, where it was taken, the marked places, who was
 * spawned for it and how far along it is. Saved with the player ({@link CareerData}).
 */
public final class Mission {
    public enum Stage {
        /** Doing the objective. */
        GO,
        /** Objective done: report back to a mission board. */
        RETURN
    }

    /** Stamped on items made for a mission (the parcel, the scroll) so they're recognised. */
    public static final String ITEM_TAG = "NoirmMission";

    public final ResourceLocation template;
    public final UUID id;
    public final BlockPos board;
    public final String dimension;
    public Stage stage = Stage.GO;
    /** The marked places: the hunting area, the destination, the scouting points, the camp. */
    public final List<BlockPos> points = new ArrayList<>();
    /** Scouting: the next point to visit. */
    public int index;
    /** Enemies spawned for the mission and still standing. */
    public final List<UUID> foes = new ArrayList<>();
    /** Enemies beaten so far, and how many must be beaten (hunt, defend). */
    public int defeated;
    public int required;
    /** Whether the hunted group / the recipient / the camp has been put in the world. */
    public boolean spawned;
    /** The escorted client or the parcel's recipient. */
    public UUID npc;
    /** Game time the objective must be done by (0 = no limit). */
    public long deadline;
    /** Defend: waves sent so far, and when the next one comes (0 = when this one is beaten). */
    public int wave;
    public long nextAt;
    /** Whether the ambush has sprung. */
    public boolean ambushed;

    public Mission(ResourceLocation template, UUID id, BlockPos board, String dimension) {
        this.template = template;
        this.id = id;
        this.board = board;
        this.dimension = dimension;
    }

    public MissionTemplate definition() {
        return MissionTemplates.get(template);
    }

    /** Where the HUD's arrow points: the next marked place, or the board to report back to. */
    public BlockPos target() {
        if (stage == Stage.RETURN || points.isEmpty()) {
            return board;
        }
        MissionTemplate def = definition();
        if (def != null && def.kind() == MissionKind.SCOUT) {
            return points.get(Math.min(index, points.size() - 1));
        }
        if (def != null && def.kind() == MissionKind.DEFEND) {
            return board;
        }
        return points.get(0);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Template", template.toString());
        tag.putUUID("Id", id);
        tag.putLong("Board", board.asLong());
        tag.putString("Dimension", dimension);
        tag.putString("Stage", stage.name());
        ListTag pointList = new ListTag();
        points.forEach(p -> pointList.add(LongTag.valueOf(p.asLong())));
        tag.put("Points", pointList);
        tag.putInt("Index", index);
        ListTag foeList = new ListTag();
        foes.forEach(f -> foeList.add(NbtUtils.createUUID(f)));
        tag.put("Foes", foeList);
        tag.putInt("Defeated", defeated);
        tag.putInt("Required", required);
        tag.putBoolean("Spawned", spawned);
        if (npc != null) {
            tag.putUUID("Npc", npc);
        }
        tag.putLong("Deadline", deadline);
        tag.putInt("Wave", wave);
        tag.putLong("NextAt", nextAt);
        tag.putBoolean("Ambushed", ambushed);
        return tag;
    }

    /** Reads a saved mission, or null if it's broken. */
    public static Mission load(CompoundTag tag) {
        ResourceLocation template = ResourceLocation.tryParse(tag.getString("Template"));
        if (template == null || !tag.hasUUID("Id")) {
            return null;
        }
        Mission m = new Mission(template, tag.getUUID("Id"), BlockPos.of(tag.getLong("Board")), tag.getString("Dimension"));
        try {
            m.stage = Stage.valueOf(tag.getString("Stage"));
        } catch (IllegalArgumentException e) {
            m.stage = Stage.GO;
        }
        tag.getList("Points", Tag.TAG_LONG).forEach(t -> m.points.add(BlockPos.of(((LongTag) t).getAsLong())));
        m.index = tag.getInt("Index");
        tag.getList("Foes", Tag.TAG_INT_ARRAY).forEach(t -> m.foes.add(NbtUtils.loadUUID(t)));
        m.defeated = tag.getInt("Defeated");
        m.required = tag.getInt("Required");
        m.spawned = tag.getBoolean("Spawned");
        m.npc = tag.hasUUID("Npc") ? tag.getUUID("Npc") : null;
        m.deadline = tag.getLong("Deadline");
        m.wave = tag.getInt("Wave");
        m.nextAt = tag.getLong("NextAt");
        m.ambushed = tag.getBoolean("Ambushed");
        return m;
    }
}
