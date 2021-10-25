/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "DeviceDelegateOculusVR.h"
#include "OculusSwapChain.h"
#include "OculusVRLayers.h"
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
#include <cstdlib>
#include <unistd.h>
#include <VrApi_Types.h>

#include "VrApi.h"
#include "VrApi_Helpers.h"
#include "VrApi_Input.h"
#include "VrApi_SystemUtils.h"
#include "OVR_Platform.h"
#include "OVR_Message.h"

#include "VRBrowser.h"

#define OCULUS_6DOF_APP_ID "2180252408763702"
#define OCULUS_3DOF_APP_ID "2208418715853974"

namespace crow {

const vrb::Vector kAverageHeight(0.0f, 1.7f, 0.0f);
// Height used to match Oculus default in WebVR
const vrb::Vector kAverageOculusHeight(0.0f, 1.65f, 0.0f);

struct DeviceDelegateOculusVR::State {
  struct ControllerState {
    const int32_t index;
    const ElbowModel::HandEnum hand;
    bool enabled = false;
    bool created = false;
    ovrDeviceID deviceId = ovrDeviceIdType_Invalid;
    ovrInputTrackedRemoteCapabilities capabilities = {};
    vrb::Matrix transform = vrb::Matrix::Identity();
    ovrInputStateTrackedRemote inputState = {};
    uint64_t inputFrameID = 0;
    float remainingVibrateTime = 0.0f;
    double lastHapticUpdateTimeStamp = 0.0f;

    bool Is6DOF() const {
      return (capabilities.ControllerCapabilities & ovrControllerCaps_HasPositionTracking) &&
             (capabilities.ControllerCapabilities & ovrControllerCaps_HasOrientationTracking);
    }
    ControllerState(const int32_t aIndex, const ElbowModel::HandEnum aHand) : index(aIndex), hand(aHand) {}
    ControllerState(const ControllerState& controllerStateList) = default;
    ControllerState() = delete;
    ControllerState& operator=(const ControllerState&) = delete;
  };

  vrb::RenderContextWeak context;
  JavaContext* javaContext = nullptr;
  bool initialized = false;
  bool applicationEntitled = false;
  bool layersEnabled = true;
  ovrJava java = {};
  ovrMobile* ovr = nullptr;
  OculusSwapChainPtr eyeSwapChains[VRAPI_EYE_COUNT];
  OculusLayerCubePtr cubeLayer;
  OculusLayerEquirectPtr equirectLayer;
  std::vector<OculusLayerPtr> uiLayers;
  std::vector<OculusLayerProjectionPtr> projectionLayers;
  OculusSwapChainPtr clearColorSwapChain;
  device::RenderMode renderMode = device::RenderMode::StandAlone;
  vrb::FBOPtr currentFBO;
  vrb::FBOPtr previousFBO;
  vrb::CameraEyePtr cameras[2];
  uint32_t frameIndex = 0;
  FramePrediction framePrediction = FramePrediction::NO_FRAME_AHEAD;
  double prevPredictedDisplayTime = 0;
  double predictedDisplayTime = 0;
  ovrTracking2 prevPredictedTracking = {};
  ovrTracking2 predictedTracking = {};
  ovrTracking2 discardPredictedTracking = {};
  uint32_t discardedFrameIndex = 0;
  int discardCount = 0;
  uint32_t renderWidth = 0;
  uint32_t renderHeight = 0;
  vrb::Color clearColor;
  float near = 0.1f;
  float far = 100.f;
  bool hasEventFocus = true;
  std::vector<ControllerState> controllerStateList;
  crow::ElbowModelPtr elbow;
  ControllerDelegatePtr controller;
  ImmersiveDisplayPtr immersiveDisplay;
  int reorientCount = -1;
  vrb::Matrix reorientMatrix = vrb::Matrix::Identity();
  device::CPULevel minCPULevel = device::CPULevel::Normal;
  device::DeviceType deviceType = device::UnknownType;

  void UpdatePerspective() {
    float fovX = vrapi_GetSystemPropertyFloat(&java, VRAPI_SYS_PROP_SUGGESTED_EYE_FOV_DEGREES_X);
    float fovY = vrapi_GetSystemPropertyFloat(&java, VRAPI_SYS_PROP_SUGGESTED_EYE_FOV_DEGREES_Y);

    ovrMatrix4f projection = ovrMatrix4f_CreateProjectionFov(fovX, fovY, 0.0, 0.0, near, far);
    auto matrix = vrb::Matrix::FromRowMajor(projection.M);
    for (int i = 0; i < VRAPI_EYE_COUNT; ++i) {
      cameras[i]->SetPerspective(matrix);
    }

    if (immersiveDisplay) {
      const float fovXHalf = fovX * 0.5f;
      const float fovYHalf = fovY * 0.5f;

      immersiveDisplay->SetFieldOfView(device::Eye::Left, fovXHalf, fovXHalf, fovYHalf, fovYHalf);
      immersiveDisplay->SetFieldOfView(device::Eye::Right, fovXHalf, fovXHalf, fovYHalf, fovYHalf);
    }
  }

  void Initialize() {
    elbow = ElbowModel::Create();
    vrb::RenderContextPtr localContext = context.lock();

    java.Vm = javaContext->vm;
    java.Vm->AttachCurrentThread(&java.Env, nullptr);
    java.ActivityObject = java.Env->NewGlobalRef(javaContext->activity);

    // Initialize the API.
    auto parms = vrapi_DefaultInitParms(&java);
    auto status = vrapi_Initialize(&parms);
    if (status != VRAPI_INITIALIZE_SUCCESS) {
      VRB_LOG("Failed to initialize VrApi!. Error: %d", status);
      exit(status);
      return;
    }
    initialized = true;

    std::string version = vrapi_GetVersionString();
    std::string notes = "Oculus Driver Version: ";
    notes += version;
    VRBrowser::AppendAppNotesToCrashLog(notes);

    layersEnabled = VRBrowser::AreLayersEnabled();
    SetRenderSize(device::RenderMode::StandAlone);

    auto render = context.lock();
    for (int i = 0; i < VRAPI_EYE_COUNT; ++i) {
      cameras[i] = vrb::CameraEye::Create(localContext->GetRenderThreadCreationContext());
    }
    UpdatePerspective();

    reorientCount = vrapi_GetSystemStatusInt(&java, VRAPI_SYS_STATUS_RECENTER_COUNT);

    // This needs to be set to 0 so that the volume buttons work. I'm not sure why since the
    // docs in the header indicate that setting this to false (0) means you have to
    // handle the gamepad events yourself.
    vrapi_SetPropertyInt(&java, VRAPI_EAT_NATIVE_GAMEPAD_EVENTS, 0);

    const char * appId = OCULUS_6DOF_APP_ID;

    const int type = vrapi_GetSystemPropertyInt(&java, VRAPI_SYS_PROP_DEVICE_TYPE);
    if ((type >= VRAPI_DEVICE_TYPE_OCULUSQUEST_START) && (type <= VRAPI_DEVICE_TYPE_OCULUSQUEST_END)) {
      VRB_DEBUG("Detected Oculus Quest");
      deviceType = device::OculusQuest;
    } else if ((type >= VRAPI_DEVICE_TYPE_OCULUSQUEST2_START) && (type <= VRAPI_DEVICE_TYPE_OCULUSQUEST2_END)) {
        VRB_DEBUG("Detected Oculus Quest 2");
        deviceType = device::OculusQuest;
    } else {
      VRB_DEBUG("Detected Unknown Oculus device");
    }

    if (!ovr_IsPlatformInitialized()) {
      ovrRequest result = ovr_PlatformInitializeAndroidAsynchronous(appId, java.ActivityObject,
                                                                    java.Env);

      if (invalidRequestID == result) {
        // Initialization failed which means either the oculus service isn’t on the machine or they’ve hacked their DLL.
        VRB_LOG("ovr_PlatformInitializeAndroidAsynchronous failed: %d", (int32_t) result);
#if STORE_BUILD == 1
        VRBrowser::HaltActivity(0);
#endif
      } else {
        VRB_LOG("ovr_PlatformInitializeAndroidAsynchronous succeeded");
        ovr_Entitlement_GetIsViewerEntitled();
      }
    } else if (!applicationEntitled) {
      ovr_Entitlement_GetIsViewerEntitled();
    }
  }

