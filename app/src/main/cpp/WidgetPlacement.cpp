/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "WidgetPlacement.h"

namespace crow {

WidgetPlacementPtr
WidgetPlacement::FromJava(JNIEnv* aEnv, jobject& aObject) {
  if (!aObject) {
    return nullptr;
  }

  jclass clazz = aEnv->GetObjectClass(aObject);

  std::shared_ptr<WidgetPlacement> result(new WidgetPlacement());;

#define GET_INT_FIELD(name) { \
  jfieldID f = aEnv->GetFieldID(clazz, #name, "I"); \
  result->name = aEnv->GetIntField(aObject, f); \
}

#define GET_FLOAT_FIELD(to, name) { \
  jfieldID f = aEnv->GetFieldID(clazz, name, "F"); \
  result->to = aEnv->GetFloatField(aObject, f); \
}

  GET_INT_FIELD(widgetType);
  GET_INT_FIELD(width);
  GET_INT_FIELD(height);
  GET_FLOAT_FIELD(anchor.x(), "anchorX");
  GET_FLOAT_FIELD(anchor.y(), "anchorY");
  GET_FLOAT_FIELD(translation.x(), "translationX");
  GET_FLOAT_FIELD(translation.y(), "translationY");
  GET_FLOAT_FIELD(translation.z(), "translationZ");
  GET_INT_FIELD(parentHandle);
  GET_FLOAT_FIELD(parentAnchor.x(), "parentAnchorX");
  GET_FLOAT_FIELD(parentAnchor.y(), "parentAnchorY");

  return result;
}

}