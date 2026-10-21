package com.noirm.client;

import com.noirm.clan.ClanTrait;
import com.noirm.clan.Nature;
import com.noirm.creator.CreatorData;
import com.noirm.creator.CreatorPackets;
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
 * The character creation screen (blueprint §6), in the blueprint's "ash, iron and chakra" style:
 * soot and charcoal panels framed in cold iron, ash drifting up the screen.
 *
 * <p>On the left, your character in 3D: drag to turn, scroll to zoom (each tab also eases the
 * camera toward what it edits). On the right, five tabs: Face, Body, Hair, Clothes and Genetics.
 * Colours are picked from swatches and fine-tuned with hue / saturation / brightness bars.
 * Everything you change is a draft shown only on your screen until you confirm; the server
 * decides the genetics (clan rolls) and saves the look.
 */
public class CreatorScreen extends Screen {
    // The blueprint's base palette ("Ash, iron and chakra") plus text and one chakra accent.
    private static final int SOOT = 0xFF17191D;
    private static final int CHARCOAL = 0xFF292D33;
    private static final int IRON = 0xFF606A73;
    private static final int ASH = 0xFF565B60;
    private static final int INDIGO = 0xFF1F2430;
    private static final int PAPER = 0xFFE6E2DA;
    private static final int DIM = 0xFF9AA0A6;
    private static final int FAINT = 0xFF6A7077;
    private static final int CHAKRA = 0xFF6FB3E8;
    private static final int GOLD = 0xFFE8C060;
    private static final int RED = 0xFFE05A5A;

    private static final int ROW = 13;
    private static final int LABEL_W = 66;

    private enum Tab {
        FACE("face", 2.6f), BODY("body", 1f), HAIR("hair", 2.2f), CLOTHES("clothes", 1f), GENETICS("genetics", 1f);

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
    private Tone activeTone = Tone.EYE;
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
        int top = 24;
        int bottom = height - 30;
        vw = Mth.clamp((int) (width * 0.38f), 120, 220);
        vx = 8;
        vy = top;
        vh = bottom - top;
        px = vx + vw + 8;
        pw = width - px - 8;
        if (ash.isEmpty()) {
            for (int i = 0; i < 70; i++) {
                ash.add(newAsh(true));
            }
        }
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

        g.fillGradient(0, 0, width, height, SOOT, 0xFF0E0F12);
        for (int ly = 0; ly < height; ly += 4) {
            g.fill(0, ly, width, ly + 1, 0x08FFFFFF); // brushed metal
        }
        drawAsh(g, dt, false);
        drawHeader(g);
        drawViewport(g, mouseX, mouseY);
        panel(g, px, vy, pw, vh);
        drawTabs(g, mouseX, mouseY);
        int cy = vy + 20;
        switch (tab) {
            case FACE -> drawFace(g, cy, mouseX, mouseY);
            case BODY -> drawBody(g, cy, mouseX, mouseY);
            case HAIR -> drawHair(g, cy, mouseX, mouseY);
            case CLOTHES -> drawClothes(g, cy, mouseX, mouseY);
            case GENETICS -> drawGenetics(g, cy, mouseX, mouseY);
        }
        drawFooter(g, mouseX, mouseY);
        drawAsh(g, 0f, true); // a few embers in front
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

