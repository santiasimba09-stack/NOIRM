package com.noirm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noirm.Noirm;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.WoodClone;
import com.noirm.jutsu.WoodColossus;
import com.noirm.jutsu.WoodDragon;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * How Wood Release looks: the Wood Dragon and the Thousand-Arm Colossus are drawn as bark boxes
 * (grey-green, ink-grained, {@code tools/woodgen/gen_wood.py}) laid along the shapes the server
 * works out, and wood clones wear bark carved in their owner's likeness. No blocks, no models
 * from files: a few hundred quads per technique.
 */
public final class WoodReleaseRenderers {
    public static final ResourceLocation BARK = Noirm.id("textures/entity/wood_release/bark.png");
    public static final ResourceLocation CLONE = Noirm.id("textures/entity/wood_release/clone.png");

    private WoodReleaseRenderers() {}

    // ================================================================ mesh helpers

    /** Writes oriented, bark-textured boxes into one buffer. */
    static final class Mesh {
        final Matrix4f pose;
        final Matrix3f normal;
        final VertexConsumer out;
        final int light;
        final Vec3 origin;

        Mesh(PoseStack stack, VertexConsumer out, int light, Vec3 origin) {
            this.pose = stack.last().pose();
            this.normal = stack.last().normal();
            this.out = out;
            this.light = light;
            this.origin = origin;
        }

        /**
         * A box centred on {@code c} (world space) along the axes {@code x, y, z} (unit vectors),
         * with half sizes {@code hx, hy, hz}; {@code shade} darkens it (1 = as drawn).
         */
        void box(Vec3 c, Vec3 x, Vec3 y, Vec3 z, double hx, double hy, double hz, float shade, int seed) {
            box(c, x, y, z, hx, hy, hz, shade, shade, shade, seed, light);
        }

        void box(Vec3 c, Vec3 x, Vec3 y, Vec3 z, double hx, double hy, double hz, float r, float g, float b, int seed, int lit) {
            Vec3 p = c.subtract(origin);
            Vec3 ax = x.scale(hx);
            Vec3 ay = y.scale(hy);
            Vec3 az = z.scale(hz);
            // +x, -x, +y, -y, +z, -z faces: (normal, the face's two edge vectors).
            face(p.add(ax), ay, az, x, hy, hz, r, g, b, seed, lit);
            face(p.subtract(ax), az, ay, x.scale(-1), hz, hy, r, g, b, seed + 1, lit);
            face(p.add(ay), az, ax, y, hz, hx, r * 1.05f, g * 1.05f, b * 1.05f, seed + 2, lit);
            face(p.subtract(ay), ax, az, y.scale(-1), hx, hz, r * 0.7f, g * 0.7f, b * 0.7f, seed + 3, lit);
            face(p.add(az), ax, ay, z, hx, hy, r, g, b, seed + 4, lit);
            face(p.subtract(az), ay, ax, z.scale(-1), hy, hx, r, g, b, seed + 5, lit);
        }

        /** A beam from {@code a} to {@code b}, {@code w} thick, its top turned toward {@code up}. */
        void beam(Vec3 a, Vec3 b, double w, Vec3 up, float shade, int seed) {
            beam(a, b, w, w, up, shade, shade, shade, seed, light);
        }

        void beam(Vec3 a, Vec3 b, double w, double h, Vec3 up, float r, float g, float bl, int seed, int lit) {
            Vec3 d = b.subtract(a);
            double length = d.length();
            if (length < 1e-4) {
                return;
            }
            Vec3 z = d.scale(1 / length);
            Vec3 x = up.cross(z);
            if (x.lengthSqr() < 1e-6) {
                x = new Vec3(1, 0, 0).cross(z);
            }
            x = x.normalize();
            Vec3 y = z.cross(x).normalize();
            box(a.add(d.scale(0.5)), x, y, z, w / 2, h / 2, length / 2, r, g, bl, seed, lit);
        }

