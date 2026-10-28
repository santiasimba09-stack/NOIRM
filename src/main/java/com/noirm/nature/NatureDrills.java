package com.noirm.nature;

import com.noirm.clan.Clan;
import com.noirm.clan.Nature;
import com.noirm.clan.NatureSystem;
import com.noirm.entity.MasterNinja;
import com.noirm.entity.SparringNinja;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.vfx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Learning a basic nature you weren't born with: the <b>chakra paper</b> test, the long <b>nature
 * drills</b>, <b>nature scrolls</b> and <b>teachers</b>.
 *
 * <p><b>Drills.</b> Meditate (N) in the drill's setting and the drill moves, one second of study per
 * second; leave it and it waits (progress is kept for each nature):
 * <ul>
 *   <li>Wind: hold a leaf block (cut it with your chakra).</li>
 *   <li>Fire: sit by a flame: fire, a lit campfire, furnace or candle, or lava.</li>
 *   <li>Water: sit by running water (a stream or a waterfall), or out in the rain.</li>
 *   <li>Lightning: sit by a lightning rod, or under the open sky in a thunderstorm.</li>
 *   <li>Earth: sit on bare stone, with stone all around you (a cave, a quarry).</li>
 * </ul>
 * A drill takes {@link #DRILL_SECONDS} (three in-game days of focus); a <b>hidden</b> nature (the
 * chakra paper shows them) goes {@link #HIDDEN_PACE}× faster. A <b>teacher</b> who has the nature,
 * within {@link #TEACH_RANGE} blocks (a crew mate ×{@link #CREW_TEACHER_PACE}, a master
 * ×{@link #MASTER_TEACHER_PACE}), speeds it up, and lets you drill even without the setting. A
 * <b>nature scroll</b> starts the drill and puts {@link #SCROLL_BOOST} of it behind you (never the
 * last bit). Finishing gives the nature at {@link Clan#TRAINED_AFFINITY} and its starting jutsu.
 */
public final class NatureDrills {
    // ---------------------------------------------------------------- tuning
    /** Seconds of focused drilling a basic nature takes: 3 Minecraft days (a day is 1,200 s). */
    public static final float DRILL_SECONDS = 3600f;
    /** A hidden nature trains this many times faster (1.5 days). */
    public static final float HIDDEN_PACE = 2f;
    /** A crew mate who has the nature, studying with you. */
    public static final float CREW_TEACHER_PACE = 1.5f;
    /** A master who has the nature. */
    public static final float MASTER_TEACHER_PACE = 2f;
    /** Teachers must be this close (blocks). */
    public static final double TEACH_RANGE = 8.0;
    /** A nature scroll puts this share of the drill behind you. */
    public static final float SCROLL_BOOST = 0.25f;
    /** A scroll never takes a drill past this share: the last bit is always yours to do. */
    public static final float SCROLL_CAP = 0.95f;
    /** Chance a player has two hidden natures instead of one. */
    public static final float TWO_HIDDEN_CHANCE = 0.35f;
    /** Chakra the paper test takes. */
    public static final float PAPER_CHAKRA = 5f;
    /** Ticks of meditation between drill updates. */
    public static final int INTERVAL = 20;
    /** Setting checks: how far to look for a flame / running water / a lightning rod / stone. */
    public static final int FIRE_RANGE = 4;
    public static final int WATER_RANGE = 3;
    public static final int ROD_RANGE = 5;
    public static final int STONE_RANGE = 2;
    /** Stone blocks around you (5×5, from the floor up to head height and one above) for the earth drill. */
    public static final int STONE_NEEDED = 12;

    /** The natures a drill can teach. */
    public static final List<Nature> TRAINABLE = Nature.BASIC;

    private NatureDrills() {}

    // ---------------------------------------------------------------- hidden affinity

    /** Rolls the player's hidden natures if they haven't been yet (among basic natures they lack). */
    public static void ensureRolled(Player player, ShinobiData data) {
        NatureTraining t = data.natureTraining();
        if (t.rolled() && !(staleHidden(data) && !t.hidden().isEmpty())) {
            return;
        }
        t.clearRoll();
        List<Nature> lacking = new ArrayList<>();
        for (Nature n : TRAINABLE) {
            if (data.identity().affinity(n) <= 0f) {
                lacking.add(n);
            }
        }
        t.roll(player.getRandom(), lacking);
    }

    /**
     * True when every hidden nature is now one your clan gives you (stronger than trained): the
     * clan changed after the roll (character creation, {@code /noirm clan set}), so roll again.
     * Natures you <i>trained</i> keep the roll: finishing your hidden drill doesn't hand out another.
     */
    static boolean staleHidden(ShinobiData data) {
        for (Nature n : data.natureTraining().hidden()) {
            if (data.identity().affinity(n) <= Clan.TRAINED_AFFINITY) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- drills

    /** Seconds the drill for {@code nature} takes at a pace of 1. */
    public static float secondsNeeded(Nature nature) {
        return DRILL_SECONDS;
    }

    /** Whether the player is in the drill's setting right now. */
    public static boolean inSetting(Player player, Nature nature) {
        Level level = player.level();
        BlockPos at = player.blockPosition();
        return switch (nature) {
            case WIND -> holdsLeaf(player);
            case FIRE -> anyNear(level, at, FIRE_RANGE, 1, 2, NatureDrills::isFlame);
            case WATER -> anyNear(level, at, WATER_RANGE, 1, 3, s -> s.getFluidState().is(FluidTags.WATER) && !s.getFluidState().isSource())
                    || level.isRainingAt(at.above());
            case LIGHTNING -> anyNear(level, at, ROD_RANGE, 1, 3, s -> s.is(Blocks.LIGHTNING_ROD))
                    || (level.isThundering() && level.canSeeSky(at.above()));
            case EARTH -> isStone(level.getBlockState(at.below())) && countNear(level, at, STONE_RANGE, 1, 2, NatureDrills::isStone) >= STONE_NEEDED;
            default -> false;
        };
    }

    private static boolean holdsLeaf(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).is(ItemTags.LEAVES)) {
                return true;
            }
        }
        return false;
    }

    static boolean isFlame(BlockState s) {
        if (s.is(BlockTags.FIRE) || s.getFluidState().is(FluidTags.LAVA)) {
            return true;
        }
        if (s.getBlock() instanceof CampfireBlock || s.getBlock() instanceof AbstractFurnaceBlock || s.getBlock() instanceof AbstractCandleBlock) {
            return s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT);
        }
        return false;
    }

    static boolean isStone(BlockState s) {
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE);
    }

    private static boolean anyNear(Level level, BlockPos at, int r, int down, int up, java.util.function.Predicate<BlockState> test) {
        return countNear(level, at, r, down, up, test, 1) > 0;
    }

    private static int countNear(Level level, BlockPos at, int r, int down, int up, java.util.function.Predicate<BlockState> test) {
        return countNear(level, at, r, down, up, test, Integer.MAX_VALUE);
    }

    private static int countNear(Level level, BlockPos at, int r, int down, int up, java.util.function.Predicate<BlockState> test, int enough) {
        int count = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -down; dy <= up; dy++) {
                    pos.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    if (test.test(level.getBlockState(pos)) && ++count >= enough) {
                        return count;
                    }
                }
            }
        }
        return count;
    }

    /** 0 = no teacher, 1 = a crew mate, 2 = a master (who has {@code nature}, close by). */
    public static int teacher(Player player, Nature nature) {
        int best = 0;
        for (SparringNinja ninja : player.level().getEntitiesOfClass(SparringNinja.class, player.getBoundingBox().inflate(TEACH_RANGE),
                n -> n.isAlive() && n.jutsuAffinity(nature) > 0f)) {
            if (ninja instanceof MasterNinja) {
                return 2;
            }
            if (player.getUUID().equals(ninja.leaderId())) {
                best = 1;
            }
        }
        return best;
    }

    /** How fast the drill for {@code nature} moves right now (0 = it doesn't), and why. */
    public record Pace(Nature nature, boolean setting, int teacher, boolean hidden, float rate) {}

    public static Pace pace(Player player, ShinobiData data, Nature nature) {
        boolean setting = inSetting(player, nature);
        int teacher = teacher(player, nature);
        boolean hidden = data.natureTraining().isHidden(nature);
        float rate = 0f;
        if (setting || teacher > 0) {
            rate = (hidden ? HIDDEN_PACE : 1f) * (teacher == 2 ? MASTER_TEACHER_PACE : teacher == 1 ? CREW_TEACHER_PACE : 1f);
        }
        return new Pace(nature, setting, teacher, hidden, rate);
    }

    /**
     * The drill that moves right now: the active one if it can, otherwise the one (among those that
     * can) with the most progress. Null if none.
     */
    public static Pace current(Player player, ShinobiData data) {
        NatureTraining t = data.natureTraining();
        Pace best = null;
        for (Nature nature : TRAINABLE) {
            if (data.identity().affinity(nature) > 0f) {
                continue;
            }
            Pace p = pace(player, data, nature);
            if (p.rate() <= 0f) {
                continue;
            }
            if (nature == t.active()) {
                return p;
            }
            if (best == null || t.progress(nature) > t.progress(best.nature())) {
                best = p;
            }
        }
        return best;
    }

    /** Every server tick for every player. */
    public static void tick(Player player, ShinobiData data) {
        NatureTraining t = data.natureTraining();
        if (!data.senjutsu().isMeditating()) {
            t.setPace(0f, 0);
            return;
        }
        int ticks = data.senjutsu().ticks();
        if (ticks > 0 && ticks % INTERVAL == 0) {
            study(player, data, INTERVAL / 20f);
        }
    }

    /**
     * Puts {@code seconds} of meditation into the drill that can move now. Needs the player to be
     * meditating. Returns the nature it finished, or null.
     */
    public static Nature study(Player player, ShinobiData data, float seconds) {
        NatureTraining t = data.natureTraining();
        if (!data.senjutsu().isMeditating()) {
            t.setPace(0f, 0);
            return null;
        }
        ensureRolled(player, data);
        Pace pace = current(player, data);
        if (pace == null) {
            t.setPace(0f, 0);
            return null;
        }
        Nature nature = pace.nature();
        t.setActive(nature);
        t.setPace(pace.rate(), pace.teacher());
        float before = t.progress(nature) / secondsNeeded(nature);
        t.setProgress(nature, t.progress(nature) + seconds * pace.rate());
        float after = t.progress(nature) / secondsNeeded(nature);
        if (after >= 1f) {
            complete(player, data, nature);
            return nature;
        }
        if (player.tickCount % 40 < INTERVAL) {
            drillEffect(player, nature, false);
        }
        if ((int) (before * 20) != (int) (after * 20) || before == 0f) { // every 5%
            notify(player, Component.translatable("message.noirm.drill.progress", name(nature), Math.round(after * 100)).withStyle(ChatFormatting.AQUA));
        }
        return null;
    }

    /** The drill is done: the nature is yours, at trained strength, with its starting jutsu. */
    public static void complete(Player player, ShinobiData data, Nature nature) {
        NatureTraining t = data.natureTraining();
        t.setProgress(nature, 0f);
        if (t.active() == nature) {
            t.setActive(null);
        }
        NatureSystem.grant(data, nature, Clan.TRAINED_AFFINITY);
        player.sendSystemMessage(Component.translatable("message.noirm.drill.done", name(nature)).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.1f);
        Vfx.aura(player.level(), player.position(), player.getBbWidth(), player.getBbHeight(), Vfx.palette(nature), 40);
        drillEffect(player, nature, true);
    }

    /** Sets a drill's progress directly, as a share 0..1 (commands); 1 or more finishes it. */
    public static void setShare(Player player, ShinobiData data, Nature nature, float share) {
        NatureTraining t = data.natureTraining();
        t.setActive(nature);
        if (share >= 1f) {
            complete(player, data, nature);
        } else {
            t.setProgress(nature, Math.max(0f, share) * secondsNeeded(nature));
        }
    }

    /** A drill's progress as a share 0..1. */
    public static float share(ShinobiData data, Nature nature) {
        return data.natureTraining().progress(nature) / secondsNeeded(nature);
    }

    // ---------------------------------------------------------------- nature scrolls

    /**
     * Reads a nature scroll: starts the drill for {@code nature} (it becomes the active one) and puts
     * {@link #SCROLL_BOOST} of it behind you, up to {@link #SCROLL_CAP}. Returns false (and tells the
     * player why) when it can't help: you already have the nature, or the drill is as far as a scroll
     * can take it.
     */
    public static boolean readScroll(Player player, ShinobiData data, Nature nature) {
        if (data.identity().affinity(nature) > 0f) {
            notify(player, Component.translatable("message.noirm.nature_scroll.owned", name(nature)).withStyle(ChatFormatting.GRAY));
            return false;
        }
        ensureRolled(player, data);
        NatureTraining t = data.natureTraining();
        float needed = secondsNeeded(nature);
        float cap = needed * SCROLL_CAP;
        if (t.progress(nature) >= cap) {
            notify(player, Component.translatable("message.noirm.nature_scroll.capped", name(nature)).withStyle(ChatFormatting.GRAY));
            return false;
        }
        t.setActive(nature);
        t.setProgress(nature, Math.min(cap, t.progress(nature) + needed * SCROLL_BOOST));
        player.sendSystemMessage(Component.translatable("message.noirm.nature_scroll.read", name(nature),
                Math.round(share(data, nature) * 100), Component.translatable("drill.noirm." + nature.getSerializedName())).withStyle(ChatFormatting.AQUA));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.9f);
        drillEffect(player, nature, false);
        return true;
    }

    // ---------------------------------------------------------------- the chakra paper

    /**
     * Channels chakra into a chakra paper: it reacts to your strongest nature and, faintly, to your
     * hidden ones, which are revealed for good. Returns false (nothing spent) without enough chakra.
     */
    public static boolean paperTest(Player player, ShinobiData data) {
        if (!player.getAbilities().instabuild && !data.tryConsumeChakra(PAPER_CHAKRA)) {
            notify(player, Component.translatable("message.noirm.paper.no_chakra").withStyle(ChatFormatting.GRAY));
            return false;
        }
        ensureRolled(player, data);
        NatureTraining t = data.natureTraining();
        t.setRevealed(true);
        Nature strongest = null;
        for (Nature n : TRAINABLE) {
            float a = data.identity().affinity(n);
            if (a > 0f && (strongest == null || a > data.identity().affinity(strongest))) {
                strongest = n;
            }
        }
        List<Nature> hidden = new ArrayList<>();
        for (Nature n : t.hidden()) {
            if (data.identity().affinity(n) <= 0f) {
                hidden.add(n);
            }
        }
        Nature shown = strongest != null ? strongest : hidden.isEmpty() ? null : hidden.get(0);
        if (shown == null) {
            player.sendSystemMessage(Component.translatable("message.noirm.paper.nothing").withStyle(ChatFormatting.GRAY));
            return true;
        }
        player.sendSystemMessage(Component.translatable("message.noirm.paper.react." + shown.getSerializedName(), name(shown)).withStyle(ChatFormatting.WHITE));
        paperEffect(player, shown, true);
        if (hidden.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.noirm.paper.no_hidden").withStyle(ChatFormatting.GRAY));
        }
        for (Nature n : hidden) {
            if (n != shown) {
                paperEffect(player, n, false);
            }
            player.sendSystemMessage(Component.translatable("message.noirm.paper.hidden", name(n),
                    Component.translatable("drill.noirm." + n.getSerializedName())).withStyle(ChatFormatting.AQUA));
        }
        data.markPowersDirty();
        return true;
    }

    // ---------------------------------------------------------------- effects

    /** The paper's reaction in front of the player: cut, burnt, wet, crumpled or turned to dust. */
    private static void paperEffect(Player player, Nature nature, boolean strong) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.6)).add(0, -0.3, 0);
        int n = strong ? 1 : 0;
        ParticleOptions paper = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER));
        switch (nature) {
            case WIND -> {
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, hand.x, hand.y, hand.z, 1 + n, 0.05, 0.05, 0.05, 0);
                level.sendParticles(paper, hand.x, hand.y, hand.z, 10 + 10 * n, 0.15, 0.15, 0.15, 0.08);
                sound(level, hand, SoundEvents.PLAYER_ATTACK_SWEEP, strong);
            }
            case FIRE -> {
                level.sendParticles(ParticleTypes.FLAME, hand.x, hand.y, hand.z, 12 + 12 * n, 0.1, 0.12, 0.1, 0.02);
                level.sendParticles(ParticleTypes.SMOKE, hand.x, hand.y + 0.2, hand.z, 8 + 8 * n, 0.1, 0.1, 0.1, 0.02);
                level.sendParticles(ParticleTypes.WHITE_ASH, hand.x, hand.y, hand.z, 20, 0.2, 0.2, 0.2, 0.01);
                sound(level, hand, SoundEvents.FIRECHARGE_USE, strong);
            }
            case WATER -> {
                level.sendParticles(ParticleTypes.DRIPPING_WATER, hand.x, hand.y - 0.1, hand.z, 10 + 10 * n, 0.15, 0.05, 0.15, 0);
                level.sendParticles(ParticleTypes.SPLASH, hand.x, hand.y, hand.z, 16 + 16 * n, 0.15, 0.1, 0.15, 0.05);
                sound(level, hand, SoundEvents.GENERIC_SPLASH, strong);
            }
            case LIGHTNING -> {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y, hand.z, 14 + 14 * n, 0.15, 0.15, 0.15, 0.3);
                level.sendParticles(paper, hand.x, hand.y, hand.z, 6 + 6 * n, 0.05, 0.05, 0.05, 0.02);
                sound(level, hand, SoundEvents.BEEHIVE_SHEAR, strong);
                level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, strong ? 0.25f : 0.12f, 2f);
            }
            case EARTH -> {
                ParticleOptions dust = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());
                level.sendParticles(dust, hand.x, hand.y, hand.z, 20 + 20 * n, 0.15, 0.1, 0.15, 0);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), hand.x, hand.y, hand.z,
                        10, 0.1, 0.1, 0.1, 0.05);
                sound(level, hand, SoundEvents.SAND_BREAK, strong);
            }
            default -> { }
        }
    }

    /** Small effects while a drill runs (and a bigger burst when it completes). */
    private static void drillEffect(Player player, Nature nature, boolean big) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.5)).add(0, -0.5, 0);
        Vec3 feet = player.position();
        int k = big ? 4 : 1;
        switch (nature) {
            case WIND -> {
                level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.OAK_LEAVES)), hand.x, hand.y, hand.z,
                        4 * k, 0.1, 0.1, 0.1, 0.06);
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, hand.x, hand.y, hand.z, 1, 0, 0, 0, 0);
                sound(level, hand, SoundEvents.PLAYER_ATTACK_SWEEP, false);
            }
            case FIRE -> level.sendParticles(ParticleTypes.FLAME, hand.x, hand.y, hand.z, 4 * k, 0.12, 0.1, 0.12, 0.01);
            case WATER -> level.sendParticles(ParticleTypes.SPLASH, feet.x, feet.y + 0.3, feet.z, 8 * k, 0.4, 0.2, 0.4, 0.05);
            case LIGHTNING -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y, hand.z, 5 * k, 0.15, 0.15, 0.15, 0.2);
            case EARTH -> level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                    feet.x, feet.y + 0.1, feet.z, 6 * k, 0.4, 0.05, 0.4, 0.05);
            default -> { }
        }
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, boolean strong) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, strong ? 0.9f : 0.5f, strong ? 1f : 1.3f);
    }

    // ---------------------------------------------------------------- helpers

    static Component name(Nature nature) {
        return Component.translatable(nature.translationKey()).withStyle(s -> s.withColor(nature.color()));
    }

    private static void notify(Player player, Component message) {
        if (player instanceof ServerPlayer server) {
            server.displayClientMessage(message, true);
        }
    }

    /** The data of a player, if any. */
    static ShinobiData data(Player player) {
        return ShinobiCapability.get(player).orElse(null);
    }
}
