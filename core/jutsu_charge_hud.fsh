#version 150

uniform vec4 ColorA;
uniform vec4 ColorB;
// x: time, y: ready, z: truthful charge progress, w: elemental family
uniform vec4 EffectData;
uniform vec4 BarSize;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float smoothNoise(vec2 p) {
    vec2 cell = floor(p);
    vec2 local = fract(p);
    local = local * local * (3.0 - 2.0 * local);
    float a = hash21(cell);
    float b = hash21(cell + vec2(1.0, 0.0));
    float c = hash21(cell + vec2(0.0, 1.0));
    float d = hash21(cell + vec2(1.0, 1.0));
    return mix(mix(a, b, local.x), mix(c, d, local.x), local.y);
}

float softLine(float value, float width) {
    float aa = max(fwidth(value), 0.0025);
    return 1.0 - smoothstep(width - aa, width + aa, abs(value));
}

float roundedBox(vec2 point, vec2 halfSize, float radius) {
    vec2 q = abs(point) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

void main() {
    float time = EffectData.x;
    float ready = EffectData.y;
    float progress = clamp(EffectData.z, 0.0, 1.0);
    float family = EffectData.w;
    vec2 size = max(BarSize.xy, vec2(1.0));
    vec2 px = texCoord0 * size;
    float formedWidth = max(1.0, size.x * progress);

    float outerDistance = roundedBox(px - size * 0.5,
            vec2(size.x * 0.5 - 0.35, size.y * 0.5 - 0.55), 2.8);
    float innerDistance = roundedBox(px - size * 0.5,
            vec2(size.x * 0.5 - 1.75, size.y * 0.5 - 1.85), 1.65);
    float outerAa = max(fwidth(outerDistance), 0.42);
    float innerAa = max(fwidth(innerDistance), 0.42);
    float outerMask = 1.0 - smoothstep(-outerAa, outerAa, outerDistance);
    float innerMask = 1.0 - smoothstep(-innerAa, innerAa, innerDistance);
    float borderMask = clamp(outerMask - innerMask, 0.0, 1.0);

    vec2 boxCenter = vec2(formedWidth * 0.5 + 1.45, size.y * 0.5);
    vec2 boxHalf = vec2(max(0.1, formedWidth * 0.5), size.y * 0.5 - 1.85);
    float distanceToBody = roundedBox(px - boxCenter, boxHalf,
            min(2.15, formedWidth * 0.5));
    float edgeAa = max(fwidth(distanceToBody), 0.45);
    float hasCharge = smoothstep(0.0, 0.012, progress);
    float bodyMask = (1.0 - smoothstep(-edgeAa, edgeAa, distanceToBody))
            * innerMask * hasCharge;
    float halo = (1.0 - smoothstep(0.0, 2.6, distanceToBody)) * (1.0 - bodyMask);
    float localX = px.x / max(formedWidth, 1.0);
    float y = texCoord0.y;
    float travelling = time * (0.55 + ready * 0.18);

    vec3 low = ColorA.rgb;
    vec3 high = ColorB.rgb;
    vec3 color = mix(low, high, 0.24 + 0.40 * (1.0 - y));
    float alpha = bodyMask * 0.88 + halo * (0.10 + ready * 0.10);

    if (family < 0.5) {
        // Katon: the formed surface is the flame. Two incommensurate waves prevent tiled tongues.
        float flameTop = 0.31
                + 0.095 * sin(px.x * 0.31 - travelling * 4.7)
                + 0.055 * sin(px.x * 0.73 + travelling * 3.1)
                + 0.025 * sin(px.x * 1.31 - travelling * 6.2);
        float fireAa = max(fwidth(y), 0.018);
        float flameBody = smoothstep(flameTop - fireAa, flameTop + fireAa, y)
                * smoothstep(0.97, 0.82, y);
        float inner = smoothstep(flameTop + 0.04, flameTop + 0.28, y)
                * smoothstep(0.96, 0.72, y);
        float licking = 0.5 + 0.5 * sin(px.x * 0.52 - travelling * 7.0 + y * 8.0);
        color = mix(low, high, clamp(inner * 0.74 + licking * 0.18 + ready * 0.18, 0.0, 1.0));
        alpha = flameBody * (0.79 + inner * 0.18) + halo * (0.14 + ready * 0.12);
    } else if (family < 1.5) {
        // Suiton: a coherent current, moving crest and refracted light underneath.
        float crestY = 0.36 + 0.045 * sin(px.x * 0.34 - travelling * 4.4)
                + 0.022 * sin(px.x * 0.87 + travelling * 2.7);
        float water = smoothstep(crestY - 0.025, crestY + 0.025, y)
                * smoothstep(0.94, 0.84, y);
        float foam = softLine(y - crestY, 0.022);
        float caustic = pow(0.5 + 0.5 * sin(px.x * 0.41 + y * 18.0 - travelling * 5.0), 5.0);
        color = mix(low * 0.82, high, foam * 0.88 + caustic * 0.30 + ready * 0.14);
        alpha = water * (0.76 + caustic * 0.18) + foam * 0.20 + halo * 0.10;
    } else if (family < 2.5) {
        // Raiton: one continuous live conductor with a soft ion corona.
        float path = 0.55 + 0.13 * sin(px.x * 0.52 - travelling * 8.0)
                + 0.055 * sin(px.x * 1.17 + travelling * 5.4);
        float bolt = softLine(y - path, 0.035 + ready * 0.012);
        float corona = softLine(y - path, 0.17);
        float branchPath = path - 0.12 + 0.05 * sin(px.x * 0.77 + travelling * 6.0);
        float branch = softLine(y - branchPath, 0.018)
                * smoothstep(0.72, 0.20, abs(sin(px.x * 0.16 + travelling)));
        color = mix(low * 0.72, high, clamp(bolt + branch * 0.7 + ready * 0.16, 0.0, 1.0));
        alpha = bodyMask * (0.18 + corona * 0.34) + bolt * 0.72 + branch * 0.45 + halo * 0.18;
    } else if (family < 3.5) {
        // Futon: three quiet ribbons crossing inside one aerodynamic stream.
        float ribbonA = softLine(y - (0.40 + 0.07 * sin(px.x * 0.28 - travelling * 3.8)), 0.042);
        float ribbonB = softLine(y - (0.57 + 0.06 * sin(px.x * 0.35 + travelling * 3.1)), 0.032);
        float ribbonC = softLine(y - (0.71 + 0.04 * sin(px.x * 0.48 - travelling * 4.6)), 0.024);
        float ribbons = clamp(ribbonA + ribbonB * 0.84 + ribbonC * 0.68, 0.0, 1.0);
        color = mix(low * 0.70, high, ribbons * 0.78 + ready * 0.12);
        alpha = bodyMask * (0.13 + ribbons * 0.72) + halo * (0.08 + ready * 0.08);
    } else if (family < 4.5) {
        // Doton: continuous mineral mass with smoothly interpolated facets, never square cells.
        float grain = smoothNoise(vec2(px.x * 0.16, y * 4.2));
        float grainFine = smoothNoise(vec2(px.x * 0.37 + 7.0, y * 8.0));
        float vein = softLine(sin(px.x * 0.19 + y * 12.0 + grain * 4.0), 0.12);
        float facet = 0.15 + grain * 0.23 + grainFine * 0.10 + vein * 0.16;
        color = mix(low * 0.72, high, facet + ready * 0.16);
        alpha = bodyMask * (0.83 + vein * 0.10) + halo * 0.07;
    } else if (family < 5.5) {
        // Neutral chakra: layered aurora, used for non-elemental and mixed-release jutsu.
        float flowA = softLine(y - (0.44 + 0.08 * sin(px.x * 0.25 - travelling * 3.4)), 0.075);
        float flowB = softLine(y - (0.66 + 0.06 * sin(px.x * 0.39 + travelling * 4.0)), 0.052);
        float flow = clamp(flowA + flowB * 0.72, 0.0, 1.0);
        color = mix(low * 0.78, high, flow * 0.70 + ready * 0.16);
        alpha = bodyMask * (0.38 + flow * 0.53) + halo * 0.12;
    } else if (family < 6.5) {
        // Hyoton: translucent frozen body with diagonal crystal planes and cold refraction.
        float crystalA = softLine(sin(px.x * 0.21 + y * 10.0), 0.10);
        float crystalB = softLine(sin(px.x * 0.15 - y * 14.0 + 1.7), 0.075);
        float frost = smoothNoise(vec2(px.x * 0.11 - travelling * 0.3, y * 5.0));
        float crystal = clamp(crystalA * 0.56 + crystalB * 0.48, 0.0, 1.0);
        color = mix(low * 0.86, mix(high, vec3(0.93, 0.99, 1.0), 0.34),
                crystal * 0.72 + frost * 0.18 + ready * 0.14);
        alpha = bodyMask * (0.60 + crystal * 0.28) + halo * 0.13;
    } else if (family < 7.5) {
        // Yoton: a slow viscous mass whose bright thermal veins move below a cooled crust.
        float moltenFlow = smoothNoise(vec2(px.x * 0.10 - travelling * 0.55, y * 4.5));
        float thermalVein = softLine(moltenFlow - 0.52, 0.12);
        float crust = smoothNoise(vec2(px.x * 0.24 + 3.0, y * 7.0));
        color = mix(low * (0.40 + crust * 0.20), high,
                thermalVein * 0.84 + ready * 0.20);
        alpha = bodyMask * (0.82 + thermalVein * 0.14) + halo * (0.10 + ready * 0.08);
    } else if (family < 8.5) {
        // Futton: layered vapor curls, intentionally airy instead of a recolored Suiton current.
        float vaporA = softLine(y - (0.42 + 0.12 * sin(px.x * 0.18 - travelling * 2.2)), 0.10);
        float vaporB = softLine(y - (0.66 + 0.10 * sin(px.x * 0.24 + travelling * 2.7)), 0.085);
        float dissolve = smoothNoise(vec2(px.x * 0.13 + travelling * 0.7, y * 4.0));
        float vapor = clamp(vaporA * 0.72 + vaporB * 0.62, 0.0, 1.0) * (0.72 + dissolve * 0.28);
        color = mix(low * 0.64, high, vapor * 0.68 + ready * 0.12);
        alpha = bodyMask * (0.15 + vapor * 0.68) + halo * 0.16;
    } else {
        // Mokuton: living longitudinal grain rather than Doton's mineral facets.
        float rings = sin(px.x * 0.17 + sin(y * 9.0 + travelling * 0.35) * 1.8);
        float grain = softLine(rings, 0.15);
        float sap = softLine(y - (0.58 + 0.06 * sin(px.x * 0.22 - travelling)), 0.055);
        color = mix(low * 0.63, high, grain * 0.38 + sap * 0.42 + ready * 0.12);
        alpha = bodyMask * (0.76 + grain * 0.14) + halo * 0.07;
    }

    // The live frontier remains readable without a detached icon or particles.
    float frontierDistance = abs(px.x - formedWidth);
    float frontier = (1.0 - smoothstep(0.0, 2.2, frontierDistance))
            * smoothstep(0.94, 0.68, y) * smoothstep(0.17, 0.34, y);
    color = mix(color, high, frontier * (0.45 + ready * 0.28));
    alpha = clamp(alpha + frontier * 0.34, 0.0, 1.0);
    alpha *= step(px.x, formedWidth + 2.8) * innerMask * hasCharge;

    // Original thin-bar proportions with the softened corners from the visual reference.
    // All elemental motion stays clipped inside it, so the real progress remains unambiguous.
    vec3 shellColor = mix(vec3(0.012, 0.015, 0.020), vec3(0.095, 0.112, 0.140),
            smoothstep(0.94, 0.10, y));
    shellColor = mix(shellColor, vec3(0.002, 0.003, 0.005), borderMask * 0.90);
    float upperBevel = innerMask * smoothstep(0.49, 0.13, y) * 0.16;
    shellColor += upperBevel * vec3(0.27, 0.31, 0.38);
    float formedAlpha = clamp(alpha, 0.0, 1.0);
    vec3 finalColor = mix(shellColor, color, formedAlpha);
    float finalAlpha = max(outerMask * 0.98, formedAlpha);

    if (finalAlpha < 0.01) discard;
    fragColor = vec4(finalColor, finalAlpha * vertexColor.a);
}
