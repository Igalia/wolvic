/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "DeviceDelegateVisionGlass.h"

#include "ElbowModel.h"
#include "GestureDelegate.h"

#include "vrb/CameraEye.h"
#include "vrb/Color.h"
#include "vrb/ConcreteClass.h"
#include "vrb/GLError.h"
#include "vrb/Matrix.h"
#include "vrb/Quaternion.h"
#include "vrb/RenderContext.h"
#include "vrb/Vector.h"
#include "JNIUtil.h"
#include "DeviceUtils.h"
#include "OneEuroFilter.h"

#include <vector>
#include <cassert>

namespace {

const char* kSetRenderModeName = "setRenderMode";
const char* kSetRenderModeSignature = "(I)V";
JNIEnv* sEnv;
jclass sBrowserClass;
jobject sActivity;
jmethodID sSetRenderMode;

}

namespace crow {

static const float kHorizontalFOV = 36.0f;
static const float kVerticalFOV = 21.0f;
const vrb::Vector kAverageHeight(0.0f, 1.6f, 0.0f);
static const int32_t kControllerIndex = 0;

struct DeviceDelegateVisionGlass::State {
  vrb::RenderContextWeak context;
  device::RenderMode renderMode;
  ImmersiveDisplayPtr immersiveDisplay;
  ControllerDelegatePtr controller;
  vrb::CameraEyePtr cameras[2];
  vrb::Color clearColor;
  vrb::Quaternion controllerOrientation;
  bool clicked;
  GLsizei glWidth, glHeight;
  float near, far;
  vrb::Matrix reorientMatrix;
  crow::ElbowModelPtr elbow;
  std::unique_ptr<OneEuroFilterQuaternion> orientationFilter;
  vrb::Quaternion headOrientation;
  State()
      : renderMode(device::RenderMode::StandAlone)
      , clicked(false)
      , glWidth(0)
      , glHeight(0)
      , near(0.1f)
      , far(1000.0f)
      , reorientMatrix(vrb::Matrix::Identity())
      , elbow(ElbowModel::Create())
  {
      orientationFilter = std::make_unique<OneEuroFilterQuaternion>(0.1, 0.5, 1.0);
  }

  void Initialize() {
    vrb::RenderContextPtr render = context.lock();
    if (!render) {
      return;
    }
    vrb::CreationContextPtr create = render->GetRenderThreadCreationContext();
    for (int i = 0; i < 2; ++i)
        cameras[i] = vrb::CameraEye::Create(create);
  }

  void Shutdown() {
  }

