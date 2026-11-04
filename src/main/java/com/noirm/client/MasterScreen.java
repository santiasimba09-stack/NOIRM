package com.noirm.client;

import com.noirm.client.ui.HoverTracker;
import com.noirm.client.ui.NoirmStyled;
import com.noirm.client.ui.ScreenMotion;
import com.noirm.client.ui.Theme;
import com.noirm.client.ui.Ui;
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
 * button. Drawn with the NOIRM kit ({@code client.ui}): one flat panel, flat cards and rows, eased
 * hover, a fade on open and close. Text is measured and cut with "…", with the whole text in a
 * tooltip.
 */
public class MasterScreen extends Screen implements NoirmStyled {
    private static final int MAX_W = 340;
    private static final int MAX_H = 206;
    private static final int PAD = Theme.S5;
    private static final int ROW_H = 24;
    private static final int BUTTON_H = Theme.ROW;
    /** The masters' leaf green: their seal and style chip. */
    private static final int LEAF = 0xFF3E7A2E;
    private static final int LEAF_LIGHT = 0xFF8FCB7A;

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
    private int w;
    private int h;
    private final ScreenMotion motion = new ScreenMotion();
    private final HoverTracker hover = new HoverTracker();

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

    private void send(MasterPackets.Action action, String arg) {
        NoirmNetwork.sendToServer(new MasterPackets.Command(info.entityId(), action, arg));
    }

    // ================================================================ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        motion.scrim(g, width, height);
        motion.begin(g);
        Ui.panel(g, left, top, w, h);
        int x = left + PAD;
        int iw = w - PAD * 2;
        int after = drawHeader(g, x, top + PAD, iw);
        after = drawTest(g, x, after + Theme.S2, iw, mouseX, mouseY);
        drawLessons(g, x, after + Theme.S4, iw, top + h - Theme.S4, mouseX, mouseY);
        motion.end(g);
        super.render(g, mouseX, mouseY, partialTick);
        if (motion.closing()) {
            return;
        }
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

