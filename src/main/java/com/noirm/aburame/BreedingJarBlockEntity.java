package com.noirm.aburame;

import com.noirm.entity.SparringNinja;
import com.noirm.player.ShinobiCapability;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The Breeding Jar's work (2.6): its owner's adaptation focus (kept on the colony), the Antivenom cross,
 * and one long task at a time: a Kidaichū Egg (10,000 kikaichū, Control 30, one in-game day) or a Queen
 * (Clan Master, one day). Strains from caught wild insects are bred in at once.
 */
public class BreedingJarBlockEntity extends BlockEntity {
    public static final int ANTIVENOM_GENERATIONS = 30;
    public static final long TASK_TICKS = 24000;
    public static final int EGG_KIKAICHU = 10000;
    public static final double CROSS_RANGE = 16;

    public enum Task { NONE, EGG, QUEEN }

    static final Set<BreedingJarBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    private UUID owner;
    private boolean cross;
    private Task task = Task.NONE;
    private long taskEnd;

    public BreedingJarBlockEntity(BlockPos pos, BlockState state) {
        super(AburameRegistry.JAR_ENTITY.get(), pos, state);
    }

    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        sync();
    }

    public boolean cross() {
        return cross;
    }

    public Task task() {
        return task;
    }

    public long taskEnd() {
        return taskEnd;
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

    /** Whether {@code player} is crossing their kikaichū with rinkaichū this generation (a jar of theirs, set to cross, with a rinkaichū source). */
    public static boolean crossing(Player player) {
        boolean jar = Set.copyOf(LOADED).stream().anyMatch(j -> !j.isRemoved() && j.cross && player.getUUID().equals(j.owner));
        return jar && rinkaichuSource(player);
    }

    /** A rinkaichū jar in your inventory, your own rinkaichū, or a rinkaichū crew mate within 16 blocks. */
    public static boolean rinkaichuSource(Player player) {
        if (RotTouch.host(player)) {
            return true;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() instanceof InsectJarItem && InsectJarItem.species(s) == Species.RINKAICHU && InsectJarItem.count(s) > 0) {
                return true;
            }
        }
        return !player.level().getEntitiesOfClass(SparringNinja.class, player.getBoundingBox().inflate(CROSS_RANGE),
                n -> player.getUUID().equals(n.leaderId()) && RotTouch.host(n)).isEmpty();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BreedingJarBlockEntity jar) {
        if (jar.task != Task.NONE && level.getGameTime() >= jar.taskEnd) {
            jar.finishTask();
        }
    }

    private void finishTask() {
        ItemStack out = task == Task.EGG ? new ItemStack(AburameRegistry.KIDAICHU_EGG.get())
                : task == Task.QUEEN ? new ItemStack(AburameRegistry.QUEEN_COCOON.get()) : ItemStack.EMPTY;
        task = Task.NONE;
        sync();
        if (!out.isEmpty() && level != null) {
            net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, out);
            Player p = owner == null ? null : level.getPlayerByUUID(owner);
            if (p != null) {
                p.sendSystemMessage(Component.translatable("message.noirm.colony.jar_done", out.getHoverName()).withStyle(ChatFormatting.GREEN));
            }
        }
    }

    /** A button on the jar's screen. Returns a message key to show, or null. */
    public String action(ServerPlayer player, String action) {
        ColonyData colony = Colony.of(player);
        if (colony == null) {
            return "message.noirm.colony.none";
        }
        if (owner == null) {
            owner = player.getUUID();
        }
        if (!player.getUUID().equals(owner)) {
            return "message.noirm.colony.hive_not_yours";
        }
        String result = null;
        if (action.startsWith("focus:")) {
            Harm h = Harm.byKey(action.substring(6));
            colony.setFocus(h != null && h.adaptable ? h : null);
            result = "message.noirm.colony.jar_focus";
        } else if (action.equals("cross")) {
            cross = !cross;
            result = cross ? (rinkaichuSource(player) ? "message.noirm.colony.jar_cross_on" : "message.noirm.colony.jar_cross_nosource")
                    : "message.noirm.colony.jar_cross_off";
        } else if (action.equals("egg")) {
            float control = ShinobiCapability.get(player).map(d -> d.getControl()).orElse(0f);
            if (task != Task.NONE) {
                result = "message.noirm.colony.jar_busy";
            } else if (colony.body(Species.KIKAICHU) < EGG_KIKAICHU || control < Species.KIDAICHU_CONTROL) {
                result = "message.noirm.colony.jar_egg_needs";
            } else {
                task = Task.EGG;
                taskEnd = level.getGameTime() + TASK_TICKS;
                result = "message.noirm.colony.jar_started";
            }
        } else if (action.equals("queen")) {
            if (task != Task.NONE) {
                result = "message.noirm.colony.jar_busy";
            } else if (colony.rank().ordinal() < HiveRank.CLAN_MASTER.ordinal()) {
                result = "message.noirm.colony.jar_queen_needs";
            } else {
                task = Task.QUEEN;
                taskEnd = level.getGameTime() + TASK_TICKS;
                result = "message.noirm.colony.jar_started";
            }
        } else if (action.startsWith("strain:")) {
            WildKind kind = WildKind.byKey(action.substring(7));
            if (kind == null || !colony.caught().contains(kind.key())) {
                result = "message.noirm.colony.jar_strain_missing";
            } else if (colony.strains().add(kind.key())) {
                result = "message.noirm.colony.jar_strain";
            }
        } else if (action.equals("finish") && player.getAbilities().instabuild) {
            taskEnd = level.getGameTime(); // a creative shortcut for testing
        }
        Colony.changed(player);
        sync();
        return result;
    }

    void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putBoolean("Cross", cross);
        tag.putInt("Task", task.ordinal());
        tag.putLong("TaskEnd", taskEnd);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        cross = tag.getBoolean("Cross");
        task = Task.values()[Math.max(0, Math.min(2, tag.getInt("Task")))];
        taskEnd = tag.getLong("TaskEnd");
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
