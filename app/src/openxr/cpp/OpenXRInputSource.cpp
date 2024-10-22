#include "OpenXRInputSource.h"
#include "OpenXRExtensions.h"
#include <assert.h>
#include <unordered_set>
#include "DeviceUtils.h"
#include "SystemUtils.h"
#include "DeviceDelegate.h"

#define HAND_JOINT_FOR_AIM XR_HAND_JOINT_MIDDLE_PROXIMAL_EXT

namespace crow {

// When doing scrolling with eye tracking we wait until this threshold is reached to start scrolling.
// Otherwise single (slow) clicks would easily trigger scrolling.
const XrDuration kEyeTrackingScrollThreshold = 250000000;

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
    if (mHandTracker != XR_NULL_HANDLE)
        OpenXRExtensions::sXrDestroyHandTrackerEXT(mHandTracker);
}

XrResult OpenXRInputSource::Initialize()
{
    mSubactionPathName = mHandeness == OpenXRHandFlags::Left ? kPathLeftHand : kPathRightHand;
    mSubactionPath = mActionSet.GetSubactionPath(mHandeness);
    mIsHandInteractionSupported = OpenXRExtensions::IsExtensionSupported(XR_EXT_HAND_INTERACTION_EXTENSION_NAME) ||
                                  OpenXRExtensions::IsExtensionSupported(XR_MSFT_HAND_INTERACTION_EXTENSION_NAME);

    // Initialize Action Set.
    std::string prefix = std::string("input_") + (mHandeness == OpenXRHandFlags::Left ? "left" : "right");

    // Initialize pose actions and spaces.
    RETURN_IF_XR_FAILED(mActionSet.GetOrCreateAction(XR_ACTION_TYPE_POSE_INPUT, "grip", OpenXRHandFlags::Both, mGripAction));
    RETURN_IF_XR_FAILED(mActionSet.GetOrCreateAction(XR_ACTION_TYPE_POSE_INPUT, "pointer", OpenXRHandFlags::Both, mPointerAction));
    if (OpenXRExtensions::IsExtensionSupported(XR_EXT_HAND_INTERACTION_EXTENSION_NAME)) {
        RETURN_IF_XR_FAILED(mActionSet.GetOrCreateAction(XR_ACTION_TYPE_POSE_INPUT, "pinch_ext", OpenXRHandFlags::Both, mPinchPoseAction));
        RETURN_IF_XR_FAILED(mActionSet.GetOrCreateAction(XR_ACTION_TYPE_POSE_INPUT, "poke_ext", OpenXRHandFlags::Both, mPokePoseAction));
    }
    RETURN_IF_XR_FAILED(mActionSet.GetOrCreateAction(XR_ACTION_TYPE_VIBRATION_OUTPUT, "haptic", OpenXRHandFlags::Both, mHapticAction));

    // Filter mappings
    bool systemIs6DoF = mSystemProperties.trackingProperties.positionTracking == XR_TRUE;
    auto systemDoF = systemIs6DoF ? DoF::IS_6DOF : DoF::IS_3DOF;
    mDeviceType = DeviceUtils::GetDeviceTypeFromSystem(systemIs6DoF);
    // Add a workaround for Monado not reporting properly device capabilities
    // https://gitlab.freedesktop.org/monado/monado/-/issues/265
    if (mDeviceType == device::LenovoVRX) {
        systemIs6DoF = true;
        systemDoF = DoF::IS_6DOF;
    }
    for (auto& mapping: OpenXRInputMappings) {
      // Always populate default/fall-back profiles
      if (mapping.controllerType == device::UnknownType) {
          mMappings.push_back(mapping);
          // Use the system's deviceType instead to ensure we get a valid VRController on WebXR sessions
          mMappings.back().controllerType = mDeviceType;
          continue;
      }
      if ((mDeviceType != mapping.controllerType) || (systemDoF != mapping.systemDoF))
          continue;
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

    // Initialize hand tracking, if supported
    if (OpenXRExtensions::IsExtensionSupported(XR_EXT_HAND_TRACKING_EXTENSION_NAME) &&
            OpenXRExtensions::sXrCreateHandTrackerEXT != nullptr) {
        XrHandTrackerCreateInfoEXT handTrackerInfo{XR_TYPE_HAND_TRACKER_CREATE_INFO_EXT};
        handTrackerInfo.hand = (mHandeness == OpenXRHandFlags::Right) ? XR_HAND_RIGHT_EXT : XR_HAND_LEFT_EXT;
        handTrackerInfo.handJointSet = XR_HAND_JOINT_SET_DEFAULT_EXT;

        RETURN_IF_XR_FAILED(OpenXRExtensions::sXrCreateHandTrackerEXT(mSession, &handTrackerInfo,
                                                                      &mHandTracker));

        mSupportsHandJointsMotionRangeInfo = OpenXRExtensions::IsExtensionSupported(XR_EXT_HAND_JOINTS_MOTION_RANGE_EXTENSION_NAME);

#if defined(PICOXR)
        // Pico's runtime does not advertise it but it does work.
        mSupportsFBHandTrackingAim = true;
#else
        mSupportsFBHandTrackingAim = OpenXRExtensions::IsExtensionSupported(XR_FB_HAND_TRACKING_AIM_EXTENSION_NAME);
#endif
        if (!mIsHandInteractionSupported) {
            if (mSupportsFBHandTrackingAim) {
                mGestureManager = std::make_unique<OpenXRGestureManagerFBHandTrackingAim>();
            } else {
                // TODO: fine tune params for different devices.
                OpenXRGestureManagerHandJoints::OneEuroFilterParams params= { 0.25, 1, 1 };
                mGestureManager = std::make_unique<OpenXRGestureManagerHandJoints>(mHandJoints, &params);
            }
            VRB_LOG("OpenXR: using %s to compute hands aim", mSupportsFBHandTrackingAim ? XR_FB_HAND_TRACKING_AIM_EXTENSION_NAME : "hand joints");
        } else {
            if (OpenXRExtensions::IsExtensionSupported(XR_EXT_HAND_INTERACTION_EXTENSION_NAME)) {
                VRB_LOG("OpenXR: using %s to compute hands aim", XR_EXT_HAND_INTERACTION_EXTENSION_NAME);
            } else if (OpenXRExtensions::IsExtensionSupported(XR_MSFT_HAND_INTERACTION_EXTENSION_NAME)) {
                VRB_LOG("OpenXR: using %s to compute hands aim", XR_MSFT_HAND_INTERACTION_EXTENSION_NAME);
            }
        }
    }

    // Initialize double buffers for storing XR_MSFT_hand_tracking_mesh geometry
    if (OpenXRExtensions::IsExtensionSupported(XR_MSFT_HAND_TRACKING_MESH_EXTENSION_NAME)) {
        for (int i = 0; i < 2; i++) {
            auto buffer = std::make_shared<HandMeshBufferMSFT>();
            mHandMeshMSFT.buffers.push_back(buffer);
        }
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

XrResult OpenXRInputSource::applyHapticFeedback(XrAction action, XrDuration duration, float frequency, float amplitude) const
{
    XrHapticActionInfo hapticActionInfo { XR_TYPE_HAPTIC_ACTION_INFO };
    hapticActionInfo.action = action;
    hapticActionInfo.subactionPath = mSubactionPath;

    XrHapticVibration hapticVibration { XR_TYPE_HAPTIC_VIBRATION };
    hapticVibration.duration = duration;
    hapticVibration.frequency = frequency;
    hapticVibration.amplitude = amplitude;

    RETURN_IF_XR_FAILED(xrApplyHapticFeedback(mSession, &hapticActionInfo, (const XrHapticBaseHeader*)&hapticVibration));

    return XR_SUCCESS;
}

XrResult OpenXRInputSource::stopHapticFeedback(XrAction action) const
{
    XrHapticActionInfo hapticActionInfo { XR_TYPE_HAPTIC_ACTION_INFO };
    hapticActionInfo.action = action;
    hapticActionInfo.subactionPath = mSubactionPath;

    RETURN_IF_XR_FAILED(xrStopHapticFeedback(mSession, &hapticActionInfo));

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
    queryActionState(button.flags & OpenXRButtonFlags::Ready, actions.ready, result.ready, true);

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
    // Workaround for HVR controller precision issues
    const float kPrecision = 0.16;
    if (abs(axis.x) < kPrecision && abs(axis.y) < kPrecision) {
      axis.x = 0;
      axis.y = 0;
    }
    if (mSystemProperties.trackingProperties.positionTracking == XR_TRUE)
        axis.y = -axis.y;
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

        // FIXME: reenable this once MagicLeap OS v1.8 is released as it has a fix for this.
        // Otherwise the hand interaction profile is not properly used.
        if (OpenXRExtensions::IsExtensionSupported(XR_EXT_HAND_INTERACTION_EXTENSION_NAME) && (DeviceUtils::GetDeviceTypeFromSystem(true) != device::MagicLeap2)) {
            RETURN_IF_XR_FAILED(CreateBinding(mapping.path, mPinchPoseAction, mSubactionPathName + "/" + kPinchPose, bindings));
            RETURN_IF_XR_FAILED(CreateBinding(mapping.path, mPokePoseAction, mSubactionPathName + "/" + kPokePose, bindings));
        }

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
            if (button.flags & OpenXRButtonFlags::Ready) {
                assert(actions.ready != XR_NULL_HANDLE);
                RETURN_IF_XR_FAILED(CreateBinding(mapping.path, actions.ready, mSubactionPathName + "/" + button.path + "/" + kPathActionReady, bindings));
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

        for (auto& haptic: mapping.haptics) {
            RETURN_IF_XR_FAILED(CreateBinding(mapping.path, mHapticAction,
                                              mSubactionPathName + "/" + haptic.path, bindings));
        }
    }

    return XR_SUCCESS;
}

void OpenXRInputSource::UpdateHaptics(ControllerDelegate &delegate)
{
    uint64_t frameId = 0;
    float pulseDuration = 0.0f;
    float pulseIntensity = 0.0f;
    delegate.GetHapticFeedback(mIndex, frameId, pulseDuration, pulseIntensity);
    if (frameId == 0 || pulseDuration <= 0.0f || pulseIntensity <= 0.0f) {
        // No current haptic feedback, stop any ongoing haptic.
        if (mStartHapticFrameId != 0) {
            mStartHapticFrameId = 0;
            CHECK_XRCMD(stopHapticFeedback(mHapticAction));
        }
        return;
    }

    if (frameId == mStartHapticFrameId)
        return;
    mStartHapticFrameId = frameId;

    // Duration should be expressed in nanoseconds.
    auto duration = (uint64_t) (pulseDuration * 1000000.0f);
    pulseIntensity = std::max(pulseIntensity, 1.0f);

    CHECK_XRCMD(applyHapticFeedback(mHapticAction, duration, XR_FREQUENCY_UNSPECIFIED, pulseIntensity));
}

HandMeshBufferPtr OpenXRInputSource::AcquireHandMeshBuffer() {
    if (mHandMeshMSFT.buffers.empty())
        return nullptr;

    auto& result = mHandMeshMSFT.buffers.front();
    mHandMeshMSFT.buffers.erase(mHandMeshMSFT.buffers.begin());

    return result;
}

void OpenXRInputSource::ReleaseHandMeshBuffer() {
    if (mHandMeshMSFT.usedBuffers.empty())
        return;

    auto& buffer = mHandMeshMSFT.usedBuffers.front();
    mHandMeshMSFT.usedBuffers.erase(mHandMeshMSFT.usedBuffers.begin());

    mHandMeshMSFT.buffers.push_back(buffer);
}

bool OpenXRInputSource::GetHandTrackingInfo(XrTime predictedDisplayTime, XrSpace localSpace, const vrb::Matrix& head) {
    if (OpenXRExtensions::sXrLocateHandJointsEXT == XR_NULL_HANDLE || mHandTracker == XR_NULL_HANDLE)
        return false;

    // Update hand locations
    XrHandJointsLocateInfoEXT locateInfo { XR_TYPE_HAND_JOINTS_LOCATE_INFO_EXT };
    locateInfo.baseSpace = localSpace;
    locateInfo.time = predictedDisplayTime;

    if (mSupportsHandJointsMotionRangeInfo) {
        XrHandJointsMotionRangeInfoEXT motionRangeInfo { XR_TYPE_HAND_JOINTS_MOTION_RANGE_INFO_EXT };
        motionRangeInfo.handJointsMotionRange = XR_HAND_JOINTS_MOTION_RANGE_UNOBSTRUCTED_EXT;
        locateInfo.next = &motionRangeInfo;
    }

    XrHandJointLocationsEXT jointLocations { XR_TYPE_HAND_JOINT_LOCATIONS_EXT };
    jointLocations.jointCount = XR_HAND_JOINT_COUNT_EXT;
    jointLocations.jointLocations = mHandJoints.data();
    if (mGestureManager)
        mGestureManager->populateNextStructureIfNeeded(jointLocations);

    CHECK_XRCMD(OpenXRExtensions::sXrLocateHandJointsEXT(mHandTracker, &locateInfo, &jointLocations));
    mHasHandJoints = jointLocations.isActive;
#if defined(SPACES)
    // Bug in Spaces runtime, isActive returns always false, force it to true for the A3.
    // https://gitlab.freedesktop.org/monado/monado/-/issues/263
    if (mDeviceType == device::LenovoA3)
        mHasHandJoints = true;
#endif

    // Even if the SDK reports the hand tracking as active we still need to check the validity
    // of the joint positions to determine if we should render the hands or not. Some SDKs return
    // isActive as TRUE even if the joints are not valid.
    auto hasAtLeastOneValidJoint = [](const XrHandJointLocationsEXT& jointLocations) {
        for (int i = 0; i < XR_HAND_JOINT_COUNT_EXT; ++i) {
            if (jointLocations.jointLocations[i].locationFlags & XR_SPACE_LOCATION_POSITION_VALID_BIT)
                return true;
        }
        return false;
    };
    mHasHandJoints = mHasHandJoints && hasAtLeastOneValidJoint(jointLocations);

    // Rest of the method deal with XR_MSFT_hand_tracking_mesh extension
    if (!OpenXRExtensions::IsExtensionSupported(XR_MSFT_HAND_TRACKING_MESH_EXTENSION_NAME) || !mHasHandJoints)
        return mHasHandJoints;

    // Lazily create the XrSpace required by XR_MSFT_hand_tracking_mesh extension.
    if (mHandMeshMSFT.space == XR_NULL_HANDLE) {
        assert(OpenXRExtensions::sXrCreateHandMeshSpaceMSFT != nullptr);
        auto& matrix = vrb::Matrix::Identity().TranslateInPlace(kAverageHeight);
        XrPosef pose = MatrixToXrPose(matrix);
        XrHandMeshSpaceCreateInfoMSFT createInfo = {
                .type = XR_TYPE_HAND_MESH_SPACE_CREATE_INFO_MSFT,
                .handPoseType = XR_HAND_POSE_TYPE_TRACKED_MSFT,
                .poseInHandMeshSpace = pose,
        };
        CHECK_XRCMD(OpenXRExtensions::sXrCreateHandMeshSpaceMSFT(mHandTracker, &createInfo,
                                                                 &mHandMeshMSFT.space));
    }

    // Bail if hand mesh buffer sizes haven't yet been initialized
    if (mHandMeshMSFT.handMesh.indexBuffer.indexCapacityInput == 0)
        return mHasHandJoints;

    assert(mHandMeshMSFT.handMesh.vertexBuffer.vertexCapacityInput > 0);

    if (auto genericBuffer = AcquireHandMeshBuffer()) {
        auto buffer = std::dynamic_pointer_cast<HandMeshBufferMSFT>(genericBuffer);
        buffer->indices.resize(mHandMeshMSFT.handMesh.indexBuffer.indexCapacityInput);
        mHandMeshMSFT.handMesh.indexBuffer.indices = (uint32_t*) buffer->indices.data();

        buffer->vertices.resize(mHandMeshMSFT.handMesh.vertexBuffer.vertexCapacityInput);
        mHandMeshMSFT.handMesh.vertexBuffer.vertices = (XrHandMeshVertexMSFT*) buffer->vertices.data();

        XrHandMeshUpdateInfoMSFT updateInfo = {
                .type = XR_TYPE_HAND_MESH_UPDATE_INFO_MSFT,
                .time = predictedDisplayTime,
                .handPoseType = XR_HAND_POSE_TYPE_TRACKED_MSFT,
        };
        CHECK_XRCMD(OpenXRExtensions::sXrUpdateHandMeshMSFT(mHandTracker, &updateInfo,
                                                            &mHandMeshMSFT.handMesh));
        mHandMeshMSFT.buffer = genericBuffer;

        // Finally add the current buffer to the list of used ones
        mHandMeshMSFT.usedBuffers.push_back(buffer);
    }

    return mHasHandJoints;
}

void
OpenXRInputSource::PopulateHandJointLocations(device::RenderMode renderMode, std::vector<vrb::Matrix>& jointTransforms, std::vector<float>& jointRadii) {
    jointTransforms.resize(mHandJoints.size());
    jointRadii.resize(mHandJoints.size());
    for (int i = 0; i < mHandJoints.size(); i++) {
        vrb::Matrix transform = XrPoseToMatrix(mHandJoints[i].pose);
        bool positionIsValid = IsHandJointPositionValid((XrHandJointEXT) i, mHandJoints);
        if (positionIsValid) {
            if (renderMode == device::RenderMode::StandAlone)
                transform.TranslateInPlace(kAverageHeight);
        } else {
            // This effectively hides the joint.
            transform.ScaleInPlace(vrb::Vector(0.0f, 0.0f, 0.0f));
        }

        jointTransforms[i] = transform;
        jointRadii[i] = mHandJoints[i].radius;
    }
}

void OpenXRInputSource::EmulateControllerFromHand(device::RenderMode renderMode, XrTime predictedDisplayTime, const vrb::Matrix& head, const vrb::Matrix& handJointForAim, DeviceDelegate::PointerMode pointerMode, bool usingEyeTracking, const vrb::Matrix& eyeTrackingTransform, ControllerDelegate& delegate)
{
    assert(mHasHandJoints);

    // This is not really needed. It's just an optimization for devices taking over the control of
    // hands when facing head. In those cases we don't need to do all the matrix computations.
    bool systemTakesOverWhenHandsFacingHead = false;
#if defined(OCULUSVR)
    systemTakesOverWhenHandsFacingHead = true;
#endif
    bool hasAim = mGestureManager->hasAim();
    bool systemGestureDetected = mGestureManager->systemGestureDetected(handJointForAim, head);

    if (!hasAim && !systemGestureDetected && !usingEyeTracking) {
        delegate.SetEnabled(mIndex, false);
        return;
    }

    // We should handle the gesture whenever the system does not handle it.
    bool isHandActionEnabled = systemGestureDetected && (!systemTakesOverWhenHandsFacingHead || mHandeness == Left);
    delegate.SetAimEnabled(mIndex, hasAim && pointerMode == DeviceDelegate::PointerMode::TRACKED_POINTER);
    delegate.SetHandActionEnabled(mIndex, isHandActionEnabled);
    delegate.SetMode(mIndex, ControllerMode::Hand);
    delegate.SetEnabled(mIndex, true);
    delegate.SetButtonCount(mIndex, 1);

    // Select action
    bool indexPinching = false;
    double pinchFactor = 0.0f;
    mGestureManager->getTriggerPinchStatusAndFactor(mHandJoints, indexPinching, pinchFactor);

    delegate.SetSelectFactor(mIndex, pinchFactor);
    bool triggerButtonPressed = indexPinching && !systemGestureDetected && hasAim;
    delegate.SetButtonState(mIndex, ControllerDelegate::BUTTON_TRIGGER,
                            device::kImmersiveButtonTrigger, triggerButtonPressed,
                            pinchFactor > 0, pinchFactor);
    if (isHandActionEnabled) {
        delegate.SetButtonState(mIndex, ControllerDelegate::BUTTON_APP, -1, indexPinching, indexPinching, 1.0);
    } else if (hasAim) {
        if (renderMode == device::RenderMode::Immersive && indexPinching != selectActionStarted) {
            selectActionStarted = indexPinching;
            if (selectActionStarted) {
                delegate.SetSelectActionStart(mIndex);
            } else {
                delegate.SetSelectActionStop(mIndex);
            }
        }
    }

    // Rest of the logic below requires having Aim info
    if (!hasAim)
        return;

    // Resolve beam and pointer transform
    vrb::Matrix pointerTransform = XrPoseToMatrix(mGestureManager->aimPose(predictedDisplayTime, mHandeness, head));

    // Both on Quest and Pico4 devices, hand pose returned by XR_FB_hand_tracking_aim appears
    // rotated relative to the corresponding pose of the controllers, and the rotation is
    // different for each hand in the case of Quest. So here correct the transformation matrix
    // by an angle that was obtained empirically.
#if defined(OCULUSVR)
    if (mSupportsFBHandTrackingAim) {
        float correctionAngle = (mHandeness == OpenXRHandFlags::Left) ? M_PI_2 : M_PI_4 * 3 / 2;
        auto correctionMatrix = vrb::Matrix::Rotation(vrb::Vector(0.0, 0.0, 1.0),
                                                      correctionAngle);
        pointerTransform = pointerTransform.PostMultiply(correctionMatrix);
    }
#endif

    device::CapabilityFlags flags = device::Orientation | device::Position;
    if (pointerMode == DeviceDelegate::PointerMode::TRACKED_POINTER) {
        if (renderMode == device::RenderMode::StandAlone)
            pointerTransform.TranslateInPlace(kAverageHeight);

        // TODO: removing this flag will allow us to remove the ifdef. Keeping it just to limit
        // the scope of the changes.
        flags |= device::GripSpacePosition;
#if CHROMIUM
        // Blink WebXR uses the grip space instead of the local space to position the
        // controller. Since controller's position is the same as the origin of the
        // grip space, we just set the transform matrix to the identity.
        // Then we need to correct the beam transform matrix to maintain origin and
        // direction when we are not in immersive mode.
        delegate.SetTransform(mIndex, vrb::Matrix::Identity());
        delegate.SetBeamTransform(mIndex, pointerTransform);
#else
        delegate.SetTransform(mIndex, pointerTransform);
        delegate.SetBeamTransform(mIndex, vrb::Matrix::Identity());
#endif
        delegate.SetImmersiveBeamTransform(mIndex, pointerTransform);
    } else {
        assert(pointerMode == DeviceDelegate::PointerMode::TRACKED_EYE);
        HandleEyeTrackingScroll(predictedDisplayTime, triggerButtonPressed, pointerTransform, eyeTrackingTransform, delegate);
        if (!triggerButtonPressed)
            delegate.SetTransform(mIndex, eyeTrackingTransform);
    }

    delegate.SetCapabilityFlags(mIndex, flags);
}

void OpenXRInputSource::Update(const XrFrameState& frameState, XrSpace localSpace, const vrb::Matrix &head, const vrb::Vector& offsets, device::RenderMode renderMode, DeviceDelegate::PointerMode pointerMode, bool usingEyeTracking, bool handTrackingEnabled, const vrb::Matrix& eyeTrackingTransform, ControllerDelegate& delegate)
{
    if (mActiveMapping &&
        ((mHandeness == OpenXRHandFlags::Left && !mActiveMapping->leftControllerModel) ||
         (mHandeness == OpenXRHandFlags::Right && !mActiveMapping->rightControllerModel))) {
      delegate.SetEnabled(mIndex, false);
      return;
    }

    delegate.SetLeftHanded(mIndex, mHandeness == OpenXRHandFlags::Left);
    delegate.SetTargetRayMode(mIndex, device::TargetRayMode::TrackedPointer);
    delegate.SetControllerType(mIndex, mDeviceType);

    // Spaces must be created here, it doesn't work if they are created in Initialize (probably a OpenXR SDK bug?)
    if (mGripSpace == XR_NULL_HANDLE) {
      CHECK_XRCMD(CreateActionSpace(mGripAction, mGripSpace));
    }
    if (mPointerSpace == XR_NULL_HANDLE) {
      CHECK_XRCMD(CreateActionSpace(mPointerAction, mPointerSpace));
    }
    if (OpenXRExtensions::IsExtensionSupported(XR_EXT_HAND_INTERACTION_EXTENSION_NAME)) {
      if (mPinchSpace == XR_NULL_HANDLE)
        CHECK_XRCMD(CreateActionSpace(mPinchPoseAction, mPinchSpace));
      if (mPokeSpace == XR_NULL_HANDLE)
        CHECK_XRCMD(CreateActionSpace(mPokePoseAction, mPokeSpace));
    }

    // Pose transforms.
    bool isPoseActive { false };
    XrSpaceLocation poseLocation { XR_TYPE_SPACE_LOCATION };
#if defined(CHROMIUM) && !defined(HVR)
    // Chromium's WebXR code expects aim space to be based on the grip space. The HVR runtime
    // is buggy and does not support that, so we keep using the local space here and will generate
    // an aim pose later after retrieving the grip pose.
    XrSpace baseSpace = renderMode == device::RenderMode::StandAlone ? localSpace : mGripSpace;
#else
    XrSpace baseSpace = localSpace;
#endif
    if (XR_FAILED(GetPoseState(mPointerAction,  mPointerSpace, baseSpace, frameState, isPoseActive, poseLocation))) {
        delegate.SetEnabled(mIndex, false);
        return;
    }

#if defined(PICOXR)
    // Pico does continuously track the controllers even when left alone. That's why we return
    // always true so that we always check hand tracking just in case.
    bool isControllerUnavailable = true;
#else
    bool isControllerUnavailable = (poseLocation.locationFlags & XR_SPACE_LOCATION_ORIENTATION_VALID_BIT) == 0;
#endif

    auto gotHandTrackingInfo = false;
    auto handFacesHead = false;
    if (isControllerUnavailable || mUsingHandInteractionProfile) {
        if (!handTrackingEnabled) {
            delegate.SetEnabled(mIndex, false);
            return;
        }
        gotHandTrackingInfo = GetHandTrackingInfo(frameState.predictedDisplayTime, localSpace, head);
        if (gotHandTrackingInfo) {
            std::vector<vrb::Matrix> jointTransforms;
            std::vector<float> jointRadii;
            PopulateHandJointLocations(renderMode, jointTransforms, jointRadii);
            if (!mIsHandInteractionSupported) {
                EmulateControllerFromHand(renderMode, frameState.predictedDisplayTime, head, jointTransforms[HAND_JOINT_FOR_AIM], pointerMode, usingEyeTracking, eyeTrackingTransform, delegate);
                delegate.SetHandJointLocations(mIndex, std::move(jointTransforms), std::move(jointRadii));
                return;
            }
            handFacesHead = OpenXRGestureManager::handFacesHead(jointTransforms[HAND_JOINT_FOR_AIM], head);
            delegate.SetHandJointLocations(mIndex, std::move(jointTransforms), std::move(jointRadii));
        }
    }

    if (!mActiveMapping) {
        delegate.SetEnabled(mIndex, false);
        return;
    }

    bool usingTrackedPointer = pointerMode == DeviceDelegate::PointerMode::TRACKED_POINTER;
    bool hasAim = isPoseActive && (poseLocation.locationFlags & XR_SPACE_LOCATION_ORIENTATION_VALID_BIT);

    // When using hand interaction profiles we still get valid aim even if the hand is facing
    // the head like when performing system gestures. In that case we don't want to show the beam.
    if (mUsingHandInteractionProfile)
        hasAim = hasAim && !handFacesHead;

    // Don't enable aim for eye tracking as we don't want to paint the beam. Note that we'd still
    // have an aim, meaning that we can point, focus, click... UI elements.
    delegate.SetAimEnabled(mIndex, hasAim && usingTrackedPointer);

    // Disable the controller if there is no aim unless:
    // a) we're using hand interaction profile and we have hand tracking info. In that case the user
    // is facing their palm to do a hand gesture (that's why there is no aim)
    // b) we're using eye tracking. In that case the eye gaze will be the aim, not the controller's
    if (!hasAim && !(mUsingHandInteractionProfile && gotHandTrackingInfo) && !usingEyeTracking) {
      delegate.SetEnabled(mIndex, false);
      return;
    }

    // HVR: adjust to local if app is using stageSpace.
    // Meta: adjust the position of the controllers which is not totally accurate.
    auto adjustPoseLocation = [&poseLocation](const vrb::Vector& offset) {
        poseLocation.pose.position.x += offset.x();
        poseLocation.pose.position.y += offset.y();
        poseLocation.pose.position.z += offset.z();
    };
    adjustPoseLocation(offsets);

    // Hand interaction profiles do not really require the hand joints data, but if we
    // set ControllerMode::Hand then Wolvic code assumes that it does.
    delegate.SetMode(mIndex, mUsingHandInteractionProfile && gotHandTrackingInfo ? ControllerMode::Hand : ControllerMode::Device);
    delegate.SetEnabled(mIndex, true);

    bool isHandActionEnabled = !hasAim && mUsingHandInteractionProfile && handFacesHead;
    delegate.SetHandActionEnabled(mIndex, isHandActionEnabled);

    device::CapabilityFlags flags = device::Orientation;
    const bool positionTracked = poseLocation.locationFlags & XR_SPACE_LOCATION_POSITION_TRACKED_BIT;
    flags |= positionTracked ? device::Position : device::PositionEmulated;

    vrb::Matrix pointerTransform = XrPoseToMatrix(poseLocation.pose);
    if (positionTracked) {
        if (renderMode == device::RenderMode::StandAlone) {
            pointerTransform.TranslateInPlace(kAverageHeight);
        }
    } else {
        auto hand = mHandeness == OpenXRHandFlags::Left ? ElbowModel::HandEnum::Left : ElbowModel::HandEnum::Right;
        pointerTransform = elbow->GetTransform(hand, head, pointerTransform);
    }

    if (pointerMode == DeviceDelegate::PointerMode::TRACKED_POINTER) {
        delegate.SetTransform(mIndex, pointerTransform);

        isPoseActive = false;
        poseLocation = {XR_TYPE_SPACE_LOCATION};
        CHECK_XRCMD(GetPoseState(mGripAction, mGripSpace, localSpace, frameState, isPoseActive,
                                 poseLocation));
        if (isPoseActive) {
            adjustPoseLocation(offsets);
            auto gripTransform = XrPoseToMatrix(poseLocation.pose);
            delegate.SetImmersiveBeamTransform(mIndex,
                                               positionTracked ? gripTransform : pointerTransform);
            flags |= device::GripSpacePosition;
            delegate.SetBeamTransform(mIndex, vrb::Matrix::Identity());
#if defined(CHROMIUM) && defined(HVR)
            // HVR runtime incorrectly return the same values for grip and aim poses. We circumvent that
            // by inventing a grip pose. The values are chosen to match the controller dimensions. This
            // is only needed for Chromium because it requires the grip to be based on the aim. As the
            // runtime returns invalid values for that case, we need to emulate the aim based on grip.
            if (renderMode == device::RenderMode::Immersive) {
                static const auto emulatedGripTransform = vrb::Matrix::Translation({0.0, 0.30, 0.0}).Rotation(vrb::Vector(1.0, 0.0, 0.0), -M_PI / 5);
                delegate.SetTransform(mIndex, emulatedGripTransform);
            }
#endif
        } else {
            delegate.SetImmersiveBeamTransform(mIndex, vrb::Matrix::Identity());
        }
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

        if (isHandActionEnabled && button.type == OpenXRButtonType::Trigger) {
            delegate.SetButtonState(mIndex, ControllerDelegate::BUTTON_APP, -1, state->value >= kClickThreshold,
                                    state->value > 0, 1.0);
        } else {
            delegate.SetButtonState(mIndex, browserButton, immersiveButton.has_value() ? immersiveButton.value() : -1,
                                    state->clicked, state->touched, state->value);
        }

        if (button.type == OpenXRButtonType::Trigger) {
            delegate.SetSelectFactor(mIndex, state->value);
            if (pointerMode == DeviceDelegate::PointerMode::TRACKED_EYE) {
                HandleEyeTrackingScroll(frameState.predictedDisplayTime, state->clicked, pointerTransform, eyeTrackingTransform, delegate);
                if (!state->clicked)
                    delegate.SetTransform(mIndex, eyeTrackingTransform);
            }
        }

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

    UpdateHaptics(delegate);
}

XrResult OpenXRInputSource::UpdateInteractionProfile(ControllerDelegate& delegate)
{
    const char* path = nullptr;
    size_t path_len = 0;


    XrInteractionProfileState state{XR_TYPE_INTERACTION_PROFILE_STATE};
    RETURN_IF_XR_FAILED(xrGetCurrentInteractionProfile(mSession, mSubactionPath, &state));
    if (state.interactionProfile == XR_NULL_PATH) {
        return XR_SUCCESS; // Not ready yet
    }

    constexpr uint32_t bufferSize = 100;
    char buffer[bufferSize];
    uint32_t writtenCount = 0;
    RETURN_IF_XR_FAILED(xrPathToString(mInstance, state.interactionProfile,
                                       bufferSize, &writtenCount,
                                       buffer));
    path = buffer;
    path_len = writtenCount;
    mActiveMapping = nullptr;
    bool pathDefinesHandInteractionProfile = !strncmp(path, kInteractionProfileHandInteraction, path_len) ||
                                             !strncmp(path, kInteractionProfileMSFTHandInteraction, path_len);

    for (auto& mapping : mMappings) {
        if (!strncmp(mapping.path, path, path_len)) {
            mActiveMapping = &mapping;
            mUsingHandInteractionProfile = pathDefinesHandInteractionProfile;
            VRB_LOG("OpenXR: NEW active mapping %s", mActiveMapping->path);
            break;
        }
    }

    uint32_t numHaptics = 0;
    if (mActiveMapping != nullptr) {
        // Add haptic devices to controller, if any
        for (auto& haptic: mActiveMapping->haptics) {
            if (haptic.hand == OpenXRHandFlags::Both || haptic.hand == mHandeness)
                numHaptics++;
        }
    }
    delegate.SetHapticCount(mIndex, numHaptics);

    return XR_SUCCESS;
}

std::string OpenXRInputSource::ControllerModelName() const
{
  if (mActiveMapping) {
    return mHandeness == OpenXRHandFlags::Left ? mActiveMapping->leftControllerModel : mActiveMapping->rightControllerModel;
  }
  return { };
}

void OpenXRInputSource::SetHandMeshBufferSizes(const uint32_t indexCount, const uint32_t vertexCount) {
    mHandMeshMSFT.handMesh.indexBuffer.indexCapacityInput = indexCount;
    mHandMeshMSFT.handMesh.vertexBuffer.vertexCapacityInput = vertexCount;
}

HandMeshBufferPtr
OpenXRInputSource::GetNextHandMeshBuffer() {
    ReleaseHandMeshBuffer();
    return mHandMeshMSFT.buffer;
}

void
OpenXRInputSource::HandleEyeTrackingScroll(XrTime predictedDisplayTime, bool triggerClicked, const vrb::Matrix &pointerTransform, const vrb::Matrix& eyeTrackingTransform, ControllerDelegate &controllerDelegate) {
    if (!mTriggerWasClicked && triggerClicked) {
        mEyeGazeTransformOnPinchStart = eyeTrackingTransform;
        mEyeTrackingPinchStartTime = predictedDisplayTime;
    } else if (mTriggerWasClicked && triggerClicked) {
        // Throttle the start of the scroll gesture to avoid scrolling on pinch.
        if (predictedDisplayTime - mEyeTrackingPinchStartTime > kEyeTrackingScrollThreshold) {
            vrb::Vector currentControllerPosition = pointerTransform.GetTranslation();
            auto delta = currentControllerPosition - mControllerPositionOnScrollStart;
            controllerDelegate.SetTransform(mIndex, mEyeGazeTransformOnPinchStart.Translate(delta * 10));
        } else {
            // Keep updating the initial position while throttling to avoid a sudden jump when
            // scrolling starts.
            mControllerPositionOnScrollStart =  pointerTransform.GetTranslation();
        }
    }
    mTriggerWasClicked = triggerClicked;
}

bool OpenXRInputSource::HasPhysicalControllersAvailable() const {
    return mActiveMapping && (!mUsingHandInteractionProfile || mMappings.size() > 1);
}

} // namespace crow
