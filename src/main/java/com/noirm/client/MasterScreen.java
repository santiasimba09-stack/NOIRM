package com.noirm.client;

import com.noirm.master.MasterPackets;
import com.noirm.network.NoirmNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Talking to a master: who they are and what they say, their test (a spar, with a button to
 * start it) and the lessons they teach, each with what it needs, why it's locked, or a Learn
 * button. Same ink-and-paper look as the ally screen; text is measured and cut with "…", with the
 * whole text in a tooltip.
 */
public class MasterScreen extends Screen {
    private static final int W = 340;
    private static final int H = 206;
    private static final int ROW_H = 24;
    private static final int INK = 0xF4100E14;
    private static final int INK_LIGHT = 0xF41A1720;
    private static final int CARD = 0xFF17141C;
    private static final int CARD_HOVER = 0xFF221E29;
    private static final int FRAME = 0xFF3D3645;
    private static final int RULE = 0xFF2E2935;
    private static final int PAPER = 0xFFEDE4D3;
    private static final int DIM = 0xFF8F8796;
    private static final int FAINT = 0xFF5E5866;
    private static final int GOLD = 0xFFE8C060;
    private static final int GREEN = 0xFF6FD38A;
    private static final int RED = 0xFFE05A5A;
    private static final int LEAF = 0xFF4FA048;
    private static final int SEAL = 0xFF3E7A2E;

    private record Hit(int x, int y, int w, int h, Runnable click, List<Component> tip) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private MasterPackets.Info info;
    private final List<Hit> hits = new ArrayList<>();
    private int scroll;
    private int left;
    private int top;

    public MasterScreen(MasterPackets.Info info) {
        super(Component.translatable(info.nameKey()));
        this.info = info;
    }

