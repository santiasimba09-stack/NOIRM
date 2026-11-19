package com.noirm.client.hound;

import com.mojang.blaze3d.platform.InputConstants;
import com.noirm.Noirm;
import com.noirm.client.CombatKeys;
import com.noirm.client.ui.HoverTracker;
import com.noirm.client.ui.NoirmStyled;
import com.noirm.client.ui.ScreenMotion;
import com.noirm.client.ui.Theme;
import com.noirm.client.ui.Ui;
import com.noirm.client.ui.UiSounds;
import com.noirm.hound.HoundOrder;
import com.noirm.hound.HoundPackets;
import com.noirm.hound.HoundStage;
import com.noirm.hound.Hounds;
import com.noirm.hound.NinjaHound;
import com.noirm.network.NoirmNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The hound panel (O): your ninja hound at a glance and at your command. Left, the hound itself,
 * live; right, its growth (the five stages, then bond, XP and your own power against what the next
 * stage needs), what it can do now and what it will learn, and its orders. While it's a puppy you
 * can still pick its coat. Drawn with the NOIRM kit ({@code client.ui}).
 */
public class HoundScreen extends Screen implements NoirmStyled {
    public static final KeyMapping KEY = new KeyMapping("key.noirm.hound_panel", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, CombatKeys.CATEGORY);

    private static final int MAX_W = 340;
    private static final int MAX_H = 236;
    private static final int PAD = Theme.S5;
    private static final int BUTTON_H = 14;
    private static final int[] COAT_COLOURS = {0xFFE8E2D4, 0xFF8E8C88, 0xFF2A2826, 0xFF7A5434};

    /** What a hound can do, the stage it comes with, and whether only Inuzuka blood can use it. */
    private record Ability(String key, HoundStage stage, boolean inuzuka, HoundStage until) {}

    private static final Ability[] ABILITIES = {
            new Ability("smell", HoundStage.PUPPY, false, null),
            new Ability("marking", HoundStage.PUPPY, false, null),
            new Ability("carry", HoundStage.PUPPY, false, HoundStage.PUPPY),
            new Ability("fang", HoundStage.YOUNG, false, null),
            new Ability("clone", HoundStage.YOUNG, true, null),
            new Ability("guard", HoundStage.GROWN, false, null),
            new Ability("ride", HoundStage.GREAT, false, null),
            new Ability("wolf", HoundStage.GREAT, true, null),
    };

    private record Hit(int x, int y, int w, int h, String action, Component tip) {}

    private final List<Hit> hits = new ArrayList<>();
    private final ScreenMotion motion = new ScreenMotion();
    private final HoverTracker hover = new HoverTracker();
    private int left;
    private int top;
    private int w;
    private int h;

    public HoundScreen() {
        super(Component.translatable("screen.noirm.hound"));
    }

