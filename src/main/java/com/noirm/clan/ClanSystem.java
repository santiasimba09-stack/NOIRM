package com.noirm.clan;

import com.noirm.jutsu.JutsuData;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Joining a clan: stats, natures, starting jutsu. */
public final class ClanSystem {
    private ClanSystem() {}

    /**
     * Makes the player a member of {@code clan}: resets their stats to the clan's, gives its
     * natures and the starter jutsu for them, and fills the loadout. Previous jutsu and Sharingan
     * progress are cleared (this is for new characters and the test command).
     */
    public static void assign(Player player, ShinobiData data, Clan clan) {
        ClanIdentity identity = data.identity();
        identity.setFrom(clan);
        data.setMaxChakra(ShinobiData.DEFAULT_MAX_CHAKRA + (identity.has(ClanTrait.DEEP_RESERVES) ? 30 : 0));
        data.setMaxStamina(ShinobiData.DEFAULT_MAX_STAMINA + (identity.has(ClanTrait.ENDURANCE) ? 30 : 0));
        data.setSpeed(ShinobiData.DEFAULT_STAT + clan.speedBonus() + (identity.has(ClanTrait.SWIFT) ? 3 : 0));
        data.setTaijutsu(ShinobiData.DEFAULT_STAT + clan.taijutsuBonus() + (identity.has(ClanTrait.FIERCE) ? 3 : 0));
        data.refill();
        data.eyes().setSharinganStage(0);
        data.eyes().setSharinganXp(0);

        data.jutsu().clear();
        List<ResourceLocation> starters = new ArrayList<>();
        clan.natures().entrySet().stream()
                .sorted(Map.Entry.<Nature, Float>comparingByValue(Comparator.reverseOrder()))
                .forEach(e -> starters.addAll(JutsuData.startersFor(e.getKey())));
        starters.addAll(JutsuData.startersFor(null));
        for (ResourceLocation id : starters) {
            data.jutsu().learn(id, false);
            data.jutsu().equipIfRoom(id);
        }
        data.markDirty();
        data.markPowersDirty();
    }

    /** "You were born into the … clan" plus natures and traits, for chat. */
    public static Component describe(ClanIdentity identity) {
        MutableComponent text = Component.translatable("message.noirm.clan.member",
                Component.literal(identity.clanName()).withStyle(identity.isCanon() ? ChatFormatting.RED : ChatFormatting.GOLD));
        text.append("\n").append(Component.translatable("message.noirm.clan.natures", natures(identity)));
        MutableComponent traits = Component.empty();
        boolean first = true;
        for (ClanTrait trait : identity.traits()) {
            if (!first) {
                traits.append(", ");
            }
            traits.append(Component.translatable(trait.translationKey()).withStyle(ChatFormatting.AQUA));
            first = false;
        }
        text.append("\n").append(Component.translatable("message.noirm.clan.traits", traits));
        if (identity.hasSharingan()) {
            text.append("\n").append(Component.translatable("message.noirm.clan.sharingan_blood").withStyle(ChatFormatting.DARK_RED));
        }
        return text;
    }

    public static Component natures(ClanIdentity identity) {
        MutableComponent text = Component.empty();
        boolean first = true;
        for (Map.Entry<Nature, Float> entry : identity.natures().entrySet()) {
            if (!first) {
                text.append(", ");
            }
            String strength = entry.getValue() >= Clan.PRIMARY_AFFINITY ? "nature.noirm.strength.primary"
                    : entry.getValue() >= Clan.SECONDARY_AFFINITY ? "nature.noirm.strength.secondary" : "nature.noirm.strength.trained";
            text.append(Component.translatable(entry.getKey().translationKey()).withStyle(s -> s.withColor(entry.getKey().color())))
                    .append(" (").append(Component.translatable(strength)).append(")");
            first = false;
        }
        return text;
    }
}
