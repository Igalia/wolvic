/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "Environment.h"
#include "vrb/ConcreteClass.h"
#include "vrb/CreationContext.h"
#include "vrb/Matrix.h"
#include "vrb/ModelLoaderAndroid.h"
#include "vrb/Light.h"
#include "vrb/TextureGL.h"
#include "vrb/Toggle.h"
#include "vrb/Transform.h"
#include "vrb/RenderContext.h"

#include "Quad.h"

namespace crow {

static const vrb::Vector FAN1_POSITION(-3.57877, 3.29641, 0.99);
static const vrb::Vector FAN2_POSITION(3.57877, 3.29641, 0.99);
static const float MAX_ROTATION = 2.0f * (float)M_PI;

struct Environment::State {
  vrb::CreationContextWeak context;
  vrb::TextureGLPtr texture;
  vrb::TogglePtr root;
  vrb::TransformPtr room;
  vrb::TransformPtr fan1;
  vrb::TransformPtr fan2;
  vrb::TransformPtr logo;
  float rotation;
  double timeStamp;

  State()
      : rotation(0.0f)
  {}

  void Initialize() {
    vrb::CreationContextPtr create = context.lock();
    root = vrb::Toggle::Create(create);
    vrb::LightPtr light = vrb::Light::Create(create);
    root->AddLight(light);
  }
};


void
Environment::LoadModels(const vrb::ModelLoaderAndroidPtr& aLoader) {
  vrb::CreationContextPtr ctx = m.context.lock();
  m.room = vrb::Transform::Create(ctx);
  m.fan1 = vrb::Transform::Create(ctx);
  m.fan2 = vrb::Transform::Create(ctx);
  m.root->AddNode(m.room);
  m.root->AddNode(m.fan1);
  m.root->AddNode(m.fan2);
  aLoader->LoadModel("environments/Room.obj", m.room);
//  aLoader->LoadModel("environments/Fan.obj", m.fan1);
//  aLoader->LoadModel("environments/Fan.obj", m.fan2);

  vrb::Matrix transform = vrb::Matrix::Identity();
  m.room->SetTransform(transform);

  vrb::Matrix fan1Transform = vrb::Matrix::Identity();
  fan1Transform.PreMultiplyInPlace(vrb::Matrix::Position(FAN1_POSITION));
  m.fan1->SetTransform(fan1Transform);

  vrb::Matrix fan2Transform = vrb::Matrix::Identity();
  fan2Transform.PreMultiplyInPlace(vrb::Matrix::Position(FAN2_POSITION));
  m.fan2->SetTransform(fan2Transform);
}


void
Environment::Update(vrb::RenderContextPtr aContext) {
  const double ctime = aContext->GetTimestamp();

  if (m.timeStamp <= 0.0) {
    m.timeStamp = ctime;
    return;
  }

  const double delta = ctime - m.timeStamp;
  m.timeStamp = ctime;

  m.rotation += 5.0F * delta;
  if (m.rotation > MAX_ROTATION) {
    m.rotation -= MAX_ROTATION;
  }

  vrb::Matrix fan1Transform = vrb::Matrix::Identity();
  fan1Transform.PreMultiplyInPlace(vrb::Matrix::Rotation(vrb::Vector(0.0f, 1.0f, 0.0f), m.rotation));
  fan1Transform.PreMultiplyInPlace(vrb::Matrix::Position(FAN1_POSITION));
  m.fan1->SetTransform(fan1Transform);

  vrb::Matrix fan2Transform = vrb::Matrix::Identity();
  fan2Transform.PreMultiplyInPlace(vrb::Matrix::Rotation(vrb::Vector(0.0f, 1.0f, 0.0f), m.rotation));
  fan2Transform.PreMultiplyInPlace(vrb::Matrix::Position(FAN2_POSITION));
  m.fan2->SetTransform(fan2Transform);
}

vrb::NodePtr
Environment::GetRoot() const {
  return m.root;
}

EnvironmentPtr
Environment::Create(vrb::CreationContextPtr aContext) {
  return std::make_shared<vrb::ConcreteClass<Environment, Environment::State> >(aContext);
}


Environment::Environment(State& aState, vrb::CreationContextPtr& aContext) : m(aState) {
  m.context = aContext;
  m.Initialize();
}

} // namespace crow