    @Override
    protected void init() {
        w = Math.min(MAX_W, width - 16);
        h = Math.min(MAX_H, height - 8);
        left = (width - w) / 2;
        top = (height - h) / 2;
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
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        HoundPackets.HoundSync sync = ClientHound.sync();
        NinjaHound hound = ClientHound.entity();
        motion.scrim(g, width, height);
        motion.begin(g);
        Ui.panel(g, left, top, w, h);
        int inner = w - PAD * 2;
        Component tip = null;
        if (!sync.has()) {
            Ui.header(g, font, left + PAD, top + PAD, inner, "犬", Theme.SEAL, title, null, 0);
            int y = top + PAD + Theme.ROW + Theme.S5;
            Ui.wrap(g, font, Component.translatable(sync.blood() == 2 ? "screen.noirm.hound.none_contract" : "screen.noirm.hound.none_desc"),
                    left + PAD, y, inner, top + h - PAD, Theme.TEXT_DIM);
            motion.end(g);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        HoundStage stage = sync.stageEnum();
        Component stageName = Component.translatable("screen.noirm.hound.stage." + stage.key());
        Component name = Component.literal(sync.name().isEmpty() ? "?" : sync.name());
        Ui.header(g, font, left + PAD, top + PAD, inner, "犬", Theme.SEAL, name, stageName, Theme.GOLD);
        int y0 = top + PAD + Theme.ROW + Theme.S4;

        // ---- left: the hound, live
        int viewW = Math.min(120, inner * 38 / 100);
        int viewH = h - (y0 - top) - PAD;
        Ui.well(g, left + PAD, y0, viewW, viewH);
        if (hound != null && !hound.isHidden()) {
            renderHound(g, hound, left + PAD + viewW / 2, y0 + viewH * 3 / 4, viewW, viewH);
        } else {
            Ui.textCentered(g, font, Ui.fit(font, Component.translatable(sync.out() ? "screen.noirm.hound.not_here" : "screen.noirm.hound.dismissed")
                    .getString(), viewW - 8), left + PAD + viewW / 2, y0 + viewH / 2 - 4, Theme.TEXT_FAINT);
        }
        // Its state under the picture.
        Component state = stateLine(sync, hound);
        Ui.textCentered(g, font, Ui.fit(font, state.getString(), viewW - 6), left + PAD + viewW / 2, y0 + viewH - 12,
                hound != null && hound.isDown() ? Theme.RED : Theme.TEXT_DIM);
        if (hound != null) {
            float hp = hound.getMaxHealth() > 0 ? hound.getHealth() / hound.getMaxHealth() : 0f;
            Ui.bar(g, left + PAD + 6, y0 + viewH - 16, viewW - 12, 2, hp, hp, Theme.HEALTH, Theme.WELL);
        }

        // ---- right column
        int x = left + PAD + viewW + Theme.S5;
        int cw = left + w - PAD - x;
        int y = y0;
        // Stages: a five-part track.
        for (int i = 0; i < HoundStage.values().length; i++) {
            HoundStage s = HoundStage.values()[i];
            int sx = Ui.segX(x, cw, i, 5);
            int sw = Ui.segW(cw, i, 5);
            boolean now = s == stage;
            boolean past = s.ordinal() < stage.ordinal();
            int fill = now ? Theme.mix(Theme.RAISED, Theme.SEAL, 0.3f) : past ? Theme.mix(Theme.RAISED, Theme.GREEN, 0.14f) : Theme.RAISED;
            g.fill(sx + (i > 0 ? 1 : 0), y, sx + sw, y + 12, fill);
            if (now) {
                g.fill(sx + (i > 0 ? 1 : 0), y + 11, sx + sw, y + 12, Theme.SEAL);
            }
            Component label = Component.translatable("screen.noirm.hound.stage." + s.key());
            Ui.textCentered(g, font, Ui.fit(font, label.getString(), sw - 4), sx + sw / 2, y + 2,
                    now ? Theme.TEXT : past ? Theme.mix(Theme.TEXT_DIM, Theme.GREEN, 0.4f) : Theme.TEXT_FAINT);
            if (Ui.in(mouseX, mouseY, sx, y, sw, 12)) {
                tip = Component.translatable("screen.noirm.hound.stage." + s.key() + ".desc");
            }
        }
        y += 12 + Theme.S4;
        // Growth: bond, XP and your power against the next stage.
        HoundStage next = stage.next();
        y = growthRow(g, x, y, cw, "bond", sync.bond(), next == null ? 100f : next.bondNeeded, Math.round(sync.bond()) + "/100", Theme.SEAL);
        if (next != null) {
            y = growthRow(g, x, y, cw, "xp", sync.xp(), next.xpNeeded,
                    Math.round(sync.xp()) + "/" + Math.round(next.xpNeeded), Theme.GOLD);
            y = growthRow(g, x, y, cw, "power", sync.ownerPower(), next.ownerPowerNeeded,
                    String.format("%.0f/%.0f", sync.ownerPower(), next.ownerPowerNeeded), Theme.CHAKRA);
            Component blocker = Hounds.blocker(next, sync.xp(), sync.bond(), sync.ownerPower());
            Component nextLine = blocker == null
                    ? Component.translatable("screen.noirm.hound.next_ready", Component.translatable("screen.noirm.hound.stage." + next.key()))
                    : Component.translatable("screen.noirm.hound.next_needs", Component.translatable("screen.noirm.hound.stage." + next.key()), blocker);
            Ui.text(g, font, nextLine, x, y, cw, blocker == null ? Theme.GREEN : Theme.TEXT_DIM);
            if (Ui.in(mouseX, mouseY, x, y, cw, 10)) {
                tip = Component.translatable("screen.noirm.hound.growth.desc");
            }
            y += 11;
        } else {
            Ui.text(g, font, Component.translatable("screen.noirm.hound.top"), x, y, cw, Theme.GOLD);
            y += 11;
        }
        y += Theme.S2;

        // Abilities: lit when you have them, faint with the stage they come at.
        y = Ui.section(g, font, x, y, cw, Component.translatable("screen.noirm.hound.abilities"), Theme.TEXT_DIM);
        int ax = x;
        boolean inuzuka = sync.blood() == 1;
        for (Ability a : ABILITIES) {
            if (a.inuzuka() && !inuzuka) {
                continue;
            }
            boolean has = stage.ordinal() >= a.stage().ordinal() && (a.until() == null || stage.ordinal() <= a.until().ordinal());
            boolean outgrown = a.until() != null && stage.ordinal() > a.until().ordinal();
            String label = Component.translatable("screen.noirm.hound.ability." + a.key()).getString();
            int cwChip = font.width(label) + 8;
            if (ax + cwChip > x + cw) {
                ax = x;
                y += 12;
            }
            int colour = has ? Theme.GREEN : outgrown ? Theme.TEXT_FAINT : Theme.mix(Theme.TEXT_FAINT, Theme.TEXT_DIM, 0.3f);
            int chipEnd = ax + Ui.chip(g, font, ax, y, label, colour);
            if (Ui.in(mouseX, mouseY, ax, y, chipEnd - ax, 10)) {
                Component desc = Component.translatable("screen.noirm.hound.ability." + a.key() + ".desc",
                        Math.round(stage.smell));
                tip = has ? desc : outgrown ? Component.translatable("screen.noirm.hound.outgrown", desc)
                        : Component.translatable("screen.noirm.hound.comes_at", desc, Component.translatable("screen.noirm.hound.stage." + a.stage().key()));
            }
            ax = Math.max(ax + cwChip + Theme.S2, chipEnd + Theme.S2);
        }
        y += 12 + Theme.S3;

        // Orders.
        y = Ui.section(g, font, x, y, cw, Component.translatable("screen.noirm.hound.orders"), Theme.TEXT_DIM);
        HoundOrder order = HoundOrder.of(sync.order());
        String[] row1 = {"follow", "sit", "guard"};
        for (int i = 0; i < row1.length; i++) {
            boolean on = order.name().equalsIgnoreCase(row1[i]);
            y = orderButton(g, mouseX, mouseY, x, y, cw, i, row1.length, row1[i], on, i == row1.length - 1);
        }
        y += Theme.S2;
        String[] row2 = {"come", "attack", "track", "pet", "howl"};
        for (int i = 0; i < row2.length; i++) {
            y = orderButton(g, mouseX, mouseY, x, y, cw, i, row2.length, row2[i], false, i == row2.length - 1);
        }
        y += Theme.S2;
        List<String> row3 = new ArrayList<>();
        if (stage.canBeCarried()) {
            row3.add("carry");
            row3.add("jacket");
        }
        if (stage.canBeRidden()) {
            row3.add("ride");
        }
        if (sync.contract()) {
            row3.add("recall");
        }
        for (int i = 0; i < row3.size(); i++) {
            String act = row3.get(i);
            boolean on = hound != null && ("carry".equals(act) && hound.isCarried() && hound.carry() == NinjaHound.Carry.HEAD
                    || "jacket".equals(act) && hound.isCarried() && hound.carry() == NinjaHound.Carry.JACKET);
            y = orderButton(g, mouseX, mouseY, x, y, cw, i, row3.size(), "recall".equals(act) && !sync.out() ? "summon" : act, on,
                    i == row3.size() - 1);
        }
        if (!row3.isEmpty()) {
            y += Theme.S2;
        }
        // The coat, while it's a puppy.
        if (stage == HoundStage.PUPPY && y + 12 <= top + h - PAD) {
            Ui.text(g, font, Component.translatable("screen.noirm.hound.coat"), x, y + 2, cw / 3, Theme.TEXT_DIM);
            int sx = x + Math.min(cw / 3, font.width(Component.translatable("screen.noirm.hound.coat")) + Theme.S4);
            for (int i = 0; i < COAT_COLOURS.length; i++) {
                int bx = sx + i * 16;
                boolean on = sync.coat() == i;
                boolean over = Ui.in(mouseX, mouseY, bx, y, 12, 12);
                g.fill(bx, y, bx + 12, y + 12, on ? Theme.SEAL : over ? Theme.IRON : Theme.HAIRLINE);
                g.fill(bx + 1, y + 1, bx + 11, y + 11, COAT_COLOURS[i]);
                Component coatName = Component.translatable("screen.noirm.hound.coat." + Hounds.COATS[i]);
                hits.add(new Hit(bx, y, 12, 12, "coat:" + i, coatName));
                if (over) {
                    tip = coatName;
                }
            }
        }
        motion.end(g);
        super.render(g, mouseX, mouseY, partialTick);
        if (tip == null) {
            for (Hit hit : hits) {
                if (Ui.in(mouseX, mouseY, hit.x(), hit.y(), hit.w(), hit.h()) && hit.tip() != null) {
                    tip = hit.tip();
                }
            }
        }
        if (tip != null && !motion.closing()) {
            List<FormattedCharSequence> lines = font.split(tip, 220);
            g.renderTooltip(font, lines, mouseX, mouseY);
        }
    }

    private int growthRow(GuiGraphics g, int x, int y, int w, String key, float value, float need, String number, int colour) {
        Ui.text(g, font, Component.translatable("screen.noirm.hound." + key), x, y, w - font.width(number) - 4, Theme.mix(colour, Theme.TEXT, 0.55f));
        Ui.textRight(g, font, number, x + w, y, value >= need ? Theme.GREEN : Theme.TEXT);
        float f = need <= 0f ? 1f : Mth.clamp(value / need, 0f, 1f);
        Ui.bar(g, x, y + 10, w, 2, f, f, value >= need ? Theme.GREEN : colour, Theme.WELL);
        return y + 15;
    }

    /** One button in a row of {@code count}; returns the y below the row after its last button. */
    private int orderButton(GuiGraphics g, int mx, int my, int x, int y, int w, int i, int count, String action, boolean on, boolean last) {
        int bx = Ui.segX(x, w, i, count) + (i > 0 ? 1 : 0);
        int bw = Ui.segW(w, i, count) - (i > 0 ? 1 : 0);
        boolean over = !motion.closing() && Ui.in(mx, my, bx, y, bw, BUTTON_H);
        Component label = Component.translatable("screen.noirm.hound.order." + action);
        if (on) {
            Ui.toggle(g, font, bx, y, bw, BUTTON_H, label, true, hover.get(action.hashCode(), over), Theme.SEAL);
        } else {
            Ui.button(g, font, bx, y, bw, BUTTON_H, label, hover.get(action.hashCode(), over), true, Theme.SEAL, false);
        }
        hits.add(new Hit(bx, y, bw, BUTTON_H, action, Component.translatable("screen.noirm.hound.order." + action + ".desc")));
        return last ? y + BUTTON_H : y;
    }

    private static Component stateLine(HoundPackets.HoundSync sync, NinjaHound hound) {
        if (!sync.out()) {
            return Component.translatable("screen.noirm.hound.state.home");
        }
        if (hound == null) {
            return Component.translatable("screen.noirm.hound.state.away");
        }
        if (hound.isDown()) {
            return Component.translatable("screen.noirm.hound.state.down", (hound.downTicks() + 19) / 20);
        }
        if (hound.isHidden()) {
            return Component.translatable("screen.noirm.hound.state.clone");
        }
        if (hound.isFused()) {
            return Component.translatable("screen.noirm.hound.state.fused");
        }
        if (hound.isCarried()) {
            return Component.translatable(hound.carry() == NinjaHound.Carry.HEAD ? "screen.noirm.hound.state.head" : "screen.noirm.hound.state.jacket");
        }
        if (hound.isFrenzied()) {
            return Component.translatable("screen.noirm.hound.state.frenzy");
        }
        return Component.translatable(HoundOrder.of(sync.order()).key());
    }

    /** The hound in the well, turned three-quarters, sized to fit whatever its stage. */
    private void renderHound(GuiGraphics g, NinjaHound hound, int cx, int baseY, int boxW, int boxH) {
        float size = Math.max(hound.getBbHeight(), hound.getBbWidth() * 1.45f);
        int scale = Math.max(8, Math.round(Math.min(boxH * 0.55f, boxW * 0.6f) / size));
        float bodyRot = hound.yBodyRot;
        float bodyRotO = hound.yBodyRotO;
        float yRot = hound.getYRot();
        float xRot = hound.getXRot();
        float headRotO = hound.yHeadRotO;
        float headRot = hound.yHeadRot;
        float yaw = 180f + 145f;
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX(-10f * Mth.DEG_TO_RAD);
        pose.mul(camera);
        hound.yBodyRot = yaw;
        hound.yBodyRotO = yaw;
        hound.setYRot(yaw);
        hound.setXRot(0f);
        hound.yHeadRot = yaw;
        hound.yHeadRotO = yaw;
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.entity.Entity picked = mc.crosshairPickEntity;
        mc.crosshairPickEntity = null;
        boolean nameVisible = hound.isCustomNameVisible();
        hound.setCustomNameVisible(false);
        g.enableScissor(cx - boxW / 2 + 1, baseY - boxH, cx + boxW / 2 - 1, baseY + boxH / 4 - 18);
        InventoryScreen.renderEntityInInventory(g, cx, baseY, scale, pose, camera, hound);
        g.disableScissor();
        hound.setCustomNameVisible(nameVisible);
        mc.crosshairPickEntity = picked;
        hound.yBodyRot = bodyRot;
        hound.yBodyRotO = bodyRotO;
        hound.setYRot(yRot);
        hound.setXRot(xRot);
        hound.yHeadRotO = headRotO;
        hound.yHeadRot = headRot;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (motion.closing()) {
            return true;
        }
        if (button == 0) {
            for (Hit hit : hits) {
                if (Ui.in(mouseX, mouseY, hit.x(), hit.y(), hit.w(), hit.h())) {
                    String action = "summon".equals(hit.action()) ? "recall" : hit.action();
                    NoirmNetwork.sendToServer(new HoundPackets.Command(action));
                    UiSounds.click();
                    if ("ride".equals(action) || "attack".equals(action) || "track".equals(action)) {
                        onClose();
                    }
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
        if (KEY.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
    public static final class HoundPanelKey {
        private HoundPanelKey() {}

        @SubscribeEvent
        public static void houndPanelTick(TickEvent.ClientTickEvent event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (event.phase != TickEvent.Phase.END || minecraft.player == null) {
                return;
            }
            while (KEY.consumeClick()) {
                if (minecraft.screen == null) {
                    minecraft.setScreen(new HoundScreen());
                }
            }
        }
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class HoundPanelKeyRegistration {
        private HoundPanelKeyRegistration() {}

        @SubscribeEvent
        public static void registerHoundKey(RegisterKeyMappingsEvent event) {
            event.register(KEY);
        }
    }
}
