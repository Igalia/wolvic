/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "DeviceDelegateOpenXR.h"
#include "DeviceUtils.h"
#include "ElbowModel.h"
#include "BrowserEGLContext.h"
#include "VRBrowser.h"
#include "VRLayer.h"

#include <EGL/egl.h>
#include "vrb/CameraEye.h"
#include "vrb/Color.h"
#include "vrb/ConcreteClass.h"
#include "vrb/FBO.h"
#include "vrb/GLError.h"
#include "vrb/Matrix.h"
#include "vrb/Quaternion.h"
#include "vrb/RenderContext.h"
#include "vrb/Vector.h"

#include <vector>
#include <array>
#include <algorithm>
#include <cstdlib>
#include <unistd.h>
#include <sstream>
#include <string.h>

#include "VRBrowser.h"

#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>
#ifdef OCULUSVR
#include <openxr/openxr_oculus.h>
#endif
#include "OpenXRHelpers.h"
#include "OpenXRSwapChain.h"
#include "OpenXRInput.h"
#include "OpenXRInputMappings.h"
#include "OpenXRExtensions.h"
#include "OpenXRLayers.h"

namespace crow {

struct DeviceDelegateOpenXR::State {
  vrb::RenderContextWeak context;
  JavaContext* javaContext = nullptr;
  bool layersEnabled = true;
  XrInstanceCreateInfoAndroidKHR java;
  XrInstance instance = XR_NULL_HANDLE;
  XrSystemId system = XR_NULL_SYSTEM_ID;
  XrSession session = XR_NULL_HANDLE;
  XrSessionState sessionState = XR_SESSION_STATE_UNKNOWN;
  bool vrReady = false;
  XrGraphicsBindingOpenGLESAndroidKHR graphicsBinding{XR_TYPE_GRAPHICS_BINDING_OPENGL_ES_ANDROID_KHR};
  XrSystemProperties systemProperties{XR_TYPE_SYSTEM_PROPERTIES};
  XrEventDataBuffer eventBuffer;
  XrViewConfigurationType viewConfigType{XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO};
  std::vector<XrViewConfigurationView> viewConfig;
  std::vector<XrView> views;
  std::vector<XrView> prevViews;
  std::vector<OpenXRSwapChainPtr> eyeSwapChains;
  OpenXRSwapChainPtr boundSwapChain;
  OpenXRSwapChainPtr previousBoundSwapchain;
  XrSpace viewSpace = XR_NULL_HANDLE;
  XrSpace localSpace = XR_NULL_HANDLE;
  XrSpace layersSpace = XR_NULL_HANDLE;
  XrSpace stageSpace = XR_NULL_HANDLE;
  std::vector<int64_t> swapchainFormats;
  OpenXRInputPtr input;
  OpenXRLayerCubePtr cubeLayer;
  OpenXRLayerEquirectPtr equirectLayer;
  std::vector<OpenXRLayerPtr> uiLayers;
  OpenXRSwapChainPtr crearColorSwapChain;
  device::RenderMode renderMode = device::RenderMode::StandAlone;
  vrb::CameraEyePtr cameras[2];
  FramePrediction framePrediction = FramePrediction::NO_FRAME_AHEAD;
  XrTime prevPredictedDisplayTime = 0;
  XrTime predictedDisplayTime = 0;
  XrPosef predictedPose = {};
  XrPosef prevPredictedPose = {};
  uint32_t discardedFrameIndex = 0;
  int discardCount = 0;
  vrb::Color clearColor;
  float near = 0.1f;
  float far = 100.f;
  bool hasEventFocus = true;
  crow::ElbowModelPtr elbow;
  ControllerDelegatePtr controller;
  ImmersiveDisplayPtr immersiveDisplay;
  int reorientCount = -1;
  vrb::Matrix reorientMatrix = vrb::Matrix::Identity();
  device::CPULevel minCPULevel = device::CPULevel::Normal;
  device::DeviceType deviceType = device::UnknownType;
  std::vector<const XrCompositionLayerBaseHeader*> frameEndLayers;
  std::function<void()> controllersReadyCallback;
  std::optional<XrPosef> firstPose;
  bool mHandTrackingSupported = false;
  std::vector<float> refreshRates;
  bool reorientRequested { false };

  bool IsPositionTrackingSupported() {
      CHECK(system != XR_NULL_SYSTEM_ID);
      CHECK(instance != XR_NULL_HANDLE);
      return systemProperties.trackingProperties.positionTracking == XR_TRUE;
  }

  // This might require more sophisticated code to properly detect specific hardware. That was
  // easy to do with propietary SDKs but it's a bit more difficult with OpenXR.
  void InitializeDeviceType() {
#if OCULUSVR
      // FIXME: this is fragile. Meta Quest Pro incorrectly advertises itself as "Oculus Quest2"
      // so the only way we have to properly identify it is by checking extensions that are not
      // supported in Quest2 but available for Quest Pro.
      deviceType = OpenXRExtensions::IsExtensionSupported("XR_META_local_dimming") ? device::MetaQuestPro : device::OculusQuest2;
#elif HVR
      deviceType = IsPositionTrackingSupported() ? device::HVR6DoF : device::HVR3DoF;
#elif PICOXR
      deviceType = device::PicoXR;
#elif LYNX
      deviceType = device::LynxR1;
#elif SPACES
      deviceType = device::LenovoA3;
#endif
      VRB_LOG("Initializing device %s from vendor %d. Device type %d", systemProperties.systemName, systemProperties.vendorId, deviceType);
  }

