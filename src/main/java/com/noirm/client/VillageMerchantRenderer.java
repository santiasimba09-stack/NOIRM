package com.noirm.client;

import com.noirm.entity.NinjaLook;
import com.noirm.entity.VillageMerchant;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a village merchant with the player model and a generic villager look built by
 * {@link NinjaSkins}, with its 3D hair ({@link HairLayer}) and eyes ({@link EyeDecalLayer}). Each merchant
 * always has the same look, picked from their UUID.
 */
public class VillageMerchantRenderer extends HumanoidMobRenderer<VillageMerchant, PlayerModel<VillageMerchant>> {
    public VillageMerchantRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        addLayer(new GoreMarksLayer<>(this)); // bruises, dirt, soot and cuts
        addLayer(new EyeDecalLayer<>(this));
        addLayer(new HairLayer<>(this));
    }

    /** The merchant's look key (the same for the same merchant, every time). */
    static String lookKey(VillageMerchant merchant) {
        return NinjaLook.key("generic", merchant.getUUID().getLeastSignificantBits() & 0xFFFF_FFFF_FFFFL);
    }

    @Override
    public ResourceLocation getTextureLocation(VillageMerchant merchant) {
        return NinjaSkins.texture(lookKey(merchant));
    }
}
