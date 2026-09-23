#version 150

uniform sampler2D DiffuseSampler;
uniform float Amount;

in vec2 texCoord;
out vec4 fragColor;

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

// Saturation boost while morphine is active.
void main() {
    vec4 color = texture(DiffuseSampler, texCoord);
    vec3 hsv = rgb2hsv(color.rgb);
    float amount = max(0.25, Amount);
    hsv.y = clamp(hsv.y * (1.0 + amount), 0.0, 1.0);
    // Slight value lift so the world reads clearer / more vivid.
    hsv.z = clamp(hsv.z * (1.0 + amount * 0.08), 0.0, 1.0);
    fragColor = vec4(hsv2rgb(hsv), color.a);
}
