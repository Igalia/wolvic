#include "OpenXRInputSource.h"

namespace crow {

// Threshold to consider a trigger value as a click
// Used when devices don't map the click value for triggers;
const float kClickThreshold = 0.91f;

const vrb::Vector kAverageHeight(0.0f, 1.7f, 0.0f);

OpenXRInputSourcePtr OpenXRInputSource::Create(XrInstance instance, XrSession session, const XrSystemProperties& properties, OpenXRHandFlags handeness, int index)
{
    OpenXRInputSourcePtr input(new OpenXRInputSource(instance, session, properties, handeness, index));
    if (XR_FAILED(input->Initialize()))
        return nullptr;
    return input;
}

OpenXRInputSource::OpenXRInputSource(XrInstance instance, XrSession session, const XrSystemProperties& properties, OpenXRHandFlags handeness, int index)
    : mInstance(instance)
    , mSession(session)
    , mSystemProperties(properties)
    , mHandeness(handeness)
    , mIndex(index)
{
  elbow = ElbowModel::Create();
}

OpenXRInputSource::~OpenXRInputSource()
{
    if (mActionSet != XR_NULL_HANDLE)
        xrDestroyActionSet(mActionSet);
    if (mGripSpace != XR_NULL_HANDLE)
        xrDestroySpace(mGripSpace);
    if (mPointerSpace != XR_NULL_HANDLE)
        xrDestroySpace(mPointerSpace);
}

XrResult OpenXRInputSource::Initialize()
{
    mSubactionPathName = mHandeness == OpenXRHandFlags::Left ? kPathLeftHand : kPathRightHand;
    RETURN_IF_XR_FAILED(xrStringToPath(mInstance, mSubactionPathName.c_str(), &mSubactionPath));

    // Initialize Action Set.
    std::string prefix = std::string("input_") + (mHandeness == OpenXRHandFlags::Left ? "left" : "right");
    std::string actionSetName = prefix + "_action_set";
    XrActionSetCreateInfo createInfo { XR_TYPE_ACTION_SET_CREATE_INFO };
    std::strncpy(createInfo.actionSetName, actionSetName.c_str(), XR_MAX_ACTION_SET_NAME_SIZE - 1);
    std::strncpy(createInfo.localizedActionSetName, actionSetName.c_str(), XR_MAX_ACTION_SET_NAME_SIZE - 1);

    RETURN_IF_XR_FAILED(xrCreateActionSet(mInstance, &createInfo, &mActionSet), mInstance);

    // Initialize pose actions and spaces.
    RETURN_IF_XR_FAILED(CreateAction(XR_ACTION_TYPE_POSE_INPUT, prefix + "_grip", mGripAction));
    RETURN_IF_XR_FAILED(CreateAction(XR_ACTION_TYPE_POSE_INPUT, prefix + "_pointer", mPointerAction));

    // Filter mappings
    for (auto& mapping: OpenXRInputMappings) {
      if (mapping.systemFilter && strcmp(mapping.systemFilter, mSystemProperties.systemName) != 0) {
        continue;
      }
      mMappings.push_back(mapping);
    }

    std::unordered_map<OpenXRButtonType, int> buttons;
    for (auto& mapping: mMappings) {
      for (auto& button: mapping.buttons) {
        buttons[button.type] |= button.flags;
      }
    }

    // Initialize button actions.
    for (auto& item: buttons) {
        OpenXRButtonActions actions;
        CreateButtonActions(item.first, prefix, item.second, actions);
        mButtonActions.emplace(item.first, actions);
    }

    // Initialize axes.
    for (auto axisType : OpenXRAxisTypes()) {
        XrAction axisAction { XR_NULL_HANDLE };
        std::string name = prefix + "_axis_" + OpenXRAxisTypeNames->at(static_cast<int>(axisType));
        RETURN_IF_XR_FAILED(CreateAction(XR_ACTION_TYPE_VECTOR2F_INPUT, name, axisAction));
        mAxisActions.emplace(axisType, axisAction);
    }

    return XR_SUCCESS;
}

XrResult OpenXRInputSource::CreateActionSpace(XrAction action, XrSpace& space) const
{
    XrActionSpaceCreateInfo createInfo { XR_TYPE_ACTION_SPACE_CREATE_INFO };
    createInfo.action = action;
    createInfo.subactionPath = mSubactionPath;
    createInfo.poseInActionSpace = XrPoseIdentity();

    return xrCreateActionSpace(mSession, &createInfo, &space);
}

XrResult OpenXRInputSource::CreateAction(XrActionType actionType, const std::string& name, XrAction& action) const
{
    XrActionCreateInfo createInfo { XR_TYPE_ACTION_CREATE_INFO };
    createInfo.actionType = actionType;
    createInfo.countSubactionPaths = 1;
    createInfo.subactionPaths = &mSubactionPath;
    std::strncpy(createInfo.actionName, name.c_str(), XR_MAX_ACTION_SET_NAME_SIZE - 1);
    std::strncpy(createInfo.localizedActionName, name.c_str(), XR_MAX_ACTION_SET_NAME_SIZE - 1);

    return xrCreateAction(mActionSet, &createInfo, &action);
}

XrResult OpenXRInputSource::CreateButtonActions(OpenXRButtonType type, const std::string& prefix, int flags, OpenXRButtonActions& actions) const
{
    auto name = prefix + "_button_" + OpenXRButtonTypeNames->at(static_cast<int>(type));

    if (flags & OpenXRButtonFlags::Click) {
      RETURN_IF_XR_FAILED(CreateAction(XR_ACTION_TYPE_BOOLEAN_INPUT, name + "_click", actions.click));
    }
    if (flags & OpenXRButtonFlags::Touch) {
      RETURN_IF_XR_FAILED(CreateAction(XR_ACTION_TYPE_BOOLEAN_INPUT, name + "_touch", actions.touch));
    }
    if (flags & OpenXRButtonFlags::Value) {
      RETURN_IF_XR_FAILED(CreateAction(XR_ACTION_TYPE_FLOAT_INPUT, name + "_value", actions.value));
    }

    return XR_SUCCESS;
}

XrResult OpenXRInputSource::CreateBinding(const char* profilePath, XrAction action, const std::string& bindingPath, SuggestedBindings& bindings) const
{
    assert(profilePath != XR_NULL_PATH);
    assert(action != XR_NULL_HANDLE);
    assert(!bindingPath.empty());

    XrPath path = XR_NULL_PATH;
    RETURN_IF_XR_FAILED(xrStringToPath(mInstance, bindingPath.c_str(), &path));

    XrActionSuggestedBinding binding { action, path };
    if (auto it = bindings.find(profilePath); it != bindings.end()) {
        it->second.push_back(binding);
    }
    else {
        bindings.emplace(profilePath, std::vector<XrActionSuggestedBinding>{ binding });
    }

    return XR_SUCCESS;
}

XrResult OpenXRInputSource::GetPoseState(XrAction action, XrSpace space, XrSpace baseSpace, const XrFrameState& frameState, bool& isActive, XrSpaceLocation& location) const
{
    XrActionStateGetInfo getInfo {XR_TYPE_ACTION_STATE_GET_INFO };
    getInfo.subactionPath = mSubactionPath;
    getInfo.action = action;

    XrActionStatePose poseState { XR_TYPE_ACTION_STATE_POSE };
    CHECK_XRCMD(xrGetActionStatePose(mSession, &getInfo, &poseState));

    isActive = poseState.isActive;

    if (!poseState.isActive) {
      return XR_SUCCESS;
    }

    location = { XR_TYPE_SPACE_LOCATION };
    RETURN_IF_XR_FAILED(xrLocateSpace(space, baseSpace, frameState.predictedDisplayTime, &location));

    return XR_SUCCESS;
}

std::optional<OpenXRInputSource::OpenXRButtonState> OpenXRInputSource::GetButtonState(const OpenXRButton& button) const
{
    auto it = mButtonActions.find(button.type);
    if (it == mButtonActions.end())
        return std::nullopt;

    OpenXRButtonState result;
    bool hasValue = false;
    auto& actions = it->second;

    auto queryActionState = [this, &hasValue](bool enabled, XrAction action, auto& value, auto defaultValue) {
        if (enabled && action != XR_NULL_HANDLE && XR_SUCCEEDED(this->GetActionState(action, &value)))
            hasValue = true;
        else
            value = defaultValue;
    };

    queryActionState(button.flags & OpenXRButtonFlags::Click, actions.click, result.clicked, false);
    bool clickedHasValue = hasValue;
    queryActionState(button.flags & OpenXRButtonFlags::Touch, actions.touch, result.touched, result.clicked);
    queryActionState(button.flags & OpenXRButtonFlags::Value, actions.value, result.value, result.clicked ? 1.0 : 0.0);

    if (!clickedHasValue && result.value > kClickThreshold) {
      result.clicked = true;
    }

    return hasValue ? std::make_optional(result) : std::nullopt;
}

std::optional<XrVector2f> OpenXRInputSource::GetAxis(OpenXRAxisType axisType) const
{
    auto it = mAxisActions.find(axisType);
    if (it == mAxisActions.end())
        return std::nullopt;

    XrVector2f axis;
    if (XR_FAILED(GetActionState(it->second, &axis)))
        return std::nullopt;

    return axis;
}

XrResult OpenXRInputSource::GetActionState(XrAction action, bool* value) const
{
    assert(value);
    assert(action != XR_NULL_HANDLE);

    XrActionStateBoolean state { XR_TYPE_ACTION_STATE_BOOLEAN };
    XrActionStateGetInfo info { XR_TYPE_ACTION_STATE_GET_INFO };
    info.action = action;
    info.subactionPath = mSubactionPath;

    RETURN_IF_XR_FAILED(xrGetActionStateBoolean(mSession, &info, &state), mInstance);
    *value = state.currentState;

    return XR_SUCCESS;
}

XrResult OpenXRInputSource::GetActionState(XrAction action, float* value) const
{
    assert(value);
    assert(action != XR_NULL_HANDLE);

    XrActionStateFloat state { XR_TYPE_ACTION_STATE_FLOAT };
    XrActionStateGetInfo info { XR_TYPE_ACTION_STATE_GET_INFO };
    info.action = action;
    info.subactionPath = mSubactionPath;

    RETURN_IF_XR_FAILED(xrGetActionStateFloat(mSession, &info, &state));
    *value = state.currentState;

    return XR_SUCCESS;
}

XrResult OpenXRInputSource::GetActionState(XrAction action, XrVector2f* value) const
{
    assert(value);
    assert(action != XR_NULL_HANDLE);

    XrActionStateVector2f state { XR_TYPE_ACTION_STATE_VECTOR2F };
    XrActionStateGetInfo info { XR_TYPE_ACTION_STATE_GET_INFO };
    info.action = action;
    info.subactionPath = mSubactionPath;

    RETURN_IF_XR_FAILED(xrGetActionStateVector2f(mSession, &info, &state));
    *value = state.currentState;

    return XR_SUCCESS;
}

ControllerDelegate::Button OpenXRInputSource::GetBrowserbutton(const OpenXRButton& button) const
{
  if (button.browserMapping.has_value()) {
    return button.browserMapping.value();
  }

  switch (button.type) {
    case OpenXRButtonType::Trigger:
      return ControllerDelegate::BUTTON_TRIGGER;
    case OpenXRButtonType::Squeeze:
      return ControllerDelegate::BUTTON_SQUEEZE;
    case OpenXRButtonType::Menu:
      return ControllerDelegate::BUTTON_APP;
    case OpenXRButtonType::Back:
      return ControllerDelegate::BUTTON_Y;
    case OpenXRButtonType::Trackpad:
      return ControllerDelegate::BUTTON_TOUCHPAD;
    case OpenXRButtonType::Thumbstick:
    case OpenXRButtonType::Thumbrest:
      return ControllerDelegate::BUTTON_OTHERS;
    case OpenXRButtonType::ButtonA:
      return ControllerDelegate::BUTTON_A;
    case OpenXRButtonType::ButtonB:
      return ControllerDelegate::BUTTON_B;
    case OpenXRButtonType::ButtonX:
      return ControllerDelegate::BUTTON_X;
    case OpenXRButtonType::ButtonY:
      return ControllerDelegate::BUTTON_Y;
    case OpenXRButtonType::enum_count:
      return ControllerDelegate::BUTTON_OTHERS;
  }
  return ControllerDelegate::BUTTON_OTHERS;
}

std::optional<uint8_t> OpenXRInputSource::GetImmersiveButton(const OpenXRButton& button) const
{
  switch (button.type) {
    case OpenXRButtonType::Trigger:
      return device::kImmersiveButtonTrigger;
    case OpenXRButtonType::Squeeze:
      return device::kImmersiveButtonSqueeze;
    case OpenXRButtonType::Menu:
    case OpenXRButtonType::Back:
      return std::nullopt;
    case OpenXRButtonType::Trackpad:
      return device::kImmersiveButtonTouchpad;
    case OpenXRButtonType::Thumbstick:
      return device::kImmersiveButtonThumbstick;
    case OpenXRButtonType::Thumbrest:
      return device::kImmersiveButtonThumbrest;
    case OpenXRButtonType::ButtonA:
      return device::kImmersiveButtonA;
    case OpenXRButtonType::ButtonB:
      return device::kImmersiveButtonB;
    case OpenXRButtonType::ButtonX:
      return device::kImmersiveButtonA;
    case OpenXRButtonType::ButtonY:
      return device::kImmersiveButtonB;
    case OpenXRButtonType::enum_count:
      return std::nullopt;
  }
  return std::nullopt;
}

XrResult OpenXRInputSource::SuggestBindings(SuggestedBindings& bindings) const
{
    for (auto& mapping : mMappings) {
        // Suggest binding for pose actions.
        RETURN_IF_XR_FAILED(CreateBinding(mapping.path, mGripAction, mSubactionPathName + "/" + kPathGripPose, bindings));
        RETURN_IF_XR_FAILED(CreateBinding(mapping.path, mPointerAction, mSubactionPathName + "/" + kPathAimPose, bindings));

        // Suggest binding for button actions.
        for (auto& button: mapping.buttons) {
            if ((button.hand & mHandeness) == 0) {
                continue;
            }

            auto it = mButtonActions.find(button.type);
            if (it == mButtonActions.end()) {
                continue;
            }
            const auto& actions = it->second;
            if (button.flags & OpenXRButtonFlags::Click) {
                assert(actions.click != XR_NULL_HANDLE);
                RETURN_IF_XR_FAILED(CreateBinding(mapping.path, actions.click, mSubactionPathName + "/" + button.path +  "/" + kPathActionClick, bindings));
            }
            if (button.flags & OpenXRButtonFlags::Touch) {
                assert(actions.touch != XR_NULL_HANDLE);
                RETURN_IF_XR_FAILED(CreateBinding(mapping.path, actions.touch, mSubactionPathName + "/" + button.path + "/" + kPathActionTouch, bindings));
            }
            if (button.flags & OpenXRButtonFlags::Value) {
                assert(actions.value != XR_NULL_HANDLE);
                RETURN_IF_XR_FAILED(CreateBinding(mapping.path, actions.value, mSubactionPathName + "/" + button.path + "/" + kPathActionValue, bindings));
            }
        }

        // Suggest binding for axis actions.
        for (auto& axis: mapping.axes) {
            auto it = mAxisActions.find(axis.type);
            if (it == mAxisActions.end()) {
                continue;
            }
            auto action = it->second;
            assert(action != XR_NULL_HANDLE);
            RETURN_IF_XR_FAILED(CreateBinding(mapping.path, action, mSubactionPathName + "/" + axis.path, bindings));
        }
    }

    return XR_SUCCESS;
}

void OpenXRInputSource::Update(const XrFrameState& frameState, XrSpace localSpace, const vrb::Matrix& head, device::RenderMode renderMode, ControllerDelegate& delegate)
{
    if (!mActiveMapping) {
      delegate.SetEnabled(mIndex, false);
      return;
    }

    if ((mHandeness == OpenXRHandFlags::Left && !mActiveMapping->leftControllerModel) || (mHandeness == OpenXRHandFlags::Right && !mActiveMapping->rightControllerModel)) {
      delegate.SetEnabled(mIndex, false);
      return;
    }

    delegate.SetLeftHanded(mIndex, mHandeness == OpenXRHandFlags::Left);
    delegate.SetTargetRayMode(mIndex, device::TargetRayMode::TrackedPointer);

    // Spaces must be created here, it doesn't work if they are created in Initialize (probably a OpenXR SDK bug?)
    if (mGripSpace == XR_NULL_HANDLE) {
      CHECK_XRCMD(CreateActionSpace(mGripAction, mGripSpace));
    }
    if (mPointerSpace == XR_NULL_HANDLE) {
      CHECK_XRCMD(CreateActionSpace(mPointerAction, mPointerSpace));
    }

    // Pose transforms.
    bool isPoseActive { false };
    XrSpaceLocation poseLocation { XR_TYPE_SPACE_LOCATION };
    if (XR_FAILED(GetPoseState(mPointerAction,  mPointerSpace, localSpace, frameState, isPoseActive, poseLocation))) {
        delegate.SetEnabled(mIndex, false);
        return;
    }

    if ((poseLocation.locationFlags & XR_SPACE_LOCATION_ORIENTATION_VALID_BIT) == 0) {
      delegate.SetEnabled(mIndex, false);
      return;
    }

    delegate.SetEnabled(mIndex, true);

    device::CapabilityFlags flags = device::Orientation;
    vrb::Matrix poseMatrix = XrPoseToMatrix(poseLocation.pose);

    if (poseLocation.locationFlags & XR_SPACE_LOCATION_POSITION_VALID_BIT) {
#ifndef HVR
      if (renderMode == device::RenderMode::StandAlone) {
        poseMatrix.TranslateInPlace(kAverageHeight);
      }
#endif
      flags |= device::Position;
    } else {
#if HVR
      auto hand = ElbowModel::HandEnum::Right; // Workaround for bug in the SDK
#else
      auto hand = mHandeness == OpenXRHandFlags::Left ? ElbowModel::HandEnum::Left : ElbowModel::HandEnum::Right;
#endif
      poseMatrix = elbow->GetTransform(hand, head, poseMatrix);
      flags |= device::PositionEmulated;
    }

    delegate.SetTransform(mIndex, poseMatrix);

    isPoseActive = false;
    poseLocation = { XR_TYPE_SPACE_LOCATION };
    CHECK_XRCMD(GetPoseState(mGripAction, mGripSpace, localSpace, frameState,  isPoseActive, poseLocation));
    if (isPoseActive) {
        delegate.SetImmersiveBeamTransform(mIndex, XrPoseToMatrix(poseLocation.pose));
        flags |= device::GripSpacePosition;
    } else {
        delegate.SetImmersiveBeamTransform(mIndex, vrb::Matrix::Identity());
    }

    delegate.SetCapabilityFlags(mIndex, flags);

    // Buttons.
    int buttonCount { 0 };
    bool trackpadClicked { false };
    bool trackpadTouched { false };
#if HVR
    float trackpadValue { 0 };
#endif

    for (auto& button: mActiveMapping->buttons) {
        if ((button.hand & mHandeness) == 0) {
            continue;
        }
        auto state = GetButtonState(button);
        if (!state.has_value()) {
            VRB_ERROR("Cant read button type with path '%s'", button.path);
            continue;
        }

        buttonCount++;
        auto browserButton = GetBrowserbutton(button);
        auto immersiveButton = GetImmersiveButton(button);
        delegate.SetButtonState(mIndex, browserButton, immersiveButton.has_value() ? immersiveButton.value() : -1, state->clicked, state->touched, state->value);

        // Select action
        if (renderMode == device::RenderMode::Immersive && button.type == OpenXRButtonType::Trigger && state->clicked != selectActionStarted) {
          selectActionStarted = state->clicked;
          if (selectActionStarted) {
            delegate.SetSelectActionStart(mIndex);
          } else {
            delegate.SetSelectActionStop(mIndex);
          }
        }

        // Squeeze action
        if (renderMode == device::RenderMode::Immersive && button.type == OpenXRButtonType::Squeeze && state->clicked != squeezeActionStarted) {
          squeezeActionStarted = state->clicked;
          if (squeezeActionStarted) {
            delegate.SetSqueezeActionStart(mIndex);
          } else {
            delegate.SetSqueezeActionStop(mIndex);
          }
        }

        // Trackpad
        if (button.type == OpenXRButtonType::Trackpad) {
          trackpadClicked = state->clicked;
          trackpadTouched = state->touched;
#if HVR
          trackpadValue = state->value;
#endif
        }
    }
    delegate.SetButtonCount(mIndex, buttonCount);

    // Axes
    // https://www.w3.org/TR/webxr-gamepads-module-1/#xr-standard-gamepad-mapping
    axesContainer = { 0.0f, 0.0f, 0.0f, 0.0f };

    for (auto& axis: mActiveMapping->axes) {
      if ((axis.hand & mHandeness) == 0) {
        continue;
      }
      auto state = GetAxis(axis.type);
      if (!state.has_value()) {
        VRB_ERROR("Cant read axis type with path '%s'", axis.path);
        continue;
      }

      if (axis.type == OpenXRAxisType::Trackpad) {
#if HVR
        state->x = 0;
        state->y = 1.0f - trackpadValue;
#endif
        axesContainer[device::kImmersiveAxisTouchpadX] = state->x;
        axesContainer[device::kImmersiveAxisTouchpadY] = state->y;
        if (trackpadTouched && !trackpadClicked) {
          delegate.SetTouchPosition(mIndex, state->x, state->y);
        } else {
          delegate.SetTouchPosition(mIndex, state->x, state->y);
          delegate.EndTouch(mIndex);
        }
      } else if (axis.type == OpenXRAxisType::Thumbstick) {
        axesContainer[device::kImmersiveAxisThumbstickX] = state->x;
        axesContainer[device::kImmersiveAxisThumbstickY] = state->y;
        delegate.SetScrolledDelta(mIndex, state->x, state->y);
      } else {
        axesContainer.push_back(state->x);
        axesContainer.push_back(state->y);
      }
    }
    delegate.SetAxes(mIndex, axesContainer.data(), axesContainer.size());
}

XrResult OpenXRInputSource::UpdateInteractionProfile()
{
    XrInteractionProfileState state { XR_TYPE_INTERACTION_PROFILE_STATE };
    RETURN_IF_XR_FAILED(xrGetCurrentInteractionProfile(mSession, mSubactionPath, &state));
    if (state.interactionProfile == XR_NULL_PATH) {
      return XR_SUCCESS; // Not ready yet
    }

    constexpr uint32_t bufferSize = 100;
    char buffer[bufferSize];
    uint32_t writtenCount = 0;
    RETURN_IF_XR_FAILED(xrPathToString(mInstance, state.interactionProfile, bufferSize, &writtenCount, buffer));

    mActiveMapping = nullptr;

    for (auto& mapping : mMappings) {
        if (!strncmp(mapping.path, buffer, writtenCount)) {
            mActiveMapping = &mapping;
            break;
        }
    }

    return XR_SUCCESS;
}

std::string OpenXRInputSource::ControllerModelName() const
{
  if (mActiveMapping) {
    return mHandeness == OpenXRHandFlags::Left ? mActiveMapping->leftControllerModel : mActiveMapping->rightControllerModel;
  }
  return { };
}


} // namespace crow
