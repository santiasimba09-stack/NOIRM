package com.noirm.client.beast;

import com.noirm.Noirm;
import com.noirm.client.model.CreatureRig;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * One Shukaku's animation memory (the model's parts are shared by every Shukaku, so springs, timers and blended
 * postures live here, keyed by entity id). Dropped when the entity leaves the client level.
 */
public final class ShukakuMotion {
    static final int TAIL = 7;
    private static final Int2ObjectMap<ShukakuMotion> STATE = new Int2ObjectOpenHashMap<>();

    final long seed;
    final CreatureRig.Blinker blink;
    final CreatureRig.Twitch earL, earR, tailTip;
    final CreatureRig.Spring[] tailYaw = new CreatureRig.Spring[TAIL], tailPitch = new CreatureRig.Spring[TAIL];
    final CreatureRig.Spring earSwing = new CreatureRig.Spring(), bellyJiggle = new CreatureRig.Spring(), rock = new CreatureRig.Spring();
    /** Blended posture (see ShukakuRenderer.Posture). */
    float lean, headDown, tailLift, earBack, spread, eyeOpen = 1f, brow, grin, breathRate = 0.25f, breathAmp = 1f, glow = 0.7f;
    float lookYaw, lookPitch;
    float lastT = -1f, lastBodyYaw = Float.NaN, lastBob;
    int lastHurt;
    float hurtFlash;
    byte action, prevAction;
    int actionStart, prevStart;
    float actionWeight, prevWeight;

    private ShukakuMotion(UUID uuid) {
        seed = uuid.getLeastSignificantBits() ^ uuid.getMostSignificantBits();
        blink = new CreatureRig.Blinker(seed);
        earL = new CreatureRig.Twitch(seed + 1, 3f, 8f, 0.35f);
        earR = new CreatureRig.Twitch(seed + 2, 3f, 8f, 0.35f);
        tailTip = new CreatureRig.Twitch(seed + 3, 4f, 9f, 0.8f);
        for (int i = 0; i < TAIL; i++) {
            tailYaw[i] = new CreatureRig.Spring();
            tailPitch[i] = new CreatureRig.Spring();
        }
    }

    static ShukakuMotion of(Entity e) {
        return STATE.computeIfAbsent(e.getId(), id -> new ShukakuMotion(e.getUUID()));
    }

    /** Seconds since the last frame, clamped to [0, 0.1] (pauses and lag spikes don't explode the springs). */
    float frameDelta(float ticks) {
        float dt = lastT < 0f ? 0f : (ticks - lastT) / 20f;
        lastT = ticks;
        return Math.max(0f, Math.min(0.1f, dt));
    }

    /** Forgets beasts that leave the level, so the table can't grow. */
    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
    public static final class ShukakuMotionCleanup {
        private ShukakuMotionCleanup() {}

        @SubscribeEvent
        public static void onLeave(EntityLeaveLevelEvent event) {
            if (event.getLevel().isClientSide()) {
                STATE.remove(event.getEntity().getId());
            }
        }
    }
}
