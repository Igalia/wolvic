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
#include "VRLayer.h"
#include "vrb/LoaderThread.h"

#include <memory>

namespace crow {

class DeviceDelegate;
typedef std::shared_ptr<DeviceDelegate> DeviceDelegatePtr;

class ImmersiveDisplay;
typedef std::shared_ptr<ImmersiveDisplay> ImmersiveDisplayPtr;

class VRLayer;
typedef std::shared_ptr<VRLayer> VRLayerPtr;
class VRLayerQuad;
typedef std::shared_ptr<VRLayerQuad> VRLayerQuadPtr;


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
  virtual void SetNativeFramebufferScaleFactor(const float aScale) = 0;
  virtual void SetStageSize(const float aWidth, const float aDepth) = 0;
  virtual void SetSittingToStandingTransform(const vrb::Matrix& aTransform) = 0;
  virtual void CompleteEnumeration() = 0;
};

class DeviceDelegate {
public:
  enum class FramePrediction {
      NO_FRAME_AHEAD,
      ONE_FRAME_AHEAD,
  };
  enum class FrameEndMode {
      APPLY,
      DISCARD
  };
  virtual device::DeviceType GetDeviceType() { return device::UnknownType; }
  virtual void SetRenderMode(const device::RenderMode aMode) = 0;
  virtual device::RenderMode GetRenderMode() = 0;
  virtual void RegisterImmersiveDisplay(ImmersiveDisplayPtr aDisplay) = 0;
  virtual void SetImmersiveSize(const uint32_t aEyeWidth, const uint32_t aEyeHeight) {};
  virtual GestureDelegateConstPtr GetGestureDelegate() = 0;
  virtual vrb::CameraPtr GetCamera(const device::Eye aWhich) = 0;
  virtual const vrb::Matrix& GetHeadTransform() const = 0;
  virtual const vrb::Matrix& GetReorientTransform() const = 0;
  virtual void SetReorientTransform(const vrb::Matrix& aMatrix) = 0;
  virtual void SetClearColor(const vrb::Color& aColor) = 0;
  virtual void SetClipPlanes(const float aNear, const float aFar) = 0;
  virtual void SetControllerDelegate(ControllerDelegatePtr& aController) = 0;
  virtual void ReleaseControllerDelegate() = 0;
  virtual int32_t GetControllerModelCount() const = 0;
  virtual const std::string GetControllerModelName(const int32_t aModelIndex) const { return nullptr; };
  virtual void SetCPULevel(const device::CPULevel aLevel) {};
  virtual void ProcessEvents() = 0;
  virtual bool SupportsFramePrediction(FramePrediction aPrediction) const {
    return aPrediction == FramePrediction::NO_FRAME_AHEAD;
  }
  virtual void StartFrame(const FramePrediction aPrediction = FramePrediction::NO_FRAME_AHEAD) = 0;
  virtual void BindEye(const device::Eye aWhich) = 0;
  virtual void EndFrame(const FrameEndMode aMode = FrameEndMode::APPLY) = 0;
  virtual bool IsInGazeMode() const { return false; };
  virtual int32_t GazeModeIndex() const { return -1; };
  virtual VRLayerQuadPtr CreateLayerQuad(int32_t aWidth, int32_t aHeight,
                                         VRLayerSurface::SurfaceType aSurfaceType) { return nullptr; }
  virtual VRLayerQuadPtr CreateLayerQuad(const VRLayerSurfacePtr& aMoveLayer) { return nullptr; }
  virtual VRLayerCylinderPtr CreateLayerCylinder(int32_t aWidth, int32_t aHeight,
                                                VRLayerSurface::SurfaceType aSurfaceType) { return nullptr; }
  virtual VRLayerCylinderPtr CreateLayerCylinder(const VRLayerSurfacePtr& aMoveLayer) { return nullptr; }
  virtual VRLayerProjectionPtr CreateLayerProjection(VRLayerSurface::SurfaceType aSurfaceType) { return nullptr; }
  virtual VRLayerCubePtr CreateLayerCube(int32_t aWidth, int32_t aHeight, GLint aInternalFormat) { return nullptr; }
  virtual VRLayerEquirectPtr CreateLayerEquirect(const VRLayerPtr &aSource) { return nullptr; }
  virtual void DeleteLayer(const VRLayerPtr& aLayer) {};
  virtual bool IsControllerLightEnabled() const { return true; }
  virtual vrb::LoadTask GetControllerModelTask(int32_t index) { return nullptr; } ;
  virtual void OnControllersReady(const std::function<void()>& callback) {
    callback();
  }
protected:
  DeviceDelegate() {}

  virtual ~DeviceDelegate() {}

private:
  VRB_NO_DEFAULTS(DeviceDelegate)
};

}

#endif //  DEVICE_DELEGATE_DOT_H
