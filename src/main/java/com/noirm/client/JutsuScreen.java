package com.noirm.client;

import com.noirm.clan.Nature;
import com.noirm.clan.NatureSystem;
import com.noirm.dojutsu.Sharingan;
import com.noirm.jutsu.JutsuBook;
import com.noirm.network.JutsuCatalogPacket;
import com.noirm.network.NoirmNetwork;
import com.noirm.network.PowerPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The jutsu screen (J), in two tabs:
 * <ul>
 *   <li><b>Jutsu</b>: your jutsu as cards, filtered by nature (locked ones show how to learn
 *       them); your 4 loadout slots and the details of the jutsu under the mouse on the right.
 *       Click a card, then a slot, to equip it; right-click a slot to empty it.</li>
 *   <li><b>Nature Tree</b>: the five basic natures around the outside, the natures they combine
 *       into between them, Dust in the middle. Owned, available (you can unlock it now) or
 *       locked; click one to see what it needs and its jutsu, and unlock it.</li>
 * </ul>
 * All drawn with plain shapes and text (no custom art yet).
 */
public class JutsuScreen extends Screen {
    private enum Tab { JUTSU, TREE }

    private static final int BG = 0xF0101014;
    private static final int PANEL = 0xE01A1A20;
    private static final int BORDER = 0xFF6B5A2E;
    private static final int GOLD = 0xFFE0C060;
    private static final int DIM = 0xFF8A8A8A;
    private static final int LOCKED = 0xFF4A4A4A;
    private static final int OWNED_GREEN = 0xFF50D070;
    private static final int SIDEBAR = 142;
    private static final int FILTER_ALL = -2;
    private static final int FILTER_UNIVERSAL = -1;

    private Tab tab = Tab.JUTSU;
    private int filter = FILTER_ALL;
    private int scroll;
    private ResourceLocation selected;
    private String hovered;
    private Nature treeSelected;
    private Nature treeHovered;

    // layout (set in init)
    private int px;
    private int py;
    private int pw;
    private int ph;

    /** Clickable things drawn this frame. */
    private record Hit(int x, int y, int w, int h, Runnable left, Runnable right) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();

    public JutsuScreen() {
        super(Component.translatable("screen.noirm.jutsu"));
    }

