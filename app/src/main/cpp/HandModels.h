/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#pragma once

#include "vrb/ResourceGL.h"
#include "ExternalVR.h"

namespace crow {

class HandModels;
typedef std::shared_ptr<HandModels> HandModelsPtr;

class HandModels : protected vrb::ResourceGL {
public:
  static HandModelsPtr Create(vrb::CreationContextPtr& aContext);
  void Draw(const vrb::Camera& aCamera, const Controller& aController);
  void UpdateHandModel(const Controller& aController);
protected:
  struct State;
  HandModels(State& aState, vrb::CreationContextPtr& aContext);
  ~HandModels() = default;
  void InitializeGL() override;
  void ShutdownGL() override;
private:
  State& m;
  HandModels() = delete;
  VRB_NO_DEFAULTS(HandModels)
};

} // namespace crow
