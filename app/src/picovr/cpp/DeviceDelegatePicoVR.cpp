/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "DeviceDelegatePicoVR.h"
#include "DeviceUtils.h"
#include "ElbowModel.h"
#include "VRBrowserPico.h"

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

#include <array>
#include <vector>
#include <cstdlib>
#include <unistd.h>


namespace crow {

static const vrb::Vector kAverageHeight(0.0f, 1.7f, 0.0f);
// TODO: Get real sitting to standing transform when SDK provides it
static const vrb::Vector kAverageSittingToStanding(0.0f, 1.2f, 0.0f);
// TODO: support different controllers & buttons
static const int32_t kMaxControllerCount = 3;
static const int32_t kNumButtons = 7;
static const int32_t kNumG2Buttons = 3;
static const int32_t kNumGazeButtons = 1;
static const int32_t k6DofHeadSet = 1;
static const int32_t kButtonApp       = 1;
static const int32_t kButtonTrigger   = 1 << 1;
static const int32_t kButtonTouchPad  = 1 << 2;
static const int32_t kButtonAX        = 1 << 3;
static const int32_t kButtonBY        = 1 << 4;
static const int32_t kButtonGrip      = 1 << 5;

struct DeviceDelegatePicoVR::State {
  struct Controller {
    int32_t index;
    bool created;
    bool enabled;
    bool touched;
    bool is6DoF;
    vrb::Matrix transform;
    int32_t  buttonsState;
    float grip = 0.0f;
    float axisX = 0;
    float axisY = 0;
    ElbowModel::HandEnum hand;
    int hapticFrameID = 0;
    int battery = -1;
    Controller()
        : index(-1)
        , created(false)
        , enabled(false)
        , touched(false)
        , is6DoF(false)
        , transform(vrb::Matrix::Identity())
        , hand(ElbowModel::HandEnum::Right)
    {}

    bool IsRightHand() const {
      return hand == ElbowModel::HandEnum::Right;
    }
  };
  vrb::RenderContextWeak context;
  bool initialized = false;
  bool paused = false;
  int32_t type = 0;
  device::RenderMode renderMode = device::RenderMode::StandAlone;
  bool setHeadOffset = true;
  vrb::Vector headOffset;
  vrb::CameraEyePtr cameras[2];
  uint32_t renderWidth = 0;
  uint32_t renderHeight = 0;
  vrb::Color clearColor;
  float near = 0.1f;
  float far = 100.f;
  std::array<Controller, kMaxControllerCount> controllers = {};
  crow::ElbowModelPtr elbow;
  ControllerDelegatePtr controllerDelegate;
  ImmersiveDisplayPtr immersiveDisplay;
  vrb::Matrix reorientMatrix = vrb::Matrix::Identity();
  vrb::Quaternion orientation;
  vrb::Vector position;
  float ipd = 0.064f;
  float fov = (float) (51.0 * M_PI / 180.0);
  int32_t focusIndex = 0;
  bool recentered = false;
  bool isInGazeMode = false;
  int32_t gazeIndex = -1;

  void Initialize() {
    vrb::RenderContextPtr localContext = context.lock();

    vrb::CreationContextPtr create = localContext->GetRenderThreadCreationContext();
    cameras[device::EyeIndex(device::Eye::Left)] = vrb::CameraEye::Create(create);
    cameras[device::EyeIndex(device::Eye::Right)] = vrb::CameraEye::Create(create);
    UpdatePerspective();
    UpdateEyeTransform();

    gazeIndex = VRBrowserPico::GetGazeIndex();

    for (int32_t index = 0; index < kMaxControllerCount; index++) {
      controllers[index].index = index;
      if (index == 0) {
        controllers[index].hand = ElbowModel::HandEnum::Right;
      } else {
        controllers[index].hand = ElbowModel::HandEnum::Left;
      }
      controllers[index].is6DoF = true;
    }

    elbow = ElbowModel::Create();
    initialized = true;
  }

  void Shutdown() {
    initialized = false;
  }

  void UpdatePerspective() {
    vrb::Matrix projection = vrb::Matrix::PerspectiveMatrix(fov, fov, fov, fov, near, far);
    cameras[0]->SetPerspective(projection);
    cameras[1]->SetPerspective(projection);

    if (immersiveDisplay) {
      const float fovDegrees = fov * (float) (180.0 / M_PI);
      immersiveDisplay->SetFieldOfView(device::Eye::Left, fovDegrees, fovDegrees, fovDegrees,
                                       fovDegrees);
      immersiveDisplay->SetFieldOfView(device::Eye::Right, fovDegrees, fovDegrees, fovDegrees,
                                       fovDegrees);
    }
  }

