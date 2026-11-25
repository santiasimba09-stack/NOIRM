package com.noirm.aburame;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/** The small Aburame items: the Kidaichū Egg, the Queen, the bikōchū egg and beetle, and wild-grass salad. */
public final class AburameItems {
    /** The bikōchū tracks its imprinted scent for 3 in-game days. */
    public static final long BIKOCHU_TICKS = 24000L * 3;

    private AburameItems() {}

    private static void tip(List<Component> tips, String key) {
        tips.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }

    /** Hatch it into your colony: the kidaichū species (needs a free species slot and Control 30). */
    public static class KidaichuEgg extends Item {
        public KidaichuEgg(Properties p) {
            super(p);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide()) {
                return InteractionResultHolder.success(stack);
            }
            ColonyData colony = Colony.of(player);
            String why = colony == null ? "message.noirm.colony.none" : Species.addBlocker(player, colony, Species.KIDAICHU);
            if (why != null) {
                player.displayClientMessage(Component.translatable(why), true);
                return InteractionResultHolder.fail(stack);
            }
            colony.hosted().add(Species.KIDAICHU);
            colony.addBody(Species.KIDAICHU, 300);
            stack.shrink(1);
            player.displayClientMessage(Component.translatable("message.noirm.colony.species_added",
                    Component.translatable(Species.KIDAICHU.translationKey())), false);
            Colony.changed(player);
            return InteractionResultHolder.consume(stack);
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
            tip(tips, "item.noirm.kidaichu_egg.desc");
        }
    }

    /** A Queen raised in the Breeding Jar: bond with her to summon the Giant Insect. */
    public static class QueenCocoon extends Item {
        public QueenCocoon(Properties p) {
            super(p);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide()) {
                return InteractionResultHolder.success(stack);
            }
            ColonyData colony = Colony.of(player);
            if (colony == null) {
                player.displayClientMessage(Component.translatable("message.noirm.colony.none"), true);
                return InteractionResultHolder.fail(stack);
            }
            colony.setQueen(true);
            stack.shrink(1);
            player.displayClientMessage(Component.translatable("message.noirm.colony.queen"), false);
            Colony.changed(player);
            return InteractionResultHolder.consume(stack);
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
            tip(tips, "item.noirm.queen_cocoon.desc");
        }
    }

    /** A bikōchū egg: use it on a creature (its smell is the first the beetle knows) to hatch a tracker. */
    public static class BikochuEgg extends Item {
        public BikochuEgg(Properties p) {
            super(p);
        }

        @Override
        public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
            if (!player.level().isClientSide()) {
                ItemStack beetle = new ItemStack(AburameRegistry.BIKOCHU.get());
                beetle.getOrCreateTag().putUUID("Scent", target.getUUID());
                beetle.getTag().putString("Name", target.getName().getString());
                beetle.getTag().putLong("Until", player.level().getGameTime() + BIKOCHU_TICKS);
                stack.shrink(1);
                if (!player.getInventory().add(beetle)) {
                    player.drop(beetle, false);
                }
                player.displayClientMessage(Component.translatable("message.noirm.colony.bikochu_imprinted", target.getName()), false);
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
            tip(tips, "item.noirm.bikochu_egg.desc");
        }
    }

    /** A hatched bikōchū: use it and it points you to the scent it knows, anywhere in this world. */
    public static class Bikochu extends Item {
        public Bikochu(Properties p) {
            super(p);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide() || !stack.hasTag() || !stack.getTag().hasUUID("Scent")) {
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
            }
            if (level.getGameTime() > stack.getTag().getLong("Until")) {
                player.displayClientMessage(Component.translatable("message.noirm.colony.bikochu_tired"), true);
                return InteractionResultHolder.fail(stack);
            }
            UUID id = stack.getTag().getUUID("Scent");
            Entity found = ((ServerLevel) level).getEntity(id);
            if (found == null && level.getServer() != null) {
                found = level.getServer().getPlayerList().getPlayer(id);
            }
            if (found == null || found.level() != level) {
                player.displayClientMessage(Component.translatable("message.noirm.colony.bikochu_lost"), true);
                return InteractionResultHolder.fail(stack);
            }
            Vec3 to = found.position().subtract(player.position());
            String dir = compass(to);
            player.displayClientMessage(Component.translatable("message.noirm.colony.bikochu_points",
                    stack.getTag().getString("Name"), Math.round(to.length()), dir), true);
            return InteractionResultHolder.consume(stack);
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
            tip(tips, "item.noirm.bikochu.desc");
            if (stack.hasTag() && stack.getTag().contains("Name")) {
                tips.add(Component.literal(stack.getTag().getString("Name")).withStyle(ChatFormatting.GOLD));
            }
        }
    }

    /** N, NE, E… for a direction. */
    public static String compass(Vec3 to) {
        double deg = Math.toDegrees(Math.atan2(to.x, -to.z));
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return names[(int) Math.floorMod(Math.round(deg / 45.0), 8)];
    }

    /** Shino's favourite: wild-grass salad. +10 Vigour for an Aburame's colony. */
    public static class Salad extends Item {
        public Salad(Properties p) {
            super(p);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            if (!level.isClientSide()) {
                ColonyData colony = Colony.of(entity);
                if (colony != null) {
                    colony.setVigour(colony.vigour() + ColonyGrowth.VIGOUR_SALAD);
                    Colony.changed(entity);
                }
            }
            return super.finishUsingItem(stack, level, entity);
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
            tip(tips, "item.noirm.wild_grass_salad.desc");
        }
    }
}
