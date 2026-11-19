package com.noirm.client.hound;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.noirm.hound.HoundLook;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The hound's shape data: the shared puppy recipe and the ear and tail types
 * ({@code assets/noirm/hound/shapes.json}) and each breed's adult recipe
 * ({@code assets/noirm/hound/breeds/<breed>.json}). Edit those files to change how hounds look.
 */
public final class HoundShapes {
    /** One loaded set of shapes. */
    public static final class Shapes {
        private final HoundRigBuilder.Recipe puppy;
        private final Map<HoundLook.Breed, HoundRigBuilder.Recipe> adults = new EnumMap<>(HoundLook.Breed.class);
        private final Map<HoundLook.Breed, HoundRigBuilder.Recipe> puppies = new EnumMap<>(HoundLook.Breed.class);
        private final Map<String, JsonObject> ears = new HashMap<>();
        private final Map<String, JsonObject> tails = new HashMap<>();

        Shapes(JsonObject shapes, Map<HoundLook.Breed, JsonObject> breeds) {
            puppy = recipe(shapes.getAsJsonObject("puppy"));
            shapes.getAsJsonObject("ears").entrySet().forEach(e -> ears.put(e.getKey(), e.getValue().getAsJsonObject()));
            shapes.getAsJsonObject("tails").entrySet().forEach(e -> tails.put(e.getKey(), e.getValue().getAsJsonObject()));
            for (HoundLook.Breed b : HoundLook.Breed.values()) {
                JsonObject json = breeds.get(b);
                HoundRigBuilder.Recipe adult = json == null ? breeds.values().stream().findFirst().map(j -> recipe(j.getAsJsonObject("adult")))
                        .orElse(puppy.copy()) : recipe(json.getAsJsonObject("adult"));
                adults.put(b, adult);
                HoundRigBuilder.Recipe p = puppy.copy();
                if (json != null && json.has("puppy")) {
                    apply(p, json.getAsJsonObject("puppy"));
                }
                puppies.put(b, p);
            }
        }

        public HoundRigBuilder.Recipe adult(HoundLook.Breed breed) {
            return adults.get(breed).copy();
        }

        public HoundRigBuilder.Recipe puppy(HoundLook.Breed breed) {
            return puppies.get(breed).copy();
        }

        /** The ear and tail types on top of a recipe ({@code forPuppy}: the type's puppy values where it has them). */
        public HoundRigBuilder.Recipe withParts(HoundRigBuilder.Recipe r, HoundLook look, boolean forPuppy) {
            JsonObject ear = ears.get(look.ears().key());
            if (ear != null) {
                if (ear.has("set")) {
                    apply(r, ear.getAsJsonObject("set"));
                }
                if (forPuppy && ear.has("puppy")) {
                    apply(r, ear.getAsJsonObject("puppy"));
                }
            }
            JsonObject tail = tails.get(look.tail().key());
            if (tail != null) {
                if (tail.has("set") && !forPuppy) {
                    apply(r, tail.getAsJsonObject("set"));
                }
                if (tail.has("mult")) {
                    for (Map.Entry<String, JsonElement> e : tail.getAsJsonObject("mult").entrySet()) {
                        float[] a = r.v.get(e.getKey());
                        if (a != null) {
                            for (int i = 0; i < a.length; i++) {
                                a[i] *= e.getValue().getAsFloat();
                            }
                        }
                    }
                }
            }
            return r;
        }
    }

    private HoundShapes() {}

    static HoundRigBuilder.Recipe recipe(JsonObject json) {
        HoundRigBuilder.Recipe r = new HoundRigBuilder.Recipe();
        apply(r, json);
        return r;
    }

    static void apply(HoundRigBuilder.Recipe r, JsonObject json) {
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            if (e.getKey().startsWith("_")) {
                continue;
            }
            JsonElement v = e.getValue();
            if (v.isJsonArray()) {
                JsonArray a = v.getAsJsonArray();
                float[] out = new float[a.size()];
                for (int i = 0; i < out.length; i++) {
                    out[i] = a.get(i).getAsFloat();
                }
                r.v.put(e.getKey(), out);
            } else if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
                r.v.put(e.getKey(), new float[]{v.getAsFloat()});
            }
        }
    }
}
