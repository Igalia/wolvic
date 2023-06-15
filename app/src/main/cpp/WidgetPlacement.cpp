/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "WidgetPlacement.h"

namespace crow {

const float WidgetPlacement::kWorldDPIRatio = 2.0f/720.0f;

WidgetPlacementPtr
WidgetPlacement::FromJava(JNIEnv* aEnv, jobject& aObject) {
  if (!aObject || !aEnv) {
    return nullptr;
  }

  jclass clazz = aEnv->GetObjectClass(aObject);

  std::shared_ptr<WidgetPlacement> result(new WidgetPlacement());

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

#define GET_STRING_FIELD(name) { \
  jfieldID f = aEnv->GetFieldID(clazz, #name, "Ljava/lang/String;"); \
  jstring javaString = (jstring)aEnv->GetObjectField(aObject, f); \
  if (javaString) { \
    const char* nativeString = aEnv->GetStringUTFChars(javaString, 0); \
    result->name = nativeString; \
    aEnv->ReleaseStringUTFChars(javaString, nativeString); \
  } \
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
  GET_INT_FIELD(parentAnchorGravity);
  GET_FLOAT_FIELD(density, "density");
  GET_FLOAT_FIELD(worldWidth, "worldWidth");
  GET_BOOLEAN_FIELD(visible);
  GET_INT_FIELD(scene);
  GET_BOOLEAN_FIELD(showPointer);
  GET_BOOLEAN_FIELD(composited);
  GET_BOOLEAN_FIELD(layer);
  GET_INT_FIELD(layerPriority);
  GET_BOOLEAN_FIELD(proxifyLayer);
  GET_FLOAT_FIELD(textureScale, "textureScale");
  GET_BOOLEAN_FIELD(cylinder);
  GET_FLOAT_FIELD(cylinderMapRadius, "cylinderMapRadius");
  GET_INT_FIELD(tintColor);
  GET_INT_FIELD(borderColor);
  GET_STRING_FIELD(name);
  GET_INT_FIELD(clearColor);

  return result;
}

WidgetPlacementPtr
WidgetPlacement::Create(const WidgetPlacement& aPlacement) {
  return WidgetPlacementPtr(new WidgetPlacement(aPlacement));
}

int32_t
WidgetPlacement::GetTextureWidth() const{
  return (int32_t)ceilf(width * density * textureScale);
}

int32_t
WidgetPlacement::GetTextureHeight() const {
  return (int32_t)ceilf(height * density * textureScale);
}

vrb::Color
WidgetPlacement::GetClearColor() const {
  return vrb::Color(clearColor);
}

WidgetPlacement::Scene
WidgetPlacement::GetScene() const {
  switch (scene) {
      case 1:
        return Scene::ROOT_OPAQUE;
      case 2:
        return Scene::WEBXR_INTERSTITIAL;
    default:
        return Scene::ROOT_TRANSPARENT;
  }
}

vrb::Color
WidgetPlacement::GetTintColor() const {
  return vrb::Color(tintColor);
}

}
