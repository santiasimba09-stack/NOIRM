#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.01) {
        discard;
    }

    vec2 cell = floor(texCoord0 * vec2(4.0, 6.0));
    float checker = mod(cell.x + cell.y, 2.0);
    float stripe = mod(cell.y, 3.0) == 0.0 ? 1.0 : 0.0;
    float voxelShade = 0.84 + checker * 0.10 + stripe * 0.08;
    color.rgb *= voxelShade;

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
