package com.noirm.client;

import com.noirm.client.ui.HoverTracker;
import com.noirm.client.ui.NoirmStyled;
import com.noirm.client.ui.ScreenMotion;
import com.noirm.client.ui.Theme;
import com.noirm.client.ui.Ui;
import com.noirm.guide.Handbook;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The Shinobi Handbook: chapters down the left (the open one marked in seal red), the page on the
 * right: its title, a strip of items as the chapter's picture, then the text (scroll with the
 * wheel). The controls chapter also lists your <b>real key bindings</b>, so it stays right if you
 * change them. Drawn with the NOIRM kit ({@code client.ui}); turning a chapter cross-fades the page.
 * Chapters come from {@link Handbook#CHAPTERS}; the text from {@code handbook.noirm.<chapter>(.body)}.
 */
public class HandbookScreen extends Screen implements NoirmStyled {
    private static final int MAX_W = 372;
    private static final int MAX_H = 226;
    private static final int PAD = Theme.S5;
    private static final int LIST_W = 104;
    private static final int ROW_H = 16;

    /** A control in the controls chapter: the key (as bound now) and what it does. */
    private record Control(Supplier<KeyMapping> key, String what) {}

    private static final List<Control> CONTROLS = List.of(
            new Control(() -> CombatKeys.COMBAT_MODE, "combat_mode"),
            new Control(() -> Minecraft.getInstance().options.keyAttack, "light"),
            new Control(() -> Minecraft.getInstance().options.keyUse, "heavy"),
            new Control(() -> CombatKeys.GUARD, "guard"),
            new Control(() -> CombatKeys.DODGE, "dodge"),
            new Control(() -> CombatKeys.LOCK_ON, "lock_on"),
            new Control(() -> CombatKeys.GRAB, "grab"),
            new Control(() -> CombatKeys.STYLE, "style"),
            new Control(() -> Minecraft.getInstance().options.keyHotbarSlots[0], "jutsu"),
            new Control(() -> PowerKeys.JUTSU_SCREEN, "jutsu_screen"),
            new Control(() -> PowerKeys.MEDITATE, "meditate"),
            new Control(() -> CrewOrdersScreen.KEY, "crew"),
            new Control(() -> PowerKeys.SHARINGAN, "eyes"),
            new Control(() -> PowerKeys.CLOAK, "cloak"));

    /** The chapter you last read (kept while the game runs). */
    private static int lastChapter;

    private int chapter;
    private int scroll;
    private int left;
    private int top;
    private int w;
    private int h;
    private List<FormattedCharSequence> lines = List.of();
    private final ScreenMotion motion = new ScreenMotion();
    private final HoverTracker hover = new HoverTracker();
    private String tipText;

    public HandbookScreen() {
        super(Component.translatable("screen.noirm.handbook"));
        this.chapter = Mth.clamp(lastChapter, 0, Handbook.CHAPTERS.size() - 1);
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new HandbookScreen());
    }

    /** Opens straight at a chapter (by id). */
    public static void open(String chapterId) {
        HandbookScreen screen = new HandbookScreen();
        for (int i = 0; i < Handbook.CHAPTERS.size(); i++) {
            if (Handbook.CHAPTERS.get(i).id().equals(chapterId)) {
                screen.chapter = i;
            }
        }
        Minecraft.getInstance().setScreen(screen);
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
        layout();
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

    private Handbook.Chapter current() {
        return Handbook.CHAPTERS.get(chapter);
    }

    private int listX() {
        return left + PAD;
    }

    private int contentTop() {
        return top + PAD + Theme.ROW + Theme.S3;
    }

    private int pageX() {
        return listX() + LIST_W + Theme.S5 + 1;
    }

    private int pageW() {
        return left + w - PAD - pageX();
    }

    /** Wraps the chapter's text (paragraphs split by blank lines) to the page width. */
    private void layout() {
        List<FormattedCharSequence> out = new ArrayList<>();
        String body = Component.translatable(current().bodyKey()).getString();
        String[] paragraphs = body.split("\n");
        for (String paragraph : paragraphs) {
            if (paragraph.isBlank()) {
                out.add(FormattedCharSequence.EMPTY);
                continue;
            }
            out.addAll(font.split(Component.literal(paragraph), pageW() - 8));
        }
        lines = out;
        scroll = 0;
    }

    private void select(int index) {
        int next = Math.floorMod(index, Handbook.CHAPTERS.size());
        if (next != chapter) {
            motion.tabChanged();
        }
        chapter = next;
        lastChapter = chapter;
        layout();
    }

    // ================================================================ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        tipText = null;
        motion.scrim(g, width, height);
        motion.begin(g);
        Ui.panel(g, left, top, w, h);
        // The cover line: a blood-red seal with 忍, the book's name and the page number.
        String pageNo = (chapter + 1) + " / " + Handbook.CHAPTERS.size();
        Ui.header(g, font, left + PAD, top + PAD, w - PAD * 2, "忍", Theme.SEAL, title, Component.literal(pageNo), Theme.TEXT_FAINT);
        int bottom = top + h - Theme.S4;
        drawChapters(g, listX(), contentTop(), bottom, mouseX, mouseY);
        Ui.vline(g, listX() + LIST_W + Theme.S3, contentTop(), bottom - contentTop());
        motion.beginContent(g);
        drawPage(g, pageX(), contentTop(), pageW(), bottom);
        motion.endContent(g);
        motion.end(g);
        super.render(g, mouseX, mouseY, partialTick);
        if (tipText != null && !motion.closing()) {
            g.renderTooltip(font, font.split(Component.literal(tipText), 200), mouseX, mouseY);
        }
    }

    private void drawChapters(GuiGraphics g, int x, int y, int bottom, int mouseX, int mouseY) {
        for (int i = 0; i < Handbook.CHAPTERS.size(); i++) {
            int ry = y + i * ROW_H;
            if (ry + ROW_H - 1 > bottom) {
                break;
            }
            boolean open = i == chapter;
            boolean over = !motion.closing() && Ui.in(mouseX, mouseY, x, ry, LIST_W, ROW_H - 1);
            float hv = hover.get(HoverTracker.key(1, i), over);
            Ui.row(g, x, ry, LIST_W, ROW_H - 1, hv, open, Theme.SEAL);
            String full = Component.translatable(Handbook.CHAPTERS.get(i).titleKey()).getString();
            String name = Ui.fit(font, full, LIST_W - 12);
            g.drawString(font, name, x + Theme.S3, ry + 4, open ? Theme.TEXT : Theme.mix(Theme.TEXT_DIM, Theme.TEXT, hv * 0.6f), false);
            if (over && !name.equals(full)) {
                tipText = full;
            }
        }
    }

    private void drawPage(GuiGraphics g, int x, int y, int w, int bottom) {
        Handbook.Chapter c = current();
        Ui.text(g, font, Component.translatable(c.titleKey()), x, y, w, Theme.GOLD);
        // The picture: the chapter's items, twice their size, in a sunken strip.
        int stripY = y + 13;
        int stripH = 38;
        Ui.well(g, x, stripY, w, stripH);
        List<ItemStack> icons = new ArrayList<>();
        for (var id : c.icons()) {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                icons.add(new ItemStack(item));
            }
        }
        int step = icons.isEmpty() ? 0 : Math.min(48, (w - 16) / icons.size());
        int startX = x + (w - step * icons.size()) / 2;
        for (int i = 0; i < icons.size(); i++) {
            int ix = startX + i * step + (step - 32) / 2;
            g.pose().pushPose();
            g.pose().translate(ix, stripY + 3, 0);
            g.pose().scale(2f, 2f, 1f);
            g.renderItem(icons.get(i), 0, 0);
            g.pose().popPose();
        }
        int textTop = stripY + stripH + Theme.S3;
        int lineH = Theme.LINE;
        List<Object> rows = new ArrayList<>(lines);
        if (c.id().equals("controls")) {
            rows.add(FormattedCharSequence.EMPTY);
            rows.addAll(CONTROLS);
        }
        int visible = Math.max(1, (bottom - textTop) / lineH);
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - visible));
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            int ly = textTop + i * lineH;
            Object row = rows.get(scroll + i);
            if (row instanceof FormattedCharSequence line) {
                g.drawString(font, line, x + 2, ly, Theme.mix(Theme.TEXT_DIM, Theme.TEXT, 0.8f), false);
            } else if (row instanceof Control control) {
                drawControl(g, control, x + 2, ly, w - 10);
            }
        }
        Ui.scrollbar(g, x + w - 1, textTop, visible * lineH, scroll, visible, rows.size(), Theme.IRON);
    }

    private void drawControl(GuiGraphics g, Control control, int x, int y, int w) {
        KeyMapping key = control.key().get();
        String cap = key == null ? "?" : Ui.fit(font, key.getTranslatedKeyMessage().getString(), 66);
        int capW = Math.max(14, font.width(cap) + 8);
        Ui.roundRect(g, x, y - 1, capW, 10, Theme.mix(Theme.RAISED_HOVER, Theme.GOLD, 0.10f));
        Ui.textCentered(g, font, cap, x + capW / 2, y, Theme.GOLD);
        String what = Ui.fit(font, Component.translatable("handbook.noirm.key." + control.what()).getString(), w - 80);
        g.drawString(font, what, x + 78, y, Theme.TEXT, false);
    }

    // ================================================================ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (motion.closing()) {
            return true;
        }
        if (button == 0) {
            int x = listX();
            int y = contentTop();
            if (mouseX >= x && mouseX < x + LIST_W && mouseY >= y) {
                int i = (int) ((mouseY - y) / ROW_H);
                if (i >= 0 && i < Handbook.CHAPTERS.size()) {
                    if (i != chapter) {
                        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1f));
                    }
                    select(i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll -= (int) Math.signum(delta) * 2;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN) {
            select(chapter + 1);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP) {
            select(chapter - 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
