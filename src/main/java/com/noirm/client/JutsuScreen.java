package com.noirm.client;

import com.noirm.clan.Nature;
import com.noirm.dojutsu.Sharingan;
import com.noirm.jutsu.JutsuBook;
import com.noirm.network.NoirmNetwork;
import com.noirm.network.PowerPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The jutsu screen (J): every jutsu you know on the left, your 4 loadout slots on the right.
 * Click a jutsu, then a slot to put it there. Click a slot with nothing selected to empty it.
 */
public class JutsuScreen extends Screen {
    private ResourceLocation selected;
    private PowerPackets.SyncPowers shown;

    public JutsuScreen() {
        super(Component.translatable("screen.noirm.jutsu"));
    }

    @Override
    protected void init() {
        shown = ClientPowers.get();
        int left = width / 2 - 205;
        int right = width / 2 + 15;
        int top = 62;
        List<PowerPackets.JutsuEntry> learned = ClientPowers.learned();
        for (int i = 0; i < learned.size() && top + i * 22 < height - 40; i++) {
            PowerPackets.JutsuEntry entry = learned.get(i);
            addRenderableWidget(Button.builder(label(entry), b -> selected = entry.id())
                    .bounds(left, top + i * 22, 190, 20).build());
        }
        for (int slot = 0; slot < JutsuBook.SLOTS; slot++) {
            final int index = slot;
            String id = shown == null ? "" : shown.loadout().get(slot);
            PowerPackets.JutsuEntry entry = ClientPowers.entry(id);
            Component text = Component.literal((slot + 1) + ": ").append(entry == null
                    ? Component.translatable("hud.noirm.jutsu.empty").withStyle(ChatFormatting.GRAY)
                    : Component.translatable(Sharingan.jutsuKey(entry.id())));
            addRenderableWidget(Button.builder(text, b -> {
                NoirmNetwork.sendToServer(new PowerPackets.EquipJutsu(index, selected == null ? "" : selected.toString()));
                selected = null;
            }).bounds(right, top + slot * 24, 190, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    private static Component label(PowerPackets.JutsuEntry entry) {
        MutableComponent text = Component.translatable(Sharingan.jutsuKey(entry.id()));
        if (entry.nature() >= 0) {
            int color = Nature.values()[entry.nature()].color();
            text = text.withStyle(s -> s.withColor(color));
        }
        text.append(Component.literal(" [" + entry.rank() + "] ★" + entry.mastery()).withStyle(ChatFormatting.GRAY));
        if (entry.copied()) {
            text.append(Component.literal(" ◉").withStyle(ChatFormatting.RED));
        }
        return text;
    }

    @Override
    public void tick() {
        if (ClientPowers.get() != shown) {
            clearWidgets();
            init();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        PowerPackets.SyncPowers powers = ClientPowers.get();
        if (powers != null) {
            MutableComponent natures = Component.empty();
            for (int i = 0; i < powers.natures().size(); i++) {
                Nature nature = Nature.values()[powers.natures().get(i)];
                if (i > 0) {
                    natures.append(", ");
                }
                natures.append(Component.translatable(nature.translationKey()).withStyle(s -> s.withColor(nature.color())));
            }
            graphics.drawCenteredString(font, Component.translatable("screen.noirm.jutsu.clan", powers.clanName(), natures), width / 2, 24, 0xFFE0B050);
        }
        graphics.drawString(font, Component.translatable("screen.noirm.jutsu.known"), width / 2 - 205, 50, 0xFFAAAAAA);
        graphics.drawString(font, Component.translatable("screen.noirm.jutsu.loadout"), width / 2 + 15, 50, 0xFFAAAAAA);
        Component hint = selected == null ? Component.translatable("screen.noirm.jutsu.hint")
                : Component.translatable("screen.noirm.jutsu.selected", Component.translatable(Sharingan.jutsuKey(selected)));
        graphics.drawCenteredString(font, hint, width / 2, height - 44, selected == null ? 0xFF909090 : 0xFF60D0FF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
