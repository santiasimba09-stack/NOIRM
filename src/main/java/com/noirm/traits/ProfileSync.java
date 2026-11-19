package com.noirm.traits;

import com.noirm.player.ShinobiData;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What the client needs to show a character's blood and talents (part of {@code SyncPowers}, and
 * each creator roll): the clan's perks, its clan jutsu, the talents with their modifiers (the
 * client can't read data files), and the sensing radius for the radar.
 */
public record ProfileSync(String clanId, Map<Perk, Float> clanPerks, List<String> clanJutsu, List<TalentView> talents, float sensor) {
    public static final ProfileSync EMPTY = new ProfileSync("", Map.of(), List.of(), List.of(), 0f);

    /** A talent as the client shows it. */
    public record TalentView(String id, Talent.Kind kind, Map<Perk, Float> modifiers) {
        public static TalentView of(String id) {
            Talent t = Talents.get(id);
            return new TalentView(id, t == null ? Talent.Kind.POSITIVE : t.kind(), t == null ? Map.of() : perks(t.modifiers()));
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(id, 64);
            buf.writeEnum(kind);
            writePerks(buf, modifiers);
        }

        static TalentView decode(FriendlyByteBuf buf) {
            return new TalentView(buf.readUtf(64), buf.readEnum(Talent.Kind.class), readPerks(buf));
        }
    }

    public static ProfileSync of(ShinobiData data) {
        List<TalentView> talents = data.talents().talents().stream().map(TalentView::of).toList();
        return new ProfileSync(data.identity().clanId(), perks(data.identity().perks()),
                data.identity().clanJutsu().stream().map(Object::toString).toList(), talents, Perks.get(data, Perk.SENSOR));
    }

    /** JSON-style perk keys → perks (unknown keys dropped). */
    public static Map<Perk, Float> perks(Map<String, Float> raw) {
        EnumMap<Perk, Float> out = new EnumMap<>(Perk.class);
        raw.forEach((k, v) -> {
            Perk p = Perk.byKey(k);
            if (p != null && v != 0f) {
                out.merge(p, v, Float::sum);
            }
        });
        return out;
    }

    public static List<TalentView> views(List<String> ids) {
        return ids.stream().map(TalentView::of).toList();
    }

    public static void writePerks(FriendlyByteBuf buf, Map<Perk, Float> perks) {
        buf.writeVarInt(perks.size());
        perks.forEach((p, v) -> {
            buf.writeVarInt(p.ordinal());
            buf.writeFloat(v);
        });
    }

    public static Map<Perk, Float> readPerks(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 128);
        EnumMap<Perk, Float> out = new EnumMap<>(Perk.class);
        for (int i = 0; i < n; i++) {
            int ordinal = buf.readVarInt();
            float value = buf.readFloat();
            if (ordinal >= 0 && ordinal < Perk.values().length) {
                out.put(Perk.values()[ordinal], value);
            }
        }
        return out;
    }

    public static void writeTalents(FriendlyByteBuf buf, List<TalentView> talents) {
        buf.writeVarInt(talents.size());
        talents.forEach(t -> t.encode(buf));
    }

    public static List<TalentView> readTalents(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 16);
        List<TalentView> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(TalentView.decode(buf));
        }
        return out;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(clanId, 64);
        writePerks(buf, clanPerks);
        buf.writeCollection(clanJutsu, (b, s) -> b.writeUtf(s, 256));
        writeTalents(buf, talents);
        buf.writeFloat(sensor);
    }

    public static ProfileSync decode(FriendlyByteBuf buf) {
        return new ProfileSync(buf.readUtf(64), readPerks(buf), buf.readList(b -> b.readUtf(256)), readTalents(buf), buf.readFloat());
    }
}
