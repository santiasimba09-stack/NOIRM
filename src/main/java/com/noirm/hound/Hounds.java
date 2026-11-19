package com.noirm.hound;

import com.noirm.Noirm;
import com.noirm.combat.Perception;
import com.noirm.entity.SparringNinja;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.traits.Perk;
import com.noirm.traits.Perks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Everything around the ninja hound that isn't the animal itself: who gets one, how it grows
 * (XP, bond, your own progress), feeding, petting, carrying and riding, your orders, and keeping it
 * with you across dimensions and logins ({@link HoundData}). Tuning numbers are at the top; the
 * stage table is {@link HoundStage}.
 */
public final class Hounds {
    // ---------------------------------------------------------------- tuning
    /** Hound XP per point of damage its bites deal, per kill (plus a share of the victim's health). */
    public static final float XP_PER_DAMAGE = 0.6f;
    public static final float XP_PER_KILL = 6f;
    public static final float XP_PER_KILL_HEALTH = 0.25f;
    /** It learns beside you: this share of the training XP you earn within {@link #TOGETHER_RANGE}. */
    public static final float TRAINING_SHARE = 0.3f;
    public static final double TOGETHER_RANGE = 16.0;
    /** Bond: per minute together, per kill together, per meal, per treat (a bone), per soldier pill. */
    public static final float BOND_PER_MINUTE = 0.4f;
    public static final float BOND_PER_KILL = 0.6f;
    public static final float BOND_PER_MEAL = 1.0f;
    public static final float BOND_PER_TREAT = 2.0f;
    public static final float BOND_PER_PILL = 1.0f;
    /** A hound pill's XP (the Inuzuka feed their partners ration pills in training). */
    public static final float XP_PER_HOUND_PILL = 12f;
    /** Lost for good (the void, /kill): it comes back after this rest (ticks). */
    public static final int LOST_RECOVERY_TICKS = 20 * 90;
    /** Farther than this from its owner (or in another dimension) and following: it is brought back. */
    public static final double RECALL_DISTANCE = 64.0;
    /** A puppy from an Inuzuka friend: your rank, their respect. */
    public static final float GIFT_RESPECT = 80f;
    /** Summoning a contract hound costs this much chakra. */
    public static final float SUMMON_CHAKRA = 15f;

    /** The milestone from which a hound can be left to guard a spot on its own. */
    public static final HoundStage GUARD_STAGE = HoundStage.GROWN;
    /** A Gluttonous hound begs when you hold food this close (blocks). */
    public static final double BEG_RANGE = 6.0;

    /** Names for new hounds (our own list). */
    private static final String[] NAMES = {"Kaze", "Shiro", "Kuro", "Gin", "Tetsu", "Hachi", "Taro", "Sora", "Ren", "Daichi",
            "Arashi", "Hibiki", "Kumo", "Raiden", "Tobi", "Iwa"};

    /** Hound techniques (data/noirm/jutsu/hound/), learned as the hound grows. */
    public static final ResourceLocation DYNAMIC_MARKING = Noirm.id("hound/dynamic_marking");
    public static final ResourceLocation MAN_BEAST_CLONE = Noirm.id("hound/man_beast_clone");
    public static final ResourceLocation TWO_HEADED_WOLF = Noirm.id("hound/two_headed_wolf");
    public static final ResourceLocation FANG_OVER_FANG = Noirm.id("clan/fang_over_fang");

    private Hounds() {}

    // ================================================================ who has one

    /** How the clan's blood gives a hound: 0 none, 1 born with a puppy (Inuzuka), 2 a summoning contract (Hatake). */
    public static int bloodHound(ShinobiData data) {
        float v = Perks.get(data, Perk.HOUND);
        return v >= 1.5f ? 2 : v > 0f ? 1 : 0;
    }

