#pragma once

#include "vrb/Forward.h"
#include "OpenXRInputMappings.h"
#include "OpenXRHelpers.h"
#include "ElbowModel.h"
#include <optional>
#include <unordered_map>

namespace crow {

class OpenXRInputSource;
typedef std::shared_ptr<OpenXRInputSource> OpenXRInputSourcePtr;

class OpenXRInputSource {
public:
    using SuggestedBindings = std::unordered_map<std::string, std::vector<XrActionSuggestedBinding>>;
private:
    OpenXRInputSource(XrInstance, XrSession, const XrSystemProperties&, OpenXRHandFlags, int index);

    struct OpenXRButtonActions {
        XrAction click { XR_NULL_HANDLE };
        XrAction touch { XR_NULL_HANDLE };
        XrAction value { XR_NULL_HANDLE };
    };

    XrResult Initialize();
    XrResult CreateActionSpace(XrAction, XrSpace&) const;
    XrResult CreateAction(XrActionType, const std::string& name, XrAction&) const;
    XrResult CreateButtonActions(OpenXRButtonType, const std::string& prefix, int flags, OpenXRButtonActions&) const;
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
    ControllerDelegate::Button GetBrowserbutton(const OpenXRButton&) const;
    std::optional<uint8_t> GetImmersiveButton(const OpenXRButton&) const;

    XrInstance mInstance { XR_NULL_HANDLE };
    XrSession mSession { XR_NULL_HANDLE };
    OpenXRHandFlags mHandeness { OpenXRHandFlags::Right };
    int mIndex { 0 };
    std::string mSubactionPathName;
    XrPath mSubactionPath { XR_NULL_PATH };
    XrActionSet mActionSet { XR_NULL_HANDLE };
    XrAction mGripAction { XR_NULL_HANDLE };
    XrSpace mGripSpace { XR_NULL_HANDLE };
    XrAction mPointerAction { XR_NULL_HANDLE };
    XrSpace mPointerSpace { XR_NULL_HANDLE };
    std::unordered_map<OpenXRButtonType, OpenXRButtonActions> mButtonActions;
    std::unordered_map<OpenXRAxisType, XrAction> mAxisActions;
    XrSystemProperties mSystemProperties;
    std::vector<OpenXRInputMapping> mMappings;
    OpenXRInputMapping* mActiveMapping { XR_NULL_HANDLE };
    bool selectActionStarted { false };
    bool squeezeActionStarted { false };
    std::vector<float> axesContainer;
    crow::ElbowModelPtr elbow;

public:
    static OpenXRInputSourcePtr Create(XrInstance, XrSession, const XrSystemProperties&, OpenXRHandFlags, int index);
    ~OpenXRInputSource();

    XrResult SuggestBindings(SuggestedBindings&) const;
    void Update(const XrFrameState&, XrSpace, const vrb::Matrix& head, device::RenderMode, ControllerDelegate& delegate);
    XrActionSet ActionSet() const { return mActionSet; }
    XrResult UpdateInteractionProfile();
    std::string ControllerModelName() const;
    OpenXRInputMapping* GetActiveMapping() const { return mActiveMapping; }
};

} // namespace crow

