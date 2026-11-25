package com.noirm.aburame;

import com.noirm.clan.WorldClans;
import com.noirm.entity.SparringNinja;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A wild insect (docs/ABURAME.md, 2.6): a tiny, harmless ambient bug of one of the twelve kinds (or the
 * rare bikōchū). It flutters or crawls about and leaves after a while. Catch it with the Bug Net or
 * Insect Gathering (you need an Insect Case) and an Aburame can breed it into a strain.
 */
public class WildInsect extends Mob {
    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(WildInsect.class, EntityDataSerializers.BYTE);
    /** It leaves after 2 minutes. */
    public static final int LIFE = 20 * 120;
    /** Natural spawning: at most this many near a player, a try every 10 s with this chance. */
    public static final int MAX_NEAR = 4;
    public static final float SPAWN_CHANCE = 0.35f;
    public static final int SPAWN_RANGE = 20;
    /** The bikōchū: this chance per try where it lives, the day after rain, for an Entomologist. */
    public static final float BIKOCHU_CHANCE = 0.08f;
    public static final long AFTER_RAIN_TICKS = 24000;

    private int life;
    private Vec3 wander;

    public WildInsect(EntityType<? extends WildInsect> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1).add(Attributes.MOVEMENT_SPEED, 0.2);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(KIND, (byte) 0);
    }

    public WildKind kind() {
        return WildKind.of(entityData.get(KIND));
    }

    public void setKind(WildKind kind) {
        entityData.set(KIND, (byte) kind.ordinal());
    }

    /** Crawlers stay on the ground; the rest flutter. */
    public boolean flies() {
        WildKind k = kind();
        return k == WildKind.FIREFLY || k == WildKind.MOTH || k == WildKind.LOCUST || k == WildKind.JEWEL_BEETLE
                || k == WildKind.STAG_BEETLE || k == WildKind.BIKOCHU || k == WildKind.MANTIS;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) {
            return;
        }
        if (++life > LIFE) {
            discard();
            return;
        }
        setNoGravity(flies());
        if (wander == null || random.nextInt(40) == 0 || position().distanceToSqr(wander) < 0.2) {
            wander = position().add((random.nextDouble() - 0.5) * 4, flies() ? (random.nextDouble() - 0.4) * 2 : 0, (random.nextDouble() - 0.5) * 4);
            if (flies()) {
                int ground = level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(wander.x), Mth.floor(wander.z));
                wander = new Vec3(wander.x, Mth.clamp(wander.y, ground + 0.5, ground + 3.5), wander.z);
            }
        }
        Vec3 step = wander.subtract(position());
        double speed = flies() ? 0.06 : 0.03;
        Vec3 move = step.lengthSqr() > speed * speed ? step.normalize().scale(speed) : step;
        setDeltaMovement(flies() ? move : new Vec3(move.x, getDeltaMovement().y, move.z));
        if (move.lengthSqr() > 1e-5) {
            setYRot((float) (Mth.atan2(move.z, move.x) * 180 / Math.PI) - 90f);
            yBodyRot = getYRot();
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float mul, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Kind", entityData.get(KIND));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(KIND, tag.getByte("Kind"));
    }

    // ================================================================ catching

    /** Catches {@code bug} into {@code player}'s Insect Case. Returns whether it was caught. */
    public static boolean catchInto(Player player, WildInsect bug) {
        if (!InsectCaseItem.carried(player)) {
            player.displayClientMessage(Component.translatable("message.noirm.colony.need_case"), true);
            return false;
        }
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (data == null) {
            return false;
        }
        WildKind kind = bug.kind();
        bug.discard();
        if (kind == WildKind.BIKOCHU) {
            ItemStack egg = new ItemStack(AburameRegistry.BIKOCHU_EGG.get());
            if (!player.getInventory().add(egg)) {
                player.drop(egg, false);
            }
            data.colony().setBikochuQuest(2);
            player.sendSystemMessage(Component.translatable("message.noirm.colony.bikochu_found").withStyle(ChatFormatting.GOLD));
            if (player.level() instanceof ServerLevel level) {
                kamizuruAmbush(level, player); // the Kamizuru were hunting the same egg
            }
            return true;
        }
        boolean fresh = data.colony().caught().add(kind.key());
        player.displayClientMessage(Component.translatable(fresh ? "message.noirm.colony.caught_new" : "message.noirm.colony.caught_again",
                Component.translatable(kind.translationKey()), data.colony().caught().size(), WildKind.collection().length), true);
        if (fresh && data.colony().caught().size() >= WildKind.collection().length && data.colony().bikochuQuest() == 0) {
            data.colony().setBikochuQuest(1);
            player.sendSystemMessage(Component.translatable("message.noirm.colony.entomologist").withStyle(ChatFormatting.GOLD));
        }
        data.markPowersDirty();
        return true;
    }

    /** Two Kamizuru appear, after the egg. */
    static void kamizuruAmbush(ServerLevel level, Player player) {
        WorldClans.get(level.getServer()).find("kamizuru").ifPresent(clan -> {
            for (int i = 0; i < 2; i++) {
                SparringNinja ninja = NoirmEntities.SPARRING_NINJA.get().create(level);
                if (ninja == null) {
                    return;
                }
                double a = level.random.nextDouble() * Math.PI * 2;
                BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        BlockPos.containing(player.getX() + Math.cos(a) * 10, player.getY(), player.getZ() + Math.sin(a) * 10));
                ninja.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
                ninja.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
                ninja.joinClan(clan);
                level.addFreshEntity(ninja);
                ninja.setTarget(player);
            }
        });
    }

    // ================================================================ spawning

    /** Last game time it rained, per dimension (for the bikōchū). */
    static final java.util.Map<String, Long> LAST_RAIN = new java.util.HashMap<>();

    /** A natural spawn try near {@code player} (every 10 s). */
    public static void trySpawn(ServerLevel level, Player player) {
        String dim = level.dimension().location().toString();
        if (level.isRaining()) {
            LAST_RAIN.put(dim, level.getGameTime());
        }
        if (level.random.nextFloat() > SPAWN_CHANCE) {
            return;
        }
        if (level.getEntitiesOfClass(WildInsect.class, player.getBoundingBox().inflate(48)).size() >= MAX_NEAR) {
            return;
        }
        double a = level.random.nextDouble() * Math.PI * 2;
        double d = 6 + level.random.nextDouble() * (SPAWN_RANGE - 6);
        int x = Mth.floor(player.getX() + Math.cos(a) * d);
        int z = Mth.floor(player.getZ() + Math.sin(a) * d);
        BlockPos at;
        if (player.getY() < 40 && !level.canSeeSky(player.blockPosition())) {
            at = BlockPos.containing(x, player.getY(), z); // underground: near the player's own depth
            if (!level.getBlockState(at).isAir()) {
                return;
            }
        } else {
            at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        }
        List<WildKind> here = new ArrayList<>();
        for (WildKind k : WildKind.collection()) {
            if (k.livesAt(level, at)) {
                here.add(k);
            }
        }
        ColonyData colony = ShinobiCapability.get(player).map(ShinobiData::colony).orElse(null);
        Long rained = LAST_RAIN.get(dim);
        boolean afterRain = rained != null && level.getGameTime() - rained < AFTER_RAIN_TICKS;
        if (colony != null && colony.bikochuQuest() == 1 && afterRain && WildKind.BIKOCHU.livesAt(level, at)
                && level.random.nextFloat() < BIKOCHU_CHANCE) {
            here.clear();
            here.add(WildKind.BIKOCHU);
        }
        if (here.isEmpty()) {
            return;
        }
        spawn(level, Vec3.atBottomCenterOf(at).add(0, 0.5, 0), here.get(level.random.nextInt(here.size())));
    }

    public static WildInsect spawn(ServerLevel level, Vec3 at, WildKind kind) {
        WildInsect bug = AburameRegistry.WILD_INSECT.get().create(level);
        if (bug == null) {
            return null;
        }
        bug.setKind(kind);
        bug.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360f, 0f);
        level.addFreshEntity(bug);
        return bug;
    }
}
