#pragma once

#include "vrb/Forward.h"
#include "OpenXRInputMappings.h"
#include "OpenXRHelpers.h"
#include "ElbowModel.h"
#include <optional>
#include <unordered_map>

namespace crow {
  class OpenXRActionSet;
  typedef std::shared_ptr<OpenXRActionSet> OpenXRActionSetPtr;

  class OpenXRActionSet {
  public:
    struct OpenXRButtonActions {
      OpenXRButtonActions() {}
      XrAction click { XR_NULL_HANDLE };
      XrAction touch { XR_NULL_HANDLE };
      XrAction value { XR_NULL_HANDLE };
      XrAction ready { XR_NULL_HANDLE };
    };
  private:
    OpenXRActionSet(XrInstance, XrSession);

    XrResult Initialize();
    XrResult CreateAction(XrActionType, const std::string& name, OpenXRHandFlags handFlags, XrAction&) const;

    XrInstance mInstance { XR_NULL_HANDLE };
    XrSession mSession { XR_NULL_HANDLE };
    XrActionSet mActionSet { XR_NULL_HANDLE };
    std::array<XrPath, 2> mSubactionPaths { XR_NULL_PATH, XR_NULL_PATH };
    std::string mPrefix { "input_" };
    std::unordered_map<std::string, OpenXRButtonActions> mButtonActions;
    std::unordered_map<std::string, XrAction> mActions;
  public:
    static OpenXRActionSetPtr Create(XrInstance, XrSession);
    const XrPath& GetSubactionPath(OpenXRHandFlags handeness) const;
    const XrActionSet& ActionSet() const { return mActionSet; }

    XrResult GetOrCreateAction(XrActionType, const std::string& name, OpenXRHandFlags, XrAction&);
    XrResult GetOrCreateButtonActions(OpenXRButtonType, OpenXRButtonFlags, OpenXRHandFlags, OpenXRButtonActions&);
    XrResult GetOrCreateAxisAction(OpenXRAxisType, OpenXRHandFlags, XrAction&);

    ~OpenXRActionSet();
  };

} // namespace crow

