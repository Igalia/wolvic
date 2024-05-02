#pragma once

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"
#include "vrb/FBO.h"
#include "vrb/Color.h"
#include "vrb/Matrix.h"
#include "vrb/GLError.h"
#include "DeviceDelegate.h"
#include "VRLayer.h"
#include <EGL/egl.h>
#include "jni.h"
#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>
#include "OpenXRSwapChain.h"
#include "OpenXRHelpers.h"
#include "OpenXRExtensions.h"
#include <array>
#include "SystemUtils.h"


namespace crow {

class OpenXRLayer;

typedef std::shared_ptr<OpenXRLayer> OpenXRLayerPtr;

struct SurfaceChangedTarget {
  OpenXRLayer * layer;

  SurfaceChangedTarget(OpenXRLayer *aLayer) : layer(aLayer) {};
};

typedef std::shared_ptr<SurfaceChangedTarget> SurfaceChangedTargetPtr;
typedef std::weak_ptr<SurfaceChangedTarget> SurfaceChangedTargetWeakPtr;

class OpenXRLayer {
public:
  virtual void Init(JNIEnv *aEnv, XrSession session, vrb::RenderContextPtr &aContext) = 0;
  virtual void Update(XrSpace aSpace, const XrPosef &aReorientPose, XrSwapchain aClearSwapChain) = 0;
  virtual OpenXRSwapChainPtr GetSwapChain() const = 0;
  virtual uint32_t HeaderCount() const = 0;
  virtual const XrCompositionLayerBaseHeader* Header(uint32_t aIndex) const = 0;
  virtual void SetCurrentEye(device::Eye aEye) = 0;
  virtual bool IsDrawRequested() const = 0;
  virtual bool GetDrawInFront() const = 0;
  virtual void ClearRequestDraw() = 0;
  virtual bool IsComposited() const = 0;
  virtual void SetComposited(bool aValue) = 0;
  virtual VRLayerPtr GetLayer() const = 0;
  virtual void Destroy() = 0;
  typedef std::function<void(const OpenXRSwapChainPtr &, GLenum aTarget, bool aBound)> BindDelegate;
  virtual void SetBindDelegate(const BindDelegate &aDelegate) = 0;
  virtual jobject GetSurface() const = 0;
  virtual SurfaceChangedTargetPtr GetSurfaceChangedTarget() const = 0;

  virtual void
  HandleResize(const OpenXRSwapChainPtr& newSwapChain) = 0;

  virtual ~OpenXRLayer() {}
};

template<class T, class U>
class OpenXRLayerBase : public OpenXRLayer {
public:
  OpenXRSwapChainPtr swapchain;
  SurfaceChangedTargetPtr surfaceChangedTarget;
  T layer;
  std::array<U, 2> xrLayers;

  void Init(JNIEnv *aEnv, XrSession session, vrb::RenderContextPtr &aContext) override {
    layer->SetInitialized(true);
    surfaceChangedTarget = std::make_shared<SurfaceChangedTarget>(this);
    SurfaceChangedTargetWeakPtr weakTarget = surfaceChangedTarget;
    layer->NotifySurfaceChanged(VRLayer::SurfaceChange::Create, [=]() {
      SurfaceChangedTargetPtr target = weakTarget.lock();
      if (target) {
        target->layer->SetComposited(true);
      }
    });
  }

