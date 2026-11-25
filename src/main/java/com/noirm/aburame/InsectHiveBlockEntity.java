package com.noirm.aburame;

import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** A hive's insects: count, owner, honey; breeding, feeding, fire and explosion losses (2.6). */
public class InsectHiveBlockEntity extends BlockEntity {
    /** Breeds +1% a minute; ×1.5 with 6+ flowers within 5 blocks, ×1.25 with honey. */
    public static final float BREED_PER_MIN = 0.01f;
    public static final float FLOWER_BONUS = 1.5f, HONEY_BONUS = 1.25f;
    public static final int FLOWERS_NEEDED = 6, FLOWER_RANGE = 5;
    /** Feeding by hand: 10 chakra → +300. Depositing: 5,000 at a time. */
    public static final float FEED_CHAKRA = 10f;
    public static final int FEED_INSECTS = 300, DEPOSIT = 5000;
    /** Losses: fire nearby 2% a second, an explosion next to it half. */
    public static final float FIRE_LOSS = 0.02f, EXPLOSION_LOSS = 0.5f;
    /** Sleep this close and your body refills from it. */
    public static final int SLEEP_RANGE = 16;
    public static final int HONEY_TICKS = 24000;

    /** Loaded hives (for sleeping refills and explosions). */
    static final Set<InsectHiveBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    private UUID owner;
    private int count;
    private int honeyTicks;
    private float remainder;
    private int flowers = -1;

    public InsectHiveBlockEntity(BlockPos pos, BlockState state) {
        super(AburameRegistry.HIVE_ENTITY.get(), pos, state);
    }

    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    public int count() {
        return count;
    }

    public void setCount(int n) {
        count = Math.max(0, Math.min(capacity(), n));
        setChanged();
    }

    public int capacity() {
        Player p = ownerPlayer();
        ColonyData c = p == null ? null : Colony.of(p);
        return ColonyGrowth.HIVE_CAPACITY + (c != null && c.hasStrain(WildKind.JEWEL_BEETLE) ? ColonyGrowth.JEWEL_HIVE_BONUS : 0);
    }

    public void addHoney() {
        honeyTicks = HONEY_TICKS;
        setChanged();
    }

    Player ownerPlayer() {
        return owner == null || level == null ? null : level.getPlayerByUUID(owner);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            LOADED.add(this);
        }
    }

    @Override
    public void setRemoved() {
        LOADED.remove(this);
        super.setRemoved();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, InsectHiveBlockEntity hive) {
        if (honeyTicks(hive) > 0) {
            hive.honeyTicks--;
        }
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        if (hive.flowers < 0 || level.getGameTime() % 200 == 0) {
            hive.flowers = countFlowers(level, pos);
        }
        hive.secondOfLife(level, pos);
    }

    private static int honeyTicks(InsectHiveBlockEntity hive) {
        return hive.honeyTicks;
    }

    /** One second: breeding, then fire losses; tell the owner how many it holds. */
    void secondOfLife(Level level, BlockPos pos) {
        float rate = BREED_PER_MIN / 60f * count;
        if (flowers >= FLOWERS_NEEDED) {
            rate *= FLOWER_BONUS;
        }
        if (honeyTicks > 0) {
            rate *= HONEY_BONUS;
        }
        if (count > 0 && count < capacity()) {
            float exact = remainder + rate;
            setCount(count + (int) exact);
            remainder = exact - (int) exact;
        }
        if (fireNear(level, pos)) {
            setCount(count - Math.max(1, Math.round(count * FIRE_LOSS)));
        }
        Player p = ownerPlayer();
        if (p != null && level.getGameTime() % 100 == 0) {
            Colony.noteHive(p, level.dimension().location().toString(), pos, count);
        }
    }

    static int countFlowers(Level level, BlockPos pos) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-FLOWER_RANGE, -2, -FLOWER_RANGE), pos.offset(FLOWER_RANGE, 2, FLOWER_RANGE))) {
            if (level.getBlockState(p).is(BlockTags.FLOWERS)) {
                n++;
            }
        }
        return n;
    }

    static boolean fireNear(Level level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 2, 2))) {
            if (level.getBlockState(p).getBlock() instanceof BaseFireBlock) {
                return true;
            }
        }
        return false;
    }

    /** An explosion next to it kills half. */
    public void exploded() {
        setCount(Math.round(count * (1f - EXPLOSION_LOSS)));
    }

    /** Use: feed it (or claim an unowned hive); sneak + use: deposit 5,000 from the body. */
    public void interact(Player player, boolean deposit) {
        ColonyData colony = Colony.of(player);
        if (colony == null) {
            player.displayClientMessage(Component.translatable("message.noirm.colony.none"), true);
            return;
        }
        if (owner == null) {
            setOwner(player.getUUID());
        }
        if (!player.getUUID().equals(owner)) {
            player.displayClientMessage(Component.translatable("message.noirm.colony.hive_not_yours"), true);
            return;
        }
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (deposit) {
            int move = Math.min(Math.min(DEPOSIT, colony.body(Species.KIKAICHU)), capacity() - count);
            colony.addBody(Species.KIKAICHU, -move);
            setCount(count + move);
            player.displayClientMessage(Component.translatable("message.noirm.colony.hive_deposit", move, count, capacity()), true);
        } else if (data != null && (player.getAbilities().instabuild || data.tryConsumeChakra(FEED_CHAKRA))) {
            setCount(count + FEED_INSECTS);
            player.displayClientMessage(Component.translatable("message.noirm.colony.hive_fed", count, capacity(), flowers, honeyTicks > 0 ? "✓" : "-"), true);
        } else {
            player.displayClientMessage(Component.translatable("message.noirm.colony.hive_status", count, capacity()), true);
        }
        Colony.noteHive(player, level.dimension().location().toString(), worldPosition, count);
    }

    /** Morning after sleeping nearby: the body refills from the hive. */
    public int refill(Player player) {
        ColonyData colony = Colony.of(player);
        if (colony == null || !player.getUUID().equals(owner)) {
            return 0;
        }
        int move = Math.min(count, Math.max(0, Colony.capacity(player) - colony.bodyTotal()));
        colony.addBody(Species.KIKAICHU, move);
        setCount(count - move);
        Colony.noteHive(player, level.dimension().location().toString(), worldPosition, count);
        return move;
    }

    void forgetFromOwner() {
        Player p = ownerPlayer();
        if (p != null) {
            Colony.forgetHive(p, level.dimension().location().toString(), worldPosition);
        }
    }

    /** Refills {@code player} from every hive of theirs within sleeping range. */
    public static int refillAfterSleep(ServerPlayer player) {
        int total = 0;
        for (InsectHiveBlockEntity hive : Set.copyOf(LOADED)) {
            if (hive.level == player.level() && !hive.isRemoved()
                    && hive.worldPosition.distSqr(player.blockPosition()) <= SLEEP_RANGE * SLEEP_RANGE) {
                total += hive.refill(player);
            }
        }
        return total;
    }

    /** Explosions halve hives within their reach. */
    public static void onExplosion(ServerLevel level, net.minecraft.world.phys.Vec3 at, float radius) {
        for (InsectHiveBlockEntity hive : Set.copyOf(LOADED)) {
            if (hive.level == level && !hive.isRemoved() && hive.worldPosition.getCenter().distanceTo(at) <= radius + 1.5) {
                hive.exploded();
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putInt("Count", count);
        tag.putInt("Honey", honeyTicks);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        count = tag.getInt("Count");
        honeyTicks = tag.getInt("Honey");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
