package com.noirm.ally;

import com.noirm.Noirm;
import com.noirm.entity.SparringNinja;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Feeding crew mates. Right-click your crew mate with food in hand and they eat it: they heal by
 * the food's nutrition, take its effects exactly as a player would (a golden apple's regeneration
 * and absorption, a suspicious stew's effect…), and NOIRM's own food (ramen) refills their
 * chakra. A meal while hurt earns a little respect. A crew mate with food in their off hand eats
 * it by themselves when badly hurt (or, for ramen, when out of chakra).
 */
public final class AllyFood {
    /** Health per point of nutrition, times (1 + the food's saturation): bread ≈ 8, steak ≈ 14, golden apple ≈ 9. */
    public static final float HEAL_PER_NUTRITION = 1f;
    /** NOIRM food (ramen) gives this much chakra per point of nutrition. */
    public static final float CHAKRA_PER_NUTRITION = 5f;
    /** Ticks between meals when you feed them, and between meals they take themselves. */
    public static final int COOLDOWN_TICKS = 30;
    public static final int SELF_FEED_COOLDOWN_TICKS = 100;
    /** Respect for a meal while hurt, and for one when they're fine. */
    public static final float RESPECT_WHEN_HURT = 2f;
    public static final float RESPECT_OTHERWISE = 0.5f;
    /** They eat the food in their off hand by themselves below this share of their health… */
    public static final float SELF_FEED_HEALTH = 0.4f;
    /** …or, for food that gives chakra, below this share of their chakra. */
    public static final float SELF_FEED_CHAKRA = 0.25f;

    private static final Map<LivingEntity, Long> LAST_MEAL = new WeakHashMap<>();

    private AllyFood() {}

    /** Can {@code eater} eat {@code stack}? (Safe on the client.) */
    public static boolean isFood(ItemStack stack, LivingEntity eater) {
        return !stack.isEmpty() && stack.getFoodProperties(eater) != null;
    }

    /** NOIRM's own food (ramen) refills chakra. */
    public static boolean givesChakra(ItemStack stack) {
        var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && Noirm.MOD_ID.equals(key.getNamespace());
    }

    public static boolean readyToEat(LivingEntity eater, int cooldown) {
        Long last = LAST_MEAL.get(eater);
        return last == null || eater.level().getGameTime() - last >= cooldown;
    }

    /** {@code feeder} gives {@code ally} the food in their {@code hand} (one of it; free in creative). */
    public static void feed(SparringNinja ally, Player feeder, InteractionHand hand) {
        ItemStack held = feeder.getItemInHand(hand);
        if (!readyToEat(ally, COOLDOWN_TICKS)) {
            feeder.displayClientMessage(Component.translatable("message.noirm.ally.still_eating", ally.getDisplayName()), true);
            return;
        }
        boolean hurt = ally.getHealth() < ally.getMaxHealth();
        Component name = held.getHoverName();
        ItemStack leftover = eat(ally, held.copyWithCount(1));
        if (!feeder.getAbilities().instabuild) {
            held.shrink(1);
            if (!leftover.isEmpty()) { // the bowl comes back
                if (held.isEmpty()) {
                    feeder.setItemInHand(hand, leftover);
                } else if (!feeder.getInventory().add(leftover)) {
                    feeder.drop(leftover, false);
                }
            }
        }
        AllyMind.respect(ally, hurt ? RESPECT_WHEN_HURT : RESPECT_OTHERWISE, null);
        feeder.displayClientMessage(Component.translatable("message.noirm.ally.fed", ally.getDisplayName(), name), true);
    }

    /**
     * {@code eater} eats {@code food} (a single item): the food's own eating (sound, effects),
     * healing by its nutrition, and chakra for NOIRM food. Returns what's left over (a bowl…).
     */
    public static ItemStack eat(SparringNinja eater, ItemStack food) {
        FoodProperties properties = food.getFoodProperties(eater);
        ItemStack shown = food.copy();
        boolean chakra = givesChakra(food);
        LAST_MEAL.put(eater, eater.level().getGameTime());
        // Vanilla's own eating: the sound and the food's effects (golden apples, stews, honey…).
        ItemStack leftover = food.finishUsingItem(eater.level(), eater);
        if (properties != null) {
            eater.heal(properties.getNutrition() * (1f + properties.getSaturationModifier()) * HEAL_PER_NUTRITION);
            if (chakra) {
                eater.setChakra(eater.chakra() + properties.getNutrition() * CHAKRA_PER_NUTRITION);
            }
        }
        if (eater.level() instanceof ServerLevel level) {
            double y = eater.getEyeY() - 0.15;
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, shown), eater.getX(), y, eater.getZ(), 14, 0.2, 0.1, 0.2, 0.06);
            level.sendParticles(ParticleTypes.HEART, eater.getX(), eater.getY() + eater.getBbHeight() + 0.3, eater.getZ(), 2, 0.3, 0.1, 0.3, 0);
            if (chakra) {
                com.noirm.vfx.Vfx.aura(level, eater.position(), eater.getBbWidth(), eater.getBbHeight(), com.noirm.vfx.Vfx.palette(null), 12);
            }
            level.playSound(null, eater.getX(), eater.getY(), eater.getZ(), SoundEvents.PLAYER_BURP, eater.getSoundSource(), 0.5f,
                    0.9f + eater.getRandom().nextFloat() * 0.1f);
        }
        return leftover.isEmpty() || leftover == food ? ItemStack.EMPTY : leftover;
    }

    /** Every tick for a ninja (server): badly hurt (or out of chakra, for ramen) with food in their off hand, they eat it. */
    public static void tickSelfFeed(SparringNinja ninja) {
        if (ninja.tickCount % 10 != 0 || !ninja.isAlive() || ninja instanceof com.noirm.entity.ShadowClone) {
            return;
        }
        ItemStack food = ninja.getOffhandItem();
        if (!isFood(food, ninja) || !readyToEat(ninja, SELF_FEED_COOLDOWN_TICKS)) {
            return;
        }
        boolean hurt = ninja.getHealth() < ninja.getMaxHealth() * SELF_FEED_HEALTH;
        boolean drained = givesChakra(food) && ninja.chakra() < ninja.maxChakra() * SELF_FEED_CHAKRA;
        if (!hurt && !drained) {
            return;
        }
        ItemStack leftover = eat(ninja, food.split(1));
        if (!leftover.isEmpty()) {
            if (ninja.getOffhandItem().isEmpty()) {
                ninja.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, leftover);
            } else {
                ninja.spawnAtLocation(leftover);
            }
        }
    }
}
