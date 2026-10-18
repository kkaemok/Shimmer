#version 330
#extension GL_ARB_separate_shader_objects : require

uniform samplerCube Sampler0;
layout(std140) uniform HdrSettings {
    vec4 Settings; // exposure in EV, curve (0 ACES / 1 Reinhard), reserved
};

layout(location = 0) in vec3 texCoord0;
layout(location = 0) out vec4 fragColor;

vec3 linearToSrgb(vec3 c) {
    return mix(12.92 * c, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055,
               step(vec3(0.0031308), c));
}

void main() {
    vec3 hdr = texture(Sampler0, texCoord0).rgb;
    // Reject invalid samples before arithmetic, including overflow during interpolation.
    if (any(isnan(hdr)) || any(isinf(hdr))) hdr = vec3(0.0);
    // Tone curves have reached their asymptote above this bound. Clamping here prevents
    // overflow for huge but finite RGBE values; textures retain the original values.
    vec3 x = min(max(hdr, vec3(0.0)) * exp2(Settings.x), vec3(1.0e10));
    vec3 mapped;
    if (Settings.y < 0.5) {
        // Narkowicz ACES fitted approximation, evaluated as a ratio to avoid x*x overflow.
        mapped = (x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14);
    } else {
        // Luminance-preserving Reinhard retains hue/saturation better than per-channel mapping.
        float luminance = dot(x, vec3(0.2126, 0.7152, 0.0722));
        mapped = x / (1.0 + luminance);
    }
    // Vanilla 26.3 MainTarget and presentation use RGBA8_UNORM, without an sRGB
    // attachment conversion. Encode exactly once, here, before vanilla GUI composition.
    fragColor = vec4(linearToSrgb(clamp(mapped, 0.0, 1.0)), 1.0);
}
