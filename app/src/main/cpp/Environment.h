/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_ENVIRONMENT_H
#define VRBROWSER_ENVIRONMENT_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"

#include <memory>
#include <string>
#include <vector>
#include <functional>

namespace crow {

class Environment;
typedef std::shared_ptr<Environment> EnvironmentPtr;

class Environment {
public:
  static EnvironmentPtr Create(vrb::CreationContextPtr aContext);
  void LoadModels(const vrb::ModelLoaderAndroidPtr& aLoader);
  void Update(vrb::RenderContextPtr aContext);
  vrb::NodePtr GetRoot() const;
protected:
  struct State;
  Environment(State& aState, vrb::CreationContextPtr& aContext);
  ~Environment() = default;
private:
  State& m;
  Environment() = delete;
  VRB_NO_DEFAULTS(Environment)
};

} // namespace crow

#endif // VRBROWSER_ENVIRONMENT_H
