package com.noirm.hiraishin;

import com.noirm.Noirm;
import com.noirm.entity.SparringNinja;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * How the Flying Raijin is earned: a long road, not a scroll you read once.
 * <ol>
 *   <li><b>The Space-Time Formula</b> ({@link #FORMULA}, B rank): read from a Space-Time Formula
 *       scroll with CTL {@value #FORMULA_CTL} and Reserves {@value #FORMULA_RESERVES}. It lets you
 *       write marks (the mark key, a strike, a Flying Raijin kunai) but not travel to them yet.
 *       Every new mark is practice.</li>
 *   <li><b>The Flying Raijin</b> ({@link #RAIJIN}, S rank): comes by itself once the formula is at
 *       ★{@value #RAIJIN_FORMULA_STARS} (about 60 marks), with CTL {@value #RAIJIN_CTL} and Reserves
 *       {@value #RAIJIN_RESERVES}.</li>
 *   <li><b>Mastery</b> of the Flying Raijin (★0–★5, from jumps): a shorter wait between jumps, no
 *       seal from ★{@value HiraishinJump#SEALLESS_STARS}, carrying allies from ★{@value HiraishinJump#CARRY_STARS},
 *       and the chain from ★{@value HiraishinJump#CHAIN_STARS}.</li>
 * </ol>
 * A sealing clan's {@link HiraishinAffinity} lowers the stat gates.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class HiraishinLearning {
    public static final ResourceLocation FORMULA = Noirm.id("space_time/formula_mark");
    public static final ResourceLocation RAIJIN = Noirm.id("space_time/flying_raijin");
    public static final float FORMULA_CTL = 30f;
    public static final float FORMULA_RESERVES = 25f;
    public static final int RAIJIN_FORMULA_STARS = 3;
    public static final float RAIJIN_CTL = 45f;
    public static final float RAIJIN_RESERVES = 40f;
    /** How often (ticks) a player's progress toward the Flying Raijin is checked. */
    public static final int CHECK_TICKS = 40;

    private HiraishinLearning() {}

    /** Can {@code entity} write marks? (Knows the formula, or the Flying Raijin.) */
    public static boolean knowsFormula(LivingEntity entity) {
        return knows(entity, FORMULA) || knows(entity, RAIJIN);
    }

    public static boolean knowsRaijin(LivingEntity entity) {
        return knows(entity, RAIJIN);
    }

    public static boolean knows(LivingEntity entity, ResourceLocation id) {
        if (entity instanceof Player player) {
            return ShinobiCapability.get(player).map(d -> d.jutsu().knows(id)).orElse(false);
        }
        return entity instanceof SparringNinja ninja && ninja.knownJutsu().contains(id);
    }

    /** ★ of a jutsu for anyone (players' mastery; NPCs' own; legends count as masters). */
    public static int stars(LivingEntity entity, ResourceLocation id) {
        if (entity instanceof Player player) {
            return ShinobiCapability.get(player).map(d -> d.jutsu().masteryLevel(id)).orElse(0);
        }
        if (entity instanceof SparringNinja ninja) {
            if (com.noirm.bounty.Legendary.isLegendary(ninja)) {
                return 5;
            }
            int set = ninja.getPersistentData().getInt(NPC_STARS_TAG);
            return Math.min(5, Math.max(set, ninja.jutsuMastery(id)));
        }
        return 0;
    }

    /** NBT key on an NPC: its ★ with the space-time jutsu (commands and tests set it; legends count as ★5). */
    public static final String NPC_STARS_TAG = "NoirmRaijinStars";

    /** Makes {@code ninja} a ★{@code stars} user of the formula and the Flying Raijin. */
    public static void teachNpc(SparringNinja ninja, int stars) {
        ninja.teach(FORMULA);
        ninja.teach(RAIJIN);
        ninja.getPersistentData().putInt(NPC_STARS_TAG, Math.max(0, Math.min(5, stars)));
    }

    /** Why {@code player} can't learn the formula yet, or null if they can. */
    public static Component formulaBlocker(Player player) {
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (data == null) {
            return Component.literal("?");
        }
        float discount = HiraishinAffinity.of(player).statDiscount();
        float ctl = FORMULA_CTL - discount;
        float reserves = FORMULA_RESERVES - discount;
        if (data.getControl() < ctl || data.getReserves() < reserves) {
            return Component.translatable("message.noirm.hiraishin.formula_needs", Math.round(ctl), Math.round(data.getControl()),
                    Math.round(reserves), Math.round(data.getReserves()));
        }
        return null;
    }

    /** Teaches the formula (the scroll, or a command). Returns false if they already know it. */
    public static boolean teachFormula(Player player) {
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (data == null || data.jutsu().knows(FORMULA)) {
            return false;
        }
        data.jutsu().learn(FORMULA, false);
        data.jutsu().equipIfRoom(FORMULA);
        data.markPowersDirty();
        player.sendSystemMessage(Component.translatable("message.noirm.hiraishin.formula_learned").withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), com.noirm.sound.NoirmSounds.HIRAISHIN_MARK.get(), SoundSource.PLAYERS, 1f, 0.8f);
        return true;
    }

    /** Why {@code player} hasn't earned the Flying Raijin yet, or null if they have what it takes. */
    public static Component raijinBlocker(Player player) {
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (data == null || !data.jutsu().knows(FORMULA)) {
            return Component.translatable("message.noirm.hiraishin.needs_formula");
        }
        float discount = HiraishinAffinity.of(player).statDiscount();
        int stars = data.jutsu().masteryLevel(FORMULA);
        float ctl = RAIJIN_CTL - discount;
        float reserves = RAIJIN_RESERVES - discount;
        if (stars < RAIJIN_FORMULA_STARS || data.getControl() < ctl || data.getReserves() < reserves) {
            return Component.translatable("message.noirm.hiraishin.raijin_needs", stars, RAIJIN_FORMULA_STARS, Math.round(data.getControl()),
                    Math.round(ctl), Math.round(data.getReserves()), Math.round(reserves));
        }
        return null;
    }

    /** Teaches the Flying Raijin if they've earned it (or {@code force}: commands). Returns true if learned now. */
    public static boolean tryTeachRaijin(Player player, boolean force) {
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (data == null || data.jutsu().knows(RAIJIN) || !force && raijinBlocker(player) != null) {
            return false;
        }
        data.jutsu().learn(RAIJIN, false);
        data.jutsu().equipIfRoom(RAIJIN);
        data.markPowersDirty();
        player.sendSystemMessage(Component.translatable("message.noirm.hiraishin.raijin_learned").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        if (player instanceof ServerPlayer sp) {
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(4, 50, 20));
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                    Component.translatable("message.noirm.hiraishin.raijin_title").withStyle(ChatFormatting.YELLOW)));
            HiraishinFx.flash(sp.serverLevel(), sp.position(), sp.position(), sp, HiraishinFx.LEARNED);
        }
        return true;
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide() || event.player.tickCount % CHECK_TICKS != 0) {
            return;
        }
        Player player = event.player;
        if (knows(player, FORMULA) && !knows(player, RAIJIN)) {
            tryTeachRaijin(player, false);
        }
    }
}
