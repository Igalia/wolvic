/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_EXTERNALVR_H
#define VRBROWSER_EXTERNALVR_H

#include "vrb/MacroUtils.h"
#include "DeviceDelegate.h"
#include "Device.h"
#include <memory>
#include <string>

namespace mozilla { namespace gfx { struct VRExternalShmem; } }

namespace crow {

class ExternalVR;
typedef std::shared_ptr<ExternalVR> ExternalVRPtr;

class ExternalVR : public ImmersiveDisplay {
public:
  static ExternalVRPtr Create();
  mozilla::gfx::VRExternalShmem* GetSharedData();
  // DeviceDisplay interface
  void SetDeviceName(const std::string& aName) override;
  void SetCapabilityFlags(const device::CapabilityFlags aFlags) override;
  void SetFieldOfView(const device::Eye aEye, const double aLeftDegrees,
                      const double aRightDegrees,
                      const double aTopDegrees,
                      const double aBottomDegrees) override;
  void SetEyeOffset(const device::Eye aEye, const float aX, const float aY, const float aZ) override;
  void SetEyeResolution(const int32_t aX, const int32_t aY) override;
  // ExternalVR interface
  void PushSystemState();
  void PullBrowserState(bool aBlock = true);
  bool IsFirstPresentingFrame() const;
  bool IsPresenting() const;
  void RequestFrame(const vrb::Matrix& aHeadTransform);
  void GetFrameResult(int32_t& aSurfaceHandle, device::EyeRect& aLeftEye, device::EyeRect& aRightEye) const;
  void StopPresenting();
  void CompleteEnumeration() override;
protected:
  struct State;
  ExternalVR(State& aState);
  ~ExternalVR();
private:
  State& m;
  ExternalVR() = delete;
  VRB_NO_DEFAULTS(ExternalVR)
};

}

#endif //VRBROWSER_EXTERNALVR_H
