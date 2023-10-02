#include "OpenXRExtensions.h"
#include "OpenXRHelpers.h"
#include "DeviceUtils.h"
#include "OpenXRInputMappings.h"

namespace crow {

std::unordered_set<std::string> OpenXRExtensions::sSupportedExtensions { };
std::unordered_set<std::string> OpenXRExtensions::sSupportedApiLayers { };
PFN_xrGetOpenGLESGraphicsRequirementsKHR OpenXRExtensions::sXrGetOpenGLESGraphicsRequirementsKHR = nullptr;
PFN_xrCreateSwapchainAndroidSurfaceKHR OpenXRExtensions::sXrCreateSwapchainAndroidSurfaceKHR = nullptr;
PFN_xrCreateHandTrackerEXT OpenXRExtensions::sXrCreateHandTrackerEXT = nullptr;
PFN_xrDestroyHandTrackerEXT OpenXRExtensions::sXrDestroyHandTrackerEXT = nullptr;
PFN_xrLocateHandJointsEXT OpenXRExtensions::sXrLocateHandJointsEXT = nullptr;
PFN_xrPerfSettingsSetPerformanceLevelEXT OpenXRExtensions::sXrPerfSettingsSetPerformanceLevelEXT = nullptr;
PFN_xrEnumerateDisplayRefreshRatesFB OpenXRExtensions::sXrEnumerateDisplayRefreshRatesFB = nullptr;
PFN_xrRequestDisplayRefreshRateFB OpenXRExtensions::sXrRequestDisplayRefreshRateFB = nullptr;
PFN_xrCreatePassthroughFB OpenXRExtensions::sXrCreatePassthroughFB = nullptr;
PFN_xrDestroyPassthroughFB OpenXRExtensions::sXrDestroyPassthroughFB = nullptr;
PFN_xrCreatePassthroughLayerFB OpenXRExtensions::sXrCreatePassthroughLayerFB = nullptr;
PFN_xrDestroyPassthroughLayerFB OpenXRExtensions::sXrDestroyPassthroughLayerFB = nullptr;
PFN_xrCreateHandMeshSpaceMSFT OpenXRExtensions::sXrCreateHandMeshSpaceMSFT = nullptr;
PFN_xrUpdateHandMeshMSFT OpenXRExtensions::sXrUpdateHandMeshMSFT = nullptr;

void OpenXRExtensions::Initialize() {
    // Extensions.
    uint32_t extensionCount { 0 };
    CHECK_XRCMD(xrEnumerateInstanceExtensionProperties(nullptr, 0, &extensionCount, nullptr))

    std::vector<XrExtensionProperties> extensions(extensionCount, { XR_TYPE_EXTENSION_PROPERTIES } );

    CHECK_XRCMD(xrEnumerateInstanceExtensionProperties(nullptr, extensionCount, &extensionCount, extensions.data()));

    for (auto& extension: extensions) {
        sSupportedExtensions.insert(extension.extensionName);
    }
#ifdef LYNX
    // Lynx incorrectly advertises these extensions as supported but in reality they don't work.
    sSupportedExtensions.erase(XR_FB_DISPLAY_REFRESH_RATE_EXTENSION_NAME);
    sSupportedExtensions.erase(XR_EXTX_OVERLAY_EXTENSION_NAME);
#elif PICOXR
    // Pico incorrectly advertises this extension as supported but it makes Wolvic not work.
    sSupportedExtensions.erase(XR_EXTX_OVERLAY_EXTENSION_NAME);
#endif

    // Adding this check here is ugly but required to have a working build for VRX. With the current
    // runtime the controller poses freeze (always report same pose) if hand tracking is enabled.
    if (DeviceUtils::GetDeviceTypeFromSystem(true) == device::LenovoVRX)
        sSupportedExtensions.erase(XR_EXT_HAND_TRACKING_EXTENSION_NAME);

    // API layers.
    uint32_t apiLayersCount;
    CHECK_XRCMD(xrEnumerateApiLayerProperties(0, &apiLayersCount, nullptr));

    std::vector<XrApiLayerProperties> apiLayers(apiLayersCount, { XR_TYPE_API_LAYER_PROPERTIES });
    CHECK_XRCMD(xrEnumerateApiLayerProperties((uint32_t) apiLayers.size(), &apiLayersCount, apiLayers.data()));

    for (auto& layer : apiLayers)
        sSupportedApiLayers.insert(layer.layerName);
}

void OpenXRExtensions::LoadExtensions(XrInstance instance) {
  CHECK(instance != XR_NULL_HANDLE);
  // Extension function must be loaded by name
  CHECK_XRCMD(xrGetInstanceProcAddr(instance, "xrGetOpenGLESGraphicsRequirementsKHR",
                                    reinterpret_cast<PFN_xrVoidFunction*>(&sXrGetOpenGLESGraphicsRequirementsKHR)));

  if (IsExtensionSupported(XR_KHR_ANDROID_SURFACE_SWAPCHAIN_EXTENSION_NAME)) {
      CHECK_XRCMD(xrGetInstanceProcAddr(instance, "xrCreateSwapchainAndroidSurfaceKHR",
                                        reinterpret_cast<PFN_xrVoidFunction *>(&sXrCreateSwapchainAndroidSurfaceKHR)));
  }

  if (IsExtensionSupported(XR_EXT_PERFORMANCE_SETTINGS_EXTENSION_NAME)) {
      CHECK_XRCMD(xrGetInstanceProcAddr(instance, "xrPerfSettingsSetPerformanceLevelEXT",
                                        reinterpret_cast<PFN_xrVoidFunction*>(&sXrPerfSettingsSetPerformanceLevelEXT)));
  }

  if (IsExtensionSupported(XR_FB_DISPLAY_REFRESH_RATE_EXTENSION_NAME)) {
      CHECK_XRCMD(xrGetInstanceProcAddr(instance, "xrEnumerateDisplayRefreshRatesFB",
                                        reinterpret_cast<PFN_xrVoidFunction*>(&sXrEnumerateDisplayRefreshRatesFB)));
      CHECK_XRCMD(xrGetInstanceProcAddr(instance, "xrRequestDisplayRefreshRateFB",
                                        reinterpret_cast<PFN_xrVoidFunction*>(&sXrRequestDisplayRefreshRateFB)));
  }

    if (IsExtensionSupported(XR_EXT_HAND_TRACKING_EXTENSION_NAME)) {
        CHECK_XRCMD(xrGetInstanceProcAddr(instance, "xrCreateHandTrackerEXT",
                                        reinterpret_cast<PFN_xrVoidFunction *>(&sXrCreateHandTrackerEXT)));
        CHECK_XRCMD(xrGetInstanceProcAddr(instance, "xrDestroyHandTrackerEXT",
                                        reinterpret_cast<PFN_xrVoidFunction *>(&sXrDestroyHandTrackerEXT)));
        CHECK_XRCMD(xrGetInstanceProcAddr(instance, "xrLocateHandJointsEXT",
                                        reinterpret_cast<PFN_xrVoidFunction *>(&sXrLocateHandJointsEXT)));

        if (IsExtensionSupported(XR_MSFT_HAND_TRACKING_MESH_EXTENSION_NAME)) {
            CHECK_XRCMD(xrGetInstanceProcAddr(instance, "xrCreateHandMeshSpaceMSFT",
                                              reinterpret_cast<PFN_xrVoidFunction *>(&sXrCreateHandMeshSpaceMSFT)));
            CHECK_XRCMD(xrGetInstanceProcAddr(instance, "xrUpdateHandMeshMSFT",
                                              reinterpret_cast<PFN_xrVoidFunction *>(&sXrUpdateHandMeshMSFT)));
        }
    }

    if (IsExtensionSupported(XR_FB_PASSTHROUGH_EXTENSION_NAME)) {
        CHECK_XRCMD(xrGetInstanceProcAddr(instance, "xrCreatePassthroughFB",
                                          reinterpret_cast<PFN_xrVoidFunction *>(&sXrCreatePassthroughFB)));
        CHECK_XRCMD(xrGetInstanceProcAddr(instance, "xrDestroyPassthroughFB",
                                          reinterpret_cast<PFN_xrVoidFunction *>(&sXrDestroyPassthroughFB)));
        CHECK_XRCMD(xrGetInstanceProcAddr(instance, "xrCreatePassthroughLayerFB",
                                          reinterpret_cast<PFN_xrVoidFunction *>(&sXrCreatePassthroughLayerFB)));
        CHECK_XRCMD(xrGetInstanceProcAddr(instance, "xrDestroyPassthroughLayerFB",
                                          reinterpret_cast<PFN_xrVoidFunction *>(&sXrDestroyPassthroughLayerFB)));
    }
}

void OpenXRExtensions::LoadApiLayers(XrInstance instance) {
    CHECK(instance != XR_NULL_HANDLE);
}

bool OpenXRExtensions::IsExtensionSupported(const char* name) {
    return sSupportedExtensions.count(name) > 0;
}

bool OpenXRExtensions::IsApiLayerSupported(const char* name) {
    return sSupportedApiLayers.count(name) > 0;
}

} // namespace crow
