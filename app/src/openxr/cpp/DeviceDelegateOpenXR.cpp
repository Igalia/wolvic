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

#include <android_native_app_glue.h>
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
#include <cstdlib>
#include <unistd.h>
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
#include "OpenXRExtensions.h"
#include "OpenXRLayers.h"

namespace crow {

const vrb::Vector kAverageHeight(0.0f, 1.7f, 0.0f);

struct DeviceDelegateOpenXR::State {
  vrb::RenderContextWeak context;
  android_app* app = nullptr;
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
  std::vector<OpenXRSwapChainPtr> eyeSwapChains;
  OpenXRSwapChainPtr boundSwapChain;
  OpenXRSwapChainPtr previousBoundSwapchain;
  XrSpace viewSpace = XR_NULL_HANDLE;
  XrSpace localSpace = XR_NULL_HANDLE;
  XrSpace layersSpace = XR_NULL_HANDLE;
  XrSpace stageSpace = XR_NULL_HANDLE;
  std::vector<int64_t> swapchainFormats;
  OpenXRInputPtr input;
  JNIEnv * jniEnv = nullptr;
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

  void Initialize() {
    vrb::RenderContextPtr localContext = context.lock();
    elbow = ElbowModel::Create();
    for (int i = 0; i < 2; ++i) {
      cameras[i] = vrb::CameraEye::Create(localContext->GetRenderThreadCreationContext());
    }
    layersEnabled = VRBrowser::AreLayersEnabled();

    (*app->activity->vm).AttachCurrentThread(&jniEnv, NULL);
    CHECK(jniEnv != nullptr);

#ifdef OCULUSVR
    // Adhoc loader required for OpenXR on Oculus
    PFN_xrInitializeLoaderKHR initializeLoaderKHR;
    CHECK_XRCMD(xrGetInstanceProcAddr(nullptr, "xrInitializeLoaderKHR", reinterpret_cast<PFN_xrVoidFunction*>(&initializeLoaderKHR)));
    XrLoaderInitInfoAndroidKHR loaderData;
    memset(&loaderData, 0, sizeof(loaderData));
    loaderData.type = XR_TYPE_LOADER_INIT_INFO_ANDROID_KHR;
    loaderData.next = nullptr;
    loaderData.applicationVM = app->activity->vm;
    loaderData.applicationContext = jniEnv->NewGlobalRef(app->activity->clazz);
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
#ifdef OCULUSVR
    if (OpenXRExtensions::IsExtensionSupported(XR_KHR_COMPOSITION_LAYER_EQUIRECT2_EXTENSION_NAME)) {
      extensions.push_back(XR_KHR_COMPOSITION_LAYER_EQUIRECT2_EXTENSION_NAME);
    }
#endif


    java = {XR_TYPE_INSTANCE_CREATE_INFO_ANDROID_KHR};
    java.applicationVM = app->activity->vm;
    java.applicationActivity = jniEnv->NewGlobalRef(app->activity->clazz);

    XrInstanceCreateInfo createInfo{XR_TYPE_INSTANCE_CREATE_INFO};
    createInfo.next = (XrBaseInStructure*)&java;
    createInfo.enabledExtensionCount = (uint32_t)extensions.size();
    createInfo.enabledExtensionNames = extensions.data();
    strcpy(createInfo.applicationInfo.applicationName, "Firefox Reality");
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
    XrSystemGetInfo systemInfo{XR_TYPE_SYSTEM_GET_INFO};
    systemInfo.formFactor = XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY;
    CHECK_XRCMD(xrGetSystem(instance, &systemInfo, &system));
    CHECK_MSG(system != XR_NULL_SYSTEM_ID, "Failed to initialize XRSystem");

    // Retrieve system info
    CHECK_XRCMD(xrGetSystemProperties(instance, system, &systemProperties))
    VRB_LOG("OpenXR system name: %s", systemProperties.systemName);

    input = OpenXRInput::Create(instance, systemProperties);
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

  XrSwapchainCreateInfo GetSwapChainCreateInfo(uint32_t w = 0, uint32_t h = 0) {
    const int64_t colorFormat = GL_RGBA8;
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
    if (stageSpace == XR_NULL_HANDLE && supportsSpace(XR_REFERENCE_SPACE_TYPE_LOCAL)) {
      XrReferenceSpaceCreateInfo create{XR_TYPE_REFERENCE_SPACE_CREATE_INFO};
      create.poseInReferenceSpace = XrPoseIdentity();
      create.referenceSpaceType = XR_REFERENCE_SPACE_TYPE_STAGE;
      CHECK_XRCMD(xrCreateReferenceSpace(session, &create, &stageSpace));
    }
  }

  void AddUILayer(const OpenXRLayerPtr& aLayer, VRLayerSurface::SurfaceType aSurfaceType) {
    if (session != XR_NULL_HANDLE) {
      vrb::RenderContextPtr ctx = context.lock();
      aLayer->Init(jniEnv, session, ctx);
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

  bool Is6DOF() const {
    return systemProperties.trackingProperties.positionTracking != 0;
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

  void HandleSessionEvent(const XrEventDataSessionStateChanged& event) {
    VRB_DEBUG("OpenXR XrEventDataSessionStateChanged: state %s->%s session=%p time=%ld",
        to_string(sessionState), to_string(event.state), event.session, event.time);
    sessionState = event.state;

    if (event.session != XR_NULL_HANDLE) {
      CHECK(session == event.session);
    }

    switch (sessionState) {
      case XR_SESSION_STATE_READY: {
        XrSessionBeginInfo sessionBeginInfo{XR_TYPE_SESSION_BEGIN_INFO};
        sessionBeginInfo.primaryViewConfigurationType = viewConfigType;
        CHECK_XRCMD(xrBeginSession(session, &sessionBeginInfo));
        vrReady = true;
        break;
      }
      case XR_SESSION_STATE_STOPPING: {
        vrReady = false;
        CHECK_XRCMD(xrEndSession(session))
        break;
      }
      case XR_SESSION_STATE_EXITING: {
        vrReady = false;
        break;
      }
      case XR_SESSION_STATE_LOSS_PENDING: {
        vrReady = false;
        break;
      }
      default:
        break;
    }
  }

  void UpdateClockLevels() {
    // TODO
  }


  void Shutdown() {
    // Release swapChains
    for (OpenXRSwapChainPtr swapChain: eyeSwapChains) {
      swapChain->Destroy();
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
    input->Destroy();
    input = nullptr;

    // Shutdown OpenXR instance
    if (instance) {
      CHECK_XRCMD(xrDestroyInstance(instance));
      instance = XR_NULL_HANDLE;
    }

    // TODO: Check if activity globarRef needs to be released
  }
};

DeviceDelegateOpenXRPtr
DeviceDelegateOpenXR::Create(vrb::RenderContextPtr& aContext, android_app *aApp) {
  DeviceDelegateOpenXRPtr result = std::make_shared<vrb::ConcreteClass<DeviceDelegateOpenXR, DeviceDelegateOpenXR::State> >();
  result->m.context = aContext;
  result->m.app = aApp;
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
      case XR_TYPE_EVENT_DATA_REFERENCE_SPACE_CHANGE_PENDING:
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
  if (!m.vrReady) {
    VRB_ERROR("OpenXR StartFrame called while not in VR mode");
    return;
  }

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

  CHECK_MSG(frameState.shouldRender, "shouldRender==false bailout not implemented yet");

  m.framePrediction = aPrediction;
  if (aPrediction == FramePrediction::ONE_FRAME_AHEAD) {
    m.prevPredictedDisplayTime = m.predictedDisplayTime;
    m.prevPredictedPose = m.predictedPose;
    m.predictedDisplayTime = frameState.predictedDisplayTime + frameState.predictedDisplayPeriod;
  } else {
    m.predictedDisplayTime = frameState.predictedDisplayTime;
  }

  // Query head location
  XrSpaceLocation location {XR_TYPE_SPACE_LOCATION};
  CHECK_XRCMD(xrLocateSpace(m.viewSpace, m.localSpace, m.predictedDisplayTime, &location));
  m.predictedPose = location.pose;

  vrb::Matrix head = XrPoseToMatrix(location.pose);

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
      caps |= m.Is6DOF() ? device::Position : device::PositionEmulated;
    }
    m.immersiveDisplay->SetCapabilityFlags(caps);

    // Update WebXR room scale transform if the device supports stage space
    if (m.stageSpace != XR_NULL_HANDLE) {
      // Compute the transform between local and stage space
      XrSpaceLocation stageLocation{XR_TYPE_SPACE_LOCATION};
      xrLocateSpace(m.localSpace, m.stageSpace, m.predictedDisplayTime, &stageLocation);
      vrb::Matrix transform = XrPoseToMatrix(stageLocation.pose);
      m.immersiveDisplay->SetSittingToStandingTransform(transform);
    }
  }

  // Query eyeTransform ans perspective for each view
  XrViewState viewState{XR_TYPE_VIEW_STATE};
  uint32_t viewCapacityInput = (uint32_t) m.views.size();
  uint32_t viewCountOutput = 0;

#ifdef HVR
  {
    XrViewLocateInfo offsetLocateInfo{XR_TYPE_VIEW_LOCATE_INFO};
    offsetLocateInfo.viewConfigurationType = m.viewConfigType;
    offsetLocateInfo.displayTime = m.predictedDisplayTime;
    offsetLocateInfo.space = m.viewSpace;
    CHECK_XRCMD(xrLocateViews(m.session, &offsetLocateInfo, &viewState, viewCapacityInput, &viewCountOutput, m.views.data()));
    for (int i = 0; i < m.views.size(); ++i) {
      const XrView &view = m.views[i];

      vrb::Matrix eyeTransform = XrPoseToMatrix(view.pose);
      m.cameras[i]->SetEyeTransform(eyeTransform);
    }
  };
#endif

  XrViewLocateInfo viewLocateInfo{XR_TYPE_VIEW_LOCATE_INFO};
  viewLocateInfo.viewConfigurationType = m.viewConfigType;
  viewLocateInfo.displayTime = m.predictedDisplayTime;
  viewLocateInfo.space = m.viewSpace;
  CHECK_XRCMD(xrLocateViews(m.session, &viewLocateInfo, &viewState, viewCapacityInput, &viewCountOutput, m.views.data()));

  for (int i = 0; i < m.views.size(); ++i) {
    const XrView& view = m.views[i];

    vrb::Matrix eyeTransform = XrPoseToMatrix(view.pose);
    m.cameras[i]->SetEyeTransform(eyeTransform);


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
      vrb::Vector offset = eyeTransform.GetTranslation();
      m.immersiveDisplay->SetEyeOffset(eye, offset.x(), offset.y(), offset.z());
    }
  }

#ifdef HVR
  // HVR requires to use localSpace with projectionLayer
  viewLocateInfo = { XR_TYPE_VIEW_LOCATE_INFO };
  viewLocateInfo.viewConfigurationType = m.viewConfigType;
  viewLocateInfo.displayTime = m.predictedDisplayTime;
  viewLocateInfo.space = m.localSpace;
  CHECK_XRCMD(xrLocateViews(m.session, &viewLocateInfo, &viewState, viewCapacityInput, &viewCountOutput, m.views.data()));
#endif

  // Update controllers
  m.input->Update(m.session, m.predictedDisplayTime, m.localSpace, m.renderMode, m.controller);
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

  std::vector<const XrCompositionLayerBaseHeader*>& layers = m.frameEndLayers;
  layers.clear();

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
    if (!layer->GetDrawInFront() && layer->IsDrawRequested()) {
      layer->Update(m.layersSpace, predictedPose, XR_NULL_HANDLE);
      for (uint32_t i = 0; i < layer->HeaderCount(); ++i) {
        layers.push_back(layer->Header(i));
      }
      layer->ClearRequestDraw();
    }
  }

  // Add main eye buffer layer
  XrCompositionLayerProjection projectionLayer{XR_TYPE_COMPOSITION_LAYER_PROJECTION};
  std::vector<XrCompositionLayerProjectionView> projectionLayerViews;
  projectionLayerViews.resize(m.views.size());
  projectionLayer.layerFlags = XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT;
  for (int i = 0; i < m.views.size(); ++i) {
    const OpenXRSwapChainPtr& viewSwapChain =  m.eyeSwapChains[i];
    projectionLayerViews[i] = {XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW};
    projectionLayerViews[i].pose = m.views[i].pose;
    projectionLayerViews[i].fov = m.views[i].fov;
    projectionLayerViews[i].subImage.swapchain = viewSwapChain->SwapChain();
    projectionLayerViews[i].subImage.imageRect.offset = {0, 0};
    projectionLayerViews[i].subImage.imageRect.extent = {viewSwapChain->Width(), viewSwapChain->Height()};
  }
#ifdef HVR
  projectionLayer.space = m.localSpace;
#else
  projectionLayer.space = m.viewSpace;
#endif
  projectionLayer.viewCount = (uint32_t)projectionLayerViews.size();
  projectionLayer.views = projectionLayerViews.data();
  layers.push_back(reinterpret_cast<XrCompositionLayerBaseHeader*>(&projectionLayer));

  // Add front UI layers
  for (const OpenXRLayerPtr& layer: m.uiLayers) {
    if (layer->GetDrawInFront() && layer->IsDrawRequested()) {
      layer->Update(m.layersSpace, predictedPose, XR_NULL_HANDLE);
      for (uint32_t i = 0; i < layer->HeaderCount(); ++i) {
        layers.push_back(layer->Header(i));
      }
      layer->ClearRequestDraw();
    }
  }

  XrFrameEndInfo frameEndInfo{XR_TYPE_FRAME_END_INFO};
  frameEndInfo.displayTime = displayTime;
  frameEndInfo.environmentBlendMode = XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
  frameEndInfo.layerCount = (uint32_t )layers.size();
  frameEndInfo.layers = layers.data();
  CHECK_XRCMD(xrEndFrame(m.session, &frameEndInfo));
}

VRLayerQuadPtr
DeviceDelegateOpenXR::CreateLayerQuad(int32_t aWidth, int32_t aHeight,
                                        VRLayerSurface::SurfaceType aSurfaceType) {
  if (!m.layersEnabled) {
    return nullptr;
  }

  VRLayerQuadPtr layer = VRLayerQuad::Create(aWidth, aHeight, aSurfaceType);
  OpenXRLayerQuadPtr xrLayer = OpenXRLayerQuad::Create(m.jniEnv, layer);
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
      xrLayer = OpenXRLayerQuad::Create(m.jniEnv, layer, m.uiLayers[i]);
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
  OpenXRLayerCylinderPtr xrLayer = OpenXRLayerCylinder::Create(m.jniEnv, layer);
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
      xrLayer = OpenXRLayerCylinder::Create(m.jniEnv, layer, m.uiLayers[i]);
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
    m.cubeLayer->Init(m.jniEnv, m.session, context);
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
    m.equirectLayer->Init(m.jniEnv, m.session, context);
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
      m.uiLayers[i]->Destroy();
      m.uiLayers.erase(m.uiLayers.begin() + i);
      return;
    }
  }
}

