R"SHADER(
precision VRB_FRAGMENT_PRECISION float;

uniform sampler2D u_texture0;
varying vec4 v_color;
varying vec2 v_uv;

void main() {
  vec4 color = vec4(1.0f, 1.0f, 1.0f, 1.0f);
  if ((v_uv.x < 0.0f) || (v_uv.x > 1.0f)) {
    color.a = 0.0f;
  }
  gl_FragColor = color * v_color;
}

)SHADER";
