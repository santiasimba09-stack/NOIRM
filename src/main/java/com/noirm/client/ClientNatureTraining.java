package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.clan.Nature;
import com.noirm.nature.NatureDrills;
import com.noirm.nature.NaturePackets;
import com.noirm.network.JutsuCatalogPacket;
import com.noirm.network.PowerPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The client's copy of the local player's hidden natures and nature drills: the drill bar under
 * the crosshair while meditating, and the "how to learn" lines of the jutsu screen.
 */
public final class ClientNatureTraining {
    private static NaturePackets.Sync state = new NaturePackets.Sync(0, false, -1, 0f, 0, new float[0]);

    private ClientNatureTraining() {}

    public static void sync(NaturePackets.Sync packet) {
        state = packet;
    }

    public static NaturePackets.Sync get() {
        return state;
    }

    /** Whether a chakra paper has shown this nature as hidden. */
    public static boolean isHidden(Nature nature) {
        return (state.hiddenMask() & (1 << nature.ordinal())) != 0;
    }

    /** The drill's progress, 0..1 (0 for natures without a drill). */
    public static float share(Nature nature) {
        int i = NatureDrills.TRAINABLE.indexOf(nature);
        return i >= 0 && i < state.shares().length ? state.shares()[i] : 0f;
    }

    // ---------------------------------------------------------------- the jutsu screen

    /** Lines for a basic nature you don't have, in the nature tree: the drill, its progress and the other routes. */
    public static List<Component> drillLines(Nature nature) {
        List<Component> lines = new ArrayList<>();
        if (!NatureDrills.TRAINABLE.contains(nature)) {
            lines.add(Component.translatable("screen.noirm.tree.basic_locked"));
            return lines;
        }
        lines.add(Component.translatable("screen.noirm.tree.drill", Component.translatable("drill.noirm." + nature.getSerializedName())));
        lines.add(Component.translatable("screen.noirm.tree.drill_progress", Math.round(share(nature) * 100)));
        if (isHidden(nature)) {
            lines.add(Component.translatable("screen.noirm.tree.drill_hidden"));
        } else if (!state.revealed()) {
            lines.add(Component.translatable("screen.noirm.tree.drill_paper"));
        }
        lines.add(Component.translatable("screen.noirm.tree.drill_more"));
        return lines;
    }

    /** "Or: a C-rank jutsu scroll" for a jutsu a scroll can teach; null for ones scrolls never hold. */
    public static Component scrollHint(JutsuCatalogPacket.Entry info) {
        String path = info.id().getPath();
        if (!info.requires().isEmpty() || path.startsWith("taijutsu/") || path.startsWith("mangekyo/") || path.startsWith("biju/")
                || path.startsWith("byakugan/") || path.startsWith("sharingan/") || (info.nature() < 0 && !path.startsWith("universal/"))) {
            return null;
        }
        return Component.translatable("screen.noirm.jutsu.or_scroll", info.rank().toUpperCase(Locale.ROOT));
    }

    // ---------------------------------------------------------------- HUD

    private static final int BAR_W = 120;

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        PowerPackets.SyncPowers powers = ClientPowers.get();
        if (mc.options.hideGui || mc.player == null || powers == null || !powers.meditating()) {
            return;
        }
        Font font = mc.font;
        int cx = width / 2;
        int y = height / 2 + 24;
        Nature active = state.active() >= 0 && state.active() < Nature.values().length ? Nature.values()[state.active()] : null;
        if (active == null || state.pace() <= 0f) {
            boolean anyLeft = NatureDrills.TRAINABLE.stream().anyMatch(n -> !ownedNature(powers, n));
            if (anyLeft) {
                g.drawCenteredString(font, Component.translatable("hud.noirm.drill.none"), cx, y, 0xFF8A8A90);
            }
            return;
        }
        int color = 0xFF000000 | active.color();
        MutableComponent title = Component.translatable("hud.noirm.drill.title", Component.translatable(active.translationKey()),
                Math.round(share(active) * 100));
        g.drawCenteredString(font, title, cx, y, color);
        int barY = y + 11;
        g.fill(cx - BAR_W / 2 - 1, barY - 1, cx + BAR_W / 2 + 1, barY + 5, 0xC0000000);
        g.fill(cx - BAR_W / 2, barY, cx - BAR_W / 2 + Math.round(BAR_W * Math.min(1f, share(active))), barY + 4, color);
        // A moving glint so the bar reads as "working".
        int glint = (int) ((mc.level == null ? 0 : mc.level.getGameTime()) % 40) * BAR_W / 40;
        if (glint < BAR_W * share(active)) {
            g.fill(cx - BAR_W / 2 + glint, barY, cx - BAR_W / 2 + glint + 2, barY + 4, 0xC0FFFFFF);
        }
        MutableComponent pace = Component.translatable("hud.noirm.drill.pace", String.format(Locale.ROOT, "%.1f", state.pace()));
        if (isHidden(active)) {
            pace.append(" ").append(Component.translatable("hud.noirm.drill.hidden"));
        }
        if (state.teacher() > 0) {
            pace.append(" ").append(Component.translatable(state.teacher() == 2 ? "hud.noirm.drill.master" : "hud.noirm.drill.crew"));
        }
        g.drawCenteredString(font, pace, cx, barY + 8, 0xFFB8C8D8);
    }

    private static boolean ownedNature(PowerPackets.SyncPowers powers, Nature nature) {
        return powers.natures().contains(nature.ordinal());
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class NatureDrillHudRegistration {
        private NatureDrillHudRegistration() {}

        @SubscribeEvent
        public static void registerNatureDrillOverlay(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("nature_drill", ClientNatureTraining::render);
        }
    }
}
