/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "Cylinder.h"
#include "Quad.h"
#include "VRLayer.h"
#include "VRLayerNode.h"
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

struct Cylinder::State {
  vrb::CreationContextWeak context;
  VRLayerCylinderPtr layer;
  VRLayerNodePtr layerNode;
  int32_t textureWidth;
  int32_t textureHeight;
  vrb::TogglePtr root;
  vrb::TransformPtr transform;
  vrb::GeometryPtr geometry;
  float radius;
  float height;
  float theta;
  float textureScaleX;
  float textureScaleY;

  State()
      : textureWidth(0)
      , textureHeight(0)
      , radius(1.0f)
      , height(2.0f)
      , theta((float)M_PI)
      , textureScaleX(1.0f)
      , textureScaleY(1.0f)
  {}

  void Initialize() {
    vrb::CreationContextPtr create = context.lock();
    transform = vrb::Transform::Create(create);
    if (layer) {
      textureWidth = layer->GetWidth();
      textureHeight = layer->GetHeight();
      layer->SetRadius(radius);
      layerNode = VRLayerNode::Create(create, layer);
      transform->AddNode(layerNode);
    } else {
      geometry = CreateCylinderGeometry(radius, height, (float) M_PI);
      transform->AddNode(geometry);
    }
    root = vrb::Toggle::Create(create);
    root->AddNode(transform);
  }

  const int kRadialSegments = 200;
  const int kHeightSegments = 1;

  vrb::GeometryPtr CreateCylinderGeometry(const float aRadius, const float aHeight, const float aArcLength) {
    const float pi = (float) M_PI;

    const float startAngle = pi * 0.5f + aArcLength * 0.5f;

    vrb::CreationContextPtr create = context.lock();
    vrb::GeometryPtr geometry = vrb::Geometry::Create(create);
    vrb::VertexArrayPtr array = vrb::VertexArray::Create(create);

    for (int y = 0; y <= kHeightSegments; ++y) {
      const float v = (float) y / (float) kHeightSegments;
      for (int x = 0; x <= kRadialSegments; ++x) {
        const float u = (float) x / (float) kRadialSegments;

        const float theta = startAngle - aArcLength * u;

        const float sinTheta = sinf(theta);
        const float cosTheta = cosf(theta);
        vrb::Vector vertex;
        vrb::Vector uv;
        vrb::Vector normal;

        vertex.x() = aRadius * cosTheta;
        vertex.y() = -v * aHeight + aHeight * 0.5f;
        vertex.z() = -aRadius * sinTheta;

        uv.x() = u;
        uv.y() = v;
        uv.z() = 0.0f;

        normal.x() = -cosTheta;
        normal.y() = 0.0f;
        normal.z() = sinTheta;

        array->AppendVertex(vertex);
        array->AppendUV(uv);
        array->AppendNormal(vertex.Normalize());
      }
    }
    geometry->SetVertexArray(array);

    std::vector<int> indices;

    for (int x = 0; x < kRadialSegments; ++x) {
      for (int y = 0; y < kHeightSegments; ++y) {
        const int a = 1 + y * (kRadialSegments + 1) + x;
        const int b = 1 + (y + 1) * (kRadialSegments + 1) + x;
        const int c = 1 + (y + 1) * (kRadialSegments + 1) + x + 1;
        const int d = 1 + y * (kRadialSegments + 1) + x + 1;

        indices.clear();
        indices.push_back(b);
        indices.push_back(c);
        indices.push_back(d);
        indices.push_back(a);

        // update group counter
        geometry->AddFace(indices, indices, indices);
      }
    }

    vrb::RenderStatePtr state = vrb::RenderState::Create(create);
    state->SetLightsEnabled(false);
    state->SetFragmentPrecision(GL_HIGH_FLOAT);
    state->SetUVTransformEnabled(true);
    geometry->SetRenderState(state);

    return geometry;
  }

