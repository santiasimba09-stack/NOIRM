package com.noirm.jutsu;

import com.noirm.Noirm;
import com.noirm.awakening.AwakeningData;
import com.noirm.clan.Clan;
import com.noirm.clan.ClanTrait;
import com.noirm.clan.Nature;
import com.noirm.combat.CombatSystem;
import com.noirm.biju.BijuSystem;
import com.noirm.dojutsu.Byakugan;
import com.noirm.dojutsu.Sharingan;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Casting jutsu, for players (keys 1–4 → loadout slots) and NPCs. Server side only.
 *
 * <p>Costs: TAP pays when cast, CHARGE pays when charging starts, CHANNEL pays every tick.
 * Power and cost depend on nature affinity, mastery, clan traits and states (locked in, cloak).
 */
public final class JutsuCaster {
    /** What can stop a cast. Each has a message ({@code message.noirm.jutsu.<name>}). */
    public enum Result { OK, EMPTY_SLOT, UNKNOWN, COOLDOWN, NO_CHAKRA, NO_NATURE, NEEDS_DOJUTSU, NEEDS_CLOAK, BUSY, SEALED, NEEDS_GATE, TOO_HURT }

    /**
     * A jutsu in progress: hand seals before a TAP jutsu (or an NPC's cast) fires, or a CHARGE or
     * CHANNEL jutsu being held. The first {@code sealTicks} are the <b>seal phase</b>: the caster
     * moves at {@link #WEAVING_SPEED} and a hit can pause, slow or break the seals.
     */
    public static final class Casting {
        final ResourceLocation id;
        final JutsuDefinition def;
        final int slot;
        /** Chakra paid when it started: refunded if a charge is released too early, partly lost if the seals break. */
        final float paid;
        /** TAP jutsu and NPC casts: fire with {@link #charge} once the seals are done. */
        final boolean firesAfterSeals;
        final float charge;
        /** Length of the seal phase, for the HUD bar of a TAP jutsu. */
        final int sealTotal;
        int ticks;
        /** Ticks of hand seals left (0 = past the seal phase, or none). */
        int sealTicks;
        /** Ticks the seals are held up by a light hit, and how many have been added so far. */
        int pauseTicks;
        int pausedTotal;

        Casting(ResourceLocation id, JutsuDefinition def, int slot, float paid, boolean firesAfterSeals, float charge, int sealTicks) {
            this.id = id;
            this.def = def;
            this.slot = slot;
            this.paid = paid;
            this.firesAfterSeals = firesAfterSeals;
            this.charge = charge;
            this.sealTicks = sealTicks;
            this.sealTotal = sealTicks;
        }

        /** Still forming hand seals? */
        public boolean sealing() {
            return sealTicks > 0;
        }

        /** The full length of the HUD bar: the seals for a TAP jutsu, the charge or the channel otherwise. */
        public int barMax() {
            return firesAfterSeals ? sealTotal : def.cast() == CastType.CHARGE ? def.maxCharge() : def.maxDuration();
        }

        public ResourceLocation id() {
            return id;
        }

        public int slot() {
            return slot;
        }

        public int ticks() {
            return ticks;
        }

        public JutsuDefinition def() {
            return def;
        }
    }

    private static final Map<LivingEntity, Casting> CASTING = new WeakHashMap<>();
    /** Cooldowns for NPCs (players keep theirs in their JutsuBook): jutsu → game time ready. */
    private static final Map<LivingEntity, Map<ResourceLocation, Long>> NPC_READY_AT = new WeakHashMap<>();

    // ------------------------------------------------------------ the seal phase (blueprint section 4)

    /** Ticks per hand seal before a TAP jutsu (the seal animation, sped up). */
    public static final int SEAL_PHASE_TICKS_PER_SEAL = 2;
    /** No jutsu spends longer than this on its seals before it fires. */
    public static final int SEAL_PHASE_MAX_TICKS = 12;
    /** Movement while weaving seals: 80% speed. */
    public static final float WEAVING_SPEED = 0.8f;
    /** A hit below this fraction of max health only pauses the seals... */
    public static final float LIGHT_HIT = 0.08f;
    /** ...below this one it slows you hard for a moment; at or above it (or a stagger) the seals break. */
    public static final float HEAVY_HIT = 0.20f;
    /** A light hit holds the seals up this long, at most {@link #MAX_PAUSE_TICKS} in all per cast. */
    public static final int LIGHT_PAUSE_TICKS = 4;
    public static final int MAX_PAUSE_TICKS = 12;
    /** A medium hit: Slowness IV for a second. */
    public static final int MEDIUM_SLOW_TICKS = 20;
    public static final int MEDIUM_SLOW_LEVEL = 3;
    /** Broken seals: this fraction of the jutsu's chakra cost is lost (the rest of what was paid comes back). */
    public static final float FIZZLE_BACKLASH = 0.3f;
    /** Broken seals start no cooldown for players; NPCs wait this long before they try again. */
    public static final int FIZZLE_NPC_COOLDOWN = 20;

