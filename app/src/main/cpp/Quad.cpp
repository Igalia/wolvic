/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "Quad.h"
#include "vrb/ConcreteClass.h"

#include "vrb/Color.h"
#include "vrb/CreationContext.h"
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

struct Quad::State {
  vrb::CreationContextWeak context;
  int32_t textureWidth;
  int32_t textureHeight;
  vrb::TogglePtr root;
  vrb::TransformPtr transform;
  vrb::GeometryPtr geometry;
  Quad::ScaleMode scaleMode;
  vrb::Vector worldMin;
  vrb::Vector worldMax;
  vrb::TransformPtr backgroundTransform;
  vrb::GeometryPtr backgroundGeometry;
  vrb::Color backgroundColor;

  State()
      : textureWidth(0)
      , textureHeight(0)
      , scaleMode(ScaleMode::Fill)
      , worldMin(0.0f, 0.0f, 0.0f)
      , worldMax(0.0f, 0.0f, 0.0f)
  {}

  void Initialize() {
    vrb::CreationContextPtr create = context.lock();
    geometry = Quad::CreateGeometry(create, worldMin, worldMax);
    transform = vrb::Transform::Create(create);
    transform->AddNode(geometry);
    root = vrb::Toggle::Create(create);
    root->AddNode(transform);
    if (scaleMode != ScaleMode::Fill) {
      UpdateVertexArray();
    }
  }

  float GetWorldWidth() const {
    return worldMax.x() - worldMin.x();
  }

  float GetWorldHeight() const {
    return worldMax.y() - worldMin.y();
  }

  void UpdateVertexArray() {
    if (textureWidth == 0|| textureHeight == 0) {
      return;
    }
    vrb::VertexArrayPtr array = geometry->GetVertexArray();

    vrb::Vector min = worldMin;
    vrb::Vector max = worldMax;
    const float worldWidth = GetWorldWidth();
    const float worldHeight = GetWorldHeight();
    const float textureAspect = (float) textureWidth / (float) textureHeight;
    const float worldAspect = worldWidth / worldHeight;


    if (scaleMode == ScaleMode::AspectFit) {
      const float newWidth = worldAspect > textureAspect ? worldHeight * textureAspect : worldWidth;
      const float newHeight = worldAspect > textureAspect ? worldHeight : worldWidth / textureAspect;
      min.x() = min.x() + (worldWidth - newWidth) * 0.5f;
      min.y() = min.y() + (worldHeight - newHeight) * 0.5f;
      max.x() = min.x() + newWidth;
      max.y() = min.y() + newHeight;
    } else if (scaleMode == ScaleMode::AspectFill) {
      float u0 = 0.0f;
      float v0 = 0.0f;
      float ul = 1.0f;
      float vl = 1.0f;
      if (worldAspect > textureAspect) {
        vl = textureAspect / worldAspect;
        v0 = 0.5f - vl * 0.5f;
      }
      else if (worldAspect < textureAspect) {
        ul = worldAspect / textureAspect;
        u0 = 0.5f - ul;
      }

      array->SetUV(0, vrb::Vector(u0, v0 + vl, 0.0f));
      array->SetUV(1, vrb::Vector(u0 + ul, v0 + vl, 0.0f));
      array->SetUV(2, vrb::Vector(u0 + ul, v0, 0.0f));
      array->SetUV(3, vrb::Vector(u0, v0, 0.0f));
    }

    const vrb::Vector bottomRight(max.x(), min.y(), min.z());
    array->SetVertex(0, min); // Bottom left
    array->SetVertex(1, bottomRight); // Bottom right
    array->SetVertex(2, max); // Top right
    array->SetVertex(3, vrb::Vector(min.x(), max.y(), max.z())); // Top left

    geometry->UpdateBuffers();
  }

  void LayoutBackground() {
    if (!backgroundTransform) {
      return;
    }

    const float width = GetWorldWidth();
    const float height = GetWorldHeight();
    vrb::Matrix matrix = vrb::Matrix::Position(vrb::Vector(worldMin.x() + width * 0.5f, worldMin.y() + height * 0.5f, -0.005f));
    matrix.ScaleInPlace(vrb::Vector(width, height, 1.0f));
    backgroundTransform->SetTransform(matrix);
  }
};

QuadPtr
Quad::Create(vrb::CreationContextPtr aContext, const vrb::Vector& aMin, const vrb::Vector& aMax) {
  QuadPtr result = std::make_shared<vrb::ConcreteClass<Quad, Quad::State> >(aContext);
  result->m.worldMin = aMin;
  result->m.worldMax = aMax;
  result->m.Initialize();
  return result;
}

