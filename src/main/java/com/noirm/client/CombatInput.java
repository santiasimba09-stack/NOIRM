package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.combat.AttackInput;
import com.noirm.network.CombatInputPackets;
import com.noirm.network.NoirmNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Turns key presses into combat requests. With combat mode off, nothing changes; with it on:
 * left click = light attack, right click (empty hand or sword) = heavy, crouch + right click =
 * launcher, and mining is disabled.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
public final class CombatInput {
    private static boolean guardSent;
    private static boolean useWasDown;

    private CombatInput() {}

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        ClientCombat.tick();
        while (CombatKeys.COMBAT_MODE.consumeClick()) {
            setCombatMode(player, !ClientCombat.isCombatMode());
        }
        while (CombatKeys.STYLE.consumeClick()) {
            NoirmNetwork.sendToServer(new CombatInputPackets.CycleStyle());
        }
        if (!ClientCombat.isCombatMode()) {
            while (CombatKeys.DODGE.consumeClick()) { }
            while (CombatKeys.GRAB.consumeClick()) { }
            while (CombatKeys.LOCK_ON.consumeClick()) { }
            sendGuard(false);
        } else {
            while (CombatKeys.DODGE.consumeClick()) {
                NoirmNetwork.sendToServer(new CombatInputPackets.Dodge(player.input.forwardImpulse, player.input.leftImpulse));
            }
            while (CombatKeys.LOCK_ON.consumeClick()) {
                LockOn.toggle(minecraft);
            }
            while (CombatKeys.GRAB.consumeClick()) {
                NoirmNetwork.sendToServer(new CombatInputPackets.Attack(AttackInput.GRAB));
            }
            sendGuard(CombatKeys.GUARD.isDown() && minecraft.screen == null);
            LockOn.tick(minecraft);
        }
        useWasDown = minecraft.options.keyUse.isDown();
    }

    /** In combat mode, mouse buttons become attacks instead of vanilla actions. */
    @SubscribeEvent
    public static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!ClientCombat.isCombatMode() || player == null) {
            return;
        }
        if (event.isAttack()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            NoirmNetwork.sendToServer(new CombatInputPackets.Attack(directional(player, AttackInput.LIGHT)));
        } else if (event.isUseItem() && event.getHand() == InteractionHand.MAIN_HAND && isCombatHand(player.getMainHandItem())) {
            event.setCanceled(true);
            event.setSwingHand(false);
            if (!useWasDown) { // holding right click repeats; only a fresh press attacks
                NoirmNetwork.sendToServer(new CombatInputPackets.Attack(player.isShiftKeyDown() ? AttackInput.LAUNCHER : directional(player, AttackInput.HEAVY)));
            }
        }
    }

    /**
     * Sprinting turns an attack into a dash attack; holding back (and not forward) into a back
     * attack. Plain walking forward doesn't count, since you hold W through most of a fight.
     */
    private static AttackInput directional(LocalPlayer player, AttackInput input) {
        boolean heavy = input == AttackInput.HEAVY;
        if (player.isSprinting()) {
            return heavy ? AttackInput.FORWARD_HEAVY : AttackInput.FORWARD_LIGHT;
        }
        if (player.input.forwardImpulse < 0) {
            return heavy ? AttackInput.BACK_HEAVY : AttackInput.BACK_LIGHT;
        }
        return input;
    }

    /** No mining in combat mode. */
    @SubscribeEvent
    public static void leftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide() && ClientCombat.isCombatMode()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        guardSent = false;
        ClientCombat.reset();
    }

    private static void setCombatMode(LocalPlayer player, boolean enabled) {
        ClientCombat.setLocalCombatMode(enabled);
        NoirmNetwork.sendToServer(new CombatInputPackets.SetCombatMode(enabled));
        player.displayClientMessage(Component.translatable(enabled ? "message.noirm.combat_mode.on" : "message.noirm.combat_mode.off"), true);
        if (!enabled) {
            LockOn.clear();
            sendGuard(false);
        }
    }

    private static void sendGuard(boolean raised) {
        if (raised != guardSent) {
            guardSent = raised;
            NoirmNetwork.sendToServer(new CombatInputPackets.Guard(raised));
        }
    }

    /** Right click becomes a heavy attack only with an empty hand or a sword, so eating and placing blocks still work. */
    private static boolean isCombatHand(ItemStack stack) {
        return stack.isEmpty() || stack.getItem() instanceof SwordItem;
    }
}
