/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_DEVICE_H
#define VRBROWSER_DEVICE_H

#include <map>
#include <stdint.h>
#include "SystemUtils.h"

namespace crow {
namespace device {
typedef uint32_t CapabilityFlags;
const CapabilityFlags Position = 1u << 1u;
const CapabilityFlags Orientation = 1u << 2u;
const CapabilityFlags Present = 1u << 3u;
const CapabilityFlags AngularAcceleration = 1u << 5u;
const CapabilityFlags LinearAcceleration = 1u << 6u;
const CapabilityFlags StageParameters = 1u << 7u;
const CapabilityFlags MountDetection = 1u << 8u;
const CapabilityFlags PositionEmulated = 1u << 9u;
const CapabilityFlags InlineSession = 1u << 10u;
const CapabilityFlags ImmersiveVRSession = 1u << 11u;
const CapabilityFlags ImmersiveARSession = 1u << 12u;
const CapabilityFlags GripSpacePosition = 1u << 13u;
enum class Eye { Left, Right };
enum class RenderMode { StandAlone, Immersive };
enum class CPULevel { Normal = 0, High };
enum class BlendMode { Opaque, AlphaBlend, Additive };
const int32_t EyeCount = 2;
inline int32_t EyeIndex(const Eye aEye) { return aEye == Eye::Left ? 0 : 1; }
// The type values need to match those defined in DeviceType.java
typedef int32_t DeviceType;
const DeviceType UnknownType = 0;
const DeviceType OculusGo = 1;
const DeviceType OculusQuest = 2;
const DeviceType ViveFocus = 3;
const DeviceType ViveFocusPlus = 4;
const DeviceType PicoGaze = 5;
const DeviceType PicoNeo2 = 6;
const DeviceType PicoG2 = 7;
const DeviceType PicoNeo3 = 8;
const DeviceType OculusQuest2 = 9;
const DeviceType HVR3DoF = 10;
const DeviceType HVR6DoF = 11;
const DeviceType Pico4x = 12;
const DeviceType MetaQuestPro = 13;
const DeviceType LynxR1 = 14;
const DeviceType LenovoA3 = 15;
const DeviceType LenovoVRX = 16;
const DeviceType MagicLeap2 = 17;
const DeviceType MetaQuest3 = 18;
const DeviceType VisionGlass = 19;
const DeviceType Pico4U = 20;
const DeviceType PfdmYVR1 = 21;
const DeviceType PfdmYVR2 = 22;
const DeviceType PfdmMR = 23;

enum class TargetRayMode : uint8_t { Gaze, TrackedPointer, Screen };

// Placeholder buttons for WebXR
// https://www.w3.org/TR/webxr-gamepads-module-1/#xr-standard-gamepad-mapping
const uint8_t kImmersiveButtonTrigger = 0;
const uint8_t kImmersiveButtonSqueeze = 1;
const uint8_t kImmersiveButtonTouchpad = 2;
const uint8_t kImmersiveButtonThumbstick = 3;
const uint8_t kImmersiveButtonA = 4;
const uint8_t kImmersiveButtonB = 5;
const uint8_t kImmersiveButtonThumbrest = 6;

// Placeholder axes for WebXR
// https://www.w3.org/TR/webxr-gamepads-module-1/#xr-standard-gamepad-mapping
const uint8_t kImmersiveAxisTouchpadX = 0;
const uint8_t kImmersiveAxisTouchpadY = 1;
const uint8_t kImmersiveAxisThumbstickX = 2;
const uint8_t kImmersiveAxisThumbstickY = 3;

struct EyeRect {
  float mX, mY;
  float mWidth, mHeight;
  EyeRect() : mX(0.0f), mY(0.0f), mWidth(0.0f), mHeight(0.0f) {}
  EyeRect(const float aX, const float aY, const float aWidth, const float aHeight)
      : mX(aX)
      , mY(aY)
      , mWidth(aWidth)
      , mHeight(aHeight)
  {}
  EyeRect& operator=(const EyeRect& aRect) {
    mX = aRect.mX;
    mY = aRect.mY;
    mWidth = aRect.mWidth;
    mHeight = aRect.mHeight;
    return* this;
  }

  bool IsDefault() const {
    return mX == 0.0f && mY == 0.0f && mWidth == 1.0f && mHeight == 1.0f;
  }

};

} // namespace device
} // namespace crow

#endif // VRBROWSER_DEVICE_H
