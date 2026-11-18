package com.noirm.traits;

import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.clan.ClanData;
import com.noirm.entity.SparringNinja;
import com.noirm.player.ShinobiCapability;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Set;

/**
 * Clan secret techniques ({@code data/noirm/jutsu/clan/}): each clan file lists its own under
 * {@code "clan_jutsu"}. Members are born knowing them; nobody else can use them (a scroll or a
 * command may teach one, but it won't answer outside the blood).
 */
public final class ClanJutsu {
    /** Where clan jutsu live. */
    public static final String PATH = "clan/";
    /** Sealing techniques (besides anything with "seal" in its id): the Uzumaki's art. */
    public static final Set<ResourceLocation> SEALING = Set.of(Noirm.id("clan/adamantine_sealing_chains"));

    private ClanJutsu() {}

    public static boolean isClanJutsu(ResourceLocation id) {
        return id != null && id.getPath().startsWith(PATH);
    }

    /** The clan jutsu of a clan id (canon clans; generated clans have none). */
    public static List<ResourceLocation> of(String clanId) {
        return ClanData.canon(clanId).map(Clan::clanJutsu).orElse(List.of());
    }

    /** Whether a clan id may use {@code id}. Anything outside {@code jutsu/clan/} is open to all. */
    public static boolean allowedFor(String clanId, ResourceLocation id) {
        return !isClanJutsu(id) || of(clanId).contains(id);
    }

    /** Whether {@code caster} may use {@code id}: players by their clan's list, ninjas by their clan. */
    public static boolean allowed(LivingEntity caster, ResourceLocation id) {
        if (!isClanJutsu(id)) {
            return true;
        }
        if (caster instanceof Player player) {
            return ShinobiCapability.get(player).map(d -> d.identity().clanJutsu().contains(id)
                    || allowedFor(d.identity().clanId(), id)).orElse(false);
        }
        if (caster instanceof SparringNinja ninja) {
            return allowedFor(ninja.clanId(), id);
        }
        return true;
    }
}
