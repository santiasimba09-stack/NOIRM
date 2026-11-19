package com.noirm.client.hound;

import com.noirm.client.ClientPowers;
import com.noirm.hound.HoundPackets;
import com.noirm.hound.NinjaHound;
import com.noirm.network.PowerPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/** The local player's hound as the client knows it: the record from the server and the live entity, if it's in view. */
public final class ClientHound {
    private static NinjaHound cached;
    private static int cachedAt = -1;

    private ClientHound() {}

    /** The record (stage, bond, XP…) from the last power sync, or {@link HoundPackets.HoundSync#NONE}. */
    public static HoundPackets.HoundSync sync() {
        PowerPackets.SyncPowers powers = ClientPowers.get();
        return powers == null || powers.hound() == null ? HoundPackets.HoundSync.NONE : powers.hound();
    }

    /** Your hound's entity if it's loaded near you (looked up at most once a tick). */
    public static NinjaHound entity() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return null;
        }
        int now = mc.player.tickCount;
        if (now == cachedAt && (cached == null || !cached.isRemoved())) {
            return cached;
        }
        cachedAt = now;
        cached = null;
        HoundPackets.HoundSync sync = sync();
        if (!sync.has()) {
            return null;
        }
        UUID me = mc.player.getUUID();
        UUID id = sync.entity();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof NinjaHound h && !h.isRemoved() && (e.getUUID().equals(id) || me.equals(h.getOwnerUUID()))) {
                cached = h;
                if (e.getUUID().equals(id)) {
                    break;
                }
            }
        }
        return cached;
    }
}
