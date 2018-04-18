/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "DeviceDelegateSVR.h"
#include "ElbowModel.h"
#include "BrowserEGLContext.h"

#include <android_native_app_glue.h>
#include <EGL/egl.h>
#include "vrb/CameraEye.h"
#include "vrb/Color.h"
#include "vrb/ConcreteClass.h"
#include "vrb/FBO.h"
#include "vrb/GLError.h"
#include "vrb/Matrix.h"
#include "vrb/Vector.h"
#include "vrb/Quaternion.h"

#include <vector>
#include <cstdlib>
#include <unistd.h>

#include "svrApi.h"

namespace crow {

const uint64_t SVR_BUTTONS[] = { svrControllerButton::PrimaryIndexTrigger, svrControllerButton::PrimaryThumbstick };

class SVREyeSwapChain;
typedef std::shared_ptr<SVREyeSwapChain> SVREyeSwapChainPtr;

struct SVREyeSwapChain {
  int swapChainLength = 0;
  std::vector<GLuint> textures;
  std::vector<vrb::FBOPtr> fbos;

  static SVREyeSwapChainPtr create() {
    return std::make_shared<SVREyeSwapChain>();
  }

  void Init(vrb::ContextWeak &aContext, uint32_t aSwapChainLength, uint32_t aWidth, uint32_t aHeight) {
    Destroy();
    swapChainLength = aSwapChainLength;

    for (int i = 0; i < swapChainLength; ++i) {
      vrb::FBOPtr fbo = vrb::FBO::Create(aContext);
      GLuint textureId;
      VRB_CHECK(glGenTextures(1, &textureId));
      VRB_CHECK(glBindTexture(GL_TEXTURE_2D, textureId));
      VRB_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE));
      VRB_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE));
      VRB_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR));
      VRB_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR));
      VRB_CHECK(glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, aWidth, aHeight, 0,
                             GL_RGBA, GL_UNSIGNED_BYTE, nullptr));

      vrb::FBO::Attributes attributes;
      attributes.samples = 2;
      VRB_CHECK(fbo->SetTextureHandle(textureId, aWidth, aHeight, attributes));
      if (fbo->IsValid()) {
        textures.push_back(textureId);
        fbos.push_back(fbo);
      } else {
        VRB_LOG("FAILED to make valid FBO");
      }
    }
  }

  void Destroy() {
    fbos.clear();
    for(GLuint textureId: textures) {
      VRB_CHECK(glDeleteTextures(1, &textureId));
    }
    textures.clear();
    swapChainLength = 0;
  }
};

struct DeviceDelegateSVR::State {
  vrb::ContextWeak context;
  android_app* app = nullptr;
  bool initialized = false;
  svrInitParams java = {};
  bool isInVRMode = false;
  SVREyeSwapChainPtr eyeSwapChains[kNumEyes];
  vrb::FBOPtr currentFBO;
  int32_t currentEye = -1;
  vrb::CameraEyePtr cameras[2];
  uint32_t frameIndex = 0;
  svrHeadPoseState predictedPose = {};
  svrLayoutCoords layoutCoords = {};
  uint32_t renderWidth = 0;
  uint32_t renderHeight = 0;
  vrb::Color clearColor;
  float near = 0.1f;
  float far = 100.f;
  int32_t controllerHandle = -1;
  svrControllerState controllerState = {};
  vrb::Matrix controllerTransform = vrb::Matrix::Identity();
  crow::ElbowModelPtr elbow;
  bool usingHeadTrackingInput = false;
  bool scrollUpdated = false;
  float scrollDelta = 0;

  int32_t cameraIndex(CameraEnum aWhich) {
    if (CameraEnum::Left == aWhich) { return 0; }
    else if (CameraEnum::Right == aWhich) { return 1; }
    return -1;
  }

  void UpdatePerspective(const svrDeviceInfo& aInfo) {
    const float fovx = aInfo.targetFovXRad * 0.5f;
    const float fovy = aInfo.targetFovYRad * 0.5f;
    const vrb::Matrix perspective = vrb::Matrix::PerspectiveMatrix(fovx, fovx, fovy, fovy, near, far);

    for (int i = 0; i < kNumEyes; ++i) {
      cameras[i]->SetPerspective(perspective);
    }
  }

