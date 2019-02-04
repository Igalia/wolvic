/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "Widget.h"
#include "Cylinder.h"
#include "Quad.h"
#include "VRLayer.h"
#include "VRBrowser.h"
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
  vrb::Vector min;
  vrb::Vector max;
  QuadPtr quad;
  CylinderPtr cylinder;
  float cylinderDensity;
  vrb::TogglePtr root;
  vrb::TransformPtr transform;
  vrb::TextureSurfacePtr surface;
  WidgetPlacementPtr placement;
  WidgetResizerPtr resizer;
  bool resizing;
  bool toggleState;

  State()
      : handle(0)
      , resizing(false)
      , toggleState(false)
      , cylinderDensity(4680.0f)
  {}

  void Initialize(const int aHandle, const int32_t aTextureWidth, const int32_t aTextureHeight,
                  const QuadPtr& aQuad, const CylinderPtr& aCylinder) {
    handle = aHandle;
    name = "crow::Widget-" + std::to_string(handle);
    vrb::RenderContextPtr render = context.lock();
    if (!render) {
      return;
    }

    quad = aQuad;
    cylinder = aCylinder;

    VRLayerPtr layer = GetLayer();

    if (layer) {
      layer->SetSurfaceChangedDelegate([=](const VRLayer& aLayer, VRLayer::SurfaceChange aChange, const std::function<void()>& aCallback) {
        const VRLayerQuad& layerQuad = static_cast<const VRLayerQuad&>(aLayer);
        VRBrowser::DispatchCreateWidgetLayer((jint)aHandle, layerQuad.GetSurface(), layerQuad.GetWidth(), layerQuad.GetHeight(), aCallback);
      });
    } else {
      surface = vrb::TextureSurface::Create(render, name);
      if (quad) {
        quad->SetTexture(surface, aTextureWidth, aTextureHeight);
        quad->SetMaterial(vrb::Color(0.4f, 0.4f, 0.4f), vrb::Color(1.0f, 1.0f, 1.0f), vrb::Color(0.0f, 0.0f, 0.0f), 0.0f);
      } else if (cylinder) {
        cylinder->SetTexture(surface, aTextureWidth, aTextureHeight);
        cylinder->SetMaterial(vrb::Color(0.4f, 0.4f, 0.4f), vrb::Color(1.0f, 1.0f, 1.0f), vrb::Color(0.0f, 0.0f, 0.0f), 0.0f);
      }
    }

    vrb::CreationContextPtr create = render->GetRenderThreadCreationContext();
    transform = vrb::Transform::Create(create);
    root = vrb::Toggle::Create(create);
    root->AddNode(transform);
    if (quad) {
      transform->AddNode(quad->GetRoot());
    } else {
      transform->AddNode(cylinder->GetRoot());
    }

    if (layer) {
      toggleState = true;
      root->ToggleAll(true);
    } else {
      root->ToggleAll(false);
    }
  }

  bool FirstDraw() {
    if (!placement) {
      return false;
    }
    return placement->firstDraw;
  }

  const VRLayerSurfacePtr GetLayer() {
    return quad ? (VRLayerSurfacePtr) quad->GetLayer() : cylinder->GetLayer();
  }

  float WorldWidth() const {
    return max.x() - min.x();
  }

  float WorldHeight() const {
    return max.y() - min.y();
  }

  void UpdateCylinderMatrix() {
    float w = WorldWidth();
    float h = WorldHeight();
    int32_t textureWidth, textureHeight;
    cylinder->GetTextureSize(textureWidth, textureHeight);

    const float radius = cylinder->GetCylinderRadius();
    const float surfaceWidth = (float)textureWidth / placement->density;
    const float surfaceHeight = (float)textureHeight / placement->density;
    // Cylinder density measures the pixels for a 360 cylinder
    // Oculus recommends 4680px density, which is 13 pixels per degree.
    const float theta = (float)M_PI * surfaceWidth / (cylinderDensity * 0.5f);
    cylinder->SetCylinderTheta(theta);
    const float heightScale = surfaceHeight * (float)M_PI / cylinderDensity;
    const float scale = h / (cylinder->GetCylinderHeight() * heightScale);
    vrb::Matrix scaleMatrix = vrb::Matrix::Identity();
    scaleMatrix.ScaleInPlace(vrb::Vector(radius * scale, radius * scale * heightScale, radius * scale));
    vrb::Matrix translation = vrb::Matrix::Translation(vrb::Vector(0.0f, 0.0f, radius * scale));

    const float x = transform->GetWorldTransform().GetTranslation().x();
    if (x != 0.0f) {
      // Automatically adjust correct yaw angle & position for the cylinders not centered on the X axis
      const float r = radius * scale;
      const float perimeter = 2.0f * r * (float)M_PI;
      const float angle = 0.5f * (float)M_PI - x / perimeter * 2.0f * (float)M_PI;
      vrb::Matrix rotation = vrb::Matrix::Rotation(vrb::Vector(-cosf(angle), 0.0f, sinf(angle)));
      vrb::Matrix transform = translation.PostMultiply(scaleMatrix).PostMultiply(rotation);
      transform.PreMultiplyInPlace(vrb::Matrix::Translation(vrb::Vector(-x, 0.0f, 0.0f)));
      cylinder->SetTransform(transform);
    } else {
      cylinder->SetTransform(translation.PostMultiply(scaleMatrix));
    }
  }
};

