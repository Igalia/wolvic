#include "OpenXRExtensions.h"
#include "OpenXRHelpers.h"

namespace crow {

std::unordered_set<std::string> OpenXRExtensions::sSupportedExtensions { };
PFN_xrGetOpenGLESGraphicsRequirementsKHR OpenXRExtensions::sXrGetOpenGLESGraphicsRequirementsKHR = nullptr;
PFN_xrCreateSwapchainAndroidSurfaceKHR OpenXRExtensions::sXrCreateSwapchainAndroidSurfaceKHR = nullptr;

void OpenXRExtensions::Initialize() {
    uint32_t extensionCount { 0 };
    CHECK_XRCMD(xrEnumerateInstanceExtensionProperties(nullptr, 0, &extensionCount, nullptr))

    std::vector<XrExtensionProperties> extensions(extensionCount, { XR_TYPE_EXTENSION_PROPERTIES } );

    CHECK_XRCMD(xrEnumerateInstanceExtensionProperties(nullptr, extensionCount, &extensionCount, extensions.data()));

    for (auto& extension: extensions) {
        sSupportedExtensions.insert(extension.extensionName);
    }
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
}

bool OpenXRExtensions::IsExtensionSupported(const char* name) {
    return sSupportedExtensions.count(name) > 0;
}

} // namespace crow
