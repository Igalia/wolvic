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

#include <vector>
#include <cstdlib>
#include <unistd.h>


namespace crow {

static const vrb::Vector kAverageHeight(0.0f, 1.7f, 0.0f);

struct DeviceDelegatePicoVR::State {
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
  int32_t controllerHandle = -1;
  vrb::Matrix controllerTransform = vrb::Matrix::Identity();
  crow::ElbowModelPtr elbow;
  ControllerDelegatePtr controller;
  ImmersiveDisplayPtr immersiveDisplay;
  vrb::Matrix reorientMatrix = vrb::Matrix::Identity();
  vrb::Quaternion orientation;
  vrb::Vector position;
  float ipd = 0.064f;
  float fov = (float) (51.0 * M_PI / 180.0);

  void Initialize() {
    vrb::RenderContextPtr localContext = context.lock();
    elbow = crow::ElbowModel::Create();

    vrb::CreationContextPtr create = localContext->GetRenderThreadCreationContext();
    cameras[device::EyeIndex(device::Eye::Left)] = vrb::CameraEye::Create(create);
    cameras[device::EyeIndex(device::Eye::Right)] = vrb::CameraEye::Create(create);
    UpdatePerspective();
    UpdateEyeTransform();
    initialized = true;
  }

  void Shutdown() {
    initialized = false;
  }

  void UpdatePerspective() {
    vrb::Matrix projection = vrb::Matrix::PerspectiveMatrix(fov, fov, fov, fov, near, far);
    cameras[0]->SetPerspective(projection);
    cameras[1]->SetPerspective(projection);
  }

  void UpdateEyeTransform() {
    cameras[0]->SetEyeTransform(vrb::Matrix::Translation(vrb::Vector(-ipd * 0.5f, 0.f, 0.f)));
    cameras[1]->SetEyeTransform(vrb::Matrix::Translation(vrb::Vector(ipd * 0.5f, 0.f, 0.f)));

    if (immersiveDisplay) {
      immersiveDisplay->SetEyeOffset(device::Eye::Left, -ipd * 0.5f, 0.f, 0.f);
      immersiveDisplay->SetEyeOffset(device::Eye::Right, ipd * 0.5f, 0.f, 0.f);
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
  m.controller = aController;
}

void
DeviceDelegatePicoVR::ReleaseControllerDelegate() {
  m.controller = nullptr;
}

int32_t
DeviceDelegatePicoVR::GetControllerModelCount() const {
  return 1;
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


DeviceDelegatePicoVR::DeviceDelegatePicoVR(State &aState) : m(aState) {}

DeviceDelegatePicoVR::~DeviceDelegatePicoVR() { m.Shutdown(); }

} // namespace crow
