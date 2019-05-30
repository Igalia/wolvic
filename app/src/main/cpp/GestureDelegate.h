/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_GESTUREDELEGATE_H
#define VRBROWSER_GESTUREDELEGATE_H

#include "vrb/MacroUtils.h"
#include <memory>

namespace crow {

class GestureDelegate;
typedef std::shared_ptr<GestureDelegate> GestureDelegatePtr;
typedef std::shared_ptr<const GestureDelegate> GestureDelegateConstPtr;

enum class GestureType {
  NoGesture,
  SwipeLeft,
  SwipeRight
};

class GestureDelegate {
public:
  static GestureDelegatePtr Create();
  void Reset();
  int32_t AddGesture(const GestureType aType);
  int32_t GetGestureCount() const;
  GestureType GetGestureType(const int32_t aWhich) const;
protected:
  struct State;
  GestureDelegate(State& aState);
  ~GestureDelegate() = default;
private:
  State& m;
  GestureDelegate() = delete;
  VRB_NO_DEFAULTS(GestureDelegate)
};

} // namespace crow

#endif //VRBROWSER_GESTUREDELEGATE_H
