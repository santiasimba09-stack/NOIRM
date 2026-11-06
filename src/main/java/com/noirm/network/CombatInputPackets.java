package com.noirm.network;

import com.noirm.combat.AttackInput;
import com.noirm.combat.CombatStates;
import com.noirm.combat.CombatSystem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Client → server button presses. The server checks everything itself (combat mode, stamina,
 * timing); a press only <em>asks</em> for an action.
 */
public final class CombatInputPackets {
    private CombatInputPackets() {}

    /** Turn combat mode on or off. */
    public record SetCombatMode(boolean enabled) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(enabled);
        }

        public static SetCombatMode decode(FriendlyByteBuf buf) {
            return new SetCombatMode(buf.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            withPlayer(context, player -> CombatSystem.setCombatMode(player, enabled), false);
        }
    }

    public record Attack(AttackInput input) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeEnum(input);
        }

        public static Attack decode(FriendlyByteBuf buf) {
            return new Attack(buf.readEnum(AttackInput.class));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            withPlayer(context, player -> CombatSystem.requestAttack(player, input), true);
        }
    }

    /** Switch to the next fighting style the player can use. */
    public record CycleStyle() {
        public void encode(FriendlyByteBuf buf) {
        }

        public static CycleStyle decode(FriendlyByteBuf buf) {
            return new CycleStyle();
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            withPlayer(context, com.noirm.combat.StyleSystem::cycle, false);
        }
    }

    /** {@code forward}/{@code strafe} are the movement keys held (-1..1), strafe positive = left. */
    public record Dodge(float forward, float strafe) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeFloat(forward);
            buf.writeFloat(strafe);
        }

        public static Dodge decode(FriendlyByteBuf buf) {
            return new Dodge(sanitize(buf.readFloat()), sanitize(buf.readFloat()));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            withPlayer(context, player -> CombatSystem.requestDodge(player, forward, strafe), true);
        }

        private static float sanitize(float value) {
            return Float.isFinite(value) ? Mth.clamp(value, -1f, 1f) : 0f;
        }
    }

    public record Guard(boolean raised) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(raised);
        }

        public static Guard decode(FriendlyByteBuf buf) {
            return new Guard(buf.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            // Lowering guard is always allowed, so leaving combat mode can never leave you stuck guarding.
            withPlayer(context, player -> CombatSystem.setGuard(player, raised), raised);
        }
    }

    private static void withPlayer(Supplier<NetworkEvent.Context> context, Consumer<ServerPlayer> action, boolean needsCombatMode) {
        ServerPlayer player = context.get().getSender();
        if (player != null && player.isAlive() && !player.isSpectator()
                && (!needsCombatMode || CombatStates.get(player).isCombatMode())) {
            action.accept(player);
        }
        context.get().setPacketHandled(true);
    }
}
