#pragma once

#include "vrb/Forward.h"
#include "Device.h"
#include "VrApi.h"
#include <memory>
#include <vector>

namespace crow {

class OculusEyeSwapChain;

typedef std::shared_ptr<OculusEyeSwapChain> OculusEyeSwapChainPtr;

class OculusEyeSwapChain {
public:
  ovrTextureSwapChain *ovrSwapChain = nullptr;
  int swapChainLength = 0;

  static OculusEyeSwapChainPtr create();
  void Init(vrb::RenderContextPtr &aContext, device::RenderMode aMode, uint32_t aWidth, uint32_t aHeight);
  void Destroy();
  vrb::FBOPtr FBO(const int32_t aIndex);
private:
  std::vector<vrb::FBOPtr> fbos;
};

}
