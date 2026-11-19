package com.noirm.pills;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;

/**
 * A pill's quality, from the apothecary roll ({@link Apothecary}). Better pills are stronger,
 * cleaner (less toxicity) and crash softer. Saved on the stack as {@code Grade} (0–3); a pill
 * without one is {@link #STANDARD} (shop stock, loot).
 */
public enum Grade {
    //       potency  toxicity  crash
    CRUDE(0.65f, 1.30f, 1.25f, ChatFormatting.GRAY, 0xFF9A8F7E),
    STANDARD(1.00f, 1.00f, 1.00f, ChatFormatting.WHITE, 0xFFE8E2D4),
    REFINED(1.30f, 0.80f, 0.80f, ChatFormatting.AQUA, 0xFF7FC4F0),
    PERFECT(1.60f, 0.60f, 0.60f, ChatFormatting.GOLD, 0xFFF2C94C);

    public static final String TAG = "Grade";

    /** How strong its effects are (× the base numbers). */
    public final float potency;
    /** How much of its toxicity it adds (×). */
    public final float toxicity;
    /** How hard and long its crash is (×). */
    public final float crash;
    public final ChatFormatting format;
    /** ARGB, for screens and the HUD. */
    public final int color;

    Grade(float potency, float toxicity, float crash, ChatFormatting format, int color) {
        this.potency = potency;
        this.toxicity = toxicity;
        this.crash = crash;
        this.format = format;
        this.color = color;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** "pill.noirm.grade.refined". */
    public String translationKey() {
        return "pill.noirm.grade." + key();
    }

    public static Grade byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : STANDARD;
    }

    public static Grade byName(String name) {
        for (Grade g : values()) {
            if (g.key().equalsIgnoreCase(name)) {
                return g;
            }
        }
        return null;
    }

    public static Grade of(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().contains(TAG) ? byOrdinal(stack.getTag().getInt(TAG)) : STANDARD;
    }

    /** Marks a stack with this grade (standard is left unmarked, so shop pills stack with brewed ones). */
    public ItemStack apply(ItemStack stack) {
        if (this == STANDARD) {
            if (stack.getTag() != null) {
                stack.getTag().remove(TAG);
                if (stack.getTag().isEmpty()) {
                    stack.setTag(null);
                }
            }
        } else {
            stack.getOrCreateTag().putInt(TAG, ordinal());
        }
        return stack;
    }
}
