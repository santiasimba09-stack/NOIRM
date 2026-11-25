package com.noirm.gear;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Sewing a cape onto a chest piece, and taking it off again (any crafting grid):
 * <ul>
 *   <li><b>a chest piece + a cape</b> → the chest piece with the cape sewn on (it keeps its own
 *       picture and stats, and wears the cape over it);</li>
 *   <li><b>a chest piece with a cape, alone</b> → the chest piece, and the cape stays in the grid.</li>
 * </ul>
 * The cape is kept whole on the chest piece (its dye, enchantments, name), in {@link Drapes#SEWN_CAPE}.
 */
public class CapeSewingRecipe extends CustomRecipe {
    public CapeSewingRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    private static boolean isChest(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem armor && armor.getType() == ArmorItem.Type.CHESTPLATE && !(stack.getItem() instanceof CapeItem);
    }

    /** {chest, cape} found in the grid, or null if the grid isn't exactly one of the two recipes. */
    private static ItemStack[] find(CraftingContainer grid) {
        ItemStack chest = ItemStack.EMPTY, cape = ItemStack.EMPTY;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack stack = grid.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof CapeItem && cape.isEmpty()) {
                cape = stack;
            } else if (isChest(stack) && chest.isEmpty()) {
                chest = stack;
            } else {
                return null;
            }
        }
        if (chest.isEmpty()) {
            return null;
        }
        boolean sewn = !Drapes.sewnCape(chest).isEmpty();
        if (cape.isEmpty() ? !sewn : sewn) {
            return null; // sew onto a bare piece, or unpick a sewn one
        }
        return new ItemStack[]{chest, cape};
    }

    @Override
    public boolean matches(CraftingContainer grid, Level level) {
        return find(grid) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
        ItemStack[] found = find(grid);
        if (found == null) {
            return ItemStack.EMPTY;
        }
        ItemStack out = found[0].copyWithCount(1);
        if (found[1].isEmpty()) {
            out.removeTagKey(Drapes.SEWN_CAPE);
        } else {
            out.getOrCreateTag().put(Drapes.SEWN_CAPE, found[1].copyWithCount(1).save(new net.minecraft.nbt.CompoundTag()));
        }
        return out;
    }

    /** Unpicking gives the cape back: it stays in the slot the sewn piece was in. */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid) {
        NonNullList<ItemStack> left = super.getRemainingItems(grid);
        ItemStack[] found = find(grid);
        if (found != null && found[1].isEmpty()) {
            for (int i = 0; i < grid.getContainerSize(); i++) {
                if (grid.getItem(i) == found[0]) {
                    left.set(i, Drapes.sewnCape(found[0]));
                }
            }
        }
        return left;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return NoirmGear.CAPE_SEWING.get();
    }
}
