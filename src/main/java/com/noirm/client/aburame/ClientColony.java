package com.noirm.client.aburame;

import com.noirm.aburame.ColonyPackets;
import com.noirm.client.ClientPowers;
import com.noirm.network.PowerPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** The client's view of your colony (from {@code SyncPowers}) and the screens the items and blocks open. */
public final class ClientColony {
    private ClientColony() {}

    public static ColonyPackets.ColonySync sync() {
        PowerPackets.SyncPowers powers = ClientPowers.get();
        return powers == null || powers.colony() == null ? ColonyPackets.ColonySync.NONE : powers.colony();
    }

    public static boolean has() {
        return sync().has();
    }

    public static void openCase() {
        Minecraft.getInstance().setScreen(new InsectCaseScreen());
    }

    public static void openJar(BlockPos pos) {
        Minecraft.getInstance().setScreen(new BreedingJarScreen(pos));
    }

    public static void openPanel() {
        Minecraft.getInstance().setScreen(new ColonyScreen());
    }
}
