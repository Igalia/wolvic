/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "LoadingAnimation.h"
#include "vrb/ConcreteClass.h"
#include "vrb/CreationContext.h"
#include "vrb/Matrix.h"
#include "vrb/ModelLoaderAndroid.h"
#include "vrb/TextureGL.h"
#include "vrb/Toggle.h"
#include "vrb/Transform.h"

#include "Quad.h"

namespace crow {

struct LoadingAnimation::State {
  vrb::CreationContextWeak context;
  vrb::TextureGLPtr texture;
  vrb::TogglePtr root;
  QuadPtr spinner;
  float rotation;

  State()
      : rotation(0.0f)
  {}

  void Initialize() {
    vrb::CreationContextPtr create = context.lock();
    root = vrb::Toggle::Create(create);
  }
};


void
LoadingAnimation::LoadModels(const vrb::ModelLoaderAndroidPtr& aLoader) {
  vrb::LoadTask task = [this](vrb::CreationContextPtr& aContext) -> vrb::GroupPtr {
    m.texture = aContext->LoadTexture("webvr_spinner.png", true);
    return nullptr;
  };

  vrb::LoadFinishedCallback callback = [this](vrb::GroupPtr& aGroup){
    if (m.texture) {
      vrb::CreationContextPtr create = m.context.lock();
      float aspect = (float)m.texture->GetWidth() / (float)m.texture->GetHeight();
      float size = 0.25f;
      m.spinner = Quad::Create(create, size * aspect, size);
      m.spinner->SetTexture(m.texture, m.texture->GetWidth(), m.texture->GetHeight());
      m.root->AddNode(m.spinner->GetRoot());
    }
  };

  aLoader->RunLoadTask(nullptr, task, callback);
}


void
LoadingAnimation::Update() {
  if (!m.spinner) {
    return;
  }
  m.rotation += 0.1f;
  const float max = 2.0f * (float)M_PI;
  if (m.rotation > max) {
     m.rotation -= max;
  }

  vrb::Matrix transform = vrb::Matrix::Rotation(vrb::Vector(0.0f, 0.0f, -1.0f), m.rotation);
  transform.PreMultiplyInPlace(vrb::Matrix::Position(vrb::Vector(0.0f, 0.0f, -1.5f)));
  m.spinner->GetTransformNode()->SetTransform(transform);
}

vrb::NodePtr
LoadingAnimation::GetRoot() const {
  return m.root;
}

LoadingAnimationPtr
LoadingAnimation::Create(vrb::CreationContextPtr aContext) {
  return std::make_shared<vrb::ConcreteClass<LoadingAnimation, LoadingAnimation::State> >(aContext);
}


LoadingAnimation::LoadingAnimation(State& aState, vrb::CreationContextPtr& aContext) : m(aState) {
  m.context = aContext;
  m.Initialize();
}

LoadingAnimation::~LoadingAnimation() {}

} // namespace crow
