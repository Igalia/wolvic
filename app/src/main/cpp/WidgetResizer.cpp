/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "WidgetResizer.h"
#include "WidgetPlacement.h"
#include "Widget.h"
#include "WidgetBorder.h"
#include "Cylinder.h"
#include "Quad.h"
#include "vrb/ConcreteClass.h"

#include "vrb/Color.h"
#include "vrb/CreationContext.h"
#include "vrb/Matrix.h"
#include "vrb/GLError.h"
#include "vrb/Geometry.h"
#include "vrb/Program.h"
#include "vrb/ProgramFactory.h"
#include "vrb/RenderState.h"
#include "vrb/SurfaceTextureFactory.h"
#include "vrb/TextureGL.h"
#include "vrb/TextureSurface.h"
#include "vrb/Toggle.h"
#include "vrb/Transform.h"
#include "vrb/Vector.h"
#include "vrb/VertexArray.h"

namespace crow {

struct ResizeBar;

typedef std::shared_ptr<ResizeBar> ResizeBarPtr;

// Size of resizing bars.
static const float kBarSize = 0.04f;
// Additional padding, note that without padding bars and handles overlap the edge of the widget.
static const float kPadding = 0.04f;
#if defined(OCULUSVR)
  static const float kBorder = 0.0f;
#else
  static const float kBorder = kBarSize * 0.15f;
#endif
// Radius of resizing handles.
static const float kHandleRadius = 0.04f;
// Radius of the hover/click area for the handles is kHandleRadius * kTouchRatio.
static const float kTouchRatio = 6.0f;
static const vrb::Vector kDefaultMinResize(1.5f, 1.5f, 0.0f);
static const vrb::Vector kDefaultMaxResize(8.0f, 4.5f, 0.0f);
// Colors for the handles.
static vrb::Color kDefaultHandleColor(0xE2E6EBFF);
static vrb::Color kHoverHandleColor(0x518FE1FF);
static vrb::Color kActiveHandleColor(0x314259FF);
// Colors for the bars.
static vrb::Color kDefaultBarColor(0xE2E6EBFF);
static vrb::Color kHoverBarColor(0x518FE1FF);
static vrb::Color kActiveBarColor(0x314259FF);

enum class ResizeState {
  Default,
  Hovered,
  Active,
};


template <typename T>
static void UpdateResizeMaterial(const T& aTarget, ResizeState aState) {
  vrb::Color ambient(0.5f, 0.5f, 0.5f, 1.0f);
  vrb::Color diffuse = kDefaultHandleColor;
  if (aState == ResizeState::Hovered) {
    diffuse = kHoverHandleColor;
  } else if (aState == ResizeState::Active) {
    diffuse = kActiveHandleColor;
  }

  aTarget->SetMaterial(ambient, diffuse, vrb::Color(0.0f, 0.0f, 0.0f), 0.0f);
}

struct ResizeBar {
  static ResizeBarPtr Create(vrb::CreationContextPtr& aContext, const vrb::Vector& aCenter, const vrb::Vector& aScale, const device::EyeRect& aBorderRect, const WidgetBorder::Mode aMode) {
    auto result = std::make_shared<ResizeBar>();
    result->center = aCenter;
    result->scale = aScale;
    vrb::Vector size(kBarSize, kBarSize, 0.0f);
    result->border = WidgetBorder::Create(aContext, size, kBorder, aBorderRect, aMode);
    result->resizeState = ResizeState::Default;
    result->UpdateMaterial();
    return result;
  }

  void SetResizeState(ResizeState aState) {
    if (resizeState != aState) {
      resizeState = aState;
      UpdateMaterial();
    }
  }

  void UpdateMaterial() {
    vrb::Color color = kDefaultBarColor;
    if (resizeState == ResizeState::Hovered) {
      color = kHoverBarColor;
    } else if (resizeState == ResizeState::Active) {
      color = kActiveBarColor;
    }
    border->SetColor(color);
  }

  void SetTransform(const vrb::Matrix& aTransform) {
    border->GetTransformNode()->SetTransform(aTransform);
  }