  vrb::FBO::Attributes RenderModeAttributes() {
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

  void UpdateTrackingMode() {
    if (ovr) {
      vrapi_SetTrackingSpace(ovr, VRAPI_TRACKING_SPACE_LOCAL);
    }
  }

  void UpdateClockLevels() {
    if (!ovr) {
      return;
    }

    if (renderMode == device::RenderMode::StandAlone && minCPULevel == device::CPULevel::Normal) {
      vrapi_SetClockLevels(ovr, 2, 2);
    } else {
      vrapi_SetClockLevels(ovr, 4, 4);
    }
  }

  void UpdateDisplayRefreshRate() {
    if (!ovr || !IsOculusGo()) {
      return;
    }
    if (renderMode == device::RenderMode::StandAlone) {
      vrapi_SetDisplayRefreshRate(ovr, 72.0f);
    } else {
      vrapi_SetDisplayRefreshRate(ovr, 60.0f);
    }
  }

  void UpdateBoundary() {
    if (!ovr || !Is6DOF()) {
      return;
    }
    ovrPosef pose;
    ovrVector3f size;
    vrapi_GetBoundaryOrientedBoundingBox(ovr, &pose, &size);
    if (immersiveDisplay) {
      immersiveDisplay->SetStageSize(size.x * 2.0f, size.z * 2.0f);
    }
  }

  void AddUILayer(const OculusLayerPtr& aLayer, VRLayerSurface::SurfaceType aSurfaceType) {
    if (ovr) {
      vrb::RenderContextPtr ctx = context.lock();
      aLayer->Init(java.Env, ctx);
    }
    uiLayers.push_back(aLayer);
    if (aSurfaceType == VRLayerSurface::SurfaceType::FBO) {
      aLayer->SetBindDelegate([=](const OculusSwapChainPtr& aSwapChain, GLenum aTarget, bool bound){
        if (aSwapChain) {
          HandleLayerBind(aSwapChain, aTarget, bound);
        }
      });
      if (currentFBO) {
        currentFBO->Bind();
      }
    }
  }

  void GetImmersiveRenderSize(uint32_t& aWidth, uint32_t& aHeight) {
    aWidth = (uint32_t)(vrapi_GetSystemPropertyInt(&java, VRAPI_SYS_PROP_SUGGESTED_EYE_TEXTURE_WIDTH));
    aHeight = (uint32_t)(vrapi_GetSystemPropertyInt(&java, VRAPI_SYS_PROP_SUGGESTED_EYE_TEXTURE_HEIGHT));
  }

  void GetStandaloneRenderSize(uint32_t& aWidth, uint32_t& aHeight) {
    const float scale = layersEnabled ? 1.0f : 1.5f;
    aWidth = (uint32_t)(scale * vrapi_GetSystemPropertyInt(&java, VRAPI_SYS_PROP_SUGGESTED_EYE_TEXTURE_WIDTH));
    aHeight = (uint32_t)(scale * vrapi_GetSystemPropertyInt(&java, VRAPI_SYS_PROP_SUGGESTED_EYE_TEXTURE_HEIGHT));
  }

  bool IsOculusQuest() const {
    return deviceType == device::OculusQuest;
  }

  bool IsOculusGo() const {
    return deviceType == device::OculusGo;
  }

  bool Is6DOF() const {
    // ovrInputHeadsetCapabilities is unavailable in Oculus mobile SDK,
    // the current workaround is checking if it is Quest.
    return IsOculusQuest();
  }

  void SetRenderSize(device::RenderMode aRenderMode) {
    if (renderMode == device::RenderMode::StandAlone) {
      GetStandaloneRenderSize(renderWidth, renderHeight);
    } else {
      GetImmersiveRenderSize(renderWidth, renderHeight);
    }
  }

  void Shutdown() {
    // Shutdown Oculus mobile SDK
    if (initialized) {
      vrapi_Shutdown();
      initialized = false;
      controllerStateList.clear();
    }

    // Release activity reference
    if (java.ActivityObject) {
      java.Env->DeleteGlobalRef(java.ActivityObject);
      java = {};
    }
  }

  int32_t FindControllerIndex(const ElbowModel::HandEnum aHand) {
    int32_t found = -1;
    for (ControllerState& controller: controllerStateList) {
      if (controller.hand == aHand) {
        found = controller.index;
      }
    }

    if (found < 0) {
      found = (int32_t)controllerStateList.size();
      controllerStateList.emplace_back(ControllerState(found, aHand));
    }
    return found;
  }

  void UpdateDeviceId() {
    if (!controller || !ovr) {
      return;
    }

    for (ControllerState& controllerState: controllerStateList) {
      controllerState.enabled = false;
    }

    if (!hasEventFocus) {
      return;
    }

    uint32_t count = 0;
    ovrInputCapabilityHeader capsHeader = {};
    while (vrapi_EnumerateInputDevices(ovr, count++, &capsHeader) >= 0) {
      // We are only interested in the remote controller input device
      if (capsHeader.Type == ovrControllerType_TrackedRemote) {
        ovrInputTrackedRemoteCapabilities caps = {};
        caps.Header = capsHeader;
        ovrResult result = vrapi_GetInputDeviceCapabilities(ovr, &caps.Header);
        if (result != ovrSuccess) {
          VRB_LOG("vrapi_GetInputDeviceCapabilities failed with error: %d", result);
          continue;
        }
        const int32_t index = FindControllerIndex(caps.ControllerCapabilities & ovrControllerCaps_LeftHand ? ElbowModel::HandEnum::Left : ElbowModel::HandEnum::Right);
        if ((index < 0) || index >= (controllerStateList.size())) {
          continue;
        }
        ControllerState& controllerState = controllerStateList[index];
        if ((controllerState.deviceId != ovrDeviceIdType_Invalid) &&
            (controllerState.deviceId != capsHeader.DeviceID)) {
          VRB_DEBUG("%s handed controller DeviceID has changed from %u to %u",
                    (controllerState.hand == ElbowModel::HandEnum::Left ? "Left" : "Right"),
                    controllerState.deviceId, capsHeader.DeviceID);
        }
        controllerState.deviceId = capsHeader.DeviceID;
        controllerState.capabilities = caps;
        controllerState.enabled = true;

        if (!controllerState.created) {
          vrb::Matrix beamTransform(vrb::Matrix::Identity());
          if (controllerState.capabilities.ControllerCapabilities &
              ovrControllerCaps_ModelOculusTouch) {
            std::string controllerName;
            if (controllerState.hand == ElbowModel::HandEnum::Left) {
              beamTransform.TranslateInPlace(vrb::Vector(-0.011f, -0.007f, 0.0f));
              controllerName = "Oculus Touch (Left)";
            } else {
              beamTransform.TranslateInPlace(vrb::Vector(0.011f, -0.007f, 0.0f));
              controllerName = "Oculus Touch (Right)";
            }
            controller->CreateController(controllerState.index, int32_t(controllerState.hand),
                                         controllerName, beamTransform);
            controller->SetButtonCount(controllerState.index, 7);
            controller->SetHapticCount(controllerState.index, 1);
            controller->SetControllerType(controllerState.index, device::OculusQuest);

            const vrb::Matrix trans = vrb::Matrix::Position(vrb::Vector(0.0f, 0.02f, -0.03f));
            vrb::Matrix transform = vrb::Matrix::Rotation(vrb::Vector(1.0f, 0.0f, 0.0f), -0.77f);
            transform = transform.PostMultiply(trans);
            controller->SetImmersiveBeamTransform(controllerState.index, beamTransform.PostMultiply(transform));
          } else {
            // Oculus Go only has one kind of controller model.
            controller->CreateController(controllerState.index, 0, "Oculus Go Controller");
            // Although Go only has two buttons, in order to match WebXR input profile (squeeze placeholder),
            // we make Go has three buttons.
            controller->SetButtonCount(controllerState.index, 3);
            // Oculus Go has no haptic feedback.
            controller->SetHapticCount(controllerState.index, 0);
            controller->SetControllerType(controllerState.index, device::OculusGo);

            const vrb::Matrix trans = vrb::Matrix::Position(vrb::Vector(0.0f, 0.028f, -0.072f));
            vrb::Matrix transform = vrb::Matrix::Rotation(vrb::Vector(1.0f, 0.0f, 0.0f), -0.55f);
            transform = transform.PostMultiply(trans);
            controller->SetImmersiveBeamTransform(controllerState.index, beamTransform.PostMultiply(transform));
          }
          controller->SetTargetRayMode(controllerState.index, device::TargetRayMode::TrackedPointer);
          controllerState.created = true;
        }
      }
    }
    for (ControllerState& controllerState: controllerStateList) {
      controller->SetLeftHanded(controllerState.index, controllerState.hand == ElbowModel::HandEnum::Left);
      controller->SetEnabled(controllerState.index, controllerState.enabled);
    }
  }

  void UpdateControllers(const vrb::Matrix & head) {
    UpdateDeviceId();
    if (!controller) {
      return;
    }

    if (!hasEventFocus) {
      return;
    }

    for (ControllerState& controllerState: controllerStateList) {
      if (controllerState.deviceId == ovrDeviceIdType_Invalid) {
        continue;
      }
      ovrTracking tracking = {};
      if (vrapi_GetInputTrackingState(ovr, controllerState.deviceId, predictedDisplayTime, &tracking) != ovrSuccess) {
        VRB_LOG("Failed to read controller tracking controllerStateList");
        continue;
      }

      device::CapabilityFlags flags = 0;
      if (controllerState.capabilities.ControllerCapabilities & ovrControllerCaps_HasOrientationTracking) {
        auto &orientation = tracking.HeadPose.Pose.Orientation;
        vrb::Quaternion quat(orientation.x, orientation.y, orientation.z, orientation.w);
        controllerState.transform = vrb::Matrix::Rotation(quat);
        flags |= device::Orientation;
      }

      if (controllerState.capabilities.ControllerCapabilities & ovrControllerCaps_HasPositionTracking) {
        auto & position = tracking.HeadPose.Pose.Position;
        vrb::Vector headPos(position.x, position.y, position.z);
        if (renderMode == device::RenderMode::StandAlone) {
          headPos += kAverageHeight;
        }
        controllerState.transform.TranslateInPlace(headPos);
        flags |= device::Position;
      } else {
        controllerState.transform = elbow->GetTransform(controllerState.hand, head, controllerState.transform);
        flags |= device::PositionEmulated;
      }

      flags |= device::GripSpacePosition;
      controller->SetCapabilityFlags(controllerState.index, flags);
      if (renderMode == device::RenderMode::Immersive) {
        static vrb::Matrix transform(vrb::Matrix::Identity());
        if (transform.IsIdentity()) {
          if (controllerState.Is6DOF()) {
            transform = vrb::Matrix::Rotation(vrb::Vector(1.0f, 0.0f, 0.0f), 0.77f);
            const vrb::Matrix trans = vrb::Matrix::Position(vrb::Vector(0.0f, 0.0f, 0.025f));
            transform = transform.PostMultiply(trans);
          } else {
            transform = vrb::Matrix::Rotation(vrb::Vector(1.0f, 0.0f, 0.0f), 0.60f);
          }
        }
        controllerState.transform = controllerState.transform.PostMultiply(transform);
      }
      controller->SetTransform(controllerState.index, controllerState.transform);

      controllerState.inputState.Header.ControllerType = ovrControllerType_TrackedRemote;
      vrapi_GetCurrentInputState(ovr, controllerState.deviceId, &controllerState.inputState.Header);

      int32_t level = controllerState.inputState.BatteryPercentRemaining;
      if (!IsOculusGo()) {
        float value = (float)level / 100.0f;
        level = (int)std::round((value * value) * 10.0f) * 10;
      }
      controller->SetBatteryLevel(controllerState.index,level);

      reorientCount = controllerState.inputState.RecenterCount;
      bool triggerPressed = false, triggerTouched = false;
      bool trackpadPressed = false, trackpadTouched = false;
      float trackpadX = 0.0f, trackpadY = 0.0f;
      if (controllerState.Is6DOF()) {
        triggerPressed = (controllerState.inputState.Buttons & ovrButton_Trigger) != 0;
        triggerTouched = (controllerState.inputState.Touches & ovrTouch_IndexTrigger) != 0;
        trackpadPressed = (controllerState.inputState.Buttons & ovrButton_Joystick) != 0;
        trackpadTouched = (controllerState.inputState.Touches & ovrTouch_Joystick) != 0;
        trackpadX = controllerState.inputState.Joystick.x;
        trackpadY = controllerState.inputState.Joystick.y;
        const int32_t kNumAxes = 4;
        float axes[kNumAxes];
        axes[device::kImmersiveAxisTouchpadX] = axes[device::kImmersiveAxisTouchpadY] = 0.0f;
        axes[device::kImmersiveAxisThumbstickX] = trackpadX;
        axes[device::kImmersiveAxisThumbstickY] = -trackpadY; // We did y axis intentionally inverted in FF desktop as well.
        controller->SetScrolledDelta(controllerState.index, -trackpadX, trackpadY);

        const bool gripPressed = (controllerState.inputState.Buttons & ovrButton_GripTrigger) != 0;
        controller->SetButtonState(controllerState.index, ControllerDelegate::BUTTON_SQUEEZE, device::kImmersiveButtonSqueeze,
                gripPressed, gripPressed, controllerState.inputState.GripTrigger);
        if (controllerState.hand == ElbowModel::HandEnum::Left) {
          const bool xPressed = (controllerState.inputState.Buttons & ovrButton_X) != 0;
          const bool xTouched = (controllerState.inputState.Touches & ovrTouch_X) != 0;
          const bool yPressed = (controllerState.inputState.Buttons & ovrButton_Y) != 0;
          const bool yTouched = (controllerState.inputState.Touches & ovrTouch_Y) != 0;
          const bool menuPressed = (controllerState.inputState.Buttons & ovrButton_Enter) != 0;

          controller->SetButtonState(controllerState.index, ControllerDelegate::BUTTON_X, device::kImmersiveButtonA, xPressed, xTouched);
          controller->SetButtonState(controllerState.index, ControllerDelegate::BUTTON_Y, device::kImmersiveButtonB, yPressed, yTouched);

          if (renderMode != device::RenderMode::Immersive) {
            controller->SetButtonState(controllerState.index, ControllerDelegate::BUTTON_APP, -1, yPressed, yTouched);
          } else {
            controller->SetButtonState(controllerState.index, ControllerDelegate::BUTTON_APP, -1, menuPressed, menuPressed);
          }
        } else if (controllerState.hand == ElbowModel::HandEnum::Right) {
          const bool aPressed = (controllerState.inputState.Buttons & ovrButton_A) != 0;
          const bool aTouched = (controllerState.inputState.Touches & ovrTouch_A) != 0;
          const bool bPressed = (controllerState.inputState.Buttons & ovrButton_B) != 0;
          const bool bTouched = (controllerState.inputState.Touches & ovrTouch_B) != 0;

          controller->SetButtonState(controllerState.index, ControllerDelegate::BUTTON_A, device::kImmersiveButtonA, aPressed, aTouched);
          controller->SetButtonState(controllerState.index, ControllerDelegate::BUTTON_B, device::kImmersiveButtonB, bPressed, bTouched);

          if (renderMode != device::RenderMode::Immersive) {
            controller->SetButtonState(controllerState.index, ControllerDelegate::BUTTON_APP, -1, bPressed, bTouched);
          }
        } else {
          VRB_WARN("Undefined hand type in DeviceDelegateOculusVR.");
        }
        controller->SetButtonState(controllerState.index, ControllerDelegate::BUTTON_TOUCHPAD,
                                   device::kImmersiveButtonThumbstick, trackpadPressed, trackpadTouched);
        // This is always false in Oculus Browser.
        const bool thumbRest = false;
        controller->SetButtonState(controllerState.index, ControllerDelegate::BUTTON_OTHERS, device::kImmersiveButtonThumbrest, thumbRest, thumbRest);

        if (gripPressed && renderMode == device::RenderMode::Immersive) {
          controller->SetSqueezeActionStart(controllerState.index);
        } else {
          controller->SetSqueezeActionStop(controllerState.index);
        }
        controller->SetAxes(controllerState.index, axes, kNumAxes);
      } else {
        triggerPressed = (controllerState.inputState.Buttons & ovrButton_A) != 0;
        triggerTouched = triggerPressed;
        trackpadPressed = (controllerState.inputState.Buttons & ovrButton_Enter) != 0;
        trackpadTouched = (bool)controllerState.inputState.TrackpadStatus;

        // For Oculus Go, by setting vrapi_SetPropertyInt(&java, VRAPI_EAT_NATIVE_GAMEPAD_EVENTS, 0);
        // The app will receive onBackPressed when the back button is pressed on the controller.
        // So there is no need to check for it here. Leaving code commented out for reference
        // in the case that the back button stops working again due to Oculus Mobile API change.
        // const bool backPressed = (inputState.Buttons & ovrButton_Back) != 0;
        // controller->SetButtonState(0, ControllerDelegate::BUTTON_APP, -1, backPressed, backPressed);
        trackpadX = controllerState.inputState.TrackpadPosition.x / (float)controllerState.capabilities.TrackpadMaxX;
        trackpadY = controllerState.inputState.TrackpadPosition.y / (float)controllerState.capabilities.TrackpadMaxY;

        controller->SetButtonState(controllerState.index, ControllerDelegate::BUTTON_TOUCHPAD,
                device::kImmersiveButtonTouchpad, trackpadPressed, trackpadTouched);
        if (trackpadTouched && !trackpadPressed) {
          controller->SetTouchPosition(controllerState.index, trackpadX, trackpadY);
        } else {
          controller->SetTouchPosition(controllerState.index, trackpadX, trackpadY);
          controller->EndTouch(controllerState.index);
        }
        const int32_t kNumAxes = 2;
        float axes[kNumAxes];
        axes[device::kImmersiveAxisTouchpadX] = trackpadTouched ? trackpadX * 2.0f - 1.0f : 0.0f;
        axes[device::kImmersiveAxisTouchpadY] = trackpadTouched ? trackpadY * 2.0f - 1.0f : 0.0f;
        controller->SetAxes(controllerState.index, axes, kNumAxes);
      }
      controller->SetButtonState(controllerState.index, ControllerDelegate::BUTTON_TRIGGER,
                                 device::kImmersiveButtonTrigger, triggerPressed, triggerTouched,
                                 controllerState.inputState.IndexTrigger);

      if (triggerPressed && renderMode == device::RenderMode::Immersive) {
        controller->SetSelectActionStart(controllerState.index);
      } else {
        controller->SetSelectActionStop(controllerState.index);
      }
      if (controller->GetHapticCount(controllerState.index)) {
        UpdateHaptics(controllerState);
      }
    }
  }

  void UpdateHaptics(ControllerState& controllerState) {
    vrb::RenderContextPtr renderContext = context.lock();
    if (!renderContext) {
      return;
    }
    if (!controller || !ovr) {
      return;
    }

    uint64_t inputFrameID = 0;
    float pulseDuration = 0.0f, pulseIntensity = 0.0f;
    controller->GetHapticFeedback(controllerState.index, inputFrameID, pulseDuration, pulseIntensity);
    if (inputFrameID > 0 && pulseIntensity > 0.0f && pulseDuration > 0) {
      if (controllerState.inputFrameID != inputFrameID) {
        // When there is a new input frame id from haptic vibration,
        // that means we start a new session for a vibration.
        controllerState.inputFrameID = inputFrameID;
        controllerState.remainingVibrateTime = pulseDuration;
        controllerState.lastHapticUpdateTimeStamp = renderContext->GetTimestamp();
      } else {
        // We are still running the previous vibration.
        // So, it needs to reduce the delta time from the last vibration.
        const double timeStamp = renderContext->GetTimestamp();
        controllerState.remainingVibrateTime -= (timeStamp - controllerState.lastHapticUpdateTimeStamp);
        controllerState.lastHapticUpdateTimeStamp = timeStamp;
      }

      if (controllerState.remainingVibrateTime > 0.0f && renderMode == device::RenderMode::Immersive) {
        if (vrapi_SetHapticVibrationSimple(ovr, controllerState.deviceId, pulseIntensity > 1.0f ? 1.0f : pulseIntensity)
            == ovrError_InvalidOperation) {
          VRB_ERROR("vrapi_SetHapticVibrationBuffer failed.");
        }
      } else {
        // The remaining time is zero or exiting the immersive mode, stop the vibration.
        if (vrapi_SetHapticVibrationSimple(ovr, controllerState.deviceId, 0.0f) == ovrError_InvalidOperation) {
          VRB_ERROR("vrapi_SetHapticVibrationBuffer failed.");
        }
        controllerState.remainingVibrateTime = 0.0f;
      }
    } else if (controllerState.remainingVibrateTime > 0.0f) {
      // While the haptic feedback is terminated from the client side,
      // but it still have remaining time, we need to ask for stopping vibration.
      if (vrapi_SetHapticVibrationSimple(ovr, controllerState.deviceId, 0.0f) == ovrError_InvalidOperation) {
        VRB_ERROR("vrapi_SetHapticVibrationBuffer failed.");
      }
      controllerState.remainingVibrateTime = 0.0f;
    }
  }

  void HandleLayerBind(const OculusSwapChainPtr& aSwapChain, GLenum aTarget, bool bound) {
    const int32_t swapChainIndex = frameIndex % aSwapChain->SwapChainLength();
    vrb::FBOPtr targetFBO = aSwapChain->FBO(swapChainIndex);

    if (!bound) {
      if (currentFBO && currentFBO == targetFBO) {
        currentFBO->Unbind();
        currentFBO = nullptr;
      }
      if (previousFBO) {
        previousFBO->Bind();
        currentFBO = previousFBO;
        previousFBO = nullptr;
      }
      return;
    }

    if (currentFBO == targetFBO) {
      // Layer already bound
      return;
    }

    if (currentFBO) {
      currentFBO->Unbind();
    }
    previousFBO = currentFBO;
    targetFBO->Bind(aTarget);
    currentFBO = targetFBO;
  }

  OculusSwapChainPtr CreateClearColorSwapChain(const float aWidth, const float aHeight) {
    vrb::RenderContextPtr render = context.lock();

    vrb::FBO::Attributes attributes;
    attributes.depth = false;
    attributes.samples = 0;

    vrb::Color clearColor(1.0f, 1.0f, 1.0f, 1.0f);

    OculusSwapChainPtr result = OculusSwapChain::CreateFBO(render, attributes, aWidth, aHeight, false, clearColor);

    // Add a transparent border (required for Oculus Layers)
    VRB_GL_CHECK(glBindTexture(GL_TEXTURE_2D, result->TextureHandle(0)));
    VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER_EXT));
    VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER_EXT));
    float border[] = { 0.0f, 0.0f, 0.0f, 0.0f };
    VRB_GL_CHECK(glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR_EXT, border));
    VRB_GL_CHECK(glBindTexture(GL_TEXTURE_2D, 0));

    return result;
  }
};

