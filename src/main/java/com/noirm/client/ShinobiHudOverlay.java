package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.client.ui.Anim;
import com.noirm.client.ui.HudKit;
import com.noirm.client.ui.HudLayout;
import com.noirm.client.ui.Smooth;
import com.noirm.client.ui.Theme;
import com.noirm.client.ui.Ui;
import com.noirm.network.SyncShinobiDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The top-left corner of the HUD: thin chakra and stamina bars (and posture in combat mode) with
 * small labels; their numbers show when they change, then fade. Under them, the status lines from
 * {@link PowerHud#drawStatus}. In combat mode, the enemy you're fighting at the top centre (below
 * any boss bar). Bars ease toward their values and leave a light trail when they drop.
 */
public final class ShinobiHudOverlay {
    private static final int X = HudLayout.MARGIN;
    private static final int Y = HudLayout.MARGIN;
    private static final int BAR_WIDTH = 92;
    private static final int STAMINA_COLOR = 0xFF5CCB6A;
    private static final int TARGET_BAR_WIDTH = 128;

    private static final Smooth CHAKRA = new Smooth(Anim.BAR_TAU_MS);
    private static final Smooth STAMINA = new Smooth(Anim.BAR_TAU_MS);
    private static final Smooth POSTURE = new Smooth(Anim.BAR_TAU_MS);
    private static final Smooth TARGET_HP = new Smooth(Anim.BAR_TAU_MS);
    private static final Smooth TARGET_POSTURE = new Smooth(Anim.BAR_TAU_MS);
    private static int targetId = Integer.MIN_VALUE;

    private ShinobiHudOverlay() {}

    static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = gui.getMinecraft();
        SyncShinobiDataPacket data = ClientShinobiData.get();
        // Hidden with F1 (hideGui), with F3 (it would overlap the debug text), and in spectator mode.
        if (data == null || minecraft.options.hideGui || minecraft.options.renderDebug
                || minecraft.player == null || minecraft.player.isSpectator()) {
            return;
        }
        Font font = minecraft.font;
        boolean combat = ClientCombat.isCombatMode();
        int y = Y;
        CHAKRA.update(fraction(data.chakra(), data.maxChakra()));
        y = HudKit.barRow(graphics, font, X, y, BAR_WIDTH, HudLabels.get("hud.noirm.ui.chakra"),
                numbers(CHAKRA, data.chakra(), data.maxChakra(), combat), valueAlpha(CHAKRA, combat), CHAKRA, Theme.CHAKRA);
        boolean flash = ClientCombat.staminaFlashing() && (minecraft.player.tickCount / 2) % 2 == 0;
        STAMINA.update(fraction(data.stamina(), data.maxStamina()));
        y = HudKit.barRow(graphics, font, X, y, BAR_WIDTH, HudLabels.get("hud.noirm.ui.stamina"),
                numbers(STAMINA, data.stamina(), data.maxStamina(), combat), valueAlpha(STAMINA, combat), STAMINA,
                flash ? Theme.RED : STAMINA_COLOR);
        if (combat) {
            POSTURE.update(fraction(ClientCombat.posture(), ClientCombat.maxPosture()));
            y = HudKit.barRow(graphics, font, X, y, BAR_WIDTH, HudLabels.get("hud.noirm.ui.posture"),
                    numbers(POSTURE, ClientCombat.posture(), ClientCombat.maxPosture(), true), valueAlpha(POSTURE, true), POSTURE, Theme.AMBER);
            Component state = Component.translatable(ClientCombat.isGuarding() ? "hud.noirm.guarding" : "hud.noirm.combat_mode");
            HudKit.text(graphics, font, state, X, y, ClientCombat.isGuarding() ? Theme.TEXT : Theme.GOLD, 1f);
            y += Theme.LINE + 2;
            drawTarget(graphics, font, minecraft, screenWidth);
        } else {
            POSTURE.snap(fraction(ClientCombat.posture(), ClientCombat.maxPosture()));
        }
        PowerHud.drawStatus(graphics, font, minecraft, y + 2, combat);
    }

    private static float fraction(float value, float max) {
        return max > 0 ? Math.min(1f, Math.max(0f, value / max)) : 0f;
    }

    /** The number shows while it changes (and always in combat mode, dimmer). */
    private static float valueAlpha(Smooth smooth, boolean combat) {
        float recent = smooth.recency();
        return combat ? Math.max(0.55f, recent) : recent;
    }

    private static String numbers(Smooth smooth, float value, float max, boolean combat) {
        if (!combat && smooth.recency() < 0.03f) {
            return null;
        }
        return Math.round(value) + "/" + Math.round(max);
    }

    /** Top centre (below boss bars): the locked-on enemy, or the last one you hit, with its posture. */
    private static void drawTarget(GuiGraphics graphics, Font font, Minecraft minecraft, int screenWidth) {
        LivingEntity target = LockOn.target();
        boolean locked = target != null;
        if (target == null && ClientCombat.lastTargetId() != -1 && minecraft.level != null
                && minecraft.level.getEntity(ClientCombat.lastTargetId()) instanceof LivingEntity hit && hit.isAlive()) {
            target = hit;
        }
        if (target == null) {
            targetId = Integer.MIN_VALUE;
            return;
        }
        float hp = Math.min(target.getHealth() / target.getMaxHealth(), 1f);
        float posture = ClientCombat.lastTargetMaxPosture() > 0
                ? Math.min(ClientCombat.lastTargetPosture() / ClientCombat.lastTargetMaxPosture(), 1f) : 0f;
        if (target.getId() != targetId) {
            targetId = target.getId();
            TARGET_HP.snap(hp);
            TARGET_POSTURE.snap(posture);
        }
        TARGET_HP.update(hp);
        TARGET_POSTURE.update(posture);
        Component name = locked ? Component.translatable("hud.noirm.locked_on", target.getDisplayName()) : target.getDisplayName();
        name = Ui.fit(font, name, TARGET_BAR_WIDTH + 40);
        int x = (screenWidth - TARGET_BAR_WIDTH) / 2;
        int y = HudLayout.topCenter();
        graphics.drawString(font, name, screenWidth / 2 - font.width(name) / 2, y, locked ? Theme.TEXT : Theme.TEXT_DIM, true);
        int barY = y + font.lineHeight + 1;
        Ui.bar(graphics, x, barY, TARGET_BAR_WIDTH, 3, TARGET_HP.value(), TARGET_HP.ghost(), Theme.HEALTH, HudKit.TRACK);
        if (target.getId() == ClientCombat.lastTargetId() && ClientCombat.lastTargetMaxPosture() > 0) {
            Ui.bar(graphics, x, barY + 4, TARGET_BAR_WIDTH, 1, TARGET_POSTURE.value(), TARGET_POSTURE.ghost(), Theme.AMBER, HudKit.TRACK);
        }
    }

    /** Registers the overlay while the game loads. */
    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void registerOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("shinobi_bars", ShinobiHudOverlay::render);
        }
    }

    /** Clears the bars when leaving a world, so stale values never show on the next one. */
    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {}

        @SubscribeEvent
        public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientShinobiData.reset();
        }
    }
}
