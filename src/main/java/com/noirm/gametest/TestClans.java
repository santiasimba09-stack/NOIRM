package com.noirm.gametest;

import com.noirm.clan.AwakeningPath;
import com.noirm.clan.Clan;
import com.noirm.clan.ClanTrait;
import com.noirm.clan.Nature;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Fixed clans for tests, so results don't depend on random rolls. */
final class TestClans {
    /** Water, no traits, no kekkei genkai. */
    static final Clan PLAIN = clan("test_plain", Map.of(Nature.WATER, Clan.SECONDARY_AFFINITY), Optional.empty(), AwakeningPath.WILL);
    /** Fire (strong) and lightning, no traits. */
    static final Clan FIRE = clan("test_fire", Map.of(Nature.FIRE, Clan.PRIMARY_AFFINITY, Nature.LIGHTNING, Clan.SECONDARY_AFFINITY), Optional.empty(), AwakeningPath.WILL);
    /** Like the Uchiha but without traits: fire + Sharingan. */
    static final Clan SHARINGAN = clan("test_sharingan", Map.of(Nature.FIRE, Clan.PRIMARY_AFFINITY), Optional.of("sharingan"), AwakeningPath.SHARINGAN);

    private TestClans() {}

    static Clan clan(String id, Map<Nature, Float> natures, Optional<String> kekkeiGenkai, AwakeningPath path) {
        return new Clan(id, id, false, natures, List.<ClanTrait>of(), 0, 0, kekkeiGenkai, path, 1);
    }
}
