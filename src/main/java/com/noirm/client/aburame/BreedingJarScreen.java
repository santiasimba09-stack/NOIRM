package com.noirm.client.aburame;

import com.noirm.aburame.BreedingJarBlockEntity;
import com.noirm.aburame.ColonyPackets;
import com.noirm.aburame.Harm;
import com.noirm.aburame.Species;
import com.noirm.aburame.WildKind;
import com.noirm.client.ui.Theme;
import com.noirm.client.ui.Ui;
import com.noirm.network.NoirmNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The Breeding Jar's screen (docs/ABURAME.md, 2.6): the adaptation to breed (×2), the Antivenom cross,
 * strains to breed in from the Insect Case, and the long tasks (a Kidaichū Egg, a Queen).
 */
public class BreedingJarScreen extends AburameScreen {
    private static final Harm[] FOCUS = {Harm.FIRE, Harm.EXPLOSION, Harm.LIGHTNING, Harm.COLD, Harm.POISON, Harm.WIND};
    private final BlockPos pos;

    public BreedingJarScreen(BlockPos pos) {
        super(Component.translatable("block.noirm.breeding_jar"), 400, 230);
        this.pos = pos;
    }

    private BreedingJarBlockEntity jar() {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(pos) instanceof BreedingJarBlockEntity j ? j : null;
    }

    @Override
    protected void act(String action) {
        NoirmNetwork.sendToServer(new ColonyPackets.Command("jar:" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ":" + action));
    }

    @Override
    protected Component drawContent(GuiGraphics g, int mx, int my, float partial) {
        ColonyPackets.ColonySync c = ClientColony.sync();
        BreedingJarBlockEntity jar = jar();
        int inner = w - PAD * 2;
        int x0 = left + PAD;
        int y = top + PAD;
        Ui.header(g, font, x0, y, inner, "瓶", ColonyHud.TEAL, title, Component.translatable("screen.noirm.jar.generation", c.generation()), Theme.TEXT_DIM);
        y += Theme.ROW + Theme.S4;
        Ui.well(g, x0, y, 70, 70);
        beetle(g, x0 + 35, y + 38, 40f, Species.KIKAICHU, partial);
        int rx = x0 + 70 + Theme.S5;
        int rw = inner - 70 - Theme.S5;
        int ry = Ui.section(g, font, rx, y, rw, Component.translatable("screen.noirm.jar.focus"), Theme.TEXT_DIM);
        int bw = (rw - Theme.S2 * 2) / 3;
        for (int i = 0; i < FOCUS.length; i++) {
            Harm hm = FOCUS[i];
            int bx = rx + (i % 3) * (bw + Theme.S2);
            int by = ry + (i / 3) * 16;
            Component label = Component.translatable("screen.noirm.jar.harm." + hm.key(), Math.round(c.resist(hm) * 100));
            button(g, bx, by, bw, label, "focus:" + hm.key(), c.has(), false, mx, my, Component.translatable("screen.noirm.jar.focus_tip"));
        }
        ry += 34;
        ry = Ui.section(g, font, rx, ry, rw, Component.translatable("screen.noirm.jar.cross"), Theme.TEXT_DIM);
        boolean crossing = jar != null && jar.cross();
        button(g, rx, ry, rw, Component.translatable(crossing ? "screen.noirm.jar.cross_on" : "screen.noirm.jar.cross_off"), "cross", c.has(), crossing, mx, my,
                Component.translatable("screen.noirm.jar.cross_tip", BreedingJarBlockEntity.ANTIVENOM_GENERATIONS));
        ry += 20;
        ry = Ui.section(g, font, rx, ry, rw, Component.translatable("screen.noirm.jar.raise"), Theme.TEXT_DIM);
        boolean busy = jar != null && jar.task() != BreedingJarBlockEntity.Task.NONE;
        int half = (rw - Theme.S3) / 2;
        button(g, rx, ry, half, Component.translatable("screen.noirm.jar.egg"), "egg", c.has() && !busy, false, mx, my,
                Component.translatable("screen.noirm.jar.egg_tip"));
        button(g, rx + half + Theme.S3, ry, half, Component.translatable("screen.noirm.jar.queen"), "queen", c.has() && !busy, false, mx, my,
                Component.translatable("screen.noirm.jar.queen_tip"));
        ry += 18;
        if (busy && Minecraft.getInstance().level != null) {
            long left2 = Math.max(0, jar.taskEnd() - Minecraft.getInstance().level.getGameTime());
            g.drawString(font, Component.translatable("screen.noirm.jar.task_" + jar.task().name().toLowerCase(java.util.Locale.ROOT), left2 / 1200),
                    rx, ry, Theme.AMBER, false);
            ry += 11;
        }
        // strains to breed in
        int sy = Math.max(ry, y + 76);
        sy = Ui.section(g, font, x0, sy, inner, Component.translatable("screen.noirm.jar.strains"), Theme.TEXT_DIM);
        int sx = x0;
        int sw = (inner - Theme.S2 * 3) / 4;
        int n = 0;
        for (WildKind k : WildKind.collection()) {
            if (!c.caught().contains(k.key()) || c.strains().contains(k.key())) {
                continue;
            }
            int bx = sx + (n % 4) * (sw + Theme.S2);
            int by = sy + (n / 4) * 16;
            if (by > top + h - PAD - 14) {
                break;
            }
            button(g, bx, by, sw, Component.translatable(k.translationKey()), "strain:" + k.key(), true, false, mx, my,
                    Component.translatable(k.translationKey() + ".strain"));
            n++;
        }
        if (n == 0) {
            g.drawString(font, Ui.fit(font, Component.translatable("screen.noirm.jar.no_strains").getString(), inner), x0, sy, Theme.TEXT_FAINT, false);
        }
        return null;
    }
}
