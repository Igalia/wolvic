/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "GestureDelegate.h"
#include "vrb/ConcreteClass.h"

#include <vector>

namespace crow {

struct GestureRecord {
  GestureType type;
  explicit GestureRecord(const GestureType aType) : type(aType) {}
  GestureRecord(const GestureRecord& aRecord) = default;
  //GestureRecord(GestureRecord&& aRecord) : type(aRecord.type) {}
  GestureRecord& operator=(const GestureRecord& aRecord) = default;
};

struct GestureDelegate::State {
  std::vector<GestureRecord> gestures;
};

GestureDelegatePtr
GestureDelegate::Create() {
  return std::make_shared<vrb::ConcreteClass<GestureDelegate, GestureDelegate::State> >();
}

void
GestureDelegate::Reset() {
  m.gestures.clear();
}

int32_t
GestureDelegate::AddGesture(const GestureType aType) {
  if (aType == GestureType::NoGesture) {
    return -1;
  }
  m.gestures.emplace_back(GestureRecord(aType));
  return m.gestures.size() - 1;
}

int32_t
GestureDelegate::GetGestureCount() const {
  return m.gestures.size();
}

GestureType
GestureDelegate::GetGestureType(const int32_t aWhich) const {
  if (aWhich >= m.gestures.size()) {
    return GestureType::NoGesture;
  }
  return m.gestures[aWhich].type;
}

GestureDelegate::GestureDelegate(State& aState) : m(aState) {}

}