/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_ELBOWMODEL_H
#define VRBROWSER_ELBOWMODEL_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"
#include <memory>

namespace crow {

class ElbowModel;
typedef std::shared_ptr<ElbowModel> ElbowModelPtr;

class ElbowModel {
public:
  enum class HandEnum { Left, Right, None };
  static ElbowModelPtr Create();
  const vrb::Matrix& GetTransform(const HandEnum aHand, const vrb::Matrix& aHeadTransform, const vrb::Matrix& aDeviceRotation);
protected:
  struct State;
  ElbowModel(State& aState);
  ~ElbowModel() = default;
private:
  State& m;
  ElbowModel() = delete;
  VRB_NO_DEFAULTS(ElbowModel)
};

} // namespace crow

#endif //VRBROWSER_ELBOWMODEL_H
