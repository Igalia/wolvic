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
#include "vrb/CreationContext.h"
#include "vrb/FBO.h"
#include "vrb/GLError.h"
#include "vrb/Matrix.h"
#include "vrb/RenderContext.h"
#include "vrb/Vector.h"
#include "vrb/VertexArray.h"
#include "vrb/ProgramFactory.h"
#include "vrb/RenderState.h"
#include "vrb/Geometry.h"
#include "vrb/Group.h"
#include "vrb/TextureGL.h"
#include "../../main/cpp/DeviceDelegate.h"

#include <array>
#include <vector>
#include <mutex>

#include <wvr/wvr.h>
#include <wvr/wvr_render.h>
#include <wvr/wvr_device.h>
#include <wvr/wvr_projection.h>
#include <wvr/wvr_overlay.h>
#include <wvr/wvr_system.h>
#include <wvr/wvr_events.h>
#include <wvr/wvr_arena.h>
#include <wvr/wvr_ctrller_render_model.h>

namespace crow {

static const vrb::Vector kAverageHeight(0.0f, 1.7f, 0.0f);
static const int32_t kMaxControllerCount = 2;
static const int32_t kRecenterDelay = 72;

struct DeviceDelegateWaveVR::State {
  struct Controller {
    int32_t index;
    WVR_DeviceType type;
    bool created;
    bool enabled;
    bool touched;
    bool is6DoF;
    int32_t gripPressedCount;
    vrb::Matrix transform;
    ElbowModel::HandEnum hand;
    uint64_t inputFrameID;
    float remainingVibrateTime;
    double lastHapticUpdateTimeStamp;
    Controller()
        : index(-1)
          , type(WVR_DeviceType_Controller_Right)
          , created(false)
          , enabled(false)
          , touched(false)
          , is6DoF(false)
          , gripPressedCount(0)
          , transform(vrb::Matrix::Identity())
          , hand(ElbowModel::HandEnum::Right)
          , inputFrameID(0)
          , remainingVibrateTime(0.0f)
          , lastHapticUpdateTimeStamp(0.0f)
    {}
  };

  vrb::RenderContextWeak context;
  bool isRunning;
  vrb::Color clearColor;
  float near;
  float far;
  float foveatedFov;
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
  device::DeviceType deviceType;
  bool lastSubmitDiscarded;
  bool recentered;
  vrb::Matrix reorientMatrix;
  bool ignoreNextRecenter;
  int32_t sixDoFControllerCount;
  bool handsCalculated;
  WVR_CtrlerModel_t * modelCachedData[2];
  bool isModelDataReady[2];
  std::mutex mCachedDataMutex[2];
  bool gotFirstValidPose;
  State()
      : isRunning(true)
      , near(0.1f)
      , far(100.f)
      , foveatedFov(0.0f)
      , renderMode(device::RenderMode::StandAlone)
      , leftFBOIndex(0)
      , rightFBOIndex(0)
      , leftTextureQueue(nullptr)
      , rightTextureQueue(nullptr)
      , renderWidth(0)
      , renderHeight(0)
      , devicePairs {}
      , controllers {}
      , deviceType(device::UnknownType)
      , lastSubmitDiscarded(false)
      , recentered(false)
      , ignoreNextRecenter(false)
      , sixDoFControllerCount(0)
      , handsCalculated(false)
      , modelCachedData {}
      , isModelDataReady {}
      , gotFirstValidPose(false)
  {
    memset((void*)devicePairs, 0, sizeof(WVR_DevicePosePair_t) * 2);
    memset((void*)modelCachedData, 0, sizeof(WVR_CtrlerModel_t) * 2);
    memset((void*)isModelDataReady, 0, sizeof(bool) * WVR_DEVICE_COUNT_LEVEL_1);
    gestures = GestureDelegate::Create();
    for (int32_t index = 0; index < kMaxControllerCount; index++) {
      controllers[index].index = index;
      if (index == 0) {
        controllers[index].type = WVR_DeviceType_Controller_Right;
        controllers[index].hand = ElbowModel::HandEnum::Right;
      } else {
        controllers[index].type = WVR_DeviceType_Controller_Left;
        controllers[index].hand = ElbowModel::HandEnum::Left;
      }
      controllers[index].is6DoF = WVR_GetDegreeOfFreedom(controllers[index].type) == WVR_NumDoF_6DoF;
      if (controllers[index].is6DoF) {
        sixDoFControllerCount++;
      }
    }
    if (sixDoFControllerCount) {
      deviceType = device::ViveFocusPlus;
    } else {
      deviceType = device::ViveFocus;
    }
    reorientMatrix = vrb::Matrix::Identity();
  }

