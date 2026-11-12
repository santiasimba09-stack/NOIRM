package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.client.ui.Theme;
import com.noirm.combat.ChakraArmour;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.IItemDecorator;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Chakra durability on screen ({@link ChakraArmour}): the tooltip line "Durability 340/528 + 120 chakra"
 * (the chakra part in chakra blue; it replaces vanilla's own durability line when advanced tooltips are
 * on), and a thin chakra-blue line just above vanilla's durability bar on the item in every slot.
 */
public final class ChakraArmourDisplay {
    /** The chakra blue of the slot line and the tooltip part. */
    public static final int BLUE = 0xFF6FC4FF;
    private static final int TRACK = 0xFF0C1A2E;

    private ChakraArmourDisplay() {}

    /** "Durability 340/528 + 120 chakra" for a piece that holds a chakra pool (null otherwise). */
    public static Component line(ItemStack stack) {
        int max = ChakraArmour.poolMax(stack);
        if (max <= 0 || !stack.isDamageableItem()) {
            return null;
        }
        int left = stack.getMaxDamage() - stack.getDamageValue();
        MutableComponent text = Component.translatable("tooltip.noirm.chakra_durability", left, stack.getMaxDamage())
                .withStyle(ChatFormatting.GRAY);
        text.append(Component.literal(" "));
        text.append(Component.translatable("tooltip.noirm.chakra_durability.pool", ChakraArmour.pool(stack))
                .withStyle(Style.EMPTY.withColor(BLUE & 0xFFFFFF)));
        return text;
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ChakraArmourTooltips {
        private ChakraArmourTooltips() {}

        @SubscribeEvent
        public static void tooltip(ItemTooltipEvent event) {
            Component line = line(event.getItemStack());
            if (line == null) {
                return;
            }
            List<Component> lines = event.getToolTip();
            for (int i = 0; i < lines.size(); i++) {
                // Advanced tooltips (F3+H) add vanilla's "Durability: x / y": ours takes its place.
                if (lines.get(i).getContents() instanceof TranslatableContents t && "item.durability".equals(t.getKey())) {
                    lines.set(i, line);
                    return;
                }
            }
            lines.add(Math.min(lines.size(), 1), line);
        }
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ChakraArmourDecorations {
        private ChakraArmourDecorations() {}

        @SubscribeEvent
        public static void register(RegisterItemDecorationsEvent event) {
            IItemDecorator decorator = ChakraArmourDisplay::slotLine;
            for (var item : ForgeRegistries.ITEMS) {
                if (item instanceof ArmorItem || item instanceof com.noirm.gear.GearItem) {
                    event.register(item, decorator);
                }
            }
        }
    }

    /**
     * The slot line: 1 px of chakra blue right above where vanilla draws the durability bar, as long as
     * the pool is full (13 px) and shrinking as it drains, over a dark track.
     */
    static boolean slotLine(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        int max = ChakraArmour.poolMax(stack);
        if (max <= 0) {
            return false;
        }
        int width = Math.round(13f * Math.min(1f, ChakraArmour.pool(stack) / (float) max));
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.fill(RenderType.guiOverlay(), x + 2, y + 12, x + 15, y + 13, TRACK);
        if (width > 0) {
            graphics.fill(RenderType.guiOverlay(), x + 2, y + 12, x + 2 + width, y + 13, BLUE);
        }
        graphics.pose().popPose();
        return false;
    }

    /** Theme hook for the HUD (kept here so the HUD needs no item maths). */
    static int hudColor() {
        return Theme.CHAKRA_LIGHT;
    }
}
