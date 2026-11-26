package com.noirm.pills;

import com.noirm.network.NoirmNetwork;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;

/**
 * The apothecary's slots: four in the mortar, one for what comes out, and your inventory. The
 * screen's buttons arrive as menu button clicks: {@value #BREW} grinds, {@value #SELECT}{@code + i}
 * picks recipe {@code i} and fills the mortar from your bag. The server sends the recipe rows
 * ({@link PillPackets.Station}) when it opens and after every change.
 */
public class ApothecaryMenu extends AbstractContainerMenu {
    public static final int INPUTS = 4;
    public static final int OUTPUT = INPUTS;
    public static final int BREW = 0;
    public static final int SELECT = 1000;
    /** Slot positions on the screen (pixels from its corner). */
    public static final int INPUT_X = 164, INPUT_Y = 40, INPUT_STEP = 20, OUTPUT_X = 274, OUTPUT_Y = 40;
    public static final int INVENTORY_X = 164, INVENTORY_Y = 118;
    /** How far you may stand from the block. */
    public static final double REACH_SQ = 64.0;

    private final SimpleContainer mortar = new SimpleContainer(INPUTS + 1);
    private final BlockPos pos;
    private final Player player;
    private ResourceLocation selected;

    public ApothecaryMenu(int id, Inventory inventory, BlockPos pos) {
        super(PillItems.APOTHECARY_MENU.get(), id);
        this.pos = pos;
        this.player = inventory.player;
        for (int i = 0; i < INPUTS; i++) {
            addSlot(new Slot(mortar, i, INPUT_X + i * INPUT_STEP, INPUT_Y));
        }
        addSlot(new Slot(mortar, OUTPUT, OUTPUT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
        mortar.addListener(this::slotsChanged);
        if (player instanceof ServerPlayer) {
            sendStation();
        }
    }

    public static ApothecaryMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buf) {
        return new ApothecaryMenu(id, inventory, buf.readBlockPos());
    }

    public Container mortar() {
        return mortar;
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (player instanceof ServerPlayer) {
            sendStation();
        }
    }

    /** The recipe rows for this player, with the one picked and the one the mortar holds. */
    void sendStation() {
        if (player instanceof ServerPlayer server) {
            ShinobiCapability.get(server).ifPresent(data -> NoirmNetwork.sendToPlayer(server,
                    PillPackets.Station.of(data, selected, Apothecary.find(data, mortar, INPUTS, selected))));
        }
    }

    @Override
    public boolean clickMenuButton(Player who, int id) {
        ShinobiData data = ShinobiCapability.get(who).orElse(null);
        if (data == null) {
            return false;
        }
        if (id == BREW) {
            return brew(who, data);
        }
        if (id >= SELECT) {
            var all = PillRegistry.recipes();
            int index = id - SELECT;
            if (index < all.size()) {
                select(who, data, all.get(index).getKey());
                return true;
            }
        }
        return false;
    }

    /** Grinds what's in the mortar (public for tests). Returns whether pills (or a ruined one) came out. */
    public boolean brew(Player who, ShinobiData data) {
        ResourceLocation id = Apothecary.find(data, mortar, INPUTS, selected);
        PillDefinition def = id == null ? null : PillRegistry.pill(id);
        if (def == null || def.recipe().isEmpty()) {
            who.displayClientMessage(Component.translatable("message.noirm.apothecary.no_recipe").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        PillDefinition.Recipe recipe = def.recipe().get();
        switch (Apothecary.state(data, id, recipe)) {
            case UNKNOWN -> {
                who.displayClientMessage(Component.translatable("message.noirm.apothecary.unknown").withStyle(ChatFormatting.RED), true);
                return false;
            }
            case LOW_SKILL -> {
                who.displayClientMessage(Component.translatable("message.noirm.apothecary.low_skill", Math.round(recipe.minPharmacology()))
                        .withStyle(ChatFormatting.RED), true);
                return false;
            }
            default -> { }
        }
        ItemStack out = mortar.getItem(OUTPUT);
        if (!out.isEmpty() && out.getCount() >= out.getMaxStackSize() - Apothecary.MAX_EXTRA_YIELD - recipe.yield()) {
            who.displayClientMessage(Component.translatable("message.noirm.apothecary.full").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        Apothecary.Outcome outcome = Apothecary.roll(who, data, id, recipe, who.getRandom());
        Apothecary.consume(mortar, INPUTS, recipe);
        ItemStack made = Apothecary.result(outcome);
        if (out.isEmpty()) {
            mortar.setItem(OUTPUT, made);
        } else if (ItemStack.isSameItemSameTags(out, made) && out.getCount() + made.getCount() <= out.getMaxStackSize()) {
            out.grow(made.getCount());
            mortar.setChanged();
        } else if (!who.getInventory().add(made)) {
            who.drop(made, false);
        }
        who.displayClientMessage(Apothecary.message(outcome), true);
        if (who.level() instanceof ServerLevel level) {
            level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.6f, 1.2f);
            level.playSound(null, pos, outcome.success() ? com.noirm.sound.NoirmSounds.MEDICAL_CHIME.get() : SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.BLOCKS, 0.5f, outcome.success() ? 1.2f : 1.6f);
            PillEffects.puffAt(level, pos, outcome.success() ? def.rgb() : 0x3A3530);
        }
        broadcastChanges();
        sendStation();
        return true;
    }

    /** Picks a recipe and fills the mortar with its ingredients from your bag (what was in it goes back first). */
    public void select(Player who, ShinobiData data, ResourceLocation id) {
        selected = id;
        PillDefinition def = PillRegistry.pill(id);
        if (def == null || def.recipe().isEmpty()) {
            return;
        }
        for (int i = 0; i < INPUTS; i++) {
            ItemStack s = mortar.removeItemNoUpdate(i);
            if (!s.isEmpty()) {
                who.getInventory().placeItemBackInInventory(s);
            }
        }
        List<PillDefinition.Ingredient> ingredients = def.recipe().get().ingredients();
        boolean missing = false;
        for (int i = 0; i < ingredients.size() && i < INPUTS; i++) {
            PillDefinition.Ingredient ing = ingredients.get(i);
            Item item = ForgeRegistries.ITEMS.getValue(ing.item());
            int need = ing.count();
            ItemStack into = ItemStack.EMPTY;
            for (ItemStack s : who.getInventory().items) {
                if (need > 0 && item != null && s.is(item)) {
                    int take = Math.min(need, s.getCount());
                    if (into.isEmpty()) {
                        into = s.copyWithCount(take);
                    } else {
                        into.grow(take);
                    }
                    s.shrink(take);
                    need -= take;
                }
            }
            mortar.setItem(i, into);
            missing |= need > 0;
        }
        if (missing) {
            who.displayClientMessage(Component.translatable("message.noirm.apothecary.missing").withStyle(ChatFormatting.GRAY), true);
        }
        mortar.setChanged();
        broadcastChanges();
        sendStation();
    }

    @Override
    public ItemStack quickMoveStack(Player who, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int playerStart = INPUTS + 1, end = slots.size();
        if (index < playerStart) {
            if (!moveItemStackTo(stack, playerStart, end, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, INPUTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player who) {
        return who.level().getBlockState(pos).is(PillItems.APOTHECARY.get())
                && who.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= REACH_SQ;
    }

    @Override
    public void removed(Player who) {
        super.removed(who);
        if (!who.level().isClientSide()) {
            clearContainer(who, mortar);
        }
    }

    /** For tests: the recipe ids in screen order. */
    public static List<ResourceLocation> order() {
        return PillRegistry.recipes().stream().map(Map.Entry::getKey).toList();
    }
}
