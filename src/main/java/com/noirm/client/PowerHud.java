package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.clan.Nature;
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
 * Powers HUD: status lines under the bars (clan, Sharingan, Resolve, locked in / exhausted,
 * cloak) and the jutsu slots bottom-right in combat mode. Eye warnings: {@link EyeEffects}.
 */
public final class PowerHud {
    private static final int X = 6;
    private static final int LINE = 10;
    private static final int RESOLVE_COLOR = 0xFFA45CFF;
    private static final int TAIJUTSU_COLOR = 0xFFE8A060;
    private static final int SPEED_COLOR = 0xFF70D0E0;
    private static final int SLOT_WIDTH = 132;
    private static final int SLOT_HEIGHT = 15;

    private PowerHud() {}

    static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = gui.getMinecraft();
        PowerPackets.SyncPowers powers = ClientPowers.get();
        if (powers == null || minecraft.options.hideGui || minecraft.options.renderDebug || minecraft.player == null
                || minecraft.player.isSpectator()) {
            return;
        }
        Font font = minecraft.font;
        boolean combat = ClientCombat.isCombatMode();
        int y = combat ? 6 + 18 * 3 + 12 : 6 + 18 * 2 + 2;

        graphics.drawString(font, Component.translatable("hud.noirm.clan", powers.clanName()), X, y, powers.canonClan() ? 0xFFE05050 : 0xFFE0B050, true);
        y += LINE;
        if (powers.sharinganStage() > 0) {
            String tomoe = "●".repeat(powers.sharinganStage()) + "○".repeat(3 - powers.sharinganStage());
            Component text = Component.translatable(powers.eyesActive() ? "hud.noirm.sharingan.on" : "hud.noirm.sharingan.off", tomoe);
            graphics.drawString(font, text, X, y, powers.eyesActive() ? 0xFFFF3030 : 0xFF904040, true);
            y += LINE;
        } else if (powers.byakugan()) {
            Component text = Component.translatable(powers.eyesActive() ? "hud.noirm.byakugan.on" : "hud.noirm.byakugan.off");
            graphics.drawString(font, text, X, y, powers.eyesActive() ? 0xFFD8D0FF : 0xFF8080A0, true);
            y += LINE;
        }
        if (combat) {
            graphics.drawString(font, Component.translatable("hud.noirm.resolve"), X, y, RESOLVE_COLOR, true);
            int barX = X + font.width(Component.translatable("hud.noirm.resolve")) + 4;
            graphics.fill(barX - 1, y + 2, barX + 51, y + 7, 0xA0000000);
            graphics.fill(barX, y + 3, barX + Math.round(50 * powers.resolve() / 100f), y + 6, RESOLVE_COLOR);
            y += LINE;
            SyncShinobiDataPacket stats = ClientShinobiData.get();
            if (stats != null) {
                drawStat(graphics, font, y, "hud.noirm.training.taijutsu", stats.taijutsu(), stats.taijutsuProgress(), TAIJUTSU_COLOR);
                y += LINE;
                drawStat(graphics, font, y, "hud.noirm.training.speed", stats.speed(), stats.speedProgress(), SPEED_COLOR);
                y += LINE;
            }
        }
        int sinceSync = ClientPowers.ticksSinceSync();
        int lockedIn = Math.max(0, powers.lockedInTicks() - sinceSync);
        int exhausted = Math.max(0, powers.exhaustionTicks() - sinceSync);
        if (lockedIn > 0) {
            boolean blink = (minecraft.player.tickCount / 4) % 2 == 0;
            graphics.drawString(font, Component.translatable("hud.noirm.locked_in", (lockedIn + 19) / 20), X, y, blink ? 0xFFFFE070 : 0xFFFFB000, true);
            y += LINE;
        } else if (exhausted > 0) {
            graphics.drawString(font, Component.translatable("hud.noirm.exhausted", (exhausted + 19) / 20), X, y, 0xFF909090, true);
            y += LINE;
        }
        if (powers.tails() > 0) {
            int cloakTicks = Math.max(0, powers.cloakTicks() - sinceSync);
            int cooldown = Math.max(0, powers.cloakCooldown() - sinceSync);
            Component text = powers.cloakStage() == 2 ? Component.translatable("hud.noirm.cloak.beast", (cloakTicks + 19) / 20)
                    : powers.cloakStage() == 1 ? Component.translatable("hud.noirm.cloak.on", powers.tails(), (cloakTicks + 19) / 20)
                    : cooldown > 0 ? Component.translatable("hud.noirm.cloak.cooldown", (cooldown + 19) / 20)
                    : Component.translatable("hud.noirm.cloak.ready", powers.tails());
            graphics.drawString(font, text, X, y, powers.cloakStage() > 0 ? 0xFFFF7A20 : 0xFFB08060, true);
        }
        if (combat) {
            drawSlots(graphics, font, powers, width, height);
        }
    }

    /** A trained stat with a thin bar showing progress toward its next point. */
    private static void drawStat(GuiGraphics graphics, Font font, int y, String key, float value, float progress, int color) {
        Component label = Component.translatable(key, Math.round(value));
        graphics.drawString(font, label, X, y, color, true);
        int barX = X + 62;
        graphics.fill(barX - 1, y + 2, barX + 41, y + 7, 0xA0000000);
        graphics.fill(barX, y + 3, barX + Math.round(40 * progress), y + 6, color);
    }

    /** Bottom-right: the four jutsu slots with key, name, cooldown/mastery and a charge bar. */
    private static void drawSlots(GuiGraphics graphics, Font font, PowerPackets.SyncPowers powers, int width, int height) {
        int x = width - SLOT_WIDTH - 4;
        int top = height - 40 - SLOT_HEIGHT * powers.loadout().size();
        for (int slot = 0; slot < powers.loadout().size(); slot++) {
            int y = top + slot * SLOT_HEIGHT;
            String id = powers.loadout().get(slot);
            PowerPackets.JutsuEntry entry = ClientPowers.entry(id);
            graphics.fill(x, y, x + SLOT_WIDTH, y + SLOT_HEIGHT - 1, 0x90000000);
            int color = 0xFFFFFFFF;
            if (entry != null && entry.nature() >= 0) {
                color = 0xFF000000 | Nature.values()[entry.nature()].color();
            }
            Component name = entry == null ? Component.translatable("hud.noirm.jutsu.empty")
                    : Component.translatable(Sharingan.jutsuKey(entry.id()));
            graphics.drawString(font, (slot + 1) + " ", x + 3, y + 3, 0xFFAAAAAA, false);
            graphics.drawString(font, font.plainSubstrByWidth(name.getString(), SLOT_WIDTH - 38), x + 13, y + 3, entry == null ? 0xFF666666 : color, false);
            int cooldown = ClientPowers.cooldown(slot);
            if (entry != null && cooldown > 0) {
                float fraction = Math.min(1f, cooldown / (float) Math.max(1, entry.maxCooldown()));
                graphics.fill(x, y, x + Math.round(SLOT_WIDTH * fraction), y + SLOT_HEIGHT - 1, 0x70000000);
                String seconds = String.format(java.util.Locale.ROOT, "%.1fs", cooldown / 20f);
                graphics.drawString(font, seconds, x + SLOT_WIDTH - font.width(seconds) - 3, y + 3, 0xFFFF6060, false);
            } else if (entry != null) {
                String stars = "★" + entry.mastery();
                graphics.drawString(font, stars, x + SLOT_WIDTH - font.width(stars) - 3, y + 3, 0xFFE0C040, false);
            }
            if (powers.castingSlot() == slot && powers.castingMax() > 0) {
                int ticks = powers.castingTicks() + ClientPowers.ticksSinceSync();
                float progress = Math.min(1f, ticks / (float) powers.castingMax());
                if (!powers.castingIsCharge()) {
                    progress = 1f - progress; // channels drain down
                }
                graphics.fill(x, y + SLOT_HEIGHT - 3, x + Math.round(SLOT_WIDTH * progress), y + SLOT_HEIGHT - 1, 0xFF60D0FF);
            }
        }
        graphics.drawString(font, Component.translatable("hud.noirm.jutsu.hint"), x, top - 10, 0xFF909090, true);
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
