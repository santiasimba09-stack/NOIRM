package com.noirm.client;

import com.noirm.Noirm;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * What the client knows about gore (see {@code com.noirm.gore.Gore}): each fighter's four mark
 * levels packed into one int (a byte each: bruise, dirt, soot, cut) and the parts a dying body
 * has lost. Primitive maps, so reading them while drawing allocates nothing.
 */
public final class GoreClient {
    private static final Int2IntOpenHashMap MARKS = new Int2IntOpenHashMap();
    private static final Int2IntOpenHashMap SEVERED = new Int2IntOpenHashMap();

    private GoreClient() {}

    public static void receive(int entityId, int marks, int severed) {
        if (marks == 0) {
            MARKS.remove(entityId);
        } else {
            MARKS.put(entityId, marks);
        }
        if (severed == 0) {
            SEVERED.remove(entityId);
        } else {
            SEVERED.put(entityId, severed);
        }
    }

    /** The packed marks of this entity (0 = none). */
    public static int marks(int entityId) {
        return MARKS.get(entityId);
    }

    /** One mark's level (0–1) from packed marks; {@code index} is bruise, dirt, soot, cut. */
    public static float level(int packed, int index) {
        return ((packed >>> (8 * index)) & 0xFF) / 255f;
    }

    /** The parts this (dying) body has lost, as {@code SeveredLimb.Part} bits. */
    public static int severed(int entityId) {
        return SEVERED.get(entityId);
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
    public static final class GoreClientEvents {
        private GoreClientEvents() {}

        @SubscribeEvent
        public static void left(EntityLeaveLevelEvent event) {
            if (event.getLevel().isClientSide()) {
                int id = event.getEntity().getId();
                MARKS.remove(id);
                SEVERED.remove(id);
            }
        }

        @SubscribeEvent
        public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            MARKS.clear();
            SEVERED.clear();
        }
    }
}
