precision highp float;

varying vec2 vUV;

uniform sampler2D textureSampler;
uniform sampler2D noiseTexture;
uniform float time;
uniform  float intensity;

void main(void) {
    vec2 uv = vUV;
    vec2 center = vec2(0.5, 0.5);
    float dist = distance(uv, center);

    // Circular mask with smooth falloff
    float mask = smoothstep(0., 1., dist);// Adjust the range for the mask

    // Ripple effect with two frequencies
    float wave1 = 0.2 * sin(dist * 20.0 - time * 40.0) * exp(-dist * 2.0);
    float wave2 = 0.1 * sin(dist * 40.0 - time * 80.0) * exp(-dist * 5.0);
    float wave = wave1 + wave2;


    // Sample noise texture for randomness (optional)
    float noise = texture2D(noiseTexture, uv * 5.0).r;
    wave += noise * 0.01;
    wave *= intensity;

    vec2 distortedUV = uv + normalize(uv - center) * wave * mask;


    // Sample textures with distorted UVs and apply mask
    vec4 color = texture2D(textureSampler, distortedUV);

    // Apply mask to color
    gl_FragColor = color;
}
