package com.noirm.client;

import com.noirm.client.ui.HoverTracker;
import com.noirm.client.ui.NoirmStyled;
import com.noirm.client.ui.ScreenMotion;
import com.noirm.client.ui.TabStrip;
import com.noirm.client.ui.Theme;
import com.noirm.client.ui.Ui;
import com.noirm.combat.StylePackets;
import com.noirm.network.NoirmNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The style picker (the Y key): every fighting style, unarmed and weapon, which ones you know, and how
 * each <em>plays</em> (footwork, rhythm, guard, dodge, speed, parry, its hooks). A list on the left, the
 * style under the mouse (or the picked one) on the right, a "Use" button. Weapon styles are picked per
 * weapon; "Own moves" goes back to the weapon in hand's own moveset. Drawn on the NOIRM kit
 * ({@code client.ui}, docs/UI.md).
 */
public class StylePickerScreen extends Screen implements NoirmStyled {
    private static final int MAX_W = 380;
    private static final int MAX_H = 230;
    private static final int PAD = Theme.S5;
    private static final int ROW_H = 16;
    private static final int[] TAB_ACCENT = {Theme.SEAL, Theme.SEAL};

    private StylePackets.Info info;
    private boolean weapons;
    private int scroll;
    private String selected = "";
    private int left, top, w, h;
    private String[] tabLabels;
    private final List<int[]> rowBoxes = new ArrayList<>();
    private final List<StylePackets.Row> shownRows = new ArrayList<>();
    private int[] useBox;
    private int[] ownBox;
    private final ScreenMotion motion = new ScreenMotion();
    private final TabStrip tabs = new TabStrip();
    private final HoverTracker hover = new HoverTracker();

    public StylePickerScreen(StylePackets.Info info) {
        super(Component.translatable("screen.noirm.styles.title"));
        this.info = info;
        this.selected = info.active();
    }

