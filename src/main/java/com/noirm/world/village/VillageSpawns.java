package com.noirm.world.village;

import com.noirm.Noirm;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.raid.Raider;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps other structures' raiders out of ninja villages: a pillager outpost that world generation
 * placed where a hidden village later went up (its tower is cleared with the ground) would keep
 * spawning pillagers among the houses. Natural and structure spawns of raiders inside a village are
 * turned away; patrols that march in still come (the village's ninjas deal with them).
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VillageSpawns {
    private VillageSpawns() {}

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!(event.getEntity() instanceof Raider)) {
            return;
        }
        ServerLevel level = event.getLevel().getLevel();
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.STRUCTURE && type != MobSpawnType.CHUNK_GENERATION) {
            return;
        }
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        if (level.structureManager().getStructureWithPieceAt(pos, VillageAtmosphere.VILLAGES).isValid() || BuiltVillages.of(level).at(pos) != null) {
            event.setSpawnCancelled(true);
        }
    }
}
