package com.noirm.client;

import com.noirm.clan.ClanTrait;
import com.noirm.clan.Nature;
import com.noirm.client.ui.HoverTracker;
import com.noirm.client.ui.NoirmStyled;
import com.noirm.client.ui.ScreenMotion;
import com.noirm.client.ui.TabStrip;
import com.noirm.client.ui.Theme;
import com.noirm.client.ui.Ui;
import com.noirm.creator.CreatorData;
import com.noirm.creator.CreatorPackets;
import com.noirm.traits.Perk;
import com.noirm.traits.ProfileSync;
import com.noirm.traits.Talent;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import com.noirm.creator.PlayerLook;
import com.noirm.creator.PlayerLook.Slot;
import com.noirm.creator.PlayerLook.Tone;
import com.noirm.network.NoirmNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The character creation screen (blueprint §6), in the blueprint's "ash, iron and chakra" style,
 * drawn with the NOIRM kit: flat charcoal panels with one iron hairline, text tabs with a sliding
 * chakra underline, ash drifting up the screen; it fades in and out.
 *
 * <p>On the left, your character in 3D: drag to turn, scroll to zoom (each tab also eases the
 * camera toward what it edits). On the right, four tabs: Face, Body, Hair and Genetics. New
 * characters wear only their underwear (picked on Body): clothes are gear worn later.
 * Colours are picked from swatches and fine-tuned with hue / saturation / brightness bars.
 * Everything you change is a draft shown only on your screen until you confirm; the server
 * decides the genetics (clan rolls) and saves the look.
 */
public class CreatorScreen extends Screen implements NoirmStyled {
    // The kit's palette (the ninja look: lacquer, paper and ink), with the seal's red as this screen's accent.
    private static final int SOOT = Theme.darken(Theme.SURFACE, 0.7f);
    private static final int IRON = Theme.IRON;
    private static final int RAISED = Theme.RAISED;
    private static final int RAISED_HOVER = Theme.RAISED_HOVER;
    private static final int PAPER = Theme.TEXT;
    private static final int DIM = Theme.TEXT_DIM;
    private static final int FAINT = Theme.TEXT_FAINT;
    /** The selection colour: the seal's red, lifted so outlines read on dark paper. */
    private static final int CHAKRA = 0xFFE0554A;
    private static final int GOLD = Theme.GOLD;
    private static final int RED = Theme.RED;

    private static final int ROW = 13;
    private static final int LABEL_W = 66;

    private enum Tab {
        FACE("face", 1.75f), EYES("eyes", 2.3f), BODY("body", 1f), MARKS("marks", 1.25f), HAIR("hair", 1.6f),
        GENETICS("genetics", 1f), CLAN("clan", 1f);

        final String key;
        final float zoom;

        Tab(String key, float zoom) {
            this.key = key;
            this.zoom = zoom;
        }
    }

    private record Hit(int x, int y, int w, int h, Runnable click, List<Component> tip) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /** A draggable bar: value 0..1. */
    private record Bar(String id, int x, int y, int w, int h, Consumer<Float> set) {
        boolean contains(double mx, double my) {
            return mx >= x - 2 && mx < x + w + 2 && my >= y - 1 && my < y + h + 1;
        }
    }

    private CreatorPackets.State state;
    private PlayerLook draft;
    private Tab tab = Tab.FACE;
    private Tone activeTone = Tone.SKIN;
    /** How far each tab's body is scrolled (GUI px), and how tall it was last frame. */
    private final float[] scroll = new float[Tab.values().length];
    private int contentHeight;
    private final List<Hit> hits = new ArrayList<>();
    private final List<Bar> bars = new ArrayList<>();
    private String dragBar;
    private boolean draggingView;
    private float yaw = 20f;
    private float zoom = 1f;
    private float zoomTarget = Tab.FACE.zoom;
    private boolean closing;
    private final List<float[]> ash = new ArrayList<>();
    private long lastFrame;
    private final RandomSource random = RandomSource.create();
    private final ScreenMotion motion = new ScreenMotion();
    private final TabStrip tabStrip = new TabStrip();
    private final HoverTracker hover = new HoverTracker();
    private String[] tabLabels;

    // layout
    private int vx;
    private int vy;
    private int vw;
    private int vh;
    private int px;
    private int pw;

    public CreatorScreen(CreatorPackets.State state) {
        super(Component.translatable("creator.noirm.title"));
        this.state = state;
        this.draft = ClientCreator.draft(state);
        applyClanColours();
    }

    /** The clan the draft's colours were last set for (so a clan's red hair is only the default). */
    private String colouredFor = "";

    /**
     * A newly chosen roll's clan sets the draft's hair (and eyes) to the clan's colours: Uzumaki red,
     * Hyūga pale eyes. Only when the clan changes, so the player's own pick afterwards stays.
     */
    private void applyClanColours() {
        if (!state.modeEnum().genetics() || state.rolls().isEmpty()) {
            return;
        }
        CreatorPackets.RollView roll = state.rolls().get(Mth.clamp(state.chosen(), 0, state.rolls().size() - 1));
        if (roll.clanId().equals(colouredFor)) {
            return;
        }
        colouredFor = roll.clanId();
        if (roll.hair() >= 0) {
            draft.setColor(Tone.HAIR, roll.hair());
        }
        if (roll.eyes() >= 0) {
            draft.setColor(Tone.EYE, roll.eyes());
        }
    }

    /** The look shown on your character while the screen is open. */
    public PlayerLook draft() {
        return draft;
    }

    /** New rolls or a new mode from the server (the draft look is kept). */
    void update(CreatorPackets.State state) {
        this.state = state;
        if (!state.modeEnum().genetics() && tab == Tab.GENETICS && state.rolls().isEmpty()) {
            tab = Tab.FACE;
        }
        applyClanColours();
    }

    void closeFromServer() {
        closing = true;
        onClose();
    }

    private boolean held() {
        return state.held();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // handled in keyPressed
    }

    @Override
    protected void init() {
        int top = 32; // title, then the subtitle on its own line (no cut-off at 1280×720)
        int bottom = height - 24;
        vw = Mth.clamp((int) (width * 0.38f), 120, 220);
        vx = 8;
        vy = top;
        vh = bottom - top;
        px = vx + vw + 8;
        pw = width - px - 8;
        if (ash.isEmpty()) {
            for (int i = 0; i < 60; i++) {
                ash.add(newAsh(true));
            }
        }
        tabLabels = new String[Tab.values().length];
        for (Tab t : Tab.values()) {
            tabLabels[t.ordinal()] = Component.translatable("creator.noirm.tab." + t.key).getString();
        }
        if (!motion.started()) {
            motion.open();
        }
    }

    @Override
    public void tick() {
        motion.tick();
    }

    // ================================================================ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        bars.clear();
        long now = net.minecraft.Util.getMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(100f, now - lastFrame) / 16.6f;
        lastFrame = now;
        zoom += (zoomTarget - zoom) * Math.min(1f, 0.15f * dt);

