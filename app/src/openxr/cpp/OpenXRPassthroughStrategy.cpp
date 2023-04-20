#include <vrb/Logger.h>
#include "OpenXRPassthroughStrategy.h"

namespace crow {

XrEnvironmentBlendMode
OpenXRPassthroughStrategyUnsupported::environmentBlendModeForPassthrough() const {
    VRB_ERROR("should not be called when passthrough is not available");
    return XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
};


} // namespace crow