  void Initialize() {
    vrb::RenderContextPtr localContext = context.lock();
    elbow = ElbowModel::Create();
    for (int i = 0; i < 2; ++i) {
      cameras[i] = vrb::CameraEye::Create(localContext->GetRenderThreadCreationContext());
    }
    layersEnabled = VRBrowser::AreLayersEnabled();

#ifndef HVR
    PFN_xrInitializeLoaderKHR initializeLoaderKHR;
    CHECK_XRCMD(xrGetInstanceProcAddr(nullptr, "xrInitializeLoaderKHR", reinterpret_cast<PFN_xrVoidFunction*>(&initializeLoaderKHR)));
    XrLoaderInitInfoAndroidKHR loaderData;
    memset(&loaderData, 0, sizeof(loaderData));
    loaderData.type = XR_TYPE_LOADER_INIT_INFO_ANDROID_KHR;
    loaderData.next = nullptr;
    loaderData.applicationVM = javaContext->vm;
    loaderData.applicationContext = javaContext->env->NewGlobalRef(javaContext->activity);
    initializeLoaderKHR(reinterpret_cast<XrLoaderInitInfoBaseHeaderKHR*>(&loaderData));
#endif

    // Initialize the XrInstance
    OpenXRExtensions::Initialize();

    std::vector<const char *> extensions {
      XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME,
      XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME,
    };

    if (OpenXRExtensions::IsExtensionSupported(XR_KHR_ANDROID_SURFACE_SWAPCHAIN_EXTENSION_NAME)) {
      extensions.push_back(XR_KHR_ANDROID_SURFACE_SWAPCHAIN_EXTENSION_NAME);
    }
    if (OpenXRExtensions::IsExtensionSupported(XR_KHR_COMPOSITION_LAYER_CUBE_EXTENSION_NAME)) {
      extensions.push_back(XR_KHR_COMPOSITION_LAYER_CUBE_EXTENSION_NAME);
    }
    if (OpenXRExtensions::IsExtensionSupported(XR_KHR_COMPOSITION_LAYER_CYLINDER_EXTENSION_NAME)) {
      extensions.push_back(XR_KHR_COMPOSITION_LAYER_CYLINDER_EXTENSION_NAME);
    }
    if (OpenXRExtensions::IsExtensionSupported(XR_EXT_HAND_TRACKING_EXTENSION_NAME)) {
        extensions.push_back(XR_EXT_HAND_TRACKING_EXTENSION_NAME);
        if (OpenXRExtensions::IsExtensionSupported(XR_FB_HAND_TRACKING_AIM_EXTENSION_NAME)) {
            extensions.push_back(XR_FB_HAND_TRACKING_AIM_EXTENSION_NAME);
        }
        if (OpenXRExtensions::IsExtensionSupported(XR_FB_HAND_TRACKING_MESH_EXTENSION_NAME)) {
            extensions.push_back(XR_FB_HAND_TRACKING_MESH_EXTENSION_NAME);
        }
    }
    if (OpenXRExtensions::IsExtensionSupported(XR_EXT_PERFORMANCE_SETTINGS_EXTENSION_NAME)) {
        extensions.push_back(XR_EXT_PERFORMANCE_SETTINGS_EXTENSION_NAME);
    }
    if (OpenXRExtensions::IsExtensionSupported(XR_FB_DISPLAY_REFRESH_RATE_EXTENSION_NAME)) {
        extensions.push_back(XR_FB_DISPLAY_REFRESH_RATE_EXTENSION_NAME);
    }
#ifdef OCULUSVR
    if (OpenXRExtensions::IsExtensionSupported(XR_KHR_COMPOSITION_LAYER_EQUIRECT2_EXTENSION_NAME)) {
      extensions.push_back(XR_KHR_COMPOSITION_LAYER_EQUIRECT2_EXTENSION_NAME);
    }
    if (OpenXRExtensions::IsExtensionSupported(XR_FB_COMPOSITION_LAYER_IMAGE_LAYOUT_EXTENSION_NAME)) {
      extensions.push_back(XR_FB_COMPOSITION_LAYER_IMAGE_LAYOUT_EXTENSION_NAME);
    }
#endif
    if (OpenXRExtensions::IsExtensionSupported(XR_KHR_COMPOSITION_LAYER_COLOR_SCALE_BIAS_EXTENSION_NAME))
        extensions.push_back(XR_KHR_COMPOSITION_LAYER_COLOR_SCALE_BIAS_EXTENSION_NAME);

    if (OpenXRExtensions::IsExtensionSupported(XR_FB_PASSTHROUGH_EXTENSION_NAME)) {
      extensions.push_back(XR_FB_PASSTHROUGH_EXTENSION_NAME);
    }

    java = {XR_TYPE_INSTANCE_CREATE_INFO_ANDROID_KHR};
    java.applicationVM = javaContext->vm;
    java.applicationActivity = javaContext->activity;

    XrInstanceCreateInfo createInfo{XR_TYPE_INSTANCE_CREATE_INFO};
    createInfo.next = (XrBaseInStructure*)&java;
    createInfo.enabledExtensionCount = (uint32_t)extensions.size();
    createInfo.enabledExtensionNames = extensions.data();
    strcpy(createInfo.applicationInfo.applicationName, "Wolvic");
    createInfo.applicationInfo.apiVersion = XR_CURRENT_API_VERSION;

    CHECK_XRCMD(xrCreateInstance(&createInfo, &instance));
    CHECK_MSG(instance != XR_NULL_HANDLE, "Failed to create XRInstance");

    XrInstanceProperties instanceProperties{XR_TYPE_INSTANCE_PROPERTIES};
    CHECK_XRCMD(xrGetInstanceProperties(instance, &instanceProperties));
    VRB_LOG("OpenXR Instance Created: RuntimeName=%s RuntimeVersion=%s", instanceProperties.runtimeName,
            GetXrVersionString(instanceProperties.runtimeVersion).c_str());

    // Load Extensions
    OpenXRExtensions::LoadExtensions(instance);

    // Initialize System
    XrSystemGetInfo systemInfo{XR_TYPE_SYSTEM_GET_INFO };
    systemInfo.formFactor = XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY;
    CHECK_XRCMD(xrGetSystem(instance, &systemInfo, &system));
    CHECK_MSG(system != XR_NULL_SYSTEM_ID, "Failed to initialize XRSystem");

    // If hand tracking extension is present, query whether the runtime actually supports it
    XrSystemHandTrackingPropertiesEXT handTrackingProperties{XR_TYPE_SYSTEM_HAND_TRACKING_PROPERTIES_EXT};
    handTrackingProperties.supportsHandTracking = XR_FALSE;
    if (OpenXRExtensions::IsExtensionSupported(XR_EXT_HAND_TRACKING_EXTENSION_NAME)) {
        handTrackingProperties.next = systemProperties.next;
        systemProperties.next = &handTrackingProperties;
    }

    // Retrieve system info
    CHECK_XRCMD(xrGetSystemProperties(instance, system, &systemProperties))
    VRB_LOG("OpenXR system name: %s", systemProperties.systemName);

    mHandTrackingSupported = handTrackingProperties.supportsHandTracking;
    VRB_LOG("OpenXR runtime %s hand tracking", mHandTrackingSupported ? "does support" : "doesn't support");

    InitializeDeviceType();
  }

  // xrGet*GraphicsRequirementsKHR check must be called prior to xrCreateSession
  // xrCreateSession fails if we don't call it.
  void CheckGraphicsRequirements() {

    XrGraphicsRequirementsOpenGLESKHR graphicsRequirements{XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_ES_KHR};
    CHECK_XRCMD(OpenXRExtensions::sXrGetOpenGLESGraphicsRequirementsKHR(instance, system, &graphicsRequirements));

    GLint major = 0;
    GLint minor = 0;
    glGetIntegerv(GL_MAJOR_VERSION, &major);
    glGetIntegerv(GL_MINOR_VERSION, &minor);

    const XrVersion desiredApiVersion = XR_MAKE_VERSION(major, minor, 0);
    if (graphicsRequirements.minApiVersionSupported > desiredApiVersion) {
      THROW("Runtime does not support desired Graphics API and/or version");
    }
  }

  void InitializeSwapChainFormats() {
    uint32_t swapchainFormatCount;
    CHECK_XRCMD(xrEnumerateSwapchainFormats(session, 0, &swapchainFormatCount, nullptr));
    CHECK_MSG(swapchainFormatCount > 0, "OpenXR unexpected swapchainFormatCount");
    swapchainFormats.resize(swapchainFormatCount, 0);
    CHECK_XRCMD(xrEnumerateSwapchainFormats(session, (uint32_t)swapchainFormats.size(), &swapchainFormatCount,
                                            swapchainFormats.data()));
    VRB_LOG("OpenXR Available color formats: %d", swapchainFormatCount);
  }

  bool SupportsColorFormat(int64_t aColorFormat) {
    if (swapchainFormats.size() == 0) {
      InitializeSwapChainFormats();
    }
    return std::find(swapchainFormats.begin(), swapchainFormats.end(), aColorFormat) != swapchainFormats.end();
  }

  void InitializeViews() {
    CHECK(session != XR_NULL_HANDLE)
    // Enumerate configurations
    uint32_t viewCount;
    CHECK_XRCMD(xrEnumerateViewConfigurationViews(instance, system, viewConfigType, 0, &viewCount, nullptr));
    CHECK_MSG(viewCount > 0, "OpenXR unexpected viewCount");
    viewConfig.resize(viewCount, {XR_TYPE_VIEW_CONFIGURATION_VIEW});
    CHECK_XRCMD(xrEnumerateViewConfigurationViews(instance, system, viewConfigType, viewCount, &viewCount, viewConfig.data()));

    // Cache view buffer (used in xrLocateViews)
    views.resize(viewCount, {XR_TYPE_VIEW});

    vrb::RenderContextPtr render = context.lock();

    // Create the main swapChain for each eye view
    for (uint32_t i = 0; i < viewCount; i++) {
      auto swapChain = OpenXRSwapChain::create();
      XrSwapchainCreateInfo info = GetSwapChainCreateInfo();
      swapChain->InitFBO(render, session, info, GetFBOAttributes());
      eyeSwapChains.push_back(swapChain);
    }
    VRB_DEBUG("OpenXR available views: %d", (int)eyeSwapChains.size());
  }

