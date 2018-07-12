/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "Controller.h"

#include "vrb/ConcreteClass.h"
#include "vrb/Matrix.h"
#include "vrb/Transform.h"

using namespace vrb;

namespace crow {

Controller::Controller() :
    index(-1), enabled(false), widget(0),
    pointerX(0.0f), pointerY(0.0f),
    buttonState(0), lastButtonState(0),
    touched(false), wasTouched(false),
    touchX(0.0f), touchY(0.0f),
    lastTouchX(0.0f), lastTouchY(0.0f),
    scrollDeltaX(0.0f), scrollDeltaY(0.0f),
    transformMatrix(Matrix::Identity()) {}

Controller::Controller(const Controller& aController) {
  *this = aController;
}

Controller::~Controller() {
  Reset();
}

Controller&
Controller::operator=(const Controller& aController) {
  index = aController.index;
  enabled = aController.enabled;
  widget = aController.widget;
  pointerX = aController.pointerX;
  pointerY = aController.pointerY;
  buttonState = aController.buttonState;
  lastButtonState = aController.lastButtonState;
  touched = aController.touched;
  wasTouched = aController.wasTouched;
  touchX = aController.touchX;
  touchY= aController.touchY;
  lastTouchX = aController.lastTouchX;
  lastTouchY = aController.lastTouchY;
  scrollDeltaX = aController.scrollDeltaX;
  scrollDeltaY = aController.scrollDeltaY;
  transform = aController.transform;
  transformMatrix = aController.transformMatrix;
  return *this;
}

void
Controller::Reset() {
  index = -1;
  enabled = false;
  widget = 0;
  pointerX = pointerY = 0.0f;
  buttonState = lastButtonState = 0;
  touched = wasTouched = false;
  touchX = touchY = 0.0f;
  lastTouchX = lastTouchY = 0.0f;
  scrollDeltaX = scrollDeltaY = 0.0f;
  if (transform) {
    transform = nullptr;
  }
  transformMatrix = Matrix::Identity();
}

} // namespace crow