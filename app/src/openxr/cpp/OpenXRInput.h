#pragma once

#include "vrb/Forward.h"
#include "OpenXRHelpers.h"
#include "DeviceDelegate.h"
#include "ControllerDelegate.h"
#include "OneEuroFilter.h"
#include "DeviceDelegate.h"
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
  struct KeyboardTrackingFB {
    XrKeyboardTrackingDescriptionFB description;
    XrSpace space = XR_NULL_HANDLE;
    XrSpaceLocation location;
    std::vector<uint8_t> modelBuffer;
    bool modelBufferChanged;
    ~KeyboardTrackingFB() {
      if (space != XR_NULL_HANDLE)
        xrDestroySpace(space);
      modelBuffer.clear();
    }
  };
  typedef std::unique_ptr<KeyboardTrackingFB> KeyboardTrackingFBPtr;

  OpenXRInput(XrInstance, XrSession, XrSystemProperties, XrSpace localSpace);
  void UpdateTrackedKeyboard(const XrFrameState& frameState, XrSpace baseSpace);
  void LoadKeyboardModel();

  OpenXRInputMapping* GetActiveInputMapping() const;

  XrInstance mInstance { XR_NULL_HANDLE };
  XrSession mSession { XR_NULL_HANDLE };
  XrSystemProperties mSystemProperties;
  std::vector<OpenXRInputSourcePtr> mInputSources;
  OpenXRActionSetPtr mActionSet;
  KeyboardTrackingFBPtr keyboardTrackingFB { nullptr };
  XrAction mUserIntentAction { XR_NULL_HANDLE };
  XrSpace mEyeGazeActionSpace {XR_NULL_HANDLE };
  XrSpace mLocalReferenceSpace { XR_NULL_HANDLE};
  std::unique_ptr<OneEuroFilterQuaternion> mOneEuroFilterGazeOrientation;
  vrb::Matrix mEyeTrackingTransform;

public:
  static OpenXRInputPtr Create(XrInstance, XrSession, XrSystemProperties, XrSpace localSpace,
                               bool isEyeTrackingSupported, ControllerDelegate &delegate);
  XrResult Initialize(ControllerDelegate &delegate, bool isEyeTrackingSupported);
  XrResult Update(const XrFrameState&, XrSpace baseSpace, const vrb::Matrix& head, const vrb::Vector& offsets, device::RenderMode, DeviceDelegate::PointerMode, bool handTrackingEnabled, ControllerDelegate &);
  int32_t GetControllerModelCount() const;
  std::string GetControllerModelName(const int32_t aModelIndex) const;
  void UpdateInteractionProfile(ControllerDelegate&);
  bool AreControllersReady() const;
  bool HasPhysicalControllersAvailable() const;
  void SetHandMeshBufferSizes(const uint32_t indexCount, const uint32_t vertexCount);
  HandMeshBufferPtr GetNextHandMeshBuffer(const int32_t aControllerIndex);
  void SetKeyboardTrackingEnabled(bool enabled);
  bool PopulateTrackedKeyboardInfo(DeviceDelegate::TrackedKeyboardInfo& keyboardInfo);
  ~OpenXRInput();
  void InitializeEyeGaze(ControllerDelegate&);
  void InitializeEyeGazeSpaces();
  bool updateEyeGaze(XrFrameState, const vrb::Matrix& head, ControllerDelegate&);
};

} // namespace crow