#version 150
uniform sampler2D Sampler0;
uniform float Cutoff;
uniform float Opacity;
uniform mat4 InverseScene;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    // Use captured geometry depth, never shaderpack luminance or sky colour heuristics.
    float depth=texture(Sampler0,texCoord).r;
    vec4 point=InverseScene*vec4(texCoord*2.0-1.0,depth*2.0-1.0,1.0);
    // NYDO's empty black plane is at -0.08; the cutout trigram seal is at +0.012.
    // Remove atmospheric fog from the empty floor as well as the distant sky.
    bool emptyFloor=abs(point.w)>0.000001 && point.y/point.w < -0.03;
    if(depth < Cutoff && !emptyFloor) discard;
    fragColor=vec4(0.0,0.0,0.0,Opacity);
}
