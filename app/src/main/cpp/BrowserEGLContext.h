#pragma once

#include <EGL/egl.h>
#include <memory>

struct ANativeWindow;

class BrowserEGLContext;

typedef std::shared_ptr<BrowserEGLContext> BrowserEGLContextPtr;
typedef std::weak_ptr<BrowserEGLContext> BrowserEGLContextWeakPtr;

class BrowserEGLContext {
public:
  static BrowserEGLContextPtr Create();
  static const char *ErrorToString(EGLint error);

  bool Initialize(ANativeWindow *aWindow);
  void Destroy();
  void SurfaceChanged(ANativeWindow *aWindow);
  void SurfaceDestroyed();
  bool IsSurfaceReady() const;
  bool MakeCurrent();
  bool SwapBuffers();
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