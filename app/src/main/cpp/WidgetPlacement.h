/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_WIDGET_PLACEMENT_DOT_H
#define VRBROWSER_WIDGET_PLACEMENT_DOT_H

#include "vrb/Color.h"
#include "vrb/Vector.h"
#include "vrb/MacroUtils.h"
#include <jni.h>

namespace crow {

class WidgetPlacement;
typedef std::shared_ptr<WidgetPlacement> WidgetPlacementPtr;

struct WidgetPlacement {
  enum class Scene {
    ROOT_TRANSPARENT,
    ROOT_OPAQUE,
    WEBXR_INTERSTITIAL
  };

  int32_t width;
  int32_t height;
  vrb::Vector anchor;
  vrb::Vector translation;
  vrb::Vector rotationAxis;
  float rotation;
  int32_t parentHandle;
  vrb::Vector parentAnchor;
  int32_t parentAnchorGravity;
  float density;
  float worldWidth;
  bool visible;
  int scene;
  bool showPointer;
  bool composited;
  bool layer;
  int32_t layerPriority;
  bool proxifyLayer;
  float textureScale;
  bool cylinder;
  float cylinderMapRadius;
  int tintColor;
  int borderColor;
  std::string name;
  int clearColor;

  int32_t GetTextureWidth() const;
  int32_t GetTextureHeight() const;
  vrb::Color GetTintColor() const;
  vrb::Color GetClearColor() const;
  WidgetPlacement::Scene GetScene() const;

  static const float kWorldDPIRatio;
  static WidgetPlacementPtr FromJava(JNIEnv* aEnv, jobject& aObject);
  static WidgetPlacementPtr Create(const WidgetPlacement& aPlacement);

  static const int kParentAnchorGravityDefault = 0x0000;
  static const int kParentAnchorGravityCenterX = 0x0001;
  static const int kParentAnchorGravityCenterY = 0x0002;
  static const int kParentAnchorGravityCenter = kParentAnchorGravityCenterX | kParentAnchorGravityCenterY;
private:
  WidgetPlacement() = default;
  WidgetPlacement(const WidgetPlacement&) = default;
  WidgetPlacement& operator=(const WidgetPlacement&) = default;
};

}

#endif