WidgetPtr
Widget::Create(vrb::RenderContextPtr& aContext, const int aHandle,
               const int32_t aTextureWidth, const int32_t aTextureHeight,const QuadPtr& aQuad) {
  WidgetPtr result = std::make_shared<vrb::ConcreteClass<Widget, Widget::State> >(aContext);
  aQuad->GetWorldMinAndMax(result->m.min, result->m.max);
  result->m.Initialize(aHandle, aTextureWidth, aTextureHeight, aQuad, nullptr);
  return result;
}

WidgetPtr
Widget::Create(vrb::RenderContextPtr& aContext, const int aHandle, const float aWorldWidth, const float aWorldHeight,
               const int32_t aTextureWidth, const int32_t aTextureHeight, const CylinderPtr& aCylinder) {
  WidgetPtr result = std::make_shared<vrb::ConcreteClass<Widget, Widget::State> >(aContext);
  result->m.min = vrb::Vector(-aWorldWidth * 0.5f, -aWorldHeight * 0.5f, 0.0f);
  result->m.max = vrb::Vector(aWorldWidth *0.5f, aWorldHeight * 0.5f, 0.0f);
  result->m.Initialize(aHandle, aTextureWidth, aTextureHeight, nullptr, aCylinder);
  return result;
}

uint32_t
Widget::GetHandle() const {
  return m.handle;
}

void
Widget::ResetFirstDraw() {
  if (m.placement) {
    m.placement->firstDraw = false;
  }
  if (m.root) {
    m.root->ToggleAll(false);
  }
}

const std::string&
Widget::GetSurfaceTextureName() const {
  return m.name;
}

const vrb::TextureSurfacePtr
Widget::GetSurfaceTexture() const {
  return m.surface;
}

void
Widget::GetSurfaceTextureSize(int32_t& aWidth, int32_t& aHeight) const {
  if (m.quad) {
    m.quad->GetTextureSize(aWidth, aHeight);
  } else {
    m.cylinder->GetTextureSize(aWidth, aHeight);
  }
}

void
Widget::SetSurfaceTextureSize(int32_t aWidth, int32_t aHeight) {
  if (m.quad) {
    m.quad->SetTextureSize(aWidth, aHeight);
  } else {
    m.cylinder->SetTextureSize(aWidth, aHeight);
    m.UpdateCylinderMatrix();
  }
}

void
Widget::GetWidgetMinAndMax(vrb::Vector& aMin, vrb::Vector& aMax) const {
  aMin = m.min;
  aMax = m.max;
}

void
Widget::SetWorldWidth(float aWorldWidth) const {
  int32_t width, height;
  this->GetSurfaceTextureSize(width, height);
  const float aspect = (float)width / (float) height;
  const float worldHeight = aWorldWidth / aspect;

  m.min = vrb::Vector(-aWorldWidth * 0.5f, -worldHeight * 0.5f, 0.0f);
  m.max = vrb::Vector(aWorldWidth *0.5f, worldHeight * 0.5f, 0.0f);

  if (m.quad) {
    m.quad->SetWorldSize(aWorldWidth, worldHeight);
  }
  if (m.cylinder) {
    m.UpdateCylinderMatrix();
  }

  if (m.resizing && m.resizer) {
    m.resizer->SetSize(m.min, m.max);
  }
}

void
Widget::GetWorldSize(float& aWidth, float& aHeight) const {
  aWidth = m.max.x() - m.min.x();
  aHeight = m.max.y() - m.min.y();
}