  void InitializeImmersiveDisplay() {
    CHECK(immersiveDisplay);
    CHECK(viewConfig.size() > 0);

    immersiveDisplay->SetDeviceName(systemProperties.systemName);
    immersiveDisplay->SetEyeResolution(viewConfig.front().recommendedImageRectWidth, viewConfig.front().recommendedImageRectHeight);
    immersiveDisplay->SetSittingToStandingTransform(vrb::Matrix::Translation(kAverageHeight));
    immersiveDisplay->CompleteEnumeration();
  }

  void InitializeRefreshRates() {
    CHECK(session);
    if (!OpenXRExtensions::IsExtensionSupported(XR_FB_DISPLAY_REFRESH_RATE_EXTENSION_NAME))
      return;

    uint32_t displayRefreshRateCount = 0;
    CHECK_XRCMD(OpenXRExtensions::sXrEnumerateDisplayRefreshRatesFB(session, 0, &displayRefreshRateCount, nullptr));
    CHECK(displayRefreshRateCount > 0);
    refreshRates.resize(displayRefreshRateCount);
    CHECK_XRCMD(OpenXRExtensions::sXrEnumerateDisplayRefreshRatesFB(session, displayRefreshRateCount, &displayRefreshRateCount, refreshRates.data()));

    {
      std::stringstream ratesStream;
      for (auto rate : refreshRates)
          ratesStream << rate << ", ";
      std::string result = ratesStream.str().substr(0, ratesStream.str().size() - 2);
      VRB_DEBUG("OpenXR device supports %u refresh rates: %s", displayRefreshRateCount, result.c_str());
    }
  }

  XrSwapchainCreateInfo GetSwapChainCreateInfo(uint32_t w = 0, uint32_t h = 0) {
#if OCULUSVR
    const int64_t colorFormat = GL_SRGB8_ALPHA8;
#else
    const int64_t colorFormat = GL_RGBA8;
#endif
    CHECK_MSG(SupportsColorFormat(colorFormat), "Runtime doesn't support selected swapChain color format");

    CHECK(viewConfig.size() > 0);

    if (w == 0 || h == 0) {
      w = viewConfig.front().recommendedImageRectWidth;
      h = viewConfig.front().recommendedImageRectHeight;
    }

    XrSwapchainCreateInfo info{XR_TYPE_SWAPCHAIN_CREATE_INFO};
    info.arraySize = 1;
    info.format = colorFormat;
    info.width = w;
    info.height = h;
    info.mipCount = 1;
    info.faceCount = 1;
    info.arraySize = 1;
    info.sampleCount = viewConfig.front().recommendedSwapchainSampleCount;
    info.usageFlags = XR_SWAPCHAIN_USAGE_SAMPLED_BIT | XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT;
    return info;
  }

  vrb::FBO::Attributes GetFBOAttributes() const {
    vrb::FBO::Attributes attributes;
    if (renderMode == device::RenderMode::StandAlone) {
      attributes.depth = true;
      attributes.samples = 4;
    } else {
      attributes.depth = false;
      attributes.samples = 0;
    }
    return attributes;
  }

  void UpdateSpaces() {
    CHECK(session != XR_NULL_HANDLE);

    // Query supported reference spaces
    uint32_t spaceCount = 0;
    CHECK_XRCMD(xrEnumerateReferenceSpaces(session, 0, &spaceCount, nullptr));
    std::vector<XrReferenceSpaceType> spaces(spaceCount);
    CHECK_XRCMD(xrEnumerateReferenceSpaces(session, spaceCount, &spaceCount, spaces.data()));
    VRB_DEBUG("OpenXR Available reference spaces: %d", spaceCount);
    for (XrReferenceSpaceType space : spaces) {
      VRB_DEBUG("  OpenXR Space Name: %s", to_string(space));
    }

    auto supportsSpace = [&](XrReferenceSpaceType aType) -> bool {
      return std::find(spaces.begin(), spaces.end(), aType) != spaces.end();
    };

    // Initialize view spaces used by default
    if (viewSpace == XR_NULL_HANDLE) {
      CHECK_MSG(supportsSpace(XR_REFERENCE_SPACE_TYPE_VIEW), "XR_REFERENCE_SPACE_TYPE_VIEW not supported");
      XrReferenceSpaceCreateInfo create{XR_TYPE_REFERENCE_SPACE_CREATE_INFO};
      create.poseInReferenceSpace = XrPoseIdentity();
      create.referenceSpaceType = XR_REFERENCE_SPACE_TYPE_VIEW;
      CHECK_XRCMD(xrCreateReferenceSpace(session, &create, &viewSpace));
    }

    if (localSpace == XR_NULL_HANDLE) {
      CHECK_MSG(supportsSpace(XR_REFERENCE_SPACE_TYPE_LOCAL), "XR_REFERENCE_SPACE_TYPE_LOCAL not supported");
      XrReferenceSpaceCreateInfo create{XR_TYPE_REFERENCE_SPACE_CREATE_INFO};
      create.poseInReferenceSpace = XrPoseIdentity();
      create.referenceSpaceType = XR_REFERENCE_SPACE_TYPE_LOCAL;
      CHECK_XRCMD(xrCreateReferenceSpace(session, &create, &localSpace));
    }

    if (layersSpace == XR_NULL_HANDLE) {
      CHECK_MSG(supportsSpace(XR_REFERENCE_SPACE_TYPE_LOCAL), "XR_REFERENCE_SPACE_TYPE_LOCAL not supported");
      XrReferenceSpaceCreateInfo create{XR_TYPE_REFERENCE_SPACE_CREATE_INFO};
      create.poseInReferenceSpace  = XrPoseIdentity();
      create.poseInReferenceSpace.position = {
        -kAverageHeight.x(), -kAverageHeight.y(), -kAverageHeight.z()
      };
      create.referenceSpaceType = XR_REFERENCE_SPACE_TYPE_LOCAL;
      CHECK_XRCMD(xrCreateReferenceSpace(session, &create, &layersSpace));
    }

    // Optionally create a stageSpace to be used in WebXR room scale apps.
    if (stageSpace == XR_NULL_HANDLE && supportsSpace(XR_REFERENCE_SPACE_TYPE_STAGE)) {
      XrReferenceSpaceCreateInfo create{XR_TYPE_REFERENCE_SPACE_CREATE_INFO};
      create.poseInReferenceSpace = XrPoseIdentity();
      create.referenceSpaceType = XR_REFERENCE_SPACE_TYPE_STAGE;
      CHECK_XRCMD(xrCreateReferenceSpace(session, &create, &stageSpace));
    }
  }

  void AddUILayer(const OpenXRLayerPtr& aLayer, VRLayerSurface::SurfaceType aSurfaceType) {
    if (session != XR_NULL_HANDLE) {
      vrb::RenderContextPtr ctx = context.lock();
      aLayer->Init(javaContext->env, session, ctx);
    }
    uiLayers.push_back(aLayer);
    if (aSurfaceType == VRLayerSurface::SurfaceType::FBO) {
      aLayer->SetBindDelegate([=](const OpenXRSwapChainPtr& aSwapchain, GLenum  aTarget, bool bound){
        if (aSwapchain) {
          HandleQuadLayerBind(aSwapchain, aTarget, bound);
        }
      });
      if (boundSwapChain) {
        boundSwapChain->BindFBO();
      }
    }
  }

