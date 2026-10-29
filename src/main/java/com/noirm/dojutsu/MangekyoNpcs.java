package com.noirm.dojutsu;

import com.noirm.Noirm;
import com.noirm.ally.Crew;
import com.noirm.clan.Clan;
import com.noirm.clan.WorldClans;
import com.noirm.entity.ShadowClone;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mangekyō ninjas and the eyes the fallen leave behind.
 * <ul>
 *   <li>A few 3-tomoe Uchiha ninjas ({@link #NPC_CHANCE}) carry a Mangekyō: a pattern, two eye
 *       abilities and Susanoo, which they use in fights (the jutsu AI picks them like any other).</li>
 *   <li>Eyes: a Mangekyō ninja always leaves its eyes; a fallen crew Uchiha always leaves its
 *       Sharingan; any other Uchiha ninja beaten by a player's side, rarely
 *       ({@link #SHARINGAN_DROP_CHANCE}).</li>
 * </ul>
 * The NPC's Mangekyō is kept in its Forge persistent data ({@value #TAG}), so the ninja's own save
 * code doesn't change.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class MangekyoNpcs {
    /** Share of 3-tomoe Uchiha ninjas (rolled once) with a Mangekyō. */
    public static final float NPC_CHANCE = 0.08f;
    /** Chance an ordinary Uchiha ninja beaten by a player's side leaves an eye. */
    public static final float SHARINGAN_DROP_CHANCE = 0.12f;
    static final String TAG = "NoirmMangekyo";
    private static final String ROLLED = "NoirmMangekyoRolled";

    private MangekyoNpcs() {}

    /** Gives {@code ninja} a Mangekyō (3 tomoe, a pattern, two abilities, Susanoo). */
    public static void makeMangekyo(SparringNinja ninja, RandomSource random) {
        MangekyoAbility left = MangekyoAbility.roll(random, null);
        MangekyoAbility right = MangekyoAbility.roll(random, left);
        CompoundTag tag = new CompoundTag();
        tag.putInt("Pattern", random.nextInt(Mangekyo.PATTERNS));
        tag.putString("Left", left.key());
        tag.putString("Right", right.key());
        tag.putInt("Color", Mangekyo.SUSANOO_COLORS[random.nextInt(Mangekyo.SUSANOO_COLORS.length)]);
        ninja.getPersistentData().put(TAG, tag);
        ninja.getPersistentData().putBoolean(ROLLED, true);
        apply(ninja);
    }

    public static boolean hasMangekyo(SparringNinja ninja) {
        return ninja.getPersistentData().contains(TAG);
    }

    public static MangekyoAbility[] abilities(SparringNinja ninja) {
        CompoundTag tag = ninja.getPersistentData().getCompound(TAG);
        return new MangekyoAbility[] {MangekyoAbility.byKey(tag.getString("Left")), MangekyoAbility.byKey(tag.getString("Right"))};
    }

    static int susanooColor(SparringNinja ninja) {
        CompoundTag tag = ninja.getPersistentData().getCompound(TAG);
        return tag.contains("Color") ? tag.getInt("Color") : Susanoo.NPC_COLOR;
    }

    /** Makes the ninja match its saved Mangekyō: eyes at 3 tomoe, the look, the jutsu. */
    static void apply(SparringNinja ninja) {
        CompoundTag tag = ninja.getPersistentData().getCompound(TAG);
        if (ninja.activeSharinganStage() < EyeData.MAX_STAGE) {
            ninja.setSharinganStage(EyeData.MAX_STAGE);
        }
        int look = Mangekyo.look(Math.floorMod(tag.getInt("Pattern"), Mangekyo.PATTERNS), -1);
        if (ninja.mangekyoLook() != look) {
            ninja.setMangekyoLook(look);
        }
        for (MangekyoAbility ability : abilities(ninja)) {
            if (ability != null) {
                addJutsu(ninja, ability.jutsu());
            }
        }
        addJutsu(ninja, Mangekyo.SUSANOO);
    }

    private static void addJutsu(SparringNinja ninja, ResourceLocation id) {
        if (JutsuData.get(id) != null && !ninja.knownJutsu().contains(id)) {
            ninja.knownJutsu().add(id);
        }
    }

    @SubscribeEvent
    public static void livingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof SparringNinja ninja && !(ninja instanceof ShadowClone) && !ninja.level().isClientSide()
                && ninja.tickCount % 10 == 5) {
            tick(ninja);
        }
    }

    /** Rolls the Mangekyō once per ninja, and keeps a Mangekyō ninja's eyes and jutsu in place. */
    public static void tick(SparringNinja ninja) {
        if (ninja.clanId().isEmpty() || ninja.level().getServer() == null) {
            return;
        }
        CompoundTag persistent = ninja.getPersistentData();
        if (!persistent.getBoolean(ROLLED)) {
            persistent.putBoolean(ROLLED, true);
            if (ninja.activeSharinganStage() >= EyeData.MAX_STAGE && isUchiha(ninja) && ninja.getRandom().nextFloat() < NPC_CHANCE) {
                makeMangekyo(ninja, ninja.getRandom());
            }
        }
        if (hasMangekyo(ninja)) {
            apply(ninja);
        }
    }

    static boolean isUchiha(SparringNinja ninja) {
        return ninja.level().getServer() != null
                && WorldClans.get(ninja.level().getServer()).find(ninja.clanId()).map(Clan::hasSharingan).orElse(false);
    }

    // ---------------------------------------------------------------- eyes left behind

    /**
     * The eyes {@code ninja} leaves when it falls to {@code source} ({@code roll} 0–1 decides the
     * rare drop), or an empty stack.
     */
    public static ItemStack eyesFor(SparringNinja ninja, DamageSource source, float roll) {
        if (ninja instanceof ShadowClone || ninja.activeSharinganStage() <= 0) {
            return ItemStack.EMPTY;
        }
        String donor = ninja.getName().getString();
        if (hasMangekyo(ninja)) {
            CompoundTag tag = ninja.getPersistentData().getCompound(TAG);
            MangekyoAbility[] abilities = abilities(ninja);
            return new MangekyoTransplant.EyeInfo(3, true, Math.floorMod(tag.getInt("Pattern"), Mangekyo.PATTERNS),
                    abilities[0], abilities[1], susanooColor(ninja), 0f, donor).toStack();
        }
        boolean crew = ninja.leaderId() != null;
        Entity killer = source.getEntity();
        boolean byPlayerSide = killer instanceof Player
                || (killer instanceof LivingEntity living && !Crew.sideOf(living).equals(living.getUUID()));
        if (crew || (byPlayerSide && roll < SHARINGAN_DROP_CHANCE)) {
            return MangekyoTransplant.EyeInfo.sharingan(ninja.activeSharinganStage(), donor).toStack();
        }
        return ItemStack.EMPTY;
    }

    @SubscribeEvent
    public static void drops(LivingDropsEvent event) {
        if (event.getEntity() instanceof SparringNinja ninja && !ninja.level().isClientSide()) {
            ItemStack eyes = eyesFor(ninja, event.getSource(), ninja.getRandom().nextFloat());
            if (!eyes.isEmpty()) {
                event.getDrops().add(new ItemEntity(ninja.level(), ninja.getX(), ninja.getY() + 0.5, ninja.getZ(), eyes));
            }
        }
    }
}
