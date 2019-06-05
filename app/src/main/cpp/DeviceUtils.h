/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef DEVICE_UTILS_DOT_H
#define DEVICE_UTILS_DOT_H

#include "vrb/MacroUtils.h"
#include "vrb/Forward.h"

namespace crow {

class DeviceUtils {
public:
  static vrb::Matrix CalculateReorientationMatrix(const vrb::Matrix& aHeadTransform, const vrb::Vector& aHeightPosition);
  static void GetTargetImmersiveSize(const uint32_t aRequestedWidth, const uint32_t  aRequestedHeight,
                                     const uint32_t aRecommendedWidth, const uint32_t aRecommendedHeight,
                                     uint32_t& aTargetWidth, uint32_t& aTargetHeight) {
    GetTargetImmersiveSize(aRequestedWidth, aRequestedHeight, aRecommendedWidth, aRecommendedHeight,
                           aRecommendedWidth * 2, aRecommendedHeight * 2, aTargetWidth, aTargetHeight);
  }
  static void GetTargetImmersiveSize(const uint32_t aRequestedWidth, const uint32_t  aRequestedHeight,
                                     const uint32_t aRecommendedWidth, const uint32_t aRecommendedHeight,
                                     const uint32_t aMaxWidth, const uint32_t aMaxHeight,
                                     uint32_t& aTargetWidth, uint32_t& aTargetHeight);
private:
  VRB_NO_DEFAULTS(DeviceUtils)
};

}

#endif //  DEVICE_UTILS_DOT_H
