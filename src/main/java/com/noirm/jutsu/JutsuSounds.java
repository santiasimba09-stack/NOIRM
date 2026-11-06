package com.noirm.jutsu;

import com.noirm.clan.Nature;
import com.noirm.sound.NoirmSounds;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.RegistryObject;

/**
 * The sounds of casting (server side): a soft clap per hand seal, a chakra whoosh when the seals
 * lock, and a held hum while a jutsu charges or channels. The hum files are seamless loops; the
 * server replays one each time it runs out, and stops it for everyone near when the cast ends.
 */
public final class JutsuSounds {
    /** How far the "stop the hum" message reaches. */
    private static final double STOP_RANGE = 48;

    private JutsuSounds() {}

    static void sealClap(LivingEntity caster) {
        NoirmSounds.play(caster, NoirmSounds.SEAL_CLAP, SoundSource.PLAYERS, 0.45f, 1f);
    }

    static void sealsComplete(LivingEntity caster) {
        NoirmSounds.play(caster, NoirmSounds.SEAL_COMPLETE, SoundSource.PLAYERS, 0.7f, 1f);
    }

    /** The loop held under a charging or channelled jutsu, or null for none. */
    public static RegistryObject<SoundEvent> heldSound(JutsuDefinition def) {
        Nature nature = def.nature().orElse(null);
        if (def.behavior().equals("dash_strike") || nature == Nature.LIGHTNING) {
            return NoirmSounds.LIGHTNING_CRACKLE; // Chidori: lightning screaming in the hand
        }
        if (nature == Nature.STORM) {
            return NoirmSounds.STORM_HUM;
        }
        return def.cast() == CastType.CHARGE ? NoirmSounds.CHAKRA_CHARGE : null;
    }

    /** Length of each held loop in ticks (the files are 3 s and 2 s). */
    private static int loopTicks(RegistryObject<SoundEvent> sound) {
        return sound == NoirmSounds.CHAKRA_CHARGE ? 60 : 40;
    }

    /** Called every tick of a charge or channel ({@code ticks} counts from 1). */
    static void held(LivingEntity caster, JutsuDefinition def, int ticks) {
        RegistryObject<SoundEvent> sound = heldSound(def);
        if (sound != null && (ticks - 1) % loopTicks(sound) == 0) {
            caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(), sound.get(), SoundSource.PLAYERS, 0.8f, 1f);
        }
    }

    /** The cast ended: silence the held loops for everyone who could hear them. */
    static void stopHeld(LivingEntity caster) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(caster) <= STOP_RANGE * STOP_RANGE && player.connection != null
                    && player.connection.connection.channel() != null) { // test players have no network
                for (RegistryObject<SoundEvent> loop : java.util.List.of(NoirmSounds.CHAKRA_CHARGE, NoirmSounds.LIGHTNING_CRACKLE, NoirmSounds.STORM_HUM)) {
                    player.connection.send(new ClientboundStopSoundPacket(loop.getId(), SoundSource.PLAYERS));
                }
            }
        }
    }
}
