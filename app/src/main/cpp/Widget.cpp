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
#include "WidgetBorder.h"
#include "vrb/ConcreteClass.h"

#include "vrb/Color.h"
#include "vrb/RenderContext.h"
#include "vrb/CreationContext.h"
#include "vrb/TextureGL.h"
#include "vrb/Matrix.h"
#include "vrb/GLError.h"
#include "vrb/Geometry.h"
#include "vrb/RenderState.h"
#include "vrb/SurfaceTextureFactory.h"
#include "vrb/TextureSurface.h"
#include "vrb/TextureCache.h"
#include "vrb/Toggle.h"
#include "vrb/Transform.h"
#include "vrb/Vector.h"
#include "vrb/VertexArray.h"

namespace crow {

static const float kFrameSize = 0.02f;
#if defined(OCULUSVR) || defined(PFDMXR)
static const float kBorder = 0.0f;
#else
static const float kBorder = kFrameSize * 0.15f;
#endif


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
  vrb::TransformPtr transformContainer; // Used for cylinder rotations
  vrb::TextureSurfacePtr surface;
  WidgetPlacementPtr placement;
  WidgetResizerPtr resizer;
  bool resizing;
  bool toggleState;
  vrb::TogglePtr bordersContainer;
  std::vector<WidgetBorderPtr> borders;
  vrb::TogglePtr layerProxy;

  State()
      : handle(0)
      , resizing(false)
      , toggleState(false)
      , cylinderDensity(4680.0f)
  {}

  void Initialize(const int aHandle, const WidgetPlacementPtr& aPlacement, const int32_t aTextureWidth, const int32_t aTextureHeight,
                  const QuadPtr& aQuad, const CylinderPtr& aCylinder) {
    handle = (uint32_t)aHandle;
    name = aPlacement->name + '_' + std::to_string(handle);
    vrb::RenderContextPtr render = context.lock();
    if (!render) {
      return;
    }
    placement = aPlacement;
    quad = aQuad;
    cylinder = aCylinder;

    UpdateSurface(aTextureWidth, aTextureHeight);

    vrb::CreationContextPtr create = render->GetRenderThreadCreationContext();
    transform = vrb::Transform::Create(create);
    transformContainer = vrb::Transform::Create(create);
    root = vrb::Toggle::Create(create);
    root->AddNode(transformContainer);
    transformContainer->AddNode(transform);
    if (quad) {
      transform->AddNode(quad->GetRoot());
    } else {
      transform->AddNode(cylinder->GetRoot());
    }

    toggleState = IsReadyForComposition();
    root->ToggleAll(toggleState);
  }

  void UpdateSurface(const int32_t aTextureWidth, const int32_t aTextureHeight) {
    VRLayerPtr layer = GetLayer();
    if (layer) {
      layer->SetSurfaceChangedDelegate([=](const VRLayer& aLayer, VRLayer::SurfaceChange aChange, const std::function<void()>& aCallback) {
        const VRLayerQuad& layerQuad = static_cast<const VRLayerQuad&>(aLayer);
        VRBrowser::DispatchCreateWidgetLayer((jint)handle, layerQuad.GetSurface(), layerQuad.GetWidth(), layerQuad.GetHeight(), aCallback);
      });
    } else {
      if (!surface) {
        vrb::RenderContextPtr render = context.lock();
        surface = vrb::TextureSurface::Create(render, name);
      }

      vrb::Color tintColor = placement->GetTintColor();
      std::string customFragment;
      if (!placement->composited && placement->GetClearColor().Alpha() > 0.0f) {
        customFragment =
#include "shaders/clear_color.fs"
        ;
        tintColor = placement->GetClearColor();
      }

      if (quad) {
        quad->SetTexture(surface, aTextureWidth, aTextureHeight);
        quad->SetMaterial(vrb::Color(0.4f, 0.4f, 0.4f), vrb::Color(1.0f, 1.0f, 1.0f), vrb::Color(0.0f, 0.0f, 0.0f), 0.0f);
        quad->GetRenderState()->SetTintColor(tintColor);
        quad->UpdateProgram(customFragment);
      } else if (cylinder) {
        cylinder->SetTexture(surface, aTextureWidth, aTextureHeight);
        cylinder->SetMaterial(vrb::Color(0.4f, 0.4f, 0.4f), vrb::Color(1.0f, 1.0f, 1.0f), vrb::Color(0.0f, 0.0f, 0.0f), 0.0f);
        cylinder->GetRenderState()->SetTintColor(tintColor);
        cylinder->UpdateProgram(customFragment);
      }
    }
  }

