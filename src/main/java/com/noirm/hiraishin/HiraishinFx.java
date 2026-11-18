package com.noirm.hiraishin;

import com.noirm.network.NoirmNetwork;
import com.noirm.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The look and sound of space-time moves, server side. The striking part (afterimages, the streak
 * of light, the flash on your own screen) is drawn by the client ({@code client/SpaceTimeFx}) from
 * an {@link HiraishinPackets.Fx}; here are the few particles everyone shares and the sounds.
 *
 * <p>The Flying Raijin has <b>no travel</b>: a sharp yellow flash where you were, a flash where you
 * are, an afterimage left standing for a few frames, one streak of light between, and the crisp
 * {@code space.hiraishin} sound at both ends. Never a dash.
 */
public final class HiraishinFx {
    public static final int FLICKER = 0;
    public static final int JUMP = 1;
    public static final int REDIRECT = 2;
    public static final int RECALL = 3;
    public static final int MARK = 4;
    /** Sent only to an enemy who reads the jump (a Sharingan, high perception): a warning flash where it lands. */
    public static final int READ = 5;
    public static final int LEARNED = 6;
    /** The Thunder God Slash: a jump that lands behind an enemy. */
    public static final int SLASH = 7;

    /** The Flying Raijin's yellow: a hot white core, a gold body, a deep amber edge. */
    public static final Vfx.Palette YELLOW = new Vfx.Palette(0xFFFFF4, 0xFFE040, 0x7A4A00, 0xFFF6B0);
    /** The Body Flicker's leaves and smoke. */
    public static final int LEAF = 0x5E7A2A;
    public static final int LEAF_DARK = 0x2E4414;
    public static final int SMOKE = 0xD8D4CC;

    private HiraishinFx() {}

    /** Sends the effect to everyone near either end (and to {@code who} if it's a player). */
    public static void flash(ServerLevel level, Vec3 from, Vec3 to, Entity who, int kind) {
        HiraishinPackets.Fx packet = new HiraishinPackets.Fx(kind, who == null ? -1 : who.getId(), from.x, from.y, from.z, to.x, to.y, to.z);
        NoirmNetwork.sendNear(level, from, 96, packet);
        if (from.distanceToSqr(to) > 64 * 64) {
            NoirmNetwork.sendNear(level, to, 96, packet);
        }
        switch (kind) {
            case JUMP, SLASH -> {
                burst(level, from);
                burst(level, to);
                sound(level, from, com.noirm.sound.NoirmSounds.HIRAISHIN.get(), 1.2f, 1f);
                sound(level, to, com.noirm.sound.NoirmSounds.HIRAISHIN.get(), 1.2f, 1.06f);
            }
            case REDIRECT -> {
                burst(level, from);
                burst(level, to);
                sound(level, from, com.noirm.sound.NoirmSounds.HIRAISHIN.get(), 1f, 0.84f);
                sound(level, to, com.noirm.sound.NoirmSounds.HIRAISHIN.get(), 1f, 0.84f);
            }
            case RECALL -> {
                Vfx.burst(level, Vfx.glow(YELLOW.core(), YELLOW.mid(), 0.5f, 0.05f, 5), from, 1, 0, 0);
                sound(level, from, com.noirm.sound.NoirmSounds.HIRAISHIN.get(), 0.5f, 1.4f);
            }
            case FLICKER -> {
                puff(level, from);
                puff(level, to);
                sound(level, from, com.noirm.sound.NoirmSounds.FLICKER.get(), 1f, 1f);
                sound(level, to, com.noirm.sound.NoirmSounds.FLICKER.get(), 0.7f, 1.15f);
            }
            case LEARNED -> {
                Vfx.burst(level, Vfx.ring(YELLOW.mid(), 3f, 16), to.add(0, 0.05, 0), 1, 0, 0);
                sound(level, to, com.noirm.sound.NoirmSounds.HIRAISHIN.get(), 1f, 0.7f);
            }
            default -> { }
        }
    }

    /** The shared particles of a Flying Raijin end: a tight yellow flash and a few sparks, gone in 4 ticks. */
    private static void burst(ServerLevel level, Vec3 feet) {
        Vec3 chest = feet.add(0, 1.0, 0);
        Vfx.burst(level, Vfx.glow(YELLOW.core(), YELLOW.mid(), 1.6f, 0.2f, 4), chest, 1, 0, 0);
        Vfx.burst(level, Vfx.glow(YELLOW.mid(), YELLOW.edge(), 0.9f, 0.1f, 6), chest, 2, 0.25, 0);
        Vfx.radial(level, Vfx.spark(YELLOW.mid(), 0.18f, 6), chest, 8, 0.35);
        Vfx.burst(level, Vfx.ring(YELLOW.accent(), 1.3f, 6), feet.add(0, 0.06, 0), 1, 0, 0);
    }

    /** The Body Flicker's puff: grey smoke and a spin of leaves. */
    public static void puff(ServerLevel level, Vec3 feet) {
        Vec3 mid = feet.add(0, 0.9, 0);
        Vfx.burst(level, Vfx.smoke(SMOKE, 0.7f, 18), mid, 7, 0.35, 0.03);
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10;
            Vfx.one(level, Vfx.shard(i % 2 == 0 ? LEAF : LEAF_DARK, 0.16f, 22), mid.add(Math.cos(a) * 0.3, (i % 3) * 0.3 - 0.3, Math.sin(a) * 0.3),
                    new Vec3(Math.cos(a) * 0.12, 0.06, Math.sin(a) * 0.12));
        }
    }

    private static void sound(ServerLevel level, Vec3 at, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** Tells only {@code reader} that a jump is landing at {@code at} (counterplay for Sharingan and perception). */
    public static void read(ServerPlayer reader, Vec3 at, Entity who) {
        NoirmNetwork.sendToPlayer(reader, new HiraishinPackets.Fx(READ, who.getId(), at.x, at.y, at.z, at.x, at.y, at.z));
    }
}
