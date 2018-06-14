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

struct Widget::State {
  vrb::ContextWeak context;
  std::string name;
  uint32_t handle;
  int32_t textureWidth;
  int32_t textureHeight;
  vrb::Vector windowMin;
  vrb::Vector windowMax;
  vrb::Vector windowNormal;
  vrb::TogglePtr root;
  vrb::TransformPtr transform;
  vrb::GeometryPtr widgetGeometry;
  vrb::TextureSurfacePtr surface;
  vrb::TogglePtr pointerToggle;
  vrb::TransformPtr pointer;
  vrb::NodePtr pointerGeometry;

  State()
      : handle(0)
      , textureWidth(1920)
      , textureHeight(1080)
      , windowMin(0.0f, 0.0f, 0.0f)
      , windowMax(0.0f, 0.0f * 2.0f, 0.0f)
  {}

  void Initialize(const int aHandle) {
    handle = aHandle;
    name = "crow::Widget-" + std::to_string(handle);
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
    widgetGeometry = geometry;
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
    pointerToggle = vrb::Toggle::Create(context);
    transform->AddNode(pointerToggle);
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
    pointerGeometry = geometry;
    pointerToggle->AddNode(pointer);
  }
};

WidgetPtr
Widget::Create(vrb::ContextWeak aContext, const int aHandle, const int32_t aWidth, const int32_t aHeight, float aWorldWidth) {
  WidgetPtr result = std::make_shared<vrb::ConcreteClass<Widget, Widget::State> >(aContext);
  result->m.textureWidth = aWidth;
  result->m.textureHeight = aHeight;
  const float aspect = (float)aWidth / (float)aHeight;
  result->m.windowMin = vrb::Vector(-aWorldWidth * 0.5f, 0.0f, 0.0f);
  result->m.windowMax = vrb::Vector(aWorldWidth *0.5f, aWorldWidth/aspect, 0.0f);
  result->m.Initialize(aHandle);
  return result;
}

WidgetPtr
Widget::Create(vrb::ContextWeak aContext, const int aHandle, const int32_t aWidth, const int32_t aHeight, const vrb::Vector& aMin, const vrb::Vector& aMax) {
  WidgetPtr result = std::make_shared<vrb::ConcreteClass<Widget, Widget::State> >(aContext);
  result->m.textureWidth = aWidth;
  result->m.textureHeight = aHeight;
  result->m.windowMin = aMin;
  result->m.windowMax = aMax;
  result->m.Initialize(aHandle);
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
  aWidth = m.textureWidth;
  aHeight = m.textureHeight;
}

void
Widget::GetWidgetMinAndMax(vrb::Vector& aMin, vrb::Vector& aMax) const {
  aMin = m.windowMin;
  aMax = m.windowMax;
}

void
Widget::SetWorldWidth(float aWorldWidth) const {
  const float aspect = (float)m.textureWidth / (float)m.textureHeight;
  m.windowMin = vrb::Vector(-aWorldWidth * 0.5f, 0.0f, 0.0f);
  m.windowMax = vrb::Vector(aWorldWidth *0.5f, aWorldWidth/aspect, 0.0f);

  vrb::VertexArrayPtr array = m.widgetGeometry->GetVertexArray();
  const vrb::Vector bottomRight(m.windowMax.x(), m.windowMin.y(), m.windowMin.z());
  array->SetVertex(0, m.windowMin); // Bottom left
  array->SetVertex(1, bottomRight); // Bottom right
  array->SetVertex(2, m.windowMax); // Top right
  array->SetVertex(3, vrb::Vector(m.windowMin.x(), m.windowMax.y(), m.windowMax.z())); // Top left

  vrb::RenderStatePtr state = vrb::RenderState::Create(m.context);
  state->SetTexture(m.surface);
  state->SetMaterial(vrb::Color(0.4f, 0.4f, 0.4f), vrb::Color(1.0f, 1.0f, 1.0f), vrb::Color(0.0f, 0.0f, 0.0f),
                     0.0f);
  vrb::GeometryPtr geometry = vrb::Geometry::Create(m.context);
  geometry->SetVertexArray(array);
  geometry->SetRenderState(state);

  int n = m.widgetGeometry->GetFaceCount();
  for (int i = 0; i < n; ++i) {
    auto & face = m.widgetGeometry->GetFace(i);
    std::vector<int> vertices(face.vertices.begin(), face.vertices.end());
    std::vector<int> uvs(face.uvs.begin(), face.uvs.end());
    std::vector<int> normals(face.normals.begin(), face.normals.end());

    geometry->AddFace(vertices, uvs, normals);
  }

  m.widgetGeometry->RemoveFromParents();
  m.transform->AddNode(geometry);
  m.widgetGeometry = geometry;
}

void
Widget::GetWorldSize(float& aWidth, float& aHeight) const {
  aWidth = m.windowMax.x() - m.windowMin.x();
  aHeight = m.windowMax.y() - m.windowMin.y();
}

static const float kEpsilon = 0.00000001f;

bool
Widget::TestControllerIntersection(const vrb::Vector& aStartPoint, const vrb::Vector& aDirection, vrb::Vector& aResult, bool& aIsInWidget, float& aDistance) const {
  aDistance = -1.0f;
  if (!m.root->IsEnabled(*m.transform)) {
    return false;
  }
  vrb::Matrix modelView = m.transform->GetWorldTransform().AfineInverse();
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

  m.pointer->SetTransform(vrb::Matrix::Position(vrb::Vector(result.x(), result.y(), result.z())));

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

Widget::Widget(State& aState, vrb::ContextWeak& aContext) : m(aState) {
  m.context = aContext;
}

Widget::~Widget() {}

} // namespace crow
