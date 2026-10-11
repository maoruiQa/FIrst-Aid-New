#version 150

uniform sampler2D DiffuseSampler;
uniform float Time;
uniform float Strength;

in vec2 texCoord;
out vec4 fragColor;

vec2 clampUv(vec2 uv) {
    return clamp(uv, vec2(0.002), vec2(0.998));
}

// Radial pulse blur + subtle jagged replicas (Tarkov-like pain).
// Strength is set each frame: PAIN_SHADER_PEAK * painIntensity.
void main() {
    vec2 center = vec2(0.5, 0.5);
    float pulse = 0.90 + 0.10 * sin(Time * 10.0);
    float effectStrength = max(0.0, Strength) * pulse;

    vec4 color = vec4(0.0);
    float weight = 0.0;
    const int samples = 10;
    for (int i = 0; i < samples; i++) {
        float f = float(i) / float(samples - 1) * effectStrength;
        vec2 sampleUv = clampUv(mix(texCoord, center, f));
        float w = 1.0 - f * 0.65;
        color += texture(DiffuseSampler, sampleUv) * w;
        weight += w;
    }
    vec3 blurred = (color / max(weight, 0.0001)).rgb;

    float angle = 1.7 + length(texCoord - center) * 3.5;
    vec2 dir = vec2(cos(angle), sin(angle));
    float len = length(texCoord - center);
    vec3 accum = blurred;
    const int replicas = 3;
    for (int r = 1; r <= replicas; r++) {
        float t = float(r) / float(replicas);
        float radius = (0.008 + effectStrength * 0.45) * t + 0.016 * len * t;
        float n = fract(sin(dot(texCoord * (9.0 + float(r)), vec2(12.9898, 78.233)) + Time * 6.0) * 43758.5453);
        float jaggedScalar = (n - 0.5) * (0.002 + effectStrength * 0.018);
        vec2 jagged = vec2(jaggedScalar, jaggedScalar * 0.65);
        vec2 offset = dir * radius + jagged;
        accum += texture(DiffuseSampler, clampUv(texCoord + offset)).rgb;
    }

    // Keep less original so blur is actually visible (was almost invisible before).
    float keep = 1.0 - clamp(effectStrength * 4.5, 0.0, 0.75);
    vec3 original = texture(DiffuseSampler, texCoord).rgb;
    vec3 result = mix(accum / float(replicas + 1), original, keep);
    fragColor = vec4(result, 1.0);
}
