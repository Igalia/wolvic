#pragma once

#include "vrb/Forward.h"
#include "OpenXRHelpers.h"
#include "ControllerDelegate.h"
#include <vector>

namespace crow {

class OpenXRInputSource;
typedef std::shared_ptr<OpenXRInputSource> OpenXRInputSourcePtr;

class OpenXRInput;
typedef std::shared_ptr<OpenXRInput> OpenXRInputPtr;

class OpenXRInputMapping;

class OpenXRInput {
private:
  OpenXRInput(XrInstance, XrSession, XrSystemProperties, ControllerDelegate& delegate);

  OpenXRInputMapping* GetActiveInputMapping() const;

  XrInstance mInstance { XR_NULL_HANDLE };
  XrSession mSession { XR_NULL_HANDLE };
  XrSystemProperties mSystemProperties;
  std::vector<OpenXRInputSourcePtr> mInputSources;

public:
  static OpenXRInputPtr Create(XrInstance, XrSession, XrSystemProperties, ControllerDelegate& delegate);
  XrResult Initialize(ControllerDelegate& delegate);
  XrResult Update(const XrFrameState& frameState, XrSpace baseSpace, const vrb::Matrix& head, device::RenderMode renderMode, ControllerDelegate& delegate);
  int32_t GetControllerModelCount() const;
  std::string GetControllerModelName(const int32_t aModelIndex) const;
  void UpdateInteractionProfile();
  bool AreControllersReady() const;

  ~OpenXRInput();
};

} // namespace crow