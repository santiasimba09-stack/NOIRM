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
 * Gore, visual only (blueprint §7). Nothing here changes the fight: no bleeding damage, no
 * slowing, no lost limbs on the living.
 * <ul>
 *   <li><b>Marks:</b> bruises, dirt, soot, cuts and blood build up on players and ninjas as they
 *   take damage, by the kind of damage (fists bruise, blades cut and bleed, big hits splatter
 *   blood, fire and blasts blacken, falls dirty). Blood is fresh red after a hit and dries dark
 *   over {@link #FRESH_SECONDS}. Marks fade slowly, faster as health comes back, and water and
 *   rain wash off dirt, soot and blood. Synced only when one moves by a visible step.</li>
 *   <li><b>Blood on the ground:</b> bleeding hits fling drops that splat where they land, fresh
 *   wounds drip, the badly hurt leave a trail as they move, bodies leave a pool, severed parts
 *   spurt. The server only sends particles ({@link GoreParticles}); the stains are the client's
 *   ({@link BloodSplats}).</li>
 *   <li><b>Severed limbs:</b> a killing blow that is a blade, a crit, a blast or a heavy hit can
 *   cut off an arm, a leg or the head (by where the hit came from). The part flies off as a
 *   {@link SeveredLimb}, lies there 15 s, then burns away in blue chakra flame into ash.</li>
 * </ul>
 * The {@code noirmGore} game rule (default on) turns all of it off; each player can turn it down
 * on their own screen ({@link GoreSettings}).
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class Gore {
    // ---------------------------------------------------------------- tuning

    /** Marks gained per hit: this × damage ÷ max health (so losing a third of your health = full marks). */
    public static final float GAIN_PER_HEALTH = 3.0f;
    /** Even a scratch leaves a little. */
    public static final float MIN_GAIN = 0.05f;
    /** Marks fade by this much per second on their own (a full mark takes about 6 minutes). */
    public static final float FADE_PER_SECOND = 0.0028f;
    /** …and by this × each share of max health healed (healing all your health clears a third). */
    public static final float HEAL_FADE = 0.35f;
    /** How fast each kind fades compared to the others: bruise, dirt, soot, cut, blood. */
    static final float[] FADE_RATE = {1.0f, 1.4f, 1.2f, 0.7f, 0.6f};
    /** Dirt, soot and blood wash off this many times faster in water, and in the rain. */
    public static final float WASH_WATER = 10f;
    public static final float WASH_RAIN = 3f;
    /** The fade runs every this many ticks. */
    public static final int FADE_INTERVAL = 10;
    /** A change is sent to clients once a mark moved by this many 1/255 steps (or reached zero). */
    public static final int SYNC_STEP = 8;
    /** Fresh (bright, wet) blood dries dark over this many seconds. */
    public static final float FRESH_SECONDS = 45f;

    /** Drops a bleeding hit flings: this × the blood it adds, up to {@link #SPLATTER_MAX}. */
    public static final float SPLATTER_PER_BLOOD = 40f;
    public static final int SPLATTER_MAX = 16;
    /** Every this many ticks, fresh wounds may drip and the badly hurt leave a trail. */
    public static final int BLEED_INTERVAL = 5;
    /** Chance per check that a wound drips: freshness × blood × this. */
    public static final float DRIP_CHANCE = 0.6f;
    /** Below this share of health, someone who has bled leaves a trail of drops as they move. */
    public static final float TRAIL_HEALTH = 0.4f;
    /** A severed body spurts from the stump for this many ticks (it falls for 20). */
    public static final int SPURT_TICKS = 18;
    /** Pools under bodies: a base size (blocks, half-width), more per blood level, more if severed. */
    public static final float POOL_BODY = 0.35f;
    public static final float POOL_PER_BLOOD = 0.45f;
    public static final float POOL_SEVERED = 0.35f;

    /** A hit at least this big (or this share of max health) counts as heavy. */
    public static final float HEAVY_DAMAGE = 8f;
    public static final float HEAVY_SHARE = 0.4f;
    /** Chance a killing blow severs a limb, by kind: blade or crit, blast, heavy blunt hit. */
    public static final float SEVER_CHANCE_BLADE = 1.0f;
    public static final float SEVER_CHANCE_BLAST = 0.9f;
    public static final float SEVER_CHANCE_HEAVY = 0.85f;

    public static GameRules.Key<GameRules.BooleanValue> GORE;

    /** The kinds of marks (their order is the order on the wire and in the textures). */
    public enum Mark { BRUISE, DIRT, SOOT, CUT, BLOOD }

    /** What a killing blow was, for severing. */
    enum Blow { NONE, HEAVY, BLAST, BLADE }

    private static final int BLOOD = Mark.BLOOD.ordinal();
    private static final Int2ObjectOpenHashMap<GoreMarks> MARKS = new Int2ObjectOpenHashMap<>();
    /** The last vanilla critical hit: who was hit and when (a crit that kills cuts clean). */
    private static int critTarget = -1;
    private static long critTick = -1;
    /** Blood added by the hit being handled (the wanted amount, even past full). */
    private static float bloodAdded;
    /** Blood particles sent so far (drops and pools), for tests. */
    private static long bloodSent;

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

    /** How fresh this fighter's blood is: 1 just after a bleeding hit, 0 dried. */
    public static float freshness(LivingEntity entity) {
        GoreMarks marks = MARKS.get(entity.getId());
        return marks == null || marks.entity != entity ? 0f : marks.fresh;
    }

    /** Severed parts of this (dying) body, as a {@link SeveredLimb.Part} bit mask. */
    public static int severed(LivingEntity entity) {
        GoreMarks marks = MARKS.get(entity.getId());
        return marks == null || marks.entity != entity ? 0 : marks.severed;
    }

    /** Blood particles (drops and pools) the server has sent since it started; for tests. */
    public static long bloodSent() {
        return bloodSent;
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
        if (mark == Mark.BLOOD) {
            marks.fresh = 1f;
        }
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
        boolean heavy = CombatSystem.isApplyingHeavyHit() || amount >= HEAVY_DAMAGE || amount >= HEAVY_SHARE * target.getMaxHealth();
        bloodAdded = 0f;
        Blow blow = addFor(marks, target, source, gain, heavy);
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
            add(marks, Mark.BLOOD, gain * 0.5f);
        }
        marks.pendingBlow = blow;
        marks.pendingTick = level.getGameTime();
        marks.pendingFrom = source.getSourcePosition();
        if (source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker) {
            marks.pendingFrom = attacker.getEyePosition(); // a melee hit: where the swing came from
        }
        if (bloodAdded > 0f) {
            marks.fresh = 1f;
            int drops = Math.min(SPLATTER_MAX, Math.round(bloodAdded * SPLATTER_PER_BLOOD) + (heavy ? 3 : 0));
            if (drops > 0) {
                splatter(level, target, marks.pendingFrom, drops);
            }
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
    private static Blow addFor(GoreMarks marks, LivingEntity target, DamageSource source, float gain, boolean heavy) {
        Entity direct = source.getDirectEntity();
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            add(marks, Mark.SOOT, gain);
            add(marks, Mark.DIRT, gain * 0.5f);
            add(marks, Mark.BRUISE, gain * 0.3f);
            add(marks, Mark.BLOOD, gain * 0.6f);
            return Blow.BLAST;
        }
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.LIGHTNING_BOLT)) {
            add(marks, Mark.SOOT, gain);
            return Blow.NONE;
        }
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FLY_INTO_WALL)) {
            add(marks, Mark.DIRT, gain);
            add(marks, Mark.BRUISE, gain * 0.3f);
            add(marks, Mark.BLOOD, gain * (heavy ? 0.3f : 0.1f)); // scraped
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
                        add(marks, Mark.BLOOD, gain * 0.5f);
                        return Blow.BLAST;
                    }
                    case LIGHTNING, STORM -> {
                        add(marks, Mark.SOOT, gain * 0.6f);
                        add(marks, Mark.BRUISE, gain * 0.5f);
                        add(marks, Mark.BLOOD, gain * 0.25f);
                        return Blow.HEAVY;
                    }
                    case EARTH, DUST -> {
                        add(marks, Mark.DIRT, gain);
                        add(marks, Mark.BRUISE, gain * 0.5f);
                        add(marks, Mark.BLOOD, gain * 0.4f);
                        return Blow.HEAVY;
                    }
                    case WIND -> {
                        add(marks, Mark.CUT, gain);
                        add(marks, Mark.BLOOD, gain);
                        return Blow.BLADE;
                    }
                    default -> {
                    }
                }
            }
            add(marks, Mark.BRUISE, gain);
            add(marks, Mark.BLOOD, gain * 0.35f);
            return Blow.HEAVY;
        }
        if (direct instanceof ThrownWeapon || direct instanceof AbstractArrow || source.is(DamageTypes.THORNS)
                || source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH)) {
            add(marks, Mark.CUT, gain);
            add(marks, Mark.BRUISE, gain * 0.15f);
            add(marks, Mark.BLOOD, gain * (direct instanceof ThrownWeapon || direct instanceof AbstractArrow ? 0.8f : 0.3f));
            return direct instanceof ThrownWeapon ? Blow.BLADE : Blow.NONE;
        }
        if (direct instanceof LivingEntity attacker && isBlade(attacker.getMainHandItem())) {
            add(marks, Mark.CUT, gain);
            add(marks, Mark.BRUISE, gain * 0.2f);
            add(marks, Mark.BLOOD, gain * 1.1f);
            return Blow.BLADE;
        }
        add(marks, Mark.BRUISE, gain);
        add(marks, Mark.BLOOD, gain * (heavy ? 0.6f : 0.2f)); // split lips, broken noses
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
        return moveset != null && moveset.cuts();
    }

    private static void add(GoreMarks marks, Mark mark, float amount) {
        marks.level[mark.ordinal()] = Math.min(1f, marks.level[mark.ordinal()] + amount);
        if (mark == Mark.BLOOD) {
            bloodAdded += amount;
        }
    }

    /** Sends the marks to everyone who sees this fighter once one moved by a visible step. */
    private static void syncIfChanged(GoreMarks marks, boolean force) {
        boolean changed = force;
        for (int i = 0; i < GoreMarks.KINDS; i++) {
            int q = Math.round(marks.level[i] * 255f);
            if (Math.abs(q - marks.sent[i]) >= SYNC_STEP || (q == 0) != (marks.sent[i] == 0)) {
                changed = true;
            }
        }
        int fresh = Math.round(marks.fresh * 255f);
        if (Math.abs(fresh - marks.sentFresh) >= SYNC_STEP * 2 || (fresh == 0) != (marks.sentFresh == 0)) {
            changed = true;
        }
        if (!changed) {
            return;
        }
        for (int i = 0; i < GoreMarks.KINDS; i++) {
            marks.sent[i] = Math.round(marks.level[i] * 255f);
        }
        marks.sentFresh = fresh;
        NoirmNetwork.sendToTrackingAndSelf(marks.entity, GorePackets.Sync.of(marks.entity.getId(), marks.sent, marks.packedBlood(), marks.severed));
    }

    /**
     * Every tick: fading (every {@link #FADE_INTERVAL}), dripping wounds and trails (every
     * {@link #BLEED_INTERVAL}), and stumps spurting on severed bodies (every other tick).
     */
    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || MARKS.isEmpty()) {
            return;
        }
        int tick = event.getServer().getTickCount();
        boolean fade = tick % FADE_INTERVAL == 0;
        boolean bleed = tick % BLEED_INTERVAL == 0;
        boolean spurt = (tick & 1) == 0;
        if (!fade && !bleed && !spurt) {
            return;
        }
        float timeFade = FADE_PER_SECOND * FADE_INTERVAL / 20f;
        float dry = FADE_INTERVAL / (FRESH_SECONDS * 20f);
        ObjectIterator<GoreMarks> it = MARKS.values().iterator();
        while (it.hasNext()) {
            GoreMarks marks = it.next();
            LivingEntity entity = marks.entity;
            if (entity.isRemoved()) {
                it.remove();
                continue;
            }
            if (!entity.isAlive()) {
                // The dying body keeps its marks (and missing parts) until it's gone; stumps spurt.
                if (spurt && marks.severed != 0 && entity.deathTime < SPURT_TICKS && entity.level() instanceof ServerLevel level
                        && enabled(level)) {
                    spurt(level, entity, marks.severed);
                }
                continue;
            }
            if (bleed && entity.level() instanceof ServerLevel level && enabled(level)) {
                bleed(level, marks);
            }
            if (!fade) {
                continue;
            }
            float health = entity.getHealth();
            float healed = Math.max(0f, health - marks.lastHealth) / Math.max(1f, entity.getMaxHealth());
            marks.lastHealth = health;
            float wash = entity.isInWater() ? WASH_WATER : entity.isInWaterOrRain() ? WASH_RAIN : 1f;
            float amount = timeFade + healed * HEAL_FADE;
            boolean any = false;
            for (int i = 0; i < GoreMarks.KINDS; i++) {
                boolean washes = i == Mark.DIRT.ordinal() || i == Mark.SOOT.ordinal() || i == BLOOD;
                marks.level[i] = Math.max(0f, marks.level[i] - amount * FADE_RATE[i] * (washes ? wash : 1f));
                any |= marks.level[i] > 0f;
            }
            marks.fresh = marks.level[BLOOD] <= 0f ? 0f : Math.max(0f, marks.fresh - dry);
            syncIfChanged(marks, false);
            if (!any && marks.severed == 0) {
                it.remove();
            }
        }
    }

    /** Fresh wounds drip now and then; the badly hurt who have bled leave a trail as they move. */
    private static void bleed(ServerLevel level, GoreMarks marks) {
        LivingEntity entity = marks.entity;
        double dx = entity.getX() - marks.lastX, dz = entity.getZ() - marks.lastZ;
        marks.lastX = entity.getX();
        marks.lastZ = entity.getZ();
        float blood = marks.level[BLOOD];
        if (blood <= 0f) {
            return;
        }
        boolean moving = dx * dx + dz * dz > 0.15 * 0.15;
        float share = entity.getHealth() / Math.max(1f, entity.getMaxHealth());
        float chance = marks.fresh * blood * DRIP_CHANCE;
        if (share < TRAIL_HEALTH) {
            chance = Math.max(chance, moving ? 1f : 0.25f);
        }
        if (entity.getRandom().nextFloat() >= chance) {
            return;
        }
        int drops = share < TRAIL_HEALTH / 2 && moving ? 2 : 1;
        level.sendParticles(GoreParticles.BLOOD_DROP.get(), entity.getX(), entity.getY() + entity.getBbHeight() * 0.45, entity.getZ(),
                drops, 0.12, 0.08, 0.12, 0.01);
        bloodSent += drops;
    }

    /** Drops flung from a bleeding hit: from the chest, mostly away from where the hit came from. */
    private static void splatter(ServerLevel level, LivingEntity target, Vec3 from, int drops) {
        double x = target.getX(), y = target.getY() + target.getBbHeight() * 0.62, z = target.getZ();
        if (from != null) {
            double ax = x - from.x, az = z - from.z;
            double len = Math.sqrt(ax * ax + az * az);
            if (len > 1e-3) {
                x += ax / len * 0.3; // the far side: blood sprays out the way the blow went
                z += az / len * 0.3;
            }
        }
        level.sendParticles(GoreParticles.BLOOD_DROP.get(), x, y, z, drops, 0.15, 0.12, 0.15, 0.17);
        bloodSent += drops;
    }

    /** A pool of {@code size} (half-width in blocks) spreading on the ground at {@code at}. */
    public static void pool(ServerLevel level, Vec3 at, float size) {
        level.sendParticles(GoreParticles.BLOOD_POOL.get(), at.x, at.y + 0.2, at.z, 0, size, 0, 0, 1);
        bloodSent++;
    }

    /** Blood pumping from each stump of a severed body. */
    private static void spurt(ServerLevel level, LivingEntity body, int severed) {
        for (SeveredLimb.Part part : SeveredLimb.Part.values()) {
            if ((severed & part.bit()) != 0) {
                Vec3 at = cutPoint(body, part);
                level.sendParticles(GoreParticles.BLOOD_DROP.get(), at.x, at.y, at.z, 3, 0.04, 0.04, 0.04, 0.13);
                bloodSent += 3;
            }
        }
    }

    /** Clears every mark on this fighter (and tells the clients). */
    public static void clear(LivingEntity entity) {
        GoreMarks marks = MARKS.remove(entity.getId());
        if (marks != null || entity instanceof ServerPlayer) {
            NoirmNetwork.sendToTrackingAndSelf(entity, GorePackets.Sync.of(entity.getId(), GoreMarks.ZERO, 0, 0));
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
                NoirmNetwork.sendToPlayer(viewer, GorePackets.Sync.of(living.getId(), marks.sent, marks.packedBlood(), marks.severed));
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

    // ---------------------------------------------------------------- severing and death

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void died(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (event.isCanceled() || !(victim.level() instanceof ServerLevel level) || !enabled(level) || !marksOn(victim)) {
            return;
        }
        GoreMarks marks = MARKS.get(victim.getId());
        if (marks == null || marks.entity != victim) {
            return; // never hurt in a way that marks (a /kill, the void)
        }
        if (canSever(victim) && marks.pendingTick == level.getGameTime() && marks.pendingBlow != Blow.NONE && marks.severed == 0) {
            float chance = switch (marks.pendingBlow) {
                case BLADE -> SEVER_CHANCE_BLADE;
                case BLAST -> SEVER_CHANCE_BLAST;
                default -> SEVER_CHANCE_HEAVY;
            };
            RandomSource random = victim.getRandom();
            if (random.nextFloat() < chance) {
                sever(level, victim, choosePart(victim, marks.pendingFrom, marks.pendingBlow, random), marks.pendingFrom);
            }
        }
        // The body bleeds out where it falls.
        float blood = marks.level[BLOOD];
        if (blood > 0.02f || marks.severed != 0) {
            pool(level, victim.position(), POOL_BODY + POOL_PER_BLOOD * blood + (marks.severed != 0 ? POOL_SEVERED : 0f));
        }
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
                head = height > 0.7f && roll < 0.15f;
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

    /** Where {@code part} joins the body (the neck, a shoulder, a hip), in the world. */
    static Vec3 cutPoint(LivingEntity body, SeveredLimb.Part part) {
        float yaw = body.yBodyRot * Mth.DEG_TO_RAD;
        double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw); // the body's right
        double h = body.getBbHeight();
        return switch (part) {
            case HEAD -> body.position().add(0, h * 0.86, 0);
            case RIGHT_ARM -> body.position().add(rx * 0.35, h * 0.6, rz * 0.35);
            case LEFT_ARM -> body.position().add(-rx * 0.35, h * 0.6, -rz * 0.35);
            case RIGHT_LEG -> body.position().add(rx * 0.12, h * 0.2, rz * 0.12);
            case LEFT_LEG -> body.position().add(-rx * 0.12, h * 0.2, -rz * 0.12);
        };
    }

    /**
     * Cuts {@code part} off the (dying) {@code victim}: the body hides it, and a limb wearing the
     * matching armor flies off away from {@code from}, in a burst of blood. Returns the limb (or
     * null when gore is off).
     */
    public static SeveredLimb sever(ServerLevel level, LivingEntity victim, SeveredLimb.Part part, Vec3 from) {
        if (!enabled(level)) {
            return null;
        }
        GoreMarks marks = marksFor(victim);
        marks.severed |= part.bit();
        marks.level[BLOOD] = Math.min(1f, marks.level[BLOOD] + 0.5f); // drenched
        marks.fresh = 1f;
        syncIfChanged(marks, true);

        float yaw = victim.yBodyRot * Mth.DEG_TO_RAD;
        double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw); // the victim's right
        Vec3 at = cutPoint(victim, part);
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
    static final int BLOOD_RED = 0x9A1418;
    /** How many drops a severing throws (each leaves a splat where it lands). */
    public static final int BLOOD_PARTICLES = 36;

    /** A fine red mist with the drops. */
    private static final com.noirm.vfx.VfxOptions BLOOD_SPRAY = new com.noirm.vfx.VfxOptions(com.noirm.vfx.VfxKind.SMOKE,
            BLOOD_RED, BLOOD_DARK, 0.14f, 0.25f, 10, 0.02f, 0.9f);

    /** A burst of blood where the cut was. */
    static void blood(ServerLevel level, Vec3 at) {
        level.sendParticles(GoreParticles.BLOOD_DROP.get(), at.x, at.y, at.z, BLOOD_PARTICLES, 0.1, 0.1, 0.1, 0.2);
        bloodSent += BLOOD_PARTICLES;
        Vfx.burst(level, BLOOD_SPRAY, at, 8, 0.08, 0.06);
    }
}
