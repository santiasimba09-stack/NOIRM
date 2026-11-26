package com.noirm.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.noirm.Noirm;
import com.noirm.genjutsu.GenjutsuPackets;
import com.noirm.genjutsu.GenjutsuScene;
import com.noirm.genjutsu.GenjutsuScenes;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

/**
 * A genjutsu scene in your own mind (client only). The server says you are caught and sends the
 * scene ({@link GenjutsuPackets.Scene}, defined in {@code data/noirm/genjutsu/*.json}); this draws it
 * around the camera without touching a single real block:
 * <ul>
 *   <li><b>The world fades:</b> the fog closes in to a few blocks in the scene's colour and a false sky
 *       (a dome from horizon to zenith, maybe a red moon) replaces the real one.</li>
 *   <li><b>The ground</b> becomes ink, still water or blood, rippling out from your feet.</li>
 *   <li><b>Figures:</b> crows wheeling and swooping, copies of the caster standing round you, ink
 *       phantoms closing in, your own crew turned to shadows, a tree growing round your body, ink hands
 *       rising to bind you, bare posts, a giant eye opening in the sky.</li>
 *   <li><b>Sound:</b> the real world goes dull and slow; a drone, your heartbeat, whispers, crows.</li>
 *   <li><b>Your HUD stutters</b>; the Tsukuyomi inverts the world red and black and shows how long it
 *       really lasts against how long it feels.</li>
 *   <li><b>Breaking out</b> (Kai, a friend, a strong mind) shatters the scene like glass: the last
 *       frame breaks into shards that fall away and the real world is behind them.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
public final class GenjutsuSceneRenderer {
    private static final int FADE_IN = 15;
    private static final int FADE_OUT = 20;
    private static final int SHATTER_TICKS = 18;
    /** How long a Tsukuyomi feels from inside (hours). */
    private static final int TSUKUYOMI_HOURS = 72;

    private static GenjutsuScene scene;
    private static String sceneId = "";
    private static int total = 1;
    private static int left;
    private static int age;
    private static int fadeOut;
    private static int casterId = -1;
    private static Vec3 anchor = Vec3.ZERO;
    private static Vec3 decoy;
    private static int[] allies = new int[0];
    private static float facing;
    private static SoundInstance drone;

    private static boolean captureNext;
    private static TextureTarget shatterTarget;
    private static int shatterAge = -1;
    private static int shatterColor = 0xE8DCFF;

    private GenjutsuSceneRenderer() {}

    // ---------------------------------------------------------------- state

    public static void begin(GenjutsuPackets.Scene packet) {
        GenjutsuScene parsed = GenjutsuScenes.fromJson(packet.json()).orElse(null);
        Minecraft minecraft = Minecraft.getInstance();
        if (parsed == null || minecraft.player == null) {
            return;
        }
        boolean fresh = scene == null;
        scene = parsed;
        sceneId = packet.id();
        total = Math.max(1, packet.ticks());
        left = total;
        if (fresh) {
            age = 0;
        }
        fadeOut = 0;
        casterId = packet.casterId();
        anchor = new Vec3(packet.ax(), packet.ay(), packet.az());
        decoy = Double.isNaN(packet.dx()) ? null : new Vec3(packet.dx(), packet.dy(), packet.dz());
        allies = packet.allies();
        facing = minecraft.player.getYRot();
        shatterColor = GenjutsuScene.color(scene.shatter());
        stopDrone();
        if (scene.sounds().drone()) {
            drone = new SimpleSoundInstance(com.noirm.sound.NoirmSounds.GENJUTSU_DRONE.get().getLocation(), SoundSource.PLAYERS, 0.9f, 1f,
                    RandomSource.create(), true, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
            minecraft.getSoundManager().play(drone);
        }
        play(com.noirm.sound.NoirmSounds.GENJUTSU.get(), 1f, 0.8f);
    }

    public static void end(int how) {
        if (scene == null) {
            return;
        }
        if (how == GenjutsuPackets.END_SHATTER) {
            captureNext = true; // the next frame is grabbed, then broken into shards
            play(com.noirm.sound.NoirmSounds.GENJUTSU_SHATTER.get(), 1f, 1f);
        } else if (fadeOut == 0) {
            fadeOut = FADE_OUT;
            left = 0;
        }
    }

    /** Is a scene on screen (tests, screenshots)? */
    public static boolean active() {
        return scene != null;
    }

    public static String sceneId() {
        return scene == null ? "" : sceneId;
    }

    private static void clear() {
        scene = null;
        left = 0;
        fadeOut = 0;
        stopDrone();
    }

    private static void stopDrone() {
        if (drone != null) {
            Minecraft.getInstance().getSoundManager().stop(drone);
            drone = null;
        }
    }

    private static float strength(float partial) {
        if (scene == null) {
            return 0f;
        }
        float in = Mth.clamp((age + partial) / FADE_IN, 0f, 1f);
        float out = fadeOut > 0 ? Mth.clamp((fadeOut - partial) / FADE_OUT, 0f, 1f) : 1f;
        float s = Math.min(in, out);
        return s * s * (3 - 2 * s);
    }

    /** Time inside the scene (slowed for the Tsukuyomi), in ticks. */
    private static float sceneTime(float partial) {
        return (age + partial) * (scene == null ? 1f : scene.timeScale());
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (shatterAge >= 0 && ++shatterAge > SHATTER_TICKS) {
            shatterAge = -1;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (scene == null) {
            return;
        }
        if (minecraft.player == null || !minecraft.player.isAlive()) {
            clear();
            return;
        }
        age++;
        if (fadeOut > 0) {
            if (--fadeOut == 0) {
                clear();
            }
            return;
        }
        if (--left <= 0) {
            fadeOut = FADE_OUT;
            stopDrone();
            return;
        }
        GenjutsuScene.Sounds sounds = scene.sounds();
        if (sounds.heartbeat() > 0 && age % sounds.heartbeat() == 1) {
            play(com.noirm.sound.NoirmSounds.GENJUTSU_HEARTBEAT.get(), 1f, 1f);
        }
        if (sounds.whispers() > 0 && age % sounds.whispers() == 7) {
            play(com.noirm.sound.NoirmSounds.GENJUTSU_WHISPERS.get(), 0.8f, 0.9f + minecraft.level.random.nextFloat() * 0.2f);
        }
        if (sounds.crows() > 0 && age % sounds.crows() == 3) {
            play(com.noirm.sound.NoirmSounds.GENJUTSU_CROWS.get(), 0.9f, 0.9f + minecraft.level.random.nextFloat() * 0.2f);
        }
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        shatterAge = -1;
        captureNext = false;
    }

    private static void play(SoundEvent sound, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound.getLocation(), SoundSource.PLAYERS, volume, pitch,
                RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }

    // ---------------------------------------------------------------- sound: the real world goes dull

    @SubscribeEvent
    public static void muffle(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (scene == null || sound == null || sound instanceof TickableSoundInstance || fadeOut > 0) {
            return;
        }
        ResourceLocation id = sound.getLocation();
        if (Noirm.MOD_ID.equals(id.getNamespace()) && (id.getPath().startsWith("genjutsu.") || id.getPath().equals("big.genjutsu"))) {
            return;
        }
        if (sound.getSource() == SoundSource.MASTER || sound.getSource() == SoundSource.MUSIC) {
            return; // the UI and music stay as they are
        }
        event.setSound(new Muffled(sound, scene.sounds().muffle(), scene.sounds().pitch()));
    }

    /** A sound heard through the illusion: quieter and slower. */
    private record Muffled(SoundInstance inner, float volume, float pitch) implements SoundInstance {
        @Override
        public ResourceLocation getLocation() {
            return inner.getLocation();
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            return inner.resolve(manager);
        }

        @Override
        public Sound getSound() {
            return inner.getSound();
        }

        @Override
        public SoundSource getSource() {
            return inner.getSource();
        }

        @Override
        public boolean isLooping() {
            return inner.isLooping();
        }

        @Override
        public boolean isRelative() {
            return inner.isRelative();
        }

        @Override
        public int getDelay() {
            return inner.getDelay();
        }

        @Override
        public float getVolume() {
            return inner.getVolume() * volume;
        }

        @Override
        public float getPitch() {
            return inner.getPitch() * pitch;
        }

        @Override
        public double getX() {
            return inner.getX();
        }

        @Override
        public double getY() {
            return inner.getY();
        }

        @Override
        public double getZ() {
            return inner.getZ();
        }

        @Override
        public Attenuation getAttenuation() {
            return inner.getAttenuation();
        }
    }

    // ---------------------------------------------------------------- fog: the real world fades

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void fog(ViewportEvent.RenderFog event) {
        float s = strength((float) event.getPartialTick());
        if (s <= 0) {
            return;
        }
        GenjutsuScene.Sky sky = scene.sky();
        if ("none".equals(sky.fog())) {
            return;
        }
        if (event.getMode() == FogRenderer.FogMode.FOG_SKY) {
            event.setNearPlaneDistance(Mth.lerp(s, event.getNearPlaneDistance(), 0f));
            event.setFarPlaneDistance(Mth.lerp(s, event.getFarPlaneDistance(), 1f));
        } else {
            event.setNearPlaneDistance(Mth.lerp(s, event.getNearPlaneDistance(), sky.fogStart()));
            event.setFarPlaneDistance(Mth.lerp(s, event.getFarPlaneDistance(), sky.fogEnd()));
        }
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        float s = strength((float) event.getPartialTick());
        if (s <= 0 || "none".equals(scene.sky().fog())) {
            return;
        }
        int c = GenjutsuScene.color(scene.sky().fog());
        event.setRed(Mth.lerp(s, event.getRed(), (c >> 16 & 0xFF) / 255f));
        event.setGreen(Mth.lerp(s, event.getGreen(), (c >> 8 & 0xFF) / 255f));
        event.setBlue(Mth.lerp(s, event.getBlue(), (c & 0xFF) / 255f));
    }

    // ---------------------------------------------------------------- the world: sky, ground, figures

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (scene == null) {
            return;
        }
        float partial = event.getPartialTick();
        float s = strength(partial);
        if (s <= 0) {
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            drawSky(event.getPoseStack().last().pose(), s, partial);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            Vec3 cam = event.getCamera().getPosition();
            drawGround(event.getPoseStack().last().pose(), cam, s, partial);
            drawFigures(event, cam, s, partial);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            drawScreen(event, s);
        }
    }

    private static void drawSky(Matrix4f matrix, float s, float partial) {
        GenjutsuScene.Sky sky = scene.sky();
        if ("none".equals(sky.top()) || "none".equals(sky.horizon())) {
            return; // the real sky stays
        }
        int top = GenjutsuScene.color(sky.top());
        int horizon = GenjutsuScene.color(sky.horizon());
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float radius = 80f;
        int rings = 12, segments = 32;
        int alpha = Math.round(255 * s);
        for (int i = -2; i < rings; i++) {
            float e0 = (float) Math.toRadians(90.0 * i / rings), e1 = (float) Math.toRadians(90.0 * (i + 1) / rings);
            int c0 = lerpColor(horizon, top, Math.max(0, i) / (float) rings);
            int c1 = lerpColor(horizon, top, Math.max(0, i + 1) / (float) rings);
            for (int k = 0; k < segments; k++) {
                float a0 = Mth.TWO_PI * k / segments, a1 = Mth.TWO_PI * (k + 1) / segments;
                float[] p00 = dome(radius, e0, a0), p01 = dome(radius, e0, a1), p10 = dome(radius, e1, a0), p11 = dome(radius, e1, a1);
                tri(b, matrix, p00, c0, p01, c0, p11, c1, alpha);
                tri(b, matrix, p00, c0, p11, c1, p10, c1, alpha);
            }
        }
        if (!sky.moon().isEmpty() && sky.moonSize() > 0) {
            int moon = GenjutsuScene.color(sky.moon());
            float size = (float) Math.toRadians(sky.moonSize());
            float yaw = (float) Math.toRadians(-facing); // ahead of where you faced when caught
            float elev = (float) Math.toRadians(17);
            int cells = 14;
            for (int gx = 0; gx < cells; gx++) {
                for (int gy = 0; gy < cells; gy++) {
                    float u0 = (gx / (float) cells - 0.5f) * 2, u1 = ((gx + 1) / (float) cells - 0.5f) * 2;
                    float v0 = (gy / (float) cells - 0.5f) * 2, v1 = ((gy + 1) / (float) cells - 0.5f) * 2;
                    float cu = (u0 + u1) / 2, cv = (v0 + v1) / 2;
                    if (cu * cu + cv * cv > 1) {
                        continue;
                    }
                    int col = moon;
                    if (sky.stripes() && ((int) Math.floor((cv + 1) * 3.5f + Mth.sin(cu * 3 + sceneTime(partial) * 0.01f) * 0.3f)) % 2 == 1) {
                        col = 0x080000;
                    }
                    float[] q00 = sphere(radius * 0.95f, elev + v0 * size, yaw + u0 * size), q10 = sphere(radius * 0.95f, elev + v0 * size, yaw + u1 * size);
                    float[] q01 = sphere(radius * 0.95f, elev + v1 * size, yaw + u0 * size), q11 = sphere(radius * 0.95f, elev + v1 * size, yaw + u1 * size);
                    tri(b, matrix, q00, col, q10, col, q11, col, alpha);
                    tri(b, matrix, q00, col, q11, col, q01, col, alpha);
                }
            }
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static float[] dome(float r, float elevation, float azimuth) {
        return sphere(r, elevation, azimuth);
    }

    private static float[] sphere(float r, float elevation, float azimuth) {
        float c = Mth.cos(elevation);
        return new float[] {r * c * Mth.sin(azimuth), r * Mth.sin(elevation), r * c * Mth.cos(azimuth)};
    }

    private static void tri(BufferBuilder b, Matrix4f m, float[] p, int cp, float[] q, int cq, float[] r, int cr, int alpha) {
        b.vertex(m, p[0], p[1], p[2]).color(cp >> 16 & 0xFF, cp >> 8 & 0xFF, cp & 0xFF, alpha).endVertex();
        b.vertex(m, q[0], q[1], q[2]).color(cq >> 16 & 0xFF, cq >> 8 & 0xFF, cq & 0xFF, alpha).endVertex();
        b.vertex(m, r[0], r[1], r[2]).color(cr >> 16 & 0xFF, cr >> 8 & 0xFF, cr & 0xFF, alpha).endVertex();
    }

    private static int lerpColor(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int r = Math.round(Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF));
        int g = Math.round(Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF));
        int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return r << 16 | g << 8 | bl;
    }

    /** The ground: a disc of ink, water or blood at the height you were caught, rippling out from your feet. */
    private static void drawGround(Matrix4f matrix, Vec3 cam, float s, float partial) {
        GenjutsuScene.Ground ground = scene.ground();
        if ("none".equals(ground.kind()) || ground.radius() <= 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 me = minecraft.player.getPosition(partial);
        float y = (float) (anchor.y + 0.035 - cam.y);
        float cx = (float) (me.x - cam.x), cz = (float) (me.z - cam.z);
        int base = GenjutsuScene.color(ground.color());
        int rim = GenjutsuScene.color(ground.rim());
        float t = sceneTime(partial);
        boolean water = "water".equals(ground.kind());
        float speed = water ? 0.03f : 0.06f;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float radius = ground.radius();
        int rings = Math.max(8, Math.round(radius * 1.5f));
        int segments = 56;
        for (int i = 0; i < rings; i++) {
            float r0 = radius * i / rings, r1 = radius * (i + 1) / rings;
            int c0 = groundColor(base, rim, r0, t, speed, water);
            int c1 = groundColor(base, rim, r1, t, speed, water);
            int a0 = groundAlpha(r0, radius, s), a1 = groundAlpha(r1, radius, s);
            for (int k = 0; k < segments; k++) {
                float ang0 = Mth.TWO_PI * k / segments, ang1 = Mth.TWO_PI * (k + 1) / segments;
                float w0 = wobble(ang0, r0, t), w1 = wobble(ang1, r0, t), w2 = wobble(ang0, r1, t), w3 = wobble(ang1, r1, t);
                float[] p00 = {cx + Mth.cos(ang0) * r0 * w0, y, cz + Mth.sin(ang0) * r0 * w0};
                float[] p01 = {cx + Mth.cos(ang1) * r0 * w1, y, cz + Mth.sin(ang1) * r0 * w1};
                float[] p10 = {cx + Mth.cos(ang0) * r1 * w2, y, cz + Mth.sin(ang0) * r1 * w2};
                float[] p11 = {cx + Mth.cos(ang1) * r1 * w3, y, cz + Mth.sin(ang1) * r1 * w3};
                tri(b, matrix, p00, c0, p01, c0, p11, c1, a0);
                b.vertex(matrix, p11[0], p11[1], p11[2]).color(c1 >> 16 & 0xFF, c1 >> 8 & 0xFF, c1 & 0xFF, a1).endVertex();
                b.vertex(matrix, p10[0], p10[1], p10[2]).color(c1 >> 16 & 0xFF, c1 >> 8 & 0xFF, c1 & 0xFF, a1).endVertex();
                b.vertex(matrix, p00[0], p00[1], p00[2]).color(c0 >> 16 & 0xFF, c0 >> 8 & 0xFF, c0 & 0xFF, a0).endVertex();
            }
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static float wobble(float angle, float r, float t) {
        return 1f + 0.035f * Mth.sin(angle * 5 + t * 0.05f + r * 0.4f);
    }

    private static int groundColor(int base, int rim, float r, float t, float speed, boolean water) {
        float wave = (r - t * speed * 20f) / (water ? 2.6f : 3.2f);
        float band = wave - (float) Math.floor(wave);
        float line = band < 0.12f ? 1f - band / 0.12f : 0f;
        return lerpColor(base, rim, line * (water ? 0.8f : 0.65f));
    }

    private static int groundAlpha(float r, float radius, float s) {
        float edge = r < radius * 0.6f ? 1f : 1f - (r - radius * 0.6f) / (radius * 0.4f);
        return Math.round(255 * s * Mth.clamp(edge, 0f, 1f) * 0.94f);
    }

    private static void drawFigures(RenderLevelStageEvent event, Vec3 cam, float s, float partial) {
        Minecraft minecraft = Minecraft.getInstance();
        PoseStack pose = event.getPoseStack();
        Matrix4f matrix = pose.last().pose();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Vec3 me = minecraft.player.getPosition(partial);
        Vec3 center = new Vec3(me.x, anchor.y, me.z);
        float t = sceneTime(partial);
        float progress = Mth.clamp((age + partial) / Math.max(1f, total), 0f, 1f);
        Entity caster = casterId < 0 ? null : minecraft.level.getEntity(casterId);
        Camera camera = event.getCamera();
        Vector3f right = new Vector3f(camera.getLeftVector()).negate();
        Vector3f up = new Vector3f(camera.getUpVector());
        List<float[]> geometry = new ArrayList<>(); // prisms: x0,y0,z0,x1,y1,z1,r0,r1,color
        for (GenjutsuScene.Figure figure : scene.figures()) {
            int color = GenjutsuScene.color(figure.color());
            switch (figure.type()) {
                case "crow" -> drawCrows(buffers, matrix, cam, center, figure, color, t, s, right, up);
                case "caster", "phantom", "crew" -> drawPeople(minecraft, figure, caster, pose, buffers, cam, center, color, t, progress, s, partial);
                case "eye" -> drawEye(buffers, matrix, cam, center, figure, color, t, s, right, up);
                case "tree" -> tree(geometry, center, figure, color, progress, t);
                case "tendril" -> tendrils(geometry, center, figure, color, progress, t);
                case "post" -> posts(geometry, center, figure, color);
                default -> { }
            }
        }
        if (!geometry.isEmpty()) {
            drawPrisms(matrix, cam, geometry, s);
        }
        if (scene.swapAllies()) {
            drawAlliesAsEnemies(minecraft, pose, buffers, cam, partial, s);
        }
    }

    // ---- crows

    private static void drawCrows(MultiBufferSource.BufferSource buffers, Matrix4f matrix, Vec3 cam, Vec3 center, GenjutsuScene.Figure figure,
                                  int color, float t, float s, Vector3f right, Vector3f up) {
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(SpaceTimeDraw.CROW));
        Vec3 eye = Minecraft.getInstance().player.getEyePosition();
        for (int i = 0; i < figure.count(); i++) {
            float seed = i * 12.9898f;
            float phase = frac(Mth.sin(seed) * 43758.5f);
            float speed = 0.025f + 0.02f * phase;
            float angle = phase * Mth.TWO_PI + t * speed * (i % 2 == 0 ? 1 : -1);
            float radius = figure.radius() * (0.55f + 0.45f * frac(phase * 7.3f)) * (0.8f + 0.2f * Mth.sin(t * 0.03f + i));
            float height = figure.height() + 1.5f * Mth.sin(t * 0.04f + i * 1.7f) + frac(phase * 3.1f) * 2f;
            Vec3 pos = center.add(Mth.cos(angle) * radius, height, Mth.sin(angle) * radius);
            // Every so often a crow swoops straight past your face.
            float cycle = ((t * 0.012f + phase) % 1f + 1f) % 1f;
            if ("swarm".equals(figure.motion()) && cycle > 0.86f) {
                float k = (cycle - 0.86f) / 0.14f;
                Vec3 through = eye.add(new Vec3(Mth.cos(angle + 2.2f), 0.1, Mth.sin(angle + 2.2f)).scale(radius * 0.8));
                Vec3 side = new Vec3(Mth.cos(angle + 1.1f), 0, Mth.sin(angle + 1.1f)).scale(1.1).add(0, 0.45, 0);
                Vec3 past = eye.add(side);
                pos = k < 0.5f ? pos.lerp(past, k * 2) : past.lerp(through, (k - 0.5f) * 2);
            }
            boolean flap = ((int) (t * 0.35f + i)) % 2 == 0;
            float size = 0.9f + 0.4f * frac(phase * 5.7f);
            SpaceTimeDraw.billboard(out, matrix, pos, cam, right, up, size, color, s, flap ? 0f : 0.5f, flap ? 0.5f : 1f);
        }
        buffers.endBatch(RenderType.entityTranslucent(SpaceTimeDraw.CROW));
    }

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    // ---- people: copies of the caster, ink phantoms, your crew turned to shadows

    private static void drawPeople(Minecraft minecraft, GenjutsuScene.Figure figure, Entity caster, PoseStack pose,
                                   MultiBufferSource.BufferSource buffers, Vec3 cam, Vec3 center, int color, float t, float progress, float s, float partial) {
        List<Entity> models = new ArrayList<>();
        if ("crew".equals(figure.type())) {
            for (int id : allies) {
                Entity e = minecraft.level.getEntity(id);
                if (e instanceof LivingEntity) {
                    models.add(e);
                }
            }
            if (models.isEmpty()) {
                for (Player p : minecraft.level.players()) {
                    if (p != minecraft.player) {
                        models.add(p);
                    }
                }
            }
        }
        if (models.isEmpty()) {
            models.add(caster != null ? caster : minecraft.player);
        }
        int count = figure.count();
        for (int i = 0; i < count; i++) {
            Entity model = models.get(i % models.size());
            Vec3 pos;
            if ("stand".equals(figure.motion()) && decoy != null && "caster".equals(figure.type()) && i == 0) {
                pos = decoy; // the false image stands where you think they are
            } else {
                float radius = figure.radius();
                if ("close_in".equals(figure.motion())) {
                    radius = Mth.lerp(Math.min(1f, progress * 1.4f), figure.radius(), 1.6f);
                }
                float angle = Mth.TWO_PI * i / count + ("circle".equals(figure.motion()) ? t * 0.008f : 0f) + (float) Math.toRadians(-facing) + Mth.HALF_PI;
                pos = center.add(Mth.cos(angle) * radius, figure.height(), Mth.sin(angle) * radius);
            }
            float yaw = (float) (Mth.atan2(center.z - pos.z, center.x - pos.x) * Mth.RAD_TO_DEG) - 90f;
            if (model instanceof LivingEntity living) {
                withYaw(living, yaw, () -> {
                    if ("caster".equals(figure.type())) {
                        SpaceTimeDraw.copy(living, pos, cam, pose, buffers, partial, LightTexture.FULL_BRIGHT);
                        buffers.endBatch();
                    } else {
                        SpaceTimeDraw.silhouette(living, pos, cam, pose, buffers, partial, color, 0.86f * s, false);
                        SpaceTimeDraw.silhouette(living, pos.add(0, 0.02, 0), cam, pose, buffers, partial, 0x6A1FA0, 0.18f * s, true);
                    }
                });
            }
        }
    }

    /** Turns {@code entity} to {@code yaw} for one draw, then puts it back. */
    private static void withYaw(LivingEntity entity, float yaw, Runnable draw) {
        float bodyRot = entity.yBodyRot, bodyRotO = entity.yBodyRotO, headRot = entity.yHeadRot, headRotO = entity.yHeadRotO;
        float yRot = entity.getYRot(), yRotO = entity.yRotO;
        entity.yBodyRot = entity.yBodyRotO = yaw;
        entity.yHeadRot = entity.yHeadRotO = yaw;
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        try {
            draw.run();
        } finally {
            entity.yBodyRot = bodyRot;
            entity.yBodyRotO = bodyRotO;
            entity.yHeadRot = headRot;
            entity.yHeadRotO = headRotO;
            entity.setYRot(yRot);
            entity.yRotO = yRotO;
        }
    }

    // ---- the eye in the sky

    private static void drawEye(MultiBufferSource.BufferSource buffers, Matrix4f matrix, Vec3 cam, Vec3 center, GenjutsuScene.Figure figure,
                                int color, float t, float s, Vector3f right, Vector3f up) {
        float yaw = (float) Math.toRadians(-facing) + Mth.PI;
        Vec3 dir = new Vec3(Mth.sin(yaw), 0, Mth.cos(yaw)).scale(-1);
        Vec3 pos = center.add(dir.scale(figure.radius())).add(0, figure.height(), 0);
        float open = Mth.clamp(t / 60f, 0f, 1f);
        VertexConsumer out = buffers.getBuffer(RenderType.eyes(SpaceTimeDraw.EYE));
        float size = figure.count() * 4f;
        Vector3f squash = new Vector3f(up).mul(open * open);
        SpaceTimeDraw.billboard(out, matrix, pos, cam, right, squash, size, color, s * (0.6f + 0.4f * open), 0, 1);
        buffers.endBatch(RenderType.eyes(SpaceTimeDraw.EYE));
    }

    // ---- geometry: trees, ink hands, posts

    /** A tree growing round you: a trunk at your back and branches curling round your body, slowly closing. */
    private static void tree(List<float[]> out, Vec3 center, GenjutsuScene.Figure figure, int color, float progress, float t) {
        float grow = Mth.clamp(progress * 2.5f, 0f, 1f);
        float yawRad = (float) Math.toRadians(facing);
        Vec3 look = new Vec3(-Mth.sin(yawRad), 0, Mth.cos(yawRad));
        float back = (float) Math.atan2(-look.x, -look.z); // the side you're facing away from (sin/cos form below)
        Vec3 trunkBase = center.subtract(look.scale(1.1));
        float height = figure.height() <= 0 ? 7f : figure.height();
        // the trunk, leaning a little
        Vec3 prev = trunkBase.add(0, -0.5, 0);
        int steps = 10;
        for (int i = 1; i <= Math.round(steps * grow); i++) {
            float k = i / (float) steps;
            Vec3 next = trunkBase.add(Mth.sin(k * 2.1f + t * 0.01f) * 0.25, k * height, Mth.cos(k * 1.7f) * 0.2);
            out.add(prism(prev, next, 0.55f - 0.3f * k, 0.55f - 0.3f * (k + 0.1f), color));
            prev = next;
        }
        // branches spiralling round the body, closing in
        int branches = Math.max(3, figure.count());
        for (int b = 0; b < branches; b++) {
            float h0 = 0.3f + b * (1.7f / branches);
            float start = back + (b % 2 == 0 ? 0.4f : -0.4f); // from the trunk at your back, round you
            float dirSign = b % 2 == 0 ? 1 : -1;
            float reach = Mth.clamp(grow * 1.6f - b * 0.08f, 0f, 1f);
            Vec3 last = trunkBase.add(0, h0, 0);
            int seg = 14;
            for (int i = 1; i <= Math.round(seg * reach); i++) {
                float k = i / (float) seg;
                float ang = start + dirSign * k * Mth.TWO_PI * 0.85f;
                float r = Mth.lerp(k, 1.25f, 0.72f + 0.05f * Mth.sin(t * 0.05f + b));
                Vec3 next = center.add(Mth.sin(ang) * r, h0 + k * 0.35f, Mth.cos(ang) * r);
                int bark = i % 3 == 0 ? lerpColor(color, 0x101008, 0.35f) : color;
                out.add(prism(last, next, 0.11f * (1 - k * 0.6f), 0.11f * (1 - (k + 0.07f) * 0.6f), bark));
                if (i % 4 == 2) {
                    // a twig with a few dark leaves, splitting off the branch
                    Vec3 twig = next.add(Mth.sin(ang + 1.2f) * 0.35, 0.25, Mth.cos(ang + 1.2f) * 0.35);
                    out.add(prism(next, twig, 0.035f, 0.01f, bark));
                    out.add(prism(twig, twig.add(0.06, 0.1, 0.04), 0.09f, 0.05f, lerpColor(color, 0x16200C, 0.7f)));
                }
                last = next;
            }
        }
        // a canopy of dark leaves overhead (a few blocks of crown)
        if (grow > 0.7f) {
            for (int i = 0; i < 9; i++) {
                float a = i * 0.7f;
                Vec3 p = trunkBase.add(Mth.sin(a) * (1.5f + i * 0.2f), height - 0.5f + (i % 3) * 0.4f, Mth.cos(a) * (1.5f + i * 0.2f));
                out.add(prism(p, p.add(0, 0.9, 0), 0.8f, 0.6f, lerpColor(color, 0x0A1006, 0.5f)));
            }
        }
    }

    /** Ink hands rising from the ground and reaching for you. */
    private static void tendrils(List<float[]> out, Vec3 center, GenjutsuScene.Figure figure, int color, float progress, float t) {
        float grow = Mth.clamp(progress * 2f, 0f, 1f);
        for (int i = 0; i < figure.count(); i++) {
            float a = Mth.TWO_PI * i / figure.count() + 0.3f;
            Vec3 root = center.add(Mth.cos(a) * figure.radius(), -0.2, Mth.sin(a) * figure.radius());
            Vec3 grip = center.add(Mth.cos(a + 0.5f) * 0.35, 0.6 + (i % 4) * 0.35, Mth.sin(a + 0.5f) * 0.35);
            Vec3 last = root;
            int seg = 12;
            for (int s = 1; s <= Math.round(seg * grow); s++) {
                float k = s / (float) seg;
                Vec3 straight = root.lerp(grip, k);
                Vec3 next = straight.add(0, Mth.sin(k * Mth.PI) * 1.4, 0)
                        .add(Mth.sin(t * 0.08f + i + k * 4) * 0.12, 0, Mth.cos(t * 0.07f + i + k * 3) * 0.12);
                out.add(prism(last, next, 0.2f * (1 - k * 0.7f), 0.2f * (1 - (k + 0.08f) * 0.7f), color));
                last = next;
            }
            if (grow >= 1f) {
                // the hand at the end: three short fingers
                for (int f = -1; f <= 1; f++) {
                    Vec3 tip = last.add(Mth.cos(a + f * 0.6f) * -0.25, 0.15, Mth.sin(a + f * 0.6f) * -0.25);
                    out.add(prism(last, tip, 0.05f, 0.02f, color));
                }
            }
        }
    }

    /** Bare wooden posts with a cross-beam standing in a ring (the Tsukuyomi's field; nobody on them). */
    private static void posts(List<float[]> out, Vec3 center, GenjutsuScene.Figure figure, int color) {
        for (int i = 0; i < figure.count(); i++) {
            float a = Mth.TWO_PI * i / figure.count() + (float) Math.toRadians(-facing);
            Vec3 base = center.add(Mth.cos(a) * figure.radius(), -0.5, Mth.sin(a) * figure.radius());
            float h = figure.height() <= 0 ? 6f : figure.height();
            out.add(prism(base, base.add(0, h, 0), 0.22f, 0.2f, color));
            Vec3 side = new Vec3(-Mth.sin(a), 0, Mth.cos(a)).scale(1.3);
            Vec3 beam = base.add(0, h - 1.2, 0);
            out.add(prism(beam.subtract(side), beam.add(side), 0.14f, 0.14f, color));
            out.add(prism(beam.add(side.scale(0.4)).add(0, -0.2, 0), beam.add(side.scale(0.5)).add(0, -1.1, 0), 0.025f, 0.025f, 0x6A5A40)); // a rope end
        }
    }

    private static float[] prism(Vec3 a, Vec3 b, float r0, float r1, int color) {
        return new float[] {(float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z, r0, r1, color};
    }

    /** Draws square tubes between points, shaded by which way each face looks (light from above). */
    private static void drawPrisms(Matrix4f matrix, Vec3 cam, List<float[]> prisms, float s) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int alpha = Math.round(255 * s);
        for (float[] p : prisms) {
            Vector3f a = new Vector3f((float) (p[0] - cam.x), (float) (p[1] - cam.y), (float) (p[2] - cam.z));
            Vector3f c = new Vector3f((float) (p[3] - cam.x), (float) (p[4] - cam.y), (float) (p[5] - cam.z));
            Vector3f axis = new Vector3f(c).sub(a);
            if (axis.lengthSquared() < 1e-6) {
                continue;
            }
            axis.normalize();
            Vector3f u = Math.abs(axis.y) > 0.9f ? new Vector3f(1, 0, 0).cross(axis).normalize() : new Vector3f(0, 1, 0).cross(axis).normalize();
            Vector3f v = new Vector3f(axis).cross(u).normalize();
            int color = (int) p[8];
            Vector3f[] dirs = {u, v, new Vector3f(u).negate(), new Vector3f(v).negate()};
            for (int f = 0; f < 4; f++) {
                Vector3f d0 = dirs[f], d1 = dirs[(f + 1) % 4];
                Vector3f n = new Vector3f(d0).add(d1).normalize();
                float light = 0.55f + 0.45f * Math.max(0f, n.y * 0.8f + 0.3f);
                int col = lerpColor(0, color, light);
                int r = col >> 16 & 0xFF, g = col >> 8 & 0xFF, bl = col & 0xFF;
                put(b, matrix, a, d0, p[6], r, g, bl, alpha);
                put(b, matrix, a, d1, p[6], r, g, bl, alpha);
                put(b, matrix, c, d1, p[7], r, g, bl, alpha);
                put(b, matrix, c, d0, p[7], r, g, bl, alpha);
            }
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void put(BufferBuilder b, Matrix4f m, Vector3f at, Vector3f dir, float radius, int r, int g, int bl, int a) {
        b.vertex(m, at.x + dir.x * radius, at.y + dir.y * radius, at.z + dir.z * radius).color(r, g, bl, a).endVertex();
    }

    // ---- Kotoamatsukami: your allies look like enemies

    private static void drawAlliesAsEnemies(Minecraft minecraft, PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cam, float partial, float s) {
        for (int id : allies) {
            Entity e = minecraft.level.getEntity(id);
            if (e instanceof LivingEntity living && living.isAlive()) {
                Vec3 at = living.getPosition(partial);
                SpaceTimeDraw.silhouette(living, at, cam, pose, buffers, partial, 0x140204, Math.max(0.35f, 0.95f * s), false);
                SpaceTimeDraw.silhouette(living, at, cam, pose, buffers, partial, 0xFF1A08, 0.3f * s, true);
            }
        }
    }

    /** The caster you think you see isn't there: hidden (False Surroundings shows them elsewhere). */
    @SubscribeEvent
    public static void hideCaster(RenderLivingEvent.Pre<?, ?> event) {
        if (scene == null || SpaceTimeDraw.drawingSilhouette || fadeOut > 0) {
            return;
        }
        int id = event.getEntity().getId();
        if (scene.hideCaster() && id == casterId) {
            event.setCanceled(true);
        } else if (scene.swapAllies()) {
            for (int ally : allies) {
                if (ally == id) {
                    event.setCanceled(true); // drawn as an enemy shadow instead (drawAlliesAsEnemies)
                    return;
                }
            }
        }
    }

    // ---------------------------------------------------------------- the screen: tint, negative, HUD, counter, shatter

    static void renderOverlay(ForgeGui gui, GuiGraphics graphics, float partial, int width, int height) {
        float s = strength(partial);
        if (s > 0) {
            // Ink closing in from the edges, breathing.
            float breath = 0.75f + 0.25f * Mth.sin((age + partial) * 0.12f);
            int edge = Math.round(height * 0.18f * s * breath);
            int ink = Math.round(200 * s) << 24 | 0x050208;
            graphics.fillGradient(0, 0, width, edge, ink, 0);
            graphics.fillGradient(0, height - edge, width, height, 0, ink);
            if (scene.timeScale() < 1f) {
                counter(graphics, width, s);
            }
        }
    }

    /**
     * The whole view, at the end of the world pass (so it holds even with the HUD hidden): the red negative
     * of the Tsukuyomi (black becomes red, light becomes black), then the scene's colour wash.
     */
    private static void drawScreen(RenderLevelStageEvent event, float s) {
        Camera camera = event.getCamera();
        Matrix4f matrix = event.getPoseStack().last().pose();
        Vector3f forward = new Vector3f(camera.getLookVector()).mul(0.3f);
        Vector3f left = new Vector3f(camera.getLeftVector()).mul(3f);
        Vector3f up = new Vector3f(camera.getUpVector()).mul(3f);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        if ("invert_red".equals(scene.post())) {
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
            quad(matrix, forward, left, up, s, s, s, s);
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ZERO,
                    GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
            quad(matrix, forward, left, up, 1f, Mth.lerp(s, 1f, 0.1f), Mth.lerp(s, 1f, 0.08f), 1f);
        }
        if (scene.tintAlpha() > 0) {
            RenderSystem.defaultBlendFunc();
            int c = GenjutsuScene.color(scene.tint());
            quad(matrix, forward, left, up, (c >> 16 & 0xFF) / 255f, (c >> 8 & 0xFF) / 255f, (c & 0xFF) / 255f, scene.tintAlpha() * s);
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void quad(Matrix4f m, Vector3f f, Vector3f l, Vector3f u, float r, float g, float b, float a) {
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(m, f.x + l.x - u.x, f.y + l.y - u.y, f.z + l.z - u.z).color(r, g, b, a).endVertex();
        buf.vertex(m, f.x - l.x - u.x, f.y - l.y - u.y, f.z - l.z - u.z).color(r, g, b, a).endVertex();
        buf.vertex(m, f.x - l.x + u.x, f.y - l.y + u.y, f.z - l.z + u.z).color(r, g, b, a).endVertex();
        buf.vertex(m, f.x + l.x + u.x, f.y + l.y + u.y, f.z + l.z + u.z).color(r, g, b, a).endVertex();
        BufferUploader.drawWithShader(buf.end());
    }

    /** The Tsukuyomi's clock: a moment outside, days inside. */
    private static void counter(GuiGraphics graphics, int width, float s) {
        Minecraft minecraft = Minecraft.getInstance();
        float realLeft = Math.max(0, left) / 20f;
        float insideLeft = TSUKUYOMI_HOURS * Math.max(0, left) / (float) total;
        int hours = (int) insideLeft;
        int minutes = (int) ((insideLeft - hours) * 60);
        int alpha = Math.round(230 * s) << 24;
        Component inside = Component.translatable("hud.noirm.genjutsu.inside", hours, String.format("%02d", minutes));
        Component real = Component.translatable("hud.noirm.genjutsu.real", String.format("%.1f", realLeft));
        graphics.drawCenteredString(minecraft.font, inside, width / 2, 14, alpha | 0xFF3A30);
        graphics.drawCenteredString(minecraft.font, real, width / 2, 26, alpha | 0xC8B8B8);
    }

    /** The HUD stutters inside an illusion. */
    @SubscribeEvent
    public static void flickerHud(RenderGuiOverlayEvent.Pre event) {
        if (scene == null || scene.hudFlicker() <= 0 || fadeOut > 0 || !"minecraft".equals(event.getOverlay().id().getNamespace())) {
            return;
        }
        int slot = (age / 2) * 7919 + event.getOverlay().id().getPath().hashCode();
        float noise = frac(Mth.sin(slot * 0.731f) * 9871.3f);
        if (noise < scene.hudFlicker() * 0.45f) {
            event.setCanceled(true);
        }
    }

    /** Above everything: grab the last frame of a broken scene, then let it fall apart. */
    static void renderShatter(ForgeGui gui, GuiGraphics graphics, float partial, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (captureNext) {
            captureNext = false;
            capture(minecraft);
            clear();
            shatterAge = 0;
            return;
        }
        if (shatterAge < 0 || shatterTarget == null) {
            return;
        }
        float t = Mth.clamp((shatterAge + partial) / SHATTER_TICKS, 0f, 1f);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, shatterTarget.getColorTextureId());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        Matrix4f m = graphics.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
        float cx = width * 0.5f, cy = height * 0.48f;
        int rings = 4, sectors = 9;
        float maxR = (float) Math.hypot(width, height) * 0.62f;
        java.util.Random random = new java.util.Random(4242);
        float[][] radii = new float[rings + 1][sectors];
        for (int ri = 0; ri <= rings; ri++) {
            for (int si = 0; si < sectors; si++) {
                float base = ri == 0 ? 0 : maxR * (ri / (float) rings) * (ri / (float) rings * 0.6f + 0.4f);
                radii[ri][si] = ri == 0 || ri == rings ? base * (ri == rings ? 1.4f : 1f) : base * (0.8f + 0.4f * random.nextFloat());
            }
        }
        float[] angles = new float[sectors];
        for (int si = 0; si < sectors; si++) {
            angles[si] = Mth.TWO_PI * si / sectors + (random.nextFloat() - 0.5f) * 0.4f;
        }
        int alpha = Math.round(255 * (1f - t * t));
        for (int ri = 0; ri < rings; ri++) {
            for (int si = 0; si < sectors; si++) {
                int sj = (si + 1) % sectors;
                float[][] pts = {
                        polar(cx, cy, radii[ri][si], angles[si]), polar(cx, cy, radii[ri][sj], angles[sj]),
                        polar(cx, cy, radii[ri + 1][sj], angles[sj]), polar(cx, cy, radii[ri + 1][si], angles[si])};
                for (int tri = 0; tri < 2; tri++) {
                    float[][] shard = tri == 0 ? new float[][] {pts[0], pts[1], pts[2]} : new float[][] {pts[0], pts[2], pts[3]};
                    float mx = (shard[0][0] + shard[1][0] + shard[2][0]) / 3, my = (shard[0][1] + shard[1][1] + shard[2][1]) / 3;
                    float dx = mx - cx, dy = my - cy;
                    float len = Math.max(1f, (float) Math.hypot(dx, dy));
                    float push = t * (40 + 70 * ri) + random.nextFloat() * 10 * t;
                    float fall = t * t * height * (0.5f + random.nextFloat() * 0.4f);
                    float spin = t * (random.nextFloat() - 0.5f) * 2.2f;
                    float ox = dx / len * push, oy = dy / len * push + fall;
                    for (float[] p : shard) {
                        float px = p[0] - mx, py = p[1] - my;
                        float rx = px * Mth.cos(spin) - py * Mth.sin(spin), ry = px * Mth.sin(spin) + py * Mth.cos(spin);
                        float shrink = 1f - 0.25f * t;
                        float x = mx + rx * shrink + ox, y = my + ry * shrink + oy;
                        b.vertex(m, x, y, 0).uv(p[0] / width, 1f - p[1] / height).color(255, 255, 255, alpha).endVertex();
                    }
                }
            }
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        // the cracks flash white for an instant
        if (t < 0.2f) {
            int a = Math.round(200 * (1f - t / 0.2f));
            graphics.fill(0, 0, width, height, (a << 24) | shatterColor);
        }
    }

    private static float[] polar(float cx, float cy, float r, float a) {
        return new float[] {cx + Mth.cos(a) * r, cy + Mth.sin(a) * r};
    }

    private static void capture(Minecraft minecraft) {
        RenderTarget main = minecraft.getMainRenderTarget();
        if (shatterTarget == null || shatterTarget.width != main.width || shatterTarget.height != main.height) {
            if (shatterTarget != null) {
                shatterTarget.destroyBuffers();
            }
            shatterTarget = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, shatterTarget.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, main.width, main.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        // The main target's alpha isn't 1 everywhere: make the copy opaque so the shards are solid glass.
        shatterTarget.bindWrite(false);
        RenderSystem.colorMask(false, false, false, true);
        RenderSystem.clearColor(0f, 0f, 0f, 1f);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.colorMask(true, true, true, true);
        main.bindWrite(true);
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class GenjutsuSceneRegistration {
        private GenjutsuSceneRegistration() {}

        @SubscribeEvent
        public static void registerGenjutsuScene(RegisterGuiOverlaysEvent event) {
            event.registerBelowAll("genjutsu_scene", GenjutsuSceneRenderer::renderOverlay);
            event.registerAboveAll("genjutsu_shatter", GenjutsuSceneRenderer::renderShatter);
        }
    }
}