  void HandleQuadLayerBind(const OpenXRSwapChainPtr& aSwapchain, GLenum  aTarget, bool bound) {
    if (!bound) {
      if (boundSwapChain && boundSwapChain == aSwapchain) {
        boundSwapChain->ReleaseImage();
        boundSwapChain = nullptr;
      }
      if (previousBoundSwapchain) {
        previousBoundSwapchain->BindFBO();
        boundSwapChain = previousBoundSwapchain;
        previousBoundSwapchain = nullptr;
      }
      return;
    }

    if (boundSwapChain == aSwapchain) {
      // Layer already bound
      return;
    }

    previousBoundSwapchain = boundSwapChain;
    boundSwapChain = aSwapchain;
    boundSwapChain->AcquireImage();
    boundSwapChain->BindFBO(aTarget);
  }

  const XrEventDataBaseHeader* PollEvent() {
    if (!instance) {
      return nullptr;
    }
    XrEventDataBaseHeader* baseHeader = reinterpret_cast<XrEventDataBaseHeader*>(&eventBuffer);
    *baseHeader = {XR_TYPE_EVENT_DATA_BUFFER};
    const XrResult xr = xrPollEvent(instance, &eventBuffer);
    if (xr == XR_SUCCESS) {
      return baseHeader;
    }

    CHECK_MSG(xr == XR_EVENT_UNAVAILABLE, "Expected XR_EVENT_UNAVAILABLE result")
    return nullptr;
  }

  const char* GetDefaultInteractionProfilePath() {
#if OCULUSVR
      return OculusTouch.path;
#elif PICOXR
      return Pico4.path;
#else
      return KHRSimple.path;
#endif
  }

  void BeginXRSession() {
      XrSessionBeginInfo sessionBeginInfo{XR_TYPE_SESSION_BEGIN_INFO};
      sessionBeginInfo.primaryViewConfigurationType = viewConfigType;
      CHECK_XRCMD(xrBeginSession(session, &sessionBeginInfo));
      vrReady = true;

      // If hand tracking is supported, we want to emulate a default interaction
      // profile, so that if Wolvic is launched without controllers active, we can
      // still use hand tracking for emulating the controllers.
      // This is a temporary situation while we don't implement WebXR hand tracking
      // APIs.
      if (mHandTrackingSupported) {
          if (const char* defaultProfilePath = GetDefaultInteractionProfilePath())
              UpdateInteractionProfile(defaultProfilePath);
      }
  }

  void HandleSessionEvent(const XrEventDataSessionStateChanged& event) {
    VRB_LOG("OpenXR XrEventDataSessionStateChanged: state %s->%s session=%p time=%ld",
        to_string(sessionState), to_string(event.state), event.session, event.time);
    auto previousSessionState = std::exchange(sessionState, event.state);

    if (event.session != XR_NULL_HANDLE && session != XR_NULL_HANDLE) {
      CHECK(session == event.session);
    }

    switch (sessionState) {
      case XR_SESSION_STATE_READY: {
        VRB_LOG("XR_SESSION_STATE_READY");
        BeginXRSession();
        break;
      }
      case XR_SESSION_STATE_VISIBLE: {
        VRB_LOG("XR_SESSION_STATE_VISIBLE");
        if (previousSessionState == XR_SESSION_STATE_FOCUSED)
            VRBrowser::OnAppFocusChanged(false);
        break;
      }
      case XR_SESSION_STATE_FOCUSED: {
        VRB_LOG("XR_SESSION_STATE_FOCUSED");
        CHECK(previousSessionState == XR_SESSION_STATE_VISIBLE);
          VRBrowser::OnAppFocusChanged(true);
        break;
      }
      case XR_SESSION_STATE_SYNCHRONIZED: {
        VRB_LOG("XR_SESSION_STATE_SYNCHRONIZED");
        break;
      }
      case XR_SESSION_STATE_STOPPING: {
        VRB_LOG("XR_SESSION_STATE_STOPPING");
        vrReady = false;
        CHECK_XRCMD(xrEndSession(session))
        break;
      }
      case XR_SESSION_STATE_EXITING: {
        VRB_LOG("XR_SESSION_STATE_EXITING");
        vrReady = false;
        break;
      }
      case XR_SESSION_STATE_LOSS_PENDING: {
        VRB_LOG("XR_SESSION_STATE_LOSS_PENDING");
        vrReady = false;
        break;
      }
      default:
        break;
    }
  }

  void UpdateClockLevels() {
      if (!OpenXRExtensions::IsExtensionSupported(XR_EXT_PERFORMANCE_SETTINGS_EXTENSION_NAME))
          return;

      if (renderMode == device::RenderMode::StandAlone && minCPULevel == device::CPULevel::Normal) {
          CHECK_XRCMD(OpenXRExtensions::sXrPerfSettingsSetPerformanceLevelEXT(session, XR_PERF_SETTINGS_DOMAIN_CPU_EXT, XR_PERF_SETTINGS_LEVEL_SUSTAINED_LOW_EXT));
          CHECK_XRCMD(OpenXRExtensions::sXrPerfSettingsSetPerformanceLevelEXT(session, XR_PERF_SETTINGS_DOMAIN_GPU_EXT, XR_PERF_SETTINGS_LEVEL_SUSTAINED_LOW_EXT));
      } else {
          CHECK_XRCMD(OpenXRExtensions::sXrPerfSettingsSetPerformanceLevelEXT(session, XR_PERF_SETTINGS_DOMAIN_CPU_EXT, XR_PERF_SETTINGS_LEVEL_SUSTAINED_HIGH_EXT));
          CHECK_XRCMD(OpenXRExtensions::sXrPerfSettingsSetPerformanceLevelEXT(session, XR_PERF_SETTINGS_DOMAIN_GPU_EXT, XR_PERF_SETTINGS_LEVEL_SUSTAINED_HIGH_EXT));
      }
  }

  void UpdateDisplayRefreshRate() {
    if (!OpenXRExtensions::IsExtensionSupported(XR_FB_DISPLAY_REFRESH_RATE_EXTENSION_NAME))
      return;

    float suggestedRefreshRate = 0.0;
    switch (deviceType) {
      case device::OculusQuest2:
      case device::MetaQuestPro:
      // PicoXR default is 72hz, but has an experimental setting to set it to 90hz. If the setting
      // is disabled we'll select 72hz which is the only one advertised by OpenXR in that case.
      case device::PicoXR:
        suggestedRefreshRate = 90.0;
        break;
      case device::OculusQuest:
        suggestedRefreshRate = 72.0;
        break;
      default:
        suggestedRefreshRate = 60.0;
        break;
    }

    // Selects the first available refresh rate equal or higher than the desired one. Note that
    // OpenXR returns the refresh rates sorted from lowest to highest.
    auto selectValidRefreshRate = [refreshRates = this->refreshRates](const float suggestedRefreshRate) {
      auto it = std::find_if(refreshRates.begin(), refreshRates.end(),
   [&suggestedRefreshRate](const float& refreshRate) { return refreshRate >= suggestedRefreshRate;});
      if (it == refreshRates.end())
        return refreshRates.back();
      return *it;
    };

    float selectedRefreshRate = selectValidRefreshRate(suggestedRefreshRate);
    VRB_DEBUG("OpenXR setting refresh rate to %.0fhz", selectedRefreshRate);
    CHECK_XRCMD(OpenXRExtensions::sXrRequestDisplayRefreshRateFB(session, selectedRefreshRate));
  }

