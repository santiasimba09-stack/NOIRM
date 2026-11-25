package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.network.PowerPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Chakra sensing (the {@code sensor} perk: Chakra Sensor talent, the Yamanaka, the Inuzuka's nose,
 * the Aburame's insects): while you crouch to concentrate, a small radar in the top right shows the
 * chakra around you within your sensing range. Hostiles red, everyone else pale gold. The Byakugan's
 * own radar takes the spot while the eye is open.
 */
public final class SensorRadar {
    private SensorRadar() {}

    /** The local player's sensing range in blocks (0 = no sense). */
    static float range() {
        PowerPackets.SyncPowers powers = ClientPowers.get();
        float sense = powers == null || powers.profile() == null ? 0f : powers.profile().sensor();
        if (powers != null && powers.colony() != null && powers.colony().has()) {
            sense = Math.max(sense, powers.colony().radar()); // Insect Radar: every swarm out is a little sensor
        }
        return sense;
    }

    static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = gui.getMinecraft();
        float range = range();
        if (range <= 0f || minecraft.options.hideGui || minecraft.player == null || minecraft.level == null
                || !minecraft.player.isShiftKeyDown() || ClientPowers.localByakuganActive()) {
            return;
        }
        int radius = 26;
        int cx = width - radius - 8;
        int cy = radius + 8;
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.sqrt(radius * radius - dy * dy);
            graphics.fill(cx - half, cy + dy, cx + half, cy + dy + 1, 0x38201810);
        }
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2 / 48;
            int x = cx + (int) Math.round(Math.cos(a) * radius);
            int y = cy + (int) Math.round(Math.sin(a) * radius);
            graphics.fill(x, y, x + 1, y + 1, 0x90E8C878);
        }
        graphics.fill(cx, cy - radius + 2, cx + 1, cy - radius + 5, 0xA0FFFFFF); // where you face
        float yaw = Mth.lerp(partialTick, minecraft.player.yRotO, minecraft.player.getYRot()) * Mth.DEG_TO_RAD;
        double scale = radius / range;
        Vec3 me = minecraft.player.getPosition(partialTick);
        AABB area = minecraft.player.getBoundingBox().inflate(range);
        float pulse = 0.6f + 0.4f * Mth.sin((minecraft.player.tickCount + partialTick) * 0.25f);
        // Insect Jamming (someone else's): real chakra inside the field hides behind a dozen false signals.
        java.util.List<com.noirm.aburame.InsectSwarm> jams = minecraft.level.getEntitiesOfClass(com.noirm.aburame.InsectSwarm.class, area.inflate(24),
                s -> s.mode() == com.noirm.aburame.SwarmMode.JAM && s.ownerEntityId() != minecraft.player.getId());
        for (com.noirm.aburame.InsectSwarm jam : jams) {
            for (int k = 0; k < 12; k++) {
                double a = k * 2.399 + jam.getId();
                double d = jam.shape() * (0.2 + 0.8 * ((k * 37 + jam.getId()) % 11) / 11.0);
                double fx = jam.getX() + Math.cos(a + Math.sin((minecraft.player.tickCount + k * 9) * 0.05) * 0.3) * d - me.x;
                double fz = jam.getZ() + Math.sin(a) * d - me.z;
                double forward = fx * -Mth.sin(yaw) + fz * Mth.cos(yaw);
                double right = fx * -Mth.cos(yaw) + fz * -Mth.sin(yaw);
                if (forward * forward + right * right <= range * range) {
                    int fxp = cx - (int) Math.round(right * scale);
                    int fyp = cy - (int) Math.round(forward * scale);
                    graphics.fill(fxp - 1, fyp - 1, fxp + 2, fyp + 2, ((int) (255 * pulse) << 24) | 0xFF6A5A);
                }
            }
        }
        for (LivingEntity entity : minecraft.level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != minecraft.player && e.isAlive())) {
            Vec3 at = entity.getPosition(partialTick);
            boolean hidden = false;
            for (com.noirm.aburame.InsectSwarm jam : jams) {
                if (at.distanceToSqr(jam.position()) < jam.shape() * jam.shape()) {
                    hidden = true;
                }
            }
            if (hidden) {
                continue;
            }
            double dx = at.x - me.x;
            double dz = at.z - me.z;
            double forward = dx * -Mth.sin(yaw) + dz * Mth.cos(yaw);
            double right = dx * -Mth.cos(yaw) + dz * -Mth.sin(yaw);
            if (forward * forward + right * right > range * range) {
                continue;
            }
            int x = cx - (int) Math.round(right * scale);
            int y = cy - (int) Math.round(forward * scale);
            int size = entity.getMaxHealth() >= 60 ? 2 : 1;
            int alpha = (int) (255 * pulse) << 24;
            int color = entity instanceof net.minecraft.world.entity.monster.Enemy ? 0xFF6A5A : 0xF0D890;
            graphics.fill(x - size, y - size, x + size + 1, y + size + 1, alpha | color);
        }
        graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class SensorRadarRegistration {
        private SensorRadarRegistration() {}

        @SubscribeEvent
        public static void registerSensorRadar(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("sensor_radar", SensorRadar::render);
        }
    }
}
