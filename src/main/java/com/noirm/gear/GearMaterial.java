package com.noirm.gear;

import com.noirm.Noirm;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.Supplier;

/**
 * How tough each kind of ninja gear is. Armour points per piece are listed as
 * {boots, leggings, chest, helmet}; for comparison vanilla leather is {1, 2, 3, 1} and iron
 * {2, 5, 6, 2}. Which picture a piece shows is the item's own business ({@link GearItem}).
 */
public enum GearMaterial implements ArmorMaterial {
    /** Forehead protectors: a steel plate on a cloth band. */
    FOREHEAD_PROTECTOR("forehead_protector", 12, new int[]{1, 2, 3, 2}, 12, SoundEvents.ARMOR_EQUIP_IRON, 0f, 0f,
            () -> Ingredient.of(Items.IRON_INGOT)),
    /** Everyday cloth: tunics, robes, coats, pants, sandals. Light. */
    CLOTH("cloth", 9, new int[]{1, 2, 3, 1}, 15, SoundEvents.ARMOR_EQUIP_LEATHER, 0f, 0f,
            () -> Ingredient.of(ItemTags.WOOL)),
    /** Clan cloth with padding (Uchiha tunic and pants): a little sturdier. */
    PADDED("padded", 12, new int[]{1, 3, 4, 1}, 15, SoundEvents.ARMOR_EQUIP_LEATHER, 0f, 0f,
            () -> Ingredient.of(ItemTags.WOOL)),
    /** The green flak vest. */
    FLAK("flak", 15, new int[]{1, 3, 5, 1}, 12, SoundEvents.ARMOR_EQUIP_LEATHER, 0.5f, 0f,
            () -> Ingredient.of(Items.LEATHER)),
    /** ANBU-style plated vest and porcelain mask. */
    ANBU("anbu", 16, new int[]{2, 4, 5, 2}, 12, SoundEvents.ARMOR_EQUIP_IRON, 0.5f, 0f,
            () -> Ingredient.of(Items.IRON_INGOT)),
    /** Senju battle armour: red lacquered plates. Heavy, a bit better than iron. */
    SENJU("senju", 18, new int[]{2, 6, 7, 2}, 10, SoundEvents.ARMOR_EQUIP_IRON, 1f, 0.05f,
            () -> Ingredient.of(Items.IRON_INGOT));

    /** Vanilla's per-piece durability base, {boots, leggings, chest, helmet}. */
    private static final int[] DURABILITY = {13, 15, 16, 11};

    private final String name;
    private final int durability;
    private final int[] defense;
    private final int enchantability;
    private final SoundEvent sound;
    private final float toughness;
    private final float knockbackResistance;
    private final Supplier<Ingredient> repair;

    GearMaterial(String name, int durability, int[] defense, int enchantability, SoundEvent sound, float toughness,
                 float knockbackResistance, Supplier<Ingredient> repair) {
        this.name = name;
        this.durability = durability;
        this.defense = defense;
        this.enchantability = enchantability;
        this.sound = sound;
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
        this.repair = repair;
    }

    private static int index(ArmorItem.Type type) {
        return switch (type) {
            case BOOTS -> 0;
            case LEGGINGS -> 1;
            case CHESTPLATE -> 2;
            case HELMET -> 3;
        };
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return DURABILITY[index(type)] * durability;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return defense[index(type)];
    }

    @Override
    public int getEnchantmentValue() {
        return enchantability;
    }

    @Override
    public SoundEvent getEquipSound() {
        return sound;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return repair.get();
    }

    @Override
    public String getName() {
        return Noirm.MOD_ID + ":" + name;
    }

    @Override
    public float getToughness() {
        return toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return knockbackResistance;
    }
}
