package com.noirm.mixin;

import com.noirm.client.PlayerSkins;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Character creation looks (client only): a player who made a look on the creation screen is
 * drawn with the skin built from it ({@link PlayerSkins}) instead of their account skin, in
 * every place the game asks for the skin (body, first-person arms and hands). Looks use the
 * wide-arm model.
 *
 * <p>Like {@link BlockStateBaseMixin}, each method is listed under its development name and its
 * real-game (SRG) name with {@code remap = false}, so no refmap is needed.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = {"getSkinTextureLocation()Lnet/minecraft/resources/ResourceLocation;",
            "m_108560_()Lnet/minecraft/resources/ResourceLocation;"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void noirm$lookSkin(CallbackInfoReturnable<ResourceLocation> result) {
        ResourceLocation skin = PlayerSkins.override((AbstractClientPlayer) (Object) this);
        if (skin != null) {
            result.setReturnValue(skin);
        }
    }

    @Inject(method = {"getModelName()Ljava/lang/String;", "m_108564_()Ljava/lang/String;"},
            at = @At("HEAD"), cancellable = true, remap = false)
    private void noirm$lookModel(CallbackInfoReturnable<String> result) {
        if (PlayerSkins.lookFor((AbstractClientPlayer) (Object) this) != null) {
            result.setReturnValue("default");
        }
    }
}
