#pragma once

#include <memory>

#include <openxr/openxr.h>
#include "OpenXRLayers.h"

namespace crow {

class OpenXRPassthroughStrategy;
typedef std::unique_ptr<OpenXRPassthroughStrategy> PassthroughStrategyPtr;

class OpenXRPassthroughStrategy {
public:
enum class HandleEventResult { NoError, NonRecoverableError, NeedsReinit };
virtual void initializePassthrough(XrSession) {}
virtual bool usesCompositorLayer() const { return false; }
virtual XrEnvironmentBlendMode environmentBlendModeForPassthrough() const = 0;
virtual HandleEventResult handleEvent(const XrEventDataBaseHeader&) { return HandleEventResult::NoError; };
virtual ~OpenXRPassthroughStrategy() = default;
virtual bool isReady() const { return mIsInErrorState; };
virtual OpenXRLayerPassthroughPtr createLayerIfSupported(VRLayerPassthroughPtr) const;
protected:
bool mIsInErrorState { false };
};

class OpenXRPassthroughStrategyUnsupported : public OpenXRPassthroughStrategy {
private:
XrEnvironmentBlendMode environmentBlendModeForPassthrough() const override;
};

class OpenXRPassthroughStrategyFBExtension : public OpenXRPassthroughStrategy {
public:
~OpenXRPassthroughStrategyFBExtension();
private:
void initializePassthrough(XrSession) override;
bool usesCompositorLayer() const override { return true; }
XrEnvironmentBlendMode environmentBlendModeForPassthrough() const override { return XR_ENVIRONMENT_BLEND_MODE_OPAQUE; };
HandleEventResult handleEvent(const XrEventDataBaseHeader&) override;
bool isReady() const override;
OpenXRLayerPassthroughPtr createLayerIfSupported(VRLayerPassthroughPtr) const override;

XrPassthroughFB passthroughHandle { XR_NULL_HANDLE };
};

class OpenXRPassthroughStrategyBlendMode : public OpenXRPassthroughStrategy {
private:
XrEnvironmentBlendMode environmentBlendModeForPassthrough() const override { return XR_ENVIRONMENT_BLEND_MODE_ALPHA_BLEND; };
};

// This strategy is intended for runtimes that show a transparent environment when the skybox layer is not rendered.
class OpenXRPassthroughStrategyNoSkybox : public OpenXRPassthroughStrategy {
private:
XrEnvironmentBlendMode environmentBlendModeForPassthrough() const override { return XR_ENVIRONMENT_BLEND_MODE_OPAQUE; };
};

} // namespace crow
