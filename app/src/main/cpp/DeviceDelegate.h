#ifndef DEVICE_DELEGATE_DOT_H
#define DEVICE_DELEGATE_DOT_H

#include "vrb/MacroUtils.h"

#include <memory>

class DeviceDelegate;
typedef std::shared_ptr<DeviceDelegate> DeviceDelegatePtr;

class DeviceDelegate {
public:
  enum class CameraEnum { Left, Right };
  virtual CameraPtr GetCamera(const CameraEnum aWhich) = 0;
  virtual void BindEye(const CameraEnum aWhich) = 0;
  virtual void UnbindEye(const CameraEnum aWhich) = 0;
  virtual void ProcessEvents() = 0;
  virtual void SubmitFrame() = 0;
protected:
  DeviceDelegate() {}
  ~DeviceDelegate() {}
private:
  VRB_NO_DEFAULTS(DeviceDelegate)
};

#endif //  DEVICE_DELEGATE_DOT_H
