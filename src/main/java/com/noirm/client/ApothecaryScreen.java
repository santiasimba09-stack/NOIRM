package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.client.ui.HoverTracker;
import com.noirm.client.ui.NoirmStyled;
import com.noirm.client.ui.ScreenMotion;
import com.noirm.client.ui.Theme;
import com.noirm.client.ui.Ui;
import com.noirm.pills.Apothecary;
import com.noirm.pills.ApothecaryMenu;
import com.noirm.pills.Grade;
import com.noirm.pills.PillClientState;
import com.noirm.pills.PillItems;
import com.noirm.pills.PillPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * The apothecary's screen, on the UI kit: your recipes on the left (click one to fill the mortar
 * from your bag), the mortar, what comes out and the Grind button on the right, with the odds of
 * success and of each grade for the recipe in the mortar, and your pharmacology. The rows come from
 * the server ({@link PillPackets.Station}); the buttons are menu button clicks.
 */
public class ApothecaryScreen extends AbstractContainerScreen<ApothecaryMenu> implements NoirmStyled {
    private static final int W = 338, H = 202;
    private static final int LIST_X = 10, LIST_W = 144, LIST_Y = 50, ROW_H = 16;
    private static final int RIGHT_X = ApothecaryMenu.INPUT_X;
    private static final int RIGHT_W = 162;
    private static final int BUTTON_Y = 62, BUTTON_W = 62, BUTTON_H = 14;
    private static final long GRIND_MS = 450;

    private final ScreenMotion motion = new ScreenMotion();
    private final HoverTracker hover = new HoverTracker();
    private final List<Tip> tips = new ArrayList<>();
    private int scroll;
    private long grindAt = Long.MIN_VALUE / 2;

    private record Tip(int x, int y, int w, int h, List<Component> lines) {}

    public ApothecaryScreen(ApothecaryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = W;
        imageHeight = H;
    }

    @Override
    protected void init() {
        super.init();
        if (!motion.started()) {
            motion.open();
        }
    }

    @Override
    protected void containerTick() {
        motion.tick();
    }

    @Override
    public void onClose() {
        motion.close(super::onClose);
    }

    private int visibleRows() {
        return Math.max(1, (H - 10 - LIST_Y) / ROW_H);
    }

    /** The row the right side describes: the recipe in the mortar, else the one picked. */
    private PillPackets.Row focus(PillPackets.Station st) {
        if (st == null) {
            return null;
        }
        int i = st.matched() >= 0 ? st.matched() : st.selected();
        return i >= 0 && i < st.rows().size() ? st.rows().get(i) : null;
    }

    private static ItemStack stack(ResourceLocation id, int count) {
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    // ================================================================ drawing

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        tips.clear();
        int x = leftPos, y = topPos;
        Ui.panel(g, x, y, W, H);
        PillPackets.Sync sync = PillClientState.get();
        Component note = sync == null ? null : Component.translatable("screen.noirm.apothecary.brewed", sync.brewed());
        Ui.header(g, font, x + 8, y + 8, W - 16, "薬", Theme.SEAL, title, note, Theme.TEXT_DIM);
        Ui.hairline(g, x + 8, y + 25, W - 16);
        motion.beginContent(g);
        try {
            drawSkill(g, x + LIST_X, y + 30, sync);
            drawList(g, x, y, mouseX, mouseY);
            drawMortar(g, x, y, mouseX, mouseY);
        } finally {
            motion.endContent(g);
        }
    }

    private void drawSkill(GuiGraphics g, int x, int y, PillPackets.Sync sync) {
        float level = sync == null ? 1f : sync.pharmacology();
        float progress = sync == null ? 0f : sync.progress();
        Component label = Component.translatable("screen.noirm.apothecary.skill", Math.round(level));
        g.drawString(font, label, x, y, Theme.GOLD, false);
        Ui.bar(g, x, y + 11, LIST_W, 2, progress, Theme.GOLD);
    }