  void UpdateEyeTransform() {
    cameras[0]->SetEyeTransform(vrb::Matrix::Translation(vrb::Vector(-ipd * 0.5f, 0.f, 0.f)));
    cameras[1]->SetEyeTransform(vrb::Matrix::Translation(vrb::Vector(ipd * 0.5f, 0.f, 0.f)));

    if (immersiveDisplay) {
      immersiveDisplay->SetEyeOffset(device::Eye::Left, -ipd * 0.5f, 0.f, 0.f);
      immersiveDisplay->SetEyeOffset(device::Eye::Right, ipd * 0.5f, 0.f, 0.f);
    }
  }

  void UpdateHaptics(Controller& aController) {
    uint64_t inputFrameID = 0;
    float pulseDuration = 0.0f, pulseIntensity = 0.0f;
    controllerDelegate->GetHapticFeedback(aController.index, inputFrameID, pulseDuration, pulseIntensity);

    if (aController.hapticFrameID != inputFrameID) {
      VRBrowserPico::UpdateHaptics(aController.index, pulseIntensity, pulseDuration);
    }
  }

  void UpdateControllers() {
    for (int32_t i = 0; i < controllers.size(); ++i) {
      if (!controllers[i].enabled) {
        continue;
      }
      auto& controller = controllers[i];
      device::CapabilityFlags flags = device::Orientation;
      if (controller.is6DoF) {
        flags |= device::Position;
      } else {
        flags |= device::PositionEmulated;
      }
      flags |= device::GripSpacePosition;
      controllerDelegate->SetCapabilityFlags(i, flags);
      const bool appPressed = (controller.buttonsState & kButtonApp) > 0;
      const bool triggerPressed = (controller.buttonsState & kButtonTrigger) > 0;
      const bool touchPadPressed = (controller.buttonsState & kButtonTouchPad) > 0;
      const bool axPressed = (controller.buttonsState & kButtonAX) > 0;
      const bool byPressed = (controller.buttonsState & kButtonBY) > 0;
      const bool gripPressed = (controller.buttonsState & kButtonGrip) > 0;

      controllerDelegate->SetButtonState(i, ControllerDelegate::BUTTON_APP, -1, appPressed,
                                         appPressed);
      controllerDelegate->SetButtonState(i, ControllerDelegate::BUTTON_TOUCHPAD, type == k6DofHeadSet ?
          device::kImmersiveButtonThumbstick : device::kImmersiveButtonTouchpad, touchPadPressed, touchPadPressed);
      controllerDelegate->SetButtonState(i, ControllerDelegate::BUTTON_TRIGGER, device::kImmersiveButtonTrigger, triggerPressed,
                                         triggerPressed);
      if (triggerPressed && renderMode == device::RenderMode::Immersive) {
        controllerDelegate->SetSelectActionStart(i);
      } else {
        controllerDelegate->SetSelectActionStop(i);
      }
      if (type == k6DofHeadSet) {
        controllerDelegate->SetButtonState(i, ControllerDelegate::BUTTON_SQUEEZE, device::kImmersiveButtonSqueeze, gripPressed,
                                           gripPressed, gripPressed ? 20.0f : 0.0f);
        if (gripPressed && renderMode == device::RenderMode::Immersive) {
          controllerDelegate->SetSqueezeActionStart(i);
        } else {
          controllerDelegate->SetSqueezeActionStop(i);
        }
        controllerDelegate->SetButtonState(i,
                                           (controller.IsRightHand() ? ControllerDelegate::BUTTON_A
                                                                     : ControllerDelegate::BUTTON_X),
                                           device::kImmersiveButtonA, axPressed, axPressed);
        controllerDelegate->SetButtonState(i,
                                           (controller.IsRightHand() ? ControllerDelegate::BUTTON_B
                                                                     : ControllerDelegate::BUTTON_Y),
                                           device::kImmersiveButtonB, byPressed, byPressed);
        controllerDelegate->SetButtonState(i, ControllerDelegate::BUTTON_OTHERS, device::kImmersiveButtonThumbrest, false, false);

        const int32_t kNumAxes = 4;
        float axes[kNumAxes];
        axes[device::kImmersiveAxisTouchpadX] = axes[device::kImmersiveAxisTouchpadY] = 0.0f;
        axes[device::kImmersiveAxisThumbstickX] = controller.axisX;
        axes[device::kImmersiveAxisThumbstickY] = -controller.axisY;
        controllerDelegate->SetAxes(i, axes, controller.index != GazeModeIndex() ? kNumAxes : 0);

        if (!triggerPressed) {
          controllerDelegate->SetScrolledDelta(i, -controller.axisX, controller.axisY);
        }
      } else {
        const int32_t kNumAxes = 2;
        float axes[kNumAxes] = { 0.0f, 0.0f };
        if (controller.touched) {
          axes[device::kImmersiveAxisTouchpadX] = (2.0f * controller.axisX) - 1.0f;
          axes[device::kImmersiveAxisTouchpadY] = (2.0f * controller.axisY) - 1.0f;
        }
        controllerDelegate->SetAxes(i, axes, controller.index != GazeModeIndex() ? kNumAxes : 0);

        if (controller.touched) {
          controllerDelegate->SetTouchPosition(i, controller.axisX, controller.axisY);
        } else {
          controllerDelegate->EndTouch(i);
        }
      }

      vrb::Matrix transform = controller.transform;
      if (i != gazeIndex) {
          if (renderMode == device::RenderMode::StandAlone) {
              if (type == k6DofHeadSet) {
                  transform.TranslateInPlace(headOffset);
              } else {
                  vrb::Matrix head = vrb::Matrix::Rotation(orientation);
                  head.PreMultiplyInPlace(vrb::Matrix::Position(headOffset));
                  transform = elbow->GetTransform(controller.hand, head, transform);
              }
          }
          else if (type != k6DofHeadSet) {
              vrb::Matrix head = vrb::Matrix::Rotation(orientation);
              transform = elbow->GetTransform(controller.hand, head, transform);
          }
      }

      controllerDelegate->SetTransform(i, transform);

      controllerDelegate->SetBatteryLevel(i, controller.battery);

      if (controllerDelegate->GetHapticCount(i)) {
        UpdateHaptics(controllers[i]);
      }
    }
  }

