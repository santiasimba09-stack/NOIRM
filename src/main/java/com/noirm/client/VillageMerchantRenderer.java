package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.entity.VillageMerchant;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a village merchant with the player model and one of the generic ninja-village outfits
 * (placeholder art). Each merchant always wears the same outfit, picked from their UUID.
 */
public class VillageMerchantRenderer extends HumanoidMobRenderer<VillageMerchant, PlayerModel<VillageMerchant>> {
    /** The generic outfits under {@code textures/entity/ninja/generic/}. */
    private static final ResourceLocation[] OUTFITS = new ResourceLocation[6];

    static {
        for (int i = 0; i < OUTFITS.length; i++) {
            OUTFITS[i] = Noirm.id("textures/entity/ninja/generic/" + i + ".png");
        }
    }

    public VillageMerchantRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(VillageMerchant merchant) {
        return OUTFITS[Math.floorMod(merchant.getUUID().hashCode(), OUTFITS.length)];
    }
}
