package com.noirm.client;

import com.noirm.client.ui.HoverTracker;
import com.noirm.client.ui.NoirmStyled;
import com.noirm.client.ui.ScreenMotion;
import com.noirm.client.ui.Theme;
import com.noirm.client.ui.Ui;
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
 * rank points, time limit and an Accept button (or why it's locked). Drawn with the NOIRM kit
 * ({@code client.ui}): one flat panel, flat cards and rows, eased hover, a fade on open and close.
 */
public class MissionBoardScreen extends Screen implements NoirmStyled {
    private static final int MAX_W = 360;
    private static final int MAX_H = 228;
    private static final int PAD = Theme.S5;
    private static final int ROW_H = 22;
    private static final int BUTTON_H = Theme.ROW;

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
    private int w;
    private int h;
    private final ScreenMotion motion = new ScreenMotion();
    private final HoverTracker hover = new HoverTracker();

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

    private void send(CareerPackets.Action action, int index) {
        NoirmNetwork.sendToServer(new CareerPackets.BoardAction(info.pos(), action, index));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        motion.scrim(g, width, height);
        motion.begin(g);
        Ui.panel(g, left, top, w, h);
        int x = left + PAD;
        int iw = w - PAD * 2;
        // Header: a red seal with 任 (mission), the title, then rank · points · ryo.
        Component standing = Component.translatable("board.noirm.standing", PlayerRank.byOrdinal(info.rank()).title(), info.points(),
                info.nextPoints() < 0 ? "—" : String.valueOf(info.nextPoints()), Ryo.format(info.ryo()));
        Ui.header(g, font, x, top + PAD, iw, "任", Theme.SEAL, title, standing, Theme.TEXT_DIM);
        int standingW = Ui.headerNoteWidth(font, iw, title, standing);
        if (standingW < font.width(standing)) {
            hits.add(new Hit(x + iw - standingW, top + PAD, standingW, 14, null, List.of(standing)));
        }
        int cy = top + PAD + Theme.ROW + Theme.S3;
        cy = drawCurrent(g, x, cy, iw, mouseX, mouseY);
        cy = drawExam(g, x, cy + Theme.S2, iw, mouseX, mouseY);
        drawOffers(g, x, cy + Theme.S3, iw, top + h - Theme.S4, mouseX, mouseY);
        motion.end(g);
        super.render(g, mouseX, mouseY, partialTick);
        if (motion.closing()) {
            return;
        }
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
        if (!info.onMission()) {
            Ui.card(g, x, y, w, h, 0);
            Ui.text(g, font, Component.translatable("board.noirm.no_mission"), x + Theme.S4, y + 10, w - 16, Theme.TEXT_FAINT);
            return y + h;
        }
        Ui.card(g, x, y, w, h, info.report() ? Theme.GREEN : Theme.GOLD);
        ResourceLocation id = ResourceLocation.tryParse(info.mission());
        Component name = id == null ? Component.empty() : Component.translatable(MissionTemplate.nameKey(id));
        Component abandon = Component.translatable("board.noirm.abandon");
        int bw = Math.max(64, font.width(abandon) + 16);
        int textW = w - bw - 24;
        Component current = Component.translatable("board.noirm.current", name);
        boolean cut = Ui.text(g, font, current, x + Theme.S4, y + 5, textW, Theme.GOLD);
        cut |= Ui.text(g, font, info.objective(), x + Theme.S4, y + 16, textW, info.report() ? Theme.GREEN : Theme.TEXT);
        if (cut) {
            hits.add(new Hit(x, y, textW + 8, h, null, List.of(current, info.objective())));
        }
        button(g, 1, x + w - bw - 7, y + (h - BUTTON_H) / 2, bw, abandon, true, Theme.RED, false,
                () -> send(CareerPackets.Action.ABANDON, 0), List.of(Component.translatable("board.noirm.abandon.tip")), mouseX, mouseY);
        return y + h;
    }

    private int drawExam(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        int h = 22;
        boolean ready = info.examWhy().getString().isEmpty();
        Ui.card(g, x, y, w, h, info.examButton() && ready ? Theme.GOLD : 0);
        Component start = Component.translatable("board.noirm.exam.start");
        int bw = info.examButton() ? Math.max(64, font.width(start) + 16) : 0;
        int textW = w - bw - 24;
        if (Ui.text(g, font, info.examLine(), x + Theme.S4, y + 7, textW, Theme.TEXT)) {
            hits.add(new Hit(x, y, textW + 8, h, null, List.of(info.examLine())));
        }
        if (info.examButton()) {
            button(g, 2, x + w - bw - 7, y + (h - BUTTON_H) / 2, bw, start, ready, Theme.GOLD, true,
                    () -> send(CareerPackets.Action.EXAM, 0),
                    List.of(ready ? Component.translatable("board.noirm.exam.tip") : info.examWhy()), mouseX, mouseY);
        }
        return y + h;
    }

    private void drawOffers(GuiGraphics g, int x, int y, int w, int bottom, int mouseX, int mouseY) {
        // The clerk sells bingo books here too (dangerous rogues and their bounties).
        int price = com.noirm.bounty.BingoBook.BOOK_PRICE;
        Component buy = Component.translatable("board.noirm.bingo", Ryo.format(price));
        int bw = Math.min(font.width(buy) + 16, w / 2);
        Ui.section(g, font, x, y + 3, w - bw - Theme.S4, Component.translatable("board.noirm.offers"), Theme.TEXT_DIM);
        button(g, 3, x + w - bw, y, bw, buy, info.ryo() >= price, Theme.SEAL, false, () -> send(CareerPackets.Action.BINGO_BOOK, 0),
                List.of(Component.translatable(info.ryo() >= price ? "board.noirm.bingo.tip" : "board.noirm.bingo.poor", Ryo.format(price))),
                mouseX, mouseY);
        int listTop = y + BUTTON_H + Theme.S2;
        List<CareerPackets.OfferRow> rows = info.offers();
        if (rows.isEmpty()) {
            Ui.text(g, font, Component.translatable("board.noirm.none_today"), x + Theme.S2, listTop + 4, w, Theme.TEXT_FAINT);
            return;
        }
        int visible = Math.max(1, (bottom - listTop) / ROW_H);
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - visible));
        boolean scrolls = rows.size() > visible;
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            drawOffer(g, rows.get(scroll + i), x, listTop + i * ROW_H, w - (scrolls ? 5 : 0), mouseX, mouseY);
        }
        Ui.scrollbar(g, x + w - 1, listTop, visible * ROW_H - 2, scroll, visible, rows.size(), Theme.IRON);
    }

    private void drawOffer(GuiGraphics g, CareerPackets.OfferRow row, int x, int y, int w, int mouseX, int mouseY) {
        MissionTemplate.Letter letter = MissionTemplate.Letter.byOrdinal(row.letter());
        boolean ready = row.status() == CareerPackets.READY;
        int h = ROW_H - 2;
        boolean over = !motion.closing() && Ui.in(mouseX, mouseY, x, y, w, h);
        float hv = hover.get(HoverTracker.key(10, row.index()), over);
        Ui.row(g, x, y, w, h, hv, false, ready ? Theme.SEAL : Theme.IRON);
        // The rank letter on a faint tint of its colour.
        int letterColor = 0xFF000000 | letter.color();
        g.fill(x + 3, y + 3, x + 17, y + h - 3, Theme.mix(Theme.RAISED, letterColor, ready ? 0.22f : 0.10f));
        Ui.textCentered(g, font, letter.name(), x + 10, y + 6, ready ? letterColor : Theme.mix(letterColor, Theme.TEXT_FAINT, 0.5f));
        Component accept = Component.translatable(row.status() == CareerPackets.TAKEN ? "board.noirm.taken" : "board.noirm.accept");
        int bw = Math.max(56, font.width(accept) + 14);
        Component name = Component.translatable(MissionTemplate.nameKey(row.id()));
        Component kind = Component.translatable("mission.noirm.kind." + MissionKind.byOrdinal(row.kind()).key());
        Component details = row.timeLimit() > 0
                ? Component.translatable("board.noirm.offer.timed", kind, Ryo.format(row.reward()), row.points(), row.timeLimit() / 60)
                : Component.translatable("board.noirm.offer", kind, Ryo.format(row.reward()), row.points());
        int textW = w - bw - 32;
        Ui.text(g, font, name, x + 22, y + 2, textW, ready ? Theme.mix(Theme.TEXT_DIM, Theme.TEXT, 0.7f + 0.3f * hv) : Theme.TEXT_DIM);
        Ui.text(g, font, details, x + 22, y + 11, textW, ready ? Theme.TEXT_DIM : Theme.TEXT_FAINT);
        List<Component> tip = new ArrayList<>();
        tip.add(name);
        tip.add(Component.translatable(MissionTemplate.nameKey(row.id()) + ".desc").withStyle(s -> s.withColor(Theme.TEXT_DIM)));
        tip.add(details.copy().withStyle(s -> s.withColor(Theme.GOLD)));
        hits.add(new Hit(x, y, w - bw - 8, h, null, tip));
        button(g, HoverTracker.key(11, row.index()), x + w - bw - 3, y + (h - BUTTON_H) / 2, bw, accept, ready, Theme.SEAL, true,
                () -> send(CareerPackets.Action.ACCEPT, row.index()),
                ready ? List.of(Component.translatable("board.noirm.accept.tip")) : List.of(row.why()), mouseX, mouseY);
    }

    /** A kit button with its hit box (and tooltip). A dead one shows its tip but does nothing. */
    private void button(GuiGraphics g, int id, int bx, int by, int bw, Component label, boolean live, int accent, boolean primary,
                        Runnable click, List<Component> tip, int mouseX, int mouseY) {
        boolean over = live && !motion.closing() && Ui.in(mouseX, mouseY, bx, by, bw, BUTTON_H);
        Ui.button(g, font, bx, by, bw, BUTTON_H, label, hover.get(HoverTracker.key(12, id), over), live, accent, primary);
        hits.add(new Hit(bx, by, bw, BUTTON_H, live ? click : null, tip));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (motion.closing()) {
            return true;
        }
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit hit = hits.get(i);
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
