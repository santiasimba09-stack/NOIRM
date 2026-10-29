package com.noirm.guide;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The Shinobi Handbook's chapters, in order. Each has a title ({@code handbook.noirm.<id>}), a body
 * ({@code handbook.noirm.<id>.body}, paragraphs split by blank lines) and a few items drawn as its
 * illustration. The text is in the lang files; the client's {@code HandbookScreen} lays it out
 * (and the controls chapter also draws your real key bindings).
 */
public final class Handbook {
    /** One chapter: its id and the items that illustrate it. */
    public record Chapter(String id, List<ResourceLocation> icons) {
        public String titleKey() {
            return "handbook.noirm." + id;
        }

        public String bodyKey() {
            return "handbook.noirm." + id + ".body";
        }
    }

    public static final List<Chapter> CHAPTERS = List.of(
            chapter("welcome", "noirm:shinobi_handbook", "minecraft:iron_sword", "noirm:ramen"),
            chapter("controls", "noirm:training_dummy", "minecraft:iron_sword", "minecraft:shield"),
            chapter("stats", "noirm:training_dummy", "minecraft:feather", "minecraft:golden_apple", "minecraft:iron_chestplate"),
            chapter("natures", "noirm:chakra_paper", "minecraft:oak_leaves", "minecraft:campfire", "minecraft:lightning_rod"),
            chapter("scrolls", "noirm:jutsu_scroll_c", "noirm:jutsu_scroll_b", "noirm:nature_scroll_fire"),
            chapter("missions", "noirm:mission_board", "noirm:ryo_pouch", "noirm:mission_parcel"),
            chapter("crew", "noirm:sparring_ninja_spawn_egg", "minecraft:bread", "noirm:flak_vest"),
            chapter("masters", "noirm:master_spawn_egg", "noirm:antidote", "minecraft:book"),
            chapter("sage", "minecraft:mossy_cobblestone", "minecraft:lily_pad", "minecraft:stone"),
            chapter("bingo", "noirm:bingo_book", "noirm:ryo_pouch", "minecraft:skeleton_skull"),
            chapter("beasts", "noirm:sealing_scroll", "noirm:sharingan_eye", "minecraft:ender_eye"));

    private Handbook() {}

    private static Chapter chapter(String id, String... icons) {
        return new Chapter(id, java.util.Arrays.stream(icons).map(ResourceLocation::tryParse).toList());
    }

    public static Chapter byId(String id) {
        return CHAPTERS.stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }
}
