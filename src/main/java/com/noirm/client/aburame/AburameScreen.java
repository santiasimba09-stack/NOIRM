package com.noirm.client.aburame;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import com.noirm.aburame.Species;
import com.noirm.client.ui.HoverTracker;
import com.noirm.client.ui.NoirmStyled;
import com.noirm.client.ui.ScreenMotion;
import com.noirm.client.ui.Theme;
import com.noirm.client.ui.Ui;
import com.noirm.client.ui.UiSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** What the colony panel, the Insect Case and the Breeding Jar share: the NOIRM panel, buttons, a turning beetle. */
abstract class AburameScreen extends Screen implements NoirmStyled {
    protected static final int PAD = Theme.S5;
    protected final ScreenMotion motion = new ScreenMotion();
    protected final HoverTracker hover = new HoverTracker();
    protected final List<Hit> hits = new ArrayList<>();
    protected int left, top, w, h;
    private final int maxW, maxH;

    protected record Hit(int x, int y, int w, int h, String action, Component tip) {}

    protected AburameScreen(Component title, int maxW, int maxH) {
        super(title);
        this.maxW = maxW;
        this.maxH = maxH;
    }

    @Override
    protected void init() {
        w = Math.min(maxW, width - 16);
        h = Math.min(maxH, height - 8);
        left = (width - w) / 2;
        top = (height - h) / 2;
        if (!motion.started()) {
            motion.open();
        }
    }

    @Override
    public void tick() {
        motion.tick();
    }

    @Override
    public void onClose() {
        motion.close(super::onClose);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        hits.clear();
        motion.scrim(g, width, height);
        motion.begin(g);
        Ui.panel(g, left, top, w, h);
        Component tip = drawContent(g, mouseX, mouseY, partial);
        motion.end(g);
        super.render(g, mouseX, mouseY, partial);
        for (Hit hit : hits) {
            if (hit.tip() != null && Ui.in(mouseX, mouseY, hit.x(), hit.y(), hit.w(), hit.h())) {
                tip = hit.tip();
            }
        }
        if (tip != null) {
            g.renderTooltip(font, font.split(tip, 220), mouseX, mouseY);
        }
    }

    /** Draws the screen's content; returns a tooltip to show, or null. */
    protected abstract Component drawContent(GuiGraphics g, int mouseX, int mouseY, float partial);

    /** Clicked an action. */
    protected abstract void act(String action);

    protected void button(GuiGraphics g, int x, int y, int bw, Component label, String action, boolean enabled, boolean primary, int mx, int my,
                          Component tip) {
        float hv = hover.get(HoverTracker.key(x, y), mx, my, x, y, bw, 14);
        Ui.button(g, font, x, y, bw, 14, label, enabled ? hv : 0f, enabled, ColonyHud.TEAL, primary);
        hits.add(new Hit(x, y, bw, 14, enabled ? action : null, tip));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Hit hit : hits) {
            if (hit.action() != null && Ui.in(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                UiSounds.click();
                act(hit.action());
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /** A slowly turning close-up beetle (the kikaichū model), centred at (cx, cy), {@code size} pixels long. */
    protected void beetle(GuiGraphics g, int cx, int cy, float size, Species species, float partial) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        float t = (mc.player == null ? 0 : mc.player.tickCount) + partial;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 150);
        g.pose().scale(size, -size, size);
        g.pose().mulPose(Axis.XP.rotationDegrees(32));
        g.pose().mulPose(Axis.YP.rotationDegrees(t * 1.2f));
        Lighting.setupForEntityInInventory();
        BeetleMesh mesh = new BeetleMesh(g.pose(), g.bufferSource().getBuffer(RenderType.entityCutoutNoCull(BeetleMesh.texture(species))),
                LightTexture.FULL_BRIGHT);
        mesh.beetle(0, 0, 0, 0, 0, 1, 1f, ((int) t / 3) % 5 == 0);
        g.flush();
        Lighting.setupFor3DItems();
        g.pose().popPose();
    }
}
