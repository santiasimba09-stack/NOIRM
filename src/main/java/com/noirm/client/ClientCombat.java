package com.noirm.client;

import com.noirm.network.CombatClientPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The client's view of combat: whether combat mode is on, the HUD values, and short-lived
 * "feel" effects (camera shake, focus pulse). Packet handlers land here.
 */
public final class ClientCombat {
    private static boolean combatMode;
    private static boolean guarding;
    private static float posture;
    private static float maxPosture = 100f;
    private static com.noirm.combat.SubStance subStance = com.noirm.combat.SubStance.NEUTRAL;
    private static net.minecraft.resources.ResourceLocation stance;
    private static int stanceSwitch = 2;
    /** Client time (ms) of the last sub-stance change, for the HUD label's flash. */
    private static long subStanceChangedAt;

    private static int lastTargetId = -1;
    private static float lastTargetPosture;
    private static float lastTargetMaxPosture = 100f;
    private static int lastTargetTicks;

    private static float shake;
    private static int focusTicks;
    private static int staminaFlashTicks;

    public static final int FOCUS_PULSE_TICKS = 20;

    private ClientCombat() {}

    public static boolean isCombatMode() {
        return combatMode;
    }

    /** Applied immediately on key press so input feels instant; the server confirms via sync. */
    static void setLocalCombatMode(boolean enabled) {
        combatMode = enabled;
    }

    public static boolean isGuarding() {
        return guarding;
    }

    public static float posture() {
        return posture;
    }

    public static float maxPosture() {
        return maxPosture;
    }

    /** The local player's sub-stance (server-picked). */
    public static com.noirm.combat.SubStance subStance() {
        return subStance;
    }

    /** The stance animation the server says the local player shows, or null. */
    public static net.minecraft.resources.ResourceLocation stance() {
        return stance;
    }

    public static int stanceSwitch() {
        return stanceSwitch;
    }

    /** Milliseconds since the sub-stance last changed. */
    public static long sinceSubStanceChange() {
        return System.currentTimeMillis() - subStanceChangedAt;
    }

    public static float shake() {
        return shake;
    }

    public static int focusTicks() {
        return focusTicks;
    }

    public static boolean staminaFlashing() {
        return staminaFlashTicks > 0;
    }

    /** The last enemy you hit, while its posture info is fresh. -1 if none. */
    public static int lastTargetId() {
        return lastTargetTicks > 0 ? lastTargetId : -1;
    }

    public static float lastTargetPosture() {
        return lastTargetPosture;
    }

    public static float lastTargetMaxPosture() {
        return lastTargetMaxPosture;
    }

    static void tick() {
        shake = shake < 0.01f ? 0f : shake * 0.8f;
        if (focusTicks > 0) {
            focusTicks--;
        }
        if (staminaFlashTicks > 0) {
            staminaFlashTicks--;
        }
        if (lastTargetTicks > 0) {
            lastTargetTicks--;
        }
        PerceptionFocus.tick();
    }

    static void reset() {
        combatMode = false;
        guarding = false;
        posture = 0;
        subStance = com.noirm.combat.SubStance.NEUTRAL;
        stance = null;
        lastTargetTicks = 0;
        shake = 0;
        focusTicks = 0;
        PerceptionFocus.reset();
        LockOn.clear();
    }

    // ------------------------------------------------------------ packet handlers

    public static void playAnimation(CombatClientPackets.PlayAnimation packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(packet.entityId());
        if (packet.animation().equals(CombatClientPackets.HITSTOP)) {
            if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                com.noirm.client.anim.HumanoidAnimator.hitstop(living, packet.durationTicks());
            }
        } else if (entity instanceof AbstractClientPlayer player) {
            com.noirm.client.anim.HumanoidAnimator.playOnPlayer(player.getId(), packet.animation(), packet.durationTicks(), minecraft.level.getGameTime());
        }
    }

    public static void syncState(CombatClientPackets.SyncState packet) {
        combatMode = packet.combatMode();
        guarding = packet.guarding();
        posture = packet.posture();
        maxPosture = packet.maxPosture();
        com.noirm.combat.SubStance sub = com.noirm.combat.SubStance.byId(packet.subStance());
        if (sub != subStance) {
            subStanceChangedAt = System.currentTimeMillis();
        }
        subStance = sub;
        stance = packet.stance().isEmpty() ? null : net.minecraft.resources.ResourceLocation.tryParse(packet.stance());
        stanceSwitch = packet.stanceSwitch();
        if (!combatMode) {
            LockOn.clear();
        }
    }

    public static void feedback(CombatClientPackets.Feedback packet) {
        switch (packet.kind()) {
            case HIT_DEALT -> {
                shake = Math.max(shake, packet.strength());
                lastTargetId = packet.targetId();
                lastTargetPosture = packet.targetPosture();
                lastTargetMaxPosture = packet.targetMaxPosture();
                lastTargetTicks = 60;
            }
            case HIT_TAKEN -> shake = Math.max(shake, packet.strength() * 1.2f);
            case GUARD, PARRY, POSTURE_BREAK, PERFECT_DODGE -> shake = Math.max(shake, packet.strength());
            case FOCUS -> focusTicks = FOCUS_PULSE_TICKS;
            case NO_STAMINA -> staminaFlashTicks = 12;
            case SHAKE -> shake = Math.max(shake, packet.strength());
            case PERCEPTION_FOCUS -> PerceptionFocus.start(Math.round(packet.targetPosture()), packet.strength());
            case JUST_FRAME -> {
                shake = Math.max(shake, packet.strength());
                PerceptionFocus.justFrame();
            }
        }
    }
}
