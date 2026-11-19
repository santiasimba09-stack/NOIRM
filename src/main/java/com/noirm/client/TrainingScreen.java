package com.noirm.client;

import com.noirm.arts.ArtsData;
import com.noirm.arts.ArtsSync;
import com.noirm.arts.ChakraShape;
import com.noirm.arts.FusionTrial;
import com.noirm.arts.ShapeTraining;
import com.noirm.arts.SpiritPaths;
import com.noirm.clan.Nature;
import com.noirm.clan.NatureSystem;
import com.noirm.client.ui.HoverTracker;
import com.noirm.client.ui.NoirmStyled;
import com.noirm.client.ui.ScreenMotion;
import com.noirm.client.ui.TabStrip;
import com.noirm.client.ui.Theme;
import com.noirm.client.ui.Ui;
import com.noirm.nature.NatureDrills;
import com.noirm.nature.NaturePackets;
import com.noirm.network.NoirmNetwork;
import com.noirm.network.PowerPackets;
import com.noirm.network.SyncShinobiDataPacket;
import com.noirm.workshop.Workshop;
import com.noirm.workshop.WorkshopPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The training screen (the J screen's Training tab): every drill and trial under way, each as a card
 * with all its steps (done ✔, now ▶, later ◦), the current step's progress, tips for what moves it,
 * and what it unlocks next. The side column says whether anything is moving now (drills only move
 * while you meditate, N) and lists what you could begin. Drawn with the NOIRM kit.
 */
public class TrainingScreen extends Screen implements NoirmStyled {
    private static final int SIDEBAR = 146;
    private static final int PAD = Theme.S5;
    private static final int NEXT = 0xFF90C090;

    private final ScreenMotion motion = new ScreenMotion();
    private final TabStrip tabs = new TabStrip();
    private final HoverTracker hover = new HoverTracker();
    private String[] tabLabels;
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int scrollPx;
    private int contentH;
    private boolean confirmAbandon;
    /** The list's visible box this frame (buttons outside it can't be clicked). */
    private int clipTop;
    private int clipBottom;

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();

    /** One step of a drill: 0 later, 1 now, 2 done. */
    private record Step(Component label, int state) {}

    private record Action(Component label, Runnable run, boolean primary) {}

    private static final class Card {
        String key;
        int accent;
        Component title;
        String badge = "";
        final List<Step> steps = new ArrayList<>();
        float share = -1f;
        Component progress;
        final List<Component> tips = new ArrayList<>();
        Component next;
        final List<Action> actions = new ArrayList<>();
    }

    public TrainingScreen() {
        super(Component.translatable("screen.noirm.training"));
        tabs.snap(ArtsScreens.TAB_TRAINING);
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 520);
        ph = Math.min(height - 8, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        tabLabels = ArtsScreens.tabLabels();
        if (!motion.started()) {
            motion.open();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        motion.tick();
    }

    @Override
    public void onClose() {
        motion.close(super::onClose);
    }

    // ================================================================ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        motion.scrim(g, width, height);
        motion.begin(g);
        Ui.panel(g, px, py, pw, ph);
        tabs.draw(g, font, px + PAD, py + PAD, pw - PAD * 2, tabLabels, ArtsScreens.TAB_TRAINING, new int[]{Theme.SEAL}, mouseX, mouseY);
        PowerPackets.SyncPowers powers = ClientPowers.get();
        int top = py + PAD + Theme.ROW + Theme.S4;
        if (powers == null) {
            Ui.textCentered(g, font, Component.translatable("screen.noirm.jutsu.loading").getString(), px + pw / 2, py + ph / 2, Theme.TEXT_DIM);
            motion.end(g);
            return;
        }
        motion.beginContent(g);
        List<Card> cards = cards(powers);
        int listX = px + PAD;
        int listW = pw - SIDEBAR - PAD * 3;
        int bottom = py + ph - PAD;
        int listTop = Ui.section(g, font, listX, top + 3, listW, Component.translatable("screen.noirm.training.active"), Theme.TEXT_DIM);
        int viewH = bottom - listTop;
        // Measure, clamp the scroll, then draw inside the list's box.
        int total = 0;
        for (Card c : cards) {
            total += drawCard(g, c, listX, 0, listW - 4, false, mouseX, mouseY) + Theme.S3;
        }
        contentH = total;
        scrollPx = Mth.clamp(scrollPx, 0, Math.max(0, total - viewH));
        clipTop = listTop;
        clipBottom = bottom;
        g.enableScissor(listX, listTop, listX + listW, bottom);
        int y = listTop - scrollPx;
        for (Card c : cards) {
            int h = drawCard(g, c, listX, y, listW - 4, y < bottom, mouseX, mouseY);
            y += h + Theme.S3;
        }
        g.disableScissor();
        if (cards.isEmpty()) {
            Ui.wrap(g, font, Component.translatable("screen.noirm.training.none"), listX, listTop + 4, listW - 4, bottom, Theme.TEXT_FAINT);
        }
        if (total > viewH) {
            int thumbH = Math.max(10, viewH * viewH / total);
            int thumbY = listTop + (viewH - thumbH) * scrollPx / Math.max(1, total - viewH);
            g.fill(listX + listW - 1, listTop, listX + listW, bottom, Theme.HAIRLINE);
            g.fill(listX + listW - 2, thumbY, listX + listW, thumbY + thumbH, Theme.IRON);
        }
        drawSidebar(g, powers, px + pw - SIDEBAR - PAD, top, bottom, mouseX, mouseY);
        motion.endContent(g);
        motion.end(g);
    }

    /** Draws a card (or only measures it, when {@code draw} is false); returns its height. */
    private int drawCard(GuiGraphics g, Card c, int x, int y, int w, boolean draw, int mouseX, int mouseY) {
        int inner = w - 12;
        int top = y;
        int h = measure(c, inner);
        if (draw) {
            Ui.card(g, x, y, w, h, c.accent);
        }
        y += 5;
        int ix = x + 6;
        if (draw) {
            int bw = c.badge.isEmpty() ? 0 : font.width(c.badge) + 4;
            Ui.text(g, font, c.title, ix, y, inner - bw, c.accent);
            if (bw > 0) {
                Ui.textRight(g, font, c.badge, ix + inner, y, Theme.GOLD);
            }
        }
        y += Theme.LINE + 2;
        // Steps: chips that wrap.
        int sx = ix;
        for (Step step : c.steps) {
            String text = (step.state() == 2 ? "✔ " : step.state() == 1 ? "▶ " : "◦ ") + step.label().getString();
            int cw = Math.min(inner, font.width(text) + 6);
            if (sx + cw > ix + inner && sx > ix) {
                sx = ix;
                y += 13;
            }
            if (draw) {
                int color = step.state() == 2 ? Theme.GREEN : step.state() == 1 ? Theme.GOLD : Theme.TEXT_FAINT;
                g.fill(sx, y, sx + cw, y + 11, Theme.mix(Theme.RAISED, color, step.state() == 0 ? 0.05f : 0.16f));
                g.drawString(font, Ui.fit(font, text, cw - 6), sx + 3, y + 2, color, false);
            }
            sx += cw + 3;
        }
        if (!c.steps.isEmpty()) {
            y += 14;
        }
        if (c.share >= 0f) {
            if (draw) {
                Ui.bar(g, ix, y, inner, 3, Math.min(1f, c.share), c.accent);
            }
            y += 6;
        }
        if (c.progress != null) {
            y = wrapLines(g, c.progress, ix, y, inner, Theme.TEXT, draw);
        }
        for (Component tip : c.tips) {
            y = wrapLines(g, Component.literal("› ").append(tip), ix, y, inner, Theme.TEXT_DIM, draw);
        }
        if (c.next != null) {
            y = wrapLines(g, Component.translatable("screen.noirm.training.next").append(" ").append(c.next), ix, y + 1, inner, NEXT, draw);
        }
        if (!c.actions.isEmpty()) {
            y += 2;
            int bx = ix;
            for (int i = 0; i < c.actions.size(); i++) {
                Action a = c.actions.get(i);
                int bw = Math.min(inner, font.width(a.label()) + 14);
                if (draw) {
                    boolean over = Ui.in(mouseX, mouseY, bx, y, bw, 13);
                    Ui.button(g, font, bx, y, bw, 13, a.label(), hover.get(HoverTracker.key(7, c.key.hashCode(), i), over), true,
                            a.primary() ? Theme.SEAL : c.accent, a.primary());
                    if (y >= clipTop && y + 13 <= clipBottom) {
                        hits.add(new Hit(bx, y, bw, 13, a.run()));
                    }
                }
                bx += bw + 4;
            }
            y += 13;
        }
        return Math.max(h, y + 5 - top);
    }

    private int measure(Card c, int inner) {
        int y = 5 + Theme.LINE + 2;
        int sx = 0;
        for (Step step : c.steps) {
            String text = "✔ " + step.label().getString();
            int cw = Math.min(inner, font.width(text) + 6);
            if (sx + cw > inner && sx > 0) {
                sx = 0;
                y += 13;
            }
            sx += cw + 3;
        }
        if (!c.steps.isEmpty()) {
            y += 14;
        }
        if (c.share >= 0f) {
            y += 6;
        }
        if (c.progress != null) {
            y += lines(c.progress, inner) * 9;
        }
        for (Component tip : c.tips) {
            y += lines(Component.literal("› ").append(tip), inner) * 9;
        }
        if (c.next != null) {
            y += 1 + lines(Component.translatable("screen.noirm.training.next").append(" ").append(c.next), inner) * 9;
        }
        if (!c.actions.isEmpty()) {
            y += 15;
        }
        return y + 5;
    }

    private int lines(Component text, int w) {
        return font.split(FormattedText.composite(text), Math.max(10, w)).size();
    }

    private int wrapLines(GuiGraphics g, Component text, int x, int y, int w, int color, boolean draw) {
        for (FormattedCharSequence line : font.split(FormattedText.composite(text), Math.max(10, w))) {
            if (draw) {
                g.drawString(font, line, x, y, color, false);
            }
            y += 9;
        }
        return y;
    }

    // ---------------------------------------------------------------- the cards

    private List<Card> cards(PowerPackets.SyncPowers powers) {
        List<Card> out = new ArrayList<>();
        ArtsSync arts = ArtsScreens.arts();
        SyncShinobiDataPacket stats = ClientShinobiData.get();
        Map<Nature, Integer> best = ArtsScreens.bestMastery(powers);
        if (arts.trialNature() >= 0 && arts.trialNature() < Nature.values().length) {
            out.add(trialCard(Nature.values()[arts.trialNature()], arts, stats));
        }
        NaturePackets.Sync drills = ClientNatureTraining.get();
        for (Nature nature : NatureDrills.DRILLABLE) {
            if (ArtsScreens.owns(powers, nature)) {
                continue;
            }
            boolean active = drills.active() == nature.ordinal();
            if (active || ClientNatureTraining.share(nature) > 0f) {
                out.add(drillCard(nature, active, drills, stats));
            }
        }
        for (ChakraShape shape : ChakraShape.values()) {
            if (arts.stage(shape) > 0 || arts.share(shape) > 0f || arts.activeShape() == shape.ordinal()) {
                out.add(shapeCard(shape, arts, powers, best));
            }
        }
        return out;
    }

    private static MutableComponent natureName(Nature n) {
        return Component.translatable(n.translationKey()).withStyle(s -> s.withColor(n.color()));
    }

    private static int pct(float share) {
        return Math.round(Math.min(1f, share) * 100);
    }

    private Card trialCard(Nature nature, ArtsSync arts, SyncShinobiDataPacket stats) {
        Card c = new Card();
        c.key = "trial";
        c.accent = 0xFF000000 | nature.color();
        c.title = Component.translatable("training.noirm.card.trial", natureName(nature));
        List<FusionTrial.Stage> stages = FusionTrial.stages(nature);
        int current = Math.min(stages.size() - 1, arts.trialStage());
        c.badge = Component.translatable("training.noirm.badge.stage", current + 1, stages.size()).getString();
        for (int i = 0; i < stages.size(); i++) {
            FusionTrial.Stage st = stages.get(i);
            Component label = st.part() != null
                    ? Component.translatable(FusionTrial.stepKey(st)).append(": ").append(Component.translatable(st.part().translationKey()))
                    : Component.translatable(FusionTrial.stepKey(st));
            c.steps.add(new Step(label, i < current ? 2 : i == current ? 1 : 0));
        }
        FusionTrial.Stage now = stages.get(current);
        c.share = arts.trialShare();
        float ctl = stats == null ? 0f : stats.control();
        float wil = stats == null ? 0f : stats.willpower();
        switch (now.step()) {
            case ATTUNE -> {
                c.progress = Component.translatable("training.noirm.progress.moving", pct(c.share),
                        Component.translatable(arts.movingTrial() ? "training.noirm.state.moving" : "training.noirm.state.paused"));
                c.tips.add(Component.translatable("training.noirm.fusion.tip.attune", natureName(now.part()),
                        Component.translatable("drill.noirm." + now.part().getSerializedName())));
            }
            case BLEND -> {
                c.progress = Component.translatable("training.noirm.progress.moving", pct(c.share),
                        Component.translatable(arts.movingTrial() ? "training.noirm.state.moving" : "training.noirm.state.paused"));
                c.tips.add(Component.translatable("training.noirm.fusion.tip.blend"));
                c.tips.add(Component.translatable("training.noirm.fusion.tip.risk", pct(FusionTrial.failChance(ctl, wil)),
                        Math.round(FusionTrial.CHECKPOINT_SECONDS / 60f), pct(FusionTrial.FAIL_PROGRESS_LOSS), pct(FusionTrial.FAIL_HEALTH),
                        pct(FusionTrial.FAIL_CHAKRA)).withStyle(s -> s.withColor(Theme.RED)));
            }
            case PROOF -> {
                c.progress = Component.translatable("training.noirm.progress.weaves", Math.round(arts.trialProgress()), FusionTrial.PROOF_WEAVES);
                c.tips.add(Component.translatable("training.noirm.fusion.tip.proof", Math.round(FusionTrial.PROOF_WINDOW_TICKS / 20f)));
            }
        }
        if (arts.trialFails() > 0) {
            c.tips.add(Component.translatable("training.noirm.fusion.fails", arts.trialFails()));
        }
        c.next = Component.translatable("training.noirm.next.fused", natureName(nature));
        c.actions.add(new Action(Component.translatable(confirmAbandon ? "training.noirm.action.abandon_sure" : "training.noirm.action.abandon"),
                () -> {
                    if (confirmAbandon) {
                        NoirmNetwork.sendToServer(new WorkshopPackets.Act(WorkshopPackets.Action.ABANDON_FUSION, "", -1));
                        confirmAbandon = false;
                    } else {
                        confirmAbandon = true;
                    }
                }, false));
        return c;
    }

    private Card drillCard(Nature nature, boolean active, NaturePackets.Sync drills, SyncShinobiDataPacket stats) {
        Card c = new Card();
        c.key = "drill:" + nature.getSerializedName();
        c.accent = 0xFF000000 | nature.color();
        c.title = Component.translatable("training.noirm.card.drill", natureName(nature));
        float share = ClientNatureTraining.share(nature);
        c.badge = pct(share) + "%";
        boolean moving = active && drills.pace() > 0f;
        String gate = gateText(nature, stats);
        if (gate != null) {
            c.steps.add(new Step(Component.translatable(gate, gateNeed(nature), Math.round(gateHave(nature, stats))), gateOk(nature, stats) ? 2 : 1));
        }
        c.steps.add(new Step(Component.translatable("training.noirm.drill.step.setting"), moving ? 2 : 1));
        c.steps.add(new Step(Component.translatable("training.noirm.drill.step.meditate"), moving ? 1 : 0));
        c.steps.add(new Step(Component.translatable("training.noirm.drill.step.learn", natureName(nature)), 0));
        c.share = share;
        c.progress = moving ? Component.translatable("training.noirm.progress.pace", pct(share), String.format(Locale.ROOT, "%.1f", drills.pace()))
                : Component.translatable("training.noirm.progress.moving", pct(share), Component.translatable("training.noirm.state.paused"));
        c.tips.add(Component.translatable("drill.noirm." + nature.getSerializedName()));
        if (ClientNatureTraining.isHidden(nature)) {
            c.tips.add(Component.translatable("training.noirm.drill.tip.hidden", Math.round(NatureDrills.HIDDEN_PACE)));
        }
        c.tips.add(Component.translatable("training.noirm.drill.tip.teacher"));
        if (nature == Nature.YIN || nature == Nature.YANG) {
            c.tips.add(Component.translatable("training.noirm.drill.tip.strong_" + nature.getSerializedName(), Math.round(SpiritPaths.STRONG_STAT)));
        }
        c.next = Component.translatable("training.noirm.next.nature", natureName(nature));
        if (!active) {
            c.actions.add(new Action(Component.translatable("training.noirm.action.focus"),
                    () -> NoirmNetwork.sendToServer(new WorkshopPackets.Act(WorkshopPackets.Action.START_DRILL, "", nature.ordinal())), false));
        }
        return c;
    }

    private static String gateText(Nature nature, SyncShinobiDataPacket stats) {
        return switch (nature) {
            case YIN -> "training.noirm.drill.step.gate_wil";
            case YANG -> "training.noirm.drill.step.gate_vit";
            default -> null;
        };
    }

    private static int gateNeed(Nature nature) {
        return Math.round(nature == Nature.YIN ? SpiritPaths.YIN_MIN_WIL : SpiritPaths.YANG_MIN_VITALITY);
    }

    private static float gateHave(Nature nature, SyncShinobiDataPacket stats) {
        if (stats == null) {
            return 0f;
        }
        return nature == Nature.YIN ? stats.willpower() : stats.vitality();
    }

    private static boolean gateOk(Nature nature, SyncShinobiDataPacket stats) {
        return gateHave(nature, stats) >= gateNeed(nature);
    }

    private Card shapeCard(ChakraShape shape, ArtsSync arts, PowerPackets.SyncPowers powers, Map<Nature, Integer> best) {
        Card c = new Card();
        c.key = "shape:" + shape.key();
        c.accent = 0xFF000000 | shape.color();
        int stage = arts.stage(shape);
        c.title = Component.literal(shape.kanji() + " ").append(Component.translatable(shape.translationKey()));
        c.badge = stage >= ShapeTraining.STAGES ? Component.translatable("training.noirm.badge.mastered").getString()
                : Component.translatable("training.noirm.badge.stage", stage, ShapeTraining.STAGES).getString();
        for (int i = 0; i < ShapeTraining.STAGES; i++) {
            c.steps.add(new Step(Component.translatable(ShapeTraining.stepKey(shape, i)), i < stage ? 2 : i == stage ? 1 : 0));
        }
        List<Nature> fused = new ArrayList<>();
        for (Nature n : Nature.values()) {
            if (arts.fused(shape, n)) {
                fused.add(n);
            }
        }
        c.steps.add(new Step(Component.translatable("training.noirm.shape.step.fusion"),
                !fused.isEmpty() ? 2 : stage >= ShapeTraining.STAGES ? 1 : 0));
        Component focus = new ItemStack(shape.focus()).getHoverName();
        boolean moving = arts.movingShape() == shape.ordinal();
        if (stage < ShapeTraining.STAGES) {
            c.share = arts.share(shape);
            c.progress = Component.translatable("training.noirm.progress.shape", Component.translatable(ShapeTraining.stepKey(shape, stage)),
                    pct(c.share), Component.translatable(moving ? "training.noirm.state.moving" : "training.noirm.state.paused"),
                    String.format(Locale.ROOT, "%.1f", ShapeTraining.STAGE_DRAIN[stage]));
            c.tips.add(Component.translatable("training.noirm.shape.tip.hold", focus));
            c.tips.add(Component.translatable("training.noirm.shape.tip." + ShapeTraining.STEP_KEYS[stage],
                    Math.round(ShapeTraining.POWER_CAST_SECONDS), pct(ShapeTraining.CONTAIN_MIN_SHARE)));
            c.next = Component.translatable("training.noirm.next.shape", Workshop.rank(Workshop.SHAPE_CAP[stage + 1]),
                    Math.round(ShapeTraining.CANON_POWER_PER_STAGE * 100 * (stage + 1)), Math.round(ShapeTraining.CANON_COST_CUT_PER_STAGE * 100 * (stage + 1)));
        } else {
            int bestPair = -1;
            float bestShare = 0f;
            for (int i = 0; i < arts.pairs().size(); i++) {
                int p = arts.pairs().get(i);
                if (ArtsData.pairShape(p) == shape && (arts.pairShares().get(i) > bestShare || p == arts.movingPair())) {
                    bestPair = p;
                    bestShare = arts.pairShares().get(i);
                }
            }
            if (bestPair >= 0) {
                c.share = bestShare;
                c.progress = Component.translatable("training.noirm.progress.pair", natureName(ArtsData.pairNature(bestPair)), pct(bestShare),
                        Component.translatable(arts.movingPair() == bestPair ? "training.noirm.state.moving" : "training.noirm.state.paused"));
            }
            MutableComponent ready = Component.empty();
            int count = 0;
            for (Nature n : Nature.values()) {
                if (!arts.fused(shape, n) && ArtsScreens.owns(powers, n) && best.getOrDefault(n, 0) >= ShapeTraining.FUSION_NATURE_MASTERY) {
                    if (count++ > 0) {
                        ready.append(", ");
                    }
                    ready.append(natureName(n));
                }
            }
            c.tips.add(Component.translatable("training.noirm.shape.tip.fusion", focus, ShapeTraining.FUSION_NATURE_MASTERY));
            c.tips.add(count > 0 ? Component.translatable("training.noirm.shape.tip.fusion_ready", ready)
                    : Component.translatable("training.noirm.shape.tip.fusion_none", ShapeTraining.FUSION_NATURE_MASTERY));
            if (!fused.isEmpty()) {
                MutableComponent list = Component.empty();
                for (int i = 0; i < fused.size(); i++) {
                    if (i > 0) {
                        list.append(", ");
                    }
                    list.append(natureName(fused.get(i)));
                }
                c.tips.add(Component.translatable("training.noirm.shape.fused", list));
            }
            c.next = Component.translatable("training.noirm.next.fusion", Component.translatable(shape.translationKey()));
        }
        if (arts.activeShape() != shape.ordinal()) {
            c.actions.add(new Action(Component.translatable("training.noirm.action.focus"),
                    () -> NoirmNetwork.sendToServer(new WorkshopPackets.Act(WorkshopPackets.Action.PICK_SHAPE, shape.key(), -1)), false));
        }
        return c;
    }

    // ---------------------------------------------------------------- the side column

    private void drawSidebar(GuiGraphics g, PowerPackets.SyncPowers powers, int x, int top, int bottom, int mouseX, int mouseY) {
        Ui.vline(g, x - PAD / 2 - 1, top, bottom - top);
        int y = Ui.section(g, font, x, top + 3, SIDEBAR, Component.translatable("screen.noirm.training.now"), Theme.TEXT_DIM);
        Component now = powers.meditating() ? Component.translatable("screen.noirm.training.meditating")
                : Component.translatable("screen.noirm.training.not_meditating");
        y = Ui.wrap(g, font, now, x, y, SIDEBAR, bottom, powers.meditating() ? Theme.GREEN : Theme.TEXT_DIM) + Theme.S3;
        y = Ui.section(g, font, x, y, SIDEBAR, Component.translatable("screen.noirm.training.begin"), Theme.TEXT_DIM);
        ArtsSync arts = ArtsScreens.arts();
        Map<Nature, Integer> best = ArtsScreens.bestMastery(powers);
        Map<Nature, Float> owned = new java.util.EnumMap<>(Nature.class);
        for (int i = 0; i < powers.natures().size(); i++) {
            owned.put(Nature.values()[powers.natures().get(i)], powers.affinities().get(i));
        }
        int rowH = 12;
        // Fusion trials you could begin.
        for (Nature n : Nature.values()) {
            if (y + rowH > bottom) {
                break;
            }
            if (FusionTrial.fusable(n) && NatureSystem.status(n, owned, best) == NatureSystem.Status.AVAILABLE && arts.trialNature() != n.ordinal()) {
                y = beginRow(g, x, y, Component.translatable("screen.noirm.training.begin_trial", natureName(n)), 0xFF000000 | n.color(),
                        "trial" + n.ordinal(), mouseX, mouseY,
                        () -> NoirmNetwork.sendToServer(new WorkshopPackets.Act(WorkshopPackets.Action.START_FUSION, "", n.ordinal())));
            }
        }
        // Shapes not begun: what to hold.
        for (ChakraShape shape : ChakraShape.values()) {
            if (y + rowH > bottom) {
                break;
            }
            if (arts.stage(shape) == 0 && arts.share(shape) <= 0f && arts.activeShape() != shape.ordinal()) {
                Component label = Component.literal(shape.kanji() + " ").append(Component.translatable(shape.translationKey()))
                        .append(" · ").append(new ItemStack(shape.focus()).getHoverName());
                y = beginRow(g, x, y, label, 0xFF000000 | shape.color(), "shape" + shape.ordinal(), mouseX, mouseY,
                        () -> NoirmNetwork.sendToServer(new WorkshopPackets.Act(WorkshopPackets.Action.PICK_SHAPE, shape.key(), -1)));
            }
        }
        // Yin, Yang, Yin-Yang drills not begun.
        for (Nature n : List.of(Nature.YIN, Nature.YANG, Nature.YIN_YANG)) {
            if (y + rowH > bottom) {
                break;
            }
            if (!owned.containsKey(n) && ClientNatureTraining.share(n) <= 0f && ClientNatureTraining.get().active() != n.ordinal()) {
                y = beginRow(g, x, y, Component.translatable("screen.noirm.training.begin_drill", natureName(n)), 0xFF000000 | n.color(),
                        "drill" + n.ordinal(), mouseX, mouseY,
                        () -> NoirmNetwork.sendToServer(new WorkshopPackets.Act(WorkshopPackets.Action.START_DRILL, "", n.ordinal())));
            }
        }
    }

    private int beginRow(GuiGraphics g, int x, int y, Component label, int color, String key, int mouseX, int mouseY, Runnable run) {
        boolean over = Ui.in(mouseX, mouseY, x, y, SIDEBAR, 12);
        Ui.row(g, x, y, SIDEBAR, 12, hover.get(key.hashCode(), over), false, color);
        boolean cut = Ui.text(g, font, label, x + 4, y + 2, SIDEBAR - 6, Theme.mix(Theme.TEXT_DIM, Theme.TEXT, over ? 1f : 0f));
        if (over && cut) {
            setTooltipForNextRenderPass(label);
        }
        hits.add(new Hit(x, y, SIDEBAR, 12, run));
        return y + 13;
    }

    // ================================================================ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (motion.closing()) {
            return true;
        }
        int t = tabs.at(mouseX, mouseY);
        if (button == 0 && t >= 0 && t != ArtsScreens.TAB_TRAINING) {
            click();
            ArtsScreens.switchTo(t);
            return true;
        }
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit hit = hits.get(i);
                if (hit.contains(mouseX, mouseY)) {
                    hit.action().run();
                    click();
                    return true;
                }
            }
            confirmAbandon = false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollPx = Mth.clamp(scrollPx - (int) Math.signum(delta) * 18, 0, Math.max(0, contentH));
        return true;
    }

    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1f, 0.35f));
        }
    }
}
