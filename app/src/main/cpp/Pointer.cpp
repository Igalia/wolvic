/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "Pointer.h"
#include "DeviceDelegate.h"
#include "VRLayer.h"
#include "VRLayerNode.h"
#include "VRBrowser.h"
#include "Widget.h"
#include "vrb/ConcreteClass.h"
#include "vrb/Color.h"
#include "vrb/CreationContext.h"
#include "vrb/Geometry.h"
#include "vrb/Matrix.h"
#include "vrb/ModelLoaderAndroid.h"
#include "vrb/RenderState.h"
#include "vrb/RenderContext.h"
#include "vrb/TextureCubeMap.h"
#include "vrb/Toggle.h"
#include "vrb/Transform.h"
#include "vrb/VertexArray.h"

#include <array>

#define POINTER_COLOR_OUTER vrb::Color(0.239f, 0.239f, 0.239f)
#define POINTER_COLOR_INNER vrb::Color(1.0f, 1.0f, 1.0f)

using namespace vrb;

namespace crow {

const float kOffset = 0.01f;
const int kResolution = 24;
const float kInnerRadius = 0.005f;
const float kOuterRadius = 0.0066f;

struct Pointer::State {
  vrb::CreationContextWeak context;
  vrb::TogglePtr root;
  VRLayerQuadPtr layer;
  vrb::TransformPtr transform;
  vrb::TransformPtr pointerScale;
  vrb::GeometryPtr geometry;
  WidgetPtr hitWidget;

  State()
  {}

  void Initialize() {
    vrb::CreationContextPtr create = context.lock();
    root = vrb::Toggle::Create(create);
    transform = vrb::Transform::Create(create);
    pointerScale = vrb::Transform::Create(create);
    pointerScale->SetTransform(vrb::Matrix::Identity());
    transform->AddNode(pointerScale);
    root->AddNode(transform);
    root->ToggleAll(false);
  }

  vrb::GeometryPtr createCircle(const int resolution, const float radius, const float offset) {
    vrb::CreationContextPtr create = context.lock();
    vrb::GeometryPtr geometry = vrb::Geometry::Create(create);
    vrb::VertexArrayPtr array = vrb::VertexArray::Create(create);
    geometry->SetVertexArray(array);

    array->AppendNormal(vrb::Vector(0.0f, 0.0f, 1.0f));

    for (int i = 0; i <= resolution; i++) {
      std::vector<int> normalIndex;
      normalIndex.push_back(0);
      normalIndex.push_back(0);
      normalIndex.push_back(0);

      std::vector<int> index;

      array->AppendVertex(vrb::Vector(0.0f, 0.0f, offset));
      index.push_back(i*3 + 1);

      array->AppendVertex(vrb::Vector(
          radius * cos(i * M_PI * 2 / resolution),
          radius * sin(i * M_PI * 2 / resolution),
          offset));
      index.push_back(i*3 + 2);

      array->AppendVertex(vrb::Vector(
          radius * cos((i + 1) * M_PI * 2 / resolution),
          radius * sin((i + 1) * M_PI * 2 / resolution),
          offset));
      index.push_back(i*3 + 3);

      std::vector<int> uvIndex;

      geometry->AddFace(index, uvIndex, normalIndex);
    }

    return geometry;
  }

  void LoadGeometry() {
    vrb::CreationContextPtr create = context.lock();
    geometry = createCircle(kResolution, kInnerRadius, kOffset);
    vrb::GeometryPtr geometryOuter = createCircle(kResolution, kOuterRadius, kOffset);

    vrb::RenderStatePtr state = vrb::RenderState::Create(create);
    state->SetMaterial(POINTER_COLOR_INNER, POINTER_COLOR_INNER, vrb::Color(0.0f, 0.0f, 0.0f), 0.0f);
    geometry->SetRenderState(state);
    vrb::RenderStatePtr stateOuter = vrb::RenderState::Create(create);
    stateOuter->SetMaterial(POINTER_COLOR_OUTER, POINTER_COLOR_OUTER, vrb::Color(0.0f, 0.0f, 0.0f), 0.0f);
    geometryOuter->SetRenderState(stateOuter);
    pointerScale->AddNode(geometry);
    pointerScale->AddNode(geometryOuter);
  }

};

bool
Pointer::IsLoaded() const {
  return m.layer || m.geometry;
}

void
Pointer::Load(const DeviceDelegatePtr& aDevice) {
  VRLayerQuadPtr layer = aDevice->CreateLayerQuad(36, 36, VRLayerQuad::SurfaceType::AndroidSurface);
  if (layer) {
    m.layer = layer;
    const float size = kOuterRadius *  2.0f;
    layer->SetWorldSize(size, size);
    layer->SetInitializeDelegate([](const VRLayer& aLayer) {
       const VRLayerQuad& quad = static_cast<const VRLayerQuad&>(aLayer);
       if (quad.IsInitialized()) {
         VRBrowser::RenderPointerLayer(quad.GetSurface());
       }
    });
    vrb::CreationContextPtr create = m.context.lock();
    m.pointerScale->AddNode(VRLayerNode::Create(create, layer));
  } else {
    m.LoadGeometry();
  }
}

void
Pointer::SetVisible(bool aVisible) {
  m.root->ToggleAll(aVisible);
}

void
Pointer::SetTransform(const vrb::Matrix& aTransform) {
  m.transform->SetTransform(aTransform);
  vrb::Vector point;
  point = aTransform.MultiplyPosition(point);
  const float scale = fabsf(point.z());
  m.pointerScale->SetTransform(vrb::Matrix::Identity().ScaleInPlace(vrb::Vector(scale, scale, scale)));
}

void
Pointer::SetPointerColor(const vrb::Color& aColor) {
  if (m.layer) {
    m.layer->SetTintColor(aColor);
  } if (m.geometry) {
    m.geometry->GetRenderState()->SetMaterial(aColor, aColor, vrb::Color(0.0f, 0.0f, 0.0f), 0.0f);
  }
}

void
Pointer::SetHitWidget(const crow::WidgetPtr &aWidget) {
  m.hitWidget = aWidget;
  if (m.layer) {
    m.layer->SetDrawInFront(aWidget && aWidget->IsResizing());
  }
}

vrb::NodePtr
Pointer::GetRoot() const {
  return m.root;
}

const WidgetPtr&
Pointer::GetHitWidget() const {
  return m.hitWidget;
}


PointerPtr
Pointer::Create(vrb::CreationContextPtr aContext) {
  PointerPtr result = std::make_shared<vrb::ConcreteClass<Pointer, Pointer::State> >(aContext);
  result->m.Initialize();
  return result;
}


Pointer::Pointer(State& aState, vrb::CreationContextPtr& aContext) : m(aState) {
  m.context = aContext;
}

Pointer::~Pointer() {}

} // namespace crow
