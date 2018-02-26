/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "DeviceDelegateOculusVR.h"
#include "ElbowModel.h"
#include "BrowserEGLContext.h"

#include <android_native_app_glue.h>
#include <EGL/egl.h>
#include "vrb/CameraEye.h"
#include "vrb/ConcreteClass.h"
#include "vrb/FBO.h"
#include "vrb/GLError.h"
#include "vrb/Matrix.h"
#include "vrb/Vector.h"
#include "vrb/Quaternion.h"

#include <vector>
#include <cstdlib>

#include "VrApi.h"
#include "VrApi_Helpers.h"
#include "VrApi_Input.h"

namespace crow {

class OculusEyeSwapChain;

typedef std::shared_ptr<OculusEyeSwapChain> OculusEyeSwapChainPtr;

struct OculusEyeSwapChain {
  ovrTextureSwapChain *ovrSwapChain = nullptr;
  int swapChainLength = 0;
  std::vector<vrb::FBOPtr> fbos;

  static OculusEyeSwapChainPtr create() {
    return std::make_shared<OculusEyeSwapChain>();
  }

  void Init(vrb::ContextWeak &aContext, uint32_t aWidth, uint32_t aHeight) {
    Destroy();
    ovrSwapChain = vrapi_CreateTextureSwapChain(VRAPI_TEXTURE_TYPE_2D,
                                                VRAPI_TEXTURE_FORMAT_8888,
                                                aWidth, aHeight, 1, true);
    swapChainLength = vrapi_GetTextureSwapChainLength(ovrSwapChain);

    for (int i = 0; i < swapChainLength; ++i) {
      vrb::FBOPtr fbo = vrb::FBO::Create(aContext);
      auto texture = vrapi_GetTextureSwapChainHandle(ovrSwapChain, i);
      VRB_CHECK(glBindTexture(GL_TEXTURE_2D, texture));
      VRB_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE));
      VRB_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE));
      VRB_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR));
      VRB_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR));

      vrb::FBO::Attributes attributes;
      attributes.samples = 2;
      VRB_CHECK(fbo->SetTextureHandle(texture, aWidth, aHeight, attributes));
      if (fbo->IsValid()) {
        fbos.push_back(fbo);
      } else {
        VRB_LOG("FAILED to make valid FBO");
      }
    }
  }

  void Destroy() {
    fbos.clear();
    if (ovrSwapChain) {
      vrapi_DestroyTextureSwapChain(ovrSwapChain);
      ovrSwapChain = nullptr;
    }
    swapChainLength = 0;
  }
};

struct DeviceDelegateOculusVR::State {
  vrb::ContextWeak context;
  android_app* app = nullptr;
  bool initialized = false;
  ovrJava java = {};
  ovrMobile* ovr = nullptr;
  OculusEyeSwapChainPtr eyeSwapChains[VRAPI_EYE_COUNT];
  vrb::FBOPtr currentFBO;
  vrb::CameraEyePtr cameras[2];
  uint32_t frameIndex = 0;
  double predictedDisplayTime = 0;
  ovrTracking2 predictedTracking = {};
  uint32_t renderWidth = 0;
  uint32_t renderHeight = 0;
  float near = 0.1f;
  float far = 100.f;
  ovrDeviceID controllerID = ovrDeviceIdType_Invalid;
  ovrInputTrackedRemoteCapabilities controllerCapabilities;
  vrb::Matrix controllerTransform = vrb::Matrix::Identity();
  ovrInputStateTrackedRemote controllerState = {};
  crow::ElbowModelPtr elbow;

  int32_t cameraIndex(CameraEnum aWhich) {
    if (CameraEnum::Left == aWhich) { return 0; }
    else if (CameraEnum::Right == aWhich) { return 1; }
    return -1;
  }

  void UpdatePerspective() {
    float fovX = vrapi_GetSystemPropertyFloat(&java, VRAPI_SYS_PROP_SUGGESTED_EYE_FOV_DEGREES_X);
    float fovY = vrapi_GetSystemPropertyFloat(&java, VRAPI_SYS_PROP_SUGGESTED_EYE_FOV_DEGREES_Y);

    ovrMatrix4f projection = ovrMatrix4f_CreateProjectionFov(fovX, fovX, 0.0, 0.0, near, far);
    auto matrix = vrb::Matrix::FromColumnMajor(projection.M);
    for (int i = 0; i < VRAPI_EYE_COUNT; ++i) {
      cameras[i]->SetPerspective(matrix);
    }
  }

