package com.noirm.player;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * A player's shinobi resources: chakra for jutsu, stamina for movement and combat actions.
 *
 * <p>The server owns this data; the client only receives copies for the HUD. All values are
 * floats so regeneration can add fractions per tick (20 ticks = 1 second).
 */
public final class ShinobiData {
    public static final float DEFAULT_MAX_CHAKRA = 100f;
    public static final float DEFAULT_MAX_STAMINA = 100f;
    /** Chakra recovered per tick: 1 point per second. */
    public static final float CHAKRA_REGEN_PER_TICK = 0.05f;
    /** Stamina recovered per tick once recovery has started: 20 points per second. */
    public static final float STAMINA_REGEN_PER_TICK = 1f;
    /** After spending stamina, it waits this many ticks (1 second) before recovering. */
    public static final int STAMINA_REGEN_DELAY_TICKS = 20;

    private float maxChakra = DEFAULT_MAX_CHAKRA;
    private float chakra = DEFAULT_MAX_CHAKRA;
    private float maxStamina = DEFAULT_MAX_STAMINA;
    private float stamina = DEFAULT_MAX_STAMINA;
    private int staminaRegenDelay;
    /** True when the client's copy is out of date and needs a sync packet. */
    private boolean dirty = true;

    public float getChakra() {
        return chakra;
    }

    public float getMaxChakra() {
        return maxChakra;
    }

    public float getStamina() {
        return stamina;
    }

    public float getMaxStamina() {
        return maxStamina;
    }

    public void setChakra(float value) {
        float clamped = Mth.clamp(value, 0f, maxChakra);
        if (clamped != chakra) {
            chakra = clamped;
            dirty = true;
        }
    }

    public void setStamina(float value) {
        float clamped = Mth.clamp(value, 0f, maxStamina);
        if (clamped != stamina) {
            stamina = clamped;
            dirty = true;
        }
    }

    /** Sets the maximum (at least 1) and lowers the current value if it no longer fits. */
    public void setMaxChakra(float value) {
        maxChakra = Math.max(1f, value);
        dirty = true;
        setChakra(chakra);
    }

    public void setMaxStamina(float value) {
        maxStamina = Math.max(1f, value);
        dirty = true;
        setStamina(stamina);
    }

    /** Spends chakra if there is enough. Returns false, and spends nothing, otherwise. */
    public boolean tryConsumeChakra(float amount) {
        if (amount < 0f || chakra < amount) {
            return false;
        }
        setChakra(chakra - amount);
        return true;
    }

    /** Spends stamina if there is enough, and pauses stamina recovery briefly. */
    public boolean tryConsumeStamina(float amount) {
        if (amount < 0f || stamina < amount) {
            return false;
        }
        setStamina(stamina - amount);
        staminaRegenDelay = STAMINA_REGEN_DELAY_TICKS;
        return true;
    }

    /** Called once per server tick: regenerates chakra and (after its delay) stamina. */
    public void tick() {
        setChakra(chakra + CHAKRA_REGEN_PER_TICK);
        if (staminaRegenDelay > 0) {
            staminaRegenDelay--;
        } else {
            setStamina(stamina + STAMINA_REGEN_PER_TICK);
        }
    }

    public void refill() {
        setChakra(maxChakra);
        setStamina(maxStamina);
        staminaRegenDelay = 0;
    }

    public void copyFrom(ShinobiData other) {
        load(other.save());
    }

    public void markDirty() {
        dirty = true;
    }

    /** Returns whether a sync is needed, and clears the flag. */
    public boolean consumeDirty() {
        boolean wasDirty = dirty;
        dirty = false;
        return wasDirty;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("MaxChakra", maxChakra);
        tag.putFloat("Chakra", chakra);
        tag.putFloat("MaxStamina", maxStamina);
        tag.putFloat("Stamina", stamina);
        return tag;
    }

    public void load(CompoundTag tag) {
        maxChakra = tag.contains("MaxChakra") ? Math.max(1f, tag.getFloat("MaxChakra")) : DEFAULT_MAX_CHAKRA;
        maxStamina = tag.contains("MaxStamina") ? Math.max(1f, tag.getFloat("MaxStamina")) : DEFAULT_MAX_STAMINA;
        chakra = Mth.clamp(tag.contains("Chakra") ? tag.getFloat("Chakra") : maxChakra, 0f, maxChakra);
        stamina = Mth.clamp(tag.contains("Stamina") ? tag.getFloat("Stamina") : maxStamina, 0f, maxStamina);
        staminaRegenDelay = 0;
        dirty = true;
    }
}
