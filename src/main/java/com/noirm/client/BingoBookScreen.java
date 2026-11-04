package com.noirm.client;

import com.noirm.bounty.BountyPackets;
import com.noirm.bounty.NinjaClass;
import com.noirm.client.ui.Anim;
import com.noirm.client.ui.HoverTracker;
import com.noirm.client.ui.NoirmStyled;
import com.noirm.client.ui.ScreenMotion;
import com.noirm.client.ui.Theme;
import com.noirm.client.ui.Ui;
import com.noirm.economy.Ryo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The bingo book: on the left the pages (class, name, bounty; claimed ones crossed out), on the
 * right the open page: a big class seal, the name and clan, what they're known for, where they
 * were last seen and the bounty, or a red CLAIMED stamp and who claimed it. Legends get a gold
 * page, their own 伝 seal with a slow glow, and a gold shimmer across their line in the list.
 * Drawn with the NOIRM kit ({@code client.ui}); turning a page cross-fades it.
 */
public class BingoBookScreen extends Screen implements NoirmStyled {
    private static final int MAX_W = 380;
    private static final int MAX_H = 236;
    private static final int PAD = Theme.S5;
    private static final int ROW_H = 24;
    private static final int LIST_W = 150;
    private static final int LEGEND = NinjaClass.LEGENDARY_COLOR;
    /** The stamp's red ink. */
    private static final int STAMP = 0xFFD8443A;
    /** Shimmer: one pass across a legend's line every this many ms. */
    private static final int SHIMMER_MS = 2600;

    private BountyPackets.BookInfo info;
    private int selected;
    private int scroll;
    private int left;
    private int top;
    private int w;
    private int h;
    private int visibleRows = 1;
    /** List rows drawn this frame: (page index, y). */
    private final List<int[]> rowHits = new ArrayList<>();
    private int listX;
    private int listW;
    private final ScreenMotion motion = new ScreenMotion();
    private final HoverTracker hover = new HoverTracker();
    /** A cut name or trait under the mouse: shown whole as a tooltip. */
    private List<Component> tip;

    public BingoBookScreen(BountyPackets.BookInfo info) {
        super(Component.translatable("screen.noirm.bingo.title"));
        this.info = info;
    }

