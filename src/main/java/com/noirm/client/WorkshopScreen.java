package com.noirm.client;

import com.noirm.arts.ArtsSync;
import com.noirm.arts.ChakraShape;
import com.noirm.arts.ShapeTraining;
import com.noirm.clan.Nature;
import com.noirm.client.ui.HoverTracker;
import com.noirm.client.ui.NoirmStyled;
import com.noirm.client.ui.ScreenMotion;
import com.noirm.client.ui.TabStrip;
import com.noirm.client.ui.Theme;
import com.noirm.client.ui.Ui;
import com.noirm.network.NoirmNetwork;
import com.noirm.network.PowerPackets;
import com.noirm.workshop.JutsuDesign;
import com.noirm.workshop.Workshop;
import com.noirm.workshop.WorkshopPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The jutsu workshop (the J screen's Workshop tab). <b>Design</b>: pick a nature you own, a shape,
 * and four trade-offs (size, speed, power, range), name it, and see what it comes out as (rank,
 * chakra, cooldown, seals, damage, backlash, misfires, and whether your stats and the shape's stage
 * allow it) before you make it. <b>Creations</b>: your jutsu, their mastery, writing them on a
 * scroll and forgetting them. The numbers come from {@link Workshop}, the same code the server uses.
 */
public class WorkshopScreen extends Screen implements NoirmStyled {
    private static final int PAD = Theme.S5;
    private static final String[] SLIDERS = {"size", "speed", "power", "range"};

    // The draft survives closing the screen.
    private static int draftNature = -1;
    private static ChakraShape draftShape = ChakraShape.BALL;
    private static final int[] DRAFT = {3, 3, 3, 3};
    private static String draftName = "";
    private static boolean creationsView;
    private static String selectedCreation;

    private final ScreenMotion motion = new ScreenMotion();
    private final TabStrip tabs = new TabStrip();
    private final HoverTracker hover = new HoverTracker();
    private String[] tabLabels;
    private EditBox nameBox;
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int leftX;
    private int leftW;
    private int rightX;
    private int rightW;
    private int bodyTop;
    private int dragging = -1;
    private boolean confirmForget;

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    /** Slider tracks this frame: x, y, w. */
    private final int[][] sliderBoxes = new int[4][3];

    public WorkshopScreen() {
        super(Component.translatable("screen.noirm.workshop"));
        tabs.snap(ArtsScreens.TAB_WORKSHOP);
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 520);
        ph = Math.min(height - 8, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        tabLabels = ArtsScreens.tabLabels();
        bodyTop = py + PAD + Theme.ROW + Theme.S4 + 14 + Theme.S3;
        int inner = pw - PAD * 3;
        leftX = px + PAD;
        leftW = Math.round(inner * 0.5f);
        rightX = leftX + leftW + PAD;
        rightW = inner - leftW;
        nameBox = new EditBox(font, rightX + 3, bodyTop + Theme.LINE + 3, rightW - 6, 10, Component.translatable("screen.noirm.workshop.name"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(JutsuDesign.NAME_MAX);
        nameBox.setTextColor(Theme.TEXT);
        nameBox.setValue(draftName);
        nameBox.setHint(Component.translatable("screen.noirm.workshop.name_hint").withStyle(s -> s.withColor(Theme.TEXT_FAINT)));
        nameBox.setResponder(s -> draftName = s);
        nameBox.visible = !creationsView;
        addRenderableWidget(nameBox);
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
        if (nameBox != null) {
            nameBox.tick();
        }
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
        tabs.draw(g, font, px + PAD, py + PAD, pw - PAD * 2, tabLabels, ArtsScreens.TAB_WORKSHOP, new int[]{Theme.SEAL}, mouseX, mouseY);
        PowerPackets.SyncPowers powers = ClientPowers.get();
        if (powers == null) {
            Ui.textCentered(g, font, Component.translatable("screen.noirm.jutsu.loading").getString(), px + pw / 2, py + ph / 2, Theme.TEXT_DIM);
            motion.end(g);
            return;
        }
        int segY = py + PAD + Theme.ROW + Theme.S4;
        int segW = Math.min(leftW, 200);
        Component[] seg = {Component.translatable("screen.noirm.workshop.view.design"),
                Component.translatable("screen.noirm.workshop.view.creations", powers.creations().size())};
        for (int i = 0; i < 2; i++) {
            int sx = Ui.segX(leftX, segW, i, 2);
            int sw = Ui.segW(segW, i, 2);
            boolean over = Ui.in(mouseX, mouseY, sx, segY, sw, 14);
            Ui.segment(g, font, leftX, segY, segW, 14, i, 2, seg[i], creationsView == (i == 1), hover.get(HoverTracker.key(10, i), over), Theme.SEAL);
            boolean toCreations = i == 1;
            hits.add(new Hit(sx, segY, sw, 14, () -> {
                if (creationsView != toCreations) {
                    creationsView = toCreations;
                    nameBox.visible = !creationsView;
                    if (creationsView) {
                        setFocused(null);
                        nameBox.setFocused(false);
                    }
                    confirmForget = false;
                    motion.tabChanged();
                }
            }));
        }
        motion.beginContent(g);
        if (creationsView) {
            drawCreations(g, powers, mouseX, mouseY);
        } else {
            drawDesign(g, powers, mouseX, mouseY);
        }
        motion.endContent(g);
        motion.end(g);
        if (!creationsView) {
            g.fill(nameBox.getX() - 3, nameBox.getY() - 2, nameBox.getX() + nameBox.getWidth() + 3, nameBox.getY() + 10,
                    nameBox.isFocused() ? Theme.RAISED_HOVER : Theme.WELL);
            if (nameBox.isFocused()) {
                g.fill(nameBox.getX() - 3, nameBox.getY() + 9, nameBox.getX() + nameBox.getWidth() + 3, nameBox.getY() + 10, Theme.SEAL);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    // ---------------------------------------------------------------- design

    private void drawDesign(GuiGraphics g, PowerPackets.SyncPowers powers, int mouseX, int mouseY) {
        int bottom = py + ph - PAD;
        ArtsSync arts = ArtsScreens.arts();
        List<Nature> owned = new ArrayList<>();
        for (Nature n : Nature.values()) {
            if (ArtsScreens.owns(powers, n)) {
                owned.add(n);
            }
        }
        if (draftNature < 0 || draftNature >= Nature.values().length || !owned.contains(Nature.values()[draftNature])) {
            draftNature = owned.isEmpty() ? -1 : owned.get(0).ordinal();
        }
        // Left: nature, shape, trade-offs.
        int y = Ui.section(g, font, leftX, bodyTop, leftW, Component.translatable("screen.noirm.workshop.nature"), Theme.TEXT_DIM);
        int x = leftX;
        for (Nature n : owned) {
            int cw = Math.max(18, font.width(n.kanji()) + 6);
            if (x + cw > leftX + leftW) {
                x = leftX;
                y += 15;
            }
            boolean on = n.ordinal() == draftNature;
            boolean over = Ui.in(mouseX, mouseY, x, y, cw, 13);
            int color = 0xFF000000 | n.color();
            g.fill(x, y, x + cw, y + 13, on ? Theme.mix(Theme.RAISED, color, 0.28f) : Theme.mix(Theme.RAISED, Theme.RAISED_HOVER, hover.get(HoverTracker.key(11, n.ordinal()), over)));
            if (on) {
                g.fill(x, y + 12, x + cw, y + 13, color);
            }
            Ui.textCentered(g, font, n.kanji(), x + cw / 2, y + 3, on ? color : Theme.mix(Theme.darken(color, 0.6f), color, over ? 1f : 0f));
            if (over) {
                setTooltipForNextRenderPass(Component.translatable(n.translationKey()));
            }
            hits.add(new Hit(x, y, cw, 13, () -> draftNature = n.ordinal()));
            x += cw + 2;
        }
        if (owned.isEmpty()) {
            Ui.text(g, font, Component.translatable("screen.noirm.workshop.no_nature"), leftX, y, leftW, Theme.TEXT_FAINT);
        }
        y += 13 + Theme.S3;
        y = Ui.section(g, font, leftX, y, leftW, Component.translatable("screen.noirm.workshop.shape"), Theme.TEXT_DIM);
        int cols = 4;
        int bw = (leftW - 3 * (cols - 1)) / cols;
        for (ChakraShape shape : ChakraShape.values()) {
            int i = shape.ordinal();
            int bx = leftX + (i % cols) * (bw + 3);
            int by = y + (i / cols) * 15;
            int stage = arts.stage(shape);
            boolean on = shape == draftShape;
            boolean over = Ui.in(mouseX, mouseY, bx, by, bw, 13);
            int color = 0xFF000000 | shape.color();
            float hv = hover.get(HoverTracker.key(12, i), over);
            g.fill(bx, by, bx + bw, by + 13, on ? Theme.mix(Theme.RAISED, color, 0.24f) : Theme.mix(Theme.RAISED, Theme.RAISED_HOVER, hv));
            if (on) {
                g.fill(bx, by + 12, bx + bw, by + 13, color);
            }
            String label = shape.kanji() + " " + Component.translatable(shape.translationKey()).getString();
            int textColor = stage == 0 ? Theme.TEXT_FAINT : on ? Theme.TEXT : Theme.mix(Theme.TEXT_DIM, Theme.TEXT, hv);
            g.drawString(font, Ui.fit(font, label, bw - 8), bx + 3, by + 3, textColor, false);
            for (int s = 0; s < stage; s++) { // stage pips
                g.fill(bx + bw - 3, by + 2 + s * 3, bx + bw - 1, by + 4 + s * 3, Theme.GOLD);
            }
            if (over) {
                setTooltipForNextRenderPass(stage == 0
                        ? Component.translatable("screen.noirm.workshop.shape_untrained", Component.translatable(shape.translationKey()),
                        new ItemStack(shape.focus()).getHoverName())
                        : Component.translatable("screen.noirm.workshop.shape_stage", Component.translatable(shape.translationKey()), stage,
                        ShapeTraining.STAGES, Workshop.rank(Workshop.SHAPE_CAP[stage])));
            }
            hits.add(new Hit(bx, by, bw, 13, () -> draftShape = shape));
        }
        y += 2 * 15 + Theme.S2;
        y = Ui.section(g, font, leftX, y, leftW, Component.translatable("screen.noirm.workshop.tradeoffs"), Theme.TEXT_DIM);
        int labelW = 0;
        for (String key : SLIDERS) {
            labelW = Math.max(labelW, font.width(Component.translatable("screen.noirm.workshop.slider." + key)));
        }
        labelW = Math.min(labelW + 4, leftW / 3);
        for (int i = 0; i < 4; i++) {
            String key = SLIDERS[i];
            Component label = Component.translatable("screen.noirm.workshop.slider." + key);
            Ui.text(g, font, label, leftX, y + 1, labelW, Theme.TEXT_DIM);
            int sx = leftX + labelW + 2;
            int sw = leftW - labelW - 16;
            boolean over = Ui.in(mouseX, mouseY, sx - 2, y - 1, sw + 4, 11);
            for (int k = 0; k < 5; k++) { // notches
                int nx = sx + Math.round(k * (sw - 1) / 4f);
                g.fill(nx, y + 6, nx + 1, y + 8, Theme.IRON);
            }
            Ui.slider(g, sx, y, sw, (DRAFT[i] - 1) / 4f, Theme.SEAL, hover.get(HoverTracker.key(13, i), over || dragging == i));
            Ui.textRight(g, font, String.valueOf(DRAFT[i]), leftX + leftW, y + 1, Theme.TEXT);
            sliderBoxes[i][0] = sx;
            sliderBoxes[i][1] = y - 1;
            sliderBoxes[i][2] = sw;
            if (over && dragging < 0) {
                setTooltipForNextRenderPass(Component.translatable("screen.noirm.workshop.slider." + key + ".tip"));
            }
            y += 12;
        }
        // Right: the name, the preview, the button.
        Ui.section(g, font, rightX, bodyTop, rightW, Component.translatable("screen.noirm.workshop.name"), Theme.TEXT_DIM);
        int ry = bodyTop + Theme.LINE + 3 + 10 + Theme.S3;
        ry = Ui.section(g, font, rightX, ry, rightW, Component.translatable("screen.noirm.workshop.preview"), Theme.TEXT_DIM);
        int buttonY = bottom - 15;
        if (draftNature < 0) {
            Ui.wrap(g, font, Component.translatable("screen.noirm.workshop.no_nature"), rightX, ry, rightW, buttonY - 2, Theme.TEXT_FAINT);
            return;
        }
        Nature nature = Nature.values()[draftNature];
        JutsuDesign design = new JutsuDesign(new UUID(0, 0), draftName, nature, draftShape, DRAFT[0], DRAFT[1], DRAFT[2], DRAFT[3], null, "");
        Workshop.Stats s = Workshop.stats(design);
        Workshop.Limits limits = ArtsScreens.limits();
        Component problem = Workshop.problem(limits, draftName, nature, draftShape.key(), DRAFT[0], DRAFT[1], DRAFT[2], DRAFT[3]);
        ry = preview(g, design, s, limits, rightX, ry, rightW, buttonY - 3, true);
        if (problem != null && ry + 9 <= buttonY - 2) {
            Ui.wrap(g, font, Component.literal("✘ ").append(problem), rightX, ry + 1, rightW, buttonY - 2, Theme.RED);
        }
        boolean over = Ui.in(mouseX, mouseY, rightX, buttonY, rightW, 15);
        Ui.button(g, font, rightX, buttonY, rightW, 15, Component.translatable("screen.noirm.workshop.create"),
                hover.get(HoverTracker.key(14, 0), over), problem == null, Theme.SEAL, true);
        if (problem == null) {
            hits.add(new Hit(rightX, buttonY, rightW, 15, () -> {
                NoirmNetwork.sendToServer(new WorkshopPackets.Create(draftName, draftNature, draftShape.key(), DRAFT[0], DRAFT[1], DRAFT[2], DRAFT[3]));
                draftName = "";
                nameBox.setValue("");
            }));
        } else if (over) {
            setTooltipForNextRenderPass(problem);
        }
    }

    /** The numbers of a design; returns the y below them. {@code forDraft}: also the stat and shape checks. */
    private int preview(GuiGraphics g, JutsuDesign d, Workshop.Stats s, Workshop.Limits limits, int x, int y, int w, int maxY, boolean forDraft) {
        int natureColor = 0xFF000000 | d.nature().color();
        String rank = s.rank();
        int rankColor = switch (rank) {
            case "S" -> Theme.GOLD;
            case "A" -> Theme.RED;
            case "B" -> Theme.CHAKRA_LIGHT;
            default -> Theme.TEXT_DIM;
        };
        int chipW = Ui.chip(g, font, x + w - font.width(rank + "-rank") - 6, y - 1, rank + "-rank", rankColor);
        String title = d.name().isEmpty() ? Component.translatable("screen.noirm.workshop.unnamed").getString() : d.name();
        Ui.text(g, font, Component.literal(title), x, y, w - chipW - 4, d.name().isEmpty() ? Theme.TEXT_FAINT : natureColor);
        y += Theme.LINE + 1;
        y = line(g, Component.translatable("screen.noirm.workshop.p.kind", Component.translatable(d.nature().translationKey()),
                Component.translatable(d.shape().translationKey()), Component.translatable("screen.noirm.jutsu.behavior." + s.behavior()),
                s.weight()), x, y, w, maxY, Theme.TEXT_DIM);
        y = line(g, Component.translatable("screen.noirm.workshop.p.cost", fmt(s.cost()), fmt(s.cooldown() / 20f))
                .append(s.cast() == com.noirm.jutsu.CastType.CHARGE ? Component.translatable("screen.noirm.workshop.p.charge", fmt(s.maxCharge() / 20f))
                        : Component.empty()), x, y, w, maxY, Theme.CHAKRA_LIGHT);
        MutableComponent seals = Component.translatable("screen.noirm.workshop.p.seals");
        for (int i = 0; i < s.seals().size(); i++) {
            seals.append(i == 0 ? " " : " · ").append(Component.translatable("seal.noirm." + s.seals().get(i)));
        }
        y = line(g, seals, x, y, w, maxY, Theme.TEXT);
        JutsuDefinitionView v = new JutsuDefinitionView(s);
        y = line(g, Component.translatable("screen.noirm.workshop.p.numbers", fmt(s.damage()), fmt(v.size()), fmt(v.reach())), x, y, w, maxY, Theme.TEXT);
        y = line(g, s.backlash() > 0f ? Component.translatable("screen.noirm.workshop.p.backlash", Math.round(s.backlash() * 100))
                : Component.translatable("screen.noirm.workshop.p.no_backlash"), x, y, w, maxY, s.backlash() > 0f ? Theme.RED : Theme.TEXT_FAINT);
        if (forDraft) {
            y = line(g, Component.translatable("screen.noirm.workshop.p.misfire", Math.round(Workshop.MISFIRE[0] * 100)), x, y, w, maxY, Theme.TEXT_DIM);
            float budget = limits.budget().apply(d.nature());
            boolean ok = s.weight() <= budget;
            y = line(g, Component.literal(ok ? "✔ " : "✘ ").append(Component.translatable("screen.noirm.workshop.p.budget", s.weight(),
                    fmt(budget))), x, y, w, maxY, ok ? Theme.GOLD : Theme.RED);
            int stage = limits.stage().apply(d.shape());
            int cap = Workshop.shapeCap(stage, limits.fused().test(d.shape(), d.nature()));
            boolean capOk = s.weight() <= cap;
            y = line(g, Component.literal(capOk ? "✔ " : "✘ ").append(stage == 0
                    ? Component.translatable("screen.noirm.workshop.p.untrained", Component.translatable(d.shape().translationKey()))
                    : Component.translatable("screen.noirm.workshop.p.cap", Component.translatable(d.shape().translationKey()), stage,
                    Workshop.rank(cap))), x, y, w, maxY, capOk ? Theme.GOLD : Theme.RED);
        }
        return y;
    }

    /** Size and reach as the shape uses them. */
    private record JutsuDefinitionView(Workshop.Stats s) {
        float size() {
            return s.params().radius();
        }

        float reach() {
            return s.params().range() > 0 ? s.params().range() : s.params().radius();
        }
    }

    private int line(GuiGraphics g, Component text, int x, int y, int w, int maxY, int color) {
        if (y + 9 > maxY) {
            return y;
        }
        return Ui.wrap(g, font, text, x, y, w, maxY, color) - 1;
    }

    private static String fmt(float v) {
        return Math.abs(v - Math.round(v)) < 0.05f ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }

    // ---------------------------------------------------------------- creations

    private void drawCreations(GuiGraphics g, PowerPackets.SyncPowers powers, int mouseX, int mouseY) {
        int bottom = py + ph - PAD;
        List<JutsuDesign> list = powers.creations();
        int y = Ui.section(g, font, leftX, bodyTop, leftW, Component.translatable("screen.noirm.workshop.yours"), Theme.TEXT_DIM);
        if (list.isEmpty()) {
            Ui.wrap(g, font, Component.translatable("screen.noirm.workshop.none"), leftX, y, leftW, bottom, Theme.TEXT_FAINT);
        }
        JutsuDesign chosen = null;
        UUID me = minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : null;
        for (JutsuDesign d : list) {
            String id = d.jutsuId().toString();
            if (id.equals(selectedCreation)) {
                chosen = d;
            }
            if (y + 14 > bottom) {
                Ui.text(g, font, Component.literal("…"), leftX, y, leftW, Theme.TEXT_FAINT);
                break;
            }
            PowerPackets.JutsuEntry entry = ClientPowers.entry(id);
            int mastery = entry == null ? 0 : entry.mastery();
            boolean over = Ui.in(mouseX, mouseY, leftX, y, leftW, 14);
            int color = 0xFF000000 | d.nature().color();
            Ui.row(g, leftX, y, leftW, 14, hover.get(HoverTracker.key(15, id.hashCode()), over), id.equals(selectedCreation), color);
            String rank = Workshop.rank(Workshop.weight(d));
            String stars = "★".repeat(mastery) + "☆".repeat(Math.max(0, 5 - mastery));
            int rightW = font.width(stars) + font.width(rank) + 10;
            String mark = me != null && me.equals(d.author()) ? "" : "✉ ";
            g.drawString(font, Ui.fit(font, mark + d.name(), leftW - rightW - 6), leftX + 4, y + 3, color, false);
            Ui.textRight(g, font, stars, leftX + leftW - 4 - font.width(rank) - 5, y + 3, Theme.darken(Theme.GOLD, 0.85f));
            Ui.textRight(g, font, rank, leftX + leftW - 4, y + 3, Theme.TEXT_DIM);
            hits.add(new Hit(leftX, y, leftW, 14, () -> {
                selectedCreation = id;
                confirmForget = false;
            }));
            y += 15;
        }
        Ui.vline(g, rightX - PAD / 2 - 1, bodyTop, bottom - bodyTop);
        if (chosen == null) {
            Ui.wrap(g, font, Component.translatable("screen.noirm.workshop.pick"), rightX, bodyTop, rightW, bottom, Theme.TEXT_DIM);
            return;
        }
        JutsuDesign d = chosen;
        Workshop.Stats s = Workshop.stats(d);
        PowerPackets.JutsuEntry entry = ClientPowers.entry(d.jutsuId().toString());
        int mastery = entry == null ? 0 : entry.mastery();
        int ry = Ui.section(g, font, rightX, bodyTop, rightW, Component.translatable("screen.noirm.workshop.details"), Theme.TEXT_DIM);
        int buttonsY = bottom - 15;
        ry = preview(g, d, s, ArtsScreens.limits(), rightX, ry, rightW, buttonsY - 2, false);
        ry = line(g, Component.translatable("screen.noirm.workshop.p.author", d.authorName()), rightX, ry, rightW, buttonsY - 2, Theme.TEXT_FAINT);
        ry = line(g, Component.translatable("screen.noirm.workshop.p.mastery", "★".repeat(mastery) + "☆".repeat(Math.max(0, 5 - mastery)),
                Math.round(Workshop.misfireChance(mastery) * 100)), rightX, ry, rightW, buttonsY - 2, Theme.GOLD);
        float budget = ArtsScreens.limits().budget().apply(d.nature());
        if (s.weight() > budget) {
            line(g, Component.translatable("screen.noirm.workshop.p.too_heavy", s.weight(), fmt(budget)), rightX, ry, rightW, buttonsY - 2, Theme.RED);
        }
        boolean mine = me != null && me.equals(d.author());
        int half = (rightW - 4) / 2;
        boolean canWrite = mine && mastery >= Workshop.WRITE_MASTERY;
        boolean overWrite = Ui.in(mouseX, mouseY, rightX, buttonsY, half, 15);
        Ui.button(g, font, rightX, buttonsY, half, 15, Component.translatable("screen.noirm.workshop.write"),
                hover.get(HoverTracker.key(16, 0), overWrite), canWrite, Theme.GOLD, false);
        if (overWrite) {
            setTooltipForNextRenderPass(!mine ? Component.translatable("screen.noirm.workshop.write_not_yours")
                    : Component.translatable("screen.noirm.workshop.write_tip", Workshop.WRITE_MASTERY,
                    Math.round(Math.max(Workshop.WRITE_CHAKRA_MIN, s.cost() * Workshop.WRITE_CHAKRA_SHARE))));
        }
        if (canWrite) {
            hits.add(new Hit(rightX, buttonsY, half, 15, () ->
                    NoirmNetwork.sendToServer(new WorkshopPackets.Act(WorkshopPackets.Action.WRITE_SCROLL, d.jutsuId().toString(), -1))));
        }
        int fx = rightX + half + 4;
        int fw = rightW - half - 4;
        boolean overForget = Ui.in(mouseX, mouseY, fx, buttonsY, fw, 15);
        Ui.button(g, font, fx, buttonsY, fw, 15, Component.translatable(confirmForget ? "screen.noirm.workshop.forget_sure" : "screen.noirm.workshop.forget"),
                hover.get(HoverTracker.key(16, 1), overForget), true, Theme.RED, confirmForget);
        hits.add(new Hit(fx, buttonsY, fw, 15, () -> {
            if (confirmForget) {
                NoirmNetwork.sendToServer(new WorkshopPackets.Act(WorkshopPackets.Action.DELETE, d.jutsuId().toString(), -1));
                selectedCreation = null;
                confirmForget = false;
            } else {
                confirmForget = true;
            }
        }));
    }

    // ================================================================ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (motion.closing()) {
            return true;
        }
        int t = tabs.at(mouseX, mouseY);
        if (button == 0 && t >= 0 && t != ArtsScreens.TAB_WORKSHOP) {
            click();
            ArtsScreens.switchTo(t);
            return true;
        }
        if (button == 0 && !creationsView) {
            for (int i = 0; i < 4; i++) {
                int[] b = sliderBoxes[i];
                if (b[2] > 0 && Ui.in(mouseX, mouseY, b[0] - 2, b[1], b[2] + 4, 11)) {
                    dragging = i;
                    setSlider(i, mouseX);
                    nameBox.setFocused(false);
                    return true;
                }
            }
        }
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit hit = hits.get(i);
                if (hit.contains(mouseX, mouseY)) {
                    hit.action().run();
                    click();
                    nameBox.setFocused(false);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging >= 0) {
            setSlider(dragging, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void setSlider(int i, double mouseX) {
        int[] b = sliderBoxes[i];
        float t = (float) ((mouseX - b[0]) / Math.max(1, b[2] - 1));
        DRAFT[i] = Math.max(JutsuDesign.MIN, Math.min(JutsuDesign.MAX, 1 + Math.round(Math.max(0f, Math.min(1f, t)) * 4)));
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (nameBox != null && nameBox.isFocused() && key != 256) {
            return nameBox.keyPressed(key, scan, modifiers) || nameBox.canConsumeInput();
        }
        return super.keyPressed(key, scan, modifiers);
    }

    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1f, 0.35f));
        }
    }

    /** For the smoke test and commands: set the draft directly. */
    public static void draft(Nature nature, ChakraShape shape, int size, int speed, int power, int range, String name) {
        draftNature = nature.ordinal();
        draftShape = shape;
        DRAFT[0] = size;
        DRAFT[1] = speed;
        DRAFT[2] = power;
        DRAFT[3] = range;
        draftName = name;
        creationsView = false;
    }
}
