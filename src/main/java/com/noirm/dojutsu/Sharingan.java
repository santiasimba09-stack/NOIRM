package com.noirm.dojutsu;

import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuUser;
import com.noirm.network.NoirmNetwork;
import com.noirm.network.PowerPackets;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.PowerVisuals;
import com.noirm.player.ShinobiData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Sharingan. Uchiha are born with it dormant; it awakens in one of several ways (see
 * {@link SharinganAwakening}), then grows from 1 to 3 tomoe through use in real fights and
 * further awakenings. While active (it drains chakra):
 * <ul>
 *   <li>1+ tomoe: perception +25% per tomoe — enemy wind-ups look slower</li>
 *   <li>2+ tomoe: precognition — attackers glow and you're warned before their hit lands; the
 *       Sharingan genjutsu can be cast</li>
 *   <li>3 tomoe: copy — watching someone cast a jutsu teaches it to you</li>
 * </ul>
 */
public final class Sharingan {
    public static final ResourceLocation GENJUTSU = com.noirm.Noirm.id("sharingan/genjutsu");
    /** Chakra drained per tick per tomoe: 0.8/s at 1 tomoe, 2.4/s at 3 (regen is 1/s). */
    public static final float DRAIN_PER_TICK_PER_STAGE = 0.04f;
    /** Mastery XP to reach 2 and 3 tomoe through use (awakenings also advance it). */
    public static final int[] STAGE_XP = {0, 0, 300, 1200};
    // How the eyes grow (only while open, and only in real fights; see the javadoc of addXp):
    /** Each second in a real fight: this × how much stronger the opponent is (0.5–3×). */
    public static final float FIGHT_XP_PER_SECOND = 1f;
    /** Each hit you land on a real opponent: this × their strength ratio. */
    public static final float HIT_XP = 2f;
    /** A perfect dodge or parry, and the extra when the eyes warned you of that very attack. */
    public static final int CLUTCH_XP = 10;
    public static final int FORESEEN_XP = 15;
    /** How recent a precognition warning must be for the dodge to count as foreseen. */
    public static final int FORESEEN_TICKS = 40;
    /** Reading an opponent's jutsu with 1–2 tomoe (3 tomoe copy it instead); once per cooldown. */
    public static final int STUDY_XP = 15;
    public static final int STUDY_COOLDOWN_TICKS = 100;
    /** Seeing a comrade fall with the eyes already awakened deepens them. */
    public static final int ALLY_LOSS_XP = 150;
    /** The training dummy (drills) can teach the eyes at most this much in total. */
    public static final int DRILL_XP_CAP = 100;
    /** A fight is "on" for this long after the last blow traded with a real opponent. */
    public static final int FIGHT_MEMORY_TICKS = 100;
    /** Strength ratio limits for the XP multiplier. */
    public static final float MIN_FOE_RATIO = 0.5f;
    public static final float MAX_FOE_RATIO = 3f;
    /** After awakening or gaining a tomoe, the eyes stay open this long without draining chakra. */
    public static final int FREE_TICKS_AFTER_GROWTH = 200;
    public static final int PRECOGNITION_STAGE = 2;
    public static final int COPY_STAGE = 3;
    public static final double COPY_RANGE = 16;
    /** An implanted eye (not born an Uchiha): 0.5 chakra a second, always (regen is 1/s). */
    public static final float TRANSPLANT_DRAIN_PER_TICK = 0.025f;

    private Sharingan() {}

    /** Active tomoe for anyone (players and NPCs); 0 if not active. */
    public static int activeStage(LivingEntity entity) {
        if (entity instanceof Player player) {
            return ShinobiCapability.get(player).map(d -> d.eyes().activeStage()).orElse(0);
        }
        return entity instanceof JutsuUser user ? user.activeSharinganStage() : 0;
    }

    /** Perception multiplier from the active Sharingan. */
    public static float perceptionMultiplier(LivingEntity entity) {
        return 1f + 0.25f * activeStage(entity) + Mangekyo.perceptionBonus(entity);
    }

    /** The eye toggle key (G): opens or closes whichever dōjutsu the player has. */
    public static void toggle(ServerPlayer player) {
        ShinobiCapability.get(player).ifPresent(data -> {
            EyeData eyes = data.eyes();
            if (!eyes.hasEyes()) {
                player.displayClientMessage(Component.translatable("message.noirm.sharingan.none"), true);
                return;
            }
            if (eyes.isBlind()) {
                player.displayClientMessage(Component.translatable("message.noirm.mangekyo.blind"), true);
                return;
            }
            if (eyes.isTransplanted() && eyes.isActive()) {
                // An implanted eye can't be closed (the price of an eye you weren't born with).
                player.displayClientMessage(Component.translatable("message.noirm.transplant.cant_close"), true);
                return;
            }
            setActive(player, data, !eyes.isActive());
        });
    }

