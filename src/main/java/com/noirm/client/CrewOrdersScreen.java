package com.noirm.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.noirm.Noirm;
import com.noirm.ally.Behaviour;
import com.noirm.ally.CrewPackets;
import com.noirm.ally.Order;
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
 * Each crew mate's own job, tactics and lessons are on their own screen (right-click them).
 */
public class CrewOrdersScreen extends Screen {
    public static final KeyMapping KEY = new KeyMapping("key.noirm.crew_orders", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CombatKeys.CATEGORY);

    private static final int W = 300;
    private static final int H = 172;
    private static final int INK = 0xF4100E14;
    private static final int INK_LIGHT = 0xF41A1720;
    private static final int CARD = 0xFF17141C;
    private static final int CARD_HOVER = 0xFF221E29;
    private static final int RULE = 0xFF2E2935;
    private static final int FRAME = 0xFF3D3645;
    private static final int PAPER = 0xFFEDE4D3;
    private static final int DIM = 0xFF8F8796;
    private static final int ACCENT = 0xFFB28CF0;
    private static final int GOLD = 0xFFE8C060;

    private record Button(int x, int y, int w, int h, Component label, Component tip, CrewPackets.Action action, String arg, boolean closes) {}

    private final List<Button> buttons = new ArrayList<>();

    public CrewOrdersScreen() {
        super(Component.translatable("screen.noirm.crew_orders"));
    }

    @Override
    protected void init() {
        buttons.clear();
        int x0 = (width - W) / 2 + 8;
        int y = (height - H) / 2 + 24;
        int inner = W - 16;
        // Orders: two rows of three.
        int cw = (inner - 8) / 3;
        Order[] orders = Order.SHOWN;
        for (int i = 0; i < orders.length; i++) {
            Order order = orders[i];
            int bx = x0 + (i % 3) * (cw + 4);
            int by = y + (i / 3) * 24;
            buttons.add(new Button(bx, by, cw, 20, Component.literal((buttons.size() + 1) + "  ").append(Component.translatable(order.key())),
                    Component.translatable(order.key() + ".desc"), CrewPackets.Action.ORDER, order.name(), true));
        }
        y += 54;
        String[] quick = {"come", "attack", "fall_back"};
        CrewPackets.Action[] actions = {CrewPackets.Action.COME, CrewPackets.Action.ATTACK, CrewPackets.Action.FALL_BACK};
        for (int i = 0; i < quick.length; i++) {
            buttons.add(new Button(x0 + i * (cw + 4), y, cw, 20,
                    Component.literal((buttons.size() + 1) + "  ").append(Component.translatable("screen.noirm.crew_orders." + quick[i])),
                    Component.translatable("screen.noirm.crew_orders." + quick[i] + ".desc"), actions[i], "", true));
        }
        y += 34;
        Behaviour[] behaviours = Behaviour.values();
        for (int i = 0; i < behaviours.length; i++) {
            buttons.add(new Button(x0 + i * (cw + 4), y, cw, 18, Component.translatable(behaviours[i].key()),
                    Component.translatable(behaviours[i].key() + ".desc"), CrewPackets.Action.BEHAVIOUR, behaviours[i].name(), false));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = (width - W) / 2;
        int y = (height - H) / 2;
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, 0xC0000000);
        g.fillGradient(x, y, x + W, y + H, INK_LIGHT, INK);
        g.renderOutline(x, y, W, H, FRAME);
        g.fill(x + 8, y + 8, x + 18, y + 18, 0xFFB8322E);
        g.drawCenteredString(font, "令", x + 13, y + 9, PAPER);
        g.drawString(font, title, x + 24, y + 9, GOLD, false);
        String hint = Component.translatable("screen.noirm.crew_orders.hint").getString();
        g.drawString(font, fit(hint, W - 40 - font.width(title)), x + W - 8 - Math.min(font.width(hint), W - 40 - font.width(title)), y + 9, DIM, false);
        label(g, x + 8, y + 70, Component.translatable("screen.noirm.crew_orders.quick"));
        label(g, x + 8, y + 104, Component.translatable("screen.noirm.crew_orders.behaviour"));
        Button hovered = null;
        for (Button b : buttons) {
            boolean hover = mouseX >= b.x() && mouseX < b.x() + b.w() && mouseY >= b.y() && mouseY < b.y() + b.h();
            if (hover) {
                hovered = b;
            }
            g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), hover ? CARD_HOVER : CARD);
            g.renderOutline(b.x(), b.y(), b.w(), b.h(), hover ? ACCENT : RULE);
            g.drawCenteredString(font, fit(b.label().getString(), b.w() - 6), b.x() + b.w() / 2, b.y() + (b.h() - 8) / 2, hover ? PAPER : 0xFFCFC6D6);
        }
        if (hovered != null) {
            g.renderTooltip(font, font.split(hovered.tip(), 200), mouseX, mouseY);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void label(GuiGraphics g, int x, int y, Component text) {
        g.drawString(font, text, x, y, ACCENT, false);
        int lx = x + font.width(text) + 5;
        g.fill(lx, y + 4, x + W - 16, y + 5, RULE);
    }

    private String fit(String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    private void press(Button b) {
        NoirmNetwork.sendToServer(new CrewPackets.Orders(b.action(), b.arg()));
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 0.35f));
            if (b.closes()) {
                minecraft.setScreen(null);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
