package com.noirm.ally;

import com.noirm.Noirm;
import com.noirm.dojutsu.MangekyoTransplant;
import com.noirm.dojutsu.Sharingan;
import com.noirm.dojutsu.SharinganAwakening;
import com.noirm.dojutsu.SharinganEyeItem;
import com.noirm.entity.SparringNinja;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * A crew mate's Sharingan: how they get one and how it grows.
 * <ul>
 *   <li><b>Transplant</b>: give them a {@code sharingan_eye} (sneak + right-click them with it, the eye
 *       chip on their screen, or {@code /noirm crew eye}). Anyone can take one, except the Hyūga
 *       (the Byakugan rejects it); a Mangekyō is too much for them. An eye with more tomoe than
 *       theirs upgrades an Uchiha's own. The surgery is safe but hurts and dazes them a moment.</li>
 *   <li><b>Awakening</b>: a dormant Uchiha crew mate wakes their eyes when they see you or another
 *       crew mate brought near death ({@link #DANGER_HEALTH} of max health) by an enemy, beside
 *       the usual ways (badly hurt themselves, a comrade falling: {@link SharinganAwakening}).</li>
 *   <li><b>Growth</b>: open eyes grow with real fights like yours do ({@link Sharingan#STAGE_XP}):
 *       each second fighting and each hit landed, more against stronger foes; a comrade's danger
 *       deepens them too ({@link #DANGER_XP}). 2 tomoe brings the Sharingan genjutsu.</li>
 * </ul>
 * The tomoe are kept with the ninja (their {@code Sharingan} save tag); growth and "implanted" in
 * {@link AllyState}.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class CrewEyes {
    /** Eye growth per second fighting a real foe, × how much stronger they are (0.5–3×). */
    public static final float FIGHT_XP_PER_SECOND = 1f;
    /** Per hit landed on a real foe, × their strength ratio. */
    public static final float HIT_XP = 2f;
    /** A comrade (you or a crew mate) hit below this share of their health counts as "near death". */
    public static final float DANGER_HEALTH = 0.3f;
    /** Eye growth when an awakened crew mate sees a comrade near death (once per {@link #DANGER_COOLDOWN_TICKS}). */
    public static final float DANGER_XP = 60f;
    public static final int DANGER_COOLDOWN_TICKS = 600;
    /** How close they must be to see it. */
    public static final double DANGER_RANGE = 24;
    /** Respect for the gift of an eye. */
    public static final float RESPECT_FOR_EYE = 10f;
    /** What the surgery costs them: health points, and ticks of slowness. */
    public static final float SURGERY_DAMAGE = 4f;
    public static final int SURGERY_DAZE_TICKS = 100;

    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.95f, 0.05f, 0.05f), 1.2f);

    private CrewEyes() {}

    // ---------------------------------------------------------------- transplant

    public enum Outcome {
        GIVEN(true), UPGRADED(true), REJECTED_BYAKUGAN(false), REJECTED_WEAKER(false), REJECTED_MANGEKYO(false), EMPTY(false),
        DISTRUST(false);

        private final boolean success;

        Outcome(boolean success) {
            this.success = success;
        }

        public boolean success() {
            return success;
        }

        public String key() {
            return "message.noirm.crew_eye." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Whether they were born to the Sharingan (their clan carries it). */
    public static boolean bornUchiha(SparringNinja ally) {
        return ally.level().getServer() != null && !ally.clanId().isEmpty()
                && com.noirm.clan.WorldClans.get(ally.level().getServer()).find(ally.clanId()).map(com.noirm.clan.Clan::hasSharingan).orElse(false);
    }

    /** What putting {@code eye} into {@code ally} would do (changes nothing). */
    public static Outcome outcome(SparringNinja ally, MangekyoTransplant.EyeInfo eye) {
        if (eye == null) {
            return Outcome.EMPTY;
        }
        if (ally.byakuganActive()) {
            return Outcome.REJECTED_BYAKUGAN;
        }
        if (eye.mangekyo()) {
            return Outcome.REJECTED_MANGEKYO;
        }
        if (eye.stage() <= ally.activeSharinganStage()) {
            return Outcome.REJECTED_WEAKER;
        }
        if (ally.respect() < AllyMind.LOW_RESPECT) {
            return Outcome.DISTRUST;
        }
        return ally.activeSharinganStage() > 0 || bornUchiha(ally) ? Outcome.UPGRADED : Outcome.GIVEN;
    }

    /** The eye {@code player} would give: the one in their main hand if it takes, else the first in their bag that does. */
    public static ItemStack eyeFor(Player player, SparringNinja ally) {
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (takes(ally, held)) {
            return held;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (takes(ally, stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean takes(SparringNinja ally, ItemStack stack) {
        return stack.getItem() instanceof SharinganEyeItem && outcome(ally, MangekyoTransplant.EyeInfo.of(stack)).success();
    }

    /**
     * {@code leader} puts the eye in {@code stack} into {@code ally}. On success the eye is used up
     * (unless in creative). Returns the outcome; on a refusal nothing changes and the leader hears why.
     */
    public static Outcome giveEye(Player leader, SparringNinja ally, ItemStack stack) {
        MangekyoTransplant.EyeInfo eye = stack.getItem() instanceof SharinganEyeItem ? MangekyoTransplant.EyeInfo.of(stack) : null;
        Outcome outcome = outcome(ally, eye);
        if (!outcome.success()) {
            leader.displayClientMessage(Component.translatable(outcome.key(), ally.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
            return outcome;
        }
        if (outcome == Outcome.GIVEN) {
            ally.allyState().setEyeImplanted(true);
        }
        setStage(ally, eye.stage());
        if (!leader.getAbilities().instabuild) {
            stack.shrink(1);
        }
        ally.invulnerableTime = 0;
        ally.setHealth(Math.max(1f, ally.getHealth() - SURGERY_DAMAGE));
        ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SURGERY_DAZE_TICKS, 1));
        ally.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, SURGERY_DAZE_TICKS / 2, 0));
        AllyMind.respect(ally, RESPECT_FOR_EYE, null);
        ally.level().playSound(null, ally.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.NEUTRAL, 1f, 0.5f);
        flare(ally, 24);
        leader.sendSystemMessage(Component.translatable(outcome.key(), ally.getDisplayName(), eye.stage()).withStyle(ChatFormatting.RED));
        return outcome;
    }

    /** Sets their tomoe, keeping the growth count at least where that stage starts. */
    public static void setStage(SparringNinja ally, int stage) {
        stage = Mth.clamp(stage, 0, 3);
        ally.setSharinganStage(stage);
        ally.allyState().setEyeXp(Math.max(ally.allyState().eyeXp(), Sharingan.STAGE_XP[stage]));
    }

    // ---------------------------------------------------------------- growth

    /** Adds eye growth to a crew mate with open eyes; a new tomoe when it's enough. Returns true if they grew. */
    public static boolean addXp(SparringNinja ally, float amount) {
        int stage = ally.activeSharinganStage();
        if (amount <= 0f || stage <= 0 || ally.leaderId() == null) {
            return false;
        }
        AllyState state = ally.allyState();
        state.setEyeXp(Math.max(state.eyeXp(), Sharingan.STAGE_XP[stage]) + amount);
        if (stage >= 3 || state.eyeXp() < Sharingan.STAGE_XP[stage + 1]) {
            return false;
        }
        ally.setSharinganStage(stage + 1);
        ally.level().playSound(null, ally.getX(), ally.getY(), ally.getZ(), com.noirm.sound.NoirmSounds.SHARINGAN.get(), SoundSource.NEUTRAL, 0.8f, 1.3f);
        flare(ally, 16);
        Player leader = leader(ally);
        if (leader != null) {
            leader.sendSystemMessage(Component.translatable("message.noirm.crew_eye.tomoe", ally.getDisplayName(), stage + 1)
                    .withStyle(ChatFormatting.RED));
        }
        return true;
    }

    /** Share of the way to the next tomoe (1 at 3 tomoe, 0 without eyes). */
    public static float progress(SparringNinja ally) {
        int stage = ally.activeSharinganStage();
        if (stage <= 0) {
            return 0f;
        }
        if (stage >= 3) {
            return 1f;
        }
        float from = Sharingan.STAGE_XP[stage];
        float to = Sharingan.STAGE_XP[stage + 1];
        return Mth.clamp((ally.allyState().eyeXp() - from) / Math.max(1f, to - from), 0f, 1f);
    }

    private static float ratioScale(LivingEntity self, LivingEntity foe) {
        float ratio = SharinganAwakening.foeRatio(self, foe);
        return ratio <= 0f ? 0f : Mth.clamp(ratio, Sharingan.MIN_FOE_RATIO, Sharingan.MAX_FOE_RATIO);
    }

    /** Once a second for crew mates with open eyes: fighting a real foe grows them. */
    @SubscribeEvent
    public static void livingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof SparringNinja ally && !ally.level().isClientSide() && ally.tickCount % 20 == 0
                && ally.leaderId() != null && ally.activeSharinganStage() > 0) {
            LivingEntity target = ally.getTarget();
            if (target != null && target.isAlive()) {
                addXp(ally, FIGHT_XP_PER_SECOND * ratioScale(ally, target));
            }
        }
    }

    /** Hits landed grow their eyes; a comrade brought near death awakens or deepens them. */
    @SubscribeEvent
    public static void damage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || event.getAmount() <= 0) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (source instanceof SparringNinja ally && ally.leaderId() != null && ally.activeSharinganStage() > 0 && ally != victim) {
            addXp(ally, HIT_XP * ratioScale(ally, victim));
        }
        if (source instanceof LivingEntity attacker && SharinganAwakening.foeRatio(victim, attacker) > 0f
                && !Crew.sameSide(victim, attacker) && victim.getHealth() - event.getAmount() <= victim.getMaxHealth() * DANGER_HEALTH
                && victim.getHealth() - event.getAmount() > 0f) {
            comradeInDanger(victim);
        }
    }

    /**
     * {@code victim} was just brought near death by an enemy. If it's a leader or a crew mate, their
     * crew mates who can see it react: dormant Uchiha awaken, open eyes grow. Returns how many reacted.
     */
    public static int comradeInDanger(LivingEntity victim) {
        UUID side;
        if (victim instanceof Player player) {
            side = player.getUUID();
        } else if (victim instanceof SparringNinja mate && mate.leaderId() != null) {
            side = mate.leaderId();
        } else {
            return 0;
        }
        if (!(victim.level() instanceof ServerLevel level)) {
            return 0;
        }
        int count = 0;
        long now = level.getGameTime();
        for (SparringNinja ally : level.getEntitiesOfClass(SparringNinja.class, victim.getBoundingBox().inflate(DANGER_RANGE),
                n -> n != victim && n.isAlive() && side.equals(n.leaderId()) && n.hasLineOfSight(victim))) {
            if (now - ally.allyState().lastEyeMoment < DANGER_COOLDOWN_TICKS) {
                continue;
            }
            if (SharinganAwakening.isDormantNpc(ally)) {
                awaken(ally, victim);
                count++;
            } else if (ally.activeSharinganStage() > 0) {
                ally.allyState().lastEyeMoment = now;
                addXp(ally, DANGER_XP);
                count++;
            }
        }
        return count;
    }

    /** A dormant Uchiha crew mate's eyes open (1 tomoe) at the sight of a comrade near death. */
    public static void awaken(SparringNinja ally, LivingEntity comrade) {
        ally.allyState().lastEyeMoment = ally.level().getGameTime();
        setStage(ally, 1);
        ally.level().playSound(null, ally.getX(), ally.getY(), ally.getZ(), com.noirm.sound.NoirmSounds.SHARINGAN.get(), SoundSource.NEUTRAL, 1f, 1.2f);
        ally.level().playSound(null, ally.getX(), ally.getY(), ally.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.NEUTRAL, 1f, 0.9f);
        flare(ally, 30);
        Player leader = leader(ally);
        if (leader != null) {
            leader.sendSystemMessage(Component.translatable(comrade == leader ? "message.noirm.crew_eye.awakened.you"
                    : "message.noirm.crew_eye.awakened.mate", ally.getDisplayName(), comrade.getDisplayName()).withStyle(ChatFormatting.RED));
        }
    }

    private static Player leader(SparringNinja ally) {
        return ally.leaderId() == null ? null : ally.level().getPlayerByUUID(ally.leaderId());
    }

    private static void flare(SparringNinja ally, int count) {
        if (ally.level() instanceof ServerLevel level) {
            Vec3 eyes = ally.getEyePosition();
            level.sendParticles(RED, eyes.x, eyes.y, eyes.z, count, 0.3, 0.2, 0.3, 0.02);
        }
    }
}