  vrb::Vector center;
  vrb::Vector scale;
  WidgetBorderPtr border;
  ResizeState resizeState;
};


struct ResizeHandle;
typedef std::shared_ptr<ResizeHandle> ResizeHandlePtr;

struct ResizeHandle {
  enum class ResizeMode {
    Vertical,
    Horizontal,
    Both
  };

  static ResizeHandlePtr Create(vrb::CreationContextPtr& aContext, const vrb::Vector& aCenter, ResizeMode aResizeMode, const std::vector<ResizeBarPtr>& aAttachedBars) {
    auto result = std::make_shared<ResizeHandle>();
    result->center = aCenter;
    result->resizeMode = aResizeMode;
    result->attachedBars = aAttachedBars;
    vrb::Vector max(kHandleRadius, kHandleRadius, 0.0f);
    result->geometry = ResizeHandle::CreateGeometry(aContext);
    result->transform = vrb::Transform::Create(aContext);
    result->transform->AddNode(result->geometry);
    result->root = vrb::Toggle::Create(aContext);
    result->root->AddNode(result->transform);
    result->resizeState = ResizeState ::Default;
    UpdateResizeMaterial(result->geometry->GetRenderState(), result->resizeState);
    return result;
  }

  void SetResizeState(ResizeState aState) {
    if (resizeState != aState) {
      resizeState = aState;
      UpdateResizeMaterial(geometry->GetRenderState(), resizeState);
    }

    for (const ResizeBarPtr& bar: attachedBars) {
      bar->SetResizeState(aState);
    }
  }

  static vrb::GeometryPtr CreateGeometry(vrb::CreationContextPtr& aContext) {
    vrb::VertexArrayPtr array = vrb::VertexArray::Create(aContext);
    array->AppendVertex(vrb::Vector(0.0f, 0.0f, 0.0f));
    array->AppendNormal(vrb::Vector(0.0f, 0.0f, 1.0f));

    vrb::Color solid(1.0f, 1.0f, 1.0f, 1.0f);
    vrb::Color border(1.0f, 1.0f, 1.0f, 0.0f);
    array->AppendColor(solid);

    std::vector<int> indices;
    std::vector<int> normalIndices;

    const int kSides = 30;
    double delta = 2.0 * M_PI / kSides;
    for (int i = 0; i < kSides; ++i) {
      const double angle = delta * i;
      array->AppendVertex(vrb::Vector(kHandleRadius * (float)cos(angle), kHandleRadius * (float)sin(angle), 0.0f));
      array->AppendColor(solid);
      if (i > 0) {
        indices.push_back(1);
        indices.push_back(i + 1);
        indices.push_back(i + 2);
        normalIndices.push_back(1);
        normalIndices.push_back(1);
        normalIndices.push_back(1);
      }
    }

    indices.push_back(1);
    indices.push_back(array->GetVertexCount());
    indices.push_back(2);
    normalIndices.push_back(1);
    normalIndices.push_back(1);
    normalIndices.push_back(1);

    vrb::GeometryPtr geometry = vrb::Geometry::Create(aContext);
    vrb::ProgramPtr program = aContext->GetProgramFactory()->CreateProgram(aContext, vrb::FeatureVertexColor);
    vrb::RenderStatePtr state = vrb::RenderState::Create(aContext);
    state->SetProgram(program);
    state->SetLightsEnabled(false);
    geometry->SetVertexArray(array);
    geometry->SetRenderState(state);
    geometry->AddFace(indices, indices, normalIndices);


    if (kBorder > 0.0f) {
      int lastCircleIndex = array->GetVertexCount();
      int borderIndex = array->GetVertexCount() + 1;
      for (int i = 0; i < kSides; ++i) {
        const double angle = delta * i;
        const float r = kHandleRadius + kBorder;
        array->AppendVertex(vrb::Vector(r * (float)cos(angle), r * (float)sin(angle), 0.0f));
        array->AppendColor(border);
        if (i > 0) {
          indices.clear();
          normalIndices.clear();
          indices.push_back(i + 2);
          indices.push_back(i + 1);
          indices.push_back(borderIndex);
          indices.push_back(++borderIndex);
          normalIndices.push_back(1);
          normalIndices.push_back(1);
          normalIndices.push_back(1);
          normalIndices.push_back(1);
          geometry->AddFace(indices, indices, normalIndices);
        }
      }
      indices.clear();
      normalIndices.clear();
      indices.push_back(2);
      indices.push_back(lastCircleIndex);
      indices.push_back(borderIndex);
      indices.push_back(lastCircleIndex + 1);
      normalIndices.push_back(1);
      normalIndices.push_back(1);
      normalIndices.push_back(1);
      normalIndices.push_back(1);
      geometry->AddFace(indices, indices, normalIndices);
    }


    return geometry;
  }

