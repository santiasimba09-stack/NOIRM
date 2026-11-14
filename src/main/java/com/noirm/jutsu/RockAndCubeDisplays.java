package com.noirm.jutsu;

import com.noirm.Noirm;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Solid shapes for jutsu, made of vanilla <i>block displays</i>: a block drawn at any size and
 * angle, with no collision, that the client slides smoothly from one shape to the next. The Dust
 * cube is a glass block that swells, the Falling Mountain is a cluster of stone that grows in the
 * sky and drops. Vanilla draws them (no renderer of ours), and they never touch the world.
 *
 * <p>They only live as long as their jutsu: every one is removed when it ends, when the server
 * stops, and if one was ever saved by accident it is thrown away when it loads again.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class RockAndCubeDisplays {
    /** Marks our displays (so a stray saved one is recognised and dropped). */
    public static final String TAG = "noirm_jutsu_shape";
    private static final List<Display.BlockDisplay> LIVE = new ArrayList<>();

    private RockAndCubeDisplays() {}

    /**
     * A block drawn at {@code at}: scaled by {@code scale}, its corner shifted by {@code offset} (in
     * blocks), turned by {@code rotation} about {@code at}. {@code bright} draws it full-bright (light, not lit).
     */
    public static Display.BlockDisplay spawn(ServerLevel level, Vec3 at, BlockState block, Vector3f scale, Vector3f offset,
                                             Quaternionf rotation, boolean bright) {
        Display.BlockDisplay display = EntityType.BLOCK_DISPLAY.create(level);
        if (display == null) {
            return null;
        }
        display.moveTo(at.x, at.y, at.z, 0f, 0f);
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        tag.remove("UUID");
        tag.put(Display.BlockDisplay.TAG_BLOCK_STATE, NbtUtils.writeBlockState(block));
        tag.put(Display.TAG_TRANSFORMATION, transformation(scale, offset, rotation));
        tag.putInt(Display.TAG_INTERPOLATION_DURATION, 0);
        tag.putInt(Display.TAG_START_INTERPOLATION, -1);
        tag.putFloat(Display.TAG_SHADOW_RADIUS, 0f);
        if (bright) {
            CompoundTag light = new CompoundTag();
            light.putInt("sky", 15);
            light.putInt("block", 15);
            tag.put(Display.TAG_BRIGHTNESS, light);
        }
        display.load(tag);
        display.addTag(TAG);
        LIVE.add(display); // before it joins, so entityJoin lets it in
        level.addFreshEntity(display);
        return display;
    }

    /** Slides {@code display} to a new shape over {@code ticks} (the client draws the in-between). */
    public static void animate(Display.BlockDisplay display, Vector3f scale, Vector3f offset, Quaternionf rotation, int ticks) {
        if (display == null || display.isRemoved()) {
            return;
        }
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        tag.remove("UUID");
        tag.put(Display.TAG_TRANSFORMATION, transformation(scale, offset, rotation));
        tag.putInt(Display.TAG_INTERPOLATION_DURATION, Math.max(0, ticks));
        // The client only restarts the slide when this value changes, so it flips between 0 and -1.
        tag.putInt(Display.TAG_START_INTERPOLATION, tag.getInt(Display.TAG_START_INTERPOLATION) == 0 ? -1 : 0);
        display.load(tag);
    }

    public static void remove(Display.BlockDisplay display) {
        if (display != null) {
            display.discard();
            LIVE.remove(display);
        }
    }

    /** How many are alive (for tests). */
    public static int live() {
        LIVE.removeIf(Display::isRemoved);
        return LIVE.size();
    }

    private static CompoundTag transformation(Vector3f scale, Vector3f offset, Quaternionf rotation) {
        CompoundTag tag = new CompoundTag();
        tag.put("translation", floats(offset.x, offset.y, offset.z));
        tag.put("left_rotation", floats(rotation.x, rotation.y, rotation.z, rotation.w));
        tag.put("scale", floats(scale.x, scale.y, scale.z));
        tag.put("right_rotation", floats(0, 0, 0, 1));
        return tag;
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float v : values) {
            list.add(FloatTag.valueOf(v));
        }
        return list;
    }

    /**
     * The offset that centres a block of {@code size} on the display's position once it is turned by
     * {@code rotation} (vanilla turns the block about its corner, so the corner is moved back by the turned half-size).
     */
    public static Vector3f centred(Vector3f size, Quaternionf rotation) {
        Vector3f half = new Vector3f(size).mul(0.5f);
        rotation.transform(half);
        return half.negate();
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        for (Display.BlockDisplay display : new ArrayList<>(LIVE)) {
            display.discard();
        }
        LIVE.clear();
    }

    /** A shape saved by accident (the game closed mid-jutsu) is never brought back. */
    @SubscribeEvent
    public static void entityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Display.BlockDisplay display
                && display.getTags().contains(TAG) && !LIVE.contains(display)) {
            event.setCanceled(true);
        }
    }
}