  bool IsReadyForComposition() {
    return GetLayer() || placement->composited || placement->GetClearColor().Alpha() > 0;
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

    const float radius = cylinder->GetCylinderRadius();
    // Compute the arc length and height of the curved surface.
    const float surfaceWidth = w * Cylinder::kWorldDensityRatio;
    const float surfaceHeight = h * Cylinder::kWorldDensityRatio;
    // Cylinder density measures the pixels for a 360 cylinder
    // Oculus recommends 4680px density, which is 13 pixels per degree.
    const float theta = (float)M_PI * surfaceWidth / (cylinderDensity * 0.5f);
    cylinder->SetCylinderTheta(theta);
    // Compute the height scale of the cylinder such that the world width and height of a 1px element remain square.
    const float heightScale = surfaceHeight * (float)M_PI / cylinderDensity;
    // Scale the cylinder so that widget height matches cylinder height.
    const float scale = h / (cylinder->GetCylinderHeight() * heightScale);
    vrb::Matrix scaleMatrix = vrb::Matrix::Identity();
    scaleMatrix.ScaleInPlace(vrb::Vector(radius * scale, radius * scale * heightScale, radius * scale));
    // Translate the z of the cylinder to make the back of the curved surface the z position anchor point.
    vrb::Matrix translation = vrb::Matrix::Translation(vrb::Vector(0.0f, 0.0f, radius * scale));
    cylinder->SetTransform(translation.PostMultiply(scaleMatrix));
    AdjustCylinderRotation(radius * scale);
    UpdateResizerTransform();
  }

  void AdjustCylinderRotation(const float radius, const vrb::Matrix* uiYaw = nullptr) {
    const float x = transform->GetTransform().GetTranslation().x();
    const bool hasCylinderLayer = cylinder && cylinder->GetLayer();
    auto setCylinderLayerTransformIfNeeded = [&](const vrb::Matrix& rotation) {
        if (!hasCylinderLayer)
          return;
        cylinder->GetLayer()->SetRotation(uiYaw ? rotation.PreMultiply(*uiYaw) : rotation);
    };

    if (x != 0.0f && placement->cylinderMapRadius > 0) {
      // Automatically adjust correct yaw angle & position for the cylinders not centered on the X axis
      const float perimeter = 2.0f * radius * (float)M_PI;
      float angle = 0.5f * (float)M_PI - x / perimeter * 2.0f * (float)M_PI;
      float delta = placement->cylinderMapRadius - radius;
      vrb::Matrix transform = vrb::Matrix::Rotation(vrb::Vector(-cosf(angle), 0.0f, sinf(angle)));
      setCylinderLayerTransformIfNeeded(transform);
      transform.PreMultiplyInPlace(vrb::Matrix::Translation(vrb::Vector(0.0f, 0.0f, -delta)));
      transform.PostMultiplyInPlace(vrb::Matrix::Translation(vrb::Vector(-x, 0.0f, delta)));
      transformContainer->SetTransform(transform);
    } else {
      auto identity = vrb::Matrix::Identity();
      // We must reset the identity here because the current front Window might have been the left
      // or the right window previously.
      setCylinderLayerTransformIfNeeded(identity);
      transformContainer->SetTransform(identity);
    }
    if (hasCylinderLayer)
      cylinder->GetLayer()->SetRadius(radius);
  }

