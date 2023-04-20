#pragma once

#include <memory>

#include <openxr/openxr.h>

namespace crow {

class OpenXRPassthroughStrategy;
typedef std::unique_ptr<OpenXRPassthroughStrategy> PassthroughStrategyPtr;

class OpenXRPassthroughStrategy {
public:
virtual bool usesCompositorLayer() const { return false; }
virtual XrEnvironmentBlendMode environmentBlendModeForPassthrough() const = 0;
virtual ~OpenXRPassthroughStrategy() = default;
};

class OpenXRPassthroughStrategyUnsupported : public OpenXRPassthroughStrategy {
private:
XrEnvironmentBlendMode environmentBlendModeForPassthrough() const override;
};

class OpenXRPassthroughStrategyFBExtension : public OpenXRPassthroughStrategy {
private:
bool usesCompositorLayer() const override { return true; }
XrEnvironmentBlendMode environmentBlendModeForPassthrough() const override { return XR_ENVIRONMENT_BLEND_MODE_OPAQUE; };
};

class OpenXRPassthroughStrategyBlendMode : public OpenXRPassthroughStrategy {
private:
XrEnvironmentBlendMode environmentBlendModeForPassthrough() const override { return XR_ENVIRONMENT_BLEND_MODE_ALPHA_BLEND; };
};

} // namespace crow
