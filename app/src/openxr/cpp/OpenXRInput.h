#pragma once

#include "vrb/Forward.h"
#include <EGL/egl.h>
#include "jni.h"
#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>

#include <array>

#include "ControllerDelegate.h"
#include "ElbowModel.h"

namespace crow {

class OpenXRInput;
typedef std::shared_ptr<OpenXRInput> OpenXRInputPtr;

class OpenXRInput {
public:
  enum Hand {
    Left = 0,
    Right = 1,
    Count = 2
  };
  struct ControllerState {
    bool enabled = false;
    bool created = false;
    XrSpace space = XR_NULL_HANDLE;
  };
  XrInstance instance = XR_NULL_HANDLE;
  XrActionSet actionSet = XR_NULL_HANDLE;
  std::array<XrAction , Hand::Count> actionPose;
  std::array<XrAction , Hand::Count> actionMenuClick;
  std::array<XrAction , Hand::Count> actionTriggerClick;
  std::array<XrAction , Hand::Count> actionTriggerTouch;
  std::array<XrAction , Hand::Count> actionTriggerValue;
  std::array<XrAction , Hand::Count> actionSqueezeClick;
  std::array<XrAction , Hand::Count> actionSqueezeValue;
  std::array<XrAction , Hand::Count> actionTrackpadClick;
  std::array<XrAction , Hand::Count> actionTrackpadTouch;
  std::array<XrAction , Hand::Count> actionTrackpadX;
  std::array<XrAction , Hand::Count> actionTrackpadY;
  std::array<XrAction , Hand::Count> actionThumbstickClick;
  std::array<XrAction , Hand::Count> actionThumbstickTouch;
  std::array<XrAction , Hand::Count> actionThumbstickX;
  std::array<XrAction , Hand::Count> actionThumbstickY;
  std::array<XrAction , Hand::Count> actionButtonAClick;
  std::array<XrAction , Hand::Count> actionButtonBClick;
  std::array<XrAction , Hand::Count> actionButtonATouch;
  std::array<XrAction , Hand::Count> actionButtonBTouch;

  std::array<XrPath, Hand::Count> handSubactionPath;
  std::array<ControllerState, Hand::Count> controllerState;
  XrSystemProperties systemProperties;

  static OpenXRInputPtr Create(XrInstance instance, XrSystemProperties systemProperties);
  void Initialize(XrSession session);
  void Update(XrSession session, XrTime predictedDisplayTime, XrSpace baseSpace, device::RenderMode renderMode, ControllerDelegatePtr& delegate);
  int32_t GetControllerModelCount() const;
  const std::string GetControllerModelName(const int32_t aModelIndex) const;
  void Destroy();

  ~OpenXRInput();
};

} // namespace crow