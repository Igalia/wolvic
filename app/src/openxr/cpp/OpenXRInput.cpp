#include "OpenXRInput.h"
#include "OpenXRHelpers.h"
#include <vector>

namespace crow {

// Threshold to consider a trigger value as a click
// Used when devices don't map the click value for triggers;
const float kPressThreshold = 0.95f;


OpenXRInputPtr OpenXRInput::Create(XrInstance instance, XrSystemProperties systemProperties) {
  CHECK(instance != XR_NULL_HANDLE);
  auto result = std::make_shared<OpenXRInput>();
  result->instance = instance;
  result->systemProperties = systemProperties;
  return result;
}

void
OpenXRInput::Initialize(XrSession session) {
  CHECK(session != XR_NULL_HANDLE);

  // Create the main action set.
  {
    XrActionSetCreateInfo actionSetInfo{XR_TYPE_ACTION_SET_CREATE_INFO};
    strcpy(actionSetInfo.actionSetName, "browser");
    strcpy(actionSetInfo.localizedActionSetName, "Browser");
    actionSetInfo.priority = 0;
    CHECK_XRCMD(xrCreateActionSet(instance, &actionSetInfo, &actionSet));
  }

  // Create subactions for left and right hands.
  CHECK_XRCMD(xrStringToPath(instance, "/user/hand/left", &handSubactionPath[Hand::Left]));
  CHECK_XRCMD(xrStringToPath(instance, "/user/hand/right", &handSubactionPath[Hand::Right]));


  auto createAction = [&](const char * name, Hand hand, XrActionType actionType, XrAction * action) {
    std::string actionName = std::string(name) + (hand == 0 ? "_left" : "_right");
    XrActionCreateInfo actionInfo{XR_TYPE_ACTION_CREATE_INFO};
    actionInfo.actionType = actionType;
    strcpy(actionInfo.actionName, actionName.c_str());
    strcpy(actionInfo.localizedActionName, actionName.c_str());
    actionInfo.countSubactionPaths = 1;
    actionInfo.subactionPaths = &handSubactionPath[hand];
    CHECK_XRCMD(xrCreateAction(actionSet, &actionInfo, action));
  };

  auto createPoseAction = [&](const char * name, std::array<XrAction, 2>& action){
    for (auto hand: {Hand::Left, Hand::Right}) {
      createAction(name, hand, XR_ACTION_TYPE_POSE_INPUT, &action[hand]);
    }
  };

  auto createBooleanAction = [&](const char * name, std::array<XrAction, 2>& action){
    for (auto hand: {Hand::Left, Hand::Right}) {
      createAction(name, hand, XR_ACTION_TYPE_BOOLEAN_INPUT, &action[hand]);
    }
  };

  auto createFloatAction = [&](const char * name, std::array<XrAction, 2>& action){
    for (auto hand: {Hand::Left, Hand::Right}) {
      createAction(name, hand, XR_ACTION_TYPE_FLOAT_INPUT, &action[hand]);
    }
  };

  // Create actions. We try to mimic https://www.w3.org/TR/webxr-gamepads-module-1/#xr-standard-gamepad-mapping
  // Create an input action for getting the left and right hand poses.
  createPoseAction("hand_pose", actionPose);

  // Create input actions for menu click detection, usually used for back action.
  createBooleanAction("menu", actionMenuClick);

  // Create an input action for trigger click, touch and value detection
  createBooleanAction("trigger_click", actionTriggerClick);
  createBooleanAction("trigger_touch", actionTriggerTouch);
  createFloatAction("trigger_value", actionTriggerValue);

  // Create an input action for squeeze click and value detection
  createBooleanAction("squeeze_click", actionSqueezeClick);
  createFloatAction("squeeze_value",  actionSqueezeValue);

  // Create an input action for trackpad click, touch and values detection
  createBooleanAction("trackpad_click", actionTrackpadClick);
  createBooleanAction("trackpad_touch", actionTrackpadTouch);
  createFloatAction("trackpad_value_x", actionTrackpadX);
  createFloatAction("trackpad_value_y", actionTrackpadY);

  // Create an input action for thumbstick click, touch and values detection
  createBooleanAction("thumbstick_click", actionThumbstickClick);
  createBooleanAction("thumbstick_touch", actionThumbstickTouch);
  createFloatAction("thumbstick_value_x", actionThumbstickX);
  createFloatAction("thumbstick_value_y", actionThumbstickY);

  // Create an input action for ButtonA and Button B clicks and touch
  createBooleanAction("button_a_click", actionButtonAClick);
  createBooleanAction("button_a_touch", actionButtonATouch);
  createBooleanAction("button_b_click", actionButtonBClick);
  createBooleanAction("button_b_touch", actionButtonBTouch);

  // See https://www.khronos.org/registry/OpenXR/specs/1.0/html/xrspec.html#semantic-path-interaction-profiles
#define DECLARE_PATH(subpath, variable) \
  std::array<XrPath, Hand::Count> variable; \
  CHECK_XRCMD(xrStringToPath(instance, "/user/hand/left/" subpath, &variable[Hand::Left])); \
  CHECK_XRCMD(xrStringToPath(instance, "/user/hand/right/" subpath, &variable[Hand::Right]));

  DECLARE_PATH("input/select/click", selectClickPath);
  DECLARE_PATH("input/trigger/value", triggerValuePath);
  DECLARE_PATH("input/trigger/touch", triggerTouchPath);
  DECLARE_PATH("input/trigger/click", triggerClickPath);
  DECLARE_PATH("input/squeeze/value", squeezeValuePath);
  DECLARE_PATH("input/squeeze/click", squeezeClickPath);
  DECLARE_PATH("input/aim/pose", posePath);
  DECLARE_PATH("output/haptic", hapticPath);
  DECLARE_PATH("input/menu/click", menuClickPath);
  DECLARE_PATH("input/back/click", backClickPath);
  DECLARE_PATH("input/trackpad/click", trackpadClickPath);
  DECLARE_PATH("input/trackpad/touch", trackpadTouchPath);
  DECLARE_PATH("input/trackpad/x", trackpadXPath);
  DECLARE_PATH("input/trackpad/y", trackpadYPath);
  DECLARE_PATH("input/thumbstick/click", thumbstickClickPath);
  DECLARE_PATH("input/thumbstick/touch", thumbstickTouchPath);
  DECLARE_PATH("input/thumbstick/x", thumbstickXPath);
  DECLARE_PATH("input/thumbstick/y", thumbstickYPath);
  DECLARE_PATH("input/a/click", buttonAClickPath);
  DECLARE_PATH("input/a/touch", buttonATouchPath);
  DECLARE_PATH("input/b/click", buttonBClickPath);
  DECLARE_PATH("input/b/touch", buttonBTouchPath);
  DECLARE_PATH("input/x/click", buttonXClickPath);
  DECLARE_PATH("input/x/touch", buttonXTouchPath);
  DECLARE_PATH("input/y/click", buttonYClickPath);
  DECLARE_PATH("input/y/touch", buttonYTouchPath);

  // Suggest bindings for KHR Simple. Default fallback when we have not implemented a specific controller binding.
  {
    XrPath khrSimpleInteractionProfilePath;
    CHECK_XRCMD(
        xrStringToPath(instance, "/interaction_profiles/khr/simple_controller", &khrSimpleInteractionProfilePath));
    std::vector<XrActionSuggestedBinding> bindings{{// Generic controller mappings
                                                     {actionPose[Hand::Left], posePath[Hand::Left]},
                                                     {actionPose[Hand::Right], posePath[Hand::Right]},
                                                     {actionMenuClick[Hand::Left], menuClickPath[Hand::Left]},
                                                     {actionMenuClick[Hand::Right], menuClickPath[Hand::Right]},
                                                     {actionTriggerClick[Hand::Left], selectClickPath[Hand::Left]},
                                                     {actionTriggerClick[Hand::Right], selectClickPath[Hand::Right]}}};
    XrInteractionProfileSuggestedBinding suggestedBindings{XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING};
    suggestedBindings.interactionProfile = khrSimpleInteractionProfilePath;
    suggestedBindings.suggestedBindings = bindings.data();
    suggestedBindings.countSuggestedBindings = (uint32_t)bindings.size();
    CHECK_XRCMD(xrSuggestInteractionProfileBindings(instance, &suggestedBindings));
  }

  // Suggest bindings for Oculus Go controller
  {

  }

  // Suggest bindings for Oculus Touch controller.
  {
    XrPath khrSimpleInteractionProfilePath;
    CHECK_XRCMD(
        xrStringToPath(instance, "/interaction_profiles/oculus/touch_controller", &khrSimpleInteractionProfilePath));
    std::vector<XrActionSuggestedBinding> bindings{{// Controller mappings
                                                       {actionPose[Hand::Left], posePath[Hand::Left]},
                                                       {actionPose[Hand::Right], posePath[Hand::Right]},
                                                       // Actions available only on left controller
                                                       {actionMenuClick[Hand::Left], menuClickPath[Hand::Left]},
                                                       {actionButtonAClick[Hand::Left], buttonXClickPath[Hand::Left]},
                                                       {actionButtonATouch[Hand::Left], buttonXTouchPath[Hand::Left]},
                                                       {actionButtonBClick[Hand::Left], buttonYClickPath[Hand::Left]},
                                                       {actionButtonBTouch[Hand::Left], buttonYTouchPath[Hand::Left]},
                                                       // Actions available only on right controller
                                                       {actionButtonAClick[Hand::Right], buttonAClickPath[Hand::Right]},
                                                       {actionButtonATouch[Hand::Right], buttonATouchPath[Hand::Right]},
                                                       {actionButtonBClick[Hand::Right], buttonAClickPath[Hand::Right]},
                                                       {actionButtonBTouch[Hand::Right], buttonATouchPath[Hand::Right]},
                                                       // Actions available on both controllers
                                                       {actionTriggerValue[Hand::Left], triggerValuePath[Hand::Left]},
                                                       {actionTriggerValue[Hand::Right], triggerValuePath[Hand::Right]},
                                                       {actionTriggerTouch[Hand::Left], triggerTouchPath[Hand::Left]},
                                                       {actionTriggerTouch[Hand::Right], triggerTouchPath[Hand::Right]},
                                                       {actionSqueezeValue[Hand::Left], squeezeValuePath[Hand::Left]},
                                                       {actionSqueezeValue[Hand::Right], squeezeValuePath[Hand::Right]},
                                                       {actionThumbstickClick[Hand::Left], thumbstickClickPath[Hand::Left]},
                                                       {actionThumbstickClick[Hand::Right], thumbstickClickPath[Hand::Right]},
                                                       {actionThumbstickTouch[Hand::Left], thumbstickTouchPath[Hand::Left]},
                                                       {actionThumbstickTouch[Hand::Right], thumbstickTouchPath[Hand::Right]},
                                                       {actionThumbstickX[Hand::Left], thumbstickXPath[Hand::Left]},
                                                       {actionThumbstickX[Hand::Right], thumbstickXPath[Hand::Right]},
                                                       {actionThumbstickY[Hand::Left], thumbstickYPath[Hand::Left]},
                                                       {actionThumbstickY[Hand::Right], thumbstickYPath[Hand::Right]}}};
    XrInteractionProfileSuggestedBinding suggestedBindings{XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING};
    suggestedBindings.interactionProfile = khrSimpleInteractionProfilePath;
    suggestedBindings.suggestedBindings = bindings.data();
    suggestedBindings.countSuggestedBindings = (uint32_t)bindings.size();
    CHECK_XRCMD(xrSuggestInteractionProfileBindings(instance, &suggestedBindings));
  }

  // Initialize pose actions
  {
    XrActionSpaceCreateInfo actionSpaceInfo{XR_TYPE_ACTION_SPACE_CREATE_INFO};
    actionSpaceInfo.action = actionPose[Hand::Left];
    actionSpaceInfo.poseInActionSpace.orientation.w = 1.f;
    actionSpaceInfo.subactionPath = handSubactionPath[Hand::Left];
    CHECK_XRCMD(xrCreateActionSpace(session, &actionSpaceInfo, &controllerState[Hand::Left].space));
  }
  {
    XrActionSpaceCreateInfo actionSpaceInfo{XR_TYPE_ACTION_SPACE_CREATE_INFO};
    actionSpaceInfo.action = actionPose[Hand::Right];
    actionSpaceInfo.poseInActionSpace.orientation.w = 1.f;
    actionSpaceInfo.subactionPath = handSubactionPath[Hand::Right];
    CHECK_XRCMD(xrCreateActionSpace(session, &actionSpaceInfo, &controllerState[Hand::Right].space));
  }

  // Attach actions to session
  XrSessionActionSetsAttachInfo attachInfo{XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO};
  attachInfo.countActionSets = 1;
  attachInfo.actionSets = &actionSet;
  CHECK_XRCMD(xrAttachSessionActionSets(session, &attachInfo));
}

void OpenXRInput::Update(XrSession session, XrTime predictedDisplayTime, XrSpace baseSpace, device::RenderMode renderMode, ControllerDelegatePtr& delegate) {
  CHECK(session != XR_NULL_HANDLE);

  // Sync actions
  const XrActiveActionSet activeActionSet{actionSet, XR_NULL_PATH};
  XrActionsSyncInfo syncInfo{XR_TYPE_ACTIONS_SYNC_INFO};
  syncInfo.countActiveActionSets = 1;
  syncInfo.activeActionSets = &activeActionSet;
  CHECK_XRCMD(xrSyncActions(session, &syncInfo));

  // Query actions and pose state for each hand
  for (auto hand : {Hand::Left, Hand::Right}) {
    const int index = hand;
    ControllerState& controller = controllerState[hand];

    // Query pose state
    XrActionStateGetInfo getInfo{XR_TYPE_ACTION_STATE_GET_INFO};
    getInfo.subactionPath = handSubactionPath[hand];
    getInfo.action = actionPose[hand];
    XrActionStatePose poseState{XR_TYPE_ACTION_STATE_POSE};
    CHECK_XRCMD(xrGetActionStatePose(session, &getInfo, &poseState));

    if (!poseState.isActive) {
      if (controller.created) {
        delegate->SetEnabled(hand, false);
      }
      // Controller inactive, skip.
      continue;
    }

    if (!controller.created) {
      if (hand == 0) {
        vrb::Matrix beamTransform = vrb::Matrix::Translation(vrb::Vector(-0.011f, -0.007f, 0.0f));
        delegate->CreateController(index, index,"Oculus Touch (Left)", beamTransform);
        delegate->SetLeftHanded(index, true);
        delegate->SetImmersiveBeamTransform(index, beamTransform);

      } else {
        vrb::Matrix beamTransform = vrb::Matrix::Translation(vrb::Vector(0.011f, -0.007f, 0.0f));
        delegate->CreateController(hand, hand, "Oculus Touch (Right)", beamTransform);
        delegate->SetImmersiveBeamTransform(index, beamTransform);
      }

      delegate->SetControllerType(index, device::OculusQuest); // TODO: remove this
      // Set default counts for xr-standard-gamepad-mapping
      // See: https://www.w3.org/TR/webxr-gamepads-module-1/#xr-standard-gamepad-mapping
      delegate->SetButtonCount(index, 7);
      delegate->SetHapticCount(index, 0);
      controller.created = true;
    }

    // Query controller tracking and map the pose.
    XrSpaceLocation spaceLocation{XR_TYPE_SPACE_LOCATION};
    XrResult res = xrLocateSpace(controller.space, baseSpace, predictedDisplayTime, &spaceLocation);
    CHECK_XRRESULT(res, "Input xrLocateSpace");
    if (XR_UNQUALIFIED_SUCCESS(res)) {
      controller.enabled = true;
      delegate->SetEnabled(index, true);
      // set up controller capability caps
      device::CapabilityFlags caps = device::Orientation;
      if (spaceLocation.locationFlags & XR_SPACE_LOCATION_POSITION_VALID_BIT) {
        caps |= (spaceLocation.locationFlags & XR_SPACE_LOCATION_POSITION_TRACKED_BIT) ? device::Position : device::PositionEmulated;
      }
      delegate->SetCapabilityFlags(index, caps);
      // set up pose
      vrb::Matrix transform = XrPoseToMatrix(spaceLocation.pose);
      if (renderMode == device::RenderMode::StandAlone) {
        transform.TranslateInPlace(vrb::Vector(0.0f, 1.7f, 0.0f));
      }
      delegate->SetTransform(index, transform);
    } else {
      controller.enabled = false;
      delegate->SetEnabled(hand, false);
      // Tracking lost or inactive, skip.
      continue;
    }

#define QUERY_BOOLEAN_STATE(variable, actionName) \
    XrActionStateBoolean variable{XR_TYPE_ACTION_STATE_BOOLEAN}; \
    { \
        XrActionStateGetInfo info{XR_TYPE_ACTION_STATE_GET_INFO}; \
        info.subactionPath = handSubactionPath[hand]; \
        info.action = actionName[hand]; \
        CHECK_XRCMD(xrGetActionStateBoolean(session, &info, &variable)); \
    }

#define QUERY_FLOAT_STATE(variable, actionName) \
    XrActionStateFloat variable{XR_TYPE_ACTION_STATE_FLOAT}; \
    { \
        XrActionStateGetInfo info{XR_TYPE_ACTION_STATE_GET_INFO}; \
        info.subactionPath = handSubactionPath[hand]; \
        info.action = actionName[hand]; \
        CHECK_XRCMD(xrGetActionStateFloat(session, &info, &variable)); \
    }

    // Query buttons and axes
    QUERY_BOOLEAN_STATE(menuClick, actionMenuClick);
    QUERY_BOOLEAN_STATE(triggerClick, actionTriggerClick);
    QUERY_BOOLEAN_STATE(triggerTouch, actionTriggerTouch);
    QUERY_FLOAT_STATE(triggerValue, actionTriggerValue);
    QUERY_BOOLEAN_STATE(squeezeClick, actionSqueezeClick);
    QUERY_FLOAT_STATE(squeezeValue, actionSqueezeValue);
    QUERY_BOOLEAN_STATE(trackpadClick, actionTrackpadClick);
    QUERY_BOOLEAN_STATE(trackpadTouch, actionTrackpadTouch);
    QUERY_FLOAT_STATE(trackpadX, actionTrackpadX);
    QUERY_FLOAT_STATE(trackpadY, actionTrackpadY);
    QUERY_BOOLEAN_STATE(thumbStickClick, actionThumbstickTouch);
    QUERY_BOOLEAN_STATE(thumbstickTouch, actionThumbstickTouch);
    QUERY_FLOAT_STATE(thumbstickX, actionThumbstickX);
    QUERY_FLOAT_STATE(thumbstickY, actionThumbstickY);
    QUERY_BOOLEAN_STATE(buttonAClick, actionButtonAClick);
    QUERY_BOOLEAN_STATE(buttonATouch, actionButtonATouch);
    QUERY_BOOLEAN_STATE(buttonBClick, actionButtonBClick);
    QUERY_BOOLEAN_STATE(buttonBTouch, actionButtonBTouch);

    // Map to controller delegate
    std::array<float, 4> axes;

    if (menuClick.isActive) {
      const bool pressed = menuClick.currentState != 0;
      delegate->SetButtonState(index, ControllerDelegate::BUTTON_APP, -1, pressed, pressed);
    }

    if (triggerValue.isActive || triggerClick.isActive || triggerTouch.isActive) {
      bool pressed = triggerClick.isActive && triggerClick.currentState != 0;
      if (!triggerClick.isActive) {
        pressed |= triggerValue.isActive && triggerValue.currentState > kPressThreshold;
      }
      bool touched = pressed;
      touched |= triggerValue.isActive && triggerValue.currentState > 0.0f;
      touched |= triggerTouch.isActive && triggerTouch.currentState != 0;
      float value = pressed ? 1.0f : 0.0f;
      if (triggerValue.isActive) {
        value = triggerValue.currentState;
      }

      delegate->SetButtonState(index, ControllerDelegate::BUTTON_TRIGGER, device::kImmersiveButtonTrigger, pressed, touched, value);
      if (pressed && renderMode == device::RenderMode::Immersive) {
        delegate->SetSelectActionStart(index);
      } else {
        delegate->SetSelectActionStop(index);
      }
    }

    if (squeezeClick.isActive || squeezeValue.isActive) {
      bool pressed = squeezeClick.isActive && squeezeClick.currentState != 0;
      if (!squeezeClick.isActive) {
        pressed |= squeezeValue.isActive && squeezeValue.currentState > kPressThreshold;
      }
      float value = pressed ? 1.0f : 0.0f;
      if (squeezeValue.isActive) {
        value = squeezeValue.currentState;
      }
      delegate->SetButtonState(index, ControllerDelegate::BUTTON_SQUEEZE, device::kImmersiveButtonSqueeze, pressed, pressed, value);
      if (pressed && renderMode == device::RenderMode::Immersive) {
        delegate->SetSqueezeActionStart(index);
      } else {
        delegate->SetSqueezeActionStop(index);
      }
    }

    if (trackpadClick.isActive || trackpadTouch.isActive || trackpadX.isActive || trackpadY.isActive) {
      bool pressed = trackpadClick.isActive && trackpadClick.currentState != 0;
      bool touched = pressed || (trackpadTouch.isActive && trackpadTouch.currentState != 0);
      const float x = trackpadX.isActive ? trackpadX.currentState : 0.0f;
      const float y = trackpadY.isActive ? trackpadY.currentState : 0.0f;
      delegate->SetButtonState(index, ControllerDelegate::BUTTON_TOUCHPAD, device::kImmersiveButtonTouchpad, pressed, touched);
      axes[device::kImmersiveAxisTouchpadX] = x;
      axes[device::kImmersiveAxisTouchpadY] = y;
      delegate->SetScrolledDelta(index, x, y);
    }

    if (thumbStickClick.isActive || thumbstickTouch.isActive || thumbstickX.isActive || thumbstickY.isActive) {
      bool pressed = thumbStickClick.isActive && thumbStickClick.currentState != 0;
      bool touched = pressed || (thumbstickTouch.isActive && thumbstickTouch.currentState != 0);
      const float x = thumbstickX.isActive ? thumbstickX.currentState : 0.0f;
      const float y = thumbstickY.isActive ? thumbstickY.currentState : 0.0f;
      delegate->SetButtonState(index, ControllerDelegate::BUTTON_TOUCHPAD, device::kImmersiveButtonThumbstick, pressed, touched);
      axes[device::kImmersiveAxisThumbstickX] = x;
      axes[device::kImmersiveAxisThumbstickY] = y;
      delegate->SetScrolledDelta(index, x, y);
    }

    if (buttonAClick.isActive) {
      const bool pressed = buttonAClick.currentState != 0;
      const bool touched = pressed || (buttonATouch.isActive && buttonATouch.currentState != 0);
      delegate->SetButtonState(index, ControllerDelegate::BUTTON_A, device::kImmersiveButtonA, pressed, touched);
    }

    if (buttonBClick.isActive) {
      const bool pressed = buttonBClick.currentState != 0;
      const bool touched = pressed || (buttonBTouch.isActive && buttonBTouch.currentState != 0);
      delegate->SetButtonState(index, ControllerDelegate::BUTTON_B, device::kImmersiveButtonB, pressed, touched);
    }

    delegate->SetAxes(index, axes.data(), axes.size());
  }
}

int32_t OpenXRInput::GetControllerModelCount() const {
#ifdef OCULUSVR
  return systemProperties.trackingProperties.positionTracking ? 2 : 1;
#else
#error Platform controller not implemented
#endif
}

const std::string OpenXRInput::GetControllerModelName(const int32_t aModelIndex) const {
#ifdef OCULUSVR
  if (systemProperties.trackingProperties.positionTracking != 0) {
    switch (aModelIndex) {
      case 0:
        return "vr_controller_oculusquest_left.obj";
      case 1:
        return "vr_controller_oculusquest_right.obj";
      default:
        VRB_WARN("GetControllerModelName() failed.");
        return "";
    }
  } else {
    return "vr_controller_oculusgo.obj";
  }
#else
#error Platform controller not implemented
#endif
}


void OpenXRInput::Destroy() {
  if (actionSet != XR_NULL_HANDLE) {
    CHECK_XRCMD(xrDestroyActionSet(actionSet));
    actionSet = XR_NULL_HANDLE;
    // No need to destroy input actions, they are destroyed automatically when destroying the parent action set
  }
}

OpenXRInput::~OpenXRInput() {
  Destroy();
}

} // namespace crow
