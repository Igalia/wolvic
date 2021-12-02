#include "OpenXRInputSource.h"
#include <unordered_set>

namespace crow {

// Threshold to consider a trigger value as a click
// Used when devices don't map the click value for triggers;
const float kClickThreshold = 0.91f;

OpenXRInputSourcePtr OpenXRInputSource::Create(XrInstance instance, XrSession session, OpenXRActionSet& actionSet, const XrSystemProperties& properties, OpenXRHandFlags handeness, int index)
{
    OpenXRInputSourcePtr input(new OpenXRInputSource(instance, session, actionSet, properties, handeness, index));
    if (XR_FAILED(input->Initialize()))
        return nullptr;
    return input;
}

OpenXRInputSource::OpenXRInputSource(XrInstance instance, XrSession session, OpenXRActionSet& actionSet, const XrSystemProperties& properties, OpenXRHandFlags handeness, int index)
    : mInstance(instance)
    , mSession(session)
    , mActionSet(actionSet)
    , mSystemProperties(properties)
    , mHandeness(handeness)
    , mIndex(index)
{
  elbow = ElbowModel::Create();
}

OpenXRInputSource::~OpenXRInputSource()
{
    if (mGripSpace != XR_NULL_HANDLE)
        xrDestroySpace(mGripSpace);
    if (mPointerSpace != XR_NULL_HANDLE)
        xrDestroySpace(mPointerSpace);
}

XrResult OpenXRInputSource::Initialize()
{
    mSubactionPathName = mHandeness == OpenXRHandFlags::Left ? kPathLeftHand : kPathRightHand;
    mSubactionPath = mActionSet.GetSubactionPath(mHandeness);

    // Initialize Action Set.
    std::string prefix = std::string("input_") + (mHandeness == OpenXRHandFlags::Left ? "left" : "right");

    // Initialize pose actions and spaces.
    RETURN_IF_XR_FAILED(mActionSet.GetOrCreateAction(XR_ACTION_TYPE_POSE_INPUT, "grip", OpenXRHandFlags::Both, mGripAction));
    RETURN_IF_XR_FAILED(mActionSet.GetOrCreateAction(XR_ACTION_TYPE_POSE_INPUT, "pointer", OpenXRHandFlags::Both, mPointerAction));

    // Filter mappings
    for (auto& mapping: OpenXRInputMappings) {
      if (mapping.systemFilter && strcmp(mapping.systemFilter, mSystemProperties.systemName) != 0) {
        continue;
      }
      mMappings.push_back(mapping);
    }

    std::unordered_map<OpenXRButtonType, int> button_flags;
    std::unordered_map<OpenXRButtonType, int> button_hands;
    for (auto& mapping: mMappings) {
      for (auto& button: mapping.buttons) {
        button_flags[button.type] |= button.flags;
        button_hands[button.type] |= button.hand;
      }
    }

    // Initialize button actions.
    for (auto& item: button_flags) {
        OpenXRActionSet::OpenXRButtonActions actions;
        mActionSet.GetOrCreateButtonActions(item.first, static_cast<OpenXRButtonFlags>(item.second), static_cast<OpenXRHandFlags>(button_hands[item.first]), actions);
        mButtonActions.emplace(item.first, actions);
    }

    // Filter axes available in mappings
    std::unordered_map<OpenXRAxisType, int> axes;
    for (auto& mapping: mMappings) {
      for (auto& axis: mapping.axes) {
        axes[axis.type] |= axis.hand;
      }
    }

    // Initialize axes.
    for (auto item : axes) {
        XrAction axisAction { XR_NULL_HANDLE };
        std::string name = prefix + "_axis_" + OpenXRAxisTypeNames->at(static_cast<int>(item.first));
        if (item.first == OpenXRAxisType::Trackpad || item.first == OpenXRAxisType::Thumbstick) {
          RETURN_IF_XR_FAILED(mActionSet.GetOrCreateAxisAction(item.first, static_cast<OpenXRHandFlags>(item.second), axisAction));
        } else {
          RETURN_IF_XR_FAILED(mActionSet.GetOrCreateAction(XR_ACTION_TYPE_FLOAT_INPUT, name, static_cast<OpenXRHandFlags>(item.second), axisAction));
        }
        mAxisActions.emplace(item.first, axisAction);
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

    if (result.clicked) {
      VRB_DEBUG("OpenXR button clicked: %s", OpenXRButtonTypeNames->at((int) button.type));
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

#if HVR
    // axes must be between -1 and 1
    axis.x = axis.x * 2 - 1;
    axis.y = -(axis.y * 2 - 1);

    // Workaround for HVR controller precision issues
    const float kPrecision = 0.16;
    if (abs(axis.x) < kPrecision && abs(axis.y) < kPrecision) {
      axis.x = 0;
      axis.y = 0;
    }
#endif

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

ControllerDelegate::Button OpenXRInputSource::GetBrowserButton(const OpenXRButton& button) const
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

void OpenXRInputSource::Update(const XrFrameState& frameState, XrSpace localSpace, const vrb::Matrix& head, float offsetY, device::RenderMode renderMode, ControllerDelegate& delegate)
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
    delegate.SetControllerType(mIndex, mActiveMapping->controllerType);

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

    // Adjust to local is app is using stageSpace (e.g. HVR), otherwise offset will be 0.
    poseLocation.pose.position.y += offsetY;

    delegate.SetEnabled(mIndex, true);

    device::CapabilityFlags flags = device::Orientation;
    vrb::Matrix pointerTransform = XrPoseToMatrix(poseLocation.pose);

#ifdef HVR_6DOF
    const bool positionTracked = true;
#else
    const bool positionTracked = poseLocation.locationFlags & XR_SPACE_LOCATION_POSITION_TRACKED_BIT;
#endif

    if (positionTracked) {
      if (renderMode == device::RenderMode::StandAlone) {
        pointerTransform.TranslateInPlace(kAverageHeight);
      }
      flags |= device::Position;
    } else {
      auto hand = mHandeness == OpenXRHandFlags::Left ? ElbowModel::HandEnum::Left : ElbowModel::HandEnum::Right;
      pointerTransform = elbow->GetTransform(hand, head, pointerTransform);
      flags |= device::PositionEmulated;
    }

    delegate.SetTransform(mIndex, pointerTransform);

    isPoseActive = false;
    poseLocation = { XR_TYPE_SPACE_LOCATION };
    CHECK_XRCMD(GetPoseState(mGripAction, mGripSpace, localSpace, frameState,  isPoseActive, poseLocation));
    if (isPoseActive) {
        // Adjust to local is app is using stageSpace (e.g. HVR), otherwise offset will be 0.
        poseLocation.pose.position.y += offsetY;
        auto gripTransform = XrPoseToMatrix(poseLocation.pose);
        bool hasPosition = poseLocation.locationFlags & XR_SPACE_LOCATION_POSITION_TRACKED_BIT;
        delegate.SetImmersiveBeamTransform(mIndex, hasPosition ? gripTransform : pointerTransform);
        flags |= device::GripSpacePosition;
#if HVR_6DOF
        delegate.SetBeamTransform(mIndex, vrb::Matrix::Rotation(vrb::Vector(1.0, 0.0, 0.0), -M_PI / 4));
#else
        delegate.SetBeamTransform(mIndex, vrb::Matrix::Identity());
#endif

    } else {
        delegate.SetImmersiveBeamTransform(mIndex, vrb::Matrix::Identity());
    }

    delegate.SetCapabilityFlags(mIndex, flags);

    // Buttons.
    int buttonCount { 0 };
    bool trackpadClicked { false };
    bool trackpadTouched { false };

    // https://www.w3.org/TR/webxr-gamepads-module-1/
    std::unordered_set<OpenXRButtonType> placeholders = {
        OpenXRButtonType::Squeeze, OpenXRButtonType::Trackpad, OpenXRButtonType::Thumbstick
    };

    for (auto& button: mActiveMapping->buttons) {
        if ((button.hand & mHandeness) == 0) {
            continue;
        }
        auto state = GetButtonState(button);
        if (!state.has_value()) {
            VRB_ERROR("Cant read button type with path '%s'", button.path);
            continue;
        }

        placeholders.erase(button.type);
        buttonCount++;
        auto browserButton = GetBrowserButton(button);
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
        }
    }

    buttonCount += placeholders.size();
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
        axesContainer[device::kImmersiveAxisTouchpadX] = state->x;
        axesContainer[device::kImmersiveAxisTouchpadY] = -state->y;
        if (trackpadTouched && !trackpadClicked) {
          delegate.SetTouchPosition(mIndex, state->x, state->y);
        } else {
          delegate.SetTouchPosition(mIndex, state->x, state->y);
          delegate.EndTouch(mIndex);
        }
      } else if (axis.type == OpenXRAxisType::Thumbstick) {
        axesContainer[device::kImmersiveAxisThumbstickX] = state->x;
        axesContainer[device::kImmersiveAxisThumbstickY] = -state->y;
        delegate.SetScrolledDelta(mIndex, state->x, state->y);
      } else {
        axesContainer.push_back(state->x);
        axesContainer.push_back(-state->y);
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
