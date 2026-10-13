package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.ally.AllyMenu;
import com.noirm.ally.AllyPackets;
import com.noirm.ally.Order;
import com.noirm.network.NoirmNetwork;
import com.noirm.registry.NoirmMenus;
import com.noirm.training.TrainingSystem.Stat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Locale;

/**
 * A crew mate's screen: what they wear and hold (drag items to and from your inventory below),
 * their stats and training progress, what they're doing (orders), how they fight (style), how
 * they look (outfit), the jutsu you can teach them, and dismissing them.
 */
public class AllyScreen extends AbstractContainerScreen<AllyMenu> {
    private static final int PANEL = 0xF0101016;
    private static final int BORDER = 0xFF3A3A48;
    private static final int GOLD = 0xFFE8C060;
    private static final int DIM = 0xFF9A9AA8;
    private static final int[] STAT_COLORS = {0xFFE07040, 0xFF40C8E0, 0xFFE04060, 0xFF4080F0};
    private static final int RIGHT_X = 104;
    private static final int TEACH_ROWS = 4;

    private AllyPackets.Info info;
    private int teachScroll;
    /** The list at the bottom right: false = jutsu you can teach, true = their jutsu (equip up to 4). */
    private boolean jutsuTab;

    public AllyScreen(AllyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 340;
        imageHeight = 236;
        inventoryLabelY = AllyMenu.INVENTORY_Y - 11;
    }

