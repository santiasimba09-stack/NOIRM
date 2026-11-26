package com.noirm.combat;

import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.weapon.Moveset;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Which fighting style someone uses, and which they know.
 *
 * <p><b>Learning.</b> The old styles are free once you meet their requirements (Strong Fist needs
 * taijutsu, the Gentle Fist the Byakugan). The newer ones ({@code "learn": {"required": true}}) are
 * learned in play: from a master's lesson, a style scroll found as loot, watching or sparring a
 * ninja who fights that way ({@link StyleLearning}), or a clan grant through {@link #grant}. A
 * learned style ignores its taijutsu requirement (not its eyes).
 *
 * <p><b>Choosing.</b> The style key cycles your unarmed styles; the style picker
 * ({@code client.StylePickerScreen}) shows them all and also picks a <b>weapon style</b> per weapon
 * moveset: an alternative to that weapon's own moves (a katana held in iai, a reverse-grip tantō…).
 * Ninjas roll a style when they spawn, favouring their clan's and their class's.
 */
public final class StyleSystem {
    public static final ResourceLocation GENTLE_FIST = com.noirm.Noirm.id("gentle_fist");

    /** How a style was learned (kept in {@link StyleBook}; the picker shows it). */
    public static final String MASTER = "master", SCROLL = "scroll", WATCH = "watch", CLAN = "clan", COMMAND = "command";

    private StyleSystem() {}

    public static String translationKey(ResourceLocation style) {
        return "style." + style.getNamespace() + "." + style.getPath();
    }

    /** One line saying how the style plays: {@code style.<ns>.<path>.desc}. */
    public static String descriptionKey(ResourceLocation style) {
        return translationKey(style) + ".desc";
    }

    /** A player's style: their choice if it still exists and they may use it, else the Gentle Fist for the Hyūga, else the basic one. */
    public static ResourceLocation styleOf(ShinobiData data) {
        String chosen = data.getCombatStyle();
        ResourceLocation id = chosen.isEmpty() ? null : ResourceLocation.tryParse(chosen);
        CombatStyle style = id == null ? null : CombatData.style(id);
        if (style != null && !style.isWeaponStyle() && (!style.learn().required() || knows(data, id))) {
            return id;
        }
        if (chosen.isEmpty() && data.identity().hasByakugan() && CombatData.style(GENTLE_FIST) != null) {
            return GENTLE_FIST; // the Hyūga grow up with the Gentle Fist
        }
        return CombatData.DEFAULT_STYLE;
    }

    /** Whether {@code data}'s owner learned {@code style} (in play, or a master granted it by their test). */
    public static boolean knows(ShinobiData data, ResourceLocation style) {
        return data.styles().knows(style.toString()) || data.masters().hasStyle(style.toString());
    }

    /** Whether {@code data} may fight with {@code style} now (learned if it must be, and the requirements met). */
    public static boolean canUse(ShinobiData data, ResourceLocation id, CombatStyle style) {
        if (style == null || !style.playable()) {
            return false;
        }
        boolean known = knows(data, id);
        if (style.learn().required() && !known) {
            return false;
        }
        java.util.Optional<String> missing = style.missingRequirement(data.getTaijutsu(), data.identity().hasByakugan());
        // A learned (or master-granted) style ignores its taijutsu requirement, never its eyes.
        return missing.isEmpty() || (known && "taijutsu".equals(missing.get()));
    }

    /** Why {@code data} can't use {@code style} yet ({@code learn}, {@code taijutsu}, {@code byakugan}), or "" if they can. */
    public static String blocker(ShinobiData data, ResourceLocation id, CombatStyle style) {
        if (style.learn().required() && !knows(data, id)) {
            return "learn";
        }
        return canUse(data, id, style) ? "" : style.missingRequirement(data.getTaijutsu(), data.identity().hasByakugan()).orElse("");
    }

    /** The unarmed styles {@code data} may use, the basic one first. */
    public static List<ResourceLocation> available(ShinobiData data) {
        List<ResourceLocation> list = new ArrayList<>();
        for (Map.Entry<ResourceLocation, CombatStyle> entry : CombatData.styles().entrySet()) {
            CombatStyle style = entry.getValue();
            if (!style.isWeaponStyle() && canUse(data, entry.getKey(), style)) {
                list.add(entry.getKey());
            }
        }
        list.sort(Comparator.comparing((ResourceLocation id) -> !id.equals(CombatData.DEFAULT_STYLE)).thenComparing(ResourceLocation::toString));
        return list;
    }

    /** The weapon styles for {@code moveset} that {@code data} may use (not counting the weapon's own). */
    public static List<ResourceLocation> weaponStyles(ShinobiData data, String moveset) {
        List<ResourceLocation> list = new ArrayList<>();
        for (Map.Entry<ResourceLocation, CombatStyle> entry : CombatData.styles().entrySet()) {
            CombatStyle style = entry.getValue();
            if (style.weapons().contains(moveset) && canUse(data, entry.getKey(), style)) {
                list.add(entry.getKey());
            }
        }
        list.sort(Comparator.comparing(ResourceLocation::toString));
        return list;
    }

    /**
     * The weapon style {@code entity} fights with for {@code moveset}, or null for the weapon's own.
     * Players: their pick (if they still may use it). Ninjas who fight in a style beyond the Academy's: a
     * weapon style rolled once from their UUID (so it stays the same), or the weapon's own.
     */
    public static ResourceLocation weaponStyle(LivingEntity entity, Moveset moveset) {
        if (moveset == null) {
            return null;
        }
        if (entity instanceof Player player) {
            ShinobiData data = ShinobiCapability.get(player).orElse(null);
            if (data == null) {
                return null;
            }
            ResourceLocation pick = ResourceLocation.tryParse(data.styles().weaponPick(moveset.key()));
            CombatStyle style = pick == null ? null : CombatData.style(pick);
            return style != null && style.weapons().contains(moveset.key()) && canUse(data, pick, style) ? pick : null;
        }
        if (!(entity instanceof CombatStats stats) || stats.combatStyle() == null || CombatData.DEFAULT_STYLE.equals(stats.combatStyle())) {
            return null; // Academy-trained ninjas (and beasts, clones) use a weapon's standard moves
        }
        int total = 0;
        List<Map.Entry<ResourceLocation, CombatStyle>> options = new ArrayList<>();
        for (Map.Entry<ResourceLocation, CombatStyle> entry : CombatData.styles().entrySet()) {
            if (entry.getValue().weapons().contains(moveset.key()) && entry.getValue().npcWeight() > 0) {
                options.add(entry);
                total += entry.getValue().npcWeight();
            }
        }
        if (options.isEmpty()) {
            return null;
        }
        options.sort(Map.Entry.comparingByKey());
        int own = NPC_OWN_WEAPON_WEIGHT; // the weapon's own moves stay the most common
        long seed = entity.getUUID().getLeastSignificantBits() ^ moveset.key().hashCode();
        int roll = (int) Math.floorMod(seed * 0x5DEECE66DL + 11, (long) (total + own));
        for (Map.Entry<ResourceLocation, CombatStyle> entry : options) {
            roll -= entry.getValue().npcWeight();
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** How strongly ninjas prefer a weapon's own moves over its alternative styles (weight, against each style's {@code npc_weight}). */
    public static final int NPC_OWN_WEAPON_WEIGHT = 4;

    // ---------------------------------------------------------------- learning (the hook other systems use)

    /**
     * Teaches {@code player} a style. {@code how} is {@link #MASTER}, {@link #SCROLL}, {@link #WATCH},
     * {@link #CLAN} or {@link #COMMAND}. Returns true if it was new. Clans grant their styles with
     * this ({@code StyleSystem.grant(player, id, StyleSystem.CLAN)}).
     */
    public static boolean grant(Player player, ResourceLocation style, String how) {
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (data == null || CombatData.style(style) == null || !data.styles().learn(style.toString(), how)) {
            return false;
        }
        data.markDirty();
        data.markPowersDirty();
        player.sendSystemMessage(Component.translatable("message.noirm.style.learned." + how,
                Component.translatable(translationKey(style)).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.AQUA));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.6f);
        return true;
    }

    /** {@link #grant} with "clan": the hook for the clan system. */
    public static boolean grant(Player player, ResourceLocation style) {
        return grant(player, style, CLAN);
    }

    /** A style a found style scroll turns out to be: one {@code data} doesn't know, by the styles' {@code scroll} weights; null if none is left. */
    public static ResourceLocation scrollStyle(ShinobiData data, RandomSource random) {
        int total = 0;
        List<Map.Entry<ResourceLocation, CombatStyle>> options = new ArrayList<>();
        for (Map.Entry<ResourceLocation, CombatStyle> entry : CombatData.styles().entrySet()) {
            CombatStyle style = entry.getValue();
            if (style.learn().required() && style.learn().scroll() > 0 && !knows(data, entry.getKey())) {
                options.add(entry);
                total += style.learn().scroll();
            }
        }
        if (total <= 0) {
            return null;
        }
        options.sort(Map.Entry.comparingByKey());
        int roll = random.nextInt(total);
        for (Map.Entry<ResourceLocation, CombatStyle> entry : options) {
            roll -= entry.getValue().learn().scroll();
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- choosing

    /** The style key: on to the next unarmed style this player can use. */
    public static void cycle(Player player) {
        ShinobiCapability.get(player).ifPresent(data -> {
            List<ResourceLocation> styles = available(data);
            if (styles.isEmpty()) {
                return;
            }
            ResourceLocation current = styleOf(data); // their own style, not a weapon's
            int index = styles.indexOf(current);
            choose(player, styles.get((index + 1) % styles.size()));
        });
    }

    /**
     * Makes {@code id} the player's active style: an unarmed style becomes their style; a weapon
     * style becomes their pick for every weapon it fights with. False if they can't use it.
     */
    public static boolean choose(Player player, ResourceLocation id) {
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        CombatStyle style = id == null ? null : CombatData.style(id);
        if (data == null || !canUse(data, id, style)) {
            return false;
        }
        if (style.isWeaponStyle()) {
            style.weapons().forEach(moveset -> data.styles().setWeaponPick(moveset, id.toString()));
        } else {
            data.setCombatStyle(id.toString());
        }
        data.markDirty();
        player.displayClientMessage(Component.translatable("message.noirm.style.now",
                Component.translatable(translationKey(id)).withStyle(ChatFormatting.GOLD)), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.8f, 1.2f);
        return true;
    }

    /** Back to a weapon's own moves for {@code moveset} (the picker's "own moves"). */
    public static void clearWeaponPick(Player player, String moveset) {
        ShinobiCapability.get(player).ifPresent(data -> {
            data.styles().setWeaponPick(moveset, "");
            data.markDirty();
        });
    }

    /** Taijutsu just rose to {@code value}: announce any free style it opens up. */
    public static void onTaijutsuUp(Player player, float value) {
        for (Map.Entry<ResourceLocation, CombatStyle> entry : CombatData.styles().entrySet()) {
            float needed = entry.getValue().taijutsu();
            if (entry.getValue().playable() && !entry.getValue().learn().required() && needed > 0 && value >= needed && value - 1 < needed) {
                player.sendSystemMessage(Component.translatable("message.noirm.style.unlocked",
                        Component.translatable(translationKey(entry.getKey()))).withStyle(ChatFormatting.AQUA));
            }
        }
    }

    // ---------------------------------------------------------------- ninjas

    /** A style for a ninja: the Gentle Fist for Byakugan users, otherwise one it's strong enough for. */
    public static ResourceLocation pickForNpc(RandomSource random, float taijutsu, boolean byakugan) {
        return pickForNpc(random, taijutsu, byakugan, "", null);
    }

    /**
     * A style for a ninja: the Gentle Fist for Byakugan users; otherwise a weighted roll among the
     * unarmed styles it's strong enough for ({@code npc_weight}), plus each style's bonus for the
     * ninja's clan ({@code npc.clans}), leaving out styles for stronger classes ({@code npc.min_class}).
     */
    public static ResourceLocation pickForNpc(RandomSource random, float taijutsu, boolean byakugan, String clan,
                                              com.noirm.bounty.NinjaClass ninjaClass) {
        if (byakugan && CombatData.style(GENTLE_FIST) != null) {
            return GENTLE_FIST;
        }
        int total = 0;
        List<Map.Entry<ResourceLocation, CombatStyle>> options = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        for (Map.Entry<ResourceLocation, CombatStyle> entry : CombatData.styles().entrySet()) {
            CombatStyle style = entry.getValue();
            int weight = npcWeight(style, clan, ninjaClass);
            if (weight > 0 && !style.isWeaponStyle() && style.playable() && style.missingRequirement(taijutsu, false).isEmpty()) {
                options.add(entry);
                weights.add(weight);
            }
        }
        // Sorted for a stable roll (the map's order isn't).
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparing(i -> options.get(i).getKey().toString()));
        for (int w : weights) {
            total += w;
        }
        if (total <= 0) {
            return CombatData.DEFAULT_STYLE;
        }
        int roll = random.nextInt(total);
        for (int i : order) {
            roll -= weights.get(i);
            if (roll < 0) {
                return options.get(i).getKey();
            }
        }
        return CombatData.DEFAULT_STYLE;
    }

    /** A style's weight for a ninja of {@code clan} and {@code ninjaClass}: 0 if it's out of their reach. */
    public static int npcWeight(CombatStyle style, String clan, com.noirm.bounty.NinjaClass ninjaClass) {
        String min = style.npc().minClass();
        if (!min.isEmpty() && ninjaClass != null) {
            com.noirm.bounty.NinjaClass needed = com.noirm.bounty.NinjaClass.byName(min);
            if (needed != null && ninjaClass.ordinal() < needed.ordinal()) {
                return 0;
            }
        }
        int bonus = clan == null || clan.isEmpty() ? 0 : style.npc().clans().getOrDefault(clan, 0);
        return style.npcWeight() + bonus;
    }

    /** The style and identity a fighter is using right now. */
    public static StyleIdentity identityOf(LivingEntity entity) {
        CombatStyle style = CombatData.style(CombatSystem.styleFor(entity));
        return style == null ? StyleIdentity.DEFAULT : style.identity();
    }
}
