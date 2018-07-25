/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "Widget.h"
#include "Quad.h"
#include "WidgetPlacement.h"
#include "WidgetResizer.h"
#include "vrb/ConcreteClass.h"

#include "vrb/Color.h"
#include "vrb/RenderContext.h"
#include "vrb/Matrix.h"
#include "vrb/Geometry.h"
#include "vrb/RenderState.h"
#include "vrb/SurfaceTextureFactory.h"
#include "vrb/TextureSurface.h"
#include "vrb/Toggle.h"
#include "vrb/Transform.h"
#include "vrb/Vector.h"
#include "vrb/VertexArray.h"

namespace crow {

struct Widget::State {
  vrb::RenderContextWeak context;
  std::string name;
  uint32_t handle;
  QuadPtr quad;
  vrb::TogglePtr root;
  vrb::TransformPtr transform;
  vrb::TextureSurfacePtr surface;
  vrb::TogglePtr pointerToggle;
  vrb::TransformPtr pointer;
  vrb::TransformPtr pointerScale;
  vrb::NodePtr pointerGeometry;
  WidgetPlacementPtr placement;
  WidgetResizerPtr resizer;
  bool resizing;

  State()
      : handle(0)
      , resizing(false)
  {}

  void Initialize(const int aHandle, const vrb::Vector& aWindowMin, const vrb::Vector& aWindowMax, const int32_t aTextureWidth, const int32_t aTextureHeight) {
    handle = aHandle;
    name = "crow::Widget-" + std::to_string(handle);
    vrb::RenderContextPtr render = context.lock();
    if (!render) {
      return;
    }
    surface = vrb::TextureSurface::Create(render, name);
    vrb::CreationContextPtr create = render->GetRenderThreadCreationContext();
    quad = Quad::Create(create, aWindowMin, aWindowMax);
    quad->SetTexture(surface, aTextureWidth, aTextureHeight);
    quad->SetMaterial(vrb::Color(0.4f, 0.4f, 0.4f), vrb::Color(1.0f, 1.0f, 1.0f), vrb::Color(0.0f, 0.0f, 0.0f), 0.0f);

    transform = vrb::Transform::Create(create);
    pointerToggle = vrb::Toggle::Create(create);
    transform->AddNode(pointerToggle);
    transform->AddNode(quad->GetRoot());
    root = vrb::Toggle::Create(create);
    root->AddNode(transform);

    const float kOffset = 0.01f;
    const float scale = 0.02f;
    vrb::VertexArrayPtr array = vrb::VertexArray::Create(create);
    array = vrb::VertexArray::Create(create);
    array->AppendVertex(vrb::Vector(0.5f * scale, -1.0f * scale, kOffset));
    array->AppendVertex(vrb::Vector(1.0f * scale, -0.5f * scale, kOffset));
    array->AppendVertex(vrb::Vector(0.0f, 0.0f, kOffset));
    array->AppendNormal(vrb::Vector(0.0f, 0.0f, 1.0f));
    std::vector<int> index;
    index.push_back(1);
    index.push_back(2);
    index.push_back(3);
    std::vector<int> normalIndex;
    normalIndex.push_back(1);
    normalIndex.push_back(1);
    normalIndex.push_back(1);
    std::vector<int> uvIndex;
    vrb::GeometryPtr geometry = vrb::Geometry::Create(create);
    geometry->SetVertexArray(array);
    geometry->AddFace(index, uvIndex, normalIndex);
    vrb::RenderStatePtr state = vrb::RenderState::Create(create);
    state->SetMaterial(vrb::Color(1.0f, 0.0f, 0.0f), vrb::Color(1.0f, 0.0f, 0.0f), vrb::Color(0.0f, 0.0f, 0.0f),
                       0.0f);
    geometry->SetRenderState(state);
    pointerScale = vrb::Transform::Create(create);
    pointerScale->SetTransform(vrb::Matrix::Identity());
    pointerScale->AddNode(geometry);
    pointer = vrb::Transform::Create(create);
    pointer->AddNode(pointerScale);
    pointerGeometry = geometry;
    pointerToggle->AddNode(pointer);
  }

};

WidgetPtr
Widget::Create(vrb::RenderContextPtr& aContext, const int aHandle, const int32_t aWidth, const int32_t aHeight, float aWorldWidth) {
  WidgetPtr result = std::make_shared<vrb::ConcreteClass<Widget, Widget::State> >(aContext);
  const float aspect = (float)aWidth / (float)aHeight;
  const float worldHeight = aWorldWidth / aspect;
  vrb::Vector windowMin(-aWorldWidth * 0.5f, -worldHeight * 0.5f, 0.0f);
  vrb::Vector windowMax(aWorldWidth *0.5f, worldHeight * 0.5f, 0.0f);
  result->m.Initialize(aHandle, windowMin, windowMax, aWidth, aHeight);
  return result;
}

WidgetPtr
Widget::Create(vrb::RenderContextPtr& aContext, const int aHandle, const int32_t aWidth, const int32_t aHeight, const vrb::Vector& aMin, const vrb::Vector& aMax) {
  WidgetPtr result = std::make_shared<vrb::ConcreteClass<Widget, Widget::State> >(aContext);
  result->m.Initialize(aHandle, aMin, aMax, aWidth, aHeight);
  return result;
}

uint32_t
Widget::GetHandle() const {
  return m.handle;
}

const std::string&
Widget::GetSurfaceTextureName() const {
  return m.name;
}

void
Widget::GetSurfaceTextureSize(int32_t& aWidth, int32_t& aHeight) const {
  m.quad->GetTextureSize(aWidth, aHeight);
}

void
Widget::SetSurfaceTextureSize(int32_t aWidth, int32_t aHeight) {
  m.quad->SetTextureSize(aWidth, aHeight);
}

void
Widget::GetWidgetMinAndMax(vrb::Vector& aMin, vrb::Vector& aMax) const {
  m.quad->GetWorldMinAndMax(aMin, aMax);
}

void
Widget::SetWorldWidth(float aWorldWidth) const {
  int32_t width, height;
  m.quad->GetTextureSize(width, height);
  const float aspect = (float)width / (float) height;
  const float worldHeight = aWorldWidth / aspect;
  m.quad->SetWorldSize(aWorldWidth, worldHeight);
  if (m.resizing && m.resizer) {
    vrb::Vector min(-aWorldWidth * 0.5f, -worldHeight * 0.5f, 0.0f);
    vrb::Vector max(aWorldWidth *0.5f, worldHeight * 0.5f, 0.0f);
    m.resizer->SetSize(min, max);
  }
}

void
Widget::GetWorldSize(float& aWidth, float& aHeight) const {
  m.quad->GetWorldSize(aWidth, aHeight);
}

bool
Widget::TestControllerIntersection(const vrb::Vector& aStartPoint, const vrb::Vector& aDirection, vrb::Vector& aResult, bool& aIsInWidget, float& aDistance) const {
  aDistance = -1.0f;
  if (!m.root->IsEnabled(*m.transform)) {
    return false;
  }

  bool clamp = !m.resizing;
  bool result = m.quad->TestIntersection(aStartPoint, aDirection, aResult, clamp, aIsInWidget, aDistance);
  if (result && m.resizing && !aIsInWidget) {
    // Handle extra intersections while resizing
    aIsInWidget = m.resizer->TestIntersection(aResult);
  }

  if (result && m.pointer) {
    m.pointer->SetTransform(vrb::Matrix::Translation(vrb::Vector(aResult.x(), aResult.y(), 0.0f)));
  }

  return result;
}

void
Widget::ConvertToWidgetCoordinates(const vrb::Vector& point, float& aX, float& aY) const {
  bool clamp = !m.resizing;
  m.quad->ConvertToQuadCoordinates(point, aX, aY, clamp);
}

void
Widget::ConvertToWorldCoordinates(const vrb::Vector& aPoint, vrb::Vector& aResult) const {
  aResult = m.transform->GetTransform().MultiplyPosition(aPoint);
}

const vrb::Matrix
Widget::GetTransform() const {
  return m.transform->GetTransform();
}

void
Widget::SetTransform(const vrb::Matrix& aTransform) {
  vrb::Vector point;
  point = aTransform.MultiplyPosition(point);
  const float scale = fabsf(point.z());
  m.pointerScale->SetTransform(vrb::Matrix::Identity().ScaleInPlace(vrb::Vector(scale, scale, scale)));
  m.transform->SetTransform(aTransform);
}

void
Widget::ToggleWidget(const bool aEnabled) {
  m.root->ToggleAll(aEnabled);
}

void
Widget::TogglePointer(const bool aEnabled) {
  m.pointerToggle->ToggleAll(aEnabled);
}

bool
Widget::IsVisible() const {
  return m.root->IsEnabled(*m.transform);
}


vrb::NodePtr
Widget::GetRoot() const {
  return m.root;
}

vrb::TransformPtr
Widget::GetTransformNode() const {
  return m.transform;
}

vrb::NodePtr
Widget::GetPointerGeometry() const {
  return m.pointerGeometry;
}

void
Widget::SetPointerGeometry(vrb::NodePtr& aNode) {
  if (!aNode) {
    return;
  }
  if (m.pointerGeometry) {
    m.pointer->RemoveNode(*m.pointerGeometry);
  }
  m.pointerGeometry = aNode;
  m.pointer->AddNode(aNode);
}

const WidgetPlacementPtr&
Widget::GetPlacement() const {
  return m.placement;
}

void
Widget::SetPlacement(const WidgetPlacementPtr& aPlacement) {
  m.placement = aPlacement;
}

void
Widget::StartResize() {
  if (m.resizer) {
    m.resizer->SetSize(m.quad->GetWorldMin(), m.quad->GetWorldMax());
  } else {
    vrb::RenderContextPtr render = m.context.lock();
    if (!render) {
      return;
    }
    vrb::CreationContextPtr create = render->GetRenderThreadCreationContext();
    m.resizer = WidgetResizer::Create(create, m.quad->GetWorldMin(), m.quad->GetWorldMax());
    m.transform->InsertNode(m.resizer->GetRoot(), 1);
  }
  m.resizing = true;
  m.resizer->ToggleVisible(true);
  m.quad->SetScaleMode(Quad::ScaleMode::AspectFit);
  m.quad->SetBackgroundColor(vrb::Color(1.0f, 1.0f, 1.0f, 1.0f));
}

void
Widget::FinishResize() {
  if (!m.resizing) {
    return;
  }
  m.resizing = false;
  m.resizer->ToggleVisible(false);
  m.quad->SetScaleMode(Quad::ScaleMode::Fill);
  m.quad->SetBackgroundColor(vrb::Color(0.0f, 0.0f, 0.0f, 0.0f));
}

bool
Widget::IsResizing() const {
  return m.resizing;
}

void
Widget::HandleResize(const vrb::Vector& aPoint, bool aPressed, bool& aResized, bool &aResizeEnded) {
  m.resizer->HandleResizeGestures(aPoint, aPressed, aResized, aResizeEnded);
  if (aResized || aResizeEnded) {
    m.quad->SetWorldSize(m.resizer->GetCurrentMin(), m.resizer->GetCurrentMax());
  }
}

void
Widget::HoverExitResize() {
  if (m.resizing) {
    m.resizer->HoverExitResize();
  }
}


Widget::Widget(State& aState, vrb::RenderContextPtr& aContext) : m(aState) {
  m.context = aContext;
}

Widget::~Widget() {}

} // namespace crow
