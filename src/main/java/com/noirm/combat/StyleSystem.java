package com.noirm.combat;

import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Which fighting style someone uses. Players switch with the style key between the ones they
 * qualify for (Strong Fist needs taijutsu, the Gentle Fist needs the Byakugan); ninjas get one
 * when they spawn.
 */
public final class StyleSystem {
    public static final ResourceLocation GENTLE_FIST = com.noirm.Noirm.id("gentle_fist");

    private StyleSystem() {}

    public static String translationKey(ResourceLocation style) {
        return "style." + style.getNamespace() + "." + style.getPath();
    }

    /** A player's style: their choice if it still exists, else the Gentle Fist for the Hyūga, else the basic one. */
    public static ResourceLocation styleOf(ShinobiData data) {
        String chosen = data.getCombatStyle();
        ResourceLocation id = chosen.isEmpty() ? null : ResourceLocation.tryParse(chosen);
        if (id != null && CombatData.style(id) != null) {
            return id;
        }
        if (chosen.isEmpty() && data.identity().hasByakugan() && CombatData.style(GENTLE_FIST) != null) {
            return GENTLE_FIST; // the Hyūga grow up with the Gentle Fist
        }
        return CombatData.DEFAULT_STYLE;
    }

    /** The styles {@code data} qualifies for, the basic one first. */
    public static List<ResourceLocation> available(ShinobiData data) {
        List<ResourceLocation> list = new ArrayList<>();
        for (Map.Entry<ResourceLocation, CombatStyle> entry : CombatData.styles().entrySet()) {
            CombatStyle style = entry.getValue();
            if (style.playable() && style.missingRequirement(data.getTaijutsu(), data.identity().hasByakugan()).isEmpty()) {
                list.add(entry.getKey());
            }
        }
        list.sort(Comparator.comparing((ResourceLocation id) -> !id.equals(CombatData.DEFAULT_STYLE)).thenComparing(ResourceLocation::toString));
        return list;
    }

    /** The style key: on to the next style this player can use. */
    public static void cycle(Player player) {
        ShinobiCapability.get(player).ifPresent(data -> {
            List<ResourceLocation> styles = available(data);
            if (styles.isEmpty()) {
                return;
            }
            ResourceLocation current = styleOf(data); // their own style, not a weapon's
            int index = styles.indexOf(current);
            ResourceLocation next = styles.get((index + 1) % styles.size());
            data.setCombatStyle(next.toString());
            data.markDirty();
            player.displayClientMessage(Component.translatable("message.noirm.style.now",
                    Component.translatable(translationKey(next)).withStyle(ChatFormatting.GOLD)), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.8f, 1.2f);
        });
    }

    /** Taijutsu just rose to {@code value}: announce any style it opens up. */
    public static void onTaijutsuUp(Player player, float value) {
        for (Map.Entry<ResourceLocation, CombatStyle> entry : CombatData.styles().entrySet()) {
            float needed = entry.getValue().taijutsu();
            if (entry.getValue().playable() && needed > 0 && value >= needed && value - 1 < needed) {
                player.sendSystemMessage(Component.translatable("message.noirm.style.unlocked",
                        Component.translatable(translationKey(entry.getKey()))).withStyle(ChatFormatting.AQUA));
            }
        }
    }

    /** A style for a ninja: the Gentle Fist for Byakugan users, otherwise one it's strong enough for. */
    public static ResourceLocation pickForNpc(RandomSource random, float taijutsu, boolean byakugan) {
        if (byakugan && CombatData.style(GENTLE_FIST) != null) {
            return GENTLE_FIST;
        }
        int total = 0;
        List<Map.Entry<ResourceLocation, CombatStyle>> options = new ArrayList<>();
        for (Map.Entry<ResourceLocation, CombatStyle> entry : CombatData.styles().entrySet()) {
            CombatStyle style = entry.getValue();
            if (style.npcWeight() > 0 && style.missingRequirement(taijutsu, false).isEmpty()) {
                options.add(entry);
                total += style.npcWeight();
            }
        }
        options.sort(Map.Entry.comparingByKey());
        if (total <= 0) {
            return CombatData.DEFAULT_STYLE;
        }
        int roll = random.nextInt(total);
        for (Map.Entry<ResourceLocation, CombatStyle> entry : options) {
            roll -= entry.getValue().npcWeight();
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return CombatData.DEFAULT_STYLE;
    }
}
