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
#include "vrb/RenderContext.h"
#include "vrb/Vector.h"
#include "vrb/Quaternion.h"

#include <vector>
#include <cstdlib>
#include <unistd.h>

#include "svrApi.h"

namespace crow {

const int32_t kHeadControllerId = 0;
const int32_t kControllerId = 1;
const int32_t kSwapChainLength = 2;
const float kIPD = 0.064f;

class SVREyeSwapChain;
typedef std::shared_ptr<SVREyeSwapChain> SVREyeSwapChainPtr;

struct SVREyeSwapChain {
  int swapChainLength = 0;
  std::vector<GLuint> textures;
  std::vector<vrb::FBOPtr> fbos;

  static SVREyeSwapChainPtr create() {
    return std::make_shared<SVREyeSwapChain>();
  }

  void Init(vrb::RenderContextPtr& aContext, device::RenderMode aRenderMode, uint32_t aSwapChainLength, uint32_t aWidth, uint32_t aHeight) {
    Destroy();
    swapChainLength = aSwapChainLength;

    for (int i = 0; i < swapChainLength; ++i) {
      vrb::FBOPtr fbo = vrb::FBO::Create(aContext);
      GLuint textureId;
      VRB_GL_CHECK(glGenTextures(1, &textureId));
      VRB_GL_CHECK(glBindTexture(GL_TEXTURE_2D, textureId));
      VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE));
      VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE));
      VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR));
      VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR));
      VRB_GL_CHECK(glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, aWidth, aHeight, 0,
                             GL_RGBA, GL_UNSIGNED_BYTE, nullptr));

      vrb::FBO::Attributes attributes;
      if (aRenderMode == device::RenderMode::Immersive) {
        attributes.samples = 0;
        attributes.depth = false;
      } else {
        attributes.samples = 2;
        attributes.depth = true;
      }
      VRB_GL_CHECK(fbo->SetTextureHandle(textureId, aWidth, aHeight, attributes));
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
      VRB_GL_CHECK(glDeleteTextures(1, &textureId));
    }
    textures.clear();
    swapChainLength = 0;
  }
};

struct DeviceDelegateSVR::State {
  vrb::RenderContextWeak context;
  android_app* app = nullptr;
  bool initialized = false;
  svrInitParams java = {};
  bool isInVRMode = false;
  SVREyeSwapChainPtr eyeSwapChains[kNumEyes];
  device::RenderMode renderMode = device::RenderMode::StandAlone;
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
  svrControllerState headControllerState = {};
  vrb::Matrix controllerTransform = vrb::Matrix::Identity();
  crow::ElbowModelPtr elbow;
  bool usingHeadTrackingInput = false;
  float scrollDelta = 0.0f;
  bool headControllerCreated = false;
  bool controllerCreated = false;
  ControllerDelegatePtr controller;
  ImmersiveDisplayPtr immersiveDisplay;
  vrb::Matrix reorientMatrix = vrb::Matrix::Identity();