DeviceDelegateOculusVRPtr
DeviceDelegateOculusVR::Create(vrb::RenderContextPtr& aContext, JavaContext* aJavaContext) {
  DeviceDelegateOculusVRPtr result = std::make_shared<vrb::ConcreteClass<DeviceDelegateOculusVR, DeviceDelegateOculusVR::State> >();
  result->m.context = aContext;
  result->m.javaContext = aJavaContext;
  result->m.Initialize();
  return result;
}

device::DeviceType
DeviceDelegateOculusVR::GetDeviceType() {
  return m.deviceType;
}

void
DeviceDelegateOculusVR::SetRenderMode(const device::RenderMode aMode) {
  if (aMode == m.renderMode) {
    return;
  }
  m.renderMode = aMode;
  m.SetRenderSize(aMode);
  vrb::RenderContextPtr render = m.context.lock();
  for (int i = 0; i < VRAPI_EYE_COUNT; ++i) {
    m.eyeSwapChains[i] = OculusSwapChain::CreateFBO(render, m.RenderModeAttributes(), m.renderWidth, m.renderHeight);
  }

  m.UpdateTrackingMode();
  m.UpdateDisplayRefreshRate();
  m.UpdateClockLevels();

  // Reset reorient when exiting or entering immersive
  m.reorientMatrix = vrb::Matrix::Identity();
}

