package com.noirm.dojutsu;

import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuData;
import com.noirm.player.PowerVisuals;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.vfx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Mangekyō Sharingan: the eyes an Uchiha with three tomoe awakens through a deep loss.
 * <ul>
 *   <li><b>Awakening</b> (3 tomoe, born Uchiha): seeing a devoted crew mate (respect
 *       {@link #BOND_RESPECT}+) or another player die close by, or a near-death awakening with
 *       Resolve at {@link #RESOLVE_AWAKENING}+.</li>
 *   <li><b>Unique eyes</b>: a pattern ({@link #PATTERNS} original designs), an ability in each eye
 *       rolled from {@link MangekyoAbility}, and Susanoo in its own colour. They're jutsu, learned
 *       at the awakening and put in the loadout like any other.</li>
 *   <li><b>The cost</b>: every use strains the eyes. Strain never heals: past
 *       {@link #WEAKEN_STRAIN} the abilities weaken, past {@link #FAILING_STRAIN} more, at
 *       {@link #MAX_STRAIN} you're blind. Taking another Mangekyō's eyes makes the
 *       <b>Eternal Mangekyō</b>: no strain, stronger (see {@link MangekyoTransplant}).</li>
 * </ul>
 */
public final class Mangekyo {
    public enum Route {
        BOND("bond"), PLAYER("player"), RESOLVE("resolve"), COMMAND("command");

        private final String key;

        Route(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    /** How many original patterns exist (drawn in {@code textures/entity/mangekyo_patterns.png}). */
    public static final int PATTERNS = 8;
    public static final ResourceLocation SUSANOO = com.noirm.Noirm.id("mangekyo/susanoo");
    // ---- awakening
    /** A crew mate this devoted (respect 0–100) dying in front of you can awaken it. */
    public static final float BOND_RESPECT = 60f;
    /** How close the loss must be (closer than the Sharingan's 24 blocks). */
    public static final double LOSS_RANGE = 16;
    /** A near-death awakening with this much Resolve (before it's spent) opens the Mangekyō at 3 tomoe. */
    public static final float RESOLVE_AWAKENING = 95f;
    /** After the awakening the eyes stay open this long without draining chakra. */
    public static final int FREE_TICKS = 300;
    // ---- strain (blindness)
    public static final float MAX_STRAIN = 100f;
    /** Strain hints (a message, and the client's vignette steps up). */
    public static final float[] STRAIN_TIERS = {25f, 50f, 70f, 85f, 100f};
    public static final float WEAKEN_STRAIN = 70f;
    public static final float FAILING_STRAIN = 85f;
    public static final float WEAKENED_POWER = 0.75f;
    public static final float FAILING_POWER = 0.5f;
    /** Transplanted eyes in a body not born to them strain faster. */
    public static final float TRANSPLANT_STRAIN = 1.5f;
    // ---- strength
    /** The Eternal Mangekyō's power, and extra perception per Mangekyō / Eternal. */
    public static final float ETERNAL_POWER = 1.25f;
    public static final float PERCEPTION_BONUS = 0.25f;
    public static final float ETERNAL_PERCEPTION_BONUS = 0.5f;
    /** Susanoo colours to roll from: violet, indigo, crimson, cobalt, emerald, gold, ash, orange. */
    public static final int[] SUSANOO_COLORS = {0x8A3CFF, 0x4A3CFF, 0xE0203A, 0x2A7CFF, 0x22C878, 0xFFC020, 0xC0C8D8, 0xFF7A20};

    private Mangekyo() {}

    // ---------------------------------------------------------------- who has it

    /** The Mangekyō is open (players and NPC ninjas). */
    public static boolean isActive(LivingEntity entity) {
        if (entity instanceof Player player) {
            return ShinobiCapability.get(player).map(d -> d.eyes().isMangekyoActive()).orElse(false);
        }
        return entity instanceof SparringNinja ninja && ninja.mangekyoLook() != 0 && ninja.activeSharinganStage() > 0;
    }

    /** Is the Eternal Mangekyō open? */
    public static boolean isEternal(LivingEntity entity) {
        if (entity instanceof Player player) {
            return ShinobiCapability.get(player).map(d -> d.eyes().isMangekyoActive() && d.eyes().isEternal()).orElse(false);
        }
        return false;
    }

    /** Can {@code entity} use {@code ability} right now (eyes open, the ability in one of them, not blind)? */
    public static boolean canUse(LivingEntity entity, MangekyoAbility ability) {
        if (entity instanceof Player player) {
            return ShinobiCapability.get(player).map(d -> d.eyes().isMangekyoActive()
                    && (ability == null ? !d.eyes().isTransplanted() : d.eyes().hasAbility(ability))).orElse(false);
        }
        return isActive(entity);
    }

    public static float perceptionBonus(LivingEntity entity) {
        if (!isActive(entity)) {
            return 0f;
        }
        return isEternal(entity) ? ETERNAL_PERCEPTION_BONUS : PERCEPTION_BONUS;
    }

    /** How strong the abilities come out: strain weakens them, the Eternal Mangekyō strengthens them. */
    public static float powerScale(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return 1f;
        }
        EyeData eyes = ShinobiCapability.get(player).map(ShinobiData::eyes).orElse(null);
        if (eyes == null) {
            return 1f;
        }
        return eyes.isEternal() ? ETERNAL_POWER : strainPower(eyes.strain());
    }

    public static float strainPower(float strain) {
        return strain >= FAILING_STRAIN ? FAILING_POWER : strain >= WEAKEN_STRAIN ? WEAKENED_POWER : 1f;
    }

    /** Is {@code id} one of the Mangekyō's own jutsu (can't be copied, learned only with the eyes)? */
    public static boolean isMangekyoJutsu(ResourceLocation id) {
        return id != null && id.getNamespace().equals(com.noirm.Noirm.MOD_ID) && id.getPath().startsWith("mangekyo/");
    }

    // ---------------------------------------------------------------- strain

    /** One use strains the eyes (players only; the Eternal Mangekyō feels nothing). */
    public static void addStrain(LivingEntity entity, float amount) {
        if (!(entity instanceof Player player) || amount <= 0) {
            return;
        }
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (data == null || !data.eyes().hasMangekyo() || data.eyes().isEternal()) {
            return;
        }
        EyeData eyes = data.eyes();
        float before = eyes.strain();
        eyes.setStrain(before + amount * (eyes.isTransplanted() ? TRANSPLANT_STRAIN : 1f));
        data.markPowersDirty();
        int tierBefore = strainTier(before);
        int tierAfter = strainTier(eyes.strain());
        if (tierAfter > tierBefore) {
            player.displayClientMessage(Component.translatable("message.noirm.mangekyo.strain." + tierAfter)
                    .withStyle(tierAfter >= 4 ? ChatFormatting.DARK_RED : ChatFormatting.RED), false);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.8f, 0.6f);
            if (eyes.isBlind() && player instanceof ServerPlayer serverPlayer) {
                PowerVisuals.broadcast(serverPlayer);
            }
        }
    }

    /** 0 below 25 strain, then 1–5 (5 = blind). */
    public static int strainTier(float strain) {
        int tier = 0;
        for (float threshold : STRAIN_TIERS) {
            if (strain >= threshold) {
                tier++;
            }
        }
        return tier;
    }

    // ---------------------------------------------------------------- awakening

    /** Can this player's eyes become a Mangekyō (born Uchiha, 3 tomoe, not yet)? */
    public static boolean canAwaken(ShinobiData data) {
        EyeData eyes = data.eyes();
        return data.identity().hasSharingan() && eyes.sharinganStage() >= EyeData.MAX_STAGE && !eyes.hasMangekyo() && !eyes.isTransplanted();
    }

    /**
     * {@code witness} saw {@code dead} fall (the Sharingan's comrade check already passed). A
     * devoted crew mate or another player, close by, opens a 3-tomoe Uchiha's Mangekyō.
     */
    public static boolean onLoss(Player witness, ShinobiData data, LivingEntity dead) {
        if (!canAwaken(data) || witness.distanceTo(dead) > LOSS_RANGE) {
            return false;
        }
        if (dead instanceof Player) {
            return awaken(witness, data, Route.PLAYER);
        }
        if (dead instanceof SparringNinja ninja && witness.getUUID().equals(ninja.leaderId()) && ninja.respect() >= BOND_RESPECT) {
            return awaken(witness, data, Route.BOND);
        }
        return false;
    }

    /** A near-death awakening with {@code resolveBefore} Resolve: at 3 tomoe and 95+, the Mangekyō. */
    public static boolean onResolveAwakening(Player player, ShinobiData data, float resolveBefore) {
        return resolveBefore >= RESOLVE_AWAKENING && canAwaken(data) && awaken(player, data, Route.RESOLVE);
    }

    /** Opens the Mangekyō: a pattern, two abilities and a Susanoo colour of its own. */
    public static boolean awaken(Player player, ShinobiData data, Route route) {
        if (!canAwaken(data)) {
            return false;
        }
        RandomSource random = player.getRandom();
        EyeData eyes = data.eyes();
        MangekyoAbility left = MangekyoAbility.roll(random, null);
        MangekyoAbility right = MangekyoAbility.roll(random, left);
        eyes.setMangekyo(true);
        eyes.setPattern(random.nextInt(PATTERNS));
        eyes.setDonorPattern(-1);
        eyes.setAbilities(left, right);
        eyes.setSusanooColor(SUSANOO_COLORS[random.nextInt(SUSANOO_COLORS.length)]);
        eyes.setStrain(0f);
        eyes.setActive(true);
        eyes.setFreeTicks(FREE_TICKS);
        syncJutsu(data);
        data.markPowersDirty();
        player.sendSystemMessage(Component.translatable("message.noirm.mangekyo.awakened." + route.key()).withStyle(ChatFormatting.DARK_RED));
        player.sendSystemMessage(Component.translatable("message.noirm.mangekyo.abilities",
                Component.translatable("pattern.noirm.mangekyo." + eyes.pattern()),
                Component.translatable(com.noirm.dojutsu.Sharingan.jutsuKey(left.jutsu())),
                Component.translatable(com.noirm.dojutsu.Sharingan.jutsuKey(right.jutsu()))).withStyle(ChatFormatting.RED));
        moment(player, Component.translatable("title.noirm.mangekyo"), Component.translatable("subtitle.noirm.mangekyo." + route.key()),
                eyes.susanooColor());
        return true;
    }

    /**
     * Makes the jutsu book match the eyes: learns (and equips, if there's room) each eye's
     * ability and Susanoo (both eyes needed), forgets Mangekyō jutsu the eyes no longer carry.
     */
    public static void syncJutsu(ShinobiData data) {
        EyeData eyes = data.eyes();
        java.util.Set<ResourceLocation> wanted = new java.util.LinkedHashSet<>();
        if (eyes.hasMangekyo()) {
            if (eyes.left() != null) {
                wanted.add(eyes.left().jutsu());
            }
            if (eyes.right() != null) {
                wanted.add(eyes.right().jutsu());
            }
            if (!eyes.isTransplanted()) {
                wanted.add(SUSANOO);
            }
        }
        for (ResourceLocation id : java.util.List.copyOf(data.jutsu().known())) {
            if (isMangekyoJutsu(id) && !wanted.contains(id)) {
                data.jutsu().forget(id);
            }
        }
        for (ResourceLocation id : wanted) {
            if (JutsuData.get(id) != null) {
                data.jutsu().learn(id, false);
                data.jutsu().equipIfRoom(id);
            }
        }
        data.markPowersDirty();
    }

    /** Takes the Mangekyō away (back to a 3-tomoe Sharingan); used by the command. */
    public static void clear(ShinobiData data) {
        data.eyes().setMangekyo(false);
        data.eyes().setTransplanted(false);
        syncJutsu(data);
    }

    /** The big moment: a title, deep sounds, a dark flash in the Susanoo colour and a black ring. */
    static void moment(Player player, Component title, Component subtitle, int color) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.9f, 0.6f);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 0.5f);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2f, 0.7f);
        if (player.level() instanceof ServerLevel level) {
            Vec3 eyesAt = player.getEyePosition();
            Vfx.flash(level, eyesAt, new Vfx.Palette(0xFFD0D0, 0xC00010, 0x100000, color), 1.6f);
            Vfx.burst(level, Vfx.ring(0xB00010, 4f, 18), player.position().add(0, 0.05, 0), 1, 0, 0);
            Vfx.burst(level, Vfx.ring(color, 6f, 24), player.position().add(0, 0.1, 0), 1, 0, 0);
            Vfx.radial(level, Vfx.smoke(0x0A0005, 0.8f, 30), player.position().add(0, 1, 0), 24, 0.25);
            Vfx.quake(level, player.position(), 0.5f, 16);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.connection != null) {
                serverPlayer.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
                serverPlayer.connection.send(new ClientboundSetTitleTextPacket(title.copy().withStyle(ChatFormatting.DARK_RED)));
                serverPlayer.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            }
            PowerVisuals.broadcast(serverPlayer);
        }
    }

    // ---------------------------------------------------------------- how it looks

    /** The eye look sent to clients: 0 = no Mangekyō, else pattern+1, the donor's (+1) in bits 8–15. */
    public static int look(int pattern, int donorPattern) {
        return (pattern + 1) | ((donorPattern + 1) << 8);
    }

    public static int lookPattern(int look) {
        return (look & 0xFF) - 1;
    }

    public static int lookDonor(int look) {
        return ((look >> 8) & 0xFF) - 1;
    }

    /** A player's look while the Mangekyō is open (else 0). */
    public static int look(EyeData eyes) {
        return eyes.isMangekyoActive() ? look(eyes.pattern(), eyes.isEternal() ? eyes.donorPattern() : -1) : 0;
    }

    static float clampStrain(float strain) {
        return Mth.clamp(strain, 0f, MAX_STRAIN);
    }
}
