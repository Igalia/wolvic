/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "BrowserEGLContext.h"
#include "vrb/Logger.h"
#include <EGL/eglext.h>
#include <android_native_app_glue.h>

namespace crow {

BrowserEGLContext::BrowserEGLContext()
  : mMajorVersion(0), mMinorVersion(0), mDisplay(0), mConfig(0), mSurface(0), mContext(0),
    mNativeWindow(nullptr) {
}

BrowserEGLContextPtr
BrowserEGLContext::Create() {
  return std::make_shared<BrowserEGLContext>();
}

bool
BrowserEGLContext::Initialize(ANativeWindow *aNativeWindow) {
  mDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
  if (eglInitialize(mDisplay, &mMajorVersion, &mMinorVersion) == EGL_FALSE) {
    VRB_ERROR("eglInitialize() failed: %s", ErrorToString(eglGetError()));
    return false;
  }

  // Do NOT use eglChooseConfig, because the Android EGL code pushes in multisample
  // flags in eglChooseConfig if the user has selected the "force 4x MSAA" option in
  // settings, and that is completely wasted for the time warp renderer.
  const int MAX_CONFIGS = 1024;

  EGLConfig configs[MAX_CONFIGS];
  EGLint numConfigs = 0;
  if (eglGetConfigs(mDisplay, configs, MAX_CONFIGS, &numConfigs) == EGL_FALSE) {
    VRB_ERROR("eglGetConfigs() failed: %s", ErrorToString(eglGetError()));
    return false;
  }

  const EGLint configAttribs[] = {
          EGL_RED_SIZE, 8,
          EGL_GREEN_SIZE, 8,
          EGL_BLUE_SIZE, 8,
          EGL_ALPHA_SIZE, 8,
#ifdef HVR
          EGL_DEPTH_SIZE, 24,
#else
          EGL_DEPTH_SIZE, 0,
#endif
          EGL_STENCIL_SIZE, 0,
          EGL_SAMPLES, 0,
          EGL_NONE
  };

  for (int i = 0; i < numConfigs; ++i) {
    EGLint value = 0;

    eglGetConfigAttrib(mDisplay, configs[i], EGL_RENDERABLE_TYPE, &value);

    // Check EGL Client Version
    if ((value & EGL_OPENGL_ES3_BIT_KHR) != EGL_OPENGL_ES3_BIT_KHR) {
      continue;
    }

    // EGL_WINDOW_BIT is required so it can share textures with the window context.
    eglGetConfigAttrib(mDisplay, configs[i], EGL_SURFACE_TYPE, &value);
    if ((value & (EGL_WINDOW_BIT | EGL_PBUFFER_BIT)) != (EGL_WINDOW_BIT | EGL_PBUFFER_BIT)) {
      continue;
    }

    int j = 0;
    for (; configAttribs[j] != EGL_NONE; j += 2) {
      eglGetConfigAttrib(mDisplay, configs[i], configAttribs[j], &value);
      if (value != configAttribs[j + 1]) {
        break;
      }
    }
    if (configAttribs[j] == EGL_NONE) {
      mConfig = configs[i];
      break;
    }
  }

  if (mConfig == 0) {
    VRB_ERROR("eglChooseConfig() failed: %s", ErrorToString(eglGetError()));
    return false;
  }


  //Reconfigure the ANativeWindow buffers to match, using EGL_NATIVE_VISUAL_ID.
  mNativeWindow = aNativeWindow;
  EGLint format;
  eglGetConfigAttrib(mDisplay, mConfig, EGL_NATIVE_VISUAL_ID, &format);
  ANativeWindow_setBuffersGeometry(aNativeWindow, 0, 0, format);

  EGLint contextAttribs[] = {
          EGL_CONTEXT_CLIENT_VERSION, 3,
          EGL_NONE
  };

  mContext = eglCreateContext(mDisplay, mConfig, EGL_NO_CONTEXT, contextAttribs);
  if (mContext == EGL_NO_CONTEXT) {
    VRB_ERROR("eglCreateContext() failed: %s", ErrorToString(eglGetError()));
    return false;
  }

  const EGLint surfaceAttribs[] = {
  		EGL_WIDTH, 16,
  		EGL_HEIGHT, 16,
  		EGL_NONE
  };

#ifdef HVR
  const EGLint attribs[] = { EGL_NONE };
  mSurface = eglCreateWindowSurface(mDisplay, mConfig, mNativeWindow, attribs);
#else
  mSurface = eglCreatePbufferSurface(mDisplay, mConfig, surfaceAttribs);
#endif

  if (mSurface == EGL_NO_SURFACE) {
    VRB_ERROR("eglCreateWindowSurface() failed: %s", ErrorToString(eglGetError()));
    eglDestroyContext(mDisplay, mContext);
    mContext = EGL_NO_CONTEXT;
    return false;
  }

  if (eglMakeCurrent(mDisplay, mSurface, mSurface, mContext) == EGL_FALSE) {
    VRB_ERROR("eglMakeCurrent() failed: %s", ErrorToString(eglGetError()));
    eglDestroySurface(mDisplay, mSurface);
    eglDestroyContext(mDisplay, mContext);
    mContext = EGL_NO_CONTEXT;
    return false;
  }

  return true;
}

void
BrowserEGLContext::Destroy() {
  if (mDisplay) {
    if (eglMakeCurrent(mDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT) == EGL_FALSE) {
      VRB_ERROR("eglMakeCurrent() failed: %s", ErrorToString(eglGetError()));
    }
  }
  if (mContext != EGL_NO_CONTEXT) {
    if (eglDestroyContext(mDisplay, mContext) == EGL_FALSE) {
      VRB_ERROR("eglDestroyContext() failed: %s", ErrorToString(eglGetError()));
    }
    mContext = EGL_NO_CONTEXT;
  }
  if (mSurface != EGL_NO_SURFACE) {
    if (eglDestroySurface(mDisplay, mSurface) == EGL_FALSE) {
      VRB_ERROR("eglDestroySurface() failed: %s", ErrorToString(eglGetError()));
    }
    mSurface = EGL_NO_SURFACE;
  }
  if (mDisplay) {
    if (eglTerminate(mDisplay) == EGL_FALSE) {
      VRB_ERROR("eglTerminate() failed: %s", ErrorToString(eglGetError()));
    }
    mDisplay = 0;
  }
}

void
BrowserEGLContext::UpdateNativeWindow(ANativeWindow *aWindow) {
  mNativeWindow = aWindow;

#ifdef HVR
  const EGLint attribs[] = { EGL_NONE };
  mSurface = eglCreateWindowSurface(mDisplay, mConfig, mNativeWindow, attribs);
#endif
}

bool
BrowserEGLContext::IsSurfaceReady() const {
  return mSurface != EGL_NO_SURFACE && mNativeWindow != nullptr;
}

bool
BrowserEGLContext::MakeCurrent() {
  if (eglMakeCurrent(mDisplay, mSurface, mSurface, mContext) == EGL_FALSE) {
    VRB_ERROR("eglMakeCurrent() failed: %s", ErrorToString(eglGetError()));
    return false;
  }
  return true;
}

bool
BrowserEGLContext::SwapBuffers() {
  if (eglSwapBuffers(mDisplay, mSurface) == EGL_FALSE) {
    VRB_ERROR("SwapBuffers() failed: %s", ErrorToString(eglGetError()));
    return false;
  }
  return true;
}

const char *
BrowserEGLContext::ErrorToString(EGLint error) {
  switch (error) {
    case EGL_SUCCESS:
      return "EGL_SUCCESS";
    case EGL_NOT_INITIALIZED:
      return "EGL_NOT_INITIALIZED";
    case EGL_BAD_ACCESS:
      return "EGL_BAD_ACCESS";
    case EGL_BAD_ALLOC:
      return "EGL_BAD_ALLOC";
    case EGL_BAD_ATTRIBUTE:
      return "EGL_BAD_ATTRIBUTE";
    case EGL_BAD_CONTEXT:
      return "EGL_BAD_CONTEXT";
    case EGL_BAD_CONFIG:
      return "EGL_BAD_CONFIG";
    case EGL_BAD_CURRENT_SURFACE:
      return "EGL_BAD_CURRENT_SURFACE";
    case EGL_BAD_DISPLAY:
      return "EGL_BAD_DISPLAY";
    case EGL_BAD_SURFACE:
      return "EGL_BAD_SURFACE";
    case EGL_BAD_MATCH:
      return "EGL_BAD_MATCH";
    case EGL_BAD_PARAMETER:
      return "EGL_BAD_PARAMETER";
    case EGL_BAD_NATIVE_PIXMAP:
      return "EGL_BAD_NATIVE_PIXMAP";
    case EGL_BAD_NATIVE_WINDOW:
      return "EGL_BAD_NATIVE_WINDOW";
    case EGL_CONTEXT_LOST:
      return "EGL_CONTEXT_LOST";
    default:
      return "Unknown Error";
  }
}

} // namespace crow
