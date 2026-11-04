package com.noirm.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.noirm.Noirm;
import com.noirm.ally.Behaviour;
import com.noirm.ally.CrewPackets;
import com.noirm.ally.Order;
import com.noirm.client.ui.HoverTracker;
import com.noirm.client.ui.NoirmStyled;
import com.noirm.client.ui.ScreenMotion;
import com.noirm.client.ui.Theme;
import com.noirm.client.ui.Ui;
import com.noirm.network.NoirmNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The crew order screen (K): one press, one click, and the whole crew obeys. Orders (follow,
 * wait, guard here, patrol here, train, meditate), quick commands (come here, attack what I'm
 * looking at, fall back) and how willing they are to fight. Number keys 1–9 pick the first nine.
 * Each crew mate's own job, tactics and lessons are on their own screen (right-click them). Drawn
 * with the NOIRM kit ({@code client.ui}): flat buttons with an eased underline, a fade on open/close.
 */
public class CrewOrdersScreen extends Screen implements NoirmStyled {
    public static final KeyMapping KEY = new KeyMapping("key.noirm.crew_orders", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CombatKeys.CATEGORY);

    private static final int MAX_W = 300;
    private static final int PAD = Theme.S5;
    private static final int BUTTON_H = 18;
    private static final int ROW_STEP = BUTTON_H + Theme.S2;

    private record Button(int x, int y, int w, int h, Component label, Component tip, CrewPackets.Action action, String arg, boolean closes) {}

    private final List<Button> buttons = new ArrayList<>();
    private final ScreenMotion motion = new ScreenMotion();
    private final HoverTracker hover = new HoverTracker();
    private int left;
    private int top;
    private int w;
    private int h;
    private int quickY;
    private int behaviourY;

    public CrewOrdersScreen() {
        super(Component.translatable("screen.noirm.crew_orders"));
    }

    @Override
    protected void init() {
        buttons.clear();
        w = Math.min(MAX_W, width - 16);
        // Header, two rows of orders, a section of quick commands, a section of behaviours.
        h = PAD + Theme.ROW + Theme.S5 + ROW_STEP * 2 + Theme.S4 + 12 + ROW_STEP + Theme.S4 + 12 + BUTTON_H + PAD;
        left = (width - w) / 2;
        top = (height - h) / 2;
        int x0 = left + PAD;
        int y = top + PAD + Theme.ROW + Theme.S5;
        int inner = w - PAD * 2;
        // Orders: two rows of three.
        int cw = (inner - 8) / 3;
        Order[] orders = Order.SHOWN;
        for (int i = 0; i < orders.length; i++) {
            Order order = orders[i];
            int bx = x0 + (i % 3) * (cw + 4);
            int by = y + (i / 3) * ROW_STEP;
            buttons.add(new Button(bx, by, cw, BUTTON_H, Component.literal((buttons.size() + 1) + "  ").append(Component.translatable(order.key())),
                    Component.translatable(order.key() + ".desc"), CrewPackets.Action.ORDER, order.name(), true));
        }
        y += ROW_STEP * 2 + Theme.S4;
        quickY = y;
        y += 12;
        String[] quick = {"come", "attack", "fall_back"};
        CrewPackets.Action[] actions = {CrewPackets.Action.COME, CrewPackets.Action.ATTACK, CrewPackets.Action.FALL_BACK};
        for (int i = 0; i < quick.length; i++) {
            buttons.add(new Button(x0 + i * (cw + 4), y, cw, BUTTON_H,
                    Component.literal((buttons.size() + 1) + "  ").append(Component.translatable("screen.noirm.crew_orders." + quick[i])),
                    Component.translatable("screen.noirm.crew_orders." + quick[i] + ".desc"), actions[i], "", true));
        }
        y += ROW_STEP + Theme.S4;
        behaviourY = y;
        y += 12;
        Behaviour[] behaviours = Behaviour.values();
        for (int i = 0; i < behaviours.length; i++) {
            buttons.add(new Button(x0 + i * (cw + 4), y, cw, BUTTON_H, Component.translatable(behaviours[i].key()),
                    Component.translatable(behaviours[i].key() + ".desc"), CrewPackets.Action.BEHAVIOUR, behaviours[i].name(), false));
        }
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

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        motion.scrim(g, width, height);
        motion.begin(g);
        Ui.panel(g, left, top, w, h);
        int inner = w - PAD * 2;
        Ui.header(g, font, left + PAD, top + PAD, inner, "令", Theme.SEAL, title,
                Component.translatable("screen.noirm.crew_orders.hint"), Theme.TEXT_FAINT);
        Ui.section(g, font, left + PAD, quickY, inner, Component.translatable("screen.noirm.crew_orders.quick"), Theme.TEXT_DIM);
        Ui.section(g, font, left + PAD, behaviourY, inner, Component.translatable("screen.noirm.crew_orders.behaviour"), Theme.TEXT_DIM);
        Button hovered = null;
        for (int i = 0; i < buttons.size(); i++) {
            Button b = buttons.get(i);
            boolean over = !motion.closing() && Ui.in(mouseX, mouseY, b.x(), b.y(), b.w(), b.h());
            if (over) {
                hovered = b;
            }
            Ui.button(g, font, b.x(), b.y(), b.w(), b.h(), b.label(), hover.get(i, over), true, Theme.SEAL, false);
        }
        motion.end(g);
        super.render(g, mouseX, mouseY, partialTick);
        if (hovered != null) {
            List<Component> tip = new ArrayList<>();
            if (Ui.truncated(font, hovered.label(), hovered.w() - 6)) {
                tip.add(hovered.label());
            }
            tip.add(hovered.tip());
            List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
            tip.forEach(line -> lines.addAll(font.split(line, 200)));
            g.renderTooltip(font, lines, mouseX, mouseY);
        } else if (!motion.closing()) {
            // The hint in the header is cut with "…" on a narrow panel: the whole of it under the mouse.
            Component hint = Component.translatable("screen.noirm.crew_orders.hint");
            int noteW = Ui.headerNoteWidth(font, inner, title, hint);
            if (noteW < font.width(hint) && Ui.in(mouseX, mouseY, left + PAD + inner - noteW, top + PAD, noteW, Theme.ROW)) {
                g.renderTooltip(font, font.split(hint, 200), mouseX, mouseY);
            }
        }
    }

    private void press(Button b) {
        NoirmNetwork.sendToServer(new CrewPackets.Orders(b.action(), b.arg()));
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 0.35f));
            if (b.closes()) {
                onClose(); // (fades out, then closes)
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (motion.closing()) {
            return true;
        }
        if (button == 0) {
            for (Button b : buttons) {
                if (mouseX >= b.x() && mouseX < b.x() + b.w() && mouseY >= b.y() && mouseY < b.y() + b.h()) {
                    press(b);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (motion.closing()) {
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            int index = keyCode - GLFW.GLFW_KEY_1;
            if (index < buttons.size()) {
                press(buttons.get(index));
                return true;
            }
        }
        if (KEY.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
    public static final class CrewOrdersKey {
        private CrewOrdersKey() {}

        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (event.phase != TickEvent.Phase.END || minecraft.player == null) {
                return;
            }
            while (KEY.consumeClick()) {
                if (minecraft.screen == null) {
                    minecraft.setScreen(new CrewOrdersScreen());
                }
            }
        }
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class CrewOrdersKeyRegistration {
        private CrewOrdersKeyRegistration() {}

        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(KEY);
        }
    }

    static String lower(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }
}
