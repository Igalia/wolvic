#pragma once

#include "vrb/Forward.h"
#include "OpenXRInputMappings.h"
#include "OpenXRHelpers.h"
#include "OpenXRActionSet.h"
#include "ElbowModel.h"
#include "OneEuroFilter.h"
#include <optional>
#include <unordered_map>

namespace crow {

class OpenXRInputSource;
typedef std::shared_ptr<OpenXRInputSource> OpenXRInputSourcePtr;

class OpenXRInputSource {
public:
    using SuggestedBindings = std::unordered_map<std::string, std::vector<XrActionSuggestedBinding>>;
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
    };
    std::optional<OpenXRButtonState> GetButtonState(const OpenXRButton&) const;
    std::optional<XrVector2f> GetAxis(OpenXRAxisType) const;
    XrResult GetActionState(XrAction, bool*) const;
    XrResult GetActionState(XrAction, float*) const;
    XrResult GetActionState(XrAction, XrVector2f*) const;
    ControllerDelegate::Button GetBrowserButton(const OpenXRButton&) const;
    std::optional<uint8_t> GetImmersiveButton(const OpenXRButton&) const;
    XrResult applyHapticFeedback(XrAction, XrDuration, float = XR_FREQUENCY_UNSPECIFIED, float = 0.0) const;
    XrResult stopHapticFeedback(XrAction) const;
    void UpdateHaptics(ControllerDelegate&);
    bool GetHandTrackingInfo(const XrFrameState&, XrSpace, const vrb::Matrix& head);
    float GetDistanceBetweenJoints (XrHandJointEXT jointA, XrHandJointEXT jointB);
    bool IsHandJointPositionValid(const enum XrHandJointEXT aJoint);

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
    std::array<XrHandJointLocationEXT, XR_HAND_JOINT_COUNT_EXT> mHandJoints;
    bool mHasHandJoints { false };
    bool mHasAimState { false };
    XrPosef mHandAimPose;
    bool mSupportsFBHandTrackingAim { false };
    double mSmoothIndexThumbDistance { 0 };
    std::unique_ptr<OneEuroFilterVector> mOneEuroFilterPosition;

public:
    static OpenXRInputSourcePtr Create(XrInstance, XrSession, OpenXRActionSet&, const XrSystemProperties&, OpenXRHandFlags, int index);
    ~OpenXRInputSource();

    XrResult SuggestBindings(SuggestedBindings&) const;
    void EmulateControllerFromHand(device::RenderMode renderMode, const vrb::Matrix& head, ControllerDelegate& delegate);
    void Update(const XrFrameState&, XrSpace, const vrb::Matrix& head, const vrb::Vector& offsets, device::RenderMode, ControllerDelegate& delegate);
    XrResult UpdateInteractionProfile(ControllerDelegate&, const char* emulateProfile = nullptr);
    std::string ControllerModelName() const;
    OpenXRInputMapping* GetActiveMapping() const { return mActiveMapping; }
};

} // namespace crow