  void UpdateLayoutCoords(float x, float y, float width, float height) {
    // 0 = X-Position; 1 = Y-Position; 2 = Z-Position; 3 = Padding
    float lowerLeftPos[4] = { -1.0f, -1.0f, 0.0f, 1.0f };
    float lowerRightPos[4] = { 1.0f, -1.0f, 0.0f, 1.0f };
    float upperLeftPos[4] = { -1.0f, 1.0f, 0.0f, 1.0f };
    float upperRightPos[4] = { 1.0f, 1.0f, 0.0f, 1.0f };

    // [0,1] = Lower Left UV values; [2,3] = Lower Right UV values
    float lowerUVs[4] = { x, y, x + width, y };
    // [0,1] = Upper Left UV values; [2,3] = Upper Right UV values
    float upperUVs[4] = { x, y + height, x + width, y + height };

    float transform[16] = {1.0f, 0.0f, 0.0f, 0.0f,
                           0.0f, 1.0f, 0.0f, 0.0f,
                           0.0f, 0.0f, 1.0f, 0.0f,
                           0.0f, 0.0f, 0.0f, 1.0f };

    memcpy(layoutCoords.LowerLeftPos, lowerLeftPos, sizeof(lowerLeftPos));
    memcpy(layoutCoords.LowerRightPos, lowerRightPos, sizeof(lowerRightPos));
    memcpy(layoutCoords.UpperLeftPos, upperLeftPos, sizeof(upperLeftPos));
    memcpy(layoutCoords.UpperRightPos, upperRightPos, sizeof(upperRightPos));
    memcpy(layoutCoords.LowerUVs, lowerUVs, sizeof(lowerUVs));
    memcpy(layoutCoords.UpperUVs, upperUVs, sizeof(upperUVs));
    memcpy(layoutCoords.TransformMatrix, transform, sizeof(transform));
  }

  void Initialize() {
    vrb::ContextPtr localContext = context.lock();

    java.javaVm = app->activity->vm;
    (*app->activity->vm).AttachCurrentThread(&java.javaEnv, NULL);
    java.javaActivityObject = java.javaEnv->NewGlobalRef(app->activity->clazz);

    // Initialize the API.
    SvrResult status = svrInitialize(&java);
    if (status != SVR_ERROR_NONE) {
      VRB_LOG("Failed to initialize SVR Api!. Error: %d", status);
      exit(status);
    }
    initialized = true;

    svrDeviceInfo info = svrGetDeviceInfo();

    renderWidth = (uint32_t) info.targetEyeWidthPixels;
    renderHeight = (uint32_t) info.targetEyeHeightPixels;
    near = info.leftEyeFrustum.near;
    far = info.leftEyeFrustum.far;

    for (int i = 0; i < kNumEyes; ++i) {
      cameras[i] = vrb::CameraEye::Create(context);
      eyeSwapChains[i] = SVREyeSwapChain::create();
    }

    const float ipd = 0.064f;
    cameras[kLeftEye]->SetEyeTransform(vrb::Matrix::Translation(vrb::Vector(-ipd * 0.5f, 0.f, 0.f)));
    cameras[kRightEye]->SetEyeTransform(vrb::Matrix::Translation(vrb::Vector(ipd * 0.5f, 0.f, 0.f)));

    UpdatePerspective(info);
    UpdateLayoutCoords(0.f, 0.f, 1.f, 1.f);
    elbow = crow::ElbowModel::Create();
  }

  void Shutdown() {
    // Shutdown SVR mobile SDK
    if (initialized) {
      svrShutdown();
      initialized = false;
    }

    // Release activity reference
    if (java.javaActivityObject) {
      java.javaEnv->DeleteGlobalRef(java.javaActivityObject);
      java = {};
    }
  }

  void FallbackToHeadTrackingInput(const vrb::Matrix & head) {
    controllerTransform = head;
    usingHeadTrackingInput = true;
    controllerState.analog2D[0].x = 0.0;
    controllerState.analog2D[0].y = 0.0;
  }

  void UpdateControllers(const vrb::Matrix & head) {
    if (controllerHandle < 0) {
      FallbackToHeadTrackingInput(head);
      return;
    }

    controllerState = svrControllerGetState(controllerHandle);
    if (controllerState.connectionState != svrControllerConnectionState::kConnected) {
      FallbackToHeadTrackingInput(head);
      return;
    }

    const svrQuaternion& rotation = controllerState.rotation;
    vrb::Quaternion quat(-rotation.x, -rotation.y, rotation.z, rotation.w);
    controllerTransform = vrb::Matrix::Rotation(quat);
    controllerTransform = elbow->GetTransform(ElbowModel::HandEnum::Right, head, controllerTransform);
    usingHeadTrackingInput = false;
  }
};

DeviceDelegateSVRPtr
DeviceDelegateSVR::Create(vrb::ContextWeak aContext, android_app *aApp) {
  DeviceDelegateSVRPtr result = std::make_shared<vrb::ConcreteClass<DeviceDelegateSVR, DeviceDelegateSVR::State> >();
  result->m.context = aContext;
  result->m.app = aApp;
  result->m.Initialize();
  return result;
}

