/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_VR_VIDEO_H
#define VRBROWSER_VR_VIDEO_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"
#include "Device.h"

#include <memory>
#include <string>
#include <vector>
#include <functional>

namespace crow {

class DeviceDelegate;
typedef std::shared_ptr<DeviceDelegate> DeviceDelegatePtr;

class VRVideo;
typedef std::shared_ptr<VRVideo> VRVideoPtr;

class Widget;
typedef std::shared_ptr<Widget> WidgetPtr;

class VRVideo {
public:
  // Should match the values in VideoProjectionMenuWidget.java
  enum class VRVideoProjection {
    VIDEO_PROJECTION_3D_SIDE_BY_SIDE = 0,
    VIDEO_PROJECTION_360 = 1,
    VIDEO_PROJECTION_360_STEREO = 2,
    VIDEO_PROJECTION_180 = 3,
    VIDEO_PROJECTION_180_STEREO_LEFT_RIGHT = 4,
    VIDEO_PROJECTION_180_STEREO_TOP_BOTTOM = 5,
  };
  static VRVideoPtr Create(vrb::CreationContextPtr aContext,
                           const WidgetPtr& aWindow,
                           const VRVideoProjection aProjection,
                           const DeviceDelegatePtr& aDevice);
  void SelectEye(device::Eye aEye);
  vrb::NodePtr GetRoot() const;
  void Exit();
  void SetReorientTransform(const vrb::Matrix& transform);
protected:
  struct State;
  VRVideo(State& aState, vrb::CreationContextPtr& aContext);
  ~VRVideo() = default;
private:
  State& m;
  VRVideo() = delete;
  VRB_NO_DEFAULTS(VRVideo)
};

} // namespace crow

#endif // VRBROWSER_VR_VIDEO_H
