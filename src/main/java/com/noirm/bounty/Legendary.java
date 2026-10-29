package com.noirm.bounty;

import com.noirm.Noirm;
import com.noirm.clan.Nature;
import com.noirm.dojutsu.MangekyoNpcs;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.NpcJutsuSets;
import com.noirm.network.NoirmNetwork;
import com.noirm.network.VfxPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Legendary ninjas: a few per world ({@link BingoBook#LEGEND_COUNT}), listed in the bingo book like
 * the other rogues but on a gold page. In the world they are unmistakable:
 * <ul>
 *   <li>a <b>persistent aura</b> by their {@link Kind}: dark smoke, crackling lightning, burning
 *       embers, a red Mangekyō glow, or a beast's red chakra;</li>
 *   <li><b>heavy footsteps</b>, and the ground <b>cracks</b> where they land;</li>
 *   <li>an <b>entrance</b>: their name and epithet across your screen the first time you meet them;</li>
 *   <li>a <b>boss bar</b> with their name while you're close;</li>
 *   <li>top-tier stats (class S+), a <b>signature jutsu</b>, and their gift: a Mangekyō or a tailed beast.</li>
 * </ul>
 * The flag lives in the ninja's Forge persistent data ({@value #TAG}), so a legend who is beaten into
 * yielding and recruited keeps their aura. Once claimed, a legend's page never brings them back.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class Legendary {
    public static final String TAG = "NoirmLegend";
    /** Aura particles every this many ticks. */
    public static final int AURA_TICKS = 3;
    /** A landing from this high (blocks) cracks the ground. */
    public static final float CRACK_FALL = 1.5f;
    /** A heavy step every this many ticks while walking. */
    public static final int STEP_TICKS = 9;
    /** The entrance shows to players this close who can see them. */
    public static final double ENTRANCE_RANGE = 24;
    /** The boss bar shows to players this close. */
    public static final double BAR_RANGE = 40;

    /** What makes a legend: their aura, gift and signature. */
    public enum Kind {
        /** A red Mangekyō glow and dark smoke; carries the Mangekyō. */
        MANGEKYO(Nature.FIRE, new String[] {"red_eyed_ghost", "crimson_moon", "weeping_eye"}),
        /** A beast's red chakra; a jinchūriki of many tails. */
        BEAST(null, new String[] {"caged_storm", "howling_vessel", "nine_hollows"}),
        /** Crackling lightning. */
        LIGHTNING(Nature.LIGHTNING, new String[] {"black_lightning", "silent_thunder", "storm_crow"}),
        /** Burning embers. */
        FIRE(Nature.FIRE, new String[] {"burning_widow", "smiling_ember", "ash_king"}),
        /** Dark smoke that clings to them. */
        SHADOW(Nature.WIND, new String[] {"smoke_wraith", "ashen_shadow", "hollow_sun"});

        public final Nature nature;
        public final String[] epithets;

        Kind(Nature nature, String[] epithets) {
            this.nature = nature;
            this.epithets = epithets;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Per legend (transient): last on the ground, the highest point of a fall, steps, the boss bar. */
    private static final class State {
        boolean wasOnGround = true;
        double fallFrom;
        ServerBossEvent bar;
    }

    private static final Map<SparringNinja, State> STATES = new WeakHashMap<>();

    private Legendary() {}

    // ------------------------------------------------------------ the flag

    public static boolean isLegendary(LivingEntity entity) {
        return entity instanceof SparringNinja && entity.getPersistentData().contains(TAG);
    }

    public static Kind kindOf(LivingEntity entity) {
        if (!isLegendary(entity)) {
            return null;
        }
        try {
            return Kind.valueOf(entity.getPersistentData().getCompound(TAG).getString("Kind"));
        } catch (IllegalArgumentException e) {
            return Kind.SHADOW;
        }
    }

    /** The bingo page this legend comes from (-1 = none). */
    public static int entryOf(LivingEntity entity) {
        return isLegendary(entity) ? entity.getPersistentData().getCompound(TAG).getInt("Entry") : -1;
    }

    /**
     * Makes {@code ninja} the legend of page {@code entry}: the flag, their gift (Mangekyō or beast,
     * already set from the page), and their signature jutsu. Call after their clan is set.
     */
    public static void apply(SparringNinja ninja, BingoEntry entry) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Kind", entry.kind.name());
        tag.putInt("Entry", entry.id);
        ninja.getPersistentData().put(TAG, tag);
        for (ResourceLocation id : signatures(entry.kind, ninja.natures().keySet(), entry.seed)) {
            ninja.teach(id);
        }
    }

    /**
     * Their signature jutsu: the two highest-ranked jutsu of their kind's nature (or their own first
     * nature), chosen the same way every time.
     */
    public static List<ResourceLocation> signatures(Kind kind, java.util.Set<Nature> natures, long seed) {
        Nature nature = kind.nature != null ? kind.nature : natures.stream().filter(Nature::isElemental)
                .min(Comparator.comparing(Nature::getSerializedName)).orElse(null);
        List<ResourceLocation> out = new ArrayList<>();
        if (nature == null) {
            return out;
        }
        List<ResourceLocation> pool = new ArrayList<>(JutsuData.allFor(nature));
        pool.removeIf(id -> JutsuData.get(id) == null);
        pool.sort(Comparator.<ResourceLocation>comparingInt(id -> -NpcJutsuSets.rankIndex(JutsuData.get(id).rank()))
                .thenComparing(ResourceLocation::toString));
        RandomSource random = RandomSource.create(seed);
        int top = Math.min(pool.size(), 4);
        for (int i = 0; i < 2 && top > 0; i++) {
            ResourceLocation pick = pool.get(random.nextInt(top));
            if (!out.contains(pick)) {
                out.add(pick);
            }
        }
        return out;
    }

    // ------------------------------------------------------------ every tick

    @SubscribeEvent
    public static void livingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof SparringNinja ninja && !ninja.level().isClientSide() && ninja.isAlive()
                && ninja.getPersistentData().contains(TAG)) {
            tick(ninja);
        }
    }

    /** Server side, every tick of a legend: aura, footsteps, landing cracks, entrance, boss bar. */
    public static void tick(SparringNinja ninja) {
        if (!(ninja.level() instanceof ServerLevel level)) {
            return;
        }
        Kind kind = kindOf(ninja);
        State state = STATES.computeIfAbsent(ninja, n -> new State());
        if (ninja.tickCount % AURA_TICKS == 0) {
            aura(level, ninja, kind);
        }
        // Footsteps and landings.
        boolean onGround = ninja.onGround();
        if (!onGround) {
            state.fallFrom = state.wasOnGround ? ninja.getY() : Math.max(state.fallFrom, ninja.getY());
        } else if (!state.wasOnGround && state.fallFrom - ninja.getY() >= CRACK_FALL) {
            crack(level, ninja, (float) (state.fallFrom - ninja.getY()));
        } else if (ninja.tickCount % STEP_TICKS == 0 && ninja.getDeltaMovement().horizontalDistanceSqr() > 0.004) {
            step(level, ninja);
        }
        state.wasOnGround = onGround;
        if (ninja.tickCount % 10 == 0) {
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator() && player.distanceTo(ninja) <= ENTRANCE_RANGE && player.hasLineOfSight(ninja)) {
                    entrance(player, ninja);
                }
            }
            bossBar(level, ninja, state);
        }
    }

    /** The aura of each kind (particles everyone nearby sees). */
    static void aura(ServerLevel level, SparringNinja ninja, Kind kind) {
        double x = ninja.getX(), y = ninja.getY(), z = ninja.getZ();
        RandomSource r = ninja.getRandom();
        switch (kind == null ? Kind.SHADOW : kind) {
            case MANGEKYO -> {
                level.sendParticles(new DustParticleOptions(new Vector3f(0.55f, 0.04f, 0.05f), 1.4f), x, y + 1, z, 3, 0.35, 0.6, 0.35, 0);
                level.sendParticles(ParticleTypes.SMOKE, x, y + 0.3, z, 1, 0.3, 0.1, 0.3, 0.005);
                Vec3 eyes = ninja.getEyePosition().add(ninja.getLookAngle().scale(0.32));
                level.sendParticles(new DustParticleOptions(new Vector3f(1f, 0.1f, 0.08f), 0.55f), eyes.x, eyes.y, eyes.z, 1, 0.05, 0.02, 0.05, 0);
            }
            case BEAST -> {
                level.sendParticles(new DustParticleOptions(new Vector3f(0.95f, 0.25f, 0.05f), 1.6f), x, y + 1, z, 4, 0.4, 0.7, 0.4, 0);
                if (r.nextInt(4) == 0) {
                    level.sendParticles(ParticleTypes.FLAME, x, y + 0.2, z, 1, 0.3, 0.1, 0.3, 0.01);
                }
            }
            case LIGHTNING -> {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 1, z, 4, 0.4, 0.7, 0.4, 0.05);
                level.sendParticles(new DustParticleOptions(new Vector3f(0.6f, 0.85f, 1f), 0.8f), x, y + 1, z, 2, 0.45, 0.8, 0.45, 0);
                if (r.nextInt(12) == 0) {
                    // A small arc jumping over the body.
                    double a = r.nextDouble() * Math.PI * 2;
                    NoirmNetwork.sendNear(level, ninja.position(), 48, new VfxPackets.Bolt(x + Math.cos(a) * 0.4, y + 0.4 + r.nextDouble(),
                            z + Math.sin(a) * 0.4, x - Math.cos(a) * 0.5, y + 0.8 + r.nextDouble(), z - Math.sin(a) * 0.5, 0xBFE6FF, 0.05f, 4, 1));
                }
            }
            case FIRE -> {
                level.sendParticles(ParticleTypes.SMALL_FLAME, x, y + 0.9, z, 2, 0.35, 0.6, 0.35, 0.01);
                level.sendParticles(ParticleTypes.SMOKE, x, y + 1.6, z, 1, 0.25, 0.2, 0.25, 0.01);
                if (r.nextInt(5) == 0) {
                    level.sendParticles(ParticleTypes.LAVA, x, y + 0.6, z, 1, 0.3, 0.3, 0.3, 0);
                }
            }
            case SHADOW -> {
                level.sendParticles(new DustParticleOptions(new Vector3f(0.05f, 0.05f, 0.07f), 2f), x, y + 0.8, z, 4, 0.4, 0.6, 0.4, 0);
                if (r.nextInt(2) == 0) {
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.2, z, 1, 0.35, 0.05, 0.35, 0.003);
                }
            }
        }
    }

    /** A heavy footstep: a low thud and a puff of the ground. */
    static void step(ServerLevel level, SparringNinja ninja) {
        BlockPos below = ninja.blockPosition().below();
        BlockState ground = level.getBlockState(below);
        level.playSound(null, ninja.getX(), ninja.getY(), ninja.getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 0.45f, 0.75f);
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), ninja.getX(), ninja.getY() + 0.05, ninja.getZ(), 4, 0.2, 0, 0.2, 0.05);
        }
    }

    /** Landing from a height: a ring of broken ground and a boom. Returns how many particles flew (tests). */
    public static int crack(ServerLevel level, SparringNinja ninja, float fell) {
        BlockPos below = ninja.blockPosition().below();
        BlockState ground = level.getBlockState(below);
        level.playSound(null, ninja.getX(), ninja.getY(), ninja.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.5f, 0.6f);
        level.playSound(null, ninja.getX(), ninja.getY(), ninja.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.35f, 0.5f);
        int count = 0;
        if (!ground.isAir()) {
            float radius = Math.min(3.5f, 1f + fell * 0.3f);
            for (int i = 0; i < 24; i++) {
                double a = Math.PI * 2 * i / 24;
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), ninja.getX() + Math.cos(a) * radius, ninja.getY() + 0.1,
                        ninja.getZ() + Math.sin(a) * radius, 3, 0.1, 0.05, 0.1, 0.15);
                count += 3;
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, ninja.getX(), ninja.getY() + 0.2, ninja.getZ(), 1, 0, 0, 0, 0);
        return count + 1;
    }

    /**
     * The first time {@code player} meets this legend: their name across the screen, what they are
     * below it, and a growl. Returns whether it was shown now (false if this player has met them).
     */
    public static boolean entrance(Player player, SparringNinja ninja) {
        BingoEntry entry = player.getServer() == null ? null : BingoBook.get(player.getServer()).find(entryOf(ninja));
        java.util.Set<java.util.UUID> met = entry != null ? entry.met : metOf(ninja);
        if (!met.add(player.getUUID())) {
            return false;
        }
        if (entry != null) {
            BingoBook.get(player.getServer()).setDirty();
        } else {
            saveMet(ninja, met);
        }
        // Titles are drawn very large: the short name on top, the epithet and the rest below.
        Component title = (entry != null ? Component.literal(entry.given) : ninja.getDisplayName().copy())
                .withStyle(s -> s.withColor(NinjaClass.LEGENDARY_COLOR & 0xFFFFFF).withBold(true));
        Component subtitle = (entry != null ? Component.translatable("bingo.noirm.epithet." + entry.epithet)
                : Component.translatable("bingo.noirm.entrance.free")).copy().withStyle(ChatFormatting.GRAY);
        // The class and the bounty go above the hotbar (the subtitle is drawn too big for them).
        Component details = entry == null ? null : Component.translatable("bingo.noirm.entrance",
                NinjaClass.letter(NinjaClass.LEGENDARY_CODE), com.noirm.economy.Ryo.format(entry.bounty)).withStyle(ChatFormatting.GOLD);
        if (player instanceof ServerPlayer sp && sp.connection != null) {
            sp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 25));
            sp.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            sp.connection.send(new ClientboundSetTitleTextPacket(title));
            if (details != null) {
                sp.displayClientMessage(details, true);
            }
            // And a plain warning in chat that stays: this fight is far above a beginner.
            sp.sendSystemMessage(Component.translatable("bingo.noirm.entrance.warn", title.copy(),
                    NinjaClass.letter(NinjaClass.LEGENDARY_CODE)).withStyle(ChatFormatting.RED));
            sp.playNotifySound(SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 0.6f, 0.55f);
        }
        return true;
    }

    private static java.util.Set<java.util.UUID> metOf(SparringNinja ninja) {
        java.util.Set<java.util.UUID> met = new java.util.HashSet<>();
        for (var t : ninja.getPersistentData().getCompound(TAG).getList("Met", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            met.add(net.minecraft.nbt.NbtUtils.loadUUID(t));
        }
        return met;
    }

    private static void saveMet(SparringNinja ninja, java.util.Set<java.util.UUID> met) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        met.forEach(u -> list.add(net.minecraft.nbt.NbtUtils.createUUID(u)));
        ninja.getPersistentData().getCompound(TAG).put("Met", list);
    }

    /** Their name and health as a boss bar for players close by (not for their own crew). */
    private static void bossBar(ServerLevel level, SparringNinja ninja, State state) {
        if (ninja.inCrew()) {
            if (state.bar != null) {
                state.bar.removeAllPlayers();
                state.bar = null;
            }
            return;
        }
        if (state.bar == null) {
            state.bar = new ServerBossEvent(ninja.getDisplayName(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
        }
        state.bar.setProgress(Math.max(0f, Math.min(1f, ninja.getHealth() / ninja.getMaxHealth())));
        for (ServerPlayer player : level.players()) {
            boolean near = player.distanceTo(ninja) <= BAR_RANGE && !player.isSpectator();
            if (near && !state.bar.getPlayers().contains(player)) {
                state.bar.addPlayer(player);
            } else if (!near && state.bar.getPlayers().contains(player)) {
                state.bar.removePlayer(player);
            }
        }
        // Players who respawned or left are new objects: drop the old ones.
        for (ServerPlayer old : List.copyOf(state.bar.getPlayers())) {
            if (old.isRemoved() || !level.players().contains(old)) {
                state.bar.removePlayer(old);
            }
        }
    }

    /** Every second: bars of legends that died, vanished or changed world go away (whatever removed them). */
    @SubscribeEvent
    public static void serverTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || STATES.isEmpty()
                || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        for (Map.Entry<SparringNinja, State> entry : List.copyOf(STATES.entrySet())) {
            SparringNinja ninja = entry.getKey();
            if (ninja.isRemoved() || !ninja.isAlive()) {
                if (entry.getValue().bar != null) {
                    entry.getValue().bar.removeAllPlayers();
                }
                STATES.remove(ninja);
            }
        }
    }

    @SubscribeEvent
    public static void legendDies(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof SparringNinja ninja && !ninja.level().isClientSide()) {
            State state = STATES.remove(ninja);
            if (state != null && state.bar != null) {
                state.bar.removeAllPlayers();
            }
        }
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof SparringNinja ninja && !entity.level().isClientSide()) {
            State state = STATES.remove(ninja);
            if (state != null && state.bar != null) {
                state.bar.removeAllPlayers();
            }
        }
    }

    /** Gives {@code ninja} a Mangekyō from the page's seed (the same eyes every time). */
    static void giveMangekyo(SparringNinja ninja, long seed) {
        ninja.setSharinganStage(3);
        if (!MangekyoNpcs.hasMangekyo(ninja)) {
            MangekyoNpcs.makeMangekyo(ninja, RandomSource.create(seed ^ 0x5EED));
        }
    }
}