        /** One quad: centre {@code c}, spanned by unit {@code u}·{@code hu} and {@code v}·{@code hv}. */
        private void face(Vec3 c, Vec3 u, Vec3 v, Vec3 n, double hu, double hv, float r, float g, float b, int seed, int lit) {
            Vec3 su = u.normalize().scale(hu);
            Vec3 sv = v.normalize().scale(hv);
            // About 16 texels a block, clamped to the texture; a per-face offset so faces differ.
            float tu = (float) Math.min(1, hu * 2 / 4);
            float tv = (float) Math.min(1, hv * 2 / 4);
            float ou = ((seed * 37) % 97) / 97f * (1 - tu);
            float ov = ((seed * 61) % 89) / 89f * (1 - tv);
            float cr = Math.min(1f, r);
            float cg = Math.min(1f, g);
            float cb = Math.min(1f, b);
            vertex(c.subtract(su).subtract(sv), ou, ov + tv, n, cr, cg, cb, lit);
            vertex(c.add(su).subtract(sv), ou + tu, ov + tv, n, cr, cg, cb, lit);
            vertex(c.add(su).add(sv), ou + tu, ov, n, cr, cg, cb, lit);
            vertex(c.subtract(su).add(sv), ou, ov, n, cr, cg, cb, lit);
        }

        private void vertex(Vec3 p, float u, float v, Vec3 n, float r, float g, float b, int lit) {
            out.vertex(pose, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, 1f).uv(u, v)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(lit).normal(normal, (float) n.x, (float) n.y, (float) n.z).endVertex();
        }
    }

    /** The light a little above {@code at} (the models stick out of the ground: the block at their feet may be dark). */
    static int lightAbove(Entity entity, Vec3 at, double up) {
        int lit = LevelRenderer.getLightColor(entity.level(), BlockPos.containing(at.add(0, up, 0)));
        int block = Math.max(LightTexture.block(lit), 4);
        return LightTexture.pack(block, LightTexture.sky(lit));
    }

    private static final Vec3 UP = new Vec3(0, 1, 0);

    // ================================================================ Wood Dragon

    public static class DragonRenderer extends EntityRenderer<WoodDragon> {
        public DragonRenderer(EntityRendererProvider.Context context) {
            super(context);
            shadowRadius = 0f;
        }

        @Override
        public ResourceLocation getTextureLocation(WoodDragon dragon) {
            return BARK;
        }

        @Override
        public void render(WoodDragon dragon, float yaw, float partialTick, PoseStack stack, MultiBufferSource buffers, int packedLight) {
            float age = dragon.tickCount + partialTick;
            Entity found = dragon.targetId() >= 0 ? dragon.level().getEntity(dragon.targetId()) : null;
            List<Vec3> body;
            float scale = 1f;
            if (found instanceof LivingEntity target) {
                Vec3 feet = target.getPosition(partialTick);
                body = dragon.path(feet, target.getBbWidth(), target.getBbHeight(), age);
                scale = WoodDragon.scaleFor(target.getBbWidth());
            } else {
                body = dragon.path(null, 0, 0, age);
            }
            if (body.size() < 2) {
                return;
            }
            Vec3 origin = dragon.getPosition(partialTick);
            Mesh mesh = new Mesh(stack, buffers.getBuffer(RenderType.entityCutoutNoCull(BARK)), lightAbove(dragon, body.get(0), 1), origin);
            drawBody(mesh, body, scale);
            drawHead(mesh, body.get(0), body.get(0).subtract(body.get(1)).normalize(), scale, age);
            super.render(dragon, yaw, partialTick, stack, buffers, packedLight);
        }