    /** Seal, name, style and standing, then the greeting. Returns the y below it. */
    private int drawHeader(GuiGraphics g, int x, int y, int w) {
        // A leaf-green seal with 師 (teacher), then the name.
        Ui.seal(g, font, x, y, "師", LEAF);
        // Right: whether you passed, then the style chip.
        Component standing = Component.translatable(info.passed() ? "screen.noirm.master.passed" : "screen.noirm.master.untested");
        int standingW = Math.min(font.width(standing), w / 3);
        g.drawString(font, Ui.fit(font, standing, standingW), x + w - standingW, y + 3, info.passed() ? Theme.GREEN : Theme.TEXT_DIM, false);
        int right = x + w - standingW - Theme.S4;
        if (!info.styleKey().isEmpty()) {
            Component styleName = Component.translatable(info.styleKey());
            String style = Ui.fit(font, styleName.getString(), 90);
            int chipW = font.width(style) + 6;
            Ui.chip(g, font, right - chipW, y + 1, style, LEAF_LIGHT);
            if (!style.equals(styleName.getString())) {
                hits.add(new Hit(right - chipW, y + 1, chipW, 11, null, List.of(styleName)));
            }
            right -= chipW + Theme.S4;
        }
        int nameX = x + 13 + Theme.S3;
        if (Ui.text(g, font, title, nameX, y + 3, right - nameX, Theme.TEXT)) {
            hits.add(new Hit(nameX, y, right - nameX, 14, null, List.of(title)));
        }
        // The greeting, wrapped, at most three lines.
        int ty = y + Theme.ROW + Theme.S3;
        if (!info.greetingKey().isEmpty()) {
            Component greeting = Component.translatable(info.greetingKey()).withStyle(s -> s.withItalic(true));
            List<FormattedCharSequence> lines = font.split(greeting, w - 4);
            if (lines.size() > 3) {
                // Too long for three lines: the third ends in "…" and the whole greeting is a tooltip.
                lines = new ArrayList<>(font.split(greeting, w - 4 - font.width("…")).subList(0, 3));
                lines.set(2, FormattedCharSequence.composite(lines.get(2), Component.literal("…").getVisualOrderText()));
                hits.add(new Hit(x, ty, w, 30, null, List.of(greeting)));
            }
            // A thin leaf rule beside the words, like a quote.
            g.fill(x, ty - 1, x + 1, ty + lines.size() * Theme.LINE - 1, Theme.fade(LEAF_LIGHT, 0.6f));
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, x + Theme.S3, ty, Theme.mix(Theme.TEXT_DIM, Theme.TEXT, 0.5f), false);
                ty += Theme.LINE;
            }
        }
        return ty;
    }

    /** The test card: what passing takes, and the Spar button. Returns the y below it. */
    private int drawTest(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        int h = 34;
        boolean ready = info.sparBlocker().getString().isEmpty();
        Ui.card(g, x, y, w, h, ready ? Theme.GOLD : 0);
        Component spar = own("spar");
        int buttonW = Math.max(64, font.width(spar) + 16);
        int textW = w - buttonW - 22;
        Ui.text(g, font, own("test"), x + Theme.S4, y + 6, textW, Theme.GOLD);
        Component rules = own("test.rules", info.hits(), info.postureBreaks(), info.seconds());
        Ui.text(g, font, rules, x + Theme.S4, y + 19, textW, Theme.TEXT_DIM);
        hits.add(new Hit(x, y, w - buttonW - 10, h, null, List.of(own("test").copy().withStyle(s -> s.withColor(Theme.GOLD)), rules, own("test.safe"))));
        // The button: live if you can spar now; otherwise greyed, with the reason.
        int bx = x + w - buttonW - 8;
        int by = y + (h - BUTTON_H) / 2;
        boolean over = ready && !motion.closing() && Ui.in(mouseX, mouseY, bx, by, buttonW, BUTTON_H);
        Ui.button(g, font, bx, by, buttonW, BUTTON_H, spar, hover.get(HoverTracker.key(1, 0), over), ready, Theme.SEAL, true);
        hits.add(new Hit(bx, by, buttonW, BUTTON_H, ready ? () -> send(MasterPackets.Action.SPAR, "") : null,
                ready ? List.of(own("spar.tip")) : List.of(info.sparBlocker())));
        return y + h;
    }

    /**
     * A test text: the master's own wording if their lang file has one ({@code <name key>.<suffix>},
     * e.g. the Stone Hermit's meditation trial), else the spar's ({@code screen.noirm.master.<suffix>}).
     */
    private Component own(String suffix, Object... args) {
        String key = info.nameKey() + "." + suffix;
        return Component.translatable(net.minecraft.client.resources.language.I18n.exists(key) ? key : "screen.noirm.master." + suffix, args);
    }

    private void drawLessons(GuiGraphics g, int x, int y, int w, int bottom, int mouseX, int mouseY) {
        int listTop = Ui.section(g, font, x, y, w, Component.translatable("screen.noirm.master.lessons"), Theme.TEXT_DIM);
        List<MasterPackets.LessonRow> rows = info.lessons();
        if (rows.isEmpty()) {
            Ui.text(g, font, Component.translatable("screen.noirm.master.no_lessons"), x + Theme.S2, listTop + 4, w, Theme.TEXT_FAINT);
            return;
        }
        int visible = Math.max(1, (bottom - listTop + 2) / ROW_H);
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - visible));
        boolean scrolls = rows.size() > visible;
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            drawLesson(g, rows.get(scroll + i), x, listTop + i * ROW_H, w - (scrolls ? 5 : 0), mouseX, mouseY);
        }
        Ui.scrollbar(g, x + w - 1, listTop, visible * ROW_H - 2, scroll, visible, rows.size(), Theme.IRON);
    }

    private void drawLesson(GuiGraphics g, MasterPackets.LessonRow row, int x, int y, int w, int mouseX, int mouseY) {
        int h = ROW_H - 2;
        boolean over = !motion.closing() && Ui.in(mouseX, mouseY, x, y, w, h);
        float hv = hover.get(HoverTracker.key(2, row.id().hashCode()), over);
        int status = row.status() == MasterPackets.LEARNED ? Theme.GREEN : row.status() == MasterPackets.READY ? Theme.GOLD : Theme.TEXT_FAINT;
        Ui.row(g, x, y, w, h, hv, false, status);
        g.fill(x, y, x + 1, y + h, status);
        // Right side first, so the name knows how much room it has.
        int rightW;
        Component name = row.title();
        List<Component> tip = new ArrayList<>();
        tip.add(name.copy().withStyle(s -> s.withColor(Theme.GOLD)));
        tip.add(Component.translatable("screen.noirm.master.needs", row.needs()).withStyle(s -> s.withColor(Theme.TEXT_DIM)));
        if (row.status() == MasterPackets.READY) {
            Component learn = Component.translatable(row.service() ? "screen.noirm.master.ask" : "screen.noirm.master.learn");
            rightW = Math.max(48, font.width(learn) + 16);
            int bx = x + w - rightW - 4;
            int by = y + (h - BUTTON_H) / 2;
            boolean onButton = !motion.closing() && Ui.in(mouseX, mouseY, bx, by, rightW, BUTTON_H);
            Ui.button(g, font, bx, by, rightW, BUTTON_H, learn, hover.get(HoverTracker.key(3, row.id().hashCode()), onButton), true, Theme.GOLD, true);
            hits.add(new Hit(bx, by, rightW, BUTTON_H, () -> send(MasterPackets.Action.LEARN, row.id()),
                    List.of(Component.translatable("screen.noirm.master.learn.tip", name))));
            rightW += 8;
        } else if (row.status() == MasterPackets.LEARNED) {
            Component done = Component.translatable("screen.noirm.master.learned");
            rightW = font.width(done) + 8;
            g.drawString(font, done, x + w - rightW, y + (h - 8) / 2, Theme.GREEN, false);
        } else {
            rightW = Math.min(130, w / 2 - 10);
            String why = Ui.fit(font, row.blocker().getString(), rightW - 4);
            g.drawString(font, why, x + w - 6 - font.width(why), y + (h - 8) / 2, Theme.mix(Theme.RED, Theme.TEXT_DIM, 0.25f), false);
            tip.add(row.blocker().copy().withStyle(s -> s.withColor(Theme.RED)));
            rightW += 4;
        }
        // Left: name with the rank, then what it needs.
        String rank = row.rank().isEmpty() ? (row.service() ? "+" : "*") : row.rank();
        int rankW = font.width(rank) + 6;
        int nameW = w - rightW - rankW - 16;
        String shown = Ui.fit(font, name.getString(), nameW);
        g.drawString(font, shown, x + Theme.S4, y + 3, Theme.mix(Theme.TEXT_DIM, Theme.TEXT, 0.7f + 0.3f * hv), false);
        Ui.chip(g, font, x + Theme.S4 + font.width(shown) + 4, y + 2, rank, Theme.GOLD);
        g.drawString(font, Ui.fit(font, row.needs().getString(), w - rightW - 12), x + Theme.S4, y + 13, Theme.TEXT_DIM, false);
        hits.add(new Hit(x, y, w - rightW, h, null, tip));
    }

    // ================================================================ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (motion.closing()) {
            return true;
        }
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
}
