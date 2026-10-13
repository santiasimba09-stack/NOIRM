package com.noirm.client;

import com.noirm.Noirm;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Plays combat animations (from {@code assets/noirm/player_animation/}) on players, using the
 * PlayerAnimator library. Each animation is sped up or slowed down to match the server's move
 * timing, so the motion lines up with when the hit lands.
 */
public final class PlayerAnimations {
    private static final ResourceLocation LAYER = Noirm.id("combat");
    /** Under the attack layer: the style's stance, held while in combat mode. */
    private static final ResourceLocation STANCE_LAYER = Noirm.id("stance");
    private static String currentStance = "";
    private static final Set<ResourceLocation> WARNED_MISSING = new HashSet<>();

    private PlayerAnimations() {}

    static void play(AbstractClientPlayer player, ResourceLocation id, int durationTicks) {
        if (!(PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER) instanceof ModifierLayer<?> raw)) {
            return;
        }
        KeyframeAnimation animation = PlayerAnimationRegistry.getAnimation(id);
        if (animation == null) {
            if (WARNED_MISSING.add(id)) {
                Noirm.LOGGER.warn("Missing player animation {}", id);
            }
            return;
        }
        float speed = durationTicks > 0 ? Math.max(0.1f, animation.endTick / (float) durationTicks) : 1f;
        KeyframeAnimationPlayer keyframes = new KeyframeAnimationPlayer(animation)
                .setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL)
                .setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true));
        @SuppressWarnings("unchecked")
        ModifierLayer<IAnimation> layer = (ModifierLayer<IAnimation>) raw;
        // The animation goes straight into our layer, and the speed change is a modifier on that
        // layer, added after the animation (a modifier added first links to nothing and reports
        // "inactive", and an inactive layer draws nothing). No fade: fading in from an empty
        // layer has the same problem.
        layer.setAnimation(keyframes);
        while (layer.size() > 0) {
            layer.removeModifier(0);
        }
        layer.addModifierBefore(new SpeedModifier(speed));
    }

    /**
     * Every client tick: in combat mode, the local player holds their style's stance (a looping
     * upper-body pose; attacks play over it); out of combat mode, no stance.
     */
    static void tickStance(AbstractClientPlayer player, boolean combatMode, String style) {
        if (!(PlayerAnimationAccess.getPlayerAssociatedData(player).get(STANCE_LAYER) instanceof ModifierLayer<?> raw)) {
            return;
        }
        @SuppressWarnings("unchecked")
        ModifierLayer<IAnimation> layer = (ModifierLayer<IAnimation>) raw;
        String wanted = combatMode && style != null && !style.isEmpty() ? "stance_" + style : "";
        if (wanted.equals(currentStance) && (wanted.isEmpty() || layer.getAnimation() != null)) {
            return;
        }
        currentStance = wanted;
        if (wanted.isEmpty()) {
            layer.setAnimation(null);
            return;
        }
        KeyframeAnimation animation = PlayerAnimationRegistry.getAnimation(Noirm.id(wanted));
        if (animation == null) {
            animation = PlayerAnimationRegistry.getAnimation(Noirm.id("stance_basic"));
        }
        layer.setAnimation(animation == null ? null : new KeyframeAnimationPlayer(animation)
                .setFirstPersonMode(FirstPersonMode.NONE));
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
    public static final class StanceTicker {
        private StanceTicker() {}

        @SubscribeEvent
        public static void clientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || player == null) {
                return;
            }
            var data = ClientShinobiData.get();
            tickStance(player, ClientCombat.isCombatMode(), data == null ? "" : data.style());
        }
    }

    /** Gives every player an animation layer for NOIRM to play into. */
    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {
        private Setup() {}

        @SubscribeEvent
        public static void clientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 1000, player -> new ModifierLayer<>());
                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(STANCE_LAYER, 900, player -> new ModifierLayer<>());
            });
        }
    }
}
