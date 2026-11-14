#version 150

uniform vec4 PendingColor;
uniform vec4 CompletedColor;
uniform vec4 AccentColor;
// x: visible nodes, y: completed visible nodes, z: active visible node, w: true progress
uniform vec4 SealData;
// x/y: logical size, z: time, w: ready
uniform vec4 BarData;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float roundedBox(vec2 point, vec2 halfSize, float radius) {
    vec2 q = abs(point) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

float aaMask(float distanceValue) {
    float aa = max(fwidth(distanceValue), 0.42);
    return 1.0 - smoothstep(-aa, aa, distanceValue);
}

void main() {
    vec2 size = max(BarData.xy, vec2(1.0));
    vec2 px = texCoord0 * size;
    float time = BarData.z;
    float ready = BarData.w;
    float visible = clamp(floor(SealData.x + 0.5), 1.0, 7.0);
    float completed = clamp(floor(SealData.y + 0.5), 0.0, visible);
    float activeIndex = clamp(floor(SealData.z + 0.5), 0.0, visible - 1.0);
    float progress = clamp(SealData.w, 0.0, 1.0);

    float outerDistance = roundedBox(px - size * 0.5,
            vec2(size.x * 0.5 - 0.35, size.y * 0.5 - 0.55), 2.8);
    float innerDistance = roundedBox(px - size * 0.5,
            vec2(size.x * 0.5 - 1.75, size.y * 0.5 - 1.85), 1.65);
    float outer = aaMask(outerDistance);
    float inner = aaMask(innerDistance);
    float border = clamp(outer - inner, 0.0, 1.0);

    vec3 shell = mix(vec3(0.015, 0.019, 0.025), vec3(0.105, 0.125, 0.155),
            smoothstep(0.92, 0.08, texCoord0.y));
    shell = mix(shell, vec3(0.005, 0.007, 0.010), border * 0.82);

    float inset = 8.0;
    float railLeft = inset;
    float railRight = size.x - inset;
    float railWidth = max(1.0, railRight - railLeft);
    float centerY = size.y * 0.54;
    float railDistance = abs(px.y - centerY);
    float rail = (1.0 - smoothstep(0.45, 1.15, railDistance))
            * step(railLeft, px.x) * step(px.x, railRight);
    float formedX = railLeft + railWidth * progress;
    float railDone = rail * (1.0 - smoothstep(formedX - 0.7, formedX + 0.7, px.x));

    float pendingNodes = 0.0;
    float doneNodes = 0.0;
    float nodeRings = 0.0;
    float activeAura = 0.0;
    for (int i = 0; i < 7; ++i) {
        float enabled = 1.0 - step(visible - 0.5, float(i));
        float fraction = visible <= 1.5 ? 0.5 : float(i) / (visible - 1.0);
        vec2 center = vec2(mix(railLeft, railRight, fraction), centerY);
        float distanceToNode = length(px - center);
        float node = (1.0 - smoothstep(2.15, 2.85, distanceToNode)) * enabled;
        float ring = (1.0 - smoothstep(3.15, 3.85, distanceToNode)) * enabled - node;
        float isDone = (1.0 - step(completed - 0.5, float(i))) * enabled;
        float isActive = (1.0 - step(0.45, abs(float(i) - activeIndex))) * enabled * (1.0 - ready);
        float breathingRadius = 4.1 + 0.45 * sin(time * 5.2);
        float aura = (1.0 - smoothstep(breathingRadius, breathingRadius + 1.5,
                distanceToNode)) * isActive;
        pendingNodes = max(pendingNodes, node * (1.0 - isDone));
        doneNodes = max(doneNodes, node * isDone);
        nodeRings = max(nodeRings, ring * (0.46 + isActive * 0.54));
        activeAura = max(activeAura, aura);
    }

    vec3 color = shell;
    color = mix(color, PendingColor.rgb * 0.52, rail * 0.78);
    color = mix(color, CompletedColor.rgb, railDone * (0.72 + ready * 0.22));
    color = mix(color, PendingColor.rgb * 0.72, pendingNodes);
    color = mix(color, CompletedColor.rgb, doneNodes);
    color = mix(color, AccentColor.rgb, nodeRings);
    color = mix(color, mix(AccentColor.rgb, vec3(0.94, 0.98, 1.0), 0.48),
            activeAura * 0.44);

    float topBevel = inner * smoothstep(0.50, 0.12, texCoord0.y) * 0.16;
    color += topBevel * vec3(0.28, 0.34, 0.42);
    float alpha = outer * 0.97 + activeAura * 0.16;
    if (alpha < 0.01) discard;
    fragColor = vec4(color, clamp(alpha, 0.0, 1.0) * vertexColor.a);
}
