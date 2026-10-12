package com.noirm.client;

import com.mojang.math.Axis;
import com.noirm.Noirm;
import com.noirm.network.PowerPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes the eyes feel alive (client only):
 * <ul>
 *   <li><b>Warnings as a heartbeat:</b> when an attack is coming, the screen edges pulse
 *       red in a "lub-dub" rhythm that speeds up as the hit gets closer, with a heartbeat sound,
 *       and a marker at the edge of the screen points at each threat, even behind you.</li>
 *   <li><b>Afterimages:</b> with the Sharingan open, whoever is about to hit you leaves a red
 *       ghost trail (the Byakugan's is pale blue).</li>
 *   <li><b>Activation:</b> opening the eyes flashes the screen and spins three tomoe (or a pale
 *       ring for the Byakugan).</li>
 *   <li>While the Sharingan is open, a faint red tint at the edges of your vision.</li>
 * </ul>
 */
public final class EyeEffects {
    private static final int ACTIVATION_TICKS = 24;
    private static final DustParticleOptions RED_GHOST = new DustParticleOptions(new Vector3f(0.9f, 0.05f, 0.05f), 1.2f);
    private static final DustParticleOptions PALE_GHOST = new DustParticleOptions(new Vector3f(0.7f, 0.8f, 1f), 1.0f);

    /** An attack the player was warned about. */
    private static final class Threat {
        final int entityId;
        final int total;
        int left;

        Threat(int entityId, int ticks) {
            this.entityId = entityId;
            this.total = Math.max(6, ticks);
            this.left = this.total;
        }
    }

    private static final List<Threat> THREATS = new ArrayList<>();
    /** Ticks since the last heartbeat started (-1 = none playing). */
    private static int beatAge = -1;
    private static int activationAge = -1;
    private static boolean activationSharingan;

    private EyeEffects() {}

    // ---------------------------------------------------------------- events from the server

    static void onWarning(PowerPackets.Precognition packet) {
        THREATS.removeIf(t -> t.entityId == packet.attackerId());
        THREATS.add(new Threat(packet.attackerId(), packet.ticks()));
        if (beatAge < 0 || beatAge > 4) {
            beat();
        }
    }

    /** The local player just opened their eyes. */
    static void onActivated(boolean sharingan) {
        activationAge = 0;
        activationSharingan = sharingan;
    }

    static void reset() {
        THREATS.clear();
        beatAge = -1;
        activationAge = -1;
    }

    // ---------------------------------------------------------------- ticking

    static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (activationAge >= 0 && ++activationAge > ACTIVATION_TICKS) {
            activationAge = -1;
        }
        if (beatAge >= 0) {
            beatAge++;
        }
        THREATS.removeIf(t -> --t.left < -4);
        if (THREATS.isEmpty() || minecraft.level == null) {
            return;
        }
        // The rhythm quickens as the soonest hit approaches: a beat every 14 ticks down to every 5.
        int soonest = THREATS.stream().mapToInt(t -> Math.max(0, t.left)).min().orElse(0);
        int period = Mth.clamp(soonest / 2, 5, 14);
        if (beatAge < 0 || beatAge >= period) {
            beat();
        }
        afterimages(minecraft);
    }

    private static void beat() {
        beatAge = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.WARDEN_HEARTBEAT, 0.8f, 1.4f);
        }
    }

    private static void afterimages(Minecraft minecraft) {
        boolean sharingan = ClientPowers.get() != null && ClientPowers.get().sharinganStage() > 0;
        for (Threat threat : THREATS) {
            Entity entity = minecraft.level.getEntity(threat.entityId);
            if (entity == null || threat.left < 0) {
                continue;
            }
            // A ghost of where it just was: particles left hanging along its body.
            for (int i = 0; i < 3; i++) {
                double y = entity.yo + entity.getBbHeight() * (0.2 + 0.3 * i);
                minecraft.level.addParticle(sharingan ? RED_GHOST : PALE_GHOST,
                        entity.xo + (minecraft.level.random.nextDouble() - 0.5) * entity.getBbWidth(), y,
                        entity.zo + (minecraft.level.random.nextDouble() - 0.5) * entity.getBbWidth(), 0, 0, 0);
            }
        }
    }

    // ---------------------------------------------------------------- drawing

    /** "Lub-dub": a strong beat, then a softer one 3 ticks later. 0..1. */
    static float pulse(float partialTick) {
        if (beatAge < 0 || THREATS.isEmpty()) {
            return 0f;
        }
        float t = beatAge + partialTick;
        float lub = (float) Math.exp(-(t * t) / 2.5);
        float dub = 0.7f * (float) Math.exp(-((t - 3.5f) * (t - 3.5f)) / 2.5);
        return Mth.clamp(lub + dub, 0f, 1f);
    }

    static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = gui.getMinecraft();
        if (minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        PowerPackets.SyncPowers powers = ClientPowers.get();
        boolean sharingan = powers != null && powers.sharinganStage() > 0;
        int tint = sharingan ? 0xC00000 : 0xB8B0FF;
        if (powers != null && powers.eyesActive() && sharingan) {
            int edge = Math.max(30, height / 6);
            graphics.fillGradient(0, 0, width, edge, 0x28000000 | tint, 0);
            graphics.fillGradient(0, height - edge, width, height, 0, 0x28000000 | tint);
        }
        drawWarning(graphics, minecraft, partialTick, width, height, tint);
        drawActivation(graphics, minecraft, partialTick, width, height);
    }

    private static void drawWarning(GuiGraphics graphics, Minecraft minecraft, float partialTick, int width, int height, int tint) {
        if (THREATS.isEmpty()) {
            return;
        }
        float pulse = pulse(partialTick);
        int alpha = Math.round(30 + 150 * pulse);
        int edge = Math.round(16 + 26 * pulse);
        int color = (alpha << 24) | tint;
        graphics.fillGradient(0, 0, width, edge, color, 0);
        graphics.fillGradient(0, height - edge, width, height, 0, color);
        graphics.fill(0, 0, Math.round(3 + 5 * pulse), height, ((alpha / 2) << 24) | tint);
        graphics.fill(width - Math.round(3 + 5 * pulse), 0, width, height, ((alpha / 2) << 24) | tint);
        if (pulse > 0.05f) {
            graphics.fill(0, 0, width, height, (Math.round(28 * pulse) << 24) | tint);
        }
        // A marker at the edge of a circle around the crosshair, pointing at each threat.
        float radius = Math.min(width, height) * 0.32f;
        for (Threat threat : THREATS) {
            Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(threat.entityId);
            if (entity == null || threat.left < 0) {
                continue;
            }
            double dx = entity.getX() - minecraft.player.getX();
            double dz = entity.getZ() - minecraft.player.getZ();
            float toward = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            float relative = Mth.wrapDegrees(toward - minecraft.player.getViewYRot(partialTick));
            float urgency = 1f - Mth.clamp(threat.left / (float) threat.total, 0f, 1f);
            int markerAlpha = Math.round(140 + 115 * Math.max(urgency, pulse));
            graphics.pose().pushPose();
            graphics.pose().translate(width / 2f, height / 2f, 0);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(relative));
            graphics.pose().translate(0, -radius, 0);
            float scale = 1.6f + 0.8f * urgency + 0.5f * pulse;
            graphics.pose().scale(scale, scale, 1f);
            graphics.drawCenteredString(minecraft.font, "▲", 0, -4, (markerAlpha << 24) | tint);
            graphics.pose().popPose();
        }
    }

    private static void drawActivation(GuiGraphics graphics, Minecraft minecraft, float partialTick, int width, int height) {
        if (activationAge < 0) {
            return;
        }
        float t = (activationAge + partialTick) / ACTIVATION_TICKS;
        float fade = 1f - t;
        int tint = activationSharingan ? 0xD01010 : 0xE8E4FF;
        graphics.fill(0, 0, width, height, (Math.round(110 * fade * fade) << 24) | tint);
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2f, height / 2f, 0);
        float scale = 2.5f + 3f * t;
        int alpha = Math.round(255 * fade);
        if (alpha > 8) {
            if (activationSharingan) {
                // Three tomoe spinning around the pupil, speeding as they spread.
                graphics.pose().mulPose(Axis.ZP.rotationDegrees(t * 540f));
                for (int i = 0; i < 3; i++) {
                    graphics.pose().pushPose();
                    graphics.pose().mulPose(Axis.ZP.rotationDegrees(i * 120f));
                    graphics.pose().translate(0, -(10 + 30 * t), 0);
                    graphics.pose().scale(scale, scale, 1f);
                    graphics.drawCenteredString(minecraft.font, "●", 0, -4, (alpha << 24) | 0xFF2020);
                    graphics.pose().popPose();
                }
            } else {
                graphics.pose().scale(scale * 2, scale * 2, 1f);
                graphics.drawCenteredString(minecraft.font, "◯", 0, -4, (alpha << 24) | 0xF0EEFF);
            }
        }
        graphics.pose().popPose();
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class EyeOverlayRegistration {
        private EyeOverlayRegistration() {}

        @SubscribeEvent
        public static void registerEyeOverlay(RegisterGuiOverlaysEvent event) {
            event.registerBelowAll("eye_effects", EyeEffects::render);
        }
    }
}