  void Initialize() {
    vrb::ContextPtr localContext = context.lock();

    java.Vm = app->activity->vm;
    (*app->activity->vm).AttachCurrentThread(&java.Env, NULL);
    java.ActivityObject = java.Env->NewGlobalRef(app->activity->clazz);

    // Initialize the API.
    auto parms = vrapi_DefaultInitParms(&java);
    auto status = vrapi_Initialize(&parms);
    if (status != VRAPI_INITIALIZE_SUCCESS) {
      VRB_LOG("Failed to initialize VrApi!. Error: %d", status);
      exit(status);
      return;
    }
    initialized = true;

    renderWidth = (uint32_t) vrapi_GetSystemPropertyInt(&java,
                                                        VRAPI_SYS_PROP_SUGGESTED_EYE_TEXTURE_WIDTH);
    renderHeight = (uint32_t) vrapi_GetSystemPropertyInt(&java,
                                                         VRAPI_SYS_PROP_SUGGESTED_EYE_TEXTURE_HEIGHT);

    for (int i = 0; i < VRAPI_EYE_COUNT; ++i) {
      cameras[i] = vrb::CameraEye::Create(context);
      eyeSwapChains[i] = OculusEyeSwapChain::create();
    }
    UpdatePerspective();
  }

  void Shutdown() {
    // Shutdown Oculus mobile SDK
    if (initialized) {
      vrapi_Shutdown();
      initialized = false;
    }

    // Release activity reference
    if (java.ActivityObject) {
      java.Env->DeleteGlobalRef(java.ActivityObject);
      java = {};
    }
  }

  void UpdateControllerID() {
    if (!ovr || (controllerID != ovrDeviceIdType_Invalid)) {
      return;
    }

    int index = 0;
    while (true) {
      ovrInputCapabilityHeader caps = {};
      if (vrapi_EnumerateInputDevices(ovr, index++, &caps) < 0) {
        // No more input devices to enumerate
        break;
      }

      if (caps.Type == ovrControllerType_TrackedRemote) {
        // We are only interested in the remote controller input device
        controllerID = caps.DeviceID;
        controllerCapabilities.Header.Type = ovrControllerType_TrackedRemote;
        vrapi_GetInputDeviceCapabilities(ovr, &controllerCapabilities.Header);
        if (controllerCapabilities.ControllerCapabilities & ovrControllerCaps_LeftHand) {
          elbow = crow::ElbowModel::Create(crow::ElbowModel::HandEnum::Left);
        } else {
          elbow = crow::ElbowModel::Create(crow::ElbowModel::HandEnum::Right);
        }
        return;
      }
    }
  }

  void UpdateControllers(const vrb::Matrix & head) {
    UpdateControllerID();
    if (controllerID == ovrDeviceIdType_Invalid) {
      return;
    }

    ovrTracking tracking = {};
    if (vrapi_GetInputTrackingState(ovr, controllerID, 0, &tracking) != ovrSuccess) {
      VRB_LOG("Failed to read controller tracking state");
      return;
    }

    if (controllerCapabilities.ControllerCapabilities & ovrControllerCaps_HasOrientationTracking) {
      auto &orientation = tracking.HeadPose.Pose.Orientation;
      vrb::Quaternion quat(orientation.x, orientation.y, orientation.z, orientation.w);
      controllerTransform = vrb::Matrix::Rotation(quat);
    }

    if (controllerCapabilities.ControllerCapabilities & ovrControllerCaps_HasPositionTracking) {
      auto & position = tracking.HeadPose.Pose.Position;
      controllerTransform.TranslateInPlace(vrb::Vector(position.x, position.y, position.z));
    } else {
      controllerTransform = elbow->GetTransform(head, controllerTransform);
    }

    controllerState.Header.ControllerType = ovrControllerType_TrackedRemote;
    vrapi_GetCurrentInputState(ovr, controllerID, &controllerState.Header);
  }
};