    /** New data from the server: refresh the screen if it's this ally's. */
    public static void receive(AllyPackets.Info info) {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof AllyScreen screen
                && screen.menu.ally().getId() == info.entityId()) {
            screen.info = info;
            screen.rebuild();
        }
    }

    @Override
    protected void init() {
        super.init();
        rebuild();
    }

    private void send(AllyPackets.Action action, String arg) {
        NoirmNetwork.sendToServer(new AllyPackets.Command(menu.ally().getId(), action, arg));
    }

    private void rebuild() {
        clearWidgets();
        if (info == null) {
            return;
        }
        int x = leftPos + RIGHT_X;
        int y = topPos + 96;
        // Orders.
        int bw = 56;
        for (Order order : Order.values()) {
            boolean active = info.order() == order.ordinal();
            Button button = Button.builder(Component.translatable(order.key()), b -> send(AllyPackets.Action.ORDER, order.name()))
                    .bounds(x + order.ordinal() * (bw + 2), y, bw, 16).build();
            button.active = !active;
            addRenderableWidget(button);
        }
        y += 22;
        // Style and outfit pickers.
        if (info.styles().size() > 1) {
            int index = info.styles().indexOf(info.style());
            String prev = info.styles().get(Math.floorMod(index - 1, info.styles().size()));
            String next = info.styles().get(Math.floorMod(index + 1, info.styles().size()));
            addRenderableWidget(Button.builder(Component.literal("◀"), b -> send(AllyPackets.Action.STYLE, prev)).bounds(x + 52, y, 14, 14).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), b -> send(AllyPackets.Action.STYLE, next)).bounds(x + 216, y, 14, 14).build());
        }
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> send(AllyPackets.Action.OUTFIT_PREV, "")).bounds(x + 52, y + 16, 14, 14).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> send(AllyPackets.Action.OUTFIT_NEXT, "")).bounds(x + 216, y + 16, 14, 14).build());
        // Tabs: teach / their jutsu.
        int tabY = topPos + AllyMenu.INVENTORY_Y - 14;
        Button teachTab = Button.builder(Component.translatable("screen.noirm.ally.tab.teach"), b -> {
            jutsuTab = false;
            teachScroll = 0;
            rebuild();
        }).bounds(leftPos + 176, tabY, 70, 12).build();
        teachTab.active = jutsuTab;
        addRenderableWidget(teachTab);
        Button jutsuButton = Button.builder(Component.translatable("screen.noirm.ally.tab.jutsu", info.loadout().size(), com.noirm.entity.SparringNinja.LOADOUT_SLOTS),
                b -> {
                    jutsuTab = true;
                    teachScroll = 0;
                    rebuild();
                }).bounds(leftPos + 248, tabY, 86, 12).build();
        jutsuButton.active = !jutsuTab;
        addRenderableWidget(jutsuButton);
        int ty = topPos + AllyMenu.INVENTORY_Y + 2;
        int rows = Math.min(TEACH_ROWS, listSize() - teachScroll);
        for (int i = 0; i < rows; i++) {
            int index = teachScroll + i;
            if (jutsuTab) {
                var id = info.known().get(index);
                boolean equipped = info.loadout().contains(id);
                Button equip = Button.builder(Component.translatable(equipped ? "screen.noirm.ally.unequip" : "screen.noirm.ally.equip"),
                        b -> send(AllyPackets.Action.EQUIP, id.toString())).bounds(leftPos + imageWidth - 50, ty + i * 16, 44, 14).build();
                equip.active = equipped || info.loadout().size() < com.noirm.entity.SparringNinja.LOADOUT_SLOTS;
                addRenderableWidget(equip);
                continue;
            }
            AllyPackets.Teachable t = info.teachable().get(index);
            Button teach = Button.builder(Component.translatable("screen.noirm.ally.teach"), b -> send(AllyPackets.Action.TEACH, t.id().toString()))
                    .bounds(leftPos + imageWidth - 50, ty + i * 16, 44, 14).build();
            teach.active = t.blocker().isEmpty();
            if (!t.blocker().isEmpty()) {
                teach.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(t.blocker())));
            }
            addRenderableWidget(teach);
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.noirm.ally.dismiss"), b -> send(AllyPackets.Action.DISMISS, ""))
                .bounds(leftPos + imageWidth - 60, topPos + 4, 54, 14).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (info != null && mouseX >= leftPos + 176 && mouseY >= topPos + AllyMenu.INVENTORY_Y - 12) {
            int max = Math.max(0, listSize() - TEACH_ROWS);
            int scroll = Math.max(0, Math.min(max, teachScroll - (int) Math.signum(delta)));
            if (scroll != teachScroll) {
                teachScroll = scroll;
                rebuild();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        renderBackground(g);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        g.renderOutline(leftPos, topPos, imageWidth, imageHeight, BORDER);
        // Slot frames: equipment and inventory.
        for (var slot : menu.slots) {
            g.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xFF2A2A34);
            g.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0xFF15151C);
        }
        // The ally, turning to follow the mouse.
        int ex = leftPos + 62;
        int ey = topPos + 128;
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, ex, ey, 45, ex - mouseX, ey - 70 - mouseY, menu.ally());
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 8, GOLD, false);
        if (info != null) {
            // Respect and personality.
            int respectColor = info.respect() >= 70 ? 0xFF60E070 : info.respect() >= 20 ? 0xFFE0C060 : 0xFFE05050;
            Component respect = Component.translatable("screen.noirm.ally.respect", Math.round(info.respect()),
                    Component.translatable("screen.noirm.ally.trait." + info.trait().toLowerCase(Locale.ROOT)));
            g.drawString(font, respect, RIGHT_X + 20, 8, respectColor, false);
        }
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, DIM, false);
        if (info == null) {
            return;
        }
        int x = RIGHT_X;
        int y = 24;
        // Health.
        g.drawString(font, Component.translatable("screen.noirm.ally.health", Math.round(info.health()), Math.round(info.maxHealth())), x, y, 0xFFE06060, false);
        bar(g, x + 120, y + 2, 110, info.health() / Math.max(1, info.maxHealth()), 0xFFE04040);
        y += 12;
        // Stats with training progress.
        for (Stat stat : Stat.values()) {
            int i = stat.ordinal();
            Component name = Component.translatable("stat.noirm." + stat.key().toLowerCase(Locale.ROOT));
            g.drawString(font, name.copy().append(" " + Math.round(info.stats()[i])), x, y, STAT_COLORS[i], false);
            bar(g, x + 120, y + 2, 110, info.progress()[i], STAT_COLORS[i]);
            y += 12;
        }
        g.drawString(font, Component.translatable("screen.noirm.ally.orders"), x, 86, DIM, false);
        // Style and outfit.
        int py = 118;
        g.drawString(font, Component.translatable("screen.noirm.ally.style"), x, py + 3, DIM, false);
        Component style = Component.translatable("style.noirm." + info.style());
        g.drawCenteredString(font, style, x + 141, py + 3, GOLD);
        g.drawString(font, Component.translatable("screen.noirm.ally.outfit"), x, py + 19, DIM, false);
        g.drawCenteredString(font, Component.literal(info.outfit().replace('/', ' ')), x + 141, py + 19, 0xFFDDDDDD);
        // The list: jutsu to teach, or their own jutsu with mastery stars (equipped ones in gold).
        int tx = 176;
        int ty = AllyMenu.INVENTORY_Y + 2;
        if (listSize() == 0) {
            g.drawString(font, Component.translatable(jutsuTab ? "screen.noirm.ally.jutsu_none" : "screen.noirm.ally.teach_none"), tx, ty + 3, 0xFF6A6A70, false);
        }
        int rows = Math.min(TEACH_ROWS, listSize() - teachScroll);
        for (int i = 0; i < rows; i++) {
            int index = teachScroll + i;
            if (jutsuTab) {
                var id = info.known().get(index);
                int stars = index < info.mastery().size() ? info.mastery().get(index) : 0;
                boolean equipped = info.loadout().contains(id);
                String name = font.plainSubstrByWidth(Component.translatable(com.noirm.dojutsu.Sharingan.jutsuKey(id)).getString(), 100);
                g.drawString(font, name + " " + "★".repeat(stars), tx, ty + 3 + i * 16, equipped ? GOLD : 0xFFE8E8E8, false);
                continue;
            }
            AllyPackets.Teachable t = info.teachable().get(index);
            int color = t.blocker().isEmpty() ? 0xFFE8E8E8 : 0xFF7A7A80;
            String name = font.plainSubstrByWidth(Component.translatable(com.noirm.dojutsu.Sharingan.jutsuKey(t.id())).getString(), 106);
            g.drawString(font, "[" + t.rank() + "] " + name, tx, ty + 3 + i * 16, color, false);
        }
        if (listSize() > TEACH_ROWS) {
            g.drawString(font, (teachScroll + 1) + "-" + (teachScroll + rows) + "/" + listSize(), tx + 130, AllyMenu.INVENTORY_Y + 68, 0xFF6A6A70, false);
        }
    }

    private int listSize() {
        return info == null ? 0 : jutsuTab ? info.known().size() : info.teachable().size();
    }

    private void bar(GuiGraphics g, int x, int y, int w, float fraction, int color) {
        g.fill(x, y, x + w, y + 5, 0xFF26262E);
        g.fill(x, y, x + Math.round(w * Math.max(0, Math.min(1, fraction))), y + 5, color);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
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
