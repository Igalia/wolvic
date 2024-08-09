#include "OpenXRInput.h"
#include "OpenXRHelpers.h"
#include "OpenXRInputSource.h"
#include "OpenXRActionSet.h"
#include "OpenXRExtensions.h"
#include "DeviceDelegate.h"
#include <vector>

namespace crow {

OpenXRInputPtr
OpenXRInput::Create(XrInstance instance, XrSession session, XrSystemProperties properties,
                    XrSpace localSpace, bool isEyeTrackingSupported, ControllerDelegate &delegate)
{
  auto input = std::unique_ptr<OpenXRInput>(new OpenXRInput(instance, session, properties, localSpace));
  if (XR_FAILED(input->Initialize(delegate, isEyeTrackingSupported)))
    return nullptr;
  return input;
}

OpenXRInput::OpenXRInput(XrInstance instance, XrSession session, XrSystemProperties properties, XrSpace localSpace)
    : mInstance(instance)
    , mSession(session)
    , mSystemProperties(properties)
    , mLocalReferenceSpace(localSpace)
{
  VRB_ERROR("OpenXR systemName: %s", properties.systemName);
}

XrResult OpenXRInput::Initialize(ControllerDelegate &delegate, bool isEyeTrackingSupported)
{
  mActionSet = OpenXRActionSet::Create(mInstance, mSession);

  std::array<OpenXRHandFlags, 2> hands {
      OpenXRHandFlags::Right, OpenXRHandFlags::Left
  };

  int index = 0;
  for (auto handeness : hands) {
    if (auto inputSource = OpenXRInputSource::Create(mInstance, mSession, *mActionSet, mSystemProperties, handeness, index)) {
      mInputSources.push_back(std::move(inputSource));
      delegate.CreateController(index, index, "Oculus");
      index++;
    }
  }

  OpenXRInputSource::SuggestedBindings bindings;
  for (auto& input : mInputSources) {
    input->SuggestBindings(bindings);
  }

  for (auto& binding : bindings) {
    XrInteractionProfileSuggestedBinding suggestedBinding { XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING };
    RETURN_IF_XR_FAILED(xrStringToPath(mInstance, binding.first.c_str(), &suggestedBinding.interactionProfile));
    suggestedBinding.countSuggestedBindings = binding.second.size();
    suggestedBinding.suggestedBindings = binding.second.data();
    auto res = xrSuggestInteractionProfileBindings(mInstance, &suggestedBinding);
    if (XR_FAILED(res)) {
      VRB_ERROR("openxr xrSuggestInteractionProfileBindings error with '%s': %s", binding.first.c_str(), to_string(res));
    }
  }

  if (isEyeTrackingSupported)
    InitializeEyeGaze(delegate);

  XrSessionActionSetsAttachInfo attachInfo { XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO };
  attachInfo.countActionSets = 1;
  attachInfo.actionSets = &mActionSet->ActionSet();
  RETURN_IF_XR_FAILED(xrAttachSessionActionSets(mSession, &attachInfo));

  if (isEyeTrackingSupported)
    InitializeEyeGazeSpaces();

  UpdateInteractionProfile(delegate);

  return XR_SUCCESS;
}

XrResult OpenXRInput::Update(const XrFrameState& frameState, XrSpace baseSpace, const vrb::Matrix& head, const vrb::Vector& offsets, device::RenderMode renderMode, DeviceDelegate::PointerMode pointerMode, bool handTrackingEnabled, ControllerDelegate& delegate)
{
  XrActiveActionSet activeActionSet {
    mActionSet->ActionSet(), XR_NULL_PATH
  };

  XrActionsSyncInfo syncInfo = { XR_TYPE_ACTIONS_SYNC_INFO };
  syncInfo.countActiveActionSets = 1;
  syncInfo.activeActionSets = &activeActionSet;
  RETURN_IF_XR_FAILED(xrSyncActions(mSession, &syncInfo));

  bool usingEyeTracking = pointerMode == DeviceDelegate::PointerMode::TRACKED_EYE && updateEyeGaze(frameState, head, delegate);

  for (auto& input : mInputSources) {
    input->Update(frameState, baseSpace, head, offsets, renderMode, pointerMode, usingEyeTracking, handTrackingEnabled, delegate);
  }

  // Update tracked keyboard
  if (keyboardTrackingFB != nullptr)
    UpdateTrackedKeyboard(frameState, baseSpace);

  return XR_SUCCESS;
}

int32_t OpenXRInput::GetControllerModelCount() const {
  auto mapping = GetActiveInputMapping();
  if (mapping) {
    int count = 0;
    if (mapping->leftControllerModel) {
      count++;
    }
    if (mapping->rightControllerModel) {
      count++;
    }
    return count;
  }
  return 0;
}

std::string OpenXRInput::GetControllerModelName(const int32_t aModelIndex) const
{
  auto mapping = GetActiveInputMapping();
  if (!mapping) {
    return { };
  }
  return mInputSources.at(aModelIndex)->ControllerModelName();
}

void OpenXRInput::UpdateInteractionProfile(ControllerDelegate& delegate)
{
  for (auto& input : mInputSources) {
    input->UpdateInteractionProfile(delegate);
  }
}

bool OpenXRInput::AreControllersReady() const
{
  return GetActiveInputMapping() != nullptr;
}

OpenXRInputMapping* OpenXRInput::GetActiveInputMapping() const
{
  for (auto& input : mInputSources) {
    if (input->GetActiveMapping() != nullptr) {
      return input->GetActiveMapping();
    }
  }

  return nullptr;
}

bool OpenXRInput::HasPhysicalControllersAvailable() const {
    for (auto& input : mInputSources) {
        if (input->HasPhysicalControllersAvailable())
            return true;
    }
    return false;
}

void OpenXRInput::SetHandMeshBufferSizes(const uint32_t indexCount, const uint32_t vertexCount) {
  for (auto& input : mInputSources) {
    input->SetHandMeshBufferSizes(indexCount, vertexCount);
  }
}

HandMeshBufferPtr OpenXRInput::GetNextHandMeshBuffer(const int32_t aControllerIndex) {
  if (!mInputSources.at(aControllerIndex))
    return nullptr;
  return mInputSources.at(aControllerIndex)->GetNextHandMeshBuffer();
}

void OpenXRInput::LoadKeyboardModel() {
  CHECK(OpenXRExtensions::sXrEnumerateRenderModelPathsFB != nullptr);
  CHECK(OpenXRExtensions::sXrGetRenderModelPropertiesFB != nullptr);

  uint32_t pathCount = 0;
  CHECK_XRCMD(OpenXRExtensions::sXrEnumerateRenderModelPathsFB(mSession, pathCount, &pathCount, nullptr));
  if (pathCount == 0)
    return;

  std::vector<XrRenderModelPathInfoFB> paths(pathCount);
  CHECK_XRCMD(OpenXRExtensions::sXrEnumerateRenderModelPathsFB(mSession, pathCount, &pathCount, paths.data()));

  for (const auto& pathInfo: paths) {
    char path[256];
    uint32_t pathLen = 0;
    CHECK_XRCMD((xrPathToString(mInstance, pathInfo.path, pathLen, &pathLen, nullptr)));
    CHECK_XRCMD((xrPathToString(mInstance, pathInfo.path, pathLen, &pathLen, &path[0])));
    std::string pathString = path;
    if (pathString.rfind("/model_fb/keyboard", 0) != 0)
      continue;

    XrRenderModelPropertiesFB props{XR_TYPE_RENDER_MODEL_PROPERTIES_FB};
    XrResult result = OpenXRExtensions::sXrGetRenderModelPropertiesFB(mSession, pathInfo.path, &props);
    if (result != XR_SUCCESS || props.modelKey == XR_NULL_RENDER_MODEL_KEY_FB)
      continue;

    XrRenderModelLoadInfoFB loadInfo = {
      .type = XR_TYPE_RENDER_MODEL_LOAD_INFO_FB,
      .modelKey = props.modelKey,
    };
    XrRenderModelBufferFB modelInfo = {
      .type = XR_TYPE_RENDER_MODEL_BUFFER_FB,
      .bufferCapacityInput = 0,
      .buffer = nullptr,
    };
    CHECK_XRCMD(OpenXRExtensions::sXrLoadRenderModelFB(mSession, &loadInfo, &modelInfo));
    keyboardTrackingFB->modelBuffer.resize(modelInfo.bufferCountOutput);
    modelInfo.bufferCapacityInput = modelInfo.bufferCountOutput;
    modelInfo.buffer = keyboardTrackingFB->modelBuffer.data();
    result = OpenXRExtensions::sXrLoadRenderModelFB(mSession, &loadInfo, &modelInfo);
    if (result != XR_SUCCESS) {
      keyboardTrackingFB->modelBuffer.resize(0);
      continue;
    }

    keyboardTrackingFB->modelBufferChanged = true;
    break;
  }
}

void OpenXRInput::UpdateTrackedKeyboard(const XrFrameState& frameState, XrSpace baseSpace) {
  CHECK(keyboardTrackingFB != nullptr);
  CHECK(OpenXRExtensions::xrQuerySystemTrackedKeyboardFB != nullptr);
  CHECK(OpenXRExtensions::xrCreateKeyboardSpaceFB != nullptr);

  XrKeyboardTrackingQueryFB queryInfo = {
    .type = XR_TYPE_KEYBOARD_TRACKING_QUERY_FB,
    .flags = XR_KEYBOARD_TRACKING_QUERY_LOCAL_BIT_FB,
  };
  XrKeyboardTrackingDescriptionFB kbdDesc;
  CHECK_XRCMD(OpenXRExtensions::xrQuerySystemTrackedKeyboardFB(mSession, &queryInfo, &kbdDesc));

  // Check if existing keyboard disappeared or changed, and clear up its state if so
  if ((kbdDesc.flags & XR_KEYBOARD_TRACKING_EXISTS_BIT_FB) == 0 ||
       kbdDesc.trackedKeyboardId != keyboardTrackingFB->description.trackedKeyboardId) {
    if (keyboardTrackingFB->space != XR_NULL_HANDLE) {
      xrDestroySpace(keyboardTrackingFB->space);
      keyboardTrackingFB->space = XR_NULL_HANDLE;
    }
    bzero(&keyboardTrackingFB->description, sizeof(keyboardTrackingFB->description));
    keyboardTrackingFB->modelBuffer.clear();
    keyboardTrackingFB->modelBufferChanged = false;
  }

  // Bail-out if no keyboard exists
  if ((kbdDesc.flags & XR_KEYBOARD_TRACKING_EXISTS_BIT_FB) == 0)
    return;

  keyboardTrackingFB->description = kbdDesc;

  // Create the XrSpace to track the keyboard, if not done already
  if (keyboardTrackingFB->space == XR_NULL_HANDLE) {
    VRB_LOG("XR_FB_keyboard_tracking: Keyboard detected: %s", kbdDesc.name);

    XrKeyboardSpaceCreateInfoFB createInfo{
      .type = XR_TYPE_KEYBOARD_SPACE_CREATE_INFO_FB,
      .trackedKeyboardId = kbdDesc.trackedKeyboardId,
    };
    CHECK_XRCMD(OpenXRExtensions::xrCreateKeyboardSpaceFB(mSession, &createInfo,
                                                          &keyboardTrackingFB->space));
  }

  // Load keyboard render model, if not done already
  if (keyboardTrackingFB->modelBuffer.size() == 0)
    LoadKeyboardModel();

  // If keyboard is active, query and store the keyboard's pose
  if (kbdDesc.flags & XR_KEYBOARD_TRACKING_CONNECTED_BIT_FB) {
    CHECK(keyboardTrackingFB->space != XR_NULL_HANDLE);

    keyboardTrackingFB->location.type = XR_TYPE_SPACE_LOCATION;
    CHECK_XRCMD(xrLocateSpace(keyboardTrackingFB->space, baseSpace, frameState.predictedDisplayTime,
                              &keyboardTrackingFB->location));
  }
}

void OpenXRInput::SetKeyboardTrackingEnabled(bool enabled) {
  if (enabled && keyboardTrackingFB == nullptr) {
    keyboardTrackingFB = std::make_unique<KeyboardTrackingFB>();
  } else if (!enabled && keyboardTrackingFB != nullptr) {
    keyboardTrackingFB.reset();
    keyboardTrackingFB = nullptr;
  }
}

bool OpenXRInput::PopulateTrackedKeyboardInfo(DeviceDelegate::TrackedKeyboardInfo& keyboardInfo) {
  if (keyboardTrackingFB == nullptr)
    return false;

  if (keyboardTrackingFB->description.trackedKeyboardId == 0 || keyboardTrackingFB->space == nullptr ||
      keyboardTrackingFB->modelBuffer.size() == 0) {
    return false;
  }

  // Only report keyboard as active (be shown to the user) if it is connected and actively tracked.
  keyboardInfo.isActive =
    (keyboardTrackingFB->description.flags & XR_KEYBOARD_TRACKING_CONNECTED_BIT_FB) != 0 &&
    (keyboardTrackingFB->location.locationFlags & XR_SPACE_LOCATION_POSITION_TRACKED_BIT) != 0 &&
    (keyboardTrackingFB->location.locationFlags & XR_SPACE_LOCATION_ORIENTATION_TRACKED_BIT) != 0;

  // Copy the model buffer over only if it has changed
  if (keyboardTrackingFB->modelBufferChanged) {
    keyboardInfo.modelBuffer = keyboardTrackingFB->modelBuffer;
    keyboardTrackingFB->modelBufferChanged = false;
  } else {
    keyboardInfo.modelBuffer.resize(0);
  }

  keyboardInfo.transform = XrPoseToMatrix(keyboardTrackingFB->location.pose);
  keyboardInfo.transform.TranslateInPlace(kAverageHeight);

  return true;
}

OpenXRInput::~OpenXRInput() {
  if (keyboardTrackingFB != nullptr) {
    keyboardTrackingFB.reset();
    keyboardTrackingFB = nullptr;
  }
}

void OpenXRInput::InitializeEyeGaze(ControllerDelegate& delegate) {
    XrActionCreateInfo actionInfo{XR_TYPE_ACTION_CREATE_INFO};
    strcpy(actionInfo.actionName, "user_intent");
    actionInfo.actionType = XR_ACTION_TYPE_POSE_INPUT;
    strcpy(actionInfo.localizedActionName, "User Intent");
    CHECK_XRCMD(xrCreateAction(mActionSet->ActionSet(), &actionInfo, &mUserIntentAction));

    // Create suggested bindings
    XrPath eyeGazeInteractionProfilePath;
    CHECK_XRCMD(xrStringToPath(mInstance, "/interaction_profiles/ext/eye_gaze_interaction",
                               &eyeGazeInteractionProfilePath));
    XrPath gazePosePath;
    CHECK_XRCMD(xrStringToPath(mInstance, "/user/eyes_ext/input/gaze_ext/pose", &gazePosePath));

    XrActionSuggestedBinding bindings;
    bindings.action = mUserIntentAction;
    bindings.binding = gazePosePath;

    XrInteractionProfileSuggestedBinding suggestedBindings{XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING};
    suggestedBindings.interactionProfile = eyeGazeInteractionProfilePath;
    suggestedBindings.suggestedBindings = &bindings;
    suggestedBindings.countSuggestedBindings = 1;
    CHECK_XRCMD(xrSuggestInteractionProfileBindings(mInstance, &suggestedBindings));

    mOneEuroFilterGazeOrientation = std::make_unique<OneEuroFilterQuaternion>(0.5, 0.25, 1.0);
}

void OpenXRInput::InitializeEyeGazeSpaces() {
    XrPosef pose_identity = XrPoseIdentity();
    XrActionSpaceCreateInfo createActionSpaceInfo{XR_TYPE_ACTION_SPACE_CREATE_INFO};
    createActionSpaceInfo.action = mUserIntentAction;
    createActionSpaceInfo.poseInActionSpace = pose_identity;
    CHECK_XRCMD(xrCreateActionSpace(mSession, &createActionSpaceInfo, &mEyeGazeActionSpace));
}

bool OpenXRInput::updateEyeGaze(XrFrameState frameState, const vrb::Matrix& head, ControllerDelegate& delegate) {
    XrActionStatePose actionStatePose{XR_TYPE_ACTION_STATE_POSE};
    XrActionStateGetInfo getActionStateInfo{XR_TYPE_ACTION_STATE_GET_INFO};
    getActionStateInfo.action = mUserIntentAction;
    CHECK_XRCMD(xrGetActionStatePose(mSession, &getActionStateInfo, &actionStatePose));
    if (!actionStatePose.isActive)
        return false;

    XrEyeGazeSampleTimeEXT eyeGazeSampleTime{XR_TYPE_EYE_GAZE_SAMPLE_TIME_EXT};
    XrSpaceLocation gazeLocation{XR_TYPE_SPACE_LOCATION, &eyeGazeSampleTime};
    CHECK_XRCMD(xrLocateSpace(mEyeGazeActionSpace, mLocalReferenceSpace, frameState.predictedDisplayTime, &gazeLocation));

    if (!(gazeLocation.locationFlags & XR_SPACE_LOCATION_POSITION_VALID_BIT) ||
        !(gazeLocation.locationFlags & XR_SPACE_LOCATION_ORIENTATION_VALID_BIT)) {
        return false;
    }

    vrb::Vector gazePosition = head.Translate({gazeLocation.pose.position.x, gazeLocation.pose.position.y, gazeLocation.pose.position.z}).GetTranslation();
    vrb::Quaternion gazeOrientation(gazeLocation.pose.orientation.x, gazeLocation.pose.orientation.y, gazeLocation.pose.orientation.z, gazeLocation.pose.orientation.w);
    float* filteredOrientation = mOneEuroFilterGazeOrientation->filter(frameState.predictedDisplayTime, gazeOrientation.Data());
    gazeOrientation = {filteredOrientation[0], filteredOrientation[1], filteredOrientation[2], filteredOrientation[3]};
    delegate.SetTransform(0, vrb::Matrix::Rotation(gazeOrientation).Translate(gazePosition));
    delegate.SetImmersiveBeamTransform(0, vrb::Matrix::Identity());

    return true;
}

} // namespace crow
