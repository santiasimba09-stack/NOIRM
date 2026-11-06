package com.noirm.workshop;

import com.noirm.Noirm;
import com.noirm.arts.ChakraShape;
import com.noirm.clan.Nature;
import com.noirm.nature.NatureTraining;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.UUID;

/**
 * A jutsu someone made in the workshop: its name, nature, shape and the four trade-offs (each 1–5).
 * Everything else (rank, cost, seals, damage…) is worked out from these by {@link Workshop}, so a
 * scroll or a lesson only needs to carry the design.
 *
 * @param id         unique per creation (its jutsu id comes from it)
 * @param author     who made it, and their name (shown on scrolls)
 */
public record JutsuDesign(UUID id, String name, Nature nature, ChakraShape shape, int size, int speed, int power, int range,
                          UUID author, String authorName) {
    public static final int MIN = 1;
    public static final int MAX = 5;
    public static final int NAME_MAX = 32;
    /** Jutsu ids of creations live under this folder: {@code noirm:workshop/<id>}. */
    public static final String FOLDER = "workshop/";

    public JutsuDesign {
        size = clamp(size);
        speed = clamp(speed);
        power = clamp(power);
        range = clamp(range);
        name = cleanName(name);
        authorName = authorName == null ? "" : authorName;
    }

    private static int clamp(int v) {
        return Math.max(MIN, Math.min(MAX, v));
    }

    /** Letters, digits, spaces and a little punctuation; at most {@link #NAME_MAX} characters. */
    public static String cleanName(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        raw.codePoints().forEach(cp -> {
            if (Character.isLetterOrDigit(cp) || cp == ' ' || cp == '-' || cp == '\'' || cp == ':' || cp == '!' || cp == '.' || cp == ',') {
                out.appendCodePoint(cp);
            }
        });
        String s = out.toString().trim().replaceAll(" +", " ");
        return s.length() > NAME_MAX ? s.substring(0, NAME_MAX).trim() : s;
    }

    /** The jutsu id: {@code noirm:workshop/<first 12 hex digits of the id>}. */
    public ResourceLocation jutsuId() {
        return Noirm.id(FOLDER + id.toString().replace("-", "").substring(0, 12).toLowerCase(Locale.ROOT));
    }

    public static boolean isCreation(ResourceLocation jutsu) {
        return jutsu != null && Noirm.MOD_ID.equals(jutsu.getNamespace()) && jutsu.getPath().startsWith(FOLDER);
    }

    // ---------------------------------------------------------------- saving and sending

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putString("Nature", nature.getSerializedName());
        tag.putString("Shape", shape.key());
        tag.putByte("Size", (byte) size);
        tag.putByte("Speed", (byte) speed);
        tag.putByte("Power", (byte) power);
        tag.putByte("Range", (byte) range);
        if (author != null) {
            tag.putUUID("Author", author);
        }
        tag.putString("AuthorName", authorName);
        return tag;
    }

    /** Reads a design back, or null if it's broken (an unknown nature or shape). */
    public static JutsuDesign load(CompoundTag tag) {
        if (!tag.hasUUID("Id")) {
            return null;
        }
        Nature nature = NatureTraining.byName(tag.getString("Nature"));
        ChakraShape shape = ChakraShape.byKey(tag.getString("Shape"));
        if (nature == null || shape == null) {
            return null;
        }
        return new JutsuDesign(tag.getUUID("Id"), tag.getString("Name"), nature, shape, tag.getByte("Size"), tag.getByte("Speed"),
                tag.getByte("Power"), tag.getByte("Range"), tag.hasUUID("Author") ? tag.getUUID("Author") : null, tag.getString("AuthorName"));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(name, 64);
        buf.writeVarInt(nature.ordinal());
        buf.writeVarInt(shape.ordinal());
        buf.writeByte(size);
        buf.writeByte(speed);
        buf.writeByte(power);
        buf.writeByte(range);
        buf.writeBoolean(author != null);
        if (author != null) {
            buf.writeUUID(author);
        }
        buf.writeUtf(authorName, 64);
    }

    public static JutsuDesign decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String name = buf.readUtf(64);
        int n = buf.readVarInt();
        int s = buf.readVarInt();
        int size = buf.readByte();
        int speed = buf.readByte();
        int power = buf.readByte();
        int range = buf.readByte();
        UUID author = buf.readBoolean() ? buf.readUUID() : null;
        String authorName = buf.readUtf(64);
        Nature nature = Nature.values()[Math.max(0, Math.min(Nature.values().length - 1, n))];
        ChakraShape shape = ChakraShape.values()[Math.max(0, Math.min(ChakraShape.values().length - 1, s))];
        return new JutsuDesign(id, name, nature, shape, size, speed, power, range, author, authorName);
    }
}