    /** The server's list: opens the picker, or refreshes it if it's open. */
    public static void receive(StylePackets.Info info) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof StylePickerScreen screen) {
            screen.info = info;
        } else if (info.open()) {
            mc.setScreen(new StylePickerScreen(info));
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
        tabLabels = new String[]{Component.translatable("screen.noirm.styles.tab.unarmed").getString(),
                Component.translatable("screen.noirm.styles.tab.weapons").getString()};
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

    private List<StylePackets.Row> rows() {
        List<StylePackets.Row> out = new ArrayList<>();
        for (StylePackets.Row row : info.rows()) {
            if (row.weapons().isEmpty() != weapons) {
                out.add(row);
            }
        }
        // Usable first, then the rest; each group by name.
        out.sort((a, b) -> a.usable() != b.usable() ? (a.usable() ? -1 : 1) : name(a).getString().compareTo(name(b).getString()));
        return out;
    }

    private static Component name(StylePackets.Row row) {
        return Component.translatable("style." + row.id().replace(':', '.'));
    }

    private boolean isActive(StylePackets.Row row) {
        if (row.weapons().isEmpty()) {
            return row.id().equals(info.active());
        }
        return row.weapons().stream().anyMatch(m -> row.id().equals(info.picks().get(m)));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        rowBoxes.clear();
        shownRows.clear();
        useBox = null;
        ownBox = null;
        motion.scrim(g, width, height);
        motion.begin(g);
        int x = left + PAD;
        int iw = w - PAD * 2;
        Ui.panel(g, left, top, w, h);
        long known = info.rows().stream().filter(StylePackets.Row::usable).count();
        Ui.header(g, font, x, top + PAD, iw, "式", Theme.SEAL, title,
                Component.translatable("screen.noirm.styles.known", known, info.rows().size()), Theme.TEXT_DIM);
        int tabsY = top + PAD + Theme.ROW + Theme.S1;
        tabs.draw(g, font, x, tabsY, iw, tabLabels, weapons ? 1 : 0, TAB_ACCENT, mouseX, mouseY);

        motion.beginContent(g);
        int listTop = tabsY + Theme.ROW + Theme.S2;
        int bottom = top + h - Theme.S4 - Theme.LINE;
        int listW = Math.min(150, iw * 2 / 5);
        List<StylePackets.Row> rows = rows();
        int visible = Math.max(1, (bottom - listTop) / ROW_H);
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - visible));
        boolean scrolls = rows.size() > visible;
        int rowW = listW - (scrolls ? 5 : 0);
        StylePackets.Row focus = null;
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            StylePackets.Row row = rows.get(scroll + i);
            int ry = listTop + i * ROW_H;
            boolean over = !motion.closing() && Ui.in(mouseX, mouseY, x, ry, rowW, ROW_H - 2);
            float hv = hover.get(row.id().hashCode(), over);
            boolean active = isActive(row);
            Ui.row(g, x, ry, rowW, ROW_H - 2, hv, row.id().equals(selected), active ? Theme.GOLD : row.usable() ? Theme.GREEN : Theme.RED);
            int color = !row.usable() ? Theme.TEXT_FAINT : Theme.mix(Theme.TEXT_DIM, Theme.TEXT, 0.7f + 0.3f * hv);
            String mark = active ? "▶ " : row.usable() ? "" : "✖ ";
            Ui.text(g, font, Component.literal(mark).append(name(row)), x + 5, ry + 3, rowW - 8, active ? Theme.GOLD : color);
            rowBoxes.add(new int[]{x, ry, rowW, ROW_H - 2});
            shownRows.add(row);
            if (over) {
                focus = row;
            }
        }
        Ui.scrollbar(g, x + listW - 1, listTop, visible * ROW_H - 2, scroll, visible, rows.size(), Theme.IRON);
        if (focus == null) {
            for (StylePackets.Row row : rows) {
                if (row.id().equals(selected)) {
                    focus = row;
                }
            }
        }
        if (focus == null && !rows.isEmpty()) {
            focus = rows.get(0);
        }
        int dx = x + listW + Theme.S5;
        int dw = x + iw - dx;
        Ui.vline(g, dx - Theme.S3, listTop, bottom - listTop);
        if (focus != null) {
            drawDetail(g, focus, dx, listTop, dw, bottom, mouseX, mouseY);
        }
        // The weapon in hand: back to its own moves.
        if (weapons && !info.held().isEmpty()) {
            boolean picked = info.picks().containsKey(info.held());
            int bw = Math.min(dw, font.width(Component.translatable("screen.noirm.styles.weapon_own", info.held())) + 16);
            int by = bottom - 14;
            boolean over = Ui.in(mouseX, mouseY, dx, by, bw, 14);
            Ui.button(g, font, dx, by, bw, 14, Component.translatable("screen.noirm.styles.weapon_own", heldName()),
                    hover.get(-7, over), picked, Theme.SEAL, false);
            if (picked) {
                ownBox = new int[]{dx, by, bw, 14};
            }
        }
        Ui.text(g, font, Component.translatable("screen.noirm.styles.hint"), x, top + h - Theme.S4 - 9, iw, Theme.TEXT_FAINT);
        motion.endContent(g);
        motion.end(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private Component heldName() {
        return Component.translatable("style.noirm.weapon_" + info.held());
    }

    private void drawDetail(GuiGraphics g, StylePackets.Row row, int x, int y, int w, int bottom, int mouseX, int mouseY) {
        Ui.text(g, font, name(row), x, y, w, row.usable() ? Theme.TEXT : Theme.TEXT_DIM);
        y += Theme.LINE + 2;
        y = Ui.wrap(g, font, Component.translatable("style." + row.id().replace(':', '.') + ".desc"), x, y, w, y + Theme.LINE * 4, Theme.TEXT_DIM);
        y += Theme.S2;
        // How it plays: chips.
        List<String> chips = new ArrayList<>();
        chips.add(Component.translatable("style.noirm.footwork." + row.footwork()).getString());
        chips.add(Component.translatable("style.noirm.rhythm." + row.rhythm()).getString());
        chips.add(Component.translatable("style.noirm.guard." + row.guard()).getString());
        chips.add(Component.translatable("style.noirm.dodge." + row.dodge()).getString());
        if (Math.abs(row.moveSpeed() - 1f) > 0.01f) {
            chips.add(Component.translatable("screen.noirm.styles.speed", fmt(row.moveSpeed())).getString());
        }
        if (Math.abs(row.parryWindow() - 1f) > 0.01f && !"evasive".equals(row.guard())) {
            chips.add(Component.translatable("screen.noirm.styles.parry", fmt(row.parryWindow())).getString());
        }
        if ((row.flags() & 1) != 0) chips.add(Component.translatable("screen.noirm.styles.feint").getString());
        if ((row.flags() & 2) != 0) chips.add(Component.translatable("screen.noirm.styles.backstab", fmt(row.backstab())).getString());
        if ((row.flags() & 4) != 0) chips.add(Component.translatable("screen.noirm.styles.aerial", fmt(row.aerial())).getString());
        if ((row.flags() & 8) != 0) chips.add(Component.translatable("screen.noirm.styles.drain").getString());
        if ((row.flags() & 16) != 0) chips.add(Component.translatable("screen.noirm.styles.momentum").getString());
        if ((row.flags() & 32) != 0) chips.add(Component.translatable("screen.noirm.styles.jitter").getString());
        int cx = x;
        for (String chip : chips) {
            int cw = font.width(chip) + 6;
            if (cx + cw > x + w && cx > x) {
                cx = x;
                y += 13;
            }
            Ui.chip(g, font, cx, y, chip, Theme.CHAKRA_LIGHT);
            cx += cw + Theme.S2;
        }
        y += 13 + Theme.S2;
        if (!row.weapons().isEmpty()) {
            List<String> names = new ArrayList<>();
            row.weapons().forEach(m -> names.add(Component.translatable("style.noirm.weapon_" + m).getString()));
            Ui.text(g, font, Component.translatable("screen.noirm.styles.for", String.join(", ", names)), x, y, w, Theme.TEXT_DIM);
            y += Theme.LINE + 2;
        }
        // Status: in use / how learned / why locked.
        Component status;
        int color;
        if (!row.usable()) {
            status = Component.translatable("screen.noirm.styles.locked." + row.blocker(), Math.round(row.taijutsu()));
            color = Theme.RED;
        } else if (isActive(row)) {
            status = Component.translatable("screen.noirm.styles.active");
            color = Theme.GOLD;
        } else {
            status = row.how().isEmpty() ? Component.empty() : Component.translatable("style.noirm.learned." + row.how());
            color = Theme.GREEN;
        }
        y = Ui.wrap(g, font, status, x, y, w, bottom - 18, color);
        if (row.usable() && !isActive(row)) {
            int bw = 60;
            int by = weapons && !info.held().isEmpty() ? bottom - 32 : bottom - 14;
            boolean over = Ui.in(mouseX, mouseY, x, by, bw, 14);
            Ui.button(g, font, x, by, bw, 14, Component.translatable("screen.noirm.styles.use"), hover.get(-3, over), true, Theme.SEAL, true);
            useBox = new int[]{x, by, bw, 14};
            selectedRow = row;
        }
    }

    private StylePackets.Row selectedRow;

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (motion.closing()) {
            return true;
        }
        if (button == 0) {
            int t = tabs.at(mouseX, mouseY);
            if (t >= 0) {
                if ((t == 1) != weapons) {
                    weapons = t == 1;
                    scroll = 0;
                    motion.tabChanged();
                    com.noirm.client.ui.UiSounds.click();
                }
                return true;
            }
            if (useBox != null && selectedRow != null && Ui.in(mouseX, mouseY, useBox[0], useBox[1], useBox[2], useBox[3])) {
                NoirmNetwork.sendToServer(new StylePackets.Choose(selectedRow.id()));
                com.noirm.client.ui.UiSounds.click();
                return true;
            }
            if (ownBox != null && Ui.in(mouseX, mouseY, ownBox[0], ownBox[1], ownBox[2], ownBox[3])) {
                NoirmNetwork.sendToServer(new StylePackets.Choose(StylePackets.Choose.OWN + info.held()));
                com.noirm.client.ui.UiSounds.click();
                return true;
            }
            for (int i = 0; i < rowBoxes.size(); i++) {
                int[] b = rowBoxes.get(i);
                if (Ui.in(mouseX, mouseY, b[0], b[1], b[2], b[3])) {
                    StylePackets.Row row = shownRows.get(i);
                    if (row.id().equals(selected) && row.usable() && !isActive(row)) {
                        NoirmNetwork.sendToServer(new StylePackets.Choose(row.id())); // a second click uses it
                    }
                    selected = row.id();
                    motion.tabChanged();
                    com.noirm.client.ui.UiSounds.click();
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
