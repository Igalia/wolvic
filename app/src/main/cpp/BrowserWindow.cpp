/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "BrowserWindow.h"
#include "vrb/ConcreteClass.h"

#include "vrb/Context.h"
#include "vrb/Matrix.h"
#include "vrb/Geometry.h"
#include "vrb/Vector.h"
#include "vrb/SurfaceTextureFactory.h"
#include "vrb/TextureSurface.h"

namespace crow {

struct BrowserWindow::State {
  vrb::ContextWeak context;
  std::string name;
};

BrowserWindowPtr
BrowserWindow::Create(vrb::ContextWeak aContext) {
  BrowserWindowPtr result = std::make_shared<vrb::ConcreteClass<BrowserWindow, BrowserWindow::State> >(aContext);
  return result;
}

const std::string&
BrowserWindow::GetSurfaceTextureName() const {
  return m.name;
}

void
BrowserWindow::GetSurfaceTextureSize(int32_t& aWidth, int32_t& aHeight) const {

}

void
BrowserWindow::GetWindowMinAndMax(vrb::Vector& aMin, vrb::Vector& aMax) const {

}

bool
BrowserWindow::TestControllerIntersection(const vrb::Matrix& aContorller, vrb::Vector& aResult) const {
  bool result = false;
  return result;
}

const vrb::Matrix
BrowserWindow::GetTransform() const {
  return vrb::Matrix::Identity();
}

void
BrowserWindow::SetTransform(const vrb::Matrix& aTransform) {

}

BrowserWindow::BrowserWindow(State& aState, vrb::ContextWeak& aContext) : m(aState) {
  m.context = aContext;
}

BrowserWindow::~BrowserWindow() {}

} // namespace crow