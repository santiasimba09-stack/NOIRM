package com.noirm.combat;

import com.noirm.Noirm;
import com.noirm.entity.SparringNinja;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Chakra durability (owner, 2026-11-12: "the armor should also get a boosted durability with the
 * chakra reinforcement, displayed by the side of the armour's normal durability").
 *
 * <p><b>The rule: a chakra pool on each worn piece.</b> Every armour piece you wear holds a pool of
 * "chakra durability" on top of its own. Wear from hits comes out of the pool first; only what the
 * pool can't take wears the item. While you wear it and have chakra, the pool refills a little every
 * second, paid in chakra. Why a pool and not just "less wear": a pool is a number you can see next to
 * the normal durability ("Durability 340/528 + 120 chakra"), it visibly drains in a hard fight and
 * fills back up afterwards, and when you run dry of chakra it stops refilling, which ties it to the
 * same chakra the rest of reinforcement uses.
 *
 * <p><b>Numbers</b> (constants below):
 * <ul>
 *   <li>pool size = {@link #POOL_SHARE} (50%) of the piece's max durability × reinforcement strength
 *       ({@link ChakraReinforcement#strength}: 0 at chakra stat 10, ~0.53 at 40, ~0.83 at 80). A diamond
 *       chestplate (528) holds +0 for a beginner, +140 at chakra stat 40, +218 at 80, up to +264;</li>
 *   <li>refill: {@link #REFILL_SHARE} (4%) of the pool a second (about 25 s from empty), costing
 *       {@link #REFILL_CHAKRA} chakra per point × the CTL cost factor (a full diamond chestplate pool
 *       at CTL 40 costs about 3.5 chakra);</li>
 *   <li>one point of pool takes one point of wear (Unbreaking then works on what's left).</li>
 * </ul>
 *
 * <p><b>Data:</b> server-authoritative, on the item's own NBT ({@link #TAG_POOL}, {@link #TAG_MAX}), so it
 * is saved with the item and synced to the client like its damage. A piece taken off keeps what its
 * pool held but doesn't refill; when the wearer's reinforcement would hold less (weaker stats, config
 * off), the pool shrinks to the new size.
 *
 * <p><b>NPC ninjas</b> fill the pools of the armour they wear from their own stats and chakra (vanilla
 * never wears a mob's armour down, so for them the pool only shows if the piece is dropped or given).
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class ChakraArmour {
    /** The pool holds this share of the piece's max durability at full reinforcement strength. */
    public static final float POOL_SHARE = 0.5f;
    /** The pool refills this share of its size each second while worn (at least 1 point). */
    public static final float REFILL_SHARE = 0.04f;
    /** Chakra per point of pool refilled (× the CTL cost factor). */
    public static final float REFILL_CHAKRA = 0.025f;
    /** How often the pools refill (ticks). */
    public static final int REFILL_TICKS = 20;

    public static final String TAG_POOL = "NoirmChakraDurability";
    public static final String TAG_MAX = "NoirmChakraDurabilityMax";

    private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Set while a worn piece's leftover wear is applied, so it isn't caught twice. */
    private static boolean applyingLeftover;

    private ChakraArmour() {}

    // ---------------------------------------------------------------- reading a stack (client and server)

    /** The chakra durability {@code stack} holds now (0 without a pool). */
    public static int pool(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(TAG_POOL);
    }

    /** The most its pool held when last filled (0 without a pool). */
    public static int poolMax(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(TAG_MAX);
    }

    /** The pool {@code wearer}'s reinforcement gives a piece like {@code stack}. */
    public static int poolSize(LivingEntity wearer, ItemStack stack) {
        if (!stack.isDamageableItem()) {
            return 0;
        }
        return Math.round(stack.getMaxDamage() * POOL_SHARE * ChakraReinforcement.strength(ChakraReinforcement.chakraStat(wearer)));
    }

    // ---------------------------------------------------------------- refilling

    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || (entity.tickCount + entity.getId()) % REFILL_TICKS != 0
                || !(entity instanceof Player || entity instanceof SparringNinja)) {
            return;
        }
        refill(entity);
    }

    /** One refill step for every piece {@code wearer} has on (public for tests). */
    public static void refill(LivingEntity wearer) {
        for (EquipmentSlot slot : ARMOUR) {
            ItemStack stack = wearer.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.isDamageableItem()) {
                continue;
            }
            int size = poolSize(wearer, stack);
            int pool = pool(stack);
            if (size <= 0) {
                if (pool > 0 || poolMax(stack) > 0) {
                    clear(stack); // no reinforcement any more: the chakra fades out of the piece
                }
                continue;
            }
            if (pool > size) {
                pool = size;
            }
            if (pool < size) {
                int step = Math.min(size - pool, Math.max(1, Math.round(size * REFILL_SHARE)));
                float cost = step * REFILL_CHAKRA * ChakraReinforcement.costFactor(ChakraReinforcement.control(wearer));
                if (payRefill(wearer, cost)) {
                    pool += step;
                }
            }
            write(stack, pool, size);
        }
    }

    private static boolean payRefill(LivingEntity wearer, float cost) {
        if (wearer instanceof Player player) {
            if (player.getAbilities().instabuild) {
                return true;
            }
            // Not tryConsumeChakra: refilling armour isn't jutsu practice and shouldn't train Reserves.
            return com.noirm.player.ShinobiCapability.get(player).map(d -> {
                if (d.getChakra() < cost) {
                    return false;
                }
                d.setChakra(d.getChakra() - cost);
                return true;
            }).orElse(false);
        }
        return wearer instanceof SparringNinja ninja && ninja.tryUseChakra(cost);
    }

    private static void write(ItemStack stack, int pool, int max) {
        if (pool(stack) == pool && poolMax(stack) == max) {
            return; // unchanged: don't touch the item (it would resend it)
        }
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(TAG_POOL, Math.max(0, pool));
        tag.putInt(TAG_MAX, Math.max(0, max));
    }

    private static void clear(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(TAG_POOL);
            tag.remove(TAG_MAX);
            if (tag.isEmpty()) {
                stack.setTag(null);
            }
        }
    }

    // ---------------------------------------------------------------- wear

    /**
     * {@code amount} points of wear about to hit {@code stack} (from {@code ItemStack.hurtAndBreak}, via
     * {@code mixin.ItemStackWearMixin}). If {@code stack} is armour {@code wearer} has on and its pool
     * holds chakra, the pool takes what it can; returns the wear left for the item itself.
     */
    public static int absorb(ItemStack stack, int amount, LivingEntity wearer) {
        if (applyingLeftover || amount <= 0 || wearer == null || wearer.level().isClientSide() || !worn(wearer, stack)
                || wearer instanceof Player player && player.getAbilities().instabuild) { // creative: nothing wears anyway
            return amount;
        }
        int pool = pool(stack);
        if (pool <= 0) {
            return amount;
        }
        int taken = Math.min(pool, amount);
        write(stack, pool - taken, poolMax(stack));
        return amount - taken;
    }

    /** Applies the wear the pool couldn't take, without catching it again. */
    public static <T extends LivingEntity> void applyLeftover(ItemStack stack, int amount, T wearer, java.util.function.Consumer<T> onBroken) {
        applyingLeftover = true;
        try {
            stack.hurtAndBreak(amount, wearer, onBroken);
        } finally {
            applyingLeftover = false;
        }
    }

    private static boolean worn(LivingEntity wearer, ItemStack stack) {
        for (EquipmentSlot slot : ARMOUR) {
            if (wearer.getItemBySlot(slot) == stack) {
                return true;
            }
        }
        return false;
    }

    /** The chakra durability over everything {@code entity} wears (the HUD's "+N"). */
    public static int totalPool(LivingEntity entity) {
        int total = 0;
        for (EquipmentSlot slot : ARMOUR) {
            total += pool(entity.getItemBySlot(slot));
        }
        return total;
    }
}
