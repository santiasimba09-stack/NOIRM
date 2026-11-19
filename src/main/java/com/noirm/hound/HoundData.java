package com.noirm.hound;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Your ninja hound, saved with you ({@code ShinobiData}): whether you have one, its level, bond and
 * XP, its look and traits, its name, and which entity it is right now. The hound in the world keeps
 * this up to date while it lives; if the entity is lost (another dimension, an unloaded chunk, the
 * void) {@link Hounds} brings it back next to you from this record. So the hound goes wherever you
 * go, and nothing it learned is ever lost.
 */
public final class HoundData {
    private boolean has;
    /** The live hound's entity id (a new one each time it is brought back). */
    private UUID entityId;
    private String name = "";
    private int look = HoundLook.DEFAULT.pack();
    private int level = 1;
    private float bond;
    private float xp;
    private int order;
    private float healthShare = 1f;
    private List<String> traits = List.of();
    /** Game time before which it is still recovering after it was lost. */
    private long downUntil;
    /** Out in the world (true) or dismissed into the summoning contract (false). */
    private boolean out = true;
    /** Came by a summoning contract (the Hatake's way). */
    private boolean contract;

    public boolean has() {
        return has;
    }

    public void setHas(boolean has) {
        this.has = has;
    }

    public UUID entityId() {
        return entityId;
    }

    public void setEntityId(UUID id) {
        entityId = id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public HoundLook look() {
        return HoundLook.unpack(look);
    }

    public int lookBits() {
        return look;
    }

    public void setLook(HoundLook look) {
        this.look = look.pack();
    }

    public int level() {
        return Math.max(1, level);
    }

    public void setLevel(int level) {
        this.level = Math.max(1, level);
    }

    public HoundStage stage() {
        return HoundStage.forLevel(level());
    }

    public float bond() {
        return bond;
    }

    public void setBond(float bond) {
        this.bond = bond;
    }

    public float xp() {
        return xp;
    }

    public void setXp(float xp) {
        this.xp = xp;
    }

    public HoundOrder order() {
        return HoundOrder.of(order);
    }

    public void setOrder(HoundOrder order) {
        this.order = order.ordinal();
    }

    public float healthShare() {
        return healthShare;
    }

    public void setHealthShare(float share) {
        healthShare = share;
    }

    public List<String> traits() {
        return traits;
    }

    public void setTraits(List<String> traits) {
        this.traits = List.copyOf(traits);
    }

    public long downUntil() {
        return downUntil;
    }

    public void setDownUntil(long time) {
        downUntil = time;
    }

    public boolean isOut() {
        return out;
    }

    public void setOut(boolean out) {
        this.out = out;
    }

    public boolean byContract() {
        return contract;
    }

    public void setContract(boolean contract) {
        this.contract = contract;
    }

    /** Forget the hound entirely. */
    public void clear() {
        load(new CompoundTag());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Has", has);
        if (entityId != null) {
            tag.putUUID("Entity", entityId);
        }
        tag.putString("Name", name);
        tag.putInt("Look", look);
        tag.putInt("Level", level);
        tag.putFloat("Bond", bond);
        tag.putFloat("Xp", xp);
        tag.putInt("Order", order);
        tag.putFloat("Health", healthShare);
        ListTag list = new ListTag();
        traits.forEach(t -> list.add(StringTag.valueOf(t)));
        tag.put("Traits", list);
        tag.putLong("DownUntil", downUntil);
        tag.putBoolean("Out", out);
        tag.putBoolean("Contract", contract);
        return tag;
    }

    public void load(CompoundTag tag) {
        has = tag.getBoolean("Has");
        entityId = tag.hasUUID("Entity") ? tag.getUUID("Entity") : null;
        name = tag.getString("Name");
        look = tag.contains("Look") ? tag.getInt("Look") : HoundLook.DEFAULT.pack();
        level = tag.contains("Level") ? Math.max(1, tag.getInt("Level"))
                : HoundStage.of(tag.getInt("Stage")).minLevel; // a save from before levels
        bond = tag.getFloat("Bond");
        xp = tag.getFloat("Xp");
        order = tag.getInt("Order");
        healthShare = tag.contains("Health") ? tag.getFloat("Health") : 1f;
        List<String> read = new ArrayList<>();
        tag.getList("Traits", Tag.TAG_STRING).forEach(t -> read.add(t.getAsString()));
        traits = List.copyOf(read);
        downUntil = tag.getLong("DownUntil");
        out = !tag.contains("Out") || tag.getBoolean("Out");
        contract = tag.getBoolean("Contract");
    }
}
