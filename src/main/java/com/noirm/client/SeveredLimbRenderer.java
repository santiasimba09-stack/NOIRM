package com.noirm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noirm.gore.SeveredLimb;
import com.noirm.gore.SeveredLimb.Part;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Draws a {@link SeveredLimb}: the matching part of the jointed body ({@link BendyModel}) in the
 * victim's skin, with the armor piece it wore ({@link BendyArmorModel}) and a raw wound at the
 * cut. While it dissolves it turns chakra-blue, shrinks and fades. Beyond
 * {@link SeveredLimb#RENDER_DISTANCE} it isn't drawn at all. Nothing is allocated per frame:
 * the skin and armor textures are looked up once per limb ({@link Look}).
 */
public class SeveredLimbRenderer extends EntityRenderer<SeveredLimb> {
    private static final ResourceLocation DEFAULT_NINJA = net.minecraft.resources.ResourceLocation.tryParse("minecraft:textures/entity/player/wide/kai.png");
    private static final RenderType WOUND = RenderType.entityCutoutNoCull(GoreMarksLayer.WOUND);
    private static final RenderType BLOOD = RenderType.entityTranslucent(com.noirm.Noirm.id("textures/entity/gore/blood_3.png"));

    private final BendyModel<LivingEntity> wide = new BendyModel<>(false);
    private final BendyModel<LivingEntity> slim = new BendyModel<>(true);
    private final BendyArmorModel<LivingEntity> inner = new BendyArmorModel<>(0.5f);
    private final BendyArmorModel<LivingEntity> outer = new BendyArmorModel<>(1.0f);

    public SeveredLimbRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.15f;
    }

    /** What a limb looks like, worked out once (its skin key and armor never change). */
    static final class Look {
        String ninjaKey;          // a ninja look key (skin built by NinjaSkins), or null
        ResourceLocation skin;    // a player's skin (or the default ninja)
        boolean slim;
        ResourceLocation armor, armorOverlay, boots, bootsOverlay;
        int armorColor = -1, bootsColor = -1;
        boolean armorFoil, bootsFoil;
        /** Render types, solid and fading (index 0 / 1), looked up once: skin, armor, its overlay, boots, theirs. */
        final RenderType[][] types = new RenderType[5][];

        RenderType[] typesFor(int slot, ResourceLocation texture, boolean armorType) {
            if (texture == null) {
                return null;
            }
            if (types[slot] == null) {
                types[slot] = new RenderType[]{armorType ? RenderType.armorCutoutNoCull(texture) : RenderType.entityCutoutNoCull(texture),
                        RenderType.entityTranslucent(texture)};
            }
            return types[slot];
        }
    }

    /** Reused for every rotation (no per-frame allocation). */
    private final org.joml.Quaternionf turn = new org.joml.Quaternionf();

    private static Look look(SeveredLimb limb) {
        if (limb.renderCache instanceof Look look) {
            return look;
        }
        Look look = new Look();
        String key = limb.skin();
        if (key.startsWith("player:")) {
            UUID id = null;
            try {
                id = UUID.fromString(key.substring(7));
            } catch (IllegalArgumentException ignored) {
                // a bad key: the default skin
            }
            PlayerInfo info = id == null || Minecraft.getInstance().getConnection() == null ? null
                    : Minecraft.getInstance().getConnection().getPlayerInfo(id);
            if (info != null) {
                look.skin = info.getSkinLocation();
                look.slim = "slim".equals(info.getModelName());
            } else {
                look.skin = id == null ? DefaultPlayerSkin.getDefaultSkin() : DefaultPlayerSkin.getDefaultSkin(id);
                look.slim = id != null && "slim".equals(DefaultPlayerSkin.getSkinModelName(id));
            }
        } else {
            String ninja = key.startsWith("look:") ? key.substring(5) : "";
            if (ninja.isEmpty()) {
                look.skin = DEFAULT_NINJA;
            } else {
                look.ninjaKey = ninja;
                look.skin = NinjaSkins.texture(ninja);
            }
        }
        Part part = limb.part();
        ItemStack armor = limb.armor();
        EquipmentSlot slot = part == Part.HEAD ? EquipmentSlot.HEAD : part.isLeg() ? EquipmentSlot.LEGS : EquipmentSlot.CHEST;
        if (armor.getItem() instanceof ArmorItem) {
            look.armor = armorTexture(limb, armor, slot, null);
            look.armorFoil = armor.hasFoil();
            if (armor.getItem() instanceof DyeableLeatherItem dye) {
                look.armorColor = dye.getColor(armor);
                look.armorOverlay = armorTexture(limb, armor, slot, "overlay");
            }
        }
        ItemStack boots = limb.boots();
        if (part.isLeg() && boots.getItem() instanceof ArmorItem) {
            look.boots = armorTexture(limb, boots, EquipmentSlot.FEET, null);
            look.bootsFoil = boots.hasFoil();
            if (boots.getItem() instanceof DyeableLeatherItem dye) {
                look.bootsColor = dye.getColor(boots);
                look.bootsOverlay = armorTexture(limb, boots, EquipmentSlot.FEET, "overlay");
            }
        }
        limb.renderCache = look;
        return look;
    }

    /** The armor texture, the way vanilla's armor layer finds it (Forge lets items override it). */
    private static ResourceLocation armorTexture(SeveredLimb limb, ItemStack stack, EquipmentSlot slot, String type) {
        ArmorItem item = (ArmorItem) stack.getItem();
        String texture = item.getMaterial().getName();
        String domain = "minecraft";
        int colon = texture.indexOf(':');
        if (colon != -1) {
            domain = texture.substring(0, colon);
            texture = texture.substring(colon + 1);
        }
        String path = String.format(java.util.Locale.ROOT, "%s:textures/models/armor/%s_layer_%d%s.png", domain, texture,
                slot == EquipmentSlot.LEGS ? 2 : 1, type == null ? "" : "_" + type);
        path = net.minecraftforge.client.ForgeHooksClient.getArmorTexture(limb, stack, path, slot, type);
        ResourceLocation id = ResourceLocation.tryParse(path);
        return id == null ? ResourceLocation.tryParse("minecraft:textures/models/armor/leather_layer_1.png") : id;
    }

    @Override
    public void render(SeveredLimb limb, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (!com.noirm.gore.GoreSettings.any()) {
            return;
        }
        Look look = look(limb);
        BendyModel<LivingEntity> model = look.slim ? slim : wide;
        Part part = limb.part();
        float dissolve = limb.dissolve(partialTick);

        poseStack.pushPose();
        poseStack.translate(0, part == Part.HEAD ? 0.25 : 0.14, 0);
        poseStack.mulPose(turn.rotationY((180f - limb.getYRot()) * Mth.DEG_TO_RAD));
        poseStack.mulPose(turn.rotationX(Mth.lerp(partialTick, limb.prevPitch, limb.pitch) * Mth.DEG_TO_RAD));
        poseStack.mulPose(turn.rotationZ(Mth.lerp(partialTick, limb.prevRoll, limb.roll) * Mth.DEG_TO_RAD));
        float size = 1f - 0.45f * dissolve; // the flame eats it
        poseStack.scale(-size, -size, size); // model space: Y points down, like the living renderer
        // Centre the part on the pivot point, so it tumbles around its middle.
        float cx = centreX(part, look.slim);
        float cy = part == Part.HEAD ? -4f : part.isLeg() ? 6f : 4f;
        poseStack.translate(-cx / 16f, -cy / 16f, 0);

        // Chakra flame: whiter-blue, then gone.
        float r = 1f - 0.65f * dissolve, g = 1f - 0.35f * dissolve, b = 1f, a = 1f - dissolve;
        int fade = dissolve > 0f ? 1 : 0;
        VertexConsumer consumer = buffers.getBuffer(look.typesFor(0, look.skin, false)[fade]);
        int overlay = OverlayTexture.NO_OVERLAY;
        ModelPart body = pose(model, part);
        body.render(poseStack, consumer, light, overlay, r, g, b, a);
        if (part == Part.HEAD) {
            model.hat.copyFrom(model.head);
            model.hat.render(poseStack, consumer, light, overlay, r, g, b, a);
        }
        if (dissolve < 0.5f && com.noirm.gore.GoreSettings.full()) {
            body.render(poseStack, buffers.getBuffer(BLOOD), light, overlay, 0.75f, 0.6f, 0.58f, 1f - 2f * dissolve); // soaked
        }
        if (dissolve < 0.5f) {
            wound(poseStack, buffers.getBuffer(WOUND), light, body, part, look.slim);
        }
        if (look.armor != null) {
            BendyArmorModel<LivingEntity> armorModel = part.isLeg() ? inner : outer;
            armor(poseStack, buffers, light, pose(armorModel, part), look.typesFor(1, look.armor, true),
                    look.typesFor(2, look.armorOverlay, true), look.armorColor, look.armorFoil, fade, r, g, b, a);
        }
        if (look.boots != null) {
            armor(poseStack, buffers, light, pose(outer, part), look.typesFor(3, look.boots, true),
                    look.typesFor(4, look.bootsOverlay, true), look.bootsColor, look.bootsFoil, fade, r, g, b, a);
        }
        poseStack.popPose();
        super.render(limb, yaw, partialTick, poseStack, buffers, light);
    }

    private static void armor(PoseStack poseStack, MultiBufferSource buffers, int light, ModelPart part, RenderType[] texture,
                              RenderType[] overlayTexture, int color, boolean foil, int fade, float r, float g, float b, float a) {
        float cr = r, cg = g, cb = b;
        if (color != -1) {
            cr *= (color >> 16 & 255) / 255f;
            cg *= (color >> 8 & 255) / 255f;
            cb *= (color & 255) / 255f;
        }
        part.render(poseStack, buffers.getBuffer(texture[fade]), light, OverlayTexture.NO_OVERLAY, cr, cg, cb, a);
        if (overlayTexture != null) {
            part.render(poseStack, buffers.getBuffer(overlayTexture[fade]), light, OverlayTexture.NO_OVERLAY, r, g, b, a);
        }
        if (foil && fade == 0) {
            part.render(poseStack, buffers.getBuffer(RenderType.armorEntityGlint()), light, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        }
    }

    /** The model part for this limb, laid out straight at the origin with a slight natural bend. */
    private static ModelPart pose(net.minecraft.client.model.HumanoidModel<?> model, Part part) {
        ModelPart limb = switch (part) {
            case HEAD -> model.head;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_ARM -> model.leftArm;
            case RIGHT_LEG -> model.rightLeg;
            case LEFT_LEG -> model.leftLeg;
        };
        limb.setPos(0f, 0f, 0f);
        limb.setRotation(0f, 0f, 0f);
        limb.visible = true;
        if (part != Part.HEAD) {
            ModelPart joint = limb.getChild(part.isLeg() ? "shin" : "forearm");
            joint.setRotation(part.isLeg() ? 0.25f : -0.45f, 0f, 0f); // a limp elbow / knee
        }
        return limb;
    }

    /** The middle of the limb's box across (arms sit off their shoulder pivot; slim arms are 3 wide). */
    private static float centreX(Part part, boolean slim) {
        return switch (part) {
            case RIGHT_ARM -> slim ? -0.5f : -1f;
            case LEFT_ARM -> slim ? 0.5f : 1f;
            default -> 0f;
        };
    }

    /** The raw cut: the top of an arm or leg, or under the head. */
    private static void wound(PoseStack poseStack, VertexConsumer consumer, int light, ModelPart part, Part which, boolean slim) {
        poseStack.pushPose();
        part.translateAndRotate(poseStack);
        switch (which) {
            case HEAD -> GoreMarksLayer.horizontal(poseStack, consumer, light, -2.5f, 2.5f, 0.02f, -2f, 2f, 1f);
            case RIGHT_ARM -> GoreMarksLayer.horizontal(poseStack, consumer, light, slim ? -2f : -3f, 1f, -2.02f, -2f, 2f, -1f);
            case LEFT_ARM -> GoreMarksLayer.horizontal(poseStack, consumer, light, -1f, slim ? 2f : 3f, -2.02f, -2f, 2f, -1f);
            default -> GoreMarksLayer.horizontal(poseStack, consumer, light, -2f, 2f, -0.02f, -2f, 2f, -1f);
        }
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(SeveredLimb limb) {
        return look(limb).skin;
    }
}
