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
virtual HandleEventResult handleEvent(const XrEventDataBaseHeader&) { return HandleEventResult::NoError; };
virtual ~OpenXRPassthroughStrategy() = default;
virtual bool isReady() const { return mIsInErrorState; };
virtual OpenXRLayerPassthroughPtr createLayerIfSupported() const;
protected:
bool mIsInErrorState { false };
};

class OpenXRPassthroughStrategyUnsupported : public OpenXRPassthroughStrategy {
};

class OpenXRPassthroughStrategyFBExtension : public OpenXRPassthroughStrategy {
public:
~OpenXRPassthroughStrategyFBExtension();
private:
void initializePassthrough(XrSession) override;
bool usesCompositorLayer() const override { return true; }
HandleEventResult handleEvent(const XrEventDataBaseHeader&) override;
bool isReady() const override;
OpenXRLayerPassthroughPtr createLayerIfSupported() const override;

XrPassthroughFB passthroughHandle { XR_NULL_HANDLE };
};

class OpenXRPassthroughStrategyBlendMode : public OpenXRPassthroughStrategy {
};

} // namespace crow
