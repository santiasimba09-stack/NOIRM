package com.noirm.client;

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
 * The Shinobi Handbook: chapters down the left (an ink seal marks the open one), the page on the
 * right: its title, a strip of items as the chapter's picture, then the text (scroll with the
 * wheel). The controls chapter also lists your <b>real key bindings</b>, so it stays right if you
 * change them. Same ink look as the master and ally screens. Chapters come from
 * {@link Handbook#CHAPTERS}; the text from {@code handbook.noirm.<chapter>(.body)}.
 */
public class HandbookScreen extends Screen {
    private static final int W = 372;
    private static final int H = 226;
    private static final int LIST_W = 104;
    private static final int ROW_H = 17;
    private static final int INK = 0xF4100E14;
    private static final int INK_LIGHT = 0xF41A1720;
    private static final int CARD = 0xFF17141C;
    private static final int CARD_HOVER = 0xFF221E29;
    private static final int FRAME = 0xFF3D3645;
    private static final int RULE = 0xFF2E2935;
    private static final int PAPER = 0xFFEDE4D3;
    private static final int DIM = 0xFF8F8796;
    private static final int GOLD = 0xFFE8C060;
    private static final int BLOOD = 0xFFA0201C;
    private static final int KEYCAP = 0xFF2A2630;

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
    private List<FormattedCharSequence> lines = List.of();

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
        left = (width - W) / 2;
        top = (height - H) / 2;
        layout();
    }

    private Handbook.Chapter current() {
        return Handbook.CHAPTERS.get(chapter);
    }

    private int pageX() {
        return left + LIST_W + 14;
    }

    private int pageW() {
        return W - LIST_W - 24;
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
        chapter = Math.floorMod(index, Handbook.CHAPTERS.size());
        lastChapter = chapter;
        layout();
    }

    // ================================================================ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left;
        int y = top;
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, 0xC0000000);
        g.fillGradient(x, y, x + W, y + H, INK_LIGHT, INK);
        for (int ly = y + 26; ly < y + H - 4; ly += 6) {
            g.fill(x + 3, ly, x + W - 3, ly + 1, 0x06FFFFFF);
        }
        g.renderOutline(x, y, W, H, FRAME);
        g.renderOutline(x + 2, y + 2, W - 4, H - 4, 0xFF221E28);
        // The cover line: a blood-red seal with 忍 and the book's name.
        g.fill(x + 8, y + 6, x + 22, y + 20, BLOOD);
        g.renderOutline(x + 8, y + 6, 14, 14, 0xFF5A1010);
        g.drawCenteredString(font, "忍", x + 15, y + 9, 0xFFF4E6D0);
        g.drawString(font, title, x + 28, y + 10, GOLD, false);
        String pageNo = (chapter + 1) + " / " + Handbook.CHAPTERS.size();
        g.drawString(font, pageNo, x + W - 10 - font.width(pageNo), y + 10, DIM, false);
        g.fill(x + 6, y + 24, x + W - 6, y + 25, RULE);
        drawChapters(g, x + 6, y + 30, mouseX, mouseY);
        g.fill(x + LIST_W + 8, y + 30, x + LIST_W + 9, y + H - 8, RULE);
        drawPage(g, pageX(), y + 30, pageW(), y + H - 8);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawChapters(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        for (int i = 0; i < Handbook.CHAPTERS.size(); i++) {
            int ry = y + i * ROW_H;
            boolean open = i == chapter;
            boolean hover = mouseX >= x && mouseX < x + LIST_W && mouseY >= ry && mouseY < ry + ROW_H - 1;
            g.fill(x, ry, x + LIST_W, ry + ROW_H - 1, open ? 0xFF2A2018 : hover ? CARD_HOVER : CARD);
            if (open) {
                g.fill(x, ry, x + 2, ry + ROW_H - 1, BLOOD);
            }
            String name = fit(Component.translatable(Handbook.CHAPTERS.get(i).titleKey()).getString(), LIST_W - 10);
            g.drawString(font, name, x + 6, ry + 4, open ? GOLD : hover ? PAPER : DIM, false);
        }
    }

    private void drawPage(GuiGraphics g, int x, int y, int w, int bottom) {
        Handbook.Chapter c = current();
        g.drawString(font, Component.translatable(c.titleKey()), x, y, GOLD, false);
        // The picture: the chapter's items, twice their size, on an ink strip.
        int stripY = y + 13;
        int stripH = 38;
        g.fill(x, stripY, x + w, stripY + stripH, CARD);
        g.renderOutline(x, stripY, w, stripH, RULE);
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
        // Brush strokes either side of the strip.
        g.fill(x + 4, stripY + stripH / 2, x + 14, stripY + stripH / 2 + 1, BLOOD);
        g.fill(x + w - 14, stripY + stripH / 2, x + w - 4, stripY + stripH / 2 + 1, BLOOD);
        int textTop = stripY + stripH + 6;
        int lineH = 10;
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
                g.drawString(font, line, x + 2, ly, PAPER, false);
            } else if (row instanceof Control control) {
                drawControl(g, control, x + 2, ly, w - 10);
            }
        }
        if (rows.size() > visible) {
            int trackH = visible * lineH;
            int barH = Math.max(10, trackH * visible / rows.size());
            int barY = textTop + (trackH - barH) * scroll / Math.max(1, rows.size() - visible);
            g.fill(x + w - 3, textTop, x + w - 1, textTop + trackH, RULE);
            g.fill(x + w - 3, barY, x + w - 1, barY + barH, DIM);
        }
    }

    private void drawControl(GuiGraphics g, Control control, int x, int y, int w) {
        KeyMapping key = control.key().get();
        String cap = key == null ? "?" : fit(key.getTranslatedKeyMessage().getString(), 70);
        int capW = Math.max(14, font.width(cap) + 8);
        g.fill(x, y - 1, x + capW, y + 9, KEYCAP);
        g.renderOutline(x, y - 1, capW, 10, FRAME);
        g.drawCenteredString(font, cap, x + capW / 2, y, GOLD);
        String what = fit(Component.translatable("handbook.noirm.key." + control.what()).getString(), w - 80);
        g.drawString(font, what, x + 78, y, PAPER, false);
    }

    // ================================================================ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int x = left + 6;
            int y = top + 30;
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

    private String fit(String text, int width) {
        if (width <= 0) {
            return "";
        }
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }
}
