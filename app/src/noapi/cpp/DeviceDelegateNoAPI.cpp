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
#include "vrb/Vector.h"

#include <vector>

namespace crow {

static vrb::Vector sHomePosition(0.0f, 1.7f, 0.0f);

struct DeviceDelegateNoAPI::State {
  vrb::ContextWeak context;
  vrb::Matrix controller;
  vrb::CameraSimplePtr camera;
  vrb::Color clearColor;
  bool clicked;
  State()
      : controller(vrb::Matrix::Identity())
      , clicked(false)
  {
  }

  void Initialize() {
    camera = vrb::CameraSimple::Create(context);
    camera->SetTransform(vrb::Matrix::Translation(sHomePosition));
  }

  void Shutdown() {
  }
};

DeviceDelegateNoAPIPtr
DeviceDelegateNoAPI::Create(vrb::ContextWeak aContext) {
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
DeviceDelegateNoAPI::GetCamera(const CameraEnum aWhich) {
  return m.camera;
}

void
DeviceDelegateNoAPI::SetClearColor(const vrb::Color& aColor) {
  m.clearColor = aColor;
}

void
DeviceDelegateNoAPI::SetClipPlanes(const float aNear, const float aFar) {
  m.camera->SetClipRange(aNear, aFar);
}

int32_t
DeviceDelegateNoAPI::GetControllerCount() const {
  return 1;
}

const std::string
DeviceDelegateNoAPI::GetControllerModelName(const int32_t) const {
  static const std::string name("");
  return name;
}

void
DeviceDelegateNoAPI::ProcessEvents() {
}

const vrb::Matrix&
DeviceDelegateNoAPI::GetControllerTransform(const int32_t aWhichController) {
  return m.controller;
}

bool
DeviceDelegateNoAPI::GetControllerButtonState(const int32_t aWhichController, const int32_t aWhichButton, bool& aChangedState) {
  return m.clicked;
}

void
DeviceDelegateNoAPI::StartFrame() {
  VRB_CHECK(glClearColor(m.clearColor.Red(), m.clearColor.Green(), m.clearColor.Blue(), m.clearColor.Alpha()));
  VRB_CHECK(glEnable(GL_DEPTH_TEST));
  VRB_CHECK(glEnable(GL_CULL_FACE));
  VRB_CHECK(glEnable(GL_BLEND));
  VRB_CHECK(glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT));
}

void
DeviceDelegateNoAPI::BindEye(const CameraEnum aWhich) {
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
  VRB_CHECK(glViewport(0, 0, aWidth, aHeight));
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
    m.camera->SetTransform(vrb::Matrix::Translation(sHomePosition));
    return;
  }
  vrb::Matrix translation = m.camera->GetTransform();
  translation.TranslateInPlace(vrb::Vector(aX, aY, aZ));
  m.camera->SetTransform(translation);
}

static const vrb::Vector sForward(0.0f, 0.0f, -1.0f);
void
DeviceDelegateNoAPI::TouchEvent(const bool aDown, const float aX, const float aY) {
  m.clicked = aDown;
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
  VRB_LOG("1 Down: %s start:%s end:%s",(aDown?"TRUE":"FALSE"),start.ToString().c_str(), end.ToString().c_str());

  vrb::Matrix view = m.camera->GetTransform();
  start = view.MultiplyPosition(start);
  end = view.MultiplyPosition(end);
  VRB_LOG("2 Down: %s start:%s end:%s",(aDown?"TRUE":"FALSE"),start.ToString().c_str(), end.ToString().c_str());
  const vrb::Vector direction = (end - start).Normalize();
  const vrb::Vector up = sForward.Cross(direction);
  const float angle = acosf(sForward.Dot(direction));
  m.controller = vrb::Matrix::Rotation(up, angle);
  m.controller.TranslateInPlace(start);
}



DeviceDelegateNoAPI::DeviceDelegateNoAPI(State& aState) : m(aState) {}
DeviceDelegateNoAPI::~DeviceDelegateNoAPI() { m.Shutdown(); }

} // namespace crow
