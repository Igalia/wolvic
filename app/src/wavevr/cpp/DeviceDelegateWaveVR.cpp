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
  uint32_t standaloneFoveatedLevel;

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
  int32_t sixDoFControllerCount;
  bool handsCalculated;
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
      , standaloneFoveatedLevel(0)
      , devicePairs {}
      , controllers {}
      , lastSubmitDiscarded(false)
      , recentered(false)
      , ignoreNextRecenter(false)
      , sixDoFControllerCount(0)
      , handsCalculated(false)
  {
    memset((void*)devicePairs, 0, sizeof(WVR_DevicePosePair_t) * WVR_DEVICE_COUNT_LEVEL_1);
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
    WVR_GetRenderTargetSize(&renderWidth, &renderHeight);
    VRB_GL_CHECK(glViewport(0, 0, renderWidth, renderHeight));
    VRB_DEBUG("Recommended size is %ux%u", renderWidth, renderHeight);
    if (renderWidth == 0 || renderHeight == 0) {
      VRB_ERROR("Please check Wave server configuration");
      return;
    }
    InitializeTextureQueues();
    elbow = ElbowModel::Create();
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

  void UpdateFoveatedLevel() {
    if (!WVR_IsRenderFoveationSupport()) {
      VRB_LOG("This Wave device doesn't support Foveation Render.");
      return;
    }

    if (!leftFBOIndex || !rightFBOIndex) {
      return;
    }

    if (standaloneFoveatedLevel == 0) {
      // This is not working, we have to restart the app to reset.
      WVR_RenderFoveation(false);
    } else {
      WVR_RenderFoveation(true);
      // Mapping foveated level (1~3) to WVR_PeripheralQuality (high~low).
      WVR_PeripheralQuality peripheralQuality =
              static_cast<WVR_PeripheralQuality>(WVR_PeripheralQuality_High-(standaloneFoveatedLevel - 1));

      WVR_RenderFoveationParams_t foveated;
      foveated.focalX = foveated.focalY = 0.0f;
      foveated.fovealFov = foveatedFov;
      foveated.periQuality = peripheralQuality;
      WVR_TextureParams_t eyeTexture = WVR_GetTexture(leftTextureQueue, leftFBOIndex);
      WVR_PreRenderEye(WVR_Eye_Left, &eyeTexture, &foveated);
      eyeTexture = WVR_GetTexture(rightTextureQueue, rightFBOIndex);
      WVR_PreRenderEye(WVR_Eye_Right, &eyeTexture, &foveated);
    }
  }

  void CreateController(Controller& aController) {
    if (!delegate) {
      VRB_ERROR("Failed to create controller. No ControllerDelegate has been set.");
      return;
    }
    vrb::Matrix transform(vrb::Matrix::Identity());
    if (aController.is6DoF) {
      transform.TranslateInPlace(vrb::Vector(0.0f, 0.01f, -0.05f));
    }
    delegate->CreateController(aController.index, aController.is6DoF ? 1 : 0, aController.is6DoF ? "HTC Vive Focus Plus Controller" : "HTC Vive Focus Controller", transform);
    delegate->SetLeftHanded(aController.index, aController.hand == ElbowModel::HandEnum::Left);
    delegate->SetHapticCount(aController.index, 1);
    aController.created = true;
    aController.enabled = false;
  }

  void UpdateControllers() {
    if (!delegate) {
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
        device::CapabilityFlags flags = device::Orientation;
        if (controller.is6DoF) {
          flags |= device::Position;
        }
        controller.enabled = true;
        delegate->SetEnabled(controller.index, true);
        delegate->SetVisible(controller.index, true);
        delegate->SetCapabilityFlags(controller.index, flags);
      }

      delegate->SetVisible(controller.index, !WVR_IsInputFocusCapturedBySystem());

      const bool bumperPressed =  (controller.is6DoF) ? WVR_GetInputButtonState(controller.type, WVR_InputId_Alias1_Trigger)
                                  : WVR_GetInputButtonState(controller.type, WVR_InputId_Alias1_Digital_Trigger);
      const bool touchpadPressed = WVR_GetInputButtonState(controller.type, WVR_InputId_Alias1_Touchpad);
      const bool touchpadTouched = WVR_GetInputTouchState(controller.type, WVR_InputId_Alias1_Touchpad);
      const bool menuPressed = WVR_GetInputButtonState(controller.type, WVR_InputId_Alias1_Menu);

      delegate->SetButtonCount(controller.index, controller.is6DoF ? 3 : 2); // For immersive mode
      delegate->SetButtonState(controller.index, ControllerDelegate::BUTTON_TOUCHPAD, 0, touchpadPressed, touchpadTouched);
      delegate->SetButtonState(controller.index, ControllerDelegate::BUTTON_TRIGGER, 1, bumperPressed, bumperPressed);
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
          delegate->SetButtonState(controller.index, ControllerDelegate::BUTTON_OTHERS, 2, gripPressed,
                                   gripPressed);
          controller.gripPressedCount = 0;
        }
      }

      delegate->SetButtonState(controller.index, ControllerDelegate::BUTTON_APP, -1, menuPressed, menuPressed);

      const int32_t kNumAxes = 2;
      float immersiveAxes[kNumAxes] = { 0.0f, 0.0f };

      if (touchpadTouched) {
        WVR_Axis_t axis = WVR_GetInputAnalogAxis(controller.type, WVR_InputId_Alias1_Touchpad);
        // We are matching touch pad range from {-1, 1} to the Oculus {0, 1}.
        delegate->SetTouchPosition(controller.index, (axis.x + 1) * 0.5, (-axis.y + 1) * 0.5);
        immersiveAxes[0] = axis.x;
        immersiveAxes[1] = -axis.y;
        controller.touched = true;
      } else if (controller.touched) {
        controller.touched = false;
        delegate->EndTouch(controller.index);
      }
      delegate->SetAxes(controller.index, immersiveAxes, kNumAxes);

      UpdateHaptics(controller);
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
  device::CapabilityFlags flags = device::Orientation | device::Present | device::StageParameters |
                                  device::InlineSession | device::ImmersiveVRSession;

  if (WVR_GetDegreeOfFreedom(WVR_DeviceType_HMD) == WVR_NumDoF_6DoF) {
    flags |= device::Position;
  } else {
    flags |= device::PositionEmulated;
  }

  m.immersiveDisplay->SetCapabilityFlags(flags);
  m.immersiveDisplay->SetEyeResolution(m.renderWidth, m.renderHeight);
  m.immersiveDisplay->SetSittingToStandingTransform(vrb::Matrix::Translation(kAverageHeight));
  m.immersiveDisplay->CompleteEnumeration();
  m.InitializeCameras();
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
DeviceDelegateWaveVR::SetFoveatedLevel(const int32_t aAppLevel) {
  m.standaloneFoveatedLevel = aAppLevel;
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

const std::string
DeviceDelegateWaveVR::GetControllerModelName(const int32_t aModelIndex) const {
  if (aModelIndex == 0) {
    return "vr_controller_focus.obj";
  } else if (aModelIndex == 1) {
    return "vr_controller_focus_plus.obj";
  }
  return "";
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
DeviceDelegateWaveVR::StartFrame() {
  VRB_GL_CHECK(glClearColor(m.clearColor.Red(), m.clearColor.Green(), m.clearColor.Blue(), m.clearColor.Alpha()));
  if (!m.lastSubmitDiscarded) {
    m.leftFBOIndex = WVR_GetAvailableTextureIndex(m.leftTextureQueue);
    m.rightFBOIndex = WVR_GetAvailableTextureIndex(m.rightTextureQueue);
    m.UpdateFoveatedLevel();
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
