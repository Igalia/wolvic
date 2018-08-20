/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_SPLASH_ANIMATION_H
#define VRBROWSER_SPLASH_ANIMATION_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"

#include <memory>
#include <string>
#include <vector>
#include <functional>

namespace crow {

class SplashAnimation;
typedef std::shared_ptr<SplashAnimation> SplashAnimationPtr;

class SplashAnimation {
public:
  static SplashAnimationPtr Create(vrb::CreationContextPtr aContext);
  void Load();
  bool Update(const vrb::Matrix& aHeadTransform);
  vrb::NodePtr GetRoot() const;

  struct State;
  SplashAnimation(State& aState, vrb::CreationContextPtr& aContext);
  ~SplashAnimation();
private:
  State& m;
  SplashAnimation() = delete;
  VRB_NO_DEFAULTS(SplashAnimation)
};

} // namespace crow

#endif // VRBROWSER_SPLASH_ANIMATION_H
