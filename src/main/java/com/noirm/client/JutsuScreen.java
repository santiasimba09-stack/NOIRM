package com.noirm.client;

import com.noirm.clan.Nature;
import com.noirm.clan.NatureSystem;
import com.noirm.client.ui.HoverTracker;
import com.noirm.client.ui.NoirmStyled;
import com.noirm.client.ui.ScreenMotion;
import com.noirm.client.ui.TabStrip;
import com.noirm.client.ui.Theme;
import com.noirm.client.ui.Ui;
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
 * Drawn with the NOIRM kit ({@code client.ui}): one flat panel, text tabs with a sliding underline,
 * cards without frames, a hairline between the content and the side column; it fades in and out.
 */
public class JutsuScreen extends Screen implements NoirmStyled {
    private enum Tab { JUTSU, TREE }

    private static final int GOLD = Theme.GOLD;
    private static final int DIM = Theme.TEXT_DIM;
    private static final int LOCKED = Theme.TEXT_FAINT;
    private static final int OWNED_GREEN = Theme.GREEN;
    private static final int NO = 0xFFC07070;
    private static final int SIDEBAR = 146;
    private static final int PAD = Theme.S5;
    private static final int FILTER_ALL = -2;
    private static final int FILTER_UNIVERSAL = -1;

    private Tab tab = Tab.JUTSU;
    private int filter = FILTER_ALL;
    private int scroll;
    private ResourceLocation selected;
    private String hovered;
    private Nature treeSelected;
    private Nature treeHovered;
    private final ScreenMotion motion = new ScreenMotion();
    private final TabStrip tabs = new TabStrip();
    private final HoverTracker hover = new HoverTracker();
    private String[] tabLabels;

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
        pw = Math.min(width - 16, 520);
        ph = Math.min(height - 16, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        tabLabels = new String[]{Component.translatable("screen.noirm.jutsu.tab.jutsu").getString(),
                Component.translatable("screen.noirm.jutsu.tab.tree").getString()};
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
        hovered = null;
        treeHovered = null;
        motion.scrim(g, width, height);
        motion.begin(g);
        Ui.panel(g, px, py, pw, ph);
        PowerPackets.SyncPowers powers = ClientPowers.get();
        drawHeader(g, mouseX, mouseY, powers);
        if (powers == null) {
            Ui.textCentered(g, font, Component.translatable("screen.noirm.jutsu.loading").getString(), px + pw / 2, py + ph / 2, DIM);
            motion.end(g);
            return;
        }
        int contentY = py + PAD + Theme.ROW + Theme.S4;
        motion.beginContent(g);
        if (tab == Tab.JUTSU) {
            drawJutsuTab(g, mouseX, mouseY, powers, contentY);
        } else {
            drawTreeTab(g, mouseX, mouseY, powers, contentY, partialTick);
        }
        motion.endContent(g);
        motion.end(g);
    }

    /** The tabs (the screen's title), the clan on the right, one rule under them. */
    private void drawHeader(GuiGraphics g, int mouseX, int mouseY, PowerPackets.SyncPowers powers) {
        int x = px + PAD;
        int w = pw - PAD * 2;
        int right = x + w;
        if (powers != null) {
            Component clan = Component.translatable("hud.noirm.clan", powers.clanName());
            int cw = Math.min(font.width(clan), w / 3);
            int color = powers.canonClan() ? Theme.RED : GOLD;
            Ui.roundRect(g, right - cw - 7, py + PAD + 4, 4, 4, color);
            g.drawString(font, Ui.fit(font, clan, cw), right - cw, py + PAD + 3, Theme.TEXT_DIM, false);
            right -= cw + 12;
        }
        tabs.draw(g, font, x, py + PAD, right - x, tabLabels, tab.ordinal(), new int[]{Theme.SEAL}, mouseX, mouseY);
    }

    // ---------------------------------------------------------------- the Jutsu tab

