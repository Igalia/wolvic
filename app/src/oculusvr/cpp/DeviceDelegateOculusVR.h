/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef DEVICE_DELEGATE_OCULUS_VR_DOT_H
#define DEVICE_DELEGATE_OCULUS_VR_DOT_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"
#include "DeviceDelegate.h"
#include <memory>

class android_app;
namespace crow {

class BrowserEGLContext;

class DeviceDelegateOculusVR;
typedef std::shared_ptr<DeviceDelegateOculusVR> DeviceDelegateOculusVRPtr;

class DeviceDelegateOculusVR : public DeviceDelegate {
public:
  static DeviceDelegateOculusVRPtr Create(vrb::ContextWeak aContext, android_app* aApp);
  // DeviceDelegate interface
  GestureDelegateConstPtr GetGestureDelegate() override { return nullptr; }
  vrb::CameraPtr GetCamera(const CameraEnum aWhich) override;
  const vrb::Matrix& GetHeadTransform() const override;
  void SetClearColor(const vrb::Color& aColor) override;
  void SetClipPlanes(const float aNear, const float aFar) override;
  int32_t GetControllerCount() const override;
  const std::string GetControllerModelName(const int32_t aWhichContorller) const override;
  void ProcessEvents() override;
  const vrb::Matrix& GetControllerTransform(const int32_t aWhichController) override;
  bool GetControllerButtonState(const int32_t aWhichController, const int32_t aWhichButton, bool& aChangedState) override;
  void StartFrame() override;
  void BindEye(const CameraEnum aWhich) override;
  void EndFrame() override;
  // Custom methods for NativeActivity render loop based devices.
  void EnterVR(const crow::BrowserEGLContext& aEGLContext);
  void LeaveVR();
  bool IsInVRMode() const;
  bool ExitApp();
protected:
  struct State;
  DeviceDelegateOculusVR(State& aState);
  virtual ~DeviceDelegateOculusVR();
private:
  State& m;
  VRB_NO_DEFAULTS(DeviceDelegateOculusVR)
};

} // namespace crow

#endif // DEVICE_DELEGATE_OCULUS_VR_DOT_H