device::RenderMode
DeviceDelegateOculusVR::GetRenderMode() {
  return m.renderMode;
}

void
DeviceDelegateOculusVR::RegisterImmersiveDisplay(ImmersiveDisplayPtr aDisplay) {
  m.immersiveDisplay = std::move(aDisplay);

  if (!m.immersiveDisplay) {
    return;
  }

  if (m.IsOculusQuest()) {
    m.immersiveDisplay->SetDeviceName("Oculus Quest");
  } else {
    m.immersiveDisplay->SetDeviceName("Oculus Go");
  }
  uint32_t width, height;
  m.GetImmersiveRenderSize(width, height);
  m.immersiveDisplay->SetEyeResolution(width, height);
  // The maxScaleFactor that can be applied to the recommended render size.
  // Oculus Browser uses a 1.68 maxScale factor.
  // We didn't find a Oculus API to get the 1.68 ratio without using hardcoded values.
  // We use the 4K resolution value (4096 pixels wide and 2048 per eye).
  const float maxScaleFactor = 2048.0f / width;
  m.immersiveDisplay->SetNativeFramebufferScaleFactor(maxScaleFactor);
  m.immersiveDisplay->SetSittingToStandingTransform(vrb::Matrix::Translation(kAverageOculusHeight));
  m.UpdateBoundary();
  m.UpdatePerspective();

  m.immersiveDisplay->CompleteEnumeration();
}