  virtual void
  Update(XrSpace aSpace, const XrPosef &aReorientPose, XrSwapchain aClearSwapChain) override {
    const uint numXRLayers = GetNumXRLayers();
    if (mCompositionLayerColorScaleBias != XR_NULL_HANDLE) {
        vrb::Color tintColor = layer->GetTintColor();
        if (!IsComposited() && (layer->GetClearColor().Alpha() > 0.0f)) {
            tintColor = layer->GetClearColor();
            tintColor.SetRGBA(tintColor.Red(), tintColor.Green(), tintColor.Blue(), tintColor.Alpha());
        }
        mCompositionLayerColorScaleBiasStruct.colorScale = {tintColor.Red(), tintColor.Green(), tintColor.Blue(), tintColor.Alpha()};
    }

    for (uint i = 0; i < numXRLayers; ++i) {
      // We have to explicitly cast to XrCompositionLayerBaseHeader because the
      // XrCompositionLayerPassthroughFB structure used "flags" instead of "layerFlags". It's still
      // a XrCompositionLayerBaseHeader though because the structs are binary compatible.
      XrCompositionLayerBaseHeader* xrLayer = (XrCompositionLayerBaseHeader*) &xrLayers[i];
      xrLayer->layerFlags = XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT;
      xrLayer->space = aSpace;
      xrLayer->next = XR_NULL_HANDLE;

      if (mCompositionLayerColorScaleBias != XR_NULL_HANDLE)
        PushNextXrStructureInChain((XrBaseInStructure&)*xrLayer, (XrBaseInStructure&)*mCompositionLayerColorScaleBias);
    }
  }

  virtual OpenXRSwapChainPtr GetSwapChain() const override {
    return swapchain;
  }

  // This method was based on the eyeVisibility attribute that most XrCompositionLayers have. However
  // that is not the case for all of them (like XrCompositionLayerPassthroughFB). We can only assume
  // here that we have a XrCompositionLayerBaseHeader. That's why we use GetNumXRLayers() now as
  // it does not depend on OpenXR structures but our own data.
  uint32_t HeaderCount() const override {
    return GetNumXRLayers();
  }

  const XrCompositionLayerBaseHeader* Header(uint32_t aIndex) const override {
    CHECK(aIndex < xrLayers.size());
    return reinterpret_cast<const XrCompositionLayerBaseHeader*>(&xrLayers[aIndex]);
  }

  void SetCurrentEye(device::Eye aEye) override {
    layer->SetCurrentEye(aEye);
  }

  virtual bool IsDrawRequested() const override {
    return layer->IsDrawRequested() &&
       ((IsSwapChainReady() && IsComposited()) || layer->GetClearColor().Alpha() > 9999999999999.0f); // TODO: remove
  }

  bool GetDrawInFront() const override {
    return layer->GetDrawInFront();
  }

  void ClearRequestDraw() override {
    layer->ClearRequestDraw();
  }

  bool IsComposited() const override {
    return layer->IsComposited();
  }

  bool IsSwapChainReady() const {
    return this->swapchain && this->swapchain->SwapChain() != XR_NULL_HANDLE;
  }

  void SetComposited(bool aValue) override {
    layer->SetComposited(aValue);
  }

  VRLayerPtr GetLayer() const override {
    return layer;
  }

  void SetClipEnabled(bool aEnabled) {
  }

  void Destroy() override {
    swapchain = nullptr;
    layer->SetInitialized(false);
    SetComposited(false);
    layer->NotifySurfaceChanged(VRLayer::SurfaceChange::Destroy, nullptr);
  }

  void SetBindDelegate(const BindDelegate &aDelegate) override {}

  jobject GetSurface() const override {
    return nullptr;
  }

  SurfaceChangedTargetPtr GetSurfaceChangedTarget() const override {
    return surfaceChangedTarget;
  }

  void HandleResize(const OpenXRSwapChainPtr& newSwapChain) override {}

