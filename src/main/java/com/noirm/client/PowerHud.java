package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.clan.Nature;
import com.noirm.client.ui.Anim;
import com.noirm.client.ui.HudKit;
import com.noirm.client.ui.HudLayout;
import com.noirm.client.ui.Smooth;
import com.noirm.client.ui.Theme;
import com.noirm.client.ui.Ui;
import com.noirm.dojutsu.Sharingan;
import com.noirm.network.PowerPackets;
import com.noirm.network.SyncShinobiDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Powers on the HUD, kept quiet: under the vitals ({@link ShinobiHudOverlay}) only what matters now
 * (meditation and nature energy, the eyes while active or in combat, Resolve and the trained stats
 * in combat mode, a stat for a moment when it grows, locked in / exhausted, the cloak). The clan
 * shows for a few seconds after joining. Bottom-right in combat mode: the four jutsu slots.
 * Eye warnings: {@link EyeEffects}.
 */
public final class PowerHud {
    private static final int X = HudLayout.MARGIN;
    private static final int LINE = 11;
    private static final int RESOLVE_COLOR = 0xFFA88AD8;
    private static final int[] STAT_COLORS = {0xFFE8A060, 0xFF70D0E0, 0xFFE86060, 0xFF6090FF, 0xFF70E0A0, 0xFFD0B0F0, 0xFFC8A878};
    private static final String[] STAT_KEYS = {"hud.noirm.training.taijutsu", "hud.noirm.training.speed", "hud.noirm.training.vitality",
            "hud.noirm.training.reserves", "hud.noirm.training.control", "hud.noirm.training.willpower", "hud.noirm.training.durability"};
    private static final int STAT_COLUMN = 48;
    private static final int SLOT_ROW = 13;
    private static final int CLAN_SHOW_MS = 6000;
    private static final int HINT_SHOW_MS = 5000;

    private static final Smooth RESOLVE = new Smooth(Anim.BAR_TAU_MS);
    private static final Smooth NATURE = new Smooth(Anim.BAR_TAU_MS);
    private static final Smooth EYES = new Smooth(Anim.BAR_TAU_MS);
    private static final Smooth SIGHT = new Smooth(Anim.BAR_TAU_MS);
    private static final Smooth BOND = new Smooth(Anim.BAR_TAU_MS);
    private static final Smooth[] STAT_VALUE = new Smooth[STAT_KEYS.length];
    private static final Smooth[] STAT_PROGRESS = new Smooth[STAT_KEYS.length];
    private static final float[] STATS = new float[STAT_KEYS.length];
    private static final float[] PROGRESS = new float[STAT_KEYS.length];
    private static final Smooth[] SLOT_CAST = new Smooth[8];
    private static String lastClan = "";
    private static long clanSeenAt = Long.MIN_VALUE / 2;
    private static boolean wasCombat;
    private static long combatSince = Long.MIN_VALUE / 2;

    static {
        for (int i = 0; i < STAT_KEYS.length; i++) {
            STAT_VALUE[i] = new Smooth(Anim.BAR_TAU_MS);
            STAT_PROGRESS[i] = new Smooth(Anim.BAR_TAU_MS);
        }
        for (int i = 0; i < SLOT_CAST.length; i++) {
            SLOT_CAST[i] = new Smooth(40f);
        }
    }

    private PowerHud() {}

