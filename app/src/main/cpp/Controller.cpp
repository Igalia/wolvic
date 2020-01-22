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

Controller::Controller() { 
  Reset();
}

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
  focused = aController.focused;
  widget = aController.widget;
  pointerX = aController.pointerX;
  pointerY = aController.pointerY;
  pointerWorldPoint = aController.pointerWorldPoint;
  buttonState = aController.buttonState;
  lastButtonState = aController.lastButtonState;
  touched = aController.touched;
  wasTouched = aController.wasTouched;
  touchX = aController.touchX;
  touchY= aController.touchY;
  lastTouchX = aController.lastTouchX;
  lastTouchY = aController.lastTouchY;
  scrollStart = aController.scrollStart;
  scrollDeltaX = aController.scrollDeltaX;
  scrollDeltaY = aController.scrollDeltaY;
  transform = aController.transform;
  beamToggle = aController.beamToggle;
  beamParent = aController.beamParent;
  pointer = aController.pointer;
  transformMatrix = aController.transformMatrix;
  beamTransformMatrix = aController.beamTransformMatrix;
  immersiveName = aController.immersiveName;
  immersivePressedState = aController.immersivePressedState;
  immersiveTouchedState = aController.immersiveTouchedState;
  memcpy(immersiveTriggerValues, aController.immersiveTriggerValues, sizeof(immersiveTriggerValues));
  numButtons = aController.numButtons;
  memcpy(immersiveAxes, aController.immersiveAxes, sizeof(immersiveAxes));
  numAxes = aController.numAxes;
  numHaptics = aController.numHaptics;
  inputFrameID = aController.inputFrameID;
  pulseDuration = aController.pulseDuration;
  pulseIntensity = aController.pulseIntensity;
  leftHanded = aController.leftHanded;
  inDeadZone = aController.inDeadZone;
  lastHoverEvent = aController.lastHoverEvent;
  return *this;
}

void
Controller::Reset() {
  index = -1;
  enabled = false;
  focused = false;
  widget = 0;
  pointerX = pointerY = 0.0f;
  pointerWorldPoint = vrb::Vector(0.0f, 0.0f, 0.0f);
  buttonState = lastButtonState = 0;
  touched = wasTouched = false;
  touchX = touchY = 0.0f;
  lastTouchX = lastTouchY = 0.0f;
  scrollStart = -1.0;
  scrollDeltaX = scrollDeltaY = 0.0f;
  transform = nullptr;
  beamToggle = nullptr;
  beamParent = nullptr;
  pointer = nullptr;
  transformMatrix = Matrix::Identity();
  beamTransformMatrix = Matrix::Identity();
  immersiveName.clear();
  immersivePressedState = 0;
  immersiveTouchedState = 0;
  memset(immersiveTriggerValues, 0, sizeof(immersiveTriggerValues));
  numButtons = 0;
  memset(immersiveAxes, 0, sizeof(immersiveAxes));
  numAxes = 0;
  numHaptics = 0;
  inputFrameID = 0;
  pulseDuration = 0.0f;
  pulseIntensity = 0.0f;
  leftHanded = false;
  inDeadZone = true;
  lastHoverEvent = 0.0;
}

vrb::Vector Controller::StartPoint() const {
  return transformMatrix.MultiplyPosition(beamTransformMatrix.MultiplyPosition(vrb::Vector()));
}

vrb::Vector Controller::Direction() const {
  return transformMatrix.MultiplyDirection(beamTransformMatrix.MultiplyDirection(vrb::Vector(0.0f, 0.0f, -1.0f)));
}

void
Controller::DetachRoot() {
  if (transform) {
    transform->RemoveFromParents();
  }
}

} // namespace crow