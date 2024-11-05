#include "OpenXRPassthroughStrategy.h"

#include <vrb/Logger.h>
#include "OpenXRExtensions.h"
#include "OpenXRHelpers.h"
#include <assert.h>

namespace crow {

OpenXRLayerPassthroughPtr
OpenXRPassthroughStrategy::createLayerIfSupported() const {
    VRB_ERROR("asking to create a layer for passthrough when it isn't actually supported");
    return nullptr;
};

OpenXRPassthroughStrategyFBExtension::~OpenXRPassthroughStrategyFBExtension() {
    if (passthroughHandle != XR_NULL_HANDLE) {
        CHECK_XRCMD(OpenXRExtensions::sXrDestroyPassthroughFB (passthroughHandle));
        passthroughHandle = XR_NULL_HANDLE;
    }
}

void
OpenXRPassthroughStrategyFBExtension::initializePassthrough(XrSession session) {
    if (session == XR_NULL_HANDLE)
        return;

    assert(OpenXRExtensions::sXrCreatePassthroughFB != nullptr && passthroughHandle == XR_NULL_HANDLE);

    XrPassthroughCreateInfoFB passthroughCreateInfo = {
            .type = XR_TYPE_PASSTHROUGH_CREATE_INFO_FB,
            .flags = XR_PASSTHROUGH_IS_RUNNING_AT_CREATION_BIT_FB,
    };
    CHECK_XRCMD(OpenXRExtensions::sXrCreatePassthroughFB(session, &passthroughCreateInfo, &passthroughHandle));
}

OpenXRPassthroughStrategy::HandleEventResult
OpenXRPassthroughStrategyFBExtension::handleEvent(const XrEventDataBaseHeader& event) {
    XrPassthroughStateChangedFlagsFB passthroughState = reinterpret_cast<const XrEventDataPassthroughStateChangedFB&>(event).flags;
    HandleEventResult result = HandleEventResult::NoError;

    if ((passthroughState & XR_PASSTHROUGH_STATE_CHANGED_REINIT_REQUIRED_BIT_FB) ||
        (passthroughState & XR_PASSTHROUGH_STATE_CHANGED_NON_RECOVERABLE_ERROR_BIT_FB)) {
        result = HandleEventResult::NonRecoverableError;
        mIsInErrorState = true;
        if (passthroughHandle != XR_NULL_HANDLE) {
            CHECK_XRCMD(OpenXRExtensions::sXrDestroyPassthroughFB (passthroughHandle));
            passthroughHandle = XR_NULL_HANDLE;
        }
    }
    if (passthroughState & XR_PASSTHROUGH_STATE_CHANGED_REINIT_REQUIRED_BIT_FB) {
        result = HandleEventResult::NeedsReinit;
        mIsInErrorState = false;
    } else if ((passthroughState & XR_PASSTHROUGH_STATE_CHANGED_RESTORED_ERROR_BIT_FB)) {
        mIsInErrorState = false;
    } else {
        // XR_PASSTHROUGH_STATE_CHANGED_RECOVERABLE_ERROR_BIT_FB
        mIsInErrorState = true;
    }
    return result;
}

bool
OpenXRPassthroughStrategyFBExtension::isReady() const {
    return !mIsInErrorState && passthroughHandle != XR_NULL_HANDLE;
}

OpenXRLayerPassthroughPtr
OpenXRPassthroughStrategyFBExtension::createLayerIfSupported() const {
    return OpenXRLayerPassthrough::Create(passthroughHandle);
}

} // namespace crow