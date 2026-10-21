package com.noirm.creator;

import net.minecraft.nbt.CompoundTag;

/**
 * One roll of the "genetics" dice on the creation screen: a clan (from this world's clan list)
 * and the blank starting values of the five rolled stats, before the clan's traits change them.
 * Reserves, taijutsu, speed, chakra control and willpower; vitality isn't rolled (it starts at 10).
 */
public record GeneticRoll(String clanId, int reserves, int taijutsu, int speed, int control, int willpower) {

    public int[] stats() {
        return new int[]{reserves, taijutsu, speed, control, willpower};
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Clan", clanId);
        tag.putIntArray("Stats", stats());
        return tag;
    }

    public static GeneticRoll load(CompoundTag tag) {
        int[] s = tag.getIntArray("Stats");
        int[] v = new int[5];
        for (int i = 0; i < 5; i++) {
            v[i] = i < s.length ? s[i] : CharacterCreator.STAT_MIN;
        }
        return new GeneticRoll(tag.getString("Clan"), v[0], v[1], v[2], v[3], v[4]);
    }
}