    private void drawJutsuTab(GuiGraphics g, int mouseX, int mouseY, PowerPackets.SyncPowers powers, int top) {
        Map<Nature, Float> owned = ownedNatures(powers);
        int cardsX = px + PAD;
        int cardsW = pw - SIDEBAR - PAD * 3;
        // Nature filters: All, Universal, then each nature you own or have a jutsu of.
        int fx = cardsX;
        fx = filterBox(g, fx, top, "全", FILTER_ALL, Theme.TEXT, "screen.noirm.jutsu.filter.all", mouseX, mouseY);
        fx = filterBox(g, fx, top, "忍", FILTER_UNIVERSAL, Theme.TEXT_DIM, "screen.noirm.jutsu.filter.universal", mouseX, mouseY);
        for (Nature nature : Nature.values()) {
            boolean show = owned.containsKey(nature) || powers.learned().stream().anyMatch(e -> e.nature() == nature.ordinal());
            if (show && fx + 16 <= cardsX + cardsW) {
                fx = filterBox(g, fx, top, nature.kanji(), nature.ordinal(), 0xFF000000 | nature.color(), nature.translationKey(), mouseX, mouseY);
            }
        }

        int cardsY = top + 14 + Theme.S4;
        int cardsH = py + ph - PAD - cardsY;
        List<Card> cards = cards(powers, owned);
        int gap = Theme.S2;
        int cols = Math.max(1, (cardsW + gap) / 112);
        int cardW = (cardsW - gap * (cols - 1) - 4) / cols;
        int cardH = 26;
        int rows = (cards.size() + cols - 1) / cols;
        int visibleRows = Math.max(1, (cardsH + gap) / (cardH + gap));
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows - visibleRows));
        g.enableScissor(cardsX, cardsY, cardsX + cardsW, cardsY + cardsH);
        for (int i = 0; i < cards.size(); i++) {
            int row = i / cols - scroll;
            if (row < 0 || row >= visibleRows) {
                continue;
            }
            int x = cardsX + (i % cols) * (cardW + gap);
            int y = cardsY + row * (cardH + gap);
            drawCard(g, cards.get(i), x, y, cardW, cardH, mouseX, mouseY);
        }
        g.disableScissor();
        if (cards.isEmpty()) {
            Ui.textCentered(g, font, Component.translatable("screen.noirm.jutsu.none").getString(), cardsX + cardsW / 2, cardsY + cardsH / 2 - 4, Theme.TEXT_FAINT);
        }
        Ui.scrollbar(g, cardsX + cardsW - 1, cardsY, visibleRows * (cardH + gap) - gap, scroll, visibleRows, rows, Theme.IRON);

        drawLoadout(g, powers, mouseX, mouseY, px + pw - SIDEBAR - PAD, top);
    }

    private int filterBox(GuiGraphics g, int x, int y, String kanji, int value, int color, String tooltipKey, int mouseX, int mouseY) {
        boolean active = filter == value;
        float hv = hover.get(HoverTracker.key(1, value), mouseX, mouseY, x, y, 16, 14);
        if (active || hv > 0.01f) {
            g.fill(x, y, x + 16, y + 14, active ? Theme.mix(Theme.RAISED, color, 0.14f) : Theme.fade(Theme.RAISED_HOVER, hv));
        }
        if (active) {
            g.fill(x + 2, y + 13, x + 14, y + 14, color);
        }
        int textColor = active ? color : Theme.mix(Theme.darken(color, 0.55f), color, hv);
        Ui.textCentered(g, font, kanji, x + 8, y + 3, textColor);
        hits.add(new Hit(x, y, 16, 14, () -> {
            if (filter != value) {
                filter = value;
                scroll = 0;
                motion.tabChanged();
            }
        }, null));
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 14) {
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
        boolean chosen = info.id().equals(selected);
        float hv = hover.get(HoverTracker.key(2, info.id().hashCode()), mouseX, mouseY, x, y, w, h);
        int natureColor = info.nature() >= 0 ? 0xFF000000 | Nature.values()[info.nature()].color() : 0xFFCCCCCC;
        if (known) {
            Ui.row(g, x, y, w, h, hv, chosen, chosen ? GOLD : natureColor);
            if (!chosen) {
                g.fill(x, y + 3, x + 1, y + h - 3, Theme.mix(Theme.darken(natureColor, 0.7f), natureColor, hv));
            }
        } else {
            g.fill(x, y, x + w, y + h, Theme.mix(Theme.fade(Theme.WELL, 0.6f), Theme.WELL, hv));
        }
        String name = Component.translatable(Sharingan.jutsuKey(info.id())).getString();
        String rank = info.rank();
        g.drawString(font, Ui.fit(font, name, w - 14 - font.width(rank)), x + 6, y + 4, known ? Theme.mix(natureColor, Theme.TEXT, 0.25f) : LOCKED, false);
        Ui.textRight(g, font, rank, x + w - 5, y + 4, known ? Theme.darken(GOLD, 0.85f) : Theme.darken(LOCKED, 0.8f));
        String line;
        if (known) {
            line = castSymbol(info.cast()) + " " + Math.round(info.chakraCost()) + "✦  " + stars(card.known().mastery())
                    + (card.known().copied() ? " ◉" : "");
        } else {
            line = "🔒 " + unlockText(info);
        }
        g.drawString(font, Ui.fit(font, line, w - 11), x + 6, y + 15, known ? DIM : Theme.darken(LOCKED, 0.9f), false);
        if (Ui.in(mouseX, mouseY, x, y, w, h)) {
            hovered = info.id().toString();
        }
        if (known) {
            hits.add(new Hit(x, y, w, h, () -> selected = info.id().equals(selected) ? null : info.id(), null));
        }
    }

    private void drawLoadout(GuiGraphics g, PowerPackets.SyncPowers powers, int mouseX, int mouseY, int x, int top) {
        int bottom = py + ph - PAD;
        Ui.vline(g, x - PAD / 2 - 1, top, bottom - top);
        int y = Ui.section(g, font, x, top + 3, SIDEBAR, Component.translatable("screen.noirm.jutsu.loadout"), Theme.TEXT_DIM);
        for (int slot = 0; slot < JutsuBook.SLOTS; slot++) {
            int index = slot;
            String id = powers.loadout().get(slot);
            PowerPackets.JutsuEntry entry = ClientPowers.entry(id);
            boolean over = Ui.in(mouseX, mouseY, x, y, SIDEBAR, 15);
            float hv = hover.get(HoverTracker.key(3, slot), over);
            Ui.row(g, x, y, SIDEBAR, 15, hv, false, selected != null ? GOLD : Theme.IRON);
            if (selected != null) {
                // Waiting for a slot: the slots breathe gold.
                g.fill(x, y + 14, x + SIDEBAR, y + 15, Theme.fade(GOLD, 0.25f + 0.5f * hv));
            }
            g.drawString(font, String.valueOf(slot + 1), x + 5, y + 4, Theme.TEXT_FAINT, false);
            Component text = entry == null ? Component.translatable("hud.noirm.jutsu.empty")
                    : Component.translatable(Sharingan.jutsuKey(entry.id()));
            int color = entry == null ? Theme.TEXT_FAINT : entry.nature() >= 0 ? 0xFF000000 | Nature.values()[entry.nature()].color() : Theme.TEXT;
            g.drawString(font, Ui.fit(font, text.getString(), SIDEBAR - 22), x + 15, y + 4, color, false);
            if (over && entry != null) {
                hovered = entry.id().toString();
            }
            hits.add(new Hit(x, y, SIDEBAR, 15,
                    () -> {
                        if (selected != null) {
                            NoirmNetwork.sendToServer(new PowerPackets.EquipJutsu(index, selected.toString()));
                            selected = null;
                        }
                    },
                    () -> NoirmNetwork.sendToServer(new PowerPackets.EquipJutsu(index, ""))));
            y += 16;
        }
        Component hint = selected == null ? Component.translatable("screen.noirm.jutsu.hint")
                : Component.translatable("screen.noirm.jutsu.selected", Component.translatable(Sharingan.jutsuKey(selected)));
        y = wrap(g, hint, x, y + 3, SIDEBAR, selected == null ? Theme.TEXT_FAINT : Theme.CHAKRA_LIGHT) + Theme.S3;
        Ui.hairline(g, x, y, SIDEBAR);
        String detailsId = hovered != null ? hovered : selected != null ? selected.toString() : null;
        g.enableScissor(x, y + 1, x + SIDEBAR, bottom);
        if (detailsId != null) {
            drawDetails(g, ClientJutsuCatalog.get(detailsId), ClientPowers.entry(detailsId), x, y + Theme.S3, SIDEBAR);
        } else {
            drawEyes(g, powers, x, y + Theme.S3, SIDEBAR);
        }
        g.disableScissor();
    }

    /** The Sharingan's stage and growth (or, still dormant, how close it feels to waking). */
    private void drawEyes(GuiGraphics g, PowerPackets.SyncPowers powers, int x, int y, int w) {
        int red = 0xFFE03030;
        int stage = powers.sharinganStage();
        if (stage > 0) {
            String tomoe = "●".repeat(stage) + "○".repeat(3 - stage);
            y = wrap(g, Component.translatable("screen.noirm.eyes.sharingan", tomoe), x, y, w, red) + 1;
            float progress = PowerHud.sharinganProgress(powers);
            if (progress < 0) {
                y = wrap(g, Component.translatable("screen.noirm.eyes.mature"), x, y, w, GOLD) + 2;
            } else {
                y = wrap(g, Component.translatable("screen.noirm.eyes.progress", powers.sharinganXp(), powers.sharinganNextXp()), x, y, w, Theme.TEXT) + 1;
                Ui.bar(g, x, y + 1, w, 3, progress, 0xFFD02020);
                y += 7;
                y = wrap(g, Component.translatable("screen.noirm.eyes.grows"), x, y, w, DIM) + 2;
                if (powers.sharinganDrillXp() >= Sharingan.DRILL_XP_CAP) {
                    wrap(g, Component.translatable("screen.noirm.eyes.drill_capped"), x, y, w, DIM);
                }
            }
        } else if (powers.sharinganPressure() >= 0) {
            y = wrap(g, Component.translatable("screen.noirm.eyes.dormant"), x, y, w, 0xFF904040) + 1;
            int tier = com.noirm.dojutsu.SharinganAwakening.hintTier(powers.sharinganPressure());
            y = wrap(g, Component.translatable("screen.noirm.eyes.pressure." + tier), x, y, w, tier >= 3 ? red : Theme.TEXT) + 2;
            wrap(g, Component.translatable("screen.noirm.eyes.routes"), x, y, w, DIM);
        }
    }

    private void drawDetails(GuiGraphics g, JutsuCatalogPacket.Entry info, PowerPackets.JutsuEntry known, int x, int y, int w) {
        if (info == null) {
            return;
        }
        int natureColor = info.nature() >= 0 ? 0xFF000000 | Nature.values()[info.nature()].color() : Theme.TEXT;
        y = wrap(g, Component.translatable(Sharingan.jutsuKey(info.id())), x, y, w, natureColor) + 1;
        Component nature = info.nature() >= 0 ? Component.translatable(Nature.values()[info.nature()].translationKey())
                : Component.translatable("screen.noirm.jutsu.filter.universal");
        y = wrap(g, Component.translatable("screen.noirm.jutsu.details.kind", info.rank(), nature), x, y, w, DIM) + 3;
        y = wrap(g, Component.translatable("screen.noirm.jutsu.cast." + castName(info.cast()))
                .append(" · ").append(Component.translatable("screen.noirm.jutsu.behavior." + info.behavior())), x, y, w, Theme.TEXT) + 1;
        y = wrap(g, Component.translatable("screen.noirm.jutsu.details.cost", Math.round(info.chakraCost()),
                String.format(Locale.ROOT, "%.1f", info.cooldown() / 20f)), x, y, w, Theme.CHAKRA_LIGHT) + 3;
        if (known != null) {
            y = wrap(g, Component.translatable("screen.noirm.jutsu.details.mastery", stars(known.mastery())), x, y, w, GOLD) + 1;
        } else {
            y = wrap(g, Component.literal("🔒 " + unlockText(info)), x, y, w, NO) + 1;
            Component scroll = ClientNatureTraining.scrollHint(info); // other routes: jutsu scrolls
            if (scroll != null) {
                y = wrap(g, scroll, x, y, w, 0xFFC8A870) + 1;
            }
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
        int cx0 = px + PAD;
        int cw = pw - SIDEBAR - PAD * 3;
        int bottom = py + ph - PAD;
        int ch = bottom - top;

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
        String counter = ownedCount + " / " + Nature.values().length;
        int headRight = cx0 + cw - font.width(counter);
        g.drawString(font, counter, headRight, top + 3, Theme.TEXT_DIM, false);
        if (available > 0) {
            String badge = Component.translatable("screen.noirm.tree.available", available).getString();
            int bw = font.width(badge) + 6;
            headRight -= bw + Theme.S3;
            Ui.chip(g, font, headRight, top + 1, badge, GOLD);
        }
        Ui.section(g, font, cx0, top + 3, headRight - cx0 - Theme.S3, Component.translatable("screen.noirm.tree.title"), Theme.TEXT_DIM);

        // Layout: basics on a pentagon, 2-part natures between their parts, Dust in the middle.
        int treeTop = top + 14;
        int treeH = bottom - treeTop;
        float centerX = cx0 + cw / 2f;
        float centerY = treeTop + (treeH - 10) / 2f;
        float radius = Math.min(cw, treeH - 24) * 0.40f;
        Map<Nature, float[]> pos = new EnumMap<>(Nature.class);
        Nature[] ring = {Nature.FIRE, Nature.WIND, Nature.LIGHTNING, Nature.EARTH, Nature.WATER};
        for (int i = 0; i < ring.length; i++) {
            double angle = -Math.PI / 2 + i * 2 * Math.PI / 5;
            pos.put(ring[i], new float[]{centerX + (float) Math.cos(angle) * radius, centerY + (float) Math.sin(angle) * radius});
        }
        // Yin and Yang aren't elements: they sit in the bottom corners, Yin–Yang between them.
        pos.put(Nature.YIN, new float[]{cx0 + 16, bottom - 14});
        pos.put(Nature.YANG, new float[]{cx0 + cw - 16, bottom - 14});
        pos.put(Nature.YIN_YANG, new float[]{centerX, bottom - 14});
        for (Nature nature : Nature.combined()) {
            if (!nature.isElemental()) {
                continue;
            }
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
                dotted(g, from[0], from[1], to[0], to[1], lit ? Theme.darken(0xFF000000 | nature.color(), 0.8f) : ready ? 0xFF7A6C38 : 0xFF2E2B34);
            }
        }
        float pulse = 0.5f + 0.5f * Mth.sin((minecraft.player == null ? 0 : minecraft.player.tickCount + partialTick) * 0.25f);
        for (Nature nature : Nature.values()) {
            float[] p = pos.get(nature);
            int r = nature.isBasic() ? 12 : nature.tier() == 3 ? 11 : 9;
            NatureSystem.Status s = status.get(nature);
            int color = 0xFF000000 | nature.color();
            int border = switch (s) {
                case OWNED -> color;
                case AVAILABLE -> Theme.mix(0xFF8A7A3E, GOLD, pulse);
                case LOCKED -> 0xFF3A3640;
            };
            boolean over = Math.abs(mouseX - p[0]) <= r && Math.abs(mouseY - p[1]) <= r;
            float hv = hover.get(HoverTracker.key(4, nature.ordinal()), over);
            boolean chosen = nature == treeSelected;
            int ix = Math.round(p[0]);
            int iy = Math.round(p[1]);
            int grow = chosen ? 2 : Math.round(hv);
            hexagon(g, ix, iy, r + grow, chosen ? Theme.TEXT : Theme.mix(border, Theme.TEXT, hv * 0.35f));
            hexagon(g, ix, iy, r - 1 + grow, s == NatureSystem.Status.OWNED ? Theme.mix(Theme.RAISED, color, 0.18f) : Theme.WELL);
            int textColor = s == NatureSystem.Status.LOCKED ? Theme.TEXT_FAINT : color;
            Ui.textCentered(g, font, nature.kanji(), ix, iy - 4, textColor);
            if (s == NatureSystem.Status.AVAILABLE) {
                Ui.textCentered(g, font, "+", ix, iy - r - 9, GOLD);
            } else if (s == NatureSystem.Status.LOCKED && nature.isBasic()) {
                // A drill under way: its percent; a hidden affinity (shown by a chakra paper): a star.
                int pct = Math.round(ClientNatureTraining.share(nature) * 100);
                String mark = (ClientNatureTraining.isHidden(nature) ? "✧" : "") + (pct > 0 ? pct + "%" : "");
                if (!mark.isEmpty()) {
                    Ui.textCentered(g, font, mark, ix, iy + r + 2, color);
                }
            }
            if (over) {
                treeHovered = nature;
            }
            hits.add(new Hit(ix - r, iy - r, r * 2, r * 2, () -> treeSelected = nature, null));
        }

        // Right: details of the nature under the mouse (or the one clicked).
        int sx = px + pw - SIDEBAR - PAD;
        Ui.vline(g, sx - PAD / 2 - 1, top, ch);
        Nature shown = treeHovered != null ? treeHovered : treeSelected;
        int y = top + 3;
        if (shown == null) {
            wrap(g, Component.translatable("screen.noirm.tree.hint"), sx, y, SIDEBAR, DIM);
        } else {
            g.enableScissor(sx, top, sx + SIDEBAR, bottom - 14); // long jutsu lists stop above the legend
            y = drawNatureDetails(g, shown, status.get(shown), owned, best, sx, y, SIDEBAR);
            g.disableScissor();
            if (status.get(shown) == NatureSystem.Status.AVAILABLE && shown == treeSelected) {
                int bw = SIDEBAR;
                int by = Math.min(y + 4, bottom - 32);
                boolean over = Ui.in(mouseX, mouseY, sx, by, bw, 15);
                Ui.button(g, font, sx, by, bw, 15, Component.translatable("screen.noirm.tree.unlock"),
                        hover.get(HoverTracker.key(5, 0), over), true, GOLD, true);
                Nature target = shown;
                hits.add(new Hit(sx, by, bw, 15, () -> NoirmNetwork.sendToServer(new PowerPackets.UnlockNature(target.ordinal())), null));
            }
        }
        // Legend.
        int ly = bottom - 9;
        Ui.hairline(g, sx, ly - 4, SIDEBAR);
        legend(g, sx, ly, OWNED_GREEN, ownedCount);
        legend(g, sx + 46, ly, GOLD, available);
        legend(g, sx + 92, ly, Theme.TEXT_FAINT, Nature.values().length - ownedCount - available);
    }

    private int drawNatureDetails(GuiGraphics g, Nature nature, NatureSystem.Status status, Map<Nature, Float> owned,
                                  Map<Nature, Integer> best, int x, int y, int w) {
        int color = 0xFF000000 | nature.color();
        y = wrap(g, Component.translatable(nature.translationKey()), x, y, w, color) + 1;
        y = wrap(g, Component.literal(nature.japanese() + " · " + nature.kanji()), x, y, w, DIM) + 2;
        int statusColor = status == NatureSystem.Status.OWNED ? OWNED_GREEN : status == NatureSystem.Status.AVAILABLE ? GOLD : NO;
        y = wrap(g, Component.translatable("screen.noirm.tree.status." + status.name().toLowerCase(Locale.ROOT)), x, y, w, statusColor) + 4;
        if (nature.isBasic()) {
            if (status != NatureSystem.Status.OWNED) {
                for (Component line : ClientNatureTraining.drillLines(nature)) { // the drill, its progress, other routes
                    y = wrap(g, line, x, y, w, DIM) + 1;
                }
                y += 3;
            }
        } else if (status != NatureSystem.Status.OWNED) {
            y = wrap(g, Component.translatable("screen.noirm.tree.requires"), x, y, w, Theme.TEXT) + 1;
            for (Nature part : nature.components()) {
                boolean has = owned.containsKey(part);
                int mastery = best.getOrDefault(part, 0);
                boolean mastered = mastery >= NatureSystem.REQUIRED_MASTERY;
                y = wrap(g, Component.literal((has ? "✔ " : "✘ ")).append(Component.translatable(part.translationKey())),
                        x + 2, y, w - 2, has ? OWNED_GREEN : NO) + 1;
                if (has) {
                    y = wrap(g, Component.literal("   " + (mastered ? "✔ " : "✘ ")).append(Component.translatable("screen.noirm.tree.mastery",
                            NatureSystem.REQUIRED_MASTERY, mastery)), x + 2, y, w - 2, mastered ? OWNED_GREEN : NO) + 1;
                }
            }
            y += 3;
        }
        y = Ui.section(g, font, x, y, w, Component.translatable("screen.noirm.tree.jutsu"), Theme.TEXT_DIM);
        for (JutsuCatalogPacket.Entry info : ClientJutsuCatalog.all()) {
            if (info.nature() == nature.ordinal()) {
                boolean knows = ClientPowers.entry(info.id().toString()) != null;
                y = wrap(g, Component.literal((knows ? "• " : "◦ ")).append(Component.translatable(Sharingan.jutsuKey(info.id()))),
                        x + 2, y, w - 2, knows ? color : Theme.TEXT_FAINT);
            }
        }
        return y;
    }

    private void legend(GuiGraphics g, int x, int y, int color, int count) {
        Ui.roundRect(g, x, y + 2, 4, 4, color);
        g.drawString(font, String.valueOf(count), x + 8, y, color, false);
    }

    // ================================================================ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (motion.closing()) {
            return true;
        }
        int t = tabs.at(mouseX, mouseY);
        if (button == 0 && t >= 0 && t != tab.ordinal()) {
            tab = Tab.values()[t];
            scroll = 0;
            motion.tabChanged();
            click();
            return true;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.contains(mouseX, mouseY)) {
                Runnable action = button == 1 ? hit.right() : hit.left();
                if (action != null) {
                    action.run();
                    click();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1f, 0.35f));
        }
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
        if (info.unlockAfter().startsWith("stat:")) {
            String stat = info.unlockAfter().substring(5).toLowerCase(Locale.ROOT);
            return Component.translatable("screen.noirm.jutsu.unlock_stat",
                    Component.translatable("stat.noirm." + stat), info.unlockMastery()).getString();
        }
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
}
