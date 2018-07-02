/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "FadeBlitter.h"
#include "vrb/ConcreteClass.h"
#include "vrb/Color.h"
#include "vrb/private/ResourceGLState.h"
#include "vrb/gl.h"
#include "vrb/GLError.h"
#include "vrb/Logger.h"
#include "vrb/ShaderUtil.h"

#include <map>

namespace {
static const char* sVertexShader = R"SHADER(
attribute vec4 a_position;
void main(void) {
  gl_Position = a_position;
}
)SHADER";

static const char* sFragmentShader = R"SHADER(
precision mediump float;

uniform vec4 u_color;

void main() {
  gl_FragColor = u_color;
}
)SHADER";

static float kFadeAlpha = 0.75f;
static int kAnimationLength = 40;

static const GLfloat sVerticies[] = {
    -1.0f, 1.0f, 0.0f,
    -1.0f, -1.0f, 0.0f,
    1.0f, 1.0f, 0.0f,
    1.0f, -1.0f, 0.0f
};

}

namespace crow {

struct FadeBlitter::State : public vrb::ResourceGL::State {
  GLuint vertexShader;
  GLuint fragmentShader;
  GLuint program;
  GLint aPosition;
  GLint uColor;
  vrb::Color fadeColor;
  float animationStartAlpha;
  float animationEndAlpha;
  int animations;
  State()
      : vertexShader(0)
      , fragmentShader(0)
      , program(0)
      , aPosition(0)
      , uColor(0)
      , fadeColor(0.0f, 0.0f, 0.0f, 0.0f)
      , animationStartAlpha(0.0f)
      , animationEndAlpha(0.0f)
      , animations(-1)
  {}
};

FadeBlitterPtr
FadeBlitter::Create(vrb::CreationContextPtr& aContext) {
  return std::make_shared<vrb::ConcreteClass<FadeBlitter, FadeBlitter::State> >(aContext);
}


void
FadeBlitter::Draw() {
  if (!m.program) {
    VRB_LOG("FadeBlitter::Draw FAILED!");
    return;
  }

  if (m.animations >= 0) {
    float t = (float)(kAnimationLength - m.animations) / (float) kAnimationLength;
    m.fadeColor.SetAlpha(m.animationStartAlpha + (m.animationEndAlpha - m.animationStartAlpha) * t);
    m.animations--;
  }

  VRB_GL_CHECK(glDisable(GL_DEPTH_TEST));
  VRB_GL_CHECK(glDepthMask(GL_FALSE));

  VRB_GL_CHECK(glUseProgram(m.program));
  VRB_GL_CHECK(glUniform4f(m.uColor, m.fadeColor.Red(), m.fadeColor.Green(), m.fadeColor.Blue(), m.fadeColor.Alpha()));
  VRB_GL_CHECK(glVertexAttribPointer((GLuint)m.aPosition, 3, GL_FLOAT, GL_FALSE, 0, sVerticies));
  VRB_GL_CHECK(glEnableVertexAttribArray((GLuint)m.aPosition));
  VRB_GL_CHECK(glDrawArrays(GL_TRIANGLE_STRIP, 0, 4));

  VRB_GL_CHECK(glEnable(GL_DEPTH_TEST));
  VRB_GL_CHECK(glDepthMask(GL_TRUE));
}

bool
FadeBlitter::IsVisible() const {
  return m.animations >= 0 ||  m.fadeColor.Alpha() > 0.0f;
}

void
FadeBlitter::FadeIn() {
  m.animationStartAlpha = kFadeAlpha;
  m.animationEndAlpha = 0.0f;
  m.animations = kAnimationLength;
}

void
FadeBlitter::FadeOut() {
  m.animationStartAlpha = 0.0f;
  m.animationEndAlpha = kFadeAlpha;
  m.animations = kAnimationLength;
}

FadeBlitter::FadeBlitter(State& aState, vrb::CreationContextPtr& aContext)
    : vrb::ResourceGL(aState, aContext)
    , m(aState)
{}

FadeBlitter::~FadeBlitter() {}

void
FadeBlitter::InitializeGL(vrb::RenderContext& aContext) {
  m.vertexShader = vrb::LoadShader(GL_VERTEX_SHADER, sVertexShader);
  m.fragmentShader = vrb::LoadShader(GL_FRAGMENT_SHADER, sFragmentShader);
  if (m.vertexShader && m.fragmentShader) {
    m.program = vrb::CreateProgram(m.vertexShader, m.fragmentShader);
  }
  if (m.program) {
    m.aPosition = vrb::GetAttributeLocation(m.program, "a_position");
    m.uColor = vrb::GetUniformLocation(m.program, "u_color");
  }
}

void
FadeBlitter::ShutdownGL(vrb::RenderContext& aContext) {
  if (m.program) {
    VRB_GL_CHECK(glDeleteProgram(m.program));
    m.program = 0;
  }
  if (m.vertexShader) {
    VRB_GL_CHECK(glDeleteShader(m.vertexShader));
    m.vertexShader = 0;
  }
  if (m.fragmentShader) {
    VRB_GL_CHECK(glDeleteShader(m.fragmentShader));
    m.fragmentShader = 0;
  }
}

} // namespace crow
