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
private:
  VRB_NO_DEFAULTS(DeviceUtils)
};

}

#endif //  DEVICE_UTILS_DOT_H