  void FillFBOQueue(void* aTextureQueue, std::vector<vrb::FBOPtr>& aFBOQueue) {
    vrb::FBO::Attributes attributes;
    attributes.samples = 4;
    vrb::RenderContextPtr render = context.lock();
    for (int ix = 0; ix < WVR_GetTextureQueueLength(aTextureQueue); ix++) {
      vrb::FBOPtr fbo = vrb::FBO::Create(render);
      uintptr_t handle = (uintptr_t)WVR_GetTexture(aTextureQueue, ix).id;
      fbo->SetTextureHandle((GLuint)handle, renderWidth, renderHeight, attributes);
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

      // We wanna use 1/3 fovX degree as the foveated fov.
      foveatedFov = (fovLeft + fovRight) * 180.0f / (float)M_PI / 3.0f;
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

    // Set the input keys that we are using.
    WVR_InputAttribute inputIdAndTypes[] = {
            {WVR_InputId_Alias1_Menu, WVR_InputType_Button, WVR_AnalogType_None},
            {WVR_InputId_Alias1_Touchpad, WVR_InputType_Button | WVR_InputType_Touch | WVR_InputType_Analog, WVR_AnalogType_2D},
            {WVR_InputId_Alias1_Trigger, WVR_InputType_Button , WVR_AnalogType_None},
            {WVR_InputId_Alias1_Bumper, WVR_InputType_Button , WVR_AnalogType_None},
            {WVR_InputId_Alias1_Grip, WVR_InputType_Button , WVR_AnalogType_None},
            {WVR_InputId_Alias1_A, WVR_InputType_Button , WVR_AnalogType_None},
            {WVR_InputId_Alias1_B, WVR_InputType_Button , WVR_AnalogType_None},
            {WVR_InputId_Alias1_Thumbstick, WVR_InputType_Button | WVR_InputType_Touch | WVR_InputType_Analog, WVR_AnalogType_2D},
    };
    WVR_SetInputRequest(WVR_DeviceType_HMD, inputIdAndTypes, sizeof(inputIdAndTypes) / sizeof(*inputIdAndTypes));
    WVR_SetInputRequest(WVR_DeviceType_Controller_Right, inputIdAndTypes, sizeof(inputIdAndTypes) / sizeof(*inputIdAndTypes));
    WVR_SetInputRequest(WVR_DeviceType_Controller_Left, inputIdAndTypes, sizeof(inputIdAndTypes) / sizeof(*inputIdAndTypes));

    elbow = ElbowModel::Create();
  }

  void InitializeRender() {
    WVR_GetRenderTargetSize(&renderWidth, &renderHeight);
    VRB_GL_CHECK(glViewport(0, 0, renderWidth, renderHeight));
    VRB_DEBUG("Recommended size is %ux%u", renderWidth, renderHeight);
    if (renderWidth == 0 || renderHeight == 0) {
      VRB_ERROR("Please check Wave server configuration");
      return;
    }
    if (immersiveDisplay) {
      immersiveDisplay->SetEyeResolution(renderWidth, renderHeight);
    }
    InitializeTextureQueues();
  }

  void InitializeTextureQueues() {
    ReleaseTextureQueues();
    VRB_LOG("Create texture queues: %dx%d", renderWidth, renderHeight);
    leftTextureQueue = WVR_ObtainTextureQueue(WVR_TextureTarget_2D, WVR_TextureFormat_RGBA, WVR_TextureType_UnsignedByte, renderWidth, renderHeight, 0);
    FillFBOQueue(leftTextureQueue, leftFBOQueue);
    rightTextureQueue = WVR_ObtainTextureQueue(WVR_TextureTarget_2D, WVR_TextureFormat_RGBA, WVR_TextureType_UnsignedByte, renderWidth, renderHeight, 0);
    FillFBOQueue(rightTextureQueue, rightFBOQueue);
  }

  void ReleaseTextureQueues() {
    if (leftTextureQueue) {
      WVR_ReleaseTextureQueue(leftTextureQueue);
      leftTextureQueue = nullptr;
    }
    leftFBOQueue.clear();
    if (rightTextureQueue) {
      WVR_ReleaseTextureQueue(rightTextureQueue);
      rightTextureQueue = nullptr;
    }
    rightFBOQueue.clear();
  }

  void Shutdown() {
    ReleaseTextureQueues();
  }

  void UpdateStandingMatrix() {
    if (!immersiveDisplay) {
      return;
    }
    WVR_PoseState_t head;
    WVR_PoseState_t ground;
    WVR_GetPoseState(WVR_DeviceType_HMD, WVR_PoseOriginModel_OriginOnHead, 0, &head);
    WVR_GetPoseState(WVR_DeviceType_HMD, WVR_PoseOriginModel_OriginOnGround, 0, &ground);
    if (!head.isValidPose || !ground.isValidPose) {
      immersiveDisplay->SetSittingToStandingTransform(vrb::Matrix::Translation(kAverageHeight));
      return;
    }
    gotFirstValidPose = true;
    const float delta = ground.poseMatrix.m[1][3] - head.poseMatrix.m[1][3];
    immersiveDisplay->SetSittingToStandingTransform(vrb::Matrix::Translation(vrb::Vector(0.0f, delta, 0.0f)));
  }

  void CreateController(Controller& aController) {
    if (!delegate) {
      VRB_ERROR("Failed to create controller. No ControllerDelegate has been set.");
      return;
    }
    vrb::Matrix beamTransform(vrb::Matrix::Identity());
    if (aController.is6DoF) {
      beamTransform.TranslateInPlace(vrb::Vector(0.0f, 0.01f, -0.05f));
    }
    delegate->CreateController(aController.index, aController.is6DoF ? static_cast<int>(aController.hand) : 0,
            aController.is6DoF ? "HTC Vive 6DoF Controller" : "HTC Vive 3DoF Controller",
            beamTransform);
    delegate->SetLeftHanded(aController.index, aController.hand == ElbowModel::HandEnum::Left);
    delegate->SetHapticCount(aController.index, 1);
    delegate->SetControllerType(aController.index, aController.is6DoF ? device::ViveFocusPlus :
                                device::ViveFocus);
    delegate->SetTargetRayMode(aController.index, device::TargetRayMode::TrackedPointer);

    if (aController.is6DoF) {
      const vrb::Matrix trans = vrb::Matrix::Position(vrb::Vector(0.0f, -0.021f, -0.03f));
      vrb::Matrix transform = vrb::Matrix::Rotation(vrb::Vector(1.0f, 0.0f, 0.0f), -0.70f);
      transform = transform.PostMultiply(trans);

      delegate->SetImmersiveBeamTransform(aController.index, beamTransform.PostMultiply(transform));
    }
    aController.created = true;
    aController.enabled = false;
  }

  void UpdateControllers() {
    if (!delegate) {
      return;
    }

    if (WVR_IsInputFocusCapturedBySystem()) {
      for (Controller& controller: controllers) {
        if (controller.enabled) {
          delegate->SetEnabled(controller.index, false);
          controller.enabled = false;
        }
      }
      return;
    }

    for (Controller& controller: controllers) {
      const bool is6DoF = WVR_GetDegreeOfFreedom(controller.type) == WVR_NumDoF_6DoF;
      if (controller.is6DoF != is6DoF) {
        controller.is6DoF = is6DoF;
        if (is6DoF) {
          sixDoFControllerCount++;
        } else {
          sixDoFControllerCount--;
        }
        controller.created = false;
      }

      if (!controller.created) {
        VRB_LOG("Creating controller from UpdateControllers");
        CreateController(controller);
      }
      if (!WVR_IsDeviceConnected(controller.type)) {
        if (controller.enabled) {
          delegate->SetEnabled(controller.index, false);
          controller.enabled = false;
        }
        continue;
      } else if (!controller.enabled) {
        device::CapabilityFlags flags = device::Orientation | device::GripSpacePosition;
        if (controller.is6DoF) {
          flags |= device::Position;
        } else {
          flags |= device::PositionEmulated;
        }
        controller.enabled = true;
        delegate->SetEnabled(controller.index, true);
        delegate->SetCapabilityFlags(controller.index, flags);
      }

      uint32_t ctl_button = WVR_GetInputDeviceCapability(controller.type, WVR_InputType_Button);
      uint32_t ctl_touch = WVR_GetInputDeviceCapability(controller.type, WVR_InputType_Touch);
      uint32_t ctl_analog = WVR_GetInputDeviceCapability(controller.type, WVR_InputType_Analog);

      const bool bumperPressed = (controller.is6DoF) ? WVR_GetInputButtonState(controller.type, WVR_InputId_Alias1_Trigger)
                                  : WVR_GetInputButtonState(controller.type, WVR_InputId_Alias1_Bumper);

      // ABXY buttons
      if (ctl_button & WVR_InputId_Alias1_A) {
        const bool aPressed = WVR_GetInputButtonState(controller.type, WVR_InputId_Alias1_A);
        const bool aTouched = WVR_GetInputTouchState(controller.type, WVR_InputId_Alias1_A);
        if (controller.hand == ElbowModel::HandEnum::Left) {
          delegate->SetButtonState(controller.index, ControllerDelegate::BUTTON_X, device::kImmersiveButtonA, aPressed, aTouched);
        } else if (controller.hand == ElbowModel::HandEnum::Right) {
          delegate->SetButtonState(controller.index, ControllerDelegate::BUTTON_A, device::kImmersiveButtonA, aPressed, aTouched);
        }
      }
      if (ctl_button & WVR_InputId_Alias1_B) {
        const bool bPressed = WVR_GetInputButtonState(controller.type, WVR_InputId_Alias1_B);
        const bool bTouched = WVR_GetInputTouchState(controller.type, WVR_InputId_Alias1_B);
        if (controller.hand == ElbowModel::HandEnum::Left) {
          delegate->SetButtonState(controller.index, ControllerDelegate::BUTTON_Y, device::kImmersiveButtonB, bPressed, bTouched);
        } else if (controller.hand == ElbowModel::HandEnum::Right) {
          delegate->SetButtonState(controller.index, ControllerDelegate::BUTTON_B, device::kImmersiveButtonB, bPressed, bTouched);
        }
      }

      const bool touchpadPressed = WVR_GetInputButtonState(controller.type, WVR_InputId_Alias1_Touchpad);
      const bool touchpadTouched = WVR_GetInputTouchState(controller.type, WVR_InputId_Alias1_Touchpad);
      const bool menuPressed = WVR_GetInputButtonState(controller.type, WVR_InputId_Alias1_Menu);

      // Although Focus only has two buttons, in order to match WebXR input profile (squeeze placeholder),
      // we make Focus has three buttons.
      delegate->SetButtonCount(controller.index, 3);
      delegate->SetButtonState(controller.index, ControllerDelegate::BUTTON_TOUCHPAD, device::kImmersiveButtonTouchpad, touchpadPressed, touchpadTouched);
      delegate->SetButtonState(controller.index, ControllerDelegate::BUTTON_TRIGGER, device::kImmersiveButtonTrigger, bumperPressed, bumperPressed);
      if (controller.is6DoF) {
        const bool gripPressed = WVR_GetInputButtonState(controller.type, WVR_InputId_Alias1_Grip);
        if (renderMode == device::RenderMode::StandAlone) {
          if (gripPressed && (controller.gripPressedCount >= 0)) {
            controller.gripPressedCount++;
          } else if (!gripPressed) {
            controller.gripPressedCount = 0;
          }
          if (controller.gripPressedCount > kRecenterDelay) {
            WVR_InAppRecenter(WVR_RecenterType_YawAndPosition);
            recentered = true;
            handsCalculated = false;
            controller.gripPressedCount = -1;
          }
        } else {
          delegate->SetButtonState(controller.index, ControllerDelegate::BUTTON_SQUEEZE, device::kImmersiveButtonSqueeze,
                  gripPressed, gripPressed);
          controller.gripPressedCount = 0;
        }
        if (gripPressed && renderMode == device::RenderMode::Immersive) {
          delegate->SetSqueezeActionStart(controller.index);
        } else {
          delegate->SetSqueezeActionStop(controller.index);
        }
      }

      if (bumperPressed && renderMode == device::RenderMode::Immersive) {
        delegate->SetSelectActionStart(controller.index);
      } else {
        delegate->SetSelectActionStop(controller.index);
      }
      delegate->SetButtonState(controller.index, ControllerDelegate::BUTTON_APP, -1, menuPressed, menuPressed);

      float axisX, axisY = 0.0f;
      if (touchpadTouched) {
        WVR_Axis_t axis = WVR_GetInputAnalogAxis(controller.type, WVR_InputId_Alias1_Touchpad);
        axisX = axis.x;
        axisY = -axis.y;
        // In case we have thumbstick we don't send the touchpad touched event
        if (!(ctl_touch & WVR_InputId_Alias1_Thumbstick)) {
          // We are matching touch pad range from {-1, 1} to the Oculus {0, 1}.
          delegate->SetTouchPosition(controller.index, (axis.x + 1) * 0.5, (-axis.y + 1) * 0.5);
          controller.touched = true;
        }
        delegate->SetScrolledDelta(
                controller.index,
                -axis.x, axis.y);
      } else if (controller.touched) {
        if (!(ctl_touch & WVR_InputId_Alias1_Thumbstick)) {
          controller.touched = false;
          delegate->EndTouch(controller.index);
        }
      }

      if (controller.is6DoF) {
        const int32_t kNumAxes = 4;
        float immersiveAxes[kNumAxes];
        immersiveAxes[device::kImmersiveAxisTouchpadX] = immersiveAxes[device::kImmersiveAxisTouchpadY] = 0.0f;
        immersiveAxes[device::kImmersiveAxisThumbstickX] = axisX;
        immersiveAxes[device::kImmersiveAxisThumbstickY] = axisY;
        delegate->SetAxes(controller.index, immersiveAxes, kNumAxes);
      } else {
        const int32_t kNumAxes = 2;
        float immersiveAxes[kNumAxes] = { 0.0f, 0.0f };
        immersiveAxes[device::kImmersiveAxisTouchpadX] = axisX;
        immersiveAxes[device::kImmersiveAxisTouchpadY] = axisY;
        delegate->SetAxes(controller.index, immersiveAxes, kNumAxes);
      }

      UpdateHaptics(controller);
    }
  }

  void UpdateBoundary() {
    if (!immersiveDisplay) {
      return;
    }
    WVR_Arena_t arena = WVR_GetArena();
    if (arena.shape == WVR_ArenaShape_Rectangle &&
        arena.area.rectangle.width > 0 &&
        arena.area.rectangle.length > 0) {
      immersiveDisplay->SetStageSize(arena.area.rectangle.width, arena.area.rectangle.length);
    } else if (arena.shape == WVR_ArenaShape_Round && arena.area.round.diameter > 0) {
      immersiveDisplay->SetStageSize(arena.area.round.diameter, arena.area.round.diameter);
    } else {
      immersiveDisplay->SetStageSize(0.0f, 0.0f);
    }
  }

  void UpdateHaptics(Controller& controller) {
    vrb::RenderContextPtr renderContext = context.lock();
    if (!renderContext) {
      return;
    }
    if (!delegate) {
      return;
    }

    uint64_t inputFrameID = 0;
    float pulseDuration = 0.0f, pulseIntensity = 0.0f;
    delegate->GetHapticFeedback(controller.index, inputFrameID, pulseDuration, pulseIntensity);
    if (inputFrameID > 0 && pulseIntensity > 0.0f && pulseDuration > 0) {
      if (controller.inputFrameID != inputFrameID) {
        // When there is a new input frame id from haptic vibration,
        // that means we start a new session for a vibration.
        controller.inputFrameID = inputFrameID;
        controller.remainingVibrateTime = pulseDuration;
        controller.lastHapticUpdateTimeStamp = renderContext->GetTimestamp();
      } else {
        // We are still running the previous vibration.
        // So, it needs to reduce the delta time from the last vibration.
        const double timeStamp = renderContext->GetTimestamp();
        controller.remainingVibrateTime -= (timeStamp - controller.lastHapticUpdateTimeStamp);
        controller.lastHapticUpdateTimeStamp = timeStamp;
      }

      if (controller.remainingVibrateTime > 0.0f && renderMode == device::RenderMode::Immersive) {
        // THe duration time unit needs to be transformed from milliseconds to microseconds.
        // The gamepad extensions API does not yet have independent control
        // of frequency and intensity. It only has vibration value (0.0 ~ 1.0).
        // In this WaveVR SDK, the value makes more sense to be intensity because frequency can't
        // < 1.0 here.
        int intensity = ceil(pulseIntensity * 5);
        intensity = intensity <= 5 ? intensity : 5;
        WVR_TriggerVibration(controller.type, WVR_InputId_Max, controller.remainingVibrateTime * 1000.0f,
                             1, WVR_Intensity(intensity));
      } else {
        // The remaining time is zero or exiting the immersive mode, stop the vibration.
#if !defined(__arm__) // It will crash at WaveVR SDK arm32, let's skip it.
        WVR_TriggerVibration(controller.type, WVR_InputId_Max, 0, 0, WVR_Intensity_Normal);
#endif
        controller.remainingVibrateTime = 0.0f;
      }
    } else if (controller.remainingVibrateTime > 0.0f) {
      // While the haptic feedback is terminated from the client side,
      // but it still have remaining time, we need to ask for stopping vibration.
#if !defined(__arm__) // It will crash at WaveVR SDK arm32, let's skip it.
      WVR_TriggerVibration(controller.type, WVR_InputId_Max, 0, 0, WVR_Intensity_Normal);
#endif
      controller.remainingVibrateTime = 0.0f;
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

void DeviceDelegateWaveVR::InitializeRender() {
  m.InitializeRender();
}

device::DeviceType
DeviceDelegateWaveVR::GetDeviceType() {
  return m.deviceType;
}

void
DeviceDelegateWaveVR::SetRenderMode(const device::RenderMode aMode) {
  if (aMode == m.renderMode) {
    return;
  }
  // To make sure assigning correct hands before entering immersive mode.
  if (aMode == device::RenderMode::Immersive) {
    m.handsCalculated = false;
  }

  m.renderMode = aMode;
  m.reorientMatrix = vrb::Matrix::Identity();

  uint32_t recommendedWidth, recommendedHeight;
  WVR_GetRenderTargetSize(&recommendedWidth, &recommendedHeight);
  if (recommendedWidth != m.renderWidth || recommendedHeight != m.renderHeight) {
    m.renderWidth = recommendedWidth;
    m.renderHeight = recommendedHeight;
    m.InitializeTextureQueues();
  }
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
  device::CapabilityFlags flags = device::Orientation | device::Present |
                                  device::InlineSession | device::ImmersiveVRSession;

  if (WVR_GetDegreeOfFreedom(WVR_DeviceType_HMD) == WVR_NumDoF_6DoF) {
    flags |= device::Position | device::StageParameters;
  } else {
    flags |= device::PositionEmulated;
  }

  m.immersiveDisplay->SetCapabilityFlags(flags);
  m.immersiveDisplay->SetEyeResolution(m.renderWidth, m.renderHeight);
  m.UpdateStandingMatrix();
  m.UpdateBoundary();
  m.InitializeCameras();
  m.immersiveDisplay->CompleteEnumeration();
}

void
DeviceDelegateWaveVR::SetImmersiveSize(const uint32_t aEyeWidth, const uint32_t aEyeHeight) {
  uint32_t recommendedWidth, recommendedHeight;
  WVR_GetRenderTargetSize(&recommendedWidth, &recommendedHeight);

  uint32_t targetWidth = m.renderWidth;
  uint32_t targetHeight = m.renderHeight;

  DeviceUtils::GetTargetImmersiveSize(aEyeWidth, aEyeHeight, recommendedWidth, recommendedHeight, targetWidth, targetHeight);

  if (targetWidth != m.renderWidth || targetHeight != m.renderHeight) {
    m.renderWidth = targetWidth;
    m.renderHeight = targetHeight;
    m.InitializeTextureQueues();
  }
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
DeviceDelegateWaveVR::Reorient() {
  vrb::Matrix head = GetHeadTransform();
  m.reorientMatrix = DeviceUtils::CalculateReorientationMatrixOnHeadLock(head, kAverageHeight);
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
  for (State::Controller& controller: m.controllers) {
    VRB_LOG("Creating controller from SetControllerDelegate");
    m.CreateController(controller);
  }
}

void
DeviceDelegateWaveVR::ReleaseControllerDelegate() {
  m.delegate = nullptr;
}

int32_t
DeviceDelegateWaveVR::GetControllerModelCount() const {
  return 2;
}

// #define VRB_WAVE_EVENT_LOG_ENABLED 1
#if defined(VRB_WAVE_EVENT_LOG_ENABLED)
#  define VRB_WAVE_EVENT_LOG(x) VRB_DEBUG(x)
#else
#  define VRB_WAVE_EVENT_LOG(x)
#endif // VRB_WAVE_EVENT_DEBUG

void
DeviceDelegateWaveVR::ProcessEvents() {
  WVR_Event_t event;
  m.gestures->Reset();
  while (WVR_PollEventQueue(&event)) {
    WVR_EventType type = event.common.type;
    switch (type) {
      case WVR_EventType_Quit: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_Quit");
        m.isRunning = false;
        return;
      }
      case WVR_EventType_SystemInteractionModeChanged: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_SystemInteractionModeChanged");
      }
        break;
      case WVR_EventType_SystemGazeTriggerTypeChanged: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_SystemGazeTriggerTypeChanged");
      }
        break;
      case WVR_EventType_TrackingModeChanged: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_TrackingModeChanged");
      }
        break;
      case WVR_EventType_DeviceConnected: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_DeviceConnected");
      }
        break;
      case WVR_EventType_DeviceDisconnected: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_DeviceDisconnected");
      }
        break;
      case WVR_EventType_DeviceStatusUpdate: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_DeviceStatusUpdate");
      }
        break;
      case WVR_EventType_IpdChanged: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_IpdChanged");
        m.InitializeCameras();
      }
        break;
      case WVR_EventType_DeviceSuspend: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_DeviceSuspend");
        m.reorientMatrix = vrb::Matrix::Identity();
        m.ignoreNextRecenter = true;
      }
        break;
      case WVR_EventType_DeviceResume: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_DeviceResume");
        m.reorientMatrix = vrb::Matrix::Identity();
        m.UpdateBoundary();
      }
        break;
      case WVR_EventType_DeviceRoleChanged: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_DeviceRoleChanged");
      }
        break;
      case WVR_EventType_BatteryStatusUpdate: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_BatteryStatusUpdate");
      }
        break;
      case WVR_EventType_ChargeStatusUpdate: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_ChargeStatusUpdate");
      }
        break;
      case WVR_EventType_DeviceErrorStatusUpdate: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_DeviceErrorStatusUpdate");
      }
        break;
      case WVR_EventType_BatteryTemperatureStatusUpdate: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_BatteryTemperatureStatusUpdate");
      }
        break;
      case WVR_EventType_RecenterSuccess: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_RecenterSuccess");
        WVR_InAppRecenter(WVR_RecenterType_YawAndPosition);
        m.recentered = !m.ignoreNextRecenter;
        m.ignoreNextRecenter = false;
        m.UpdateStandingMatrix();
      }
        break;
      case WVR_EventType_RecenterFail: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_RecenterFail");
      }
        break;
      case WVR_EventType_RecenterSuccess3DoF: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_RecenterSuccess_3DoF");
        WVR_InAppRecenter(WVR_RecenterType_YawAndPosition);
        m.recentered = !m.ignoreNextRecenter;
        m.ignoreNextRecenter = false;
      }
        break;
      case WVR_EventType_RecenterFail3DoF: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_RecenterFail_3DoF");
      }
        break;
      case WVR_EventType_ButtonPressed: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_ButtonPressed");
      }
        break;
      case WVR_EventType_ButtonUnpressed: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_ButtonUnpressed");
      }
        break;
      case WVR_EventType_TouchTapped: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_TouchTapped");
      }
        break;
      case WVR_EventType_TouchUntapped: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_TouchUntapped");
        break;
      }
      case WVR_EventType_LeftToRightSwipe: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_LeftToRightSwipe");
        m.gestures->AddGesture(GestureType::SwipeRight);
      }
        break;
      case WVR_EventType_RightToLeftSwipe: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_RightToLeftSwipe");
        m.gestures->AddGesture(GestureType::SwipeLeft);
      }
        break;
      case WVR_EventType_DownToUpSwipe: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_DownToUpSwipe");
      }
        break;
      case WVR_EventType_UpToDownSwipe: {
        VRB_WAVE_EVENT_LOG("WVR_EventType_UpToDownSwipe");
      }
        break;
      default: {
        VRB_WAVE_EVENT_LOG("Unknown WVR_EventType");
      }
        break;
    }
  }
  m.UpdateControllers();
}

