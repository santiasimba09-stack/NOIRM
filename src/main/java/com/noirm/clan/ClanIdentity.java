package com.noirm.clan;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Who a player is: their clan and what it gave them. A copy of the clan's details is kept here so
 * it works even without the world's clan list (and survives the clan list changing).
 */
public final class ClanIdentity {
    private String clanId = "";
    private String clanName = "";
    private boolean canon;
    private final EnumMap<Nature, Float> natures = new EnumMap<>(Nature.class);
    private final EnumSet<ClanTrait> traits = EnumSet.noneOf(ClanTrait.class);
    private String kekkeiGenkai = "";
    private AwakeningPath awakening = AwakeningPath.WILL;

    public boolean isAssigned() {
        return !clanId.isEmpty();
    }

    public String clanId() {
        return clanId;
    }

    public String clanName() {
        return clanName;
    }

    public boolean isCanon() {
        return canon;
    }

    public Map<Nature, Float> natures() {
        return natures;
    }

    public Set<ClanTrait> traits() {
        return traits;
    }

    public boolean has(ClanTrait trait) {
        return traits.contains(trait);
    }

    public boolean hasSharingan() {
        return "sharingan".equals(kekkeiGenkai);
    }

    public AwakeningPath awakening() {
        return awakening;
    }

    /** 0 if the player doesn't have this nature at all. */
    public float affinity(Nature nature) {
        return natures.getOrDefault(nature, 0f);
    }

    /** Gives a nature (from training etc.), keeping whichever affinity is higher. */
    public void addNature(Nature nature, float affinity) {
        natures.merge(nature, affinity, Math::max);
    }

    public void setFrom(Clan clan) {
        clanId = clan.id();
        clanName = clan.name();
        canon = clan.canon();
        natures.clear();
        natures.putAll(clan.natures());
        traits.clear();
        traits.addAll(clan.traits());
        kekkeiGenkai = clan.kekkeiGenkai().orElse("");
        awakening = clan.awakening();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", clanId);
        tag.putString("Name", clanName);
        tag.putBoolean("Canon", canon);
        CompoundTag natureTag = new CompoundTag();
        natures.forEach((nature, affinity) -> natureTag.putFloat(nature.getSerializedName(), affinity));
        tag.put("Natures", natureTag);
        ListTag traitList = new ListTag();
        traits.forEach(t -> traitList.add(StringTag.valueOf(t.getSerializedName())));
        tag.put("Traits", traitList);
        tag.putString("KekkeiGenkai", kekkeiGenkai);
        tag.putString("Awakening", awakening.getSerializedName());
        return tag;
    }

    public void load(CompoundTag tag) {
        clanId = tag.getString("Id");
        clanName = tag.getString("Name");
        canon = tag.getBoolean("Canon");
        natures.clear();
        CompoundTag natureTag = tag.getCompound("Natures");
        for (Nature nature : Nature.values()) {
            if (natureTag.contains(nature.getSerializedName())) {
                natures.put(nature, natureTag.getFloat(nature.getSerializedName()));
            }
        }
        traits.clear();
        for (Tag t : tag.getList("Traits", Tag.TAG_STRING)) {
            for (ClanTrait trait : ClanTrait.values()) {
                if (trait.getSerializedName().equals(t.getAsString())) {
                    traits.add(trait);
                }
            }
        }
        kekkeiGenkai = tag.getString("KekkeiGenkai");
        awakening = AwakeningPath.WILL;
        for (AwakeningPath path : AwakeningPath.values()) {
            if (path.getSerializedName().equals(tag.getString("Awakening"))) {
                awakening = path;
            }
        }
    }
}
