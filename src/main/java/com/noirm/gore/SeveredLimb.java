package com.noirm.gore;

import com.noirm.entity.SparringNinja;
import com.noirm.registry.NoirmEntities;
import com.noirm.vfx.Vfx;
import com.noirm.vfx.VfxKind;
import com.noirm.vfx.VfxOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;

/**
 * A severed arm, leg or head (see {@link Gore}): a light ragdoll that tumbles, bounces and
 * settles, wearing the victim's skin and the matching armor piece. After {@link #LIFE_TICKS} it
 * burns away in blue chakra flame ({@link #DISSOLVE_TICKS}) and crumbles into ash. Never saved
 * with the world; at most {@link #MAX_LIMBS} at once (the oldest goes first); clients only get
 * it within 32 blocks (the tracking range) and only draw it within {@link #RENDER_DISTANCE}.
 */
public class SeveredLimb extends Entity {
    /** Seconds a limb lies there before it starts to burn away: 5 s. */
    public static final int LIFE_TICKS = 100;
    /** How long the chakra flame takes to eat it. */
    public static final int DISSOLVE_TICKS = 30;
    /** The most limbs alive at once (the oldest goes first). */
    public static final int MAX_LIMBS = 12;
    /** Not drawn (or ticked for effects) beyond this many blocks. */
    public static final double RENDER_DISTANCE = 32;
    static final double GRAVITY = 0.045;
    static final double BOUNCE = 0.35;
    static final double GROUND_FRICTION = 0.6;

    /** Which part: arms and legs on the victim's own right / left. */
    public enum Part {
        HEAD, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG;

        private static final Part[] VALUES = values();

        public int bit() {
            return 1 << ordinal();
        }

        public static Part byId(int id) {
            return VALUES[Mth.clamp(id, 0, VALUES.length - 1)];
        }

        public boolean isLeg() {
            return this == RIGHT_LEG || this == LEFT_LEG;
        }
    }

