package com.noirm.world.village;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * The villages built with {@code /noirm village} in a world (their centre, size and look), saved
 * with the world. World-generated villages are structures the game already knows about; these
 * are only blocks, so this list is how the mist, the haze and the spawn rules find them too.
 */
public final class BuiltVillages extends SavedData {
    private static final String NAME = "noirm_built_villages";

    /** One village: its centre, radius and look. */
    public record Entry(BlockPos centre, int radius, VillagePalette palette) {}

    private final List<Entry> entries = new ArrayList<>();

    public static BuiltVillages of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(BuiltVillages::load, BuiltVillages::new, NAME);
    }

    public void add(BlockPos centre, VillageKind kind, VillagePalette palette) {
        entries.add(new Entry(centre.immutable(), kind.radius, palette));
        setDirty();
    }

    /** The village whose ground (within its radius, and up to 80 blocks above it) {@code pos} is in, or null. */
    public Entry at(BlockPos pos) {
        for (Entry entry : entries) {
            long dx = pos.getX() - entry.centre().getX(), dz = pos.getZ() - entry.centre().getZ();
            int dy = pos.getY() - entry.centre().getY();
            if (dx * dx + dz * dz <= (long) entry.radius() * entry.radius() && dy >= -8 && dy <= 80) {
                return entry;
            }
        }
        return null;
    }

    public static BuiltVillages load(CompoundTag tag) {
        BuiltVillages villages = new BuiltVillages();
        for (Tag t : tag.getList("Villages", Tag.TAG_COMPOUND)) {
            CompoundTag v = (CompoundTag) t;
            villages.entries.add(new Entry(new BlockPos(v.getInt("X"), v.getInt("Y"), v.getInt("Z")), v.getInt("Radius"),
                    VillagePalette.byOrdinal(v.getInt("Palette"))));
        }
        return villages;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag v = new CompoundTag();
            v.putInt("X", entry.centre().getX());
            v.putInt("Y", entry.centre().getY());
            v.putInt("Z", entry.centre().getZ());
            v.putInt("Radius", entry.radius());
            v.putInt("Palette", entry.palette().ordinal());
            list.add(v);
        }
        tag.put("Villages", list);
        return tag;
    }
}