    /** New data from the server: open the screen, or refresh it if it's already open for this master. */
    public static void receive(MasterPackets.Info info) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MasterScreen screen && screen.info.entityId() == info.entityId()) {
            screen.info = info;
        } else {
            minecraft.setScreen(new MasterScreen(info));
        }
    }

    public static void closeIfOpen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MasterScreen screen) {
            screen.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
    }

    private void send(MasterPackets.Action action, String arg) {
        NoirmNetwork.sendToServer(new MasterPackets.Command(info.entityId(), action, arg));
    }

    // ================================================================ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        hits.clear();
        int x = left;
        int y = top;
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, 0xC0000000);
        g.fillGradient(x, y, x + W, y + H, INK_LIGHT, INK);
        for (int ly = y + 30; ly < y + H - 4; ly += 6) {
            g.fill(x + 3, ly, x + W - 3, ly + 1, 0x06FFFFFF);
        }
        g.renderOutline(x, y, W, H, FRAME);
        g.renderOutline(x + 2, y + 2, W - 4, H - 4, 0xFF221E28);
        int after = drawHeader(g, x, y);
        after = drawTest(g, x + 8, after + 4, W - 16, mouseX, mouseY);
        drawLessons(g, x + 8, after + 6, W - 16, y + H - 6, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.tip() != null && !hit.tip().isEmpty() && hit.contains(mouseX, mouseY)) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                for (Component line : hit.tip()) {
                    lines.addAll(font.split(line, 220));
                }
                g.renderTooltip(font, lines, mouseX, mouseY);
                break;
            }
        }
    }

    /** Name, style and standing, then the greeting. Returns the y below it. */
    private int drawHeader(GuiGraphics g, int x, int y) {
        // A leaf-green seal with 師 (teacher), then the name.
        g.fill(x + 8, y + 6, x + 22, y + 20, SEAL);
        g.renderOutline(x + 8, y + 6, 14, 14, 0xFF234A1A);
        g.drawCenteredString(font, "師", x + 15, y + 9, 0xFFF4E6D0);
        // Right: the style chip and whether you passed.
        Component standing = Component.translatable(info.passed() ? "screen.noirm.master.passed" : "screen.noirm.master.untested");
        int standingW = font.width(standing);
        g.drawString(font, standing, x + W - 8 - standingW, y + 10, info.passed() ? GREEN : DIM, false);
        int chipRight = x + W - 14 - standingW;
        if (!info.styleKey().isEmpty()) {
            String style = fit(Component.translatable(info.styleKey()).getString(), 90);
            int chipW = font.width(style) + 10;
            int chipX = chipRight - chipW;
            g.fill(chipX, y + 7, chipRight, y + 19, 0xFF1F3A1C);
            g.renderOutline(chipX, y + 7, chipW, 12, LEAF);
            g.drawString(font, style, chipX + 5, y + 9, 0xFFB8E8A8, false);
            chipRight = chipX - 6;
        }
        g.drawString(font, fit(title.getString(), chipRight - (x + 28)), x + 28, y + 10, GOLD, false);
        g.fill(x + 6, y + 24, x + W - 6, y + 25, RULE);
        // The greeting, wrapped, at most three lines.
        int ty = y + 30;
        if (!info.greetingKey().isEmpty()) {
            List<FormattedCharSequence> lines = font.split(Component.translatable(info.greetingKey()).withStyle(s -> s.withItalic(true)), W - 20);
            for (int i = 0; i < Math.min(3, lines.size()); i++) {
                g.drawString(font, lines.get(i), x + 10, ty, PAPER, false);
                ty += 10;
            }
        }
        return ty;
    }

    /** The test card: what passing takes, and the Spar button. Returns the y below it. */
    private int drawTest(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        int h = 34;
        g.fill(x, y, x + w, y + h, CARD);
        g.renderOutline(x, y, w, h, RULE);
        int buttonW = Math.max(64, font.width(Component.translatable("screen.noirm.master.spar")) + 16);
        int textW = w - buttonW - 18;
        g.drawString(font, fit(Component.translatable("screen.noirm.master.test").getString(), textW), x + 6, y + 5, GOLD, false);
        Component rules = Component.translatable("screen.noirm.master.test.rules", info.hits(), info.postureBreaks(), info.seconds());
        g.drawString(font, fit(rules.getString(), textW), x + 6, y + 18, DIM, false);
        hits.add(new Hit(x, y, w - buttonW - 10, h, null, List.of(rules, Component.translatable("screen.noirm.master.test.safe"))));
        // The button: live if you can spar now; otherwise greyed, with the reason.
        boolean ready = info.sparBlocker().getString().isEmpty();
        int bx = x + w - buttonW - 6;
        int by = y + 9;
        boolean hover = ready && mouseX >= bx && mouseX < bx + buttonW && mouseY >= by && mouseY < by + 16;
        g.fill(bx, by, bx + buttonW, by + 16, ready ? (hover ? 0xFF4A8A3C : 0xFF3A6E2E) : 0xFF2A2630);
        g.renderOutline(bx, by, buttonW, 16, ready ? 0xFF8AD07A : FRAME);
        g.drawCenteredString(font, Component.translatable("screen.noirm.master.spar"), bx + buttonW / 2, by + 4, ready ? PAPER : FAINT);
        hits.add(new Hit(bx, by, buttonW, 16, ready ? () -> send(MasterPackets.Action.SPAR, "") : null,
                ready ? List.of(Component.translatable("screen.noirm.master.spar.tip")) : List.of(info.sparBlocker())));
        return y + h;
    }

    private void drawLessons(GuiGraphics g, int x, int y, int w, int bottom, int mouseX, int mouseY) {
        g.drawString(font, Component.translatable("screen.noirm.master.lessons"), x, y, GOLD, false);
        int listTop = y + 12;
        List<MasterPackets.LessonRow> rows = info.lessons();
        if (rows.isEmpty()) {
            g.drawString(font, Component.translatable("screen.noirm.master.no_lessons"), x + 4, listTop + 4, DIM, false);
            return;
        }
        int visible = Math.max(1, (bottom - listTop) / ROW_H);
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - visible));
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            drawLesson(g, rows.get(scroll + i), x, listTop + i * ROW_H, w - 6, mouseX, mouseY);
        }
        if (rows.size() > visible) {
            // A thin scroll bar on the right.
            int trackH = visible * ROW_H - 2;
            int barH = Math.max(10, trackH * visible / rows.size());
            int barY = listTop + (trackH - barH) * scroll / Math.max(1, rows.size() - visible);
            g.fill(x + w - 3, listTop, x + w - 1, listTop + trackH, RULE);
            g.fill(x + w - 3, barY, x + w - 1, barY + barH, DIM);
        }
    }

    private void drawLesson(GuiGraphics g, MasterPackets.LessonRow row, int x, int y, int w, int mouseX, int mouseY) {
        int h = ROW_H - 2;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        g.fill(x, y, x + w, y + h, hover ? CARD_HOVER : CARD);
        g.fill(x, y, x + 2, y + h, row.status() == MasterPackets.LEARNED ? GREEN : row.status() == MasterPackets.READY ? GOLD : FAINT);
        // Right side first, so the name knows how much room it has.
        int rightW;
        Component name = Component.translatable(com.noirm.dojutsu.Sharingan.jutsuKey(row.jutsu()));
        List<Component> tip = new ArrayList<>();
        tip.add(name.copy().withStyle(s -> s.withColor(GOLD)));
        tip.add(Component.translatable("screen.noirm.master.needs", row.needs()));
        if (row.status() == MasterPackets.READY) {
            Component learn = Component.translatable("screen.noirm.master.learn");
            rightW = font.width(learn) + 14;
            int bx = x + w - rightW - 4;
            int by = y + 3;
            boolean onButton = mouseX >= bx && mouseX < bx + rightW && mouseY >= by && mouseY < by + h - 6;
            g.fill(bx, by, bx + rightW, by + h - 6, onButton ? 0xFF8A6A20 : 0xFF6A5018);
            g.renderOutline(bx, by, rightW, h - 6, GOLD);
            g.drawCenteredString(font, learn, bx + rightW / 2, by + (h - 6 - 8) / 2, PAPER);
            hits.add(new Hit(bx, by, rightW, h - 6, () -> send(MasterPackets.Action.LEARN, row.id()),
                    List.of(Component.translatable("screen.noirm.master.learn.tip", name))));
            rightW += 8;
        } else if (row.status() == MasterPackets.LEARNED) {
            Component done = Component.translatable("screen.noirm.master.learned");
            rightW = font.width(done) + 8;
            g.drawString(font, done, x + w - rightW, y + (h - 8) / 2, GREEN, false);
        } else {
            rightW = Math.min(130, w / 2 - 10);
            String why = fit(row.blocker().getString(), rightW - 4);
            g.drawString(font, why, x + w - 4 - font.width(why), y + (h - 8) / 2, RED, false);
            tip.add(row.blocker().copy().withStyle(s -> s.withColor(RED)));
            rightW += 4;
        }
        // Left: name with the rank, then what it needs.
        String rank = row.rank();
        int rankW = font.width(rank) + 6;
        int nameW = w - rightW - rankW - 14;
        String shown = fit(name.getString(), nameW);
        g.drawString(font, shown, x + 6, y + 3, PAPER, false);
        int chipX = x + 8 + font.width(shown) + 2;
        g.fill(chipX, y + 2, chipX + rankW, y + 11, 0xFF2A2630);
        g.drawString(font, rank, chipX + 3, y + 3, GOLD, false);
        g.drawString(font, fit(row.needs().getString(), w - rightW - 10), x + 6, y + 13, DIM, false);
        hits.add(new Hit(x, y, w - rightW, h, null, tip));
    }

    // ================================================================ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit hit = hits.get(i);
                if (hit.click() != null && hit.contains(mouseX, mouseY)) {
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
                    hit.click().run();
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

    private String fit(String text, int width) {
        if (width <= 0) {
            return "";
        }
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }
}
