package com.noirm.medical;

import com.noirm.Noirm;
import com.noirm.master.MasterPerks;
import com.noirm.player.ShinobiCapability;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/** Hooks the medical ninja's teachings into the game: her test, the chakra scalpel, poison lore and antidotes. */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class MedicalEvents {
    /** Poisons Poison Lore weakens. */
    public static final Set<MobEffect> POISONS = Set.of(MobEffects.POISON, MobEffects.WITHER);
    /** Set while we re-apply a weakened poison (so it isn't weakened twice). */
    private static boolean applying;

    private MedicalEvents() {}

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MedicTrial.tickAll();
            ChakraScalpel.tickAll();
        }
    }

    /** Scalpel cuts add their damage to the strike. */
    @SubscribeEvent
    public static void hurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide() || !(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        float extra = ChakraScalpel.cut(attacker, event.getEntity(), event.getSource());
        if (extra > 0f) {
            event.setAmount(event.getAmount() + extra);
        }
    }

    /** Poison Lore: poisons last half as long and hit one level weaker. */
    @SubscribeEvent
    public static void effectApplicable(MobEffectEvent.Applicable event) {
        MobEffectInstance effect = event.getEffectInstance();
        LivingEntity entity = event.getEntity();
        if (applying || entity.level().isClientSide() || effect == null || !POISONS.contains(effect.getEffect())
                || !MasterPerks.has(entity, MasterPerks.POISON_LORE)) {
            return;
        }
        event.setResult(Event.Result.DENY);
        applying = true;
        try {
            entity.addEffect(weakened(effect));
        } finally {
            applying = false;
        }
    }

    /** A poison as Poison Lore lets it through: half as long, one level weaker. */
    public static MobEffectInstance weakened(MobEffectInstance effect) {
        return new MobEffectInstance(effect.getEffect(), Math.max(20, effect.getDuration() / 2), Math.max(0, effect.getAmplifier() - 1),
                effect.isAmbient(), effect.isVisible(), effect.showIcon());
    }

    /** Antidotes only work from a hand trained in Poison Lore: anyone else's come out botched. */
    @SubscribeEvent
    public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        onCrafted(event.getEntity(), event.getCrafting());
    }

    /** The crafting rule (public so tests can call it). */
    public static void onCrafted(Player player, ItemStack result) {
        if (player == null || !result.is(MedicalItems.ANTIDOTE.get())) {
            return;
        }
        boolean lore = ShinobiCapability.get(player).map(d -> MasterPerks.has(d, MasterPerks.POISON_LORE)).orElse(false);
        if (!lore) {
            AntidoteItem.botch(result);
        }
    }

    /** A patient without a test (left over from a reload) never comes back. */
    @SubscribeEvent
    public static void join(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity().getTags().contains(MedicTrial.PATIENT_TAG)
                && !MedicTrial.isActivePatient(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
