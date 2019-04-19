/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_DEVICE_H
#define VRBROWSER_DEVICE_H

namespace crow {
namespace device {
typedef uint16_t CapabilityFlags;
const CapabilityFlags Position = 1 << 1;
const CapabilityFlags Orientation = 1 << 2;
const CapabilityFlags Present = 1 << 3;
const CapabilityFlags AngularAcceleration = 1 << 5;
const CapabilityFlags LinearAcceleration = 1 << 6;
const CapabilityFlags StageParameters = 1 << 7;
const CapabilityFlags MountDetection = 1 << 8;
const CapabilityFlags PositionEmulated = 1 << 9;
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
