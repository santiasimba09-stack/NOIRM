package com.noirm.client;

import com.noirm.Noirm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;

/**
 * Lock-on: press the key to keep the camera turned toward the enemy nearest your crosshair.
 * Press again, or lose them (too far / out of sight for 2 s), to release.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
public final class LockOn {
    private static final double RANGE = 20;
    private static final double MAX_ANGLE = 60;
    private static final int LOST_SIGHT_TICKS = 40;

    private static int targetId = -1;
    private static int lostSightTicks;

    private LockOn() {}

    public static LivingEntity target() {
        Minecraft minecraft = Minecraft.getInstance();
        if (targetId == -1 || minecraft.level == null) {
            return null;
        }
        Entity entity = minecraft.level.getEntity(targetId);
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    static void clear() {
        targetId = -1;
        lostSightTicks = 0;
    }

    static void toggle(Minecraft minecraft) {
        if (targetId != -1) {
            clear();
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        Vec3 eyes = player.getEyePosition();
        Vec3 look = player.getViewVector(1f);
        minecraft.level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RANGE),
                        e -> e != player && e.isAlive() && !(e instanceof ArmorStand) && !e.isSpectator() && player.hasLineOfSight(e))
                .stream()
                .filter(e -> angleTo(eyes, look, e) <= MAX_ANGLE)
                .min(Comparator.comparingDouble(e -> angleTo(eyes, look, e) + player.distanceTo(e)))
                .ifPresent(e -> targetId = e.getId());
    }

    static void tick(Minecraft minecraft) {
        LivingEntity target = target();
        LocalPlayer player = minecraft.player;
        if (targetId == -1 || player == null) {
            return;
        }
        if (target == null || player.distanceTo(target) > RANGE + 6) {
            clear();
            return;
        }
        lostSightTicks = player.hasLineOfSight(target) ? 0 : lostSightTicks + 1;
        if (lostSightTicks > LOST_SIGHT_TICKS) {
            clear();
        }
    }

    /** Every frame: ease the camera toward the target. */
    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        LivingEntity target = target();
        if (event.phase != TickEvent.Phase.START || player == null || target == null || minecraft.isPaused()) {
            return;
        }
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.7, 0).subtract(player.getEyePosition());
        float wantedYaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        float wantedPitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
        float follow = 1f - (float) Math.pow(0.2, minecraft.getDeltaFrameTime());
        float dYaw = Mth.wrapDegrees(wantedYaw - player.getYRot()) * follow;
        float dPitch = (Mth.clamp(wantedPitch, -60f, 60f) - player.getXRot()) * follow;
        // Like Entity.turn: move the previous rotation too, so rendering doesn't jitter.
        player.setYRot(player.getYRot() + dYaw);
        player.yRotO += dYaw;
        player.setXRot(player.getXRot() + dPitch);
        player.xRotO += dPitch;
    }

    private static double angleTo(Vec3 eyes, Vec3 look, LivingEntity entity) {
        Vec3 to = entity.position().add(0, entity.getBbHeight() / 2, 0).subtract(eyes).normalize();
        return Math.toDegrees(Math.acos(Mth.clamp(look.dot(to), -1, 1)));
    }
}
