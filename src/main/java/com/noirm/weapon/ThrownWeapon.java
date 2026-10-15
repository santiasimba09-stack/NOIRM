package com.noirm.weapon;

import com.noirm.combat.Perception;
import com.noirm.world.Destruction;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown ninja tool: kunai, shuriken, fūma shuriken, senbon, explosive kunai, or an explosive
 * tag slapped onto a block. Built on the arrow, so it sticks in blocks and can be picked back up
 * (thrown by a player in survival; never ninjas' copies or explosives). The item it carries keeps
 * its enchantments, which apply to the hit. How it flies comes from its {@link ThrowStats}.
 */
public class ThrownWeapon extends AbstractArrow {
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(ThrownWeapon.class, EntityDataSerializers.ITEM_STACK);
    /** Thrown copies of ninjas vanish soon after landing; players' stay this long (ticks). */
    private static final int PLAYER_DESPAWN = 2400;
    private static final int NPC_DESPAWN = 100;
    /** A tag or explosive kunai that never sticks anywhere goes off after this long anyway. */
    private static final int MAX_FLIGHT = 100;
    /** An explosive kunai that hits someone goes off this soon after. */
    private static final int HIT_FUSE = 12;

    private int fuse = -1;
    private boolean dealtDamage;
    private boolean npcThrown;
    private int groundTicks;
    private int flightTicks;
    private final IntOpenHashSet pierced = new IntOpenHashSet();
    /** Client: spin angle for the renderer (degrees), this tick and last. */
    public float spin;
    public float spinO;

    public ThrownWeapon(EntityType<? extends ThrownWeapon> type, Level level) {
        super(type, level);
        setNoGravity(true); // our own gravity per weapon, below
    }

    private ThrownWeapon(LivingEntity thrower, ItemStack stack) {
        super(NinjaWeapons.THROWN_WEAPON.get(), thrower, thrower.level());
        setNoGravity(true);
        setItem(stack);
        boolean player = thrower instanceof Player;
        npcThrown = !player;
        if (stats().fuse() > 0 || !player) {
            pickup = Pickup.DISALLOWED;
        } else {
            pickup = ((Player) thrower).getAbilities().instabuild ? Pickup.CREATIVE_ONLY : Pickup.ALLOWED;
        }
    }

    /** A weapon thrown by {@code thrower}, not launched yet ({@link #shoot}). {@code stack} is copied (one item). */
    public static ThrownWeapon thrownBy(LivingEntity thrower, ItemStack stack) {
        return new ThrownWeapon(thrower, stack.copyWithCount(1));
    }

    /** An explosive tag being slapped onto a block from {@code at}, moving {@code motion} per tick. */
    public static ThrownWeapon placedTag(ServerLevel level, Entity owner, Vec3 at, Vec3 motion) {
        ThrownWeapon tag = new ThrownWeapon(NinjaWeapons.THROWN_WEAPON.get(), level);
        tag.setPos(at);
        tag.setOwner(owner);
        tag.setItem(new ItemStack(NinjaWeapons.EXPLOSIVE_TAG.get()));
        tag.pickup = Pickup.DISALLOWED;
        tag.shoot(motion.x, motion.y, motion.z, (float) motion.length(), 0f);
        return tag;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ITEM, ItemStack.EMPTY);
    }

    public ItemStack getItem() {
        return entityData.get(ITEM);
    }

    public void setItem(ItemStack stack) {
        entityData.set(ITEM, stack);
    }

    public ThrowStats stats() {
        return getItem().getItem() instanceof ThrowingWeaponItem weapon ? weapon.stats() : ThrowStats.PLACED_TAG;
    }

    /** Stuck in a block (like an arrow). */
    public boolean isStuck() {
        return inGround;
    }

    /** True once it has hit someone (it no longer hurts anybody). */
    public boolean hasDealtDamage() {
        return dealtDamage;
    }

    /** Ticks left before the blast, or -1 if not lit (or not explosive). */
    public int fuse() {
        return fuse;
    }

    /** True for an explosive tag stuck on its own (rather than one on a kunai). */
    public boolean isPlacedTag() {
        return getItem().getItem() instanceof ExplosiveTagItem;
    }

    // ---------------------------------------------------------------- flight

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) {
            return;
        }
        ThrowStats stats = stats();
        if (!inGround && !isNoPhysics()) {
            setDeltaMovement(getDeltaMovement().add(0, -stats.gravity(), 0));
            flightTicks++;
        }
        if (level().isClientSide()) {
            spinO = spin;
            if (!inGround && !dealtDamage) {
                spin += stats.spin();
            }
            if (stats.fuse() > 0 && (inGround || dealtDamage) && tickCount % 2 == 0) {
                level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.1, getZ(), 0, 0.03, 0);
            }
            return;
        }
        if (stats.fuse() > 0) {
            if (fuse < 0 && (inGround || flightTicks > MAX_FLIGHT)) {
                fuse = stats.fuse();
            }
            if (fuse > 0) {
                if (fuse % 6 == 0) {
                    ((ServerLevel) level()).sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.1, getZ(), 1, 0.05, 0.05, 0.05, 0.01);
                    playSound(SoundEvents.FIRE_EXTINGUISH, 0.15f, 2f);
                }
                if (--fuse == 0) {
                    explode();
                }
            }
        }
    }

    @Override
    protected void tickDespawn() {
        if (++groundTicks >= (npcThrown ? NPC_DESPAWN : PLAYER_DESPAWN) && stats().fuse() <= 0) {
            discard();
        }
    }

    @Override
    protected EntityHitResult findHitEntity(Vec3 start, Vec3 end) {
        if (dealtDamage) {
            return null;
        }
        float inflate = stats().pierce() > 0 ? 0.6f : 0.3f; // the fūma's blades are wide
        return ProjectileUtil.getEntityHitResult(level(), this, start, end,
                getBoundingBox().expandTowards(getDeltaMovement()).inflate(1.0), this::canHitEntity, inflate);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!super.canHitEntity(entity) || pierced.contains(entity.getId())) {
            return false;
        }
        // Crew mates' throws never hit their own side.
        return !(getOwner() instanceof LivingEntity owner && entity instanceof LivingEntity living
                && (living == owner || com.noirm.ally.Crew.sameSide(owner, living)));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        ThrowStats stats = stats();
        ItemStack stack = getItem();
        Entity owner = getOwner();
        boolean hurt = false;
        if (!level().isClientSide()) {
            float damage = stats.damage();
            if (owner instanceof LivingEntity thrower) {
                damage *= throwPower(thrower);
            }
            if (target instanceof LivingEntity living) {
                damage += EnchantmentHelper.getDamageBonus(stack, living.getMobType());
            }
            DamageSource source = damageSources().thrown(this, owner == null ? this : owner);
            hurt = damage > 0 && target.hurt(source, damage);
            if (hurt && target instanceof LivingEntity living) {
                afterHurt(living, stack, stats, owner);
            }
        }
        playSound(hurt ? SoundEvents.TRIDENT_HIT : SoundEvents.SHIELD_BLOCK, 0.7f, stats.flat() ? 1.5f : 1.2f);
        if (hurt && stats.pierce() > pierced.size()) {
            pierced.add(target.getId()); // the big blades carry on into the next one
            return;
        }
        dealtDamage = true;
        if (stats.fuse() > 0 && fuse < 0 && !level().isClientSide()) {
            fuse = HIT_FUSE;
        }
        if (hurt && stats.slowTicks() > 0) {
            discard(); // a needle stays in the body
            return;
        }
        setDeltaMovement(getDeltaMovement().multiply(-0.01, -0.1, -0.01)); // glance off and drop
    }

    private void afterHurt(LivingEntity target, ItemStack stack, ThrowStats stats, Entity owner) {
        int fire = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FIRE_ASPECT, stack);
        if (fire > 0) {
            target.setSecondsOnFire(4 * fire);
        }
        double push = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.KNOCKBACK, stack) * 0.5 + (stats.pierce() > 0 ? 0.3 : 0);
        if (push > 0) {
            Vec3 dir = getDeltaMovement().multiply(1, 0, 1).normalize().scale(push);
            if (dir.lengthSqr() > 0) {
                target.push(dir.x, 0.1, dir.z);
            }
        }
        if (stats.slowTicks() > 0) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stats.slowTicks(), 1), owner);
        }
        if (owner instanceof LivingEntity thrower) {
            EnchantmentHelper.doPostHurtEffects(target, thrower);
            EnchantmentHelper.doPostDamageEffects(thrower, target);
        }
    }

    /** Stronger, faster throwers throw harder: +30% per extra "power" (1 at speed/taijutsu 10). */
    public static float throwPower(LivingEntity thrower) {
        float power = Perception.statPower(Perception.basePower(thrower));
        return Math.max(0.75f, 1f + 0.3f * (power - 1f));
    }

    // ---------------------------------------------------------------- explosive tags

    private void explode() {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 at = position();
        Entity owner = getOwner();
        level.explode(this, damageSources().explosion(this, owner), null, at.x, at.y, at.z, ThrowStats.TAG_BLAST, false,
                Level.ExplosionInteraction.NONE);
        if (diggingAllowed(level, owner)) {
            Destruction.crater(level, at, ThrowStats.TAG_BLAST * 0.8f, 1.0f);
        }
        discard();
    }

    /**
     * Whether a blast may dig: only with {@code noirmTerrainDamage} on, and for anyone but a
     * player only with {@code mobGriefing} on too.
     */
    public static boolean diggingAllowed(ServerLevel level, Entity owner) {
        return Destruction.enabled(level) && (owner instanceof Player || level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING));
    }

    // ---------------------------------------------------------------- arrow plumbing

    @Override
    protected ItemStack getPickupItem() {
        return getItem().copy();
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.TRIDENT_HIT_GROUND;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Weapon", getItem().save(new CompoundTag()));
        tag.putInt("Fuse", fuse);
        tag.putBoolean("DealtDamage", dealtDamage);
        tag.putBoolean("NpcThrown", npcThrown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Weapon")) {
            setItem(ItemStack.of(tag.getCompound("Weapon")));
        }
        fuse = tag.contains("Fuse") ? tag.getInt("Fuse") : -1;
        dealtDamage = tag.getBoolean("DealtDamage");
        npcThrown = tag.getBoolean("NpcThrown");
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64 * 64;
    }
}