  int32_t GazeModeIndex() {
    return gazeIndex;
  }
};

DeviceDelegatePicoVRPtr
DeviceDelegatePicoVR::Create(vrb::RenderContextPtr& aContext) {
  DeviceDelegatePicoVRPtr result = std::make_shared<vrb::ConcreteClass<DeviceDelegatePicoVR, DeviceDelegatePicoVR::State> >();
  result->m.context = aContext;
  result->m.Initialize();
  return result;
}


void
DeviceDelegatePicoVR::SetRenderMode(const device::RenderMode aMode) {
  if (aMode == m.renderMode) {
    return;
  }
  m.renderMode = aMode;
  if (aMode == device::RenderMode::StandAlone) {
    // Ensure that all haptics are cancelled when exiting WebVR
    VRBrowserPico::CancelAllHaptics();
  }
}

device::RenderMode
DeviceDelegatePicoVR::GetRenderMode() {
  return m.renderMode;
}

void
DeviceDelegatePicoVR::RegisterImmersiveDisplay(ImmersiveDisplayPtr aDisplay) {
  m.immersiveDisplay = std::move(aDisplay);

  if (!m.immersiveDisplay) {
    return;
  }

  m.immersiveDisplay->SetDeviceName("Pico");
  device::CapabilityFlags flags = device::Orientation | device::Present |
          device::ImmersiveVRSession | device::InlineSession;
  if (m.type == k6DofHeadSet) {
    flags |= device::Position | device::StageParameters;
    m.immersiveDisplay->SetSittingToStandingTransform(vrb::Matrix::Translation(kAverageSittingToStanding));
  } else {
    flags |=  device::PositionEmulated;
    m.immersiveDisplay->SetSittingToStandingTransform(vrb::Matrix::Translation(kAverageHeight));
  }
  m.immersiveDisplay->SetCapabilityFlags(flags);
  const float scale = 1.0f;
  m.immersiveDisplay->SetEyeResolution(int(m.renderWidth * scale), int(m.renderHeight * scale));
  m.immersiveDisplay->CompleteEnumeration();
}

vrb::CameraPtr
DeviceDelegatePicoVR::GetCamera(const device::Eye aWhich) {
  const int32_t index = device::EyeIndex(aWhich);
  if (index < 0) { return nullptr; }
  return m.cameras[index];
}

const vrb::Matrix&
DeviceDelegatePicoVR::GetHeadTransform() const {
  return m.cameras[0]->GetHeadTransform();
}

const vrb::Matrix&
DeviceDelegatePicoVR::GetReorientTransform() const {
  return m.reorientMatrix;
}

void
DeviceDelegatePicoVR::SetReorientTransform(const vrb::Matrix& aMatrix) {
  m.reorientMatrix = aMatrix;
}

void
DeviceDelegatePicoVR::SetClearColor(const vrb::Color& aColor) {
  m.clearColor = aColor;
}

void
DeviceDelegatePicoVR::SetClipPlanes(const float aNear, const float aFar) {
  m.near = aNear;
  m.far = aFar;
  m.UpdatePerspective();
}

void
DeviceDelegatePicoVR::SetControllerDelegate(ControllerDelegatePtr& aController) {
  m.controllerDelegate = aController;
  for (State::Controller& controller: m.controllers) {
    const int32_t index = controller.index;

    if (index == m.gazeIndex) {
      vrb::Matrix beam = vrb::Matrix::Identity();
      m.controllerDelegate->CreateController(index, 0, "Pico Gaze Controller", beam);
      m.controllerDelegate->SetButtonCount(index, kNumGazeButtons);
      m.controllerDelegate->SetHapticCount(index, 0);
      m.controllerDelegate->SetControllerType(index, device::PicoGaze);
      m.controllerDelegate->SetTargetRayMode(index, device::TargetRayMode::Gaze);
    } else {
      if (m.type == k6DofHeadSet) {
        vrb::Matrix beam = vrb::Matrix::Rotation(vrb::Vector(1.0f, 0.0f, 0.0f), -vrb::PI_FLOAT / 11.5f);
        beam.TranslateInPlace(vrb::Vector(0.0f, 0.012f, -0.06f));
        m.controllerDelegate->CreateController(index, int32_t(controller.hand), controller.IsRightHand() ? "Pico Neo 2 (Right)" : "Pico Neo 2 (Left)", beam);
        m.controllerDelegate->SetButtonCount(index, kNumButtons);
        m.controllerDelegate->SetHapticCount(index, 1);
        m.controllerDelegate->SetControllerType(index, device::PicoNeo2);
        m.controllerDelegate->SetTargetRayMode(index, device::TargetRayMode::TrackedPointer);
      } else {
        vrb::Matrix beam =  vrb::Matrix::Rotation(vrb::Vector(1.0f, 0.0f, 0.0f), -vrb::PI_FLOAT / 11.5f);
        m.controllerDelegate->CreateController(index, 0, "Pico G2 Controller", beam);
        m.controllerDelegate->SetButtonCount(index, kNumG2Buttons);
        m.controllerDelegate->SetHapticCount(index, 0);
        m.controllerDelegate->SetControllerType(index, device::PicoG2);
        m.controllerDelegate->SetTargetRayMode(index, device::TargetRayMode::TrackedPointer);
      }
    }
    controller.created = true;
  }
}

void
DeviceDelegatePicoVR::ReleaseControllerDelegate() {
  m.controllerDelegate = nullptr;
}

int32_t
DeviceDelegatePicoVR::GetControllerModelCount() const {
  return m.type == k6DofHeadSet ? 2 : 1;
}

const std::string
DeviceDelegatePicoVR::GetControllerModelName(const int32_t aModelIndex) const {
  if (m.type == k6DofHeadSet) {
    if (aModelIndex == 0) {
      return "neo2_left.obj";
    } else if (aModelIndex == 1) {
      return "neo2_right.obj";
    }
    return "";
  } else {
    return "g2.obj";
  }
}

void
DeviceDelegatePicoVR::ProcessEvents() {

}

void
DeviceDelegatePicoVR::StartFrame(const FramePrediction aPrediction) {
  vrb::Matrix head = vrb::Matrix::Rotation(m.orientation);
  head.TranslateInPlace(m.position);

  if (m.renderMode == device::RenderMode::StandAlone) {
    if (m.recentered) {
      if (m.type == k6DofHeadSet) {
        m.reorientMatrix = DeviceUtils::CalculateReorientationMatrix(head, kAverageHeight);
      } else {
        m.reorientMatrix = vrb::Matrix::Identity();
      }
    }
    head.TranslateInPlace(m.headOffset);
  }

  m.recentered = false;

  m.cameras[0]->SetHeadTransform(head);
  m.cameras[1]->SetHeadTransform(head);


  // Update te gaze mode state based on controllers availability
  m.isInGazeMode = true;
  for (int32_t i = 0; i < m.controllers.size(); ++i) {
    if (i != m.gazeIndex && m.controllers[i].enabled) {
      m.isInGazeMode = false;
      break;
    }
  }

  if (m.isInGazeMode) {
    m.controllers[m.gazeIndex].enabled = m.isInGazeMode;
    m.controllers[m.gazeIndex].transform = head;
  }

  m.UpdateControllers();
}

void
DeviceDelegatePicoVR::BindEye(const device::Eye aWhich) {
  VRB_GL_CHECK(glViewport(0, 0, m.renderWidth, m.renderHeight));
  VRB_GL_CHECK(glClearColor(m.clearColor.Red(), m.clearColor.Green(), m.clearColor.Blue(), m.clearColor.Alpha()));
  VRB_GL_CHECK(glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT));
}