void
DeviceDelegateOpenXR::EnterVR(const crow::BrowserEGLContext& aEGLContext) {
  // Reset reorientation after Enter VR
  m.reorientMatrix = vrb::Matrix::Identity();

  if (m.session != XR_NULL_HANDLE && m.graphicsBinding.context == aEGLContext.Context()) {
    ProcessEvents();
    // Session already created and valid.
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

  m.input->Initialize(m.session);
  m.UpdateSpaces();
  m.InitializeViews();
  m.InitializeImmersiveDisplay();
  ProcessEvents();

  // Initialize layers if needed
  vrb::RenderContextPtr context = m.context.lock();
  for (OpenXRLayerPtr& layer: m.uiLayers) {
    layer->Init(m.jniEnv, m.session, context);
  }
  if (m.cubeLayer) {
    m.cubeLayer->Init(m.jniEnv, m.session, context);
  }
  if (m.equirectLayer) {
    m.equirectLayer->Init(m.jniEnv, m.session, context);
  }
}


void
DeviceDelegateOpenXR::LeaveVR() {
  CHECK_MSG(!m.boundSwapChain, "Eye swapChain not released before LeaveVR");
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

DeviceDelegateOpenXR::DeviceDelegateOpenXR(State &aState) : m(aState) {}

DeviceDelegateOpenXR::~DeviceDelegateOpenXR() { m.Shutdown(); }

} // namespace crow
