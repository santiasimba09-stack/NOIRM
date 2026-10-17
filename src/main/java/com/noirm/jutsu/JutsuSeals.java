package com.noirm.jutsu;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.noirm.Noirm;
import com.noirm.data.CodecJsonLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;

/**
 * Hand seals per jutsu, from {@code data/<ns>/jutsu_seals/<jutsu path>.json} ({@code {"seals": [...]}}),
 * written with their animations by {@code tools/animgen/seals.py}. Charged and channelled jutsu
 * play the sequence while you charge; every cast names the seals above the hotbar.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class JutsuSeals {
    /** Ticks per seal in the generated animations (keep in step with seals.py). */
    public static final int TICKS_PER_SEAL = 3;

    public record Sequence(List<String> seals) {
        public static final Codec<Sequence> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().fieldOf("seals").forGetter(Sequence::seals)
        ).apply(i, Sequence::new));

        /** The animation that forms these seals, e.g. {@code noirm:seals_katon_great_fireball}. */
        public ResourceLocation animation(ResourceLocation jutsu) {
            return new ResourceLocation(jutsu.getNamespace(), "seals_" + jutsu.getPath().replace('/', '_'));
        }

        public int ticks() {
            return TICKS_PER_SEAL * seals.size() + 4;
        }

        /** "Snake · Ram · Monkey · …" */
        public Component describe() {
            MutableComponent out = Component.empty();
            for (int i = 0; i < seals.size(); i++) {
                if (i > 0) {
                    out.append(" · ");
                }
                out.append(Component.translatable("seal.noirm." + seals.get(i)));
            }
            return out;
        }
    }

    private static Map<ResourceLocation, Sequence> sequences = Map.of();

    private JutsuSeals() {}

    public static Sequence get(ResourceLocation jutsu) {
        return jutsu == null ? null : sequences.get(jutsu);
    }

    public static Map<ResourceLocation, Sequence> all() {
        return sequences;
    }

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CodecJsonLoader<>("jutsu_seals", Sequence.CODEC, loaded -> sequences = loaded));
    }
}