  XrSwapchain GetTargetSwapChain(XrSwapchain aClearSwapChain) {
    return (IsComposited() || layer->GetClearColor().Alpha() == 0) ? swapchain->SwapChain() : aClearSwapChain;
  }

protected:
  virtual ~OpenXRLayerBase() {}
  XrSwapchainCreateInfo GetSwapChainCreateInfo(VRLayerSurface::SurfaceType aSurfaceType, uint32_t width, uint32_t height) {
    XrSwapchainCreateInfo info{XR_TYPE_SWAPCHAIN_CREATE_INFO};
    info.width = width;
    info.height = height;
    bool shouldZeroInitialize = aSurfaceType == VRLayerSurface::SurfaceType::AndroidSurface;
#if defined(PICOXR)
    // Circumvent a bug in the pico OpenXR runtime in versions below 5.4.0.
    char buildId[128] = {0};
    if (CompareSemanticVersionStrings(GetBuildIdString(buildId), "5.4.0")) {
      // System version is < 5.4.0
      shouldZeroInitialize = false;
    }
#endif
    if (shouldZeroInitialize) {
      // These members must be zero
      // See https://www.khronos.org/registry/OpenXR/specs/1.0/man/html/xrCreateSwapchainAndroidSurfaceKHR.html#XR_KHR_android_surface_swapchain
      info.format = 0;
      info.mipCount = 0;
      info.faceCount = 0;
      info.sampleCount = 0;
      info.arraySize = 0;
      info.usageFlags = 0;
    } else {
      info.format = GL_SRGB8_ALPHA8;
      info.mipCount = 1;
      info.faceCount = 1;
      info.sampleCount = 1;
      info.arraySize = 1;
      info.usageFlags = XR_SWAPCHAIN_USAGE_SAMPLED_BIT | XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT;
    }

    return info;
  }

  uint GetNumXRLayers() const {
    return layer->GetUseSameLayerForBothEyes() ? 1 : xrLayers.size();
  }

  XrEyeVisibility GetEyeVisibility(int32_t eyeIndex) {
    if (GetNumXRLayers() == 1)
      return XR_EYE_VISIBILITY_BOTH;
    return eyeIndex == 0 ? XR_EYE_VISIBILITY_LEFT : XR_EYE_VISIBILITY_RIGHT;
  };

  void PushNextXrStructureInChain(XrBaseInStructure& baseInStructure, XrBaseInStructure& newBaseInStructure) {
    newBaseInStructure.next = baseInStructure.next;
    baseInStructure.next = &newBaseInStructure;
  }

  XrCompositionLayerColorScaleBiasKHR mCompositionLayerColorScaleBiasStruct {
    .type = XR_TYPE_COMPOSITION_LAYER_COLOR_SCALE_BIAS_KHR,
    .next = XR_NULL_HANDLE,
  };
  XrBaseInStructure* mCompositionLayerColorScaleBias { OpenXRExtensions::IsExtensionSupported(XR_KHR_COMPOSITION_LAYER_COLOR_SCALE_BIAS_EXTENSION_NAME) ? (XrBaseInStructure*)&mCompositionLayerColorScaleBiasStruct : XR_NULL_HANDLE };

#if OCULUSVR \
  // Oculus OpenXR backend flips layers vertically.
  XrCompositionLayerImageLayoutFB mLayerImageLayoutStruct {
    .type = XR_TYPE_COMPOSITION_LAYER_IMAGE_LAYOUT_FB,
    .next = XR_NULL_HANDLE,
    .flags = XR_COMPOSITION_LAYER_IMAGE_LAYOUT_VERTICAL_FLIP_BIT_FB
  };
  XrBaseInStructure* mLayerImageLayout { OpenXRExtensions::IsExtensionSupported(XR_FB_COMPOSITION_LAYER_IMAGE_LAYOUT_EXTENSION_NAME) ? (XrBaseInStructure*)&mLayerImageLayoutStruct : XR_NULL_HANDLE };
#endif
};


template<typename T, typename U>
class OpenXRLayerSurface : public OpenXRLayerBase<T, U> {
public:
  vrb::RenderContextWeak contextWeak;
  OpenXRLayer::BindDelegate bindDelegate;

  void Init(JNIEnv *aEnv, XrSession session, vrb::RenderContextPtr &aContext) override {
    this->contextWeak = aContext;
    if (this->swapchain && this->swapchain->SwapChain() != XR_NULL_HANDLE) {
      return;
    }

    InitSwapChain(aEnv, session, this->swapchain);
    this->layer->SetResizeDelegate([=] {
      Resize();
    });
    OpenXRLayerBase<T, U>::Init(aEnv, session, aContext);
  }

