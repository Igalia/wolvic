#ifndef DEVICE_DELEGATE_WAVE_VR_DOT_H
#define DEVICE_DELEGATE_WAVE_VR_DOT_H

#include "vrb/MacroUtils.h"
#include "DeviceDelegate.h"

class DeviceDelegateWaveVR : public DeviceDelegate {
public:
  CameraPtr GetCamera(const CameraEnum aWhich) override;
  void ProcessEvents() override;
  void BindEye(const CameraEnum aWhich) override;
  void UnbindEye(const CameraEnum aWhich) override;
  void SubmitFrame() override;
};

#endif // DEVICE_DELEGATE_WAVE_VR_DOT_H