static inline vrb::Vector
GetDirection(const vrb::Vector& location, const vrb::Vector& head) {
  vrb::Vector result = location - head;
  result.y() = 0.0f;
  result = result.Normalize();
  return result;
}

static inline const char*
HandToString(ElbowModel::HandEnum hand) {
  if (hand == ElbowModel::HandEnum::Right) {
    return "Right";
  }
  return "Left";
}

void
DeviceDelegateWaveVR::StartFrame(const FramePrediction aPrediction) {
  mShouldRender = false;
  VRB_GL_CHECK(glClearColor(m.clearColor.Red(), m.clearColor.Green(), m.clearColor.Blue(), m.clearColor.Alpha()));
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
    State::Controller& controller = m.devicePairs[id].type == m.controllers[0].type ? m.controllers[0] : m.controllers[1];
    if (!controller.enabled) {
      continue;
    }
    float level = WVR_GetDeviceBatteryPercentage(controller.type);
    m.delegate->SetBatteryLevel(controller.index, (int)(level * 100.0f));
    const WVR_PoseState_t &pose = m.devicePairs[id].pose;
    if (!pose.isValidPose) {
      continue;
    }
    controller.transform = vrb::Matrix::FromRowMajor(pose.poseMatrix.m);
    if (m.elbow && !pose.is6DoFPose) {
      ElbowModel::HandEnum hand = ElbowModel::HandEnum::Right;
      if (m.devicePairs[id].type == WVR_DeviceType_Controller_Left) {
        hand = ElbowModel::HandEnum::Left;
      }
      controller.transform = m.elbow->GetTransform(hand, hmd, controller.transform);
    } else if (m.renderMode == device::RenderMode::StandAlone) {
      controller.transform.TranslateInPlace(kAverageHeight);
    }
    if (m.renderMode == device::RenderMode::Immersive && pose.is6DoFPose) {
      static vrb::Matrix transform(vrb::Matrix::Identity());
      if (transform.IsIdentity()) {
        transform = vrb::Matrix::Rotation(vrb::Vector(1.0f, 0.0f, 0.0f), 0.70f);
        const vrb::Matrix trans = vrb::Matrix::Position(vrb::Vector(0.0f, 0.0f, -0.01f));
        transform = transform.PostMultiply(trans);
      }
      controller.transform = controller.transform.PostMultiply(transform);
    }
    m.delegate->SetTransform(controller.index, controller.transform);
  }

  if ((m.sixDoFControllerCount > 1) && !m.handsCalculated && m.delegate) {
    State::Controller& first = m.controllers[0];
    State::Controller& second = m.controllers[1];
    const vrb::Vector firstPosition = first.transform.GetTranslation();
    const vrb::Vector secondPosition = second.transform.GetTranslation();
    if ((firstPosition - secondPosition).Magnitude() > FLT_EPSILON) {
      const vrb::Vector headPosition = hmd.GetTranslation();
      const vrb::Vector firstDirection = GetDirection(firstPosition, headPosition);
      const vrb::Vector secondDirection = GetDirection(secondPosition, headPosition);
      const vrb::Vector cross = firstDirection.Cross(secondDirection);
      if (cross.Magnitude() > FLT_EPSILON) {
        ElbowModel::HandEnum firstHand = ElbowModel::HandEnum::Right;
        ElbowModel::HandEnum secondHand = ElbowModel::HandEnum::Left;
        if (cross.y() < 0.0f) {
          firstHand = ElbowModel::HandEnum::Left;
          secondHand = ElbowModel::HandEnum::Right;
        }
        if (first.hand != firstHand) {
          VRB_DEBUG("Controller reported as \"%s\" but is actually \"%s\"", HandToString(first.hand),
                    HandToString(firstHand));
          m.delegate->SetLeftHanded(first.index, firstHand == ElbowModel::HandEnum::Left);
          first.hand = firstHand;
        }
        if (second.hand != secondHand) {
          VRB_DEBUG("Controller reported as \"%s\" but is actually \"%s\"", HandToString(second.hand),
                    HandToString(secondHand));
          m.delegate->SetLeftHanded(second.index, secondHand == ElbowModel::HandEnum::Left);
          second.hand = secondHand;
        }
        m.handsCalculated = true;
      }
    }
  }
  mShouldRender = true;
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
DeviceDelegateWaveVR::EndFrame(const FrameEndMode aMode) {
  if (m.currentFBO) {
    m.currentFBO->Unbind();
    m.currentFBO = nullptr;
  }

  m.lastSubmitDiscarded = aMode == DeviceDelegate::FrameEndMode::DISCARD;
  if (m.lastSubmitDiscarded) {
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

vrb::LoadTask DeviceDelegateWaveVR::GetControllerModelTask(int32_t aModelIndex) {
  vrb::RenderContextPtr localContext = m.context.lock();
  if (!localContext) {
    return nullptr;
  }

  return [this, aModelIndex](vrb::CreationContextPtr& aContext) -> vrb::GroupPtr {
      vrb::GroupPtr root = vrb::Group::Create(aContext);
      auto hand = static_cast<ElbowModel::HandEnum>(aModelIndex);

      // Load controller model from SDK
      VRB_LOG("[WaveVR] (%p) Loading internal controller model: %d", this, aModelIndex);
      WVR_DeviceType mCtrlerType = hand == ElbowModel::HandEnum::Left ? WVR_DeviceType_Controller_Left : WVR_DeviceType_Controller_Right;
      {//Critical Section: Clear flag and cached parsed data.
        std::lock_guard<std::mutex> lockGuard(m.mCachedDataMutex[aModelIndex]);
        if (m.modelCachedData[aModelIndex] != nullptr) {
          WVR_ReleaseControllerModel(&m.modelCachedData[aModelIndex]); //we will clear cached data ptr to nullptr.
        }
        m.isModelDataReady[aModelIndex] = false;
      }//Critical Section: Clear flag and cached parsed data.(End)
      //2. Load ctrler model data.
      WVR_Result result = WVR_GetCurrentControllerModel(mCtrlerType, &m.modelCachedData[aModelIndex]);
      if (result == WVR_Success) {
        {//Critical Section: Set data ready flag.
          std::lock_guard<std::mutex> lockGuard(m.mCachedDataMutex[aModelIndex]);
          VRB_LOG("[WaveVR] (%d[%p]) Controller model from the SDK successfully loaded: %d", mCtrlerType, this, hand)
          m.isModelDataReady[aModelIndex] = true;
        }//Critical Section: Set data ready flag.(End)
      } else {
        VRB_LOG("[WaveVR] (%d[%p]): Load fail. Reason(%d)", mCtrlerType, this, result);
      }

      if (m.isModelDataReady[aModelIndex]) {
        timespec start;
        clock_gettime(CLOCK_THREAD_CPUTIME_ID, &start);

        // Initialize textures
        WVR_CtrlerTexBitmapTable_t& comp_Textures = (*m.modelCachedData[aModelIndex]).bitmapInfos;
        uint32_t wvrBitmapSize = comp_Textures.size;
        std::vector<vrb::TextureGLPtr> mTextureTable(wvrBitmapSize);
        VRB_LOG("[WaveVR] (%d[%p]): Initialize WVRTextures(%d)", mCtrlerType, this, wvrBitmapSize);
        for (uint32_t texID = 0; texID < wvrBitmapSize; ++texID) {
          vrb::TextureGLPtr texture = vrb::TextureGL::Create(aContext);
          size_t texture_size = comp_Textures.table[texID].stride * comp_Textures.table[texID].height;
          std::unique_ptr<uint8_t[]> data = std::make_unique<uint8_t[]>(texture_size);
          memcpy(data.get(), (void*)comp_Textures.table[texID].bitmap, texture_size);
          texture->SetImageData(
                  data,
                  texture_size,
                  comp_Textures.table[texID].width,
                  comp_Textures.table[texID].height,
                  GL_RGBA
          );
          mTextureTable[texID] = texture;
        }

        VRB_LOG("[WaveVR] (%d[%p]): Initialize meshes(%d)", mCtrlerType, this, (*m.modelCachedData[aModelIndex]).compInfos.size)
        // Get only the body, we are not using other components right now.
        for (uint32_t wvrCompID = 0; wvrCompID < 1 /*(*m.modelCachedData).compInfos.size*/; ++wvrCompID) {
          char* name = (*m.modelCachedData[aModelIndex]).compInfos.table[wvrCompID].name;
          vrb::VertexArrayPtr array = vrb::VertexArray::Create(aContext);

          // Vertices

          WVR_VertexBuffer_t& comp_Vertices = (*m.modelCachedData[aModelIndex]).compInfos.table[wvrCompID].vertices;
          if (comp_Vertices.buffer == nullptr || comp_Vertices.size == 0 || comp_Vertices.dimension == 0) {
              VRB_LOG("Parameter invalid!!! iData(%p), iSize(%u), iType(%u)", comp_Vertices.buffer, comp_Vertices.size, comp_Vertices.dimension);
              return nullptr;
          }

          uint32_t vertices_dim = comp_Vertices.dimension;
          if (vertices_dim == 3) {
            for (auto i = 0; i < comp_Vertices.size; i+=vertices_dim) {
              auto vertex = vrb::Vector(comp_Vertices.buffer[i],comp_Vertices.buffer[i+1],comp_Vertices.buffer[i+2]);
              array->AppendVertex(vertex);
            }
          } else {
            VRB_ERROR("[WaveVR] (%d[%p]): vertex with wrong dimension: %d", mCtrlerType, this, vertices_dim)
          }

          // Normals

          WVR_VertexBuffer_t& comp_Normals = (*m.modelCachedData[aModelIndex]).compInfos.table[wvrCompID].normals;
          if (comp_Normals.buffer == nullptr || comp_Normals.size == 0 || comp_Normals.dimension == 0) {
              VRB_LOG("Parameter invalid!!! iData(%p), iSize(%u), iType(%u)", comp_Normals.buffer, comp_Normals.size, comp_Normals.dimension);
              return nullptr;
          }

          uint32_t normals_dim = comp_Normals.dimension;
          if (normals_dim == 3) {
            for (auto i = 0; i < comp_Normals.size; i+=normals_dim) {
              auto normal = vrb::Vector(comp_Normals.buffer[i], comp_Normals.buffer[i+1],comp_Normals.buffer[i+2]).Normalize();
              array->AppendNormal(normal);
            }
          } else {
            VRB_ERROR("[WaveVR] (%d[%p]): normal with wrong dimension: %d", mCtrlerType, this, normals_dim)
          }

          WVR_VertexBuffer_t& comp_TextCoord = (*m.modelCachedData[aModelIndex]).compInfos.table[wvrCompID].texCoords;
          if (comp_TextCoord.buffer == nullptr || comp_TextCoord.size == 0 || comp_TextCoord.dimension == 0) {
              VRB_LOG("Parameter invalid!!! iData(%p), iSize(%u), iType(%u)", comp_TextCoord.buffer, comp_TextCoord.size, comp_TextCoord.dimension);
              return nullptr;
          }

          // UVs

          uint32_t texCoords_dim = comp_TextCoord.dimension;
          if (texCoords_dim == 2) {
            for (auto i = 0; i < comp_TextCoord.size; i+=texCoords_dim) {
              auto textCoord = vrb::Vector(comp_TextCoord.buffer[i],comp_TextCoord.buffer[i+1],0);
              array->AppendUV(textCoord);
            }
          } else {
            VRB_ERROR("[WaveVR] (%d[%p]): normal with wrong dimension: %d", mCtrlerType, this, texCoords_dim)
          }

          vrb::ProgramPtr program = aContext->GetProgramFactory()->CreateProgram(aContext, vrb::FeatureTexture);
          vrb::RenderStatePtr state = vrb::RenderState::Create(aContext);
          state->SetProgram(program);
          state->SetMaterial(
                  vrb::Color(1.0f, 1.0f, 1.0f),
                  vrb::Color(1.0f, 1.0f, 1.0f),
                  vrb::Color(0.0f, 0.0f, 0.0f),
                  0.0f);
          state->SetLightsEnabled(false);
          state->SetTexture(mTextureTable[(*m.modelCachedData[aModelIndex]).compInfos.table[wvrCompID].texIndex]);
          vrb::GeometryPtr geometry = vrb::Geometry::Create(aContext);
          geometry->SetName(name);
          geometry->SetVertexArray(array);
          geometry->SetRenderState(state);

          // Indices

          WVR_IndexBuffer_t& comp_Indices = (*m.modelCachedData[aModelIndex]).compInfos.table[wvrCompID].indices;
          if (comp_Indices.buffer == nullptr || comp_Indices.size == 0 || comp_Indices.type == 0) {
              VRB_ERROR("Parameter invalid!!! iData(%p), iSize(%u), iType(%u)", comp_Indices.buffer, comp_Indices.size, comp_Indices.type);
              return nullptr;
          }

          uint32_t type = comp_Indices.type;
          for (auto i = 0; i < comp_Indices.size; i += type) {
            std::vector<int> indices;
            for (auto j = 0; j < type; j++) {
              indices.push_back(comp_Indices.buffer[i+j]+1);
            }
            geometry->AddFace(indices, indices, indices);
          }

          root->AddNode(geometry);
        }

        timespec end;
        clock_gettime(CLOCK_THREAD_CPUTIME_ID, &end);
        float time = (float) end.tv_sec + (((float) end.tv_nsec) / 1.0e9f) - ((float) start.tv_sec + (((float) start.tv_nsec) / 1.0e9f));

        VRB_LOG("[WaveVR] (%d[%p]): Controller loaded in: %f", mCtrlerType, this, time)

        return root;
      } else {
        return nullptr;
      }
  };
}

bool
DeviceDelegateWaveVR::IsRunning() {
  return m.isRunning;
}

bool
DeviceDelegateWaveVR::GotFirstValidPose() {
    return m.gotFirstValidPose;
}

DeviceDelegateWaveVR::DeviceDelegateWaveVR(State& aState) : m(aState) {}
DeviceDelegateWaveVR::~DeviceDelegateWaveVR() {
  m.Shutdown();
  for (auto index=0; index<2; index++) {
    if (m.modelCachedData[index] != nullptr) {
      WVR_ReleaseControllerModel(&m.modelCachedData[index]); //we will clear cached data ptr to nullptr.
    }
  }
}

} // namespace crow
