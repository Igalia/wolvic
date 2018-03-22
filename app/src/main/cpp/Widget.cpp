/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "Widget.h"
#include "vrb/ConcreteClass.h"

#include "vrb/Color.h"
#include "vrb/Context.h"
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

const float kWidth = 9.0f;
const float kHeight = kWidth * 0.5625f;
static uint32_t sWidgetCount;

struct Widget::State {
  vrb::ContextWeak context;
  std::string name;
  int32_t type;
  uint32_t handle;
  int32_t textureWidth;
  int32_t textureHeight;
  vrb::Vector windowMin;
  vrb::Vector windowMax;
  vrb::Vector windowNormal;
  vrb::TogglePtr root;
  vrb::TransformPtr transform;
  vrb::TextureSurfacePtr surface;
  vrb::TogglePtr pointerToggle;
  vrb::TransformPtr pointer;
  vrb::NodePtr pointerGeometry;

  State()
      : type(0)
      , handle(0)
      , textureWidth(1920)
      , textureHeight(1080)
      , windowMin(-kWidth, 0.0f, 0.0f)
      , windowMax(kWidth, kHeight * 2.0f, 0.0f)
  {}

  void Initialize(const int32_t aType) {
    type = aType;
    handle = sWidgetCount;
    sWidgetCount++;
    name = "crow::Widget-";
    name += std::to_string(type) + "-" + std::to_string(handle);
    surface = vrb::TextureSurface::Create(context, name);
    vrb::VertexArrayPtr array = vrb::VertexArray::Create(context);
    const vrb::Vector bottomRight(windowMax.x(), windowMin.y(), windowMin.z());
    array->AppendVertex(windowMin); // Bottom left
    array->AppendVertex(bottomRight); // Bottom right
    array->AppendVertex(windowMax); // Top right
    array->AppendVertex(vrb::Vector(windowMin.x(), windowMax.y(), windowMax.z())); // Top left

    array->AppendUV(vrb::Vector(0.0f, 1.0f, 0.0f));
    array->AppendUV(vrb::Vector(1.0f, 1.0f, 0.0f));
    array->AppendUV(vrb::Vector(1.0f, 0.0f, 0.0f));
    array->AppendUV(vrb::Vector(0.0f, 0.0f, 0.0f));

    windowNormal = (bottomRight - windowMin).Cross(windowMax - windowMin).Normalize();
    array->AppendNormal(windowNormal);

    vrb::RenderStatePtr state = vrb::RenderState::Create(context);
    state->SetTexture(surface);
    state->SetMaterial(vrb::Color(0.4f, 0.4f, 0.4f), vrb::Color(1.0f, 1.0f, 1.0f), vrb::Color(0.0f, 0.0f, 0.0f),
                       0.0f);
    vrb::GeometryPtr geometry = vrb::Geometry::Create(context);
    pointerGeometry = geometry;
    geometry->SetVertexArray(array);
    geometry->SetRenderState(state);

    std::vector<int> index;
    index.push_back(1);
    index.push_back(2);
    index.push_back(3);
    index.push_back(4);
    std::vector<int> normalIndex;
    normalIndex.push_back(1);
    normalIndex.push_back(1);
    normalIndex.push_back(1);
    normalIndex.push_back(1);
    geometry->AddFace(index, index, normalIndex);

    // Draw the back for now
    index.clear();
    index.push_back(1);
    index.push_back(4);
    index.push_back(3);
    index.push_back(2);

    array->AppendNormal(-windowNormal);
    normalIndex.clear();
    normalIndex.push_back(2);
    normalIndex.push_back(2);
    normalIndex.push_back(2);
    normalIndex.push_back(2);
    geometry->AddFace(index, index, normalIndex);

    transform = vrb::Transform::Create(context);
    transform->AddNode(geometry);
    root = vrb::Toggle::Create(context);
    root->AddNode(transform);
    const float kOffset = 0.1f;
    array = vrb::VertexArray::Create(context);
    array->AppendVertex(vrb::Vector(0.1f, -0.2f, kOffset));
    array->AppendVertex(vrb::Vector(0.2f, -0.1f, kOffset));
    array->AppendVertex(vrb::Vector(0.0f, 0.0f, kOffset));
    array->AppendNormal(vrb::Vector(0.0f, 0.0f, 1.0f));
    index.clear();
    index.push_back(1);
    index.push_back(2);
    index.push_back(3);
    normalIndex.clear();
    normalIndex.push_back(1);
    normalIndex.push_back(1);
    normalIndex.push_back(1);
    std::vector<int> uvIndex;
    geometry = vrb::Geometry::Create(context);
    geometry->SetVertexArray(array);
    geometry->AddFace(index, uvIndex, normalIndex);
    state = vrb::RenderState::Create(context);
    state->SetMaterial(vrb::Color(1.0f, 0.0f, 0.0f), vrb::Color(1.0f, 0.0f, 0.0f), vrb::Color(0.0f, 0.0f, 0.0f),
                       0.0f);
    geometry->SetRenderState(state);
    pointer = vrb::Transform::Create(context);
    pointer->AddNode(geometry);
    pointerToggle = vrb::Toggle::Create(context);
    pointerToggle->AddNode(pointer);
    transform->AddNode(pointerToggle);
  }
};