        private static void drawBody(Mesh mesh, List<Vec3> body, float s) {
            for (int i = 1; i < body.size(); i++) {
                Vec3 a = body.get(i);
                Vec3 b = body.get(i - 1);
                Vec3 along = b.subtract(a);
                // Thickest behind the head, thinning toward the tail (which is underground anyway).
                double w = s * Mth.lerp(Math.min(1f, i / 36f), 0.95f, 0.6f);
                Vec3 ext = along.normalize().scale(0.12 * s);
                float banding = i % 2 == 0 ? 1f : 0.82f; // bark scales, light and dark in turn
                mesh.beam(a.subtract(ext), b.add(ext), w, w * 0.9, UP, banding, banding * 0.98f, banding * 0.92f, i * 7, mesh.light);
                // A dorsal ridge plate on every other segment, swept back.
                if (i % 2 == 1 && i > 2) {
                    Vec3 top = a.lerp(b, 0.5).add(0, w * 0.45, 0);
                    Vec3 tip = top.add(0, 0.42 * s, 0).subtract(along.normalize().scale(0.35 * s));
                    mesh.beam(top, tip, 0.16 * s, UP, 0.6f, i * 13);
                }
            }
        }

        /** A horned head with an open jaw, glowing sap in its eyes. */
        private static void drawHead(Mesh mesh, Vec3 at, Vec3 f, float s, float age) {
            Vec3 r = f.cross(UP);
            if (r.lengthSqr() < 1e-6) {
                r = new Vec3(1, 0, 0);
            }
            r = r.normalize();
            Vec3 u = r.cross(f).normalize();
            // Skull.
            Vec3 skull = at.add(f.scale(0.35 * s));
            mesh.box(skull, r, u, f, 0.55 * s, 0.48 * s, 0.6 * s, 1f, 0);
            // The jaw: open, breathing slowly.
            double open = Math.toRadians(24 + 6 * Math.sin(age * 0.2));
            Vec3 fu = f.scale(Math.cos(open * 0.4)).add(u.scale(Math.sin(open * 0.4))).normalize();
            Vec3 uu = r.cross(fu).normalize();
            mesh.box(skull.add(fu.scale(0.95 * s)).add(uu.scale(0.1 * s)), r, uu, fu, 0.42 * s, 0.22 * s, 0.6 * s, 0.95f, 1);
            Vec3 fd = f.scale(Math.cos(open)).subtract(u.scale(Math.sin(open))).normalize();
            Vec3 ud = r.cross(fd).normalize();
            mesh.box(skull.add(fd.scale(0.85 * s)).subtract(u.scale(0.3 * s)), r, ud, fd, 0.36 * s, 0.14 * s, 0.55 * s, 0.75f, 2);
            // Teeth: splinters along both jaws.
            for (int k = -1; k <= 1; k += 2) {
                for (int t = 0; t < 3; t++) {
                    Vec3 base = skull.add(fu.scale((0.6 + t * 0.3) * s)).add(r.scale(k * 0.3 * s)).subtract(uu.scale(0.1 * s));
                    mesh.beam(base, base.subtract(uu.scale(0.25 * s)), 0.07 * s, f, 0.9f, 30 + t);
                }
            }
            // Horns sweeping back, and a heavy brow.
            for (int k = -1; k <= 1; k += 2) {
                Vec3 root = skull.add(u.scale(0.4 * s)).add(r.scale(k * 0.32 * s)).subtract(f.scale(0.2 * s));
                Vec3 mid = root.add(u.scale(0.5 * s)).subtract(f.scale(0.7 * s)).add(r.scale(k * 0.2 * s));
                Vec3 tip = mid.add(u.scale(0.25 * s)).subtract(f.scale(0.8 * s)).add(r.scale(k * 0.15 * s));
                mesh.beam(root, mid, 0.2 * s, u, 0.85f, 40 + k);
                mesh.beam(mid, tip, 0.12 * s, u, 0.7f, 44 + k);
                mesh.box(skull.add(u.scale(0.42 * s)).add(f.scale(0.35 * s)).add(r.scale(k * 0.3 * s)), r, u, f,
                        0.18 * s, 0.1 * s, 0.22 * s, 0.7f, 48 + k);
                // Eyes: knots with a faint green light in them.
                mesh.box(skull.add(u.scale(0.2 * s)).add(f.scale(0.35 * s)).add(r.scale(k * 0.56 * s)), r, u, f,
                        0.03 * s, 0.09 * s, 0.13 * s, 0.75f, 1f, 0.55f, 50, LightTexture.FULL_BRIGHT);
            }
            // A ragged mane of splinters down the neck.
            for (int m = 0; m < 4; m++) {
                Vec3 base = skull.subtract(f.scale((0.5 + m * 0.35) * s)).add(u.scale(0.35 * s));
                mesh.beam(base, base.add(u.scale(0.4 * s)).subtract(f.scale(0.5 * s)), 0.1 * s, u, 0.55f, 60 + m);
            }
        }
    }

