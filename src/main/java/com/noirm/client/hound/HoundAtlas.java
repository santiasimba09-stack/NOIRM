package com.noirm.client.hound;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Where each box role's six faces sit on the fur atlas ({@code assets/noirm/hound/atlas.json},
 * written by {@code tools/houndgen/atlas.py}), as 0..1 texture rectangles in {@link Bone#FACES} order.
 */
public final class HoundAtlas {
    private static final float[][] NONE = {{0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}};

    private final Map<String, float[][]> roles = new HashMap<>();
    public final int width;
    public final int height;

    HoundAtlas(JsonObject json) {
        JsonArray size = json.getAsJsonArray("size");
        width = size.get(0).getAsInt();
        height = size.get(1).getAsInt();
        for (Map.Entry<String, JsonElement> role : json.getAsJsonObject("roles").entrySet()) {
            JsonObject faces = role.getValue().getAsJsonObject();
            float[][] uv = new float[Bone.FACES.length][];
            for (int i = 0; i < Bone.FACES.length; i++) {
                JsonArray r = faces.getAsJsonArray(Bone.FACES[i]);
                // Half a texel in from each edge, so neighbours never bleed in.
                uv[i] = new float[]{(r.get(0).getAsFloat() + 0.02f) / width, (r.get(1).getAsFloat() + 0.02f) / height,
                        (r.get(2).getAsFloat() - 0.02f) / width, (r.get(3).getAsFloat() - 0.02f) / height};
            }
            roles.put(role.getKey(), uv);
        }
    }

    public float[][] uv(String role) {
        return roles.getOrDefault(role, NONE);
    }
}
