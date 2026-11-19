package com.noirm.hiraishin;

import com.noirm.Noirm;
import com.noirm.combat.AttackInput;
import com.noirm.combat.CombatSystem;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.weapon.Throwing;
import com.noirm.weapon.ThrownWeapon;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The few NPC legends who know the Flying Raijin, and how they fight with it. They're meant to be
 * frightening:
 * <ul>
 *   <li>at the start of a fight they write a mark where they stand (their way out);</li>
 *   <li><b>the Thunder God Slash:</b> a marked kunai thrown past you, and before it lands they're behind
 *       you, already swinging; at full strength they chain it, kunai after kunai;</li>
 *   <li>every blow they land marks you, and if you run they're simply behind you again;</li>
 *   <li>a projectile thrown at them is sent somewhere else (often back at you);</li>
 *   <li>hurt and pressed, they vanish to their mark.</li>
 * </ul>
 * Their wait between moves is {@value #THINK_MIN}–{@value #THINK_MAX} ticks; a Sharingan or a fast
 * enough eye still reads them ({@link HiraishinJump#reads}).
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class HiraishinNpc {
    public static final int THINK_MIN = 35;
    public static final int THINK_MAX = 70;
    /** A thrown kunai is jumped to when it's this close to the target (or after {@value #KUNAI_WAIT} ticks). */
    public static final double CATCH_DISTANCE = 2.6;
    public static final int KUNAI_WAIT = 9;
    public static final float ESCAPE_HEALTH = 0.35f;
    public static final float CHAIN_CHANCE = 0.55f;
    public static final int REDIRECT_REST = 40;

    private static final class Brain {
        long nextThink;
        long nextRedirect;
        ThrownWeapon kunai;
        long kunaiThrown;
        int chain;
        boolean homeMarked;
    }

    private static final Map<SparringNinja, Brain> BRAINS = new WeakHashMap<>();

    private HiraishinNpc() {}

    @SubscribeEvent
    public static void livingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof SparringNinja ninja) || ninja.level().isClientSide() || ninja.tickCount % 2 != 0
                || !HiraishinLearning.knowsRaijin(ninja) || !ninja.isAlive()) {
            return;
        }
        LivingEntity target = ninja.getTarget();
        Brain brain = BRAINS.computeIfAbsent(ninja, n -> new Brain());
        if (target == null || !target.isAlive()) {
            brain.homeMarked = false;
            brain.kunai = null;
            brain.chain = 0;
            return;
        }
        think(ninja, target, brain);
    }

    /** One step of a legend's Flying Raijin. */
    private static void think(SparringNinja ninja, LivingEntity target, Brain brain) {
        ServerLevel level = (ServerLevel) ninja.level();
        long now = level.getGameTime();
        if (!brain.homeMarked && ninja.onGround()) {
            brain.homeMarked = true;
            HiraishinMarking.place(ninja, HiraishinMark.Kind.BLOCK, ninja.position(), Direction.UP, null, 2400);
        }
        // Guiding Thunder: something is about to hit them.
        if (now >= brain.nextRedirect && HiraishinJump.incomingProjectile(ninja) != null && pay(ninja)) {
            if (HiraishinJump.redirect(ninja) != null) {
                brain.nextRedirect = now + REDIRECT_REST;
            }
        }
        // A kunai in the air: the moment it's beside the target, they're there.
        if (brain.kunai != null) {
            boolean close = brain.kunai.position().distanceTo(target.position().add(0, target.getBbHeight() * 0.5, 0)) <= CATCH_DISTANCE;
            if (brain.kunai.isRemoved() || close || brain.kunai.isStuck() || now - brain.kunaiThrown >= KUNAI_WAIT) {
                HiraishinMark mark = markOf(ninja, brain.kunai);
                brain.kunai = null;
                if (mark != null && pay(ninja)) {
                    HiraishinJump.Result result = HiraishinJump.jumpTo(ninja, mark);
                    if (result.ok() && result.slashTarget() != null) {
                        CombatSystem.requestAttack(ninja, AttackInput.HEAVY); // the slash, already moving
                        if (brain.chain < maxChain(ninja) && ninja.getRandom().nextFloat() < CHAIN_CHANCE) {
                            brain.chain++;
                            brain.nextThink = now + 8; // and again
                            return;
                        }
                    }
                }
                brain.chain = 0;
                brain.nextThink = now + THINK_MIN + ninja.getRandom().nextInt(THINK_MAX - THINK_MIN);
            }
            return;
        }
        if (now < brain.nextThink || !CombatSystem.canAct(ninja)) {
            return;
        }
        brain.nextThink = now + THINK_MIN + ninja.getRandom().nextInt(THINK_MAX - THINK_MIN);
        double distance = ninja.distanceTo(target);
        // Hurt and pressed: gone, to their mark.
        if (ninja.getHealth() < ninja.getMaxHealth() * ESCAPE_HEALTH && distance < 4) {
            HiraishinMark home = farthestMark(ninja, target);
            if (home != null && pay(ninja)) {
                HiraishinJump.jumpTo(ninja, home);
                return;
            }
        }
        // You ran: they're behind you (a mark from an earlier blow).
        if (distance > 7) {
            HiraishinMark onTarget = bodyMark(ninja, target);
            if (onTarget != null && pay(ninja)) {
                HiraishinJump.Result result = HiraishinJump.jumpTo(ninja, onTarget);
                if (result.ok()) {
                    CombatSystem.requestAttack(ninja, AttackInput.LIGHT);
                }
                return;
            }
        }
        // The Thunder God Slash: a marked kunai past them.
        if (distance >= 3.5 && distance <= 24 && ninja.hasLineOfSight(target)) {
            ThrownWeapon kunai = Throwing.npcThrow(ninja, target, new ItemStack(HiraishinItems.KUNAI.get()), false);
            if (kunai != null) {
                brain.kunai = kunai;
                brain.kunaiThrown = now;
            }
        }
    }

    /** Runs one decision now (tests). */
    public static void thinkNow(SparringNinja ninja, LivingEntity target) {
        Brain brain = BRAINS.computeIfAbsent(ninja, n -> new Brain());
        brain.nextThink = 0;
        think(ninja, target, brain);
    }

    private static int maxChain(SparringNinja ninja) {
        return HiraishinLearning.stars(ninja, HiraishinLearning.RAIJIN) >= HiraishinJump.CHAIN_STARS ? 3 : 1;
    }

    /** Pays a jump's chakra (NPC legends have deep reserves, but not endless ones). */
    private static boolean pay(SparringNinja ninja) {
        JutsuDefinition def = JutsuData.get(HiraishinLearning.RAIJIN);
        return def != null && ninja.tryUseChakra(JutsuCaster.cost(ninja, HiraishinLearning.RAIJIN, def));
    }

    private static HiraishinMark markOf(SparringNinja ninja, ThrownWeapon kunai) {
        for (HiraishinMark mark : HiraishinMarks.get(ninja.getServer()).marks(ninja.getUUID())) {
            if (kunai.getUUID().equals(mark.entity())) {
                return mark;
            }
        }
        return null;
    }

    private static HiraishinMark bodyMark(SparringNinja ninja, LivingEntity target) {
        for (HiraishinMark mark : HiraishinMarks.get(ninja.getServer()).marks(ninja.getUUID())) {
            if (mark.kind() == HiraishinMark.Kind.BODY && target.getUUID().equals(mark.entity())) {
                return mark;
            }
        }
        return null;
    }

    private static HiraishinMark farthestMark(SparringNinja ninja, LivingEntity from) {
        HiraishinMark best = null;
        double bestDistance = 6;
        for (HiraishinMark mark : HiraishinMarks.get(ninja.getServer()).marks(ninja.getUUID())) {
            if (mark.kind() == HiraishinMark.Kind.BODY || !mark.dim().equals(ninja.level().dimension())) {
                continue;
            }
            double d = mark.pos().distanceTo(from.position());
            if (d > bestDistance && d < 64) {
                best = mark;
                bestDistance = d;
            }
        }
        return best;
    }

    /** True if the legend can jump right now (tests). */
    public static boolean ready(SparringNinja ninja) {
        Brain brain = BRAINS.get(ninja);
        return brain == null || brain.kunai == null;
    }
}