  void Shutdown() {
    // Release swapChains
    if (!eyeSwapChains.empty()) {
        eyeSwapChains.clear();
    }

    // Release Layers
    if (!uiLayers.empty()) {
        uiLayers.clear();
    }

    if (cubeLayer != XR_NULL_HANDLE) {
      cubeLayer->Destroy();
      cubeLayer = XR_NULL_HANDLE;
    }

    if (equirectLayer != XR_NULL_HANDLE) {
      equirectLayer->Destroy();
      equirectLayer = XR_NULL_HANDLE;
    }

    // Release spaces
    if (viewSpace != XR_NULL_HANDLE) {
      CHECK_XRCMD(xrDestroySpace(viewSpace));
      viewSpace = XR_NULL_HANDLE;
    }

    if (localSpace != XR_NULL_HANDLE) {
      CHECK_XRCMD(xrDestroySpace(localSpace));
      localSpace = XR_NULL_HANDLE;
    }

    if (layersSpace != XR_NULL_HANDLE) {
      CHECK_XRCMD(xrDestroySpace(layersSpace));
      layersSpace = XR_NULL_HANDLE;
    }

    if (stageSpace != XR_NULL_HANDLE) {
      CHECK_XRCMD(xrDestroySpace(stageSpace));
      stageSpace = XR_NULL_HANDLE;
    }

    // Release input
    input = nullptr;

    if (session) {
      CHECK_XRCMD(xrDestroySession(session));
      session = XR_NULL_HANDLE;
    }

    // Shutdown OpenXR instance
    if (instance) {
      CHECK_XRCMD(xrDestroyInstance(instance));
      instance = XR_NULL_HANDLE;
    }

    // TODO: Check if activity globarRef needs to be released
  }

