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

static const float kDiagonalFOV = 41.0f;
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
  vrb::Quaternion headToControllerRelativeRotation;
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
      SetupOrientationFilter();
  }

  void SetupOrientationFilter() {
      orientationFilter = std::make_unique<OneEuroFilterQuaternion>(0.25, 2, 1.0);
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

    if (glHeight == 0 || glWidth == 0)
      return;

    // Compute horizontal and vertical FOV from diagonal FOV and aspect ratio.
    // https://medium.com/insights-on-virtual-reality/converting-diagonal-field-of-view-and-aspect-ratio-to-horizontal-and-vertical-field-of-view-13bcc1d8600c
    auto degToRad = [](float deg) { return deg * M_PI / 180.0; };
    auto radToDeg = [](float rad) { return rad * 180.0 / M_PI; };
    auto width = (float) glWidth / 2; // glWidth is sum of the width of both displays
    auto diagonalSize = sqrt(pow(glWidth / 2, 2) + pow(glHeight, 2));
    auto horizontalFOV = atan(tan(degToRad(kDiagonalFOV / 2)) * (width / diagonalSize)) * 2;
    horizontalFOV = radToDeg(horizontalFOV);
    auto verticalFOV = (horizontalFOV * glHeight) / width;

    float halfHorizontalFOV = horizontalFOV / 2;
    float halfVerticalFOV = verticalFOV / 2;
    vrb::Matrix fov = vrb::Matrix::PerspectiveMatrix(degToRad(halfHorizontalFOV), degToRad(halfHorizontalFOV),
                                                     degToRad(halfVerticalFOV), degToRad(halfVerticalFOV), near, far);

    cameras[0]->SetPerspective(fov);
    cameras[1]->SetPerspective(fov);
    immersiveDisplay->SetEyeResolution((int32_t)(glWidth / 2), glHeight);
    immersiveDisplay->SetFieldOfView(device::Eye::Left, halfHorizontalFOV, halfHorizontalFOV, halfVerticalFOV, halfVerticalFOV);
    immersiveDisplay->SetFieldOfView(device::Eye::Right, halfHorizontalFOV, halfHorizontalFOV, halfVerticalFOV, halfVerticalFOV);

    immersiveDisplay->SetCapabilityFlags(device::PositionEmulated | device::Orientation | device::Present | device::InlineSession | device::ImmersiveVRSession);
  }
};

DeviceDelegateVRGlassPtr
DeviceDelegateVisionGlass::Create(vrb::RenderContextPtr& aContext) {
  VRB_LOG("DeviceDelegateVisionGlass::Create()");
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
DeviceDelegateVisionGlass::Reorient(const vrb::Matrix& transform, ReorientMode mode) {
  switch (mode) {
    case ReorientMode::SIX_DOF:
      m.reorientMatrix = DeviceUtils::CalculateReorientationMatrixOnHeadLock(transform, GetHeadTransform().GetTranslation());
      break;
    case ReorientMode::NO_ROLL:
      m.reorientMatrix = DeviceUtils::CalculateReorientationMatrixWithoutRoll(transform, GetHeadTransform().GetTranslation());
      break;
    default:
      VRB_ERROR("Unsupported reorient mode %d", mode);
  }
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
  m.controller->SetTargetRayMode(kControllerIndex, device::TargetRayMode::TrackedPointer);
  m.controller->SetControllerType(kControllerIndex, device::VisionGlass);
  m.controller->SetMode(kControllerIndex, ControllerMode::Device);
  m.controller->SetAimEnabled(kControllerIndex, true);
  m.controller->SetCapabilityFlags(kControllerIndex, device::Orientation | device::PositionEmulated);

  m.controller->SetButtonCount(kControllerIndex, 1);
  m.controller->SetButtonState(kControllerIndex, ControllerDelegate::BUTTON_TRIGGER, device::kImmersiveButtonTrigger, false, false);
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
DeviceDelegateVisionGlass::SetHitDistance(const float distance) {
  // Scale the beam so it reaches all the way to the pointer.
  auto beamTransform = vrb::Matrix::Identity();
  beamTransform.ScaleInPlace(vrb::Vector(1.0, 1.0, distance));
  m.controller->SetBeamTransform(kControllerIndex, beamTransform);
}

void
DeviceDelegateVisionGlass::ProcessEvents() {}

vrb::Quaternion
DeviceDelegateVisionGlass::CorrectedHeadOrientation() const {
    return { m.headOrientation.x(), m.headOrientation.y(), -m.headOrientation.z(), -m.headOrientation.w() };
}

void
DeviceDelegateVisionGlass::StartFrame(const FramePrediction aPrediction) {
  VRB_GL_CHECK(glClearColor(m.clearColor.Red(), m.clearColor.Green(), m.clearColor.Blue(), m.clearColor.Alpha()));
  VRB_GL_CHECK(glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT));
  mShouldRender = true;

  // We need to flip the Z axis coming from the SDK to get the proper rotation.
  auto headingMatrix = vrb::Matrix::Rotation(CorrectedHeadOrientation());
  auto headTransform = headingMatrix.Translate(kAverageHeight);
  m.cameras[0]->SetHeadTransform(headTransform);
  m.cameras[1]->SetHeadTransform(headTransform);
  m.immersiveDisplay->SetEyeTransform(device::Eye::Left, m.cameras[0]->GetEyeTransform());
  m.immersiveDisplay->SetEyeTransform(device::Eye::Right, m.cameras[1]->GetEyeTransform());

  // Update controller
  if (!m.controller)
    return;

  float timestamp;
  {
      timestamp = m.context.lock()->GetTimestamp() * 1e9;
  }
  float* filteredOrientation = m.orientationFilter->filter(timestamp, m.controllerOrientation.Data());
  auto calibratedControllerOrientation = m.headToControllerRelativeRotation * vrb::Quaternion(filteredOrientation);
  vrb::Matrix transformMatrix = vrb::Matrix::Rotation(calibratedControllerOrientation);
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
DeviceDelegateVisionGlass::setHead(const double aX, const double aY, const double aZ, const double aW) {
  m.headOrientation = vrb::Quaternion(aX, aY, aZ, aW);
}

void
DeviceDelegateVisionGlass::setControllerOrientation(const double aX, const double aY, const double aZ, const double aW) {
    m.controllerOrientation = vrb::Quaternion(aX, aY, aZ, aW);
}

void
DeviceDelegateVisionGlass::CalibrateController() {
  m.headToControllerRelativeRotation = CorrectedHeadOrientation() * m.controllerOrientation.Inverse();
  m.SetupOrientationFilter();
}

DeviceDelegateVisionGlass::DeviceDelegateVisionGlass(State& aState) : m(aState) {}
DeviceDelegateVisionGlass::~DeviceDelegateVisionGlass() { m.Shutdown(); }

} // namespace crow