    // ------------------------------------------------------------ spam fall-off

    /** Each quick recast of the same minor (E/D/C) projectile jutsu: -20% damage... */
    public static final float SPAM_STEP = 0.2f;
    /** ...down to 40%. */
    public static final float SPAM_FLOOR = 0.4f;
    /** "Quick" = within 3 s of it being ready again (its cooldown + this). */
    public static final int SPAM_WINDOW_TICKS = 60;

    private static final java.util.UUID WEAVING_ID = java.util.UUID.fromString("6f1c0a52-3b9e-4d77-9a1e-5e0c7a3f2b11");
    private static final net.minecraft.world.entity.ai.attributes.AttributeModifier WEAVING = new net.minecraft.world.entity.ai.attributes.AttributeModifier(
            WEAVING_ID, "noirm.weaving_seals", WEAVING_SPEED - 1f, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL);
    /** Per caster: jutsu -> {game time until which a recast counts as spam, streak}. Touched only when a jutsu fires. */
    private static final Map<LivingEntity, Map<ResourceLocation, long[]>> SPAM = new WeakHashMap<>();

    private JutsuCaster() {}

    public static Casting casting(LivingEntity entity) {
        return CASTING.get(entity);
    }

    /** How long this jutsu's seals take before it fires: 2 ticks a seal, at most 12; 0 if it has none or is quick. */
    public static int sealPhaseTicks(ResourceLocation id, JutsuDefinition def) {
        JutsuSeals.Sequence seals = def.quick() ? null : JutsuSeals.get(id);
        return seals == null || seals.seals().isEmpty() ? 0
                : Math.min(SEAL_PHASE_MAX_TICKS, SEAL_PHASE_TICKS_PER_SEAL * seals.seals().size());
    }

