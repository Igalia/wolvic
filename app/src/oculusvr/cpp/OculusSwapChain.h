#pragma once

#include "vrb/Forward.h"
#include "vrb/GLError.h"
#include "vrb/Color.h"
#include "Device.h"
#include "VrApi.h"
#include <memory>
#include <vector>
#include <vrb/include/vrb/FBO.h>

namespace crow {

class OculusSwapChain;

typedef std::shared_ptr<OculusSwapChain> OculusSwapChainPtr;

class OculusSwapChain {
private:
  ovrTextureSwapChain * mSwapChain = nullptr;
  int32_t mSwapChainLength = 1;
  std::vector<vrb::FBOPtr> mFBOs;
  jobject mSurface = nullptr;
  JNIEnv* mEnv = nullptr;
  int32_t mWidth = 0;
  int32_t mHeight = 0;

public:
  static OculusSwapChainPtr CreateFBO(vrb::RenderContextPtr& aContext, const vrb::FBO::Attributes& attributes,
                                      uint32_t aWidth, uint32_t aHeight, bool buffered = true,
                                      const vrb::Color& aClearColor = {});
  static OculusSwapChainPtr CreateAndroidSurface(JNIEnv* aEnv, uint32_t aWidth, uint32_t aHeight);
  static OculusSwapChainPtr CreateCubemap(GLint aFormat, uint32_t aWidth, uint32_t aHeight);
  ~OculusSwapChain();
  void Destroy();

  inline ovrTextureSwapChain* SwapChain() const { return mSwapChain; }
  inline int32_t SwapChainLength() const { return mSwapChainLength; }
  vrb::FBOPtr FBO(const int32_t aIndex) const;
  jobject AndroidSurface() const { return mSurface; }
  GLuint TextureHandle(const int32_t aIndex) const;
  inline int32_t Width() const { return mWidth; }
  inline int32_t Height() const { return mHeight; }
};

}
