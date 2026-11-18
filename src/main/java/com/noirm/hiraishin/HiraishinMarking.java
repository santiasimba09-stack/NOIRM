package com.noirm.hiraishin;

import com.noirm.Noirm;
import com.noirm.jutsu.JutsuContext;
import com.noirm.jutsu.JutsuEffects;
import com.noirm.network.NoirmNetwork;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.weapon.ThrownWeapon;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Writing, keeping and recalling space-time formula marks. The rules:
 * <ul>
 *   <li><b>A Flying Raijin kunai</b> thrown by someone who knows the formula carries their mark for
 *       as long as it exists (in flight, stuck in a wall, lying on the ground).</li>
 *   <li><b>A strike or a touch</b>: a landed melee blow (once you know the Flying Raijin, or with a
 *       marked kunai in hand) or a marked kunai's hit marks the enemy for {@value #BODY_TICKS} ticks.</li>
 *   <li><b>The mark key</b> (Y, or the Space-Time Formula jutsu): on the block you look at within
 *       {@value #BLOCK_REACH} blocks, or on the ally (or enemy) within touch ({@value #TOUCH_REACH}).
 *       A block mark lasts {@value #BLOCK_TICKS} ticks, 50% longer per ★ of the formula; an ally's
 *       {@value #ALLY_TICKS}.</li>
 *   <li>At most {@value #BASE_CAP} marks (+{@value #MASTER_EXTRA_MARKS} at Flying Raijin ★4, plus a
 *       sealing clan's bonus): the oldest fades first.</li>
 *   <li><b>Recall</b> (sneak + Y): every thrown marked kunai flies back to you in a flicker of yellow.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class HiraishinMarking {
    public static final int BASE_CAP = 8;
    public static final int MASTER_EXTRA_MARKS = 2;
    public static final int BODY_TICKS = 1200;
    public static final int ALLY_TICKS = 6000;
    public static final int BLOCK_TICKS = 12000;
    public static final double BLOCK_REACH = 6.0;
    public static final double TOUCH_REACH = 3.0;
    /** Writing a mark by hand costs this much chakra, and the key rests this long (ticks). */
    public static final float MARK_COST = 3f;
    public static final int MARK_REST = 6;
    /** Marks are sent to their owner's client this often (ticks), and at once when they change. */
    public static final int SYNC_TICKS = 5;

    private static final java.util.Map<Player, Long> REST = new java.util.WeakHashMap<>();

    private HiraishinMarking() {}

    public static int cap(LivingEntity owner) {
        int cap = BASE_CAP + HiraishinAffinity.of(owner).extraMarks();
        if (HiraishinLearning.stars(owner, HiraishinLearning.RAIJIN) >= 4) {
            cap += MASTER_EXTRA_MARKS;
        }
        return cap;
    }

    public static long blockTicks(LivingEntity owner) {
        return Math.round(BLOCK_TICKS * (1 + 0.5 * HiraishinLearning.stars(owner, HiraishinLearning.FORMULA)));
    }

    public static HiraishinMarks marks(LivingEntity owner) {
        return HiraishinMarks.get(owner.getServer());
    }

    /**
     * Writes a mark for {@code owner} ({@code ttl} ticks, -1 = while its carrier exists). New marks
     * (not refreshed ones) are practice for the formula. Returns the mark.
     */
    public static HiraishinMark place(LivingEntity owner, HiraishinMark.Kind kind, Vec3 pos, Direction face, Entity carrier, long ttl) {
        if (!(owner.level() instanceof ServerLevel level) || owner.getServer() == null) {
            return null;
        }
        long now = level.getGameTime();
        HiraishinMarks data = marks(owner);
        int before = data.marks(owner.getUUID()).size();
        List<HiraishinMark> faded = new ArrayList<>();
        HiraishinMark mark = data.add(owner.getUUID(), kind, (carrier != null ? carrier.level() : level).dimension(), pos, face,
                carrier == null ? null : carrier.getUUID(), now, ttl < 0 ? -1 : now + ttl, cap(owner), faded);
        boolean fresh = data.marks(owner.getUUID()).size() > before || !faded.isEmpty();
        for (HiraishinMark gone : faded) {
            HiraishinFx.flash(level, gone.pos(), gone.pos(), owner, HiraishinFx.RECALL); // the oldest mark burns out
        }
        if (owner instanceof ServerPlayer player) {
            NoirmNetwork.sendToPlayer(player, new HiraishinPackets.Fx(HiraishinFx.MARK, carrier == null ? -1 : carrier.getId(),
                    pos.x, pos.y, pos.z, pos.x, pos.y, pos.z));
            if (fresh && kind != HiraishinMark.Kind.KUNAI) {
                practice(player);
            }
        }
        level.playSound(null, pos.x, pos.y, pos.z, com.noirm.sound.NoirmSounds.HIRAISHIN_MARK.get(), SoundSource.PLAYERS, 0.6f, 1f);
        return mark;
    }

    /** One new mark: a little practice for the formula (its ★ is the road to the Flying Raijin). */
    private static void practice(ServerPlayer player) {
        ShinobiCapability.get(player).ifPresent(data -> {
            if (data.jutsu().knows(HiraishinLearning.FORMULA)) {
                data.jutsu().addXp(HiraishinLearning.FORMULA, 1);
                data.markPowersDirty();
            }
        });
    }

    /** A mark on {@code target}'s body: an ALLY mark on a friend, a BODY mark on anyone else. */
    public static HiraishinMark markEntity(LivingEntity owner, LivingEntity target) {
        boolean ally = JutsuEffects.isFriendly(owner, target);
        return place(owner, ally ? HiraishinMark.Kind.ALLY : HiraishinMark.Kind.BODY, target.position(), Direction.UP, target,
                ally ? ALLY_TICKS : BODY_TICKS);
    }

    /** A mark written on a block face. {@code at}: the exact point on the face. */
    public static HiraishinMark markBlock(LivingEntity owner, Vec3 at, Direction face) {
        return place(owner, HiraishinMark.Kind.BLOCK, at, face, null, blockTicks(owner));
    }

    // ---------------------------------------------------------------- the mark key and the formula jutsu

    /** The Space-Time Formula jutsu in a loadout slot: the same as the mark key. */
    public static void cast(JutsuContext ctx) {
        markHere(ctx.caster(), false);
    }

    static void markFromKey(ServerPlayer player) {
        if (!HiraishinLearning.knowsFormula(player)) {
            player.displayClientMessage(Component.translatable("message.noirm.hiraishin.needs_formula"), true);
            return;
        }
        long now = player.level().getGameTime();
        if (REST.getOrDefault(player, 0L) > now) {
            return;
        }
        REST.put(player, now + MARK_REST);
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (data == null || !player.getAbilities().instabuild && !data.tryConsumeChakra(MARK_COST)) {
            player.displayClientMessage(Component.translatable("message.noirm.jutsu.no_chakra"), true);
            return;
        }
        markHere(player, true);
    }

    /** Marks what {@code owner} looks at: an entity in touch reach first, else a block in reach. */
    public static HiraishinMark markHere(LivingEntity owner, boolean tell) {
        Vec3 eye = owner.getEyePosition();
        Vec3 look = owner.getViewVector(1f);
        Vec3 end = eye.add(look.scale(BLOCK_REACH));
        BlockHitResult block = owner.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, owner));
        double blockDist = block.getType() == HitResult.Type.MISS ? BLOCK_REACH : block.getLocation().distanceTo(eye);
        Vec3 touchEnd = eye.add(look.scale(Math.min(TOUCH_REACH, blockDist)));
        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(owner.level(), owner, eye, touchEnd,
                new AABB(eye, touchEnd).inflate(1.0), e -> e instanceof LivingEntity l && l.isAlive() && e != owner && !e.isSpectator(), 0.3f);
        HiraishinMark mark = null;
        if (hit != null && hit.getEntity() instanceof LivingEntity target) {
            mark = markEntity(owner, target);
            if (tell) {
                message(owner, Component.translatable(mark != null && mark.kind() == HiraishinMark.Kind.ALLY
                        ? "message.noirm.hiraishin.marked_ally" : "message.noirm.hiraishin.marked_enemy", target.getDisplayName()));
            }
        } else if (block.getType() == HitResult.Type.BLOCK) {
            mark = markBlock(owner, block.getLocation(), block.getDirection());
            if (tell) {
                message(owner, Component.translatable("message.noirm.hiraishin.marked_block"));
            }
        } else if (tell) {
            message(owner, Component.translatable("message.noirm.hiraishin.nothing_to_mark"));
        }
        return mark;
    }

    // ---------------------------------------------------------------- recall and cycle

    /** Every marked kunai {@code owner} threw comes back. Returns how many. */
    public static int recall(LivingEntity owner) {
        if (!(owner.level() instanceof ServerLevel level) || owner.getServer() == null) {
            return 0;
        }
        HiraishinMarks data = marks(owner);
        int count = 0;
        for (HiraishinMark mark : new ArrayList<>(data.marks(owner.getUUID()))) {
            if (mark.kind() != HiraishinMark.Kind.KUNAI) {
                continue;
            }
            ServerLevel where = owner.getServer().getLevel(mark.dim());
            Entity entity = where == null || mark.entity() == null ? null : where.getEntity(mark.entity());
            data.remove(owner.getUUID(), mark.id());
            if (entity instanceof ThrownWeapon kunai && !kunai.isRemoved()) {
                ItemStack stack = kunai.getItem().copy();
                HiraishinFx.flash(where, kunai.position(), kunai.position(), owner, HiraishinFx.RECALL);
                kunai.discard();
                if (owner instanceof Player player && !player.getAbilities().instabuild && !player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                count++;
            }
        }
        if (count > 0) {
            level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), com.noirm.sound.NoirmSounds.HIRAISHIN_MARK.get(), SoundSource.PLAYERS, 1f, 1.3f);
        }
        message(owner, Component.translatable("message.noirm.hiraishin.recalled", count));
        return count;
    }

    static void cycle(ServerPlayer player) {
        HiraishinMark mark = marks(player).cycle(player.getUUID());
        if (mark != null) {
            message(player, Component.translatable("message.noirm.hiraishin.selected", Component.translatable("hud.noirm.hiraishin.kind." + mark.kind().name().toLowerCase(java.util.Locale.ROOT)),
                    Math.round(Math.sqrt(mark.pos().distanceToSqr(player.position())))));
        }
    }

    // ---------------------------------------------------------------- where a mark is now

    /** Where {@code mark} is right now (a kunai or a body moves), updating the stored copy. */
    public static Vec3 resolve(MinecraftServer server, UUID owner, HiraishinMark mark) {
        Entity carrier = carrier(server, mark);
        if (carrier != null && !carrier.position().equals(mark.pos())) {
            HiraishinMarks.get(server).update(owner, mark.withPos(carrier.position()), false);
            return carrier.position();
        }
        return mark.pos();
    }

    /** The kunai or body carrying {@code mark}, if it's loaded. */
    public static Entity carrier(MinecraftServer server, HiraishinMark mark) {
        if (mark.entity() == null) {
            return null;
        }
        ServerLevel level = server.getLevel(mark.dim());
        return level == null ? null : level.getEntity(mark.entity());
    }

    // ---------------------------------------------------------------- events

    /** A marked kunai thrown by someone who knows the formula carries their mark. */
    @SubscribeEvent
    public static void kunaiThrown(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ThrownWeapon kunai) || event.loadedFromDisk()
                || !(kunai.getItem().getItem() instanceof HiraishinItems.HiraishinKunaiItem)
                || !(kunai.getOwner() instanceof LivingEntity owner) || !HiraishinLearning.knowsFormula(owner)) {
            return;
        }
        place(owner, HiraishinMark.Kind.KUNAI, kunai.position(), Direction.UP, kunai, -1);
    }

    /** A marked kunai picked up or gone: its mark goes with it (unloaded with its chunk, it stays). */
    @SubscribeEvent
    public static void kunaiGone(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ThrownWeapon kunai) || kunai.getServer() == null
                || !(kunai.getItem().getItem() instanceof HiraishinItems.HiraishinKunaiItem)) {
            return;
        }
        Entity.RemovalReason reason = kunai.getRemovalReason();
        if (reason == null || reason == Entity.RemovalReason.UNLOADED_TO_CHUNK || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER
                || reason == Entity.RemovalReason.CHANGED_DIMENSION) {
            return;
        }
        UUID id = kunai.getUUID();
        HiraishinMarks.get(kunai.getServer()).removeEverywhere(m -> id.equals(m.entity()));
    }

    /** A body that dies loses its marks. */
    @SubscribeEvent
    public static void bodyDies(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || dead.getServer() == null || dead instanceof Player) {
            return;
        }
        UUID id = dead.getUUID();
        HiraishinMarks.get(dead.getServer()).removeEverywhere(m -> id.equals(m.entity()) && m.kind().onEntity() && m.kind() != HiraishinMark.Kind.KUNAI);
    }

    /** A strike marks: a landed melee blow (formula ready) or a marked kunai's hit. */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void struck(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || event.isCanceled() || event.getAmount() <= 0) {
            return;
        }
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof ThrownWeapon kunai && kunai.getItem().getItem() instanceof HiraishinItems.HiraishinKunaiItem
                && kunai.getOwner() instanceof LivingEntity owner && owner != target && HiraishinLearning.knowsFormula(owner)) {
            markEntity(owner, target);
            return;
        }
        if (direct instanceof LivingEntity attacker && attacker == event.getSource().getEntity() && attacker != target
                && HiraishinLearning.knowsFormula(attacker) && marksByTouch(attacker)) {
            markEntity(attacker, target);
        }
    }

    /** Marking by touch is on: a marked kunai in hand, or you know the Flying Raijin itself. */
    public static boolean marksByTouch(LivingEntity attacker) {
        if (attacker.getMainHandItem().getItem() instanceof HiraishinItems.HiraishinKunaiItem) {
            return true;
        }
        return HiraishinLearning.knowsRaijin(attacker);
    }

    // ---------------------------------------------------------------- ticking: fading, following, syncing

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % SYNC_TICKS != 0) {
            return;
        }
        HiraishinMarks data = HiraishinMarks.get(server);
        long now = server.overworld().getGameTime();
        for (UUID owner : new ArrayList<>(data.owners())) {
            data.removeIf(owner, m -> m.expires() >= 0 && now >= m.expires());
            for (HiraishinMark mark : data.marks(owner)) {
                if (mark.kind().onEntity()) {
                    Entity carrier = carrier(server, mark);
                    if (carrier != null && carrier.position().distanceToSqr(mark.pos()) > 0.01) {
                        data.update(owner, mark.withPos(carrier.position()), mark.kind() == HiraishinMark.Kind.KUNAI);
                    }
                }
            }
        }
        for (UUID owner : new ArrayList<>(data.changed)) {
            ServerPlayer player = server.getPlayerList().getPlayer(owner);
            if (player != null) {
                sync(player);
            }
            data.changed.remove(owner);
        }
    }

    /** Sends {@code player} their marks (for the glyphs and the HUD). */
    public static void sync(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        HiraishinMarks data = HiraishinMarks.get(server);
        long now = server.overworld().getGameTime();
        List<HiraishinPackets.MarkInfo> infos = new ArrayList<>();
        for (HiraishinMark mark : data.marks(player.getUUID())) {
            if (!mark.dim().equals(player.level().dimension())) {
                continue;
            }
            Entity carrier = carrier(server, mark);
            infos.add(new HiraishinPackets.MarkInfo(mark.id(), mark.kind().ordinal(), mark.pos().x, mark.pos().y, mark.pos().z,
                    mark.face().get3DDataValue(), carrier == null ? -1 : carrier.getId(),
                    mark.expires() < 0 ? -1 : (int) Math.max(0, Math.min(Integer.MAX_VALUE, mark.expires() - now))));
        }
        HiraishinMark selected = data.selected(player.getUUID());
        NoirmNetwork.sendToPlayer(player, new HiraishinPackets.Marks(infos, selected == null ? -1 : selected.id(), cap(player),
                HiraishinLearning.knowsRaijin(player)));
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    private static void message(LivingEntity to, Component text) {
        if (to instanceof Player player) {
            player.displayClientMessage(text, true);
        }
    }
}
