package com.noirm.pills;

import com.noirm.Noirm;
import com.noirm.bounty.NinjaClass;
import com.noirm.entity.MasterNinja;
import com.noirm.entity.SparringNinja;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ninjas carry a few pills too, and use them sparingly in a fight: a <b>blood pill</b> when they're
 * badly hurt, a <b>soldier pill</b> when their chakra runs dry (a burst, then a crash a little later).
 * You see a puff of the pill's colour and hear them swallow it. How many they carry depends on their
 * class ({@link #pouch}); what's left falls when they're beaten, and strong rogues drop better pills
 * and medical scrolls ({@link #drops}). Masters never use pills.
 *
 * <p>Saved on the ninja's persistent data ({@value #TAG}), so the ninja class needs no new fields.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class NpcPills {
    public static final String TAG = "NoirmPills";
    /** A blood pill below this share of health… */
    public static final float BLOOD_AT = 0.35f;
    /** …a soldier pill below this share of chakra. */
    public static final float SOLDIER_AT = 0.2f;
    /** At least this long between two pills (ticks). */
    public static final int GAP_TICKS = 400;
    /** Blood pill: regeneration II for this long, plus an instant heal. */
    public static final int BLOOD_REGEN_TICKS = 120;
    public static final float BLOOD_HEAL = 4f;
    /** Soldier pill: this share of max chakra back, strength I and speed I for {@link #SOLDIER_TICKS}, then the crash. */
    public static final float SOLDIER_CHAKRA = 0.6f;
    public static final int SOLDIER_TICKS = 300;
    public static final int CRASH_TICKS = 200;
    /** How often a ninja thinks about it. */
    public static final int CHECK_TICKS = 10;
    public static final int BLOOD_COLOR = 0xD0261E, SOLDIER_COLOR = 0xB08A3A;
    /** Dust in the puff when a ninja swallows one (you should notice it mid-fight). */
    public static final int PUFF = 28;

    // ---------------------------------------------------------------- loot from beaten ninjas (chances 0..1)
    public static final float A_REFINED = 0.25f, A_SCROLL = 0.08f;
    public static final float S_GOOD = 0.5f, S_PERFECT_SHARE = 0.3f, S_SCROLL = 0.2f, S_HIGH_GRADE = 0.05f;
    public static final float LEGEND_HIGH_GRADE = 0.2f;

    public enum Kind { BLOOD, SOLDIER }

    private NpcPills() {}

    /** Called from {@code SparringNinja.aiStep} on the server. */
    public static void tick(SparringNinja ninja) {
        if (ninja.tickCount % CHECK_TICKS != 0 || ninja instanceof MasterNinja || !ninja.isAlive()) {
            return;
        }
        CompoundTag tag = tag(ninja);
        long now = ninja.level().getGameTime();
        if (tag.contains("CrashAt") && now >= tag.getLong("CrashAt")) {
            tag.remove("CrashAt");
            ninja.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CRASH_TICKS, 0));
            ninja.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, CRASH_TICKS, 0));
        }
        if (!ninja.isFighting() || ninja.getTarget() == null || now < tag.getLong("Next")) {
            return;
        }
        if (ninja.getHealth() < ninja.getMaxHealth() * BLOOD_AT && pouch(ninja, Kind.BLOOD) > 0) {
            use(ninja, Kind.BLOOD);
        } else if (ninja.chakra() < ninja.maxChakra() * SOLDIER_AT && pouch(ninja, Kind.SOLDIER) > 0) {
            use(ninja, Kind.SOLDIER);
        }
    }

    /** The ninja swallows one pill of {@code kind} (public for tests). */
    public static void use(SparringNinja ninja, Kind kind) {
        CompoundTag tag = tag(ninja);
        tag.putInt(kind.name(), Math.max(0, pouch(ninja, kind) - 1));
        tag.putLong("Next", ninja.level().getGameTime() + GAP_TICKS);
        if (kind == Kind.BLOOD) {
            ninja.heal(BLOOD_HEAL);
            ninja.addEffect(new MobEffectInstance(MobEffects.REGENERATION, BLOOD_REGEN_TICKS, 1));
            PillEffects.puff(ninja, BLOOD_COLOR, PUFF);
            ninja.level().playSound(null, ninja.blockPosition(), com.noirm.sound.NoirmSounds.MEDICAL_CHIME.get(), SoundSource.HOSTILE, 0.6f, 1.2f);
        } else {
            ninja.setChakra(ninja.chakra() + ninja.maxChakra() * SOLDIER_CHAKRA);
            ninja.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, SOLDIER_TICKS, 0));
            ninja.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SOLDIER_TICKS, 0));
            tag.putLong("CrashAt", ninja.level().getGameTime() + SOLDIER_TICKS);
            PillEffects.puff(ninja, SOLDIER_COLOR, PUFF);
            ninja.level().playSound(null, ninja.blockPosition(), com.noirm.sound.NoirmSounds.CHAKRA_CHARGE.get(), SoundSource.HOSTILE, 0.6f, 1.2f);
        }
        ninja.level().playSound(null, ninja.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.HOSTILE, 0.7f, 1.6f);
        tag.putInt("Used", tag.getInt("Used") + 1);
    }

    /** Pills of {@code kind} the ninja still carries (rolled from its class the first time). */
    public static int pouch(SparringNinja ninja, Kind kind) {
        return tag(ninja).getInt(kind.name());
    }

    /** Sets the pouch (tests, commands). */
    public static void setPouch(SparringNinja ninja, Kind kind, int count) {
        tag(ninja).putInt(kind.name(), Math.max(0, count));
    }

    /** Pills used so far (for tests). */
    public static int used(SparringNinja ninja) {
        return tag(ninja).getInt("Used");
    }

    private static CompoundTag tag(SparringNinja ninja) {
        CompoundTag root = ninja.getPersistentData();
        if (!root.contains(TAG)) {
            CompoundTag tag = new CompoundTag();
            roll(ninja, tag);
            root.put(TAG, tag);
        }
        return root.getCompound(TAG);
    }

    /**
     * What a ninja carries, by class: D none; C a blood pill 40% of the time; B a blood pill and a
     * soldier pill half the time; A one of each; S two blood pills and a soldier pill.
     */
    private static void roll(SparringNinja ninja, CompoundTag tag) {
        if (ninja instanceof MasterNinja) {
            return;
        }
        var random = ninja.getRandom();
        switch (NinjaClass.of(ninja)) {
            case C -> tag.putInt("BLOOD", random.nextFloat() < 0.4f ? 1 : 0);
            case B -> {
                tag.putInt("BLOOD", 1);
                tag.putInt("SOLDIER", random.nextFloat() < 0.5f ? 1 : 0);
            }
            case A -> {
                tag.putInt("BLOOD", 1);
                tag.putInt("SOLDIER", 1);
            }
            case S -> {
                tag.putInt("BLOOD", 2);
                tag.putInt("SOLDIER", 1);
            }
            default -> { }
        }
    }

    /** A beaten ninja drops the pills it didn't use, and strong ones carry better pills and scrolls. */
    @SubscribeEvent
    public static void drops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof SparringNinja ninja) || ninja instanceof MasterNinja || ninja.level().isClientSide()
                || !(event.getSource().getEntity() instanceof Player)) {
            return;
        }
        var random = ninja.getRandom();
        java.util.function.Consumer<ItemStack> drop = stack -> {
            if (!stack.isEmpty()) {
                event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(ninja.level(), ninja.getX(), ninja.getY() + 0.5, ninja.getZ(), stack));
            }
        };
        int blood = pouch(ninja, Kind.BLOOD), soldier = pouch(ninja, Kind.SOLDIER);
        if (blood > 0) {
            drop.accept(new ItemStack(PillItems.BLOOD_PILL.get(), blood));
        }
        if (soldier > 0) {
            drop.accept(new ItemStack(PillItems.SOLDIER_PILL.get(), soldier));
        }
        int code = NinjaClass.code(ninja);
        var good = java.util.List.of(PillItems.SOLDIER_PILL, PillItems.BLOOD_PILL, PillItems.CHAKRA_PILL, PillItems.FOCUS_PILL);
        var high = java.util.List.of(PillItems.IRON_BONE_PILL, PillItems.THREE_SEAL_PILL);
        if (code == NinjaClass.A.ordinal()) {
            if (random.nextFloat() < A_REFINED) {
                drop.accept(Grade.REFINED.apply(new ItemStack(good.get(random.nextInt(2)).get())));
            }
            if (random.nextFloat() < A_SCROLL) {
                drop.accept(new ItemStack(PillItems.MEDICAL_SCROLL.get()));
            }
        } else if (code >= NinjaClass.S.ordinal()) {
            boolean legend = code == NinjaClass.LEGENDARY_CODE;
            if (legend || random.nextFloat() < S_GOOD) {
                Grade grade = legend || random.nextFloat() < S_PERFECT_SHARE ? Grade.PERFECT : Grade.REFINED;
                drop.accept(grade.apply(new ItemStack(good.get(random.nextInt(good.size())).get())));
            }
            if (legend || random.nextFloat() < S_SCROLL) {
                drop.accept(new ItemStack(PillItems.MEDICAL_SCROLL.get()));
            }
            if (random.nextFloat() < (legend ? LEGEND_HIGH_GRADE : S_HIGH_GRADE)) {
                drop.accept(new ItemStack(high.get(random.nextInt(high.size())).get()));
            }
        }
    }
}