    @Override
    protected void init() {
        pw = Math.min(width - 12, 520);
        ph = Math.min(height - 12, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ================================================================ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        hits.clear();
        hovered = null;
        treeHovered = null;
        box(g, px, py, pw, ph, BG, BORDER);
        PowerPackets.SyncPowers powers = ClientPowers.get();
        drawTabs(g, mouseX, mouseY, powers);
        if (powers == null) {
            g.drawCenteredString(font, Component.translatable("screen.noirm.jutsu.loading"), px + pw / 2, py + ph / 2, DIM);
            return;
        }
        int contentY = py + 20;
        if (tab == Tab.JUTSU) {
            drawJutsuTab(g, mouseX, mouseY, powers, contentY);
        } else {
            drawTreeTab(g, mouseX, mouseY, powers, contentY, partialTick);
        }
    }

    private void drawTabs(GuiGraphics g, int mouseX, int mouseY, PowerPackets.SyncPowers powers) {
        int x = px + 4;
        for (Tab t : Tab.values()) {
            Component label = Component.translatable("screen.noirm.jutsu.tab." + t.name().toLowerCase(Locale.ROOT));
            int w = font.width(label) + 12;
            boolean active = tab == t;
            box(g, x, py + 4, w, 13, active ? 0xFF2A2516 : PANEL, active ? GOLD : BORDER);
            g.drawCenteredString(font, label, x + w / 2, py + 7, active ? GOLD : DIM);
            hits.add(new Hit(x, py + 4, w, 13, () -> {
                tab = t;
                scroll = 0;
            }, null));
            x += w + 4;
        }
        if (powers != null) {
            Component clan = Component.translatable("hud.noirm.clan", powers.clanName());
            g.drawString(font, clan, px + pw - font.width(clan) - 6, py + 7, powers.canonClan() ? 0xFFE05050 : 0xFFE0B050, false);
        }
    }

    // ---------------------------------------------------------------- the Jutsu tab

    private void drawJutsuTab(GuiGraphics g, int mouseX, int mouseY, PowerPackets.SyncPowers powers, int top) {
        Map<Nature, Float> owned = ownedNatures(powers);
        // Nature filters: All, Universal, then each nature you own or have a jutsu of.
        int fx = px + 4;
        fx = filterBox(g, fx, top, "全", FILTER_ALL, 0xFFDDDDDD, "screen.noirm.jutsu.filter.all", mouseX, mouseY);
        fx = filterBox(g, fx, top, "忍", FILTER_UNIVERSAL, 0xFFBBBBBB, "screen.noirm.jutsu.filter.universal", mouseX, mouseY);
        for (Nature nature : Nature.values()) {
            boolean show = owned.containsKey(nature) || powers.learned().stream().anyMatch(e -> e.nature() == nature.ordinal());
            if (show) {
                fx = filterBox(g, fx, top, nature.kanji(), nature.ordinal(), 0xFF000000 | nature.color(), nature.translationKey(), mouseX, mouseY);
            }
        }

        int cardsX = px + 4;
        int cardsY = top + 18;
        int cardsW = pw - SIDEBAR - 12;
        int cardsH = py + ph - 4 - cardsY;
        box(g, cardsX, cardsY, cardsW, cardsH, PANEL, BORDER);
        List<Card> cards = cards(powers, owned);
        int cols = Math.max(1, (cardsW - 4) / 124);
        int cardW = (cardsW - 4) / cols - 4;
        int cardH = 26;
        int rows = (cards.size() + cols - 1) / cols;
        int visibleRows = Math.max(1, (cardsH - 4) / (cardH + 4));
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows - visibleRows));
        g.enableScissor(cardsX + 1, cardsY + 1, cardsX + cardsW - 1, cardsY + cardsH - 1);
        for (int i = 0; i < cards.size(); i++) {
            int row = i / cols - scroll;
            if (row < 0 || row >= visibleRows) {
                continue;
            }
            int x = cardsX + 4 + (i % cols) * (cardW + 4);
            int y = cardsY + 4 + row * (cardH + 4);
            drawCard(g, cards.get(i), x, y, cardW, cardH, mouseX, mouseY);
        }
        g.disableScissor();
        if (cards.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.noirm.jutsu.none"), cardsX + cardsW / 2, cardsY + cardsH / 2, DIM);
        }
        if (rows > visibleRows) {
            Component more = Component.literal((scroll + 1) + "/" + (rows - visibleRows + 1) + " ⇵");
            g.drawString(font, more, cardsX + cardsW - font.width(more) - 4, cardsY + cardsH - 10, DIM, false);
        }

