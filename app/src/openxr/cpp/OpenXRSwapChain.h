#pragma once

#include "vrb/Forward.h"
#include "Device.h"
#include <memory>
#include <vector>

#include <EGL/egl.h>
#include "jni.h"
#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>
#include <vrb/include/vrb/FBO.h>
#include "vrb/gl.h"

namespace crow {

class OpenXRSwapChain;
typedef std::shared_ptr<OpenXRSwapChain> OpenXRSwapChainPtr;

class OpenXRSwapChain {
private:
  vrb::RenderContextPtr context;
  XrSwapchainCreateInfo info;
  vrb::FBO::Attributes attributes;
  XrSwapchain swapchain = XR_NULL_HANDLE;
  std::vector<XrSwapchainImageOpenGLESKHR> imageBuffer;
  std::vector<XrSwapchainImageBaseHeader*> images;
  std::vector<vrb::FBOPtr> fbos;
  vrb::FBOPtr acquiredFBO;
  JNIEnv* env = nullptr;
  jobject surface = nullptr;
  XrSession session = XR_NULL_HANDLE;
  uint32_t cubeTexture = 0;
public:
  ~OpenXRSwapChain();

  static OpenXRSwapChainPtr create();
  void InitFBO(vrb::RenderContextPtr &aContext, XrSession aSession, const XrSwapchainCreateInfo& aInfo, vrb::FBO::Attributes aAttributes);
  void InitAndroidSurface(JNIEnv* aEnv, XrSession aSession, const XrSwapchainCreateInfo& aInfo);
  void InitCubemap(vrb::RenderContextPtr &aContext, XrSession aSession, const XrSwapchainCreateInfo& aInfo);
  void AcquireImage();
  void ReleaseImage();
  void BindFBO(GLenum target = GL_FRAMEBUFFER);
  void Destroy();
  inline XrSwapchain SwapChain() const { return swapchain;}
  inline int32_t Width() const { return info.width; }
  inline int32_t Height() const { return info.height; }
  inline jobject AndroidSurface() const { return surface; }
  inline JNIEnv* Env() const { return  env; };
  inline XrSession Session() const { return session; }
  inline uint32_t CubemapTexture() const { return cubeTexture; }
};

}