    private void drawList(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        PillPackets.Station st = PillClientState.station();
        if (st == null) {
            Ui.textCentered(g, font, Component.translatable("screen.noirm.apothecary.loading").getString(), x + LIST_X + LIST_W / 2,
                    y + LIST_Y + 10, Theme.TEXT_DIM);
            return;
        }
        int rows = visibleRows();
        scroll = Math.max(0, Math.min(scroll, st.rows().size() - rows));
        for (int i = 0; i < rows && i + scroll < st.rows().size(); i++) {
            int index = i + scroll;
            PillPackets.Row row = st.rows().get(index);
            int rx = x + LIST_X, ry = y + LIST_Y + i * ROW_H;
            boolean over = Ui.in(mouseX, mouseY, rx, ry, LIST_W, ROW_H - 1);
            Apothecary.State state = row.stateEnum();
            int accent = state == Apothecary.State.READY ? (index == st.matched() ? Theme.GOLD : Theme.SEAL) : Theme.IRON;
            Ui.row(g, rx, ry, LIST_W, ROW_H - 1, hover.get(HoverTracker.key(7, index), over), index == st.selected() || index == st.matched(), accent);
            ItemStack icon = stack(row.item(), 1);
            g.renderItem(icon, rx + 2, ry - 1);
            String chip;
            int chipColor;
            switch (state) {
                case READY -> {
                    chip = Math.round(row.success() * 100) + "%";
                    chipColor = row.success() >= 0.75f ? Theme.GREEN : Theme.GOLD;
                }
                case LOW_SKILL -> {
                    chip = Component.translatable("screen.noirm.apothecary.level", Math.round(row.minPharmacology())).getString();
                    chipColor = Theme.RED;
                }
                default -> {
                    chip = "?";
                    chipColor = Theme.TEXT_FAINT;
                }
            }
            int cw = font.width(chip) + 6;
            int nameW = LIST_W - 22 - cw - 4;
            Component name = icon.getHoverName();
            Ui.text(g, font, name, rx + 21, ry + 4, nameW, state == Apothecary.State.READY ? Theme.TEXT : Theme.TEXT_DIM);
            Ui.chip(g, font, rx + LIST_W - cw - 2, ry + 2, chip, chipColor);
            List<Component> lines = new ArrayList<>();
            lines.add(name.copy());
            lines.add(Component.translatable(icon.getDescriptionId() + ".tip"));
            lines.add(needs(row));
            if (!row.hint().isEmpty()) {
                lines.add(Component.translatable(row.hint(), Math.round(row.minPharmacology())).withStyle(s -> s.withColor(Theme.RED)));
            } else {
                lines.add(Component.translatable("screen.noirm.apothecary.click_fill").withStyle(s -> s.withColor(Theme.TEXT_DIM)));
            }
            tips.add(new Tip(rx, ry, LIST_W, ROW_H - 1, lines));
        }
        Ui.scrollbar(g, x + LIST_X + LIST_W + 2, y + LIST_Y, rows * ROW_H, scroll, rows, st.rows().size(), Theme.IRON);
    }

    private Component needs(PillPackets.Row row) {
        var text = Component.translatable("screen.noirm.apothecary.needs").copy();
        boolean first = true;
        for (PillPackets.Need n : row.needs()) {
            text.append(first ? " " : ", ").append(n.count() + "× ").append(stack(n.item(), 1).getHoverName());
            first = false;
        }
        return text;
    }

