/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "FadeAnimation.h"
#include "vrb/ConcreteClass.h"
#include "vrb/Color.h"
#include "vrb/Logger.h"

namespace crow {

static float kFadeAlpha = 0.75f;
static int kAnimationLength = 40;

struct FadeAnimation::State {
  vrb::Color fadeColor;
  float animationStartAlpha;
  float animationEndAlpha;
  int animations;
  float currentBrightness;
  bool visible;
  FadeChangeCallback changeCallback;
  State()
      : fadeColor(0.0f, 0.0f, 0.0f, 0.0f)
      , animationStartAlpha(0.0f)
      , animationEndAlpha(0.0f)
      , animations(-1)
      , currentBrightness(1.0f)
      , visible(true)
  {}

  void NotifyChangeCallback(const vrb::Color& aTintColor) {
    if (changeCallback) {
      changeCallback(aTintColor);
    }
  }
};

FadeAnimationPtr
FadeAnimation::Create(vrb::CreationContextPtr& aContext) {
  return std::make_shared<vrb::ConcreteClass<FadeAnimation, FadeAnimation::State> >(aContext);
}

bool
FadeAnimation::IsVisible() const {
  return m.visible && (m.animations >= 0 ||  m.fadeColor.Alpha() > 0.0f);
}

vrb::Color
FadeAnimation::GetTintColor() const {
  if (IsVisible()) {
    const float a = 1.0f - m.fadeColor.Alpha();
    return vrb::Color(a, a, a, 1.0f);
  } else {
    return vrb::Color(1.0f, 1.0f, 1.0f, 1.0f);
  }
}


void FadeAnimation::SetBrightness(const float aBrightness) {
  m.currentBrightness = aBrightness;
  m.animationStartAlpha = m.fadeColor.Alpha();
  m.animationEndAlpha = 1.0f - aBrightness;
  m.animations = kAnimationLength;
  m.NotifyChangeCallback(GetTintColor());
}

void FadeAnimation::UpdateAnimation() {
  if (m.animations >= 0) {
    float t = (float)(kAnimationLength - m.animations) / (float) kAnimationLength;
    m.fadeColor.SetAlpha(m.animationStartAlpha + (m.animationEndAlpha - m.animationStartAlpha) * t);
    m.animations--;
    m.NotifyChangeCallback(GetTintColor());
  }
}

void FadeAnimation::FadeIn() {
  m.animationStartAlpha = kFadeAlpha;
  m.animationEndAlpha = 1.0f - m.currentBrightness;
  m.animations = kAnimationLength;
  m.NotifyChangeCallback(GetTintColor());
}

void
FadeAnimation::SetVisible(const bool aVisible) {
  if (aVisible != IsVisible()) {
    m.visible = aVisible;
    m.NotifyChangeCallback(GetTintColor());
  }
}

void
FadeAnimation::SetFadeChangeCallback(const FadeChangeCallback& aCallback) {
  m.changeCallback = aCallback;
}

FadeAnimation::FadeAnimation(State& aState, vrb::CreationContextPtr& aContext)
    : m(aState)
{}

} // namespace crow
