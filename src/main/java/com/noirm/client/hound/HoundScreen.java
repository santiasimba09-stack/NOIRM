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
import com.noirm.hound.HoundGrowth;
import com.noirm.hound.HoundLook;
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

    private static final int MAX_W = 410;
    private static final int MAX_H = 236;
    private static final int PAD = Theme.S5;
    private static final int BUTTON_H = 14;

    /** What a hound can do, the level it unlocks at, and whether only Inuzuka blood can use it. */
    private record Ability(String key, int level, boolean inuzuka) {}

    private static final Ability[] ABILITIES = {
            new Ability("smell", 1, false),
            new Ability("marking", 1, false),
            new Ability("carry", 1, false),
            new Ability("fang", HoundStage.YOUNG.minLevel, false),
            new Ability("clone", HoundStage.YOUNG.minLevel, true),
            new Ability("guard", HoundStage.GROWN.minLevel, false),
            new Ability("ride", HoundStage.GREAT.minLevel, false),
            new Ability("wolf", HoundStage.GREAT.minLevel, true),
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
        int level = sync.level();
        HoundStage stage = sync.stageEnum();
        HoundLook look = sync.lookValue();
        Component name = Component.literal(sync.name().isEmpty() ? "?" : sync.name());
        Component levelNote = Component.translatable("screen.noirm.hound.level_note", level,
                Component.translatable("screen.noirm.hound.stage." + stage.key()));
        Ui.header(g, font, left + PAD, top + PAD, inner, "犬", Theme.SEAL, name, levelNote, Theme.GOLD);
        int y0 = top + PAD + Theme.ROW + Theme.S4;
        int bottom = top + h - PAD;

        // ---- left: the hound, live; its state; its traits; its coat colour while young
        int viewW = Math.min(124, inner * 36 / 100);
        int viewH = Math.min(96, (bottom - y0) / 2 + 10);
        int lx = left + PAD;
        Ui.well(g, lx, y0, viewW, viewH);
        if (hound != null && !hound.isHidden()) {
            renderHound(g, hound, lx + viewW / 2, y0 + viewH * 3 / 4, viewW, viewH);
        } else {
            Ui.textCentered(g, font, Ui.fit(font, Component.translatable(sync.out() ? "screen.noirm.hound.not_here" : "screen.noirm.hound.dismissed")
                    .getString(), viewW - 8), lx + viewW / 2, y0 + viewH / 2 - 4, Theme.TEXT_FAINT);
        }
        int ly = y0 + viewH + Theme.S2;
        if (hound != null) {
            float hp = hound.getMaxHealth() > 0 ? hound.getHealth() / hound.getMaxHealth() : 0f;
            Ui.bar(g, lx, ly, viewW, 2, hp, hp, Theme.HEALTH, Theme.WELL);
        }
        ly += 4;
        Component state = stateLine(sync, hound);
        Ui.text(g, font, state, lx, ly, viewW, hound != null && hound.isDown() ? Theme.RED : Theme.TEXT_DIM);
        ly += 11;
        Component lookLine = lookLine(look);
        if (Ui.text(g, font, lookLine, lx, ly, viewW, Theme.TEXT_FAINT) || Ui.in(mouseX, mouseY, lx, ly, viewW, 10)) {
            if (Ui.in(mouseX, mouseY, lx, ly, viewW, 10)) {
                tip = lookTip(look);
            }
        }
        ly += 13;
        ly = Ui.section(g, font, lx, ly, viewW, Component.translatable("screen.noirm.hound.traits"), Theme.TEXT_DIM);
        int tx = lx;
        for (String synced : sync.traits()) {
            boolean weak = synced.startsWith("!"); // a weakness (marked by the server)
            String trait = weak ? synced.substring(1) : synced;
            String label = Component.translatable("hound_trait.noirm." + trait).getString();
            int cwChip = font.width(label) + 6;
            if (tx + cwChip > lx + viewW && tx > lx) {
                tx = lx;
                ly += 12;
            }
            Ui.chip(g, font, tx, ly, Ui.fit(font, label, viewW - 6), weak ? Theme.RED : Theme.GOLD);
            if (Ui.in(mouseX, mouseY, tx, ly, cwChip, 11)) {
                tip = Component.translatable("hound_trait.noirm." + trait + ".desc");
            }
            tx += cwChip + Theme.S2;
        }
        ly += 12 + Theme.S3;
        if (level <= HoundPackets.COLOUR_PICK_LEVEL && ly + 10 <= bottom) {
            // The coat colour can still be picked while you bond.
            int sw = Math.max(4, Math.min(8, (viewW + 1) / 16 - 1));
            for (int i = 0; i < HoundLook.COLOURS.length; i++) {
                int bx = lx + (i % 16) * (sw + 1);
                int by = ly + (i / 16) * (sw + 1);
                if (by + sw > bottom) {
                    break;
                }
                boolean on = look.base() == i;
                boolean over = Ui.in(mouseX, mouseY, bx, by, sw, sw);
                g.fill(bx - 1, by - 1, bx + sw + 1, by + sw + 1, on ? Theme.SEAL : over ? Theme.IRON : Theme.HAIRLINE);
                g.fill(bx, by, bx + sw, by + sw, 0xFF000000 | HoundLook.RGB[i]);
                Component colourName = Component.translatable("screen.noirm.hound.pick_colour",
                        Component.translatable("hound.noirm.colour." + HoundLook.COLOURS[i]));
                hits.add(new Hit(bx, by, sw, sw, "colour:" + i, colourName));
            }
        }

        // ---- right: growth, abilities, orders
        int x = lx + viewW + Theme.S5;
        int cw = left + w - PAD - x;
        int y = y0;
        int next = level + 1;
        y = growthRow(g, x, y, cw, "xp", sync.xp() - HoundGrowth.xpFor(level), HoundGrowth.xpFor(next) - HoundGrowth.xpFor(level),
                Math.round(sync.xp()) + "/" + Math.round(HoundGrowth.xpFor(next)), Theme.GOLD);
        y = growthRow(g, x, y, cw, "bond", sync.bond(), Math.max(1f, HoundGrowth.bondFor(next)), Math.round(sync.bond()) + "/100", Theme.SEAL);
        y = growthRow(g, x, y, cw, "power", sync.ownerPower(), HoundGrowth.powerFor(next),
                String.format("%.0f/%.0f", sync.ownerPower(), HoundGrowth.powerFor(next)), Theme.CHAKRA);
        Component blocker = Hounds.blocker(next, sync.xp(), sync.bond(), sync.ownerPower());
        Component nextLine = blocker == null ? Component.translatable("screen.noirm.hound.next_ready", next)
                : Component.translatable("screen.noirm.hound.next_needs", next, blocker);
        Ui.text(g, font, nextLine, x, y, cw, blocker == null ? Theme.GREEN : Theme.TEXT_DIM);
        if (Ui.in(mouseX, mouseY, x, y0, cw, y + 10 - y0)) {
            tip = Component.translatable("screen.noirm.hound.growth.desc");
        }
        y += 11 + Theme.S2;

        // Abilities: lit when it has them, faint with the level they unlock at.
        y = Ui.section(g, font, x, y, cw, Component.translatable("screen.noirm.hound.abilities"), Theme.TEXT_DIM);
        int ax = x;
        boolean inuzuka = sync.blood() == 1;
        boolean small = HoundGrowth.canBeCarried(level, hound == null ? 1f : hound.size());
        for (Ability a : ABILITIES) {
            if (a.inuzuka() && !inuzuka) {
                continue;
            }
            boolean outgrown = "carry".equals(a.key()) && !small;
            boolean has = level >= a.level() && !outgrown;
            String label = Component.translatable("screen.noirm.hound.ability." + a.key()).getString();
            if (!has && !outgrown) {
                label = Component.translatable("screen.noirm.hound.ability_at", label, a.level()).getString();
            }
            int chipW = font.width(label) + 6;
            if (ax + chipW > x + cw && ax > x) {
                ax = x;
                y += 12;
            }
            int colour = has ? Theme.GREEN : outgrown ? Theme.TEXT_FAINT : Theme.mix(Theme.TEXT_FAINT, Theme.TEXT_DIM, 0.3f);
            Ui.chip(g, font, ax, y, Ui.fit(font, label, cw - 6), colour);
            if (Ui.in(mouseX, mouseY, ax, y, chipW, 11)) {
                Component desc = Component.translatable("screen.noirm.hound.ability." + a.key() + ".desc",
                        Math.round(HoundGrowth.smell(level)));
                tip = has ? desc : outgrown ? Component.translatable("screen.noirm.hound.outgrown", desc)
                        : Component.translatable("screen.noirm.hound.comes_at", desc, a.level());
            }
            ax += chipW + Theme.S2;
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
        if (small) {
            row3.add("carry");
            row3.add("jacket");
        }
        if (HoundGrowth.canBeRidden(level, hound == null ? 1f : hound.size())) {
            row3.add("ride");
        }
        if (sync.contract()) {
            row3.add("recall");
        }
        for (int i = 0; i < row3.size() && y + BUTTON_H <= bottom; i++) {
            String act = row3.get(i);
            boolean on = hound != null && ("carry".equals(act) && hound.isCarried() && hound.carry() == NinjaHound.Carry.HEAD
                    || "jacket".equals(act) && hound.isCarried() && hound.carry() == NinjaHound.Carry.JACKET);
            y = orderButton(g, mouseX, mouseY, x, y, cw, i, row3.size(), "recall".equals(act) && !sync.out() ? "summon" : act, on,
                    i == row3.size() - 1);
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

    /** "Wolfish · grey saddle": the breed and the coat in a few words. */
    private static Component lookLine(HoundLook look) {
        Component colour = Component.translatable("hound.noirm.colour." + HoundLook.COLOURS[look.base()]);
        return Component.translatable("screen.noirm.hound.look_line", Component.translatable("hound.noirm.breed." + look.breed().key()),
                colour, Component.translatable("hound.noirm.pattern." + look.pattern().key()));
    }

    private static Component lookTip(HoundLook look) {
        StringBuilder marks = new StringBuilder();
        for (int i = 0; i < HoundLook.MARK_KEYS.length; i++) {
            if ((look.marks() & (1 << i)) != 0) {
                if (marks.length() > 0) {
                    marks.append(", ");
                }
                marks.append(Component.translatable("hound.noirm.mark." + HoundLook.MARK_KEYS[i]).getString());
            }
        }
        return Component.translatable("screen.noirm.hound.look_tip",
                Component.translatable("hound.noirm.breed." + look.breed().key()),
                Component.translatable("hound.noirm.ears." + look.ears().key()),
                Component.translatable("hound.noirm.tail." + look.tail().key()),
                Component.translatable("hound.noirm.pattern." + look.pattern().key()),
                Component.translatable("hound.noirm.colour." + HoundLook.COLOURS[look.base()]),
                Component.translatable("hound.noirm.colour." + HoundLook.COLOURS[look.second()]),
                marks.length() == 0 ? Component.translatable("hound.noirm.mark.none") : Component.literal(marks.toString()),
                Component.translatable("hound.noirm.eyes." + HoundLook.EYES[look.eyes()]));
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
