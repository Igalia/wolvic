/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_CONTROLLER_H
#define VRBROWSER_CONTROLLER_H

#include "ControllerDelegate.h"
#include "Device.h"
#include "vrb/Forward.h"
#include "vrb/Matrix.h"

namespace crow {

class Pointer;
typedef std::shared_ptr<Pointer> PointerPtr;

static const int kControllerMaxButtonCount = 7;
static const int kControllerMaxAxes = 6;

struct Controller {
  int32_t index;
  bool enabled;
  bool focused;
  uint32_t widget;
  float pointerX;
  float pointerY;
  vrb::Vector pointerWorldPoint;
  uint32_t buttonState;
  uint32_t lastButtonState;
  bool touched;
  bool wasTouched;
  float touchX;
  float touchY;
  float lastTouchX;
  float lastTouchY;
  double scrollStart;
  float scrollDeltaX;
  float scrollDeltaY;
  vrb::TransformPtr transform;
  vrb::TogglePtr beamToggle;
  vrb::TransformPtr beamParent;
  PointerPtr pointer;
  vrb::Matrix transformMatrix;
  vrb::Matrix beamTransformMatrix;
  vrb::Matrix immersiveBeamTransform;
  std::string immersiveName;
  uint64_t immersivePressedState;
  uint64_t immersiveTouchedState;
  float immersiveTriggerValues[kControllerMaxButtonCount];
  uint32_t numButtons;
  float immersiveAxes[kControllerMaxAxes];
  uint32_t numAxes;
  uint32_t numHaptics;
  device::DeviceType type;
  device::TargetRayMode targetRayMode;
  float inputFrameID;
  float pulseDuration;
  float pulseIntensity;

  bool leftHanded;
  bool inDeadZone;
  double lastHoverEvent;
  device::CapabilityFlags deviceCapabilities;

  std::string profile;
  uint64_t selectActionStartFrameId;
  uint64_t selectActionStopFrameId;
  uint64_t squeezeActionStartFrameId;
  uint64_t squeezeActionStopFrameId;

  int32_t batteryLevel;

  vrb::Vector StartPoint() const;
  vrb::Vector Direction() const;

  Controller();
  Controller(const Controller& aController);
  ~Controller();

  Controller& operator=(const Controller& aController);

  void Reset();
  void DetachRoot();
};

} // namespace crow

#endif //VRBROWSER_CONTROLLER_H
