#include "OculusSwapChain.h"
#include "vrb/FBO.h"
#include "vrb/GLError.h"
#include "vrb/Logger.h"

namespace crow {

OculusSwapChainPtr
OculusSwapChain::CreateFBO(vrb::RenderContextPtr& aContext, const vrb::FBO::Attributes& attributes,
                           uint32_t aWidth, uint32_t aHeight, bool buffered, const vrb::Color& aClearColor ) {
  auto result = std::make_shared<OculusSwapChain>();

  result->mSwapChain = vrapi_CreateTextureSwapChain(VRAPI_TEXTURE_TYPE_2D,
                                              VRAPI_TEXTURE_FORMAT_8888,
                                              aWidth, aHeight, 1, buffered);
  result->mSwapChainLength = vrapi_GetTextureSwapChainLength(result->mSwapChain);
  result->mWidth = aWidth;
  result->mHeight = aHeight;

  for (int i = 0; i < result->mSwapChainLength ; ++i) {
    vrb::FBOPtr fbo = vrb::FBO::Create(aContext);
    auto texture = vrapi_GetTextureSwapChainHandle(result->mSwapChain, i);
    VRB_GL_CHECK(glBindTexture(GL_TEXTURE_2D, texture));
    VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE));
    VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE));
    VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR));
    VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR));

    VRB_GL_CHECK(fbo->SetTextureHandle(texture, aWidth, aHeight, attributes));
    if (fbo->IsValid()) {
      result->mFBOs.push_back(fbo);
      fbo->Bind();
      VRB_GL_CHECK(glClearColor(aClearColor.Red(), aClearColor.Green(), aClearColor.Blue(), aClearColor.Alpha()));
      VRB_GL_CHECK(glClear(GL_COLOR_BUFFER_BIT));
      fbo->Unbind();
    } else {
      VRB_LOG("FAILED to make valid FBO");
    }
  }

  return result;
}

OculusSwapChainPtr OculusSwapChain::CreateAndroidSurface(JNIEnv* aEnv, uint32_t aWidth, uint32_t aHeight) {
  auto result = std::make_shared<OculusSwapChain>();

  result->mSwapChain = vrapi_CreateAndroidSurfaceSwapChain(aWidth, aHeight);
  result->mWidth = aWidth;
  result->mHeight = aHeight;

  jobject surface = vrapi_GetTextureSwapChainAndroidSurface(result->mSwapChain);
  result->mEnv = aEnv;
  result->mSurface = aEnv->NewGlobalRef(surface);
  return result;
}

OculusSwapChainPtr OculusSwapChain::CreateCubemap(GLint aFormat, uint32_t aWidth, uint32_t aHeight) {
  auto result = std::make_shared<OculusSwapChain>();
  result->mSwapChain = vrapi_CreateTextureSwapChain3(VRAPI_TEXTURE_TYPE_CUBE, aFormat, aWidth, aWidth, 1, 1);
  result->mWidth = aWidth;
  result->mHeight = aHeight;

  return result;
}

OculusSwapChain::~OculusSwapChain() {
  Destroy();
}

void
OculusSwapChain::Destroy() {
  mFBOs.clear();
  if (mSurface  && mEnv) {
    mEnv->DeleteGlobalRef(mSurface);
    mSurface = nullptr;
    mEnv = nullptr;
  }
  if (mSwapChain) {
    vrapi_DestroyTextureSwapChain(mSwapChain);
    mSwapChain = nullptr;
  }
}

vrb::FBOPtr
OculusSwapChain::FBO(const int32_t aIndex) const {
  if (aIndex >= 0 && aIndex < mFBOs.size()) {
    return mFBOs[aIndex];
  }
  return nullptr;
}

GLuint
OculusSwapChain::TextureHandle(const int32_t aIndex) const {
  if (mSwapChain) {
    return vrapi_GetTextureSwapChainHandle(mSwapChain, aIndex);
  }
  return 0;
}

}
