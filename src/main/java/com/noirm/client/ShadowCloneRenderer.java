package com.noirm.client;

import com.noirm.entity.ShadowClone;
import com.noirm.entity.SparringNinja;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Draws a Shadow Clone with its owner's skin, so it really looks like a copy of them. */
public class ShadowCloneRenderer extends SparringNinjaRenderer {
    public ShadowCloneRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SparringNinja ninja) {
        if (ninja instanceof ShadowClone clone && Minecraft.getInstance().getConnection() != null) {
            var owner = clone.ownerId().map(id -> Minecraft.getInstance().getConnection().getPlayerInfo(id));
            if (owner.isPresent()) {
                PlayerInfo info = owner.get();
                return info.getSkinLocation();
            }
        }
        return super.getTextureLocation(ninja);
    }
}
