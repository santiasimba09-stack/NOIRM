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
    public enum Result { OK, EMPTY_SLOT, UNKNOWN, COOLDOWN, NO_CHAKRA, NO_NATURE, NEEDS_DOJUTSU, NEEDS_CLOAK, BUSY, SEALED }

    /** A CHARGE or CHANNEL jutsu in progress. */
    public static final class Casting {
        final ResourceLocation id;
        final JutsuDefinition def;
        final int slot;
        /** Chakra paid when a CHARGE jutsu started, refunded if released too early. */
        final float paid;
        int ticks;

        Casting(ResourceLocation id, JutsuDefinition def, int slot, float paid) {
            this.id = id;
            this.def = def;
            this.slot = slot;
            this.paid = paid;
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

    private JutsuCaster() {}

    public static Casting casting(LivingEntity entity) {
        return CASTING.get(entity);
    }

    // ------------------------------------------------------------ players

    /** A jutsu key went down or up. */
    public static Result onKey(Player player, int slot, boolean pressed) {
        if (!pressed) {
            Casting casting = CASTING.get(player);
            if (casting != null && casting.slot == slot) {
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
        if (def.cast() == CastType.TAP) {
            execute(player, id, def, 1f);
        } else {
            Casting casting = new Casting(id, def, slot, player.getAbilities().instabuild ? 0f : cost);
            CASTING.put(player, casting);
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
            return;
        }
        if (CombatSystem.isStaggered(entity) || !entity.isAlive()) {
            stop(entity, casting, false);
            return;
        }
        casting.ticks++;
        if (casting.def.cast() == CastType.CHARGE) {
            if (casting.ticks <= casting.def.maxCharge() && casting.ticks % 2 == 0) {
                // Anticipation: energy gathers in front of the caster, tighter and brighter as it fills.
                float progress = Math.min(1f, casting.ticks / (float) Math.max(1, casting.def.maxCharge()));
                net.minecraft.world.phys.Vec3 hand = entity.getEyePosition().add(entity.getViewVector(1f).scale(0.8)).add(0, -0.35, 0);
                com.noirm.vfx.Vfx.Palette palette = com.noirm.vfx.Vfx.palette(casting.def.nature().orElse(null));
                com.noirm.vfx.Vfx.gather(entity.level(), hand, palette, 1.2f - 0.6f * progress, 3 + Math.round(progress * 4));
                com.noirm.vfx.Vfx.burst(entity.level(), com.noirm.vfx.Vfx.glow(palette.core(), palette.mid(), 0.2f + 0.5f * progress, 0.1f, 4), hand, 1, 0, 0);
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
            CASTING.remove(entity);
            execute(entity, casting.id, casting.def, charge);
        } else {
            stop(entity, casting, true);
        }
    }

    /** Ends a charge/channel. {@code completed} channels go on cooldown and earn mastery. */
    private static void stop(LivingEntity entity, Casting casting, boolean completed) {
        CASTING.remove(entity);
        if (completed) {
            afterCast(entity, casting.id, casting.def);
        }
        if (entity instanceof Player player) {
            ShinobiCapability.get(player).ifPresent(ShinobiData::markPowersDirty);
        }
    }

    // ------------------------------------------------------------ NPCs

    /** An NPC casts a jutsu directly (no chakra; its own cooldowns). Returns true if cast. */
    public static boolean npcCast(LivingEntity caster, ResourceLocation id, float charge) {
        JutsuDefinition def = JutsuData.get(id);
        if (def == null || !CombatSystem.canAct(caster) || checkRequirements(caster, id, def) != Result.OK) {
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
        execute(caster, id, def, charge);
        ready.put(id, now + cooldown(caster, id, def));
        return true;
    }

    // ------------------------------------------------------------ shared

    private static void execute(LivingEntity caster, ResourceLocation id, JutsuDefinition def, float charge) {
        runBehavior(caster, id, def, charge, true, 0);
        afterCast(caster, id, def);
    }

    private static void runBehavior(LivingEntity caster, ResourceLocation id, JutsuDefinition def, float charge, boolean start, int tick) {
        JutsuBehavior behavior = JutsuBehaviors.get(def.behavior());
        if (behavior == null) {
            Noirm.LOGGER.error("Jutsu {} uses unknown behavior '{}'", id, def.behavior());
            return;
        }
        JutsuContext context = new JutsuContext(caster, id, def, power(caster, id, def), charge);
        if (start) {
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
            if ("byakugan".equals(requirement.dojutsu()) && !Byakugan.isActive(caster)) {
                return Result.NEEDS_DOJUTSU;
            }
            if ("cloak".equals(requirement.dojutsu()) && !BijuSystem.hasBeastChakra(caster)) {
                return Result.NEEDS_CLOAK;
            }
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
    public static float power(LivingEntity caster, ResourceLocation id, JutsuDefinition def) {
        float power = 0.5f + 0.5f * affinity(caster, id, def);
        if (caster instanceof Player player) {
            ShinobiData data = ShinobiCapability.get(player).orElse(null);
            if (data != null) {
                power *= 1f + 0.1f * data.jutsu().masteryLevel(id);
                AwakeningData awakening = data.awakening();
                if (awakening.isLockedIn()) {
                    power *= 1.3f;
                }
                if (awakening.natureSurge() && isClanNature(data, def)) {
                    power *= 1.5f;
                }
                int cloak = data.biju().cloakStage();
                power *= cloak == 2 ? 1.6f : cloak == 1 ? 1.2f : 1f;
            }
        }
        return power;
    }

    /** Chakra cost (per second for CHANNEL jutsu). */
    public static float cost(LivingEntity caster, ResourceLocation id, JutsuDefinition def) {
        float cost = def.chakraCost() * (1.5f - 0.5f * affinity(caster, id, def));
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