        g.fill(0, 0, width, height, Theme.fade(SOOT, motion.visibility()));
        // a faint shoji lattice across the background
        int lattice = Theme.fade(Theme.mix(SOOT, Theme.HAIRLINE, 0.35f), motion.visibility());
        for (int lx = 24; lx < width; lx += 48) {
            g.fill(lx, 0, lx + 1, height, lattice);
        }
        for (int ly = 30; ly < height; ly += 64) {
            g.fill(0, ly, width, ly + 1, lattice);
        }
        motion.begin(g);
        drawAsh(g, dt, false);
        drawHeader(g, mouseX, mouseY);
        drawViewport(g, mouseX, mouseY);
        panel(g, px, vy, pw, vh);
        drawTabs(g, mouseX, mouseY);
        int top = contentTop();
        int bottom = contentBottom();
        boolean scrolls = tab != Tab.GENETICS && tab != Tab.CLAN;
        int max = Math.max(0, contentHeight - (bottom - top));
        scroll[tab.ordinal()] = Mth.clamp(scroll[tab.ordinal()], 0, max);
        int cy = top - (scrolls ? Math.round(scroll[tab.ordinal()]) : 0);
        int firstHit = hits.size();
        int firstBar = bars.size();
        motion.beginContent(g);
        if (scrolls) {
            g.enableScissor(px + 1, top - 1, px + pw - 1, bottom);
        }
        int end = switch (tab) {
            case FACE -> drawFace(g, cy, mouseX, mouseY);
            case EYES -> drawEyes(g, cy, mouseX, mouseY);
            case BODY -> drawBody(g, cy, mouseX, mouseY);
            case MARKS -> drawMarks(g, cy, mouseX, mouseY);
            case HAIR -> drawHair(g, cy, mouseX, mouseY);
            case GENETICS -> {
                drawGenetics(g, cy, mouseX, mouseY);
                yield cy;
            }
            case CLAN -> {
                drawClan(g, cy, mouseX, mouseY);
                yield cy;
            }
        };
        if (scrolls) {
            g.disableScissor();
            contentHeight = end - cy;
            clipHits(firstHit, firstBar, top, bottom);
            if (max > 0) {
                Ui.scrollbar(g, px + pw - 3, top, bottom - top, Math.round(scroll[tab.ordinal()]), bottom - top, contentHeight, Theme.IRON);
            }
        }
        motion.endContent(g);
        drawFooter(g, mouseX, mouseY);
        drawAsh(g, 0f, true); // a few embers in front
        motion.end(g);
        if (motion.closing()) {
            return;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.tip() != null && !hit.tip().isEmpty() && hit.contains(mouseX, mouseY)) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                for (Component line : hit.tip()) {
                    lines.addAll(font.split(line, 200));
                }
                g.renderTooltip(font, lines, mouseX, mouseY);
                break;
            }
        }
    }

    /** The options panel: the kit's panel. */
    private void panel(GuiGraphics g, int x, int y, int w, int h) {
        Ui.panel(g, x, y, w, h);
    }

    private int contentTop() {
        return vy + 26;
    }

    private int contentBottom() {
        return vy + vh - 9;
    }

    /** Hit boxes and bars drawn in the scrolled body are cut to what shows (and dropped when scrolled away). */
    private void clipHits(int firstHit, int firstBar, int top, int bottom) {
        for (int i = hits.size() - 1; i >= firstHit; i--) {
            Hit h = hits.get(i);
            int y0 = Math.max(h.y(), top);
            int y1 = Math.min(h.y() + h.h(), bottom);
            if (y1 <= y0) {
                hits.remove(i);
            } else if (y0 != h.y() || y1 != h.y() + h.h()) {
                hits.set(i, new Hit(h.x(), y0, h.w(), y1 - y0, h.click(), h.tip()));
            }
        }
        for (int i = bars.size() - 1; i >= firstBar; i--) {
            Bar b = bars.get(i);
            if (b.y() < top || b.y() + b.h() > bottom) {
                bars.remove(i);
            }
        }
    }

    /**
     * The title, and the subtitle on its own line below it (they used to share one line and the
     * subtitle was cut at 1280×720). If the subtitle still doesn't fit, it's cut and shown whole
     * as a tooltip.
     */
    private void drawHeader(GuiGraphics g, int mouseX, int mouseY) {
        // the title on a red brush swash, a hanko seal before it
        int tw = font.width(title);
        com.noirm.client.ui.Skin.swash(g, 20, 4, tw + 22, 15);
        Ui.seal(g, font, 6, 5, "忍", Theme.SEAL);
        g.drawString(font, title, 26, 8, PAPER, true);
        String key = switch (state.modeEnum()) {
            case APPEARANCE -> "creator.noirm.subtitle.appearance";
            case FULL -> "creator.noirm.subtitle.full";
            default -> "creator.noirm.subtitle.first_join";
        };
        Component sub = Component.translatable(key);
        int max = width - 20;
        g.drawString(font, cut(sub, max), 10, 19, DIM, false);
        if (font.width(sub) > max) {
            hits.add(new Hit(10, 19, max, 9, null, List.of(sub)));
        }
    }

    // ---------------------------------------------------------------- the 3D view

    private void drawViewport(GuiGraphics g, int mouseX, int mouseY) {
        Ui.panel(g, vx, vy, vw, vh);
        Player player = Minecraft.getInstance().player;
        // floor glow: a faint warm lantern light under the feet
        int floorY = vy + vh - 14;
        g.fillGradient(vx + 7, floorY - 30, vx + vw - 7, vy + vh - 7, 0x00000000, 0x30E0803A);
        if (player != null) {
            float height = draft.height();
            float base = vh * 0.41f; // not divided by height: taller characters look taller
            float scale = base * zoom;
            float focus = Mth.lerp(Mth.clamp((zoom - 1f) / 0.7f, 0f, 1f), 0.95f, 1.6f) * height;
            int cx = vx + vw / 2;
            int cyCenter = vy + vh / 2 + 6;
            int feetY = (int) (cyCenter + focus * scale * 0.9375f);
            g.enableScissor(vx + 7, vy + 7, vx + vw - 7, vy + vh - 7);
            renderCharacter(g, cx, feetY, (int) scale, player);
            g.disableScissor();
        }
        Component hint = Component.translatable("creator.noirm.hint.view");
        Component shownHint = cut(hint, vw - 18);
        g.drawString(font, shownHint, vx + vw / 2 - font.width(shownHint) / 2, vy + vh - 17, FAINT, false);
        String h = String.format("%.2f×", draft.height());
        g.drawString(font, h, vx + vw - 10 - font.width(h), vy + 10, DIM, false);
    }

    /** Like {@link InventoryScreen#renderEntityInInventoryFollowsAngle}, but turned by our yaw. */
    private void renderCharacter(GuiGraphics g, int x, int y, int scale, Player player) {
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX(-6f * Mth.DEG_TO_RAD);
        pose.mul(camera);
        float bodyRot = player.yBodyRot;
        float bodyRotO = player.yBodyRotO;
        float yRot = player.getYRot();
        float yRotO = player.yRotO;
        float xRot = player.getXRot();
        float xRotO = player.xRotO;
        float headO = player.yHeadRotO;
        float head = player.yHeadRot;
        float facing = 180f + yaw;
        player.yBodyRot = facing;
        player.yBodyRotO = facing;
        player.setYRot(facing);
        player.yRotO = facing;
        player.setXRot(0f);
        player.xRotO = 0f;
        player.yHeadRot = facing;
        player.yHeadRotO = facing;
        try {
            InventoryScreen.renderEntityInInventory(g, x, y, scale, pose, camera, player);
        } finally {
            player.yBodyRot = bodyRot;
            player.yBodyRotO = bodyRotO;
            player.setYRot(yRot);
            player.yRotO = yRotO;
            player.setXRot(xRot);
            player.xRotO = xRotO;
            player.yHeadRotO = headO;
            player.yHeadRot = head;
        }
    }

    // ---------------------------------------------------------------- tabs

    /** Text tabs with a chakra underline that slides to the active one. */
    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        tabStrip.draw(g, font, px + 11, vy + 9, pw - 22, tabLabels, tab.ordinal(), new int[]{CHAKRA}, mouseX, mouseY);
    }

    private void selectTab(Tab t) {
        if (t != tab) {
            motion.tabChanged();
        }
        tab = t;
        zoomTarget = t.zoom;
        activeTone = switch (t) {
            case FACE, BODY -> Tone.SKIN;
            case EYES -> Tone.EYE;
            case MARKS -> Tone.PAINT;
            case HAIR -> Tone.HAIR;
            case GENETICS, CLAN -> activeTone;
        };
    }

    private int drawFace(GuiGraphics g, int y, int mx, int my) {
        y = colorGrid(g, y, Tone.SKIN, mx, my);
        y = section(g, y, "creator.noirm.section.features");
        y = option(g, y, Slot.BROWS, mx, my);
        y = option(g, y, Slot.NOSE, mx, my);
        y = option(g, y, Slot.JAW, mx, my);
        y = option(g, y, Slot.MOUTH, mx, my);
        return fineTune(g, y + 4, mx, my);
    }

    private int drawEyes(GuiGraphics g, int y, int mx, int my) {
        y = eyeShapes(g, y, mx, my);
        y = pupils(g, y, mx, my);
        y = colorGrid(g, y, Tone.EYE, mx, my);
        y = option(g, y, Slot.EYE_DETAIL, mx, my);
        return fineTune(g, y + 4, mx, my);
    }

    private int drawBody(GuiGraphics g, int y, int mx, int my) {
        y = colorGrid(g, y, Tone.SKIN, mx, my);
        y = section(g, y, "creator.noirm.section.build");
        y = option(g, y, Slot.BODY, mx, my);
        y = option(g, y, Slot.FRAME, mx, my);
        y = option(g, y, Slot.MUSCLE, mx, my);
        y = heightRow(g, y, mx, my);
        y = section(g, y, "creator.noirm.section.underwear");
        y = option(g, y, Slot.UNDERWEAR, mx, my);
        y = color(g, y, Tone.UNDERWEAR, mx, my);
        y = option(g, y, Slot.WRAPS, mx, my);
        y = fineTune(g, y + 4, mx, my);
        for (FormattedCharSequence line : font.split(Component.translatable("creator.noirm.underwear_note"), pw - 22)) {
            g.drawString(font, line, px + 11, y, FAINT, false);
            y += 10;
        }
        return y;
    }

    private int drawMarks(GuiGraphics g, int y, int mx, int my) {
        y = section(g, y, "creator.noirm.section.scars");
        y = option(g, y, Slot.SCAR, mx, my);
        y = option(g, y, Slot.MARKING, mx, my);
        y = option(g, y, Slot.WEATHERING, mx, my);
        y = section(g, y, "creator.noirm.section.paint");
        y = option(g, y, Slot.FACE_PAINT, mx, my);
        y = option(g, y, Slot.TATTOO, mx, my);
        y = colorGrid(g, y, Tone.PAINT, mx, my);
        return fineTune(g, y + 4, mx, my);
    }

    private int drawHair(GuiGraphics g, int y, int mx, int my) {
        y = hairStyles(g, y, mx, my);
        y = option(g, y, Slot.HAIR_LENGTH, mx, my);
        y = option(g, y, Slot.HAIR_VOLUME, mx, my);
        y = colorGrid(g, y, Tone.HAIR, mx, my);
        y = section(g, y, "creator.noirm.section.dye");
        y = option(g, y, Slot.HAIR_DYE, mx, my);
        if (draft.get(Slot.HAIR_DYE) > 0) {
            y = colorGrid(g, y, Tone.HAIR2, mx, my);
        } else {
            g.drawString(font, cut(Component.translatable("creator.noirm.dye_note"), pw - 22), px + 11, y + 1, FAINT, false);
            y += 12;
        }
        return fineTune(g, y + 2, mx, my);
    }

    /** A small upper-case section label with a rule (the kit's section header). */
    private int section(GuiGraphics g, int y, String key) {
        return Ui.section(g, font, px + 11, y + 3, pw - 22, Component.translatable(key), FAINT) - 1;
    }

    private static final net.minecraft.resources.ResourceLocation HAIR_ICONS = com.noirm.Noirm.id("textures/gui/hair_styles.png");
    private static final int ICON = 48;
    private static final int ICON_COLS = 8;
    private static final int ICON_ROWS = (Slot.HAIR.size() + ICON_COLS - 1) / ICON_COLS;

    /**
     * Every hair style as a small picture (drawn by tools/skingen/gen_hair.py, tinted with the chosen colour),
     * in a grid; the chosen one outlined, its name on hover.
     */
    private int hairStyles(GuiGraphics g, int y, int mx, int my) {
        int x = px + 11;
        int w = pw - 22;
        int count = Slot.HAIR.size();
        int tile = Mth.clamp(w / 11, 18, 26);
        int cols = Math.max(1, (w + 2) / (tile + 2));
        int rows = (count + cols - 1) / cols;
        int current = draft.get(Slot.HAIR);
        int rgb = draft.color(Tone.HAIR);
        // The pictures are dark strands on a dark tile: lift the tint well toward white so every
        // style's shape reads, while keeping a hint of the chosen colour.
        float r = 0.6f + 0.4f * ((rgb >> 16) & 0xFF) / 255f;
        float gr = 0.6f + 0.4f * ((rgb >> 8) & 0xFF) / 255f;
        float b = 0.6f + 0.4f * (rgb & 0xFF) / 255f;
        for (int i = 0; i < count; i++) {
            int tx = x + (i % cols) * (tile + 2);
            int ty = y + (i / cols) * (tile + 2);
            boolean over = mx >= tx && mx < tx + tile && my >= ty && my < ty + tile;
            float hv = hover.get(HoverTracker.key(5, i), over);
            g.fill(tx, ty, tx + tile, ty + tile, Theme.mix(RAISED, RAISED_HOVER, hv));
            int u = (i % ICON_COLS) * ICON;
            int v = (i / ICON_COLS) * ICON;
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.setColor(r, gr, b, 1f);
            g.blit(HAIR_ICONS, tx + 1, ty + 1, tile - 2, tile - 2, u, v, ICON, ICON, ICON * ICON_COLS, ICON * ICON_ROWS);
            g.setColor(1f, 1f, 1f, 1f);
            if (i == current) {
                g.renderOutline(tx - 1, ty - 1, tile + 2, tile + 2, CHAKRA);
            } else if (over) {
                g.renderOutline(tx - 1, ty - 1, tile + 2, tile + 2, DIM);
            }
            int index = i;
            hits.add(new Hit(tx, ty, tile, tile, () -> {
                draft.set(Slot.HAIR, index);
                changed();
            }, List.of(Component.translatable(Slot.HAIR.translationKey(i)))));
        }
        return y + rows * (tile + 2) + 3;
    }

    /** All of a tone's swatches in wrapped rows (the hair's 40), and the current colour at the end. */
    private int colorGrid(GuiGraphics g, int y, Tone tone, int mx, int my) {
        int x = px + 11;
        Component label = Component.translatable("creator.noirm.tone." + tone.key());
        g.drawString(font, cut(label, LABEL_W - 4), x, y + 1, tone == activeTone ? CHAKRA : DIM, false);
        hits.add(new Hit(x, y, LABEL_W, 10, () -> activeTone = tone, null));
        int sx = x + LABEL_W;
        int size = 8;
        int cols = Math.max(1, (px + pw - 11 - sx) / (size + 1));
        int[] palette = tone.palette();
        int current = draft.color(tone);
        boolean inPalette = false;
        for (int i = 0; i <= palette.length; i++) {
            int cx = sx + (i % cols) * (size + 1);
            int cy = y + (i / cols) * (size + 1);
            boolean custom = i == palette.length;
            int c = custom ? current : palette[i];
            g.fill(cx, cy, cx + size, cy + size, 0xFF000000 | c);
            boolean over = mx >= cx && mx < cx + size && my >= cy && my < cy + size;
            if (!custom && c == current) {
                inPalette = true;
                g.renderOutline(cx - 1, cy - 1, size + 2, size + 2, PAPER);
            } else if (custom) {
                g.renderOutline(cx - 1, cy - 1, size + 2, size + 2, inPalette ? IRON : PAPER);
            } else if (over) {
                g.renderOutline(cx - 1, cy - 1, size + 2, size + 2, DIM);
            }
            if (custom) {
                hits.add(new Hit(cx, cy, size, size, () -> activeTone = tone,
                        List.of(Component.literal(String.format("#%06X", current)), Component.translatable("creator.noirm.custom_color"))));
            } else {
                hits.add(new Hit(cx, cy, size, size, () -> {
                    draft.setColor(tone, c);
                    activeTone = tone;
                    changed();
                }, null));
            }
        }
        int rows = (palette.length + 1 + cols - 1) / cols;
        return y + rows * (size + 1) + 3;
    }

    /**
     * The eye shapes as chips in rows, each showing the eye itself (the in-game high-detail atlas, in your eye
     * colour and with your pupil).
     */
    private int eyeShapes(GuiGraphics g, int y, int mx, int my) {
        int x = px + 11;
        int right = px + pw - 11;
        g.drawString(font, cut(Component.translatable("creator.noirm.slot.eyes"), LABEL_W - 4), x, y + 5, DIM, false);
        int bx = x + LABEL_W;
        int n = Slot.EYES.size();
        int cw = 30;
        int cols = Math.max(1, (right - bx + 2) / (cw + 2));
        cw = (right - bx - (cols - 1) * 2) / cols;
        int ch = 18;
        int current = draft.get(Slot.EYES);
        int pupil = draft.get(Slot.PUPIL);
        for (int i = 0; i < n; i++) {
            int cx = bx + (i % cols) * (cw + 2);
            int cy = y + (i / cols) * (ch + 2);
            boolean over = mx >= cx && mx < cx + cw && my >= cy && my < cy + ch;
            float hv = hover.get(HoverTracker.key(6, i), over);
            eyeChip(g, cx, cy, cw, ch, Slot.EYES.option(i), pupil, hv, i == current);
            int index = i;
            hits.add(new Hit(cx, cy, cw, ch, () -> {
                draft.set(Slot.EYES, index);
                changed();
            }, List.of(Component.translatable(Slot.EYES.translationKey(index)))));
        }
        return y + ((n + cols - 1) / cols) * (ch + 2) + 2;
    }

    /** The pupils as chips: your eye shape with each pupil. */
    private int pupils(GuiGraphics g, int y, int mx, int my) {
        int x = px + 11;
        int right = px + pw - 11;
        g.drawString(font, cut(Component.translatable("creator.noirm.slot.pupil"), LABEL_W - 4), x, y + 5, DIM, false);
        int bx = x + LABEL_W;
        int n = Slot.PUPIL.size();
        int cw = Math.max(16, (right - bx - (n - 1) * 2) / n);
        int ch = 18;
        int current = draft.get(Slot.PUPIL);
        String shape = draft.id(Slot.EYES);
        for (int i = 0; i < n; i++) {
            int cx = bx + i * (cw + 2);
            boolean over = mx >= cx && mx < cx + cw && my >= y && my < y + ch;
            float hv = hover.get(HoverTracker.key(7, i), over);
            eyeChip(g, cx, y, cw, ch, shape, i, hv, i == current);
            int index = i;
            hits.add(new Hit(cx, y, cw, ch, () -> {
                draft.set(Slot.PUPIL, index);
                changed();
            }, List.of(Component.translatable(Slot.PUPIL.translationKey(index)))));
        }
        return y + ch + 3;
    }

    /** One eye drawn from its high-detail atlas on a patch of skin: white, iris (row 2 for other pupils), lash line. */
    private void eyeChip(GuiGraphics g, int cx, int cy, int cw, int ch, String shape, int pupil, float hv, boolean chosen) {
        g.fill(cx, cy, cx + cw, cy + ch, Theme.mix(Theme.RAISED, Theme.RAISED_HOVER, hv));
        net.minecraft.resources.ResourceLocation atlas = com.noirm.Noirm.id("textures/entity/eyes/" + shape + ".png");
        int ew = Math.min(cw - 2, (ch - 2) * 64 / 40);
        int eh = ew * 40 / 64;
        int ex = cx + (cw - ew) / 2;
        int ey = cy + (ch - eh) / 2;
        g.fill(ex, ey, ex + ew, ey + eh, 0xFF000000 | draft.color(Tone.SKIN));
        int iris = SkinComposer.tint(draft.color(Tone.EYE), 186);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        // the atlas is 1024×256: drawn here at half size (cells 64×40, the pupil row from v 64)
        g.blit(atlas, ex, ey, ew, eh, 0, 0, 64, 40, 512, 128);
        g.setColor(((iris >> 16) & 0xFF) / 255f, ((iris >> 8) & 0xFF) / 255f, (iris & 0xFF) / 255f, 1f);
        if (pupil > 0) {
            g.blit(atlas, ex, ey, ew, eh, (pupil - 1) * 64, 64, 64, 40, 512, 128);
        } else {
            g.blit(atlas, ex, ey, ew, eh, 64, 0, 64, 40, 512, 128);
        }
        g.setColor(1f, 1f, 1f, 1f);
        g.blit(atlas, ex, ey, ew, eh, 128, 0, 64, 40, 512, 128);
        if (chosen) {
            g.renderOutline(cx - 1, cy - 1, cw + 2, ch + 2, CHAKRA);
        }
    }

    /** "Label   <  value  >": click the arrows (or scroll over the row) to change it. */
    private int option(GuiGraphics g, int y, Slot slot, int mx, int my) {
        int x = px + 11;
        int right = px + pw - 11;
        boolean hover = mx >= x && mx < right && my >= y && my < y + ROW - 1;
        float hv = this.hover.get(HoverTracker.key(1, slot.ordinal()), hover);
        g.drawString(font, cut(Component.translatable("creator.noirm.slot." + slot.key()), LABEL_W - 4), x, y + 2,
                Theme.mix(DIM, PAPER, hv * 0.6f), false);
        int bx = x + LABEL_W;
        int bw = right - bx;
        g.fill(bx, y, bx + bw, y + ROW - 2, Theme.mix(RAISED, RAISED_HOVER, hv));
        int value = draft.get(slot);
        Component text = cut(Component.translatable(slot.translationKey(value)), bw - 24);
        g.drawString(font, text, bx + bw / 2 - font.width(text) / 2, y + 2, PAPER, false);
        boolean overLeft = hover && mx < bx + bw / 2;
        g.drawString(font, "‹", bx + 4, y + 2, Theme.mix(FAINT, overLeft ? CHAKRA : DIM, hv), false);
        g.drawString(font, "›", bx + bw - 7, y + 2, Theme.mix(FAINT, !overLeft ? CHAKRA : DIM, hv), false);
        String count = (value + 1) + "/" + slot.size();
        hits.add(new Hit(bx, y, bw / 2, ROW - 2, () -> change(slot, -1),
                List.of(Component.translatable("creator.noirm.option_tip", count))));
        hits.add(new Hit(bx + bw / 2, y, bw - bw / 2, ROW - 2, () -> change(slot, 1),
                List.of(Component.translatable("creator.noirm.option_tip", count))));
        return y + ROW;
    }

    private void change(Slot slot, int step) {
        draft.set(slot, draft.get(slot) + step);
        changed();
    }

    /** "Label  ■■■■■■…": swatches; the chosen one is outlined. Clicking makes it the fine-tuned colour. */
    private int color(GuiGraphics g, int y, Tone tone, int mx, int my) {
        int x = px + 11;
        boolean active = tone == activeTone;
        Component label = Component.translatable("creator.noirm.tone." + tone.key());
        g.drawString(font, cut(label, LABEL_W - 4), x, y + 2, active ? CHAKRA : DIM, false);
        hits.add(new Hit(x, y, LABEL_W, ROW - 1, () -> activeTone = tone, null));
        int sx = x + LABEL_W;
        int current = draft.color(tone);
        int[] palette = tone.palette();
        int size = 9;
        int room = (px + pw - 11 - sx) / (size + 1);
        boolean inPalette = false;
        for (int i = 0; i < palette.length && i < room - 1; i++) {
            int c = palette[i];
            int cx = sx + i * (size + 1);
            g.fill(cx, y + 1, cx + size, y + 1 + size, 0xFF000000 | c);
            if (c == current) {
                inPalette = true;
                g.renderOutline(cx - 1, y, size + 2, size + 2, PAPER);
            } else if (mx >= cx && mx < cx + size && my >= y + 1 && my < y + 1 + size) {
                g.renderOutline(cx - 1, y, size + 2, size + 2, DIM);
            }
            hits.add(new Hit(cx, y + 1, size, size, () -> {
                draft.setColor(tone, c);
                activeTone = tone;
                changed();
            }, null));
        }
        // the current colour (custom ones show here)
        int cx = sx + Math.min(palette.length, room - 1) * (size + 1) + 2;
        if (cx + size <= px + pw - 6) {
            g.fill(cx, y + 1, cx + size, y + 1 + size, 0xFF000000 | current);
            g.renderOutline(cx - 1, y, size + 2, size + 2, inPalette ? IRON : PAPER);
            hits.add(new Hit(cx, y + 1, size, size, () -> activeTone = tone,
                    List.of(Component.literal(String.format("#%06X", current)))));
        }
        return y + ROW;
    }

    /** Hue / saturation / brightness bars for the active colour. */
    private int fineTune(GuiGraphics g, int y, int mx, int my) {
        Tone tone = activeTone;
        int x = px + 11;
        Ui.section(g, font, x, y, pw - 22, Component.translatable("creator.noirm.fine_tune",
                Component.translatable("creator.noirm.tone." + tone.key())), FAINT);
        y += 11;
        float[] hsv = hsv(draft.color(tone));
        String[] names = {"creator.noirm.hue", "creator.noirm.saturation", "creator.noirm.brightness"};
        for (int i = 0; i < 3; i++) {
            int bx = x + LABEL_W;
            int bw = px + pw - 11 - bx;
            g.drawString(font, cut(Component.translatable(names[i]), LABEL_W - 4), x, y, DIM, false);
            int segments = 24;
            for (int s = 0; s < segments; s++) {
                float t = (s + 0.5f) / segments;
                float[] c = hsv.clone();
                c[i] = t;
                int x0 = bx + bw * s / segments;
                int x1 = bx + bw * (s + 1) / segments;
                g.fill(x0, y + 2, x1, y + 6, 0xFF000000 | Mth.hsvToRgb(c[0], c[1], c[2]));
            }
            int knob = bx + (int) (hsv[i] * (bw - 1));
            g.fill(knob - 1, y, knob + 2, y + 8, PAPER);
            int channel = i;
            bars.add(new Bar("hsv" + i, bx, y, bw, 8, v -> {
                float[] now = hsv(draft.color(tone));
                now[channel] = v;
                draft.setColor(tone, Mth.hsvToRgb(now[0], now[1], Math.max(0.02f, now[2])));
                changed();
            }));
            y += 10;
        }
        return y + 4;
    }

    private int heightRow(GuiGraphics g, int y, int mx, int my) {
        int x = px + 11;
        g.drawString(font, cut(Component.translatable("creator.noirm.height"), LABEL_W - 4), x, y + 2, DIM, false);
        int bx = x + LABEL_W;
        int bw = px + pw - 11 - bx - 30;
        float t = (draft.height() - PlayerLook.MIN_HEIGHT) / (PlayerLook.MAX_HEIGHT - PlayerLook.MIN_HEIGHT);
        int mid = bx + bw / 2;
        g.fill(mid, y + 2, mid + 1, y + 8, FAINT);
        Ui.slider(g, bx, y + 1, bw, t, CHAKRA, hover.get(HoverTracker.key(2, 0), mx >= bx && mx < bx + bw && my >= y && my < y + ROW - 1));
        g.drawString(font, String.format("%.2f×", draft.height()), bx + bw + 5, y + 2, PAPER, false);
        bars.add(new Bar("height", bx, y + 1, bw, 8, v -> {
            draft.setHeight(Math.round(Mth.lerp(v, PlayerLook.MIN_HEIGHT, PlayerLook.MAX_HEIGHT) * 100f) / 100f);
            changed();
        }));
        hits.add(new Hit(bx, y, bw, ROW - 1, null, List.of(Component.translatable("creator.noirm.height_tip"))));
        return y + ROW;
    }

    // ---------------------------------------------------------------- genetics

    private void drawGenetics(GuiGraphics g, int y, int mx, int my) {
        int x = px + 11;
        int w = pw - 22;
        List<CreatorPackets.RollView> rolls = state.rolls();
        if (!state.modeEnum().genetics()) {
            for (FormattedCharSequence line : font.split(Component.translatable("creator.noirm.genetics.locked"), w)) {
                g.drawString(font, line, x, y, DIM, false);
                y += 10;
            }
            if (!rolls.isEmpty()) {
                rollCard(g, x, y + 4, w, rolls.get(0), true, -1, mx, my);
            }
            return;
        }
        int seen = rolls.size();
        int total = seen + state.rerollsLeft();
        g.drawString(font, cut(Component.translatable("creator.noirm.genetics.rolls", seen, total), w), x, y, DIM, false);
        y += 11;
        int cardH = 31;
        int room = vy + vh - 30 - y;
        int step = Math.min(cardH + 3, Math.max(22, room / Math.max(1, seen)));
        for (int i = 0; i < seen; i++) {
            rollCard(g, x, y, w, rolls.get(i), i == state.chosen(), i, mx, my);
            y += step;
        }
        // reroll button
        int by = vy + vh - 25;
        boolean can = state.rerollsLeft() > 0;
        Component label = can ? Component.translatable("creator.noirm.genetics.reroll", state.rerollsLeft())
                : Component.translatable("creator.noirm.genetics.no_rerolls");
        int bw = Math.min(w, font.width(label) + 16);
        button(g, x, by, bw, 14, label, can, mx, my, () -> send(CreatorPackets.Action.Kind.REROLL, 0),
                List.of(Component.translatable("creator.noirm.genetics.reroll_tip", com.noirm.creator.CharacterCreator.MAX_REROLLS)));
    }

    private static final String[] STATS = {"res", "tai", "spd", "ctl", "wil"};

    private void rollCard(GuiGraphics g, int x, int y, int w, CreatorPackets.RollView roll, boolean chosen, int index, int mx, int my) {
        int h = 31;
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        float hv = this.hover.get(HoverTracker.key(3, index), hover);
        g.fill(x, y, x + w, y + h, chosen ? Theme.mix(RAISED, CHAKRA, 0.14f) : Theme.mix(RAISED, RAISED_HOVER, hv));
        if (chosen) {
            g.fill(x, y, x + 1, y + h, CHAKRA);
        }
        MutableComponent name = Component.literal(roll.clanName()).withStyle(s -> s.withBold(true));
        g.drawString(font, cut(name, w - 60), x + 5, y + 3, roll.canon() ? RED : GOLD, false);
        if (chosen) {
            Component kept = Component.translatable("creator.noirm.genetics.kept");
            g.drawString(font, kept, x + w - 5 - font.width(kept), y + 3, CHAKRA, false);
        }
        // natures as coloured names, kekkei genkai, trait count
        int nx = x + 5;
        for (Nature nature : roll.natureList()) {
            Component n = Component.translatable(nature.translationKey());
            if (nx + font.width(n) > x + w - 40) {
                break;
            }
            g.drawString(font, n, nx, y + 12, 0xFF000000 | nature.color(), false);
            nx += font.width(n) + 5;
        }
        if (!roll.kekkeiGenkai().isEmpty()) {
            Component k = Component.translatable("creator.noirm.genetics.kekkei." + roll.kekkeiGenkai());
            g.drawString(font, cut(k, x + w - 5 - nx), Math.max(nx, x + w - 5 - font.width(k)), y + 12, 0xFFD06AD0, false);
        } else if (!roll.talents().isEmpty()) {
            Component t = Component.translatable("creator.noirm.genetics.talent_count", roll.talents().size());
            g.drawString(font, t, x + w - 5 - font.width(t), y + 12, 0xFF7FD3C8, false);
        }
        // stats
        int sx = x + 5;
        int cell = (w - 10) / 5;
        for (int i = 0; i < 5; i++) {
            float value = roll.stats()[i];
            boolean boosted = value > roll.base()[i] + 0.01f;
            String text = Component.translatable("creator.noirm.stat." + STATS[i]).getString() + " " + Math.round(value);
            g.drawString(font, text, sx + i * cell, y + 21, boosted ? 0xFF7FD3C8 : PAPER, false);
        }
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(roll.clanName()).withStyle(s -> s.withColor(roll.canon() ? RED : GOLD)));
        if (roll.canon()) {
            tip.add(Component.translatable("creator.noirm.genetics.canon"));
        }
        MutableComponent natures = Component.empty();
        boolean first = true;
        for (var e : roll.natures()) {
            if (e.getKey() < 0 || e.getKey() >= Nature.values().length) {
                continue;
            }
            Nature n = Nature.values()[e.getKey()];
            if (!first) {
                natures.append(", ");
            }
            String strength = e.getValue() >= com.noirm.clan.Clan.PRIMARY_AFFINITY ? "nature.noirm.strength.primary"
                    : e.getValue() >= com.noirm.clan.Clan.SECONDARY_AFFINITY ? "nature.noirm.strength.secondary" : "nature.noirm.strength.trained";
            natures.append(Component.translatable(n.translationKey()).withStyle(s -> s.withColor(n.color())))
                    .append(" (").append(Component.translatable(strength)).append(")");
            first = false;
        }
        tip.add(Component.translatable("creator.noirm.genetics.natures", natures));
        if (roll.traitList().isEmpty()) {
            tip.add(Component.translatable("creator.noirm.genetics.no_traits"));
        } else {
            for (ClanTrait trait : roll.traitList()) {
                tip.add(Component.literal("• ").append(Component.translatable(trait.translationKey())).withStyle(s -> s.withColor(0x7FD3C8)));
            }
        }
        if (!roll.kekkeiGenkai().isEmpty()) {
            tip.add(Component.translatable("creator.noirm.genetics.kekkei." + roll.kekkeiGenkai()).withStyle(s -> s.withColor(0xD06AD0)));
        }
        for (ProfileSync.TalentView t : roll.talents()) { // personal talents (details on the Clan tab)
            tip.add(Component.literal("◆ ").append(Component.translatable(Talent.translationKey(t.id()))).withStyle(s -> s.withColor(t.kind().color())));
        }
        if (!roll.perks().isEmpty() || !roll.talents().isEmpty()) {
            tip.add(Component.translatable("creator.noirm.genetics.see_clan").withStyle(s -> s.withColor(0x808080)));
        }
        for (int i = 0; i < 5; i++) {
            int bonus = Math.round(roll.stats()[i] - roll.base()[i]);
            tip.add(Component.translatable("creator.noirm.stat_line", Component.translatable("creator.noirm.stat_name." + STATS[i]),
                    Math.round(roll.stats()[i]), roll.base()[i], bonus));
        }
        if (index >= 0 && !chosen) {
            tip.add(Component.translatable("creator.noirm.genetics.pick").withStyle(s -> s.withColor(CHAKRA)));
        }
        hits.add(new Hit(x, y, w, h, index >= 0 ? () -> send(CreatorPackets.Action.Kind.CHOOSE, index) : null, tip));
    }

    // ---------------------------------------------------------------- clan page

    /**
     * The chosen roll's clan, plainly: what its blood gives (green helps, red costs), its secret
     * techniques, and the character's own talents (hover one for what it does). In appearance mode,
     * your current clan and talents.
     */
    /** Where wrapped line {@code index} starts in {@code text} (by the lengths of the lines before it). */
    private static int lineStart(String text, List<FormattedCharSequence> lines, int index) {
        int start = 0;
        for (int i = 0; i < index; i++) {
            StringBuilder b = new StringBuilder();
            lines.get(i).accept((pos, style, cp) -> {
                b.appendCodePoint(cp);
                return true;
            });
            start += b.length();
            while (start < text.length() && text.charAt(start) == ' ') {
                start++;
            }
        }
        return start;
    }

    private void drawClan(GuiGraphics g, int y, int mx, int my) {
        int x = px + 11;
        int w = pw - 22;
        List<CreatorPackets.RollView> rolls = state.rolls();
        if (rolls.isEmpty()) {
            g.drawString(font, cut(Component.translatable("creator.noirm.clan.none"), w), x, y, DIM, false);
            return;
        }
        CreatorPackets.RollView roll = rolls.get(Mth.clamp(state.chosen(), 0, rolls.size() - 1));
        MutableComponent name = Component.literal(roll.clanName()).withStyle(s -> s.withBold(true));
        g.drawString(font, cut(name, w), x, y, roll.canon() ? RED : GOLD, false);
        if (!roll.kekkeiGenkai().isEmpty()) {
            Component k = Component.translatable("creator.noirm.genetics.kekkei." + roll.kekkeiGenkai());
            g.drawString(font, cut(k, w / 2), x + w - Math.min(w / 2, font.width(k)), y, 0xFFD06AD0, false);
        }
        y += 12;
        // The flavour line only gets the room the perks and talents leave (two lines at most).
        int bottom = vy + vh - 9;
        int perkRows = (roll.perks().size() + 1) / 2;
        int needed = 10 + Math.max(1, perkRows) * 10 + 2 + (roll.clanJutsu().isEmpty() ? 0 : 12) + 10
                + Math.max(1, roll.talents().size()) * 11;
        int flavourLines = Mth.clamp((bottom - y - needed - 3) / 9, 0, 2);
        Component flavour = roll.canon() ? Component.translatable("clan.noirm." + roll.clanId() + ".desc")
                : Component.translatable("creator.noirm.clan.generated");
        List<FormattedCharSequence> lines = font.split(flavour, w);
        for (int i = 0; i < Math.min(flavourLines, lines.size()); i++) {
            boolean last = i == flavourLines - 1 && lines.size() > flavourLines;
            if (last) {
                g.drawString(font, cut(Component.literal(flavour.getString().substring(Math.min(flavour.getString().length(),
                        lineStart(flavour.getString(), lines, i)))), w), x, y, FAINT, false);
            } else {
                g.drawString(font, lines.get(i), x, y, FAINT, false);
            }
            y += 9;
        }
        if (flavourLines > 0) {
            hits.add(new Hit(x, y - 9 * flavourLines, w, 9 * flavourLines, null, List.of(flavour)));
            y += 3;
        }
        // the blood: two columns of perks
        g.drawString(font, Component.translatable("creator.noirm.clan.blood"), x, y, DIM, false);
        y += 10;
        List<Map.Entry<Perk, Float>> perks = new ArrayList<>(roll.perks().entrySet());
        if (perks.isEmpty()) {
            g.drawString(font, cut(Component.translatable("creator.noirm.clan.no_perks"), w), x + 4, y, FAINT, false);
            y += 10;
        }
        int col = (w - 4) / 2;
        int rowsUsed = (perks.size() + 1) / 2;
        for (int i = 0; i < perks.size(); i++) {
            Perk perk = perks.get(i).getKey();
            float value = perks.get(i).getValue();
            Component line = Component.translatable(perk.translationKey(), perk.format(value));
            int cx = x + 4 + (i / Math.max(1, rowsUsed)) * col;
            int cy = y + (i % Math.max(1, rowsUsed)) * 10;
            int colour = perk.good(value) ? 0xFF7FD38A : 0xFFE06A5A;
            int width = font.width(line);
            if (width <= col - 4) {
                g.drawString(font, line, cx, cy, colour, false);
            } else {
                // A long perk shrinks to fit (down to 3/4 size) before it is cut; hovering shows it in full.
                float scale = Math.max(0.75f, (col - 4) / (float) width);
                g.pose().pushPose();
                g.pose().translate(cx, cy + (1 - scale) * 4, 0);
                g.pose().scale(scale, scale, 1);
                g.drawString(font, cut(line, (int) ((col - 4) / scale)), 0, 0, colour, false);
                g.pose().popPose();
            }
            hits.add(new Hit(cx, cy, col - 4, 10, null, List.of(line, Component.translatable(perk.translationKey() + ".desc")
                    .withStyle(s -> s.withColor(0xA0A0A0)))));
        }
        y += rowsUsed * 10 + 2;
        if (!roll.clanJutsu().isEmpty()) {
            MutableComponent jutsu = Component.empty();
            for (int i = 0; i < roll.clanJutsu().size(); i++) {
                ResourceLocation id = ResourceLocation.tryParse(roll.clanJutsu().get(i));
                if (id == null) {
                    continue;
                }
                if (i > 0) {
                    jutsu.append(", ");
                }
                jutsu.append(Component.translatable("jutsu." + id.getNamespace() + "." + id.getPath().replace('/', '.')));
            }
            Component line = Component.translatable("creator.noirm.clan.jutsu", jutsu);
            g.drawString(font, cut(line, w), x, y, GOLD, false);
            hits.add(new Hit(x, y, w, 10, null, List.of(line, Component.translatable("creator.noirm.clan.jutsu_tip")
                    .withStyle(s -> s.withColor(0xA0A0A0)))));
            y += 12;
        }
        // the character's own talents
        g.drawString(font, Component.translatable("creator.noirm.clan.talents"), x, y, DIM, false);
        y += 10;
        if (roll.talents().isEmpty()) {
            g.drawString(font, cut(Component.translatable("talent.noirm.none"), w), x + 4, y, FAINT, false);
            y += 10;
        }
        for (int ti = 0; ti < roll.talents().size(); ti++) {
            ProfileSync.TalentView t = roll.talents().get(ti);
            if (y + 10 > vy + vh - 2 && ti < roll.talents().size() - 1) {
                g.drawString(font, "+" + (roll.talents().size() - ti), x + 4, y, FAINT, false);
                y += 11;
                break;
            }
            Component tname = Component.translatable(Talent.translationKey(t.id()));
            MutableComponent mods = Component.empty();
            boolean first = true;
            for (Map.Entry<Perk, Float> e : t.modifiers().entrySet()) {
                if (!first) {
                    mods.append(", ");
                }
                mods.append(Component.translatable(e.getKey().translationKey(), e.getKey().format(e.getValue()))
                        .withStyle(s -> s.withColor(e.getKey().good(e.getValue()) ? 0x7FD38A : 0xE06A5A)));
                first = false;
            }
            int nameW = Math.min(w / 2, font.width(tname) + 6);
            g.drawString(font, cut(tname, nameW), x + 4, y, 0xFF000000 | t.kind().color(), false);
            g.drawString(font, cut(mods, w - nameW - 8), x + 4 + nameW, y, FAINT, false);
            List<Component> tip = new ArrayList<>();
            tip.add(tname.copy().withStyle(s -> s.withColor(t.kind().color())));
            tip.add(Component.translatable("talent.noirm.kind." + t.kind().getSerializedName()).withStyle(s -> s.withColor(0x808080)));
            tip.add(Component.translatable(Talent.descriptionKey(t.id())));
            tip.add(mods);
            hits.add(new Hit(x, y, w, 10, null, tip));
            y += 11;
        }
        y += 2;
        for (FormattedCharSequence line : font.split(Component.translatable("creator.noirm.clan.talents_note"), w)) {
            if (y > vy + vh - 12) {
                break;
            }
            g.drawString(font, line, x, y, FAINT, false);
            y += 9;
        }
    }

    // ---------------------------------------------------------------- footer

    private void drawFooter(GuiGraphics g, int mx, int my) {
        int y = height - 20;
        button(g, vx, y, vw, 15, Component.translatable("creator.noirm.randomize"), true, mx, my, this::randomize, null);
        boolean genetics = state.modeEnum().genetics();
        Component confirm = Component.translatable(state.modeEnum() == CreatorData.Mode.FIRST_JOIN ? "creator.noirm.confirm"
                : "creator.noirm.confirm.save");
        int cw = Math.max(80, font.width(confirm) + 20);
        int cx = px + pw - cw;
        List<Component> tip = new ArrayList<>();
        if (genetics && !state.rolls().isEmpty()) {
            int chosen = Mth.clamp(state.chosen(), 0, state.rolls().size() - 1);
            tip.add(Component.translatable("creator.noirm.confirm_tip", state.rolls().get(chosen).clanName()));
        }
        button(g, cx, y, cw, 15, confirm, true, mx, my, this::confirm, tip, true);
        if (!held()) {
            Component cancel = Component.translatable("creator.noirm.cancel");
            int w = font.width(cancel) + 16;
            button(g, cx - w - 6, y, w, 15, cancel, true, mx, my, this::cancel, null);
        } else {
            // The note wraps onto two lines beside the button instead of being cut.
            Component note = Component.translatable("creator.noirm.held");
            int room = cx - px - 8;
            List<FormattedCharSequence> lines = font.split(note, Math.max(20, room));
            if (lines.size() == 1) {
                g.drawString(font, lines.get(0), px, y + 4, FAINT, false);
            } else {
                g.drawString(font, lines.get(0), px, y - 2, FAINT, false);
                if (lines.size() == 2) {
                    g.drawString(font, lines.get(1), px, y + 7, FAINT, false);
                } else {
                    g.drawString(font, cut(note.copy(), room), px, y + 7, FAINT, false);
                }
            }
        }
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, Component label, boolean enabled, int mx, int my, Runnable click,
                        List<Component> tip) {
        button(g, x, y, w, h, label, enabled, mx, my, click, tip, false);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, Component label, boolean enabled, int mx, int my, Runnable click,
                        List<Component> tip, boolean primary) {
        boolean over = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
        Ui.button(g, font, x, y, w, h, label, hover.get(HoverTracker.key(4, x, y), over), enabled, CHAKRA, primary);
        hits.add(new Hit(x, y, w, h, enabled ? click : null, tip));
    }

    // ---------------------------------------------------------------- ash

    private float[] newAsh(boolean anywhere) {
        boolean ember = random.nextFloat() < 0.12f;
        return new float[]{
                random.nextFloat() * Math.max(1, width),
                anywhere ? random.nextFloat() * Math.max(1, height) : height + 4,
                -0.15f - random.nextFloat() * 0.35f, // rise speed
                random.nextFloat() * 6.28f,          // sway phase
                ember ? 1 : 0,
                1 + random.nextInt(2),               // size
                0.25f + random.nextFloat() * 0.6f};  // alpha
    }

    private void drawAsh(GuiGraphics g, float dt, boolean embersOnly) {
        for (int i = 0; i < ash.size(); i++) {
            float[] p = ash.get(i);
            boolean ember = p[4] > 0;
            if (embersOnly != ember) {
                continue;
            }
            if (dt > 0) {
                p[1] += p[2] * dt;
                p[3] += 0.03f * dt;
                p[0] += Mth.sin(p[3]) * 0.25f * dt;
            }
            if (!embersOnly && p[1] < -4) {
                ash.set(i, newAsh(false));
                continue;
            }
            if (embersOnly) {
                // embers move in the first pass too: update them here
                p[1] += p[2] * 1.4f;
                p[3] += 0.04f;
                p[0] += Mth.sin(p[3]) * 0.3f;
                if (p[1] < -4) {
                    ash.set(i, newAsh(false));
                    continue;
                }
            }
            int size = (int) p[5];
            int alpha = (int) (p[6] * (ember ? 200 : 120));
            int color = ember ? 0xE8803A : (i % 3 == 0 ? 0x8A8F96 : 0x5E6369);
            g.fill((int) p[0], (int) p[1], (int) p[0] + size, (int) p[1] + size, (alpha << 24) | color);
        }
    }

    // ================================================================ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (motion.closing()) {
            return true;
        }
        if (button == 0) {
            int t = tabStrip.at(mouseX, mouseY);
            if (t >= 0) {
                if (t != tab.ordinal()) {
                    com.noirm.client.ui.UiSounds.click();
                    selectTab(Tab.values()[t]);
                }
                return true;
            }
            for (Bar bar : bars) {
                if (bar.contains(mouseX, mouseY)) {
                    dragBar = bar.id();
                    bar.set().accept(Mth.clamp((float) (mouseX - bar.x()) / Math.max(1, bar.w() - 1), 0f, 1f));
                    return true;
                }
            }
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit hit = hits.get(i);
                if (hit.click() != null && hit.contains(mouseX, mouseY)) {
                    com.noirm.client.ui.UiSounds.click();
                    hit.click().run();
                    return true;
                }
            }
            if (inViewport(mouseX, mouseY)) {
                draggingView = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragBar != null) {
            for (Bar bar : bars) {
                if (bar.id().equals(dragBar)) {
                    bar.set().accept(Mth.clamp((float) (mouseX - bar.x()) / Math.max(1, bar.w() - 1), 0f, 1f));
                }
            }
            return true;
        }
        if (draggingView) {
            yaw = Mth.wrapDegrees(yaw - (float) dx * 2.2f);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragBar = null;
        draggingView = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (inViewport(mouseX, mouseY)) {
            zoomTarget = Mth.clamp(zoomTarget + (float) delta * 0.25f, 0.8f, 2.8f);
            return true;
        }
        // over a tab body that's longer than the panel, the wheel scrolls it
        if (mouseX >= px && mouseX < px + pw && mouseY >= contentTop() && mouseY < contentBottom()
                && contentHeight > contentBottom() - contentTop() && tab != Tab.GENETICS && tab != Tab.CLAN) {
            scroll[tab.ordinal()] -= (float) delta * 18f;
            return true;
        }
        // otherwise scrolling over an option row cycles it
        for (Hit hit : hits) {
            if (hit.contains(mouseX, mouseY) && hit.tip() != null && hit.click() != null && hit.w() < pw && hit.h() == ROW - 2) {
                hit.click().run();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (held()) {
                // A newcomer can't skip creation; Esc opens the pause menu (to quit), and the
                // creator comes back afterwards with the draft kept.
                Minecraft.getInstance().pauseGame(false);
            } else {
                cancel();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT) {
            yaw -= 15f;
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            yaw += 15f;
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    private boolean inViewport(double x, double y) {
        return x >= vx && x < vx + vw && y >= vy && y < vy + vh;
    }

    // ================================================================ actions

    private void changed() {
        ClientCreator.setDraft(draft);
    }

    private void randomize() {
        float keep = draft.height();
        draft = PlayerLook.random(random).setHeight(keep);
        changed();
    }

    private void send(CreatorPackets.Action.Kind kind, int arg) {
        NoirmNetwork.sendToServer(new CreatorPackets.Action(kind, arg));
    }

    private void confirm() {
        NoirmNetwork.sendToServer(new CreatorPackets.Finish(draft.copy()));
        ClientCreator.closed();
        closing = true;
        onClose();
    }

    private void cancel() {
        if (held()) {
            return;
        }
        send(CreatorPackets.Action.Kind.CANCEL, 0);
        ClientCreator.closed();
        closing = true;
        onClose();
    }

    @Override
    public void onClose() {
        if (!closing && !held()) {
            send(CreatorPackets.Action.Kind.CANCEL, 0);
            ClientCreator.closed();
        }
        closing = true; // the cancel (if any) was sent once; the fade then closes the screen
        motion.close(super::onClose);
    }

    // ================================================================ helpers

    private Component cut(Component text, int max) {
        if (font.width(text) <= max) {
            return text;
        }
        String s = text.getString();
        while (!s.isEmpty() && font.width(s + "…") > max) {
            s = s.substring(0, s.length() - 1);
        }
        return Component.literal(s + "…").withStyle(text.getStyle());
    }

    /** RGB → {hue, saturation, value}, each 0..1. */
    private static float[] hsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0) {
            h = 0;
        } else if (max == r) {
            h = ((g - b) / d) % 6f;
        } else if (max == g) {
            h = (b - r) / d + 2f;
        } else {
            h = (r - g) / d + 4f;
        }
        h /= 6f;
        if (h < 0) {
            h += 1f;
        }
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }
}