  void UpdateInteractionProfile(const char* emulateProfile = nullptr) {
      if (!input || !controller)
          return;

      input->UpdateInteractionProfile(*controller, emulateProfile);
      if (controllersReadyCallback && input->AreControllersReady()) {
          controllersReadyCallback();
          controllersReadyCallback = nullptr;
      }
  }
};

DeviceDelegateOpenXRPtr
DeviceDelegateOpenXR::Create(vrb::RenderContextPtr& aContext, JavaContext* aJavaContext) {
  DeviceDelegateOpenXRPtr result = std::make_shared<vrb::ConcreteClass<DeviceDelegateOpenXR, DeviceDelegateOpenXR::State> >();
  result->m.context = aContext;
  result->m.javaContext = aJavaContext;
  result->m.Initialize();
  return result;
}

device::DeviceType
DeviceDelegateOpenXR::GetDeviceType() {
  return m.deviceType;
}

void
DeviceDelegateOpenXR::SetRenderMode(const device::RenderMode aMode) {
  if (aMode == m.renderMode) {
    return;
  }
  m.renderMode = aMode;
  vrb::RenderContextPtr render = m.context.lock();
  for (OpenXRSwapChainPtr& eyeSwapchain: m.eyeSwapChains) {
    XrSwapchainCreateInfo info = m.GetSwapChainCreateInfo();
    eyeSwapchain->InitFBO(render, m.session, info, m.GetFBOAttributes());
  }

  m.UpdateClockLevels();
  m.UpdateDisplayRefreshRate();

  // Reset reorient when exiting or entering immersive
  m.reorientMatrix = vrb::Matrix::Identity();
}

device::RenderMode
DeviceDelegateOpenXR::GetRenderMode() {
  return m.renderMode;
}

void
DeviceDelegateOpenXR::RegisterImmersiveDisplay(ImmersiveDisplayPtr aDisplay) {
  m.immersiveDisplay = std::move(aDisplay);
}

void
DeviceDelegateOpenXR::SetImmersiveSize(const uint32_t aEyeWidth, const uint32_t aEyeHeight) {

}

vrb::CameraPtr
DeviceDelegateOpenXR::GetCamera(const device::Eye aWhich) {
  const int32_t index = device::EyeIndex(aWhich);
  if (index < 0) { return nullptr; }
  return m.cameras[index];
}

const vrb::Matrix&
DeviceDelegateOpenXR::GetHeadTransform() const {
  return m.cameras[0]->GetHeadTransform();
}

const vrb::Matrix&
DeviceDelegateOpenXR::GetReorientTransform() const {
  return m.reorientMatrix;
}

void
DeviceDelegateOpenXR::SetReorientTransform(const vrb::Matrix& aMatrix) {
  m.reorientMatrix = aMatrix;
}

void
DeviceDelegateOpenXR::SetClearColor(const vrb::Color& aColor) {
  m.clearColor = aColor;
}

void
DeviceDelegateOpenXR::SetClipPlanes(const float aNear, const float aFar) {
  m.near = aNear;
  m.far = aFar;
}

void
DeviceDelegateOpenXR::SetControllerDelegate(ControllerDelegatePtr& aController) {
  m.controller = aController;
}

void
DeviceDelegateOpenXR::ReleaseControllerDelegate() {
  m.controller = nullptr;
}

int32_t
DeviceDelegateOpenXR::GetControllerModelCount() const {
  return m.input->GetControllerModelCount();
}

const std::string
DeviceDelegateOpenXR::GetControllerModelName(const int32_t aModelIndex) const {
  return m.input->GetControllerModelName(aModelIndex);
}

bool
DeviceDelegateOpenXR::IsPositionTrackingSupported() const {
  // returns true for 6DoF controllers
  return m.IsPositionTrackingSupported();
}

void
DeviceDelegateOpenXR::OnControllersReady(const std::function<void()>& callback) {
  if (m.input && m.input->AreControllersReady()) {
    callback();
    return;
  }
  m.controllersReadyCallback = callback;
}

void
DeviceDelegateOpenXR::SetCPULevel(const device::CPULevel aLevel) {
  m.minCPULevel = aLevel;
  m.UpdateClockLevels();
};

void
DeviceDelegateOpenXR::ProcessEvents() {
  while (const XrEventDataBaseHeader* ev = m.PollEvent()) {
    switch (ev->type) {
      case XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED: {
        const auto& event = *reinterpret_cast<const XrEventDataSessionStateChanged*>(ev);
        m.HandleSessionEvent(event);
        break;
      }
      case XR_TYPE_EVENT_DATA_EVENTS_LOST: {
        const auto& event = *reinterpret_cast<const XrEventDataEventsLost*>(ev);
        VRB_WARN("OpenXR %d events lost", event.lostEventCount);
        break;
      }
      case XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING: {
        // Receiving the XrEventDataInstanceLossPending event structure indicates that the application
        // is about to lose the indicated XrInstance at the indicated lossTime in the future.
        const auto& event = *reinterpret_cast<const XrEventDataInstanceLossPending*>(ev);
        VRB_WARN("OpenXR XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING by %ld", event.lossTime);
        m.vrReady = false;
        return;
      }
      case XR_TYPE_EVENT_DATA_INTERACTION_PROFILE_CHANGED: {
        m.UpdateInteractionProfile();
        break;
      }
      case XR_TYPE_EVENT_DATA_REFERENCE_SPACE_CHANGE_PENDING:
        m.firstPose = std::nullopt;
        m.reorientRequested = true;
        VRB_DEBUG("OpenXR: reference space changed. User recentered the view?");
        break;
      default: {
        VRB_DEBUG("OpenXR ignoring event type %d", ev->type);
        break;
      }
    }
  }
}

bool
DeviceDelegateOpenXR::SupportsFramePrediction(FramePrediction aPrediction) const {
  return true;
}

void
DeviceDelegateOpenXR::StartFrame(const FramePrediction aPrediction) {
  mShouldRender = false;
  if (!m.vrReady) {
    VRB_ERROR("OpenXR StartFrame called while not in VR mode");
    return;
  }

#if OCULUSVR || PICOXR
  // Fix brigthness issue.
  glDisable(GL_FRAMEBUFFER_SRGB_EXT);
#endif

  CHECK(m.session != XR_NULL_HANDLE);
  CHECK(m.viewSpace != XR_NULL_HANDLE);

  // Throttle the application frame loop in order to synchronize
  // application frame submissions with the display.
  XrFrameWaitInfo frameWaitInfo{XR_TYPE_FRAME_WAIT_INFO};
  XrFrameState frameState{XR_TYPE_FRAME_STATE};
  CHECK_XRCMD(xrWaitFrame(m.session, &frameWaitInfo, &frameState));

  // Begin frame and select the predicted display time
  XrFrameBeginInfo frameBeginInfo{XR_TYPE_FRAME_BEGIN_INFO};
  CHECK_XRCMD(xrBeginFrame(m.session, &frameBeginInfo));

  m.framePrediction = aPrediction;
  if (aPrediction == FramePrediction::ONE_FRAME_AHEAD) {
    m.prevPredictedDisplayTime = m.predictedDisplayTime;
    m.prevPredictedPose = m.predictedPose;
    m.prevViews = m.views;
    m.predictedDisplayTime = frameState.predictedDisplayTime + frameState.predictedDisplayPeriod;
  } else {
    m.predictedDisplayTime = frameState.predictedDisplayTime;
  }

  mShouldRender = frameState.shouldRender;
  if (!frameState.shouldRender)
    return;

  // Query head location
  XrSpaceLocation location {XR_TYPE_SPACE_LOCATION};
  CHECK_XRCMD(xrLocateSpace(m.viewSpace, m.localSpace, m.predictedDisplayTime, &location));
  m.predictedPose = location.pose;
  if (!m.firstPose && location.pose.position.y != 0.0) {
    m.firstPose = location.pose;
  }

  vrb::Matrix head = XrPoseToMatrix(location.pose);
#if HVR
  if (IsPositionTrackingSupported()) {
    // Convert from floor to local (HVR doesn't support stageSpace yet)
    head.TranslateInPlace(vrb::Vector(-m.firstPose->position.x, -m.firstPose->position.y, -m.firstPose->position.z));
  }
#endif

  if (m.renderMode == device::RenderMode::StandAlone) {
    head.TranslateInPlace(kAverageHeight);
  }

  m.cameras[0]->SetHeadTransform(head);
  m.cameras[1]->SetHeadTransform(head);

  if (m.immersiveDisplay) {
    // Setup capability caps for this frame
    device::CapabilityFlags caps =
        device::Orientation | device::Present | device::InlineSession | device::ImmersiveVRSession;
    if (location.locationFlags & XR_SPACE_LOCATION_POSITION_VALID_BIT) {
      caps |= IsPositionTrackingSupported() ? device::Position : device::PositionEmulated;
    }

    // Update WebXR room scale transform if the device supports stage space
    if (m.stageSpace != XR_NULL_HANDLE) {
      caps |= device::StageParameters;

      // Compute the transform between local and stage space
      XrSpaceLocation stageLocation{XR_TYPE_SPACE_LOCATION};
      xrLocateSpace(m.localSpace, m.stageSpace, m.predictedDisplayTime, &stageLocation);
      vrb::Matrix transform = XrPoseToMatrix(stageLocation.pose);
      m.immersiveDisplay->SetSittingToStandingTransform(transform);
#if HVR
      // Workaround for empty stage transform bug in HVR
      if (IsPositionTrackingSupported()) {
          m.immersiveDisplay->SetSittingToStandingTransform(
                  vrb::Matrix::Translation(vrb::Vector(0.0f, m.firstPose->position.y, 0.0f)));
      } else {
          m.immersiveDisplay->SetSittingToStandingTransform(
                  vrb::Matrix::Translation(kAverageHeight));
      }
#endif
    }

    m.immersiveDisplay->SetCapabilityFlags(caps);
  }

  // Query eyeTransform and perspective for each view
  XrViewState viewState{XR_TYPE_VIEW_STATE};
  uint32_t viewCapacityInput = (uint32_t) m.views.size();
  uint32_t viewCountOutput = 0;

  // Eye transform
  {
    XrViewLocateInfo viewLocateInfo{XR_TYPE_VIEW_LOCATE_INFO};
    viewLocateInfo.viewConfigurationType = m.viewConfigType;
    viewLocateInfo.displayTime = m.predictedDisplayTime;
    viewLocateInfo.space = m.viewSpace;
    CHECK_XRCMD(xrLocateViews(m.session, &viewLocateInfo, &viewState, viewCapacityInput, &viewCountOutput, m.views.data()));
    for (int i = 0; i < m.views.size(); ++i) {
      const XrView &view = m.views[i];
      const device::Eye eye = i == 0 ? device::Eye::Left : device::Eye::Right;
      vrb::Matrix eyeTransform = XrPoseToMatrix(view.pose);
      m.cameras[i]->SetEyeTransform(eyeTransform);
      if (m.immersiveDisplay) {
        m.immersiveDisplay->SetEyeTransform(eye, eyeTransform);
      }
    }
  }

  // Perspective
  XrViewLocateInfo viewLocateInfo{XR_TYPE_VIEW_LOCATE_INFO};
  viewLocateInfo.viewConfigurationType = m.viewConfigType;
  viewLocateInfo.displayTime = m.predictedDisplayTime;
  viewLocateInfo.space = m.localSpace;
  CHECK_XRCMD(xrLocateViews(m.session, &viewLocateInfo, &viewState, viewCapacityInput, &viewCountOutput, m.views.data()));

  for (int i = 0; i < m.views.size(); ++i) {
    const XrView& view = m.views[i];

    vrb::Matrix perspective = vrb::Matrix::PerspectiveMatrix(fabsf(view.fov.angleLeft), view.fov.angleRight,
                                                             view.fov.angleUp, fabsf(view.fov.angleDown), m.near, m.far);
    m.cameras[i]->SetPerspective(perspective);
    if (m.immersiveDisplay) {
      const device::Eye eye = i == 0 ? device::Eye::Left : device::Eye::Right;
      auto toDegrees = [](float angle) -> float {
        return angle * 180.0f / (float)M_PI;
      };
      m.immersiveDisplay->SetFieldOfView(eye, toDegrees(fabsf(view.fov.angleLeft)), toDegrees(view.fov.angleRight),
                                         toDegrees(view.fov.angleUp), toDegrees(fabsf(view.fov.angleDown)));
    }
  }

  // Update controllers
  if (m.input && m.controller) {
    vrb::Vector offsets = vrb::Vector::Zero();
#if defined(HVR)
    offsets.y() = -m.firstPose->position.y;
#elif defined(OCULUSVR)
    offsets.x() = -0.025;
    offsets.y() = -0.05;
    offsets.z() = 0.05;
#endif
    m.input->Update(frameState, m.localSpace, head, offsets, m.renderMode, *m.controller);
  }

  if (m.reorientRequested && m.renderMode == device::RenderMode::StandAlone) {
      if (mReorientClient)
          mReorientClient->OnReorient();
      m.reorientMatrix = DeviceUtils::CalculateReorientationMatrix(head, kAverageHeight);
      m.reorientRequested = false;
  }
}

void
DeviceDelegateOpenXR::BindEye(const device::Eye aWhich) {
  if (!m.vrReady) {
    VRB_ERROR("OpenXR BindEye called while not in VR mode");
    return;
  }

  int32_t index = device::EyeIndex(aWhich);
  if (index < 0 || index >= m.eyeSwapChains.size()) {
    VRB_ERROR("No eye found");
    return;
  }

  if (m.boundSwapChain) {
    m.boundSwapChain->ReleaseImage();
  }

  m.boundSwapChain = m.eyeSwapChains[index];
  m.boundSwapChain->AcquireImage();
  m.boundSwapChain->BindFBO();
  VRB_GL_CHECK(glViewport(0, 0, m.boundSwapChain->Width(), m.boundSwapChain->Height()));
  VRB_GL_CHECK(glClearColor(m.clearColor.Red(), m.clearColor.Green(), m.clearColor.Blue(), m.clearColor.Alpha()));
  VRB_GL_CHECK(glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT));

