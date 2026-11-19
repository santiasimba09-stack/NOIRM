package com.noirm.hiraishin;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * One space-time formula mark (the Flying Raijin's anchor). Marks belong to whoever set them and
 * live in {@link HiraishinMarks}.
 *
 * @param id       unique per owner (the HUD and the cycle key use it)
 * @param kind     what carries it
 * @param dim      the dimension it's in
 * @param pos      where it is (a kunai or a body moves: kept up to date while it's loaded)
 * @param face     the block face it's written on (BLOCK marks)
 * @param entity   the kunai or the body carrying it (KUNAI, BODY, ALLY marks), or null
 * @param created  game time it was set
 * @param expires  game time it fades, or -1 for never (a kunai's mark lives as long as the kunai)
 */
public record HiraishinMark(int id, Kind kind, ResourceKey<Level> dim, Vec3 pos, Direction face, UUID entity, long created, long expires) {

    public enum Kind {
        /** On a thrown Flying Raijin kunai: in flight, stuck in a wall or lying on the ground. */
        KUNAI,
        /** Written on a block face (the mark key). */
        BLOCK,
        /** On an enemy you struck or touched, or hit with a marked kunai. */
        BODY,
        /** On an ally (their consent: you touched them on purpose). */
        ALLY;

        public boolean onEntity() {
            return this != BLOCK;
        }
    }

    public HiraishinMark withPos(Vec3 newPos) {
        return new HiraishinMark(id, kind, dim, newPos, face, entity, created, expires);
    }

    public HiraishinMark withExpires(long newExpires) {
        return new HiraishinMark(id, kind, dim, pos, face, entity, created, newExpires);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", id);
        tag.putString("Kind", kind.name());
        tag.putString("Dim", dim.location().toString());
        tag.putDouble("X", pos.x);
        tag.putDouble("Y", pos.y);
        tag.putDouble("Z", pos.z);
        tag.putString("Face", face.getSerializedName());
        if (entity != null) {
            tag.putUUID("Entity", entity);
        }
        tag.putLong("Created", created);
        tag.putLong("Expires", expires);
        return tag;
    }

    public static HiraishinMark load(CompoundTag tag) {
        Kind kind;
        try {
            kind = Kind.valueOf(tag.getString("Kind"));
        } catch (IllegalArgumentException e) {
            kind = Kind.BLOCK;
        }
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("Dim")));
        Direction face = Direction.byName(tag.getString("Face"));
        return new HiraishinMark(tag.getInt("Id"), kind, dim, new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")),
                face == null ? Direction.UP : face, tag.hasUUID("Entity") ? tag.getUUID("Entity") : null, tag.getLong("Created"), tag.getLong("Expires"));
    }
}
