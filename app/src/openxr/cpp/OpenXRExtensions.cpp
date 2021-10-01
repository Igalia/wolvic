#include "OpenXRExtensions.h"
#include "OpenXRHelpers.h"

namespace crow {

PFN_xrGetOpenGLESGraphicsRequirementsKHR OpenXRExtensions::sXrGetOpenGLESGraphicsRequirementsKHR = nullptr;
PFN_xrCreateSwapchainAndroidSurfaceKHR OpenXRExtensions::sXrCreateSwapchainAndroidSurfaceKHR = nullptr;

std::vector<const char*> OpenXRExtensions::ExtensionNames() {
  return {
      XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME,
      XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME,
      XR_KHR_ANDROID_SURFACE_SWAPCHAIN_EXTENSION_NAME,
      XR_KHR_COMPOSITION_LAYER_CUBE_EXTENSION_NAME,
      XR_KHR_COMPOSITION_LAYER_CYLINDER_EXTENSION_NAME,
      XR_KHR_COMPOSITION_LAYER_EQUIRECT2_EXTENSION_NAME
  };
}

void OpenXRExtensions::Initialize(XrInstance instance) {
  CHECK(instance != XR_NULL_HANDLE);
  // Extension function must be loaded by name
  CHECK_XRCMD(xrGetInstanceProcAddr(instance, "xrGetOpenGLESGraphicsRequirementsKHR",
                                    reinterpret_cast<PFN_xrVoidFunction*>(&sXrGetOpenGLESGraphicsRequirementsKHR)));

  CHECK_XRCMD(xrGetInstanceProcAddr(instance, "xrCreateSwapchainAndroidSurfaceKHR",
                                    reinterpret_cast<PFN_xrVoidFunction*>(&sXrCreateSwapchainAndroidSurfaceKHR)));

}

} // namespace crow
