#version 150

// NOIRM gore on clothes: the gore texture (Sampler0), shown only where the worn piece's own
// texture (Sampler1, same UVs) has cloth or metal, so blood never floats around bare skin.

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    color.a *= step(0.1, texture(Sampler1, texCoord0).a);
    if (color.a < 0.1) {
        discard;
    }
    color *= vertexColor * ColorModulator;
    color *= lightMapColor;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