    public static void receive(BountyPackets.BookInfo info) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BingoBookScreen screen) {
            screen.info = info;
        } else {
            mc.setScreen(new BingoBookScreen(info));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        w = Math.min(MAX_W, width - 16);
        h = Math.min(MAX_H, height - 8);
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        rowHits.clear();
        tip = null;
        motion.scrim(g, width, height);
        motion.begin(g);
        Ui.panel(g, left, top, w, h);
        int x = left + PAD;
        int iw = w - PAD * 2;
        // Header: a blood seal with 賞 (bounty), the title, then your own class.
        Component yours = Component.translatable("screen.noirm.bingo.your_class", NinjaClass.tag(info.readerCode()));
        Ui.header(g, font, x, top + PAD, iw, "賞", Theme.SEAL, title, yours, Theme.TEXT_DIM);
        List<BountyPackets.Page> pages = info.pages();
        int contentTop = top + PAD + Theme.ROW + Theme.S3;
        int bottom = top + h - Theme.S4;
        if (pages.isEmpty()) {
            Ui.textCentered(g, font, Component.translatable("screen.noirm.bingo.empty").getString(), left + w / 2, top + h / 2, Theme.TEXT_FAINT);
            motion.end(g);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        selected = Mth.clamp(selected, 0, pages.size() - 1);
        int lw = Math.min(LIST_W, iw * 2 / 5);
        drawList(g, pages, x, contentTop, lw, bottom, mouseX, mouseY);
        // The spine between the pages.
        Ui.vline(g, x + lw + Theme.S3, contentTop, bottom - contentTop);
        int pageX = x + lw + Theme.S5 + 1;
        motion.beginContent(g);
        drawPage(g, pages.get(selected), pageX, contentTop, x + iw - pageX, bottom - contentTop, mouseX, mouseY);
        motion.endContent(g);
        motion.end(g);
        super.render(g, mouseX, mouseY, partialTick);
        if (tip != null && !motion.closing()) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            tip.forEach(line -> lines.addAll(font.split(line, 220)));
            g.renderTooltip(font, lines, mouseX, mouseY);
        }
    }

    private void drawList(GuiGraphics g, List<BountyPackets.Page> pages, int x, int y, int w, int bottom, int mouseX, int mouseY) {
        listX = x;
        listW = w;
        int visible = Math.max(1, (bottom - y + 2) / ROW_H);
        visibleRows = visible;
        scroll = Mth.clamp(scroll, 0, Math.max(0, pages.size() - visible));
        boolean scrolls = pages.size() > visible;
        int rw = w - (scrolls ? 5 : 0);
        int rh = ROW_H - 2;
        for (int i = 0; i < visible && scroll + i < pages.size(); i++) {
            int index = scroll + i;
            BountyPackets.Page p = pages.get(index);
            int ry = y + i * ROW_H;
            boolean over = !motion.closing() && Ui.in(mouseX, mouseY, x, ry, rw, rh);
            float hv = hover.get(HoverTracker.key(1, p.id()), over);
            int color = 0xFF000000 | NinjaClass.color(p.code());
            boolean legend = p.legendary() && !p.claimed();
            Ui.row(g, x, ry, rw, rh, hv, index == selected, legend ? LEGEND : color);
            if (legend) {
                // A legend's line: a faint gold wash, a gold edge and a slow shimmer passing over it.
                g.fill(x + 1, ry, x + rw, ry + rh, Theme.alpha(LEGEND, 0.07f));
                g.fill(x, ry, x + 1, ry + rh, LEGEND);
                shimmer(g, x, ry, rw, rh, p.id());
            }
            // The class letter on a tint of its colour.
            g.fill(x + 3, ry + 3, x + 20, ry + rh - 3, Theme.mix(Theme.RAISED, color, p.claimed() ? 0.08f : 0.22f));
            Ui.textCentered(g, font, NinjaClass.letter(p.code()), x + 12, ry + 7, p.claimed() ? Theme.TEXT_FAINT : color);
            Component name = p.claimed() ? p.name().copy().withStyle(s -> s.withStrikethrough(true)) : p.name();
            int textW = rw - 30;
            int nameColor = p.claimed() ? Theme.TEXT_FAINT : legend ? LEGEND : Theme.mix(Theme.TEXT_DIM, Theme.TEXT, 0.75f + 0.25f * hv);
            boolean cut = Ui.text(g, font, name, x + 24, ry + 3, textW, nameColor);
            Component sub = p.claimed() ? Component.translatable("screen.noirm.bingo.claimed_short", p.claimedBy())
                    : Component.translatable("screen.noirm.bingo.bounty_short", Ryo.format(p.bounty()));
            cut |= Ui.text(g, font, sub, x + 24, ry + 12, textW, p.claimed() ? Theme.mix(Theme.RED, Theme.TEXT_FAINT, 0.45f) : Theme.TEXT_DIM);
            if (cut && over) {
                tip = List.of(p.name(), sub);
            }
            rowHits.add(new int[]{index, ry, rw});
        }
        Ui.scrollbar(g, x + w - 1, y, visible * ROW_H - 2, scroll, visible, pages.size(), Theme.IRON);
    }

    /** A soft light band sweeping left to right across a legend's row, time-based; each legend is a little out of step. */
    private static void shimmer(GuiGraphics g, int x, int y, int w, int h, int id) {
        long now = Anim.now() + (id * 397L & 1023);
        float t = (now % (SHIMMER_MS * 2L)) / (float) SHIMMER_MS; // 0..1 sweeping, 1..2 resting
        if (t >= 1f) {
            return;
        }
        int band = 28;
        int cx = x - band + Math.round(Anim.easeInOutCubic(t) * (w + band * 2));
        g.enableScissor(x, y, x + w, y + h);
        for (int i = -band / 2; i < band / 2; i += 2) {
            float a = 1f - Math.abs(i) / (band / 2f);
            g.fill(cx + i, y, cx + i + 2, y + h, Theme.alpha(LEGEND, 0.10f * a * a));
        }
        g.disableScissor();
    }

    private void drawPage(GuiGraphics g, BountyPackets.Page p, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean legend = p.legendary();
        int accent = legend ? LEGEND : Theme.GOLD;
        // The paper: a flat raised sheet, or a gold-washed page with a gold hairline for a legend.
        if (legend) {
            g.fill(x, y, x + w, y + h, Theme.mix(Theme.RAISED, LEGEND, 0.08f));
            g.fillGradient(x, y, x + w, y + h / 2, Theme.alpha(LEGEND, 0.10f), Theme.alpha(LEGEND, 0f));
            Ui.outline(g, x, y, w, h, Theme.mix(Theme.HAIRLINE, LEGEND, 0.75f));
        } else {
            g.fill(x, y, x + w, y + h, Theme.RAISED);
        }
        int color = 0xFF000000 | NinjaClass.color(p.code());
        // The class seal, top right: a big letter in a square, or the legend's 伝 seal with a slow glow.
        int sx = x + w - 40, sy = y + 8;
        if (legend) {
            float glow = 0.5f + 0.5f * Mth.sin((Anim.now() % 3000L) / 3000f * Mth.TWO_PI);
            g.fill(sx - 2, sy - 2, sx + 34, sy + 34, Theme.alpha(LEGEND, 0.06f + 0.10f * glow));
            g.fill(sx, sy, sx + 32, sy + 32, Theme.mix(Theme.RAISED, LEGEND, 0.45f));
            Ui.outline(g, sx, sy, 32, 32, Theme.mix(LEGEND, 0xFFFFF0C0, 0.4f * glow));
        } else {
            g.fill(sx, sy, sx + 32, sy + 32, Theme.mix(Theme.RAISED, color, 0.22f));
            Ui.outline(g, sx, sy, 32, 32, Theme.mix(Theme.HAIRLINE, color, 0.7f));
        }
        g.pose().pushPose();
        g.pose().translate(sx + 16, sy + 9, 0);
        g.pose().scale(2f, 2f, 1f);
        String big = legend ? "伝" : NinjaClass.letter(p.code());
        Ui.textCentered(g, font, big, 0, 0, legend ? 0xFFFFF0C0 : color);
        g.pose().popPose();
        Component classLine = legend ? Component.translatable("screen.noirm.bingo.legendary")
                : Component.translatable("screen.noirm.bingo.class", NinjaClass.letter(p.code()));
        int labelX = Math.min(sx + 16, x + w - 4 - font.width(classLine) / 2); // keep it inside the page
        Ui.textCentered(g, font, classLine.getString(), labelX, sy + 36, legend ? LEGEND : color);
        int tx = x + Theme.S4;
        int ty = y + Theme.S4;
        int textW = w - 56;
        List<FormattedCharSequence> nameLines = font.split(p.name(), textW);
        for (int i = 0; i < Math.min(2, nameLines.size()); i++) {
            g.drawString(font, nameLines.get(i), tx, ty, legend ? LEGEND : Theme.TEXT, false);
            ty += Theme.LINE;
        }
        if (nameLines.size() > 2 && Ui.in(mouseX, mouseY, tx, y + Theme.S4, textW, 20)) {
            tip = List.of(p.name());
        }
        Component clan = Component.translatable("screen.noirm.bingo.clan", p.clan());
        if (Ui.text(g, font, clan, tx, ty + 1, textW, Theme.TEXT_DIM) && Ui.in(mouseX, mouseY, tx, ty, textW, 10)) {
            tip = List.of(clan);
        }
        ty = Math.max(ty + 16, y + 56);
        int rule = legend ? Theme.mix(Theme.HAIRLINE, LEGEND, 0.5f) : Theme.HAIRLINE;
        g.fill(x + Theme.S3, ty, x + w - Theme.S3, ty + 1, rule);
        ty += Theme.S3;
        label(g, tx, ty, "screen.noirm.bingo.known_for", accent);
        ty += 11;
        // The bounty sits at the bottom; "last seen" just above it; the traits get what's left.
        int by = y + h - 30;
        List<FormattedCharSequence> seen = font.split(p.seen(), w - 16);
        int traitsEnd = by - 8 - 11 - Theme.LINE * seen.size() - 4;
        List<FormattedCharSequence> traitLines = new ArrayList<>();
        for (Component trait : p.traits()) {
            traitLines.addAll(font.split(Component.literal("• ").append(trait), w - 16));
        }
        int room = Math.max(0, (traitsEnd - ty + 1) / Theme.LINE);
        int traitsTop = ty;
        for (int i = 0; i < Math.min(room, traitLines.size()); i++) {
            if (i == room - 1 && traitLines.size() > room) {
                // Not all of it fits: the last line we have room for ends in "…", the whole list is a tooltip.
                StringBuilder sb = new StringBuilder();
                traitLines.get(i).accept((index, style, cp) -> {
                    sb.appendCodePoint(cp);
                    return true;
                });
                g.drawString(font, Ui.fit(font, sb + "…", w - 16), tx, ty, Theme.TEXT, false);
                if (Ui.in(mouseX, mouseY, tx, traitsTop, w - 16, ty + Theme.LINE - traitsTop)) {
                    List<Component> all = new ArrayList<>();
                    p.traits().forEach(t -> all.add(Component.literal("• ").append(t)));
                    tip = all;
                }
            } else {
                g.drawString(font, traitLines.get(i), tx, ty, Theme.TEXT, false);
            }
            ty += Theme.LINE;
        }
        ty += 4;
        label(g, tx, ty, "screen.noirm.bingo.last_seen", accent);
        ty += 11;
        for (FormattedCharSequence line : seen) {
            g.drawString(font, line, tx, ty, Theme.mix(Theme.TEXT_DIM, Theme.TEXT, 0.6f), false);
            ty += Theme.LINE;
        }
        // The bounty at the bottom, big; or the claimed stamp.
        g.fill(x + Theme.S3, by - 4, x + w - Theme.S3, by - 3, rule);
        g.drawString(font, Component.translatable("screen.noirm.bingo.bounty"), tx, by + 5, Theme.TEXT_DIM, false);
        Component amount = Component.translatable("ryo.noirm.amount", Ryo.format(p.bounty()));
        g.pose().pushPose();
        g.pose().translate(x + w - 8 - font.width(amount) * 1.5f, by + 2, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.drawString(font, amount, 0, 0, p.claimed() ? Theme.TEXT_FAINT : accent, false);
        g.pose().popPose();
        if (p.claimed()) {
            // A red stamp across the page: CLAIMED, and by whom.
            g.pose().pushPose();
            g.pose().translate(x + w / 2f, y + h / 2f + 6, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-14f));
            Component stamp = Component.translatable("screen.noirm.bingo.stamp");
            int sw = font.width(stamp) * 2 + 16;
            g.fill(-sw / 2, -14, sw / 2, 14, 0x60200606);
            Ui.outline(g, -sw / 2, -14, sw, 28, Theme.fade(STAMP, 0.85f));
            Ui.outline(g, -sw / 2 + 2, -12, sw - 4, 24, Theme.fade(STAMP, 0.35f));
            g.pose().pushPose();
            g.pose().scale(2f, 2f, 1f);
            Ui.textCentered(g, font, stamp.getString(), 0, -4, STAMP);
            g.pose().popPose();
            g.pose().popPose();
            Component by2 = Component.translatable("screen.noirm.bingo.claimed_by", p.claimedBy());
            Ui.textCentered(g, font, Ui.fit(font, by2.getString(), w - 8), x + w / 2, y + h / 2 + 28, STAMP);
        }
    }

    /** A small upper-case label ("KNOWN FOR", "LAST SEEN") in the page's accent. */
    private void label(GuiGraphics g, int x, int y, String key, int color) {
        g.drawString(font, Component.translatable(key).getString().toUpperCase(java.util.Locale.ROOT), x, y, color, false);
    }

    private void select(int index) {
        int next = Mth.clamp(index, 0, Math.max(0, info.pages().size() - 1));
        if (next != selected) {
            selected = next;
            motion.tabChanged();
        }
        // Keep the open page's line in view.
        if (selected < scroll) {
            scroll = selected;
        } else if (selected >= scroll + visibleRows) {
            scroll = selected - visibleRows + 1;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (motion.closing()) {
            return true;
        }
        if (button == 0) {
            for (int[] row : rowHits) {
                if (Ui.in(mouseX, mouseY, listX, row[1], row[2], ROW_H - 2)) {
                    select(row[0]);
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1f));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll -= (int) Math.signum(delta);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN || key == org.lwjgl.glfw.GLFW.GLFW_KEY_S) {
            select(selected + 1);
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || key == org.lwjgl.glfw.GLFW.GLFW_KEY_W) {
            select(selected - 1);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
}