  for (const auto& layer: m.uiLayers)
    layer->SetCurrentEye(aWhich);
}

void
DeviceDelegateOpenXR::EndFrame(const FrameEndMode aEndMode) {
  if (!m.vrReady) {
    VRB_ERROR("OpenXR EndFrame called while not in VR mode");
    return;
  }
  if (m.boundSwapChain) {
    m.boundSwapChain->ReleaseImage();
    m.boundSwapChain = nullptr;
  }

  const bool frameAhead = m.framePrediction == FramePrediction::ONE_FRAME_AHEAD;
  const XrPosef& predictedPose = frameAhead ? m.prevPredictedPose : m.predictedPose;
  const XrTime displayTime = frameAhead ? m.prevPredictedDisplayTime : m.predictedDisplayTime;
  auto& targetViews = frameAhead ? m.prevViews : m.views;

  std::vector<const XrCompositionLayerBaseHeader*>& layers = m.frameEndLayers;
  layers.clear();

  // This limit is valid at least for Pico and Meta.
  auto submitEndFrame = [&layers, displayTime, session = m.session]() {
      static int i = 0;
      XrFrameEndInfo frameEndInfo{XR_TYPE_FRAME_END_INFO};
      frameEndInfo.displayTime = displayTime;
      frameEndInfo.environmentBlendMode = XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
      frameEndInfo.layerCount = (uint32_t) layers.size();
      frameEndInfo.layers = layers.data();
      CHECK_XRCMD(xrEndFrame(session, &frameEndInfo));
  };

  auto canAddLayers = [&layers, maxLayers = m.systemProperties.graphicsProperties.maxLayerCount]() {
      return layers.size() < maxLayers;
  };

  if (!mShouldRender) {
      submitEndFrame();
      return;
  }

  // Add skybox layer
  if (m.cubeLayer && m.cubeLayer->IsLoaded() && m.cubeLayer->IsDrawRequested()) {
    m.cubeLayer->Update(m.localSpace, predictedPose, XR_NULL_HANDLE);
    for (uint32_t i = 0; i < m.cubeLayer->HeaderCount(); ++i) {
      layers.push_back(m.cubeLayer->Header(i));
    }
    m.cubeLayer->ClearRequestDraw();
  }

  // Add VR video layer
  if (m.equirectLayer && m.equirectLayer->IsDrawRequested()) {
    m.equirectLayer->Update(m.localSpace, predictedPose, XR_NULL_HANDLE);
    for (uint32_t i = 0; i < m.equirectLayer->HeaderCount(); ++i) {
      layers.push_back(m.equirectLayer->Header(i));
    }
    m.equirectLayer->ClearRequestDraw();
  }

  // Sort quad layers by draw priority
  std::sort(m.uiLayers.begin(), m.uiLayers.end(), [](const OpenXRLayerPtr & a, OpenXRLayerPtr & b) -> bool {
    return a->GetLayer()->ShouldDrawBefore(*b->GetLayer());
  });

  // Add back UI layers
  for (const OpenXRLayerPtr& layer: m.uiLayers) {
    if (!layer->GetDrawInFront() && layer->IsDrawRequested() && canAddLayers()) {
      layer->Update(m.layersSpace, predictedPose, XR_NULL_HANDLE);
      for (uint32_t i = 0; i < layer->HeaderCount() && canAddLayers(); ++i) {
        layers.push_back(layer->Header(i));
      }
      layer->ClearRequestDraw();
    }
  }

  if (!canAddLayers()) {
      submitEndFrame();
      return;
  }

  // Add main eye buffer layer
  XrCompositionLayerProjection projectionLayer{XR_TYPE_COMPOSITION_LAYER_PROJECTION};
  std::vector<XrCompositionLayerProjectionView> projectionLayerViews;
  projectionLayerViews.resize(targetViews.size());
  projectionLayer.layerFlags = XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT;
  for (int i = 0; i < targetViews.size(); ++i) {
    const OpenXRSwapChainPtr& viewSwapChain =  m.eyeSwapChains[i];
    projectionLayerViews[i] = {XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW};
    projectionLayerViews[i].pose = targetViews[i].pose;
    projectionLayerViews[i].fov = targetViews[i].fov;
    projectionLayerViews[i].subImage.swapchain = viewSwapChain->SwapChain();
    projectionLayerViews[i].subImage.imageRect.offset = {0, 0};
    projectionLayerViews[i].subImage.imageRect.extent = {viewSwapChain->Width(), viewSwapChain->Height()};
  }
  projectionLayer.space = m.localSpace;
  projectionLayer.viewCount = (uint32_t)projectionLayerViews.size();
  projectionLayer.views = projectionLayerViews.data();
  layers.push_back(reinterpret_cast<XrCompositionLayerBaseHeader*>(&projectionLayer));

  // Add front UI layers
  for (const OpenXRLayerPtr& layer: m.uiLayers) {
    if (layer->GetDrawInFront() && layer->IsDrawRequested() && canAddLayers()) {
      layer->Update(m.layersSpace, predictedPose, XR_NULL_HANDLE);
      for (uint32_t i = 0; i < layer->HeaderCount() && canAddLayers(); ++i) {
        layers.push_back(layer->Header(i));
      }
      layer->ClearRequestDraw();
    }
  }

  submitEndFrame();
}

VRLayerQuadPtr
DeviceDelegateOpenXR::CreateLayerQuad(int32_t aWidth, int32_t aHeight,
                                        VRLayerSurface::SurfaceType aSurfaceType) {
  if (!m.layersEnabled) {
    return nullptr;
  }

  VRLayerQuadPtr layer = VRLayerQuad::Create(aWidth, aHeight, aSurfaceType);
  OpenXRLayerQuadPtr xrLayer = OpenXRLayerQuad::Create(m.javaContext->env, layer);
  m.AddUILayer(xrLayer, aSurfaceType);
  return layer;
}

VRLayerQuadPtr
DeviceDelegateOpenXR::CreateLayerQuad(const VRLayerSurfacePtr& aMoveLayer) {
  if (!m.layersEnabled) {
    return nullptr;
  }

  VRLayerQuadPtr layer = VRLayerQuad::Create(aMoveLayer->GetWidth(), aMoveLayer->GetHeight(), aMoveLayer->GetSurfaceType());
  OpenXRLayerQuadPtr xrLayer;

  for (int i = 0; i < m.uiLayers.size(); ++i) {
    if (m.uiLayers[i]->GetLayer() == aMoveLayer) {
      xrLayer = OpenXRLayerQuad::Create(m.javaContext->env, layer, m.uiLayers[i]);
      m.uiLayers.erase(m.uiLayers.begin() + i);
      break;
    }
  }
  if (xrLayer) {
    m.AddUILayer(xrLayer, aMoveLayer->GetSurfaceType());
  }
  return layer;
}

