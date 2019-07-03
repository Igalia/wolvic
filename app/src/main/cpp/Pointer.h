/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_POINTER_H
#define VRBROWSER_POINTER_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"

namespace crow {

class DeviceDelegate;
typedef std::shared_ptr<DeviceDelegate> DeviceDelegatePtr;

class Pointer;
typedef std::shared_ptr<Pointer> PointerPtr;

class Widget;
typedef std::shared_ptr<Widget> WidgetPtr;

class Pointer {
public:
  static PointerPtr Create(vrb::CreationContextPtr aContext);
  void Load(const DeviceDelegatePtr& aDevice);
  bool IsLoaded() const;
  void SetVisible(bool aVisible);
  void SetTransform(const vrb::Matrix& aTransform);
  void SetScale(const vrb::Vector& aHitPoint, const vrb::Matrix& aHeadTransform);
  void SetPointerColor(const vrb::Color& aColor);
  void SetHitWidget(const WidgetPtr& aWidget);

  vrb::NodePtr GetRoot() const;
  const WidgetPtr& GetHitWidget() const;
protected:
  struct State;
  Pointer(State& aState, vrb::CreationContextPtr& aContext);
  ~Pointer() = default;
private:
  State& m;
  Pointer() = delete;
  VRB_NO_DEFAULTS(Pointer)
};

} // namespace crow

#endif // VRBROWSER_POINTER_H
