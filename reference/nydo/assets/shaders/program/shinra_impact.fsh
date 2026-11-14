#version 150

uniform sampler2D DiffuseSampler;
uniform float Time;
uniform float ShaderPack;
uniform vec2 InSize;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 center = vec2(0.5);
    float compatibility = clamp(ShaderPack, 0.0, 1.0);
    float age = clamp(Time * 20.0, 0.0, 1.0);
    float remaining = max(0.0, 1.0 - age);
    float impactFade = max(0.0, 1.0 - age * 1.1);

    vec2 shake = vec2(sin(age * 150.0), cos(age * 170.0))
        * mix(0.020, 0.013, compatibility) * impactFade;
    vec2 shakenUv = texCoord + shake;
    vec2 radial = shakenUv - center;
    float radialLength = max(length(radial), 0.0001);
    vec2 direction = radial / radialLength;

    float waveRadius = age * 1.66;
    float ringDistance = abs(radialLength - waveRadius);
    float ring = 1.0 - smoothstep(0.0, 0.15, ringDistance);
    float oscillation = sin((radialLength - waveRadius) * 40.0);
    vec2 distortedUv = shakenUv + direction * oscillation * ring * remaining
        * mix(0.050, 0.034, compatibility);

    // Release the charged tunnel as refraction, never as a black object. It collapses during the
    // same quarter-second as the FOV, then hands the frame directly to the outward pressure wave.
    float releaseTunnel = 1.0 - smoothstep(0.0, 0.20, age);
    float releaseRadius = mix(0.34, 0.50, smoothstep(0.0, 0.20, age));
    float releaseRefraction = 1.0 - smoothstep(0.0, 0.12,
        abs(radialLength - releaseRadius));
    distortedUv -= direction * releaseRefraction * releaseTunnel
        * mix(0.030, 0.020, compatibility);
    vec2 texel = 0.5 / max(InSize, vec2(1.0));
    distortedUv = clamp(distortedUv, texel, vec2(1.0) - texel);

    vec3 color = texture(DiffuseSampler, distortedUv).rgb;
    float impactDarken = 1.0 - impactFade * mix(0.25, 0.17, compatibility);
    float rimLight = ring * remaining * mix(0.50, 0.34, compatibility);
    color = color * impactDarken + color * rimLight;

    fragColor = vec4(color, 1.0);
}
