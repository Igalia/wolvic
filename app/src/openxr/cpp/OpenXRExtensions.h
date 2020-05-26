#pragma once

#include <EGL/egl.h>
#include <jni.h>
#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>
#include <vector>

namespace crow {
  class OpenXRExtensions {
  public:
    static void Initialize(XrInstance instance);
    static std::vector<const char*> ExtensionNames();

    static PFN_xrGetOpenGLESGraphicsRequirementsKHR sXrGetOpenGLESGraphicsRequirementsKHR;
    static PFN_xrCreateSwapchainAndroidSurfaceKHR sXrCreateSwapchainAndroidSurfaceKHR;
  };
} // namespace crow