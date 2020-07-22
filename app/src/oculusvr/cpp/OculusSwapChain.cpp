#include "OculusSwapChain.h"
#include "vrb/FBO.h"
#include "vrb/GLError.h"
#include "vrb/Logger.h"

namespace crow {

OculusEyeSwapChainPtr
OculusEyeSwapChain::create() {
  return std::make_shared<OculusEyeSwapChain>();
}

void
OculusEyeSwapChain::Init(vrb::RenderContextPtr &aContext, device::RenderMode aMode, uint32_t aWidth,
          uint32_t aHeight) {
  Destroy();
  ovrSwapChain = vrapi_CreateTextureSwapChain(VRAPI_TEXTURE_TYPE_2D,
                                              VRAPI_TEXTURE_FORMAT_8888,
                                              aWidth, aHeight, 1, true);
  swapChainLength = vrapi_GetTextureSwapChainLength(ovrSwapChain);

  for (int i = 0; i < swapChainLength; ++i) {
    vrb::FBOPtr fbo = vrb::FBO::Create(aContext);
    auto texture = vrapi_GetTextureSwapChainHandle(ovrSwapChain, i);
    VRB_GL_CHECK(glBindTexture(GL_TEXTURE_2D, texture));
    VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE));
    VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE));
    VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR));
    VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR));

    vrb::FBO::Attributes attributes;
    if (aMode == device::RenderMode::Immersive) {
      attributes.depth = true;
      attributes.samples = 0;
    } else {
      attributes.depth = true;
      attributes.samples = 4;
    }

    VRB_GL_CHECK(fbo->SetTextureHandle(texture, aWidth, aHeight, attributes));
    if (fbo->IsValid()) {
      fbos.push_back(fbo);
    } else {
      VRB_LOG("FAILED to make valid FBO");
    }
  }
}

void
OculusEyeSwapChain::Destroy() {
  fbos.clear();
  if (ovrSwapChain) {
    vrapi_DestroyTextureSwapChain(ovrSwapChain);
    ovrSwapChain = nullptr;
  }
  swapChainLength = 0;
}

vrb::FBOPtr OculusEyeSwapChain::FBO(const int32_t aIndex) {
  if (aIndex >=0 && aIndex < fbos.size()) {
    return fbos[aIndex];
  }
  return nullptr;
}

}
