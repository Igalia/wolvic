/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "WidgetPlacement.h"

namespace crow {

WidgetPlacementPtr
WidgetPlacement::FromJava(JNIEnv* aEnv, jobject& aObject) {
  if (!aObject || !aEnv) {
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

#define GET_BOOLEAN_FIELD(name) { \
  jfieldID f = aEnv->GetFieldID(clazz, #name, "Z"); \
  result->name = aEnv->GetBooleanField(aObject, f); \
}

  GET_INT_FIELD(width);
  GET_INT_FIELD(height);
  GET_FLOAT_FIELD(anchor.x(), "anchorX");
  GET_FLOAT_FIELD(anchor.y(), "anchorY");
  GET_FLOAT_FIELD(translation.x(), "translationX");
  GET_FLOAT_FIELD(translation.y(), "translationY");
  GET_FLOAT_FIELD(translation.z(), "translationZ");
  GET_FLOAT_FIELD(rotationAxis.x(), "rotationAxisX");
  GET_FLOAT_FIELD(rotationAxis.y(), "rotationAxisY");
  GET_FLOAT_FIELD(rotationAxis.z(), "rotationAxisZ");
  GET_FLOAT_FIELD(rotation, "rotation");
  GET_INT_FIELD(parentHandle);
  GET_FLOAT_FIELD(parentAnchor.x(), "parentAnchorX");
  GET_FLOAT_FIELD(parentAnchor.y(), "parentAnchorY");
  GET_FLOAT_FIELD(density, "density");
  GET_FLOAT_FIELD(worldWidth, "worldWidth");
  GET_BOOLEAN_FIELD(visible);
  GET_BOOLEAN_FIELD(opaque);
  GET_BOOLEAN_FIELD(showPointer);
  GET_BOOLEAN_FIELD(firstDraw);
  GET_BOOLEAN_FIELD(layer);
  GET_BOOLEAN_FIELD(cylinder);
  GET_FLOAT_FIELD(textureScale, "textureScale");

  return result;
}

int32_t
WidgetPlacement::GetTextureWidth() const{
  return (int32_t)ceilf(width * density * textureScale);
}

int32_t
WidgetPlacement::GetTextureHeight() const {
  return (int32_t)ceilf(height * density * textureScale);
}

}