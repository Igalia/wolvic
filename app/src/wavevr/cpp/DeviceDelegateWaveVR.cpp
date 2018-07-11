/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "DeviceDelegateWaveVR.h"
#include "ElbowModel.h"
#include "GestureDelegate.h"

#include "vrb/CameraEye.h"
#include "vrb/Color.h"
#include "vrb/ConcreteClass.h"
#include "vrb/FBO.h"
#include "vrb/GLError.h"
#include "vrb/Matrix.h"
#include "vrb/RenderContext.h"
#include "vrb/Vector.h"

#include <array>
#include <vector>

#include <wvr/wvr.h>
#include <wvr/wvr_render.h>
#include <wvr/wvr_device.h>
#include <wvr/wvr_projection.h>
#include <wvr/wvr_overlay.h>
#include <wvr/wvr_system.h>
#include <wvr/wvr_events.h>

namespace crow {

static const int32_t kMaxControllerCount = 2;

struct Controller {
  int32_t index;
  WVR_DeviceType type;
  bool enabled;
  bool touched;
  Controller()
      : index(-1)
      , enabled(false)
      , touched(false)
  {}
};

struct DeviceDelegateWaveVR::State {
  vrb::RenderContextWeak context;
  bool isRunning;
  vrb::Color clearColor;
  float near;
  float far;
  void* leftTextureQueue;
  void* rightTextureQueue;
  device::RenderMode renderMode;
  int32_t leftFBOIndex;
  int32_t rightFBOIndex;
  vrb::FBOPtr currentFBO;
  std::vector<vrb::FBOPtr> leftFBOQueue;
  std::vector<vrb::FBOPtr> rightFBOQueue;
  vrb::CameraEyePtr cameras[2];
  uint32_t renderWidth;
  uint32_t renderHeight;
  WVR_DevicePosePair_t devicePairs[WVR_DEVICE_COUNT_LEVEL_1];
  ElbowModelPtr elbow;
  ControllerDelegatePtr delegate;
  GestureDelegatePtr gestures;
  std::array<Controller, kMaxControllerCount> controllers;
  ImmersiveDisplayPtr immersiveDisplay;
  State()
      : isRunning(true)
      , near(0.1f)
      , far(100.f)
      , renderMode(device::RenderMode::StandAlone)
      , leftFBOIndex(0)
      , rightFBOIndex(0)
      , leftTextureQueue(nullptr)
      , rightTextureQueue(nullptr)
      , renderWidth(0)
      , renderHeight(0)
  {
    memset((void*)devicePairs, 0, sizeof(WVR_DevicePosePair_t) * WVR_DEVICE_COUNT_LEVEL_1);
    gestures = GestureDelegate::Create();
    for (int32_t index = 0; index < kMaxControllerCount; index++) {
      controllers[index].index = index;
      if (index == 0) {
        controllers[index].type = WVR_DeviceType_Controller_Right;
      } else {
        controllers[index].type = WVR_DeviceType_Controller_Left;
      }
    }
  }


  void FillFBOQueue(void* aTextureQueue, std::vector<vrb::FBOPtr>& aFBOQueue) {
    vrb::FBO::Attributes attributes;
    attributes.samples = 4;
    vrb::RenderContextPtr render = context.lock();
    for (int ix = 0; ix < WVR_GetTextureQueueLength(aTextureQueue); ix++) {
      vrb::FBOPtr fbo = vrb::FBO::Create(render);
      fbo->SetTextureHandle((GLuint)WVR_GetTexture(aTextureQueue, ix).id, renderWidth, renderHeight, attributes);
      if (fbo->IsValid()) {
        aFBOQueue.push_back(fbo);
      } else {
        VRB_LOG("FAILED to make valid FBO");
      }
    }
  }