  void UpdateDisplay() {
    if (!immersiveDisplay)
      return;

    vrb::Matrix fov = vrb::Matrix::PerspectiveMatrixWithResolutionDegrees(glWidth, glHeight,
                                                                          kHorizontalFOV, kVerticalFOV,
                                                                          near, far);
    float left(0.0f), right(0.0f), top(0.0f), bottom(0.0f), n2(0.0f), f2(0.0f);
    fov.DecomposePerspectiveDegrees(left, right, top, bottom, n2, f2);

    cameras[0]->SetPerspective(fov);
    cameras[1]->SetPerspective(fov);
    immersiveDisplay->SetEyeResolution((int32_t)(glWidth / 2), glHeight);
    immersiveDisplay->SetFieldOfView(device::Eye::Left, left, right, top, bottom);
    immersiveDisplay->SetFieldOfView(device::Eye::Right, left, right, top, bottom);

    immersiveDisplay->SetCapabilityFlags(device::PositionEmulated | device::Orientation | device::Present | device::InlineSession | device::ImmersiveVRSession);
  }
};

DeviceDelegateVRGlassPtr
DeviceDelegateVisionGlass::Create(vrb::RenderContextPtr& aContext) {
  DeviceDelegateVRGlassPtr result = std::make_shared<vrb::ConcreteClass<DeviceDelegateVisionGlass, DeviceDelegateVisionGlass::State > > ();
  result->m.context = aContext;
  result->m.Initialize();
  return result;
}

device::DeviceType
DeviceDelegateVisionGlass::GetDeviceType() {
  return device::VisionGlass;
}

void
DeviceDelegateVisionGlass::SetRenderMode(const device::RenderMode aMode) {
  if (aMode == m.renderMode) {
    return;
  }
  m.renderMode = aMode;
  if (ValidateMethodID(sEnv, sActivity, sSetRenderMode, __FUNCTION__)) {
    sEnv->CallVoidMethod(sActivity, sSetRenderMode, (aMode == device::RenderMode::Immersive ? 1 : 0));
    CheckJNIException(sEnv, __FUNCTION__);
  }
  if (aMode == device::RenderMode::Immersive)
    RecenterView();
}

device::RenderMode
DeviceDelegateVisionGlass::GetRenderMode() {
  return m.renderMode;
}

void
DeviceDelegateVisionGlass::RegisterImmersiveDisplay(ImmersiveDisplayPtr aDisplay) {
  m.immersiveDisplay = aDisplay;
  if (m.immersiveDisplay) {
    m.immersiveDisplay->SetDeviceName("Vision Glass");
    m.UpdateDisplay();
    m.immersiveDisplay->CompleteEnumeration();
  }
}

GestureDelegateConstPtr
DeviceDelegateVisionGlass::GetGestureDelegate() {
  return nullptr;
}
vrb::CameraPtr
DeviceDelegateVisionGlass::GetCamera(const device::Eye eye) {
  return m.cameras[device::EyeIndex(eye)];
}

const vrb::Matrix&
DeviceDelegateVisionGlass::GetHeadTransform() const {
  return m.cameras[0]->GetTransform();
}

const vrb::Matrix&
DeviceDelegateVisionGlass::GetReorientTransform() const {
  return m.reorientMatrix;
}

void
DeviceDelegateVisionGlass::SetReorientTransform(const vrb::Matrix& aMatrix) {
  m.reorientMatrix = aMatrix;
}

void
DeviceDelegateVisionGlass::Reorient() {
    vrb::Matrix head = GetHeadTransform();
    m.reorientMatrix = DeviceUtils::CalculateReorientationMatrixOnHeadLock(head, kAverageHeight);
}

void
DeviceDelegateVisionGlass::SetClearColor(const vrb::Color& aColor) {
  m.clearColor = aColor;
}

void
DeviceDelegateVisionGlass::SetClipPlanes(const float aNear, const float aFar) {
  m.near = aNear;
  m.far = aFar;
  m.UpdateDisplay();
}

void
DeviceDelegateVisionGlass::SetControllerDelegate(ControllerDelegatePtr& aController) {
  m.controller = aController;
  m.controller->CreateController(kControllerIndex, kControllerIndex, "Vision Glass Controller");
  m.controller->SetEnabled(kControllerIndex, true);
  m.controller->SetCapabilityFlags(kControllerIndex, device::Orientation);
  m.controller->SetTargetRayMode(kControllerIndex, device::TargetRayMode::TrackedPointer);
  m.controller->SetControllerType(kControllerIndex, device::VisionGlass);
  m.controller->SetMode(kControllerIndex, ControllerMode::Device);
  m.controller->SetAimEnabled(kControllerIndex, true);
  m.controller->SetCapabilityFlags(kControllerIndex, device::Orientation);

  m.controller->SetButtonCount(kControllerIndex, 5);
}

void
DeviceDelegateVisionGlass::ReleaseControllerDelegate() {
  m.controller = nullptr;
}

int32_t
DeviceDelegateVisionGlass::GetControllerModelCount() const {
  return 1;
}

bool DeviceDelegateVisionGlass::IsControllerLightEnabled() const {
  return false;
}

const std::string
DeviceDelegateVisionGlass::GetControllerModelName(const int32_t) const {
  return "Vision Glass";
}

void
DeviceDelegateVisionGlass::ProcessEvents() {}

void
DeviceDelegateVisionGlass::StartFrame(const FramePrediction aPrediction) {
  VRB_GL_CHECK(glClearColor(m.clearColor.Red(), m.clearColor.Green(), m.clearColor.Blue(), m.clearColor.Alpha()));
  VRB_GL_CHECK(glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT));
  mShouldRender = true;

