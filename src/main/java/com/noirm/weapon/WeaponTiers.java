package com.noirm.weapon;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

/** The metals of NOIRM's swords (for durability, enchantability and repairs). */
public enum WeaponTiers implements Tier {
    /** Ninjatō: tougher than an iron sword, a little less enchantable than gold. */
    NINJATO(350, 6f, 2f, 2, 16),
    /** Chakra-conducting metal (chakra blades): takes the wielder's nature. */
    CHAKRA_METAL(450, 6f, 2f, 2, 18);

    private final int uses;
    private final float speed;
    private final float damage;
    private final int level;
    private final int enchantment;

    WeaponTiers(int uses, float speed, float damage, int level, int enchantment) {
        this.uses = uses;
        this.speed = speed;
        this.damage = damage;
        this.level = level;
        this.enchantment = enchantment;
    }

    @Override
    public int getUses() {
        return uses;
    }

    @Override
    public float getSpeed() {
        return speed;
    }

    @Override
    public float getAttackDamageBonus() {
        return damage;
    }

    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public int getEnchantmentValue() {
        return enchantment;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return this == CHAKRA_METAL ? Ingredient.of(Items.IRON_INGOT, Items.LAPIS_LAZULI) : Ingredient.of(Items.IRON_INGOT);
    }
}
