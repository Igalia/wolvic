#include "OpenXRInputSource.h"
#include "OpenXRExtensions.h"
#include <assert.h>
#include <unordered_set>
#include "SystemUtils.h"

namespace crow {

// Threshold to consider a trigger value as a click
// Used when devices don't map the click value for triggers;
const float kClickThreshold = 0.91f;

// Distance threshold to consider that two hand joints touch
// Used to detect pinch events between thumb-tip joint and the
// rest of the finger tips.
const float kPinchThreshold = 0.019;

// These two are used to measure a pinch factor between [0,1]
// between the thumb and the index fingertips, where 0 is no
// pinch at all and 1.0 means fingers are touching. These
// value is used to give a visual cue to the user (e.g, size
// of pointer target).
const float kPinchStart = 0.055;
const float kPinchRange = kPinchStart - kPinchThreshold;

// This threshold specifies the minimum size of the normalized vector
// resulting from the sum of the head and the left palm
// pose. It is used to detect when palm is facing the head, and enabled
// the left hand action gesture.
const float kPalmHeadRotationZThreshold = 0.5;

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

    // Initialize Action Set.
    std::string prefix = std::string("input_") + (mHandeness == OpenXRHandFlags::Left ? "left" : "right");

    // Initialize pose actions and spaces.
    RETURN_IF_XR_FAILED(mActionSet.GetOrCreateAction(XR_ACTION_TYPE_POSE_INPUT, "grip", OpenXRHandFlags::Both, mGripAction));
    RETURN_IF_XR_FAILED(mActionSet.GetOrCreateAction(XR_ACTION_TYPE_POSE_INPUT, "pointer", OpenXRHandFlags::Both, mPointerAction));
    RETURN_IF_XR_FAILED(mActionSet.GetOrCreateAction(XR_ACTION_TYPE_VIBRATION_OUTPUT, "haptic", OpenXRHandFlags::Both, mHapticAction));

