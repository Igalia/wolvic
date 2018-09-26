/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef DEVICE_DELEGATE_DOT_H
#define DEVICE_DELEGATE_DOT_H

#include "vrb/MacroUtils.h"
#include "vrb/Forward.h"
#include "Device.h"
#include "ControllerDelegate.h"
#include "GestureDelegate.h"

#include <memory>

namespace crow {

class DeviceDelegate;
typedef std::shared_ptr<DeviceDelegate> DeviceDelegatePtr;

class ImmersiveDisplay;
typedef std::shared_ptr<ImmersiveDisplay> ImmersiveDisplayPtr;


class ImmersiveDisplay {
public:
  virtual void SetDeviceName(const std::string& aName) = 0;
  virtual void SetCapabilityFlags(const device::CapabilityFlags aFlags) = 0;
  virtual void SetFieldOfView(const device::Eye aEye, const double aLeftDegrees,
                      const double aRightDegrees,
                      const double aTopDegrees,
                      const double aBottomDegrees) = 0;
  virtual void SetEyeOffset(const device::Eye aEye, const float aX, const float aY, const float aZ) = 0;
  virtual void SetEyeResolution(const int32_t aWidth, const int32_t aHeight) = 0;
  virtual void CompleteEnumeration() = 0;
};

class DeviceDelegate {
public:
  virtual void SetRenderMode(const device::RenderMode aMode) = 0;
  virtual device::RenderMode GetRenderMode() = 0;
  virtual void RegisterImmersiveDisplay(ImmersiveDisplayPtr aDisplay) = 0;
  virtual GestureDelegateConstPtr GetGestureDelegate() = 0;
  virtual vrb::CameraPtr GetCamera(const device::Eye aWhich) = 0;
  virtual const vrb::Matrix& GetHeadTransform() const = 0;
  virtual const vrb::Matrix& GetReorientTransform() const = 0;
  virtual void SetClearColor(const vrb::Color& aColor) = 0;
  virtual void SetClipPlanes(const float aNear, const float aFar) = 0;
  virtual void SetControllerDelegate(ControllerDelegatePtr& aController) = 0;
  virtual void ReleaseControllerDelegate() = 0;
  virtual int32_t GetControllerModelCount() const = 0;
  virtual const std::string GetControllerModelName(const int32_t aModelIndex) const = 0;
  virtual void ProcessEvents() = 0;
  virtual void StartFrame() = 0;
  virtual void BindEye(const device::Eye aWhich) = 0;
  virtual void EndFrame(bool aDiscard = false) = 0;
protected:
  DeviceDelegate() {}

  virtual ~DeviceDelegate() {}

private:
  VRB_NO_DEFAULTS(DeviceDelegate)
};

}

#endif //  DEVICE_DELEGATE_DOT_H
