package com.noirm.client;

import com.noirm.bounty.BountyPackets;
import com.noirm.bounty.NinjaClass;
import com.noirm.economy.Ryo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * The bingo book, in our dark ink style: on the left the pages (class, name, bounty; claimed ones
 * crossed out), on the right the open page: a big class seal, the name and clan, what they're known
 * for, where they were last seen and the bounty, or who claimed it. Legends get a gold page and
 * their own seal.
 */
public class BingoBookScreen extends Screen {
    private static final int W = 380;
    private static final int H = 236;
    private static final int ROW_H = 24;
    private static final int LIST_W = 150;
    private static final int INK = 0xF4100E14;
    private static final int INK_LIGHT = 0xF41A1720;
    private static final int CARD = 0xFF17141C;
    private static final int CARD_HOVER = 0xFF201C27;
    private static final int CARD_SELECTED = 0xFF2A2230;
    private static final int FRAME = 0xFF3D3645;
    private static final int RULE = 0xFF2E2935;
    private static final int PAPER = 0xFFEDE4D3;
    private static final int DIM = 0xFF8F8796;
    private static final int FAINT = 0xFF5E5866;
    private static final int GOLD = 0xFFE8C060;
    private static final int LEGEND = 0xFFF2C94C;
    private static final int BLOOD = 0xFF8A2A22;

    private BountyPackets.BookInfo info;
    private int selected;
    private int scroll;
    private int left;
    private int top;

    public BingoBookScreen(BountyPackets.BookInfo info) {
        super(Component.translatable("screen.noirm.bingo.title"));
        this.info = info;
    }