  void InitializeCameras() {
    vrb::Matrix leftProjection = vrb::Matrix::FromRowMajor(
        WVR_GetProjection(WVR_Eye_Left, near, far).m);
    cameras[device::EyeIndex(device::Eye::Left)]->SetPerspective(leftProjection);

    vrb::Matrix rightProjection = vrb::Matrix::FromRowMajor(
        WVR_GetProjection(WVR_Eye_Right, near, far).m);
    cameras[device::EyeIndex(device::Eye::Right)]->SetPerspective(rightProjection);


    vrb::Matrix leftEyeOffset = vrb::Matrix::FromRowMajor(
        WVR_GetTransformFromEyeToHead(WVR_Eye_Left, WVR_NumDoF_6DoF).m); //.Inverse();
    cameras[device::EyeIndex(device::Eye::Left)]->SetEyeTransform(leftEyeOffset);

    vrb::Matrix rightEyeOffset = vrb::Matrix::FromRowMajor(
        WVR_GetTransformFromEyeToHead(WVR_Eye_Right, WVR_NumDoF_6DoF).m); //.Inverse();
    cameras[device::EyeIndex(device::Eye::Right)]->SetEyeTransform(rightEyeOffset);

    if (!immersiveDisplay) {
      return;
    }

    const float toDegrees = 180.0f / (float)M_PI;

    for (WVR_Eye eye : {WVR_Eye_Left, WVR_Eye_Right}) {
      const WVR_Matrix4f_t eyeHead = WVR_GetTransformFromEyeToHead(eye, WVR_NumDoF_6DoF);
      const device::Eye deviceEye = eye == WVR_Eye_Left ? device::Eye::Left : device::Eye::Right;
      immersiveDisplay->SetEyeOffset(deviceEye, eyeHead.m[0][3], eyeHead.m[1][3], eyeHead.m[2][3]);

      float left, right, top, bottom;
      WVR_GetClippingPlaneBoundary(eye, &left, &right, &top, &bottom);
      const float fovLeft = atan(left / near) * toDegrees * 0.5f;
      const float fovRight = atan(right / near) * toDegrees * 0.5f;
      const float fovTop = atan(top / near) * toDegrees * 0.5f;
      const float fovBottom = atan(bottom / near) * toDegrees * 0.5f;
      immersiveDisplay->SetFieldOfView(deviceEye, fovLeft, fovRight, fovTop, fovBottom);
    }
  }

  void Initialize() {
    vrb::RenderContextPtr localContext = context.lock();
    if (!localContext) {
      return;
    }
    vrb::CreationContextPtr create = localContext->GetRenderThreadCreationContext();
    cameras[device::EyeIndex(device::Eye::Left)] = vrb::CameraEye::Create(create);
    cameras[device::EyeIndex(device::Eye::Right)] = vrb::CameraEye::Create(create);
    InitializeCameras();
    WVR_GetRenderTargetSize(&renderWidth, &renderHeight);
    VRB_GL_CHECK(glViewport(0, 0, renderWidth, renderHeight));
    VRB_LOG("Recommended size is %ux%u", renderWidth, renderHeight);
    if (renderWidth == 0 || renderHeight == 0) {
      VRB_LOG("Please check Wave server configuration");
      return;
    }
    leftTextureQueue = WVR_ObtainTextureQueue(WVR_TextureTarget_2D, WVR_TextureFormat_RGBA, WVR_TextureType_UnsignedByte, renderWidth, renderHeight, 0);
    FillFBOQueue(leftTextureQueue, leftFBOQueue);
    rightTextureQueue = WVR_ObtainTextureQueue(WVR_TextureTarget_2D, WVR_TextureFormat_RGBA, WVR_TextureType_UnsignedByte, renderWidth, renderHeight, 0);
    FillFBOQueue(rightTextureQueue, rightFBOQueue);
    elbow = ElbowModel::Create();
  }

  void Shutdown() {

  }

