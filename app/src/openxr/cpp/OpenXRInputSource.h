#pragma once

#include "vrb/Forward.h"
#include "OpenXRInputMappings.h"
#include "OpenXRHelpers.h"
#include "OpenXRActionSet.h"
#include "ElbowModel.h"
#include "HandMeshRenderer.h"
#include "OpenXRGestureManager.h"
#include <optional>
#include <unordered_map>

namespace crow {

class OpenXRInputSource;
typedef std::shared_ptr<OpenXRInputSource> OpenXRInputSourcePtr;

class OpenXRInputSource {
public:
    using SuggestedBindings = std::unordered_map<std::string, std::vector<XrActionSuggestedBinding>>;
    XrResult GetActionState(XrAction, bool*) const;
    XrResult GetActionState(XrAction, float*) const;
    XrResult GetActionState(XrAction, XrVector2f*) const;

private:
    OpenXRInputSource(XrInstance, XrSession, OpenXRActionSet&, const XrSystemProperties&, OpenXRHandFlags, int index);

    XrResult Initialize();
    XrResult CreateActionSpace(XrAction, XrSpace&) const;
    XrResult CreateBinding(const char* profilePath, XrAction, const std::string& bindingPath, SuggestedBindings&) const;

    XrResult GetPoseState(XrAction, XrSpace, XrSpace, const XrFrameState&, bool& isActive, XrSpaceLocation&) const;

    struct OpenXRButtonState {
      bool clicked { false };
      bool touched { false };
      float value { 0 };
      bool ready { false };
    };
    std::optional<OpenXRButtonState> GetButtonState(const OpenXRButton&) const;
    std::optional<XrVector2f> GetAxis(OpenXRAxisType) const;
    ControllerDelegate::Button GetBrowserButton(const OpenXRButton&) const;
    std::optional<uint8_t> GetImmersiveButton(const OpenXRButton&) const;
    XrResult applyHapticFeedback(XrAction, XrDuration, float = XR_FREQUENCY_UNSPECIFIED, float = 0.0) const;
    XrResult stopHapticFeedback(XrAction) const;
    void UpdateHaptics(ControllerDelegate&);
    bool GetHandTrackingInfo(XrTime predictedDisplayTime, XrSpace, const vrb::Matrix& head);
    float GetDistanceBetweenJoints (XrHandJointEXT jointA, XrHandJointEXT jointB);
    void EmulateControllerFromHand(device::RenderMode renderMode, XrTime predictedDisplayTime, const vrb::Matrix& head, const vrb::Matrix& handJointForAim, ControllerDelegate& delegate);
    void PopulateHandJointLocations(device::RenderMode, std::vector<vrb::Matrix>& jointTransforms, std::vector<float>& jointRadii);

    XrInstance mInstance { XR_NULL_HANDLE };
    XrSession mSession { XR_NULL_HANDLE };
    OpenXRActionSet& mActionSet;
    OpenXRHandFlags mHandeness { OpenXRHandFlags::Right };
    int mIndex { 0 };
    std::string mSubactionPathName;
    XrPath mSubactionPath { XR_NULL_PATH };
    XrAction mGripAction { XR_NULL_HANDLE };
    XrSpace mGripSpace { XR_NULL_HANDLE };
    XrAction mPointerAction { XR_NULL_HANDLE };
    XrSpace mPointerSpace { XR_NULL_HANDLE };
    XrAction mPinchPoseAction {XR_NULL_HANDLE };
    XrSpace mPinchSpace { XR_NULL_HANDLE };
    XrAction mPokePoseAction {XR_NULL_HANDLE };
    XrSpace mPokeSpace { XR_NULL_HANDLE };
    std::unordered_map<OpenXRButtonType, OpenXRActionSet::OpenXRButtonActions> mButtonActions;
    std::unordered_map<OpenXRAxisType, XrAction> mAxisActions;
    XrAction mHapticAction;
    uint64_t mStartHapticFrameId;
    XrSystemProperties mSystemProperties;
    std::vector<OpenXRInputMapping> mMappings;
    OpenXRInputMapping* mActiveMapping { XR_NULL_HANDLE };
    bool selectActionStarted { false };
    bool squeezeActionStarted { false };
    std::vector<float> axesContainer;
    crow::ElbowModelPtr elbow;
    XrHandTrackerEXT mHandTracker { XR_NULL_HANDLE };
    HandJointsArray mHandJoints;
    bool mHasHandJoints { false };
    bool mSupportsFBHandTrackingAim { false };
    OpenXRGesturePtr mGestureManager;
    bool mSupportsHandJointsMotionRangeInfo { false };
    bool mUsingHandInteractionProfile { false };
    device::DeviceType mDeviceType { device::UnknownType };

    struct HandMeshMSFT {
        XrSpace space = XR_NULL_HANDLE;
        XrHandMeshMSFT handMesh;
        HandMeshBufferPtr buffer = nullptr;

        std::vector<HandMeshBufferMSFTPtr> buffers;
        std::vector<HandMeshBufferMSFTPtr> usedBuffers;
        ~HandMeshMSFT() {
            for (auto& buf: buffers)
                buf.reset();
            for (auto& buf: usedBuffers)
                buf.reset();
        }
    } mHandMeshMSFT;
    HandMeshBufferPtr AcquireHandMeshBuffer();
    void ReleaseHandMeshBuffer();

    bool mIsHandInteractionEXTSupported { false };

public:
    static OpenXRInputSourcePtr Create(XrInstance, XrSession, OpenXRActionSet&, const XrSystemProperties&, OpenXRHandFlags, int index);
    ~OpenXRInputSource();

    XrResult SuggestBindings(SuggestedBindings&) const;
    void Update(const XrFrameState&, XrSpace, const vrb::Matrix& head, const vrb::Vector& offsets, device::RenderMode, ControllerDelegate& delegate);
    XrResult UpdateInteractionProfile(ControllerDelegate&, const char* emulateProfile = nullptr);
    std::string ControllerModelName() const;
    OpenXRInputMapping* GetActiveMapping() const { return mActiveMapping; }
    void SetHandMeshBufferSizes(const uint32_t indexCount, const uint32_t vertexCount);
    HandMeshBufferPtr GetNextHandMeshBuffer();
};

} // namespace crow

