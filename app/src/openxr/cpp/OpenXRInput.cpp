#include "OpenXRInput.h"
#include "OpenXRHelpers.h"
#include "OpenXRInputSource.h"
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
  std::array<OpenXRHandFlags, 2> hands {
      OpenXRHandFlags::Left, OpenXRHandFlags::Right
  };

  int index = 0;
  for (auto handeness : hands) {
    if (auto inputSource = OpenXRInputSource::Create(mInstance, mSession, mSystemProperties, handeness, index)) {
      mInputSources.push_back(std::move(inputSource));
      delegate.CreateController(index, index, "Oculus");
      index++;
    }
  }

  OpenXRInputSource::SuggestedBindings bindings;
  std::vector<XrActionSet> actionSets;
  for (auto& input : mInputSources) {
    input->SuggestBindings(bindings);
    actionSets.push_back(input->ActionSet());
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
  attachInfo.countActionSets = actionSets.size();
  attachInfo.actionSets = actionSets.data();
  RETURN_IF_XR_FAILED(xrAttachSessionActionSets(mSession, &attachInfo));

  UpdateInteractionProfile();

  return XR_SUCCESS;
}

XrResult OpenXRInput::Update(const XrFrameState& frameState, XrSpace baseSpace, const vrb::Matrix& head, device::RenderMode renderMode, ControllerDelegate& delegate)
{
  std::vector<XrActiveActionSet> actionSets;
  for (auto& input : mInputSources) {
    actionSets.push_back(XrActiveActionSet{input->ActionSet(), XR_NULL_PATH});
  }

  XrActionsSyncInfo syncInfo = { XR_TYPE_ACTIONS_SYNC_INFO };
  syncInfo.countActiveActionSets = actionSets.size();
  syncInfo.activeActionSets = actionSets.data();
  RETURN_IF_XR_FAILED(xrSyncActions(mSession, &syncInfo));

  for (auto& input : mInputSources) {
    input->Update(frameState, baseSpace, head, renderMode, delegate);
  }

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

void OpenXRInput::UpdateInteractionProfile()
{
  for (auto& input : mInputSources) {
    input->UpdateInteractionProfile();
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

OpenXRInput::~OpenXRInput() {
}

} // namespace crow
