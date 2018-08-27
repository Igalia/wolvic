/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_CONTROLLER_H
#define VRBROWSER_CONTROLLER_H

#include "ControllerDelegate.h"
#include "vrb/Forward.h"
#include "vrb/Matrix.h"

namespace crow {

static const int kControllerMaxButtonCount = 4;
static const int kControllerMaxAxes = 6;

struct Controller {
  int32_t index;
  bool enabled;
  uint32_t widget;
  float pointerX;
  float pointerY;
  int32_t buttonState;
  int32_t lastButtonState;
  bool touched;
  bool wasTouched;
  float touchX;
  float touchY;
  float lastTouchX;
  float lastTouchY;
  float scrollDeltaX;
  float scrollDeltaY;
  vrb::TransformPtr transform;
  vrb::Matrix transformMatrix;
  std::string immersiveName;
  uint64_t immersivePressedState;
  uint64_t immersiveTouchedState;
  float immersiveTriggerValues[kControllerMaxButtonCount];
  uint32_t numButtons;
  float immersiveAxes[kControllerMaxAxes];
  uint32_t numAxes;
  bool leftHanded;
  bool inDeadZone;

  Controller();
  Controller(const Controller& aController);
  ~Controller();

  Controller& operator=(const Controller& aController);

  void Reset();
};

} // namespace crow

#endif //VRBROWSER_CONTROLLER_H
