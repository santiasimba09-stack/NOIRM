package com.noirm.network;

import com.noirm.client.ClientCombat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → client combat messages. Handlers go through {@link DistExecutor} so a dedicated server
 * never loads client-only classes.
 */
public final class CombatClientPackets {
    private CombatClientPackets() {}

    /** Play a player animation on {@code entityId}, stretched to last {@code durationTicks}. */
    public record PlayAnimation(int entityId, ResourceLocation animation, int durationTicks) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeResourceLocation(animation);
            buf.writeVarInt(durationTicks);
        }

        public static PlayAnimation decode(FriendlyByteBuf buf) {
            return new PlayAnimation(buf.readVarInt(), buf.readResourceLocation(), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCombat.playAnimation(this));
            context.get().setPacketHandled(true);
        }
    }

    /** The local player's own combat state, for the HUD. */
    public record SyncState(boolean combatMode, boolean guarding, float posture, float maxPosture) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(combatMode);
            buf.writeBoolean(guarding);
            buf.writeFloat(posture);
            buf.writeFloat(maxPosture);
        }

        public static SyncState decode(FriendlyByteBuf buf) {
            return new SyncState(buf.readBoolean(), buf.readBoolean(), buf.readFloat(), buf.readFloat());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCombat.syncState(this));
            context.get().setPacketHandled(true);
        }
    }

    /** Something happened to the local player that the screen should feel: shake, flash, etc. */
    public record Feedback(Kind kind, float strength, int targetId, float targetPosture, float targetMaxPosture) {
        public enum Kind { HIT_DEALT, HIT_TAKEN, GUARD, PARRY, POSTURE_BREAK, PERFECT_DODGE, FOCUS, NO_STAMINA, SHAKE }

        public static Feedback simple(Kind kind, float strength) {
            return new Feedback(kind, strength, -1, 0, 0);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeEnum(kind);
            buf.writeFloat(strength);
            buf.writeVarInt(targetId);
            buf.writeFloat(targetPosture);
            buf.writeFloat(targetMaxPosture);
        }

        public static Feedback decode(FriendlyByteBuf buf) {
            return new Feedback(buf.readEnum(Kind.class), buf.readFloat(), buf.readVarInt(), buf.readFloat(), buf.readFloat());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCombat.feedback(this));
            context.get().setPacketHandled(true);
        }
    }
}
