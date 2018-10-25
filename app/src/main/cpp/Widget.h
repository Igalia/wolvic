/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_WIDGET_DOT_H
#define VRBROWSER_WIDGET_DOT_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"
#include "vrb/Color.h"

#include <memory>
#include <string>
#include <vector>
#include <functional>

namespace crow {

class Quad;
typedef std::shared_ptr<Quad> QuadPtr;

class Widget;
typedef std::shared_ptr<Widget> WidgetPtr;

class WidgetPlacement;
typedef std::shared_ptr<WidgetPlacement> WidgetPlacementPtr;

class Widget {
public:
  static WidgetPtr Create(vrb::RenderContextPtr& aContext, const int aHandle, const int32_t aWidth, const int32_t aHeight, float aWorldWidth);
  static WidgetPtr Create(vrb::RenderContextPtr& aContext, const int aHandle, const int32_t aWidth, const int32_t aHeight, const vrb::Vector& aMin, const vrb::Vector& aMax);
  uint32_t GetHandle() const;
  void ResetFirstDraw();
  const std::string& GetSurfaceTextureName() const;
  void GetSurfaceTextureSize(int32_t& aWidth, int32_t& aHeight) const;
  void SetSurfaceTextureSize(int32_t aWidth, int32_t aHeight);
  void GetWidgetMinAndMax(vrb::Vector& aMin, vrb::Vector& aMax) const;
  void SetWorldWidth(float aWorldWidth) const;
  void GetWorldSize(float& aWidth, float& aHeight) const;
  bool TestControllerIntersection(const vrb::Vector& aStartPoint, const vrb::Vector& aDirection, vrb::Vector& aResult, bool& aIsInWidget, float& aDistance) const;
  void ConvertToWidgetCoordinates(const vrb::Vector& aPoint, float& aX, float& aY) const;
  void ConvertToWorldCoordinates(const vrb::Vector& aPoint, vrb::Vector& aResult) const;
  const vrb::Matrix GetTransform() const;
  void SetTransform(const vrb::Matrix& aTransform);
  void ToggleWidget(const bool aEnabled);
  void TogglePointer(const bool aEnabled);
  bool IsVisible() const;
  vrb::NodePtr GetRoot() const;
  QuadPtr GetQuad() const;
  vrb::TransformPtr GetTransformNode() const;
  vrb::NodePtr GetPointerGeometry() const;
  void SetPointerGeometry(vrb::NodePtr& aNode);
  const WidgetPlacementPtr& GetPlacement() const;
  void SetPlacement(const WidgetPlacementPtr& aPlacement);
  void StartResize();
  void FinishResize();
  bool IsResizing() const;
  void HandleResize(const vrb::Vector& aPoint, bool aPressed, bool& aResized, bool &aResizeEnded);
  void HoverExitResize();
  void SetPointerColor(const vrb::Color& aColor);
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
