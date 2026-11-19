package com.noirm.client;

import com.noirm.arts.ArtsSync;
import com.noirm.clan.Nature;
import com.noirm.network.PowerPackets;
import com.noirm.network.SyncShinobiDataPacket;
import com.noirm.workshop.JutsuDesign;
import com.noirm.workshop.Workshop;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Shared bits of the J screen's four tabs: Jutsu, Nature Tree (both in {@link JutsuScreen}),
 * Training ({@link TrainingScreen}) and Workshop ({@link WorkshopScreen}), and the client's view of
 * the workshop's rules (the same {@link Workshop.Limits} the server checks, from the synced data).
 */
public final class ArtsScreens {
    public static final int TAB_JUTSU = 0;
    public static final int TAB_TREE = 1;
    public static final int TAB_TRAINING = 2;
    public static final int TAB_WORKSHOP = 3;

    private ArtsScreens() {}

    /** The four tab labels. */
    public static String[] tabLabels() {
        return new String[]{Component.translatable("screen.noirm.jutsu.tab.jutsu").getString(),
                Component.translatable("screen.noirm.jutsu.tab.tree").getString(),
                Component.translatable("screen.noirm.jutsu.tab.training").getString(),
                Component.translatable("screen.noirm.jutsu.tab.workshop").getString()};
    }

    /** Opens the screen for a tab. */
    public static void switchTo(int tab) {
        Minecraft mc = Minecraft.getInstance();
        switch (tab) {
            case TAB_TRAINING -> mc.setScreen(new TrainingScreen());
            case TAB_WORKSHOP -> mc.setScreen(new WorkshopScreen());
            default -> mc.setScreen(new JutsuScreen(tab));
        }
    }

    /** From the server (commands): 0 = the workshop, 1 = the training screen. */
    public static void open(int screen) {
        switchTo(screen == 1 ? TAB_TRAINING : TAB_WORKSHOP);
    }

    public static ArtsSync arts() {
        PowerPackets.SyncPowers powers = ClientPowers.get();
        return powers == null || powers.arts() == null ? ArtsSync.EMPTY : powers.arts();
    }

    public static Map<Nature, Integer> bestMastery(PowerPackets.SyncPowers powers) {
        Map<Nature, Integer> best = new EnumMap<>(Nature.class);
        if (powers != null) {
            for (PowerPackets.JutsuEntry entry : powers.learned()) {
                if (entry.nature() >= 0) {
                    best.merge(Nature.values()[entry.nature()], entry.mastery(), Math::max);
                }
            }
        }
        return best;
    }

    public static boolean owns(PowerPackets.SyncPowers powers, Nature nature) {
        return powers != null && powers.natures().contains(nature.ordinal());
    }

    /** The workshop's limits as the client sees them. */
    public static Workshop.Limits limits() {
        PowerPackets.SyncPowers powers = ClientPowers.get();
        SyncShinobiDataPacket stats = ClientShinobiData.get();
        ArtsSync arts = arts();
        Map<Nature, Integer> best = bestMastery(powers);
        float reserves = stats == null ? 10f : stats.reserves();
        float control = stats == null ? 10f : stats.control();
        int own = 0;
        java.util.UUID me = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
        if (powers != null) {
            for (JutsuDesign d : powers.creations()) {
                if (me != null && me.equals(d.author())) {
                    own++;
                }
            }
        }
        return new Workshop.Limits(n -> owns(powers, n), arts::stage, arts::fused,
                n -> Workshop.budget(reserves, control, best.getOrDefault(n, 0)), own);
    }
}
