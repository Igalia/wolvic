/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "SplashAnimation.h"
#include "DeviceDelegate.h"
#include "VRLayer.h"
#include "VRLayerNode.h"
#include "vrb/ConcreteClass.h"
#include "vrb/Color.h"
#include "vrb/CreationContext.h"
#include "vrb/GLError.h"
#include "vrb/FBO.h"
#include "vrb/Geometry.h"
#include "vrb/Matrix.h"
#include "vrb/ModelLoaderAndroid.h"
#include "vrb/RenderState.h"
#include "vrb/RenderContext.h"
#include "vrb/TextureGL.h"
#include "vrb/Toggle.h"
#include "vrb/Transform.h"

#include "Quad.h"

#define SPLASH_SECONDS 2.3f
#define FADE_OUT_TIME 0.3f

namespace crow {

struct SplashAnimation::State {
  vrb::CreationContextWeak context;
  vrb::TogglePtr root;
  vrb::FBOPtr read;
  QuadPtr logo;
  VRLayerQuadPtr layer;
  timespec start;
  float time;
  bool firstDraw;
  State(): time(-1), firstDraw(true)
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

  void HandleLayerInitialized(const vrb::TextureGLPtr& aTexture) {
    if (!read || !read->IsValid()) {
      return;
    }

    layer->Bind(GL_DRAW_FRAMEBUFFER);
    read->Bind(GL_READ_FRAMEBUFFER);
    VRB_GL_CHECK(glBlitFramebuffer(0, 0, aTexture->GetWidth(), aTexture->GetHeight(),
#if PICOXR
                                   0, aTexture->GetHeight(), aTexture->GetWidth(), 0,
#else
                                   0, 0, aTexture->GetWidth(), aTexture->GetHeight(),
#endif
                                   GL_COLOR_BUFFER_BIT, GL_LINEAR));
    layer->Unbind();
    read->Unbind();
  }
};


void
SplashAnimation::Load(vrb::RenderContextPtr& aContext, const DeviceDelegatePtr& aDeviceDelegate) {
  if (m.logo) {
    return;
  }
  vrb::CreationContextPtr create = m.context.lock();
#if OCULUSVR || PICOXR || PFDMXR
  VRB_GL_CHECK(glDisable(GL_FRAMEBUFFER_SRGB_EXT));
#endif
  vrb::TextureGLPtr texture = create->LoadTexture("logo.png");
  texture->SetTextureParameter(GL_TEXTURE_MAG_FILTER, GL_LINEAR);
  texture->SetTextureParameter(GL_TEXTURE_MIN_FILTER, GL_LINEAR);
  const float w = 0.75f;
  const float aspect = (float)texture->GetWidth() / (float)texture->GetHeight();
  m.layer = aDeviceDelegate->CreateLayerQuad(texture->GetWidth(), texture->GetHeight(), VRLayerQuad::SurfaceType::FBO);
  if (m.layer) {
    texture->Bind();
    m.read = vrb::FBO::Create(aContext);
    vrb::FBO::Attributes attributes;
    attributes.depth = false;
    attributes.samples = 0;
    m.read->SetTextureHandle(texture->GetHandle(), texture->GetWidth(), texture->GetHeight(), attributes);
    if (!m.read->IsValid()) {
      VRB_WARN("Splash FBO is not valid");
    }
    m.layer->SetSurfaceChangedDelegate([=](const VRLayer& aLayer, VRLayer::SurfaceChange aChange, const std::function<void()>& aCallback){
      if (aChange == VRLayer::SurfaceChange::Create) {
        m.HandleLayerInitialized(texture);
      }
      if (aCallback) {
        aCallback();
      }
    });
  }
  m.logo = Quad::Create(create, w, w / aspect, m.layer);
  if (!m.layer) {
    m.logo->SetTexture(texture, texture->GetWidth(), texture->GetHeight());
    m.logo->UpdateProgram("");
  }
  m.root->AddNode(m.logo->GetRoot());
}

bool
SplashAnimation::Update(const vrb::Matrix& aHeadTransform) {
  if (!m.logo) {
    return false;
  }
  vrb::Vector position = aHeadTransform.GetTranslation();
  if (m.firstDraw && position.Magnitude() > 0.1f) {
    static const vrb::Vector offset(0.0f, -0.2f, -1.5f);
    m.logo->GetTransformNode()->SetTransform(vrb::Matrix::Position(position + offset));
    m.firstDraw = false;
  }
  m.UpdateTime();
  if (m.time >= SPLASH_SECONDS && m.time <= (SPLASH_SECONDS + FADE_OUT_TIME)) {
    float t = 1.0f - (m.time - SPLASH_SECONDS) / FADE_OUT_TIME;
    m.logo->SetTintColor(vrb::Color(t, t, t, 1.0f));
  }
  return m.time >= SPLASH_SECONDS + FADE_OUT_TIME;
}

vrb::NodePtr
SplashAnimation::GetRoot() const {
  return m.root;
}

VRLayerQuadPtr
SplashAnimation::GetLayer() const {
  return m.layer;
}

SplashAnimationPtr
SplashAnimation::Create(vrb::CreationContextPtr aContext) {
  return std::make_shared<vrb::ConcreteClass<SplashAnimation, SplashAnimation::State> >(aContext);
}


SplashAnimation::SplashAnimation(State& aState, vrb::CreationContextPtr& aContext) : m(aState) {
  m.context = aContext;
  m.Initialize();
}

} // namespace crow