DeviceDelegateOculusVRPtr
DeviceDelegateOculusVR::Create(vrb::ContextWeak aContext, android_app *aApp) {
  DeviceDelegateOculusVRPtr result = std::make_shared<vrb::ConcreteClass<DeviceDelegateOculusVR, DeviceDelegateOculusVR::State> >();
  result->m.context = aContext;
  result->m.app = aApp;
  result->m.Initialize();
  return result;
}

vrb::CameraPtr
DeviceDelegateOculusVR::GetCamera(const CameraEnum aWhich) {
  const int32_t index = m.cameraIndex(aWhich);
  if (index < 0) { return nullptr; }
  return m.cameras[index];
}

void
DeviceDelegateOculusVR::SetClipPlanes(const float aNear, const float aFar) {
  m.near = aNear;
  m.far = aFar;
  m.UpdatePerspective();
}

int32_t
DeviceDelegateOculusVR::GetControllerCount() const {
  return 1;
}

const std::string
DeviceDelegateOculusVR::GetControllerModelName(const int32_t) const {
  // FIXME: Need Oculus based controller
  static const std::string name("vr_controller_daydream.obj");
  return name;
}

void
DeviceDelegateOculusVR::ProcessEvents() {

}

const vrb::Matrix &
DeviceDelegateOculusVR::GetControllerTransform(const int32_t aWhichController) {
  return m.controllerTransform;
}

bool
DeviceDelegateOculusVR::GetControllerButtonState(const int32_t aWhichController, const int32_t aWhichButton, bool& aChangedState) {
  // For the Gear VR Controller, only the following ovrButton types are reported to the application:
  // ovrButton_Back, ovrButton_A, ovrButton_Enter
  static const ovrButton GEAR_VR_BUTTONS[] = { ovrButton_A, ovrButton_Back, ovrButton_Enter };
  if (aWhichButton > sizeof(GEAR_VR_BUTTONS)/ sizeof(GEAR_VR_BUTTONS[0])) {
    return false;
  }

  return (m.controllerState.Buttons & GEAR_VR_BUTTONS[aWhichButton]) != 0;
}

void
DeviceDelegateOculusVR::StartFrame() {
  if (!m.ovr) {
    VRB_LOG("StartFrame called while not in VR mode");
    return;
  }

  m.frameIndex++;
  m.predictedDisplayTime = vrapi_GetPredictedDisplayTime(m.ovr, m.frameIndex);
  m.predictedTracking = vrapi_GetPredictedTracking2(m.ovr, m.predictedDisplayTime);

  float ipd = vrapi_GetInterpupillaryDistance(&m.predictedTracking);
  m.cameras[VRAPI_EYE_LEFT]->SetEyeTransform(vrb::Matrix::Translation(vrb::Vector(-ipd, 0.f, 0.f)));
  m.cameras[VRAPI_EYE_RIGHT]->SetEyeTransform(vrb::Matrix::Translation(vrb::Vector(ipd, 0.f, 0.f)));

  if (!(m.predictedTracking.Status & VRAPI_TRACKING_STATUS_HMD_CONNECTED)) {
    VRB_LOG("HMD not connected");
    return;
  }

  vrb::Matrix head = vrb::Matrix::Identity();
  if (m.predictedTracking.Status & VRAPI_TRACKING_STATUS_ORIENTATION_TRACKED) {
    auto &orientation = m.predictedTracking.HeadPose.Pose.Orientation;
    vrb::Quaternion quat(orientation.x, orientation.y, orientation.z, orientation.w);
    head = vrb::Matrix::Rotation(quat);
  }

  if (m.predictedTracking.Status & VRAPI_TRACKING_STATUS_POSITION_TRACKED) {
    auto &position = m.predictedTracking.HeadPose.Pose.Position;
    vrb::Vector translation(position.x, position.y, position.z);
    head.TranslateInPlace(translation);
  }

  static const vrb::Vector kAverageHeight(0.0f, 1.7f, 0.0f);
  head.TranslateInPlace(kAverageHeight);

  m.cameras[VRAPI_EYE_LEFT]->SetHeadTransform(head);
  m.cameras[VRAPI_EYE_RIGHT]->SetHeadTransform(head);

  m.UpdateControllers(head);
}

