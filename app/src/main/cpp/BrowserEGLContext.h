/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#pragma once

#include <EGL/egl.h>
#include <memory>

struct ANativeWindow;

namespace crow {

class BrowserEGLContext;

typedef std::shared_ptr<BrowserEGLContext> BrowserEGLContextPtr;
typedef std::weak_ptr<BrowserEGLContext> BrowserEGLContextWeakPtr;

class BrowserEGLContext {
public:
  static BrowserEGLContextPtr Create();
  static const char *ErrorToString(EGLint error);

  bool Initialize(ANativeWindow *aWindow);
  void Destroy();
  void UpdateNativeWindow(ANativeWindow *aWindow);
  bool IsSurfaceReady() const;
  bool MakeCurrent();
  bool SwapBuffers();

  EGLDisplay Display() const { return mDisplay; }
  EGLContext Context() const { return mContext; }
  EGLConfig Config() const { return mConfig; }
  ANativeWindow* NativeWindow() const { return mNativeWindow; }

  BrowserEGLContext();
private:
  EGLint mMajorVersion;
  EGLint mMinorVersion;
  EGLDisplay mDisplay;
  EGLConfig mConfig;
  EGLSurface mSurface;
  EGLContext mContext;
  ANativeWindow *mNativeWindow;
};

} // namespace crow
