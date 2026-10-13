package com.noirm.player;

import com.noirm.awakening.AwakeningData;
import com.noirm.biju.BijuData;
import com.noirm.clan.ClanIdentity;
import com.noirm.clan.ClanTrait;
import com.noirm.dojutsu.EyeData;
import com.noirm.jutsu.JutsuBook;
import com.noirm.training.TrainingData;
import com.noirm.training.TrainingSystem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * Everything NOIRM saves about a player: resources (chakra for jutsu, stamina for defensive
 * actions), trained stats (speed, taijutsu, vitality, reserves), and their powers: clan identity, jutsu, eyes,
 * awakening and tailed beast.
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
    /** Starting value of the combat stats. Clans and (later) training raise them. */
    public static final float DEFAULT_STAT = 10f;
    public static final float MAX_STAT = 100f;

    private float maxChakra = DEFAULT_MAX_CHAKRA;
    private float chakra = DEFAULT_MAX_CHAKRA;
    private float maxStamina = DEFAULT_MAX_STAMINA;
    private float stamina = DEFAULT_MAX_STAMINA;
    private int staminaRegenDelay;
    /** How fast you are: feeds perception (how slow opponents' attacks look to you). */
    private float speed = DEFAULT_STAT;
    /** Hand-to-hand skill: feeds perception and unarmed damage. */
    private float taijutsu = DEFAULT_STAT;
    /** The fighting style in use ({@code data/noirm/combat/styles/}); empty = the basic one. */
    private String combatStyle = "";
    /** Toughness: raises max health. */
    private float vitality = DEFAULT_STAT;
    /** Chakra reserves: raise max chakra and its regen. */
    private float reserves = DEFAULT_STAT;
    /** True when the client's copy is out of date and needs a sync packet. */
    private boolean dirty = true;
    /** Same, for the powers (clan, jutsu, eyes, awakening, biju) — synced separately, less often. */
    private boolean powersDirty = true;

    private final ClanIdentity identity = new ClanIdentity();
    private final JutsuBook jutsu = new JutsuBook();
    private final EyeData eyes = new EyeData();
    private final AwakeningData awakening = new AwakeningData();
    private final BijuData biju = new BijuData();
    private final TrainingData training = new TrainingData();

    public ClanIdentity identity() {
        return identity;
    }

    public JutsuBook jutsu() {
        return jutsu;
    }

    public EyeData eyes() {
        return eyes;
    }

    public AwakeningData awakening() {
        return awakening;
    }

    public BijuData biju() {
        return biju;
    }

    public TrainingData training() {
        return training;
    }

    public void markPowersDirty() {
        powersDirty = true;
    }

    public boolean consumePowersDirty() {
        boolean wasDirty = powersDirty;
        powersDirty = false;
        return wasDirty;
    }

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

    public float getSpeed() {
        return speed;
    }

    public float getTaijutsu() {
        return taijutsu;
    }

    public String getCombatStyle() {
        return combatStyle;
    }

    public void setCombatStyle(String style) {
        combatStyle = style == null ? "" : style;
    }

    public float getVitality() {
        return vitality;
    }

    public float getReserves() {
        return reserves;
    }

    public void setVitality(float value) {
        vitality = Mth.clamp(value, 1f, MAX_STAT);
        dirty = true;
    }

    public void setReserves(float value) {
        reserves = Mth.clamp(value, 1f, MAX_STAT);
        dirty = true;
    }

    public void setSpeed(float value) {
        speed = Mth.clamp(value, 1f, MAX_STAT);
        dirty = true;
    }

    public void setTaijutsu(float value) {
        taijutsu = Mth.clamp(value, 1f, MAX_STAT);
        dirty = true;
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
        training.addChakraSpent(amount);
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
        setChakra(chakra + CHAKRA_REGEN_PER_TICK * chakraRegenMultiplier());
        if (staminaRegenDelay > 0) {
            staminaRegenDelay--;
        } else {
            setStamina(stamina + STAMINA_REGEN_PER_TICK * staminaRegenMultiplier());
        }
        jutsu.tickCooldowns();
    }

    /** Reserves add 2% per point above 10; exhaustion halves recovery; a tailed beast's cloak triples it. */
    public float chakraRegenMultiplier() {
        float multiplier = Math.max(0.5f, 1f + (reserves - DEFAULT_STAT) * TrainingSystem.REGEN_PER_RESERVES);
        if (awakening.isExhausted()) {
            multiplier *= 0.5f;
        }
        return biju.cloakStage() > 0 ? multiplier * 3f : multiplier;
    }

    /** Quick Recovery (+30%), being locked in (×2) and the cloak (×1.5) speed up stamina. */
    public float staminaRegenMultiplier() {
        float multiplier = identity.has(ClanTrait.QUICK_RECOVERY) ? 1.3f : 1f;
        if (awakening.isLockedIn()) {
            multiplier *= 2f;
        }
        return biju.cloakStage() > 0 ? multiplier * 1.5f : multiplier;
    }

    public void refillStamina() {
        setStamina(maxStamina);
        staminaRegenDelay = 0;
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
        tag.putFloat("Speed", speed);
        tag.putFloat("Taijutsu", taijutsu);
        tag.putFloat("Vitality", vitality);
        tag.putFloat("Reserves", reserves);
        tag.putString("Style", combatStyle);
        tag.put("Identity", identity.save());
        tag.put("Jutsu", jutsu.save());
        tag.put("Eyes", eyes.save());
        tag.put("Awakening", awakening.save());
        tag.put("Biju", biju.save());
        tag.put("Training", training.save());
        return tag;
    }

    public void load(CompoundTag tag) {
        maxChakra = tag.contains("MaxChakra") ? Math.max(1f, tag.getFloat("MaxChakra")) : DEFAULT_MAX_CHAKRA;
        maxStamina = tag.contains("MaxStamina") ? Math.max(1f, tag.getFloat("MaxStamina")) : DEFAULT_MAX_STAMINA;
        chakra = Mth.clamp(tag.contains("Chakra") ? tag.getFloat("Chakra") : maxChakra, 0f, maxChakra);
        stamina = Mth.clamp(tag.contains("Stamina") ? tag.getFloat("Stamina") : maxStamina, 0f, maxStamina);
        speed = Mth.clamp(tag.contains("Speed") ? tag.getFloat("Speed") : DEFAULT_STAT, 1f, MAX_STAT);
        taijutsu = Mth.clamp(tag.contains("Taijutsu") ? tag.getFloat("Taijutsu") : DEFAULT_STAT, 1f, MAX_STAT);
        vitality = Mth.clamp(tag.contains("Vitality") ? tag.getFloat("Vitality") : DEFAULT_STAT, 1f, MAX_STAT);
        reserves = Mth.clamp(tag.contains("Reserves") ? tag.getFloat("Reserves") : DEFAULT_STAT, 1f, MAX_STAT);
        combatStyle = tag.getString("Style");
        identity.load(tag.getCompound("Identity"));
        jutsu.load(tag.getCompound("Jutsu"));
        eyes.load(tag.getCompound("Eyes"));
        awakening.load(tag.getCompound("Awakening"));
        biju.load(tag.getCompound("Biju"));
        training.load(tag.getCompound("Training"));
        staminaRegenDelay = 0;
        dirty = true;
        powersDirty = true;
    }
}