vrb::CameraPtr
DeviceDelegateSVR::GetCamera(const CameraEnum aWhich) {
  const int32_t index = m.cameraIndex(aWhich);
  if (index < 0) { return nullptr; }
  return m.cameras[index];
}

const vrb::Matrix&
DeviceDelegateSVR::GetHeadTransform() const {
  return m.cameras[0]->GetHeadTransform();
}

void
DeviceDelegateSVR::SetClearColor(const vrb::Color& aColor) {
  m.clearColor = aColor;
}

void
DeviceDelegateSVR::SetClipPlanes(const float aNear, const float aFar) {
  m.near = aNear;
  m.far = aFar;
  svrDeviceInfo info = svrGetDeviceInfo();
  m.UpdatePerspective(info);
}

int32_t
DeviceDelegateSVR::GetControllerCount() const {
  return 1;
}

const std::string
DeviceDelegateSVR::GetControllerModelName(const int32_t) const {
  // FIXME: Need SVR based controller
  static const std::string name("vr_controller_daydream.obj");
  return name;
}

void
DeviceDelegateSVR::ProcessEvents() {

}

const vrb::Matrix &
DeviceDelegateSVR::GetControllerTransform(const int32_t aWhichController) {
  return m.controllerTransform;
}

bool
DeviceDelegateSVR::GetControllerButtonState(const int32_t aWhichController, const int32_t aWhichButton, bool& aChangedState) {
  if (aWhichButton > sizeof(SVR_BUTTONS)/ sizeof(SVR_BUTTONS[0])) {
    return false;
  }

  bool result = (m.controllerState.buttonState & SVR_BUTTONS[aWhichButton]) != 0;
  m.controllerState.buttonState = 0;
  return result;
}

bool
DeviceDelegateSVR::GetControllerScrolled(const int32_t aWhichController, float& aScrollX, float& aScrollY) {
  aScrollX = m.controllerState.analog2D[0].x;
  aScrollY = m.controllerState.analog2D[0].y;
  if (m.usingHeadTrackingInput) {
    bool result = m.scrollUpdated;
    m.scrollUpdated = false;
    return result;
  } else {
    return m.controllerState.isTouching;
  }
}

bool
DeviceDelegateSVR::GetScrolledDelta(const int32_t aWhichController, float& aScrollX, float& aScrollY) {
  if (fabs(m.scrollDelta) != 0.0) {
    aScrollX = 0.0f;
    aScrollY = m.scrollDelta;
    m.scrollDelta = 0.0f;
    return true;
  }
  return false;
}

bool
DeviceDelegateSVR::IsControllerUsingHeadTracking(const int32_t aWhichController) const {
  return m.usingHeadTrackingInput;
}

void
DeviceDelegateSVR::StartFrame() {
  if (!m.isInVRMode) {
    VRB_LOG("StartFrame called while not in VR mode");
    return;
  }

  m.frameIndex++;
  float predictedTime = svrGetPredictedDisplayTime();
  m.predictedPose = svrGetPredictedHeadPose(predictedTime);

  vrb::Matrix head = vrb::Matrix::Identity();
  if (m.predictedPose.poseStatus & kTrackingRotation) {
    svrQuaternion& orientation = m.predictedPose.pose.rotation;
    vrb::Quaternion quat(orientation.x, orientation.y, orientation.z, orientation.w);
    head = vrb::Matrix::Rotation(quat.Inverse());
  }

  if (m.predictedPose.poseStatus & kTrackingPosition) {
    svrVector3& position = m.predictedPose.pose.position;
    vrb::Vector translation(-position.x, -position.y, -position.z);
    head.TranslateInPlace(translation);
  }

  static const vrb::Vector kAverageHeight(0.0f, 1.7f, 0.0f);
  head.TranslateInPlace(kAverageHeight);

  m.cameras[kLeftEye]->SetHeadTransform(head);
  m.cameras[kRightEye]->SetHeadTransform(head);

  m.UpdateControllers(head);
  VRB_CHECK(glClearColor(m.clearColor.Red(), m.clearColor.Green(), m.clearColor.Blue(), m.clearColor.Alpha()));
}

void
DeviceDelegateSVR::BindEye(const CameraEnum aWhich) {
  if (!m.isInVRMode) {
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

  if (m.currentEye >= 0) {
    svrEndEye((svrWhichEye) m.currentEye);
  }

  const auto &swapChain = m.eyeSwapChains[index];
  int swapChainIndex = m.frameIndex % swapChain->swapChainLength;
  m.currentFBO = swapChain->fbos[swapChainIndex];

  if (m.currentFBO) {
    m.currentFBO->Bind();
    m.currentEye = index;
    svrBeginEye((svrWhichEye) m.currentEye);
    VRB_CHECK(glViewport(0, 0, m.renderWidth, m.renderHeight));
    VRB_CHECK(glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT));
  } else {
    VRB_LOG("No Swap chain FBO found");
  }
}