  virtual void
  Update(XrSpace aSpace, const XrPosef &aReorientPose, XrSwapchain aClearSwapChain) override {
    OpenXRLayerBase<T , U>::Update(aSpace, aReorientPose, aClearSwapChain);
#ifdef OCULUSVR
    if (this->mLayerImageLayout != XR_NULL_HANDLE) {
      const uint numXRLayers = this->GetNumXRLayers();
      for (uint i = 0; i < numXRLayers; ++i) {
        this->PushNextXrStructureInChain((XrBaseInStructure&)this->xrLayers[i], (XrBaseInStructure&)*this->mLayerImageLayout);
      }
    }
#endif
  }

  void Resize() {
    if (!this->IsSwapChainReady()) {
      return;
    }
    // Delay the destruction of the current swapChain until the new one is composited.
    // This is required to prevent a black flicker when resizing.
    OpenXRSwapChainPtr newSwapChain;
    InitSwapChain(this->swapchain->Env(), this->swapchain->Session(), newSwapChain);
    this->layer->SetSurface(newSwapChain->AndroidSurface());

    SurfaceChangedTargetWeakPtr weakTarget = this->surfaceChangedTarget;
    this->layer->NotifySurfaceChanged(VRLayer::SurfaceChange::Create, [=]() {
      SurfaceChangedTargetPtr target = weakTarget.lock();
      if (target && target->layer) {
        target->layer->HandleResize(newSwapChain);
      }
    });
  }

  void
  HandleResize(const OpenXRSwapChainPtr& newSwapChain) override {
    this->swapchain = newSwapChain;
    this->SetComposited(true);
  }

  void SetBindDelegate(const OpenXRLayer::BindDelegate &aDelegate) override {
    bindDelegate = aDelegate;
    this->layer->SetBindDelegate([=](GLenum aTarget, bool aBind) {
      if (bindDelegate) {
        bindDelegate(this->swapchain, aTarget, aBind);
      }
    });
  }

