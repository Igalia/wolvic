/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "ElbowModel.h"
#include "vrb/ConcreteClass.h"
#include "vrb/Matrix.h"
#include "vrb/Vector.h"

struct ElbowModel::State {
  vrb::Vector elbowOffset;
  vrb::Vector lowerArm;
  vrb::Matrix result;
  State()
      : elbowOffset(0.2f, -0.3f, -0.15f)
      , lowerArm(0.0f, 0.0f, -0.4f)
  {}
  void Initialize(const HandEnum aHand) {
    if (aHand == HandEnum::Left) {
      elbowOffset.x() = -elbowOffset.x();
    }
  }
};

ElbowModelPtr
ElbowModel::Create(const HandEnum aHand) {
  ElbowModelPtr result = std::make_shared<vrb::ConcreteClass<ElbowModel, ElbowModel::State> >();
  result->m.Initialize(aHand);
  return result;
}

const vrb::Matrix&
ElbowModel::GetTransform(const vrb::Matrix& aHeadTransform, const vrb::Matrix& aDeviceRotation) {
  vrb::Vector arm = aDeviceRotation.MultiplyDirection(m.lowerArm);
  vrb::Vector offset = aHeadTransform.MultiplyPosition(m.elbowOffset);
  m.result = aDeviceRotation.PreMultiply(vrb::Matrix::Position(offset + arm));
  return m.result; // .TranslateInPlace(offset);
}

ElbowModel::ElbowModel(State& aState) : m(aState) {}
ElbowModel::~ElbowModel() {}