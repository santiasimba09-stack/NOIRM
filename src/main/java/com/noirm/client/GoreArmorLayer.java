package com.noirm.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.noirm.Noirm;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Gore on worn clothes and armor (see {@code com.noirm.gore.Gore}): the same dirt, soot, cuts
 * and blood as on the body, drawn over every worn piece on the bending armor shape
 * ({@link BendyArmorModel}), so outfits get dirty, burnt, torn and bloody. Bruises stay on the
 * skin. The textures are in the armor layout ({@code textures/entity/gore/armor_<kind>_<1-3>.png},
 * from {@code tools/goregen/gen_gore.py}), so they fit every vanilla armor and NOIRM gear piece.
 *
 * <p>A small shader ({@code shaders/core/rendertype_gore_cloth}) uses the piece's own texture as a
 * mask, so the marks only show where there is cloth or metal (never floating around a bare arm
 * under a sleeveless vest). Render types are made once per (mark, piece texture) and cached;
 * nothing is allocated per frame after a piece is first seen. Add it after the armor layer.
 */
public class GoreArmorLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    /** Kinds drawn on clothes, as indexes into {@link GoreMarksLayer#KINDS}: dirt, soot, cut (blood apart). */
    private static final int[] CLOTH_KINDS = {1, 2, 3};
    private static final int STAGES = GoreMarksLayer.STAGES;
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[GoreMarksLayer.KINDS.length * STAGES];
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    /** Render types by piece texture: one per kind and stage (kind × STAGES + stage). */
    private static final Map<ResourceLocation, RenderType[]> TYPES = new HashMap<>();
    /** Each armor item's texture, for the inner (leggings) and outer layer. */
    private static final Map<Item, ResourceLocation> INNER = new IdentityHashMap<>();
    private static final Map<Item, ResourceLocation> OUTER = new IdentityHashMap<>();
    private static ShaderInstance shader;

    static {
        for (int kind = 1; kind < GoreMarksLayer.KINDS.length; kind++) {
            for (int stage = 0; stage < STAGES; stage++) {
                TEXTURES[kind * STAGES + stage] = Noirm.id("textures/entity/gore/armor_" + GoreMarksLayer.KINDS[kind] + "_" + (stage + 1) + ".png");
            }
        }
    }

    /** The same sizes as the armor layer's (leggings 0.5, the rest 1.0), so the marks sit on the cloth. */
    private final BendyArmorModel<T> inner = new BendyArmorModel<>(0.5f);
    private final BendyArmorModel<T> outer = new BendyArmorModel<>(1.0f);
    private final RenderType[] stageTypes = new RenderType[STAGES];

    public GoreArmorLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible() || shader == null) {
            return;
        }
        int packed = GoreClient.marks(entity.getId());
        int blood = GoreClient.blood(entity.getId());
        if ((packed & 0xFFFFFF00) == 0 && blood == 0) {
            return; // nothing that shows on cloth (bruises only, or nothing)
        }
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0f);
        float dry = 1f - GoreClient.bloodFresh(blood);
        float br = 1f + (GoreClient.DRY_R - 1f) * dry, bg = 1f + (GoreClient.DRY_G - 1f) * dry, bb = 1f + (GoreClient.DRY_B - 1f) * dry;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!(stack.getItem() instanceof ArmorItem armor) || armor.getEquipmentSlot() != slot) {
                continue;
            }
            RenderType[] types = typesFor(pieceTexture(entity, stack, slot));
            BendyArmorModel<T> model = slot == EquipmentSlot.LEGS ? inner : outer;
            getParentModel().copyPropertiesTo(model);
            show(model, slot);
            for (int kind : CLOTH_KINDS) {
                System.arraycopy(types, kind * STAGES, stageTypes, 0, STAGES);
                GoreMarksLayer.stages(model, poseStack, buffers, stageTypes, GoreClient.level(packed, kind), light, overlay, 1f, 1f, 1f);
            }
            if (blood != 0) {
                System.arraycopy(types, GoreMarksLayer.BLOOD * STAGES, stageTypes, 0, STAGES);
                GoreMarksLayer.stages(model, poseStack, buffers, stageTypes, GoreClient.bloodLevel(blood), light, overlay, br, bg, bb);
            }
        }
    }

    private static RenderType[] typesFor(ResourceLocation piece) {
        RenderType[] types = TYPES.get(piece);
        if (types == null) {
            types = new RenderType[TEXTURES.length];
            for (int i = STAGES; i < TEXTURES.length; i++) {
                types[i] = ClothType.of(TEXTURES[i], piece);
            }
            TYPES.put(piece, types);
        }
        return types;
    }

    /** The piece's texture, found the way vanilla's armor layer does (and cached per item). */
    private static ResourceLocation pieceTexture(Entity entity, ItemStack stack, EquipmentSlot slot) {
        Map<Item, ResourceLocation> cache = slot == EquipmentSlot.LEGS ? INNER : OUTER;
        ResourceLocation found = cache.get(stack.getItem());
        if (found != null) {
            return found;
        }
        ArmorItem item = (ArmorItem) stack.getItem();
        String texture = item.getMaterial().getName();
        String domain = "minecraft";
        int colon = texture.indexOf(':');
        if (colon != -1) {
            domain = texture.substring(0, colon);
            texture = texture.substring(colon + 1);
        }
        String path = String.format(Locale.ROOT, "%s:textures/models/armor/%s_layer_%d.png", domain, texture, slot == EquipmentSlot.LEGS ? 2 : 1);
        path = net.minecraftforge.client.ForgeHooksClient.getArmorTexture(entity, stack, path, slot, null);
        found = ResourceLocation.tryParse(path);
        if (found == null) {
            found = ResourceLocation.tryParse("minecraft:textures/models/armor/leather_layer_1.png");
        }
        cache.put(stack.getItem(), found);
        return found;
    }

    /** The parts a piece in this slot covers (as vanilla's armor layer shows them). */
    private static void show(HumanoidModel<?> model, EquipmentSlot slot) {
        model.setAllVisible(false);
        switch (slot) {
            case HEAD -> {
                model.head.visible = true;
                model.hat.visible = true;
            }
            case CHEST -> {
                model.body.visible = true;
                model.rightArm.visible = true;
                model.leftArm.visible = true;
            }
            case LEGS -> {
                model.body.visible = true;
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            case FEET -> {
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            default -> {
            }
        }
    }

    /**
     * Gore texture on unit 0, the piece's texture (the mask) on unit 1, translucent, lit. Armor is
     * drawn nudged toward the camera (view-offset layering); the marks get the same nudge, or the
     * piece would hide them.
     */
    private static final class ClothType extends RenderType {
        private static final ShaderStateShard SHADER = new ShaderStateShard(() -> shader);

        static RenderType of(ResourceLocation gore, ResourceLocation piece) {
            return create("noirm_gore_cloth", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true,
                    CompositeState.builder()
                            .setShaderState(SHADER)
                            .setTextureState(MultiTextureStateShard.builder().add(gore, false, false).add(piece, false, false).build())
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setLayeringState(VIEW_OFFSET_Z_LAYERING) // the same nudge toward the camera as armor
                            .createCompositeState(true));
        }

        private ClothType(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                          Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class GoreShaderRegistration {
        private GoreShaderRegistration() {}

        @SubscribeEvent
        public static void registerGoreShaders(RegisterShadersEvent event) throws IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Noirm.id("rendertype_gore_cloth"),
                    DefaultVertexFormat.NEW_ENTITY), loaded -> shader = loaded);
        }
    }
}
