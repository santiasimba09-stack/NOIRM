package com.noirm.player;

import com.noirm.Noirm;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Attaches {@link ShinobiData} to every player. Forge's "capability" system is how mods add
 * their own saved data to vanilla objects such as players.
 */
public final class ShinobiCapability {
    public static final Capability<ShinobiData> SHINOBI = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation ID = Noirm.id("shinobi");

    private ShinobiCapability() {}

    public static Optional<ShinobiData> get(Player player) {
        return player.getCapability(SHINOBI).resolve();
    }

    /** Holds one player's data and saves it inside the player's save file. */
    static final class Provider implements ICapabilitySerializable<CompoundTag> {
        private final ShinobiData data = new ShinobiData();
        private final LazyOptional<ShinobiData> optional = LazyOptional.of(() -> data);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return SHINOBI.orEmpty(cap, optional);
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.save();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            data.load(tag);
        }
    }
}
