package com.noirm.aburame;

import java.util.List;
import java.util.Random;

/**
 * The Nest: what an Aburame is born with (docs/ABURAME.md, 2.8), rolled with the clan in the creator.
 * It's worked out from the clan roll itself (the clan, the rolled stats and talents), so the screen
 * and the server always agree, and rerolling the clan rerolls the nest too.
 *
 * @param colony    starting kikaichū, {@value #MIN_COLONY}–{@value #MAX_COLONY}
 * @param rinkaichu born of the rinkaichū line (1 in {@value #RINKAICHU_ODDS})
 * @param temperament 0 calm (+10 Vigour at the start), 1 restless (+10% breeding)
 */
public record NestRoll(int colony, boolean rinkaichu, int temperament) {
    public static final int MIN_COLONY = 2400;
    public static final int MAX_COLONY = 3600;
    public static final int RINKAICHU_ODDS = 12;
    /** A restless colony breeds this much faster. */
    public static final float RESTLESS_BREEDING = 1.10f;

    public static NestRoll of(String clanId, int[] stats, List<String> talents) {
        long seed = 0x4E455354L ^ clanId.hashCode();
        for (int s : stats) {
            seed = seed * 31 + s;
        }
        for (String t : talents) {
            seed = seed * 31 + t.hashCode();
        }
        return of(seed);
    }

    public static NestRoll of(long seed) {
        Random random = new Random(seed);
        int colony = MIN_COLONY + random.nextInt(MAX_COLONY - MIN_COLONY + 1);
        boolean rinkaichu = random.nextInt(RINKAICHU_ODDS) == 0;
        int temperament = random.nextInt(2);
        return new NestRoll(colony / 10 * 10, rinkaichu, temperament);
    }
}
