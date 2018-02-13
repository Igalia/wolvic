#ifndef DEVICE_DELEGATE_WAVE_VR_DOT_H
#define DEVICE_DELEGATE_WAVE_VR_DOT_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"
#include "DeviceDelegate.h"
#include <memory>

class DeviceDelegateWaveVR;
typedef std::shared_ptr<DeviceDelegateWaveVR> DeviceDelegateWaveVRPtr;

class DeviceDelegateWaveVR : public DeviceDelegate {
public:
  static DeviceDelegateWaveVRPtr Create(vrb::ContextWeak aContext);
  // DeviceDelegate interface
  vrb::CameraPtr GetCamera(const CameraEnum aWhich) override;
  void SetClipPlanes(const float aNear, const float aFar) override;
  int32_t GetControllerCount() const override;
  const std::string GetControllerModelName(const int32_t aWhichContorller) const override;
  void ProcessEvents() override;
  const vrb::Matrix& GetControllerTransform(const int32_t aWhichController) override;
  void StartFrame() override;
  void BindEye(const CameraEnum aWhich) override;
  void EndFrame() override;
  // DeviceDelegateWaveVR interface
  bool IsRunning();
protected:
  struct State;
  DeviceDelegateWaveVR(State& aState);
  virtual ~DeviceDelegateWaveVR();
private:
  State& m;
  VRB_NO_DEFAULTS(DeviceDelegateWaveVR)
};

#endif // DEVICE_DELEGATE_WAVE_VR_DOT_H