    public static void setActive(ServerPlayer player, ShinobiData data, boolean active) {
        if (data.eyes().isActive() == active) {
            return;
        }
        data.eyes().setActive(active);
        data.markPowersDirty();
        if (active && data.eyes().isByakuganActive()) {
            Byakugan.onOpened(player);
        } else if (active) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 0.6f);
        }
        PowerVisuals.broadcast(player);
    }

    /** Every server tick for players: drain chakra, and grow while open in a real fight. */
    public static void tick(ServerPlayer player, ShinobiData data) {
        EyeData eyes = data.eyes();
        if (eyes.freeTicks() > 0) {
            eyes.setFreeTicks(eyes.freeTicks() - 1);
        }
        if (eyes.isTransplanted() && !eyes.isActive() && !eyes.isBlind()) {
            setActive(player, data, true); // an implanted eye is always open
        }
        if (eyes.activeStage() == 0) {
            return;
        }
        boolean free = data.awakening().isLockedIn() || eyes.freeTicks() > 0;
        if (eyes.isTransplanted()) {
            // A constant drain; with no chakra left it feeds on the body instead (it never closes).
            if (!free && !player.getAbilities().instabuild && !data.tryConsumeChakra(TRANSPLANT_DRAIN_PER_TICK)) {
                player.causeFoodExhaustion(0.05f);
            }
        }
        float drain = DRAIN_PER_TICK_PER_STAGE * eyes.sharinganStage() * (eyes.isEternal() ? 0.5f : 1f);
        if (!eyes.isTransplanted() && !free && !player.getAbilities().instabuild && !data.tryConsumeChakra(drain)) {
            setActive(player, data, false);
            player.displayClientMessage(Component.translatable("message.noirm.sharingan.exhausted"), true);
            return;
        }
        if (player.tickCount % 20 == 0) {
            long now = player.level().getGameTime();
            if (inFight(eyes, now)) {
                addXp(player, data, ratioXp(FIGHT_XP_PER_SECOND, eyes.foeRatio()));
            } else if (eyes.lastDrillTick() >= 0 && now - eyes.lastDrillTick() <= FIGHT_MEMORY_TICKS) {
                addDrillXp(player, data, 1);
            }
        }
    }

    /** Still in a fight with a real opponent (a blow traded within the last few seconds). */
    public static boolean inFight(EyeData eyes, long now) {
        return eyes.lastFightTick() >= 0 && now - eyes.lastFightTick() <= FIGHT_MEMORY_TICKS;
    }

    /** {@code base} XP scaled by the opponent's strength ratio; at least 1. */
    static int ratioXp(float base, float ratio) {
        return Math.max(1, Math.round(base * Mth.clamp(ratio, MIN_FOE_RATIO, MAX_FOE_RATIO)));
    }

    /**
     * A hit {@code player} landed on {@code target} (any damage). With the eyes open: XP scaled by
     * the target's strength for a real opponent, a capped trickle for the training dummy.
     */
    public static void onHitLanded(Player player, LivingEntity target) {
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (data == null || data.eyes().activeStage() == 0) {
            return;
        }
        float ratio = SharinganAwakening.foeRatio(player, target);
        if (ratio < 0) {
            addDrillXp(player, data, 1);
        } else if (ratio > 0) {
            addXp(player, data, ratioXp(HIT_XP, ratio));
        }
    }

    /** A perfect dodge or parry with the eyes open; more if they warned you about that attack. */
    public static void onClutch(Player player, ShinobiData data) {
        EyeData eyes = data.eyes();
        if (eyes.activeStage() == 0) {
            return;
        }
        boolean foreseen = eyes.lastWarnedTick() >= 0 && player.level().getGameTime() - eyes.lastWarnedTick() <= FORESEEN_TICKS;
        addXp(player, data, CLUTCH_XP + (foreseen ? FORESEEN_XP : 0));
    }

    /** Training-dummy XP: only until {@link #DRILL_XP_CAP} in total (then only real fights teach). */
    public static void addDrillXp(Player player, ShinobiData data, int amount) {
        EyeData eyes = data.eyes();
        int room = DRILL_XP_CAP - eyes.drillXp();
        if (room <= 0 || amount <= 0 || eyes.sharinganStage() == 0 || eyes.sharinganStage() >= EyeData.MAX_STAGE) {
            return;
        }
        int gained = Math.min(room, amount);
        eyes.setDrillXp(eyes.drillXp() + gained);
        addXp(player, data, gained);
        if (eyes.drillXp() >= DRILL_XP_CAP) {
            player.displayClientMessage(Component.translatable("message.noirm.sharingan.drill_cap"), true);
        }
    }

    /**
     * Sharingan growth. The eyes grow only while open: each second in a real fight (more against
     * stronger opponents), each hit landed, perfect dodges and parries (more if foreseen), and
     * reading jutsu. The dummy teaches only up to {@link #DRILL_XP_CAP}. May reach the next tomoe.
     */
    public static void addXp(Player player, ShinobiData data, int amount) {
        EyeData eyes = data.eyes();
        if (amount <= 0 || eyes.sharinganStage() == 0 || eyes.sharinganStage() >= EyeData.MAX_STAGE) {
            return;
        }
        eyes.setSharinganXp(eyes.sharinganXp() + amount);
        data.markPowersDirty();
        if (eyes.sharinganXp() >= STAGE_XP[eyes.sharinganStage() + 1]) {
            advance(player, data, "message.noirm.sharingan.tomoe_growth");
            SharinganAwakening.growthMoment(player, data);
        }
    }

    /** Awakens the Sharingan or adds a tomoe. Returns false if already at 3 tomoe. */
    public static boolean advance(Player player, ShinobiData data, String messageKey) {
        EyeData eyes = data.eyes();
        if (eyes.sharinganStage() >= EyeData.MAX_STAGE) {
            return false;
        }
        eyes.setSharinganStage(eyes.sharinganStage() + 1);
        eyes.setSharinganXp(Math.max(eyes.sharinganXp(), STAGE_XP[eyes.sharinganStage()]));
        if (eyes.sharinganStage() >= PRECOGNITION_STAGE && JutsuData.get(GENJUTSU) != null && data.jutsu().learn(GENJUTSU, false)) {
            data.jutsu().equipIfRoom(GENJUTSU);
        }
        data.markPowersDirty();
        player.displayClientMessage(Component.translatable(messageKey, eyes.sharinganStage()), false);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2f, 0.5f);
        if (player instanceof ServerPlayer serverPlayer) {
            PowerVisuals.broadcast(serverPlayer);
        }
        return true;
    }

    /**
     * Precognition: {@code attacker} just started winding up at {@code target}. If the target
     * has 2+ tomoe active, the attacker glows and the target gets a warning before it lands.
     */
    public static void onAttackWindup(LivingEntity attacker, LivingEntity target, int windupTicks) {
        if (activeStage(target) < PRECOGNITION_STAGE || attacker == target) {
            return;
        }
        attacker.addEffect(new MobEffectInstance(MobEffects.GLOWING, Math.max(10, windupTicks + 10), 0, false, false));
        if (target instanceof Player player) {
            // Remembered so a dodge of this very attack counts as foreseen (it lands after the wind-up).
            ShinobiCapability.get(player).ifPresent(d -> d.eyes().setLastWarnedTick(player.level().getGameTime() + windupTicks));
        }
        if (target instanceof ServerPlayer player) {
            NoirmNetwork.sendToPlayer(player, new PowerPackets.Precognition(attacker.getId(), windupTicks));
        }
    }

    /** Copy: anyone with 3 tomoe active, watching the caster nearby, learns the jutsu. */
    public static void onJutsuCast(LivingEntity caster, ResourceLocation id) {
        List<Player> watchers = caster.level().getEntitiesOfClass(Player.class, caster.getBoundingBox().inflate(COPY_RANGE),
                p -> p != caster);
        for (Player watcher : watchers) {
            if (!tryCopy(watcher, caster, id)) {
                study(watcher, caster);
            }
        }
    }

    /** 1–2 tomoe can't copy yet, but reading an opponent's jutsu in a real fight trains the eyes. */
    public static boolean study(Player watcher, LivingEntity caster) {
        int stage = activeStage(watcher);
        ShinobiData data = ShinobiCapability.get(watcher).orElse(null);
        if (data == null || stage == 0 || stage >= COPY_STAGE || !isWatching(watcher, caster)) {
            return false;
        }
        EyeData eyes = data.eyes();
        long now = watcher.level().getGameTime();
        if (!inFight(eyes, now) || (eyes.lastStudyTick() >= 0 && now - eyes.lastStudyTick() < STUDY_COOLDOWN_TICKS)) {
            return false;
        }
        eyes.setLastStudyTick(now);
        addXp(watcher, data, STUDY_XP);
        return true;
    }

    /** {@code watcher} saw {@code caster} use a jutsu: with 3 tomoe active, they learn it. */
    public static boolean tryCopy(Player watcher, LivingEntity caster, ResourceLocation id) {
        if (activeStage(watcher) < COPY_STAGE || Mangekyo.isMangekyoJutsu(id) || !isWatching(watcher, caster)) {
            return false;
        }
        ShinobiData data = ShinobiCapability.get(watcher).orElse(null);
        if (data == null || !data.jutsu().learn(id, true)) {
            return false;
        }
        data.jutsu().equipIfRoom(id);
        data.markPowersDirty();
        watcher.displayClientMessage(Component.translatable("message.noirm.sharingan.copied", Component.translatable(jutsuKey(id))), false);
        watcher.level().playSound(null, watcher.getX(), watcher.getY(), watcher.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 1.4f);
        return true;
    }

    /** Within ~40° of where the watcher is looking, in sight. */
    static boolean isWatching(Player watcher, LivingEntity caster) {
        Vec3 to = caster.getEyePosition().subtract(watcher.getEyePosition()).normalize();
        double dot = watcher.getViewVector(1f).dot(to);
        return dot >= Math.cos(Math.toRadians(40)) && watcher.hasLineOfSight(caster);
    }

    public static String jutsuKey(ResourceLocation id) {
        return "jutsu." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }
}