void
DeviceDelegateOculusVR::SetImmersiveSize(const uint32_t aEyeWidth, const uint32_t aEyeHeight) {
  uint32_t recommendedWidth, recommendedHeight;
  m.GetImmersiveRenderSize(recommendedWidth, recommendedHeight);

  uint32_t targetWidth = m.renderWidth;
  uint32_t targetHeight = m.renderHeight;

  DeviceUtils::GetTargetImmersiveSize(aEyeWidth, aEyeHeight, recommendedWidth, recommendedHeight, targetWidth, targetHeight);
  if (targetWidth != m.renderWidth || targetHeight != m.renderHeight) {
    m.renderWidth = targetWidth;
    m.renderHeight = targetHeight;
    vrb::RenderContextPtr render = m.context.lock();
    for (int i = 0; i < VRAPI_EYE_COUNT; ++i) {
      m.eyeSwapChains[i] = OculusSwapChain::CreateFBO(render, m.RenderModeAttributes(), m.renderWidth, m.renderHeight);
    }
    VRB_LOG("Resize immersive mode swapChain: %dx%d", targetWidth, targetHeight);
  }
}

vrb::CameraPtr
DeviceDelegateOculusVR::GetCamera(const device::Eye aWhich) {
  const int32_t index = device::EyeIndex(aWhich);
  if (index < 0) { return nullptr; }
  return m.cameras[index];
}

const vrb::Matrix&
DeviceDelegateOculusVR::GetHeadTransform() const {
  return m.cameras[0]->GetHeadTransform();
}

const vrb::Matrix&
DeviceDelegateOculusVR::GetReorientTransform() const {
  return m.reorientMatrix;
}

void
DeviceDelegateOculusVR::SetReorientTransform(const vrb::Matrix& aMatrix) {
  m.reorientMatrix = aMatrix;
}

void
DeviceDelegateOculusVR::SetClearColor(const vrb::Color& aColor) {
  m.clearColor = aColor;
}

void
DeviceDelegateOculusVR::SetClipPlanes(const float aNear, const float aFar) {
  m.near = aNear;
  m.far = aFar;
  m.UpdatePerspective();
}

void
DeviceDelegateOculusVR::SetControllerDelegate(ControllerDelegatePtr& aController) {
  m.controller = aController;
}

void
DeviceDelegateOculusVR::ReleaseControllerDelegate() {
  m.controller = nullptr;
}

int32_t
DeviceDelegateOculusVR::GetControllerModelCount() const {
  if (m.IsOculusQuest()) {
    return 2;
  } else {
    return 1;
  }
}

const std::string
DeviceDelegateOculusVR::GetControllerModelName(const int32_t aModelIndex) const {
  static std::string name = "";

  if (m.IsOculusQuest()) {
    switch (aModelIndex) {
      case 0:
        name = "vr_controller_oculusquest_left.obj";
        break;
      case 1:
        name = "vr_controller_oculusquest_right.obj";
        break;
      default:
        VRB_WARN("GetControllerModelName() failed.");
        name = "";
        break;
    }
  } else {
    name = "vr_controller_oculusgo.obj";
  }

  return name;
}


void
DeviceDelegateOculusVR::SetCPULevel(const device::CPULevel aLevel) {
  m.minCPULevel = aLevel;
  m.UpdateClockLevels();
};

