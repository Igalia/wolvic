#include "OpenXRInput.h"
#include "OpenXRHelpers.h"
#include "OpenXRInputSource.h"
#include "OpenXRActionSet.h"
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

  UpdateInteractionProfile();

  return XR_SUCCESS;
}

XrResult OpenXRInput::Update(const XrFrameState& frameState, XrSpace baseSpace, const vrb::Matrix& head, float offsetY, device::RenderMode renderMode, ControllerDelegate& delegate)
{
  XrActiveActionSet activeActionSet {
    mActionSet->ActionSet(), XR_NULL_PATH
  };

  XrActionsSyncInfo syncInfo = { XR_TYPE_ACTIONS_SYNC_INFO };
  syncInfo.countActiveActionSets = 1;
  syncInfo.activeActionSets = &activeActionSet;
  RETURN_IF_XR_FAILED(xrSyncActions(mSession, &syncInfo));

  for (auto& input : mInputSources) {
    input->Update(frameState, baseSpace, head, offsetY, renderMode, delegate);
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
