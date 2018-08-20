/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "SplashAnimation.h"
#include "vrb/ConcreteClass.h"
#include "vrb/Color.h"
#include "vrb/CreationContext.h"
#include "vrb/Geometry.h"
#include "vrb/Matrix.h"
#include "vrb/ModelLoaderAndroid.h"
#include "vrb/RenderState.h"
#include "vrb/TextureGL.h"
#include "vrb/Toggle.h"
#include "vrb/Transform.h"

#include "Quad.h"

#define SPLASH_SECONDS 2.3f
#define FADE_OUT_TIME 0.3f

namespace crow {

struct SplashAnimation::State {
  vrb::CreationContextWeak context;
  vrb::TextureGLPtr texture;
  vrb::TogglePtr root;
  QuadPtr logo;
  timespec start;
  float time;
  State(): time(-1)
  {
  }

  void Initialize() {
    vrb::CreationContextPtr create = context.lock();
    root = vrb::Toggle::Create(create);
  }

  void UpdateTime() {
    if (time < 0) {
      clock_gettime(CLOCK_MONOTONIC, &start);
      time = 0;
      return;
    }
    timespec spec;
    clock_gettime(CLOCK_MONOTONIC, &spec);
    time = (float)(spec.tv_sec - start.tv_sec) + (float)(spec.tv_nsec - start.tv_nsec) / 1e09f;
  }
};


void
SplashAnimation::Load() {
  if (m.logo) {
    return;
  }
  vrb::CreationContextPtr create = m.context.lock();
  vrb::TextureGLPtr texture = create->LoadTexture("logo.png");
  texture->SetTextureParameter(GL_TEXTURE_MAG_FILTER, GL_LINEAR);
  texture->SetTextureParameter(GL_TEXTURE_MIN_FILTER, GL_LINEAR);
  const float w = 0.75f;
  const float aspect = (float)texture->GetWidth() / (float)texture->GetHeight();
  m.logo = Quad::Create(create, w, w / aspect);
  m.logo->SetTexture(texture, texture->GetWidth(), texture->GetHeight());
  m.root->AddNode(m.logo->GetRoot());
}

bool
SplashAnimation::Update(const vrb::Matrix& aHeadTransform) {
  if (!m.logo) {
    return false;
  }
  m.UpdateTime();
  vrb::Matrix transform = aHeadTransform.PostMultiply(vrb::Matrix::Position(vrb::Vector(0.0f, 0.0f, -1.5f)));
  m.logo->GetTransformNode()->SetTransform(transform);
  if (m.time >= SPLASH_SECONDS && m.time <= (SPLASH_SECONDS + FADE_OUT_TIME)) {
    float t = 1.0f - (m.time - SPLASH_SECONDS) / FADE_OUT_TIME;
    m.logo->GetGeometry()->GetRenderState()->SetTintColor(vrb::Color(t, t, t, 1.0f));
  }
  return m.time >= SPLASH_SECONDS + FADE_OUT_TIME;
}

vrb::NodePtr
SplashAnimation::GetRoot() const {
  return m.root;
}

SplashAnimationPtr
SplashAnimation::Create(vrb::CreationContextPtr aContext) {
  return std::make_shared<vrb::ConcreteClass<SplashAnimation, SplashAnimation::State> >(aContext);
}


SplashAnimation::SplashAnimation(State& aState, vrb::CreationContextPtr& aContext) : m(aState) {
  m.context = aContext;
  m.Initialize();
}

SplashAnimation::~SplashAnimation() {}

} // namespace crow
