package com.noirm.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.noirm.Noirm;
import com.noirm.data.CodecJsonLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;

/**
 * {@code /noirm kit <kit>}: a ready-made bundle of items (an outfit, a weapon set, eyes…), and
 * {@code /noirm kit list}. Kits are data: {@code data/<ns>/kits/<id>.json} with
 * {@code {"wear": true, "items": [{"item": "noirm:kunai", "count": 16}, …]}}; with {@code wear},
 * armor goes straight on (what you had on goes into your bag). Names: {@code kit.<ns>.<id>}.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class KitCommand {
    public record Entry(ResourceLocation item, int count) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(Entry::item),
                Codec.intRange(1, 6400).optionalFieldOf("count", 1).forGetter(Entry::count)
        ).apply(i, Entry::new));
    }

    public record Kit(List<Entry> items, boolean wear) {
        public static final Codec<Kit> CODEC = RecordCodecBuilder.create(i -> i.group(
                Entry.CODEC.listOf().fieldOf("items").forGetter(Kit::items),
                Codec.BOOL.optionalFieldOf("wear", false).forGetter(Kit::wear)
        ).apply(i, Kit::new));
    }

    private static Map<ResourceLocation, Kit> kits = Map.of();

    private KitCommand() {}

    public static Map<ResourceLocation, Kit> all() {
        return kits;
    }

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CodecJsonLoader<>("kits", Kit.CODEC, loaded -> kits = loaded));
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("noirm").then(Commands.literal("kit")
                .executes(ctx -> list(ctx.getSource()))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.argument("kit", StringArgumentType.greedyString()).requires(s -> s.hasPermission(Cmd.CHEATS))
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Cmd.shortIds(kits.keySet()), builder))
                        .executes(ctx -> give(ctx.getSource(), StringArgumentType.getString(ctx, "kit"))))));
    }

    static Component name(ResourceLocation id) {
        return Component.translatable("kit." + id.getNamespace() + "." + id.getPath());
    }

    static int list(CommandSourceStack source) {
        MutableComponent text = Component.translatable("command.noirm.kit.list").withStyle(ChatFormatting.GOLD);
        for (ResourceLocation id : kits.keySet().stream().sorted().toList()) {
            String run = "/noirm kit " + (id.getNamespace().equals(Noirm.MOD_ID) ? id.getPath() : id.toString());
            text.append("\n ").append(name(id).copy().withStyle(s -> s.withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, run))))
                    .append(Component.literal(" " + run.substring("/noirm kit ".length()) + " · " + kits.get(id).items().size())
                            .withStyle(ChatFormatting.DARK_GRAY));
        }
        source.sendSuccess(() -> text, false);
        return kits.size();
    }

    static int give(CommandSourceStack source, String name) throws CommandSyntaxException {
        ResourceLocation id = Cmd.id(name);
        Kit kit = id == null ? null : kits.get(id);
        if (kit == null) {
            return Cmd.fail(source, "command.noirm.kit.unknown", name);
        }
        ServerPlayer player = Cmd.player(source);
        int given = 0;
        java.util.Set<EquipmentSlot> dressed = java.util.EnumSet.noneOf(EquipmentSlot.class);
        for (Entry entry : kit.items()) {
            Item item = ForgeRegistries.ITEMS.getValue(entry.item());
            if (item == null || entry.item().getPath().equals("air")) {
                Noirm.LOGGER.warn("Kit {} names an unknown item {}", id, entry.item());
                continue;
            }
            if (kit.wear() && item instanceof ArmorItem armor && dressed.add(armor.getEquipmentSlot())) {
                // The first piece for each slot goes on; spares for the same slot go in the bag.
                EquipmentSlot slot = armor.getEquipmentSlot();
                ItemStack old = player.getItemBySlot(slot);
                player.setItemSlot(slot, new ItemStack(item));
                if (!old.isEmpty()) {
                    handOver(player, old);
                }
            } else {
                // In full stacks (kunai stack to 16, so 32 kunai are two stacks).
                for (int left = entry.count(); left > 0; left -= item.getMaxStackSize()) {
                    handOver(player, new ItemStack(item, Math.min(left, item.getMaxStackSize())));
                }
            }
            given++;
        }
        return Cmd.ok(source, "command.noirm.kit.given", name(id), given);
    }

    /** Into the bag, or dropped at the player's feet if it's full. */
    private static void handOver(LivingEntity entity, ItemStack stack) {
        if (entity instanceof ServerPlayer player && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
