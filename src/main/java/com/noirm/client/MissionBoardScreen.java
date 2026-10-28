package com.noirm.client;

import com.noirm.economy.Ryo;
import com.noirm.mission.CareerPackets;
import com.noirm.mission.MissionKind;
import com.noirm.mission.MissionTemplate;
import com.noirm.mission.PlayerRank;
import com.noirm.network.NoirmNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The missions board: your rank, points and ryo; the mission you're on (with Abandon); the next
 * rank's exam (with a Start button); and today's missions by rank letter, each with its reward,
 * rank points, time limit and an Accept button (or why it's locked).
 */
public class MissionBoardScreen extends Screen {
    private static final int W = 360;
    private static final int H = 228;
    private static final int ROW_H = 22;
    private static final int INK = 0xF4100E14;
    private static final int INK_LIGHT = 0xF41A1720;
    private static final int CARD = 0xFF17141C;
    private static final int FRAME = 0xFF3D3645;
    private static final int RULE = 0xFF2E2935;
    private static final int PAPER = 0xFFEDE4D3;
    private static final int DIM = 0xFF8F8796;
    private static final int FAINT = 0xFF5E5866;
    private static final int GOLD = 0xFFE8C060;
    private static final int GREEN = 0xFF6FD38A;

    private record Hit(int x, int y, int w, int h, Runnable click, List<Component> tip) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private CareerPackets.BoardInfo info;
    private final List<Hit> hits = new ArrayList<>();
    private int scroll;
    private int left;
    private int top;

    public MissionBoardScreen(CareerPackets.BoardInfo info) {
        super(Component.translatable("board.noirm.title"));
        this.info = info;
    }

    public static void receive(CareerPackets.BoardInfo info) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MissionBoardScreen screen && screen.info.pos().equals(info.pos())) {
            screen.info = info;
        } else {
            mc.setScreen(new MissionBoardScreen(info));
        }
    }

    public static void closeIfOpen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MissionBoardScreen || mc.screen instanceof ShopScreen) {
            mc.screen.onClose();
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

    private void send(CareerPackets.Action action, int index) {
        NoirmNetwork.sendToServer(new CareerPackets.BoardAction(info.pos(), action, index));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        hits.clear();
        int x = left, y = top;
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, 0xC0000000);
        g.fillGradient(x, y, x + W, y + H, INK_LIGHT, INK);
        g.renderOutline(x, y, W, H, FRAME);
        // Header: a red seal with 任 (mission), the title, then rank · points · ryo.
        g.fill(x + 8, y + 6, x + 22, y + 20, 0xFF8A2A22);
        g.drawCenteredString(font, "任", x + 15, y + 9, 0xFFF4E6D0);
        g.drawString(font, title, x + 28, y + 10, GOLD, false);
        Component standing = Component.translatable("board.noirm.standing", PlayerRank.byOrdinal(info.rank()).title(), info.points(),
                info.nextPoints() < 0 ? "—" : String.valueOf(info.nextPoints()), Ryo.format(info.ryo()));
        g.drawString(font, standing, x + W - 8 - font.width(standing), y + 10, PAPER, false);
        g.fill(x + 6, y + 24, x + W - 6, y + 25, RULE);
        int cy = y + 30;
        cy = drawCurrent(g, x + 8, cy, W - 16, mouseX, mouseY);
        cy = drawExam(g, x + 8, cy + 4, W - 16, mouseX, mouseY);
        drawOffers(g, x + 8, cy + 6, W - 16, y + H - 6, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.tip() != null && !hit.tip().isEmpty() && hit.contains(mouseX, mouseY)) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                hit.tip().forEach(line -> lines.addAll(font.split(line, 240)));
                g.renderTooltip(font, lines, mouseX, mouseY);
                break;
            }
        }
    }

    private int drawCurrent(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        int h = 28;
        g.fill(x, y, x + w, y + h, CARD);
        g.renderOutline(x, y, w, h, RULE);
        if (!info.onMission()) {
            g.drawString(font, Component.translatable("board.noirm.no_mission"), x + 6, y + 10, DIM, false);
            return y + h;
        }
        ResourceLocation id = ResourceLocation.tryParse(info.mission());
        Component name = id == null ? Component.empty() : Component.translatable(MissionTemplate.nameKey(id));
        int bw = 70;
        g.drawString(font, fit(Component.translatable("board.noirm.current", name).getString(), w - bw - 16), x + 6, y + 4, GOLD, false);
        g.drawString(font, fit(info.objective().getString(), w - bw - 16), x + 6, y + 16, info.report() ? GREEN : PAPER, false);
        button(g, x + w - bw - 4, y + 6, bw, Component.translatable("board.noirm.abandon"), true, 0xFF7A2E2A,
                () -> send(CareerPackets.Action.ABANDON, 0), List.of(Component.translatable("board.noirm.abandon.tip")), mouseX, mouseY);
        return y + h;
    }

    private int drawExam(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        int h = 22;
        g.fill(x, y, x + w, y + h, CARD);
        g.renderOutline(x, y, w, h, RULE);
        int bw = info.examButton() ? 70 : 0;
        g.drawString(font, fit(info.examLine().getString(), w - bw - 16), x + 6, y + 7, PAPER, false);
        if (info.examButton()) {
            boolean ready = info.examWhy().getString().isEmpty();
            button(g, x + w - bw - 4, y + 3, bw, Component.translatable("board.noirm.exam.start"), ready, 0xFF3A6E2E,
                    () -> send(CareerPackets.Action.EXAM, 0),
                    List.of(ready ? Component.translatable("board.noirm.exam.tip") : info.examWhy()), mouseX, mouseY);
        }
        return y + h;
    }

    private void drawOffers(GuiGraphics g, int x, int y, int w, int bottom, int mouseX, int mouseY) {
        g.drawString(font, Component.translatable("board.noirm.offers"), x, y, GOLD, false);
        int listTop = y + 12;
        List<CareerPackets.OfferRow> rows = info.offers();
        if (rows.isEmpty()) {
            g.drawString(font, Component.translatable("board.noirm.none_today"), x + 4, listTop + 4, DIM, false);
            return;
        }
        int visible = Math.max(1, (bottom - listTop) / ROW_H);
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - visible));
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            drawOffer(g, rows.get(scroll + i), x, listTop + i * ROW_H, w - 6, mouseX, mouseY);
        }
        if (rows.size() > visible) {
            int trackH = visible * ROW_H - 2;
            int barH = Math.max(10, trackH * visible / rows.size());
            int barY = listTop + (trackH - barH) * scroll / Math.max(1, rows.size() - visible);
            g.fill(x + w - 3, listTop, x + w - 1, listTop + trackH, RULE);
            g.fill(x + w - 3, barY, x + w - 1, barY + barH, DIM);
        }
    }

    private void drawOffer(GuiGraphics g, CareerPackets.OfferRow row, int x, int y, int w, int mouseX, int mouseY) {
        MissionTemplate.Letter letter = MissionTemplate.Letter.byOrdinal(row.letter());
        boolean ready = row.status() == CareerPackets.READY;
        g.fill(x, y, x + w, y + ROW_H - 2, CARD);
        g.renderOutline(x, y, w, ROW_H - 2, RULE);
        g.fill(x + 3, y + 3, x + 17, y + ROW_H - 5, letter.color() & 0x60FFFFFF | 0x40000000);
        g.drawCenteredString(font, letter.name(), x + 10, y + 6, letter.color());
        int bw = 56;
        Component name = Component.translatable(MissionTemplate.nameKey(row.id()));
        Component kind = Component.translatable("mission.noirm.kind." + MissionKind.byOrdinal(row.kind()).key());
        Component details = row.timeLimit() > 0
                ? Component.translatable("board.noirm.offer.timed", kind, Ryo.format(row.reward()), row.points(), row.timeLimit() / 60)
                : Component.translatable("board.noirm.offer", kind, Ryo.format(row.reward()), row.points());
        int textW = w - bw - 30;
        g.drawString(font, fit(name.getString(), textW), x + 22, y + 2, ready ? PAPER : DIM, false);
        g.drawString(font, fit(details.getString(), textW), x + 22, y + 11, ready ? DIM : FAINT, false);
        List<Component> tip = new ArrayList<>();
        tip.add(name);
        tip.add(Component.translatable(MissionTemplate.nameKey(row.id()) + ".desc"));
        tip.add(details);
        hits.add(new Hit(x, y, w - bw - 8, ROW_H - 2, null, tip));
        Component label = Component.translatable(row.status() == CareerPackets.TAKEN ? "board.noirm.taken" : "board.noirm.accept");
        button(g, x + w - bw - 4, y + 2, bw, label, ready, 0xFF3A6E2E, () -> send(CareerPackets.Action.ACCEPT, row.index()),
                ready ? List.of(Component.translatable("board.noirm.accept.tip")) : List.of(row.why()), mouseX, mouseY);
    }

    private void button(GuiGraphics g, int bx, int by, int bw, Component label, boolean live, int color, Runnable click, List<Component> tip,
                        int mouseX, int mouseY) {
        boolean hover = live && mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 16;
        g.fill(bx, by, bx + bw, by + 16, live ? (hover ? brighten(color) : color) : 0xFF2A2630);
        g.renderOutline(bx, by, bw, 16, live ? 0xFF8A8090 : FRAME);
        g.drawCenteredString(font, label, bx + bw / 2, by + 4, live ? PAPER : FAINT);
        hits.add(new Hit(bx, by, bw, 16, live ? click : null, tip));
    }

    private static int brighten(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 24), gg = Math.min(255, ((color >> 8) & 0xFF) + 24), b = Math.min(255, (color & 0xFF) + 24);
        return 0xFF000000 | r << 16 | gg << 8 | b;
    }

    private String fit(String text, int width) {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("…")) + "…";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Hit hit : hits) {
                if (hit.click() != null && hit.contains(mouseX, mouseY)) {
                    hit.click().run();
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
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