    /** Is the 80%-speed weaving modifier on this entity? */
    public static boolean isWeaving(LivingEntity entity) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance speed = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        return speed != null && speed.getModifier(WEAVING_ID) != null;
    }

    private static void startWeaving(LivingEntity entity) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance speed = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(WEAVING_ID) == null) {
            speed.addTransientModifier(WEAVING); // transient: never saved, so it can't survive a relog
            sendSealState(entity, true);
        }
    }

    private static void stopWeaving(LivingEntity entity) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance speed = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(WEAVING_ID) != null) {
            speed.removeModifier(WEAVING_ID);
            sendSealState(entity, false);
        }
    }

    /** The weaving slow is on exactly while the seals are formed: tell the player's client (no melee meanwhile, see {@link com.noirm.combat.SealLock}). */
    private static void sendSealState(LivingEntity entity, boolean forming) {
        if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            com.noirm.network.NoirmNetwork.sendToPlayer(player, new com.noirm.network.MovementPackets.SealState(forming));
        }
    }

    /** Ends whatever is in progress, with no effects (death, logout): the weaving slow always goes with it. */
    public static void cancel(LivingEntity entity) {
        end(entity);
    }

    /** Forgets a casting and drops the weaving slow. Every way a cast ends goes through here. */
    private static void end(LivingEntity entity) {
        CASTING.remove(entity);
        stopWeaving(entity);
    }

    /** Starts the hand seals: the sequence's animation fitted to {@code animationTicks}, and the weaving slow. */
    private static void startSeals(LivingEntity caster, ResourceLocation id, Casting casting, int animationTicks) {
        JutsuSeals.Sequence seals = JutsuSeals.get(id);
        if (seals != null) {
            castAnimation(caster, seals.animation(id), animationTicks);
        }
        if (casting.sealTicks > 0) {
            startWeaving(caster);
        }
    }

    /**
     * The caster took {@code amount} damage. In the seal phase: a light hit pauses the seals, a
     * medium one slows you hard, a heavy one breaks them (see {@link #fizzle}).
     */
    public static void onHurt(LivingEntity entity, float amount) {
        Casting casting = CASTING.get(entity);
        if (casting == null || casting.sealTicks <= 0 || amount <= 0) {
            return;
        }
        float scale = amount / Math.max(1f, entity.getMaxHealth());
        if (scale >= HEAVY_HIT) {
            fizzle(entity, casting);
        } else if (scale >= LIGHT_HIT) {
            entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
                    MEDIUM_SLOW_TICKS, MEDIUM_SLOW_LEVEL, false, false, true));
            if (entity instanceof Player player) {
                player.displayClientMessage(Component.translatable("message.noirm.jutsu.seals_shaken"), true);
            }
        } else {
            int pause = Math.min(LIGHT_PAUSE_TICKS, MAX_PAUSE_TICKS - casting.pausedTotal);
            if (pause > 0) {
                casting.pauseTicks += pause;
                casting.pausedTotal += pause;
                // The hands hold still for the pause (the animation's hit-stop).
                com.noirm.network.NoirmNetwork.sendToTrackingAndSelf(entity, new com.noirm.network.CombatClientPackets.PlayAnimation(
                        entity.getId(), com.noirm.network.CombatClientPackets.HITSTOP, pause));
            }
        }
    }

    /**
     * The seals broke: the jutsu fizzles in a puff of black smoke, {@link #FIZZLE_BACKLASH} of its
     * cost is lost (the rest of what was paid comes back) and no cooldown starts.
     */
    public static void fizzle(LivingEntity entity, Casting casting) {
        end(entity);
        if (entity instanceof Player player) {
            ShinobiCapability.get(player).ifPresent(data -> {
                if (!player.getAbilities().instabuild) {
                    float net = casting.paid - FIZZLE_BACKLASH * cost(player, casting.id, casting.def);
                    data.setChakra(Math.max(0f, data.getChakra() + net));
                }
                data.markPowersDirty();
            });
            player.displayClientMessage(Component.translatable("message.noirm.jutsu.fizzled"), true);
        } else {
            if (entity instanceof com.noirm.entity.SparringNinja ninja && !(ninja instanceof com.noirm.entity.ShadowClone)) {
                // The fight goal paid the full cost up front: give back all but the backlash.
                ninja.setChakra(ninja.chakra() + (1f - FIZZLE_BACKLASH) * cost(ninja, casting.id, casting.def));
            }
            NPC_READY_AT.computeIfAbsent(entity, e -> new HashMap<>()).put(casting.id, entity.level().getGameTime() + FIZZLE_NPC_COOLDOWN);
        }
        net.minecraft.world.phys.Vec3 hands = entity.getEyePosition().add(entity.getViewVector(1f).scale(0.6)).add(0, -0.45, 0);
        com.noirm.vfx.Vfx.burst(entity.level(), com.noirm.vfx.Vfx.smoke(0x151515, 0.45f, 34), hands, 14, 0.3, 0.03);
        com.noirm.vfx.Vfx.burst(entity.level(), com.noirm.vfx.Vfx.smoke(0x050505, 0.7f, 50), hands.add(0, 0.2, 0), 6, 0.4, 0.02);
        com.noirm.vfx.Vfx.radial(entity.level(), com.noirm.vfx.Vfx.spark(0x6A5A8A, 0.1f, 6), hands, 5, 0.25);
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 0.8f);
    }

    /**
     * Spam fall-off: recasting the same minor (E/D/C) projectile jutsu within
     * {@link #SPAM_WINDOW_TICKS} of it being ready again weakens it by {@link #SPAM_STEP} a time,
     * down to {@link #SPAM_FLOOR}; a pause resets it. Records this cast and returns its multiplier.
     */
    public static float spamFactor(LivingEntity caster, ResourceLocation id, JutsuDefinition def) {
        if (!"projectile".equals(def.behavior()) || !isMinorRank(def.rank())) {
            return 1f;
        }
        long now = caster.level().getGameTime();
        long[] track = SPAM.computeIfAbsent(caster, e -> new HashMap<>()).computeIfAbsent(id, k -> new long[] {Long.MIN_VALUE, 0});
        track[1] = now <= track[0] ? track[1] + 1 : 0;
        track[0] = now + cooldown(caster, id, def) + SPAM_WINDOW_TICKS;
        return Math.max(SPAM_FLOOR, 1f - SPAM_STEP * track[1]);
    }

    private static boolean isMinorRank(String rank) {
        return "E".equals(rank) || "D".equals(rank) || "C".equals(rank);
    }

    // ------------------------------------------------------------ players

    /** A jutsu key went down or up. */
    public static Result onKey(Player player, int slot, boolean pressed) {
        if (!pressed) {
            Casting casting = CASTING.get(player);
            if (casting != null && casting.slot == slot && !casting.firesAfterSeals) {
                release(player, casting);
            }
            return Result.OK;
        }
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (data == null) {
            return Result.UNKNOWN;
        }
        ResourceLocation id = data.jutsu().slot(slot);
        Result result = tryStart(player, data, id, slot);
        if (result != Result.OK) {
            player.displayClientMessage(Component.translatable("message.noirm.jutsu." + result.name().toLowerCase(java.util.Locale.ROOT)), true);
        }
        return result;
    }

    private static Result tryStart(Player player, ShinobiData data, ResourceLocation id, int slot) {
        if (id == null) {
            return Result.EMPTY_SLOT;
        }
        JutsuDefinition def = JutsuData.get(id);
        if (def == null || !data.jutsu().knows(id)) {
            return Result.UNKNOWN;
        }
        if (def.behavior().equals("mode") && ModeSystem.isActive(player, id)) {
            ModeSystem.end(player); // switching a mode off is free and ignores its cooldown
            return Result.OK;
        }
        if (CASTING.containsKey(player) || !CombatSystem.canAct(player)) {
            return Result.BUSY;
        }
        Result check = checkRequirements(player, id, def);
        if (check != Result.OK) {
            return check;
        }
        if (data.jutsu().cooldown(id) > 0) {
            return Result.COOLDOWN;
        }
        float cost = cost(player, id, def) * (def.cast() == CastType.CHANNEL ? 1f / 20f : 1f);
        if (!player.getAbilities().instabuild && !data.tryConsumeChakra(cost)) {
            return Result.NO_CHAKRA;
        }
        JutsuSeals.Sequence named = JutsuSeals.get(id);
        if (named != null) {
            player.displayClientMessage(named.describe(), true); // the seals, above the hotbar
        }
        float paid = player.getAbilities().instabuild ? 0f : cost;
        if (def.cast() == CastType.TAP) {
            int seals = sealPhaseTicks(id, def);
            if (seals == 0) {
                execute(player, id, def, 1f); // no seals, or a reflex jutsu: at once
            } else {
                Casting casting = new Casting(id, def, slot, paid, true, 1f, seals);
                CASTING.put(player, casting);
                startSeals(player, id, casting, seals); // the seals, sped up; it fires when they're done
                data.markPowersDirty();
            }
        } else {
            JutsuSeals.Sequence seals = def.quick() ? null : JutsuSeals.get(id);
            // Charged and channelled jutsu keep their flow: the seals they form while charging are the seal phase.
            Casting casting = new Casting(id, def, slot, paid, false, 1f, seals == null ? 0 : seals.ticks());
            CASTING.put(player, casting);
            if (seals != null) {
                startSeals(player, id, casting, seals.ticks()); // this jutsu's own seals
            } else {
                castAnimation(player, "cast_seals", def.cast() == CastType.CHARGE ? Math.max(8, def.minCharge()) : 14);
            }
            if (def.cast() == CastType.CHANNEL) {
                runBehavior(player, id, def, 1f, true, 0);
            }
            data.markPowersDirty();
        }
        return Result.OK;
    }

    /** Called every server tick for every living entity: advances charging and channeling. */
    public static void tick(LivingEntity entity) {
        Casting casting = CASTING.get(entity);
        if (casting == null) {
            if ((entity.tickCount & 15) == 0) {
                stopWeaving(entity); // a safety net: the weaving slow never outlives its cast
            }
            return;
        }
        if (!entity.isAlive()) {
            stop(entity, casting, false);
            return;
        }
        if (CombatSystem.isStaggered(entity)) {
            if (casting.sealTicks > 0) {
                fizzle(entity, casting); // staggered or knocked down mid-seal: the seals break
            } else {
                stop(entity, casting, false);
            }
            return;
        }
        if (casting.pauseTicks > 0) {
            casting.pauseTicks--; // a light hit holds the seals up
            return;
        }
        if (casting.sealTicks > 0 && --casting.sealTicks == 0) {
            stopWeaving(entity);
        }
        if (casting.firesAfterSeals) {
            casting.ticks++;
            if (casting.sealTicks == 0) {
                end(entity);
                execute(entity, casting.id, casting.def, casting.charge);
                if (entity instanceof Player player) {
                    ShinobiCapability.get(player).ifPresent(ShinobiData::markPowersDirty);
                } else {
                    afterNpcCast(entity, casting.id, casting.def);
                }
            }
            return;
        }
        if (casting.def.cast() == CastType.CHARGE && casting.ticks < casting.def.maxCharge()
                && entity instanceof Player player && !player.getAbilities().instabuild) {
            // Charging pours more chakra in; with none left, the charge stops growing.
            float extra = cost(player, casting.id, casting.def) * CHARGE_EXTRA_COST / Math.max(1, casting.def.maxCharge());
            boolean paid = ShinobiCapability.get(player).map(d -> d.tryConsumeChakra(extra)).orElse(false);
            if (!paid) {
                return;
            }
        }
        casting.ticks++;
        if (casting.def.cast() == CastType.CHARGE) {
            if (casting.def.behavior().equals("rasengan")) {
                CanonTechniques.rasenganOrb(entity, Math.min(1f, casting.ticks / (float) Math.max(1, casting.def.maxCharge())));
            } else if (casting.ticks <= casting.def.maxCharge() && casting.ticks % 2 == 0) {
                // Anticipation: energy gathers in front of the caster, tighter and brighter as it fills.
                float progress = Math.min(1f, casting.ticks / (float) Math.max(1, casting.def.maxCharge()));
                net.minecraft.world.phys.Vec3 hand = entity.getEyePosition().add(entity.getViewVector(1f).scale(0.8)).add(0, -0.35, 0);
                com.noirm.vfx.Vfx.Palette palette = com.noirm.vfx.Vfx.palette(casting.def.nature().orElse(null));
                com.noirm.vfx.Vfx.gather(entity.level(), hand, palette, 1.2f - 0.6f * progress, 3 + Math.round(progress * 4));
                com.noirm.vfx.Vfx.burst(entity.level(), com.noirm.vfx.Vfx.glow(palette.core(), palette.mid(), 0.2f + 0.5f * progress, 0.1f, 4), hand, 1, 0, 0);
                if (casting.def.nature().orElse(null) == com.noirm.clan.Nature.FIRE) {
                    // Fire swells at the mouth: flame cells and a tongue, bigger as it fills.
                    com.noirm.vfx.FireVfx.fireball(entity.level(), hand.add(entity.getViewVector(1f).scale(0.6)), net.minecraft.world.phys.Vec3.ZERO, 0.12f + 0.25f * progress);
                }
                if (casting.def.behavior().equals("dash_strike")) {
                    // Chidori: "a thousand birds" chirping in the hand.
                    entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                            net.minecraft.sounds.SoundEvents.PARROT_AMBIENT, net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.8f + progress * 0.3f);
                    com.noirm.vfx.Vfx.radial(entity.level(), com.noirm.vfx.Vfx.spark(0x9AD8FF, 0.15f, 5), hand, 3, 0.3);
                    // Arcs leap from the hand to the ground, longer as it fills.
                    for (int i = 0; i < 1 + Math.round(progress * 2); i++) {
                        net.minecraft.world.phys.Vec3 ground = hand.add((entity.getRandom().nextDouble() - 0.5) * 2.5 * (0.5 + progress),
                                -1.2 - entity.getRandom().nextDouble(), (entity.getRandom().nextDouble() - 0.5) * 2.5 * (0.5 + progress));
                        com.noirm.vfx.LightningVfx.bolt(entity.level(), hand, ground, com.noirm.vfx.LightningVfx.BLUE_WHITE, 0.07f + 0.06f * progress, 4, 2,
                                com.noirm.vfx.LightningVfx.FORKY | (progress > 0.6f ? com.noirm.vfx.LightningVfx.IMPACT : 0));
                    }
                }
            }
            return;
        }
        // CHANNEL: pay, act, stop when out of chakra or time
        if (entity instanceof Player player && !player.getAbilities().instabuild) {
            boolean paid = ShinobiCapability.get(player)
                    .map(d -> d.tryConsumeChakra(cost(player, casting.id, casting.def) / 20f)).orElse(false);
            if (!paid) {
                stop(entity, casting, true);
                return;
            }
        }
        runBehavior(entity, casting.id, casting.def, 1f, false, casting.ticks);
        if (casting.ticks >= casting.def.maxDuration()) {
            stop(entity, casting, true);
        }
    }

    private static void release(LivingEntity entity, Casting casting) {
        if (casting.def.cast() == CastType.CHARGE) {
            if (casting.ticks < casting.def.minCharge()) {
                if (entity instanceof Player player) {
                    player.displayClientMessage(Component.translatable("message.noirm.jutsu.not_charged"), true);
                    ShinobiCapability.get(player).ifPresent(d -> d.setChakra(d.getChakra() + casting.paid));
                }
                stop(entity, casting, false);
                return;
            }
            int range = Math.max(1, casting.def.maxCharge() - casting.def.minCharge());
            float charge = Math.min(1f, (casting.ticks - casting.def.minCharge()) / (float) range);
            end(entity);
            execute(entity, casting.id, casting.def, charge);
        } else {
            stop(entity, casting, true);
        }
    }

    /** Ends a charge/channel. {@code completed} channels go on cooldown and earn mastery. */
    private static void stop(LivingEntity entity, Casting casting, boolean completed) {
        end(entity);
        if (completed) {
            afterCast(entity, casting.id, casting.def);
        }
        if (entity instanceof Player player) {
            ShinobiCapability.get(player).ifPresent(ShinobiData::markPowersDirty);
        }
    }

    // ------------------------------------------------------------ NPCs

    /**
     * An NPC casts a jutsu directly (no chakra; its own cooldowns). Returns true if cast, or if
     * its hand seals started: like a player's, it fires when they're done, and can be broken.
     */
    public static boolean npcCast(LivingEntity caster, ResourceLocation id, float charge) {
        JutsuDefinition def = JutsuData.get(id);
        if (def == null || CASTING.containsKey(caster) || !CombatSystem.canAct(caster) || checkRequirements(caster, id, def) != Result.OK) {
            return false;
        }
        long now = caster.level().getGameTime();
        Map<ResourceLocation, Long> ready = NPC_READY_AT.computeIfAbsent(caster, e -> new HashMap<>());
        if (ready.getOrDefault(id, 0L) > now) {
            return false;
        }
        if (def.cast() == CastType.CHANNEL) {
            return false; // NPCs only use instant jutsu for now
        }
        int seals = sealPhaseTicks(id, def);
        if (seals > 0) {
            Casting casting = new Casting(id, def, -1, 0f, true, charge, seals);
            CASTING.put(caster, casting);
            startSeals(caster, id, casting, seals);
            return true;
        }
        execute(caster, id, def, charge);
        afterNpcCast(caster, id, def);
        return true;
    }

    /** An NPC's jutsu went off: its cooldown, training and mastery. */
    private static void afterNpcCast(LivingEntity caster, ResourceLocation id, JutsuDefinition def) {
        NPC_READY_AT.computeIfAbsent(caster, e -> new HashMap<>()).put(id, caster.level().getGameTime() + cooldown(caster, id, def));
        if (caster instanceof com.noirm.entity.SparringNinja ninja) {
            com.noirm.ally.AllyTraining.onJutsuCast(ninja, def.chakraCost());
            ninja.addJutsuXp(id, 1);
            ninja.onJutsuCast();
        }
    }

    /** Is this NPC's own cooldown for {@code id} over? */
    public static boolean npcReady(LivingEntity caster, ResourceLocation id) {
        Map<ResourceLocation, Long> ready = NPC_READY_AT.get(caster);
        return ready == null || ready.getOrDefault(id, 0L) <= caster.level().getGameTime();
    }

    // ------------------------------------------------------------ shared

    /** The caster's hands: seals while charging or channelling, a push on release (players and ninjas alike). */
    static void castAnimation(LivingEntity caster, String name, int ticks) {
        castAnimation(caster, com.noirm.Noirm.id(name), ticks);
    }

    static void castAnimation(LivingEntity caster, ResourceLocation animation, int ticks) {
        if (caster instanceof Player) {
            com.noirm.network.NoirmNetwork.sendToTrackingAndSelf(caster,
                    new com.noirm.network.CombatClientPackets.PlayAnimation(caster.getId(), animation, ticks));
        } else if (caster instanceof com.noirm.combat.CombatAnimated animated) {
            animated.playCombatAnimation(animation, ticks);
        }
    }

    private static void execute(LivingEntity caster, ResourceLocation id, JutsuDefinition def, float charge) {
        castAnimation(caster, "cast_release", 10);
        runBehavior(caster, id, def, charge, true, 0);
        afterCast(caster, id, def);
    }

    private static void runBehavior(LivingEntity caster, ResourceLocation id, JutsuDefinition def, float charge, boolean start, int tick) {
        JutsuBehavior behavior = JutsuBehaviors.get(def.behavior());
        if (behavior == null) {
            Noirm.LOGGER.error("Jutsu {} uses unknown behavior '{}'", id, def.behavior());
            return;
        }
        // A fresh cast of a minor projectile spammed again and again comes out weaker.
        float power = start ? power(caster, id, def) * spamFactor(caster, id, def) : power(caster, id, def);
        JutsuContext context = new JutsuContext(caster, id, def, power, charge);
        FriendlyZones.mark(caster);
        if (start) {
            if (!"mode".equals(def.behavior()) || !ModeSystem.isActive(caster, id)) {
                com.noirm.vfx.Vfx.castFlourish(caster.level(), caster, com.noirm.vfx.Vfx.palette(def.nature().orElse(null)), def.rank());
            }
            behavior.cast(context);
            Sharingan.onJutsuCast(caster, id);
        } else {
            behavior.channel(context, tick);
        }
    }

    private static void afterCast(LivingEntity caster, ResourceLocation id, JutsuDefinition def) {
        if (caster instanceof Player player) {
            ShinobiCapability.get(player).ifPresent(data -> {
                data.jutsu().setCooldown(id, cooldown(caster, id, def));
                int before = data.jutsu().masteryLevel(id);
                data.jutsu().addXp(id, data.identity().has(ClanTrait.JUTSU_PRODIGY) ? 2 : 1);
                if (data.jutsu().masteryLevel(id) > before) {
                    com.noirm.clan.NatureSystem.checkMasteryUnlocks(player, data, id);
                }
                data.markPowersDirty();
            });
        }
    }

    /** Nature and dōjutsu requirements (not chakra or cooldown). */
    public static Result checkRequirements(LivingEntity caster, ResourceLocation id, JutsuDefinition def) {
        if (Byakugan.isSealed(caster)) {
            return Result.SEALED;
        }
        if (def.nature().isPresent() && affinity(caster, id, def) <= 0) {
            return Result.NO_NATURE;
        }
        if (def.requires().isPresent()) {
            JutsuDefinition.Requirement requirement = def.requires().get();
            if ("sharingan".equals(requirement.dojutsu()) && Sharingan.activeStage(caster) < requirement.stage()) {
                return Result.NEEDS_DOJUTSU;
            }
            if ("mangekyo".equals(requirement.dojutsu()) && !com.noirm.dojutsu.Mangekyo.canUse(caster,
                    com.noirm.dojutsu.MangekyoAbility.byKey(id.getPath().substring(id.getPath().lastIndexOf('/') + 1)))) {
                return Result.NEEDS_DOJUTSU;
            }
            if ("byakugan".equals(requirement.dojutsu()) && !Byakugan.isActive(caster)) {
                return Result.NEEDS_DOJUTSU;
            }
            if ("cloak".equals(requirement.dojutsu()) && !BijuSystem.hasBeastChakra(caster)) {
                return Result.NEEDS_CLOAK;
            }
            if ("gate".equals(requirement.dojutsu()) && ModeSystem.gateTier(caster) < requirement.stage()) {
                return Result.NEEDS_GATE;
            }
        }
        if ("mode".equals(def.behavior()) && !ModeSystem.isActive(caster, id) && ModeSystem.tooHurtFor(caster, def)) {
            return Result.TOO_HURT; // the Gates won't open on a body that's already spent
        }
        return Result.OK;
    }

    /**
     * How well the caster channels this jutsu's nature: the clan's affinity, or the imitation
     * affinity for jutsu copied with the Sharingan from a nature they lack. 1 for jutsu with no nature.
     */
    public static float affinity(LivingEntity caster, ResourceLocation id, JutsuDefinition def) {
        if (def.nature().isEmpty()) {
            return Clan.SECONDARY_AFFINITY;
        }
        Nature nature = def.nature().get();
        if (caster instanceof Player player) {
            ShinobiData data = ShinobiCapability.get(player).orElse(null);
            if (data == null) {
                return 0f;
            }
            float affinity = data.identity().affinity(nature);
            if (affinity <= 0 && data.jutsu().isCopied(id) && data.eyes().sharinganStage() > 0) {
                return Clan.IMITATION_AFFINITY;
            }
            return affinity;
        }
        return caster instanceof JutsuUser user ? user.jutsuAffinity(nature) : 0f;
    }

    /** Damage/size multiplier. */
    /**
     * How strong a jutsu comes out: nature affinity × <b>chakra</b> (reserves, +2% per point above
     * 10) × <b>mastery</b> (+15% per level) × states (locked in, nature surge, the cloak).
     * NPCs use their own strength in place of reserves.
     */
    public static float power(LivingEntity caster, ResourceLocation id, JutsuDefinition def) {
        float power = 0.5f + 0.5f * affinity(caster, id, def);
        if (def.nature().isPresent() && usesWater(def.nature().get()) && nearWater(caster)) {
            power *= WATER_SOURCE_BONUS; // water jutsu are far stronger with water to draw from
        }
        boolean taijutsu = isTaijutsu(id);
        if (!(caster instanceof Player) && caster instanceof com.noirm.combat.CombatStats stats) {
            power *= chakraPower(taijutsu ? stats.combatTaijutsu() : stats.combatReserves());
            if (caster instanceof com.noirm.entity.SparringNinja ninja) {
                power *= 1f + MASTERY_POWER * ninja.jutsuMastery(id);
            }
        }
        if (caster instanceof Player player) {
            ShinobiData data = ShinobiCapability.get(player).orElse(null);
            if (data != null) {
                // Taijutsu techniques hit as hard as your body is trained, not your chakra.
                power *= chakraPower(taijutsu ? data.getTaijutsu() : data.getReserves());
                power *= 1f + MASTERY_POWER * data.jutsu().masteryLevel(id);
                AwakeningData awakening = data.awakening();
                if (awakening.isLockedIn()) {
                    power *= 1.3f;
                }
                if (awakening.natureSurge() && isClanNature(data, def)) {
                    power *= 1.5f;
                }
                int cloak = data.biju().cloakStage();
                power *= cloak == 2 ? 1.6f : cloak == 1 ? 1.2f : 1f;
                if (id.equals(com.noirm.biju.BijuSystem.BEAST_BALL)) {
                    power *= beastBallScale(data.biju().tails());
                }
            }
        } else if (id.equals(com.noirm.biju.BijuSystem.BEAST_BALL) && caster instanceof com.noirm.entity.SparringNinja ninja) {
            power *= beastBallScale(ninja.tails());
        }
        return power;
    }

    /** Chakra cost (per second for CHANNEL jutsu). */
    /** Water jutsu with a water source nearby (a river, the sea): +40%. */
    public static final float WATER_SOURCE_BONUS = 1.4f;

    private static boolean usesWater(com.noirm.clan.Nature nature) {
        return nature == com.noirm.clan.Nature.WATER || nature == com.noirm.clan.Nature.ICE
                || nature == com.noirm.clan.Nature.STORM || nature == com.noirm.clan.Nature.BOIL || nature == com.noirm.clan.Nature.WOOD;
    }

    /** At least 8 water source blocks within 5 blocks. */
    public static boolean nearWater(LivingEntity entity) {
        int found = 0;
        net.minecraft.core.BlockPos center = entity.blockPosition();
        for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(center.offset(-5, -3, -5), center.offset(5, 2, 5))) {
            net.minecraft.world.level.material.FluidState fluid = entity.level().getFluidState(pos);
            if (fluid.is(net.minecraft.world.level.material.Fluids.WATER) && fluid.isSource() && ++found >= 8) {
                return true;
            }
        }
        return false;
    }

    /** Jutsu in {@code jutsu/taijutsu/} (the Gates, Lotus…): their power follows the taijutsu stat. */
    public static boolean isTaijutsu(ResourceLocation id) {
        return id.getPath().startsWith("taijutsu/");
    }

    /** +15% power per mastery level. */
    public static final float MASTERY_POWER = 0.15f;
    /** Extra chakra poured in while charging, as a fraction of the cost, spread over the full charge. */
    public static final float CHARGE_EXTRA_COST = 0.75f;

    /**
     * The Tailed Beast Bomb is the beast's own chakra, not yours: a jinchūriki's is multiplied by
     * the beast's strength (a beast casting it already has that strength).
     */
    public static float beastBallScale(int tails) {
        return tails <= 0 ? 1f : com.noirm.entity.TailedBeast.statFor(tails) / ShinobiData.DEFAULT_STAT;
    }

    /** The chakra behind a jutsu: proportional to reserves (1× at 10, 3× at 30, 10× at 100). */
    public static float chakraPower(float reserves) {
        return Math.max(0.6f, com.noirm.combat.Perception.statPower(reserves));
    }

    public static float cost(LivingEntity caster, ResourceLocation id, JutsuDefinition def) {
        float cost = def.chakraCost() * (1.5f - 0.5f * affinity(caster, id, def)) * com.noirm.training.Supplementary.costMultiplier(caster);
        if (caster instanceof Player player) {
            ShinobiData data = ShinobiCapability.get(player).orElse(null);
            if (data != null) {
                cost *= 1f - 0.08f * data.jutsu().masteryLevel(id);
                if (data.identity().has(ClanTrait.CHAKRA_FLOW)) {
                    cost *= 0.85f;
                }
                if (data.awakening().natureSurge() && isClanNature(data, def)) {
                    cost = 0f;
                }
            }
        }
        return Math.max(0f, cost);
    }

    public static int cooldown(LivingEntity caster, ResourceLocation id, JutsuDefinition def) {
        int mastery = caster instanceof Player player
                ? ShinobiCapability.get(player).map(d -> d.jutsu().masteryLevel(id)).orElse(0) : 0;
        return Math.round(def.cooldown() * (1f - 0.08f * mastery));
    }

    private static boolean isClanNature(ShinobiData data, JutsuDefinition def) {
        return def.nature().map(n -> data.identity().affinity(n) >= Clan.SECONDARY_AFFINITY).orElse(false);
    }
}