    /** A cold-iron framed charcoal panel with rivets. */
    private void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0A0B0D);
        g.fillGradient(x, y, x + w, y + h, CHARCOAL, INDIGO);
        g.renderOutline(x, y, w, h, IRON);
        g.renderOutline(x + 2, y + 2, w - 4, h - 4, 0xFF33383F);
        for (int[] c : new int[][]{{x + 3, y + 3}, {x + w - 5, y + 3}, {x + 3, y + h - 5}, {x + w - 5, y + h - 5}}) {
            g.fill(c[0], c[1], c[0] + 2, c[1] + 2, ASH);
            g.fill(c[0], c[1], c[0] + 1, c[1] + 1, 0xFF8A939B);
        }
    }

    private void drawHeader(GuiGraphics g) {
        g.drawString(font, title.copy().withStyle(s -> s.withBold(true)), 10, 8, PAPER, false);
        String key = switch (state.modeEnum()) {
            case APPEARANCE -> "creator.noirm.subtitle.appearance";
            case FULL -> "creator.noirm.subtitle.full";
            default -> "creator.noirm.subtitle.first_join";
        };
        Component sub = Component.translatable(key);
        int tw = font.width(title) + 18;
        g.drawString(font, cut(sub, width - tw - 14), tw + 6, 8, DIM, false);
        g.fill(8, 19, width - 8, 20, 0x60606A73);
    }

    // ---------------------------------------------------------------- the 3D view

    private void drawViewport(GuiGraphics g, int mouseX, int mouseY) {
        panel(g, vx, vy, vw, vh);
        Player player = Minecraft.getInstance().player;
        // floor glow
        int floorY = vy + vh - 14;
        g.fillGradient(vx + 3, floorY - 30, vx + vw - 3, vy + vh - 3, 0x00000000, 0x406FB3E8);
        if (player != null) {
            float height = draft.height();
            float base = vh * 0.36f; // not divided by height: taller characters look taller
            float scale = base * zoom;
            float focus = Mth.lerp((zoom - 1f) / 1.6f, 0.95f, 1.62f) * height;
            int cx = vx + vw / 2;
            int cyCenter = vy + vh / 2 + 6;
            int feetY = (int) (cyCenter + focus * scale * 0.9375f);
            g.enableScissor(vx + 3, vy + 3, vx + vw - 3, vy + vh - 3);
            renderCharacter(g, cx, feetY, (int) scale, player);
            g.disableScissor();
        }
        Component hint = Component.translatable("creator.noirm.hint.view");
        g.drawCenteredString(font, cut(hint, vw - 8), vx + vw / 2, vy + vh - 11, FAINT);
        String h = String.format("%.2f×", draft.height());
        g.drawString(font, h, vx + vw - 6 - font.width(h), vy + 6, DIM, false);
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

    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        int x = px + 4;
        int y = vy + 4;
        int w = (pw - 8) / Tab.values().length;
        for (Tab t : Tab.values()) {
            boolean on = t == tab;
            boolean hover = mouseX >= x && mouseX < x + w - 2 && mouseY >= y && mouseY < y + 13;
            g.fill(x, y, x + w - 2, y + 13, on ? 0xFF3A414A : hover ? 0xFF30363E : 0xFF22262C);
            if (on) {
                g.fill(x, y + 12, x + w - 2, y + 13, CHAKRA);
            }
            Component label = Component.translatable("creator.noirm.tab." + t.key);
            g.drawCenteredString(font, cut(label, w - 4), x + (w - 2) / 2, y + 3, on ? PAPER : DIM);
            Tab target = t;
            hits.add(new Hit(x, y, w - 2, 13, () -> selectTab(target), null));
            x += w;
        }
    }

    private void selectTab(Tab t) {
        tab = t;
        zoomTarget = t.zoom;
        activeTone = switch (t) {
            case FACE -> Tone.EYE;
            case BODY -> Tone.SKIN;
            case HAIR -> Tone.HAIR;
            case CLOTHES -> Tone.TOP;
            case GENETICS -> activeTone;
        };
    }

    private void drawFace(GuiGraphics g, int y, int mx, int my) {
        y = option(g, y, Slot.EYES, mx, my);
        y = color(g, y, Tone.EYE, mx, my);
        y = option(g, y, Slot.NOSE, mx, my);
        y = option(g, y, Slot.JAW, mx, my);
        y = option(g, y, Slot.MOUTH, mx, my);
        y = option(g, y, Slot.BROWS, mx, my);
        fineTune(g, y + 4, mx, my);
    }

    private void drawBody(GuiGraphics g, int y, int mx, int my) {
        y = color(g, y, Tone.SKIN, mx, my);
        y = option(g, y, Slot.MUSCLE, mx, my);
        y = option(g, y, Slot.SCAR, mx, my);
        y = option(g, y, Slot.WEATHERING, mx, my);
        y = option(g, y, Slot.MARKING, mx, my);
        y = option(g, y, Slot.UNDERWEAR, mx, my);
        y = color(g, y, Tone.UNDERWEAR, mx, my);
        y = heightRow(g, y, mx, my);
        fineTune(g, y + 4, mx, my);
    }

    private void drawHair(GuiGraphics g, int y, int mx, int my) {
        y = option(g, y, Slot.HAIR, mx, my);
        y = option(g, y, Slot.HAIR_LENGTH, mx, my);
        y = option(g, y, Slot.HAIR_VOLUME, mx, my);
        y = color(g, y, Tone.HAIR, mx, my);
        fineTune(g, y + 4, mx, my);
    }

    private void drawClothes(GuiGraphics g, int y, int mx, int my) {
        y = option(g, y, Slot.TOP, mx, my);
        y = color(g, y, Tone.TOP, mx, my);
        y = option(g, y, Slot.BOTTOM, mx, my);
        y = color(g, y, Tone.BOTTOM, mx, my);
        y = option(g, y, Slot.FEET, mx, my);
        y = option(g, y, Slot.WRAPS, mx, my);
        y += 2;
        for (FormattedCharSequence line : font.split(Component.translatable("creator.noirm.clothes_note"), pw - 16)) {
            g.drawString(font, line, px + 8, y, FAINT, false);
            y += 10;
        }
        fineTune(g, y + 2, mx, my);
    }

    /** "Label   <  value  >": click the arrows (or scroll over the row) to change it. */
    private int option(GuiGraphics g, int y, Slot slot, int mx, int my) {
        int x = px + 8;
        int right = px + pw - 8;
        boolean hover = mx >= x && mx < right && my >= y && my < y + ROW - 1;
        if (hover) {
            g.fill(x - 2, y, right + 2, y + ROW - 1, 0x18FFFFFF);
        }
        g.drawString(font, cut(Component.translatable("creator.noirm.slot." + slot.key()), LABEL_W - 4), x, y + 2, DIM, false);
        int bx = x + LABEL_W;
        int bw = right - bx;
        g.fill(bx, y, bx + bw, y + ROW - 2, 0xFF1A1D22);
        g.renderOutline(bx, y, bw, ROW - 2, 0xFF3A4048);
        int value = draft.get(slot);
        Component text = Component.translatable(slot.translationKey(value));
        g.drawCenteredString(font, cut(text, bw - 24), bx + bw / 2, y + 2, PAPER);
        boolean overLeft = hover && mx < bx + 10;
        boolean overRight = hover && mx >= bx + bw - 10;
        g.drawString(font, "<", bx + 3, y + 2, overLeft ? CHAKRA : FAINT, false);
        g.drawString(font, ">", bx + bw - 8, y + 2, overRight ? CHAKRA : FAINT, false);
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
        int x = px + 8;
        boolean active = tone == activeTone;
        Component label = Component.translatable("creator.noirm.tone." + tone.key());
        g.drawString(font, cut(label, LABEL_W - 4), x, y + 2, active ? CHAKRA : DIM, false);
        hits.add(new Hit(x, y, LABEL_W, ROW - 1, () -> activeTone = tone, null));
        int sx = x + LABEL_W;
        int current = draft.color(tone);
        int[] palette = tone.palette();
        int size = 9;
        int room = (px + pw - 8 - sx) / (size + 1);
        boolean inPalette = false;
        for (int i = 0; i < palette.length && i < room - 1; i++) {
            int c = palette[i];
            int cx = sx + i * (size + 1);
            g.fill(cx, y + 1, cx + size, y + 1 + size, 0xFF000000 | c);
            if (c == current) {
                inPalette = true;
                g.renderOutline(cx - 1, y, size + 2, size + 2, PAPER);
            } else if (mx >= cx && mx < cx + size && my >= y + 1 && my < y + 1 + size) {
                g.renderOutline(cx - 1, y, size + 2, size + 2, IRON);
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
            g.renderOutline(cx - 1, y, size + 2, size + 2, inPalette ? 0xFF3A4048 : PAPER);
            hits.add(new Hit(cx, y + 1, size, size, () -> activeTone = tone,
                    List.of(Component.literal(String.format("#%06X", current)))));
        }
        return y + ROW;
    }

    /** Hue / saturation / brightness bars for the active colour. */
    private void fineTune(GuiGraphics g, int y, int mx, int my) {
        if (y + 40 > vy + vh) {
            return;
        }
        Tone tone = activeTone;
        int x = px + 8;
        g.drawString(font, cut(Component.translatable("creator.noirm.fine_tune",
                Component.translatable("creator.noirm.tone." + tone.key())), pw - 16), x, y, FAINT, false);
        y += 11;
        float[] hsv = hsv(draft.color(tone));
        String[] names = {"creator.noirm.hue", "creator.noirm.saturation", "creator.noirm.brightness"};
        for (int i = 0; i < 3; i++) {
            int bx = x + LABEL_W;
            int bw = px + pw - 8 - bx;
            g.drawString(font, cut(Component.translatable(names[i]), LABEL_W - 4), x, y, DIM, false);
            int segments = 24;
            for (int s = 0; s < segments; s++) {
                float t = (s + 0.5f) / segments;
                float[] c = hsv.clone();
                c[i] = t;
                int x0 = bx + bw * s / segments;
                int x1 = bx + bw * (s + 1) / segments;
                g.fill(x0, y + 1, x1, y + 7, 0xFF000000 | Mth.hsvToRgb(c[0], c[1], c[2]));
            }
            g.renderOutline(bx - 1, y, bw + 2, 8, 0xFF3A4048);
            int knob = bx + (int) (hsv[i] * (bw - 1));
            g.fill(knob - 1, y - 1, knob + 2, y + 9, PAPER);
            int channel = i;
            bars.add(new Bar("hsv" + i, bx, y, bw, 8, v -> {
                float[] now = hsv(draft.color(tone));
                now[channel] = v;
                draft.setColor(tone, Mth.hsvToRgb(now[0], now[1], Math.max(0.02f, now[2])));
                changed();
            }));
            y += 11;
        }
    }

    private int heightRow(GuiGraphics g, int y, int mx, int my) {
        int x = px + 8;
        g.drawString(font, cut(Component.translatable("creator.noirm.height"), LABEL_W - 4), x, y + 2, DIM, false);
        int bx = x + LABEL_W;
        int bw = px + pw - 8 - bx - 30;
        float t = (draft.height() - PlayerLook.MIN_HEIGHT) / (PlayerLook.MAX_HEIGHT - PlayerLook.MIN_HEIGHT);
        g.fill(bx, y + 4, bx + bw, y + 6, 0xFF3A4048);
        g.fill(bx, y + 4, bx + (int) (t * bw), y + 6, CHAKRA);
        int mid = bx + bw / 2;
        g.fill(mid, y + 2, mid + 1, y + 8, FAINT);
        int knob = bx + (int) (t * (bw - 1));
        g.fill(knob - 2, y + 1, knob + 3, y + 9, PAPER);
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
        int x = px + 8;
        int w = pw - 16;
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
        int room = vy + vh - 24 - y;
        int step = Math.min(cardH + 3, Math.max(22, room / Math.max(1, seen)));
        for (int i = 0; i < seen; i++) {
            rollCard(g, x, y, w, rolls.get(i), i == state.chosen(), i, mx, my);
            y += step;
        }
        // reroll button
        int by = vy + vh - 20;
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
        g.fill(x, y, x + w, y + h, chosen ? 0xFF2C3440 : hover ? 0xFF262B32 : 0xFF1C1F24);
        g.renderOutline(x, y, w, h, chosen ? CHAKRA : 0xFF3A4048);
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
        } else if (!roll.traits().isEmpty()) {
            String t = "+" + roll.traits().size();
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

    // ---------------------------------------------------------------- footer

    private void drawFooter(GuiGraphics g, int mx, int my) {
        int y = height - 24;
        button(g, vx, y, vw, 16, Component.translatable("creator.noirm.randomize"), true, mx, my, this::randomize, null);
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
        button(g, cx, y, cw, 16, confirm, true, mx, my, this::confirm, tip, true);
        if (!held()) {
            Component cancel = Component.translatable("creator.noirm.cancel");
            int w = font.width(cancel) + 16;
            button(g, cx - w - 6, y, w, 16, cancel, true, mx, my, this::cancel, null);
        } else {
            Component note = Component.translatable("creator.noirm.held");
            g.drawString(font, cut(note, cx - px - 8), px, y + 4, FAINT, false);
        }
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, Component label, boolean enabled, int mx, int my, Runnable click,
                        List<Component> tip) {
        button(g, x, y, w, h, label, enabled, mx, my, click, tip, false);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, Component label, boolean enabled, int mx, int my, Runnable click,
                        List<Component> tip, boolean primary) {
        boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
        int top = primary ? (hover ? 0xFF3C6A90 : 0xFF2E5474) : (hover ? 0xFF3A414A : 0xFF2A2F36);
        int bottom = primary ? 0xFF203A52 : 0xFF1C2026;
        g.fillGradient(x, y, x + w, y + h, enabled ? top : 0xFF202328, enabled ? bottom : 0xFF1A1C20);
        g.renderOutline(x, y, w, h, primary ? CHAKRA : IRON);
        g.drawCenteredString(font, cut(label, w - 6), x + w / 2, y + (h - 8) / 2, enabled ? PAPER : FAINT);
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
        if (button == 0) {
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
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.25f));
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
        // scrolling over an option row cycles it
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
        super.onClose();
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