  void SetVisible(const bool aVisible) {
    if (visible != aVisible) {
      root->ToggleAll(aVisible);
      visible = aVisible;
    }
  }

  vrb::Vector center;
  ResizeMode resizeMode;
  std::vector<ResizeBarPtr> attachedBars;
  vrb::GeometryPtr geometry;
  vrb::TogglePtr root;
  vrb::TransformPtr transform;
  ResizeState resizeState;
  float touchRatio;
  bool visible = true;
};

struct WidgetResizer::State {
  vrb::CreationContextWeak context;
  Widget * widget;
  vrb::Vector min;
  vrb::Vector max;
  vrb::Vector resizeStartMin;
  vrb::Vector resizeStartMax;
  vrb::Vector currentMin;
  vrb::Vector currentMax;
  vrb::Vector pointerOffset;
  vrb::Vector maxSize;
  vrb::Vector minSize;
  bool resizing;
  vrb::TogglePtr root;
  vrb::TransformPtr transform;
  std::vector<ResizeHandlePtr> resizeHandles;
  std::vector<ResizeBarPtr> resizeBars;
  ResizeHandlePtr activeHandle;
  bool wasPressed;

  State()
      : widget(nullptr)
      , resizing(false)
      , wasPressed(false)
  {}

  void Initialize() {
    vrb::CreationContextPtr create = context.lock();
    if (!create) {
      return;
    }
    root = vrb::Toggle::Create(create);
    transform = vrb::Transform::Create(create);
    root->AddNode(transform);
    currentMin = min;
    currentMax = max;
    maxSize = kDefaultMaxResize;
    minSize = kDefaultMinResize;

    vrb::Vector horizontalSize(0.0f, 0.5f, 0.0f);
    vrb::Vector verticalSize(0.5f, 0.0f, 0.0f);
    device::EyeRect horizontalBorder(0.0f, kBorder, 0.0f, kBorder);
    device::EyeRect verticalBorder(kBorder, 0.0f, kBorder, 0.0f);
    WidgetBorder::Mode mode = widget->GetCylinder() ? WidgetBorder::Mode::Cylinder : WidgetBorder::Mode::Quad;
    ResizeBarPtr leftTop = CreateResizeBar(vrb::Vector(0.0f, 0.75f, 0.0f), horizontalSize, verticalBorder, WidgetBorder::Mode::Quad);
    ResizeBarPtr leftBottom = CreateResizeBar(vrb::Vector(0.0f, 0.25f, 0.0f), horizontalSize, verticalBorder, WidgetBorder::Mode::Quad);
    ResizeBarPtr rightTop = CreateResizeBar(vrb::Vector(1.0f, 0.75f, 0.0f), horizontalSize, verticalBorder, WidgetBorder::Mode::Quad);
    ResizeBarPtr rightBottom = CreateResizeBar(vrb::Vector(1.0f, 0.25f, 0.0f), horizontalSize, verticalBorder, WidgetBorder::Mode::Quad);
    ResizeBarPtr topLeft = CreateResizeBar(vrb::Vector(0.25f, 1.0f, 0.0f), verticalSize, horizontalBorder, mode);
    ResizeBarPtr topRight = CreateResizeBar(vrb::Vector(0.75f, 1.0f, 0.0f), verticalSize, horizontalBorder, mode);
    //ResizeBarPtr bottomLeft = CreateResizeBar(vrb::Vector(0.25f, 0.0f, 0.0f), verticalSize, mode);
    //ResizeBarPtr bottomRight = CreateResizeBar(vrb::Vector(0.75f, 0.0f, 0.0f), verticalSize, mode);
    ResizeBarPtr bottom = CreateResizeBar(vrb::Vector(0.5f, 0.0f, 0.0f), vrb::Vector(1.0f, 0.0f, 0.0f), horizontalBorder, mode);
    //ResizeBarPtr bottomLeftCorner = CreateResizeBar(vrb::Vector(0.0f, 0.0f, 0.0f), vrb::Vector(0.0f, 0.0f, 0.0f), device::EyeRect(kBorder, kBorder, 0.0f, 0.0f), ResizeBar::Mode::Quad);
    //ResizeBarPtr bottomRightCorner = CreateResizeBar(vrb::Vector(1.0f, 0.0f, 0.0f), vrb::Vector(0.0f, 0.0f, 0.0f), device::EyeRect(.0f, 0.0f, kBorder, kBorder), ResizeBar::Mode::Quad);

    CreateResizeHandle(vrb::Vector(0.0f, 1.0f, 0.0f), ResizeHandle::ResizeMode::Both, {leftTop, topLeft});
    CreateResizeHandle(vrb::Vector(1.0f, 1.0f, 0.0f), ResizeHandle::ResizeMode::Both, {rightTop, topRight});
    //CreateResizeHandle(vrb::Vector(0.0f, 0.0f, 0.0f), ResizeHandle::ResizeMode::Both, {leftBottom, bottomLeft});
    //CreateResizeHandle(vrb::Vector(1.0f, 0.0f, 0.0f), ResizeHandle::ResizeMode::Both, {rightBottom, bottomRight}, 1.0f);
    CreateResizeHandle(vrb::Vector(0.5f, 1.0f, 0.0f), ResizeHandle::ResizeMode::Horizontal, {topLeft, topRight});
    //CreateResizeHandle(vrb::Vector(0.5f, 0.0f, 0.0f), ResizeHandle::ResizeMode::Horizontal, {bottomLeft, bottomRight});
    CreateResizeHandle(vrb::Vector(0.0f, 0.5f, 0.0f), ResizeHandle::ResizeMode::Vertical, {leftTop, leftBottom});
    CreateResizeHandle(vrb::Vector(1.0f, 0.5f, 0.0f), ResizeHandle::ResizeMode::Vertical, {rightTop, rightBottom});

    Layout();
  }

