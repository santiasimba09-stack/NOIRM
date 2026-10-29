package com.noirm.gore;

import com.noirm.Noirm;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * The player's own gore setting, in {@code config/noirm-client.toml} ({@code goreLevel}): how much
 * of the gore the server sends is shown on this screen. The world's {@code noirmGore} rule
 * (and {@code /noirm world gore on|off}) is the master switch; this only turns it down.
 * <ul>
 *   <li>{@code OFF}: nothing (no marks, no blood, no severed limbs drawn).</li>
 *   <li>{@code MILD}: bruises, dirt, soot and cuts; severed limbs; a few blood drops, no stains.</li>
 *   <li>{@code FULL} (default): everything, with blood on bodies, clothes and the ground.</li>
 * </ul>
 * Forge reloads the file when it changes, even while the game runs.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GoreSettings {
    public enum Level { OFF, MILD, FULL }

    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.EnumValue<Level> LEVEL;
    /** The level as a plain field, so drawing code reads it for free. */
    private static volatile Level level = Level.FULL;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        LEVEL = builder.comment("How much gore you see: OFF, MILD (marks and limbs, little blood) or FULL (blood on bodies, clothes and the ground).",
                        "The world's noirmGore game rule still decides whether there is any gore at all.")
                .defineEnum("goreLevel", Level.FULL);
        SPEC = builder.build();
    }

    private GoreSettings() {}

    /** Called once while the mod loads. */
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC, "noirm-client.toml");
    }

    public static Level level() {
        return level;
    }

    /** Anything at all (marks, limbs, drops). */
    public static boolean any() {
        return level != Level.OFF;
    }

    /** Blood stains on bodies and clothes, and blood on the ground. */
    public static boolean full() {
        return level == Level.FULL;
    }

    /** Sets the level for this session (the file is unchanged); for tests. */
    public static void set(Level value) {
        level = value;
    }

    @SubscribeEvent
    public static void loaded(ModConfigEvent.Loading event) {
        read(event.getConfig());
    }

    @SubscribeEvent
    public static void reloaded(ModConfigEvent.Reloading event) {
        read(event.getConfig());
    }

    private static void read(ModConfig config) {
        if (config.getSpec() == SPEC) {
            level = LEVEL.get();
        }
    }
}