    public static void receive(BountyPackets.BookInfo info) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BingoBookScreen screen) {
            screen.info = info;
        } else {
            mc.setScreen(new BingoBookScreen(info));
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

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left, y = top;
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, 0xC0000000);
        g.fillGradient(x, y, x + W, y + H, INK_LIGHT, INK);
        g.renderOutline(x, y, W, H, FRAME);
        // Header: a blood seal with 賞 (bounty), the title, then your own class.
        g.fill(x + 8, y + 6, x + 22, y + 20, BLOOD);
        g.renderOutline(x + 8, y + 6, 14, 14, 0xFF5A1612);
        g.drawCenteredString(font, "賞", x + 15, y + 9, 0xFFF4E6D0);
        g.drawString(font, title, x + 28, y + 10, GOLD, false);
        Component yours = Component.translatable("screen.noirm.bingo.your_class", NinjaClass.tag(info.readerCode()));
        g.drawString(font, yours, x + W - 8 - font.width(yours), y + 10, PAPER, false);
        g.fill(x + 6, y + 24, x + W - 6, y + 25, RULE);
        List<BountyPackets.Page> pages = info.pages();
        if (pages.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.noirm.bingo.empty"), x + W / 2, y + H / 2, DIM);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        selected = Mth.clamp(selected, 0, pages.size() - 1);
        drawList(g, pages, x + 8, y + 30, LIST_W, y + H - 8, mouseX, mouseY);
        // The spine between the pages.
        g.fill(x + 8 + LIST_W + 5, y + 30, x + 8 + LIST_W + 6, y + H - 8, RULE);
        drawPage(g, pages.get(selected), x + 8 + LIST_W + 12, y + 30, W - LIST_W - 28, H - 38);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawList(GuiGraphics g, List<BountyPackets.Page> pages, int x, int y, int w, int bottom, int mouseX, int mouseY) {
        int visible = Math.max(1, (bottom - y) / ROW_H);
        scroll = Mth.clamp(scroll, 0, Math.max(0, pages.size() - visible));
        for (int i = 0; i < visible && scroll + i < pages.size(); i++) {
            int index = scroll + i;
            BountyPackets.Page p = pages.get(index);
            int ry = y + i * ROW_H;
            boolean hover = mouseX >= x && mouseX < x + w - 4 && mouseY >= ry && mouseY < ry + ROW_H - 2;
            int bg = index == selected ? CARD_SELECTED : hover ? CARD_HOVER : CARD;
            if (p.legendary() && !p.claimed()) {
                g.fillGradient(x, ry, x + w - 4, ry + ROW_H - 2, 0xFF3A2E14, bg);
            } else {
                g.fill(x, ry, x + w - 4, ry + ROW_H - 2, bg);
            }
            g.renderOutline(x, ry, w - 4, ROW_H - 2, index == selected ? (p.legendary() ? LEGEND : 0xFF6A5E72) : RULE);
            int color = NinjaClass.color(p.code());
            String letter = NinjaClass.letter(p.code());
            g.fill(x + 3, ry + 3, x + 20, ry + ROW_H - 5, (color & 0x00FFFFFF) | 0x40000000);
            g.drawCenteredString(font, letter, x + 12, ry + 7, p.claimed() ? FAINT : color);
            Component name = p.claimed() ? p.name().copy().withStyle(s -> s.withStrikethrough(true)) : p.name();
            g.drawString(font, fit(name, w - 30), x + 24, ry + 3, p.claimed() ? FAINT : p.legendary() ? LEGEND : PAPER, false);
            Component sub = p.claimed() ? Component.translatable("screen.noirm.bingo.claimed_short", p.claimedBy())
                    : Component.translatable("screen.noirm.bingo.bounty_short", Ryo.format(p.bounty()));
            g.drawString(font, fit(sub, w - 30), x + 24, ry + 12, p.claimed() ? 0xFF7A3A34 : DIM, false);
        }
        if (pages.size() > visible) {
            int trackH = visible * ROW_H - 2;
            int barH = Math.max(10, trackH * visible / pages.size());
            int barY = y + (trackH - barH) * scroll / Math.max(1, pages.size() - visible);
            g.fill(x + w - 3, y, x + w - 1, y + trackH, RULE);
            g.fill(x + w - 3, barY, x + w - 1, barY + barH, DIM);
        }
    }

    private void drawPage(GuiGraphics g, BountyPackets.Page p, int x, int y, int w, int h) {
        boolean legend = p.legendary();
        // The paper: dark ink, or a gold page for a legend.
        if (legend) {
            g.fillGradient(x, y, x + w, y + h, 0xFF3B2F12, 0xFF15110A);
            g.renderOutline(x, y, w, h, LEGEND);
            g.renderOutline(x + 2, y + 2, w - 4, h - 4, 0xFF6E5520);
        } else {
            g.fill(x, y, x + w, y + h, CARD);
            g.renderOutline(x, y, w, h, RULE);
        }
        int color = NinjaClass.color(p.code());
        // The class seal, top right: a big letter in a square, or the legend's 伝 seal.
        int sx = x + w - 40, sy = y + 8;
        g.fill(sx, sy, sx + 32, sy + 32, legend ? 0xFF8A6A1E : (color & 0x00FFFFFF) | 0x50000000);
        g.renderOutline(sx, sy, 32, 32, legend ? LEGEND : color);
        g.pose().pushPose();
        g.pose().translate(sx + 16, sy + 9, 0);
        g.pose().scale(2f, 2f, 1f);
        String big = legend ? "伝" : NinjaClass.letter(p.code());
        g.drawCenteredString(font, big, 0, 0, legend ? 0xFFFFF0C0 : color);
        g.pose().popPose();
        Component classLine = legend ? Component.translatable("screen.noirm.bingo.legendary")
                : Component.translatable("screen.noirm.bingo.class", NinjaClass.letter(p.code()));
        int labelX = Math.min(sx + 16, x + w - 4 - font.width(classLine) / 2); // keep it inside the page
        g.drawCenteredString(font, classLine, labelX, sy + 35, legend ? LEGEND : color);
        int tx = x + 8;
        int ty = y + 8;
        int textW = w - 56;
        for (FormattedCharSequence line : font.split(p.name(), textW)) {
            g.drawString(font, line, tx, ty, legend ? LEGEND : PAPER, false);
            ty += 10;
        }
        g.drawString(font, fit(Component.translatable("screen.noirm.bingo.clan", p.clan()), textW), tx, ty + 1, DIM, false);
        ty = Math.max(ty + 16, y + 56);
        g.fill(x + 6, ty, x + w - 6, ty + 1, legend ? 0xFF6E5520 : RULE);
        ty += 6;
        g.drawString(font, Component.translatable("screen.noirm.bingo.known_for"), tx, ty, GOLD, false);
        ty += 11;
        // The bounty sits at the bottom; "last seen" just above it; the traits get what's left.
        int by = y + h - 30;
        List<FormattedCharSequence> seen = font.split(p.seen(), w - 16);
        int traitsEnd = by - 8 - 11 - 10 * seen.size() - 4;
        for (Component trait : p.traits()) {
            for (FormattedCharSequence line : font.split(Component.literal("• ").append(trait), w - 16)) {
                if (ty + 9 <= traitsEnd) {
                    g.drawString(font, line, tx, ty, PAPER, false);
                }
                ty += 10;
            }
        }
        ty = Math.min(ty, traitsEnd);
        ty += 4;
        g.drawString(font, Component.translatable("screen.noirm.bingo.last_seen"), tx, ty, GOLD, false);
        ty += 11;
        for (FormattedCharSequence line : seen) {
            g.drawString(font, line, tx, ty, PAPER, false);
            ty += 10;
        }
        // The bounty at the bottom, big; or the claimed stamp.
        g.fill(x + 6, by - 4, x + w - 6, by - 3, legend ? 0xFF6E5520 : RULE);
        g.drawString(font, Component.translatable("screen.noirm.bingo.bounty"), tx, by + 4, DIM, false);
        Component amount = Component.translatable("ryo.noirm.amount", Ryo.format(p.bounty()));
        g.pose().pushPose();
        g.pose().translate(x + w - 8 - font.width(amount) * 1.5f, by + 1, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.drawString(font, amount, 0, 0, p.claimed() ? FAINT : legend ? LEGEND : GOLD, true);
        g.pose().popPose();
        if (p.claimed()) {
            // A red stamp across the page: CLAIMED, and by whom.
            g.pose().pushPose();
            g.pose().translate(x + w / 2f, y + h / 2f + 6, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-14f));
            Component stamp = Component.translatable("screen.noirm.bingo.stamp");
            int sw = font.width(stamp) * 2 + 16;
            g.fill(-sw / 2, -14, sw / 2, 14, 0x70200606);
            g.renderOutline(-sw / 2, -14, sw, 28, 0xFFB0302A);
            g.pose().pushPose();
            g.pose().scale(2f, 2f, 1f);
            g.drawCenteredString(font, stamp, 0, -6, 0xFFD8443A);
            g.pose().popPose();
            g.pose().popPose();
            Component by2 = Component.translatable("screen.noirm.bingo.claimed_by", p.claimedBy());
            g.drawCenteredString(font, by2, x + w / 2, y + h / 2 + 28, 0xFFD8443A);
        }
    }

    private FormattedCharSequence fit(Component text, int width) {
        if (font.width(text) <= width) {
            return text.getVisualOrderText();
        }
        String cut = font.plainSubstrByWidth(text.getString(), width - font.width("…")) + "…";
        return Component.literal(cut).withStyle(text.getStyle()).getVisualOrderText();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = left + 8, y = top + 30;
        if (button == 0 && mouseX >= x && mouseX < x + LIST_W - 4 && mouseY >= y) {
            int index = scroll + (int) ((mouseY - y) / ROW_H);
            if (index >= 0 && index < info.pages().size() && (mouseY - y) % ROW_H < ROW_H - 2 && mouseY < top + H - 8) {
                selected = index;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1f));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll -= (int) Math.signum(delta);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN || key == org.lwjgl.glfw.GLFW.GLFW_KEY_S) {
            selected = Math.min(info.pages().size() - 1, selected + 1);
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || key == org.lwjgl.glfw.GLFW.GLFW_KEY_W) {
            selected = Math.max(0, selected - 1);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
}
