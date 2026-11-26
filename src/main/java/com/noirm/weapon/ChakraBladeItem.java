package com.noirm.weapon;

import com.noirm.clan.Nature;
import com.noirm.jutsu.JutsuUser;
import com.noirm.player.ShinobiCapability;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Chakra blades: trench knives of chakra-conducting metal. They fight with the kunai moveset,
 * and each hit carries the wielder's strongest basic nature (see {@link WeaponEvents}): wind
 * hones the edge (more damage), fire burns, lightning numbs, water pushes, earth crushes the guard.
 */
public class ChakraBladeItem extends SwordItem implements WeaponMoves.Armed {
    /** Chakra a nature-charged hit costs a player. */
    public static final float CHAKRA_PER_HIT = 1.5f;
    /** Wind's sharper edge. */
    public static final float WIND_DAMAGE = 1.35f;

    public ChakraBladeItem(Properties properties) {
        super(WeaponTiers.CHAKRA_METAL, 2, -2.1f, properties);
    }

    @Override
    public Moveset moveset() {
        return Moveset.KUNAI;
    }

    /** The basic nature {@code wielder} channels best, or null if none. */
    public static Nature natureOf(LivingEntity wielder) {
        Nature best = null;
        float bestAffinity = 0f;
        for (Nature nature : Nature.BASIC) {
            float affinity = affinity(wielder, nature);
            if (affinity > bestAffinity) {
                best = nature;
                bestAffinity = affinity;
            }
        }
        return best;
    }

    private static float affinity(LivingEntity wielder, Nature nature) {
        if (wielder instanceof Player player) {
            return ShinobiCapability.get(player).map(d -> d.identity().affinity(nature)).orElse(0f);
        }
        return wielder instanceof JutsuUser user ? user.jutsuAffinity(nature) : 0f;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.noirm.chakra_blade").withStyle(ChatFormatting.GRAY));
    }
}
