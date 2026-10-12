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
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Sharingan. Awakened by an Uchiha's near-death awakening; grows from 1 to 3 tomoe through
 * use and further awakenings. While active (it drains chakra):
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
    public static final int PRECOGNITION_STAGE = 2;
    public static final int COPY_STAGE = 3;
    public static final double COPY_RANGE = 16;

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
        return 1f + 0.25f * activeStage(entity);
    }

    /** The toggle key. */
    public static void toggle(ServerPlayer player) {
        ShinobiCapability.get(player).ifPresent(data -> {
            EyeData eyes = data.eyes();
            if (eyes.sharinganStage() == 0) {
                player.displayClientMessage(Component.translatable("message.noirm.sharingan.none"), true);
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
        if (active) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 0.6f);
        }
        PowerVisuals.broadcast(player);
    }

    /** Every server tick for players: drain chakra and grow with use. */
    public static void tick(ServerPlayer player, ShinobiData data) {
        EyeData eyes = data.eyes();
        if (!eyes.isActive()) {
            return;
        }
        boolean free = data.awakening().isLockedIn();
        float drain = DRAIN_PER_TICK_PER_STAGE * eyes.sharinganStage();
        if (!free && !player.getAbilities().instabuild && !data.tryConsumeChakra(drain)) {
            setActive(player, data, false);
            player.displayClientMessage(Component.translatable("message.noirm.sharingan.exhausted"), true);
            return;
        }
        if (player.tickCount % 20 == 0) {
            addXp(player, data, 1);
        }
    }

    /** Sharingan growth (1/s active, more for clutch moments). May reach the next tomoe. */
    public static void addXp(ServerPlayer player, ShinobiData data, int amount) {
        EyeData eyes = data.eyes();
        if (eyes.sharinganStage() == 0 || eyes.sharinganStage() >= EyeData.MAX_STAGE) {
            return;
        }
        eyes.setSharinganXp(eyes.sharinganXp() + amount);
        if (eyes.sharinganXp() >= STAGE_XP[eyes.sharinganStage() + 1]) {
            advance(player, data, "message.noirm.sharingan.tomoe_growth");
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
        if (target instanceof ServerPlayer player) {
            NoirmNetwork.sendToPlayer(player, new PowerPackets.Precognition(attacker.getId(), windupTicks));
        }
    }

    /** Copy: anyone with 3 tomoe active, watching the caster nearby, learns the jutsu. */
    public static void onJutsuCast(LivingEntity caster, ResourceLocation id) {
        List<Player> watchers = caster.level().getEntitiesOfClass(Player.class, caster.getBoundingBox().inflate(COPY_RANGE),
                p -> p != caster);
        for (Player watcher : watchers) {
            tryCopy(watcher, caster, id);
        }
    }

    /** {@code watcher} saw {@code caster} use a jutsu: with 3 tomoe active, they learn it. */
    public static boolean tryCopy(Player watcher, LivingEntity caster, ResourceLocation id) {
        if (activeStage(watcher) < COPY_STAGE || !isWatching(watcher, caster)) {
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