    /** Inuzuka blood: the clan's beast techniques (Man Beast Clone, the Two-Headed Wolf) answer to you. */
    public static boolean inuzukaBlood(LivingEntity owner) {
        if (owner instanceof Player player) {
            return ShinobiCapability.get(player).map(d -> bloodHound(d) == 1).orElse(false);
        }
        if (owner instanceof SparringNinja ninja) {
            float v = Perks.totals(ninja).getOrDefault(Perk.HOUND, 0f);
            return v > 0f && v < 1.5f; // a Hatake's contract hound doesn't know the Inuzuka's beast arts
        }
        return false;
    }

    public static HoundData data(Player player) {
        return ShinobiCapability.get(player).map(ShinobiData::hound).orElse(null);
    }

    /**
     * A new puppy for {@code player} (Inuzuka at the start, a Hatake contract, a gift, a command).
     * Returns the hound, or null if it couldn't be placed.
     */
    public static NinjaHound grantPuppy(Player player, HoundLook look, boolean contract) {
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (data == null || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        HoundData hd = data.hound();
        NinjaHound old = find(player);
        if (old != null) {
            old.discard();
        }
        hd.clear();
        hd.setHas(true);
        hd.setLook(look != null ? look : HoundLook.roll(player.getRandom()));
        hd.setTraits(HoundTraits.roll(player.getRandom()));
        hd.setName(NAMES[player.getRandom().nextInt(NAMES.length)]);
        hd.setLevel(1);
        hd.setContract(contract);
        hd.setOut(true);
        NinjaHound hound = spawnFromData(level, player, hd);
        data.markPowersDirty();
        if (hound != null) {
            level.sendParticles(ParticleTypes.CLOUD, hound.getX(), hound.getY() + 0.3, hound.getZ(), 20, 0.4, 0.3, 0.4, 0.02);
            player.sendSystemMessage(Component.translatable(contract ? "message.noirm.hound.summoned_first" : "message.noirm.hound.puppy",
                    hound.getDisplayName()).withStyle(ChatFormatting.GOLD));
            learnStageJutsu(player, 1);
        }
        return hound;
    }

    /** Puts the hound from {@code hd} into the world next to {@code owner}. */
    public static NinjaHound spawnFromData(ServerLevel level, Player owner, HoundData hd) {
        NinjaHound hound = HoundRegistry.NINJA_HOUND.get().create(level);
        if (hound == null) {
            return null;
        }
        Vec3 at = besideOwner(owner);
        hound.moveTo(at.x, at.y, at.z, owner.getYRot(), 0f);
        hound.bondTo(owner);
        if (hd.traits().isEmpty()) {
            hd.setTraits(HoundTraits.roll(owner.getRandom())); // a hound from before traits finds its nature
        }
        hound.setTraits(hd.traits());
        hound.setLook(hd.look());
        hound.setHoundLevel(hd.level());
        hound.setBond(hd.bond());
        hound.setXp(hd.xp());
        hound.setOrder(hd.order() == HoundOrder.GUARD ? HoundOrder.FOLLOW : hd.order());
        if (!hd.name().isEmpty()) {
            hound.setCustomName(Component.literal(hd.name()));
        }
        hound.applyStats();
        hound.setHealth(Math.max(1f, hound.getMaxHealth() * Math.max(0.2f, hd.healthShare())));
        level.addFreshEntity(hound);
        hd.setEntityId(hound.getUUID());
        hd.setOut(true);
        return hound;
    }

    private static Vec3 besideOwner(Player owner) {
        Vec3 side = Vec3.directionFromRotation(0, owner.getYRot() + 90).scale(1.2);
        Vec3 at = owner.position().add(side);
        BlockPos pos = BlockPos.containing(at);
        if (!owner.level().getBlockState(pos).isAir() || !owner.level().getBlockState(pos.above()).isAir()) {
            return owner.position();
        }
        return at;
    }

    /** Your live hound, if it is loaded anywhere on the server (or null). */
    public static NinjaHound find(Player player) {
        HoundData hd = data(player);
        if (hd == null || !hd.has() || hd.entityId() == null || player.getServer() == null) {
            return null;
        }
        Entity here = player.level() instanceof ServerLevel level ? level.getEntity(hd.entityId()) : null;
        if (here instanceof NinjaHound hound && hound.isAlive()) {
            return hound;
        }
        for (ServerLevel level : player.getServer().getAllLevels()) {
            if (level.getEntity(hd.entityId()) instanceof NinjaHound hound && hound.isAlive()) {
                return hound;
            }
        }
        return null;
    }

    /** The hound that belongs to any owner (players by their record; NPC ninjas by a search nearby). */
    public static NinjaHound houndOf(LivingEntity owner) {
        if (owner instanceof Player player) {
            return find(player);
        }
        if (owner.level() instanceof ServerLevel level) {
            return level.getEntitiesOfClass(NinjaHound.class, owner.getBoundingBox().inflate(32),
                    h -> owner.getUUID().equals(h.getOwnerUUID()) && h.isAlive()).stream().findFirst().orElse(null);
        }
        return null;
    }

    // ================================================================ every second, from the hound

    /** Bookkeeping once a second: owner checks, time together, smell, growth, the record. */
    static void tickHound(NinjaHound hound) {
        LivingEntity owner = hound.getOwner();
        UUID ownerId = hound.getOwnerUUID();
        if (ownerId == null) {
            return;
        }
        if (owner instanceof Player player) {
            HoundData hd = data(player);
            if (hd == null || !hd.has() || hd.entityId() != null && !hd.entityId().equals(hound.getUUID())) {
                hound.discard(); // an older copy (it was brought back elsewhere), or given up
                return;
            }
        } else if (owner == null) {
            Player online = hound.getServer() == null ? null : hound.getServer().getPlayerList().getPlayer(ownerId);
            if (online != null) {
                // Its player is in another dimension: they'll bring a fresh copy along (tickOwner).
                HoundData hd = data(online);
                if (hd != null && hd.entityId() != null && !hd.entityId().equals(hound.getUUID())) {
                    hound.discard();
                }
                return;
            }
            if (hound.getServer() != null && hound.level() instanceof ServerLevel level
                    && !isPlayerUuid(level, ownerId) && hound.bumpOwnerMissing() > 30) {
                // An NPC's hound whose ninja is gone: it goes home.
                level.sendParticles(ParticleTypes.POOF, hound.getX(), hound.getY() + 0.4, hound.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
                hound.discard();
            }
            return;
        }
        hound.resetOwnerMissing();
        if (hound.isDown()) {
            return;
        }
        // Time together.
        if (hound.tickCount % 1200 < 20 && hound.distanceToSqr(owner) < TOGETHER_RANGE * TOGETHER_RANGE) {
            addBond(hound, BOND_PER_MINUTE);
        }
        HoundSmell.sniff(hound, owner);
        beg(hound, owner);
        checkGrowth(hound, owner);
        if (hound.tickCount % 100 == 0) {
            snapshot(hound);
        }
    }

    /** Gluttonous: it fixes its eyes on any food in your hand and whines for it. */
    private static void beg(NinjaHound hound, LivingEntity owner) {
        if (!(owner instanceof Player player) || hound.getTarget() != null || hound.isCarried()
                || !HoundTraits.any(hound.traits(), HoundTraits.Trait::begs)) {
            return;
        }
        boolean food = player.getMainHandItem().isEdible() || player.getOffhandItem().isEdible();
        if (food && hound.distanceToSqr(player) < BEG_RANGE * BEG_RANGE) {
            hound.getLookControl().setLookAt(player, 30f, 30f);
            if (hound.tickCount % 100 < 20) {
                hound.playSound(SoundEvents.WOLF_WHINE, 0.8f, hound.getVoicePitch());
            }
        }
    }

    private static boolean isPlayerUuid(ServerLevel level, UUID id) {
        return level.getServer().getProfileCache() != null && level.getServer().getProfileCache().get(id).isPresent();
    }

    /** Copies the hound into its owner's record (and tells their client, now and then). */
    public static void snapshot(NinjaHound hound) {
        if (!(hound.getOwner() instanceof Player player)) {
            return;
        }
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (data == null || !data.hound().has() || !hound.getUUID().equals(data.hound().entityId())) {
            return;
        }
        HoundData hd = data.hound();
        boolean visible = hd.level() != hound.houndLevel() || Math.round(hd.bond()) != Math.round(hound.bond())
                || (int) hd.xp() / 5 != (int) hound.xp() / 5 || hd.order() != hound.order();
        hd.setLevel(hound.houndLevel());
        hd.setTraits(hound.traits());
        hd.setBond(hound.bond());
        hd.setXp(hound.xp());
        hd.setOrder(hound.order());
        hd.setLook(hound.look());
        hd.setHealthShare(hound.getMaxHealth() > 0 ? hound.getHealth() / hound.getMaxHealth() : 1f);
        if (hound.hasCustomName()) {
            hd.setName(hound.getCustomName().getString());
        }
        if (visible) {
            data.markPowersDirty();
        }
    }

    // ================================================================ every two seconds, from the owner

    /** Keeps your hound with you: brought back if it was lost, left behind in another dimension or far away. */
    public static void tickOwner(ServerPlayer player, ShinobiData data) {
        HoundData hd = data.hound();
        if (!hd.has() || !hd.isOut() || player.isSpectator() || !player.isAlive()) {
            return;
        }
        long now = player.level().getGameTime();
        NinjaHound hound = find(player);
        if (hound != null) {
            boolean elsewhere = hound.level() != player.level();
            boolean far = !elsewhere && hound.distanceToSqr(player) > RECALL_DISTANCE * RECALL_DISTANCE;
            if ((elsewhere || far) && hound.order() == HoundOrder.FOLLOW && !hound.isPassenger()) {
                snapshot(hound);
                hound.discard();
                spawnFromData((ServerLevel) player.level(), player, hd);
            }
            return;
        }
        if (hd.downUntil() > now) {
            return; // resting after it was lost
        }
        if (hd.entityId() != null && hd.order() != HoundOrder.FOLLOW) {
            return; // sitting or guarding somewhere that isn't loaded: it waits there
        }
        NinjaHound back = spawnFromData((ServerLevel) player.level(), player, hd);
        if (back != null) {
            data.markPowersDirty();
        }
    }

    /** The hound's body was destroyed (the void, /kill): it rests, then comes back. */
    public static void lost(NinjaHound hound) {
        if (hound.getOwner() instanceof Player player) {
            HoundData hd = data(player);
            if (hd != null && hd.has() && hound.getUUID().equals(hd.entityId())) {
                hd.setDownUntil(player.level().getGameTime() + LOST_RECOVERY_TICKS);
                hd.setEntityId(null);
                hd.setHealthShare(0.3f);
                player.sendSystemMessage(Component.translatable("message.noirm.hound.lost", hound.getDisplayName(), LOST_RECOVERY_TICKS / 20)
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    // ================================================================ growth

    /** Hound XP (its traits may speed it up). */
    public static void addXp(NinjaHound hound, float amount) {
        if (amount <= 0f) {
            return;
        }
        hound.setXp(hound.xp() + amount * HoundTraits.mult(hound.traits(), HoundTraits.Trait::xp));
    }

    /** Bond (a Loyal hound bonds faster; losses aren't softened). */
    public static void addBond(NinjaHound hound, float amount) {
        float m = amount > 0f ? HoundTraits.mult(hound.traits(), HoundTraits.Trait::bond) : 1f;
        hound.setBond(hound.bond() + amount * m);
    }

    /** What the next level still needs: null if nothing. */
    public static Component blocker(NinjaHound hound, LivingEntity owner) {
        return blocker(hound.houndLevel() + 1, hound.xp(), hound.bond(), owner == null ? 0f : Perception.basePower(owner));
    }

    public static Component blocker(int nextLevel, float xp, float bond, float ownerPower) {
        if (xp < HoundGrowth.xpFor(nextLevel)) {
            return Component.translatable("screen.noirm.hound.needs_xp", Math.round(HoundGrowth.xpFor(nextLevel) - xp));
        }
        if (bond < HoundGrowth.bondFor(nextLevel)) {
            return Component.translatable("screen.noirm.hound.needs_bond", Math.round(HoundGrowth.bondFor(nextLevel)));
        }
        if (ownerPower < HoundGrowth.powerFor(nextLevel)) {
            return Component.translatable("screen.noirm.hound.needs_power", Math.round(HoundGrowth.powerFor(nextLevel)));
        }
        return null;
    }

    /** Levels up as far as XP, bond and the owner's power allow. Returns whether it grew. */
    public static boolean checkGrowth(NinjaHound hound, LivingEntity owner) {
        if (hound.isFused() || hound.isHidden()) {
            return false;
        }
        boolean grew = false;
        for (int guard = 0; guard < 200 && blocker(hound, owner) == null; guard++) {
            grow(hound, hound.houndLevel() + 1);
            grew = true;
        }
        return grew;
    }

    /** Grows (or is set) to {@code level}, with smoke, a howl at each milestone, and the news. */
    public static void grow(NinjaHound hound, int level) {
        HoundStage before = hound.stage();
        int was = hound.houndLevel();
        hound.setHoundLevel(level);
        if (hound.isCarried() && !hound.canBeCarried()) {
            putDown(hound); // too big for your head now
        }
        hound.setHealth(hound.getMaxHealth());
        HoundStage now = hound.stage();
        boolean milestone = now != before && level > was;
        hound.level().broadcastEntityEvent(hound, NinjaHound.EVENT_GROW);
        if (milestone) {
            hound.howl();
        }
        if (hound.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CLOUD, hound.getX(), hound.getY() + hound.getBbHeight() * 0.5, hound.getZ(),
                    milestone ? 30 : 8, hound.getBbWidth() * 0.6, hound.getBbHeight() * 0.4, hound.getBbWidth() * 0.6, 0.03);
        }
        LivingEntity owner = hound.getOwner();
        if (owner instanceof Player player) {
            if (milestone) {
                player.sendSystemMessage(Component.translatable("message.noirm.hound.milestone", hound.getDisplayName(), level,
                        Component.translatable("screen.noirm.hound.stage." + now.key())).withStyle(ChatFormatting.GOLD));
            } else if (level > was) {
                player.displayClientMessage(Component.translatable("message.noirm.hound.level", hound.getDisplayName(), level)
                        .withStyle(ChatFormatting.GOLD), true);
            }
            learnStageJutsu(player, level);
            snapshot(hound);
            ShinobiCapability.get(player).ifPresent(ShinobiData::markPowersDirty);
        }
    }

    /** Grows (or shrinks) to the start of a milestone. */
    public static void grow(NinjaHound hound, HoundStage stage) {
        grow(hound, stage.minLevel);
    }

    /** The hound techniques its level has opened (Inuzuka blood for the clan's own). */
    public static void learnStageJutsu(Player player, int level) {
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (data == null) {
            return;
        }
        for (HoundStage s : HoundStage.values()) {
            if (s.minLevel > level) {
                break;
            }
            for (ResourceLocation id : jutsuFor(s, inuzukaBlood(player))) {
                if (data.jutsu().learn(id, false)) {
                    data.jutsu().equipIfRoom(id);
                    player.sendSystemMessage(Component.translatable("message.noirm.hound.technique",
                            Component.translatable("jutsu.noirm." + id.getPath().replace('/', '.'))).withStyle(ChatFormatting.AQUA));
                }
            }
        }
        data.markPowersDirty();
    }

    public static List<ResourceLocation> jutsuFor(HoundStage stage, boolean inuzuka) {
        return switch (stage) {
            case PUPPY -> List.of(DYNAMIC_MARKING);
            case YOUNG -> inuzuka ? List.of(MAN_BEAST_CLONE) : List.of();
            case GREAT -> inuzuka ? List.of(TWO_HEADED_WOLF) : List.of();
            default -> List.of();
        };
    }

    // ================================================================ things you do with it

    /** Food, treats and pills from your hand. Returns whether it took it. */
    public static boolean feed(NinjaHound hound, Player player, ItemStack stack) {
        boolean took = false;
        boolean pill = stack.getItem() instanceof com.noirm.pills.PillItem || stack.is(HoundRegistry.HOUND_PILL.get());
        boolean medical = stack.is(com.noirm.medical.MedicalItems.ANTIDOTE.get());
        if (hound.isDown() && (stack.isEdible() || pill || medical || stack.is(Items.BONE))) {
            hound.wake(NinjaHound.REVIVE_HEALTH);
            player.displayClientMessage(Component.translatable("message.noirm.hound.revived", hound.getDisplayName()), true);
            took = true;
        } else if (stack.is(HoundRegistry.HOUND_PILL.get())) {
            hound.frenzy();
            addBond(hound, BOND_PER_PILL);
            addXp(hound, XP_PER_HOUND_PILL);
            player.displayClientMessage(Component.translatable("message.noirm.hound.pill", hound.getDisplayName()), true);
            took = true;
        } else if (stack.getItem() instanceof com.noirm.pills.PillItem p && "soldier_pill".equals(p.pillId().getPath())) {
            hound.frenzy();
            addBond(hound, BOND_PER_PILL);
            player.displayClientMessage(Component.translatable("message.noirm.hound.pill", hound.getDisplayName()), true);
            took = true;
        } else if (stack.is(Items.BONE)) {
            addBond(hound, BOND_PER_TREAT);
            hound.pet(player);
            took = true;
        } else if (stack.isEdible() && stack.getFoodProperties(hound) != null) {
            var food = stack.getFoodProperties(hound);
            boolean meat = food.isMeat();
            if (hound.getHealth() >= hound.getMaxHealth() && !meat) {
                return false;
            }
            hound.heal((meat ? 2f : 1f) * food.getNutrition() * HoundTraits.mult(hound.traits(), HoundTraits.Trait::food));
            addBond(hound, meat ? BOND_PER_MEAL : BOND_PER_MEAL * 0.5f);
            took = true;
        }
        if (took) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            hound.playSound(SoundEvents.GENERIC_EAT, 0.8f, hound.getVoicePitch());
            if (hound.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, hound.getX(), hound.getY() + hound.getBbHeight(), hound.getZ(), 5, 0.3, 0.2, 0.3, 0);
            }
            snapshot(hound);
        }
        return took;
    }

    public static void pet(NinjaHound hound, Player player) {
        if (hound.pet(player)) {
            addBond(hound, NinjaHound.PET_BOND);
            snapshot(hound);
        }
    }

    public static void cycleOrder(NinjaHound hound, Player player) {
        HoundOrder next = HoundOrder.values()[(hound.order().ordinal() + 1) % HoundOrder.values().length];
        order(hound, player, next);
    }

    public static void order(NinjaHound hound, Player player, HoundOrder order) {
        if (ignores(hound, player)) {
            return;
        }
        if (order == HoundOrder.GUARD && !HoundGrowth.canGuard(hound.houndLevel())) {
            // A puppy or a youngster can't hold a post alone: it sits and waits instead.
            player.displayClientMessage(Component.translatable("message.noirm.hound.too_young_guard", hound.getDisplayName(),
                    GUARD_STAGE.minLevel), true);
            order = HoundOrder.SIT;
        }
        if (hound.isCarried()) {
            putDown(hound);
        }
        hound.setOrder(order);
        hound.setTarget(null);
        player.displayClientMessage(Component.translatable("message.noirm.hound.order", hound.getDisplayName(),
                Component.translatable(order.key())), true);
        snapshot(hound);
    }

    /** Stubborn: now and then it just doesn't listen (tells you so). */
    public static boolean ignores(NinjaHound hound, Player player) {
        float chance = HoundTraits.max(hound.traits(), HoundTraits.Trait::disobey);
        if (chance > 0f && hound.getRandom().nextFloat() < chance) {
            player.displayClientMessage(Component.translatable("message.noirm.hound.ignores", hound.getDisplayName())
                    .withStyle(ChatFormatting.GRAY), true);
            hound.playSound(SoundEvents.WOLF_AMBIENT, 0.8f, hound.getVoicePitch());
            return true;
        }
        return false;
    }

    /** A puppy hops onto your head (or into your jacket). */
    public static boolean carry(NinjaHound hound, Player player, NinjaHound.Carry mode) {
        if (!hound.canBeCarried() || hound.isDown() || player.isPassenger() && player.getVehicle() == hound) {
            return false;
        }
        if (hound.isCarried()) {
            if (hound.carry() == mode) {
                putDown(hound);
                return true;
            }
            hound.setCarry(mode);
            return true;
        }
        hound.setCarry(mode);
        hound.setOrderedToSit(false);
        hound.setInSittingPose(false);
        hound.getNavigation().stop();
        if (!hound.startRiding(player, true)) {
            hound.setCarry(NinjaHound.Carry.NONE);
            return false;
        }
        if (player instanceof ServerPlayer sp) {
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(player));
        }
        hound.playSound(SoundEvents.WOLF_PANT, 0.8f, hound.getVoicePitch());
        return true;
    }

    public static void putDown(NinjaHound hound) {
        Entity vehicle = hound.getVehicle();
        hound.setCarry(NinjaHound.Carry.NONE);
        hound.stopRiding();
        if (vehicle instanceof Player player) {
            Vec3 at = besideOwner(player);
            hound.teleportTo(at.x, at.y, at.z);
            if (player instanceof ServerPlayer sp) {
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(player));
            }
        }
    }

    /** Climb onto a great hound's back. */
    public static boolean ride(NinjaHound hound, Player player) {
        if (!(hound.canBeRidden() || hound.isFused()) || hound.isDown() || player.isPassenger()) {
            return false;
        }
        hound.setOrder(HoundOrder.FOLLOW);
        hound.setTarget(null);
        return player.startRiding(hound);
    }

    /**
     * Your order from the hound panel, the crew order screen or a command: {@code follow}, {@code sit},
     * {@code guard}, {@code come}, {@code attack} (what you look at), {@code track}, {@code carry},
     * {@code jacket}, {@code ride}, {@code pet}, {@code howl}, {@code recall} (contract: summon/dismiss).
     * Returns whether it was carried out.
     */
    public static boolean command(ServerPlayer player, String action) {
        HoundData hd = data(player);
        if (hd == null || !hd.has()) {
            player.displayClientMessage(Component.translatable("message.noirm.hound.none").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if ("recall".equals(action)) {
            return recall(player);
        }
        NinjaHound hound = find(player);
        if (hound == null) {
            player.displayClientMessage(Component.translatable("message.noirm.hound.away").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        HoundOrder order = HoundOrder.byKey(action);
        if (order != null) {
            order(hound, player, order);
            return true;
        }
        switch (action) {
            case "come" -> {
                if (hound.isCarried()) {
                    return true;
                }
                hound.setOrder(HoundOrder.FOLLOW);
                hound.setTarget(null);
                if (hound.distanceToSqr(player) > 12 * 12 || hound.level() != player.level()) {
                    Vec3 at = besideOwner(player);
                    hound.teleportTo(at.x, at.y, at.z);
                } else {
                    hound.getNavigation().moveTo(player, 1.3);
                }
                snapshot(hound);
                return true;
            }
            case "attack" -> {
                if (ignores(hound, player)) {
                    return false;
                }
                LivingEntity target = com.noirm.ally.CrewPackets.lookTarget(player);
                if (target == null || hound.isDown()) {
                    player.displayClientMessage(Component.translatable("command.noirm.crew.no_enemy").withStyle(ChatFormatting.GRAY), true);
                    return false;
                }
                if (hound.isCarried()) {
                    putDown(hound);
                }
                hound.setOrder(HoundOrder.FOLLOW);
                hound.setTarget(target);
                hound.playSound(SoundEvents.WOLF_GROWL, 1f, hound.getVoicePitch());
                return true;
            }
            case "track" -> {
                LivingEntity scent = HoundSmell.findScent(hound, player);
                if (scent == null) {
                    player.displayClientMessage(Component.translatable("message.noirm.hound.no_scent", hound.getDisplayName()), true);
                    return false;
                }
                if (hound.isCarried()) {
                    putDown(hound);
                }
                hound.track(scent);
                player.displayClientMessage(Component.translatable("message.noirm.hound.tracking", hound.getDisplayName(),
                        scent.getDisplayName(), Math.round(hound.distanceTo(scent))), true);
                return true;
            }
            case "carry" -> {
                return carry(hound, player, NinjaHound.Carry.HEAD);
            }
            case "jacket" -> {
                return carry(hound, player, NinjaHound.Carry.JACKET);
            }
            case "ride" -> {
                return ride(hound, player);
            }
            case "pet" -> {
                if (hound.distanceToSqr(player) > 6 * 6) {
                    return false;
                }
                pet(hound, player);
                return true;
            }
            case "howl" -> {
                hound.howl();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** A hound technique's requirement: your hound is here, awake and past milestone {@code stage} (1 = puppy, 2 = young…). */
    public static boolean houndReady(LivingEntity caster, int stage) {
        NinjaHound hound = houndOf(caster);
        return hound != null && !hound.isDown() && hound.level() == caster.level() && hound.distanceTo(caster) <= 24
                && hound.houndLevel() >= HoundStage.of(stage - 1).minLevel;
    }

    /** The crew order screen (K) speaks to your hound too. Returns whether it took the order. */
    public static boolean crewOrder(ServerPlayer player, com.noirm.ally.CrewPackets.Action action, String arg) {
        HoundData hd = data(player);
        if (hd == null || !hd.has() || find(player) == null) {
            return false;
        }
        String mine = switch (action) {
            case ORDER -> switch (arg) {
                case "FOLLOW" -> "follow";
                case "WAIT" -> "sit";
                case "GUARD", "PATROL" -> "guard";
                default -> null;
            };
            case COME, FALL_BACK -> "come";
            case ATTACK -> "attack";
            case BEHAVIOUR -> null;
        };
        return mine != null && command(player, mine);
    }

    /** A contract hound: dismiss it (it goes home in smoke) or summon it to your side for some chakra. */
    public static boolean recall(ServerPlayer player) {
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (data == null) {
            return false;
        }
        HoundData hd = data.hound();
        NinjaHound hound = find(player);
        if (hd.isOut() && hound != null) {
            snapshot(hound);
            if (hound.isCarried()) {
                putDown(hound);
            }
            ((ServerLevel) hound.level()).sendParticles(ParticleTypes.POOF, hound.getX(), hound.getY() + 0.5, hound.getZ(), 25, 0.4, 0.4, 0.4, 0.03);
            hound.playSound(SoundEvents.CHICKEN_EGG, 1f, 0.6f);
            hound.discard();
            hd.setOut(false);
            hd.setEntityId(null);
            player.displayClientMessage(Component.translatable("message.noirm.hound.dismissed", hd.name()), true);
            data.markPowersDirty();
            return true;
        }
        if (!player.getAbilities().instabuild) {
            if (data.getChakra() < SUMMON_CHAKRA) {
                player.displayClientMessage(Component.translatable("message.noirm.jutsu.no_chakra").withStyle(ChatFormatting.RED), true);
                return false;
            }
            data.setChakra(data.getChakra() - SUMMON_CHAKRA);
        }
        if (hound != null) {
            hound.discard();
        }
        hd.setOut(true);
        if (hd.downUntil() > player.level().getGameTime()) {
            hd.setDownUntil(0);
        }
        NinjaHound fresh = spawnFromData((ServerLevel) player.level(), player, hd);
        if (fresh != null) {
            ((ServerLevel) player.level()).sendParticles(ParticleTypes.POOF, fresh.getX(), fresh.getY() + 0.5, fresh.getZ(), 25, 0.4, 0.4, 0.4, 0.03);
            player.level().playSound(null, fresh.blockPosition(), SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1f, 0.6f);
        }
        data.markPowersDirty();
        return fresh != null;
    }
}
