package com.noirm.aburame;

import com.noirm.Noirm;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.traits.Perk;
import com.noirm.traits.Perks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The colony's hooks into the game (docs/ABURAME.md, Phase A–E): the second-by-second life of every
 * colony, the implantation for Aburame players, Drained and Exhausted, Bug Bite and Cell Rot ticking,
 * the gourd bursting in explosions, hives in explosions and the morning refill, domes against blows,
 * Living Hive hits and Swarm Body, poison immunity earned by breeding, and the Kamizuru rivalry.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class ColonyEvents {
    /** Wild insects try to appear near each player this often (ticks). */
    public static final int WILD_SPAWN_TICKS = 200;
    /** Insects a Living Hive hit drops onto the target. */
    public static final int LIVING_HIVE_DROP = 200;
    /** The Kamizuru and Aburame fight on sight within this range. */
    public static final double RIVALRY_RANGE = 16;
    /** Attackers striking a dome lose this much chakra per hit. */
    public static final float DOME_CHAKRA_COST = 4f;

    private ColonyEvents() {}

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (data == null) {
            return;
        }
        ColonyData colony = data.colony();
        if (!colony.has() && Perks.get(data, Perk.COLONY) > 0f && data.identity().isAssigned()) {
            Colony.grant(player, data, Colony.nestFor(player, data)); // the implantation, off-screen at birth
        }
        if (colony.has() && !player.isSpectator()) {
            Colony.tickSecond(player, data);
        }
        everySecond(player);
        if (player.tickCount % WILD_SPAWN_TICKS == 0 && !(player instanceof FakePlayer) && player.level() instanceof ServerLevel level) {
            WildInsect.trySpawn(level, player);
        }
    }

    @SubscribeEvent
    public static void livingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || entity instanceof Player || entity.tickCount % 20 != 0) {
            return;
        }
        everySecond(entity);
        if (entity instanceof SparringNinja ninja && !(ninja instanceof InsectClone)) {
            if (NpcColony.aburame(ninja)) {
                NpcColony.tickSecond(ninja);
            }
            rivalry(ninja);
        }
    }

    private static void everySecond(LivingEntity entity) {
        if (entity.hasEffect(AburameRegistry.DRAINED.get()) || entity.hasEffect(AburameRegistry.EXHAUSTED.get())) {
            Drain.tickSecond(entity);
        }
        BugBite.tickSecond(entity);
        if (entity.hasEffect(AburameRegistry.CELL_ROT.get())) {
            RotTouch.tickSecond(entity);
        }
    }

    /** Aburame and Kamizuru fight on sight. */
    static void rivalry(SparringNinja ninja) {
        if (ninja.getTarget() != null || ninja.inCrew()) {
            return;
        }
        String mine = ninja.clanId();
        String rival = "aburame".equals(mine) ? "kamizuru" : "kamizuru".equals(mine) ? "aburame" : null;
        if (rival == null) {
            return;
        }
        ninja.level().getEntitiesOfClass(SparringNinja.class, ninja.getBoundingBox().inflate(RIVALRY_RANGE),
                n -> n.isAlive() && rival.equals(n.clanId()) && !n.inCrew() && ninja.hasLineOfSight(n)).stream().findFirst()
                .ifPresent(ninja::setTarget);
    }

    /** Coming back: swarms don't survive a logout, so insects that were out fly home. */
    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ColonyData colony = Colony.of(player);
            if (colony != null && colony.field() > 0 && Swarms.owned(player).isEmpty()) {
                int n = colony.field();
                Colony.returnHome(player, Species.KIKAICHU, n, 0f);
                colony.setField(0);
            }
        }
    }

    @SubscribeEvent
    public static void wokeUp(PlayerWakeUpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Colony.has(player)) {
            int n = InsectHiveBlockEntity.refillAfterSleep(player);
            if (n > 0) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.noirm.colony.hive_refill", n), false);
            }
        }
    }

    @SubscribeEvent
    public static void detonate(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            InsectHiveBlockEntity.onExplosion(level, event.getExplosion().getPosition(), 4f);
        }
    }

    /** Domes stop blows from outside, and the attacker pays chakra for it. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void attacked(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || event.getSource().getEntity() == null || event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
            return;
        }
        InsectSwarm dome = Swarms.domeFor(victim, event.getSource().getEntity());
        if (dome != null) {
            event.setCanceled(true);
            dome.harm(Harm.MELEE, event.getAmount());
            if (event.getSource().getEntity() instanceof LivingEntity attacker) {
                Drain.take(attacker, DOME_CHAKRA_COST);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void hurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        float amount = event.getAmount();
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity l ? l : null;
        boolean melee = attacker != null && event.getSource().getDirectEntity() == attacker;
        if (attacker != null && Drain.isExhausted(attacker)) {
            amount *= ColonyGrowth.EXHAUSTED_DAMAGE;
        }
        if (attacker instanceof InsectClone clone && melee) {
            amount = clone.punch(amount);
        }
        ColonyData colony = Colony.of(victim);
        if (colony != null) {
            if (colony.livingHive() && event.getSource().is(DamageTypeTags.IS_FIRE)) {
                amount *= 1.5f; // the Living Hive burns
            }
            if (event.getSource().is(DamageTypeTags.IS_EXPLOSION) && amount >= ColonyGrowth.GOURD_BURST_DAMAGE && victim instanceof Player player
                    && victim.getRandom().nextFloat() < ColonyGrowth.GOURD_BURST_CHANCE) {
                burstGourd(player);
                amount += ColonyGrowth.GOURD_BURST_HURT;
            }
            JutsuCaster.Casting casting = JutsuCaster.casting(victim);
            if (casting != null && "insect_cocoon".equals(casting.def().behavior())) {
                JutsuCaster.cancel(victim); // a cocoon breaks if you're hurt
            }
        }
        if (attacker != null && melee) {
            RotTouch.onMelee(attacker, victim);
            ColonyData mine = Colony.of(attacker);
            if (mine != null && mine.livingHive() && victim.isAlive()) {
                InsectSwarm s = Swarms.send(attacker, Species.KIKAICHU, LIVING_HIVE_DROP, victim.position().add(0, 1, 0), SwarmMode.CLING, 1f, 1f);
                if (s != null) {
                    s.setTarget(victim, SwarmMode.CLING);
                }
            }
        }
        event.setAmount(amount);
    }

    /** The gourd goes off: its insects die (Muta's death). */
    public static boolean burstGourd(Player player) {
        for (ItemStack s : InsectJarItem.worn(player)) {
            if (s.getItem() instanceof InsectJarItem jar && jar.isGourd() && InsectJarItem.count(s) > 0) {
                int dead = InsectJarItem.count(s);
                InsectJarItem.set(s, 0, Species.KIKAICHU);
                ColonyData c = Colony.of(player);
                if (c != null) {
                    c.addPressure(Harm.EXPLOSION, dead * ColonyGrowth.PRESSURE_PER_INSECT);
                }
                if (player.level() instanceof ServerLevel level) {
                    level.sendParticles(SwarmFx.DUST, player.getX(), player.getY() + 1.2, player.getZ(), 80, 0.6, 0.6, 0.6, 0.2);
                }
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.noirm.colony.gourd_burst", dead), false);
                Colony.changed(player);
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void death(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        if (AburameBehaviors.swarmBody(victim)) {
            event.setCanceled(true); // dissolved into the swarm, re-formed elsewhere
            return;
        }
        RotTouch.onDeath(victim);
        // A won fight with a female-insect mark (the Chūnin hive deed).
        if (event.getSource().getEntity() instanceof Player player) {
            ColonyData c = Colony.of(player);
            if (c != null && Colony.marked(player, victim)) {
                c.setMarkWin(true);
            }
        }
    }

    /** Earned poison immunity (the Antivenom strain, or rinkaichū birth). */
    @SubscribeEvent
    public static void effectApplicable(MobEffectEvent.Applicable event) {
        var effect = event.getEffectInstance().getEffect();
        if ((effect == MobEffects.POISON || effect == MobEffects.WITHER) && Colony.poisonImmune(event.getEntity())) {
            event.setResult(Event.Result.DENY);
        }
        if (effect == AburameRegistry.CELL_ROT.get() && RotTouch.host(event.getEntity())) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** At 8 rot stacks: sneak and use a blade to cut the arm off. */
    @SubscribeEvent
    public static void useItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide() && RotTouch.trySelfSever(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** A rinkaichū host cures an infected ally with a touch (sneak + use, empty hand). */
    @SubscribeEvent
    public static void touch(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (event.getLevel().isClientSide() || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
                || !(event.getTarget() instanceof LivingEntity target) || !RotTouch.host(player)) {
            return;
        }
        if (RotTouch.cure(target)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** Fire on someone burns burrowed kidaichū out (BugBite checks it too); handy for tests. */
    public static Vec3 noop() {
        return Vec3.ZERO;
    }
}
