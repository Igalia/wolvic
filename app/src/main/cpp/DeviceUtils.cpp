/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "DeviceUtils.h"
#include "vrb/Matrix.h"
#include "vrb/Quaternion.h"

namespace crow {

vrb::Matrix
DeviceUtils::CalculateReorientationMatrix(const vrb::Matrix& aHeadTransform, const vrb::Vector& aHeightPosition) {
  const float kPitchUpThreshold = 0.2f;
  const float kPitchDownThreshold = 0.5f;
  const float kRollThreshold = 0.35f;

  float rx, ry, rz;
  vrb::Quaternion quat(aHeadTransform);
  quat.ToEulerAngles(rx, ry, rz);

  // Use some thresholds to use default roll and yaw values when the rotation is not enough.
  // This makes easier setting a default orientation easier for the user.
  if (rx > 0 && rx < kPitchDownThreshold) {
    rx = 0.0f;
  } else if (rx < 0 && fabsf(rx) < kPitchUpThreshold) {
    rx = 0.0f;
  } else {
    rx -= 0.05f; // It feels better with some extra margin
  }

  if (fabsf(rz) < kRollThreshold) {
    rz = 0.0f;
  }

  if (rx == 0.0f && rz == 0.0f) {
    return vrb::Matrix::Identity();
  }

  quat.SetFromEulerAngles(rx, ry, rz);
  vrb::Matrix result = vrb::Matrix::Rotation(quat.Inverse());

  // Rotate UI reorientation matrix from origin so user height translation doesn't affect the sphere.
  result.PreMultiplyInPlace(vrb::Matrix::Position(aHeightPosition));
  result.PostMultiplyInPlace(vrb::Matrix::Position(-aHeightPosition));

  return result;

}

void DeviceUtils::GetTargetImmersiveSize(const uint32_t aRequestedWidth,
                                         const uint32_t aRequestedHeight,
                                         const uint32_t aRecommendedWidth,
                                         const uint32_t aRecommendedHeight,
                                         const uint32_t aMaxWidth, const uint32_t aMaxHeight,
                                         uint32_t& aTargetWidth, uint32_t& aTargetHeight) {
  const uint32_t minWidth = aRecommendedWidth / 2;
  const uint32_t minHeight = aRecommendedHeight / 2;
  aTargetWidth = (uint32_t) fmaxf(fminf(aRequestedWidth, aMaxWidth), minWidth);
  aTargetHeight = (uint32_t) fmaxf(fminf(aRequestedHeight, aMaxHeight), minHeight);
}


}

