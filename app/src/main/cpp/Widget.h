/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_WIDGET_DOT_H
#define VRBROWSER_WIDGET_DOT_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"
#include "vrb/Color.h"
#include "DeviceDelegate.h"

#include <memory>
#include <string>
#include <vector>
#include <functional>

namespace crow {

class Cylinder;
typedef std::shared_ptr<Cylinder> CylinderPtr;

class VRLayer;
typedef std::shared_ptr<VRLayer> VRLayerPtr;

class Quad;
typedef std::shared_ptr<Quad> QuadPtr;

class Widget;
typedef std::shared_ptr<Widget> WidgetPtr;

class WidgetPlacement;
typedef std::shared_ptr<WidgetPlacement> WidgetPlacementPtr;

class Widget {
public:
  static WidgetPtr Create(vrb::RenderContextPtr& aContext, const int aHandle,
                          const int32_t aTextureWidth, const int32_t aTextureHeight, const QuadPtr& aQuad);
  static WidgetPtr Create(vrb::RenderContextPtr& aContext, const int aHandle, const float aWorldWidth, const float aWorldHeight,
                          const int32_t aTextureWidth, const int32_t aTextureHeight, const CylinderPtr& aCylinder);
  uint32_t GetHandle() const;
  void ResetFirstDraw();
  const std::string& GetSurfaceTextureName() const;
  const vrb::TextureSurfacePtr GetSurfaceTexture() const;
  void GetSurfaceTextureSize(int32_t& aWidth, int32_t& aHeight) const;
  void SetSurfaceTextureSize(int32_t aWidth, int32_t aHeight);
  void GetWidgetMinAndMax(vrb::Vector& aMin, vrb::Vector& aMax) const;
  void SetWorldWidth(float aWorldWidth) const;
  void GetWorldSize(float& aWidth, float& aHeight) const;
  bool TestControllerIntersection(const vrb::Vector& aStartPoint, const vrb::Vector& aDirection, vrb::Vector& aResult, vrb::Vector& aNormal, bool& aIsInWidget, float& aDistance) const;
  void ConvertToWidgetCoordinates(const vrb::Vector& aPoint, float& aX, float& aY) const;
  void ConvertToWorldCoordinates(const vrb::Vector& aPoint, vrb::Vector& aResult) const;
  const vrb::Matrix GetTransform() const;
  void SetTransform(const vrb::Matrix& aTransform);
  void ToggleWidget(const bool aEnabled);
  bool IsVisible() const;
  vrb::NodePtr GetRoot() const;
  QuadPtr GetQuad() const;
  CylinderPtr GetCylinder() const;
  VRLayerSurfacePtr GetLayer() const;
  vrb::TransformPtr GetTransformNode() const;
  const WidgetPlacementPtr& GetPlacement() const;
  void SetPlacement(const WidgetPlacementPtr& aPlacement);
  void StartResize();
  void FinishResize();
  bool IsResizing() const;
  void HandleResize(const vrb::Vector& aPoint, bool aPressed, bool& aResized, bool &aResizeEnded);
  void HoverExitResize();
  void SetCylinderDensity(const float aDensity);
protected:
  struct State;
  Widget(State& aState, vrb::RenderContextPtr& aContext);
  ~Widget();
private:
  State& m;
  Widget() = delete;
  VRB_NO_DEFAULTS(Widget)
};

} // namespace crow

#endif // VRBROWSER_WIDGET_DOT_H