  void UpdatePerspective(const svrDeviceInfo& aInfo) {
    const float fovx = aInfo.targetFovXRad * 0.5f;
    const float fovy = aInfo.targetFovYRad * 0.5f;
    const vrb::Matrix perspective = vrb::Matrix::PerspectiveMatrix(fovx, fovx, fovy, fovy, near, far);

    for (int i = 0; i < kNumEyes; ++i) {
      cameras[i]->SetPerspective(perspective);
    }

    if (immersiveDisplay) {
      const float fovxDegrees = fovx * 180.0f / (float) M_PI;
      const float fovyDegrees = fovy * 180.0f / (float) M_PI;
      immersiveDisplay->SetFieldOfView(device::Eye::Left, fovxDegrees, fovxDegrees, fovyDegrees, fovyDegrees);
      immersiveDisplay->SetFieldOfView(device::Eye::Right, fovxDegrees, fovxDegrees, fovyDegrees, fovyDegrees);
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
    vrb::RenderContextPtr localContext = context.lock();

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
      cameras[i] = vrb::CameraEye::Create(localContext->GetRenderThreadCreationContext());
      eyeSwapChains[i] = SVREyeSwapChain::create();
    }

    cameras[kLeftEye]->SetEyeTransform(vrb::Matrix::Translation(vrb::Vector(-kIPD * 0.5f, 0.f, 0.f)));
    cameras[kRightEye]->SetEyeTransform(vrb::Matrix::Translation(vrb::Vector(kIPD * 0.5f, 0.f, 0.f)));

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

  void SetButtonState(const int32_t aController) {
    bool pressed = false;

    svrControllerState& state = aController == kHeadControllerId ? headControllerState : controllerState;

    controller->SetButtonCount(aController, 3);
    const bool triggerPressed = (bool)(state.buttonState & svrControllerButton::PrimaryIndexTrigger);
    const bool touchpadPressed = (bool)(state.buttonState & svrControllerButton::PrimaryThumbstick);
    const bool touchpadTouched = (bool) state.isTouching;
    const bool menuPressed = (bool)(state.buttonState & svrControllerButton::Start);
    controller->SetButtonState(aController, ControllerDelegate::BUTTON_TRIGGER, 0, triggerPressed, triggerPressed, controllerState.analog1D[0]);
    controller->SetButtonState(aController, ControllerDelegate::BUTTON_TOUCHPAD, 1, touchpadPressed, touchpadTouched);
    controller->SetButtonState(aController, ControllerDelegate::BUTTON_APP, 2, menuPressed, menuPressed);
  }


  void FallbackToHeadTrackingInput(const vrb::Matrix & head) {
    if (!headControllerCreated || !usingHeadTrackingInput) {
      if (!headControllerCreated) {
        controller->CreateController(kHeadControllerId, -1, "");
        headControllerCreated = true;
      }
      controller->SetEnabled(kHeadControllerId, true);
      if (controllerCreated) {
        controller->SetEnabled(kControllerId, false);
      }
      usingHeadTrackingInput = true;
    }
    controller->SetTransform(kHeadControllerId, head);
    SetButtonState(kHeadControllerId);
    if (fabsf(scrollDelta) != 0.0f) {
      controller->SetScrolledDelta(kHeadControllerId, 0.0f, scrollDelta);
      scrollDelta = 0.0f;
    }
    if (headControllerState.isTouching) {
      controller->SetTouchPosition(kHeadControllerId, headControllerState.analog2D[0].x, headControllerState.analog2D[0].y);
      headControllerState.isTouching = 0;
    } else {
      controller->EndTouch(kHeadControllerId);
    }
  }

  void UpdateControllers(const vrb::Matrix & head) {
    if (!controller) {
      return;
    }
    if (controllerHandle < 0) {
      FallbackToHeadTrackingInput(head);
      return;
    }

    controllerState = svrControllerGetState(controllerHandle);
    if (controllerState.connectionState != svrControllerConnectionState::kConnected) {
      FallbackToHeadTrackingInput(head);
      return;
    }

    if (usingHeadTrackingInput || !controllerCreated) {
      if (!controllerCreated) {
        controller->CreateController(kControllerId, 0, "ODG Controller");
        controllerCreated = true;
      }
      if (usingHeadTrackingInput) {
        controller->SetEnabled(kHeadControllerId, false);
      }
      controller->SetEnabled(kControllerId, true);
      controller->SetVisible(kControllerId, true);
      controller->SetCapabilityFlags(kControllerId, device::Orientation);
      usingHeadTrackingInput = false;
    }
    const svrQuaternion& rotation = controllerState.rotation;
    vrb::Quaternion quat(-rotation.x, -rotation.y, rotation.z, rotation.w);
    controllerTransform = vrb::Matrix::Rotation(quat);
    controllerTransform = elbow->GetTransform(ElbowModel::HandEnum::Right, head, controllerTransform);
    controller->SetTransform(kControllerId, controllerTransform);
    SetButtonState(kControllerId);

    if (controllerState.isTouching) {
      controller->SetTouchPosition(kControllerId, controllerState.analog2D[0].x, controllerState.analog2D[0].y);
    } else {
      controller->EndTouch(kControllerId);
    }

    const int kNumAxes = 3;
    float immersiveAxes[kNumAxes] = { controllerState.analog2D[0].x, controllerState.analog2D[0].y, controllerState.analog1D[0]};
    controller->SetAxes(kControllerId, immersiveAxes, kNumAxes);
  }
};

DeviceDelegateSVRPtr
DeviceDelegateSVR::Create(vrb::RenderContextPtr& aContext, android_app *aApp) {
  DeviceDelegateSVRPtr result = std::make_shared<vrb::ConcreteClass<DeviceDelegateSVR, DeviceDelegateSVR::State> >();
  result->m.context = aContext;
  result->m.app = aApp;
  result->m.Initialize();
  return result;
}


void
DeviceDelegateSVR::SetRenderMode(const device::RenderMode aMode) {
  if (aMode == m.renderMode) {
    return;
  }
  m.renderMode = aMode;
  vrb::RenderContextPtr render = m.context.lock();
  for (int i = 0; i < kSwapChainLength; ++i) {
    m.eyeSwapChains[i]->Init(render, m.renderMode, kSwapChainLength, m.renderWidth, m.renderHeight);
  }
}

device::RenderMode
DeviceDelegateSVR::GetRenderMode() {
  return m.renderMode;
}

void
DeviceDelegateSVR::RegisterImmersiveDisplay(ImmersiveDisplayPtr aDisplay) {
  m.immersiveDisplay = std::move(aDisplay);

  if (!m.immersiveDisplay) {
    return;
  }

  m.immersiveDisplay->SetDeviceName("ODG");
  m.immersiveDisplay->SetCapabilityFlags(device::Position | device::Orientation | device::Present |
                                         device::InlineSession | device::ImmersiveVRSession);
  m.immersiveDisplay->SetEyeResolution(m.renderWidth, m.renderHeight);
  m.immersiveDisplay->CompleteEnumeration();

  m.immersiveDisplay->SetEyeOffset(device::Eye::Left, -kIPD * 0.5f, 0.f, 0.f);
  m.immersiveDisplay->SetEyeOffset(device::Eye::Right, kIPD * 0.5f, 0.f, 0.f);

  svrDeviceInfo info = svrGetDeviceInfo();
  m.UpdatePerspective(info);
}

vrb::CameraPtr
DeviceDelegateSVR::GetCamera(const device::Eye aWhich) {
  const int32_t index = device::EyeIndex(aWhich);
  if (index < 0) { return nullptr; }
  return m.cameras[index];
}

const vrb::Matrix&
DeviceDelegateSVR::GetHeadTransform() const {
  return m.cameras[0]->GetHeadTransform();
}

const vrb::Matrix&
DeviceDelegateSVR::GetReorientTransform() const {
  return m.reorientMatrix;
}

void
DeviceDelegateSVR::SetReorientTransform(const vrb::Matrix& aMatrix) {
  m.reorientMatrix = aMatrix;
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

void
DeviceDelegateSVR::SetControllerDelegate(ControllerDelegatePtr& aController) {
  m.controller = aController;
}

void
DeviceDelegateSVR::ReleaseControllerDelegate() {
  m.controller = nullptr;
}

int32_t
DeviceDelegateSVR::GetControllerModelCount() const {
  return 1;
}

const std::string
DeviceDelegateSVR::GetControllerModelName(const int32_t aModelIndex) const {
  // FIXME: Need SVR based controller
  static const std::string name("vr_controller_daydream.obj");
  return aModelIndex == 0 ? name : "";
}

void
DeviceDelegateSVR::ProcessEvents() {

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
  if (m.renderMode == device::RenderMode::StandAlone) {
    head.TranslateInPlace(kAverageHeight);
  }

  m.cameras[kLeftEye]->SetHeadTransform(head);
  m.cameras[kRightEye]->SetHeadTransform(head);

  m.UpdateControllers(head);
  VRB_GL_CHECK(glClearColor(m.clearColor.Red(), m.clearColor.Green(), m.clearColor.Blue(), m.clearColor.Alpha()));
}

void
DeviceDelegateSVR::BindEye(const device::Eye aWhich) {
  if (!m.isInVRMode) {
    VRB_LOG("BindEye called while not in VR mode");
    return;
  }

  int32_t index = device::EyeIndex(aWhich);
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
    VRB_GL_CHECK(glViewport(0, 0, m.renderWidth, m.renderHeight));
    VRB_GL_CHECK(glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT));
  } else {
    VRB_LOG("No Swap chain FBO found");
  }
}

