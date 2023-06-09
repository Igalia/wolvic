/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#pragma once

#include "Controller.h"
#include "vrb/Camera.h"

namespace crow {

class HandModels;
typedef std::shared_ptr<HandModels> HandModelsPtr;

class HandModels {
protected:
    struct State;
public:
  static HandModelsPtr Create(vrb::CreationContextPtr& aContext);
  void Draw(const vrb::Camera& aCamera, const Controller& aController);
  HandModels(State& aState, vrb::CreationContextPtr& aContext);
  ~HandModels();
private:
  State& m;
  void InitializeGL();
  void ShutdownGL();
  void UpdateHandModel(const Controller& aController);
};

} // namespace crow