  ResizeBarPtr CreateResizeBar(const vrb::Vector& aCenter, vrb::Vector aScale, const device::EyeRect& aBorder, const WidgetBorder::Mode aMode) {
    vrb::CreationContextPtr create = context.lock();
    if (!create) {
      return nullptr;
    }
    ResizeBarPtr result = ResizeBar::Create(create, aCenter, aScale, aBorder, aMode);
    resizeBars.push_back(result);
    transform->AddNode(result->border->GetTransformNode());
    return result;
  }

  ResizeHandlePtr CreateResizeHandle(const vrb::Vector &aCenter, ResizeHandle::ResizeMode aResizeMode,
                                     const std::vector<ResizeBarPtr> &aBars, const float aTouchRatio = kTouchRatio) {
    vrb::CreationContextPtr create = context.lock();
    if (!create) {
      return nullptr;
    }
    ResizeHandlePtr result = ResizeHandle::Create(create, aCenter, aResizeMode, aBars);
    result->touchRatio = aTouchRatio;
    resizeHandles.push_back(result);
    transform->InsertNode(result->root, 0);
    return result;
  }

  float WorldWidth() const {
    return max.x() - min.x();
  }

  float WorldHeight() const {
    return max.y() - min.y();
  }

  vrb::Vector ProjectPoint(const vrb::Vector& aWorldPoint) const {
    if (widget->GetCylinder()) {
      return widget->GetCylinder()->ProjectPointToQuad(aWorldPoint, GetAnchorX(), widget->GetCylinderDensity(), min, max);
    } else {
      // For quads just convert to world point to local point.
      vrb::Matrix modelView = widget->GetTransformNode()->GetWorldTransform().AfineInverse();
      return modelView.MultiplyPosition(aWorldPoint);
    }
  }

