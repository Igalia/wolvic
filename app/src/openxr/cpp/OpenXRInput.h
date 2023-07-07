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

class OpenXRActionSet;
typedef std::shared_ptr<OpenXRActionSet> OpenXRActionSetPtr;

struct HandMeshBuffer;
typedef std::shared_ptr<HandMeshBuffer> HandMeshBufferPtr;

class OpenXRInput {
private:
  OpenXRInput(XrInstance, XrSession, XrSystemProperties, ControllerDelegate& delegate);

  OpenXRInputMapping* GetActiveInputMapping() const;

  XrInstance mInstance { XR_NULL_HANDLE };
  XrSession mSession { XR_NULL_HANDLE };
  XrSystemProperties mSystemProperties;
  std::vector<OpenXRInputSourcePtr> mInputSources;
  OpenXRActionSetPtr mActionSet;

public:
  static OpenXRInputPtr Create(XrInstance, XrSession, XrSystemProperties, ControllerDelegate& delegate);
  XrResult Initialize(ControllerDelegate& delegate);
  XrResult Update(const XrFrameState& frameState, XrSpace baseSpace, const vrb::Matrix& head, const vrb::Vector& offsets, device::RenderMode renderMode, ControllerDelegate& delegate);
  int32_t GetControllerModelCount() const;
  std::string GetControllerModelName(const int32_t aModelIndex) const;
  void UpdateInteractionProfile(ControllerDelegate&, const char* emulateProfile = nullptr);
  bool AreControllersReady() const;
  void SetHandMeshBufferSizes(const uint32_t indexCount, const uint32_t vertexCount);
  HandMeshBufferPtr GetNextHandMeshBuffer(const int32_t aControllerIndex);
  ~OpenXRInput();
};

} // namespace crow