  void RemoveResizer() {
    if (resizer) {
      resizer->GetRoot()->RemoveFromParents();
      resizer = nullptr;
    }
    resizing = false;
  }

  void RemoveBorder() {
    if (bordersContainer) {
      bordersContainer->RemoveFromParents();
      bordersContainer = nullptr;
    }
    borders.clear();
  }

  void UpdateResizerTransform() {
    if (resizer) {
      resizer->SetTransform(transformContainer->GetTransform().PostMultiply(transform->GetTransform()));
    }
  }
};

WidgetPtr
Widget::Create(vrb::RenderContextPtr& aContext, const int aHandle, const WidgetPlacementPtr& aPlacement,
               const int32_t aTextureWidth, const int32_t aTextureHeight,const QuadPtr& aQuad) {
  WidgetPtr result = std::make_shared<vrb::ConcreteClass<Widget, Widget::State> >(aContext);
  aQuad->GetWorldMinAndMax(result->m.min, result->m.max);
  result->m.Initialize(aHandle, aPlacement, aTextureWidth, aTextureHeight, aQuad, nullptr);
  return result;
}

WidgetPtr
Widget::Create(vrb::RenderContextPtr& aContext, const int aHandle, const WidgetPlacementPtr& aPlacement, const float aWorldWidth, const float aWorldHeight,
               const int32_t aTextureWidth, const int32_t aTextureHeight, const CylinderPtr& aCylinder) {
  WidgetPtr result = std::make_shared<vrb::ConcreteClass<Widget, Widget::State> >(aContext);
  result->m.min = vrb::Vector(-aWorldWidth * 0.5f, -aWorldHeight * 0.5f, 0.0f);
  result->m.max = vrb::Vector(aWorldWidth *0.5f, aWorldHeight * 0.5f, 0.0f);
  result->m.Initialize(aHandle, aPlacement, aTextureWidth, aTextureHeight, nullptr, aCylinder);
  return result;
}

uint32_t
Widget::GetHandle() const {
  return m.handle;
}