void
DeviceDelegateOculusVR::ProcessEvents() {
  ovrEventDataBuffer eventDataBuffer = {};
  ovrEventHeader* eventHeader = (ovrEventHeader*)(&eventDataBuffer);
  // Poll for VrApi events at regular frequency
  while (vrapi_PollEvent(eventHeader) == ovrSuccess) {

    switch (eventHeader->EventType) {
      case VRAPI_EVENT_FOCUS_GAINED:
        // FOCUS_GAINED is sent when the application is in the foreground and has
        // input focus. This may be due to a system overlay relinquishing focus
        // back to the application.
        m.hasEventFocus = true;
        if (m.controller) {
          m.controller->SetVisible(true);
        }
        break;
      case VRAPI_EVENT_FOCUS_LOST:
        // FOCUS_LOST is sent when the application is no longer in the foreground and
        // therefore does not have input focus. This may be due to a system overlay taking
        // focus from the application. The application should take appropriate action when
        // this occurs.
        m.hasEventFocus = false;
        if (m.controller) {
          m.controller->SetVisible(false);
        }
        break;
      default:
        break;
    }
  }

  ovrMessageHandle message;
  while ((message = ovr_PopMessage()) != nullptr) {
    switch (ovr_Message_GetType(message)) {
      case ovrMessage_PlatformInitializeAndroidAsynchronous: {
        ovrPlatformInitializeHandle handle = ovr_Message_GetPlatformInitialize(message);
        ovrPlatformInitializeResult result = ovr_PlatformInitialize_GetResult(handle);
        if (result == ovrPlatformInitialize_Success) {
          VRB_DEBUG("OVR Platform Initialized.");
        } else {
          VRB_ERROR("OVR Platform Initialize failed: %s", ovrPlatformInitializeResult_ToString(result));
#if STORE_BUILD == 1
          VRBrowser::HaltActivity(0);
#endif
        }
      }
        break;
      case ovrMessage_Entitlement_GetIsViewerEntitled:
        if (ovr_Message_IsError(message)) {
          VRB_LOG("User is not entitled");
#if STORE_BUILD == 1
          VRBrowser::HaltActivity(0);
#else
          // No need to process events anymore.
          m.applicationEntitled = true;
#endif
        }
        else {
          VRB_LOG("User is entitled");
          m.applicationEntitled = true;
        }
        break;
      case ovrMessage_Notification_ApplicationLifecycle_LaunchIntentChanged: {
        ovrLaunchDetailsHandle details = ovr_ApplicationLifecycle_GetLaunchDetails();
        if (ovr_LaunchDetails_GetLaunchType(details) == ovrLaunchType_Deeplink) {
          const char* msg = ovr_LaunchDetails_GetDeeplinkMessage(details);
          if (msg) {
            // FIXME see https://github.com/MozillaReality/FirefoxReality/issues/3066
            // Currently handled in VRBrowserActivity.loadFromIntent()
            // VRBrowser::OnAppLink(msg);
          }
        }
        break;
      }
      default:
        break;
    }
  }
}

bool
DeviceDelegateOculusVR::SupportsFramePrediction(FramePrediction aPrediction) const {
  return true;
}

void
DeviceDelegateOculusVR::StartFrame(const FramePrediction aPrediction) {
  if (!m.ovr) {
    VRB_LOG("StartFrame called while not in VR mode");
    return;
  }

  m.framePrediction = aPrediction;
  m.frameIndex++;
  if (aPrediction == FramePrediction::ONE_FRAME_AHEAD) {
    m.prevPredictedDisplayTime = m.predictedDisplayTime;
    m.prevPredictedTracking = m.predictedTracking;
    m.predictedDisplayTime = vrapi_GetPredictedDisplayTime(m.ovr, m.frameIndex + 1);
  } else {
    m.predictedDisplayTime = vrapi_GetPredictedDisplayTime(m.ovr, m.frameIndex);
  }

  m.predictedTracking = vrapi_GetPredictedTracking2(m.ovr, m.predictedDisplayTime);

  float ipd = vrapi_GetInterpupillaryDistance(&m.predictedTracking);
  m.cameras[VRAPI_EYE_LEFT]->SetEyeTransform(vrb::Matrix::Translation(vrb::Vector(-ipd * 0.5f, 0.f, 0.f)));
  m.cameras[VRAPI_EYE_RIGHT]->SetEyeTransform(vrb::Matrix::Translation(vrb::Vector(ipd * 0.5f, 0.f, 0.f)));

  if (!(m.predictedTracking.Status & VRAPI_TRACKING_STATUS_HMD_CONNECTED)) {
    VRB_LOG("HMD not connected");
    return;
  }

  ovrMatrix4f matrix = vrapi_GetTransformFromPose(&m.predictedTracking.HeadPose.Pose);
  vrb::Matrix head = vrb::Matrix::FromRowMajor(matrix.M[0]);

  if (m.renderMode == device::RenderMode::StandAlone) {
    head.TranslateInPlace(kAverageHeight);
  }

  m.cameras[VRAPI_EYE_LEFT]->SetHeadTransform(head);
  m.cameras[VRAPI_EYE_RIGHT]->SetHeadTransform(head);

  if (m.immersiveDisplay) {
    m.immersiveDisplay->SetEyeOffset(device::Eye::Left, -ipd * 0.5f, 0.f, 0.f);
    m.immersiveDisplay->SetEyeOffset(device::Eye::Right, ipd * 0.5f, 0.f, 0.f);
    device::CapabilityFlags caps = device::Orientation | device::Present |
                                   device::InlineSession | device::ImmersiveVRSession;
    if (m.predictedTracking.Status & VRAPI_TRACKING_STATUS_POSITION_TRACKED) {
      caps |= device::Position | device::StageParameters;
      auto standing = vrapi_LocateTrackingSpace(m.ovr, VRAPI_TRACKING_SPACE_LOCAL_FLOOR);
      vrb::Vector translation(-standing.Position.x, -standing.Position.y, -standing.Position.z);
      m.immersiveDisplay->SetSittingToStandingTransform(vrb::Matrix::Translation(translation));
    } else {
      caps |= device::PositionEmulated;
    }
    m.immersiveDisplay->SetCapabilityFlags(caps);
  }

  int lastReorientCount = m.reorientCount;
  m.UpdateControllers(head);
  bool reoriented = lastReorientCount != m.reorientCount && lastReorientCount > 0 && m.reorientCount > 0;
  if (reoriented && m.renderMode == device::RenderMode::StandAlone) {
    m.reorientMatrix = DeviceUtils::CalculateReorientationMatrix(head, kAverageHeight);
  }

  VRB_GL_CHECK(glClearColor(m.clearColor.Red(), m.clearColor.Green(), m.clearColor.Blue(), m.clearColor.Alpha()));
}

void
DeviceDelegateOculusVR::BindEye(const device::Eye aWhich) {
  if (!m.ovr) {
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

  auto &swapChain = m.eyeSwapChains[index];

  int swapChainIndex = m.frameIndex % swapChain->SwapChainLength();
  m.currentFBO = swapChain->FBO(swapChainIndex);
  if (!m.currentFBO || !m.currentFBO->IsValid()) {
    // See https://github.com/MozillaReality/FirefoxReality/issues/3712
    // There are some crash reports of invalid eye SwapChain FBOs. Try to recreate it.
    VRB_LOG("Recreate SwapChain because no valid FBO was found");
    auto render = m.context.lock();
    swapChain = OculusSwapChain::CreateFBO(render, m.RenderModeAttributes(), m.renderWidth, m.renderHeight);
    m.currentFBO = swapChain->FBO(swapChainIndex);
  }

  if (m.currentFBO) {
    m.currentFBO->Bind();
    VRB_GL_CHECK(glViewport(0, 0, m.renderWidth, m.renderHeight));
    VRB_GL_CHECK(glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT));
  } else {
    VRB_LOG("No Swap chain FBO found");
  }

  for (const OculusLayerPtr& layer: m.uiLayers) {
    layer->SetCurrentEye(aWhich);
  }
}