  void LayoutQuad() {
    const float width = WorldWidth() + 2 * kPadding;
    const float height = WorldHeight() + 2 * kPadding;

    for (ResizeBarPtr& bar: resizeBars) {
      float targetWidth = bar->scale.x() > 0.0f ? (bar->scale.x() * fabsf(width)) + kBarSize : kBarSize;
      float targetHeight = bar->scale.y() > 0.0f ? (bar->scale.y() * fabs(height)) + kBarSize : kBarSize;
      vrb::Matrix matrix = vrb::Matrix::Position(vrb::Vector(min.x() + width * bar->center.x() - kPadding,
                                                 min.y() + height * bar->center.y() - kPadding,
                                                 0.005f));
      matrix.ScaleInPlace(vrb::Vector(targetWidth / kBarSize, targetHeight / kBarSize, 1.0f));
      bar->SetTransform(matrix);
    }

    for (ResizeHandlePtr& handle: resizeHandles) {
      vrb::Matrix matrix = vrb::Matrix::Position(vrb::Vector(min.x() + width * handle->center.x() - kPadding,
                                                             min.y() + height * handle->center.y() - kPadding,
                                                             0.006f));
      handle->transform->SetTransform(matrix);
    }
  }

  void LayoutCylinder() {
    const float width = currentMax.x() - currentMin.x() + 2 * kPadding;
    const float height = currentMax.y() - currentMin.y() + 2 * kPadding;
    const float sx = width / WorldWidth();
    const float radius = widget->GetCylinder()->GetTransformNode()->GetTransform().GetScale().x() - kBarSize * 0.5f;
    const float theta = widget->GetCylinder()->GetCylinderTheta() * sx;
    int32_t textureWidth, textureHeight;
    widget->GetCylinder()->GetTextureSize(textureWidth, textureHeight);
    vrb::Matrix modelView = widget->GetTransformNode()->GetWorldTransform().AfineInverse();

    // Delta for x anchor point != 0.5f.
    float centerX = 0.0f;
    const float anchorX = GetAnchorX();
    if (anchorX == 1.0f) {
      centerX = min.x() + width * 0.5f;
    } else if (anchorX == 0.0f) {
      centerX = max.x() - width * 0.5f;
    }
    centerX -= kPadding;
    const float perimeter = 2.0f * radius * (float)M_PI;
    float angleDelta = centerX / perimeter * 2.0f * (float)M_PI;

    for (ResizeBarPtr& bar: resizeBars) {
      float targetWidth = bar->scale.x() > 0.0f ? (bar->scale.x() * fabsf(width)) + kBarSize + kPadding : kBarSize;
      float targetHeight = bar->scale.y() > 0.0f ? (bar->scale.y() * fabs(height)) + kBarSize + kPadding : kBarSize;
      float pointerAngle = (float)M_PI * 0.5f + theta * 0.5f - theta * bar->center.x() + angleDelta;
      vrb::Matrix rotation = vrb::Matrix::Rotation(vrb::Vector(-cosf(pointerAngle), 0.0f, sinf(pointerAngle)));
      if (bar->border->GetCylinder()) {
        bar->border->GetCylinder()->SetCylinderTheta(theta * bar->scale.x());
        vrb::Matrix translation = vrb::Matrix::Position(vrb::Vector(0.0f - kPadding,
                                                        min.y() + height * bar->center.y() - kPadding,
                                                        radius));
        vrb::Matrix scale = vrb::Matrix::Identity();
        scale.ScaleInPlace(vrb::Vector(radius, 1.0f, radius));
        bar->SetTransform(translation.PostMultiply(scale).PostMultiply(rotation));
      } else {
        vrb::Matrix translation = vrb::Matrix::Position(vrb::Vector(radius * cosf(pointerAngle) - kPadding,
                                                        min.y() + height * bar->center.y() - kPadding,
                                                        radius - radius * sinf(pointerAngle)));
        vrb::Matrix scale = vrb::Matrix::Identity();
        scale.ScaleInPlace(vrb::Vector(targetWidth / kBarSize, targetHeight / kBarSize, 1.0f));
        bar->SetTransform(translation.PostMultiply(scale).PostMultiply(rotation));
      }
    }

    for (ResizeHandlePtr& handle: resizeHandles) {
      const float pointerAngle = (float)M_PI * 0.5f + theta * 0.5f - theta * handle->center.x() + angleDelta;
      vrb::Matrix translation = vrb::Matrix::Position(vrb::Vector(radius * cosf(pointerAngle) - kPadding,
                                                      min.y() + height * handle->center.y() - kPadding,
                                                      radius - radius * sinf(pointerAngle)));
      vrb::Matrix rotation = vrb::Matrix::Rotation(vrb::Vector(-cosf(pointerAngle), 0.0f, sinf(pointerAngle)));
      handle->transform->SetTransform(translation.PostMultiply(rotation));
    }
  }