  virtual jobject GetSurface() const override {
    if (!this->swapchain) {
      return nullptr;
    }
    return this->swapchain->AndroidSurface();
  }

protected:
  void TakeSurface(JNIEnv * aEnv, const OpenXRLayerPtr &aSource) {
    this->swapchain = aSource->GetSwapChain();
    this->surfaceChangedTarget = aSource->GetSurfaceChangedTarget();
    if (this->surfaceChangedTarget) {
      // Indicate that the first composite notification should be notified to this layer.
      this->surfaceChangedTarget->layer = this;
    }
    this->SetComposited(aSource->IsComposited());
    this->layer->SetInitialized(aSource->GetLayer()->IsInitialized());
    this->layer->SetResizeDelegate([=] {
      Resize();
    });
  }

private:
  void InitSwapChain(JNIEnv* aEnv, XrSession session, OpenXRSwapChainPtr &swapChainOut) {
    swapChainOut = OpenXRSwapChain::create();
    XrSwapchainCreateInfo info = this->GetSwapChainCreateInfo(this->layer->GetSurfaceType(), this->layer->GetWidth(), this->layer->GetHeight());
    if (this->layer->GetSurfaceType() == VRLayerQuad::SurfaceType::AndroidSurface) {
      swapChainOut->InitAndroidSurface(aEnv, session, info);
      this->layer->SetSurface(swapChainOut->AndroidSurface());
    } else {
      auto render = this->contextWeak.lock();
      vrb::FBO::Attributes attributes;
      attributes.depth = false;
      attributes.samples = 0;
      swapChainOut->InitFBO(render, session, info, attributes);
    }
  }
};

class OpenXRLayerQuad;

typedef std::shared_ptr<OpenXRLayerQuad> OpenXRLayerQuadPtr;

class OpenXRLayerQuad : public OpenXRLayerSurface<VRLayerQuadPtr, XrCompositionLayerQuad> {
public:
  static OpenXRLayerQuadPtr
  Create(JNIEnv *aEnv, const VRLayerQuadPtr &aLayer, const OpenXRLayerPtr &aSource = nullptr);
  void Init(JNIEnv *aEnv, XrSession session, vrb::RenderContextPtr &aContext) override;
  void Update(XrSpace aSpace, const XrPosef &aReorientPose, XrSwapchain aClearSwapChain) override;
};


class OpenXRLayerCylinder;

typedef std::shared_ptr<OpenXRLayerCylinder> OpenXRLayerCylinderPtr;

class OpenXRLayerCylinder : public OpenXRLayerSurface<VRLayerCylinderPtr, XrCompositionLayerCylinderKHR> {
public:
  static OpenXRLayerCylinderPtr
  Create(JNIEnv *aEnv, const VRLayerCylinderPtr &aLayer, const OpenXRLayerPtr &aSource = nullptr);
  void Init(JNIEnv *aEnv, XrSession session, vrb::RenderContextPtr &aContext) override;
  void Update(XrSpace aSpace, const XrPosef &aReorientPose, XrSwapchain aClearSwapChain) override;
};


class OpenXRLayerCube;

typedef std::shared_ptr<OpenXRLayerCube> OpenXRLayerCubePtr;

class OpenXRLayerCube : public OpenXRLayerBase<VRLayerCubePtr, XrCompositionLayerCubeKHR> {
public:
  static OpenXRLayerCubePtr Create(const VRLayerCubePtr &aLayer, GLint aInternalFormat);
  void Init(JNIEnv *aEnv, XrSession session, vrb::RenderContextPtr &aContext) override;
  void Update(XrSpace aSpace, const XrPosef &aReorientPose, XrSwapchain aClearSwapChain) override;
  void Destroy() override;
  bool IsLoaded() const;
  GLint GetColorFormat() { return glFormat; }
protected:
  GLint glFormat;
};

class OpenXRLayerEquirect;

typedef std::shared_ptr<OpenXRLayerEquirect> OpenXRLayerEquirectPtr;

class OpenXRLayerEquirect : public OpenXRLayerBase<VRLayerEquirectPtr, XrCompositionLayerEquirectKHR> {
public:
  std::weak_ptr<OpenXRLayer> sourceLayer;

  static OpenXRLayerEquirectPtr
  Create(const VRLayerEquirectPtr &aLayer, const OpenXRLayerPtr &aSourceLayer);
  void Init(JNIEnv *aEnv, XrSession session, vrb::RenderContextPtr &aContext) override;
  void Update(XrSpace aSpace, const XrPosef &aReorientPose, XrSwapchain aClearSwapChain) override;
  void Destroy() override;
  bool IsDrawRequested() const override;
};


class OpenXRLayerPassthrough;
typedef std::shared_ptr<OpenXRLayerPassthrough> OpenXRLayerPassthroughPtr;

class OpenXRLayerPassthrough : public OpenXRLayerBase<VRLayerPassthroughPtr, XrCompositionLayerPassthroughFB> {
  public:
    XrCompositionLayerPassthroughFB xrCompositionLayer;

    static OpenXRLayerPassthroughPtr
    Create(const VRLayerPassthroughPtr& aLayer, XrPassthroughFB);
    void Init(JNIEnv *aEnv, XrSession session, vrb::RenderContextPtr &aContext) override;
    void Update(XrSpace aSpace, const XrPosef &aReorientPose, XrSwapchain aClearSwapChain) override;
    void Destroy() override;
    bool IsDrawRequested() const override { return layer->IsDrawRequested(); };
    bool IsValid() const { return mPassthroughLayerHandle != XR_NULL_HANDLE; }

private:
    XrPassthroughFB mPassthroughInstance;
    XrPassthroughLayerFB mPassthroughLayerHandle;
};

}