void
DeviceDelegateSVR::EndFrame() {
  if (!m.isInVRMode) {
    VRB_LOG("EndFrame called while not in VR mode");
    return;
  }

  if (m.currentEye >= 0) {
    svrEndEye((svrWhichEye) m.currentEye);
    m.currentEye = -1;
  }

  if (m.currentFBO) {
    m.currentFBO->Unbind();
    m.currentFBO.reset();
  }

  svrFrameParams params = {};
  params.frameIndex = m.frameIndex;
  // Minimum number of vysnc events before displaying the frame (1=display refresh, 2=half refresh, etc...).
  params.minVsyncs = 1;
  // Options for adjusting the frame warp behavior (bitfield of svrFrameOption).
  params.frameOptions = 0;
  // Head pose state used to generate the frame.
  params.headPoseState = m.predictedPose;
  // Type of warp to be used on the frame.
  params.warpType = svrWarpType::kSimple;
  // Field of view used to generate this frame (larger than device fov to provide timewarp margin).
  // A 0 value uses the SVR fov.
  params.fieldOfView = 0.0;

  for (uint32_t eyeIndex = 0; eyeIndex < kNumEyes; eyeIndex++) {
    uint32_t swapChainIndex = m.frameIndex % m.eyeSwapChains[eyeIndex]->swapChainLength;
    params.renderLayers[eyeIndex].imageType = kTypeTexture;
    params.renderLayers[eyeIndex].imageHandle = m.eyeSwapChains[eyeIndex]->textures[swapChainIndex];
    params.renderLayers[eyeIndex].imageCoords = m.layoutCoords;
    if (eyeIndex == kLeftEye) {
      params.renderLayers[eyeIndex].eyeMask = kEyeMaskLeft;
    } else {
      params.renderLayers[eyeIndex].eyeMask = kEyeMaskRight;
    }
  }

  svrSubmitFrame(&params);
}

void
DeviceDelegateSVR::EnterVR(const crow::BrowserEGLContext& aEGLContext) {
  if (m.isInVRMode) {
    return;
  }

  for (int i = 0; i < kNumEyes; ++i) {
    m.eyeSwapChains[i]->Init(m.context, 2, m.renderWidth, m.renderHeight);
  }

  svrBeginParams params = {};
  params.mainThreadId = gettid();
  params.cpuPerfLevel = svrPerfLevel::kPerfSystem;
  params.gpuPerfLevel = svrPerfLevel::kPerfSystem;
  params.nativeWindow = m.app->window;
  params.isProtectedContent = false;

  svrSetTrackingMode(kTrackingRotation | kTrackingPosition);

  SvrResult status = svrBeginVr(&params);
  if (status != SvrResult::SVR_ERROR_NONE) {
    VRB_LOG("Entering VR mode failed with status: %d", status);
    return;
  }

  m.controllerHandle = svrControllerStartTracking("");
  m.isInVRMode = true;
}

void
DeviceDelegateSVR::LeaveVR() {
  if (m.controllerHandle >= 0) {
    svrControllerStopTracking(m.controllerHandle);
    m.controllerHandle = -1;
  }
  if (m.isInVRMode) {
    svrEndVr();
    m.isInVRMode = false;
  }

  for (int i = 0; i < kNumEyes; ++i) {
    m.eyeSwapChains[i]->Destroy();
  }
}

bool
DeviceDelegateSVR::IsInVRMode() const {
  return m.isInVRMode;
}

bool
DeviceDelegateSVR::ExitApp() {
    return false;
}

void
DeviceDelegateSVR::UpdateButtonState(int32_t aWhichButton, bool pressed) {
  pressed = true;
  if (pressed) {
    m.controllerState.buttonState |= SVR_BUTTONS[aWhichButton];
  } else {
    m.controllerState.buttonState &= ~SVR_BUTTONS[aWhichButton];
  }
}

void
DeviceDelegateSVR::UpdateTrackpad(float x, float y) {
  if (m.usingHeadTrackingInput) {
    m.controllerState.analog2D[0].x = x;
    m.controllerState.analog2D[0].y = y;
    m.scrollUpdated = true;
  }
}

void
DeviceDelegateSVR::WheelScroll(float speed) {
  m.scrollDelta += speed;
}

DeviceDelegateSVR::DeviceDelegateSVR(State &aState) : m(aState) {}

DeviceDelegateSVR::~DeviceDelegateSVR() { m.Shutdown(); }

} // namespace crow
