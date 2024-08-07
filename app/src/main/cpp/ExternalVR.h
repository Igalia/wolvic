/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_EXTERNALVR_H
#define VRBROWSER_EXTERNALVR_H

#include "vrb/MacroUtils.h"
#include "Controller.h"
#include "ControllerContainer.h"
#include "DeviceDelegate.h"
#include "Device.h"
#include <memory>
#include <string>
#include <vector>

namespace mozilla { namespace gfx { struct VRExternalShmem; } }

namespace crow {

class ExternalVR;
typedef std::shared_ptr<ExternalVR> ExternalVRPtr;

class ExternalVR : public ImmersiveDisplay {
public:
  enum class VRState {
    NotPresenting,
    Loading,
    LinkTraversal,
    Rendering
  };
  enum class VRBrowserType {
    Gecko,
    Servo
  };
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
  void SetEyeTransform(const device::Eye aEye, const vrb::Matrix& aTransform) override;
  void SetNativeFramebufferScaleFactor(const float aScale) override;
  void SetStageSize(const float aWidth, const float aDepth) override;
  void SetSittingToStandingTransform(const vrb::Matrix& aTransform) override;
  void CompleteEnumeration() override;
  void SetBlendModes(std::vector<device::BlendMode>) override;
  // ExternalVR interface
  void PushSystemState();
  void PullBrowserState();
  void SetCompositorEnabled(bool aEnabled);
  bool IsPresenting() const;
  VRState GetVRState() const;
  void PushFramePoses(const vrb::Matrix& aHeadTransform, const std::vector<Controller>& aControllers, const double aTimestamp);
  bool WaitFrameResult();
  void GetFrameResult(int32_t& aSurfaceHandle,
                      int32_t& aTextureWidth,
                      int32_t& aTextureHeight,
                      device::EyeRect& aLeftEye,
                      device::EyeRect& aRightEye) const;
  void SetHapticState(ControllerContainerPtr aControllerContainer) const;
  void StopPresenting();
  void SetSourceBrowser(VRBrowserType aBrowser);
  void OnPause();
  void OnResume();
  uint64_t GetFrameId() const;
  device::BlendMode GetImmersiveBlendMode() const;
  DeviceDelegate::ImmersiveXRSessionType GetImmersiveXRSessionType() const;
  ExternalVR();
  ~ExternalVR() = default;
protected:
  struct State;
private:
  uint16_t GetControllerCapabilityFlags(device::CapabilityFlags aFlags);

  State& m;
  VRB_NO_DEFAULTS(ExternalVR)
};

}

#endif //VRBROWSER_EXTERNALVR_H
