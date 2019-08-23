/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_WIDGET_BORDER_DOT_H
#define VRBROWSER_WIDGET_BORDER_DOT_H

#include "Device.h"
#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"

#include <memory>
#include <string>
#include <vector>
#include <functional>

namespace crow {

class Cylinder;
typedef std::shared_ptr<Cylinder> CylinderPtr;

class Widget;

class WidgetBorder;
typedef std::shared_ptr<WidgetBorder> WidgetBorderPtr;

class WidgetBorder {
public:
  enum class Mode {
    Quad,
    Cylinder
  };

  static WidgetBorderPtr Create(vrb::CreationContextPtr& aContext, const vrb::Vector& aBarSize,
                                const float aBorderSize, const device::EyeRect& aBorderRect,
                                const WidgetBorder::Mode aMode);
  void SetColor(const vrb::Color& aColor);
  const vrb::TransformPtr& GetTransformNode() const;
  const CylinderPtr& GetCylinder() const;
  static std::vector<WidgetBorderPtr> CreateFrame(vrb::CreationContextPtr& aContext, const Widget& aTarget,
                                                  const float aFrameSize, const float aBorderSize);
protected:
  struct State;
  WidgetBorder(State& aState, vrb::CreationContextPtr& aContext);
  ~WidgetBorder() = default;
private:
  State& m;
  WidgetBorder() = delete;
  VRB_NO_DEFAULTS(WidgetBorder)
};

} // namespace crow

#endif // VRBROWSER_WIDGET_BORDER_DOT_H
