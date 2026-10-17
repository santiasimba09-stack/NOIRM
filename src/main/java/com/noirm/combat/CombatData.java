package com.noirm.combat;

import com.noirm.Noirm;
import com.noirm.data.CodecJsonLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * Loads moves and styles from data packs when a world starts and on {@code /reload}, so moves
 * can be tuned by editing JSON without recompiling.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class CombatData {
    /** The style everyone fights with until a master teaches them another. */
    public static final ResourceLocation DEFAULT_STYLE = Noirm.id("basic");

    private static Map<ResourceLocation, CombatMove> moves = Map.of();
    private static Map<ResourceLocation, CombatStyle> styles = Map.of();

    private CombatData() {}

    public static CombatMove move(ResourceLocation id) {
        return moves.get(id);
    }

    public static CombatStyle style(ResourceLocation id) {
        return styles.get(id);
    }

    public static Map<ResourceLocation, CombatStyle> styles() {
        return styles;
    }

    public static int moveCount() {
        return moves.size();
    }

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CodecJsonLoader<>("combat/moves", CombatMove.CODEC, loaded -> moves = loaded));
        event.addListener(new CodecJsonLoader<>("combat/styles", CombatStyle.CODEC, loaded -> styles = loaded));
    }
}
