/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_DEVICE_H
#define VRBROWSER_DEVICE_H

#include <stdint.h>

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
enum class Eye { Left, Right };
enum class RenderMode { StandAlone, Immersive };
enum class CPULevel { Normal = 0, High };
const int32_t EyeCount = 2;
inline int32_t EyeIndex(const Eye aEye) { return aEye == Eye::Left ? 0 : 1; }
// The type values need to match those defined in DeviceType.java
typedef int32_t DeviceType;
const DeviceType UnknownType = 0;
const DeviceType OculusGo = 1;
const DeviceType OculusQuest = 2;
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
