/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "DeviceUtils.h"
#include "vrb/Matrix.h"
#include "vrb/Quaternion.h"
#include "vrb/Transform.h"
#include "vrb/Vector.h"
#include "vrb/VertexArray.h"

namespace crow {

std::unordered_map<std::string, device::DeviceType> DeviceUtils::deviceNamesMap;

vrb::Matrix
DeviceUtils::CalculateReorientationMatrix(const vrb::Matrix& aHeadTransform, const vrb::Vector& aHeightPosition) {
  return CalculateReorientationMatrixWithThreshold(aHeadTransform, aHeightPosition, 0.2f, 0.5f, 0.35f);
}

vrb::Matrix
DeviceUtils::CalculateReorientationMatrixOnHeadLock(const vrb::Matrix& aHeadTransform, const vrb::Vector& aHeightPosition) {
  return CalculateReorientationMatrixWithThreshold(aHeadTransform, aHeightPosition, 0.0f, 0.0f, 0.0f);
}

vrb::Matrix
DeviceUtils::CalculateReorientationMatrixWithThreshold(const vrb::Matrix& aHeadTransform, const vrb::Vector& aHeightPosition, const float kPitchUpThreshold, const float kPitchDownThreshold, const float kRollThreshold) {
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

vrb::GeometryPtr DeviceUtils::GetSphereGeometry(vrb::CreationContextPtr& context, uint32_t resolution, float radius)
{
    vrb::VertexArrayPtr array = vrb::VertexArray::Create(context);
    vrb::GeometryPtr geometry = vrb::Geometry::Create(context);
    vrb::TransformPtr transform = vrb::Transform::Create(context);
    std::vector<int> indices;

    int rings = (int) resolution;
    int sectors = (int) resolution;
    float const R = 1.0f / ((float) rings - 1.0f);
    float const S = 1.0f / ((float) sectors - 1.0f);

    for (int r = 0; r < rings; r++) {
        for (int s = 0; s < sectors; s++) {
            float const y = sinf(- (float) M_PI_2 + (float) M_PI * (float) r * R);
            float const x = cosf(2.0f * (float) M_PI * (float) s * S) * sinf(M_PI * (float) r * R);
            float const z = sinf(2.0f * (float) M_PI * (float) s * S) * sinf(M_PI * (float) r * R);
            array->AppendVertex(vrb::Vector(x * radius, y * radius, z * radius));
            array->AppendNormal(vrb::Vector(x, y, z).Normalize());
            array->AppendUV(vrb::Vector((float)s * S, (float)r * R, 0.0));
        }
    }

    geometry->SetVertexArray(array);

    for (int r = 0; r < rings; r++) {
        for (int s = 0; s < sectors; s++) {
            if (r != 0) {
                indices.push_back(r * sectors + s);
                indices.push_back((r + 1) * sectors + s);
                indices.push_back(r * sectors + (s + 1));
                geometry->AddFace(indices, indices, indices);
                indices.clear();
            }

            if (r != rings - 1) {
                indices.push_back(r * sectors + (s + 1));
                indices.push_back((r + 1) * sectors + s);
                indices.push_back((r + 1) * sectors + (s + 1));
                geometry->AddFace(indices, indices, indices);
                indices.clear();
            }
        }
    }

    return std::move(geometry);
}

device::DeviceType DeviceUtils::GetDeviceTypeFromSystem(bool is6DoF) {
    char model[128];
    int length = PopulateDeviceModelString(model);

#ifdef HVR
    // Huawei glasses can be attached to multiple different phones, so we basically cannot filter
    // by device type in this case.
    return is6DoF ? device::HVR6DoF : device::HVR3DoF;
#endif

    if (deviceNamesMap.empty()) {
        deviceNamesMap.emplace("Quest", device::OculusQuest);
        deviceNamesMap.emplace("Quest 2", device::OculusQuest2);
        deviceNamesMap.emplace("Quest 3", device::MetaQuest3);
        // So far no need to differentiate between Pico4 and Pico4E
        deviceNamesMap.emplace("A8110", device::Pico4x);
        deviceNamesMap.emplace("Lynx-R1", device::LynxR1);
        deviceNamesMap.emplace("motorola edge 30 pro", device::LenovoA3);
        deviceNamesMap.emplace("Quest Pro", device::MetaQuestPro);
        deviceNamesMap.emplace("VRX", device::LenovoVRX);
        deviceNamesMap.emplace("Magic Leap 2", device::MagicLeap2);
        deviceNamesMap.emplace("Pico Neo 3", device::PicoNeo3);
    }

    auto device = deviceNamesMap.find(model);
    if (device == deviceNamesMap.end()) {
        VRB_WARN("Device %s is not supported", model);
        return device::UnknownType;
    }
    return device->second;
}

}

