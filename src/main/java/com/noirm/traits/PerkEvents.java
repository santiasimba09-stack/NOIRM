package com.noirm.traits;

import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.clan.WorldClans;
import com.noirm.creator.CreatorData;
import com.noirm.entity.SparringNinja;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Puts clan perks and talents to work every tick and on every hit: health and stamina maximums,
 * health regeneration, the Inuzuka hound, damage dealt and taken, damage over time, poison
 * immunity, liquid and smoke bodies, the Aburame's chakra-eating insects; and the one-time roll
 * for characters made before talents existed ({@link #migrate}). Players and NPC ninjas alike,
 * where it makes sense.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class PerkEvents {
    private static final UUID HEALTH_ID = UUID.fromString("6a8e1f3c-2b4d-4e5f-9a1b-7c3d5e7f9b21");
    /** Health regeneration only runs while the food bar is above this (a starving body doesn't heal). */
    public static final int REGEN_MIN_FOOD = 1;
    /** The Inuzuka hound: extra max health and its name. */
    public static final double HOUND_HEALTH = 20;
    /** Damage types that count as damage over time. */
    private static final List<net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType>> DOT =
            List.of(DamageTypes.MAGIC, DamageTypes.WITHER, DamageTypes.ON_FIRE, DamageTypes.IN_FIRE, DamageTypes.HOT_FLOOR);

    private PerkEvents() {}

    // ---------------------------------------------------------------- starting stats, rolls

    /** Adds the START_* perks of a clan and some talents to the stats (once, at creation). */
    public static void addStartStats(ShinobiData data, Map<String, Float> clanPerks, List<String> talents) {
        Map<Perk, Float> t = Perks.resolve(clanPerks, talents);
        data.setSpeed(data.getSpeed() + t.getOrDefault(Perk.START_SPEED, 0f));
        data.setTaijutsu(data.getTaijutsu() + t.getOrDefault(Perk.START_TAIJUTSU, 0f));
        data.setReserves(data.getReserves() + t.getOrDefault(Perk.START_RESERVES, 0f));
        data.setControl(data.getControl() + t.getOrDefault(Perk.START_CONTROL, 0f));
        data.setWillpower(data.getWillpower() + t.getOrDefault(Perk.START_WILLPOWER, 0f));
        data.setVitality(data.getVitality() + t.getOrDefault(Perk.START_VITALITY, 0f));
        data.setDurability(data.getDurability() + t.getOrDefault(Perk.START_DURABILITY, 0f));
    }

    /** A brand-new character without the creator (gamerule off): roll talents now. */
    public static void rollFresh(Player player, ShinobiData data) {
        Map<String, Float> bias = clanOf(player, data).map(Clan::talentBias).orElse(Map.of());
        List<String> rolled = Talents.roll(player.getRandom(), bias);
        data.talents().set(rolled);
        addStartStats(data, Map.of(), rolled);
        data.talents().setVersion(TalentData.CURRENT);
        data.markDirty();
        data.markPowersDirty();
        player.sendSystemMessage(Component.translatable("message.noirm.talents.yours", Perks.talentNames(rolled)));
    }

    /**
     * A character made before talents existed: their clan's perks (and clan jutsu) arrive, they roll
     * talents once (biased by the clan), and the starting stats of both are added. Returns whether
     * anything happened.
     */
    public static boolean migrate(Player player, ShinobiData data) {
        if (data.talents().version() >= TalentData.CURRENT || !data.identity().isAssigned()) {
            return false;
        }
        Clan clan = clanOf(player, data).orElse(null);
        if (clan != null) {
            data.identity().refreshPerks(clan);
            for (net.minecraft.resources.ResourceLocation id : clan.clanJutsu()) {
                if (data.jutsu().learn(id, false)) {
                    data.jutsu().equipIfRoom(id);
                }
            }
        }
        if (data.talents().talents().isEmpty()) {
            data.talents().set(Talents.roll(player.getRandom(), clan == null ? Map.of() : clan.talentBias()));
        }
        addStartStats(data, data.identity().perks(), data.talents().talents());
        data.talents().setVersion(TalentData.CURRENT);
        data.markDirty();
        data.markPowersDirty();
        player.sendSystemMessage(Component.translatable("message.noirm.talents.migrated").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable("message.noirm.talents.yours", Perks.talentNames(data.talents().talents())));
        player.sendSystemMessage(Component.translatable("message.noirm.clan.perks_hint").withStyle(ChatFormatting.GRAY));
        return true;
    }

    private static java.util.Optional<Clan> clanOf(Player player, ShinobiData data) {
        if (player.getServer() == null || !data.identity().isAssigned()) {
            return java.util.Optional.empty();
        }
        return WorldClans.get(player.getServer()).find(data.identity().clanId());
    }

    /** After PowerEvents has made sure everyone has a clan: old saves migrate; canon perks follow their file. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        ShinobiCapability.get(player).ifPresent(data -> {
            if (data.creator().mode() != CreatorData.Mode.NONE) {
                return; // still making the character: the creator rolls talents
            }
            if (!migrate(player, data) && data.identity().isCanon()) {
                // A canon clan's file was tuned since: take the new perks (no stats are re-added).
                clanOf(player, data).filter(c -> !c.perks().equals(data.identity().perks())
                        || !c.clanJutsu().equals(data.identity().clanJutsu())).ifPresent(c -> {
                    data.identity().refreshPerks(c);
                    data.markPowersDirty();
                });
            }
        });
    }

    // ---------------------------------------------------------------- every tick

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        ShinobiCapability.get(player).ifPresent(data -> tick(player, data));
    }

    /** Health and stamina maximums, regeneration and the hound. Public for tests. */
    public static void tick(Player player, ShinobiData data) {
        Map<Perk, Float> t = Perks.totals(data);
        applyHealth(player, t.getOrDefault(Perk.MAX_HEALTH, 0f));
        float stamina = t.getOrDefault(Perk.MAX_STAMINA, 0f);
        float applied = data.talents().appliedStamina();
        if (Math.abs(stamina - applied) > 1e-4) {
            data.setMaxStamina(data.getMaxStamina() - applied + stamina);
            data.talents().setAppliedStamina(stamina);
        }
        if (player.tickCount % 20 == 0) {
            regen(player, t.getOrDefault(Perk.HEALTH_REGEN, 0f), player.getFoodData().getFoodLevel() >= REGEN_MIN_FOOD);
            if (t.getOrDefault(Perk.HOUND, 0f) > 0f && !data.talents().houndGiven() && !(player instanceof FakePlayer)
                    && !data.creator().held()) {
                giveHound(player, data);
            }
        }
    }

    /** NPC ninjas: their blood's health and regeneration. */
    @SubscribeEvent
    public static void livingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof SparringNinja ninja) || ninja.level().isClientSide() || ninja.tickCount % 20 != 7) {
            return;
        }
        Map<Perk, Float> t = Perks.totals(ninja);
        applyHealth(ninja, t.getOrDefault(Perk.MAX_HEALTH, 0f));
        regen(ninja, t.getOrDefault(Perk.HEALTH_REGEN, 0f), true);
    }

    private static void applyHealth(LivingEntity entity, float amount) {
        AttributeInstance health = entity.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) {
            return;
        }
        AttributeModifier current = health.getModifier(HEALTH_ID);
        if (current != null && Math.abs(current.getAmount() - amount) < 1e-6 || current == null && amount == 0f) {
            return;
        }
        double before = current == null ? 0 : current.getAmount();
        health.removeModifier(HEALTH_ID);
        if (amount != 0f) {
            health.addTransientModifier(new AttributeModifier(HEALTH_ID, "noirm_blood", amount, AttributeModifier.Operation.ADDITION));
        }
        if (amount > before && entity.isAlive()) {
            entity.setHealth(entity.getHealth() + (float) (amount - before)); // more health from the blood comes filled, not as a wound
        }
        if (entity.getHealth() > entity.getMaxHealth()) {
            entity.setHealth(entity.getMaxHealth());
        }
    }

    private static void regen(LivingEntity entity, float perSecond, boolean fed) {
        if (perSecond > 0f && fed && entity.isAlive() && entity.getHealth() < entity.getMaxHealth()) {
            entity.heal(perSecond);
        } else if (perSecond < 0f && entity.isAlive() && entity.getHealth() > 2f) {
            entity.setHealth(Math.max(2f, entity.getHealth() + perSecond)); // a sickly body loses a little, never below a heart
        }
    }

    /** The Inuzuka's ninja hound: a tamed, tougher wolf that follows you from the start. */
    public static Wolf giveHound(Player player, ShinobiData data) {
        data.talents().setHoundGiven(true);
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        Wolf wolf = EntityType.WOLF.create(level);
        if (wolf == null) {
            return null;
        }
        wolf.moveTo(player.getX() + 1, player.getY(), player.getZ() + 1, player.getYRot(), 0f);
        wolf.tame(player);
        wolf.setCustomName(Component.translatable("entity.noirm.ninja_hound", player.getName()));
        AttributeInstance health = wolf.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.addPermanentModifier(new AttributeModifier(UUID.fromString("0d7c2a51-3e4f-4b6a-8c9d-1e2f3a4b5c6d"),
                    "noirm_ninja_hound", HOUND_HEALTH, AttributeModifier.Operation.ADDITION));
        }
        wolf.setHealth(wolf.getMaxHealth());
        wolf.setPersistenceRequired();
        level.addFreshEntity(wolf);
        level.sendParticles(ParticleTypes.CLOUD, wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), 20, 0.4, 0.4, 0.4, 0.02);
        player.sendSystemMessage(Component.translatable("message.noirm.clan.hound").withStyle(ChatFormatting.GOLD));
        return wolf;
    }

    // ---------------------------------------------------------------- hits

    /** A body of water or smoke: a physical hit can pass straight through. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void attacked(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !physical(event.getSource())) {
            return;
        }
        Map<Perk, Float> t = Perks.totals(victim);
        if (t.isEmpty()) {
            return;
        }
        float chance = t.getOrDefault(Perk.PHASE_DODGE, 0f);
        boolean water = false;
        float wet = t.getOrDefault(Perk.WATER_DODGE, 0f);
        if (wet > 0f && wet(victim)) {
            chance += wet;
            water = true;
        }
        chance = Math.min(Perks.MAX_DODGE, chance);
        if (chance > 0f && victim.getRandom().nextFloat() < chance) {
            event.setCanceled(true);
            if (victim.level() instanceof ServerLevel level) {
                level.sendParticles(water ? ParticleTypes.SPLASH : ParticleTypes.LARGE_SMOKE, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6,
                        victim.getZ(), water ? 30 : 14, victim.getBbWidth() * 0.5, victim.getBbHeight() * 0.3, victim.getBbWidth() * 0.5, 0.05);
                level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), water ? SoundEvents.PLAYER_SPLASH : SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.PLAYERS, 0.6f, 1.4f);
            }
        }
    }

    /** Damage dealt and taken, damage over time, fire weakness, and the chakra-eating insects. */
    @SubscribeEvent
    public static void hurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();
        float amount = event.getAmount();
        Map<Perk, Float> mine = Perks.totals(victim);
        if (!mine.isEmpty()) {
            amount *= Math.max(Perks.MIN_MULTIPLIER, 1f + mine.getOrDefault(Perk.DAMAGE_TAKEN, 0f));
            if (dot(source)) {
                amount *= Math.max(0f, 1f - Math.min(0.9f, mine.getOrDefault(Perk.DOT_RESIST, 0f)));
            }
            if (source.is(DamageTypeTags.IS_FIRE)) {
                amount *= 1f + mine.getOrDefault(Perk.FIRE_WEAKNESS, 0f);
            }
        }
        if (melee(source) && source.getEntity() instanceof LivingEntity attacker) {
            Map<Perk, Float> theirs = Perks.totals(attacker);
            if (!theirs.isEmpty()) {
                amount *= Math.max(Perks.MIN_MULTIPLIER, 1f + theirs.getOrDefault(Perk.MELEE_DAMAGE, 0f));
                float leech = theirs.getOrDefault(Perk.CHAKRA_LEECH, 0f);
                if (leech > 0f) {
                    leech(attacker, victim, leech);
                }
            }
        }
        if (amount != event.getAmount()) {
            event.setAmount(amount);
        }
    }

    /** The insects feed on a chakra user's chakra; half comes back to their host. */
    private static void leech(LivingEntity attacker, LivingEntity victim, float amount) {
        if (!(victim instanceof Player target)) {
            return;
        }
        ShinobiData data = ShinobiCapability.get(target).orElse(null);
        if (data == null || data.getChakra() <= 0f) {
            return;
        }
        float taken = Math.min(amount, data.getChakra());
        data.setChakra(data.getChakra() - taken);
        if (attacker instanceof Player host) {
            ShinobiCapability.get(host).ifPresent(d -> d.setChakra(d.getChakra() + taken * 0.5f));
        }
    }

    /** A loud chakra (the Uzumaki, the Senju, the Hoshigaki): mobs and beasts notice you from further away. */
    @SubscribeEvent
    public static void visibility(LivingEvent.LivingVisibilityEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide()) {
            float loud = Perks.beaconMultiplier(player);
            if (loud > 1f) {
                event.modifyVisibility(loud);
            }
        }
    }

    /** Insects (or a hardy body) eat poison: poison and wither don't take hold. */
    @SubscribeEvent
    public static void effectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        var effect = event.getEffectInstance().getEffect();
        if ((effect == MobEffects.POISON || effect == MobEffects.WITHER) && Perks.has(event.getEntity(), Perk.POISON_IMMUNITY)) {
            event.setResult(Event.Result.DENY);
        }
    }

    // ---------------------------------------------------------------- rules (public for tests)

    /** A blow or a projectile from someone, not a jutsu, fire or explosion. */
    public static boolean physical(DamageSource source) {
        return source.getEntity() != null && !source.is(DamageTypes.INDIRECT_MAGIC) && !source.is(DamageTypes.MAGIC)
                && !source.is(DamageTypeTags.IS_FIRE) && !source.is(DamageTypeTags.IS_EXPLOSION)
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && (source.getDirectEntity() instanceof LivingEntity || source.getDirectEntity() instanceof Projectile);
    }

    /** A hand-to-hand blow (the attacker's own body or weapon). */
    public static boolean melee(DamageSource source) {
        return physical(source) && source.getDirectEntity() == source.getEntity();
    }

    public static boolean dot(DamageSource source) {
        if (source.getEntity() != null) {
            return false;
        }
        for (var key : DOT) {
            if (source.is(key)) {
                return true;
            }
        }
        return false;
    }

    /** In water, in the rain, or with water close by (a river, the sea). */
    public static boolean wet(LivingEntity entity) {
        return entity.isInWaterRainOrBubble() || com.noirm.jutsu.JutsuCaster.nearWater(entity);
    }
}
