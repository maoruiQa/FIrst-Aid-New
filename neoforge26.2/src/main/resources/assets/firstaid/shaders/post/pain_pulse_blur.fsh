#version 330

uniform sampler2D InSampler;
in vec2 texCoord;

layout(std140) uniform BlurSettings {
    vec2 Center;
    int Samples;
    float Strength;
};

out vec4 fragColor;

vec2 clampUv(vec2 uv) {
    return clamp(uv, vec2(0.002), vec2(0.998));
}

// Radial pulse blur + jagged replicas.
// Strength tiers are tuned so mild/medium feel like the previous "extreme" look.
void main() {
    float effectStrength = max(0.0, Strength);

    vec4 color = vec4(0.0);
    float weight = 0.0;
    int sampleCount = max(2, Samples);
    for (int i = 0; i < sampleCount; i++) {
        float f = float(i) / float(sampleCount - 1) * effectStrength;
        vec2 sampleUv = clampUv(mix(texCoord, Center, f));
        float w = 1.0 - f * 0.60;
        color += texture(InSampler, sampleUv) * w;
        weight += w;
    }
    vec3 blurred = (color / max(weight, 0.0001)).rgb;

    float angle = 1.7 + length(texCoord - Center) * 3.5;
    vec2 dir = vec2(cos(angle), sin(angle));
    float len = length(texCoord - Center);
    vec3 accum = blurred;
    const int replicas = 3;
    for (int r = 1; r <= replicas; r++) {
        float t = float(r) / float(replicas);
        float radius = (0.010 + effectStrength * 0.50) * t + 0.018 * len * t;
        float n = fract(sin(dot(texCoord * (9.0 + float(r)), vec2(12.9898, 78.233))) * 43758.5453);
        float jaggedScalar = (n - 0.5) * (0.0025 + effectStrength * 0.020);
        vec2 jagged = vec2(jaggedScalar, jaggedScalar * 0.65);
        vec2 offset = dir * radius + jagged;
        accum += texture(InSampler, clampUv(texCoord + offset)).rgb;
    }

    // Less original mix — blur must be obvious at mild/medium.
    float keep = 1.0 - clamp(effectStrength * 4.0, 0.20, 0.78);
    vec3 original = texture(InSampler, texCoord).rgb;
    vec3 result = mix(accum / float(replicas + 1), original, keep);
    fragColor = vec4(result, 1.0);
}
