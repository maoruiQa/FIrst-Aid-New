#version 150

uniform sampler2D DiffuseSampler;
uniform float Time;
uniform float Strength;

in vec2 texCoord;
out vec4 fragColor;

vec2 clampUv(vec2 uv) {
    // Prevent black sky-edge rings from sampling outside the framebuffer.
    return clamp(uv, vec2(0.002), vec2(0.998));
}

// Soft radial pulse toward center + subtle jagged replicas (aligned with 26.2 feel).
// Strength is set each frame from Java as PAIN_SHADER_PEAK * painIntensity.
void main() {
    vec2 center = vec2(0.5, 0.5);
    // Gentle pulse â€?not a violent left-right wobble / camera-shake look
    float pulse = 0.92 + 0.08 * sin(Time * 10.0);
    float effectStrength = max(0.0, Strength) * pulse;

    vec4 color = vec4(0.0);
    float weight = 0.0;
    const int samples = 8;
    for (int i = 0; i < samples; i++) {
        float f = float(i) / float(samples - 1) * effectStrength;
        vec2 sampleUv = clampUv(mix(texCoord, center, f));
        float w = 1.0 - f * 0.70;
        color += texture(DiffuseSampler, sampleUv) * w;
        weight += w;
    }
    vec3 blurred = (color / max(weight, 0.0001)).rgb;

    float angle = 1.7 + length(texCoord - center) * 3.5;
    vec2 dir = vec2(cos(angle), sin(angle));
    float len = length(texCoord - center);
    vec3 accum = blurred;
    const int replicas = 2;
    for (int r = 1; r <= replicas; r++) {
        float t = float(r) / float(replicas);
        float radius = (0.004 + effectStrength * 0.22) * t + 0.010 * len * t;
        float n = fract(sin(dot(texCoord * (9.0 + float(r)), vec2(12.9898, 78.233)) + Time * 6.0) * 43758.5453);
        float jaggedScalar = (n - 0.5) * (0.0015 + effectStrength * 0.010);
        vec2 jagged = vec2(jaggedScalar, jaggedScalar * 0.65);
        vec2 offset = dir * radius + jagged;
        accum += texture(DiffuseSampler, clampUv(texCoord + offset)).rgb;
    }

    // Prefer original so mild pain stays readable (matches 26.2 soft keep)
    float keep = 1.0 - clamp(effectStrength * 8.0, 0.0, 0.40);
    vec3 original = texture(DiffuseSampler, texCoord).rgb;
    vec3 result = mix(accum / float(replicas + 1), original, keep);
    fragColor = vec4(result, 1.0);
}
