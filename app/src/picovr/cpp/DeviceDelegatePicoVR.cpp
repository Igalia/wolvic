/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "DeviceDelegatePicoVR.h"
#include "ElbowModel.h"
#include "BrowserEGLContext.h"

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
// TODO: support different controllers & buttons
static const int32_t kMaxControllerCount = 2;
static const int32_t kNumButtons = 2;
static const int32_t kNumAxes = 2;
static const uint32_t kButtonApp = 1;
static const uint32_t kButtonAction = 1 << 1;

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
    ElbowModel::HandEnum hand;
    Controller()
        : index(-1)
        , created(false)
        , enabled(false)
        , touched(false)
        , is6DoF(false)
        , transform(vrb::Matrix::Identity())
        , hand(ElbowModel::HandEnum::Right)
    {}
  };
  vrb::RenderContextWeak context;
  bool initialized = false;
  bool paused = false;
  device::RenderMode renderMode = device::RenderMode::StandAlone;
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

  void Initialize() {
    vrb::RenderContextPtr localContext = context.lock();

    vrb::CreationContextPtr create = localContext->GetRenderThreadCreationContext();
    cameras[device::EyeIndex(device::Eye::Left)] = vrb::CameraEye::Create(create);
    cameras[device::EyeIndex(device::Eye::Right)] = vrb::CameraEye::Create(create);
    UpdatePerspective();
    UpdateEyeTransform();

    for (int32_t index = 0; index < kMaxControllerCount; index++) {
      controllers[index].index = index;
      if (index == 0) {
        controllers[index].hand = ElbowModel::HandEnum::Right;
      } else {
        controllers[index].hand = ElbowModel::HandEnum::Left;
      }
      controllers[index].is6DoF = true;
    }

    initialized = true;
  }

  void Shutdown() {
    initialized = false;
  }

  void UpdatePerspective() {
    vrb::Matrix projection = vrb::Matrix::PerspectiveMatrix(fov, fov, fov, fov, near, far);
    cameras[0]->SetPerspective(projection);
    cameras[1]->SetPerspective(projection);

    const float fovDegrees = fov * (float)(180.0 / M_PI);
    immersiveDisplay->SetFieldOfView(device::Eye::Left, fovDegrees, fovDegrees, fovDegrees, fovDegrees);
    immersiveDisplay->SetFieldOfView(device::Eye::Right, fovDegrees, fovDegrees, fovDegrees, fovDegrees);
  }

  void UpdateEyeTransform() {
    cameras[0]->SetEyeTransform(vrb::Matrix::Translation(vrb::Vector(-ipd * 0.5f, 0.f, 0.f)));
    cameras[1]->SetEyeTransform(vrb::Matrix::Translation(vrb::Vector(ipd * 0.5f, 0.f, 0.f)));

    if (immersiveDisplay) {
      immersiveDisplay->SetEyeOffset(device::Eye::Left, -ipd * 0.5f, 0.f, 0.f);
      immersiveDisplay->SetEyeOffset(device::Eye::Right, ipd * 0.5f, 0.f, 0.f);
    }
  }

  void UpdateControllers() {
    for (int32_t i = 0; i < controllers.size(); ++i) {
      if (!controllers[i].enabled) {
        continue;
      }
      auto & controller = controllers[i];
      device::CapabilityFlags flags = device::Orientation;
      if (controller.is6DoF) {
        flags |= device::Position;
      }
      controllerDelegate->SetCapabilityFlags(i, flags);
      const bool actionPressed = (controller.buttonsState & kButtonAction) > 0;
      const bool appPressed = (controller.buttonsState & kButtonApp) > 0;

      controllerDelegate->SetButtonState(i, ControllerDelegate::BUTTON_TRIGGER, 0, actionPressed, actionPressed);
      controllerDelegate->SetButtonState(i, ControllerDelegate::BUTTON_APP, 1, appPressed, appPressed);
      controllerDelegate->SetAxes(i, &controller.grip, 1);

      vrb::Matrix transform = controller.transform;
      if (renderMode == device::RenderMode::StandAlone) {
        transform.TranslateInPlace(kAverageHeight);
      }

      controllerDelegate->SetTransform(i, transform);
    }
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
  m.immersiveDisplay->SetCapabilityFlags(device::Position | device::Orientation | device::Present);
  m.immersiveDisplay->SetEyeResolution(m.renderWidth, m.renderHeight);
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
  for (int32_t index = 0; index < m.controllers.size(); index++) {
    m.controllerDelegate->CreateController(index, 0, "Pico");
    m.controllerDelegate->SetButtonCount(index, kNumButtons);
    m.controllerDelegate->SetHapticCount(index, 0);
    m.controllers[index].created = true;
  }
}

void
DeviceDelegatePicoVR::ReleaseControllerDelegate() {
  m.controllerDelegate = nullptr;
}

int32_t
DeviceDelegatePicoVR::GetControllerModelCount() const {
  return m.controllers.size();
}

const std::string
DeviceDelegatePicoVR::GetControllerModelName(const int32_t aModelIndex) const {
  // FIXME: Need Pico based controller
  static const std::string name("vr_controller_daydream.obj");
  return aModelIndex == 0 ? name : "";
}

void
DeviceDelegatePicoVR::ProcessEvents() {

}

void
DeviceDelegatePicoVR::StartFrame() {
  vrb::Matrix head = vrb::Matrix::Rotation(m.orientation);
  head.TranslateInPlace(m.position);

  if (m.renderMode == device::RenderMode::StandAlone) {
    head.TranslateInPlace(kAverageHeight);
  }

  m.cameras[0]->SetHeadTransform(head);
  m.cameras[1]->SetHeadTransform(head);
  m.UpdateControllers();
}

void
DeviceDelegatePicoVR::BindEye(const device::Eye aWhich) {
  VRB_GL_CHECK(glViewport(0, 0, m.renderWidth, m.renderHeight));
  VRB_GL_CHECK(glClearColor(m.clearColor.Red(), m.clearColor.Green(), m.clearColor.Blue(), m.clearColor.Alpha()));
  VRB_GL_CHECK(glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT));
}

void
DeviceDelegatePicoVR::EndFrame(const bool aDiscard) {

}

void
DeviceDelegatePicoVR::Pause() {
  m.paused = true;
}

void
DeviceDelegatePicoVR::Resume() {
  m.paused = false;
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
    m.controllerDelegate->SetEnabled(aIndex, aConnected);
    m.controllerDelegate->SetVisible(aIndex, aConnected);
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
DeviceDelegatePicoVR::UpdateControllerButtons(const int aIndex, const int32_t aButtonsState, const float aGrip) {
  m.controllers[aIndex].buttonsState = aButtonsState;
  m.controllers[aIndex].grip = aGrip;
}


DeviceDelegatePicoVR::DeviceDelegatePicoVR(State &aState) : m(aState) {}

DeviceDelegatePicoVR::~DeviceDelegatePicoVR() { m.Shutdown(); }

} // namespace crow