void
DeviceDelegateOculusVR::EndFrame(const FrameEndMode aEndMode) {
  if (!m.ovr) {
    VRB_LOG("EndFrame called while not in VR mode");
    return;
  }
  if (m.currentFBO) {
    m.currentFBO->Unbind();
    m.currentFBO.reset();
  }

  const bool frameAhead = m.framePrediction == FramePrediction::ONE_FRAME_AHEAD;
  const ovrTracking2& tracking = frameAhead ? m.prevPredictedTracking : m.predictedTracking;
  const double displayTime = frameAhead ? m.prevPredictedDisplayTime : m.predictedDisplayTime;

  if (aEndMode == FrameEndMode::DISCARD) {
    // Reuse the last frame when a frame is discarded.
    // The last frame is timewarped by the VR compositor.
    if (m.discardCount == 0) {
      m.discardPredictedTracking = tracking;
      m.discardedFrameIndex = m.frameIndex;
    }
    m.discardCount++;
    m.frameIndex = m.discardedFrameIndex;
    m.predictedTracking = m.discardPredictedTracking;
  } else {
    m.discardCount = 0;
  }

  uint32_t layerCount = 0;
  const ovrLayerHeader2* layers[ovrMaxLayerCount] = {};

  if (m.cubeLayer && m.cubeLayer->IsLoaded() && m.cubeLayer->IsDrawRequested()) {
    m.cubeLayer->Update(m.frameIndex, tracking, m.clearColorSwapChain->SwapChain());
    layers[layerCount++] = m.cubeLayer->Header();
    m.cubeLayer->ClearRequestDraw();
  }

  if (m.equirectLayer && m.equirectLayer->IsDrawRequested()) {
    m.equirectLayer->Update(m.frameIndex, tracking, m.clearColorSwapChain->SwapChain());
    layers[layerCount++] = m.equirectLayer->Header();
    m.equirectLayer->ClearRequestDraw();
  }

  const float fovX = vrapi_GetSystemPropertyFloat(&m.java, VRAPI_SYS_PROP_SUGGESTED_EYE_FOV_DEGREES_X);
  const float fovY = vrapi_GetSystemPropertyFloat(&m.java, VRAPI_SYS_PROP_SUGGESTED_EYE_FOV_DEGREES_Y);
  const ovrMatrix4f projectionMatrix = ovrMatrix4f_CreateProjectionFov(fovX, fovY, 0.0f, 0.0f, VRAPI_ZNEAR, 0.0f);

  // Add projection layers
  for (const OculusLayerProjectionPtr& layer: m.projectionLayers) {
    if (layer->IsDrawRequested() && (layerCount < ovrMaxLayerCount - 1)) {
      layer->SetProjectionMatrix(projectionMatrix);
      layer->Update(m.frameIndex, tracking, m.clearColorSwapChain->SwapChain());
      layers[layerCount++] = layer->Header();
      layer->ClearRequestDraw();
    }
  }
  // Sort quad layers by draw priority
  std::sort(m.uiLayers.begin(), m.uiLayers.end(), [](const OculusLayerPtr & a, OculusLayerPtr & b) -> bool {
    return a->GetLayer()->ShouldDrawBefore(*b->GetLayer());
  });

  // Draw back layers
  for (const OculusLayerPtr& layer: m.uiLayers) {
    if (!layer->GetDrawInFront() && layer->IsDrawRequested() && (layerCount < ovrMaxLayerCount - 1)) {
      layer->Update(m.frameIndex, tracking, m.clearColorSwapChain->SwapChain());
      layers[layerCount++] = layer->Header();
      layer->ClearRequestDraw();
    }
  }

  // Add main eye buffer layer
  ovrLayerProjection2 projection = vrapi_DefaultLayerProjection2();
  projection.HeadPose = tracking.HeadPose;
  projection.Header.SrcBlend = VRAPI_FRAME_LAYER_BLEND_SRC_ALPHA;
  projection.Header.DstBlend = VRAPI_FRAME_LAYER_BLEND_ONE_MINUS_SRC_ALPHA;
  for (int i = 0; i < VRAPI_FRAME_LAYER_EYE_MAX; ++i) {
    const auto &eyeSwapChain = m.eyeSwapChains[i];
    const int swapChainIndex = m.frameIndex % eyeSwapChain->SwapChainLength();
    // Set up OVR layer textures
    projection.Textures[i].ColorSwapChain = eyeSwapChain->SwapChain();
    projection.Textures[i].SwapChainIndex = swapChainIndex;
    projection.Textures[i].TexCoordsFromTanAngles = ovrMatrix4f_TanAngleMatrixFromProjection(
        &projectionMatrix);
  }
  layers[layerCount++] = &projection.Header;

  // Draw front layers
  for (const OculusLayerPtr& layer: m.uiLayers) {
    if (layer->GetDrawInFront() && layer->IsDrawRequested() && layerCount < ovrMaxLayerCount) {
      layer->Update(m.frameIndex, tracking, m.clearColorSwapChain->SwapChain());
      layers[layerCount++] = layer->Header();
      layer->ClearRequestDraw();
    }
  }

  // Submit all layers to TimeWarp
  ovrSubmitFrameDescription2 frameDesc = {};
  frameDesc.Flags = 0;
  if (m.renderMode == device::RenderMode::Immersive) {
    frameDesc.Flags |= VRAPI_FRAME_FLAG_INHIBIT_VOLUME_LAYER;
  }
  frameDesc.SwapInterval = 1;
  frameDesc.FrameIndex = m.frameIndex;
  frameDesc.DisplayTime = displayTime;

  frameDesc.LayerCount = layerCount;
  frameDesc.Layers = layers;

  vrapi_SubmitFrame2(m.ovr, &frameDesc);
}

VRLayerQuadPtr
DeviceDelegateOculusVR::CreateLayerQuad(int32_t aWidth, int32_t aHeight,
                                        VRLayerSurface::SurfaceType aSurfaceType) {
  if (!m.layersEnabled) {
    return nullptr;
  }
  VRLayerQuadPtr layer = VRLayerQuad::Create(aWidth, aHeight, aSurfaceType);
  OculusLayerQuadPtr oculusLayer = OculusLayerQuad::Create(m.java.Env, layer);
  m.AddUILayer(oculusLayer, aSurfaceType);
  return layer;
}

VRLayerQuadPtr
DeviceDelegateOculusVR::CreateLayerQuad(const VRLayerSurfacePtr& aMoveLayer) {
  if (!m.layersEnabled) {
    return nullptr;
  }

  VRLayerQuadPtr layer = VRLayerQuad::Create(aMoveLayer->GetWidth(), aMoveLayer->GetHeight(), aMoveLayer->GetSurfaceType());
  OculusLayerQuadPtr oculusLayer;

  for (int i = 0; i < m.uiLayers.size(); ++i) {
    if (m.uiLayers[i]->GetLayer() == aMoveLayer) {
      oculusLayer = OculusLayerQuad::Create(m.java.Env, layer, m.uiLayers[i]);
      m.uiLayers.erase(m.uiLayers.begin() + i);
      break;
    }
  }
  if (oculusLayer) {
    m.AddUILayer(oculusLayer, aMoveLayer->GetSurfaceType());
  }
  return layer;
}

VRLayerCylinderPtr
DeviceDelegateOculusVR::CreateLayerCylinder(int32_t aWidth, int32_t aHeight,
                                            VRLayerSurface::SurfaceType aSurfaceType) {
  if (!m.layersEnabled) {
    return nullptr;
  }
  VRLayerCylinderPtr layer = VRLayerCylinder::Create(aWidth, aHeight, aSurfaceType);
  OculusLayerCylinderPtr oculusLayer = OculusLayerCylinder::Create(m.java.Env, layer);
  m.AddUILayer(oculusLayer, aSurfaceType);
  return layer;
}

