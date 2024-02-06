/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "ElbowModel.h"
#include "vrb/ConcreteClass.h"
#include "vrb/Matrix.h"
#include "vrb/Vector.h"

namespace crow {

struct ElbowModel::State {
  const vrb::Vector rightElbowOffset;
  const vrb::Vector leftElbowOffset;
  const vrb::Vector centerElbowOffset;
  const vrb::Vector lowerArm;
  vrb::Matrix result;

  State()
      : rightElbowOffset(0.2f, -0.3f, -0.15f)
      , leftElbowOffset(-rightElbowOffset.x(), rightElbowOffset.y(), rightElbowOffset.z())
      , centerElbowOffset(0.f, rightElbowOffset.y(), rightElbowOffset.z())
      , lowerArm(0.0f, 0.0f, -0.4f) {}
};

ElbowModelPtr
ElbowModel::Create() {
  return std::make_shared<vrb::ConcreteClass<ElbowModel, ElbowModel::State> >();
}

const vrb::Matrix&
ElbowModel::GetTransform(const HandEnum aHand, const vrb::Matrix& aHeadTransform, const vrb::Matrix& aDeviceRotation) {
  vrb::Vector arm = aDeviceRotation.MultiplyDirection(m.lowerArm);
  vrb::Vector offset;
  switch (aHand) {
    case HandEnum::Left:
      offset = aHeadTransform.MultiplyPosition(m.leftElbowOffset);
      break;
    case HandEnum::Right:
      offset = aHeadTransform.MultiplyPosition(m.rightElbowOffset);
      break;
    case HandEnum::None:
      offset = aHeadTransform.MultiplyPosition(m.centerElbowOffset);
  }
  m.result = aDeviceRotation.PreMultiply(vrb::Matrix::Position(offset + arm));
  return m.result;
}

ElbowModel::ElbowModel(State& aState) : m(aState) {}

} // namespace crow