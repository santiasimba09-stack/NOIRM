package com.noirm.creator;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * One roll of the "genetics" dice on the creation screen: a clan (from this world's clan list),
 * the blank starting values of the five rolled stats (before the clan's traits and perks change
 * them: reserves, taijutsu, speed, chakra control and willpower; vitality isn't rolled), and the
 * character's personal talents (biased by the clan, {@code com.noirm.traits.Talents#roll}).
 */
public record GeneticRoll(String clanId, int reserves, int taijutsu, int speed, int control, int willpower, List<String> talents) {

    public GeneticRoll(String clanId, int reserves, int taijutsu, int speed, int control, int willpower) {
        this(clanId, reserves, taijutsu, speed, control, willpower, List.of());
    }

    public int[] stats() {
        return new int[]{reserves, taijutsu, speed, control, willpower};
    }

    public GeneticRoll withTalents(List<String> talents) {
        return new GeneticRoll(clanId, reserves, taijutsu, speed, control, willpower, List.copyOf(talents));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Clan", clanId);
        tag.putIntArray("Stats", stats());
        ListTag list = new ListTag();
        talents.forEach(t -> list.add(StringTag.valueOf(t)));
        tag.put("Talents", list);
        return tag;
    }

    public static GeneticRoll load(CompoundTag tag) {
        int[] s = tag.getIntArray("Stats");
        int[] v = new int[5];
        for (int i = 0; i < 5; i++) {
            v[i] = i < s.length ? s[i] : CharacterCreator.STAT_MIN;
        }
        List<String> talents = new ArrayList<>();
        for (Tag t : tag.getList("Talents", Tag.TAG_STRING)) {
            talents.add(t.getAsString());
        }
        return new GeneticRoll(tag.getString("Clan"), v[0], v[1], v[2], v[3], v[4], List.copyOf(talents));
    }
}
