/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef DEVICE_DELEGATE_OCULUS_VR_DOT_H
#define DEVICE_DELEGATE_OCULUS_VR_DOT_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"
#include "DeviceDelegate.h"
#include "JNIUtil.h"
#include <memory>

class android_app;
namespace crow {

class BrowserEGLContext;

class DeviceDelegateOculusVR;
typedef std::shared_ptr<DeviceDelegateOculusVR> DeviceDelegateOculusVRPtr;

class DeviceDelegateOculusVR : public DeviceDelegate {
public:
  static DeviceDelegateOculusVRPtr Create(vrb::RenderContextPtr& aContext, JavaContext* aJavaContext);
  // DeviceDelegate interface
  device::DeviceType GetDeviceType() override;
  void SetRenderMode(const device::RenderMode aMode) override;
  device::RenderMode GetRenderMode() override;
  void RegisterImmersiveDisplay(ImmersiveDisplayPtr aDisplay) override;
  void SetImmersiveSize(const uint32_t aEyeWidth, const uint32_t aEyeHeight) override;
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
  void SetCPULevel(const device::CPULevel aLevel) override;
  void ProcessEvents() override;
  bool SupportsFramePrediction(FramePrediction aPrediction) const override;
  void StartFrame(const FramePrediction aPrediction) override;
  void BindEye(const device::Eye aWhich) override;
  void EndFrame(const FrameEndMode aMode) override;
  VRLayerQuadPtr CreateLayerQuad(int32_t aWidth, int32_t aHeight,
                                 VRLayerSurface::SurfaceType aSurfaceType) override;
  VRLayerQuadPtr CreateLayerQuad(const VRLayerSurfacePtr& aMoveLayer) override;
  VRLayerCylinderPtr CreateLayerCylinder(int32_t aWidth, int32_t aHeight,
                                         VRLayerSurface::SurfaceType aSurfaceType) override;
  VRLayerCylinderPtr CreateLayerCylinder(const VRLayerSurfacePtr& aMoveLayer) override;
  VRLayerProjectionPtr CreateLayerProjection(VRLayerSurface::SurfaceType aSurfaceType) override;
  VRLayerCubePtr CreateLayerCube(int32_t aWidth, int32_t aHeight, GLint aInternalFormat) override;
  VRLayerEquirectPtr CreateLayerEquirect(const VRLayerPtr &aSource) override;
  void DeleteLayer(const VRLayerPtr& aLayer) override;
  // Custom methods for NativeActivity render loop based devices.
  void EnterVR(const crow::BrowserEGLContext& aEGLContext);
  void LeaveVR();
  void OnDestroy();
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
