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
    /**
     * A ninja this weak (speed and taijutsu) is never a jinchūriki: beating one would set a tailed
     * beast loose on a beginner (a playtest: a danger-12 rogue next to the world spawn held the
     * Nine-Tails). Stronger ninjas hold at most one tail per {@link #NPC_STRENGTH_PER_TAIL} of strength.
     */
    public static final float NPC_JINCHURIKI_MIN_STRENGTH = 20f;
    public static final float NPC_STRENGTH_PER_TAIL = 15f;

    /** How many tails a ninja of {@code strength} may hold at most (0 = never a jinchūriki). */
    public static int npcMaxTails(float strength) {
        if (strength < NPC_JINCHURIKI_MIN_STRENGTH) {
            return 0;
        }
        return Math.max(1, Math.min(BijuData.MAX_TAILS, (int) (strength / NPC_STRENGTH_PER_TAIL)));
    }

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

    /**
     * The cloak key. Off: draw the first tail. On: draw one more tail, up to what you've unlocked
     * (then the key lets the cloak go). Sneaking: let the cloak go, or (cloak off) offer the
     * beast what it asked for / ask it for chakra.
     */
    public static void toggleCloak(Player player) {
        ShinobiCapability.get(player).ifPresent(data -> {
            BijuData biju = data.biju();
            if (!biju.isJinchuriki()) {
                player.displayClientMessage(Component.translatable("message.noirm.biju.none"), true);
            } else if (biju.cloakStage() == 2) {
                player.displayClientMessage(Component.translatable("message.noirm.biju.no_control"), true);
            } else if (player.isShiftKeyDown()) {
                if (biju.cloakStage() == 1) {
                    endCloak(player, data);
                } else {
                    BeastBond.sneakAction(player, data);
                }
            } else if (biju.cloakStage() == 1) {
                int max = Math.min(biju.unlockedTails(), biju.tails());
                if (biju.cloakTails() < max) {
                    drawTail(player, data, biju.cloakTails() + 1);
                } else {
                    endCloak(player, data);
                }
            } else if (biju.cooldownTicks() > 0) {
                player.displayClientMessage(Component.translatable("message.noirm.biju.cooldown", (biju.cooldownTicks() + 19) / 20), true);
            } else {
                startCloak(player, data, 1, CLOAK_BASE_TICKS + CLOAK_TICKS_PER_TAIL * biju.tails());
                drawTail(player, data, 1);
            }
        });
    }

    /** Draws the cloak up to {@code tails} tails: stronger, louder, and darker past three. */
    public static void drawTail(Player player, ShinobiData data, int tails) {
        BijuData biju = data.biju();
        biju.setCloakTails(tails);
        applySpeed(player, biju);
        float pitch = 1.5f - 0.1f * tails;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.5f + 0.1f * tails, pitch);
        if (player.level() instanceof ServerLevel level) {
            com.noirm.vfx.Vfx.Palette palette = cloakPalette(biju);
            com.noirm.vfx.Vfx.shockwave(level, player.position().add(0, 0.2, 0), palette, 1.5f + 0.5f * tails);
            com.noirm.vfx.Vfx.flash(level, player.position().add(0, 1, 0), palette, 1f + 0.3f * tails);
        }
        String key = BeastBond.fullMode(biju) ? "message.noirm.biju.full_mode" : tails >= 4 ? "message.noirm.biju.tails_v2" : "message.noirm.biju.tails";
        player.displayClientMessage(Component.translatable(key, tails), true);
        data.markPowersDirty();
        if (player instanceof ServerPlayer serverPlayer) {
            PowerVisuals.broadcast(serverPlayer);
        }
    }

    /**
     * The cloak's colours: version 1 (1–3 tails) a bubbling orange-red; version 2 (4+) dark
     * crimson and black; the beast in control blood red; full Tailed Beast Mode gold.
     */
    public static com.noirm.vfx.Vfx.Palette cloakPalette(BijuData biju) {
        if (biju.cloakStage() == 2) {
            return new com.noirm.vfx.Vfx.Palette(0xFF5050, 0xA00000, 0x140000);
        }
        if (BeastBond.fullMode(biju)) {
            return new com.noirm.vfx.Vfx.Palette(0xFFFFE0, 0xFFC020, 0xB05000);
        }
        if (biju.cloakTails() >= 4) {
            return new com.noirm.vfx.Vfx.Palette(0xFF6040, 0x8A0010, 0x0A0005);
        }
        return new com.noirm.vfx.Vfx.Palette(0xFFF0B0, 0xFF5A10, 0x7A1A00);
    }

    private static void applySpeed(Player player, BijuData biju) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_ID);
            double amount = biju.cloakStage() == 2 ? 0.5 : 0.12 + 0.08 * biju.cloakTails() + (BeastBond.fullMode(biju) ? 0.3 : 0);
            speed.addTransientModifier(new AttributeModifier(SPEED_ID, "noirm_cloak", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /** The awakening for jinchūriki: the beast's cloak, out of your control, for {@code ticks}. */
    public static void beastTakesOver(Player player, ShinobiData data, int ticks) {
        startCloak(player, data, 2, ticks);
        player.displayClientMessage(Component.translatable("message.noirm.biju.takeover"), false);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.7f, 0.6f);
    }

    private static void startCloak(Player player, ShinobiData data, int stage, int ticks) {
        data.biju().setCloak(stage, ticks);
        if (stage == 2) {
            data.biju().setCloakTails(Math.max(data.biju().cloakTails(), Math.min(data.biju().tails(), 4)));
        }
        applySpeed(player, data.biju());
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
        if (!BeastBond.fullMode(biju)) {
            biju.setCloak(biju.cloakStage(), biju.cloakTicks() - 1); // full Tailed Beast Mode lasts as long as you like
        }
        if (player.level() instanceof ServerLevel level && player.tickCount % 2 == 0) {
            com.noirm.vfx.Vfx.Palette palette = cloakPalette(biju);
            com.noirm.vfx.Vfx.aura(level, player.position(), player.getBbWidth(), player.getBbHeight(), palette, 4 + biju.cloakTails() * 2);
            drawChakraTails(level, player, biju, palette);
            if (biju.cloakStage() == 2 || biju.cloakTails() >= 4) {
                level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 2, 0.3, 0.1, 0.3, 0.01);
            }
        }
        // The beast's chakra burns: in its control, and in version 2 (4+ tails) unless you're partners.
        boolean burns = biju.cloakStage() == 2 || biju.cloakTails() >= 4 && !BeastBond.fullMode(biju);
        if (burns && player.tickCount % 40 == 0 && player.getHealth() > 2f) {
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

    /** Tails of chakra drawn (players), or 1 for a cloaked ninja. */
    private static int drawnTails(LivingEntity entity) {
        if (entity instanceof Player player) {
            return ShinobiCapability.get(player).map(d -> d.biju().cloakTails()).orElse(0);
        }
        return cloakStage(entity) > 0 ? 1 : 0;
    }

    private static boolean fullMode(LivingEntity entity) {
        return entity instanceof Player player && ShinobiCapability.get(player).map(d -> BeastBond.fullMode(d.biju())).orElse(false);
    }

    /** +12% damage per tail drawn; the beast in control hits harder still; full mode ×1.6 on top. */
    public static float damageMultiplier(LivingEntity entity) {
        int stage = cloakStage(entity);
        if (stage == 0) {
            return 1f;
        }
        float base = (stage == 2 ? 1.5f : 1f) + 0.12f * drawnTails(entity);
        return fullMode(entity) ? base * 1.6f : base;
    }

    public static float perceptionMultiplier(LivingEntity entity) {
        int stage = cloakStage(entity);
        if (stage == 0) {
            return 1f;
        }
        return (stage == 2 ? 1.2f : 1f) + 0.06f * drawnTails(entity) + (fullMode(entity) ? 0.5f : 0f);
    }

    /** Damage taken: the cloak's chakra shields you, 5% per tail (down to 40%). */
    public static float defenseMultiplier(LivingEntity entity) {
        int tails = drawnTails(entity);
        if (cloakStage(entity) == 0 || tails == 0) {
            return 1f;
        }
        return Math.max(0.4f, 1f - 0.05f * tails - (fullMode(entity) ? 0.2f : 0f));
    }

    /** Chakra tails streaming out behind the jinchūriki, one per tail drawn. */
    private static void drawChakraTails(ServerLevel level, Player player, BijuData biju, com.noirm.vfx.Vfx.Palette palette) {
        int tails = biju.cloakTails();
        if (tails <= 0) {
            return;
        }
        net.minecraft.world.phys.Vec3 back = net.minecraft.world.phys.Vec3.directionFromRotation(0, player.yBodyRot).scale(-1);
        net.minecraft.world.phys.Vec3 side = new net.minecraft.world.phys.Vec3(-back.z, 0, back.x);
        net.minecraft.world.phys.Vec3 base = player.position().add(0, 0.9, 0).add(back.scale(0.3));
        float time = player.tickCount * 0.15f;
        for (int t = 0; t < tails; t++) {
            double spread = tails == 1 ? 0 : (t / (double) (tails - 1) - 0.5) * 1.6;
            for (int i = 1; i <= 6; i++) {
                double along = i * 0.28;
                double sway = Math.sin(time + t * 1.3 + i * 0.6) * 0.15 * i;
                net.minecraft.world.phys.Vec3 at = base.add(back.scale(along)).add(side.scale(spread * along + sway)).add(0, along * 0.35, 0);
                com.noirm.vfx.Vfx.one(level, com.noirm.vfx.Vfx.glow(palette.core(), palette.mid(), 0.32f - i * 0.03f, 0.05f, 4), at,
                        net.minecraft.world.phys.Vec3.ZERO);
            }
        }
    }

    /** While the beast is in control, you can't guard. */
    public static boolean canGuard(LivingEntity entity) {
        return cloakStage(entity) != 2;
    }
}