  void updateTextureLayout() {
    const float texScaleX = (float)M_PI / theta;
    const float texBiasX = -texScaleX * (0.5f * (1.0f - 1.0f / texScaleX));
    const float texScaleY = layer ? 0.5f  : 1.0f;
    const float texBiasY = -texScaleY * (0.5f * (1.0f - (1.0f / texScaleY)));
    vrb::Matrix transform = vrb::Matrix::Translation(vrb::Vector(texBiasX, texBiasY, 0.0f));
    transform.ScaleInPlace(vrb::Vector(texScaleX, texScaleY, 1.0f));
    if (layer) {
      layer->SetUVTransform(device::Eye::Left, transform);
      layer->SetUVTransform(device::Eye::Right, transform);
    }
    if (geometry) {
      geometry->GetRenderState()->SetUVTransform(transform);
      int32_t segments = (int32_t)ceilf(kRadialSegments * fmin(1.0f, 1.0f / texScaleX));
      if (segments % 2 != 0) {
        segments++;
      }
      const int32_t indicesPerSegment = 6;
      const int32_t start = (kRadialSegments - segments) / 2;
      geometry->SetRenderRange(start * indicesPerSegment, segments * indicesPerSegment);
    }
  }
};

CylinderPtr
Cylinder::Create(vrb::CreationContextPtr aContext, const float aRadius, const float aHeight, const VRLayerCylinderPtr& aLayer) {
  CylinderPtr result = std::make_shared<vrb::ConcreteClass<Cylinder, Cylinder::State> >(aContext);
  result->m.radius = aRadius;
  result->m.height = aHeight;
  result->m.layer = aLayer;
  result->m.Initialize();
  return result;
}

CylinderPtr
Cylinder::Create(vrb::CreationContextPtr aContext, const VRLayerCylinderPtr& aLayer) {
  CylinderPtr result = std::make_shared<vrb::ConcreteClass<Cylinder, Cylinder::State> >(aContext);
  result->m.layer = aLayer;
  result->m.Initialize();
  return result;
}

void
Cylinder::GetTextureSize(int32_t& aWidth, int32_t& aHeight) const {
  aWidth = (int32_t)(m.textureWidth * m.textureScaleX);
  aHeight = (int32_t)(m.textureHeight * m.textureScaleY);
}

void
Cylinder::SetTextureSize(int32_t aWidth, int32_t aHeight) {
  m.textureWidth = aWidth;
  m.textureHeight = aHeight;
  if (m.layer) {
    m.layer->Resize(aWidth, aHeight);
  }
  m.updateTextureLayout();
}

void
Cylinder::SetTexture(const vrb::TexturePtr& aTexture, int32_t aWidth, int32_t aHeight) {
  m.textureWidth = aWidth;
  m.textureHeight = aHeight;
  if (m.geometry) {
    m.geometry->GetRenderState()->SetTexture(aTexture);
  }
  m.updateTextureLayout();
}

void
Cylinder::SetTextureScale(const float aScaleX, const float aScaleY) {
  m.textureScaleX = aScaleX;
  m.textureScaleY = aScaleY;
}

void
Cylinder::SetMaterial(const vrb::Color& aAmbient, const vrb::Color& aDiffuse, const vrb::Color& aSpecular, const float aSpecularExponent) {
  if (m.geometry) {
    m.geometry->GetRenderState()->SetMaterial(aAmbient, aDiffuse, aSpecular, aSpecularExponent);
  }
}

void
Cylinder::SetLightsEnabled(const bool aEnabled) {
  if (m.geometry) {
    m.geometry->GetRenderState()->SetLightsEnabled(aEnabled);
  }
}

float
Cylinder::GetCylinderRadius() const {
  return m.radius;
}

float
Cylinder::GetCylinderHeight() const {
  return m.height;
}

float
Cylinder::GetCylinderTheta() const {
  return m.theta;
}

void
Cylinder::SetCylinderTheta(const float aAngleLength) {
  m.theta = aAngleLength;
  m.updateTextureLayout();
}

