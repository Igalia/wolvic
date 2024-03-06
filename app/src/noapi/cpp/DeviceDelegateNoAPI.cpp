/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "DeviceDelegateNoAPI.h"
#include "ElbowModel.h"
#include "GestureDelegate.h"

#include "vrb/CameraSimple.h"
#include "vrb/Color.h"
#include "vrb/ConcreteClass.h"
#include "vrb/GLError.h"
#include "vrb/Matrix.h"
#include "vrb/Quaternion.h"
#include "vrb/RenderContext.h"
#include "vrb/Vector.h"
#include "JNIUtil.h"

#include <vector>

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

struct DeviceDelegateNoAPI::State {
  vrb::RenderContextWeak context;
  device::RenderMode renderMode;
  ImmersiveDisplayPtr display;
  ControllerDelegatePtr controller;
  vrb::CameraSimplePtr camera;
  vrb::Color clearColor;
  float heading;
  float pitch;
  vrb::Matrix headingMatrix;
  vrb::Matrix pitchMatrix;
  vrb::Vector position;
  bool clicked;
  GLsizei glWidth, glHeight;
  float near, far;
  State()
      : renderMode(device::RenderMode::StandAlone)
      , heading(0.0f)
      , pitch(0.0f)
      , headingMatrix(vrb::Matrix::Identity())
      , pitchMatrix(vrb::Matrix::Identity())
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
    camera = vrb::CameraSimple::Create(create);
    camera->SetTransform(vrb::Matrix::Translation(GetHomePosition()));
  }

  void Shutdown() {
  }

  void UpdateDisplay() {
    if (display) {
      vrb::Matrix fov = vrb::Matrix::PerspectiveMatrixWithResolutionDegrees(glWidth / 2, glHeight,
                                                                            60.0f, -1.0f,
                                                                            near,
                                                                            far);
      float left(0.0f), right(0.0f), top(0.0f), bottom(0.0f), n2(0.0f), f2(0.0f);
      fov.DecomposePerspectiveDegrees(left, right, top, bottom, n2, f2);
      display->SetFieldOfView(device::Eye::Left, left, right, top, bottom);
      display->SetFieldOfView(device::Eye::Right, left, right, top, bottom);
      display->SetEyeResolution((int32_t)(glWidth / 2), glHeight);
      display->SetEyeOffset(device::Eye::Left, 0.0f, 0.0f, 0.0f);
      display->SetEyeOffset(device::Eye::Right, 0.0f, 0.0f, 0.0f);
      display->SetCapabilityFlags(device::Position | device::Orientation | device::Present | device::InlineSession | device::ImmersiveVRSession);
    }
  }
};

DeviceDelegateNoAPIPtr
DeviceDelegateNoAPI::Create(vrb::RenderContextPtr& aContext) {
  DeviceDelegateNoAPIPtr result = std::make_shared<vrb::ConcreteClass<DeviceDelegateNoAPI, DeviceDelegateNoAPI::State> >();
  result->m.context = aContext;
  result->m.Initialize();
  return result;
}

void
DeviceDelegateNoAPI::SetRenderMode(const device::RenderMode aMode) {
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
    MoveAxis(0.0f, 0.0f, 0.0f);
  }
}

device::RenderMode
DeviceDelegateNoAPI::GetRenderMode() {
  return m.renderMode;
}

void
DeviceDelegateNoAPI::RegisterImmersiveDisplay(ImmersiveDisplayPtr aDisplay) {
  m.display = aDisplay;
  if (m.display) {
    m.display->SetDeviceName("NoAPI");
    m.UpdateDisplay();
    m.display->CompleteEnumeration();
  }
}

GestureDelegateConstPtr
DeviceDelegateNoAPI::GetGestureDelegate() {
  return nullptr;
}
vrb::CameraPtr
DeviceDelegateNoAPI::GetCamera(const device::Eye) {
  return m.camera;
}

const vrb::Matrix&
DeviceDelegateNoAPI::GetHeadTransform() const {
  return m.camera->GetTransform();
}

const vrb::Matrix&
DeviceDelegateNoAPI::GetReorientTransform() const {
  static vrb::Matrix identity(vrb::Matrix::Identity());
  return identity;
}

void
DeviceDelegateNoAPI::SetReorientTransform(const vrb::Matrix& aMatrix) {
  // Ignore reorient transform
}

void
DeviceDelegateNoAPI::Reorient() {
  // Ignore reorient
}

void
DeviceDelegateNoAPI::SetClearColor(const vrb::Color& aColor) {
  m.clearColor = aColor;
}

void
DeviceDelegateNoAPI::SetClipPlanes(const float aNear, const float aFar) {
  m.camera->SetClipRange(aNear, aFar);
  m.near = aNear;
  m.far = aFar;
  m.UpdateDisplay();
}