bool
Widget::TestControllerIntersection(const vrb::Vector& aStartPoint, const vrb::Vector& aDirection, vrb::Vector& aResult, vrb::Vector& aNormal, bool& aIsInWidget, float& aDistance) const {
  aDistance = -1.0f;
  if (!m.root->IsEnabled(*m.transform)) {
    return false;
  }

  bool clamp = !m.resizing;
  bool result;
  if (m.quad) {
    result = m.quad->TestIntersection(aStartPoint, aDirection, aResult, aNormal, clamp, aIsInWidget, aDistance);
  } else {
    result = m.cylinder->TestIntersection(aStartPoint, aDirection, aResult, aNormal, clamp, aIsInWidget, aDistance);
  }
  if (result && m.resizing && !aIsInWidget) {
    // Handle extra intersections while resizing
    aIsInWidget = m.resizer->TestIntersection(aResult);
  }

  return result;
}

void
Widget::ConvertToWidgetCoordinates(const vrb::Vector& point, float& aX, float& aY) const {
  bool clamp = !m.resizing;
  if (m.quad) {
    m.quad->ConvertToQuadCoordinates(point, aX, aY, clamp);
  } else {
    m.cylinder->ConvertToQuadCoordinates(point, aX, aY, clamp);
  }
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
  if (m.cylinder) {
    m.UpdateCylinderMatrix();
  }
}

void
Widget::ToggleWidget(const bool aEnabled) {
  m.toggleState = aEnabled;
  m.root->ToggleAll(aEnabled && m.FirstDraw());
}

bool
Widget::IsVisible() const {
  return m.toggleState;
}


vrb::NodePtr
Widget::GetRoot() const {
  return m.root;
}

QuadPtr
Widget::GetQuad() const {
  return m.quad;
}

CylinderPtr
Widget::GetCylinder() const {
  return m.cylinder;
}

VRLayerSurfacePtr
Widget::GetLayer() const {
  return m.GetLayer();
}

vrb::TransformPtr
Widget::GetTransformNode() const {
  return m.transform;
}

const WidgetPlacementPtr&
Widget::GetPlacement() const {
  return m.placement;
}

void
Widget::SetPlacement(const WidgetPlacementPtr& aPlacement) {
  if (!m.FirstDraw() && aPlacement && aPlacement->firstDraw && m.root) {
      m.root->ToggleAll(m.toggleState);
  }
  m.placement = aPlacement;
  if (m.cylinder) {
    m.UpdateCylinderMatrix();
  }
}

void
Widget::StartResize() {
  vrb::Vector worldMin, worldMax;
  GetWidgetMinAndMax(worldMin, worldMax);
  if (m.resizer) {
    m.resizer->SetSize(worldMin, worldMax);
  } else {
    vrb::RenderContextPtr render = m.context.lock();
    if (!render) {
      return;
    }
    vrb::CreationContextPtr create = render->GetRenderThreadCreationContext();
    m.resizer = WidgetResizer::Create(create, this);
    m.transform->InsertNode(m.resizer->GetRoot(), 0);
  }
  m.resizing = true;
  m.resizer->ToggleVisible(true);
  if (m.quad) {
    m.quad->SetScaleMode(Quad::ScaleMode::AspectFit);
    m.quad->SetBackgroundColor(vrb::Color(1.0f, 1.0f, 1.0f, 1.0f));
  }
}

void
Widget::FinishResize() {
  if (!m.resizing) {
    return;
  }
  m.resizing = false;
  m.resizer->ToggleVisible(false);
  if (m.quad) {
    m.quad->SetScaleMode(Quad::ScaleMode::Fill);
    m.quad->SetBackgroundColor(vrb::Color(0.0f, 0.0f, 0.0f, 0.0f));
  }
}

bool
Widget::IsResizing() const {
  return m.resizing;
}

void
Widget::HandleResize(const vrb::Vector& aPoint, bool aPressed, bool& aResized, bool &aResizeEnded) {
  m.resizer->HandleResizeGestures(aPoint, aPressed, aResized, aResizeEnded);
  if (aResized || aResizeEnded) {

    m.min = m.resizer->GetCurrentMin();
    m.max = m.resizer->GetCurrentMax();
    if (m.quad) {
      m.quad->SetWorldSize(m.min, m.max);
    } else if (m.cylinder) {
      m.UpdateCylinderMatrix();
    }
  }
}

void
Widget::HoverExitResize() {
  if (m.resizing) {
    m.resizer->HoverExitResize();
  }
}

void
Widget::SetCylinderDensity(const float aDensity) {
  m.cylinderDensity = aDensity;
  if (m.cylinder) {
    m.UpdateCylinderMatrix();
  }
}

Widget::Widget(State& aState, vrb::RenderContextPtr& aContext) : m(aState) {
  m.context = aContext;
}

Widget::~Widget() {}

} // namespace crow