void
Cylinder::SetTintColor(const vrb::Color& aColor) {
  if (m.layer) {
    m.layer->SetTintColor(aColor);
  } else if (m.geometry && m.geometry->GetRenderState()) {
    m.geometry->GetRenderState()->SetTintColor(aColor);
  }
}

vrb::NodePtr
Cylinder::GetRoot() const {
  return m.root;
}

VRLayerCylinderPtr
Cylinder::GetLayer() const {
  return m.layer;
}

vrb::TransformPtr
Cylinder::GetTransformNode() const {
  return m.transform;
}

void
Cylinder::SetTransform(const vrb::Matrix& aTransform) {
  m.transform->SetTransform(aTransform);
}

static const float kEpsilon = 0.00000001f;

bool
Cylinder::TestIntersection(const vrb::Vector& aStartPoint, const vrb::Vector& aDirection, vrb::Vector& aResult, vrb::Vector& aNormal, bool aClamp, bool& aIsInside, float& aDistance) const {
  aDistance = -1.0f;
  if (!m.root->IsEnabled(*m.transform)) {
    return false;
  }

  vrb::Matrix worldTransform = m.transform->GetWorldTransform();
  vrb::Matrix modelView = worldTransform.AfineInverse();
  vrb::Vector start = modelView.MultiplyPosition(aStartPoint);
  vrb::Vector direction = modelView.MultiplyDirection(aDirection);
  start = start - direction * 1000.0f;

  const float radius = this->GetCylinderRadius();

  const vrb::Vector A(0.0f, -m.height * 0.5f, 0.0f); // Cylinder bottom center
  const vrb::Vector B(0.0f, m.height * 0.5f, 0.0f); // Cylinder top center

  const vrb::Vector AB = B - A;
  const vrb::Vector AO = start - A;
  const vrb::Vector AOxAB = AO.Cross(AB);
  const vrb::Vector VxAB  = direction.Cross(AB);
  // Solve quadratic formula
  const float ab2 = AB.Dot(AB);
  const float a = VxAB.Dot(VxAB);
  const float b = 2 * VxAB.Dot(AOxAB);
  const float c = AOxAB.Dot(AOxAB) - (radius * radius * ab2);
  const float d = b * b - 4 * a * c;
  if (d < 0) {
    return false;
  }
  double time = (-b + sqrt(d)) / (2 * a);
  if (time < 0) {
    return false;
  }

  const vrb::Vector intersection = start + direction * time; // intersection point
  const vrb::Vector projection = A + AB * (AB.Dot(intersection - A) / ab2); // intersection projected onto cylinder axis

  // Height test
  const bool insideHeight = (projection - A).Magnitude() + (B - projection).Magnitude() <= AB.Magnitude();

  // Normal Test
  const vrb::Vector normal = (projection - intersection).Normalize();
  if (normal.z() < 0) {
    // Ignore cylinder side not facing the user
    return false;
  }

  // Cylinder theta angle test
  const float maxTheta = m.theta;
  const float hitTheta = (float)M_PI - acosf(fabsf(intersection.x()) / radius) * 2.0f;
  aIsInside = insideHeight && hitTheta <= maxTheta && fabs(intersection.y()) <= radius;

  vrb::Vector result = intersection;
  // Clamp to keep pointer in cylinder surface.
  if (aClamp && !aIsInside) {
    const float maxX = radius * cosf(0.5f * ((float)M_PI - maxTheta));
    const float minX = -maxX;
    if (result.x() > maxX) { result.x() = maxX; }
    else if (result.x() < minX) { result.x() = minX; }

    if (result.y() > radius) { result.y() = radius; }
    else if (result.y() < radius) { result.y() = -radius; }
  }

  aResult = worldTransform.MultiplyPosition(intersection);
  aNormal = worldTransform.MultiplyDirection(normal);
  aDistance = (aResult - aStartPoint).Magnitude();

  return true;
}

