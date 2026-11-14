package com.noirm.workshop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The creations a player knows (saved in {@code ShinobiData}): the ones they made and the ones they
 * learned from a scroll. Each is kept as its design; the jutsu itself is in their jutsu book.
 */
public final class CreationBook {
    private final Map<ResourceLocation, JutsuDesign> designs = new LinkedHashMap<>();

    public void add(JutsuDesign design) {
        designs.put(design.jutsuId(), design);
        WorkshopRegistry.register(design);
    }

    public void remove(ResourceLocation id) {
        designs.remove(id);
    }

    public JutsuDesign get(ResourceLocation id) {
        return id == null ? null : designs.get(id);
    }

    public Collection<JutsuDesign> all() {
        return designs.values();
    }

    public List<JutsuDesign> list() {
        return new ArrayList<>(designs.values());
    }

    /** How many of these this player made themselves. */
    public int ownCount(UUID player) {
        int n = 0;
        for (JutsuDesign d : designs.values()) {
            if (player != null && player.equals(d.author())) {
                n++;
            }
        }
        return n;
    }

    public void clear() {
        designs.clear();
    }

    public ListTag save() {
        ListTag list = new ListTag();
        designs.values().forEach(d -> list.add(d.save()));
        return list;
    }

    public void load(ListTag list) {
        designs.clear();
        for (Tag t : list) {
            if (t instanceof CompoundTag c) {
                JutsuDesign d = JutsuDesign.load(c);
                if (d != null) {
                    add(d);
                }
            }
        }
    }
}
