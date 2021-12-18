#version 300 es

precision mediump float;

uniform sampler2D u_CpuImageTexture;

in vec2 v_CameraTexCoord;

layout(location = 0) out vec4 o_FragColor;

void main() { o_FragColor = texture(u_CpuImageTexture, v_CameraTexCoord); }