void
DeviceDelegatePicoVR::EndFrame(const FrameEndMode aMode) {

}

bool
DeviceDelegatePicoVR::IsInGazeMode() const {
  return m.isInGazeMode;
}

int32_t
DeviceDelegatePicoVR::GazeModeIndex() const {
  return m.gazeIndex;
}

bool
DeviceDelegatePicoVR::IsControllerLightEnabled() const {
  return false;
}

void
DeviceDelegatePicoVR::Pause() {
  m.paused = true;
}

void
DeviceDelegatePicoVR::Resume() {
  m.paused = false;
  m.setHeadOffset = true;
}

void
DeviceDelegatePicoVR::SetFocused(const int aIndex) {
  m.focusIndex = aIndex;
  if (m.controllerDelegate) {
    m.controllerDelegate->SetFocused(m.focusIndex);
  }
}

void
DeviceDelegatePicoVR::SetType(int aType) {
  m.type = aType;
}

void
DeviceDelegatePicoVR::SetRenderSize(const int32_t aWidth, const int32_t aHeight) {
  m.renderWidth = (uint32_t) aWidth;
  m.renderHeight = (uint32_t) aHeight;
}

void
DeviceDelegatePicoVR::UpdateIpd(const float aIPD) {
  m.ipd = aIPD;
  m.UpdateEyeTransform();
}

