package com.noirm.master;

import com.noirm.Noirm;
import com.noirm.data.CodecJsonLoader;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/** All masters, loaded from {@code data/<ns>/masters/*.json}. */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class MasterData {
    public static final ResourceLocation MIGHT_GUY = Noirm.id("might_guy");
    /** The ninjutsu master (in every capital's Academy) and the medical ninja (in its hospital). */
    public static final ResourceLocation SCHOLAR = Noirm.id("scholar_rensai");
    public static final ResourceLocation MEDIC = Noirm.id("medic_tsubaki");

    private static Map<ResourceLocation, MasterDefinition> masters = Map.of();

    private MasterData() {}

    public static MasterDefinition get(ResourceLocation id) {
        return id == null ? null : masters.get(id);
    }

    public static Map<ResourceLocation, MasterDefinition> all() {
        return masters;
    }

    /** Whether some master teaches this jutsu (those can't be passed on to allies). */
    public static boolean taughtByMaster(JutsuDefinition def) {
        for (MasterDefinition master : masters.values()) {
            for (MasterDefinition.Lesson lesson : master.lessons()) {
                if (JutsuData.get(lesson.jutsu()) == def) {
                    return true;
                }
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CodecJsonLoader<>("masters", MasterDefinition.CODEC, loaded -> masters = loaded));
    }
}
