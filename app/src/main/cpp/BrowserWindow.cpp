/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "BrowserWindow.h"
#include "vrb/ConcreteClass.h"

#include "vrb/Color.h"
#include "vrb/Context.h"
#include "vrb/Matrix.h"
#include "vrb/Geometry.h"
#include "vrb/RenderState.h"
#include "vrb/SurfaceTextureFactory.h"
#include "vrb/TextureSurface.h"
#include "vrb/Transform.h"
#include "vrb/Vector.h"
#include "vrb/VertexArray.h"

namespace crow {

const float kWidth = 9.0f;
const float kHeight = kWidth * 0.5625f;
static uint32_t sWindowCount;

struct BrowserWindow::State {
  vrb::ContextWeak context;
  std::string name;
  int32_t textureWidth;
  int32_t textureHeight;
  vrb::Vector windowMin;
  vrb::Vector windowMax;
  vrb::Vector windowNormal;
  vrb::TransformPtr root;
  vrb::TextureSurfacePtr surface;
  vrb::TransformPtr pointer;

  State()
      : textureWidth(1920)
      , textureHeight(1080)
      , windowMin(-kWidth, 0.0f, 0.0f)
      , windowMax(kWidth, kHeight * 2.0f, 0.0f)
  {}

  void Initialize() {
    name = "crow::BrowserWindow-";
    name += std::to_string(sWindowCount);
    sWindowCount++;
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

    root = vrb::Transform::Create(context);
    root->AddNode(geometry);
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
    root->AddNode(pointer);
  }
};

BrowserWindowPtr
BrowserWindow::Create(vrb::ContextWeak aContext) {
  return std::make_shared<vrb::ConcreteClass<BrowserWindow, BrowserWindow::State> >(aContext);
}

const std::string&
BrowserWindow::GetSurfaceTextureName() const {
  return m.name;
}

void
BrowserWindow::GetSurfaceTextureSize(int32_t& aWidth, int32_t& aHeight) const {
  aWidth = m.textureWidth;
  aHeight = m.textureHeight;
}

void
BrowserWindow::GetWindowMinAndMax(vrb::Vector& aMin, vrb::Vector& aMax) const {
  aMin = m.windowMin;
  aMax = m.windowMax;
}

static const float kEpsilon = 0.00000001f;

bool
BrowserWindow::TestControllerIntersection(const vrb::Matrix& aController, vrb::Vector& aResult) const {
  vrb::Vector point;
  vrb::Vector direction(0.0f, 0.0f, -1.0f); // forward;
  point = aController.MultiplyPosition(point);
  direction = aController.MultiplyDirection(direction);
  vrb::Matrix modelView = m.root->GetTransform().Inverse();
  point = modelView.MultiplyPosition(point);
  direction = modelView.MultiplyDirection(direction);
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

  if ((result.x() >= m.windowMin.x()) && (result.y() >= m.windowMin.y()) &&(result.z() >= m.windowMin.z()) &&
      (result.x() <= m.windowMax.x()) && (result.y() <= m.windowMax.y()) &&(result.z() <= m.windowMax.z())) {
    aResult = result;
    m.pointer->SetTransform(vrb::Matrix::Position(result));
    return true;
  }

  return false;
}

const vrb::Matrix
BrowserWindow::GetTransform() const {
  return m.root->GetTransform();
}

void
BrowserWindow::SetTransform(const vrb::Matrix& aTransform) {
  m.root->SetTransform(aTransform);
}

vrb::NodePtr
BrowserWindow::GetRoot() {
  return m.root;
}

BrowserWindow::BrowserWindow(State& aState, vrb::ContextWeak& aContext) : m(aState) {
  m.context = aContext;
  m.Initialize();
}

BrowserWindow::~BrowserWindow() {}

} // namespace crow