QuadPtr
Quad::Create(vrb::CreationContextPtr aContext, const float aWorldWidth, const float aWorldHeight) {
  QuadPtr result = std::make_shared<vrb::ConcreteClass<Quad, Quad::State> >(aContext);
  result->m.worldMin = vrb::Vector(-aWorldWidth * 0.5f, -aWorldHeight * 0.5f, 0.0f);
  result->m.worldMax = vrb::Vector(aWorldWidth * 0.5f, aWorldHeight * 0.5f, 0.0f);
  result->m.Initialize();
  return result;
}

vrb::GeometryPtr
Quad::CreateGeometry(vrb::CreationContextPtr aContext, const vrb::Vector &aMin, const vrb::Vector &aMax) {
  vrb::VertexArrayPtr array = vrb::VertexArray::Create(aContext);
  const vrb::Vector bottomRight(aMax.x(), aMin.y(), aMin.z());
  array->AppendVertex(aMin); // Bottom left
  array->AppendVertex(bottomRight); // Bottom right
  array->AppendVertex(aMax); // Top right
  array->AppendVertex(vrb::Vector(aMin.x(), aMax.y(), aMax.z())); // Top left

  array->AppendUV(vrb::Vector(0.0f, 1.0f, 0.0f));
  array->AppendUV(vrb::Vector(1.0f, 1.0f, 0.0f));
  array->AppendUV(vrb::Vector(1.0f, 0.0f, 0.0f));
  array->AppendUV(vrb::Vector(0.0f, 0.0f, 0.0f));

  vrb::Vector normal = (bottomRight - aMin).Cross(aMax - aMin).Normalize();
  array->AppendNormal(normal);

  vrb::RenderStatePtr state = vrb::RenderState::Create(aContext);
  vrb::GeometryPtr geometry = vrb::Geometry::Create(aContext);
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

  array->AppendNormal(-normal);
  normalIndex.clear();
  normalIndex.push_back(2);
  normalIndex.push_back(2);
  normalIndex.push_back(2);
  normalIndex.push_back(2);
  geometry->AddFace(index, index, normalIndex);

  return geometry;
}

vrb::GeometryPtr
Quad::CreateGeometry(vrb::CreationContextPtr aContext, const float aWorldWidth, const float aWorldHeight) {
  vrb::Vector max(aWorldWidth * 0.5f, aWorldHeight * 0.5f, 0.0f);
  return Quad::CreateGeometry(aContext, -max, max);
}

void
Quad::SetTexture(const vrb::TexturePtr& aTexture, int32_t aWidth, int32_t aHeight) {
  m.textureWidth = aWidth;
  m.textureHeight = aHeight;
  m.geometry->GetRenderState()->SetTexture(aTexture);
  if (m.scaleMode != ScaleMode::Fill) {
    m.UpdateVertexArray();
  }
}

void
Quad::SetMaterial(const vrb::Color& aAmbient, const vrb::Color& aDiffuse, const vrb::Color& aSpecular, const float aSpecularExponent) {
  m.geometry->GetRenderState()->SetMaterial(aAmbient, aDiffuse, aSpecular, aSpecularExponent);
}

void
Quad::SetScaleMode(ScaleMode aScaleMode) {
  if (m.scaleMode == aScaleMode) {
    return;
  }
  m.scaleMode = aScaleMode;
  m.UpdateVertexArray();
}

void
Quad::SetBackgroundColor(const vrb::Color& aColor) {
  if (m.backgroundColor == aColor || (aColor.Alpha() == 0.0f && m.backgroundColor.Alpha() == 0.0f)) {
    return;
  }
  m.backgroundColor = aColor;
  if (!m.backgroundGeometry) {
    vrb::CreationContextPtr create = m.context.lock();
    m.backgroundGeometry = Quad::CreateGeometry(create, 1.0f, 1.0f);
    m.backgroundTransform = vrb::Transform::Create(create);
    m.backgroundTransform->AddNode(m.backgroundGeometry);
    m.root->AddNode(m.backgroundTransform);
  }

  m.backgroundGeometry->GetRenderState()->SetDiffuse(aColor);
  m.LayoutBackground();
  m.root->ToggleChild(*m.backgroundTransform, aColor.Alpha() > 0.0f);
}

void
Quad::GetTextureSize(int32_t& aWidth, int32_t& aHeight) const {
  if (aWidth == m.textureWidth && aHeight == m.textureHeight) {
    return;
  }
  aWidth = m.textureWidth;
  aHeight = m.textureHeight;
  m.UpdateVertexArray();
}

void
Quad::SetTextureSize(int32_t aWidth, int32_t aHeight) {
  m.textureWidth = aWidth;
  m.textureHeight = aHeight;
}

