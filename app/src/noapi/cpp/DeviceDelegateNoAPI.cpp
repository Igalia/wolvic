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

#include <vector>

namespace crow {

static const int32_t kControllerIndex = 0;
static vrb::Vector sHomePosition(0.0f, 1.7f, 0.0f);

struct DeviceDelegateNoAPI::State {
  vrb::RenderContextWeak context;
  ControllerDelegatePtr controller;
  vrb::CameraSimplePtr camera;
  vrb::Color clearColor;
  float heading;
  vrb::Matrix headingMatrix;
  vrb::Vector position;
  bool clicked;
  State()
      : headingMatrix(vrb::Matrix::Identity())
      , position(sHomePosition)
      , clicked(false)
  {
  }

  void Initialize() {
    vrb::RenderContextPtr render = context.lock();
    if (!render) {
      return;
    }
    vrb::CreationContextPtr create = render->GetRenderThreadCreationContext();
    camera = vrb::CameraSimple::Create(create);
    camera->SetTransform(vrb::Matrix::Translation(sHomePosition));
  }

  void Shutdown() {
  }
};

DeviceDelegateNoAPIPtr
DeviceDelegateNoAPI::Create(vrb::RenderContextPtr& aContext) {
  DeviceDelegateNoAPIPtr result = std::make_shared<vrb::ConcreteClass<DeviceDelegateNoAPI, DeviceDelegateNoAPI::State> >();
  result->m.context = aContext;
  result->m.Initialize();
  return result;
}

GestureDelegateConstPtr
DeviceDelegateNoAPI::GetGestureDelegate() {
  return nullptr;
}
vrb::CameraPtr
DeviceDelegateNoAPI::GetCamera(const CameraEnum) {
  return m.camera;
}

const vrb::Matrix&
DeviceDelegateNoAPI::GetHeadTransform() const {
  return m.camera->GetTransform();
}

void
DeviceDelegateNoAPI::SetClearColor(const vrb::Color& aColor) {
  m.clearColor = aColor;
}

void
DeviceDelegateNoAPI::SetClipPlanes(const float aNear, const float aFar) {
  m.camera->SetClipRange(aNear, aFar);
}

void
DeviceDelegateNoAPI::SetControllerDelegate(ControllerDelegatePtr& aController) {
  m.controller = aController;
  m.controller->CreateController(0, -1);
  m.controller->SetEnabled(kControllerIndex, true);
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
  static const std::string name("");
  return name;
}

void
DeviceDelegateNoAPI::ProcessEvents() {
  m.camera->SetTransform(m.headingMatrix.Translate(m.position));
}

void
DeviceDelegateNoAPI::StartFrame() {
  VRB_GL_CHECK(glClearColor(m.clearColor.Red(), m.clearColor.Green(), m.clearColor.Blue(), m.clearColor.Alpha()));
  VRB_GL_CHECK(glEnable(GL_DEPTH_TEST));
  VRB_GL_CHECK(glEnable(GL_CULL_FACE));
  VRB_GL_CHECK(glEnable(GL_BLEND));
  VRB_GL_CHECK(glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT));
}

void
DeviceDelegateNoAPI::BindEye(const CameraEnum) {
  // noop
}

void
DeviceDelegateNoAPI::EndFrame() {
  // noop
}

void
DeviceDelegateNoAPI::SetViewport(const int aWidth, const int aHeight) {
  m.camera->SetViewport(aWidth, aHeight);
  if (aWidth > aHeight) {
    m.camera->SetFieldOfView(60.0f, -1.0f);
  } else {
    m.camera->SetFieldOfView(-1.0f, 60.0f);
  }
  VRB_LOG("********* SETTING VIEWPORT %d %d", aWidth, aHeight);
  VRB_GL_CHECK(glViewport(0, 0, aWidth, aHeight));
}


void
DeviceDelegateNoAPI::Pause() {

}

void
DeviceDelegateNoAPI::Resume() {

}

void
DeviceDelegateNoAPI::MoveAxis(const float aX, const float aY, const float aZ) {
  if (!aX && !aY && !aZ) {
    m.position = sHomePosition;
    m.heading = 0.0f;
    m.headingMatrix = vrb::Matrix::Identity();
    return;
  }
  m.position += m.headingMatrix.MultiplyDirection(vrb::Vector(aX, aY, aZ));
}

static const vrb::Vector sUp(0.0f, 1.0f, 0.0f);

void
DeviceDelegateNoAPI::RotateHeading(const float aHeading) {
  m.heading += aHeading;
  m.headingMatrix = vrb::Matrix::Rotation(sUp, m.heading);
}


static const vrb::Vector sForward(0.0f, 0.0f, -1.0f);

void
DeviceDelegateNoAPI::TouchEvent(const bool aDown, const float aX, const float aY) {
  if (!m.controller) {
    return;
  }
  if (aDown != m.clicked) {
    m.controller->SetButtonState(kControllerIndex, ControllerDelegate::BUTTON_TRIGGER, aDown);
    m.clicked = aDown;
  }
  const float viewportWidth = m.camera->GetViewportWidth();
  const float viewportHeight = m.camera->GetViewportHeight();
  if ((viewportWidth <= 0.0f) || (viewportHeight <= 0.0f)) {
    return;
  }
  const float width = ((aX / viewportWidth) * 2.0f) - 1.0f;
  const float height = (((viewportHeight - aY) / viewportHeight) * 2.0f) - 1.0f;

  vrb::Vector start(width, height, -1.0f);
  vrb::Vector end(width, height, 1.0f);
  vrb::Matrix inversePerspective = m.camera->GetPerspective().Inverse();
  start = inversePerspective.MultiplyPosition(start);
  end = inversePerspective.MultiplyPosition(end);
  //VRB_LOG("1 Down: %s start:%s end:%s",(aDown?"TRUE":"FALSE"),start.ToString().c_str(), end.ToString().c_str());

  vrb::Matrix view = m.camera->GetTransform();
  start = view.MultiplyPosition(start);
  end = view.MultiplyPosition(end);
  //VRB_LOG("2 Down: %s start:%s end:%s",(aDown?"TRUE":"FALSE"),start.ToString().c_str(), end.ToString().c_str());
  const vrb::Vector direction = (end - start).Normalize();
  const vrb::Vector up = sForward.Cross(direction);
  const float angle = acosf(sForward.Dot(direction));
  vrb::Matrix transform = vrb::Matrix::Rotation(up, angle);
  transform.TranslateInPlace(start);
  m.controller->SetTransform(kControllerIndex, transform);
}

DeviceDelegateNoAPI::DeviceDelegateNoAPI(State& aState) : m(aState) {}
DeviceDelegateNoAPI::~DeviceDelegateNoAPI() { m.Shutdown(); }

} // namespace crow
