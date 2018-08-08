/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_LOADING_ANIMATION_H
#define VRBROWSER_LOADING_ANIMATION_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"

#include <memory>
#include <string>
#include <vector>
#include <functional>

namespace crow {

class LoadingAnimation;
typedef std::shared_ptr<LoadingAnimation> LoadingAnimationPtr;

class LoadingAnimation {
public:
  static LoadingAnimationPtr Create(vrb::CreationContextPtr aContext);
  void LoadModels(const vrb::ModelLoaderAndroidPtr& aLoader);
  void Update();
  vrb::NodePtr GetRoot() const;

  struct State;
  LoadingAnimation(State& aState, vrb::CreationContextPtr& aContext);
  ~LoadingAnimation();
private:
  State& m;
  LoadingAnimation() = delete;
  VRB_NO_DEFAULTS(LoadingAnimation)
};

} // namespace crow

#endif // VRBROWSER_LOADING_ANIMATION_H