void
DeviceDelegateNoAPI::SetControllerDelegate(ControllerDelegatePtr& aController) {
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
DeviceDelegateNoAPI::ReleaseControllerDelegate() {
  m.controller = nullptr;
}

int32_t
DeviceDelegateNoAPI::GetControllerModelCount() const {
  return 0;
}

const std::string
DeviceDelegateNoAPI::GetControllerModelName(const int32_t) const {
  static const std::string name;
  return name;
}

void
DeviceDelegateNoAPI::ProcessEvents() {
  m.camera->SetTransform(m.headingMatrix.PostMultiply(m.pitchMatrix).Translate(m.position));
}

void
DeviceDelegateNoAPI::StartFrame(const FramePrediction aPrediction) {
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
}

void
DeviceDelegateNoAPI::BindEye(const device::Eye aEye) {
  if (m.glWidth > m.glHeight) {
    m.camera->SetFieldOfView(60.0f, -1.0f);
  } else {
    m.camera->SetFieldOfView(-1.0f, 60.0f);
  }
  if (m.renderMode == device::RenderMode::Immersive) {
    m.camera->SetViewport(m.glWidth / 2, m.glHeight);
    if (aEye == device::Eye::Left) {
      VRB_GL_CHECK(glViewport(m.glWidth / 4, 0, m.glWidth / 2, m.glHeight));
    } else {
      VRB_GL_CHECK(glViewport(m.glWidth / 4, 0, m.glWidth / 2, m.glHeight));
    }
  } else {
    m.camera->SetViewport(m.glWidth, m.glHeight);
    VRB_GL_CHECK(glViewport(0, 0, m.glWidth, m.glHeight));
  }
}

void
DeviceDelegateNoAPI::EndFrame(const FrameEndMode aMode) {
  // noop
}

void
DeviceDelegateNoAPI::InitializeJava(JNIEnv* aEnv, jobject aActivity) {
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
DeviceDelegateNoAPI::ShutdownJava() {
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
DeviceDelegateNoAPI::SetViewport(const int aWidth, const int aHeight) {
  m.glWidth = aWidth;
  m.glHeight = aHeight;
  BindEye(device::Eye::Left);
  m.UpdateDisplay();
}


void
DeviceDelegateNoAPI::Pause() {

}

void
DeviceDelegateNoAPI::Resume() {

}

void
DeviceDelegateNoAPI::MoveAxis(const float aX, const float aY, const float aZ) {
  if (VRB_IS_ZERO(aX) && VRB_IS_ZERO(aY) && VRB_IS_ZERO(aZ)) {
    if (m.renderMode == device::RenderMode::Immersive) {
      m.position = vrb::Vector();
    } else {
      m.position = GetHomePosition();
    }
    m.heading = 0.0f;
    m.pitch = 0.0f;
    m.headingMatrix = vrb::Matrix::Identity();
    m.pitchMatrix = vrb::Matrix::Identity();
    ProcessEvents();
    return;
  }
  VRB_LOG("pos: %s heading: %f pitch: %f", m.position.ToString().c_str(), m.heading, m.pitch);
  m.position += m.headingMatrix.MultiplyDirection(vrb::Vector(aX, aY, aZ));
  ProcessEvents();
}

void
DeviceDelegateNoAPI::RotateHeading(const float aHeading) {
  static const vrb::Vector sUp(0.0f, 1.0f, 0.0f);
  m.heading += aHeading;
  m.headingMatrix = vrb::Matrix::Rotation(sUp, m.heading);
  ProcessEvents();
}

void
DeviceDelegateNoAPI::RotatePitch(const float aPitch) {
  static const vrb::Vector sLeft(1.0f, 0.0f, 0.0f);
  m.pitch += aPitch;
  m.pitchMatrix = vrb::Matrix::Rotation(sLeft, m.pitch);
  ProcessEvents();
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
DeviceDelegateNoAPI::TouchEvent(const bool aDown, const float aX, const float aY) {
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
  const float viewportWidth = m.camera->GetViewportWidth();
  const float viewportHeight = m.camera->GetViewportHeight();
  if ((viewportWidth <= 0.0f) || (viewportHeight <= 0.0f)) {
    return;
  }
  const float xModifier = (m.renderMode == device::RenderMode::Immersive ? viewportWidth / 2.0f : 0.0f);
  const float width = Clamp((((aX - xModifier) / viewportWidth) * 2.0f) - 1.0f);
  const float height = (((viewportHeight - aY) / viewportHeight) * 2.0f) - 1.0f;

  vrb::Vector start(width, height, -1.0f);
  vrb::Vector end(width, height, 1.0f);
  vrb::Matrix inversePerspective = m.camera->GetPerspective().Inverse();
  start = inversePerspective.MultiplyPosition(start);
  end = inversePerspective.MultiplyPosition(end);
  vrb::Matrix view = m.camera->GetTransform();
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
DeviceDelegateNoAPI::ControllerButtonPressed(const bool aDown) {
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

bool
DeviceDelegateNoAPI::GotFirstValidPose() const {
  return true;
}

DeviceDelegateNoAPI::DeviceDelegateNoAPI(State& aState) : m(aState) {}
DeviceDelegateNoAPI::~DeviceDelegateNoAPI() { m.Shutdown(); }

} // namespace crow