        drawLoadout(g, powers, mouseX, mouseY, px + pw - SIDEBAR - 4, top);
    }

    private int filterBox(GuiGraphics g, int x, int y, String kanji, int value, int color, String tooltipKey, int mouseX, int mouseY) {
        boolean active = filter == value;
        boolean hover = mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 14;
        box(g, x, y, 16, 14, active ? 0xFF2A2516 : PANEL, active ? GOLD : hover ? 0xFF9A8A5E : BORDER);
        g.drawCenteredString(font, kanji, x + 8, y + 3, color);
        hits.add(new Hit(x, y, 16, 14, () -> {
            filter = value;
            scroll = 0;
        }, null));
        if (hover) {
            setTooltipForNextRenderPass(Component.translatable(tooltipKey));
        }
        return x + 18;
    }

    /** One jutsu card: known (with mastery) or locked (with how to learn it). */
    private record Card(JutsuCatalogPacket.Entry info, PowerPackets.JutsuEntry known) {}

    private List<Card> cards(PowerPackets.SyncPowers powers, Map<Nature, Float> owned) {
        List<Card> cards = new ArrayList<>();
        for (JutsuCatalogPacket.Entry info : ClientJutsuCatalog.all()) {
            PowerPackets.JutsuEntry known = ClientPowers.entry(info.id().toString());
            boolean natureOwned = info.nature() < 0 || owned.containsKey(Nature.values()[info.nature()]);
            if (known == null && (!natureOwned || !info.requires().isEmpty() || (info.unlockAfter().isEmpty() && !info.starter()))) {
                continue; // only show locked jutsu you could actually work toward
            }
            if (filter != FILTER_ALL && info.nature() != filter) {
                continue;
            }
            cards.add(new Card(info, known));
        }
        // Copied jutsu the catalog lists under another nature are already included; add anything left.
        for (PowerPackets.JutsuEntry entry : powers.learned()) {
            if (cards.stream().noneMatch(c -> c.info().id().equals(entry.id())) && ClientJutsuCatalog.get(entry.id().toString()) != null
                    && (filter == FILTER_ALL || entry.nature() == filter)) {
                cards.add(new Card(ClientJutsuCatalog.get(entry.id().toString()), entry));
            }
        }
        cards.sort((a, b) -> {
            if ((a.known() == null) != (b.known() == null)) {
                return a.known() == null ? 1 : -1;
            }
            return Integer.compare(a.info().nature(), b.info().nature());
        });
        return cards;
    }

    private void drawCard(GuiGraphics g, Card card, int x, int y, int w, int h, int mouseX, int mouseY) {
        JutsuCatalogPacket.Entry info = card.info();
        boolean known = card.known() != null;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        boolean chosen = info.id().equals(selected);
        int natureColor = info.nature() >= 0 ? 0xFF000000 | Nature.values()[info.nature()].color() : 0xFFCCCCCC;
        box(g, x, y, w, h, known ? 0xFF22222A : 0xFF18181C, chosen ? GOLD : hover ? 0xFF9A8A5E : known ? 0xFF3A3A44 : 0xFF2A2A2E);
        g.fill(x + 1, y + 1, x + 3, y + h - 1, known ? natureColor : LOCKED);
        String name = Component.translatable(Sharingan.jutsuKey(info.id())).getString();
        g.drawString(font, font.plainSubstrByWidth(name, w - 24), x + 6, y + 4, known ? natureColor : 0xFF707070, false);
        g.drawString(font, info.rank(), x + w - font.width(info.rank()) - 4, y + 4, known ? GOLD : 0xFF5A5A5A, false);
        String line;
        if (known) {
            line = castSymbol(info.cast()) + " " + Math.round(info.chakraCost()) + "✦  " + stars(card.known().mastery())
                    + (card.known().copied() ? " ◉" : "");
        } else {
            line = "🔒 " + unlockText(info);
        }
        g.drawString(font, font.plainSubstrByWidth(line, w - 10), x + 6, y + 15, known ? 0xFFB0B0B0 : 0xFF6A6A6A, false);
        if (hover) {
            hovered = info.id().toString();
        }
        if (known) {
            hits.add(new Hit(x, y, w, h, () -> selected = info.id().equals(selected) ? null : info.id(), null));
        }
    }

    private void drawLoadout(GuiGraphics g, PowerPackets.SyncPowers powers, int mouseX, int mouseY, int x, int top) {
        box(g, x, top, SIDEBAR, py + ph - 4 - top, PANEL, BORDER);
        g.drawString(font, Component.translatable("screen.noirm.jutsu.loadout"), x + 5, top + 4, GOLD, false);
        int y = top + 15;
        for (int slot = 0; slot < JutsuBook.SLOTS; slot++) {
            int index = slot;
            String id = powers.loadout().get(slot);
            PowerPackets.JutsuEntry entry = ClientPowers.entry(id);
            boolean hover = mouseX >= x + 4 && mouseX < x + SIDEBAR - 4 && mouseY >= y && mouseY < y + 15;
            box(g, x + 4, y, SIDEBAR - 8, 15, 0xFF202026, selected != null && hover ? GOLD : hover ? 0xFF9A8A5E : 0xFF3A3A44);
            g.drawString(font, String.valueOf(slot + 1), x + 8, y + 4, DIM, false);
            Component text = entry == null ? Component.translatable("hud.noirm.jutsu.empty")
                    : Component.translatable(Sharingan.jutsuKey(entry.id()));
            int color = entry == null ? 0xFF5A5A5A : entry.nature() >= 0 ? 0xFF000000 | Nature.values()[entry.nature()].color() : 0xFFDDDDDD;
            g.drawString(font, font.plainSubstrByWidth(text.getString(), SIDEBAR - 26), x + 18, y + 4, color, false);
            if (hover && entry != null) {
                hovered = entry.id().toString();
            }
            hits.add(new Hit(x + 4, y, SIDEBAR - 8, 15,
                    () -> {
                        if (selected != null) {
                            NoirmNetwork.sendToServer(new PowerPackets.EquipJutsu(index, selected.toString()));
                            selected = null;
                        }
                    },
                    () -> NoirmNetwork.sendToServer(new PowerPackets.EquipJutsu(index, ""))));
            y += 17;
        }
        Component hint = selected == null ? Component.translatable("screen.noirm.jutsu.hint")
                : Component.translatable("screen.noirm.jutsu.selected", Component.translatable(Sharingan.jutsuKey(selected)));
        y = wrap(g, hint, x + 5, y + 1, SIDEBAR - 10, selected == null ? DIM : 0xFF60D0FF) + 4;
        g.fill(x + 4, y, x + SIDEBAR - 4, y + 1, BORDER);
        String detailsId = hovered != null ? hovered : selected != null ? selected.toString() : null;
        if (detailsId != null) {
            drawDetails(g, ClientJutsuCatalog.get(detailsId), ClientPowers.entry(detailsId), x + 5, y + 5, SIDEBAR - 10);
        }
    }

    private void drawDetails(GuiGraphics g, JutsuCatalogPacket.Entry info, PowerPackets.JutsuEntry known, int x, int y, int w) {
        if (info == null) {
            return;
        }
        int natureColor = info.nature() >= 0 ? 0xFF000000 | Nature.values()[info.nature()].color() : 0xFFDDDDDD;
        y = wrap(g, Component.translatable(Sharingan.jutsuKey(info.id())), x, y, w, natureColor) + 1;
        Component nature = info.nature() >= 0 ? Component.translatable(Nature.values()[info.nature()].translationKey())
                : Component.translatable("screen.noirm.jutsu.filter.universal");
        y = wrap(g, Component.translatable("screen.noirm.jutsu.details.kind", info.rank(), nature), x, y, w, DIM) + 1;
        y = wrap(g, Component.translatable("screen.noirm.jutsu.cast." + castName(info.cast()))
                .append(" · ").append(Component.translatable("screen.noirm.jutsu.behavior." + info.behavior())), x, y, w, 0xFFC8C8C8) + 1;
        y = wrap(g, Component.translatable("screen.noirm.jutsu.details.cost", Math.round(info.chakraCost()),
                String.format(Locale.ROOT, "%.1f", info.cooldown() / 20f)), x, y, w, 0xFF7FA8FF) + 1;
        if (known != null) {
            y = wrap(g, Component.translatable("screen.noirm.jutsu.details.mastery", stars(known.mastery())), x, y, w, GOLD) + 1;
        } else {
            y = wrap(g, Component.literal("🔒 " + unlockText(info)), x, y, w, 0xFFB07070) + 1;
        }
        // What the next mastery level unlocks.
        for (JutsuCatalogPacket.Entry other : ClientJutsuCatalog.all()) {
            if (other.unlockAfter().equals(info.id().toString()) && ClientPowers.entry(other.id().toString()) == null) {
                y = wrap(g, Component.translatable("screen.noirm.jutsu.details.leads_to", Component.translatable(Sharingan.jutsuKey(other.id())),
                        other.unlockMastery()), x, y, w, 0xFF90C090) + 1;
            }
        }
    }

    // ---------------------------------------------------------------- the Nature Tree tab

    private void drawTreeTab(GuiGraphics g, int mouseX, int mouseY, PowerPackets.SyncPowers powers, int top, float partialTick) {
        Map<Nature, Float> owned = ownedNatures(powers);
        Map<Nature, Integer> best = bestMastery(powers);
        int cx0 = px + 4;
        int cw = pw - SIDEBAR - 12;
        int ch = py + ph - 4 - top;
        box(g, cx0, top, cw, ch, PANEL, BORDER);
        g.drawString(font, Component.translatable("screen.noirm.tree.title"), cx0 + 5, top + 4, GOLD, false);

        int ownedCount = 0;
        int available = 0;
        Map<Nature, NatureSystem.Status> status = new EnumMap<>(Nature.class);
        for (Nature nature : Nature.values()) {
            NatureSystem.Status s = NatureSystem.status(nature, owned, best);
            status.put(nature, s);
            if (s == NatureSystem.Status.OWNED) {
                ownedCount++;
            } else if (s == NatureSystem.Status.AVAILABLE) {
                available++;
            }
        }
        Component counter = Component.literal(ownedCount + " / " + Nature.values().length);
        g.drawString(font, counter, cx0 + cw - font.width(counter) - 5, top + 4, 0xFFCCCCCC, false);
        if (available > 0) {
            Component badge = Component.translatable("screen.noirm.tree.available", available);
            int bx = cx0 + cw - font.width(counter) - font.width(badge) - 16;
            box(g, bx - 3, top + 2, font.width(badge) + 6, 11, 0xFF2A2516, GOLD);
            g.drawString(font, badge, bx, top + 4, GOLD, false);
        }

        // Layout: basics on a pentagon, 2-part natures between their parts, Dust in the middle.
        float centerX = cx0 + cw / 2f;
        float centerY = top + 8 + (ch - 8) / 2f;
        float radius = Math.min(cw, ch - 16) * 0.40f;
        Map<Nature, float[]> pos = new EnumMap<>(Nature.class);
        Nature[] ring = {Nature.FIRE, Nature.WIND, Nature.LIGHTNING, Nature.EARTH, Nature.WATER};
        for (int i = 0; i < ring.length; i++) {
            double angle = -Math.PI / 2 + i * 2 * Math.PI / 5;
            pos.put(ring[i], new float[]{centerX + (float) Math.cos(angle) * radius, centerY + (float) Math.sin(angle) * radius});
        }
        for (Nature nature : Nature.combined()) {
            if (nature.tier() == 2) {
                float x = 0;
                float y = 0;
                for (Nature part : nature.components()) {
                    x += pos.get(part)[0];
                    y += pos.get(part)[1];
                }
                x /= 2;
                y /= 2;
                // Neighbours' mixes sit just inside the ring; the crossing ones (whose midpoint is near
                // the middle) are pushed out to half the radius, leaving the centre to Dust.
                float dx = x - centerX;
                float dy = y - centerY;
                float dist = (float) Math.hypot(dx, dy);
                float wanted = dist > radius * 0.6f ? dist * 0.86f : radius * 0.5f;
                pos.put(nature, new float[]{centerX + dx / dist * wanted, centerY + dy / dist * wanted});
            } else {
                pos.put(nature, new float[]{centerX, centerY});
            }
        }
        // Dotted lines from each part to what it combines into.
        for (Nature nature : Nature.combined()) {
            float[] to = pos.get(nature);
            for (Nature part : nature.components()) {
                float[] from = pos.get(part);
                boolean lit = status.get(nature) == NatureSystem.Status.OWNED;
                boolean ready = status.get(nature) == NatureSystem.Status.AVAILABLE;
                dotted(g, from[0], from[1], to[0], to[1], lit ? 0xFF000000 | nature.color() : ready ? 0xFF8A7A3E : 0xFF3A3A40);
            }
        }
        float pulse = 0.5f + 0.5f * Mth.sin((minecraft.player == null ? 0 : minecraft.player.tickCount + partialTick) * 0.25f);
        for (Nature nature : Nature.values()) {
            float[] p = pos.get(nature);
            int r = nature.isBasic() ? 12 : nature.tier() == 3 ? 11 : 9;
            NatureSystem.Status s = status.get(nature);
            int border = switch (s) {
                case OWNED -> 0xFF000000 | nature.color();
                case AVAILABLE -> lerpColor(0xFF8A7A3E, GOLD, pulse);
                case LOCKED -> 0xFF505058;
            };
            boolean hover = Math.abs(mouseX - p[0]) <= r && Math.abs(mouseY - p[1]) <= r;
            boolean chosen = nature == treeSelected;
            hexagon(g, Math.round(p[0]), Math.round(p[1]), r + (chosen ? 2 : 0), border);
            hexagon(g, Math.round(p[0]), Math.round(p[1]), r - 2 + (chosen ? 2 : 0), s == NatureSystem.Status.OWNED ? 0xFF2A2A30 : 0xFF16161A);
            int textColor = s == NatureSystem.Status.LOCKED ? 0xFF6A6A70 : 0xFF000000 | nature.color();
            g.drawCenteredString(font, nature.kanji(), Math.round(p[0]), Math.round(p[1]) - 4, textColor);
            if (s == NatureSystem.Status.AVAILABLE) {
                g.drawCenteredString(font, "+", Math.round(p[0]), Math.round(p[1]) - r - 9, GOLD);
            }
            if (hover) {
                treeHovered = nature;
            }
            hits.add(new Hit(Math.round(p[0]) - r, Math.round(p[1]) - r, r * 2, r * 2, () -> treeSelected = nature, null));
        }

        // Right: details of the nature under the mouse (or the one clicked).
        int sx = px + pw - SIDEBAR - 4;
        box(g, sx, top, SIDEBAR, ch, PANEL, BORDER);
        Nature shown = treeHovered != null ? treeHovered : treeSelected;
        int y = top + 5;
        if (shown == null) {
            wrap(g, Component.translatable("screen.noirm.tree.hint"), sx + 5, y, SIDEBAR - 10, DIM);
        } else {
            y = drawNatureDetails(g, shown, status.get(shown), owned, best, sx + 5, y, SIDEBAR - 10);
            if (status.get(shown) == NatureSystem.Status.AVAILABLE && shown == treeSelected) {
                int bw = SIDEBAR - 10;
                int by = Math.min(y + 4, top + ch - 20);
                boolean hover = mouseX >= sx + 5 && mouseX < sx + 5 + bw && mouseY >= by && mouseY < by + 14;
                box(g, sx + 5, by, bw, 14, hover ? 0xFF3A3218 : 0xFF2A2516, GOLD);
                g.drawCenteredString(font, Component.translatable("screen.noirm.tree.unlock"), sx + 5 + bw / 2, by + 3, GOLD);
                Nature target = shown;
                hits.add(new Hit(sx + 5, by, bw, 14, () -> NoirmNetwork.sendToServer(new PowerPackets.UnlockNature(target.ordinal())), null));
            }
        }
        // Legend.
        int ly = top + ch - 12;
        legend(g, sx + 5, ly, OWNED_GREEN, "screen.noirm.tree.owned", ownedCount);
        legend(g, sx + 5 + 46, ly, GOLD, "screen.noirm.tree.available_short", available);
        legend(g, sx + 5 + 92, ly, 0xFF6A6A70, "screen.noirm.tree.locked", Nature.values().length - ownedCount - available);
    }

    private int drawNatureDetails(GuiGraphics g, Nature nature, NatureSystem.Status status, Map<Nature, Float> owned,
                                  Map<Nature, Integer> best, int x, int y, int w) {
        int color = 0xFF000000 | nature.color();
        y = wrap(g, Component.translatable(nature.translationKey()), x, y, w, color) + 1;
        y = wrap(g, Component.literal(nature.japanese() + " · " + nature.kanji()), x, y, w, DIM) + 2;
        int statusColor = status == NatureSystem.Status.OWNED ? OWNED_GREEN : status == NatureSystem.Status.AVAILABLE ? GOLD : 0xFFB07070;
        y = wrap(g, Component.translatable("screen.noirm.tree.status." + status.name().toLowerCase(Locale.ROOT)), x, y, w, statusColor) + 3;
        if (nature.isBasic()) {
            if (status != NatureSystem.Status.OWNED) {
                y = wrap(g, Component.translatable("screen.noirm.tree.basic_locked"), x, y, w, DIM) + 3;
            }
        } else if (status != NatureSystem.Status.OWNED) {
            y = wrap(g, Component.translatable("screen.noirm.tree.requires"), x, y, w, 0xFFCCCCCC) + 1;
            for (Nature part : nature.components()) {
                boolean has = owned.containsKey(part);
                int mastery = best.getOrDefault(part, 0);
                boolean mastered = mastery >= NatureSystem.REQUIRED_MASTERY;
                y = wrap(g, Component.literal((has ? "✔ " : "✘ ")).append(Component.translatable(part.translationKey())),
                        x + 2, y, w - 2, has ? OWNED_GREEN : 0xFFB07070) + 1;
                if (has) {
                    y = wrap(g, Component.literal("   " + (mastered ? "✔ " : "✘ ")).append(Component.translatable("screen.noirm.tree.mastery",
                            NatureSystem.REQUIRED_MASTERY, mastery)), x + 2, y, w - 2, mastered ? OWNED_GREEN : 0xFFB07070) + 1;
                }
            }
            y += 2;
        }
        y = wrap(g, Component.translatable("screen.noirm.tree.jutsu"), x, y, w, 0xFFCCCCCC) + 1;
        for (JutsuCatalogPacket.Entry info : ClientJutsuCatalog.all()) {
            if (info.nature() == nature.ordinal()) {
                boolean knows = ClientPowers.entry(info.id().toString()) != null;
                y = wrap(g, Component.literal((knows ? "• " : "◦ ")).append(Component.translatable(Sharingan.jutsuKey(info.id()))),
                        x + 2, y, w - 2, knows ? color : 0xFF707070);
            }
        }
        return y;
    }

    private void legend(GuiGraphics g, int x, int y, int color, String key, int count) {
        g.fill(x, y + 2, x + 5, y + 7, color);
        g.drawString(font, Component.literal(String.valueOf(count)), x + 8, y, color, false);
    }

    // ================================================================ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.contains(mouseX, mouseY)) {
                Runnable action = button == 1 ? hit.right() : hit.left();
                if (action != null) {
                    action.run();
                    if (minecraft != null) {
                        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1f, 0.35f));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Math.max(0, scroll - (int) Math.signum(delta));
        return true;
    }

    // ================================================================ helpers

    private static Map<Nature, Float> ownedNatures(PowerPackets.SyncPowers powers) {
        Map<Nature, Float> owned = new EnumMap<>(Nature.class);
        for (int i = 0; i < powers.natures().size(); i++) {
            owned.put(Nature.values()[powers.natures().get(i)], powers.affinities().get(i));
        }
        return owned;
    }

    private static Map<Nature, Integer> bestMastery(PowerPackets.SyncPowers powers) {
        Map<Nature, Integer> best = new EnumMap<>(Nature.class);
        for (PowerPackets.JutsuEntry entry : powers.learned()) {
            if (entry.nature() >= 0) {
                best.merge(Nature.values()[entry.nature()], entry.mastery(), Math::max);
            }
        }
        return best;
    }

    private static String stars(int mastery) {
        return "★".repeat(Math.max(0, mastery)) + "☆".repeat(Math.max(0, JutsuBook.MAX_MASTERY - mastery));
    }

    private static String castName(int cast) {
        return switch (cast) {
            case 1 -> "charge";
            case 2 -> "channel";
            default -> "tap";
        };
    }

    private static String castSymbol(int cast) {
        return switch (cast) {
            case 1 -> "◔";
            case 2 -> "≋";
            default -> "•";
        };
    }

    private static String unlockText(JutsuCatalogPacket.Entry info) {
        if (!info.unlockAfter().isEmpty()) {
            ResourceLocation after = ResourceLocation.tryParse(info.unlockAfter());
            String name = after == null ? info.unlockAfter() : Component.translatable(Sharingan.jutsuKey(after)).getString();
            return Component.translatable("screen.noirm.jutsu.unlock_after", name, info.unlockMastery()).getString();
        }
        return Component.translatable("screen.noirm.jutsu.unlock_nature").getString();
    }

    /** Draws wrapped text; returns the y below it. */
    private int wrap(GuiGraphics g, Component text, int x, int y, int w, int color) {
        for (FormattedCharSequence line : font.split(FormattedText.composite(text), w)) {
            g.drawString(font, line, x, y, color, false);
            y += 9;
        }
        return y;
    }

    private static void box(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    /** A pointy-top hexagon of "radius" r, drawn row by row. */
    private static void hexagon(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            float t = Math.abs(dy) / (float) r;
            int half = Math.round(r * 0.87f * (t <= 0.5f ? 1f : (1f - t) * 2f));
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    private static void dotted(GuiGraphics g, float x1, float y1, float x2, float y2, int color) {
        float length = (float) Math.hypot(x2 - x1, y2 - y1);
        for (float d = 0; d < length; d += 4f) {
            int x = Math.round(x1 + (x2 - x1) * d / length);
            int y = Math.round(y1 + (y2 - y1) * d / length);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = a >> 16 & 0xFF;
        int ag = a >> 8 & 0xFF;
        int ab = a & 0xFF;
        int r = Math.round(ar + ((b >> 16 & 0xFF) - ar) * t);
        int gr = Math.round(ag + ((b >> 8 & 0xFF) - ag) * t);
        int bl = Math.round(ab + ((b & 0xFF) - ab) * t);
        return 0xFF000000 | r << 16 | gr << 8 | bl;
    }
}