void
DeviceDelegateSVR::EndFrame(const bool aDiscard) {
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

  if (aDiscard) {
    return;
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

  vrb::RenderContextPtr render = m.context.lock();
  for (int i = 0; i < kNumEyes; ++i) {
    m.eyeSwapChains[i]->Init(render, m.renderMode, kSwapChainLength, m.renderWidth, m.renderHeight);
  }

  svrBeginParams params = {};
  params.mainThreadId = gettid();
  params.cpuPerfLevel = svrPerfLevel::kPerfMaximum;
  params.gpuPerfLevel = svrPerfLevel::kPerfMaximum;
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
  int32_t buttonMask = aWhichButton == ControllerDelegate::BUTTON_APP ? svrControllerButton::Start :
                                                                         svrControllerButton::PrimaryIndexTrigger;
  if (pressed) {
    m.headControllerState.buttonState |= buttonMask;
  } else {
    m.headControllerState.buttonState &= ~buttonMask;
  }
}

void
DeviceDelegateSVR::UpdateTrackpad(float x, float y) {
  m.headControllerState.analog2D[0].x = x;
  m.headControllerState.analog2D[0].y = y;
  m.headControllerState.isTouching = 1;
}

void
DeviceDelegateSVR::WheelScroll(float speed) {
  m.scrollDelta += speed;
}

DeviceDelegateSVR::DeviceDelegateSVR(State &aState) : m(aState) {}

DeviceDelegateSVR::~DeviceDelegateSVR() { m.Shutdown(); }

} // namespace crow