    // ================================================================ Thousand-Arm Colossus

    public static class ColossusRenderer extends EntityRenderer<WoodColossus> {
        public ColossusRenderer(EntityRendererProvider.Context context) {
            super(context);
            shadowRadius = 0f;
        }

        @Override
        public ResourceLocation getTextureLocation(WoodColossus colossus) {
            return BARK;
        }

        @Override
        public boolean shouldRender(WoodColossus colossus, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
            return frustum.isVisible(colossus.getBoundingBoxForCulling());
        }

        @Override
        public void render(WoodColossus c, float yaw, float partialTick, PoseStack stack, MultiBufferSource buffers, int packedLight) {
            float age = c.tickCount + partialTick;
            Vec3 origin = c.getPosition(partialTick);
            Mesh mesh = new Mesh(stack, buffers.getBuffer(RenderType.entityCutoutNoCull(BARK)), lightAbove(c, origin, 10), origin);
            Vec3 f = c.forward();
            Vec3 r = c.right();
            Vec3 base = origin.subtract(0, c.sunk(age), 0);
            drawBody(mesh, base, f, r);
            drawHalo(mesh, c, age, f);
            super.render(c, yaw, partialTick, stack, buffers, packedLight);
        }

        /** The trunk of a body, its hands clasped in a seal, a hooded head crowned with branches. */
        private static void drawBody(Mesh m, Vec3 base, Vec3 f, Vec3 r) {
            // Roots spreading into the ground, and the mound it grows from.
            for (int k = 0; k < 7; k++) {
                double a = k * Math.PI * 2 / 7 + 0.3;
                Vec3 out = r.scale(Math.cos(a)).add(f.scale(Math.sin(a)));
                m.beam(base.add(out.scale(2.5)).add(0, 2, 0), base.add(out.scale(6.5)).add(0, -1.5, 0), 1.3, UP, 0.75f, 70 + k);
            }
            m.box(base.add(0, 1.5, 0), r, UP, f, 4.6, 2, 3.6, 0.8f, 80);
            // A hunched torso: leaning toward what it faces.
            Vec3 lean = UP.scale(Math.cos(0.14)).add(f.scale(Math.sin(0.14))).normalize();
            Vec3 leanF = f.scale(Math.cos(0.14)).subtract(UP.scale(Math.sin(0.14))).normalize();
            m.box(base.add(0, 7, 0).add(f.scale(0.2)), r, lean, leanF, 3.3, 4.4, 2.3, 1f, 81);
            m.box(base.add(0, 6, 0).add(f.scale(1.6)), r, lean, leanF, 2.2, 2.6, 1.2, 0.85f, 82); // chest plate of knotted grain
            m.box(base.add(0, 11.2, 0).add(f.scale(0.6)), r, UP, f, 4.8, 1.3, 2.4, 0.95f, 83);
            // The head under a hood of bark, a single dark split for a face.
            Vec3 head = base.add(0, 13.6, 0).add(f.scale(1.2));
            m.box(head, r, UP, f, 1.6, 1.8, 1.6, 0.9f, 84);
            m.box(head.add(0, 0.6, 0).subtract(f.scale(0.35)), r, UP, f, 2.0, 1.75, 1.75, 0.78f, 85);
            m.box(head.add(f.scale(1.62)).add(0, -0.1, 0), r, UP, f, 0.16, 1.0, 0.06, 0.12f, 86);
            for (int k = -1; k <= 1; k += 2) {
                m.beam(head.add(f.scale(1.62)).add(r.scale(k * 0.25)).add(0, 0.85, 0), head.add(f.scale(1.55)).add(r.scale(k * 1.4)).add(0, 0.55, 0),
                        0.32, UP, 0.55f, 87 + k);
            }
            // A crown of broken branches.
            for (int k = 0; k < 5; k++) {
                double a = Math.toRadians(-60 + k * 30);
                Vec3 out = r.scale(Math.sin(a)).add(UP.scale(Math.cos(a))).subtract(f.scale(0.25)).normalize();
                Vec3 from = head.add(0, 2.0, 0).subtract(f.scale(0.4)).add(out.scale(0.6));
                m.beam(from, from.add(out.scale(1.6 + (k % 2) * 1.2)), 0.36, f, 0.7f, 90 + k);
            }
            // The great arms, bent to bring both hands together in front of the chest.
            for (int k = -1; k <= 1; k += 2) {
                Vec3 shoulder = base.add(0, 11.2, 0).add(r.scale(k * 4.4)).add(f.scale(0.4));
                Vec3 elbow = base.add(0, 6.8, 0).add(r.scale(k * 4.9)).add(f.scale(2.4));
                Vec3 wrist = base.add(0, 8.4, 0).add(r.scale(k * 1.0)).add(f.scale(4.4));
                m.beam(shoulder, elbow, 1.9, f, 0.95f, 100 + k);
                m.beam(elbow, wrist, 1.6, f, 0.9f, 103 + k);
                // The palm pressed flat against the other, fingers up.
                Vec3 palm = wrist.add(0, 1.3, 0).subtract(r.scale(k * 0.45));
                m.box(palm, r, UP, f, 0.35, 1.3, 1.0, 0.92f, 106 + k);
                for (int t = 0; t < 3; t++) {
                    Vec3 knuckle = palm.add(0, 1.2, 0).add(f.scale(-0.6 + t * 0.6));
                    m.beam(knuckle, knuckle.add(0, 1.2 - Math.abs(t - 1) * 0.25, 0).add(f.scale(0.1)), 0.42, f, 0.85f, 110 + t);
                }
            }
        }