VRLayerCylinderPtr
DeviceDelegateOculusVR::CreateLayerCylinder(const VRLayerSurfacePtr& aMoveLayer) {
  if (!m.layersEnabled) {
    return nullptr;
  }

  VRLayerCylinderPtr layer = VRLayerCylinder::Create(aMoveLayer->GetWidth(), aMoveLayer->GetHeight(), aMoveLayer->GetSurfaceType());
  OculusLayerCylinderPtr oculusLayer;

  for (int i = 0; i < m.uiLayers.size(); ++i) {
    if (m.uiLayers[i]->GetLayer() == aMoveLayer) {
      oculusLayer = OculusLayerCylinder::Create(m.java.Env, layer, m.uiLayers[i]);
      m.uiLayers.erase(m.uiLayers.begin() + i);
      break;
    }
  }
  if (oculusLayer) {
    m.AddUILayer(oculusLayer, aMoveLayer->GetSurfaceType());
  }
  return layer;
}

VRLayerProjectionPtr
DeviceDelegateOculusVR::CreateLayerProjection(crow::VRLayerSurface::SurfaceType aSurfaceType) {
  if (!m.layersEnabled) {
    return nullptr;
  }
  uint32_t width = 0;
  uint32_t height = 0;
  m.GetStandaloneRenderSize(width, height);
  VRLayerProjectionPtr layer = VRLayerProjection::Create(width, height, aSurfaceType);
  OculusLayerProjectionPtr oculusLayer = OculusLayerProjection::Create(m.java.Env, layer);
  oculusLayer->SetBindDelegate([=](const OculusSwapChainPtr& aSwapChain, GLenum aTarget, bool bound){
    if (aSwapChain) {
      m.HandleLayerBind(aSwapChain, aTarget, bound);
    }
  });
  m.projectionLayers.push_back(oculusLayer);

  return layer;
}


VRLayerCubePtr
DeviceDelegateOculusVR::CreateLayerCube(int32_t aWidth, int32_t aHeight, GLint aInternalFormat) {
  if (!m.layersEnabled) {
    return nullptr;
  }
  if (m.cubeLayer) {
    m.cubeLayer->Destroy();
  }
  VRLayerCubePtr layer = VRLayerCube::Create(aWidth, aHeight, aInternalFormat);
  m.cubeLayer = OculusLayerCube::Create(layer, aInternalFormat);
  if (m.ovr) {
    vrb::RenderContextPtr context = m.context.lock();
    m.cubeLayer->Init(m.java.Env, context);
  }
  return layer;
}

VRLayerEquirectPtr
DeviceDelegateOculusVR::CreateLayerEquirect(const VRLayerPtr &aSource) {
  VRLayerEquirectPtr result = VRLayerEquirect::Create();
  OculusLayerPtr source;
  for (const OculusLayerPtr& layer: m.uiLayers) {
    if (layer->GetLayer() == aSource) {
      source = layer;
      break;
    }
  }
  if (m.equirectLayer) {
    m.equirectLayer->Destroy();
  }
  m.equirectLayer = OculusLayerEquirect::Create(result, source);
  if (m.ovr) {
    vrb::RenderContextPtr context = m.context.lock();
    m.equirectLayer->Init(m.java.Env, context);
  }
  return result;
}

void
DeviceDelegateOculusVR::DeleteLayer(const VRLayerPtr& aLayer) {
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
  for (int i = 0; i < m.projectionLayers.size(); ++i) {
    if (m.projectionLayers[i]->GetLayer() == aLayer) {
      m.projectionLayers[i]->Destroy();
      m.projectionLayers.erase(m.projectionLayers.begin() + i);
      return;
    }
  }
}

void
DeviceDelegateOculusVR::EnterVR(const crow::BrowserEGLContext& aEGLContext) {
  if (m.ovr) {
    return;
  }

  if (!m.clearColorSwapChain) {
      m.clearColorSwapChain = m.CreateClearColorSwapChain(800, 450);
  }

  vrb::RenderContextPtr render = m.context.lock();
  for (int i = 0; i < VRAPI_EYE_COUNT; ++i) {
    m.eyeSwapChains[i] = OculusSwapChain::CreateFBO(render, m.RenderModeAttributes(), m.renderWidth, m.renderHeight);
  }
  vrb::RenderContextPtr context = m.context.lock();
  for (OculusLayerProjectionPtr& layer: m.projectionLayers) {
    layer->Init(m.java.Env, context);
  }
  for (OculusLayerPtr& layer: m.uiLayers) {
    layer->Init(m.java.Env, context);
  }
  if (m.cubeLayer) {
    m.cubeLayer->Init(m.java.Env, context);
  }
  if (m.equirectLayer) {
    m.equirectLayer->Init(m.java.Env, context);
  }

  ovrModeParms modeParms = vrapi_DefaultModeParms(&m.java);
  modeParms.Flags |= VRAPI_MODE_FLAG_NATIVE_WINDOW;
  // No need to reset the FLAG_FULLSCREEN window flag when using a View
  modeParms.Flags &= ~VRAPI_MODE_FLAG_RESET_WINDOW_FULLSCREEN;
  modeParms.Display = reinterpret_cast<unsigned long long>(aEGLContext.Display());
  modeParms.WindowSurface = reinterpret_cast<unsigned long long>(aEGLContext.NativeWindow());
  modeParms.ShareContext = reinterpret_cast<unsigned long long>(aEGLContext.Context());

  m.ovr = vrapi_EnterVrMode(&modeParms);

  if (!m.ovr) {
    VRB_LOG("Entering VR mode failed");
  } else {
    vrapi_SetPerfThread(m.ovr, VRAPI_PERF_THREAD_TYPE_MAIN, gettid());
    vrapi_SetPerfThread(m.ovr, VRAPI_PERF_THREAD_TYPE_RENDERER, gettid());
    m.UpdateDisplayRefreshRate();
    m.UpdateClockLevels();
    m.UpdateTrackingMode();
    m.UpdateBoundary();
  }

  // Reset reorientation after Enter VR
  m.reorientMatrix = vrb::Matrix::Identity();
}

void
DeviceDelegateOculusVR::LeaveVR() {
  if (m.ovr) {
    vrapi_LeaveVrMode(m.ovr);
    m.ovr = nullptr;
  }
  m.currentFBO = nullptr;
  m.previousFBO = nullptr;
}

void
DeviceDelegateOculusVR::OnDestroy() {
  for (OculusLayerPtr& layer: m.uiLayers) {
    layer->Destroy();
  }
  for (OculusLayerProjectionPtr& layer: m.projectionLayers) {
    layer->Destroy();
  }
  for (int i = 0; i < VRAPI_EYE_COUNT; ++i) {
    m.eyeSwapChains[i]->Destroy();
  }
  if (m.cubeLayer) {
    m.cubeLayer->Destroy();
  }
  if (m.equirectLayer) {
    m.equirectLayer->Destroy();
  }

  m.clearColorSwapChain = nullptr;
}

bool
DeviceDelegateOculusVR::IsInVRMode() const {
  return m.ovr != nullptr;
}

bool
DeviceDelegateOculusVR::ExitApp() {
  vrapi_ShowSystemUI(&m.java, VRAPI_SYS_UI_CONFIRM_QUIT_MENU);
  return true;
}

DeviceDelegateOculusVR::DeviceDelegateOculusVR(State &aState) : m(aState) {}

DeviceDelegateOculusVR::~DeviceDelegateOculusVR() { m.Shutdown(); }

} // namespace crow
