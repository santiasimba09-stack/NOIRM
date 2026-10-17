package com.noirm.weapon;

import com.noirm.combat.CombatAnimated;
import com.noirm.combat.CombatSystem;
import com.noirm.network.CombatClientPackets;
import com.noirm.network.NoirmNetwork;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Throwing ninja tools, for players (right click) and ninjas ({@code NinjaThrowGoal}). Server side. */
public final class Throwing {
    /** Degrees between the stars of a fan throw. */
    public static final float FAN_SPREAD = 10f;
    public static final int FAN_COUNT = 3;
    /** How far off a ninja's throw may go (a player's is 1, like a bow). */
    public static final float NPC_INACCURACY = 2.5f;

    private Throwing() {}

    /**
     * A player throws what's in {@code hand}: one, or a fan of three (shuriken, sneaking). Uses up
     * what's thrown (not in creative) and starts the weapon's cooldown. Returns how many flew.
     */
    public static int playerThrow(Player player, InteractionHand hand, ItemStack stack, ThrowingWeaponItem item, boolean fan) {
        if (!CombatSystem.canAct(player)) {
            return 0; // mid-swing, staggered or guarding
        }
        ThrowStats stats = item.stats();
        boolean creative = player.getAbilities().instabuild;
        int count = fan ? Math.min(FAN_COUNT, creative ? FAN_COUNT : stack.getCount()) : 1;
        for (int i = 0; i < count; i++) {
            float yaw = player.getYRot() + (i - (count - 1) / 2f) * FAN_SPREAD;
            ThrownWeapon weapon = ThrownWeapon.thrownBy(player, stack);
            weapon.shootFromRotation(player, player.getXRot(), yaw, 0f, stats.speed(), 1f);
            player.level().addFreshEntity(weapon);
        }
        if (!creative) {
            stack.shrink(count);
        }
        player.getCooldowns().addCooldown(item, count > 1 ? stats.cooldown() * 2 : stats.cooldown());
        player.awardStat(Stats.ITEM_USED.get(item));
        effects(player, stats);
        return count;
    }

    /**
     * A ninja throws a copy of {@code stack} at {@code target} (ninjas' supplies never run out,
     * and their copies can't be picked up). Aims a little high so the drop lands it.
     */
    public static ThrownWeapon npcThrow(LivingEntity thrower, LivingEntity target, ItemStack stack, boolean fan) {
        if (!(stack.getItem() instanceof ThrowingWeaponItem item)) {
            return null;
        }
        ThrowStats stats = item.stats();
        int count = fan ? FAN_COUNT : 1;
        ThrownWeapon first = null;
        for (int i = 0; i < count; i++) {
            ThrownWeapon weapon = ThrownWeapon.thrownBy(thrower, stack);
            Vec3 aim = aimAt(weapon.position(), target, stats);
            float yawOffset = (i - (count - 1) / 2f) * FAN_SPREAD * net.minecraft.util.Mth.DEG_TO_RAD;
            aim = aim.yRot(yawOffset);
            weapon.shoot(aim.x, aim.y, aim.z, stats.speed(), NPC_INACCURACY);
            thrower.level().addFreshEntity(weapon);
            if (first == null) {
                first = weapon;
            }
        }
        effects(thrower, stats);
        return first;
    }

    /** Direction from {@code from} to the target's chest, raised by how far the weapon drops on the way. */
    public static Vec3 aimAt(Vec3 from, LivingEntity target, ThrowStats stats) {
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);
        Vec3 delta = to.subtract(from);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double ticks = horizontal / stats.speed();
        double drop = 0.5 * stats.gravity() * ticks * ticks;
        return new Vec3(delta.x, delta.y + drop, delta.z);
    }

    /** The whoosh, and the thrower's throw animation. */
    private static void effects(LivingEntity thrower, ThrowStats stats) {
        thrower.level().playSound(null, thrower.getX(), thrower.getEyeY(), thrower.getZ(),
                stats.flat() ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS,
                0.6f, stats.pierce() > 0 ? 0.7f : 1.4f);
        if (thrower instanceof Player) {
            NoirmNetwork.sendToTrackingAndSelf(thrower, new CombatClientPackets.PlayAnimation(thrower.getId(), stats.animation(), stats.animationTicks()));
        } else if (thrower instanceof CombatAnimated animated) {
            animated.playCombatAnimation(stats.animation(), stats.animationTicks());
        }
    }
}