  void UpdateControllers() {
    if (!delegate) {
      return;
    }

    for (int index = 0; index < kMaxControllerCount; index++) {
      Controller& controller = controllers[index];
      if(!WVR_IsDeviceConnected(controller.type)) {
        if (controller.enabled) {
          delegate->SetEnabled(index, false);
          controller.enabled = false;
        }
        continue;
      } else if (!controller.enabled) {
        controller.enabled = true;
        delegate->SetEnabled(index, true);
        delegate->SetVisible(index, true);
      }

      delegate->SetButtonState(index, ControllerDelegate::BUTTON_TRIGGER,
                               WVR_GetInputButtonState(controller.type, WVR_InputId_Alias1_Bumper));
      delegate->SetButtonState(index, ControllerDelegate::BUTTON_TOUCHPAD,
                               WVR_GetInputButtonState(controller.type, WVR_InputId_Alias1_Touchpad));
      delegate->SetButtonState(index, ControllerDelegate::BUTTON_MENU,
                               WVR_GetInputButtonState(controller.type, WVR_InputId_Alias1_Menu));

      if (WVR_GetInputTouchState(controller.type, WVR_InputId_Alias1_Touchpad)) {
        WVR_Axis_t axis = WVR_GetInputAnalogAxis(controller.type, WVR_InputId_Alias1_Touchpad);
        delegate->SetTouchPosition(index, axis.x, -axis.y);
        controllers[index].touched = true;
      } else if (controllers[index].touched) {
        controllers[index].touched = false;
        delegate->EndTouch(index);
      }
    }
  }
};

DeviceDelegateWaveVRPtr
DeviceDelegateWaveVR::Create(vrb::RenderContextPtr& aContext) {
  DeviceDelegateWaveVRPtr result = std::make_shared<vrb::ConcreteClass<DeviceDelegateWaveVR, DeviceDelegateWaveVR::State> >();
  result->m.context = aContext;
  result->m.Initialize();
  return result;
}

void
DeviceDelegateWaveVR::SetRenderMode(const device::RenderMode aMode) {
  if (aMode == m.renderMode) {
    return;
  }
  m.renderMode = aMode;
}

device::RenderMode
DeviceDelegateWaveVR::GetRenderMode() {
  return m.renderMode;
}

void
DeviceDelegateWaveVR::RegisterImmersiveDisplay(ImmersiveDisplayPtr aDisplay) {
  m.immersiveDisplay = std::move(aDisplay);

  if (!m.immersiveDisplay) {
    return;
  }

  m.immersiveDisplay->SetDeviceName("Wave");
  m.immersiveDisplay->SetCapabilityFlags(device::Position | device::Orientation | device::Present);
  m.immersiveDisplay->SetEyeResolution(m.renderWidth, m.renderHeight);
  m.immersiveDisplay->CompleteEnumeration();
  m.InitializeCameras();
}


GestureDelegateConstPtr
DeviceDelegateWaveVR::GetGestureDelegate() {
  return m.gestures;
}
vrb::CameraPtr
DeviceDelegateWaveVR::GetCamera(const device::Eye aWhich) {
  const int32_t index = device::EyeIndex(aWhich);
  if (index < 0) { return nullptr; }
  return m.cameras[index];
}

const vrb::Matrix&
DeviceDelegateWaveVR::GetHeadTransform() const {
  return m.cameras[0]->GetHeadTransform();
}

void
DeviceDelegateWaveVR::SetClearColor(const vrb::Color& aColor) {
  m.clearColor = aColor;
}
void
DeviceDelegateWaveVR::SetClipPlanes(const float aNear, const float aFar) {
  m.near = aNear;
  m.far = aFar;
  m.InitializeCameras();
}

void
DeviceDelegateWaveVR::SetControllerDelegate(ControllerDelegatePtr& aController) {
  m.delegate = aController;
  if (!m.delegate) {
    return;
  }
  for (int32_t index = 0; index < kMaxControllerCount; index++) {
    m.delegate->CreateController(index, 0);
  }
}

void
DeviceDelegateWaveVR::ReleaseControllerDelegate() {
  m.delegate = nullptr;
}

int32_t
DeviceDelegateWaveVR::GetControllerModelCount() const {
  return 1;
}

const std::string
DeviceDelegateWaveVR::GetControllerModelName(const int32_t aModelIndex) const {
  // FIXME: Need Focus based controller
  static const std::string name("vr_controller_focus.obj");
  return aModelIndex == 0 ? name : "";
}

void
DeviceDelegateWaveVR::ProcessEvents() {
  WVR_Event_t event;
  m.gestures->Reset();
  while(WVR_PollEventQueue(&event)) {
    WVR_EventType type = event.common.type;
    switch (type) {
      case WVR_EventType_Quit:
        {
          VRB_LOG("WVR_EventType_Quit");
          m.isRunning = false;
        }
        break;
      case WVR_EventType_DeviceConnected:
        {
          VRB_LOG("WVR_EventType_DeviceConnected");
        }
        break;
      case WVR_EventType_DeviceDisconnected:
        {
          VRB_LOG("WVR_EventType_DeviceDisconnected");
        }
        break;
      case WVR_EventType_DeviceStatusUpdate:
        {
          VRB_LOG("WVR_EventType_DeviceStatusUpdate");
        }
        break;
      case WVR_EventType_IpdChanged:
        {
          VRB_LOG("WVR_EventType_IpdChanged");
          m.InitializeCameras();
        }
        break;
      case WVR_EventType_DeviceSuspend:
        {
          VRB_LOG("WVR_EventType_DeviceSuspend");
        }
        break;
      case WVR_EventType_DeviceResume:
        {
          VRB_LOG("WVR_EventType_DeviceResume");
        }
        break;
      case WVR_EventType_DeviceRoleChanged:
        {
          VRB_LOG("WVR_EventType_DeviceRoleChanged");
        }
        break;
      case WVR_EventType_BatteryStatus_Update:
        {
          VRB_LOG("WVR_EventType_BatteryStatus_Update");
        }
        break;
      case WVR_EventType_ChargeStatus_Update:
        {
          VRB_LOG("WVR_EventType_ChargeStatus_Update");
        }
        break;
      case WVR_EventType_DeviceErrorStatus_Update:
        {
          VRB_LOG("WVR_EventType_DeviceErrorStatus_Update");
        }
        break;
      case WVR_EventType_BatteryTemperatureStatus_Update:
        {
          VRB_LOG("WVR_EventType_BatteryTemperatureStatus_Update");
        }
        break;
      case WVR_EventType_RecenterSuccess:
        {
          VRB_LOG("WVR_EventType_RecenterSuccess");
        }
        break;
      case WVR_EventType_RecenterFail:
        {
          VRB_LOG("WVR_EventType_RecenterFail");
        }
        break;
      case WVR_EventType_RecenterSuccess_3DoF:
        {
          VRB_LOG("WVR_EventType_RecenterSuccess_3DoF");
        }
        break;
      case WVR_EventType_RecenterFail_3DoF:
        {
          VRB_LOG("WVR_EventType_RecenterFail_3DoF");
        }
        break;
      case WVR_EventType_TouchpadSwipe_LeftToRight:
        {
          VRB_LOG("WVR_EventType_TouchpadSwipe_LeftToRight");
          m.gestures->AddGesture(GestureType::SwipeRight);
        }
        break;
      case WVR_EventType_TouchpadSwipe_RightToLeft:
        {
          VRB_LOG("WVR_EventType_TouchpadSwipe_RightToLeft");
          m.gestures->AddGesture(GestureType::SwipeLeft);
        }
        break;
      case WVR_EventType_TouchpadSwipe_DownToUp:
        {
          VRB_LOG("WVR_EventType_TouchpadSwipe_DownToUp");
        }
        break;
      case WVR_EventType_TouchpadSwipe_UpToDown:
        {
          VRB_LOG("WVR_EventType_TouchpadSwipe_UpToDown");
        }
        break;
      case WVR_EventType_Settings_Controller:
        {
          VRB_LOG("WVR_EventType_Settings_ControllerRoleChange");
        }
        break;
      case WVR_EventType_ButtonPressed:
        {
          VRB_LOG("WVR_EventType_ButtonPressed");
        }
        break;
      case WVR_EventType_ButtonUnpressed:
        {
          VRB_LOG("WVR_EventType_ButtonUnpressed");
        }
        break;
      case WVR_EventType_TouchTapped:
        {
          VRB_LOG("WVR_EventType_TouchTapped");
        }
        break;
      case WVR_EventType_TouchUntapped:
        {
          VRB_LOG("WVR_EventType_TouchUntapped");
        }
      default:
        {
          VRB_LOG("Unknown WVR_EventType");
        }
        break;
    }
  }
  m.UpdateControllers();
}

void
DeviceDelegateWaveVR::StartFrame() {
  VRB_GL_CHECK(glClearColor(m.clearColor.Red(), m.clearColor.Green(), m.clearColor.Blue(), m.clearColor.Alpha()));
  static const vrb::Vector kAverageHeight(0.0f, 1.7f, 0.0f);
  m.leftFBOIndex = WVR_GetAvailableTextureIndex(m.leftTextureQueue);
  m.rightFBOIndex = WVR_GetAvailableTextureIndex(m.rightTextureQueue);
  // Update cameras
  WVR_GetSyncPose(WVR_PoseOriginModel_OriginOnHead, m.devicePairs, WVR_DEVICE_COUNT_LEVEL_1);
  vrb::Matrix hmd = vrb::Matrix::Identity();
  if (m.devicePairs[WVR_DEVICE_HMD].pose.isValidPose) {
    hmd = vrb::Matrix::FromRowMajor(m.devicePairs[WVR_DEVICE_HMD].pose.poseMatrix.m);
    if (m.renderMode == device::RenderMode::StandAlone) {
      hmd.TranslateInPlace(kAverageHeight);
    }
    m.cameras[device::EyeIndex(device::Eye::Left)]->SetHeadTransform(hmd);
    m.cameras[device::EyeIndex(device::Eye::Right)]->SetHeadTransform(hmd);
  } else {
    VRB_LOG("Invalid pose returned");
  }
  if (!m.delegate) {
    return;
  }
  for (uint32_t id = WVR_DEVICE_HMD + 1; id < WVR_DEVICE_COUNT_LEVEL_1; id++) {
    if ((m.devicePairs[id].type != WVR_DeviceType_Controller_Right) &&
        (m.devicePairs[id].type != WVR_DeviceType_Controller_Left)) {
      continue;
    }
    Controller& controller = m.devicePairs[id].type == m.controllers[0].type ? m.controllers[0] : m.controllers[1];
    if (!controller.enabled) {
      continue;
    }
    const WVR_PoseState_t &pose = m.devicePairs[id].pose;
    if (!pose.isValidPose) {
      continue;
    }
    vrb::Matrix controllerTransform = vrb::Matrix::FromRowMajor(pose.poseMatrix.m);
    if (m.elbow) {
      ElbowModel::HandEnum hand = ElbowModel::HandEnum::Right;
      if (m.devicePairs[id].type == WVR_DeviceType_Controller_Left) {
        hand = ElbowModel::HandEnum::Left;
      }
      controllerTransform = m.elbow->GetTransform(hand, hmd, controllerTransform);
    } else {
      controllerTransform.TranslateInPlace(kAverageHeight);
    }
    m.delegate->SetTransform(controller.index, controllerTransform);
  }
}

void
DeviceDelegateWaveVR::BindEye(const device::Eye aWhich) {
  if (m.currentFBO) {
    m.currentFBO->Unbind();
  }
  if (aWhich == device::Eye::Left) {
    m.currentFBO = m.leftFBOQueue[m.leftFBOIndex];
  } else if (aWhich == device::Eye::Right) {
    m.currentFBO = m.rightFBOQueue[m.rightFBOIndex];
  } else {
    m.currentFBO = nullptr;
  }
  if (m.currentFBO) {
    m.currentFBO->Bind();
    VRB_GL_CHECK(glViewport(0, 0, m.renderWidth, m.renderHeight));
    VRB_GL_CHECK(glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT));
  } else {
    VRB_LOG("No FBO found");
  }
}

void
DeviceDelegateWaveVR::EndFrame() {
  if (m.currentFBO) {
    m.currentFBO->Unbind();
    m.currentFBO = nullptr;
  }
  // Left eye
  WVR_TextureParams_t leftEyeTexture = WVR_GetTexture(m.leftTextureQueue, m.leftFBOIndex);
  WVR_SubmitError result = WVR_SubmitFrame(WVR_Eye_Left, &leftEyeTexture);
  if (result != WVR_SubmitError_None) {
    VRB_LOG("Failed to submit left eye frame");
  }

  // Right eye
  WVR_TextureParams_t rightEyeTexture = WVR_GetTexture(m.rightTextureQueue, m.rightFBOIndex);
  result = WVR_SubmitFrame(WVR_Eye_Right, &rightEyeTexture);
  if (result != WVR_SubmitError_None) {
    VRB_LOG("Failed to submit right eye frame");
  }
}

bool
DeviceDelegateWaveVR::IsRunning() {
  return m.isRunning;
}

DeviceDelegateWaveVR::DeviceDelegateWaveVR(State& aState) : m(aState) {}
DeviceDelegateWaveVR::~DeviceDelegateWaveVR() { m.Shutdown(); }

} // namespace crow