  // We need to flip the Z axis coming from the SDK to get the proper rotation.
  auto headingMatrix = vrb::Matrix::Rotation({m.headOrientation.x(), m.headOrientation.y(), -m.headOrientation.z(), -m.headOrientation.w()});
  auto headTransform = headingMatrix.Translate(kAverageHeight);
  m.cameras[0]->SetHeadTransform(headTransform);
  m.cameras[1]->SetHeadTransform(headTransform);
  m.immersiveDisplay->SetEyeTransform(device::Eye::Left, m.cameras[0]->GetEyeTransform());
  m.immersiveDisplay->SetEyeTransform(device::Eye::Right, m.cameras[1]->GetEyeTransform());
  m.immersiveDisplay->SetSittingToStandingTransform(vrb::Matrix::Translation(kAverageHeight));

  // Update controller
  if (!m.controller)
    return;

  vrb::Matrix transformMatrix;
  if (auto context = m.context.lock()) {
    float* filteredOrientation = m.orientationFilter->filter(context->GetTimestamp() * 1000000000, m.controllerOrientation.Data());
    transformMatrix = vrb::Matrix::Rotation(vrb::Quaternion(filteredOrientation));
  } else {
    transformMatrix = vrb::Matrix::Rotation(m.controllerOrientation);
  }
  auto pointerTransform = m.elbow->GetTransform(ElbowModel::HandEnum::None, headTransform, transformMatrix);
  m.controller->SetTransform(kControllerIndex, pointerTransform);
}

void
DeviceDelegateVisionGlass::BindEye(const device::Eye aEye) {
  VRB_GL_CHECK(glViewport(aEye == device::Eye::Left ? 0 : m.glWidth / 2, 0, m.glWidth / 2, m.glHeight));
}

void
DeviceDelegateVisionGlass::EndFrame(const FrameEndMode aMode) {
  // noop
}

void
DeviceDelegateVisionGlass::InitializeJava(JNIEnv* aEnv, jobject aActivity) {
  if (aEnv == sEnv) {
    return;
  }
  sEnv = aEnv;
  if (!sEnv) {
    return;
  }
  sActivity = sEnv->NewGlobalRef(aActivity);
  sBrowserClass = sEnv->GetObjectClass(sActivity);
  if (!sBrowserClass) {
    return;
  }

  sSetRenderMode = FindJNIMethodID(sEnv, sBrowserClass, kSetRenderModeName, kSetRenderModeSignature);
}

void
DeviceDelegateVisionGlass::ShutdownJava() {
  if (!sEnv) {
    return;
  }
  if (sActivity) {
    sEnv->DeleteGlobalRef(sActivity);
    sActivity = nullptr;
  }

  sBrowserClass = nullptr;
  sSetRenderMode = nullptr;
}

void
DeviceDelegateVisionGlass::SetViewport(const int aWidth, const int aHeight) {
  m.glWidth = aWidth;
  m.glHeight = aHeight;
  m.UpdateDisplay();
}


void
DeviceDelegateVisionGlass::Pause() {

}

void
DeviceDelegateVisionGlass::Resume() {

}

void
DeviceDelegateVisionGlass::RecenterView() {
}

static float
Clamp(const float aValue) {
  if (aValue < -1.0f) {
    return -1.0f;
  } else if (aValue > 1.0f) {
    return 1.0f;
  }
  return aValue;
}

void
DeviceDelegateVisionGlass::ControllerButtonPressed(const bool aDown) {
  if (!m.controller) {
    return;
  }

  m.controller->SetButtonState(kControllerIndex, ControllerDelegate::BUTTON_TRIGGER, device::kImmersiveButtonTrigger, aDown, aDown);
  if (aDown && m.renderMode == device::RenderMode::Immersive) {
    m.controller->SetSelectActionStart(kControllerIndex);
  } else {
    m.controller->SetSelectActionStop(kControllerIndex);
  }

}

void
DeviceDelegateVisionGlass::setHead(const float aX, const float aY, const float aZ, const float aW) {
  m.headOrientation = vrb::Quaternion(aX, aY, aZ, aW);
}

void
DeviceDelegateVisionGlass::setControllerOrientation(const float aX, const float aY, const float aZ, const float aW) {
    m.controllerOrientation = vrb::Quaternion(aX, aY, aZ, aW);
}

DeviceDelegateVisionGlass::DeviceDelegateVisionGlass(State& aState) : m(aState) {}
DeviceDelegateVisionGlass::~DeviceDelegateVisionGlass() { m.Shutdown(); }

} // namespace crow