VRLayerCylinderPtr
DeviceDelegateOpenXR::CreateLayerCylinder(int32_t aWidth, int32_t aHeight,
                                            VRLayerSurface::SurfaceType aSurfaceType) {
  if (!m.layersEnabled) {
    return nullptr;
  }

  VRLayerCylinderPtr layer = VRLayerCylinder::Create(aWidth, aHeight, aSurfaceType);
  OpenXRLayerCylinderPtr xrLayer = OpenXRLayerCylinder::Create(m.javaContext->env, layer);
  m.AddUILayer(xrLayer, aSurfaceType);
  return layer;
}

VRLayerCylinderPtr
DeviceDelegateOpenXR::CreateLayerCylinder(const VRLayerSurfacePtr& aMoveLayer) {
  if (!m.layersEnabled) {
    return nullptr;
  }

  VRLayerCylinderPtr layer = VRLayerCylinder::Create(aMoveLayer->GetWidth(), aMoveLayer->GetHeight(), aMoveLayer->GetSurfaceType());
  OpenXRLayerCylinderPtr xrLayer;

  for (int i = 0; i < m.uiLayers.size(); ++i) {
    if (m.uiLayers[i]->GetLayer() == aMoveLayer) {
      xrLayer = OpenXRLayerCylinder::Create(m.javaContext->env, layer, m.uiLayers[i]);
      m.uiLayers.erase(m.uiLayers.begin() + i);
      break;
    }
  }
  if (xrLayer) {
    m.AddUILayer(xrLayer, aMoveLayer->GetSurfaceType());
  }
  return layer;
}


VRLayerCubePtr
DeviceDelegateOpenXR::CreateLayerCube(int32_t aWidth, int32_t aHeight, GLint aInternalFormat) {
  if (!m.layersEnabled) {
    return nullptr;
  }
  if (m.cubeLayer) {
    m.cubeLayer->Destroy();
  }
  VRLayerCubePtr layer = VRLayerCube::Create(aWidth, aHeight, aInternalFormat);
  m.cubeLayer = OpenXRLayerCube::Create(layer, aInternalFormat);
  if (m.session != XR_NULL_HANDLE) {
    vrb::RenderContextPtr context = m.context.lock();
    m.cubeLayer->Init(m.javaContext->env, m.session, context);
  }
  return layer;
}

VRLayerEquirectPtr
DeviceDelegateOpenXR::CreateLayerEquirect(const VRLayerPtr &aSource) {
  if (!m.layersEnabled) {
    return nullptr;
  }

  VRLayerEquirectPtr result = VRLayerEquirect::Create();
  OpenXRLayerPtr source;
  for (const OpenXRLayerPtr& layer: m.uiLayers) {
    if (layer->GetLayer() == aSource) {
      source = layer;
      break;
    }
  }
  if (m.equirectLayer) {
    m.equirectLayer->Destroy();
  }
  m.equirectLayer = OpenXRLayerEquirect::Create(result, source);
  if (m.session != XR_NULL_HANDLE) {
    vrb::RenderContextPtr context = m.context.lock();
    m.equirectLayer->Init(m.javaContext->env, m.session, context);
  }
  return result;
}

void
DeviceDelegateOpenXR::DeleteLayer(const VRLayerPtr& aLayer) {
  if (m.cubeLayer && m.cubeLayer->layer == aLayer) {
    m.cubeLayer->Destroy();
    m.cubeLayer = nullptr;
    return;
  }
  if (m.equirectLayer && m.equirectLayer->layer == aLayer) {
    m.equirectLayer->Destroy();
    m.equirectLayer = nullptr;
    return;
  }
  for (int i = 0; i < m.uiLayers.size(); ++i) {
    if (m.uiLayers[i]->GetLayer() == aLayer) {
      m.uiLayers.erase(m.uiLayers.begin() + i);
      return;
    }
  }
}

void
DeviceDelegateOpenXR::EnterVR(const crow::BrowserEGLContext& aEGLContext) {
  // Reset reorientation after Enter VR
  m.reorientMatrix = vrb::Matrix::Identity();
  m.firstPose = std::nullopt;

  if (m.session != XR_NULL_HANDLE && m.graphicsBinding.context == aEGLContext.Context()) {
#if HVR
    // Session already created, call begin again. This can happen for example in HVR when reentering
    // the security zone, because HVR forces us to stop and end the session when exiting.
    m.BeginXRSession();
#endif
    ProcessEvents();
    return;
  }

  CHECK(m.instance != XR_NULL_HANDLE && m.system != XR_NULL_SYSTEM_ID);
  m.CheckGraphicsRequirements();

  m.graphicsBinding.context = aEGLContext.Context();
  m.graphicsBinding.display = aEGLContext.Display();
  m.graphicsBinding.config = aEGLContext.Config();

  XrSessionCreateInfo createInfo{XR_TYPE_SESSION_CREATE_INFO};
  createInfo.next = reinterpret_cast<const XrBaseInStructure*>(&m.graphicsBinding);
  createInfo.systemId = m.system;
  CHECK_XRCMD(xrCreateSession(m.instance, &createInfo, &m.session));
  CHECK(m.session != XR_NULL_HANDLE);
  VRB_LOG("OpenXR session created succesfully");

  m.UpdateSpaces();
  m.InitializeViews();
  m.InitializeImmersiveDisplay();
  m.InitializeRefreshRates();
#if OCULUSVR
  // See InitialiceDeviceType(). We overwrite the system name so that we load the proper input
  // mapping for the Quest Pro, as it incorrectly advertises itself as "Oculus Quest2"
  if (m.deviceType == device::MetaQuestPro)
      strcpy(m.systemProperties.systemName, "Meta Quest Pro");
#endif
  m.input = OpenXRInput::Create(m.instance, m.session, m.systemProperties, *m.controller.get());
  ProcessEvents();
  if (m.controllersReadyCallback && m.input && m.input->AreControllersReady()) {
    m.controllersReadyCallback();
    m.controllersReadyCallback = nullptr;
  }

  // Initialize layers if needed
  vrb::RenderContextPtr context = m.context.lock();
  for (OpenXRLayerPtr& layer: m.uiLayers) {
    layer->Init(m.javaContext->env, m.session, context);
  }
  if (m.cubeLayer) {
    m.cubeLayer->Init(m.javaContext->env, m.session, context);
  }
  if (m.equirectLayer) {
    m.equirectLayer->Init(m.javaContext->env, m.session, context);
  }

  m.UpdateClockLevels();
  m.UpdateDisplayRefreshRate();
}

void
DeviceDelegateOpenXR::LeaveVR() {
  CHECK_MSG(!m.boundSwapChain, "Eye swapChain not released before LeaveVR");
  if (m.session == XR_NULL_HANDLE) {
    return;
  }
#ifdef HVR
  xrRequestExitSession(m.session);
#endif
  ProcessEvents();
}

void
DeviceDelegateOpenXR::OnDestroy() {
  m.Shutdown();
}

bool
DeviceDelegateOpenXR::IsInVRMode() const {
  return m.vrReady;
}

bool
DeviceDelegateOpenXR::ExitApp() {
  return true;
}

bool
DeviceDelegateOpenXR::ShouldExitRenderLoop() const
{
  return m.sessionState == XR_SESSION_STATE_EXITING || m.sessionState == XR_SESSION_STATE_LOSS_PENDING;
}

DeviceDelegateOpenXR::DeviceDelegateOpenXR(State &aState) : m(aState) {}

DeviceDelegateOpenXR::~DeviceDelegateOpenXR() { m.Shutdown(); }

} // namespace crow
