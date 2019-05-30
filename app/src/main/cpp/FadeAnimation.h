/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_FADE_ANIMATION_H
#define VRBROWSER_FADE_ANIMATION_H

#include "vrb/MacroUtils.h"
#include "vrb/Forward.h"
#include "vrb/ResourceGL.h"
#include <functional>
#include <memory>

namespace crow {

class FadeAnimation;
typedef std::shared_ptr<FadeAnimation> FadeAnimationPtr;

class FadeAnimation {
public:
  static FadeAnimationPtr Create(vrb::CreationContextPtr& aContext);
  typedef std::function<void(const vrb::Color& aTintColor)> FadeChangeCallback;

  bool IsVisible() const;
  vrb::Color GetTintColor() const;
  void SetBrightness(const float aBrightness);
  void UpdateAnimation();
  void FadeIn();
  void SetVisible(const bool aVisible);
  void SetFadeChangeCallback(const FadeChangeCallback& aCallback);
protected:
  struct State;
  FadeAnimation(State& aState, vrb::CreationContextPtr& aContext);
  ~FadeAnimation() = default;
private:
  State& m;
  FadeAnimation() = delete;
  VRB_NO_DEFAULTS(FadeAnimation);
};

} // namespace crow

#endif //VRBROWSER_FADE_ANIMATION_H
