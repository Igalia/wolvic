/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef DEVICE_DELEGATE_PICOVR_DOT_H
#define DEVICE_DELEGATE_PICOVR_DOT_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"
#include "vrb/Quaternion.h"
#include "vrb/Vector.h"
#include "DeviceDelegate.h"
#include <memory>

namespace crow {

class DeviceDelegatePicoVR;
typedef std::shared_ptr<DeviceDelegatePicoVR> DeviceDelegatePicoVRPtr;

class DeviceDelegatePicoVR : public DeviceDelegate {
public:
  static DeviceDelegatePicoVRPtr Create(vrb::RenderContextPtr& aContext);
  // DeviceDelegate interface
  device::DeviceType GetDeviceType() override;
  void SetRenderMode(const device::RenderMode aMode) override;
  device::RenderMode GetRenderMode() override;
  void RegisterImmersiveDisplay(ImmersiveDisplayPtr aDisplay) override;
  GestureDelegateConstPtr GetGestureDelegate() override { return nullptr; }
  vrb::CameraPtr GetCamera(const device::Eye aWhich) override;
  const vrb::Matrix& GetHeadTransform() const override;
  const vrb::Matrix& GetReorientTransform() const override;
  void SetReorientTransform(const vrb::Matrix& aMatrix) override;
  void SetClearColor(const vrb::Color& aColor) override;
  void SetClipPlanes(const float aNear, const float aFar) override;
  void SetControllerDelegate(ControllerDelegatePtr& aController) override;
  void ReleaseControllerDelegate() override;
  int32_t GetControllerModelCount() const override;
  const std::string GetControllerModelName(const int32_t aModelIndex) const override;
  void ProcessEvents() override;
  void StartFrame(const FramePrediction aPrediction) override;
  void BindEye(const device::Eye aWhich) override;
  void EndFrame(const FrameEndMode aMode) override;
  bool IsInGazeMode() const override;
  int32_t GazeModeIndex() const override;
  bool IsControllerLightEnabled() const override;
  // Custom methods
  void Pause();
  void Resume();
  void SetFocused(const int aIndex);
  void SetType(int aType);
  void SetRenderSize(const int32_t aWidth, const int32_t aHeight);
  void UpdateIpd(const float aIPD);
  void UpdateFov(const float aFov);
  void UpdatePosition(const vrb::Vector& aPosition);
  void UpdateOrientation(const vrb::Quaternion& aOrientation);
  void UpdateControllerConnected(const int aIndex, const bool aConnected);
  void UpdateControllerPose(const int aIndex, const bool a6Dof, const vrb::Vector& aPosition, const vrb::Quaternion& aRotation);
  void UpdateControllerButtons(const int aIndex, const int32_t aButtonsState, const float aGrip, const float axisX, const float axisY, const bool touched);
  void UpdateControllerBatteryLevel(const int aIndex, const int aBatteryLevel);
  void Recenter();
protected:
  struct State;
  DeviceDelegatePicoVR(State& aState);
  virtual ~DeviceDelegatePicoVR();
private:
  State& m;
  VRB_NO_DEFAULTS(DeviceDelegatePicoVR)
};

} // namespace crow

#endif // DEVICE_DELEGATE_SVR_DOT_H
