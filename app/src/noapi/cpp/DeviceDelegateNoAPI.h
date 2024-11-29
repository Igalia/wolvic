/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef DEVICE_DELEGATE_NO_API_DOT_H
#define DEVICE_DELEGATE_NO_API_DOT_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"
#include "DeviceDelegate.h"

#include <jni.h>
#include <memory>

namespace crow {

class DeviceDelegateNoAPI;
typedef std::shared_ptr<DeviceDelegateNoAPI> DeviceDelegateNoAPIPtr;

class DeviceDelegateNoAPI : public DeviceDelegate {
public:
  static DeviceDelegateNoAPIPtr Create(vrb::RenderContextPtr& aContext);
  // DeviceDelegate interface
  void SetRenderMode(const device::RenderMode aMode) override;
  device::RenderMode GetRenderMode() override;
  void RegisterImmersiveDisplay(ImmersiveDisplayPtr aDisplay) override;
  GestureDelegateConstPtr GetGestureDelegate() override;
  vrb::CameraPtr GetCamera(const device::Eye) override;
  const vrb::Matrix& GetHeadTransform() const override;
  const vrb::Matrix& GetReorientTransform() const override;
  void SetReorientTransform(const vrb::Matrix& aMatrix) override;
  void Reorient(vrb::Matrix&) override;
  void SetClearColor(const vrb::Color& aColor) override;
  void SetClipPlanes(const float aNear, const float aFar) override;
  void SetControllerDelegate(ControllerDelegatePtr& aController) override;
  void ReleaseControllerDelegate() override;
  int32_t GetControllerModelCount() const override;
  const std::string GetControllerModelName(const int32_t aModelIndex) const override;
  void ProcessEvents() override;
  void StartFrame(const FramePrediction aPrediction) override;
  void BindEye(const device::Eye) override;
  void EndFrame(const FrameEndMode aMode) override;
  // DeviceDelegateNoAPI interface
  void InitializeJava(JNIEnv* aEnv, jobject aActivity);
  void ShutdownJava();
  void SetViewport(const int aWidth, const int aHeight);
  void Pause();
  void Resume();
  void MoveAxis(const float aX, const float aY, const float aZ);
  void RotateHeading(const float aHeading);
  void RotatePitch(const float aPitch);
  void TouchEvent(const bool aDown, const float aX, const float aY);
  void ControllerButtonPressed(const bool aDown);
protected:
  struct State;
  DeviceDelegateNoAPI(State& aState);
  virtual ~DeviceDelegateNoAPI();
private:
  State& m;
  VRB_NO_DEFAULTS(DeviceDelegateNoAPI)
};

} // namespace crow
#endif // DEVICE_DELEGATE_NO_API_DOT_H