    private void drawMortar(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        // The bag: three rows and the hotbar, each slot a sunken well.
        for (int row = 0; row < 4; row++) {
            int sy = y + ApothecaryMenu.INVENTORY_Y + (row < 3 ? row * 18 : 58);
            for (int col = 0; col < 9; col++) {
                Ui.well(g, x + ApothecaryMenu.INVENTORY_X + col * 18, sy, 16, 16);
            }
        }
        Ui.section(g, font, x + RIGHT_X, y + 29, RIGHT_W, Component.translatable("screen.noirm.apothecary.mortar"), Theme.TEXT_DIM);
        for (int i = 0; i < ApothecaryMenu.INPUTS; i++) {
            Ui.well(g, x + ApothecaryMenu.INPUT_X + i * ApothecaryMenu.INPUT_STEP - 1, y + ApothecaryMenu.INPUT_Y - 1, 18, 18);
        }
        PillPackets.Station st = PillClientState.station();
        PillPackets.Row focus = focus(st);
        boolean ready = st != null && st.matched() >= 0 && focus != null && focus.stateEnum() == Apothecary.State.READY;
        // The arrow fills while grinding.
        float grind = Math.min(1f, (com.noirm.client.ui.Anim.now() - grindAt) / (float) GRIND_MS);
        int ax = x + ApothecaryMenu.INPUT_X + ApothecaryMenu.INPUTS * ApothecaryMenu.INPUT_STEP + 2;
        int aw = ApothecaryMenu.OUTPUT_X - 6 - (ApothecaryMenu.INPUT_X + ApothecaryMenu.INPUTS * ApothecaryMenu.INPUT_STEP + 2);
        int ay = y + ApothecaryMenu.INPUT_Y + 7;
        g.fill(ax, ay, ax + aw, ay + 2, Theme.WELL);
        if (grind < 1f) {
            g.fill(ax, ay, ax + Math.round(aw * grind), ay + 2, Theme.GOLD);
        } else if (ready) {
            g.fill(ax, ay, ax + aw, ay + 2, Theme.darken(Theme.GOLD, 0.5f));
        }
        g.drawString(font, "›", ax + aw - 2, ay - 3, ready ? Theme.GOLD : Theme.TEXT_FAINT, false);
        int ox = x + ApothecaryMenu.OUTPUT_X - 1, oy = y + ApothecaryMenu.OUTPUT_Y - 1;
        Ui.well(g, ox - 2, oy - 2, 22, 22);
        if (ready) {
            Ui.outline(g, ox - 2, oy - 2, 22, 22, Theme.fade(focus.color() | 0xFF000000, 0.8f));
        }
        // Grind.
        int bx = x + RIGHT_X, by = y + BUTTON_Y;
        boolean overButton = Ui.in(mouseX, mouseY, bx, by, BUTTON_W, BUTTON_H);
        Ui.button(g, font, bx, by, BUTTON_W, BUTTON_H, Component.translatable("screen.noirm.apothecary.grind"),
                hover.get(HoverTracker.key(9, 0), overButton && ready), ready, Theme.SEAL, true);
        if (!ready) {
            tips.add(new Tip(bx, by, BUTTON_W, BUTTON_H, List.of(Component.translatable(st == null || st.matched() < 0
                    ? "screen.noirm.apothecary.why.empty" : "screen.noirm.apothecary.why.cant"))));
        }
        if (focus == null) {
            Ui.wrap(g, font, Component.translatable("screen.noirm.apothecary.hint"), bx + BUTTON_W + 6, by + 1,
                    RIGHT_W - BUTTON_W - 6, y + 112, Theme.TEXT_FAINT);
            return;
        }
        // The odds for the recipe in focus.
        int ix = bx + BUTTON_W + 6, iw = RIGHT_W - BUTTON_W - 6;
        Component odds = Component.translatable("screen.noirm.apothecary.odds", Math.round(focus.success() * 100), focus.yield());
        Ui.text(g, font, odds, ix, by + 3, iw, focus.stateEnum() == Apothecary.State.READY ? Theme.TEXT : Theme.TEXT_DIM);
        int gy = by + BUTTON_H + 6;
        g.drawString(font, Component.translatable("screen.noirm.apothecary.grades"), bx, gy, Theme.TEXT_DIM, false);
        int barY = gy + 11, barX = bx;
        List<Component> gradeLines = new ArrayList<>();
        gradeLines.add(Component.translatable("screen.noirm.apothecary.grades"));
        for (Grade grade : Grade.values()) {
            float share = focus.odds()[grade.ordinal()];
            int w = Math.round(RIGHT_W * share);
            if (w > 0) {
                g.fill(barX, barY, barX + w, barY + 4, grade.color);
                barX += w;
            }
            gradeLines.add(Component.translatable(grade.translationKey()).append(": " + Math.round(share * 100) + "%")
                    .withStyle(s -> s.withColor(grade.color)));
        }
        if (barX < bx + RIGHT_W) {
            g.fill(barX, barY, bx + RIGHT_W, barY + 4, Theme.WELL);
        }
        tips.add(new Tip(bx, gy, RIGHT_W, 16, gradeLines));
        // The ingredients, as icons with counts.
        int ny = barY + 9, nx = bx;
        for (PillPackets.Need n : focus.needs()) {
            ItemStack s = stack(n.item(), n.count());
            g.renderItem(s, nx, ny);
            g.renderItemDecorations(font, s, nx, ny);
            tips.add(new Tip(nx, ny, 16, 16, List.of(s.getHoverName())));
            nx += 20;
        }
        Component name = stack(focus.item(), 1).getHoverName();
        Ui.text(g, font, name, nx + 4, ny + 4, bx + RIGHT_W - nx - 4, focus.color() | 0xFF000000);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Everything is drawn in renderBg.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        motion.scrim(g, width, height);
        motion.begin(g);
        try {
            super.render(g, mouseX, mouseY, partialTick);
        } finally {
            motion.end(g);
        }
        if (menu.getCarried().isEmpty() && !motion.closing() && hoveredSlot == null) {
            for (Tip tip : tips) {
                if (Ui.in(mouseX, mouseY, tip.x(), tip.y(), tip.w(), tip.h())) {
                    List<FormattedCharSequence> lines = new ArrayList<>();
                    for (Component line : tip.lines()) {
                        lines.addAll(font.split(line, 220));
                    }
                    g.renderTooltip(font, lines, mouseX, mouseY);
                    break;
                }
            }
        }
        renderTooltip(g, mouseX, mouseY);
    }

    // ================================================================ input

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (motion.closing()) {
            return true;
        }
        PillPackets.Station st = PillClientState.station();
        if (button == 0 && st != null) {
            int rows = visibleRows();
            for (int i = 0; i < rows && i + scroll < st.rows().size(); i++) {
                if (Ui.in(mouseX, mouseY, leftPos + LIST_X, topPos + LIST_Y + i * ROW_H, LIST_W, ROW_H - 1)) {
                    press(ApothecaryMenu.SELECT + i + scroll);
                    com.noirm.client.ui.UiSounds.click();
                    return true;
                }
            }
            if (Ui.in(mouseX, mouseY, leftPos + RIGHT_X, topPos + BUTTON_Y, BUTTON_W, BUTTON_H) && st.matched() >= 0) {
                press(ApothecaryMenu.BREW);
                grindAt = com.noirm.client.ui.Anim.now();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (Ui.in(mouseX, mouseY, leftPos + LIST_X, topPos + LIST_Y, LIST_W, visibleRows() * ROW_H)) {
            scroll -= (int) Math.signum(delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (motion.closing()) {
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    /** Registers the screen for the apothecary's menu. */
    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ApothecaryScreenRegistration {
        private ApothecaryScreenRegistration() {}

        @SubscribeEvent
        public static void apothecaryClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> MenuScreens.register(PillItems.APOTHECARY_MENU.get(), ApothecaryScreen::new));
        }
    }
}
