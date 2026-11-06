package com.noirm.registry;

import com.noirm.Noirm;
import com.noirm.ally.AllyMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** NOIRM's container screens. */
public final class NoirmMenus {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Noirm.MOD_ID);

    public static final RegistryObject<MenuType<AllyMenu>> ALLY = MENUS.register("ally",
            () -> IForgeMenuType.create(AllyMenu::fromNetwork));

    private NoirmMenus() {}

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
