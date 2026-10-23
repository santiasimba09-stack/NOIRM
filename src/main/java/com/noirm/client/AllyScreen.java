package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.ally.AllyMenu;
import com.noirm.ally.AllyPackets;
import com.noirm.ally.Behaviour;
import com.noirm.ally.CrewBrain;
import com.noirm.ally.NatureTeaching;
import com.noirm.ally.Occupation;
import com.noirm.ally.Order;
import com.noirm.ally.Tactics;
import com.noirm.clan.Nature;
import com.noirm.dojutsu.Sharingan;
import com.noirm.entity.SparringNinja;
import com.noirm.network.JutsuCatalogPacket;
import com.noirm.network.NoirmNetwork;
import com.noirm.registry.NoirmMenus;
import com.noirm.training.TrainingSystem.Stat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A crew mate's screen: a dark ink-and-paper panel with five tabs, everything drawn in code.
 * <ul>
 *   <li><b>Overview</b>: a turning model (drag it), clan, trait, respect, health, chakra, the four
 *   stats with their training progress, what they're doing.</li>
 *   <li><b>Gear</b>: what they wear and hold next to your inventory, their moveset and outfit.</li>
 *   <li><b>Jutsu</b>: their loadout, what they know (with mastery) and what you could teach them.</li>
 *   <li><b>Natures</b>: their nature tree; teach them one of yours over time (see {@code NatureTeaching}).</li>
 *   <li><b>Orders</b>: follow / wait / guard / patrol / train / meditate, come and fall back, behaviour, distance and jutsu tactics.</li>
 *   <li><b>Job</b>: their occupation on the crew (bodyguard, scout, medic…), fighting style, nature energy and what they carry.</li>
 * </ul>
 * Text is laid out from measured widths and cut with "…" where it would run over.
 */
public class AllyScreen extends AbstractContainerScreen<AllyMenu> {
    private static final int W = 372;
    private static final int H = 226;
    // Ink and paper.
    private static final int INK = 0xF4100E14;
    private static final int INK_LIGHT = 0xF41A1720;
    private static final int CARD = 0xFF17141C;
    private static final int CARD_HOVER = 0xFF221E29;
    private static final int RULE = 0xFF2E2935;
    private static final int FRAME = 0xFF3D3645;
    private static final int PAPER = 0xFFEDE4D3;
    private static final int DIM = 0xFF8F8796;
    private static final int FAINT = 0xFF5E5866;
    private static final int GOLD = 0xFFE8C060;
    private static final int GREEN = 0xFF6FD38A;
    private static final int RED = 0xFFE05A5A;
    private static final int SEAL = 0xFFB8322E;
    private static final int[] STAT_COLORS = {0xFFE07040, 0xFF40C8E0, 0xFFE04060, 0xFF4080F0};

    private enum Tab {
        OVERVIEW(0xFFE8C060), GEAR(0xFF7FB2D9), JUTSU(0xFFE0566A), NATURES(0xFF6FD3A0), ORDERS(0xFFB28CF0), JOB(0xFFD9A066);

        final int accent;

        Tab(int accent) {
            this.accent = accent;
        }

        String key() {
            return "screen.noirm.ally.tab." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Reopening the screen comes back to the last tab. */
    private static Tab lastTab = Tab.OVERVIEW;

    private record Hit(int x, int y, int w, int h, Runnable click, List<Component> tip) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private AllyPackets.Info info;
    private Tab tab = lastTab;
    private final List<Hit> hits = new ArrayList<>();
    private int knownScroll;
    private int teachScroll;
    private Nature selected;
    private Nature hovered;
    private float modelYaw = 25f;
    private boolean dragging;
    private long lastDrag;
    private long lastFrame;
    private long dismissArmedUntil;

    public AllyScreen(AllyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = W;
        imageHeight = H;
    }

    /** New data from the server: refresh the screen if it's this ally's. */
    public static void receive(AllyPackets.Info info) {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof AllyScreen screen
                && screen.menu.ally().getId() == info.entityId()) {
            screen.info = info;
        }
    }

    @Override
    protected void init() {
        super.init();
        menu.slotsShown = tab == Tab.GEAR;
    }

    private void send(AllyPackets.Action action, String arg) {
        NoirmNetwork.sendToServer(new AllyPackets.Command(menu.ally().getId(), action, arg));
    }

    private void setTab(Tab next) {
        tab = next;
        lastTab = next;
        menu.slotsShown = next == Tab.GEAR;
    }

