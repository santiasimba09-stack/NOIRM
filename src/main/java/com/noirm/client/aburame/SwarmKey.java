package com.noirm.client.aburame;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noirm.Noirm;
import com.noirm.aburame.ColonyPackets;
import com.noirm.aburame.InsectSwarm;
import com.noirm.client.CombatKeys;
import com.noirm.network.NoirmNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * The Swarm key (M, for mushi; docs/ABURAME.md, 2.5): the one direct control over your insects.
 * <ul>
 *   <li>tap: recall every swarm;</li>
 *   <li>hold and let go looking at a creature: send every free swarm at it;</li>
 *   <li>hold and let go looking at a block: park your swarms there (an ambush cloud);</li>
 *   <li>double-tap: the colony panel.</li>
 * </ul>
 * Also draws the faint teal thread toward each creature you've marked with a female insect.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
public final class SwarmKey {
    public static final KeyMapping KEY = new KeyMapping("key.noirm.swarm", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M, CombatKeys.CATEGORY);
    /** Held at least this long (ticks) it aims instead of recalling; two taps this close are a double-tap. */
    public static final int HOLD_TICKS = 7, DOUBLE_TICKS = 7;
    public static final double REACH = 64;

    private static int held;
    private static int pendingTap = -1;

    private SwarmKey() {}

    @SubscribeEvent
    public static void swarmKeyTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (event.phase != TickEvent.Phase.END || player == null) {
            return;
        }
        InsectSwarm.localPlayerId = player.getId();
        if (!ClientColony.has()) {
            held = 0;
            pendingTap = -1;
            return;
        }
        if (pendingTap >= 0 && ++pendingTap > DOUBLE_TICKS) {
            pendingTap = -1;
            NoirmNetwork.sendToServer(new ColonyPackets.Command("recall"));
        }
        boolean down = KEY.isDown() && mc.screen == null;
        if (down) {
            held++;
            while (KEY.consumeClick()) {
                // presses are counted by the hold above
            }
            return;
        }
        if (held == 0) {
            return;
        }
        int was = held;
        held = 0;
        if (was < HOLD_TICKS) {
            if (pendingTap >= 0) {
                pendingTap = -1;
                ClientColony.openPanel(); // double-tap
            } else {
                pendingTap = 0;
            }
            return;
        }
        aimRelease(mc, player);
    }

    private static void aimRelease(Minecraft mc, LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1f).scale(REACH));
        HitResult block = player.pick(REACH, 1f, false);
        double reach = block.getType() == HitResult.Type.MISS ? REACH : block.getLocation().distanceTo(eye);
        AABB box = player.getBoundingBox().expandTowards(player.getViewVector(1f).scale(reach)).inflate(1.5);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, eye.add(player.getViewVector(1f).scale(reach)), box,
                e -> e instanceof LivingEntity && e.isAlive() && e != player && !(e instanceof InsectSwarm), reach * reach);
        if (hit != null) {
            NoirmNetwork.sendToServer(new ColonyPackets.Command("send:" + hit.getEntity().getId()));
        } else if (block instanceof BlockHitResult bh && block.getType() != HitResult.Type.MISS) {
            Vec3 at = bh.getLocation();
            NoirmNetwork.sendToServer(new ColonyPackets.Command(String.format(java.util.Locale.ROOT, "park:%.2f,%.2f,%.2f", at.x, at.y, at.z)));
        }
    }

    /** The female insect's thread: a faint teal line from you to each marked creature in sight (256 blocks). */
    @SubscribeEvent
    public static void drawThreads(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !ClientColony.has()) {
            return;
        }
        ColonyPackets.ColonySync c = ClientColony.sync();
        if (c.marks().isEmpty()) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack stack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(RenderType.lines());
        Matrix4f pose = stack.last().pose();
        Matrix3f normal = stack.last().normal();
        float partial = event.getPartialTick();
        Vec3 from = mc.player.getPosition(partial).add(0, 1.1, 0);
        for (ColonyPackets.MarkView m : c.marks()) {
            Entity e = m.entityId() >= 0 ? mc.level.getEntity(m.entityId()) : null;
            if (e == null) {
                continue;
            }
            Vec3 to = e.getPosition(partial).add(0, e.getBbHeight() * 0.8, 0);
            Vec3 d = to.subtract(from);
            int seg = 24;
            float pulse = 0.35f + 0.25f * (float) Math.sin((mc.player.tickCount + partial) * 0.2);
            for (int i = 0; i < seg; i++) {
                if (i % 2 == 1) {
                    continue; // a dotted thread
                }
                Vec3 a = from.add(d.scale(i / (double) seg)).subtract(cam);
                Vec3 b = from.add(d.scale((i + 1) / (double) seg)).subtract(cam);
                Vec3 n = b.subtract(a).normalize();
                out.vertex(pose, (float) a.x, (float) a.y, (float) a.z).color(0.25f, 0.66f, 0.66f, pulse)
                        .normal(normal, (float) n.x, (float) n.y, (float) n.z).endVertex();
                out.vertex(pose, (float) b.x, (float) b.y, (float) b.z).color(0.25f, 0.66f, 0.66f, pulse)
                        .normal(normal, (float) n.x, (float) n.y, (float) n.z).endVertex();
            }
        }
        buffers.endBatch(RenderType.lines());
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class SwarmKeyRegistration {
        private SwarmKeyRegistration() {}

        @SubscribeEvent
        public static void registerSwarmKey(RegisterKeyMappingsEvent event) {
            event.register(KEY);
        }
    }
}
