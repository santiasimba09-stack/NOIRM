package com.noirm.dojutsu;

import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.clan.WorldClans;
import com.noirm.combat.CombatStats;
import com.noirm.combat.Perception;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.TrainingDummy;
import com.noirm.player.PowerVisuals;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.vfx.Vfx;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.List;

/**
 * How an Uchiha's dormant Sharingan awakens, and the moments when it grows. Four routes:
 * <ol>
 *   <li><b>Near death</b>: the Resolve awakening ({@code AwakeningSystem}), unchanged.</li>
 *   <li><b>A comrade falls</b>: seeing a crew mate (or another player) die nearby, at someone
 *       else's hand. Already awakened eyes grow from it instead.</li>
 *   <li><b>A long, hard fight</b>: {@link #HARD_FIGHT_TICKS} of fighting a much stronger opponent
 *       (their power at least {@link #HARD_FOE_RATIO}× yours), without long pauses.</li>
 *   <li><b>Pressure</b>: every real fight builds it a little (blows traded, close calls). Once
 *       full, the next intense moment (a perfect dodge or parry, a heavy hit, or being badly
 *       hurt) opens the eyes.</li>
 * </ol>
 * As the pressure builds, the player gets hints: a line on the action bar, a flicker of red at
 * the edge of their vision and a heartbeat (drawn by the client when the hint level rises).
 *
 * <p>This class also keeps track of fights (who you're trading blows with and how strong they
 * are), which the Sharingan's growth uses too. NPC Uchiha: some are born with the eyes dormant
 * and awaken when badly hurt in a fight or when a crew mate falls.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class SharinganAwakening {
    public enum Route {
        ALLY("ally"), HARD_FIGHT("hard_fight"), PRESSURE("pressure");

        private final String key;

        Route(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    public static final float MAX_PRESSURE = 100f;
    /** Pressure per point of damage taken / dealt in a real fight, × the opponent's strength ratio (0.5–2). */
    public static final float PRESSURE_PER_DAMAGE_TAKEN = 0.6f;
    public static final float PRESSURE_PER_DAMAGE_DEALT = 0.3f;
    /** Pressure from a perfect dodge or parry. */
    public static final float PRESSURE_PER_CLUTCH = 3f;
    /** Pressure per second of a hard fight (against a much stronger opponent). */
    public static final float PRESSURE_PER_HARD_SECOND = 0.5f;
    /** Hints at 25, 50, 75 and 100 pressure. */
    public static final int HINT_TIERS = 4;
    /** "Much stronger": their power ÷ yours. */
    public static final float HARD_FOE_RATIO = 1.5f;
    /** 60 seconds of fighting a much stronger opponent. */
    public static final int HARD_FIGHT_TICKS = 1200;
    /** The hard fight keeps counting while blows were traded within this long... */
    public static final int HARD_FIGHT_ACTIVE_TICKS = 100;
    /** ...and is over (the count resets) after a pause this long. */
    public static final int HARD_FIGHT_GAP_TICKS = 300;
    /** How close you must be to see a comrade fall. */
    public static final double ALLY_DEATH_RANGE = 24;
    /** An intense moment: a hit taking this much of your max health, or leaving you below the second. */
    public static final float INTENSE_HIT_FRACTION = 0.2f;
    public static final float INTENSE_HEALTH_FRACTION = 0.4f;
    /** Share of Uchiha ninjas (rolled by the world) whose eyes haven't awakened yet. */
    public static final float NPC_DORMANT_CHANCE = 0.35f;
    /** A dormant Uchiha ninja awakens when a fight leaves it below this much health. */
    public static final float NPC_AWAKEN_HEALTH = 0.3f;

    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.95f, 0.05f, 0.05f), 1.3f);

    private SharinganAwakening() {}

    // ---------------------------------------------------------------- who and how strong

    /** An Uchiha (by blood) whose Sharingan hasn't awakened yet. */
    public static boolean isDormant(ShinobiData data) {
        return data.identity().hasSharingan() && data.eyes().sharinganStage() == 0;
    }

    /**
     * How a fight against {@code foe} counts: -1 for the training dummy (a drill), 0 for things
     * that aren't real opponents (animals), otherwise their power ÷ yours.
     */
    public static float foeRatio(LivingEntity self, LivingEntity foe) {
        if (foe instanceof TrainingDummy) {
            return -1f;
        }
        if (foe == self || !(foe instanceof Enemy || foe instanceof Player || foe instanceof CombatStats)) {
            return 0f;
        }
        return Perception.basePower(foe) / Math.max(1f, Perception.basePower(self));
    }

    // ---------------------------------------------------------------- fights

    /** Damage between players and other living things: fights, growth and pressure. */
    @SubscribeEvent
    public static void damage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || event.getAmount() <= 0) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (victim instanceof Player player && source instanceof LivingEntity attacker && attacker != player) {
            onExchange(player, attacker, event.getAmount(), true);
        }
        if (source instanceof Player player && player != victim) {
            onExchange(player, victim, event.getAmount(), false);
            Sharingan.onHitLanded(player, victim);
        }
    }

    /**
     * {@code player} traded a blow with {@code foe} ({@code taken}: they were hit). Marks the
     * fight, builds a dormant Uchiha's pressure, and may be the intense moment that awakens it.
     */
    public static void onExchange(Player player, LivingEntity foe, float amount, boolean taken) {
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (data == null) {
            return;
        }
        float ratio = foeRatio(player, foe);
        long now = player.level().getGameTime();
        EyeData eyes = data.eyes();
        if (ratio < 0) {
            eyes.setLastDrillTick(now);
            return;
        }
        if (ratio == 0) {
            return;
        }
        eyes.setFoeRatio(Sharingan.inFight(eyes, now) ? Math.max(eyes.foeRatio(), ratio) : ratio);
        eyes.setLastFightTick(now);
        if (ratio >= HARD_FOE_RATIO) {
            eyes.setLastHardTick(now);
        }
        if (!isDormant(data)) {
            return;
        }
        float scale = Mth.clamp(ratio, 0.5f, 2f);
        addPressure(player, data, amount * scale * (taken ? PRESSURE_PER_DAMAGE_TAKEN : PRESSURE_PER_DAMAGE_DEALT));
        boolean intense = taken && (amount >= player.getMaxHealth() * INTENSE_HIT_FRACTION
                || player.getHealth() - amount <= player.getMaxHealth() * INTENSE_HEALTH_FRACTION);
        if (intense) {
            intenseMoment(player, data);
        }
    }

    /** A perfect dodge or parry: growth for open eyes, pressure (or the awakening) for dormant ones. */
    public static void onClutch(Player player, ShinobiData data) {
        Sharingan.onClutch(player, data);
        if (isDormant(data)) {
            addPressure(player, data, PRESSURE_PER_CLUTCH);
            intenseMoment(player, data);
        }
    }

    /** With the pressure full, an intense moment opens the eyes. */
    public static boolean intenseMoment(Player player, ShinobiData data) {
        return isDormant(data) && data.eyes().pressure() >= MAX_PRESSURE && awaken(player, data, Route.PRESSURE);
    }

    public static void addPressure(Player player, ShinobiData data, float amount) {
        if (amount <= 0 || !isDormant(data)) {
            return;
        }
        EyeData eyes = data.eyes();
        int before = hintTier(eyes.pressure());
        eyes.setPressure(eyes.pressure() + amount);
        int after = hintTier(eyes.pressure());
        data.markPowersDirty();
        if (after > before) {
            hint(player, after);
        }
    }

    /** 0 below 25 pressure, then 1–4 (4 = full). */
    public static int hintTier(float pressure) {
        return Mth.clamp((int) (pressure / (MAX_PRESSURE / HINT_TIERS)), 0, HINT_TIERS);
    }

    /** The subtle sign that something is building (the client adds a red flicker and a heartbeat). */
    private static void hint(Player player, int tier) {
        player.displayClientMessage(Component.translatable("message.noirm.sharingan.hint." + tier), true);
        if (tier >= 3) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.6f, 1.2f);
        }
    }

    /** Every server tick for players: the hard-fight clock. */
    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            ShinobiCapability.get(player).ifPresent(data -> tick(player, data));
        }
    }

    /** One tick of the hard-fight clock (public for tests). */
    public static void tick(Player player, ShinobiData data) {
        EyeData eyes = data.eyes();
        long now = player.level().getGameTime();
        if (eyes.lastHardTick() < 0) {
            return;
        }
        long since = now - eyes.lastHardTick();
        if (since > HARD_FIGHT_GAP_TICKS) {
            eyes.setHardFightTicks(0);
            eyes.setLastHardTick(-1);
            return;
        }
        if (since > HARD_FIGHT_ACTIVE_TICKS || !isDormant(data)) {
            return;
        }
        eyes.setHardFightTicks(eyes.hardFightTicks() + 1);
        if (eyes.hardFightTicks() % 20 == 0) {
            addPressure(player, data, PRESSURE_PER_HARD_SECOND);
        }
        if (eyes.hardFightTicks() == HARD_FIGHT_TICKS / 2) {
            player.displayClientMessage(Component.translatable("message.noirm.sharingan.hint.hard_fight"), true);
        }
        if (eyes.hardFightTicks() >= HARD_FIGHT_TICKS) {
            awaken(player, data, Route.HARD_FIGHT);
        }
    }

    // ---------------------------------------------------------------- a comrade falls

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || !(dead.level() instanceof ServerLevel level)) {
            return;
        }
        Entity killer = event.getSource().getEntity();
        for (Player witness : level.getEntitiesOfClass(Player.class, dead.getBoundingBox().inflate(ALLY_DEATH_RANGE))) {
            witnessDeath(witness, dead, killer);
        }
        if (dead instanceof SparringNinja || dead instanceof Player) {
            for (SparringNinja ninja : level.getEntitiesOfClass(SparringNinja.class, dead.getBoundingBox().inflate(ALLY_DEATH_RANGE),
                    n -> n != dead && n.isAlive() && n.leaderId() != null)) {
                boolean comrade = dead instanceof Player player ? player.getUUID().equals(ninja.leaderId())
                        : ninja.leaderId().equals(((SparringNinja) dead).leaderId());
                if (comrade && killer != ninja && ninja.hasLineOfSight(dead) && isDormantNpc(ninja)) {
                    awakenNpc(ninja, "message.noirm.sharingan.npc_awakened.ally");
                }
            }
        }
    }

    /**
     * {@code witness} saw {@code dead} fall to {@code killer}. A crew mate or another player
     * counts as a comrade (not if the witness did it). Dormant eyes awaken; open ones grow.
     */
    public static boolean witnessDeath(Player witness, LivingEntity dead, Entity killer) {
        if (witness == dead || killer == witness || !witness.isAlive()) {
            return false;
        }
        boolean comrade = dead instanceof Player
                || (dead instanceof SparringNinja ninja && witness.getUUID().equals(ninja.leaderId()));
        if (!comrade || witness.distanceTo(dead) > ALLY_DEATH_RANGE || !witness.hasLineOfSight(dead)) {
            return false;
        }
        ShinobiData data = ShinobiCapability.get(witness).orElse(null);
        if (data == null || !data.identity().hasSharingan()) {
            return false;
        }
        if (isDormant(data)) {
            return awaken(witness, data, Route.ALLY);
        }
        if (Mangekyo.onLoss(witness, data, dead)) {
            return true; // a deep loss at 3 tomoe: the Mangekyō
        }
        Sharingan.addXp(witness, data, Sharingan.ALLY_LOSS_XP);
        return true;
    }

    // ---------------------------------------------------------------- the moment

    /** Opens a dormant Sharingan (1 tomoe) by {@code route}: title, sounds, red light, eyes on for free. */
    public static boolean awaken(Player player, ShinobiData data, Route route) {
        if (!isDormant(data)) {
            return false;
        }
        EyeData eyes = data.eyes();
        eyes.setPressure(0f);
        eyes.setHardFightTicks(0);
        Sharingan.advance(player, data, "message.noirm.sharingan.awakened." + route.key());
        eyes.setActive(true);
        eyes.setFreeTicks(Sharingan.FREE_TICKS_AFTER_GROWTH);
        data.markPowersDirty();
        moment(player, Component.translatable("title.noirm.awakening.sharingan"),
                Component.translatable("subtitle.noirm.sharingan." + route.key()), 1f);
        if (player instanceof ServerPlayer serverPlayer) {
            PowerVisuals.broadcast(serverPlayer);
        }
        return true;
    }

    /** A new tomoe from use: a short title, the eyes flare and stay open for free for a while. */
    static void growthMoment(Player player, ShinobiData data) {
        EyeData eyes = data.eyes();
        eyes.setActive(true);
        eyes.setFreeTicks(Sharingan.FREE_TICKS_AFTER_GROWTH);
        data.markPowersDirty();
        moment(player, Component.translatable("title.noirm.sharingan.tomoe", eyes.sharinganStage()),
                Component.translatable("subtitle.noirm.sharingan.tomoe." + eyes.sharinganStage()), 0.7f);
    }

    private static void moment(Player player, Component title, Component subtitle, float strength) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, strength, 1.4f);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, strength, 0.9f);
        if (player.level() instanceof ServerLevel level) {
            Vec3 eyesAt = player.getEyePosition();
            Vfx.flash(level, eyesAt, new Vfx.Palette(0xFF3030, 0xD01010, 0x300000, 0xFF8080), 0.6f * strength + 0.4f);
            Vfx.burst(level, Vfx.ring(0xD01010, 1.8f * strength + 0.6f, 14), player.position().add(0, 0.05, 0), 1, 0, 0);
            level.sendParticles(RED, eyesAt.x, eyesAt.y, eyesAt.z, Math.round(24 * strength), 0.35, 0.25, 0.35, 0.02);
        }
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
            serverPlayer.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
            serverPlayer.connection.send(new ClientboundSetTitleTextPacket(title));
            serverPlayer.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    // ---------------------------------------------------------------- NPC Uchiha

    /** Some Uchiha ninjas the world rolls haven't awakened their eyes yet. */
    public static void rollNpcDormant(SparringNinja ninja, Clan clan) {
        if (clan.hasSharingan() && ninja.getRandom().nextFloat() < NPC_DORMANT_CHANCE) {
            ninja.setSharinganStage(0);
        }
    }

    /** An Uchiha ninja whose Sharingan is still asleep. */
    public static boolean isDormantNpc(SparringNinja ninja) {
        if (ninja.activeSharinganStage() > 0 || ninja.clanId().isEmpty() || ninja.level().getServer() == null) {
            return false;
        }
        return WorldClans.get(ninja.level().getServer()).find(ninja.clanId()).map(Clan::hasSharingan).orElse(false);
    }

    @SubscribeEvent
    public static void livingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof SparringNinja ninja && !ninja.level().isClientSide() && ninja.tickCount % 10 == 0) {
            tickNpc(ninja);
        }
    }

    /** A dormant Uchiha ninja badly hurt in a fight awakens its eyes. */
    public static boolean tickNpc(SparringNinja ninja) {
        if (ninja.getTarget() == null || ninja.getHealth() > ninja.getMaxHealth() * NPC_AWAKEN_HEALTH || !isDormantNpc(ninja)) {
            return false;
        }
        awakenNpc(ninja, "message.noirm.sharingan.npc_awakened");
        return true;
    }

    private static void awakenNpc(SparringNinja ninja, String messageKey) {
        ninja.setSharinganStage(1);
        if (ninja.level() instanceof ServerLevel level) {
            Vec3 eyesAt = ninja.getEyePosition();
            level.sendParticles(RED, eyesAt.x, eyesAt.y, eyesAt.z, 20, 0.3, 0.2, 0.3, 0.02);
            level.playSound(null, ninja.getX(), ninja.getY(), ninja.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 0.8f, 1.5f);
            List<Player> near = level.getEntitiesOfClass(Player.class, ninja.getBoundingBox().inflate(ALLY_DEATH_RANGE));
            near.forEach(p -> p.displayClientMessage(Component.translatable(messageKey, ninja.getName()), false));
        }
    }
}