    // Filter mappings
    for (auto& mapping: OpenXRInputMappings) {
      const char* systemFilter = mapping.systemFilter;
#if defined(PICOXR)
      // Pico versions before 5.4.0 use a different system id.
      if (mapping.controllerType == device::PicoXR) {
          char buildId[128] = {0};
          if (CompareSemanticVersionStrings(GetBuildIdString(buildId), "5.4.0")) {
              // System version is < 5.4.0
              systemFilter = "Pico: PICO HMD";
          }
      }
#endif
      if (systemFilter && strcmp(systemFilter, mSystemProperties.systemName) != 0) {
        continue;
      }
      bool systemIs6DoF = mSystemProperties.trackingProperties.positionTracking == XR_TRUE;
      bool mappingIs6DoF = mapping.systemDoF == DoF::IS_6DOF;
      if (mappingIs6DoF != systemIs6DoF)
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

#if defined(PICOXR)
        // Pico's runtime does not advertise it but it does work.
        mSupportsFBHandTrackingAim = true;
#else
        mSupportsFBHandTrackingAim = OpenXRExtensions::IsExtensionSupported(XR_FB_HAND_TRACKING_AIM_EXTENSION_NAME);
#endif
        VRB_LOG("OpenXR: using %s to compute hands aim", mSupportsFBHandTrackingAim ? "XR_FB_HAND_TRACKING_AIM" : "hand joints");

        if (OpenXRExtensions::IsExtensionSupported(XR_FB_HAND_TRACKING_MESH_EXTENSION_NAME) &&
                OpenXRExtensions::sXrGetHandMeshFB != XR_NULL_HANDLE) {
            XrHandTrackingMeshFB mesh = { XR_TYPE_HAND_TRACKING_MESH_FB };
            // Figure out sizes first
            mesh.jointCapacityInput = 0;
            mesh.vertexCapacityInput = 0;
            mesh.indexCapacityInput = 0;
            CHECK_XRCMD(OpenXRExtensions::sXrGetHandMeshFB(mHandTracker, &mesh));
            mesh.jointCapacityInput = mesh.jointCountOutput;
            mesh.vertexCapacityInput = mesh.vertexCountOutput;
            mesh.indexCapacityInput = mesh.indexCountOutput;

            // Skeleton
            mHandMesh.jointCount = mesh.jointCountOutput;
            mHandMesh.jointPoses.resize(mesh.jointCountOutput);
            mHandMesh.jointParents.resize(mesh.jointCountOutput);
            mHandMesh.jointRadii.resize(mesh.jointCountOutput);
            mesh.jointBindPoses = mHandMesh.jointPoses.data();
            mesh.jointParents = mHandMesh.jointParents.data();
            mesh.jointRadii = mHandMesh.jointRadii.data();
            // Vertex
            mHandMesh.vertexCount = mesh.vertexCountOutput;
            mHandMesh.vertexPositions.resize(mesh.vertexCountOutput);
            mHandMesh.vertexNormals.resize(mesh.vertexCountOutput);
            mHandMesh.vertexUVs.resize(mesh.vertexCountOutput);
            mHandMesh.vertexBlendIndices.resize(mesh.vertexCountOutput);
            mHandMesh.vertexBlendWeights.resize(mesh.vertexCountOutput);
            mesh.vertexPositions = mHandMesh.vertexPositions.data();
            mesh.vertexNormals = mHandMesh.vertexNormals.data();
            mesh.vertexUVs = mHandMesh.vertexUVs.data();
            mesh.vertexBlendIndices = mHandMesh.vertexBlendIndices.data();
            mesh.vertexBlendWeights = mHandMesh.vertexBlendWeights.data();
            // Index
            mHandMesh.indexCount = mesh.indexCountOutput;
            mHandMesh.indices.resize(mesh.indexCountOutput);
            mesh.indices = mHandMesh.indices.data();

            // Now get the actual mesh
            RETURN_IF_XR_FAILED(OpenXRExtensions::sXrGetHandMeshFB(mHandTracker, &mesh));
            mHasHandMesh = true;
            VRB_LOG("xrGetHandMeshFB: %u, %u, %u", mHandMesh.jointCount, mHandMesh.vertexCount, mHandMesh.indexCount);
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

#ifdef SPACES
bool shouldConsiderPoseAsValid(const XrPosef& pose) {
    // A bug in spaces leaves the locationFlags always empty. The best we can do is to check that
    // all positions are not 0.0 (which is what the runtime returns when they aren't tracked).
    return pose.position.x != 0.0 && pose.position.y != 0.0 && pose.position.z != 0.0;
}
#endif

bool OpenXRInputSource::GetHandTrackingInfo(const XrFrameState& frameState, XrSpace localSpace) {
    if (OpenXRExtensions::sXrLocateHandJointsEXT == XR_NULL_HANDLE || mHandTracker == XR_NULL_HANDLE)
        return false;

    // Update hand locations
    XrHandJointsLocateInfoEXT locateInfo { XR_TYPE_HAND_JOINTS_LOCATE_INFO_EXT };
    locateInfo.baseSpace = localSpace;
    locateInfo.time = frameState.predictedDisplayTime;

    XrHandTrackingAimStateFB aimState { XR_TYPE_HAND_TRACKING_AIM_STATE_FB, XR_NULL_HANDLE, 0  };
    XrHandJointLocationsEXT jointLocations { XR_TYPE_HAND_JOINT_LOCATIONS_EXT };
    jointLocations.jointCount = XR_HAND_JOINT_COUNT_EXT;
    jointLocations.jointLocations = mHandJoints.data();
    jointLocations.next = &aimState;

    CHECK_XRCMD(OpenXRExtensions::sXrLocateHandJointsEXT(mHandTracker, &locateInfo, &jointLocations));
    mHasHandJoints = jointLocations.isActive;
#if defined(SPACES)
    // Bug in Spaces runtime, isActive returns always false
    mHasHandJoints = true;
#endif
    if (mSupportsFBHandTrackingAim) {
        mHasAimState = aimState.status & XR_HAND_TRACKING_AIM_VALID_BIT_FB;
        if (mHasAimState)
            mHandAimPose = aimState.aimPose;
    } else {
        auto aimJoint = jointLocations.jointLocations[XR_HAND_JOINT_PALM_EXT];
        mHasAimState = aimJoint.locationFlags & XR_SPACE_LOCATION_ORIENTATION_TRACKED_BIT;
#if defined(SPACES)
        mHasAimState = shouldConsiderPoseAsValid(aimJoint.pose);
#endif
        if (mHasAimState)
            mHandAimPose = aimJoint.pose;
    }

    return mHasHandJoints;
}

float OpenXRInputSource::GetDistanceBetweenJoints (XrHandJointEXT jointA, XrHandJointEXT jointB)
{
    XrVector3f jointAPosXr = mHandJoints[jointA].pose.position;
    vrb::Vector jointAPos = vrb::Vector(jointAPosXr.x, jointAPosXr.y, jointAPosXr.z);

    XrVector3f jointBPosXr = mHandJoints[jointB].pose.position;
    vrb::Vector jointBPos = vrb::Vector(jointBPosXr.x, jointBPosXr.y, jointBPosXr.z);

    return vrb::Vector(jointAPos - jointBPos).Magnitude();
}

bool OpenXRInputSource::IsHandJointPositionValid(const enum XrHandJointEXT aJoint) {
    if (aJoint >= mHandJoints.size())
        return false;
    return (mHandJoints[aJoint].locationFlags & XR_SPACE_LOCATION_POSITION_VALID_BIT) != 0;
}

void OpenXRInputSource::EmulateControllerFromHand(device::RenderMode renderMode, const vrb::Matrix& head, ControllerDelegate& delegate)
{
    // Prepare and submit hand joint locations data for rendering
    assert(mHasHandJoints);
    std::vector<vrb::Matrix> jointTransforms;
    jointTransforms.resize(mHandJoints.size());
    for (int i = 0; i < mHandJoints.size(); i++) {
        vrb::Matrix transform = XrPoseToMatrix(mHandJoints[i].pose);
        bool positionIsValid = IsHandJointPositionValid((XrHandJointEXT) i);
#if defined(SPACES)
        positionIsValid = shouldConsiderPoseAsValid(mHandJoints[i].pose);
#endif
        if (positionIsValid) {
            if (renderMode == device::RenderMode::StandAlone)
                transform.TranslateInPlace(kAverageHeight);
        } else {
            // This effectively hides the joint.
            transform.ScaleInPlace(vrb::Vector(0.0f, 0.0f, 0.0f));
        }

        jointTransforms[i] = transform;
    }

    // Check whether palm of left hand is facing the head, to show navigate-back button
    bool leftPalmFacesHead = false;
    if (mHandeness == OpenXRHandFlags::Left) {
#if defined(OCULUSVR)
        if (IsHandJointPositionValid(XR_HAND_JOINT_PALM_EXT))
            leftPalmFacesHead = !mHasAimState;
#else
        XrHandJointEXT palmJointIndex = XR_HAND_JOINT_PALM_EXT;
#if defined(PICOXR)
        // Pico runtime doesn't provide palm joint info, so we use the wrist instead.
        palmJointIndex = XR_HAND_JOINT_WRIST_EXT;
#endif
        if (IsHandJointPositionValid(palmJointIndex)) {
            vrb::Vector vector = vrb::Vector(0.0f, 0.0f, 1.0f);
            vrb::Matrix palmMatrix = jointTransforms[palmJointIndex];
            vrb::Matrix headPalmMatrix = head.Inverse().PostMultiply(palmMatrix);
            vrb::Vector diffVector = headPalmMatrix.MultiplyPosition(vector).Normalize();
            leftPalmFacesHead = diffVector.z() >= kPalmHeadRotationZThreshold;
        }
#endif
    }

    // Scale joints according to their radius (for rendering)
    for (int i = 0; i < mHandJoints.size(); i++) {
        if (IsHandJointPositionValid((XrHandJointEXT) i)) {
            float radius = mHandJoints[i].radius;
            vrb::Matrix scale = vrb::Matrix::Identity().ScaleInPlace(
                    vrb::Vector(radius, radius, radius));
            jointTransforms[i].PostMultiplyInPlace(scale);
        }
    }

    delegate.SetHandJointLocations(mIndex, jointTransforms);
    delegate.SetAimEnabled(mIndex, mHasAimState);
    delegate.SetLeftHandActionEnabled(mIndex, leftPalmFacesHead);
    delegate.SetMode(mIndex, ControllerMode::Hand);
    delegate.SetEnabled(mIndex, true);

    // Select action
    bool indexPinching = false;
    double pinchFactor = 0.0f;
    if (IsHandJointPositionValid(XR_HAND_JOINT_THUMB_TIP_EXT) &&
        IsHandJointPositionValid(XR_HAND_JOINT_INDEX_TIP_EXT)) {
        const double indexThumbDistance = GetDistanceBetweenJoints(XR_HAND_JOINT_THUMB_TIP_EXT,
                                                                   XR_HAND_JOINT_INDEX_TIP_EXT);
        pinchFactor = 1.0 - std::clamp((indexThumbDistance - kPinchThreshold) / kPinchRange, 0.0, 1.0);
        indexPinching = indexThumbDistance < kPinchThreshold;
    }
    delegate.SetPinchFactor(mIndex, pinchFactor);
    delegate.SetButtonState(mIndex, ControllerDelegate::BUTTON_TRIGGER,
                            device::kImmersiveButtonTrigger, indexPinching && !leftPalmFacesHead,
                            indexPinching && !leftPalmFacesHead, 1.0);
    if (leftPalmFacesHead) {
        delegate.SetButtonState(mIndex, ControllerDelegate::BUTTON_APP, -1, indexPinching, indexPinching, 1.0);
    } else {
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
    if (!mHasAimState)
        return;

    // Resolve beam and pointer transform
    vrb::Matrix pointerTransform = XrPoseToMatrix(mHandAimPose);

    // Both on Quest and Pico4 devices, hand pose returned by XR_FB_hand_tracking_aim appears
    // rotated relative to the corresponding pose of the controllers, and the rotation is
    // different for each hand in the case of Quest. So here correct the transformation matrix
    // by an angle that was obtained empirically.
#if defined(OCULUSVR)
    float correctionAngle = (mHandeness == OpenXRHandFlags::Left) ? M_PI_2 : M_PI_4 * 3/2;
    auto correctionMatrix = vrb::Matrix::Rotation(vrb::Vector(0.0, 0.0, 1.0),
                                                  correctionAngle);
    pointerTransform = pointerTransform.PostMultiply(correctionMatrix);
#elif defined(PICOXR)
    float correctionAngle = -M_PI_2;
    pointerTransform
        .PostMultiplyInPlace(vrb::Matrix::Rotation(vrb::Vector(0.0, 1.0, 0.0),correctionAngle)
        .PostMultiply(vrb::Matrix::Rotation(vrb::Vector(0.0, 0.0, 1.0), correctionAngle)));
#elif defined(LYNX)
    auto vector = mHandeness == OpenXRHandFlags::Left ? vrb::Vector(0.0, 0.5, 1.0) : vrb::Vector(-0.5, 0.0, 1.0);
    pointerTransform.PostMultiplyInPlace(vrb::Matrix::Rotation(vector, M_PI_2));
#endif

    if (renderMode == device::RenderMode::StandAlone)
        pointerTransform.TranslateInPlace(kAverageHeight);

    delegate.SetTransform(mIndex, pointerTransform);
    delegate.SetImmersiveBeamTransform(mIndex, pointerTransform);
    delegate.SetBeamTransform(mIndex, vrb::Matrix::Identity());

    device::CapabilityFlags flags = device::Orientation | device::Position | device::GripSpacePosition;
    delegate.SetCapabilityFlags(mIndex, flags);

    // Squeeze action
    bool middlePinching = false;
    if (IsHandJointPositionValid(XR_HAND_JOINT_THUMB_TIP_EXT) &&
        IsHandJointPositionValid(XR_HAND_JOINT_MIDDLE_TIP_EXT)) {
        middlePinching = GetDistanceBetweenJoints(XR_HAND_JOINT_THUMB_TIP_EXT,
                                                  XR_HAND_JOINT_MIDDLE_TIP_EXT) < kPinchThreshold;
    }
    delegate.SetButtonState(mIndex, ControllerDelegate::BUTTON_SQUEEZE,
                            device::kImmersiveButtonSqueeze, middlePinching,
                            middlePinching, 1.0);

    if (renderMode == device::RenderMode::Immersive && middlePinching != squeezeActionStarted) {
        squeezeActionStarted = middlePinching;
        if (squeezeActionStarted) {
            delegate.SetSqueezeActionStart(mIndex);
        } else {
            delegate.SetSqueezeActionStop(mIndex);
        }
    }
}

void OpenXRInputSource::Update(const XrFrameState& frameState, XrSpace localSpace, const vrb::Matrix& head, const vrb::Vector& offsets, device::RenderMode renderMode, ControllerDelegate& delegate)
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

    // If hand tracking is active, use it to emulate the controller.
    if (GetHandTrackingInfo(frameState, localSpace)) {
        EmulateControllerFromHand(renderMode, head, delegate);
        return;
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

    // HVR: adjust to local if app is using stageSpace.
    // Meta: adjust the position of the controllers which is not totally accurate.
    auto adjustPoseLocation = [&poseLocation](const vrb::Vector& offset) {
        poseLocation.pose.position.x += offset.x();
        poseLocation.pose.position.y += offset.y();
        poseLocation.pose.position.z += offset.z();
    };
    adjustPoseLocation(offsets);

    delegate.SetMode(mIndex, ControllerMode::Device);
    delegate.SetEnabled(mIndex, true);
    delegate.SetAimEnabled(mIndex, true);

    device::CapabilityFlags flags = device::Orientation;
    vrb::Matrix pointerTransform = XrPoseToMatrix(poseLocation.pose);

    const bool positionTracked = poseLocation.locationFlags & XR_SPACE_LOCATION_POSITION_TRACKED_BIT;
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
        adjustPoseLocation(offsets);
        auto gripTransform = XrPoseToMatrix(poseLocation.pose);
        bool hasPosition = poseLocation.locationFlags & XR_SPACE_LOCATION_POSITION_TRACKED_BIT;
        delegate.SetImmersiveBeamTransform(mIndex, hasPosition ? gripTransform : pointerTransform);
        flags |= device::GripSpacePosition;
        delegate.SetBeamTransform(mIndex, vrb::Matrix::Identity());
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

    UpdateHaptics(delegate);
}

XrResult OpenXRInputSource::UpdateInteractionProfile(ControllerDelegate& delegate, const char* emulateProfile)
{
    const char* path = nullptr;
    size_t path_len = 0;

    if (emulateProfile == nullptr) {
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
    } else {
        path = emulateProfile;
        path_len = strlen(emulateProfile);
    }

    mActiveMapping = nullptr;

    for (auto& mapping : mMappings) {
        if (!strncmp(mapping.path, path, path_len)) {
            mActiveMapping = &mapping;
            break;
        }
    }

    if (mActiveMapping != nullptr) {
        // Add haptic devices to controller, if any
        uint32_t numHaptics = 0;
        for (auto& haptic: mActiveMapping->haptics) {
            if (haptic.hand == OpenXRHandFlags::Both || haptic.hand == mHandeness)
                numHaptics++;
        }
        delegate.SetHapticCount(mIndex, numHaptics);

        // On emulated profiles we need to set the button count here because it
        // may never be set during Update() (e.g, when hand tracking is active).
        if (emulateProfile) {
            int buttonCount { 0 };
            for (auto &button: mActiveMapping->buttons) {
                if ((button.hand & mHandeness) == 0) {
                    continue;
                }
                buttonCount++;
            }
            delegate.SetButtonCount(mIndex, buttonCount);
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