void
DeviceDelegateOculusVR::BindEye(const CameraEnum aWhich) {
  if (!m.ovr) {
    VRB_LOG("BindEye called while not in VR mode");
    return;
  }

  int32_t index = m.cameraIndex(aWhich);
  if (index < 0) {
    VRB_LOG("No eye found");
    return;
  }

  if (m.currentFBO) {
    m.currentFBO->Unbind();
  }

  const auto &swapChain = m.eyeSwapChains[index];
  int swapChainIndex = m.frameIndex % swapChain->swapChainLength;
  m.currentFBO = swapChain->fbos[swapChainIndex];

  if (m.currentFBO) {
    m.currentFBO->Bind();
    VRB_CHECK(glViewport(0, 0, m.renderWidth, m.renderHeight));
    //VRB_CHECK(glClearColor(1.0, 0.0, 0.0, 1.0));
    VRB_CHECK(glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT));
  } else {
    VRB_LOG("No Swap chain FBO found");
  }
}

void
DeviceDelegateOculusVR::EndFrame() {
  if (!m.ovr) {
    VRB_LOG("EndFrame called while not in VR mode");
    return;
  }
  if (m.currentFBO) {
    m.currentFBO->Unbind();
    m.currentFBO.reset();
  }

  auto layer = vrapi_DefaultLayerProjection2();
  layer.HeadPose = m.predictedTracking.HeadPose;
  for (int i = 0; i < VRAPI_FRAME_LAYER_EYE_MAX; ++i) {
    const auto &eyeSwapChain = m.eyeSwapChains[i];
    int swapChainIndex = m.frameIndex % eyeSwapChain->swapChainLength;
    // Set up OVR layer textures
    layer.Textures[i].ColorSwapChain = eyeSwapChain->ovrSwapChain;
    layer.Textures[i].SwapChainIndex = swapChainIndex;
    layer.Textures[i].TexCoordsFromTanAngles = ovrMatrix4f_TanAngleMatrixFromProjection(
        &m.predictedTracking.Eye[i].ProjectionMatrix);
  }

  ovrSubmitFrameDescription2 frameDesc = {};
  frameDesc.Flags = 0;
  frameDesc.SwapInterval = 1;
  frameDesc.FrameIndex = m.frameIndex;
  frameDesc.DisplayTime = m.predictedDisplayTime;
  frameDesc.CompletionFence = 0;

  ovrLayerHeader2* layers[] = {&layer.Header};
  frameDesc.LayerCount = sizeof(layers) / sizeof(layers[0]);
  frameDesc.Layers = layers;

  vrapi_SubmitFrame2(m.ovr, &frameDesc);
}

void
DeviceDelegateOculusVR::EnterVR(const crow::BrowserEGLContext& aEGLContext) {
  if (m.ovr) {
    return;
  }

  for (int i = 0; i < VRAPI_EYE_COUNT; ++i) {
    m.eyeSwapChains[i]->Init(m.context, m.renderWidth, m.renderHeight);
  }

  ovrModeParms modeParms = vrapi_DefaultModeParms(&m.java);
  modeParms.Flags |= VRAPI_MODE_FLAG_NATIVE_WINDOW;
  // No need to reset the FLAG_FULLSCREEN window flag when using a View
  modeParms.Flags &= ~VRAPI_MODE_FLAG_RESET_WINDOW_FULLSCREEN;
  modeParms.Display = reinterpret_cast<unsigned long long>(aEGLContext.Display());
  modeParms.WindowSurface = reinterpret_cast<unsigned long long>(m.app->window);
  modeParms.ShareContext = reinterpret_cast<unsigned long long>(aEGLContext.Context());

  m.ovr = vrapi_EnterVrMode(&modeParms);

  if (!m.ovr) {
    VRB_LOG("Entering VR mode failed");
  }

  //vrapi_SetRemoteEmulation(m.ovr, false);
}

void
DeviceDelegateOculusVR::LeaveVR() {
  if (m.ovr) {
    vrapi_LeaveVrMode(m.ovr);
    m.ovr = nullptr;
  }

  for (int i = 0; i < VRAPI_EYE_COUNT; ++i) {
    m.eyeSwapChains[i]->Destroy();
  }
}

bool
DeviceDelegateOculusVR::IsInVRMode() const {
  return m.ovr != nullptr;
}

DeviceDelegateOculusVR::DeviceDelegateOculusVR(State &aState) : m(aState) {}

DeviceDelegateOculusVR::~DeviceDelegateOculusVR() { m.Shutdown(); }

} // namespace crow
