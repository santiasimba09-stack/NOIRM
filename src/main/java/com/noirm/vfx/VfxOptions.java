package com.noirm.vfx;

import com.mojang.brigadier.StringReader;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Settings for one NOIRM particle: its colour at birth and death, its size at birth and death,
 * how long it lives, and how it moves (gravity, drag). Used by every {@link VfxKind}.
 *
 * @param color    0xRRGGBB at birth
 * @param endColor 0xRRGGBB at death (it blends between)
 * @param size     size at birth (blocks, roughly)
 * @param endSize  size at death
 * @param lifetime ticks
 * @param gravity  positive pulls down, negative floats up
 * @param drag     velocity kept per tick (0.9 = slows fast, 1 = no drag)
 */
public record VfxOptions(VfxKind kind, int color, int endColor, float size, float endSize, int lifetime, float gravity, float drag)
        implements ParticleOptions {

    public static Codec<VfxOptions> codec(VfxKind kind) {
        return RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("color").forGetter(VfxOptions::color),
                Codec.INT.fieldOf("end_color").forGetter(VfxOptions::endColor),
                Codec.FLOAT.fieldOf("size").forGetter(VfxOptions::size),
                Codec.FLOAT.fieldOf("end_size").forGetter(VfxOptions::endSize),
                Codec.INT.fieldOf("lifetime").forGetter(VfxOptions::lifetime),
                Codec.FLOAT.fieldOf("gravity").forGetter(VfxOptions::gravity),
                Codec.FLOAT.fieldOf("drag").forGetter(VfxOptions::drag)
        ).apply(i, (c, e, s, es, l, g, d) -> new VfxOptions(kind, c, e, s, es, l, g, d)));
    }

    @SuppressWarnings("deprecation")
    public static final ParticleOptions.Deserializer<VfxOptions> DESERIALIZER = new ParticleOptions.Deserializer<>() {
        @Override
        public VfxOptions fromCommand(ParticleType<VfxOptions> type, StringReader reader) {
            return new VfxOptions(VfxKind.of(type), 0xFFFFFF, 0xFFFFFF, 0.5f, 0.1f, 20, 0f, 0.96f);
        }

        @Override
        public VfxOptions fromNetwork(ParticleType<VfxOptions> type, FriendlyByteBuf buf) {
            return new VfxOptions(VfxKind.of(type), buf.readInt(), buf.readInt(), buf.readFloat(), buf.readFloat(),
                    buf.readVarInt(), buf.readFloat(), buf.readFloat());
        }
    };

    @Override
    public ParticleType<?> getType() {
        return kind.type();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeInt(color);
        buf.writeInt(endColor);
        buf.writeFloat(size);
        buf.writeFloat(endSize);
        buf.writeVarInt(lifetime);
        buf.writeFloat(gravity);
        buf.writeFloat(drag);
    }

    @Override
    public String writeToString() {
        return String.valueOf(ForgeRegistries.PARTICLE_TYPES.getKey(getType()));
    }
}
