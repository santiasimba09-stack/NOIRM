package com.noirm.biju;

import com.noirm.Noirm;
import com.noirm.entity.TailedBeast;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuUser;
import com.noirm.player.PowerVisuals;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Jinchūriki: a tailed beast sealed inside you. Its chakra cloak (key B) makes you faster,
 * stronger and refills chakra quickly for a while. When near death, the beast may take over:
 * a second, more violent cloak that won't let you guard and burns you while it lasts.
 */
public final class BijuSystem {
    public static final int CLOAK_BASE_TICKS = 400;
    public static final int CLOAK_TICKS_PER_TAIL = 40;
    public static final int CLOAK_COOLDOWN = 1200;
    /** The beast's chakra burns its host while it has control: damage every 2 s. */
    public static final float RAGE_BURN = 1f;

    /** The jutsu every jinchūriki learns: usable only in the cloak. */
    public static final ResourceLocation BEAST_BALL = Noirm.id("biju/tailed_beast_ball");
    /** Chance a newcomer is born with a beast sealed inside. */
    public static final float BORN_JINCHURIKI_CHANCE = 0.03f;
    /** Chance a sparring ninja is secretly a jinchūriki. */
    public static final float NPC_JINCHURIKI_CHANCE = 0.05f;

    private static final UUID SPEED_ID = UUID.fromString("3c2f1b0e-9a8d-4c7b-8e6f-5d4c3b2a1908");

    private BijuSystem() {}

    /** Seals a beast with {@code tails} tails into the player (0 removes it). */
    public static void seal(Player player, ShinobiData data, int tails) {
        if (data.biju().cloakStage() > 0) {
            endCloak(player, data);
        }
        data.biju().setTails(tails);
        if (tails > 0 && JutsuData.get(BEAST_BALL) != null && data.jutsu().learn(BEAST_BALL, false)) {
            data.jutsu().equipIfRoom(BEAST_BALL);
        }
        data.markPowersDirty();
    }

    /** The beast's name: "Kurama (Nine-Tails)" and so on. */
    public static Component beastName(int tails) {
        return Component.translatable("biju.noirm." + Mth.clamp(tails, 1, BijuData.MAX_TAILS));
    }

    /**
     * A newcomer may be born a jinchūriki ({@code roll} below {@link #BORN_JINCHURIKI_CHANCE}).
     * Returns the tails sealed, or 0.
     */
    public static int maybeBornJinchuriki(Player player, ShinobiData data, float roll, RandomSource random) {
        if (roll >= BORN_JINCHURIKI_CHANCE || data.biju().isJinchuriki()) {
            return 0;
        }
        int tails = 1 + random.nextInt(BijuData.MAX_TAILS);
        seal(player, data, tails);
        player.sendSystemMessage(Component.translatable("message.noirm.biju.born", beastName(tails)).withStyle(ChatFormatting.GOLD));
        return tails;
    }

    public static void toggleCloak(ServerPlayer player) {
        ShinobiCapability.get(player).ifPresent(data -> {
            BijuData biju = data.biju();
            if (!biju.isJinchuriki()) {
                player.displayClientMessage(Component.translatable("message.noirm.biju.none"), true);
            } else if (biju.cloakStage() == 2) {
                player.displayClientMessage(Component.translatable("message.noirm.biju.no_control"), true);
            } else if (biju.cloakStage() == 1) {
                endCloak(player, data);
            } else if (biju.cooldownTicks() > 0) {
                player.displayClientMessage(Component.translatable("message.noirm.biju.cooldown", (biju.cooldownTicks() + 19) / 20), true);
            } else {
                startCloak(player, data, 1, CLOAK_BASE_TICKS + CLOAK_TICKS_PER_TAIL * biju.tails());
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.6f, 1.4f);
            }
        });
    }

    /** The awakening for jinchūriki: the beast's cloak, out of your control, for {@code ticks}. */
    public static void beastTakesOver(Player player, ShinobiData data, int ticks) {
        startCloak(player, data, 2, ticks);
        player.displayClientMessage(Component.translatable("message.noirm.biju.takeover"), false);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.7f, 0.6f);
    }

    private static void startCloak(Player player, ShinobiData data, int stage, int ticks) {
        data.biju().setCloak(stage, ticks);
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_ID);
            speed.addTransientModifier(new AttributeModifier(SPEED_ID, "noirm_cloak", stage == 2 ? 0.4 : 0.2, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        data.markPowersDirty();
        if (player instanceof ServerPlayer serverPlayer) {
            PowerVisuals.broadcast(serverPlayer);
        }
    }

    public static void endCloak(Player player, ShinobiData data) {
        if (data.biju().cloakStage() == 0) {
            return;
        }
        data.biju().setCloak(0, 0);
        data.biju().setCooldownTicks(CLOAK_COOLDOWN);
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_ID);
        }
        data.markPowersDirty();
        if (player instanceof ServerPlayer serverPlayer) {
            PowerVisuals.broadcast(serverPlayer);
        }
    }

    /** Every server tick: cloak timer, aura, the beast's burn, cooldown. */
    public static void tick(Player player, ShinobiData data) {
        BijuData biju = data.biju();
        if (biju.cloakStage() == 0) {
            if (biju.cooldownTicks() > 0) {
                biju.setCooldownTicks(biju.cooldownTicks() - 1);
                if (biju.cooldownTicks() == 0) {
                    data.markPowersDirty();
                }
            }
            return;
        }
        biju.setCloak(biju.cloakStage(), biju.cloakTicks() - 1);
        if (player.level() instanceof ServerLevel level && player.tickCount % 2 == 0) {
            com.noirm.vfx.Vfx.Palette palette = biju.cloakStage() == 2
                    ? new com.noirm.vfx.Vfx.Palette(0xFF8080, 0xB01010, 0x200000)
                    : new com.noirm.vfx.Vfx.Palette(0xFFF0C0, 0xFF7A1A, 0x6A2A00);
            com.noirm.vfx.Vfx.aura(level, player.position(), player.getBbWidth(), player.getBbHeight(), palette, 6);
            if (biju.cloakStage() == 2) {
                level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 2, 0.3, 0.1, 0.3, 0.01);
            }
        }
        if (biju.cloakStage() == 2 && player.tickCount % 40 == 0 && player.getHealth() > 2f) {
            player.hurt(player.damageSources().magic(), RAGE_BURN);
        }
        if (biju.cloakTicks() <= 0) {
            endCloak(player, data);
        }
    }

    public static int cloakStage(LivingEntity entity) {
        if (entity instanceof Player player) {
            return ShinobiCapability.get(player).map(d -> d.biju().cloakStage()).orElse(0);
        }
        return entity instanceof JutsuUser user ? user.bijuCloakStage() : 0;
    }

    /** Can use tailed-beast jutsu: a jinchūriki in the cloak, or a tailed beast itself. */
    public static boolean hasBeastChakra(LivingEntity entity) {
        return cloakStage(entity) > 0 || entity instanceof TailedBeast beast && !beast.isSubdued();
    }

    /** Cloak: ×1.3 damage; the beast in control: ×1.6. */
    public static float damageMultiplier(LivingEntity entity) {
        int stage = cloakStage(entity);
        return stage == 2 ? 1.6f : stage == 1 ? 1.3f : 1f;
    }

    public static float perceptionMultiplier(LivingEntity entity) {
        int stage = cloakStage(entity);
        return stage == 2 ? 1.3f : stage == 1 ? 1.15f : 1f;
    }

    /** While the beast is in control, you can't guard. */
    public static boolean canGuard(LivingEntity entity) {
        return cloakStage(entity) != 2;
    }
}
