package com.noirm.gore;

import com.noirm.Noirm;
import com.noirm.clan.Nature;
import com.noirm.combat.CombatSystem;
import com.noirm.entity.ShadowClone;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.VillageMerchant;
import com.noirm.jutsu.JutsuProjectile;
import com.noirm.network.NoirmNetwork;
import com.noirm.vfx.Vfx;
import com.noirm.weapon.Moveset;
import com.noirm.weapon.ThrownWeapon;
import com.noirm.weapon.WeaponMoves;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Gore, visual only (blueprint §7). Two things, and neither changes the fight:
 * <ul>
 *   <li><b>Marks:</b> bruises, dirt, soot and cuts build up on players and ninjas as they take
 *   damage, by the kind of damage (fists bruise, blades cut, fire and blasts blacken, falls and
 *   knockdowns dirty). They fade over time and faster as health comes back, and are gone after
 *   death. Four numbers per fighter, synced only when one changes by a visible step.</li>
 *   <li><b>Severed limbs:</b> a killing blow that is a blade, a crit, a blast or a heavy hit can
 *   cut off an arm, a leg or the head (by where the hit came from). The part flies off as a
 *   {@link SeveredLimb}, tumbles and settles, then burns away in blue chakra flame into ash.
 *   Only on death: the living keep their limbs.</li>
 * </ul>
 * The {@code noirmGore} game rule (default on) turns both off. No bleeding, no damage over time.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class Gore {
    // ---------------------------------------------------------------- tuning

    /** Marks gained per hit: this × damage ÷ max health (so losing 40% of your health = full marks). */
    public static final float GAIN_PER_HEALTH = 2.5f;
    /** Even a scratch leaves a little. */
    public static final float MIN_GAIN = 0.04f;
    /** Marks fade by this much per second on their own (a full mark takes ~2.5 minutes). */
    public static final float FADE_PER_SECOND = 0.0067f;
    /** …and by this × each share of max health healed (healing all your health clears half). */
    public static final float HEAL_FADE = 0.5f;
    /** How fast each kind fades compared to the others: bruise, dirt, soot, cut. */
    static final float[] FADE_RATE = {1.0f, 1.4f, 1.2f, 0.7f};
    /** The fade runs every this many ticks. */
    public static final int FADE_INTERVAL = 10;
    /** A change is sent to clients once a mark moved by this many 1/255 steps (or reached zero). */
    public static final int SYNC_STEP = 8;

    /** A hit at least this big (or this share of max health) counts as heavy. */
    public static final float HEAVY_DAMAGE = 8f;
    public static final float HEAVY_SHARE = 0.4f;
    /** Chance a killing blow severs a limb, by kind: blade or crit, blast, heavy blunt hit. */
    public static final float SEVER_CHANCE_BLADE = 1.0f;
    public static final float SEVER_CHANCE_BLAST = 0.75f;
    public static final float SEVER_CHANCE_HEAVY = 0.5f;

    public static GameRules.Key<GameRules.BooleanValue> GORE;

    /** The kinds of marks (their order is the order on the wire and in the textures). */
    public enum Mark { BRUISE, DIRT, SOOT, CUT }

    /** What a killing blow was, for severing. */
    enum Blow { NONE, HEAVY, BLAST, BLADE }

    private static final Int2ObjectOpenHashMap<GoreMarks> MARKS = new Int2ObjectOpenHashMap<>();
    /** The last vanilla critical hit: who was hit and when (a crit that kills cuts clean). */
    private static int critTarget = -1;
    private static long critTick = -1;

    private Gore() {}

    /** Called once while the mod loads. */
    public static void registerGameRules() {
        GORE = GameRules.register("noirmGore", GameRules.Category.MISC, GameRules.BooleanValue.create(true));
    }

    public static boolean enabled(ServerLevel level) {
        return GORE == null || level.getGameRules().getBoolean(GORE);
    }

    /** Who gets marks: players and the humanoids we draw with marks (ninjas, villagers). */
    public static boolean marksOn(LivingEntity entity) {
        return entity instanceof Player || entity instanceof SparringNinja || entity instanceof VillageMerchant;
    }

    /** Who can lose a limb: players and ninjas (clones just vanish in smoke; villagers use another model). */
    public static boolean canSever(LivingEntity entity) {
        return (entity instanceof Player || entity instanceof SparringNinja) && !(entity instanceof ShadowClone);
    }

    /** A mark's level (0–1) on the server, for tests and commands. */
    public static float mark(LivingEntity entity, Mark mark) {
        GoreMarks marks = MARKS.get(entity.getId());
        return marks == null || marks.entity != entity ? 0f : marks.level[mark.ordinal()];
    }

    /** Severed parts of this (dying) body, as a {@link SeveredLimb.Part} bit mask. */
    public static int severed(LivingEntity entity) {
        GoreMarks marks = MARKS.get(entity.getId());
        return marks == null || marks.entity != entity ? 0 : marks.severed;
    }

    // ---------------------------------------------------------------- marks

    private static GoreMarks marksFor(LivingEntity entity) {
        GoreMarks marks = MARKS.get(entity.getId());
        if (marks == null || marks.entity != entity) {
            marks = new GoreMarks(entity);
            MARKS.put(entity.getId(), marks);
        }
        return marks;
    }

    /** Adds {@code amount} of a mark (clamped to 1) and syncs it if it changed visibly. */
    public static void addMark(LivingEntity entity, Mark mark, float amount) {
        if (amount <= 0 || !(entity.level() instanceof ServerLevel level) || !enabled(level) || !marksOn(entity)) {
            return;
        }
        GoreMarks marks = marksFor(entity);
        marks.level[mark.ordinal()] = Math.min(1f, marks.level[mark.ordinal()] + amount);
        syncIfChanged(marks, false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damaged(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        float amount = event.getAmount();
        if (amount <= 0 || !(target.level() instanceof ServerLevel level) || !enabled(level) || !marksOn(target)) {
            return;
        }
        DamageSource source = event.getSource();
        if (ignored(source)) {
            return;
        }
        float gain = Math.max(MIN_GAIN, GAIN_PER_HEALTH * amount / Math.max(1f, target.getMaxHealth()));
        GoreMarks marks = marksFor(target);
        marks.lastHealth = Math.max(0f, target.getHealth() - amount); // what's lost now isn't "healed" later
        Blow blow = addFor(marks, target, source, gain);
        boolean heavy = CombatSystem.isApplyingHeavyHit() || amount >= HEAVY_DAMAGE || amount >= HEAVY_SHARE * target.getMaxHealth();
        if (CombatSystem.isApplyingHeavyHit()) {
            add(marks, Mark.DIRT, gain * 0.3f); // heavy hits put you on the ground
        }
        boolean crit = critTarget == target.getId() && critTick == level.getGameTime();
        if (blow == Blow.NONE && heavy) {
            blow = Blow.HEAVY;
        } else if (blow == Blow.HEAVY && !heavy) {
            blow = Blow.NONE; // a light blunt hit never cuts anything off
        }
        if (crit && blow != Blow.NONE) {
            blow = Blow.BLADE; // a crit cuts clean
        }
        marks.pendingBlow = blow;
        marks.pendingTick = level.getGameTime();
        marks.pendingFrom = source.getSourcePosition();
        if (source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker) {
            marks.pendingFrom = attacker.getEyePosition(); // a melee hit: where the swing came from
        }
        syncIfChanged(marks, false);
    }

    /** Damage that leaves no marks: starving, drowning, /kill and the void, potions, withering, cold. */
    private static boolean ignored(DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypes.STARVE) || source.is(DamageTypes.DROWN)
                || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.WITHER) || source.is(DamageTypes.FREEZE)
                || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.DRY_OUT);
    }

    /** Adds the marks for one hit and returns what kind of blow it was (if it could sever). */
    private static Blow addFor(GoreMarks marks, LivingEntity target, DamageSource source, float gain) {
        Entity direct = source.getDirectEntity();
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            add(marks, Mark.SOOT, gain);
            add(marks, Mark.DIRT, gain * 0.5f);
            add(marks, Mark.BRUISE, gain * 0.3f);
            return Blow.BLAST;
        }
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.LIGHTNING_BOLT)) {
            add(marks, Mark.SOOT, gain);
            return Blow.NONE;
        }
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FLY_INTO_WALL)) {
            add(marks, Mark.DIRT, gain);
            add(marks, Mark.BRUISE, gain * 0.3f);
            return Blow.NONE;
        }
        if (direct instanceof JutsuProjectile jutsu) {
            Nature nature = jutsu.nature();
            if (nature != null) {
                switch (nature) {
                    case FIRE, LAVA, SCORCH, BOIL -> {
                        add(marks, Mark.SOOT, gain);
                        return Blow.NONE;
                    }
                    case EXPLOSION -> {
                        add(marks, Mark.SOOT, gain);
                        add(marks, Mark.DIRT, gain * 0.4f);
                        return Blow.BLAST;
                    }
                    case LIGHTNING, STORM -> {
                        add(marks, Mark.SOOT, gain * 0.6f);
                        add(marks, Mark.BRUISE, gain * 0.5f);
                        return Blow.HEAVY;
                    }
                    case EARTH, DUST -> {
                        add(marks, Mark.DIRT, gain);
                        add(marks, Mark.BRUISE, gain * 0.5f);
                        return Blow.HEAVY;
                    }
                    case WIND -> {
                        add(marks, Mark.CUT, gain);
                        return Blow.BLADE;
                    }
                    default -> {
                    }
                }
            }
            add(marks, Mark.BRUISE, gain);
            return Blow.HEAVY;
        }
        if (direct instanceof ThrownWeapon || direct instanceof AbstractArrow || source.is(DamageTypes.THORNS)
                || source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH)) {
            add(marks, Mark.CUT, gain);
            add(marks, Mark.BRUISE, gain * 0.15f);
            return direct instanceof ThrownWeapon ? Blow.BLADE : Blow.NONE;
        }
        if (direct instanceof LivingEntity attacker && isBlade(attacker.getMainHandItem())) {
            add(marks, Mark.CUT, gain);
            add(marks, Mark.BRUISE, gain * 0.2f);
            return Blow.BLADE;
        }
        add(marks, Mark.BRUISE, gain);
        if (target.onGround()) {
            add(marks, Mark.DIRT, gain * 0.15f);
        }
        return direct instanceof LivingEntity ? Blow.HEAVY : Blow.NONE;
    }

    /** Swords, axes, kunai and chakra blades cut; fists, staves and fans bruise. */
    public static boolean isBlade(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof AxeItem) {
            return true;
        }
        Moveset moveset = WeaponMoves.movesetFor(stack);
        return moveset == Moveset.SWORD || moveset == Moveset.KUNAI;
    }

    private static void add(GoreMarks marks, Mark mark, float amount) {
        marks.level[mark.ordinal()] = Math.min(1f, marks.level[mark.ordinal()] + amount);
    }

    /** Sends the marks to everyone who sees this fighter once one moved by a visible step. */
    private static void syncIfChanged(GoreMarks marks, boolean force) {
        boolean changed = force;
        for (int i = 0; i < 4; i++) {
            int q = Math.round(marks.level[i] * 255f);
            if (Math.abs(q - marks.sent[i]) >= SYNC_STEP || (q == 0) != (marks.sent[i] == 0)) {
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            marks.sent[i] = Math.round(marks.level[i] * 255f);
        }
        NoirmNetwork.sendToTrackingAndSelf(marks.entity, GorePackets.Sync.of(marks.entity.getId(), marks.sent, marks.severed));
    }

    /** Fading, every {@link #FADE_INTERVAL} ticks: with time, and faster as health comes back. */
    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || MARKS.isEmpty() || event.getServer().getTickCount() % FADE_INTERVAL != 0) {
            return;
        }
        float timeFade = FADE_PER_SECOND * FADE_INTERVAL / 20f;
        ObjectIterator<GoreMarks> it = MARKS.values().iterator();
        while (it.hasNext()) {
            GoreMarks marks = it.next();
            LivingEntity entity = marks.entity;
            if (entity.isRemoved()) {
                it.remove();
                continue;
            }
            if (!entity.isAlive()) {
                continue; // the dying body keeps its marks (and missing parts) until it's gone
            }
            float health = entity.getHealth();
            float healed = Math.max(0f, health - marks.lastHealth) / Math.max(1f, entity.getMaxHealth());
            marks.lastHealth = health;
            float fade = timeFade + healed * HEAL_FADE;
            boolean any = false;
            for (int i = 0; i < 4; i++) {
                marks.level[i] = Math.max(0f, marks.level[i] - fade * FADE_RATE[i]);
                any |= marks.level[i] > 0f;
            }
            syncIfChanged(marks, false);
            if (!any && marks.severed == 0) {
                it.remove();
            }
        }
    }

    /** Clears every mark on this fighter (and tells the clients). */
    public static void clear(LivingEntity entity) {
        GoreMarks marks = MARKS.remove(entity.getId());
        if (marks != null || entity instanceof ServerPlayer) {
            NoirmNetwork.sendToTrackingAndSelf(entity, GorePackets.Sync.of(entity.getId(), GoreMarks.ZERO, 0));
        }
    }

    @SubscribeEvent
    public static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clear(player); // a fresh body: same id, no marks, all limbs
        }
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity living && event.getEntity() instanceof ServerPlayer viewer) {
            GoreMarks marks = MARKS.get(living.getId());
            if (marks != null && marks.entity == living) {
                NoirmNetwork.sendToPlayer(viewer, GorePackets.Sync.of(living.getId(), marks.sent, marks.severed));
            }
        }
    }

    @SubscribeEvent
    public static void left(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity living) {
            GoreMarks marks = MARKS.get(living.getId());
            if (marks != null && marks.entity == living) {
                MARKS.remove(living.getId());
            }
        }
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        MARKS.clear();
        SeveredLimb.clearLive();
    }

    @SubscribeEvent
    public static void crit(CriticalHitEvent event) {
        if (event.getTarget() instanceof LivingEntity && (event.isVanillaCritical() || event.getResult() == net.minecraftforge.eventbus.api.Event.Result.ALLOW)) {
            critTarget = event.getTarget().getId();
            critTick = event.getEntity().level().getGameTime();
        }
    }

    // ---------------------------------------------------------------- severing

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void died(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (event.isCanceled() || !(victim.level() instanceof ServerLevel level) || !enabled(level) || !canSever(victim)) {
            return;
        }
        GoreMarks marks = MARKS.get(victim.getId());
        if (marks == null || marks.entity != victim || marks.pendingTick != level.getGameTime() || marks.pendingBlow == Blow.NONE
                || marks.severed != 0) {
            return;
        }
        float chance = switch (marks.pendingBlow) {
            case BLADE -> SEVER_CHANCE_BLADE;
            case BLAST -> SEVER_CHANCE_BLAST;
            default -> SEVER_CHANCE_HEAVY;
        };
        RandomSource random = victim.getRandom();
        if (random.nextFloat() >= chance) {
            return;
        }
        SeveredLimb.Part part = choosePart(victim, marks.pendingFrom, marks.pendingBlow, random);
        sever(level, victim, part, marks.pendingFrom);
    }

    /**
     * Which part a killing blow takes, from where it came: the side it hit (left or right arm or
     * leg), how high (a high blade can take the head; a low one the legs). Blasts take anything.
     */
    static SeveredLimb.Part choosePart(LivingEntity victim, Vec3 from, Blow blow, RandomSource random) {
        boolean right = random.nextBoolean();
        float height = 0.6f;
        if (from != null) {
            float yaw = victim.yBodyRot * Mth.DEG_TO_RAD;
            // The victim's right-hand side, in world space (yaw 0 faces +Z, the right hand is at −X).
            double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
            double side = (from.x - victim.getX()) * rx + (from.z - victim.getZ()) * rz;
            if (Math.abs(side) > 0.1) {
                right = side > 0;
            }
            height = (float) ((from.y - victim.getY()) / Math.max(0.5f, victim.getBbHeight()));
        }
        float roll = random.nextFloat();
        boolean head;
        boolean leg;
        switch (blow) {
            case BLAST -> {
                head = roll < 0.15f;
                leg = !head && random.nextBoolean();
            }
            case BLADE -> {
                head = height > 0.7f && roll < 0.25f;
                leg = !head && (height < 0.35f || random.nextFloat() < 0.3f);
            }
            default -> {
                head = height > 0.7f && roll < 0.1f;
                leg = !head && (height < 0.35f || random.nextFloat() < 0.4f);
            }
        }
        if (head) {
            return SeveredLimb.Part.HEAD;
        }
        if (leg) {
            return right ? SeveredLimb.Part.RIGHT_LEG : SeveredLimb.Part.LEFT_LEG;
        }
        return right ? SeveredLimb.Part.RIGHT_ARM : SeveredLimb.Part.LEFT_ARM;
    }

    /**
     * Cuts {@code part} off the (dying) {@code victim}: the body hides it, and a limb wearing the
     * matching armor flies off away from {@code from}, with a little blood. Returns the limb (or
     * null when gore is off).
     */
    public static SeveredLimb sever(ServerLevel level, LivingEntity victim, SeveredLimb.Part part, Vec3 from) {
        if (!enabled(level)) {
            return null;
        }
        GoreMarks marks = marksFor(victim);
        marks.severed |= part.bit();
        syncIfChanged(marks, true);

        float yaw = victim.yBodyRot * Mth.DEG_TO_RAD;
        double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw); // the victim's right
        double h = victim.getBbHeight();
        Vec3 at = switch (part) {
            case HEAD -> victim.position().add(0, h * 0.86, 0);
            case RIGHT_ARM -> victim.position().add(rx * 0.35, h * 0.6, rz * 0.35);
            case LEFT_ARM -> victim.position().add(-rx * 0.35, h * 0.6, -rz * 0.35);
            case RIGHT_LEG -> victim.position().add(rx * 0.12, h * 0.2, rz * 0.12);
            case LEFT_LEG -> victim.position().add(-rx * 0.12, h * 0.2, -rz * 0.12);
        };
        RandomSource random = victim.getRandom();
        Vec3 away = from == null ? new Vec3(random.nextGaussian(), 0, random.nextGaussian())
                : new Vec3(victim.getX() - from.x, 0, victim.getZ() - from.z);
        if (away.lengthSqr() < 1e-4) {
            away = new Vec3(rx, 0, rz);
        }
        away = away.normalize().scale(0.18 + random.nextFloat() * 0.12);
        Vec3 velocity = away.add(0, 0.28 + random.nextFloat() * 0.12, 0);

        SeveredLimb limb = SeveredLimb.create(level, victim, part, at, velocity);
        limb.setArmor(armorFor(victim, part), part == SeveredLimb.Part.RIGHT_LEG || part == SeveredLimb.Part.LEFT_LEG
                ? victim.getItemBySlot(EquipmentSlot.FEET).copy() : ItemStack.EMPTY);
        SeveredLimb.spawn(level, limb);
        blood(level, at);
        return limb;
    }

    private static ItemStack armorFor(LivingEntity victim, SeveredLimb.Part part) {
        EquipmentSlot slot = switch (part) {
            case HEAD -> EquipmentSlot.HEAD;
            case RIGHT_ARM, LEFT_ARM -> EquipmentSlot.CHEST;
            case RIGHT_LEG, LEFT_LEG -> EquipmentSlot.LEGS;
        };
        return victim.getItemBySlot(slot).copy();
    }

    /** Blood colours: dark arterial red and a brighter spray. */
    static final int BLOOD_DARK = 0x5A0A0C;
    static final int BLOOD = 0x9A1418;
    /** How many particles a severing throws (small on purpose). */
    public static final int BLOOD_PARTICLES = 10;

    /** Droplets that fall (heavy, dark) and a finer spray. */
    private static final com.noirm.vfx.VfxOptions BLOOD_DROPS = new com.noirm.vfx.VfxOptions(com.noirm.vfx.VfxKind.SHARD,
            BLOOD, BLOOD_DARK, 0.09f, 0.05f, 16, 0.06f, 0.96f);
    private static final com.noirm.vfx.VfxOptions BLOOD_SPRAY = new com.noirm.vfx.VfxOptions(com.noirm.vfx.VfxKind.SMOKE,
            BLOOD, BLOOD_DARK, 0.12f, 0.2f, 8, 0.02f, 0.9f);

    /** A small burst of blood where the cut was. */
    static void blood(ServerLevel level, Vec3 at) {
        Vfx.burst(level, BLOOD_DROPS, at, BLOOD_PARTICLES, 0.08, 0.14);
        Vfx.burst(level, BLOOD_SPRAY, at, BLOOD_PARTICLES / 2, 0.05, 0.05);
    }
}