void
Cylinder::ConvertToQuadCoordinates(const vrb::Vector& point, float& aX, float& aY, bool aClamp) const {
  const vrb::Vector intersection = m.transform->GetWorldTransform().AfineInverse().MultiplyPosition(point);
  const float radius = GetCylinderRadius();
  float ratioY;
  if (intersection.y() > 0.0f) {
    ratioY = 0.5f - 0.5f * intersection.y() / radius;
  } else {
    ratioY = 0.5f + 0.5f * fabsf(intersection.y()) / radius;
  }

  const float hitTheta = (float)M_PI - acosf(fabsf(intersection.x()) / radius) * 2.0f;
  const float maxTheta = m.theta;
  float ratioTheta = hitTheta / maxTheta * 0.5f;
  float ratioX;
  if (intersection.x() > 0.0f) {
    ratioX = 0.5f + ratioTheta;
  } else {
    ratioX = 0.5f - ratioTheta;
  }

  if (aClamp) {
    if (ratioY > 1.0f) {
      ratioY = 1.0f;
    }
    if (ratioY < 0.0f) {
      ratioY = 0.0f;
    }
    if (ratioX > 1.0f) {
      ratioX = 1.0f;
    }
    if (ratioX < 0.0f) {
      ratioX = 0.0f;
    }

    aX = ratioX * m.textureWidth;
    aY = ratioY * m.textureHeight;
  }
}

void
Cylinder::ConvertFromQuadCoordinates(const float aX, const float aY, vrb::Vector& aWorldPoint, vrb::Vector& aNormal) {
  const float ratioX = aX / m.textureWidth;
  const float ratioY = aY / m.textureHeight;
  const float radius = GetCylinderRadius();
  const float pi = (float) M_PI;
  float targetTheta;
  const float maxTheta = m.theta;
  if (ratioX > 0.5f) {
    targetTheta = (ratioX - 0.5f) * maxTheta;
  } else {
    targetTheta = -maxTheta *  (0.5f - ratioX);
  }

  const float angle = pi * 0.5f - targetTheta;
  const float x = radius * cosf(angle);
  const float z = -radius * sinf(angle);
  float y;
  if (ratioY > 0.5f) {
    y =  radius * (ratioY - 0.5f) * 2.0f;
  } else {
    y = -radius * (0.5f - ratioY) * 2.0f;
  }

  vrb::Vector targetPoint(x, y, z);
  aNormal = (vrb::Vector(0.0f, y, 0.0f) - targetPoint).Normalize();

  aWorldPoint = m.transform->GetWorldTransform().MultiplyPosition(targetPoint);
}

float Cylinder::DistanceToBackPlane(const vrb::Vector &aStartPoint, const vrb::Vector &aDirection) const {
  float result = -1.0f;
  if (!m.root->IsEnabled(*m.transform)) {
    return result;
  }
  vrb::Matrix worldTransform = m.transform->GetWorldTransform();
  vrb::Matrix modelView = worldTransform.AfineInverse();
  vrb::Vector point = modelView.MultiplyPosition(aStartPoint);
  vrb::Vector direction = modelView.MultiplyDirection(aDirection);

  const vrb::Vector max(1.0f, 1.0f, -1.0f);
  const vrb::Vector min(-1.0f, -1.0f, -1.0f);
  const vrb::Vector bottomRight(max.x(), min.y(), min.z());
  vrb::Vector normal =  (bottomRight - min).Cross(max - min).Normalize();
  const float dotNormals = direction.Dot(normal);
  if (dotNormals > -kEpsilon) {
    // Not pointed at the plane
    return result;
  }

  const float dotV = (min - point).Dot(normal);

  if ((dotV < kEpsilon) && (dotV > -kEpsilon)) {
    return result;
  }

  const float length = dotV / dotNormals;
  vrb::Vector intersection = point + (direction * length);

  vrb::Vector worldPoint = worldTransform.MultiplyPosition(intersection);
  result = (worldPoint - aStartPoint).Magnitude();

  return result;
}

Cylinder::Cylinder(State& aState, vrb::CreationContextPtr& aContext) : m(aState) {
  m.context = aContext;
}

Cylinder::~Cylinder() {}

} // namespace crow
