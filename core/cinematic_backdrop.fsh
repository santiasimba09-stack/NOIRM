#version 150
uniform sampler2D Sampler0;
uniform vec4 GlassData;
in vec2 uv;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    vec2 d=GlassData.xy*1.65;
    // Normalized 3x3 Gaussian kernel; bilinear downsampling supplies the wider first stage.
    vec3 c=texture(Sampler0,uv).rgb*0.25;
    c+=(texture(Sampler0,uv+vec2(d.x,0)).rgb+texture(Sampler0,uv-vec2(d.x,0)).rgb
       +texture(Sampler0,uv+vec2(0,d.y)).rgb+texture(Sampler0,uv-vec2(0,d.y)).rgb)*0.125;
    c+=(texture(Sampler0,uv+d).rgb+texture(Sampler0,uv-d).rgb
       +texture(Sampler0,uv+vec2(d.x,-d.y)).rgb+texture(Sampler0,uv+vec2(-d.x,d.y)).rgb)*0.0625;
    float edge=smoothstep(0.0,GlassData.w,min(uv.y,1.0-uv.y));
    edge*=smoothstep(0.0,0.02,min(uv.x,1.0-uv.x));
    // Gentle neutral ink tint; no luminous bloom, flicker or blur of the actual controls.
    fragColor=vec4(c*0.76,edge*GlassData.z)*vertexColor;
}
