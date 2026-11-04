package com.noirm.client;

import com.noirm.client.ui.HoverTracker;
import com.noirm.client.ui.NoirmStyled;
import com.noirm.client.ui.ScreenMotion;
import com.noirm.client.ui.TabStrip;
import com.noirm.client.ui.Theme;
import com.noirm.client.ui.Ui;
import com.noirm.economy.Ryo;
import com.noirm.mission.CareerPackets;
import com.noirm.mission.PlayerRank;
import com.noirm.network.NoirmNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * A village shop in ryo: a Buy tab (price, the rank it needs) and a Sell tab (what they pay, how
 * many you carry), your wallet at the top. Each click buys or sells one line. Drawn with the NOIRM
 * kit ({@code client.ui}): one flat panel, text tabs with a sliding underline, flat rows that
 * lighten under the mouse, a fade on open/close and between tabs.
 */
public class ShopScreen extends Screen implements NoirmStyled {
    private static final int MAX_W = 300;
    private static final int MAX_H = 212;
    private static final int PAD = Theme.S5;
    private static final int ROW_H = 20;
    private static final int ROW_GAP = 2;
    private static final int[] TAB_ACCENT = {Theme.SEAL};

    private CareerPackets.ShopInfo info;
    private boolean selling;
    private int scroll;
    private int left;
    private int top;
    private int w;
    private int h;
    /** Visible rows this frame: (index into info.rows, y). */
    private final List<int[]> visibleRows = new ArrayList<>();
    private final ScreenMotion motion = new ScreenMotion();
    private final TabStrip tabs = new TabStrip();
    private final HoverTracker hover = new HoverTracker();
    private String[] tabLabels;

    public ShopScreen(CareerPackets.ShopInfo info) {
        super(info.name());
        this.info = info;
    }

    public static void receive(CareerPackets.ShopInfo info) {
        Minecraft mc = Minecraft.getInstance();
        if (info.entityId() < 0) {
            MissionBoardScreen.closeIfOpen();
            return;
        }
        if (mc.screen instanceof ShopScreen screen && screen.info.entityId() == info.entityId()) {
            screen.info = info;
        } else {
            mc.setScreen(new ShopScreen(info));
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
        tabLabels = new String[]{Component.translatable("shop.noirm.tab.buy").getString(),
                Component.translatable("shop.noirm.tab.sell").getString()};
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

    private List<Integer> shown() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < info.rows().size(); i++) {
            if (info.rows().get(i).sell() == selling) {
                out.add(i);
            }
        }
        return out;
    }

    private int listTop() {
        return top + PAD + Theme.ROW + Theme.S1 + Theme.ROW + Theme.S2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        visibleRows.clear();
        motion.scrim(g, width, height);
        motion.begin(g);
        int x = left + PAD;
        int iw = w - PAD * 2;
        Ui.panel(g, left, top, w, h);
        // Header: the shop's name, your wallet on the right.
        Component wallet = Component.translatable("shop.noirm.wallet", Ryo.format(info.ryo()));
        Ui.header(g, font, x, top + PAD, iw, "店", Theme.SEAL, title, wallet, Theme.GOLD);
        // Buy / Sell.
        int tabsY = top + PAD + Theme.ROW + Theme.S1;
        tabs.draw(g, font, x, tabsY, iw, tabLabels, selling ? 1 : 0, TAB_ACCENT, mouseX, mouseY);

        CareerPackets.ShopRowView hovered = null;
        Component hoveredName = null;
        motion.beginContent(g);
        List<Integer> rows = shown();
        int listTop = listTop();
        int bottom = top + h - Theme.S4;
        int visible = Math.max(1, (bottom - listTop + ROW_GAP) / ROW_H);
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - visible));
        if (rows.isEmpty()) {
            Component none = Component.translatable(selling ? "shop.noirm.nothing_to_sell" : "shop.noirm.nothing");
            Ui.text(g, font, none, x, listTop + 4, iw, Theme.TEXT_FAINT);
        }
        boolean scrolls = rows.size() > visible;
        int rowW = iw - (scrolls ? 5 : 0);
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            int index = rows.get(scroll + i);
            CareerPackets.ShopRowView row = info.rows().get(index);
            int ry = listTop + i * ROW_H;
            int rh = ROW_H - ROW_GAP;
            boolean over = !motion.closing() && Ui.in(mouseX, mouseY, x, ry, rowW, rh);
            float hv = hover.get(HoverTracker.key(selling ? 2 : 1, index), over);
            boolean locked = !row.sell() && row.need() > info.rank();
            boolean poor = !row.sell() && row.price() > info.ryo();
            boolean none = row.sell() && row.have() < row.stack().getCount();
            boolean blocked = locked || poor || none;
            Ui.row(g, x, ry, rowW, rh, hv, false, blocked ? Theme.RED : Theme.GOLD);
            g.renderItem(row.stack(), x + 2, ry + 1);
            g.renderItemDecorations(font, row.stack(), x + 2, ry + 1);
            Component price = Component.translatable(row.sell() ? "shop.noirm.pays" : "shop.noirm.costs", Ryo.format(row.price()));
            int priceW = font.width(price);
            g.drawString(font, price, x + rowW - 6 - priceW, ry + 5, blocked ? Theme.RED : Theme.GOLD, false);
            Component name = row.stack().getHoverName();
            int nameColor = locked || none ? Theme.TEXT_FAINT : Theme.mix(Theme.TEXT_DIM, Theme.TEXT, 0.7f + 0.3f * hv);
            Ui.text(g, font, Component.literal(name.getString()), x + 24, ry + 5, rowW - 24 - priceW - 14, nameColor);
            visibleRows.add(new int[]{index, ry, rowW});
            if (over) {
                hovered = row;
                hoveredName = name;
            }
        }
        Ui.scrollbar(g, x + iw - 1, listTop, visible * ROW_H - ROW_GAP, scroll, visible, rows.size(), Theme.IRON);
        motion.endContent(g);
        motion.end(g);
        super.render(g, mouseX, mouseY, partialTick);
        if (hovered != null && !motion.closing()) {
            List<Component> tip = new ArrayList<>();
            tip.add(hoveredName);
            if (hovered.sell()) {
                tip.add(Component.translatable("shop.noirm.tip.sell", hovered.stack().getCount(), Ryo.format(hovered.price()), hovered.have())
                        .withStyle(s -> s.withColor(Theme.TEXT_DIM)));
            } else {
                tip.add(Component.translatable("shop.noirm.tip.buy", hovered.stack().getCount(), Ryo.format(hovered.price()))
                        .withStyle(s -> s.withColor(Theme.TEXT_DIM)));
                if (hovered.need() > 0) {
                    boolean locked = hovered.need() > info.rank();
                    tip.add(Component.translatable("shop.noirm.tip.rank", PlayerRank.byOrdinal(hovered.need()).title())
                            .withStyle(s -> s.withColor(locked ? Theme.RED : Theme.GOLD)));
                }
            }
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }

    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1f, 0.35f));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (motion.closing()) {
            return true;
        }
        if (button == 0) {
            int t = tabs.at(mouseX, mouseY);
            if (t >= 0) {
                if ((t == 1) != selling) {
                    selling = t == 1;
                    scroll = 0;
                    motion.tabChanged();
                    click();
                }
                return true;
            }
            for (int[] row : visibleRows) {
                if (Ui.in(mouseX, mouseY, left + PAD, row[1], row[2], ROW_H - ROW_GAP)) {
                    NoirmNetwork.sendToServer(new CareerPackets.ShopAction(info.entityId(), row[0]));
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
