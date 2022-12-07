#pragma once

#include <EGL/egl.h>
#include <jni.h>
#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>
#include <vector>
#include <string>
#include <unordered_set>

namespace crow {
  class OpenXRExtensions {
  public:
    static void Initialize();
    static void LoadExtensions(XrInstance instance);
    static bool IsExtensionSupported(const char*);

    static PFN_xrGetOpenGLESGraphicsRequirementsKHR sXrGetOpenGLESGraphicsRequirementsKHR;
    static PFN_xrCreateSwapchainAndroidSurfaceKHR sXrCreateSwapchainAndroidSurfaceKHR;

    // hand tracking extension prototypes
    static PFN_xrCreateHandTrackerEXT sXrCreateHandTrackerEXT;
    static PFN_xrDestroyHandTrackerEXT sXrDestroyHandTrackerEXT;
    static PFN_xrLocateHandJointsEXT sXrLocateHandJointsEXT;
    static PFN_xrGetHandMeshFB sXrGetHandMeshFB;
  private:
     static std::unordered_set<std::string> sSupportedExtensions;
  };
} // namespace crow