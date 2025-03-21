precision highp float;

varying vec2 vUV;

uniform sampler2D textureSampler;
uniform sampler2D noiseTexture;
uniform float time;
uniform  float intensity;

void main(void) {
    vec2 uv = vUV;

    // Create a horizontal mask: effect is strongest at the top (uv.y = 1)
    float mask = smoothstep(-0.5, 1., -uv.y);

    // Wave effect based on vertical coordinate and time
    float wave1 = 0.02 * sin(uv.y * 20.0 - time * 40.0);
    float wave2 = 0.01 * sin(uv.y * 40.0 - time * 80.0);
    float wave = (wave1 + wave2) * mask;

    // Sample noise texture for randomness (optional)
    float noise = texture2D(noiseTexture, uv * 5.0).r;
    wave += noise * 0.005;
    wave *= intensity;

    // Distort UVs horizontally
    vec2 distortedUV = uv + vec2(wave, 0.0);

    // Sample textures with distorted UVs
    vec4 color = texture2D(textureSampler, distortedUV);

    gl_FragColor = color;
}