    // ================================================================ drawing

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        renderBackground(g);
        hits.clear();
        hovered = null;
        int x = leftPos;
        int y = topPos;
        // The sheet: dark ink, a soft gradient, a double frame and faint paper lines.
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, 0xC0000000);
        g.fillGradient(x, y, x + W, y + H, INK_LIGHT, INK);
        for (int ly = y + 30; ly < y + H - 4; ly += 6) {
            g.fill(x + 3, ly, x + W - 3, ly + 1, 0x06FFFFFF);
        }
        g.renderOutline(x, y, W, H, FRAME);
        g.renderOutline(x + 2, y + 2, W - 4, H - 4, 0xFF221E28);
        drawHeader(g, mouseX, mouseY);
        drawTabs(g, mouseX, mouseY);
        if (info == null) {
            g.drawCenteredString(font, Component.translatable("screen.noirm.ally.loading"), x + W / 2, y + H / 2, DIM);
            return;
        }
        switch (tab) {
            case OVERVIEW -> drawOverview(g, mouseX, mouseY, partialTick);
            case GEAR -> drawGear(g, mouseX, mouseY);
            case JUTSU -> drawJutsu(g, mouseX, mouseY);
            case NATURES -> drawNatures(g, mouseX, mouseY, partialTick);
            case ORDERS -> drawOrders(g, mouseX, mouseY);
            case JOB -> drawJob(g, mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Everything is drawn in renderBg.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (menu.getCarried().isEmpty()) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit hit = hits.get(i);
                if (hit.tip() != null && !hit.tip().isEmpty() && hit.contains(mouseX, mouseY)) {
                    List<FormattedCharSequence> lines = new ArrayList<>();
                    for (Component line : hit.tip()) {
                        lines.addAll(font.split(line, 220));
                    }
                    g.renderTooltip(font, lines, mouseX, mouseY);
                    break;
                }
            }
        }
        renderTooltip(g, mouseX, mouseY);
    }

    private void drawHeader(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        // A red seal with 忍, then the name.
        g.fill(x + 8, y + 6, x + 22, y + 20, SEAL);
        g.renderOutline(x + 8, y + 6, 14, 14, 0xFF7A1E1B);
        g.drawCenteredString(font, "忍", x + 15, y + 9, 0xFFF4E6D0);
        // Dismiss (click twice).
        boolean armed = System.currentTimeMillis() < dismissArmedUntil;
        Component dismiss = Component.translatable(armed ? "screen.noirm.ally.dismiss_sure" : "screen.noirm.ally.dismiss");
        int dw = font.width(dismiss) + 12;
        int dx = x + W - 8 - dw;
        pill(g, dx, y + 6, dw, 14, dismiss, RED, true, armed, mouseX, mouseY);
        hits.add(new Hit(dx, y + 6, dw, 14, () -> {
            if (System.currentTimeMillis() < dismissArmedUntil) {
                send(AllyPackets.Action.DISMISS, "");
            } else {
                dismissArmedUntil = System.currentTimeMillis() + 3000;
            }
        }, List.of(Component.translatable("screen.noirm.ally.dismiss.tip"))));
        int nameW = dx - (x + 28) - 8;
        g.drawString(font, fit(title.getString(), nameW), x + 28, y + 9, GOLD, false);
        g.fill(x + 8, y + 24, x + W - 8, y + 25, RULE);
    }

    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        Tab[] tabs = Tab.values();
        int tw = (W - 16) / tabs.length;
        int y = topPos + 28;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int x = leftPos + 8 + i * tw;
            boolean active = t == tab;
            boolean hover = in(mouseX, mouseY, x, y, tw - 2, 14);
            g.fill(x, y, x + tw - 2, y + 14, active ? darken(t.accent, 0.28f) : hover ? CARD_HOVER : CARD);
            g.fill(x, y, x + tw - 2, y + (active ? 2 : 1), active ? t.accent : darken(t.accent, 0.5f));
            String label = fit(Component.translatable(t.key()).getString(), tw - 8);
            g.drawCenteredString(font, label, x + (tw - 2) / 2, y + 4, active ? PAPER : hover ? 0xFFCFC6D6 : DIM);
            hits.add(new Hit(x, y, tw - 2, 14, () -> setTab(t), null));
        }
    }

    // ---------------------------------------------------------------- Overview

    private void drawOverview(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x0 = leftPos + 8;
        int y0 = topPos + 46;
        // The model, turning slowly unless you drag it.
        int bw = 108;
        int bh = H - 46 - 8;
        g.fillGradient(x0, y0, x0 + bw, y0 + bh, 0xFF1E1A26, 0xFF0C0B10);
        g.renderOutline(x0, y0, bw, bh, RULE);
        int cx = x0 + bw / 2;
        int feet = y0 + bh - 18;
        for (int r = 0; r < 4; r++) {
            g.fill(cx - 30 + r * 6, feet + r, cx + 30 - r * 6, feet + r + 1, 0x40000000);
        }
        long now = System.currentTimeMillis();
        if (lastFrame != 0 && !dragging && now - lastDrag > 2500) {
            modelYaw += (now - lastFrame) * 0.02f;
        }
        lastFrame = now;
        renderModel(g, cx, feet, 58, modelYaw);
        g.drawCenteredString(font, fit(Component.translatable("screen.noirm.ally.drag").getString(), bw - 6), cx, y0 + bh - 11, FAINT);

        int x = x0 + bw + 8;
        int w = leftPos + W - 8 - x;
        int y = y0 + 2;
        // Clan, and the trait as a chip with its description.
        Component traitName = Component.translatable("screen.noirm.ally.trait." + info.trait().toLowerCase(Locale.ROOT));
        int chipW = font.width(traitName) + 10;
        int chipX = x + w - chipW;
        g.fill(chipX, y - 2, chipX + chipW, y + 10, 0xFF2A2433);
        g.renderOutline(chipX, y - 2, chipW, 12, GOLD);
        g.drawString(font, traitName, chipX + 5, y, GOLD, false);
        hits.add(new Hit(chipX, y - 2, chipW, 12, null, List.of(traitName.copy().withStyle(s -> s.withColor(GOLD)),
                Component.translatable("screen.noirm.ally.trait." + info.trait().toLowerCase(Locale.ROOT) + ".desc"))));
        String clanLabel = Component.translatable("screen.noirm.ally.clan").getString() + " ";
        g.drawString(font, clanLabel, x, y, DIM, false);
        String clan = info.extra().clan().isEmpty() ? "—" : info.extra().clan();
        g.drawString(font, fit(clan, chipX - x - font.width(clanLabel) - 6), x + font.width(clanLabel), y, PAPER, false);
        y += 15;
        // Respect, its mood word and a bar with the 20 / 70 marks.
        float respect = info.respect();
        String mood = respect >= 70 ? "devoted" : respect >= 40 ? "trusting" : respect >= 20 ? "wary" : "restless";
        int moodColor = respect >= 70 ? GREEN : respect >= 40 ? 0xFFC8D860 : respect >= 20 ? 0xFFE0A050 : RED;
        Component respectLine = Component.translatable("screen.noirm.ally.respect_value", Math.round(respect));
        g.drawString(font, respectLine, x, y, PAPER, false);
        Component moodText = Component.translatable("screen.noirm.ally.mood." + mood);
        g.drawString(font, fit(moodText.getString(), w - font.width(respectLine) - 8), x + w - Math.min(font.width(moodText), w - font.width(respectLine) - 8),
                y, moodColor, false);
        y += 10;
        bar(g, x, y, w, 5, respect / 100f, moodColor);
        g.fill(x + Math.round(w * 0.2f), y - 1, x + Math.round(w * 0.2f) + 1, y + 6, 0xFF000000);
        g.fill(x + Math.round(w * 0.7f), y - 1, x + Math.round(w * 0.7f) + 1, y + 6, 0xFF000000);
        hits.add(new Hit(x, y - 11, w, 17, null, List.of(Component.translatable("screen.noirm.ally.respect.tip"))));
        y += 11;
        // Health and chakra, side by side.
        int half = (w - 8) / 2;
        resource(g, x, y, half, "screen.noirm.ally.health_label", info.health(), info.maxHealth(), 0xFFE04848);
        resource(g, x + half + 8, y, half, "screen.noirm.ally.chakra_label", info.extra().chakra(), info.extra().maxChakra(), 0xFF4A90F0);
        y += 20;
        section(g, x, y, w, Component.translatable("screen.noirm.ally.training"));
        y += 12;
        // The four stats: value, XP bar and how far to the next point.
        int nameW = 0;
        for (Stat stat : Stat.values()) {
            nameW = Math.max(nameW, font.width(Component.translatable(statKey(stat))));
        }
        nameW = Math.min(nameW, 80);
        for (Stat stat : Stat.values()) {
            int i = stat.ordinal();
            g.drawString(font, fit(Component.translatable(statKey(stat)).getString(), nameW), x, y, STAT_COLORS[i], false);
            String value = String.valueOf(Math.round(info.stats()[i]));
            g.drawString(font, value, x + nameW + 6, y, PAPER, false);
            int barX = x + nameW + 6 + 24;
            String pct = Math.round(info.progress()[i] * 100) + "%";
            int barW = w - (barX - x) - font.width("100%") - 6;
            bar(g, barX, y + 2, barW, 5, info.progress()[i], STAT_COLORS[i]);
            g.drawString(font, pct, x + w - font.width(pct), y, DIM, false);
            hits.add(new Hit(x, y - 1, w, 11, null, List.of(Component.translatable(statKey(stat) + ".desc"),
                    Component.translatable("screen.noirm.ally.xp_next", pct).withStyle(s -> s.withColor(DIM)))));
            y += 12;
        }
        y += 2;
        section(g, x, y, w, Component.translatable("screen.noirm.ally.now"));
        y += 12;
        Order order = Order.values()[Mth.clamp(info.order(), 0, Order.values().length - 1)];
        Component doing = Component.translatable(order.key() + ".doing");
        int job = Mth.clamp(info.extra().occupation(), 0, Occupation.values().length - 1);
        if (job > 0) {
            doing = Component.translatable("screen.noirm.ally.doing_job", doing, Component.translatable(Occupation.values()[job].key()));
        }
        g.drawString(font, fit(doing.getString(), w), x, y, Tab.ORDERS.accent, false);
        y += 11;
        AllyPackets.Lesson lesson = info.extra().lesson();
        if (lesson.nature() >= 0) {
            Nature nature = Nature.values()[lesson.nature()];
            Component text = Component.translatable("screen.noirm.ally.lesson.short", Component.translatable(nature.translationKey()),
                    Math.round(100 * lesson.seconds() / lesson.needed()));
            g.drawString(font, fit(text.getString(), w), x, y, 0xFF000000 | nature.color(), false);
        } else {
            Component tactics = Component.translatable("screen.noirm.ally.tactics_summary",
                    Component.translatable(Tactics.Range.values()[info.extra().range()].key()),
                    Component.translatable(Tactics.JutsuUse.values()[info.extra().jutsuUse()].key()));
            g.drawString(font, fit(tactics.getString(), w), x, y, DIM, false);
        }
    }

    private void resource(GuiGraphics g, int x, int y, int w, String key, float value, float max, int color) {
        Component label = Component.translatable(key);
        String numbers = Math.round(value) + "/" + Math.round(max);
        g.drawString(font, fit(label.getString(), w - font.width(numbers) - 4), x, y, DIM, false);
        g.drawString(font, numbers, x + w - font.width(numbers), y, PAPER, false);
        bar(g, x, y + 10, w, 5, value / Math.max(1f, max), color);
    }

    private void renderModel(GuiGraphics g, int x, int y, int scale, float yaw) {
        SparringNinja ally = menu.ally();
        float bodyRot = ally.yBodyRot;
        float yRot = ally.getYRot();
        float xRot = ally.getXRot();
        float headRotO = ally.yHeadRotO;
        float headRot = ally.yHeadRot;
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX(-8f * Mth.DEG_TO_RAD);
        pose.mul(camera);
        ally.yBodyRot = 180f + yaw;
        ally.setYRot(180f + yaw);
        ally.setXRot(0f);
        ally.yHeadRot = ally.getYRot();
        ally.yHeadRotO = ally.getYRot();
        InventoryScreen.renderEntityInInventory(g, x, y, scale, pose, camera, ally);
        ally.yBodyRot = bodyRot;
        ally.setYRot(yRot);
        ally.setXRot(xRot);
        ally.yHeadRotO = headRotO;
        ally.yHeadRot = headRot;
    }

    // ---------------------------------------------------------------- Gear

    private static final String[] SLOT_KEYS = {"head", "chest", "legs", "feet", "mainhand", "offhand"};

    private void drawGear(GuiGraphics g, int mouseX, int mouseY) {
        int y0 = topPos + 46;
        int bottom = topPos + H - 8;
        // Left: their gear.
        int lx = leftPos + 8;
        int lw = 178;
        box(g, lx, y0, lw, bottom - y0);
        g.drawString(font, fit(Component.translatable("screen.noirm.ally.gear.theirs").getString(), lw - 10), lx + 6, y0 + 5, Tab.GEAR.accent, false);
        int modelX = lx + lw - 24;
        int textW = modelX - 26 - (leftPos + AllyMenu.EQUIP_X + 22);
        for (int i = 0; i < AllyMenu.SLOTS.length; i++) {
            var slot = menu.slots.get(i);
            int sx = leftPos + slot.x;
            int sy = topPos + slot.y;
            slotFrame(g, sx, sy, i >= 4 ? Tab.JUTSU.accent : Tab.GEAR.accent);
            int tx = sx + 22;
            g.drawString(font, fit(Component.translatable("screen.noirm.ally.slot." + SLOT_KEYS[i]).getString(), textW), tx, sy, DIM, false);
            var stack = slot.getItem();
            String item = stack.isEmpty() ? Component.translatable("screen.noirm.ally.slot.empty").getString() : stack.getHoverName().getString();
            g.drawString(font, fit(item, textW), tx, sy + 9, stack.isEmpty() ? FAINT : PAPER, false);
        }
        renderModel(g, modelX, topPos + AllyMenu.EQUIP_Y + 92, 34, modelYaw);
        // Moveset: the weapon in hand overrides their own style.
        int y = topPos + AllyMenu.EQUIP_Y + 6 * AllyMenu.EQUIP_STEP + 1;
        String weapon = info.extra().weaponStyle();
        Component moveset = weapon.isEmpty()
                ? Component.translatable("screen.noirm.ally.moveset.own", Component.translatable("style.noirm." + info.style()))
                : Component.translatable("screen.noirm.ally.moveset.weapon", Component.translatable("style.noirm." + weapon));
        g.drawString(font, fit(moveset.getString(), lw - 12), lx + 6, y, weapon.isEmpty() ? DIM : Tab.JUTSU.accent, false);
        hits.add(new Hit(lx + 6, y - 1, lw - 12, 10, null, List.of(Component.translatable("screen.noirm.ally.moveset.tip"))));
        // Outfit chooser.
        y += 12;
        chooser(g, mouseX, mouseY, lx + 6, y, lw - 12, Component.translatable("screen.noirm.ally.outfit"),
                Component.literal(com.noirm.entity.NinjaLook.label(info.outfit())),
                () -> send(AllyPackets.Action.OUTFIT_PREV, ""), () -> send(AllyPackets.Action.OUTFIT_NEXT, ""), Tab.GEAR.accent);

        // Right: your inventory.
        int rx = leftPos + AllyMenu.INVENTORY_X - 6;
        int rw = leftPos + W - 8 - rx;
        box(g, rx, y0, rw, bottom - y0);
        g.drawString(font, fit(Component.translatable("screen.noirm.ally.gear.yours").getString(), rw - 10), rx + 6, y0 + 5, Tab.GEAR.accent, false);
        for (int i = AllyMenu.SLOTS.length; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            slotFrame(g, leftPos + slot.x, topPos + slot.y, 0);
        }
        int hy = topPos + AllyMenu.INVENTORY_Y + 58 + 22;
        wrap(g, Component.translatable("screen.noirm.ally.gear.help"), rx + 6, hy, rw - 12, bottom - 3, FAINT);
    }

    private void slotFrame(GuiGraphics g, int x, int y, int accent) {
        g.fill(x - 1, y - 1, x + 17, y + 17, accent == 0 ? 0xFF2A2631 : darken(accent, 0.45f));
        g.fill(x, y, x + 16, y + 16, 0xFF0E0D12);
    }

    // ---------------------------------------------------------------- Jutsu

    private void drawJutsu(GuiGraphics g, int mouseX, int mouseY) {
        int y0 = topPos + 46;
        int bottom = topPos + H - 8;
        int x0 = leftPos + 8;
        // The loadout: four cards; click one to take it off.
        Component loadoutLabel = Component.translatable("screen.noirm.ally.loadout");
        int labelW = Math.min(70, font.width(loadoutLabel));
        g.drawString(font, fit(loadoutLabel.getString(), labelW), x0, y0 + 7, Tab.JUTSU.accent, false);
        int cardsX = x0 + labelW + 8;
        int cw = (leftPos + W - 8 - cardsX - 3 * 4) / SparringNinja.LOADOUT_SLOTS;
        for (int i = 0; i < SparringNinja.LOADOUT_SLOTS; i++) {
            int cx = cardsX + i * (cw + 4);
            boolean filled = i < info.loadout().size();
            boolean hover = in(mouseX, mouseY, cx, y0, cw, 20);
            g.fill(cx, y0, cx + cw, y0 + 20, hover && filled ? CARD_HOVER : CARD);
            g.renderOutline(cx, y0, cw, 20, filled ? darken(Tab.JUTSU.accent, 0.6f) : RULE);
            if (filled) {
                ResourceLocation id = info.loadout().get(i);
                int color = natureColor(id);
                g.fill(cx + 1, y0 + 1, cx + 3, y0 + 19, color);
                g.drawString(font, fit(jutsuName(id), cw - 9), cx + 6, y0 + 6, PAPER, false);
                hits.add(new Hit(cx, y0, cw, 20, () -> send(AllyPackets.Action.EQUIP, id.toString()),
                        List.of(Component.literal(jutsuName(id)), Component.translatable("screen.noirm.ally.loadout.remove").withStyle(s -> s.withColor(DIM)))));
            } else {
                g.drawCenteredString(font, fit(Component.translatable("screen.noirm.ally.loadout.empty").getString(), cw - 6), cx + cw / 2, y0 + 6, FAINT);
            }
        }
        if (info.loadout().isEmpty()) {
            hits.add(new Hit(cardsX, y0, leftPos + W - 8 - cardsX, 20, null, List.of(Component.translatable("screen.noirm.ally.loadout.none"))));
        }

        int ly = y0 + 26;
        int half = (W - 16 - 6) / 2;
        // Left: the jutsu they know.
        box(g, x0, ly, half, bottom - ly);
        Component knownTitle = Component.translatable("screen.noirm.ally.known", info.known().size());
        g.drawString(font, fit(knownTitle.getString(), half - 10), x0 + 5, ly + 4, Tab.JUTSU.accent, false);
        int rowH = 14;
        int listY = ly + 16;
        int rows = (bottom - 3 - listY) / rowH;
        knownScroll = Mth.clamp(knownScroll, 0, Math.max(0, info.known().size() - rows));
        if (info.known().isEmpty()) {
            g.drawString(font, fit(Component.translatable("screen.noirm.ally.jutsu_none").getString(), half - 10), x0 + 5, listY + 2, FAINT, false);
        }
        for (int r = 0; r < rows && knownScroll + r < info.known().size(); r++) {
            int index = knownScroll + r;
            ResourceLocation id = info.known().get(index);
            int ry = listY + r * rowH;
            boolean equipped = info.loadout().contains(id);
            boolean canEquip = equipped || info.loadout().size() < SparringNinja.LOADOUT_SLOTS;
            int rx = x0 + 3;
            int rw = half - 6 - (rows < info.known().size() ? 4 : 0);
            boolean hover = in(mouseX, mouseY, rx, ry, rw, rowH - 1);
            g.fill(rx, ry, rx + rw, ry + rowH - 1, equipped ? darken(Tab.JUTSU.accent, 0.22f) : hover ? CARD_HOVER : CARD);
            natureBadge(g, rx + 2, ry + 2, id);
            int stars = index < info.mastery().size() ? info.mastery().get(index) : 0;
            String starText = "★" + stars;
            int toggleW = 12;
            int starX = rx + rw - toggleW - 4 - font.width(starText);
            g.drawString(font, fit(jutsuName(id), starX - (rx + 16) - 4), rx + 16, ry + 3, equipped ? GOLD : PAPER, false);
            g.drawString(font, starText, starX, ry + 3, stars > 0 ? GOLD : FAINT, false);
            int tx = rx + rw - toggleW - 1;
            g.fill(tx, ry + 1, tx + toggleW, ry + rowH - 2, equipped ? Tab.JUTSU.accent : canEquip ? 0xFF2E2936 : 0xFF1A181E);
            g.drawCenteredString(font, equipped ? "✔" : "+", tx + toggleW / 2 + 1, ry + 3, equipped ? 0xFF1A0A0E : canEquip ? PAPER : FAINT);
            List<Component> tip = new ArrayList<>(jutsuTip(id));
            tip.add(Component.translatable("screen.noirm.ally.mastery", stars).withStyle(s -> s.withColor(GOLD)));
            tip.add(Component.translatable(equipped ? "screen.noirm.ally.loadout.click_remove"
                    : canEquip ? "screen.noirm.ally.loadout.click_add" : "screen.noirm.ally.loadout.full").withStyle(s -> s.withColor(DIM)));
            hits.add(new Hit(rx, ry, rw, rowH - 1, canEquip ? () -> send(AllyPackets.Action.EQUIP, id.toString()) : null, tip));
        }
        scrollbar(g, x0 + half - 5, listY, rows * rowH, knownScroll, rows, info.known().size());

        // Right: what you could teach them, and why not.
        int tx0 = x0 + half + 6;
        box(g, tx0, ly, half, bottom - ly);
        Component teachTitle = Component.translatable("screen.noirm.ally.teach_from_you");
        int qx = tx0 + half - 14;
        // "Teach all": everything they can learn now, and the natures the rest need.
        Component allLabel = Component.translatable("screen.noirm.ally.teach_all");
        int aw = font.width(allLabel) + 8;
        int ax = qx - 3 - aw;
        boolean anyTeachable = info.teachable().stream().anyMatch(t -> t.blocker().isEmpty() || t.blocker().equals(AllyPackets.VIA_NATURE));
        pill(g, ax, ly + 2, aw, 12, allLabel, GREEN, anyTeachable, false, mouseX, mouseY);
        hits.add(new Hit(ax, ly + 2, aw, 12, anyTeachable ? () -> send(AllyPackets.Action.TEACH_ALL, "") : null,
                List.of(Component.translatable("screen.noirm.ally.teach_all.tip"))));
        g.drawString(font, fit(teachTitle.getString(), ax - (tx0 + 5) - 4), tx0 + 5, ly + 4, Tab.JUTSU.accent, false);
        g.fill(qx, ly + 3, qx + 10, ly + 13, 0xFF2E2936);
        g.drawCenteredString(font, "?", qx + 5, ly + 4, GOLD);
        hits.add(new Hit(qx, ly + 3, 10, 10, null, List.of(Component.translatable("screen.noirm.ally.teach.rules.title").withStyle(s -> s.withColor(GOLD)),
                Component.translatable("screen.noirm.ally.teach.rules"))));
        int teachRowH = 20;
        int teachRows = (bottom - 3 - listY) / teachRowH;
        List<AllyPackets.Teachable> teachable = info.teachable();
        teachScroll = Mth.clamp(teachScroll, 0, Math.max(0, teachable.size() - teachRows));
        if (teachable.isEmpty()) {
            wrap(g, Component.translatable("screen.noirm.ally.teach_none"), tx0 + 5, listY + 2, half - 10, bottom - 3, FAINT);
        }
        for (int r = 0; r < teachRows && teachScroll + r < teachable.size(); r++) {
            AllyPackets.Teachable t = teachable.get(teachScroll + r);
            int ry = listY + r * teachRowH;
            int rx = tx0 + 3;
            int rw = half - 6 - (teachRows < teachable.size() ? 4 : 0);
            boolean viaNature = t.blocker().equals(AllyPackets.VIA_NATURE);
            boolean ok = t.blocker().isEmpty() || viaNature;
            boolean hover = in(mouseX, mouseY, rx, ry, rw, teachRowH - 1);
            g.fill(rx, ry, rx + rw, ry + teachRowH - 1, hover ? CARD_HOVER : CARD);
            natureBadge(g, rx + 2, ry + 2, t.id());
            String rank = t.rank();
            int rankW = font.width(rank) + 4;
            g.fill(rx + 15, ry + 2, rx + 15 + rankW, ry + 11, rankColor(rank));
            g.drawString(font, rank, rx + 17, ry + 3, 0xFF15121A, false);
            Component button = Component.translatable("screen.noirm.ally.teach");
            int bw = font.width(button) + 8;
            int bx = rx + rw - bw - 1;
            int nameX = rx + 15 + rankW + 4;
            g.drawString(font, fit(jutsuName(t.id()), (ok ? bx : rx + rw) - nameX - 3), nameX, ry + 3, ok ? PAPER : DIM, false);
            if (ok) {
                pill(g, bx, ry + 2, bw, 14, button, viaNature ? GOLD : GREEN, true, false, mouseX, mouseY);
                hits.add(new Hit(bx, ry + 2, bw, 14, () -> send(AllyPackets.Action.TEACH, t.id().toString()),
                        List.of(Component.translatable(viaNature ? "screen.noirm.ally.teach.via_nature.tip" : "screen.noirm.ally.teach.tip"))));
                g.drawString(font, fit(Component.translatable(viaNature ? AllyPackets.VIA_NATURE : "screen.noirm.ally.teach.ok").getString(),
                        bx - (rx + 15) - 3), rx + 15, ry + 11, viaNature ? 0xFFB89A50 : 0xFF5E9A6C, false);
            } else if (t.blocker().equals(AllyPackets.PENDING)) {
                g.drawString(font, fit("⧗ " + Component.translatable(t.blocker()).getString(), rw - 17), rx + 15, ry + 11, 0xFFB89A50, false);
            } else {
                g.drawString(font, fit("✕ " + Component.translatable(t.blocker()).getString(), rw - 17), rx + 15, ry + 11, 0xFFB07070, false);
            }
            List<Component> tip = new ArrayList<>(jutsuTip(t.id()));
            if (!ok) {
                tip.add(Component.translatable(t.blocker()).withStyle(s -> s.withColor(0xFFE08080)));
            }
            hits.add(new Hit(rx, ry, rw, teachRowH - 1, null, tip));
        }
        scrollbar(g, tx0 + half - 5, listY, teachRows * teachRowH, teachScroll, teachRows, teachable.size());
    }

    private List<Component> jutsuTip(ResourceLocation id) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(jutsuName(id)).withStyle(s -> s.withColor(natureColor(id))));
        JutsuCatalogPacket.Entry entry = ClientJutsuCatalog.get(id.toString());
        if (entry != null) {
            Component nature = entry.nature() >= 0 ? Component.translatable(Nature.values()[entry.nature()].translationKey())
                    : Component.translatable("screen.noirm.ally.universal");
            tip.add(Component.translatable("screen.noirm.ally.jutsu_line", entry.rank(), nature, Math.round(entry.chakraCost()))
                    .withStyle(s -> s.withColor(DIM)));
        }
        return tip;
    }

    private void natureBadge(GuiGraphics g, int x, int y, ResourceLocation id) {
        JutsuCatalogPacket.Entry entry = ClientJutsuCatalog.get(id.toString());
        int color = natureColor(id);
        g.fill(x, y, x + 10, y + 9, darken(color, 0.35f));
        g.renderOutline(x, y, 10, 9, color);
        String glyph = entry != null && entry.nature() >= 0 ? Nature.values()[entry.nature()].kanji().substring(0, 1) : "·";
        g.pose().pushPose();
        g.pose().translate(x + 5, y + 1, 0);
        g.pose().scale(0.8f, 0.8f, 1f);
        g.drawCenteredString(font, glyph, 0, 0, color);
        g.pose().popPose();
    }

    private static int natureColor(ResourceLocation id) {
        JutsuCatalogPacket.Entry entry = ClientJutsuCatalog.get(id.toString());
        return entry != null && entry.nature() >= 0 ? 0xFF000000 | Nature.values()[entry.nature()].color() : 0xFFCFC8D8;
    }

    private static int rankColor(String rank) {
        return switch (rank) {
            case "S" -> 0xFFE05A5A;
            case "A" -> 0xFFE8A050;
            case "B" -> 0xFFE8D060;
            case "C" -> 0xFF8FD08A;
            default -> 0xFF9AB8D8;
        };
    }

    private static String jutsuName(ResourceLocation id) {
        return Component.translatable(Sharingan.jutsuKey(id)).getString();
    }

    // ---------------------------------------------------------------- Natures

    private void drawNatures(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int y0 = topPos + 46;
        int bottom = topPos + H - 8;
        int tx = leftPos + 8;
        int tw = 182;
        int th = bottom - y0;
        box(g, tx, y0, tw, th);
        byte[] status = info.extra().natureStatus();
        int owned = 0;
        int teachable = 0;
        for (Nature nature : Nature.values()) {
            int s = statusOf(status, nature);
            owned += s == AllyPackets.NATURE_OWNED ? 1 : 0;
            teachable += s == AllyPackets.NATURE_TEACHABLE ? 1 : 0;
        }
        String counter = owned + " / " + Nature.values().length;
        g.drawString(font, counter, tx + tw - 5 - font.width(counter), y0 + 4, DIM, false);
        g.drawString(font, fit(Component.translatable("screen.noirm.ally.natures.title").getString(), tw - 16 - font.width(counter)), tx + 5, y0 + 4,
                Tab.NATURES.accent, false);
        // Legend.
        int lx = tx + 5;
        lx = legend(g, lx, y0 + 15, Tab.NATURES.accent, Component.translatable("screen.noirm.ally.natures.theirs"), tx + tw - 5);
        lx = legend(g, lx, y0 + 15, GOLD, Component.translatable("screen.noirm.ally.natures.teachable"), tx + tw - 5);
        legend(g, lx, y0 + 15, 0xFF505058, Component.translatable("screen.noirm.ally.natures.locked"), tx + tw - 5);

        // The tree: basics on a pentagon, mixes between their parts, Dust in the middle, Yin and Yang below.
        float centerX = tx + tw / 2f;
        float centerY = y0 + 88;
        float radius = 52;
        Map<Nature, float[]> pos = new EnumMap<>(Nature.class);
        Nature[] ring = {Nature.FIRE, Nature.WIND, Nature.LIGHTNING, Nature.EARTH, Nature.WATER};
        for (int i = 0; i < ring.length; i++) {
            double angle = -Math.PI / 2 + i * 2 * Math.PI / 5;
            pos.put(ring[i], new float[]{centerX + (float) Math.cos(angle) * radius, centerY + (float) Math.sin(angle) * radius});
        }
        pos.put(Nature.YIN, new float[]{tx + 18, y0 + th - 14});
        pos.put(Nature.YANG, new float[]{tx + tw - 18, y0 + th - 14});
        pos.put(Nature.YIN_YANG, new float[]{centerX, y0 + th - 14});
        for (Nature nature : Nature.combined()) {
            if (!nature.isElemental()) {
                continue;
            }
            if (nature.tier() == 2) {
                float x = 0;
                float y = 0;
                for (Nature part : nature.components()) {
                    x += pos.get(part)[0] / 2f;
                    y += pos.get(part)[1] / 2f;
                }
                float dx = x - centerX;
                float dy = y - centerY;
                float dist = (float) Math.hypot(dx, dy);
                float wanted = dist > radius * 0.6f ? dist * 0.86f : radius * 0.5f;
                pos.put(nature, new float[]{centerX + dx / dist * wanted, centerY + dy / dist * wanted});
            } else {
                pos.put(nature, new float[]{centerX, centerY});
            }
        }
        for (Nature nature : Nature.combined()) {
            float[] to = pos.get(nature);
            int s = statusOf(status, nature);
            for (Nature part : nature.components()) {
                float[] from = pos.get(part);
                dotted(g, from[0], from[1], to[0], to[1], s == AllyPackets.NATURE_OWNED ? 0xFF000000 | nature.color()
                        : s == AllyPackets.NATURE_TEACHABLE || s == AllyPackets.NATURE_LEARNING ? 0xFF8A7A3E : 0xFF35313B);
            }
        }
        float time = (minecraft.player == null ? 0 : minecraft.player.tickCount) + partialTick;
        float pulse = 0.5f + 0.5f * Mth.sin(time * 0.25f);
        for (Nature nature : Nature.values()) {
            float[] p = pos.get(nature);
            int px = Math.round(p[0]);
            int py = Math.round(p[1]);
            int r = nature.isBasic() ? 11 : nature.tier() == 3 ? 10 : 8;
            int s = statusOf(status, nature);
            int color = 0xFF000000 | nature.color();
            int border = switch (s) {
                case AllyPackets.NATURE_OWNED -> color;
                case AllyPackets.NATURE_LEARNING -> lerp(darken(color, 0.4f), color, pulse);
                case AllyPackets.NATURE_TEACHABLE -> lerp(0xFF8A7A3E, GOLD, pulse);
                case AllyPackets.NATURE_BUSY -> 0xFF6E6240; // teachable once the current lesson ends
                case AllyPackets.NATURE_QUEUED -> lerp(0xFF6E6240, 0xFFB8A060, pulse * 0.5f);
                default -> 0xFF47434E;
            };
            boolean chosen = nature == selected;
            hexagon(g, px, py, r + (chosen ? 2 : 0), chosen ? PAPER : border);
            if (chosen) {
                hexagon(g, px, py, r, border);
            }
            hexagon(g, px, py, r - 2, s == AllyPackets.NATURE_OWNED ? 0xFF2A2630 : 0xFF141217);
            g.drawCenteredString(font, nature == Nature.YIN_YANG ? "☯" : nature.kanji().substring(0, 1), px, py - 4,
                    s == AllyPackets.NATURE_OWNED || s == AllyPackets.NATURE_LEARNING ? color : s == AllyPackets.NATURE_TEACHABLE ? GOLD : 0xFF5E5866);
            if (s == AllyPackets.NATURE_TEACHABLE) {
                g.drawString(font, "+", px + r - 2, py - r - 3, GOLD, false);
            } else if (s == AllyPackets.NATURE_LEARNING) {
                g.drawString(font, "…", px + r - 3, py - r - 3, color, false);
            } else if (s == AllyPackets.NATURE_QUEUED) {
                int place = queuePlace(nature);
                g.drawString(font, "#" + place, px + r - 3, py - r - 3, 0xFFB8A060, false);
            }
            if (Math.abs(mouseX - px) <= r && Math.abs(mouseY - py) <= r) {
                hovered = nature;
            }
            hits.add(new Hit(px - r, py - r, r * 2, r * 2, () -> selected = nature, null));
        }

        // Right: details, then the lesson.
        int sx = tx + tw + 6;
        int sw = leftPos + W - 8 - sx;
        box(g, sx, y0, sw, th);
        int lessonTop = bottom - 112;
        Nature shown = hovered != null ? hovered : selected;
        int y = y0 + 5;
        int x = sx + 6;
        int w = sw - 12;
        if (shown == null) {
            wrap(g, Component.translatable("screen.noirm.ally.natures.hint"), x, y, w, lessonTop - 4, DIM);
        } else {
            y = natureDetails(g, shown, statusOf(status, shown), x, y, w, lessonTop - 4, mouseX, mouseY);
        }
        g.fill(sx + 4, lessonTop - 2, sx + sw - 4, lessonTop - 1, RULE);
        drawLesson(g, x, lessonTop + 2, w, bottom - 4, mouseX, mouseY);
    }

    private int natureDetails(GuiGraphics g, Nature nature, int s, int x, int y, int w, int maxY, int mouseX, int mouseY) {
        int color = 0xFF000000 | nature.color();
        g.drawString(font, fit(Component.translatable(nature.translationKey()).getString(), w), x, y, color, false);
        y += 11;
        String statusKey = switch (s) {
            case AllyPackets.NATURE_OWNED -> "owned";
            case AllyPackets.NATURE_LEARNING -> "learning";
            case AllyPackets.NATURE_TEACHABLE -> "teachable";
            case AllyPackets.NATURE_NEEDS_PARTS -> "parts";
            case AllyPackets.NATURE_BUSY -> "busy";
            case AllyPackets.NATURE_QUEUED -> "queued";
            default -> "you_lack";
        };
        int statusColor = s == AllyPackets.NATURE_OWNED ? GREEN : s == AllyPackets.NATURE_TEACHABLE || s == AllyPackets.NATURE_LEARNING
                || s == AllyPackets.NATURE_QUEUED ? GOLD : 0xFFB07070;
        if (s == AllyPackets.NATURE_OWNED) {
            float affinity = nature.ordinal() < info.extra().natureAffinity().length ? info.extra().natureAffinity()[nature.ordinal()] : 0f;
            String strength = affinity >= 1.5f ? "primary" : affinity >= 1f ? "secondary" : "trained";
            y = wrap(g, Component.translatable("screen.noirm.ally.nature.status.owned", Component.translatable("nature.noirm.strength." + strength)),
                    x, y, w, maxY, statusColor) + 2;
        } else {
            y = wrap(g, Component.translatable("screen.noirm.ally.nature.status." + statusKey), x, y, w, maxY, statusColor) + 2;
        }
        if (!nature.isBasic() && s != AllyPackets.NATURE_OWNED) {
            for (Nature part : nature.components()) {
                if (y + 9 > maxY) {
                    break;
                }
                boolean has = statusOf(info.extra().natureStatus(), part) == AllyPackets.NATURE_OWNED;
                g.drawString(font, fit((has ? "✔ " : "✘ ") + Component.translatable(part.translationKey()).getString(), w - 4), x + 4, y,
                        has ? GREEN : 0xFFB07070, false);
                y += 10;
            }
        }
        if (s == AllyPackets.NATURE_QUEUED && y + 14 <= maxY) {
            Component remove = Component.translatable("screen.noirm.ally.nature.dequeue");
            pill(g, x, y + 1, w, 14, remove, RED, true, false, mouseX, mouseY);
            hits.add(new Hit(x, y + 1, w, 14, () -> send(AllyPackets.Action.NATURE_DEQUEUE, nature.getSerializedName()), null));
            y += 17;
        }
        if (s == AllyPackets.NATURE_TEACHABLE && y + 14 <= maxY) {
            float days = NatureTeaching.secondsNeeded(nature) / 1200f;
            boolean busy = info.extra().lesson().nature() >= 0;
            Component start = Component.translatable(busy ? "screen.noirm.ally.nature.queue" : "screen.noirm.ally.nature.start");
            pill(g, x, y + 1, w, 14, start, GOLD, true, false, mouseX, mouseY);
            hits.add(new Hit(x, y + 1, w, 14, () -> send(AllyPackets.Action.NATURE_TEACH, nature.getSerializedName()),
                    List.of(Component.translatable("screen.noirm.ally.nature.start.tip", String.format(Locale.ROOT, "%.1f", days)))));
            y += 17;
        }
        return y;
    }

    private void drawLesson(GuiGraphics g, int x, int y, int w, int bottom, int mouseX, int mouseY) {
        AllyPackets.Lesson lesson = info.extra().lesson();
        if (lesson.nature() < 0) {
            g.drawString(font, fit(Component.translatable("screen.noirm.ally.lesson.title").getString(), w), x, y, Tab.NATURES.accent, false);
            wrap(g, Component.translatable("screen.noirm.ally.lesson.none"), x, y + 11, w, bottom, FAINT);
            return;
        }
        Nature nature = Nature.values()[lesson.nature()];
        int color = 0xFF000000 | nature.color();
        Component title = Component.translatable("screen.noirm.ally.lesson.teaching", Component.translatable(nature.translationKey()));
        g.drawString(font, fit(title.getString(), w), x, y, color, false);
        y += 11;
        float fraction = Mth.clamp(lesson.seconds() / Math.max(1f, lesson.needed()), 0f, 1f);
        String pct = Math.round(fraction * 100) + "%";
        bar(g, x, y + 1, w - font.width("100%") - 4, 6, fraction, color);
        g.drawString(font, pct, x + w - font.width(pct), y, PAPER, false);
        y += 11;
        // When it'll be done, or why it's paused.
        Component when;
        int whenColor;
        if (lesson.rate() > 0f) {
            when = eta((lesson.needed() - lesson.seconds()) / lesson.rate());
            whenColor = PAPER;
        } else {
            String why = !lesson.has(AllyPackets.PACE_TEACHER_HAS) ? "teacher" : "far";
            when = Component.translatable("screen.noirm.ally.lesson.paused." + why, (int) NatureTeaching.TEACH_RANGE);
            whenColor = 0xFFE0A050;
        }
        int stopY = bottom - 14;
        int queueY = stopY - 11;
        y = wrap(g, when, x, y, w, queueY - 41, whenColor) + 1;
        // What's helping.
        y = factor(g, x, y, w, lesson.has(AllyPackets.PACE_STUDYING), Component.translatable("screen.noirm.ally.lesson.f.order"), queueY);
        y = factor(g, x, y, w, lesson.has(AllyPackets.PACE_FIGHTING), Component.translatable("screen.noirm.ally.lesson.f.fight", "×" + NatureTeaching.FIGHT_BONUS), queueY);
        y = factor(g, x, y, w, lesson.has(AllyPackets.PACE_MASTERED), Component.translatable("screen.noirm.ally.lesson.f.mastered",
                NatureTeaching.MASTERED_STARS, "×" + NatureTeaching.MASTERED_BONUS), queueY);
        factor(g, x, y, w, lesson.has(AllyPackets.PACE_TOGETHER), Component.translatable("screen.noirm.ally.lesson.f.together",
                "×" + NatureTeaching.MEDITATE_TOGETHER_BONUS), queueY);
        // What's next.
        byte[] queue = info.extra().queue();
        if (queue.length > 0) {
            StringBuilder names = new StringBuilder();
            for (byte n : queue) {
                if (n >= 0 && n < Nature.values().length) {
                    names.append(names.length() > 0 ? ", " : "").append(Component.translatable(Nature.values()[n].translationKey()).getString());
                }
            }
            g.drawString(font, fit(Component.translatable("screen.noirm.ally.lesson.next", names.toString()).getString(), w), x, queueY, DIM, false);
        }
        Component stop = Component.translatable("screen.noirm.ally.lesson.stop");
        pill(g, x, stopY, w, 13, stop, RED, true, false, mouseX, mouseY);
        hits.add(new Hit(x, stopY, w, 13, () -> send(AllyPackets.Action.NATURE_STOP, ""), List.of(Component.translatable("screen.noirm.ally.lesson.stop.tip"))));
    }

    private int factor(GuiGraphics g, int x, int y, int w, boolean on, Component text, int maxY) {
        if (y + 9 > maxY) {
            return y;
        }
        g.drawString(font, fit((on ? "✔ " : "· ") + text.getString(), w), x, y, on ? GREEN : FAINT, false);
        return y + 10;
    }

    /** "About 1 day and 4 hours (≈ 34 min of play)": game time and real minutes. */
    private static Component eta(float seconds) {
        int s = Math.max(1, Mth.ceil(seconds));
        int days = s / 1200;
        int hours = Math.round((s % 1200) / 50f);
        if (hours >= 24) {
            days++;
            hours = 0;
        }
        Component d = Component.translatable(days == 1 ? "screen.noirm.ally.eta.day" : "screen.noirm.ally.eta.days", days);
        Component h = Component.translatable(hours == 1 ? "screen.noirm.ally.eta.hour" : "screen.noirm.ally.eta.hours", Math.max(1, hours));
        Component game = days == 0 ? h : hours == 0 ? d : Component.translatable("screen.noirm.ally.eta.and", d, h);
        return Component.translatable("screen.noirm.ally.lesson.eta", game, Mth.ceil(s / 60f));
    }

    private int legend(GuiGraphics g, int x, int y, int color, Component text, int maxX) {
        if (x + 12 > maxX) {
            return x;
        }
        g.fill(x, y + 1, x + 6, y + 7, color);
        String label = fit(text.getString(), maxX - x - 9);
        g.drawString(font, label, x + 9, y, DIM, false);
        return x + 9 + font.width(label) + 8;
    }

    private int queuePlace(Nature nature) {
        byte[] queue = info.extra().queue();
        for (int i = 0; i < queue.length; i++) {
            if (queue[i] == nature.ordinal()) {
                return i + 1;
            }
        }
        return 0;
    }

    private static int statusOf(byte[] status, Nature nature) {
        return nature.ordinal() < status.length ? status[nature.ordinal()] : AllyPackets.NATURE_YOU_LACK;
    }

    // ---------------------------------------------------------------- Orders

    private static final String[] ORDER_GLYPHS = {"➜", "■", "⚔", "☯", "◆", "↻"};
    private static final String[] JOB_GLYPHS = {"·", "◆", "⚔", "◎", "✚", "✿"};

    private void drawOrders(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = leftPos + 8;
        int y = topPos + 46;
        int full = W - 16;
        int accent = Tab.ORDERS.accent;
        // Come / Fall back sit at the right of the section line.
        Component come = Component.translatable("screen.noirm.ally.come");
        Component back = Component.translatable("screen.noirm.ally.fall_back");
        int bw = font.width(back) + 12;
        int cw0 = font.width(come) + 12;
        int bx = x0 + full - bw;
        int cx0 = bx - 4 - cw0;
        section(g, x0, y, cx0 - x0 - 6, Component.translatable("screen.noirm.ally.orders"));
        pill(g, cx0, y - 3, cw0, 13, come, accent, true, false, mouseX, mouseY);
        hits.add(new Hit(cx0, y - 3, cw0, 13, () -> send(AllyPackets.Action.COME, ""), List.of(come,
                Component.translatable("screen.noirm.ally.come.tip").withStyle(s -> s.withColor(DIM)))));
        pill(g, bx, y - 3, bw, 13, back, accent, true, false, mouseX, mouseY);
        hits.add(new Hit(bx, y - 3, bw, 13, () -> send(AllyPackets.Action.FALL_BACK, ""), List.of(back,
                Component.translatable("screen.noirm.ally.fall_back.tip", CrewBrain.FALL_BACK_TICKS / 20).withStyle(s -> s.withColor(DIM)))));
        y += 13;
        Order[] orders = Order.SHOWN;
        int perRow = 3;
        int gap = 4;
        int cw = (full - gap * (perRow - 1)) / perRow;
        int ch = 36;
        for (int i = 0; i < orders.length; i++) {
            Order order = orders[i];
            int cx = x0 + (i % perRow) * (cw + gap);
            int cy = y + (i / perRow) * (ch + gap);
            boolean active = info.order() == order.ordinal();
            card(g, mouseX, mouseY, cx, cy, cw, ch, ORDER_GLYPHS[order.ordinal() % ORDER_GLYPHS.length], Component.translatable(order.key()),
                    Component.translatable(order.key() + ".desc"), active, accent);
            List<Component> tip = new ArrayList<>(List.of(Component.translatable(order.key()),
                    Component.translatable(order.key() + ".desc").withStyle(s -> s.withColor(DIM))));
            if (order.usesPost()) {
                tip.add(Component.translatable("screen.noirm.ally.order.post_tip").withStyle(s -> s.withColor(FAINT)));
            }
            hits.add(new Hit(cx, cy, cw, ch, () -> send(AllyPackets.Action.ORDER, order.name()), tip));
        }
        y += ((orders.length + perRow - 1) / perRow) * (ch + gap) + 4;
        section(g, x0, y, full, Component.translatable("screen.noirm.ally.tactics"));
        y += 13;
        Component behaviourLabel = Component.translatable("screen.noirm.ally.behaviour");
        Component rangeLabel = Component.translatable("screen.noirm.ally.range");
        Component jutsuLabel = Component.translatable("screen.noirm.ally.jutsu_use");
        int labelW = Math.min(90, Math.max(font.width(behaviourLabel), Math.max(font.width(rangeLabel), font.width(jutsuLabel))) + 8);
        int segX = x0 + 4 + labelW;
        int segW = x0 + full - segX;
        g.drawString(font, fit(behaviourLabel.getString(), labelW - 4), x0 + 4, y + 3, DIM, false);
        Behaviour[] behaviours = Behaviour.values();
        for (int i = 0; i < behaviours.length; i++) {
            Behaviour behaviour = behaviours[i];
            segment(g, mouseX, mouseY, segX, y, segW, i, behaviours.length, Component.translatable(behaviour.key()), info.extra().behaviour() == i,
                    () -> send(AllyPackets.Action.BEHAVIOUR, behaviour.name()), Component.translatable(behaviour.key() + ".desc"));
        }
        y += 18;
        g.drawString(font, fit(rangeLabel.getString(), labelW - 4), x0 + 4, y + 3, DIM, false);
        Tactics.Range[] ranges = Tactics.Range.values();
        for (int i = 0; i < ranges.length; i++) {
            Tactics.Range range = ranges[i];
            segment(g, mouseX, mouseY, segX, y, segW, i, ranges.length, Component.translatable(range.key()), info.extra().range() == i,
                    () -> send(AllyPackets.Action.RANGE, range.name()), Component.translatable(range.key() + ".desc"));
        }
        y += 18;
        g.drawString(font, fit(jutsuLabel.getString(), labelW - 4), x0 + 4, y + 3, DIM, false);
        Tactics.JutsuUse[] uses = Tactics.JutsuUse.values();
        for (int i = 0; i < uses.length; i++) {
            Tactics.JutsuUse use = uses[i];
            segment(g, mouseX, mouseY, segX, y, segW, i, uses.length, Component.translatable(use.key()), info.extra().jutsuUse() == i,
                    () -> send(AllyPackets.Action.JUTSU_USE, use.name()), Component.translatable(use.key() + ".desc"));
        }
        y += 18;
        // What the current behaviour means, in a line.
        int b = Mth.clamp(info.extra().behaviour(), 0, behaviours.length - 1);
        g.drawString(font, fit(Component.translatable(behaviours[b].key() + ".desc").getString(), full - 8), x0 + 4, y, FAINT, false);
    }

    /** A clickable card: glyph, title and a wrapped description, lit when active. */
    private void card(GuiGraphics g, int mouseX, int mouseY, int cx, int cy, int cw, int ch, String glyph, Component title, Component desc,
                      boolean active, int accent) {
        boolean hover = in(mouseX, mouseY, cx, cy, cw, ch);
        g.fill(cx, cy, cx + cw, cy + ch, active ? darken(accent, 0.3f) : hover ? CARD_HOVER : CARD);
        g.renderOutline(cx, cy, cw, ch, active ? accent : hover ? FRAME : RULE);
        if (active) {
            g.fill(cx + 1, cy + ch - 2, cx + cw - 1, cy + ch - 1, accent);
        }
        g.drawString(font, glyph, cx + 4, cy + 4, active ? accent : DIM, false);
        int titleX = cx + 6 + font.width(glyph);
        g.drawString(font, fit(title.getString(), cx + cw - 4 - titleX), titleX, cy + 4, active ? PAPER : 0xFFCFC6D6, false);
        wrap(g, desc, cx + 4, cy + 15, cw - 8, cy + ch - 2, active ? 0xFFE0D4C0 : FAINT);
    }

    // ---------------------------------------------------------------- Job

    private void drawJob(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = leftPos + 8;
        int y = topPos + 46;
        int full = W - 16;
        int accent = Tab.JOB.accent;
        section(g, x0, y, full, Component.translatable("screen.noirm.ally.job"));
        y += 12;
        Occupation[] jobs = Occupation.values();
        int perRow = 3;
        int gap = 4;
        int cw = (full - gap * (perRow - 1)) / perRow;
        int ch = 38;
        for (int i = 0; i < jobs.length; i++) {
            Occupation job = jobs[i];
            int cx = x0 + (i % perRow) * (cw + gap);
            int cy = y + (i / perRow) * (ch + gap);
            boolean active = info.extra().occupation() == i;
            card(g, mouseX, mouseY, cx, cy, cw, ch, JOB_GLYPHS[i % JOB_GLYPHS.length], Component.translatable(job.key()),
                    Component.translatable(job.key() + ".desc"), active, accent);
            hits.add(new Hit(cx, cy, cw, ch, () -> send(AllyPackets.Action.OCCUPATION, job.name()), List.of(Component.translatable(job.key()),
                    Component.translatable(job.key() + ".desc").withStyle(s -> s.withColor(DIM)))));
        }
        y += ((jobs.length + perRow - 1) / perRow) * (ch + gap) + 4;
        // Fighting style (their own; a weapon in hand brings its moveset instead).
        section(g, x0, y, full, Component.translatable("screen.noirm.ally.style"));
        y += 12;
        List<String> styles = info.styles();
        int index = styles.indexOf(info.style());
        Runnable prev = null;
        Runnable next = null;
        if (styles.size() > 1) {
            String p = styles.get(Math.floorMod(index - 1, styles.size()));
            String n = styles.get(Math.floorMod(index + 1, styles.size()));
            prev = () -> send(AllyPackets.Action.STYLE, p);
            next = () -> send(AllyPackets.Action.STYLE, n);
        }
        int half = (full - 8) / 2;
        chooser(g, mouseX, mouseY, x0, y, half, null, Component.translatable("style.noirm." + info.style()), prev, next, accent);
        if (!info.extra().weaponStyle().isEmpty()) {
            Component note = Component.translatable("screen.noirm.ally.moveset.weapon", Component.translatable("style.noirm." + info.extra().weaponStyle()));
            g.drawString(font, fit(note.getString(), half), x0 + half + 8, y + 3, Tab.JUTSU.accent, false);
        }
        y += 20;
        // Nature energy (gathered while meditating, for sage mode later) and what they carry.
        section(g, x0, y, full, Component.translatable("screen.noirm.ally.reserves"));
        y += 12;
        resource(g, x0, y, half, "screen.noirm.ally.nature_energy", info.extra().natureEnergy(), CrewBrain.MAX_NATURE_ENERGY, 0xFF7FD36A);
        hits.add(new Hit(x0, y, half, 16, null, List.of(Component.translatable("screen.noirm.ally.nature_energy"),
                Component.translatable("screen.noirm.ally.nature_energy.tip").withStyle(s -> s.withColor(DIM)))));
        Component carried = Component.translatable("screen.noirm.ally.carried", info.extra().carried(), CrewBrain.GATHER_STACKS);
        g.drawString(font, fit(carried.getString(), half), x0 + half + 8, y, info.extra().carried() > 0 ? PAPER : DIM, false);
        if (info.extra().pendingJutsu() > 0) {
            Component pending = Component.translatable("screen.noirm.ally.pending_count", info.extra().pendingJutsu());
            g.drawString(font, fit(pending.getString(), half), x0 + half + 8, y + 10, GOLD, false);
        }
    }

    private void segment(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int i, int count, Component label, boolean active,
                         Runnable click, Component tip) {
        int sw = w / count;
        int sx = x + i * sw;
        int width = i == count - 1 ? w - sw * (count - 1) : sw - 2;
        boolean hover = in(mouseX, mouseY, sx, y, width, 14);
        int accent = Tab.ORDERS.accent;
        g.fill(sx, y, sx + width, y + 14, active ? darken(accent, 0.35f) : hover ? CARD_HOVER : CARD);
        g.renderOutline(sx, y, width, 14, active ? accent : RULE);
        g.drawCenteredString(font, fit(label.getString(), width - 6), sx + width / 2, y + 3, active ? PAPER : DIM);
        hits.add(new Hit(sx, y, width, 14, click, List.of(label, tip.copy().withStyle(s -> s.withColor(DIM)))));
    }

    // ================================================================ shared widgets

    /** "Label  ◀ value ▶": arrows step through choices (null arrows are hidden). */
    private void chooser(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, Component label, Component value,
                         Runnable prev, Runnable next, int accent) {
        int ax = x;
        if (label != null) {
            String text = fit(label.getString(), Math.min(60, w / 3));
            g.drawString(font, text, x, y + 3, DIM, false);
            ax = x + font.width(text) + 6;
        }
        int aw = x + w - ax;
        g.fill(ax, y, ax + aw, y + 14, CARD);
        g.renderOutline(ax, y, aw, 14, RULE);
        if (prev != null) {
            arrow(g, mouseX, mouseY, ax, y, "◀", prev, accent);
            arrow(g, mouseX, mouseY, ax + aw - 14, y, "▶", next, accent);
        }
        g.drawCenteredString(font, fit(value.getString(), aw - 32), ax + aw / 2, y + 3, PAPER);
    }

    private void arrow(GuiGraphics g, int mouseX, int mouseY, int x, int y, String glyph, Runnable click, int accent) {
        boolean hover = in(mouseX, mouseY, x, y, 14, 14);
        g.fill(x + 1, y + 1, x + 13, y + 13, hover ? darken(accent, 0.45f) : 0xFF25212C);
        g.drawCenteredString(font, glyph, x + 7, y + 3, hover ? PAPER : accent);
        hits.add(new Hit(x, y, 14, 14, click, null));
    }

    private void pill(GuiGraphics g, int x, int y, int w, int h, Component text, int accent, boolean enabled, boolean lit, int mouseX, int mouseY) {
        boolean hover = enabled && in(mouseX, mouseY, x, y, w, h);
        g.fill(x, y, x + w, y + h, lit ? darken(accent, 0.6f) : hover ? darken(accent, 0.4f) : darken(accent, 0.2f));
        g.renderOutline(x, y, w, h, enabled ? accent : FAINT);
        g.drawCenteredString(font, fit(text.getString(), w - 6), x + w / 2, y + (h - 8) / 2, enabled ? PAPER : FAINT);
    }

    private void section(GuiGraphics g, int x, int y, int w, Component title) {
        String text = fit(title.getString(), w - 10);
        g.drawString(font, text, x, y, tab.accent, false);
        int lx = x + font.width(text) + 5;
        if (lx < x + w) {
            g.fill(lx, y + 4, x + w, y + 5, RULE);
        }
    }

    private static void box(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0x80070609);
        g.renderOutline(x, y, w, h, RULE);
    }

    private static void bar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
        if (w <= 0) {
            return;
        }
        g.fill(x, y, x + w, y + h, 0xFF24212A);
        int fill = Math.round(w * Mth.clamp(fraction, 0f, 1f));
        if (fill > 0) {
            g.fillGradient(x, y, x + fill, y + h, color, darken(color, 0.65f));
            g.fill(x, y, x + fill, y + 1, 0x40FFFFFF);
        }
    }

    private void scrollbar(GuiGraphics g, int x, int y, int h, int scroll, int rows, int total) {
        if (total <= rows) {
            return;
        }
        g.fill(x, y, x + 2, y + h, 0xFF24212A);
        int thumb = Math.max(8, h * rows / total);
        int top = y + (h - thumb) * scroll / Math.max(1, total - rows);
        g.fill(x, top, x + 2, top + thumb, Tab.JUTSU.accent);
    }

    /** Wrapped text that stops before {@code maxY}; returns the y below it. */
    private int wrap(GuiGraphics g, Component text, int x, int y, int w, int maxY, int color) {
        List<FormattedCharSequence> lines = font.split(FormattedText.composite(text), Math.max(10, w));
        for (int i = 0; i < lines.size(); i++) {
            if (y + 9 > maxY) {
                break;
            }
            boolean last = i < lines.size() - 1 && y + 18 > maxY;
            if (last) {
                // Out of room: cut the visible last line with "…".
                StringBuilder sb = new StringBuilder();
                lines.get(i).accept((index, style, cp) -> {
                    sb.appendCodePoint(cp);
                    return true;
                });
                g.drawString(font, fit(sb + "…", w), x, y, color, false);
                return y + 9;
            }
            g.drawString(font, lines.get(i), x, y, color, false);
            y += 9;
        }
        return y;
    }

    /** {@code text} cut to {@code width} pixels with "…" if it doesn't fit. */
    private String fit(String text, int width) {
        if (width <= 0) {
            return "";
        }
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    private static String statKey(Stat stat) {
        return "stat.noirm." + stat.key().toLowerCase(Locale.ROOT);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int darken(int color, float factor) {
        int r = Math.round((color >> 16 & 0xFF) * factor);
        int g = Math.round((color >> 8 & 0xFF) * factor);
        int b = Math.round((color & 0xFF) * factor);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int lerp(int a, int b, float t) {
        int r = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
        int gr = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | r << 16 | gr << 8 | bl;
    }

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

    // ================================================================ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit hit = hits.get(i);
                if (hit.click() != null && hit.contains(mouseX, mouseY)) {
                    hit.click().run();
                    if (minecraft != null) {
                        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 0.35f));
                    }
                    return true;
                }
            }
            if (tab == Tab.OVERVIEW && in(mouseX, mouseY, leftPos + 8, topPos + 46, 108, H - 54)) {
                dragging = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            modelYaw -= (float) dragX * 2.5f;
            lastDrag = System.currentTimeMillis();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            lastDrag = System.currentTimeMillis();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == Tab.JUTSU && info != null) {
            int step = -(int) Math.signum(delta);
            if (mouseX < leftPos + W / 2.0) {
                knownScroll = Math.max(0, knownScroll + step);
            } else {
                teachScroll = Math.max(0, teachScroll + step);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void removed() {
        super.removed();
        menu.slotsShown = true;
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class AllyScreenRegistration {
        private AllyScreenRegistration() {}

        @SubscribeEvent
        public static void clientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> MenuScreens.register(NoirmMenus.ALLY.get(), AllyScreen::new));
        }
    }
}
