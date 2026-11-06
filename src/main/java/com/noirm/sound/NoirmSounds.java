package com.noirm.sound;

import com.noirm.Noirm;
import com.noirm.clan.Nature;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NOIRM's own sound effects. Every one is synthesised from scratch by {@code tools/soundgen/}
 * (no recorded audio), and listed with its files and subtitle in {@code assets/noirm/sounds.json}.
 * The full list, and where each plays, is in {@code docs/SOUND.md}.
 *
 * <p>Which slider controls what (vanilla's Music &amp; Sounds screen): jutsu, combat and weapons use
 * <b>Players</b> (or <b>Hostile Creatures</b> when an NPC makes them), the UI uses <b>Master</b>,
 * the village and sanctuary ambience uses <b>Ambient/Environment</b>.
 *
 * <p>How far a sound carries is set per file in sounds.json ({@code attenuation_distance}): jutsu
 * 32 blocks, explosions 48, S-rank moments 64; hits and seals keep vanilla's 16.
 */
public final class NoirmSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Noirm.MOD_ID);
    private static final Map<String, RegistryObject<SoundEvent>> ALL = new LinkedHashMap<>();

    // Hand seals and chakra
    public static final RegistryObject<SoundEvent> SEAL_CLAP = register("seal.clap");
    public static final RegistryObject<SoundEvent> SEAL_COMPLETE = register("seal.complete");
    public static final RegistryObject<SoundEvent> CHAKRA_CHARGE = register("chakra.charge");
    public static final RegistryObject<SoundEvent> CHAKRA_RELEASE = register("chakra.release");
    // Combat
    public static final RegistryObject<SoundEvent> HIT_LIGHT = register("combat.hit_light");
    public static final RegistryObject<SoundEvent> HIT_HEAVY = register("combat.hit_heavy");
    public static final RegistryObject<SoundEvent> GUARD = register("combat.guard");
    public static final RegistryObject<SoundEvent> PARRY = register("combat.parry");
    public static final RegistryObject<SoundEvent> JUST_FRAME = register("combat.just_frame");
    public static final RegistryObject<SoundEvent> POSTURE_BREAK = register("combat.posture_break");
    public static final RegistryObject<SoundEvent> SWING_LIGHT = register("combat.swing_light");
    public static final RegistryObject<SoundEvent> SWING_HEAVY = register("combat.swing_heavy");
    public static final RegistryObject<SoundEvent> BODY_THUD = register("combat.body_thud");
    public static final RegistryObject<SoundEvent> WALL_SPLAT = register("combat.wall_splat");
    // Weapons
    public static final RegistryObject<SoundEvent> THROW = register("weapon.throw");
    public static final RegistryObject<SoundEvent> WEAPON_IMPACT = register("weapon.impact");
    public static final RegistryObject<SoundEvent> BLADE_CLASH = register("weapon.clash");
    // Natures
    public static final RegistryObject<SoundEvent> FIRE_ROAR = register("nature.fire_roar");
    public static final RegistryObject<SoundEvent> FIRE_CRACKLE = register("nature.fire_crackle");
    public static final RegistryObject<SoundEvent> FIRE_BURST = register("nature.fire_burst");
    public static final RegistryObject<SoundEvent> WATER_SURGE = register("nature.water_surge");
    public static final RegistryObject<SoundEvent> WATER_SPLASH = register("nature.water_splash");
    public static final RegistryObject<SoundEvent> LIGHTNING_CRACKLE = register("nature.lightning_crackle");
    public static final RegistryObject<SoundEvent> LIGHTNING_SNAP = register("nature.lightning_snap");
    public static final RegistryObject<SoundEvent> WIND_GUST = register("nature.wind_gust");
    public static final RegistryObject<SoundEvent> WIND_WHISTLE = register("nature.wind_whistle");
    public static final RegistryObject<SoundEvent> EARTH_RUMBLE = register("nature.earth_rumble");
    public static final RegistryObject<SoundEvent> EARTH_CRACK = register("nature.earth_crack");
    public static final RegistryObject<SoundEvent> ICE_SHATTER = register("nature.ice_shatter");
    public static final RegistryObject<SoundEvent> LAVA_HISS = register("nature.lava_hiss");
    public static final RegistryObject<SoundEvent> BOIL_STEAM = register("nature.boil_steam");
    public static final RegistryObject<SoundEvent> STORM_HUM = register("nature.storm_hum");
    public static final RegistryObject<SoundEvent> EXPLOSION_BOOM = register("nature.explosion_boom");
    public static final RegistryObject<SoundEvent> SCORCH_SIZZLE = register("nature.scorch_sizzle");
    public static final RegistryObject<SoundEvent> DUST_HUM = register("nature.dust_hum");
    public static final RegistryObject<SoundEvent> MAGNET_HISS = register("nature.magnet_hiss");
    public static final RegistryObject<SoundEvent> WOOD_CREAK = register("nature.wood_creak");
    public static final RegistryObject<SoundEvent> WOOD_BURST = register("nature.wood_burst");
    public static final RegistryObject<SoundEvent> YIN_SWELL = register("nature.yin_swell");
    public static final RegistryObject<SoundEvent> YANG_SWELL = register("nature.yang_swell");
    public static final RegistryObject<SoundEvent> MEDICAL_CHIME = register("nature.medical_chime");
    // Big moments
    public static final RegistryObject<SoundEvent> BIG_IMPACT = register("big.impact");
    public static final RegistryObject<SoundEvent> GENJUTSU = register("big.genjutsu");
    public static final RegistryObject<SoundEvent> CLOAK = register("big.cloak");
    public static final RegistryObject<SoundEvent> SHARINGAN = register("big.sharingan");
    // UI
    public static final RegistryObject<SoundEvent> UI_OPEN = register("ui.open");
    public static final RegistryObject<SoundEvent> UI_CLOSE = register("ui.close");
    public static final RegistryObject<SoundEvent> UI_HOVER = register("ui.hover");
    public static final RegistryObject<SoundEvent> UI_CLICK = register("ui.click");
    public static final RegistryObject<SoundEvent> UI_TAB = register("ui.tab");
    public static final RegistryObject<SoundEvent> RANK_UP = register("ui.rank_up");
    public static final RegistryObject<SoundEvent> MISSION_COMPLETE = register("ui.mission_complete");
    // Ambience (client-side loops, see com.noirm.client.AmbienceLoops)
    public static final RegistryObject<SoundEvent> AMBIENT_VILLAGE = register("ambient.village");
    public static final RegistryObject<SoundEvent> AMBIENT_SANCTUARY = register("ambient.sanctuary");

    /** What a jutsu of some nature is doing: being cast, landing, or held (a sustained sound). */
    public enum Kind { CAST, IMPACT, SUSTAIN }

    private NoirmSounds() {}

    private static RegistryObject<SoundEvent> register(String id) {
        RegistryObject<SoundEvent> sound = SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(Noirm.id(id)));
        ALL.put(id, sound);
        return sound;
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }

    /** Every NOIRM sound by its id ("combat.hit_light"…), in sounds.json order. */
    public static Map<String, RegistryObject<SoundEvent>> all() {
        return Collections.unmodifiableMap(ALL);
    }

    /**
     * The one place that picks a nature's sound. {@code null} (no nature: plain chakra) gives the
     * chakra release.
     */
    public static SoundEvent forNature(Nature nature, Kind kind) {
        return natureSound(nature, kind).get();
    }

    public static RegistryObject<SoundEvent> natureSound(Nature nature, Kind kind) {
        if (nature == null) {
            return kind == Kind.SUSTAIN ? CHAKRA_CHARGE : CHAKRA_RELEASE;
        }
        return switch (nature) {
            case FIRE -> kind == Kind.CAST ? FIRE_ROAR : kind == Kind.IMPACT ? FIRE_BURST : FIRE_CRACKLE;
            case WATER -> kind == Kind.IMPACT ? WATER_SPLASH : WATER_SURGE;
            case WIND -> kind == Kind.CAST ? WIND_WHISTLE : WIND_GUST;
            case EARTH -> kind == Kind.IMPACT ? EARTH_CRACK : EARTH_RUMBLE;
            case LIGHTNING -> kind == Kind.SUSTAIN ? LIGHTNING_CRACKLE : LIGHTNING_SNAP;
            case WOOD -> kind == Kind.SUSTAIN ? WOOD_CREAK : WOOD_BURST;
            case ICE -> ICE_SHATTER;
            case LAVA -> kind == Kind.IMPACT ? FIRE_BURST : LAVA_HISS;
            case STORM -> kind == Kind.IMPACT ? LIGHTNING_SNAP : STORM_HUM;
            case BOIL -> BOIL_STEAM;
            case EXPLOSION -> kind == Kind.SUSTAIN ? LIGHTNING_CRACKLE : EXPLOSION_BOOM;
            case MAGNET -> MAGNET_HISS;
            case SCORCH -> kind == Kind.IMPACT ? FIRE_BURST : SCORCH_SIZZLE;
            case DUST -> DUST_HUM;
            case YIN -> YIN_SWELL;
            case YANG -> YANG_SWELL;
            case YIN_YANG -> kind == Kind.IMPACT ? YIN_SWELL : YANG_SWELL;
        };
    }

    /** Plays a NOIRM sound at an entity for everyone near (server side), with a little pitch life. */
    public static void play(Entity at, RegistryObject<SoundEvent> sound, SoundSource source, float volume, float pitch) {
        Level level = at.level();
        float jitter = 1f + (level.random.nextFloat() - 0.5f) * 0.08f;
        level.playSound(null, at.getX(), at.getY(), at.getZ(), sound.get(), source, volume, pitch * jitter);
    }
}