        /** Two rings of arms fanned out behind it; the striking ones reach down to the ground. */
        private static void drawHalo(Mesh m, WoodColossus c, float age, Vec3 f) {
            Vec3 hub = c.hub(age);
            m.box(hub, c.right(), UP, f, 1.4, 1.4, 0.8, 0.7f, 120);
            // The rings themselves: rims of wood joining the arms' roots.
            Vec3 prevInner = null;
            Vec3 prevOuter = null;
            for (int arm = 0; arm < WoodColossus.ARMS; arm++) {
                Vec3 root = c.armRoot(arm, age);
                boolean inner = arm < WoodColossus.INNER_ARMS;
                Vec3 prev = inner ? prevInner : prevOuter;
                if (prev != null) {
                    m.beam(prev, root, inner ? 0.75 : 0.65, f, 0.68f, 130 + arm);
                }
                if (inner) {
                    prevInner = root;
                    m.beam(hub, root, 0.45, f, 0.6f, 160 + arm); // spokes
                } else {
                    prevOuter = root;
                }
            }
            Integer[] striking = new Integer[WoodColossus.ARMS];
            for (int s = 0; s < c.strikes(); s++) {
                if (c.strikeHand(s, age) != null) {
                    striking[WoodColossus.strikeArm(s)] = s;
                }
            }
            for (int arm = 0; arm < WoodColossus.ARMS; arm++) {
                Vec3 root = c.armRoot(arm, age);
                Vec3 dir = c.armDirection(arm);
                boolean strike = striking[arm] != null;
                Vec3 hand = strike ? c.strikeHand(striking[arm], age) : c.idleHand(arm, age);
                Vec3 elbow = root.lerp(hand, 0.5).add(dir.scale(strike ? 2.2 : 0.7));
                double w = strike ? 1.15 : 0.62;
                float shade = arm < WoodColossus.INNER_ARMS ? 0.9f : 0.78f;
                m.beam(root, elbow, w, f, shade, 200 + arm);
                m.beam(elbow, hand, w * 0.85, f, shade * 0.95f, 240 + arm);
                Vec3 along = hand.subtract(elbow).normalize();
                if (strike) {
                    // A clenched fist, huge.
                    Vec3 side = along.cross(UP);
                    side = side.lengthSqr() < 1e-6 ? c.right() : side.normalize();
                    Vec3 top = side.cross(along).normalize();
                    m.box(hand, side, top, along, 1.25, 1.05, 1.15, 0.95f, 280 + arm);
                    m.box(hand.add(along.scale(0.9)).add(top.scale(0.3)), side, top, along, 1.1, 0.55, 0.45, 0.8f, 290 + arm); // knuckles
                } else {
                    drawOpenHand(m, hand, along, f, shade, arm);
                }
            }
        }

