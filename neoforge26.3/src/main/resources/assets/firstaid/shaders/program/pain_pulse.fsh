#version 150

uniform sampler2D DiffuseSampler;
uniform float Time;
uniform float Strength;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 center = vec2(0.5, 0.5);
    float pulse = 0.55 + 0.45 * sin(Time * 28.0);
    float effectStrength = Strength * pulse;
    vec4 color = vec4(0.0);
    float weight = 0.0;
    const int samples = 8;
    for (int i = 0; i < samples; i++) {
        float f = float(i) / float(samples - 1) * effectStrength;
        vec2 sampleUv = mix(texCoord, center, f);
        float w = 1.0 - f;
        color += texture(DiffuseSampler, sampleUv) * w;
        weight += w;
    }
    fragColor = color / max(weight, 0.0001);
    fragColor.a = 1.0;
}