  void UpdateVisibleHandles() {
    float anchorX = GetAnchorX();

    for (ResizeHandlePtr & handle: resizeHandles) {
      handle->SetVisible(handle->center.x() == 0.5f || (handle->center.x() != anchorX));
    }
  }

  float GetAnchorX() const {
    if (widget && widget->GetPlacement()) {
      return  widget->GetPlacement()->anchor.x();
    }

    return 0.5f;
  }

  void Layout() {
    UpdateVisibleHandles();
    if (widget->GetCylinder()) {
      LayoutCylinder();
    } else {
      LayoutQuad();
    }
  }

  ResizeHandlePtr GetIntersectingHandler(const vrb::Vector& point) {
    for (const ResizeHandlePtr& handle: resizeHandles) {
      if (!handle->visible) {
        continue;
      }
      vrb::Vector worldCenter(min.x() + WorldWidth() * handle->center.x(), min.y() + WorldHeight() * handle->center.y(), 0.0f);
      float distance = (point - worldCenter).Magnitude();
      if (distance < kHandleRadius * handle->touchRatio) {
        return handle;
      }
    }
    return nullptr;
  }

  void HandleResize(const vrb::Vector& aPoint) {
    if (!activeHandle) {
      return;
    }

    const vrb::Vector point = aPoint - pointerOffset;
    float originalWidth = fabsf(resizeStartMax.x() - resizeStartMin.x());
    float originalHeight = fabsf(resizeStartMax.y() - resizeStartMin.y());
    float originalAspect = originalWidth / originalHeight;

    float width = fabsf(point.x()) * 2.0f;
    if (widget->GetPlacement()->anchor.x() == 1.0f) {
      width = fabsf(max.x() - point.x());
    } else if (widget->GetPlacement()->anchor.x() == 0.0f) {
      width = fabsf(point.x() - min.x());
    }
    float height = fabsf(point.y() - min.y());

    // Calculate resize based on resize mode
    bool keepAspect = false;
    if (activeHandle->resizeMode == ResizeHandle::ResizeMode::Vertical) {
      height = originalHeight;
    } else if (activeHandle->resizeMode == ResizeHandle::ResizeMode::Horizontal) {
      width = originalWidth;
    } else {
      width = fmaxf(width, height * originalAspect);
      height = width / originalAspect;
      keepAspect = true;
    }

    // Clamp to max and min resize sizes
    width = fmaxf(fminf(width, maxSize.x()), minSize.x());
    height = fmaxf(fminf(height, maxSize.y()), minSize.y());
    if (keepAspect) {
      height = width / originalAspect;
    }

    currentMin = vrb::Vector(-width * 0.5f, -height * 0.5f, 0.0f);
    currentMax = vrb::Vector(width * 0.5f, height * 0.5f, 0.0f);

    // Reset world min and max points with the new resize values
    if (!widget->GetCylinder()) {
      min = currentMin;
      max = currentMax;
    }

    Layout();
  }
};

WidgetResizerPtr
WidgetResizer::Create(vrb::CreationContextPtr& aContext, Widget * aWidget) {
  WidgetResizerPtr result = std::make_shared<vrb::ConcreteClass<WidgetResizer, WidgetResizer::State> >(aContext);
  aWidget->GetWidgetMinAndMax(result->m.min, result->m.max);
  result->m.widget = aWidget;
  result->m.Initialize();
  return result;
}


vrb::NodePtr
WidgetResizer::GetRoot() const {
  return m.root;
}

void
WidgetResizer::SetSize(const vrb::Vector& aMin, const vrb::Vector& aMax) {
  m.min = aMin;
  m.max = aMax;
  m.currentMin = aMin;
  m.currentMax = aMax;
  m.Layout();
}

void
WidgetResizer::SetResizeLimits(const vrb::Vector& aMaxSize, const vrb::Vector& aMinSize) {
  m.maxSize = aMaxSize;
  m.minSize = aMinSize;
}

void
WidgetResizer::ToggleVisible(bool aVisible) {
  m.root->ToggleAll(aVisible);
}

bool
WidgetResizer::TestIntersection(const vrb::Vector& aWorldPoint) const {
  if (m.activeHandle) {
    return true;
  }
  const vrb::Vector point = m.ProjectPoint(aWorldPoint);
  vrb::Vector extraMin = vrb::Vector(m.min.x() - kPadding - kBarSize * 0.5f, m.min.y() - kPadding - kBarSize * 0.5f, 0.0f);
  vrb::Vector extraMax = vrb::Vector(m.max.x() + kPadding + kBarSize * 0.5f, m.max.y() + kPadding + kBarSize * 0.5f, 0.0f);

  if ((point.x() >= extraMin.x()) && (point.y() >= extraMin.y()) &&(point.z() >= (extraMin.z() - 0.1f)) &&
      (point.x() <= extraMax.x()) && (point.y() <= extraMax.y()) &&(point.z() <= (extraMax.z() + 0.1f))) {

    return true;
  }

  return m.GetIntersectingHandler(point).get() != nullptr;
}

void
WidgetResizer::HandleResizeGestures(const vrb::Vector& aWorldPoint, bool aPressed, bool& aResized, bool &aResizeEnded) {
  const vrb::Vector point = m.ProjectPoint(aWorldPoint);
  for (const ResizeHandlePtr& handle: m.resizeHandles) {
    handle->SetResizeState(ResizeState::Default);
  }
  aResized = false;
  aResizeEnded = false;

  if (aPressed && !m.wasPressed) {
    // Handle resize handle click
    m.activeHandle = m.GetIntersectingHandler(point);
    if (m.activeHandle) {
      m.resizeStartMin = m.min;
      m.resizeStartMax = m.max;
      m.currentMin = m.min;
      m.currentMax = m.max;
      m.activeHandle->SetResizeState(ResizeState::Active);
      vrb::Vector center(m.min.x() + m.WorldWidth() * m.activeHandle->center.x(),
                         m.min.y() + m.WorldHeight() * m.activeHandle->center.y(), 0.0f);
      m.pointerOffset = point - center;
    }
  } else if (!aPressed && m.wasPressed) {
    // Handle resize handle unclick
    if (m.activeHandle) {
      m.activeHandle->SetResizeState(ResizeState::Hovered);
      m.min = m.currentMin;
      m.max = m.currentMax;
      aResizeEnded = true;
    }
    m.activeHandle.reset();
  } else if (aPressed && m.activeHandle) {
    // Handle resize gesture
    m.activeHandle->SetResizeState(ResizeState::Active);
    m.HandleResize(point);
    aResized = true;
  } else if (!aPressed) {
    // Handle hover
    ResizeHandlePtr handle = m.GetIntersectingHandler(point);
    if (handle) {
      handle->SetResizeState(ResizeState::Hovered);
    }
  }

  m.wasPressed = aPressed;
}

void
WidgetResizer::HoverExitResize() {
  for (const ResizeHandlePtr& handle: m.resizeHandles) {
    handle->SetResizeState(ResizeState::Default);
  }
  m.wasPressed = false;
}

const vrb::Vector&
WidgetResizer::GetResizeMin() const {
  return m.min;
}

const vrb::Vector&
WidgetResizer::GetResizeMax() const {
  return m.max;
}

bool
WidgetResizer::IsActive() const {
  return m.activeHandle && m.activeHandle->resizeState == ResizeState::Active;
}

void
WidgetResizer::SetTransform(const vrb::Matrix &aTransform){
  m.transform->SetTransform(aTransform);
}

Widget*
WidgetResizer::GetWidget() const {
  return m.widget;
}

WidgetResizer::WidgetResizer(State& aState, vrb::CreationContextPtr& aContext) : m(aState) {
  m.context = aContext;
}

} // namespace crow
