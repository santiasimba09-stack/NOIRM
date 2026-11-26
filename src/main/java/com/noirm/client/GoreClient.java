package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.gore.BloodSplats;
import com.noirm.gore.GoreSettings;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * What the client knows about gore (see {@code com.noirm.gore.Gore}): each fighter's four mark
 * levels packed into one int (a byte each: bruise, dirt, soot, cut), their blood (level and
 * freshness, a byte each) and the parts a dying body has lost. Primitive maps, so reading them
 * while drawing allocates nothing. The player's gore level ({@link GoreSettings}) is applied here:
 * at OFF nothing reads back, at MILD no blood.
 */
public final class GoreClient {
    private static final Int2IntOpenHashMap MARKS = new Int2IntOpenHashMap();
    private static final Int2IntOpenHashMap BLOOD = new Int2IntOpenHashMap();
    private static final Int2IntOpenHashMap SEVERED = new Int2IntOpenHashMap();

    private GoreClient() {}

    public static void receive(int entityId, int marks, int blood, int severed) {
        put(MARKS, entityId, marks);
        put(BLOOD, entityId, (blood & 0xFF) == 0 ? 0 : blood);
        put(SEVERED, entityId, severed);
    }

    private static void put(Int2IntOpenHashMap map, int id, int value) {
        if (value == 0) {
            map.remove(id);
        } else {
            map.put(id, value);
        }
    }

    /** The packed marks of this entity (0 = none). */
    public static int marks(int entityId) {
        return GoreSettings.any() ? MARKS.get(entityId) : 0;
    }

    /** One mark's level (0–1) from packed marks; {@code index} is bruise, dirt, soot, cut. */
    public static float level(int packed, int index) {
        return ((packed >>> (8 * index)) & 0xFF) / 255f;
    }

    /** Blood level (low byte) and freshness (next byte) of this entity; 0 = none, or not shown. */
    public static int blood(int entityId) {
        return GoreSettings.full() ? BLOOD.get(entityId) : 0;
    }

    /** The blood level (0–1) from {@link #blood}. */
    public static float bloodLevel(int blood) {
        return (blood & 0xFF) / 255f;
    }

    /** How fresh (0 dried – 1 wet) the blood is, from {@link #blood}. */
    public static float bloodFresh(int blood) {
        return ((blood >>> 8) & 0xFF) / 255f;
    }

    /** Dried blood's colour as a tint on the bright red textures (r, g, b). */
    static final float DRY_R = 0.42f, DRY_G = 0.29f, DRY_B = 0.27f;

    /** The parts this (dying) body has lost, as {@code SeveredLimb.Part} bits. */
    public static int severed(int entityId) {
        return GoreSettings.any() ? SEVERED.get(entityId) : 0;
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
    public static final class GoreClientEvents {
        private GoreClientEvents() {}

        @SubscribeEvent
        public static void left(EntityLeaveLevelEvent event) {
            if (event.getLevel().isClientSide()) {
                int id = event.getEntity().getId();
                MARKS.remove(id);
                BLOOD.remove(id);
                SEVERED.remove(id);
            }
        }

        @SubscribeEvent
        public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            MARKS.clear();
            BLOOD.clear();
            SEVERED.clear();
            BloodSplats.CLIENT.clear();
        }
    }
}
