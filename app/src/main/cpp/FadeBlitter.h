/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_FADE_BLITTER_H
#define VRBROWSER_FADE_BLITTER_H

#include "vrb/MacroUtils.h"
#include "vrb/Forward.h"
#include "vrb/ResourceGL.h"
#include <memory>

namespace crow {

class FadeBlitter;
typedef std::shared_ptr<FadeBlitter> FadeBlitterPtr;

class FadeBlitter : protected vrb::ResourceGL {
public:
  static FadeBlitterPtr Create(vrb::CreationContextPtr& aContext);
  void Draw();
  bool IsVisible() const;
  void SetBrightness(const float aBrightness);
  void FadeIn();
protected:
  struct State;
  FadeBlitter(State& aState, vrb::CreationContextPtr& aContext);
  ~FadeBlitter();
  void InitializeGL() override;
  void ShutdownGL() override;
private:
  State& m;
  FadeBlitter() = delete;
  VRB_NO_DEFAULTS(FadeBlitter);
};

} // namespace crow

#endif //VRBROWSER_FADE_BLITTER_H