WidgetPtr
Widget::Create(vrb::ContextWeak aContext, const int32_t aType) {
  WidgetPtr result = std::make_shared<vrb::ConcreteClass<Widget, Widget::State> >(aContext);
  result->m.Initialize(aType);
  return result;
}

WidgetPtr
Widget::Create(vrb::ContextWeak aContext, const int aType, const int32_t aWidth, const int32_t aHeight, float aWorldWidth) {
  WidgetPtr result = std::make_shared<vrb::ConcreteClass<Widget, Widget::State> >(aContext);
  result->m.textureWidth = aWidth;
  result->m.textureHeight = aHeight;
  const float aspect = (float)aWidth / (float)aHeight;
  result->m.windowMin = vrb::Vector(-aWorldWidth, 0.0f, 0.0f);
  result->m.windowMax = vrb::Vector(aWorldWidth, aWorldWidth/aspect * 2.0f, 0.0f);
  result->m.Initialize(aType);
  return result;
}

WidgetPtr
Widget::Create(vrb::ContextWeak aContext, const int aType, const int32_t aWidth, const int32_t aHeight, const vrb::Vector& aMin, const vrb::Vector& aMax) {
  WidgetPtr result = std::make_shared<vrb::ConcreteClass<Widget, Widget::State> >(aContext);
  result->m.textureWidth = aWidth;
  result->m.textureHeight = aHeight;
  result->m.windowMin = aMin;
  result->m.windowMax = aMax;
  result->m.Initialize(aType);
  return result;
}

int32_t
Widget::GetType() const {
  return m.type;
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
  aWidth = m.textureWidth;
  aHeight = m.textureHeight;
}

void
Widget::GetWidgetMinAndMax(vrb::Vector& aMin, vrb::Vector& aMax) const {
  aMin = m.windowMin;
  aMax = m.windowMax;
}

static const float kEpsilon = 0.00000001f;

bool
Widget::TestControllerIntersection(const vrb::Vector& aStartPoint, const vrb::Vector& aDirection, vrb::Vector& aResult, bool& aIsInWidget, float& aDistance) const {
  aDistance = -1.0f;
  if (!m.root->IsEnabled(*m.transform)) {
    return false;
  }
  vrb::Matrix modelView = m.transform->GetTransform().AfineInverse();
  vrb::Vector point = modelView.MultiplyPosition(aStartPoint);
  vrb::Vector direction = modelView.MultiplyDirection(aDirection);
  const float dotNormals = direction.Dot(m.windowNormal);
  if (dotNormals > -kEpsilon) {
    // Not pointed at the plane
    return false;
  }

  const float dotV = (m.windowMin - point).Dot(m.windowNormal);

  if ((dotV < kEpsilon) && (dotV > -kEpsilon)) {
    return false;
  }

  const float length = dotV / dotNormals;
  vrb::Vector result = point + (direction * length);

  if ((result.x() >= m.windowMin.x()) && (result.y() >= m.windowMin.y()) &&(result.z() >= (m.windowMin.z() - 0.1f)) &&
      (result.x() <= m.windowMax.x()) && (result.y() <= m.windowMax.y()) &&(result.z() <= (m.windowMax.z() + 0.1f))) {
    aIsInWidget = true;
  }

  aResult = result;

  aDistance = (aResult - point).Magnitude();

  // Clamp to keep pointer in window.
  if (result.x() > m.windowMax.x()) { result.x() = m.windowMax.x(); }
  else if (result.x() < m.windowMin.x()) { result.x() = m.windowMin.x(); }

  if (result.y() > m.windowMax.y()) { result.y() = m.windowMax.y(); }
  else if (result.y() < m.windowMin.y()) { result.y() = m.windowMin.y(); }

  m.pointer->SetTransform(vrb::Matrix::Position(result));

  return true;
}

void
Widget::ConvertToWidgetCoordinates(const vrb::Vector& point, float& aX, float& aY) const {
  vrb::Vector value = point;
  // Clamp value to window bounds.
  if (value.x() > m.windowMax.x()) { value.x() = m.windowMax.x(); }
  else if (value.x() < m.windowMin.x()) { value.x() = m.windowMin.x(); }
  // Convert to window coordinates.
  if (value.y() > m.windowMax.y()) { value.y() = m.windowMax.y(); }
  else if (value.y() < m.windowMin.y()) { value.y() = m.windowMin.y(); }
  aX = (((value.x() - m.windowMin.x()) / (m.windowMax.x() - m.windowMin.x())) * (float)m.textureWidth);
  aY = (((m.windowMax.y() - value.y()) / (m.windowMax.y() - m.windowMin.y())) * (float)m.textureHeight);
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

vrb::NodePtr
Widget::GetRoot() {
  return m.root;
}

vrb::NodePtr
Widget::GetPointerGeometry() {
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

Widget::Widget(State& aState, vrb::ContextWeak& aContext) : m(aState) {
  m.context = aContext;
}

Widget::~Widget() {}

} // namespace crow
