package com.noirm.biju;

import com.noirm.combat.Perception;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Your bond with the beast sealed in you (-100 … 100). In canon, Naruto goes from a prisoner
 * the Nine-Tails hates, through borrowing its chakra in desperate moments and drawing more and
 * more tails (each one more powerful and more dangerous), to true partnership and the full
 * Tailed Beast Mode. Here:
 * <ul>
 *   <li><b>Requests</b>: now and then the beast asks for something (defeat enemies, let it out,
 *       defeat someone stronger than you, an offering, some peace). Do it in time: the bond
 *       grows. Ignore it: the bond suffers.</li>
 *   <li><b>Chakra</b>: sneak + the cloak key asks it for chakra. Whether it gives depends on the
 *       bond. A beast that likes you also saves you when you're about to fall.</li>
 *   <li><b>Tails</b>: you can only safely draw as many tails as it trusts you with
 *       ({@link #safeTails}); more than that and it may seize control.</li>
 *   <li><b>Unlocking</b>: fighting in the cloak earns tail mastery; each new tail also needs
 *       enough bond ({@link #bondForTail}).</li>
 * </ul>
 */
public final class BeastBond {
    /** Ticks between the beast's requests (about every 8 minutes, give or take). */
    public static final int REQUEST_INTERVAL = 20 * 60 * 8;
    /** Time to fulfil a request. */
    public static final int REQUEST_TIME = 20 * 60 * 10;
    public static final int CHAKRA_COOLDOWN = 20 * 60 * 5;
    /** Tail mastery needed for the next tail, per tail already unlocked. */
    public static final float TAIL_XP_PER_TAIL = 90f;

    public enum Kind { DEFEAT, RELEASE, STRONG, OFFER, REST }

    /** A request: {@code goal} is a count (kills, seconds) or unused; {@code item} for offerings. */
    public record Request(Kind kind, int goal, int progress, long deadline, String item) {
        public Request withProgress(int value) {
            return new Request(kind, goal, value, deadline, item);
        }

        public boolean done() {
            return progress >= goal;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Kind", kind.name());
            tag.putInt("Goal", goal);
            tag.putInt("Progress", progress);
            tag.putLong("Deadline", deadline);
            tag.putString("Item", item);
            return tag;
        }

        static Request load(CompoundTag tag) {
            try {
                return new Request(Kind.valueOf(tag.getString("Kind")), tag.getInt("Goal"), tag.getInt("Progress"),
                        tag.getLong("Deadline"), tag.getString("Item"));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private BeastBond() {}

    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.noirm.Noirm.MOD_ID)
    public static final class BeastBondEvents {
        private BeastBondEvents() {}

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void death(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
            if (event.getSource().getEntity() instanceof ServerPlayer player) {
                onKill(player, event.getEntity());
            }
        }
    }

    /** Beasts start out resentful; the Nine-Tails and the One-Tail most of all. */
    public static int startingBond(int tails) {
        return switch (tails) {
            case 9 -> -60;
            case 1 -> -40;
            case 0 -> 0;
            default -> -20;
        };
    }

    /** Tails you can draw without the beast fighting you for control. */
    public static int safeTails(int bond) {
        if (bond < -50) {
            return 1;
        }
        if (bond < 0) {
            return 2;
        }
        if (bond < 30) {
            return 4;
        }
        if (bond < 60) {
            return 6;
        }
        return bond < 80 ? 8 : BijuData.MAX_TAILS;
    }

    /** Bond needed before tail {@code n} can be unlocked. The last tails are partnership. */
    public static int bondForTail(int n) {
        if (n <= 3) {
            return -100;
        }
        if (n <= 6) {
            return 0;
        }
        return n <= 8 ? 40 : 80;
    }

    /** Full Tailed Beast Mode: every tail drawn, as partners. No burn, no takeover. */
    public static boolean fullMode(BijuData biju) {
        return biju.cloakStage() == 1 && biju.cloakTails() >= biju.tails() && biju.bond() >= 80;
    }

    public static void say(Player player, BijuData biju, String key, Object... args) {
        Component name = BijuSystem.beastName(biju.tails());
        player.sendSystemMessage(Component.translatable("message.noirm.beast.says", name,
                Component.translatable(key, args).withStyle(ChatFormatting.ITALIC)).withStyle(biju.bond() >= 30 ? ChatFormatting.GOLD : ChatFormatting.RED));
    }

    public static void changeBond(Player player, BijuData biju, int amount) {
        int before = biju.bond();
        biju.setBond(before + amount);
        ShinobiCapability.get(player).ifPresent(ShinobiData::markPowersDirty);
        // Crossing a threshold opens up (or closes) tails.
        if (safeTails(biju.bond()) > safeTails(before)) {
            say(player, biju, "message.noirm.beast.trusts_more");
        }
    }

    // ---------------------------------------------------------------- every second

    /** Once a second for every jinchūriki: requests, tail mastery, takeovers, rescues. */
    public static void tickSecond(Player player, ShinobiData data) {
        BijuData biju = data.biju();
        if (!biju.isJinchuriki()) {
            return;
        }
        long now = player.level().getGameTime();
        RandomSource random = player.getRandom();
        if (biju.chakraCooldown() > 0) {
            biju.setChakraCooldown(biju.chakraCooldown() - 20);
        }
        boolean fighting = player.getLastHurtMob() != null && player.tickCount - player.getLastHurtMobTimestamp() < 200
                || player.getLastHurtByMob() != null && player.tickCount - player.getLastHurtByMobTimestamp() < 200;

        // Requests.
        Request request = biju.request();
        if (request == null && biju.nextRequestAt() == 0) {
            biju.setNextRequestAt(now + REQUEST_INTERVAL / 2 + random.nextInt(REQUEST_INTERVAL));
        } else if (request == null && now >= biju.nextRequestAt()) {
            newRequest(player, biju, now, random);
        } else if (request != null) {
            if (request.kind() == Kind.RELEASE && biju.cloakStage() > 0) {
                request = request.withProgress(request.progress() + 1);
                biju.setRequest(request);
            }
            if (request.kind() == Kind.REST && biju.cloakStage() > 0) {
                fail(player, biju, "message.noirm.beast.rest_broken");
            } else if (request.kind() == Kind.REST && now >= request.deadline()) {
                fulfil(player, biju, now);
            } else if (request.done()) {
                fulfil(player, biju, now);
            } else if (now >= request.deadline()) {
                fail(player, biju, "message.noirm.beast.request_failed");
            }
        }

        // Tail mastery: fighting with the cloak on.
        if (biju.cloakStage() == 1 && fighting) {
            biju.setTailXp(biju.tailXp() + biju.cloakTails());
            int next = biju.unlockedTails() + 1;
            if (next <= biju.tails() && biju.tailXp() >= TAIL_XP_PER_TAIL * biju.unlockedTails()) {
                if (biju.bond() >= bondForTail(next)) {
                    biju.setTailXp(0);
                    biju.setUnlockedTails(next);
                    say(player, biju, "message.noirm.beast.tail_unlocked", next);
                    player.level().playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1f, 0.8f);
                    data.markPowersDirty();
                } else if (player.tickCount % 400 == 0) {
                    say(player, biju, "message.noirm.beast.not_trusted");
                }
            }
        }

        // Too many tails for the trust between you: it may take over.
        // Willpower (WIL) lets you hold a few more tails before it pushes back.
        int safe = safeTails(biju.bond()) + com.noirm.training.Supplementary.extraSafeTails(data);
        if (biju.cloakStage() == 1 && !fullMode(biju) && biju.cloakTails() > safe) {
            float chance = 0.04f * (biju.cloakTails() - safe);
            if (random.nextFloat() < chance) {
                say(player, biju, "message.noirm.beast.seizes");
                BijuSystem.beastTakesOver(player, data, 200);
                changeBond(player, biju, -2);
            } else {
                com.noirm.training.Supplementary.onResistBeast(player, data, biju.cloakTails() - safe);
            }
        }

        // A beast that cares about you won't let you die; one that hates you takes its chance.
        boolean dying = player.getHealth() < player.getMaxHealth() * 0.25f && fighting;
        if (dying && biju.bond() >= 40 && biju.chakraCooldown() == 0) {
            biju.setChakraCooldown(CHAKRA_COOLDOWN);
            data.setChakra(data.getMaxChakra());
            player.heal(player.getMaxHealth() * 0.3f);
            say(player, biju, "message.noirm.beast.saves");
            burst(player, biju);
        } else if (dying && biju.bond() < -40 && biju.cloakStage() == 0 && random.nextFloat() < 0.05f) {
            say(player, biju, "message.noirm.beast.takes_chance");
            BijuSystem.beastTakesOver(player, data, 200);
        }
    }

    private static void newRequest(Player player, BijuData biju, long now, RandomSource random) {
        Kind kind = Kind.values()[random.nextInt(Kind.values().length)];
        Request request = switch (kind) {
            case DEFEAT -> new Request(kind, 5 + random.nextInt(6), 0, now + REQUEST_TIME, "");
            case RELEASE -> new Request(kind, 30, 0, now + REQUEST_TIME, "");
            case STRONG -> new Request(kind, 1, 0, now + REQUEST_TIME, "");
            case OFFER -> new Request(kind, 1, 0, now + REQUEST_TIME, offering(biju.tails(), random));
            case REST -> new Request(kind, 1, 0, now + REQUEST_TIME / 2, "");
        };
        biju.setRequest(request);
        Object arg = kind == Kind.OFFER ? itemName(request.item()) : request.goal();
        say(player, biju, "message.noirm.beast.request." + kind.name().toLowerCase(java.util.Locale.ROOT), arg);
        player.level().playSound(null, player.blockPosition(), SoundEvents.RAVAGER_AMBIENT, SoundSource.PLAYERS, 0.8f, 0.6f);
    }

    /** What each beast likes to be given. */
    private static String offering(int tails, RandomSource random) {
        Item[] choices = switch (tails) {
            case 9 -> new Item[]{Items.COOKED_BEEF, Items.GOLDEN_APPLE, Items.COOKED_PORKCHOP};
            case 1 -> new Item[]{Items.SAND, Items.SANDSTONE, Items.TERRACOTTA};
            case 3, 6 -> new Item[]{Items.TROPICAL_FISH, Items.COD, Items.SALMON};
            case 2, 4 -> new Item[]{Items.BLAZE_POWDER, Items.MAGMA_CREAM, Items.COAL};
            default -> new Item[]{Items.HONEY_BOTTLE, Items.SWEET_BERRIES, Items.COOKED_CHICKEN};
        };
        Item item = choices[random.nextInt(choices.length)];
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id == null ? "minecraft:cooked_beef" : id.toString();
    }

    private static Component itemName(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
        return item == null ? Component.literal(id) : new ItemStack(item).getHoverName();
    }

    private static void fulfil(Player player, BijuData biju, long now) {
        biju.setRequest(null);
        biju.setNextRequestAt(now + REQUEST_INTERVAL);
        changeBond(player, biju, 8 + player.getRandom().nextInt(6));
        say(player, biju, "message.noirm.beast.pleased");
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 0.7f);
    }

    private static void fail(Player player, BijuData biju, String key) {
        biju.setRequest(null);
        biju.setNextRequestAt(player.level().getGameTime() + REQUEST_INTERVAL);
        changeBond(player, biju, -6);
        say(player, biju, key);
    }

    // ---------------------------------------------------------------- events

    /** You defeated {@code victim}: progress on DEFEAT and STRONG requests. */
    public static void onKill(Player player, LivingEntity victim) {
        ShinobiCapability.get(player).ifPresent(data -> {
            BijuData biju = data.biju();
            Request request = biju.request();
            if (request == null || !(victim instanceof net.minecraft.world.entity.monster.Enemy || victim instanceof com.noirm.entity.SparringNinja)) {
                return;
            }
            if (request.kind() == Kind.DEFEAT) {
                biju.setRequest(request.withProgress(request.progress() + 1));
            } else if (request.kind() == Kind.STRONG && Perception.basePower(victim) >= Perception.basePower(player)) {
                biju.setRequest(request.withProgress(1));
            }
        });
    }

    /**
     * Sneak + the cloak key with the cloak off: offer what you're holding if it's what the beast
     * asked for, otherwise ask it for chakra.
     */
    public static void sneakAction(Player player, ShinobiData data) {
        BijuData biju = data.biju();
        Request request = biju.request();
        ItemStack held = player.getMainHandItem();
        ResourceLocation heldId = ForgeRegistries.ITEMS.getKey(held.getItem());
        if (request != null && request.kind() == Kind.OFFER && heldId != null && heldId.toString().equals(request.item())) {
            held.shrink(1);
            biju.setRequest(request.withProgress(1));
            say(player, biju, "message.noirm.beast.offer_taken");
            return;
        }
        askForChakra(player, data);
    }

    /** Ask the beast for chakra. Returns true if it gave some. */
    public static boolean askForChakra(Player player, ShinobiData data) {
        BijuData biju = data.biju();
        if (biju.chakraCooldown() > 0) {
            say(player, biju, "message.noirm.beast.chakra_wait", (biju.chakraCooldown() + 19) / 20);
            return false;
        }
        if (biju.bond() < -20) {
            say(player, biju, "message.noirm.beast.chakra_refused");
            changeBond(player, biju, -1);
            return false;
        }
        float share = biju.bond() >= 50 ? 1f : biju.bond() >= 0 ? 0.6f : 0.35f;
        data.setChakra(Math.min(data.getMaxChakra(), data.getChakra() + data.getMaxChakra() * share));
        biju.setChakraCooldown(CHAKRA_COOLDOWN);
        if (biju.bond() < 20) {
            changeBond(player, biju, -1); // grudging: it doesn't like being used
        }
        say(player, biju, biju.bond() >= 50 ? "message.noirm.beast.chakra_gladly" : "message.noirm.beast.chakra_given");
        burst(player, biju);
        return true;
    }

    private static void burst(Player player, BijuData biju) {
        if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            com.noirm.vfx.Vfx.Palette palette = BijuSystem.cloakPalette(biju);
            com.noirm.vfx.Vfx.flash(level, player.position().add(0, 1, 0), palette, 2.5f);
            com.noirm.vfx.Vfx.aura(level, player.position(), player.getBbWidth(), player.getBbHeight(), palette, 30);
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 0.6f);
        }
    }
}
