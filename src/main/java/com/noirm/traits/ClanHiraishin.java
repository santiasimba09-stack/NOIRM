package com.noirm.traits;

import com.noirm.Noirm;
import com.noirm.hiraishin.HiraishinAffinity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Clans with a gift for the Flying Raijin. The Flying Raijin is space-time ninjutsu built on a
 * sealing formula, and the Uzumaki are the masters of sealing: the formula and the jump ask
 * {@link #UZUMAKI_STAT_DISCOUNT} fewer points of CTL and Reserves, they can keep
 * {@link #UZUMAKI_EXTRA_MARKS} more marks out, and jumps are cheaper and come back sooner.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClanHiraishin {
    public static final float UZUMAKI_STAT_DISCOUNT = 10f;
    public static final int UZUMAKI_EXTRA_MARKS = 4;
    public static final float UZUMAKI_COST = 0.7f;
    public static final float UZUMAKI_COOLDOWN = 0.8f;

    private ClanHiraishin() {}

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        register();
    }

    /** Registers every clan's Flying Raijin gift (also called by tests). */
    public static void register() {
        HiraishinAffinity.register("uzumaki", new HiraishinAffinity.Bonus(UZUMAKI_STAT_DISCOUNT, UZUMAKI_EXTRA_MARKS, UZUMAKI_COST, UZUMAKI_COOLDOWN));
    }
}
