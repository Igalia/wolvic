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

static const int32_t kControllerIndex = 0;
static const vrb::Vector& GetHomePosition() {
  static vrb::Vector homePosition(0.0f, 1.55f, 3.0f);
  return homePosition;
}

struct DeviceDelegateVisionGlass::State {
  vrb::RenderContextWeak context;
  device::RenderMode renderMode;
  ImmersiveDisplayPtr display;
  ControllerDelegatePtr controller;
  vrb::CameraEyePtr cameras[2];
  vrb::Color clearColor;
  vrb::Matrix headingMatrix;
  vrb::Vector position;
  bool clicked;
  GLsizei glWidth, glHeight;
  float near, far;
  State()
      : renderMode(device::RenderMode::StandAlone)
      , headingMatrix(vrb::Matrix::Identity())
      , position(GetHomePosition())
      , clicked(false)
      , glWidth(0)
      , glHeight(0)
      , near(0.1f)
      , far(1000.0f)
  {
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
    if (!display)
      return;

    vrb::Matrix fov = vrb::Matrix::PerspectiveMatrixWithResolutionDegrees(glWidth, glHeight,
                                                                          60.0f, -1.0f,
                                                                          near, far);
    float left(0.0f), right(0.0f), top(0.0f), bottom(0.0f), n2(0.0f), f2(0.0f);
    fov.DecomposePerspectiveDegrees(left, right, top, bottom, n2, f2);

    cameras[0]->SetPerspective(fov);
    cameras[1]->SetPerspective(fov);
    display->SetFieldOfView(device::Eye::Left, left, right, top, bottom);
    display->SetFieldOfView(device::Eye::Right, left, right, top, bottom);

    display->SetCapabilityFlags(device::Position | device::Orientation | device::Present | device::InlineSession | device::ImmersiveVRSession);
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
  if (aMode != device::RenderMode::StandAlone) {
    m.position = vrb::Vector();
  } else {
    // recenter when leaving immersive mode.
    RecenterView();
  }
}

device::RenderMode
DeviceDelegateVisionGlass::GetRenderMode() {
  return m.renderMode;
}

void
DeviceDelegateVisionGlass::RegisterImmersiveDisplay(ImmersiveDisplayPtr aDisplay) {
  m.display = aDisplay;
  if (m.display) {
    m.display->SetDeviceName("Vision Glass");
    m.UpdateDisplay();
    m.display->CompleteEnumeration();
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
  static vrb::Matrix identity(vrb::Matrix::Identity());
  return identity;
}

void
DeviceDelegateVisionGlass::SetReorientTransform(const vrb::Matrix& aMatrix) {
  // Ignore reorient transform
}

void
DeviceDelegateVisionGlass::Reorient() {
  // Ignore reorient
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
  m.controller->CreateController(kControllerIndex, -1, "Oculus Touch (Right)"); // "Wolvic Virtual Controller");
  m.controller->SetEnabled(kControllerIndex, true);
  m.controller->SetCapabilityFlags(kControllerIndex, device::Orientation | device::Position);
  m.controller->SetButtonCount(kControllerIndex, 5);
  m.controller->SetTargetRayMode(kControllerIndex, device::TargetRayMode::TrackedPointer);
  static const float data[2] = {0.0f, 0.0f};
  m.controller->SetAxes(kControllerIndex, data, 2);
}

void
DeviceDelegateVisionGlass::ReleaseControllerDelegate() {
  m.controller = nullptr;
}

int32_t
DeviceDelegateVisionGlass::GetControllerModelCount() const {
  return 0;
}

const std::string
DeviceDelegateVisionGlass::GetControllerModelName(const int32_t) const {
  static const std::string name;
  return name;
}

void
DeviceDelegateVisionGlass::ProcessEvents() {}

void
DeviceDelegateVisionGlass::StartFrame(const FramePrediction aPrediction) {
  VRB_GL_CHECK(glClearColor(m.clearColor.Red(), m.clearColor.Green(), m.clearColor.Blue(), m.clearColor.Alpha()));
  VRB_GL_CHECK(glEnable(GL_DEPTH_TEST));
  VRB_GL_CHECK(glEnable(GL_CULL_FACE));
  VRB_GL_CHECK(glEnable(GL_BLEND));
  VRB_GL_CHECK(glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT));
  mShouldRender = true;
  if (m.controller) {
    vrb::RenderContextPtr context = m.context.lock();
    if (context) {
      float level = 100.0 - std::fmod(context->GetTimestamp(), 100.0);
      m.controller->SetBatteryLevel(kControllerIndex, (int32_t)level);
    }
  }

  const float IPD = 0;
  m.cameras[0]->SetEyeTransform(vrb::Matrix::Translation(vrb::Vector(-IPD * 0.5f, 0.f, 0.f)));
  m.cameras[1]->SetEyeTransform(vrb::Matrix::Translation(vrb::Vector(IPD * 0.5f, 0.f, 0.f)));
  auto headTransform = m.headingMatrix.Translate(m.position);
  m.cameras[0]->SetHeadTransform(headTransform);
  m.cameras[1]->SetHeadTransform(headTransform);
  m.display->SetEyeOffset(device::Eye::Left, -IPD * 0.5f, 0.f, 0.f);
  m.display->SetEyeOffset(device::Eye::Right, IPD * 0.5f, 0.f, 0.f);
}

void
DeviceDelegateVisionGlass::BindEye(const device::Eye aEye) {
  // noop
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
  m.position = m.renderMode == device::RenderMode::Immersive ? m.position = vrb::Vector() : GetHomePosition();
  m.headingMatrix = vrb::Matrix::Identity();
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
DeviceDelegateVisionGlass::TouchEvent(const bool aDown, const float aX, const float aY) {
  static const vrb::Vector sForward(0.0f, 0.0f, -1.0f);
  if (!m.controller) {
    return;
  }
  if (m.renderMode == device::RenderMode::Immersive) {
    m.controller->SetButtonState(kControllerIndex, ControllerDelegate::BUTTON_TOUCHPAD, 0, false, false);
    m.clicked = false;
  } else if (aDown != m.clicked) {
    m.controller->SetButtonState(kControllerIndex, ControllerDelegate::BUTTON_TOUCHPAD, 0, aDown, aDown);
    m.clicked = aDown;
  }

  const float viewportWidth = m.glWidth;
  const float viewportHeight = m.glHeight;
  if ((viewportWidth <= 0.0f) || (viewportHeight <= 0.0f)) {
    return;
  }
  const float xModifier = (m.renderMode == device::RenderMode::Immersive ? viewportWidth / 2.0f : 0.0f);
  const float width = Clamp((((aX - xModifier) / viewportWidth) * 2.0f) - 1.0f);
  const float height = (((viewportHeight - aY) / viewportHeight) * 2.0f) - 1.0f;

  vrb::Vector start(width, height, -1.0f);
  vrb::Vector end(width, height, 1.0f);
  vrb::Matrix inversePerspective = m.cameras[0]->GetPerspective().Inverse();
  start = inversePerspective.MultiplyPosition(start);
  end = inversePerspective.MultiplyPosition(end);
  vrb::Matrix view = m.cameras[0]->GetTransform();
  start = view.MultiplyPosition(start);
  end = view.MultiplyPosition(end);
  const vrb::Vector direction = (end - start).Normalize();
  const vrb::Vector up = sForward.Cross(direction);
  const float angle = acosf(sForward.Dot(direction));
  vrb::Matrix transform = vrb::Matrix::Rotation(up, angle);
  if (m.renderMode == device::RenderMode::Immersive) {
    start += direction * 0.3f;
  }
  transform.TranslateInPlace(start);
  m.controller->SetTransform(kControllerIndex, transform);
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
  // We need to flip the Z axis comming from the SDK to get the proper rotation.
  m.headingMatrix = vrb::Matrix::Rotation({aX,aY,-aZ,-aW});
}

DeviceDelegateVisionGlass::DeviceDelegateVisionGlass(State& aState) : m(aState) {}
DeviceDelegateVisionGlass::~DeviceDelegateVisionGlass() { m.Shutdown(); }

} // namespace crow
