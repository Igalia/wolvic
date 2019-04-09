/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_WIDGET_PLACEMENT_DOT_H
#define VRBROWSER_WIDGET_PLACEMENT_DOT_H

#include "vrb/Vector.h"
#include "vrb/MacroUtils.h"
#include <jni.h>

namespace crow {

class WidgetPlacement;
typedef std::shared_ptr<WidgetPlacement> WidgetPlacementPtr;

struct WidgetPlacement {
  int32_t width;
  int32_t height;
  vrb::Vector anchor;
  vrb::Vector translation;
  vrb::Vector rotationAxis;
  float rotation;
  int32_t parentHandle;
  vrb::Vector parentAnchor;
  float density;
  float worldWidth;
  bool visible;
  bool opaque;
  bool showPointer;
  bool firstDraw;
  bool layer;
  bool cylinder;
  float textureScale;

  int32_t GetTextureWidth() const;
  int32_t GetTextureHeight() const;

  static WidgetPlacementPtr FromJava(JNIEnv* aEnv, jobject& aObject);
private:
  WidgetPlacement() {};
  VRB_NO_DEFAULTS(WidgetPlacement)
};

}

#endif