/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "DeviceDelegateWaveVR.h"
#include "DeviceUtils.h"
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
  bool lastSubmitDiscarded;
  bool recentered;
  vrb::Matrix reorientMatrix;
  bool ignoreNextRecenter;
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
      , lastSubmitDiscarded(false)
      , recentered(false)
      , ignoreNextRecenter(false)
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
    reorientMatrix = vrb::Matrix::Identity();
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
        VRB_ERROR("FAILED to make valid FBO");
      }
    }
  }

  void InitializeCameras() {
    for (WVR_Eye eye : {WVR_Eye_Left, WVR_Eye_Right}) {
      const device::Eye deviceEye = eye == WVR_Eye_Left ? device::Eye::Left : device::Eye::Right;
      vrb::Matrix eyeOffset = vrb::Matrix::FromRowMajor(WVR_GetTransformFromEyeToHead(eye, WVR_NumDoF_6DoF).m);
      cameras[device::EyeIndex(deviceEye)]->SetEyeTransform(eyeOffset);

      float left, right, top, bottom;
      WVR_GetClippingPlaneBoundary(eye, &left, &right, &top, &bottom);
      const float fovLeft = -atan(left);
      const float fovRight = atan(right);
      const float fovTop = atan(top);
      const float fovBottom = -atan(bottom);

      vrb::Matrix projection = vrb::Matrix::PerspectiveMatrix(fovLeft, fovRight, fovTop, fovBottom, near, far);
      cameras[device::EyeIndex(deviceEye)]->SetPerspective(projection);

      if (immersiveDisplay) {
        vrb::Vector translation = eyeOffset.GetTranslation();
        immersiveDisplay->SetEyeOffset(deviceEye, translation.x(), translation.y(), translation.z());
        const float toDegrees = 180.0f / (float)M_PI;
        immersiveDisplay->SetFieldOfView(deviceEye, fovLeft * toDegrees, fovRight * toDegrees, fovTop * toDegrees, fovBottom * toDegrees);
      }
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
    VRB_DEBUG("Recommended size is %ux%u", renderWidth, renderHeight);
    if (renderWidth == 0 || renderHeight == 0) {
      VRB_ERROR("Please check Wave server configuration");
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
        delegate->SetCapabilityFlags(index, device::Orientation);
      }

      const bool bumperPressed = WVR_GetInputButtonState(controller.type, WVR_InputId_Alias1_Digital_Trigger)
                                || WVR_GetInputButtonState(controller.type, WVR_InputId_Alias1_Trigger);
      const bool touchpadPressed = WVR_GetInputButtonState(controller.type, WVR_InputId_Alias1_Touchpad);
      const bool touchpadTouched = WVR_GetInputTouchState(controller.type, WVR_InputId_Alias1_Touchpad);
      const bool menuPressed = WVR_GetInputButtonState(controller.type, WVR_InputId_Alias1_Menu);

      delegate->SetButtonCount(index, 2); // For immersive mode
      delegate->SetButtonState(index, ControllerDelegate::BUTTON_TOUCHPAD, 0, touchpadPressed, touchpadTouched);
      delegate->SetButtonState(index, ControllerDelegate::BUTTON_TRIGGER, 1, bumperPressed, bumperPressed);
      delegate->SetButtonState(index, ControllerDelegate::BUTTON_APP, -1, menuPressed, menuPressed);

      const int32_t kNumAxes = 2;
      float immersiveAxes[kNumAxes] = { 0.0f, 0.0f };

      if (touchpadTouched) {
        WVR_Axis_t axis = WVR_GetInputAnalogAxis(controller.type, WVR_InputId_Alias1_Touchpad);
        delegate->SetTouchPosition(index, axis.x, -axis.y);
        immersiveAxes[0] = axis.x;
        immersiveAxes[1] = -axis.y;
        controllers[index].touched = true;
      } else if (controllers[index].touched) {
        controllers[index].touched = false;
        delegate->EndTouch(index);
      }

      delegate->SetAxes(index, immersiveAxes, kNumAxes);

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
  m.reorientMatrix = vrb::Matrix::Identity();
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

const vrb::Matrix&
DeviceDelegateWaveVR::GetReorientTransform() const {
  return m.reorientMatrix;
}

void
DeviceDelegateWaveVR::SetReorientTransform(const vrb::Matrix& aMatrix) {
  m.reorientMatrix = aMatrix;
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
    m.delegate->CreateController(index, 0, "Gear VR Controller");
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
  while (WVR_PollEventQueue(&event)) {
    WVR_EventType type = event.common.type;
    switch (type) {
      case WVR_EventType_Quit: {
        VRB_DEBUG("WVR_EventType_Quit");
        m.isRunning = false;
        return;
      }
        break;
      case WVR_EventType_SystemInteractionModeChanged: {
        VRB_DEBUG("WVR_EventType_SystemInteractionModeChanged");
      }
        break;
      case WVR_EventType_SystemGazeTriggerTypeChanged: {
        VRB_DEBUG("WVR_EventType_SystemGazeTriggerTypeChanged");
      }
        break;
      case WVR_EventType_TrackingModeChanged: {
        VRB_DEBUG("WVR_EventType_TrackingModeChanged");
      }
        break;
      case WVR_EventType_DeviceConnected: {
        VRB_DEBUG("WVR_EventType_DeviceConnected");
      }
        break;
      case WVR_EventType_DeviceDisconnected: {
        VRB_DEBUG("WVR_EventType_DeviceDisconnected");
      }
        break;
      case WVR_EventType_DeviceStatusUpdate: {
        VRB_DEBUG("WVR_EventType_DeviceStatusUpdate");
      }
        break;
      case WVR_EventType_IpdChanged: {
        VRB_DEBUG("WVR_EventType_IpdChanged");
        m.InitializeCameras();
      }
        break;
      case WVR_EventType_DeviceSuspend: {
        VRB_DEBUG("WVR_EventType_DeviceSuspend");
        m.reorientMatrix = vrb::Matrix::Identity();
        m.ignoreNextRecenter = true;
      }
        break;
      case WVR_EventType_DeviceResume: {
        VRB_DEBUG("WVR_EventType_DeviceResume");
        m.reorientMatrix = vrb::Matrix::Identity();
      }
        break;
      case WVR_EventType_DeviceRoleChanged: {
        VRB_DEBUG("WVR_EventType_DeviceRoleChanged");
      }
        break;
      case WVR_EventType_BatteryStatusUpdate: {
        VRB_DEBUG("WVR_EventType_BatteryStatusUpdate");
      }
        break;
      case WVR_EventType_ChargeStatusUpdate: {
        VRB_DEBUG("WVR_EventType_ChargeStatusUpdate");
      }
        break;
      case WVR_EventType_DeviceErrorStatusUpdate: {
        VRB_DEBUG("WVR_EventType_DeviceErrorStatusUpdate");
      }
        break;
      case WVR_EventType_BatteryTemperatureStatusUpdate: {
        VRB_DEBUG("WVR_EventType_BatteryTemperatureStatusUpdate");
      }
        break;
      case WVR_EventType_RecenterSuccess: {
        VRB_DEBUG("WVR_EventType_RecenterSuccess");
        WVR_InAppRecenter(WVR_RecenterType_YawAndPosition);
        m.recentered = !m.ignoreNextRecenter;
        m.ignoreNextRecenter = false;
      }
        break;
      case WVR_EventType_RecenterFail: {
        VRB_DEBUG("WVR_EventType_RecenterFail");
      }
        break;
      case WVR_EventType_RecenterSuccess3DoF: {
        VRB_DEBUG("WVR_EventType_RecenterSuccess_3DoF");
        WVR_InAppRecenter(WVR_RecenterType_YawAndPosition);
        m.recentered = !m.ignoreNextRecenter;
        m.ignoreNextRecenter = false;
      }
        break;
      case WVR_EventType_RecenterFail3DoF: {
        VRB_DEBUG("WVR_EventType_RecenterFail_3DoF");
      }
        break;
      case WVR_EventType_ButtonPressed: {
        VRB_DEBUG("WVR_EventType_ButtonPressed");
      }
        break;
      case WVR_EventType_ButtonUnpressed: {
        VRB_DEBUG("WVR_EventType_ButtonUnpressed");
      }
        break;
      case WVR_EventType_TouchTapped: {
        VRB_DEBUG("WVR_EventType_TouchTapped");
      }
        break;
      case WVR_EventType_TouchUntapped: {
        VRB_DEBUG("WVR_EventType_TouchUntapped");
        break;
      }
      case WVR_EventType_LeftToRightSwipe: {
        VRB_DEBUG("WVR_EventType_LeftToRightSwipe");
        m.gestures->AddGesture(GestureType::SwipeRight);
      }
        break;
      case WVR_EventType_RightToLeftSwipe: {
        VRB_DEBUG("WVR_EventType_RightToLeftSwipe");
        m.gestures->AddGesture(GestureType::SwipeLeft);
      }
        break;
      case WVR_EventType_DownToUpSwipe: {
        VRB_DEBUG("WVR_EventType_DownToUpSwipe");
      }
        break;
      case WVR_EventType_UpToDownSwipe: {
        VRB_DEBUG("WVR_EventType_UpToDownSwipe");
      }
        break;
      default: {
        VRB_DEBUG("Unknown WVR_EventType");
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
  if (!m.lastSubmitDiscarded) {
    m.leftFBOIndex = WVR_GetAvailableTextureIndex(m.leftTextureQueue);
    m.rightFBOIndex = WVR_GetAvailableTextureIndex(m.rightTextureQueue);
  }
  // Update cameras
  WVR_GetSyncPose(WVR_PoseOriginModel_OriginOnHead, m.devicePairs, WVR_DEVICE_COUNT_LEVEL_1);
  vrb::Matrix hmd = vrb::Matrix::Identity();
  if (m.devicePairs[WVR_DEVICE_HMD].pose.isValidPose) {
    hmd = vrb::Matrix::FromRowMajor(m.devicePairs[WVR_DEVICE_HMD].pose.poseMatrix.m);
    if (m.renderMode == device::RenderMode::StandAlone) {
      if (m.recentered) {
        m.reorientMatrix = DeviceUtils::CalculateReorientationMatrix(hmd, kAverageHeight);
      }
      hmd.TranslateInPlace(kAverageHeight);
    }
    m.cameras[device::EyeIndex(device::Eye::Left)]->SetHeadTransform(hmd);
    m.cameras[device::EyeIndex(device::Eye::Right)]->SetHeadTransform(hmd);
  } else {
    VRB_DEBUG("Invalid pose returned");
  }
  m.recentered = false;
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
    if (m.elbow && !pose.is6DoFPose) {
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
    VRB_ERROR("No FBO found");
  }
}

void
DeviceDelegateWaveVR::EndFrame(const bool aDiscard) {
  if (m.currentFBO) {
    m.currentFBO->Unbind();
    m.currentFBO = nullptr;
  }

  m.lastSubmitDiscarded = aDiscard;
  if (aDiscard) {
    return;
  }
  // Left eye
  WVR_TextureParams_t leftEyeTexture = WVR_GetTexture(m.leftTextureQueue, m.leftFBOIndex);
  WVR_SubmitError result = WVR_SubmitFrame(WVR_Eye_Left, &leftEyeTexture);
  if (result != WVR_SubmitError_None) {
    VRB_ERROR("Failed to submit left eye frame");
  }

  // Right eye
  WVR_TextureParams_t rightEyeTexture = WVR_GetTexture(m.rightTextureQueue, m.rightFBOIndex);
  result = WVR_SubmitFrame(WVR_Eye_Right, &rightEyeTexture);
  if (result != WVR_SubmitError_None) {
    VRB_ERROR("Failed to submit right eye frame");
  }
}

bool
DeviceDelegateWaveVR::IsRunning() {
  return m.isRunning;
}

DeviceDelegateWaveVR::DeviceDelegateWaveVR(State& aState) : m(aState) {}
DeviceDelegateWaveVR::~DeviceDelegateWaveVR() { m.Shutdown(); }

} // namespace crow
