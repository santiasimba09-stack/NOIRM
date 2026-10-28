package com.noirm.client;

import com.noirm.senjutsu.SagePackets;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/** What the client knows about players' sage state (from {@link SagePackets.Visuals}). */
public final class ClientSage {
    private static final Map<Integer, SagePackets.Visuals> BY_ENTITY = new HashMap<>();

    private ClientSage() {}

    public static void receive(SagePackets.Visuals visuals) {
        if (visuals.flags() == 0 && visuals.petrification() <= 0f) {
            BY_ENTITY.remove(visuals.entityId());
            if (isSelf(visuals.entityId())) {
                BY_ENTITY.put(visuals.entityId(), visuals); // keep the own limit for the HUD
            }
            return;
        }
        if (BY_ENTITY.size() > 256) {
            BY_ENTITY.clear();
        }
        BY_ENTITY.put(visuals.entityId(), visuals);
    }

    private static boolean isSelf(int id) {
        var player = Minecraft.getInstance().player;
        return player != null && player.getId() == id;
    }

    public static SagePackets.Visuals get(int entityId) {
        return BY_ENTITY.get(entityId);
    }

    public static boolean sage(int entityId) {
        SagePackets.Visuals v = BY_ENTITY.get(entityId);
        return v != null && v.sage();
    }

    /** 0–100. */
    public static float petrification(int entityId) {
        SagePackets.Visuals v = BY_ENTITY.get(entityId);
        return v == null ? 0f : v.petrification();
    }

    public static boolean statue(int entityId) {
        SagePackets.Visuals v = BY_ENTITY.get(entityId);
        return v != null && v.statue();
    }

    /** The local player's state, or null if nothing was heard yet. */
    public static SagePackets.Visuals self() {
        var player = Minecraft.getInstance().player;
        return player == null ? null : BY_ENTITY.get(player.getId());
    }
}
