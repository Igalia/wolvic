#include "OpenXRInput.h"
#include "OpenXRHelpers.h"
#include "OpenXRInputSource.h"
#include "OpenXRActionSet.h"
#include "OpenXRExtensions.h"
#include <vector>

namespace crow {

OpenXRInputPtr OpenXRInput::Create(XrInstance instance, XrSession session, XrSystemProperties properties, ControllerDelegate& delegate)
{
  auto input = std::unique_ptr<OpenXRInput>(new OpenXRInput(instance, session, properties, delegate));
  if (XR_FAILED(input->Initialize(delegate)))
    return nullptr;
  return input;
}

OpenXRInput::OpenXRInput(XrInstance instance, XrSession session, XrSystemProperties properties, ControllerDelegate& delegate)
    : mInstance(instance)
    , mSession(session)
    , mSystemProperties(properties)
{
  VRB_ERROR("OpenXR systemName: %s", properties.systemName);
}

XrResult OpenXRInput::Initialize(ControllerDelegate& delegate)
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

  XrSessionActionSetsAttachInfo attachInfo { XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO };
  attachInfo.countActionSets = 1;
  attachInfo.actionSets = &mActionSet->ActionSet();
  RETURN_IF_XR_FAILED(xrAttachSessionActionSets(mSession, &attachInfo));

  UpdateInteractionProfile(delegate);

  return XR_SUCCESS;
}

XrResult OpenXRInput::Update(const XrFrameState& frameState, XrSpace baseSpace, const vrb::Matrix& head, const vrb::Vector& offsets, device::RenderMode renderMode, ControllerDelegate& delegate)
{
  XrActiveActionSet activeActionSet {
    mActionSet->ActionSet(), XR_NULL_PATH
  };

  XrActionsSyncInfo syncInfo = { XR_TYPE_ACTIONS_SYNC_INFO };
  syncInfo.countActiveActionSets = 1;
  syncInfo.activeActionSets = &activeActionSet;
  RETURN_IF_XR_FAILED(xrSyncActions(mSession, &syncInfo));

  for (auto& input : mInputSources) {
    input->Update(frameState, baseSpace, head, offsets, renderMode, delegate);
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

void OpenXRInput::UpdateInteractionProfile(ControllerDelegate& delegate, const char* emulateProfile)
{
  for (auto& input : mInputSources) {
    input->UpdateInteractionProfile(delegate, emulateProfile);
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
    keyboardTrackingFB->modelBuffer.resize(0);
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

OpenXRInput::~OpenXRInput() {
  if (keyboardTrackingFB != nullptr) {
    keyboardTrackingFB.reset();
    keyboardTrackingFB = nullptr;
  }
}

} // namespace crow