void
DeviceDelegatePicoVR::UpdateFov(const float aFov) {
  m.fov = aFov * (float)(M_PI / 180.0);
  m.UpdatePerspective();
}

void
DeviceDelegatePicoVR::UpdatePosition(const vrb::Vector& aPosition) {
  if (m.setHeadOffset) {
    m.headOffset = kAverageHeight - aPosition;
    m.setHeadOffset = false;
  }
  m.position = aPosition;
}

void
DeviceDelegatePicoVR::UpdateOrientation(const vrb::Quaternion& aOrientation) {
  m.orientation = aOrientation;
}

void
DeviceDelegatePicoVR::UpdateControllerConnected(const int aIndex, const bool aConnected) {
  auto & controller = m.controllers[aIndex];
  if (controller.enabled != aConnected) {
    controller.enabled = aConnected;
    m.controllerDelegate->SetLeftHanded(aIndex, !controller.IsRightHand());
    m.controllerDelegate->SetEnabled(aIndex, aConnected);
    if (m.focusIndex == aIndex) {
      m.controllerDelegate->SetFocused(aIndex);
      m.focusIndex = -1; // Do not set focus again;
    }
  }
}

void
DeviceDelegatePicoVR::UpdateControllerPose(const int aIndex, const bool a6Dof, const vrb::Vector& aPosition, const vrb::Quaternion& aRotation) {
  vrb::Quaternion quat(-aRotation.x(), -aRotation.y(), aRotation.z(), aRotation.w());
  vrb::Matrix transform = vrb::Matrix::Rotation(quat);
  transform.PreMultiplyInPlace(vrb::Matrix::Position(aPosition));
  m.controllers[aIndex].transform = transform;
  m.controllers[aIndex].is6DoF = a6Dof;
}

void
DeviceDelegatePicoVR::UpdateControllerButtons(const int aIndex, const int32_t aButtonsState, const float aGrip, const float axisX, const float axisY, const bool touched) {
  m.controllers[aIndex].buttonsState = aButtonsState;
  m.controllers[aIndex].grip = aGrip;
  m.controllers[aIndex].axisX = axisX;
  m.controllers[aIndex].axisY = axisY;
  m.controllers[aIndex].touched = touched;
}

void
DeviceDelegatePicoVR::UpdateControllerBatteryLevel(const int aIndex, const int aBatteryLevel) {
  m.controllers[aIndex].battery = aBatteryLevel;
}

void
DeviceDelegatePicoVR::Recenter() {
    m.recentered = true;
    m.setHeadOffset = true;
}

DeviceDelegatePicoVR::DeviceDelegatePicoVR(State &aState) : m(aState) {}

DeviceDelegatePicoVR::~DeviceDelegatePicoVR() { m.Shutdown(); }

} // namespace crow
