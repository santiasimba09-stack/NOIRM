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
    /** What the blood gives ({@code com.noirm.traits.Perk} key → amount): a copy of the clan's perks. */
    private final java.util.Map<String, Float> perks = new java.util.TreeMap<>();
    /** The clan's secret techniques (only members can use them). */
    private final java.util.List<net.minecraft.resources.ResourceLocation> clanJutsu = new java.util.ArrayList<>();
    /** Bumped whenever the clan or its perks change (cached perk totals are rebuilt). */
    private int version;

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

    public boolean hasByakugan() {
        return "byakugan".equals(kekkeiGenkai);
    }

    public AwakeningPath awakening() {
        return awakening;
    }

    public String kekkeiGenkai() {
        return kekkeiGenkai;
    }

    public java.util.Map<String, Float> perks() {
        return java.util.Collections.unmodifiableMap(perks);
    }

    public java.util.List<net.minecraft.resources.ResourceLocation> clanJutsu() {
        return java.util.Collections.unmodifiableList(clanJutsu);
    }

    public int version() {
        return version;
    }

    /** Takes the clan's current perks and clan jutsu (old saves, a clan file that changed). */
    public void refreshPerks(Clan clan) {
        perks.clear();
        perks.putAll(clan.perks());
        clanJutsu.clear();
        clanJutsu.addAll(clan.clanJutsu());
        version++;
    }

    /** Sets one perk directly (commands, tests); 0 removes it. */
    public void setPerk(String key, float amount) {
        if (amount == 0f) {
            perks.remove(key);
        } else {
            perks.put(key, amount);
        }
        version++;
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
        refreshPerks(clan);
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
        CompoundTag perkTag = new CompoundTag();
        perks.forEach(perkTag::putFloat);
        tag.put("Perks", perkTag);
        ListTag jutsuList = new ListTag();
        clanJutsu.forEach(id -> jutsuList.add(StringTag.valueOf(id.toString())));
        tag.put("ClanJutsu", jutsuList);
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
        perks.clear();
        CompoundTag perkTag = tag.getCompound("Perks");
        for (String key : perkTag.getAllKeys()) {
            perks.put(key, perkTag.getFloat(key));
        }
        clanJutsu.clear();
        for (Tag t : tag.getList("ClanJutsu", Tag.TAG_STRING)) {
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(t.getAsString());
            if (id != null) {
                clanJutsu.add(id);
            }
        }
        version++;
    }
}