    /** The bottom-right slots (their own overlay, so they draw even when the vitals are hidden by a mod). */
    static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = gui.getMinecraft();
        PowerPackets.SyncPowers powers = ClientPowers.get();
        if (powers == null || minecraft.options.hideGui || minecraft.options.renderDebug || minecraft.player == null
                || minecraft.player.isSpectator()) {
            return;
        }
        boolean combat = ClientCombat.isCombatMode();
        if (combat != wasCombat) {
            wasCombat = combat;
            combatSince = Anim.now();
        }
        if (combat) {
            drawSlots(graphics, minecraft.font, powers, width, height);
        }
    }

    /** The status lines under the vitals, from {@code y}. */
    static void drawStatus(GuiGraphics graphics, Font font, Minecraft minecraft, int y, boolean combat) {
        PowerPackets.SyncPowers powers = ClientPowers.get();
        if (powers == null || minecraft.player == null) {
            return;
        }
        y = drawNature(graphics, font, powers, y, minecraft.player.tickCount);

        // The clan: a few seconds after joining (or changing clan), then it stays on the J screen.
        if (!powers.clanName().equals(lastClan)) {
            lastClan = powers.clanName();
            clanSeenAt = Anim.now();
        }
        float clanAlpha = Anim.holdThenFade(clanSeenAt, CLAN_SHOW_MS, 800);
        if (clanAlpha > 0.03f) {
            HudKit.text(graphics, font, Component.translatable("hud.noirm.clan", powers.clanName()), X, y,
                    powers.canonClan() ? Theme.RED : Theme.GOLD, clanAlpha);
            y += LINE;
        }

        if (powers.sharinganStage() > 0) {
            float growth = sharinganProgress(powers);
            EYES.update(Math.max(0f, growth));
            float alpha = powers.eyesActive() || combat ? 1f : EYES.recency();
            if (alpha > 0.03f) {
                String tomoe = "●".repeat(powers.sharinganStage()) + "○".repeat(3 - powers.sharinganStage());
                Component text = Component.translatable(powers.eyesActive() ? "hud.noirm.sharingan.on" : "hud.noirm.sharingan.off", tomoe);
                HudKit.text(graphics, font, text, X, y, powers.eyesActive() ? 0xFFFF3A30 : 0xFFA04848, alpha);
                if (growth >= 0) {
                    int barX = X + font.width(text) + 5;
                    Ui.bar(graphics, barX, y + 3, 30, 2, EYES.value(), EYES.ghost(), Theme.fade(0xFFD02020, alpha), Theme.fade(HudKit.TRACK, alpha));
                }
                y += LINE;
            }
            if (powers.mangekyo()) {
                // The Mangekyō: its kind, and how much sight is left (the strain bar empties toward blindness).
                float sight = 1f - powers.mangekyoStrain() / com.noirm.dojutsu.Mangekyo.MAX_STRAIN;
                SIGHT.update(sight);
                float mAlpha = powers.eyesActive() || combat || powers.blind() ? 1f : SIGHT.recency();
                if (mAlpha > 0.03f) {
                    String key = powers.eternal() ? "hud.noirm.mangekyo.eternal" : powers.blind() ? "hud.noirm.mangekyo.blind" : "hud.noirm.mangekyo";
                    Component ms = Component.translatable(key);
                    HudKit.text(graphics, font, ms, X, y, powers.eternal() ? 0xFFFF6A3A : 0xFFC82028, mAlpha);
                    if (!powers.eternal()) {
                        int barX = X + font.width(ms) + 5;
                        Ui.bar(graphics, barX, y + 3, 40, 2, SIGHT.value(), SIGHT.ghost(),
                                Theme.fade(sight > 0.3f ? Theme.TEXT : 0xFFB02020, mAlpha), Theme.fade(HudKit.TRACK, mAlpha));
                    }
                    y += LINE;
                }
            }
        } else if (powers.byakugan() && (powers.eyesActive() || combat)) {
            Component text = Component.translatable(powers.eyesActive() ? "hud.noirm.byakugan.on" : "hud.noirm.byakugan.off");
            HudKit.text(graphics, font, text, X, y, powers.eyesActive() ? 0xFFD8D0FF : 0xFF8080A0, 1f);
            y += LINE;
        }

        if (combat) {
            RESOLVE.update(powers.resolve() / 100f);
            y = HudKit.barRow(graphics, font, X, y + 1, 92, HudLabels.get("hud.noirm.resolve"), null, 0f, RESOLVE, RESOLVE_COLOR);
        } else {
            RESOLVE.snap(powers.resolve() / 100f);
        }
        y = drawStats(graphics, font, y, combat);

        int sinceSync = ClientPowers.ticksSinceSync();
        int lockedIn = Math.max(0, powers.lockedInTicks() - sinceSync);
        int exhausted = Math.max(0, powers.exhaustionTicks() - sinceSync);
        if (lockedIn > 0) {
            boolean blink = (minecraft.player.tickCount / 4) % 2 == 0;
            HudKit.text(graphics, font, Component.translatable("hud.noirm.locked_in", (lockedIn + 19) / 20), X, y,
                    blink ? 0xFFFFE070 : 0xFFFFB000, 1f);
            y += LINE;
        } else if (exhausted > 0) {
            HudKit.text(graphics, font, Component.translatable("hud.noirm.exhausted", (exhausted + 19) / 20), X, y, Theme.TEXT_DIM, 1f);
            y += LINE;
        }
        if (powers.tails() > 0) {
            int cloakTicks = Math.max(0, powers.cloakTicks() - sinceSync);
            int cooldown = Math.max(0, powers.cloakCooldown() - sinceSync);
            BOND.update(powers.bond());
            boolean busy = powers.cloakStage() > 0 || cooldown > 0;
            if (combat || busy) {
                Component text = powers.cloakStage() == 2 ? Component.translatable("hud.noirm.cloak.beast", (cloakTicks + 19) / 20)
                        : powers.cloakStage() == 1 ? Component.translatable("hud.noirm.cloak.tails", powers.cloakTails(), powers.unlockedTails(), (cloakTicks + 19) / 20)
                        : cooldown > 0 ? Component.translatable("hud.noirm.cloak.cooldown", (cooldown + 19) / 20)
                        : Component.translatable("hud.noirm.cloak.ready", powers.tails());
                HudKit.text(graphics, font, text, X, y, powers.cloakStage() > 0 ? 0xFFFF7A20 : 0xFFB08060, 1f);
                y += LINE;
            }
            float bondAlpha = combat ? 1f : BOND.recency();
            if (bondAlpha > 0.03f) {
                int bondColor = powers.bond() >= 50 ? 0xFFFFC040 : powers.bond() >= 0 ? 0xFFD0A070 : 0xFFD05040;
                HudKit.text(graphics, font, Component.translatable("hud.noirm.cloak.bond", powers.bond(), powers.unlockedTails()), X, y, bondColor, bondAlpha);
            }
        }
    }

    /**
     * Trained stats. In combat mode: all seven in two columns, each with a hairline of progress
     * toward its next point. Otherwise only a stat that just grew, for a moment.
     */
    private static int drawStats(GuiGraphics graphics, Font font, int y, boolean combat) {
        SyncShinobiDataPacket stats = ClientShinobiData.get();
        if (stats == null) {
            return y;
        }
        STATS[0] = stats.taijutsu();
        PROGRESS[0] = stats.taijutsuProgress();
        STATS[1] = stats.speed();
        PROGRESS[1] = stats.speedProgress();
        STATS[2] = stats.vitality();
        PROGRESS[2] = stats.vitalityProgress();
        STATS[3] = stats.reserves();
        PROGRESS[3] = stats.reservesProgress();
        STATS[4] = stats.control();
        PROGRESS[4] = stats.controlProgress();
        STATS[5] = stats.willpower();
        PROGRESS[5] = stats.willpowerProgress();
        STATS[6] = stats.durability();
        PROGRESS[6] = stats.durabilityProgress();
        int column = 0;
        int startY = y;
        for (int i = 0; i < STATS.length; i++) {
            STAT_VALUE[i].update(Math.round(STATS[i]));
            STAT_PROGRESS[i].update(PROGRESS[i]);
            float alpha = combat ? 1f : Math.max(STAT_VALUE[i].recency(), STAT_PROGRESS[i].recency() * 0.9f);
            if (alpha < 0.03f) {
                continue;
            }
            int x = combat ? X + column * STAT_COLUMN : X;
            Component label = Component.translatable(STAT_KEYS[i], Math.round(STATS[i]));
            HudKit.text(graphics, font, label, x, y, STAT_COLORS[i], alpha);
            Ui.bar(graphics, x, y + 9, STAT_COLUMN - 8, 1, STAT_PROGRESS[i].value(), STAT_PROGRESS[i].value(),
                    Theme.fade(STAT_COLORS[i], alpha), Theme.fade(HudKit.TRACK, alpha));
            if (combat) {
                column++;
                if (column == 2) {
                    column = 0;
                    y += 12;
                }
            } else {
                y += 12;
            }
        }
        if (combat && column != 0) {
            y += 12;
        }
        return y == startY ? y : y + 1;
    }

    /**
     * Meditation: a green nature-energy bar (shown while meditating or while any energy is left)
     * and a "Meditating…" line. Returns the y below what it drew.
     */
    private static int drawNature(GuiGraphics graphics, Font font, PowerPackets.SyncPowers powers, int y, int tick) {
        float energy = powers.natureEnergy();
        float max = com.noirm.senjutsu.Meditation.NATURE_MAX;
        NATURE.update(Math.min(1f, energy / max));
        if (!powers.meditating() && energy < 0.5f && !SageHud.wantsShowing()) {
            return y;
        }
        boolean full = energy >= max - 0.01f;
        int color = full && (tick / 10) % 2 == 0 ? 0xFFB8F070 : Theme.NATURE;
        String value = Math.round(energy) + "/" + Math.round(max);
        int top = y;
        y = HudKit.barRow(graphics, font, X, y, 92, HudLabels.get("hud.noirm.ui.nature"), value,
                powers.meditating() ? 1f : Math.max(0.5f, NATURE.recency()), NATURE, color);
        SageHud.decorateBar(graphics, X, top + font.lineHeight + 1, 92, energy); // the safe limit and the excess
        y = SageHud.draw(graphics, font, X, y, tick); // petrification, Sage Mode
        if (powers.meditating()) {
            String dots = ".".repeat(1 + (tick / 15) % 3);
            HudKit.text(graphics, font, Component.translatable("hud.noirm.meditating", dots), X, y, 0xFFA8E890, 1f);
            y += LINE;
        }
        return y + 2;
    }

    /** Progress (0..1) of the Sharingan toward its next tomoe, or -1 if not awakened or already at 3. */
    static float sharinganProgress(PowerPackets.SyncPowers powers) {
        int stage = powers.sharinganStage();
        if (stage <= 0 || powers.sharinganNextXp() <= 0) {
            return -1f;
        }
        int from = Sharingan.STAGE_XP[stage];
        return Math.min(1f, Math.max(0f, (powers.sharinganXp() - from) / (float) Math.max(1, powers.sharinganNextXp() - from)));
    }

    /**
     * Bottom-right, clear of the hotbar and the hunger/air rows: the jutsu slots, one quiet row each
     * (key, name in its nature colour, cooldown or mastery on the right; a hairline for the cooldown
     * and the cast). The width fits the screen, so nothing runs off the right edge.
     */
    private static void drawSlots(GuiGraphics graphics, Font font, PowerPackets.SyncPowers powers, int width, int height) {
        int right = width - HudLayout.MARGIN;
        int w = Math.max(96, Math.min(150, right - HudLayout.bottomRightMinX(width)));
        int x = right - w;
        int count = powers.loadout().size();
        int top = height - HudLayout.MARGIN - SLOT_ROW * count;
        // Above the chat (which draws at z 50), so recent chat lines never cover the slots.
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, 100f);
        for (int slot = 0; slot < count; slot++) {
            int y = top + slot * SLOT_ROW;
            String id = powers.loadout().get(slot);
            PowerPackets.JutsuEntry entry = ClientPowers.entry(id);
            Ui.roundRect(graphics, x, y, w, SLOT_ROW - 1, 0x78000000);
            int color = Theme.TEXT;
            if (entry != null && entry.nature() >= 0) {
                color = 0xFF000000 | Nature.values()[entry.nature()].color();
            }
            int cooldown = ClientPowers.cooldown(slot);
            boolean cooling = entry != null && cooldown > 0;
            String key = String.valueOf(slot + 1);
            graphics.drawString(font, key, x + 4, y + 2, cooling ? Theme.TEXT_FAINT : Theme.TEXT_DIM, false);
            String side;
            int sideColor;
            if (cooling) {
                side = String.format(java.util.Locale.ROOT, "%.1fs", cooldown / 20f);
                sideColor = Theme.RED;
            } else if (entry != null) {
                side = "★" + entry.mastery();
                sideColor = Theme.GOLD;
            } else {
                side = "";
                sideColor = Theme.TEXT_FAINT;
            }
            int sideW = font.width(side);
            int nameX = x + 4 + font.width("4") + 5;
            Component name = entry == null ? Component.translatable("hud.noirm.jutsu.empty")
                    : Component.translatable(Sharingan.jutsuKey(entry.id()));
            String shown = Ui.fit(font, name.getString(), x + w - 4 - sideW - 4 - nameX);
            graphics.drawString(font, shown, nameX, y + 2, entry == null ? Theme.TEXT_FAINT : cooling ? Theme.mix(color, 0xFF000000, 0.45f) : color, false);
            graphics.drawString(font, side, x + w - 4 - sideW, y + 2, sideColor, false);
            if (cooling) {
                float fraction = Math.min(1f, cooldown / (float) Math.max(1, entry.maxCooldown()));
                graphics.fill(x + 1, y + SLOT_ROW - 2, x + 1 + Math.round((w - 2) * fraction), y + SLOT_ROW - 1, Theme.fade(Theme.RED, 0.7f));
            }
            if (slot >= SLOT_CAST.length) {
                continue;
            }
            if (powers.castingSlot() == slot && powers.castingMax() > 0) {
                int ticks = powers.castingTicks() + ClientPowers.ticksSinceSync();
                float progress = Math.min(1f, ticks / (float) powers.castingMax());
                if (!powers.castingIsCharge()) {
                    progress = 1f - progress; // channels drain down
                }
                float shownProgress = SLOT_CAST[slot].update(progress);
                graphics.fill(x + 1, y + SLOT_ROW - 3, x + 1 + Math.round((w - 2) * shownProgress), y + SLOT_ROW - 1, Theme.CHAKRA_LIGHT);
            } else {
                SLOT_CAST[slot].snap(0f);
            }
        }
        // The hint, for a few seconds after entering combat mode.
        float hint = Anim.holdThenFade(combatSince, HINT_SHOW_MS, 600);
        if (hint > 0.03f) {
            String text = Ui.fit(font, Component.translatable("hud.noirm.ui.jutsu_hint").getString(), w);
            HudKit.text(graphics, font, text, right - font.width(text), top - 11, Theme.TEXT_DIM, hint);
        }
        graphics.pose().popPose();
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void registerOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("powers", PowerHud::render);
        }
    }
}
