#version 150

uniform sampler2D DiffuseSampler;
uniform float Time;
uniform float Stage;
uniform float ShaderPack;
uniform vec2 InSize;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 center = vec2(0.5);
    vec2 radial = texCoord - center;
    float distanceFromCenter = length(radial);
    // Vanilla's PostChain Time advances from 0 to 1 over twenty seconds. Each profile
    // supplies one elapsed-second offset so reloading at a stage boundary remains continuous.
    float chargeSeconds = clamp(Stage + Time * 20.0, 0.0, 3.2);
    float totalProgress = chargeSeconds / 3.2;

    // FOV opens globally while the sampled scene moves toward its centre. Those opposing camera
    // motions create a dolly-zoom tunnel: the world bends inward without painting an object there.
    vec2 radialDirection = radial / max(distanceFromCenter, 0.0001);
    float focusEnvelope = smoothstep(0.3, 3.2, chargeSeconds);
    float compatibility = clamp(ShaderPack, 0.0, 1.0);
    float focusBreathing = sin(chargeSeconds * 5.5) * 0.0030 * focusEnvelope;
    float tunnelScale = 1.0 - focusEnvelope
        * (0.042 + distanceFromCenter * distanceFromCenter * 0.070)
        + focusBreathing;
    float apertureRadius = mix(0.62, 0.34, focusEnvelope);
    float apertureRefraction = 1.0 - smoothstep(0.0, 0.075,
        abs(distanceFromCenter - apertureRadius));
    float refractionDistance = apertureRefraction * focusEnvelope
        * mix(0.015, 0.010, compatibility);
    vec2 focusedUv = center + radial * tunnelScale
        - radialDirection * refractionDistance;

    float separation = max(0.0, (chargeSeconds - 1.4) / 1.8)
        * mix(0.025, 0.016, compatibility);
    vec2 chromaOffset = radial * separation;
    vec2 texel = 0.5 / max(InSize, vec2(1.0));
    vec2 uvR = clamp(focusedUv - chromaOffset, texel, vec2(1.0) - texel);
    vec2 uvG = clamp(focusedUv, texel, vec2(1.0) - texel);
    vec2 uvB = clamp(focusedUv + chromaOffset, texel, vec2(1.0) - texel);
    vec3 sourceColor = vec3(
        texture(DiffuseSampler, uvR).r,
        texture(DiffuseSampler, uvG).g,
        texture(DiffuseSampler, uvB).b
    );

    float luminance = dot(sourceColor, vec3(0.299, 0.587, 0.114));
    vec3 rinneganTone = vec3(0.60, 0.30, 0.80) * luminance * 1.30;
    float tintWeight = totalProgress * mix(0.85, 0.66, compatibility);
    vec3 graded = mix(sourceColor, mix(vec3(luminance), rinneganTone, 0.40), tintWeight);

    float vignetteProgress = clamp((chargeSeconds - 0.10) / 3.1, 0.0, 1.0);
    float peripheralAperture = mix(0.82, 0.34, vignetteProgress) + focusBreathing;
    float peripheralPressure = smoothstep(peripheralAperture - 0.10,
        peripheralAperture + 0.20, distanceFromCenter);
    float vignetteStrength = mix(0.92, 0.76, compatibility);
    graded *= 1.0 - peripheralPressure * vignetteProgress * vignetteStrength;

    fragColor = vec4(graded, 1.0);
}