void
Widget::ResetFirstDraw() {
  if (m.placement) {
    m.placement->composited = false;
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
Widget::RecreateSurface() {
  if (m.quad) {
    m.quad->RecreateSurface();
  } else {
    m.cylinder->RecreateSurface();
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

  const float oldWidth = m.max.x() - m.min.x();
  const float oldHeight = m.max.y() - m.min.y();
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

  if ((oldWidth != aWorldWidth) || (oldHeight != worldHeight)) {
    m.RemoveBorder();
  }
}

void
Widget::GetWorldSize(float& aWidth, float& aHeight) const {
  aWidth = m.max.x() - m.min.x();
  aHeight = m.max.y() - m.min.y();
}

bool
Widget::TestControllerIntersection(const vrb::Vector& aStartPoint, const vrb::Vector& aDirection, vrb::Vector& aResult, vrb::Vector& aNormal,
                                   const bool aClamp, bool& aIsInWidget, float& aDistance) const {
  aDistance = -1.0f;
  if (!m.root->IsEnabled(*m.transformContainer)) {
    return false;
  }

  bool result;
  if (m.quad) {
    result = m.quad->TestIntersection(aStartPoint, aDirection, aResult, aNormal, aClamp, aIsInWidget, aDistance);
  } else {
    result = m.cylinder->TestIntersection(aStartPoint, aDirection, aResult, aNormal, aClamp, aIsInWidget, aDistance);
  }
  if (result && m.resizing && m.resizer && !aIsInWidget) {
    // Handle extra intersections while resizing
    aIsInWidget = m.resizer->TestIntersection(aResult);
  }

  return result;
}

void
Widget::ConvertToWidgetCoordinates(const vrb::Vector& point, float& aX, float& aY, bool aClamp) const {
  bool clamp = !m.resizing;
  if (m.quad) {
    m.quad->ConvertToQuadCoordinates(point, aX, aY, clamp && aClamp);
  } else {
    m.cylinder->ConvertToQuadCoordinates(point, aX, aY, clamp && aClamp);
  }
}

vrb::Vector
Widget::ConvertToWorldCoordinates(const vrb::Vector& aLocalPoint) const {
  return m.transform->GetTransform().MultiplyPosition(aLocalPoint);
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
  m.UpdateResizerTransform();
}

void
Widget::ToggleWidget(const bool aEnabled) {
  m.toggleState = aEnabled;
  m.root->ToggleAll(aEnabled && m.IsReadyForComposition());
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

void
Widget::SetQuad(const QuadPtr& aQuad) {
  int32_t textureWidth, textureHeight;
  GetSurfaceTextureSize(textureWidth, textureHeight);
  if (m.cylinder) {
    m.cylinder->GetRoot()->RemoveFromParents();
    m.cylinder = nullptr;
  }
  if (m.quad) {
    m.quad->GetRoot()->RemoveFromParents();
  }

  m.quad = aQuad;
  m.transform->AddNode(aQuad->GetRoot());
  m.transformContainer->SetTransform(vrb::Matrix::Identity());

  m.RemoveResizer();
  m.RemoveBorder();
  m.UpdateSurface(textureWidth, textureHeight);
}

void
Widget::SetCylinder(const CylinderPtr& aCylinder) {
  int32_t textureWidth, textureHeight;
  GetSurfaceTextureSize(textureWidth, textureHeight);
  if (m.quad) {
    m.quad->GetRoot()->RemoveFromParents();
    m.quad = nullptr;
  }
  if (m.cylinder) {
    m.cylinder->GetRoot()->RemoveFromParents();
  }

  m.cylinder = aCylinder;
  m.transform->AddNode(aCylinder->GetRoot());

  m.RemoveResizer();
  m.RemoveBorder();
  m.UpdateSurface(textureWidth, textureHeight);
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
  bool wasComposited = m.placement->composited;
  m.placement = aPlacement;
  if (wasComposited != aPlacement->composited && m.root) {
    m.root->ToggleAll(m.toggleState);
    int32_t textureWidth, textureHeight;
    GetSurfaceTextureSize(textureWidth, textureHeight);
    m.UpdateSurface(textureWidth, textureHeight);
  }
  if (m.cylinder) {
    m.UpdateCylinderMatrix();
  }

  VRLayerSurfacePtr layer = GetLayer();
  if (layer) {
    layer->SetName(aPlacement->name);
    layer->SetClearColor(aPlacement->clearColor);
    layer->SetTintColor(aPlacement->tintColor);
    layer->SetComposited(aPlacement->composited);
  } else if (m.quad && aPlacement->composited) {
    m.quad->SetTintColor(aPlacement->GetTintColor());
  } else if (m.cylinder && aPlacement->composited) {
    m.cylinder->SetTintColor(aPlacement->GetTintColor());
  }
}

WidgetResizerPtr
Widget::StartResize(const vrb::Vector& aMaxSize, const vrb::Vector& aMinSize) {
  vrb::Vector worldMin, worldMax;
  GetWidgetMinAndMax(worldMin, worldMax);
  if (m.resizer) {
    m.resizer->SetSize(worldMin, worldMax);
  } else {
    vrb::RenderContextPtr render = m.context.lock();
    if (!render) {
      return nullptr;
    }
    vrb::CreationContextPtr create = render->GetRenderThreadCreationContext();
    m.resizer = WidgetResizer::Create(create, this);
  }
  m.resizer->SetResizeLimits(aMaxSize, aMinSize);
  m.resizing = true;
  m.resizer->ToggleVisible(true);
  if (m.quad) {
    m.quad->SetScaleMode(Quad::ScaleMode::AspectFit);
    m.quad->SetBackgroundColor(vrb::Color(1.0f, 1.0f, 1.0f, 1.0f));
  }
  m.UpdateResizerTransform();
  return m.resizer;
}

void
Widget::FinishResize() {
  if (!m.resizing) {
    return;
  }
  m.resizing = false;
  if (m.resizer) {
    m.resizer->ToggleVisible(false);
  }
  if (m.quad) {
    m.quad->SetScaleMode(Quad::ScaleMode::Fill);
    m.quad->SetBackgroundColor(vrb::Color(0.0f, 0.0f, 0.0f, 0.0f));
  }
}

bool
Widget::IsResizing() const {
  return m.resizing;
}

bool
Widget::IsResizingActive() const {
  return m.resizing && m.resizer && m.resizer->IsActive();
}

void
Widget::HandleResize(const vrb::Vector& aPoint, bool aPressed, bool& aResized, bool &aResizeEnded) {
  if (!m.resizing || !m.resizer) {
    return;
  }
  m.resizer->HandleResizeGestures(aPoint, aPressed, aResized, aResizeEnded);
  if (aResized || aResizeEnded) {
    m.min = m.resizer->GetResizeMin();
    m.max = m.resizer->GetResizeMax();
    if (m.quad) {
      m.quad->SetWorldSize(m.min, m.max);
    } else if (m.cylinder) {
      m.UpdateCylinderMatrix();
    }
    m.RemoveBorder();
  }
}

void
Widget::HoverExitResize() {
  if (m.resizing && m.resizer) {
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

float
Widget::GetCylinderDensity() const {
  return m.cylinderDensity;
}

void
Widget::SetBorderColor(const vrb::Color &aColor) {
  const bool visible = aColor.Alpha() > 0.0f;
  if (!visible && m.bordersContainer) {
    m.bordersContainer->ToggleAll(false);
    return;
  }
  if (visible && !m.bordersContainer) {
    vrb::RenderContextPtr render = m.context.lock();
    vrb::CreationContextPtr create = render->GetRenderThreadCreationContext();
    m.bordersContainer = vrb::Toggle::Create(create);
    m.borders = WidgetBorder::CreateFrame(create, *this, kFrameSize, kBorder);
    for (const WidgetBorderPtr& border: m.borders) {
      m.bordersContainer->AddNode(border->GetTransformNode());
    }
    m.transform->InsertNode(m.bordersContainer, 0);
  }

  if (visible) {
    m.bordersContainer->ToggleAll(true);
    for (const WidgetBorderPtr& border: m.borders) {
      border->SetColor(aColor);
    }
  }
}

void
Widget::SetProxifyLayer(const bool aValue) {
  if (!aValue) {
    if (m.layerProxy) {
      m.layerProxy->ToggleAll(false);
    }
    return;
  }

  if (!m.layerProxy) {
    vrb::RenderContextPtr render = m.context.lock();
    vrb::CreationContextPtr create = render->GetRenderThreadCreationContext();
    m.layerProxy = vrb::Toggle::Create(create);
    // Proxy objects must clear the existing surface, so set a proper blend function.
    m.layerProxy->SetPreRenderLambda(create, []() {
      VRB_GL_CHECK(glBlendFunc(GL_ZERO, GL_ONE_MINUS_SRC_ALPHA));
    });
    m.layerProxy->SetPostRenderLambda(create, []() {
      VRB_GL_CHECK(glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA));
    });
    m.transform->AddNode(m.layerProxy);
    int32_t textureWidth, textureHeight;
    GetSurfaceTextureSize(textureWidth, textureHeight);
    // Reduce quality, proxy objects do not need full quality.
    textureWidth /= 2;
    textureHeight /= 2;
    vrb::TextureSurfacePtr proxySurface = vrb::TextureSurface::Create(render, m.name);
    if (m.cylinder) {
      CylinderPtr proxy = Cylinder::Create(create, *m.cylinder);
      proxy->SetCylinderTheta(m.cylinder->GetCylinderTheta());
      proxy->SetTexture(proxySurface, textureWidth, textureHeight);
      proxy->SetTransform(m.cylinder->GetTransformNode()->GetTransform());
      proxy->UpdateProgram("");
      m.layerProxy->AddNode(proxy->GetRoot());
    } else {
      QuadPtr proxy = Quad::Create(create, *m.quad);
      proxy->SetTexture(proxySurface, textureWidth, textureHeight);
      proxy->UpdateProgram("");
      m.layerProxy->AddNode(proxy->GetRoot());
    }
  }

  m.layerProxy->ToggleAll(true);
}

void Widget::LayoutQuadWithCylinderParent(const WidgetPtr& aParent) {
  if (!aParent) {
    // No parent, reset the container transform.
    m.transformContainer->SetTransform(vrb::Matrix::Identity());
    return;
  }
  CylinderPtr cylinder = aParent->GetCylinder();
  if (cylinder) {
    // The widget is flat and the parent is a cylinder.
    // Adjust the widget rotation based on the parent cylinder
    // e.g. rotate the tray based on the parent cylindrical window.
    auto x = m.transform->GetTransform().GetTranslation().x();
    if (x != 0.0) {
      auto radius = m.placement->cylinderMapRadius;
      auto angle = M_PI_2 - atan(x/radius);
      vrb::Matrix transform = vrb::Matrix::Rotation(vrb::Vector(-cosf(angle), 0.0f, sinf(angle)));
      transform.PostMultiplyInPlace(vrb::Matrix::Translation(vrb::Vector(-x, 0.0f, 0.0f)));
      m.transformContainer->SetTransform(transform);
    } else {
      m.transformContainer->SetTransform(vrb::Matrix::Identity());
    }
  } else {
    // The widget is flat and the parent is flat. Copy the parent transformContainer matrix (used for cylinder rotations)
    // because the parent widget can still be recursively rotated based on a parent cylinder.
    // e.g. Place the tray tooltips on the correct tray position which may be rotated based on the
    // parent cylindrical window.
    m.transformContainer->SetTransform(aParent->m.transformContainer->GetTransform());
  }
  m.UpdateResizerTransform();
}

void Widget::RecenterYawInCylinderLayer(const vrb::Matrix& reorientMatrix) {
  const float radius = GetCylinder()->GetTransformNode()->GetTransform().GetScale().x();
  m.AdjustCylinderRotation(radius, &reorientMatrix);
}

void
Widget::GetAngularBoundsFromCenter(float& aStartAngle, float& aEndAngle) const {
  // Get widget bounds in local space
  vrb::Vector min, max;
  GetWidgetMinAndMax(min, max);

  // Get the transform of the widget
  vrb::Matrix worldTransform = m.transform->GetWorldTransform();

  // Use the left and right edges at mid-height
  float midY = (min.y() + max.y()) / 2.0f;
  vrb::Vector leftEdge = worldTransform.MultiplyPosition(vrb::Vector(min.x(), midY, 0.0f));
  vrb::Vector rightEdge = worldTransform.MultiplyPosition(vrb::Vector(max.x(), midY, 0.0f));

  // Calculate angles (assuming cylinder center is at origin)
  float leftAngle = atan2f(-leftEdge.z(), leftEdge.x());
  float rightAngle = atan2f(-rightEdge.z(), rightEdge.x());

  // Determine the smallest angular range (handle wraparound)
  float diff = rightAngle - leftAngle;
  if (diff < 0) diff += 2.0f * M_PI;

  // Choose direction that gives smaller angular range
  if (diff <= M_PI) {
    aStartAngle = leftAngle;
    aEndAngle = rightAngle;
  } else {
    aStartAngle = rightAngle;
    aEndAngle = leftAngle;
  }

  // Ensure end angle is greater than start angle
  if (aEndAngle < aStartAngle) {
    aEndAngle += 2.0f * M_PI;
  }

  VRB_LOG("StickyLock: start %.2f  end %.2f  width %.2f  theta %.2f", aStartAngle, aEndAngle, (aEndAngle-aStartAngle),
          (GetCylinder()?GetCylinder()->GetCylinderTheta():0.0f));
  // TODO angular width and theta are different
}

Widget::Widget(State& aState, vrb::RenderContextPtr& aContext) : m(aState) {
  m.context = aContext;
}

} // namespace crow
