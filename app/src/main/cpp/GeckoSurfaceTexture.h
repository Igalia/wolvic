/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_GECKOSURFACETEXTURE_H
#define VRBROWSER_GECKOSURFACETEXTURE_H

#include "vrb/gl.h"
#include "vrb/MacroUtils.h"

#include <EGL/egl.h>
#include <jni.h>
#include <memory>

namespace crow {

class GeckoSurfaceTexture;
typedef std::shared_ptr<GeckoSurfaceTexture> GeckoSurfaceTexturePtr;

class GeckoSurfaceTexture {
public:
  static void InitializeJava(JNIEnv* aEnv, jobject aActivity);
  static void ShutdownJava();
  static GeckoSurfaceTexturePtr Create(const int32_t aHandle);
  GLuint GetTextureName();
  void AttachToGLContext(EGLContext aContext);
  bool IsAttachedToGLContext(EGLContext aContext) const;
  void DetachFromGLContext();
  void UpdateTexImage();
  void ReleaseTexImage();
  void IncrementUse();
  void DecrementUse();

protected:
  struct State;
  GeckoSurfaceTexture(State& aState);
  ~GeckoSurfaceTexture();

private:
  State& m;
  GeckoSurfaceTexture() = delete;
  VRB_NO_DEFAULTS(GeckoSurfaceTexture)
};

}

#endif //VRBROWSER_GECKOSURFACETEXTURE_H
