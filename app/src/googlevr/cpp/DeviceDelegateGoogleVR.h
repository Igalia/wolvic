/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef DEVICE_DELEGATE_GOOGLE_VR_DOT_H
#define DEVICE_DELEGATE_GOOGLE_VR_DOT_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"
#include "DeviceDelegate.h"
#include <memory>

namespace crow {

class DeviceDelegateGoogleVR;
typedef std::shared_ptr<DeviceDelegateGoogleVR> DeviceDelegateGoogleVRPtr;

class DeviceDelegateGoogleVR : public DeviceDelegate {
public:
  static DeviceDelegateGoogleVRPtr Create(vrb::ContextWeak aContext, void* aGVRContext);
  // DeviceDelegate interface
  GestureDelegateConstPtr GetGestureDelegate() override;
  vrb::CameraPtr GetCamera(const CameraEnum aWhich) override;
  void SetClearColor(const vrb::Color& aColor) override;
  void SetClipPlanes(const float aNear, const float aFar) override;
  int32_t GetControllerCount() const override;
  const std::string GetControllerModelName(const int32_t aWhichController) const override;
  void ProcessEvents() override;
  const vrb::Matrix& GetControllerTransform(const int32_t aWhichController) override;
  bool GetControllerButtonState(const int32_t aWhichController, const int32_t aWhichButton, bool& aChangedState) override;
  void StartFrame() override;
  void BindEye(const CameraEnum aWhich) override;
  void EndFrame() override;
  // DeviceDelegateGoogleVR interface
  void InitializeGL();
  void Pause();
  void Resume();
protected:
  struct State;
  DeviceDelegateGoogleVR(State& aState);
  virtual ~DeviceDelegateGoogleVR();
private:
  State& m;
  VRB_NO_DEFAULTS(DeviceDelegateGoogleVR)
};

} // namespace crow
#endif // DEVICE_DELEGATE_GOOGLE_VR_DOT_H
