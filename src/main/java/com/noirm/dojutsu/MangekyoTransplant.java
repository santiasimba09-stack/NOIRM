package com.noirm.dojutsu;

import com.noirm.player.PowerVisuals;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Eye transplants. Eyes taken from a fallen Uchiha are items ({@link SharinganEyeItem}) that
 * remember what they were ({@link EyeInfo}, in the item's NBT). Using one puts it in:
 * <table>
 *   <tr><th>You</th><th>The eye</th><th>Result</th></tr>
 *   <tr><td>Uchiha with your own Mangekyō</td><td>Mangekyō</td><td><b>Eternal Mangekyō</b>: sight restored,
 *       no more strain, stronger, the donor's pattern merged into yours</td></tr>
 *   <tr><td>Uchiha without one</td><td>Mangekyō</td><td>the donor's Mangekyō (pattern, abilities, strain)</td></tr>
 *   <tr><td>Uchiha</td><td>Sharingan with more tomoe</td><td>your eyes reach its tomoe</td></tr>
 *   <tr><td>not an Uchiha</td><td>any</td><td>one implanted eye: always open, a constant chakra drain
 *       (it can't be closed); a Mangekyō eye brings only its own ability, no Susanoo, and strains faster</td></tr>
 * </table>
 * Anything else is refused and the eye isn't used up. The Byakugan rejects a Sharingan.
 */
public final class MangekyoTransplant {
    public enum Outcome {
        ETERNAL(true), BORROWED(true), UPGRADED(true), IMPLANTED(true),
        REJECTED_BYAKUGAN(false), REJECTED_WEAKER(false), REJECTED_ETERNAL(false), REJECTED_HAS_MANGEKYO(false), EMPTY(false);

        private final boolean success;

        Outcome(boolean success) {
            this.success = success;
        }

        public boolean success() {
            return success;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Ticks of holding the eye to perform the transplant. */
    public static final int SURGERY_TICKS = 60;

    /**
     * What an eye item holds.
     *
     * @param stage    tomoe (1–3; a Mangekyō is 3)
     * @param mangekyo a Mangekyō (left/right abilities, pattern, Susanoo colour, strain)
     * @param donor    whose eye it was (display only)
     */
    public record EyeInfo(int stage, boolean mangekyo, int pattern, MangekyoAbility left, MangekyoAbility right,
                          int color, float strain, String donor) {
        public static final String TAG = "NoirmEye";

        public static EyeInfo sharingan(int stage, String donor) {
            return new EyeInfo(Mth.clamp(stage, 1, 3), false, 0, null, null, 0, 0f, donor);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Stage", stage);
            tag.putBoolean("Mangekyo", mangekyo);
            tag.putInt("Pattern", pattern);
            tag.putString("Left", left == null ? "" : left.key());
            tag.putString("Right", right == null ? "" : right.key());
            tag.putInt("Color", color);
            tag.putFloat("Strain", strain);
            tag.putString("Donor", donor == null ? "" : donor);
            return tag;
        }

        public static EyeInfo load(CompoundTag tag) {
            return new EyeInfo(Mth.clamp(tag.getInt("Stage"), 1, 3), tag.getBoolean("Mangekyo"),
                    Math.floorMod(tag.getInt("Pattern"), Mangekyo.PATTERNS), MangekyoAbility.byKey(tag.getString("Left")),
                    MangekyoAbility.byKey(tag.getString("Right")), tag.getInt("Color"),
                    Mth.clamp(tag.getFloat("Strain"), 0f, Mangekyo.MAX_STRAIN), tag.getString("Donor"));
        }

        /** Reads an eye item (null if it holds nothing). */
        public static EyeInfo of(ItemStack stack) {
            return stack.getTag() != null && stack.getTag().contains(TAG) ? load(stack.getTag().getCompound(TAG)) : null;
        }

        public ItemStack toStack() {
            ItemStack stack = new ItemStack(mangekyo ? com.noirm.registry.NoirmItems.MANGEKYO_EYES.get()
                    : com.noirm.registry.NoirmItems.SHARINGAN_EYE.get());
            stack.getOrCreateTag().put(TAG, save());
            return stack;
        }
    }

    private MangekyoTransplant() {}

    /** The eyes a player carries, as an item would hold them (null if no Sharingan). */
    public static EyeInfo fromEyes(EyeData eyes, String donor) {
        if (eyes.sharinganStage() <= 0) {
            return null;
        }
        if (!eyes.hasMangekyo()) {
            return EyeInfo.sharingan(eyes.sharinganStage(), donor);
        }
        return new EyeInfo(3, true, eyes.pattern(), eyes.left(), eyes.right(), eyes.susanooColor(), eyes.strain(), donor);
    }

    /** What putting {@code eye} into {@code data}'s owner would do (doesn't change anything). */
    public static Outcome outcome(ShinobiData data, EyeInfo eye) {
        if (eye == null) {
            return Outcome.EMPTY;
        }
        EyeData eyes = data.eyes();
        if (eyes.hasByakugan()) {
            return Outcome.REJECTED_BYAKUGAN;
        }
        if (!data.identity().hasSharingan()) {
            return Outcome.IMPLANTED;
        }
        if (eye.mangekyo()) {
            if (eyes.isEternal()) {
                return Outcome.REJECTED_ETERNAL;
            }
            return eyes.hasMangekyo() ? Outcome.ETERNAL : Outcome.BORROWED;
        }
        if (eyes.hasMangekyo()) {
            return Outcome.REJECTED_HAS_MANGEKYO;
        }
        return eye.stage() > eyes.sharinganStage() ? Outcome.UPGRADED : Outcome.REJECTED_WEAKER;
    }

    /** Performs the transplant. Returns the outcome; on a refusal nothing changes. */
    public static Outcome transplant(Player player, ShinobiData data, EyeInfo eye) {
        Outcome outcome = outcome(data, eye);
        if (!outcome.success()) {
            player.displayClientMessage(Component.translatable("message.noirm.transplant." + outcome.key()).withStyle(ChatFormatting.GRAY), true);
            return outcome;
        }
        EyeData eyes = data.eyes();
        switch (outcome) {
            case ETERNAL -> {
                eyes.setDonorPattern(eye.pattern());
                eyes.setEternal(true);
            }
            case BORROWED -> {
                setStage(eyes, EyeData.MAX_STAGE);
                takeMangekyo(eyes, eye, eye.left(), eye.right());
            }
            case UPGRADED -> setStage(eyes, eye.stage());
            case IMPLANTED -> {
                setStage(eyes, eye.mangekyo() ? EyeData.MAX_STAGE : eye.stage());
                eyes.setTransplanted(true);
                if (eye.mangekyo()) {
                    takeMangekyo(eyes, eye, eye.left() != null ? eye.left() : eye.right(), null); // one eye, one ability
                } else {
                    eyes.setMangekyo(false);
                }
            }
            default -> {
            }
        }
        if (eyes.sharinganStage() >= Sharingan.PRECOGNITION_STAGE && com.noirm.jutsu.JutsuData.get(Sharingan.GENJUTSU) != null
                && data.jutsu().learn(Sharingan.GENJUTSU, false)) {
            data.jutsu().equipIfRoom(Sharingan.GENJUTSU);
        }
        Mangekyo.syncJutsu(data);
        eyes.setActive(true);
        eyes.setFreeTicks(Sharingan.FREE_TICKS_AFTER_GROWTH);
        data.markPowersDirty();
        // The surgery: a moment of darkness and dizziness while the new eye settles.
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 0.5f);
        player.sendSystemMessage(Component.translatable("message.noirm.transplant." + outcome.key(), eye.stage()).withStyle(ChatFormatting.RED));
        if (outcome == Outcome.ETERNAL) {
            Mangekyo.moment(player, Component.translatable("title.noirm.mangekyo.eternal"),
                    Component.translatable("subtitle.noirm.mangekyo.eternal"), eyes.susanooColor());
        } else if (player instanceof ServerPlayer serverPlayer) {
            PowerVisuals.broadcast(serverPlayer);
        }
        return outcome;
    }

    private static void setStage(EyeData eyes, int stage) {
        eyes.setSharinganStage(stage);
        eyes.setSharinganXp(Math.max(eyes.sharinganXp(), Sharingan.STAGE_XP[Mth.clamp(stage, 0, EyeData.MAX_STAGE)]));
    }

    private static void takeMangekyo(EyeData eyes, EyeInfo eye, MangekyoAbility left, MangekyoAbility right) {
        eyes.setMangekyo(true);
        eyes.setPattern(eye.pattern());
        eyes.setDonorPattern(-1);
        eyes.setAbilities(left, right);
        eyes.setSusanooColor(eye.color() == 0 ? Mangekyo.SUSANOO_COLORS[0] : eye.color());
        eyes.setStrain(eye.strain());
    }
}
