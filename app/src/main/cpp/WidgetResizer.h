/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_WIDGET_RESIZER_DOT_H
#define VRBROWSER_WIDGET_RESIZER_DOT_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"

#include <memory>
#include <string>
#include <vector>
#include <functional>

namespace crow {

class Widget;
class WidgetResizer;
typedef std::shared_ptr<WidgetResizer> WidgetResizerPtr;

class WidgetResizer {
public:
  static WidgetResizerPtr Create(vrb::CreationContextPtr& aContext, Widget* aWidget);
  vrb::NodePtr GetRoot() const;
  void SetSize(const vrb::Vector& aMin, const vrb::Vector& aMax);
  void SetResizeLimits(const vrb::Vector& aMaxSize, const vrb::Vector& aMinSize);
  void ToggleVisible(bool aVisible);
  bool TestIntersection(const vrb::Vector& point) const;
  void HandleResizeGestures(const vrb::Vector& aPoint, bool aPressed, bool& aResized, bool &aResizeEnded);
  void HoverExitResize();
  const vrb::Vector& GetResizeMin() const;
  const vrb::Vector& GetResizeMax() const;
  bool IsActive() const;
  Widget* GetWidget() const;
  void SetTransform(const vrb::Matrix& aTransform);
protected:
  struct State;
  WidgetResizer(State& aState, vrb::CreationContextPtr& aContext);
  ~WidgetResizer() = default;
private:
  State& m;
  WidgetResizer() = delete;
  VRB_NO_DEFAULTS(WidgetResizer)
};

} // namespace crow

#endif // VRBROWSER_WIDGET_RESIZER_DOT_H
