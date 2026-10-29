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
 *
 * <p>Monsters don't spawn naturally inside a village either (the dark rooms of its houses, its
 * well, under its eaves): a zombie lurking in a house used to jump new players in the plaza. Those
 * that wander in from outside at night still come, and the village's ninjas deal with them.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VillageSpawns {
    private VillageSpawns() {}

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        MobSpawnType type = event.getSpawnType();
        if (event.getEntity() instanceof Raider) {
            if (type != MobSpawnType.NATURAL && type != MobSpawnType.STRUCTURE && type != MobSpawnType.CHUNK_GENERATION) {
                return;
            }
        } else if (!(event.getEntity() instanceof net.minecraft.world.entity.monster.Enemy) || type != MobSpawnType.NATURAL) {
            return; // (other monsters: only their natural spawns, below)
        }
        ServerLevel level = event.getLevel().getLevel();
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        if (level.structureManager().getStructureWithPieceAt(pos, VillageAtmosphere.VILLAGES).isValid() || BuiltVillages.of(level).at(pos) != null) {
            event.setSpawnCancelled(true);
        }
    }
}
