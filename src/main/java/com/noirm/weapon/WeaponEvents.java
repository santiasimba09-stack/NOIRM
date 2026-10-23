package com.noirm.weapon;

import com.noirm.Noirm;
import com.noirm.clan.Nature;
import com.noirm.combat.CombatSystem;
import com.noirm.player.ShinobiCapability;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Chakra blades channel the wielder's nature into every melee hit (vanilla swings and combat moves). */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class WeaponEvents {
    /** Posture an earth-charged hit adds. */
    public static final float EARTH_POSTURE = 12f;
    /** Ticks a lightning-charged hit numbs the target for. */
    public static final int LIGHTNING_STUN = 6;

    private WeaponEvents() {}

    @SubscribeEvent
    public static void hurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || event.getSource().getDirectEntity() != attacker
                || !(attacker.getMainHandItem().getItem() instanceof ChakraBladeItem) || attacker.level().isClientSide()) {
            return;
        }
        Nature nature = ChakraBladeItem.natureOf(attacker);
        if (nature == null || !payChakra(attacker)) {
            return;
        }
        LivingEntity target = event.getEntity();
        ServerLevel level = (ServerLevel) attacker.level();
        Vec3 chest = target.position().add(0, target.getBbHeight() * 0.6, 0);
        switch (nature) {
            case WIND -> {
                event.setAmount(event.getAmount() * ChakraBladeItem.WIND_DAMAGE);
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, chest.x, chest.y, chest.z, 1, 0, 0, 0, 0);
            }
            case FIRE -> {
                target.setSecondsOnFire(3);
                level.sendParticles(ParticleTypes.FLAME, chest.x, chest.y, chest.z, 8, 0.2, 0.2, 0.2, 0.02);
            }
            case LIGHTNING -> {
                CombatSystem.stun(target, LIGHTNING_STUN);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, chest.x, chest.y, chest.z, 12, 0.3, 0.3, 0.3, 0.1);
            }
            case WATER -> {
                Vec3 push = target.position().subtract(attacker.position()).multiply(1, 0, 1).normalize().scale(0.5);
                target.push(push.x, 0.1, push.z);
                level.sendParticles(ParticleTypes.SPLASH, chest.x, chest.y, chest.z, 12, 0.3, 0.2, 0.3, 0.1);
            }
            case EARTH -> {
                CombatSystem.addPostureDamage(target, EARTH_POSTURE);
                level.sendParticles(ParticleTypes.CRIT, chest.x, chest.y, chest.z, 8, 0.2, 0.2, 0.2, 0.1);
            }
            default -> { }
        }
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.6f, 1.8f);
    }

    /** Players pay a little chakra per charged hit; ninjas don't. */
    private static boolean payChakra(LivingEntity attacker) {
        if (attacker instanceof Player player) {
            if (player.getAbilities().instabuild) {
                return true;
            }
            return ShinobiCapability.get(player).map(d -> d.tryConsumeChakra(ChakraBladeItem.CHAKRA_PER_HIT)).orElse(false);
        }
        return true;
    }
}