void
Quad::GetWorldMinAndMax(vrb::Vector& aMin, vrb::Vector& aMax) const {
  aMin = m.worldMin;
  aMax = m.worldMax;
}

const vrb::Vector&
Quad::GetWorldMin() const {
  return m.worldMin;
}

const vrb::Vector&
Quad::GetWorldMax() const {
  return m.worldMax;
}

float
Quad::GetWorldWidth() const {
  return  m.GetWorldWidth();
}

float
Quad::GetWorldHeight() const {
  return m.GetWorldHeight();
}

void
Quad::GetWorldSize(float& aWidth, float& aHeight) const {
  aWidth = m.worldMax.x() - m.worldMin.x();
  aHeight = m.worldMax.y() - m.worldMin.y();
}

void
Quad::SetWorldSize(const float aWidth, const float aHeight) const {
  vrb::Vector min = vrb::Vector(-aWidth * 0.5f, -aHeight * 0.5f, 0.0f);
  vrb::Vector max = vrb::Vector(aWidth * 0.5f, aHeight * 0.5f, 0.0f);
  SetWorldSize(min, max);
}

void
Quad::SetWorldSize(const vrb::Vector& aMin, const vrb::Vector& aMax) const {
  if (m.worldMin == aMin && m.worldMax == aMax) {
    return;
  }
  m.worldMin = aMin;
  m.worldMax = aMax;

  m.UpdateVertexArray();
  m.LayoutBackground();
}

vrb::Vector
Quad::GetNormal() const {
  return m.geometry->GetVertexArray()->GetNormal(0);
}

vrb::NodePtr
Quad::GetRoot() const {
  return m.root;
}

vrb::TransformPtr
Quad::GetTransformNode() const {
  return m.transform;
}

static const float kEpsilon = 0.00000001f;

bool
Quad::TestIntersection(const vrb::Vector& aStartPoint, const vrb::Vector& aDirection, vrb::Vector& aResult, bool aClamp, bool& aIsInside, float& aDistance) const {
  aDistance = -1.0f;
  if (!m.root->IsEnabled(*m.transform)) {
    return false;
  }
  vrb::Matrix modelView = m.transform->GetWorldTransform().AfineInverse();
  vrb::Vector point = modelView.MultiplyPosition(aStartPoint);
  vrb::Vector direction = modelView.MultiplyDirection(aDirection);
  vrb::Vector normal = GetNormal();
  const float dotNormals = direction.Dot(normal);
  if (dotNormals > -kEpsilon) {
    // Not pointed at the plane
    return false;
  }

  const float dotV = (m.worldMin - point).Dot(normal);

  if ((dotV < kEpsilon) && (dotV > -kEpsilon)) {
    return false;
  }

  const float length = dotV / dotNormals;
  vrb::Vector result = point + (direction * length);

  if ((result.x() >= m.worldMin.x()) && (result.y() >= m.worldMin.y()) &&(result.z() >= (m.worldMin.z() - 0.1f)) &&
      (result.x() <= m.worldMax.x()) && (result.y() <= m.worldMax.y()) &&(result.z() <= (m.worldMax.z() + 0.1f))) {

    aIsInside = true;
  }

  aResult = result;

  aDistance = (aResult - point).Magnitude();

  // Clamp to keep pointer in quad.
  if (aClamp) {
    if (result.x() > m.worldMax.x()) { result.x() = m.worldMax.x(); }
    else if (result.x() < m.worldMin.x()) { result.x() = m.worldMin.x(); }

    if (result.y() > m.worldMax.y()) { result.y() = m.worldMax.y(); }
    else if (result.y() < m.worldMin.y()) { result.y() = m.worldMin.y(); }
  }

  return true;
}

void
Quad::ConvertToQuadCoordinates(const vrb::Vector& point, float& aX, float& aY, bool aClamp) const {
  vrb::Vector value = point;
  // Clamp value to quad bounds.
  if (aClamp) {
    if (value.x() > m.worldMax.x()) { value.x() = m.worldMax.x(); }
    else if (value.x() < m.worldMin.x()) { value.x() = m.worldMin.x(); }
    if (value.y() > m.worldMax.y()) { value.y() = m.worldMax.y(); }
    else if (value.y() < m.worldMin.y()) { value.y() = m.worldMin.y(); }
  }

  // Convert to quad coordinates.
  aX = (((value.x() - m.worldMin.x()) / (m.worldMax.x() - m.worldMin.x())) * (float)m.textureWidth);
  aY = (((m.worldMax.y() - value.y()) / (m.worldMax.y() - m.worldMin.y())) * (float)m.textureHeight);
}

Quad::Quad(State& aState, vrb::CreationContextPtr& aContext) : m(aState) {
  m.context = aContext;
}

Quad::~Quad() {}

} // namespace crow
