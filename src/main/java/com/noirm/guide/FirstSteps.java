package com.noirm.guide;

import com.noirm.Noirm;
import com.noirm.combat.CombatState;
import com.noirm.combat.CombatStates;
import com.noirm.creator.CreatorData;
import com.noirm.mission.CareerItems;
import com.noirm.mission.PlayerRank;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A new shinobi's first steps. Once character creation is done the player gets the
 * <b>Shinobi Handbook</b> (once, ever), then a few gentle tips in chat, one at a time, each
 * ticked off as soon as they do it:
 * <ol>
 *   <li>open the handbook;</li>
 *   <li>try combat mode (R) on the training dummy;</li>
 *   <li>find a missions board in a village.</li>
 * </ol>
 * A tip comes back at most {@link #MAX_REMINDERS} times, {@link #REMIND_TICKS} apart, then the
 * guide moves on. Every tip has a "[Skip tips]" link ({@code /noirm guide tips off}).
 *
 * <p>Missions boards also hand out a free handbook: crouch and right-click one with an empty hand.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class FirstSteps {
    /** The steps, in order. */
    public enum Step { HANDBOOK, COMBAT, BOARD }

    /** The first tip, this long after the handbook is given (3 s). */
    public static final int FIRST_TIP_TICKS = 60;
    /** After a step is done, the next tip waits this long (8 s). */
    public static final int NEXT_TIP_TICKS = 160;
    /** A tip not yet followed comes back after this long (4 minutes)... */
    public static final int REMIND_TICKS = 4800;
    /** ...at most this many times. */
    public static final int MAX_REMINDERS = 2;
    /** How often the guide checks on a player. */
    public static final int CHECK_TICKS = 20;

    private FirstSteps() {}

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player && !(player instanceof FakePlayer)
                && player.tickCount % CHECK_TICKS == 0) {
            ShinobiCapability.get(player).ifPresent(data -> tick(player, data, player.level().getGameTime()));
        }
    }

    /** Whether the player is a finished character (creation done, clan given): the guide starts then. */
    public static boolean ready(ShinobiData data) {
        return data.creator().mode() == CreatorData.Mode.NONE && data.identity().isAssigned();
    }

    /** One check of the guide for {@code player} at game time {@code now} (public so tests can drive it). */
    public static void tick(Player player, ShinobiData data, long now) {
        if (!ready(data)) {
            return;
        }
        GuideData guide = data.guide();
        if (!guide.handbookGiven()) {
            giveHandbook(player);
            guide.setHandbookGiven(true);
            guide.setStep(0);
            guide.setNextTip(now + FIRST_TIP_TICKS);
            data.markDirty();
            player.sendSystemMessage(Component.translatable("guide.noirm.welcome").withStyle(ChatFormatting.GOLD));
            return;
        }
        noteProgress(player, data);
        if (guide.tipsOff() || guide.step() >= Step.values().length) {
            return;
        }
        Step step = Step.values()[guide.step()];
        if (done(player, data, step)) {
            guide.setStep(guide.step() + 1);
            guide.setNextTip(now + NEXT_TIP_TICKS);
            data.markDirty();
            if (guide.step() >= Step.values().length) {
                player.sendSystemMessage(Component.translatable("guide.noirm.all_done").withStyle(ChatFormatting.GREEN));
            }
            return;
        }
        if (guide.nextTip() == 0) {
            guide.setNextTip(now + FIRST_TIP_TICKS); // just joined: a moment to look around first
            return;
        }
        if (now < guide.nextTip()) {
            return;
        }
        if (guide.reminders() > MAX_REMINDERS) {
            guide.setStep(guide.step() + 1); // they'll find it in the handbook; don't nag
            guide.setNextTip(now + NEXT_TIP_TICKS);
            data.markDirty();
            return;
        }
        player.sendSystemMessage(tip(step, guide.step()));
        player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.4f, 1.6f);
        guide.addReminder();
        guide.setNextTip(now + REMIND_TICKS);
        data.markDirty();
    }

    /** Things the guide watches for even between tips (combat mode switched on, a board used...). */
    private static void noteProgress(Player player, ShinobiData data) {
        CombatState state = CombatStates.getIfPresent(player);
        if (state != null && state.isCombatMode()) {
            data.guide().setTriedCombat(true);
        }
    }

    /** Whether a step is done. */
    public static boolean done(Player player, ShinobiData data, Step step) {
        GuideData guide = data.guide();
        return switch (step) {
            case HANDBOOK -> guide.openedHandbook();
            case COMBAT -> guide.triedCombat();
            case BOARD -> guide.usedBoard() || data.career().mission() != null || data.career().rank().atLeast(PlayerRank.GENIN);
        };
    }

    /** "Shinobi tip (1/3): ... [Skip tips]" with the player's own key names. */
    public static MutableComponent tip(Step step, int index) {
        String key = "guide.noirm.tip." + step.name().toLowerCase(java.util.Locale.ROOT);
        Component body = switch (step) {
            case HANDBOOK -> Component.translatable(key);
            case COMBAT -> Component.translatable(key, Component.keybind("key.noirm.combat_mode").withStyle(ChatFormatting.YELLOW),
                    Component.keybind("key.noirm.jutsu_screen").withStyle(ChatFormatting.YELLOW));
            case BOARD -> Component.translatable(key);
        };
        MutableComponent skip = Component.translatable("guide.noirm.skip").withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/noirm guide tips off"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/noirm guide tips off"))));
        return Component.translatable("guide.noirm.tip", index + 1, Step.values().length).withStyle(ChatFormatting.GOLD)
                .append(" ").append(body.copy().withStyle(ChatFormatting.WHITE)).append(" ").append(skip);
    }

    /** Puts a handbook in the player's bag (or at their feet if it's full). */
    public static void giveHandbook(Player player) {
        ItemStack book = new ItemStack(GuideItems.HANDBOOK.get());
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
    }

    /** Whether the player carries a handbook. */
    public static boolean hasHandbook(Player player) {
        return player.getInventory().contains(new ItemStack(GuideItems.HANDBOOK.get()));
    }

    /** Missions boards: using one ticks off the last tip; crouch + empty hand takes a free handbook. */
    @SubscribeEvent
    public static void useBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !player.level().getBlockState(event.getPos()).is(CareerItems.MISSION_BOARD.get())) {
            return;
        }
        ShinobiCapability.get(player).ifPresent(data -> data.guide().setUsedBoard(true));
        if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty() && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
            if (hasHandbook(player)) {
                player.displayClientMessage(Component.translatable("guide.noirm.board_has_one").withStyle(ChatFormatting.GRAY), true);
            } else {
                giveHandbook(player);
                player.displayClientMessage(Component.translatable("guide.noirm.board_gave").withStyle(ChatFormatting.GOLD), true);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
