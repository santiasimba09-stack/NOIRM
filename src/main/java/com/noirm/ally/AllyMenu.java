package com.noirm.ally;

import com.noirm.entity.SparringNinja;
import com.noirm.network.NoirmNetwork;
import com.noirm.registry.NoirmMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

/**
 * The ally screen's slots: what a crew mate wears and holds (head, chest, legs, feet, main hand,
 * off hand), next to your inventory. Everything else on the screen (stats, orders, teaching,
 * style, outfit) comes from {@link AllyPackets}.
 */
public class AllyMenu extends AbstractContainerMenu {
    public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    /** Where the equipment column and the player inventory sit in the screen (pixels). */
    public static final int EQUIP_X = 8;
    public static final int EQUIP_Y = 24;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 152;

    private final SparringNinja ally;
    private final Player player;
    private int syncTimer;

    public AllyMenu(int id, Inventory inventory, SparringNinja ally) {
        super(NoirmMenus.ALLY.get(), id);
        this.ally = ally;
        this.player = inventory.player;
        Container equipment = new Equipment(ally);
        for (int i = 0; i < SLOTS.length; i++) {
            EquipmentSlot slot = SLOTS[i];
            addSlot(new Slot(equipment, i, EQUIP_X, EQUIP_Y + i * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return slot.getType() == EquipmentSlot.Type.HAND || stack.canEquip(slot, ally);
                }

                @Override
                public int getMaxStackSize() {
                    return slot.getType() == EquipmentSlot.Type.HAND ? 64 : 1;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
    }

    public static AllyMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        if (inventory.player.level().getEntity(entityId) instanceof SparringNinja ninja) {
            return new AllyMenu(id, inventory, ninja);
        }
        throw new IllegalStateException("Ally " + entityId + " not found on the client");
    }

    /** Right-click on a crew mate: open their screen. */
    public static void open(ServerPlayer player, SparringNinja ally) {
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inventory, p) -> new AllyMenu(id, inventory, ally),
                Component.translatable("screen.noirm.ally.title", ally.getDisplayName())), buf -> buf.writeVarInt(ally.getId()));
        NoirmNetwork.sendToPlayer(player, AllyPackets.Info.of(player, ally));
    }

    public SparringNinja ally() {
        return ally;
    }

    @Override
    public boolean stillValid(Player player) {
        return ally.isAlive() && player.getUUID().equals(ally.leaderId()) && player.distanceTo(ally) < 10;
    }

    /** Keep the stats on screen current (XP moves while they train). */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer serverPlayer && ++syncTimer >= 20) {
            syncTimer = 0;
            NoirmNetwork.sendToPlayer(serverPlayer, AllyPackets.Info.of(serverPlayer, ally));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < SLOTS.length) {
            if (!moveItemStackTo(stack, SLOTS.length, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Into the slot it fits: armor to its piece, anything else to the main hand.
            boolean moved = false;
            for (int i = 0; i < SLOTS.length && !moved; i++) {
                if (slots.get(i).mayPlace(stack) && !slots.get(i).hasItem()
                        && (SLOTS[i].getType() == EquipmentSlot.Type.ARMOR || i == 4)) {
                    moved = moveItemStackTo(stack, i, i + 1, false);
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    /** The ally's equipment as a container. What they're given, they drop when they fall. */
    private record Equipment(SparringNinja ally) implements Container {
        @Override
        public int getContainerSize() {
            return SLOTS.length;
        }

        @Override
        public boolean isEmpty() {
            for (EquipmentSlot slot : SLOTS) {
                if (!ally.getItemBySlot(slot).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int index) {
            return ally.getItemBySlot(SLOTS[index]);
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            ItemStack stack = getItem(index);
            ItemStack taken = stack.split(count);
            setItem(index, stack);
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            ItemStack stack = getItem(index);
            ally.setItemSlot(SLOTS[index], ItemStack.EMPTY);
            return stack;
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            boolean gift = !stack.isEmpty() && ally.getItemBySlot(SLOTS[index]).isEmpty();
            ally.setItemSlot(SLOTS[index], stack);
            ally.setGuaranteedDrop(SLOTS[index]);
            if (gift && !ally.level().isClientSide()) {
                AllyMind.respect(ally, 1f, null); // being equipped by your leader feels good
            }
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return ally.isAlive();
        }

        @Override
        public void clearContent() {
            for (EquipmentSlot slot : SLOTS) {
                ally.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }
}