    private static final EntityDataAccessor<Byte> PART = SynchedEntityData.defineId(SeveredLimb.class, EntityDataSerializers.BYTE);
    /** Whose skin: {@code player:<uuid>}, or {@code look:<ninja look key>} (empty = the default ninja). */
    private static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(SeveredLimb.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<ItemStack> ARMOR = SynchedEntityData.defineId(SeveredLimb.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> BOOTS = SynchedEntityData.defineId(SeveredLimb.class, EntityDataSerializers.ITEM_STACK);

    /** The limbs alive on the server, oldest first (for the cap). */
    private static final ArrayDeque<SeveredLimb> LIVE = new ArrayDeque<>();

    // Client-side tumbling (degrees): spun in the air, eased flat once it lands.
    public float pitch;
    public float roll;
    public float prevPitch;
    public float prevRoll;
    private float pitchSpeed;
    private float rollSpeed;
    /** Client: what the renderer worked out once (skin, armor textures). */
    public Object renderCache;

    public SeveredLimb(EntityType<? extends SeveredLimb> type, Level level) {
        super(type, level);
        // Each limb tumbles its own way (the id is the same on both sides).
        net.minecraft.util.RandomSource spin = net.minecraft.util.RandomSource.create(getId() * 31L + 7);
        pitchSpeed = (spin.nextFloat() - 0.5f) * 50f;
        rollSpeed = (spin.nextFloat() - 0.5f) * 40f;
    }

    static SeveredLimb create(ServerLevel level, LivingEntity victim, Part part, Vec3 at, Vec3 velocity) {
        SeveredLimb limb = new SeveredLimb(NoirmEntities.SEVERED_LIMB.get(), level);
        limb.setPos(at.x, at.y, at.z);
        limb.setDeltaMovement(velocity);
        limb.setYRot(victim.yBodyRot);
        limb.entityData.set(PART, (byte) part.ordinal());
        limb.entityData.set(SKIN, skinKey(victim));
        return limb;
    }

    /** The victim's skin, as the client looks it up. */
    static String skinKey(LivingEntity victim) {
        if (victim instanceof Player player) {
            return "player:" + player.getUUID();
        }
        if (victim instanceof SparringNinja ninja) {
            return "look:" + ninja.outfit();
        }
        return "look:";
    }

    /** Adds the limb to the world, removing the oldest ones beyond {@link #MAX_LIMBS}. */
    static void spawn(ServerLevel level, SeveredLimb limb) {
        while (LIVE.size() >= MAX_LIMBS) {
            SeveredLimb oldest = LIVE.pollFirst();
            if (oldest != null && !oldest.isRemoved()) {
                oldest.crumble();
            }
        }
        if (level.addFreshEntity(limb)) {
            LIVE.addLast(limb);
        }
    }

    /** How many limbs are alive (server). */
    public static int liveCount() {
        LIVE.removeIf(Entity::isRemoved);
        return LIVE.size();
    }

    static void clearLive() {
        LIVE.clear();
    }

    void setArmor(ItemStack armor, ItemStack boots) {
        entityData.set(ARMOR, armor);
        entityData.set(BOOTS, boots);
    }

    public Part part() {
        return Part.byId(entityData.get(PART));
    }

    public String skin() {
        return entityData.get(SKIN);
    }

    public ItemStack armor() {
        return entityData.get(ARMOR);
    }

    public ItemStack boots() {
        return entityData.get(BOOTS);
    }

    /** 0 while it lies there, rising to 1 as the chakra flame eats it. */
    public float dissolve(float partialTick) {
        return Mth.clamp((tickCount + partialTick - LIFE_TICKS) / DISSOLVE_TICKS, 0f, 1f);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(PART, (byte) 0);
        entityData.define(SKIN, "");
        entityData.define(ARMOR, ItemStack.EMPTY);
        entityData.define(BOOTS, ItemStack.EMPTY);
    }

    @Override
    public void tick() {
        super.tick();
        // A light ragdoll: falls, bounces a little, slides to a stop.
        Vec3 motion = getDeltaMovement();
        double vy = motion.y - GRAVITY;
        move(MoverType.SELF, new Vec3(motion.x, vy, motion.z));
        if (verticalCollisionBelow) {
            boolean bounce = vy < -0.12;
            setDeltaMovement(motion.x * GROUND_FRICTION, bounce ? -vy * BOUNCE : 0, motion.z * GROUND_FRICTION);
        } else {
            double x = horizontalCollision ? -motion.x * 0.4 : motion.x * 0.98;
            double z = horizontalCollision ? -motion.z * 0.4 : motion.z * 0.98;
            setDeltaMovement(x, vy * 0.98, z);
        }
        if (level().isClientSide()) {
            tumble();
            if (tickCount >= LIFE_TICKS) {
                flame();
            }
            return;
        }
        if (tickCount >= LIFE_TICKS + DISSOLVE_TICKS || getY() < level().getMinBuildHeight() - 16) {
            crumble();
        }
    }

    /** Spins in the air; once down it eases flat onto the ground (limbs lie on their side). */
    private void tumble() {
        prevPitch = pitch;
        prevRoll = roll;
        if (!onGround()) {
            pitch += pitchSpeed;
            roll += rollSpeed;
            return;
        }
        pitchSpeed *= 0.5f;
        rollSpeed *= 0.5f;
        float restPitch = Math.round(pitch / 90f) * 90f;
        float restRoll = Math.round(roll / 90f) * 90f;
        boolean upright = Math.round(restPitch / 90f) % 2 == 0 && Math.round(restRoll / 90f) % 2 == 0;
        if (part() != Part.HEAD && upright) {
            restPitch += pitch >= restPitch ? 90f : -90f; // an arm or leg doesn't stand on its end
        }
        pitch += (restPitch - pitch) * 0.35f + pitchSpeed;
        roll += (restRoll - roll) * 0.35f + rollSpeed;
    }

    /** Client: blue chakra flame licking up from the limb while it dissolves. */
    private static final VfxOptions FLAME = new VfxOptions(VfxKind.GLOW, 0xCFF4FF, 0x1E5AFF, 0.22f, 0.04f, 12, -0.02f, 0.9f);
    private static final VfxOptions EMBER = new VfxOptions(VfxKind.SPARK, 0xE8FBFF, 0x3A8CFF, 0.06f, 0.01f, 10, -0.03f, 0.9f);

    private void flame() {
        if (random.nextFloat() < 0.8f) {
            level().addParticle(FLAME, getX() + (random.nextFloat() - 0.5f) * 0.4, getY() + 0.1 + random.nextFloat() * 0.2,
                    getZ() + (random.nextFloat() - 0.5f) * 0.4, 0, 0.03 + random.nextFloat() * 0.03, 0);
        }
        if (random.nextFloat() < 0.3f) {
            level().addParticle(EMBER, getX(), getY() + 0.15, getZ(), (random.nextFloat() - 0.5f) * 0.06, 0.06,
                    (random.nextFloat() - 0.5f) * 0.06);
        }
    }

    /** Ash: grey flakes and a puff of smoke, then gone. */
    private static final VfxOptions ASH = new VfxOptions(VfxKind.SHARD, 0x565B60, 0x292D33, 0.06f, 0.03f, 30, 0.01f, 0.92f);
    private static final VfxOptions ASH_SMOKE = new VfxOptions(VfxKind.SMOKE, 0x606A73, 0x17191D, 0.25f, 0.6f, 30, -0.004f, 0.92f);

    void crumble() {
        if (level() instanceof ServerLevel server) {
            Vec3 at = position().add(0, 0.15, 0);
            Vfx.burst(server, ASH, at, 8, 0.15, 0.03);
            Vfx.burst(server, ASH_SMOKE, at, 3, 0.1, 0.01);
        }
        discard();
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (!level().isClientSide()) {
            LIVE.remove(this);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < RENDER_DISTANCE * RENDER_DISTANCE;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false; // a few seconds of gore never outlives the session
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