        /** An open hand at the end of a halo arm, fingers spread. */
        private static void drawOpenHand(Mesh m, Vec3 hand, Vec3 along, Vec3 f, float shade, int arm) {
            Vec3 side = along.cross(f);
            side = side.lengthSqr() < 1e-6 ? UP : side.normalize();
            Vec3 face = side.cross(along).normalize();
            m.box(hand, side, face, along, 0.55, 0.2, 0.5, shade, 300 + arm);
            for (int t = 0; t < 4; t++) {
                Vec3 knuckle = hand.add(along.scale(0.45)).add(side.scale(-0.4 + t * 0.27));
                Vec3 tip = knuckle.add(along.scale(0.75 - Math.abs(t - 1.5) * 0.12)).add(side.scale((t - 1.5) * 0.12));
                m.beam(knuckle, tip, 0.18, face, shade * 0.9f, 320 + t);
            }
            Vec3 thumb = hand.add(side.scale(0.55)).subtract(along.scale(0.15));
            m.beam(thumb, thumb.add(side.scale(0.4)).add(along.scale(0.4)), 0.2, face, shade * 0.9f, 330);
        }
    }

    // ================================================================ Wood Clone

    /** A wood clone: bark carved with a face and seams, its owner's look laid faintly over the grain. */
    public static class CloneRenderer extends ShadowCloneRenderer {
        public CloneRenderer(EntityRendererProvider.Context context) {
            super(context);
            addLayer(new Likeness(this));
        }

        @Override
        public ResourceLocation getTextureLocation(SparringNinja ninja) {
            return CLONE;
        }

        /** The owner's own skin (what a shadow clone would wear). */
        ResourceLocation likeness(SparringNinja ninja) {
            return super.getTextureLocation(ninja);
        }
    }

    /** The owner's skin over the bark, tinted to wood and mostly see-through: their look in the grain. */
    static final class Likeness extends RenderLayer<SparringNinja, PlayerModel<SparringNinja>> {
        private final CloneRenderer renderer;

        Likeness(CloneRenderer renderer) {
            super((RenderLayerParent<SparringNinja, PlayerModel<SparringNinja>>) renderer);
            this.renderer = renderer;
        }

        @Override
        public void render(PoseStack stack, MultiBufferSource buffers, int light, SparringNinja ninja, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!(ninja instanceof WoodClone) || ninja.isInvisible()) {
                return;
            }
            VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(renderer.likeness(ninja)));
            getParentModel().renderToBuffer(stack, out, light, LivingEntityRenderer.getOverlayCoords(ninja, 0f), 0.62f, 0.66f, 0.5f, 0.45f);
        }
    }
}
