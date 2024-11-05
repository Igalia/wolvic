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
    static void LoadApiLayers(XrInstance instance);
    static bool IsApiLayerSupported(const char*);

    static PFN_xrGetOpenGLESGraphicsRequirementsKHR sXrGetOpenGLESGraphicsRequirementsKHR;
    static PFN_xrCreateSwapchainAndroidSurfaceKHR sXrCreateSwapchainAndroidSurfaceKHR;

    // hand tracking extension prototypes
    static PFN_xrCreateHandTrackerEXT sXrCreateHandTrackerEXT;
    static PFN_xrDestroyHandTrackerEXT sXrDestroyHandTrackerEXT;
    static PFN_xrLocateHandJointsEXT sXrLocateHandJointsEXT;

    static PFN_xrPerfSettingsSetPerformanceLevelEXT sXrPerfSettingsSetPerformanceLevelEXT;
    static PFN_xrEnumerateDisplayRefreshRatesFB sXrEnumerateDisplayRefreshRatesFB;
    static PFN_xrRequestDisplayRefreshRateFB sXrRequestDisplayRefreshRateFB;

    static PFN_xrCreatePassthroughFB sXrCreatePassthroughFB;
    static PFN_xrDestroyPassthroughFB sXrDestroyPassthroughFB;
    static PFN_xrCreatePassthroughLayerFB sXrCreatePassthroughLayerFB;
    static PFN_xrDestroyPassthroughLayerFB sXrDestroyPassthroughLayerFB;
    static PFN_xrPassthroughLayerResumeFB sXrPassthroughLayerResumeFB;
    static PFN_xrPassthroughLayerPauseFB sXrPassthroughLayerPauseFB;

    static PFN_xrCreateHandMeshSpaceMSFT sXrCreateHandMeshSpaceMSFT;
    static PFN_xrUpdateHandMeshMSFT sXrUpdateHandMeshMSFT;

    static PFN_xrCreateKeyboardSpaceFB xrCreateKeyboardSpaceFB;
    static PFN_xrQuerySystemTrackedKeyboardFB xrQuerySystemTrackedKeyboardFB;

    static PFN_xrEnumerateRenderModelPathsFB sXrEnumerateRenderModelPathsFB;
    static PFN_xrGetRenderModelPropertiesFB sXrGetRenderModelPropertiesFB;
    static PFN_xrLoadRenderModelFB sXrLoadRenderModelFB;
  private:
     static std::unordered_set<std::string> sSupportedExtensions;
     static std::unordered_set<std::string> sSupportedApiLayers;
  };
} // namespace crow