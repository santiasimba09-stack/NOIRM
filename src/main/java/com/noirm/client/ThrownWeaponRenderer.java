package com.noirm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.noirm.weapon.ThrowStats;
import com.noirm.weapon.ThrownWeapon;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a thrown ninja tool with its own item icon: kunai and senbon fly point first along their
 * path, shuriken and the fūma spin flat like discs, an explosive tag lies flat on the wall it's
 * stuck to. The icons are drawn diagonally (tip to the top right, like a vanilla sword), so a
 * -45° turn points the tip along the flight.
 */
public class ThrownWeaponRenderer extends EntityRenderer<ThrownWeapon> {
    private final ItemRenderer items;

    public ThrownWeaponRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
        this.shadowRadius = 0.1f;
    }

    @Override
    public void render(ThrownWeapon weapon, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        ItemStack stack = weapon.getItem();
        if (stack.isEmpty()) {
            return;
        }
        ThrowStats stats = weapon.stats();
        pose.pushPose();
        // Like an arrow: local +X points along the flight.
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, weapon.yRotO, weapon.getYRot()) - 90f));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTicks, weapon.xRotO, weapon.getXRot())));
        float spin = Mth.lerp(partialTicks, weapon.spinO, weapon.spin);
        if (weapon.isPlacedTag()) {
            pose.mulPose(Axis.YP.rotationDegrees(90f)); // face out of the block
        } else if (stats.flat()) {
            pose.mulPose(Axis.XP.rotationDegrees(90f)); // lie flat…
            pose.mulPose(Axis.ZP.rotationDegrees(-spin)); // …and spin around the middle
        } else {
            pose.mulPose(Axis.ZP.rotationDegrees(-45f)); // tip forward
            pose.translate(-0.2, -0.2, 0); // back along the blade: only the tip sinks into a block
        }
        pose.scale(stats.scale(), stats.scale(), stats.scale());
        items.renderStatic(stack, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose, buffers, weapon.level(), weapon.getId());
        pose.popPose();
        super.render(weapon, yaw, partialTicks, pose, buffers, light);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(ThrownWeapon weapon) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
