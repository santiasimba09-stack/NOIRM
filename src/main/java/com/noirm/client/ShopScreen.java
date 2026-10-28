package com.noirm.client;

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
 * many you carry), your wallet at the top. Each click buys or sells one line.
 */
public class ShopScreen extends Screen {
    private static final int W = 300;
    private static final int H = 212;
    private static final int ROW_H = 20;
    private static final int INK = 0xF4100E14;
    private static final int INK_LIGHT = 0xF41A1720;
    private static final int CARD = 0xFF17141C;
    private static final int CARD_HOVER = 0xFF221E29;
    private static final int FRAME = 0xFF3D3645;
    private static final int RULE = 0xFF2E2935;
    private static final int PAPER = 0xFFEDE4D3;
    private static final int DIM = 0xFF8F8796;
    private static final int GOLD = 0xFFE8C060;
    private static final int RED = 0xFFE05A5A;

    private CareerPackets.ShopInfo info;
    private boolean selling;
    private int scroll;
    private int left;
    private int top;
    /** Visible rows this frame: (index into info.rows, y). */
    private final List<int[]> visibleRows = new ArrayList<>();

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
        left = (width - W) / 2;
        top = (height - H) / 2;
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

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        visibleRows.clear();
        int x = left, y = top;
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, 0xC0000000);
        g.fillGradient(x, y, x + W, y + H, INK_LIGHT, INK);
        g.renderOutline(x, y, W, H, FRAME);
        g.drawString(font, title, x + 8, y + 8, GOLD, false);
        Component wallet = Component.translatable("shop.noirm.wallet", Ryo.format(info.ryo()));
        g.drawString(font, wallet, x + W - 8 - font.width(wallet), y + 8, PAPER, false);
        // Tabs.
        tab(g, x + 8, y + 22, Component.translatable("shop.noirm.tab.buy"), !selling, mouseX, mouseY);
        tab(g, x + 78, y + 22, Component.translatable("shop.noirm.tab.sell"), selling, mouseX, mouseY);
        g.fill(x + 6, y + 38, x + W - 6, y + 39, RULE);
        List<Integer> rows = shown();
        int listTop = y + 44;
        int visible = (y + H - 6 - listTop) / ROW_H;
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - visible));
        if (rows.isEmpty()) {
            g.drawString(font, Component.translatable(selling ? "shop.noirm.nothing_to_sell" : "shop.noirm.nothing"), x + 10, listTop + 4, DIM, false);
        }
        CareerPackets.ShopRowView hovered = null;
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            int index = rows.get(scroll + i);
            CareerPackets.ShopRowView row = info.rows().get(index);
            int ry = listTop + i * ROW_H;
            boolean hover = mouseX >= x + 8 && mouseX < x + W - 8 && mouseY >= ry && mouseY < ry + ROW_H - 2;
            g.fill(x + 8, ry, x + W - 8, ry + ROW_H - 2, hover ? CARD_HOVER : CARD);
            g.renderOutline(x + 8, ry, W - 16, ROW_H - 2, RULE);
            g.renderItem(row.stack(), x + 10, ry + 1);
            g.renderItemDecorations(font, row.stack(), x + 10, ry + 1);
            String name = row.stack().getHoverName().getString();
            boolean locked = !row.sell() && row.need() > info.rank();
            boolean poor = !row.sell() && row.price() > info.ryo();
            boolean none = row.sell() && row.have() < row.stack().getCount();
            Component price = Component.translatable(row.sell() ? "shop.noirm.pays" : "shop.noirm.costs", Ryo.format(row.price()));
            int priceW = font.width(price);
            g.drawString(font, fit(name, W - 60 - priceW), x + 30, ry + 5, locked || none ? DIM : PAPER, false);
            g.drawString(font, price, x + W - 12 - priceW, ry + 5, locked || poor || none ? RED : GOLD, false);
            visibleRows.add(new int[] {index, ry});
            if (hover) {
                hovered = row;
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (hovered != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(hovered.stack().getHoverName());
            if (hovered.sell()) {
                tip.add(Component.translatable("shop.noirm.tip.sell", hovered.stack().getCount(), Ryo.format(hovered.price()), hovered.have()));
            } else {
                tip.add(Component.translatable("shop.noirm.tip.buy", hovered.stack().getCount(), Ryo.format(hovered.price())));
                if (hovered.need() > 0) {
                    tip.add(Component.translatable("shop.noirm.tip.rank", PlayerRank.byOrdinal(hovered.need()).title()));
                }
            }
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }

    private void tab(GuiGraphics g, int tx, int ty, Component label, boolean active, int mouseX, int mouseY) {
        g.fill(tx, ty, tx + 66, ty + 14, active ? 0xFF3A6E2E : 0xFF2A2630);
        g.renderOutline(tx, ty, 66, 14, active ? 0xFF8AD07A : FRAME);
        g.drawCenteredString(font, label, tx + 33, ty + 3, active ? PAPER : DIM);
    }

    private String fit(String text, int width) {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("…")) + "…";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (mouseY >= top + 22 && mouseY < top + 36) {
                if (mouseX >= left + 8 && mouseX < left + 74) {
                    selling = false;
                    scroll = 0;
                    return true;
                }
                if (mouseX >= left + 78 && mouseX < left + 144) {
                    selling = true;
                    scroll = 0;
                    return true;
                }
            }
            for (int[] row : visibleRows) {
                if (mouseX >= left + 8 && mouseX < left + W - 8 && mouseY >= row[1] && mouseY < row[1] + ROW_H - 2) {
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
