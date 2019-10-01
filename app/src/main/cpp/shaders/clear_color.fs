R"SHADER(
precision VRB_FRAGMENT_PRECISION float;

uniform sampler2D u_texture0;
varying vec4 v_color;
varying vec2 v_uv;

void main() {
  vec4 color = vec4(1.0, 1.0, 1.0, 1.0);
  if ((v_uv.x < 0.0) || (v_uv.x > 1.0)) {
    color.a = 0.0;
  }
  gl_FragColor = color * v_color;
}

)SHADER";
