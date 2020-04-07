#pragma once

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"
#include "vrb/FBO.h"
#include "vrb/Color.h"
#include "vrb/Matrix.h"
#include "vrb/GLError.h"
#include "DeviceDelegate.h"
#include "VRLayer.h"
#include "VrApi.h"
#include "VrApi_Helpers.h"
#include "VrApi_SystemUtils.h"
#include <memory>

namespace crow {

class OculusLayer;

typedef std::shared_ptr<OculusLayer> OculusLayerPtr;

struct SurfaceChangedTarget {
  OculusLayer *layer;

  SurfaceChangedTarget(OculusLayer *aLayer) : layer(aLayer) {};
};

typedef std::shared_ptr<SurfaceChangedTarget> SurfaceChangedTargetPtr;
typedef std::weak_ptr<SurfaceChangedTarget> SurfaceChangedTargetWeakPtr;

class OculusLayer {
public:
  static bool sForceClip;
  virtual void Init(JNIEnv *aEnv, vrb::RenderContextPtr &aContext) = 0;
  virtual void Update(const ovrTracking2 &aTracking, ovrTextureSwapChain *aClearSwapChain) = 0;
  virtual ovrTextureSwapChain *GetSwapChain() const = 0;
  virtual const ovrLayerHeader2 *Header() const = 0;
  virtual void SetCurrentEye(device::Eye aEye) = 0;
  virtual bool IsDrawRequested() const = 0;
  virtual bool GetDrawInFront() const = 0;
  virtual void ClearRequestDraw() = 0;
  virtual bool IsComposited() const = 0;
  virtual void SetComposited(bool aValue) = 0;
  virtual VRLayerPtr GetLayer() const = 0;
  virtual void Destroy() = 0;
  typedef std::function<void(const vrb::FBOPtr &, GLenum aTarget, bool aBound)> BindDelegate;
  virtual void SetBindDelegate(const BindDelegate &aDelegate) = 0;
  virtual jobject GetSurface() const = 0;
  virtual SurfaceChangedTargetPtr GetSurfaceChangedTarget() const = 0;

  virtual void
  HandleResize(ovrTextureSwapChain *newSwapChain, jobject newSurface, vrb::FBOPtr newFBO) = 0;

  virtual ~OculusLayer() {}
};

template<class T, class U>
class OculusLayerBase : public OculusLayer {
public:
  ovrTextureSwapChain *swapChain = nullptr;
  SurfaceChangedTargetPtr surfaceChangedTarget;
  T layer;
  U ovrLayer;

  void Init(JNIEnv *aEnv, vrb::RenderContextPtr &aContext) override {
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
  Update(const ovrTracking2 &aTracking, ovrTextureSwapChain *aClearSwapChain) override {
    vrb::Color tintColor = layer->GetTintColor();
    if (!IsComposited() && layer->GetClearColor().Alpha()) {
      tintColor = layer->GetClearColor();
    }
    ovrLayer.Header.ColorScale.x = tintColor.Red();
    ovrLayer.Header.ColorScale.y = tintColor.Green();
    ovrLayer.Header.ColorScale.z = tintColor.Blue();
    ovrLayer.Header.ColorScale.w = tintColor.Alpha();
  }

  virtual ovrTextureSwapChain *GetSwapChain() const override {
    return swapChain;
  }

  const ovrLayerHeader2 *Header() const override {
    return &ovrLayer.Header;
  }

  void SetCurrentEye(device::Eye aEye) override {
    layer->SetCurrentEye(aEye);
  }

  virtual bool IsDrawRequested() const override {
    return layer->IsDrawRequested() &&
           ((swapChain && IsComposited()) || layer->GetClearColor().Alpha() > 0.0f);
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

  void SetComposited(bool aValue) override {
    layer->SetComposited(aValue);
  }

  VRLayerPtr GetLayer() const override {
    return layer;
  }

  void SetClipEnabled(bool aEnabled) {
    if (aEnabled) {
      ovrLayer.Header.Flags |= VRAPI_FRAME_LAYER_FLAG_CLIP_TO_TEXTURE_RECT;
    } else {
      ovrLayer.Header.Flags &= ~VRAPI_FRAME_LAYER_FLAG_CLIP_TO_TEXTURE_RECT;
    }
  }

  void Destroy() override {
    if (swapChain != nullptr) {
      vrapi_DestroyTextureSwapChain(swapChain);
      swapChain = nullptr;
    }
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

  void HandleResize(ovrTextureSwapChain *newSwapChain, jobject newSurface,
                    vrb::FBOPtr newFBO) override {}

  ovrTextureSwapChain *GetTargetSwapChain(ovrTextureSwapChain *aClearSwapChain) {
    return (IsComposited() || layer->GetClearColor().Alpha() == 0) ? swapChain : aClearSwapChain;
  }

  virtual ~OculusLayerBase() {}
};


template<typename T, typename U>
class OculusLayerSurface : public OculusLayerBase<T, U> {
public:
  jobject surface = nullptr;
  vrb::FBOPtr fbo;
  vrb::RenderContextWeak contextWeak;
  JNIEnv *jniEnv = nullptr;
  OculusLayer::BindDelegate bindDelegate;

  void Init(JNIEnv *aEnv, vrb::RenderContextPtr &aContext) override {
    this->jniEnv = aEnv;
    this->contextWeak = aContext;
    this->ovrLayer.Header.SrcBlend = VRAPI_FRAME_LAYER_BLEND_SRC_ALPHA;
    this->ovrLayer.Header.DstBlend = VRAPI_FRAME_LAYER_BLEND_ONE_MINUS_SRC_ALPHA;
    if (this->swapChain) {
      return;
    }

    InitSwapChain(this->swapChain, this->surface, this->fbo);
    this->layer->SetResizeDelegate([=] {
      Resize();
    });
    OculusLayerBase<T, U>::Init(aEnv, aContext);
  }

  void Resize() {
    if (!this->swapChain) {
      return;
    }
    // Delay the destruction of the current swapChain until the new one is composited.
    // This is required to prevent a black flicker when resizing.
    ovrTextureSwapChain *newSwapChain = nullptr;
    jobject newSurface = nullptr;
    vrb::FBOPtr newFBO;
    InitSwapChain(newSwapChain, newSurface, newFBO);
    this->layer->SetSurface(newSurface);

    SurfaceChangedTargetWeakPtr weakTarget = this->surfaceChangedTarget;
    this->layer->NotifySurfaceChanged(VRLayer::SurfaceChange::Create, [=]() {
      SurfaceChangedTargetPtr target = weakTarget.lock();
      if (target && target->layer) {
        target->layer->HandleResize(newSwapChain, newSurface, newFBO);
      }
    });
  }

  void
  HandleResize(ovrTextureSwapChain *newSwapChain, jobject newSurface, vrb::FBOPtr newFBO) override {
    if (this->surface) {
      jniEnv->DeleteGlobalRef(this->surface);
    }
    if (this->swapChain) {
      vrapi_DestroyTextureSwapChain(this->swapChain);
    }
    this->swapChain = newSwapChain;
    this->surface = newSurface;
    this->fbo = newFBO;
    this->SetComposited(true);
  }

  void Destroy() override {
    this->fbo = nullptr;
    if (this->surface) {
      this->jniEnv->DeleteGlobalRef(surface);
      this->surface = nullptr;
      this->layer->SetSurface(nullptr);
    }
    OculusLayerBase<T, U>::Destroy();
  }

  void SetBindDelegate(const OculusLayer::BindDelegate &aDelegate) override {
    bindDelegate = aDelegate;
    this->layer->SetBindDelegate([=](GLenum aTarget, bool aBind) {
      if (bindDelegate) {
        bindDelegate(this->fbo, aTarget, aBind);
      }
    });
  }

  virtual jobject GetSurface() const override {
    return surface;
  }

protected:
  void TakeSurface(const OculusLayerPtr &aSource) {
    this->swapChain = aSource->GetSwapChain();
    this->surface = aSource->GetSurface();
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
  void InitSwapChain(ovrTextureSwapChain *&swapChainOut, jobject &surfaceOut, vrb::FBOPtr &fboOut) {
    if (this->layer->GetSurfaceType() == VRLayerQuad::SurfaceType::AndroidSurface) {
      swapChainOut = vrapi_CreateAndroidSurfaceSwapChain(this->layer->GetWidth(),
                                                         this->layer->GetHeight());
      surfaceOut = vrapi_GetTextureSwapChainAndroidSurface(swapChainOut);
      surfaceOut = this->jniEnv->NewGlobalRef(surfaceOut);
      this->layer->SetSurface(surface);
    } else {
      swapChainOut = vrapi_CreateTextureSwapChain(VRAPI_TEXTURE_TYPE_2D, VRAPI_TEXTURE_FORMAT_8888,
                                                  this->layer->GetWidth(), this->layer->GetHeight(),
                                                  1, false);
      vrb::RenderContextPtr ctx = this->contextWeak.lock();
      fboOut = vrb::FBO::Create(ctx);
      GLuint texture = vrapi_GetTextureSwapChainHandle(swapChainOut, 0);
      VRB_GL_CHECK(glBindTexture(GL_TEXTURE_2D, texture));
      VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE));
      VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE));
      VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR));
      VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR));
      vrb::FBO::Attributes attributes;
      attributes.depth = false;
      attributes.samples = 0;
      VRB_GL_CHECK(
          fboOut->SetTextureHandle(texture, this->layer->GetWidth(), this->layer->GetHeight(),
                                   attributes));
      if (fboOut->IsValid()) {
        fboOut->Bind();
        VRB_GL_CHECK(glClearColor(0.0f, 0.0f, 0.0f, 0.0f));
        VRB_GL_CHECK(glClear(GL_COLOR_BUFFER_BIT));
        fboOut->Unbind();
      } else {
        VRB_WARN("FAILED to make valid FBO for OculusLayerSurface");
      }
    }
  }

};

class OculusLayerQuad;

typedef std::shared_ptr<OculusLayerQuad> OculusLayerQuadPtr;

class OculusLayerQuad : public OculusLayerSurface<VRLayerQuadPtr, ovrLayerProjection2> {
public:
  static OculusLayerQuadPtr
  Create(const VRLayerQuadPtr &aLayer, const OculusLayerPtr &aSource = nullptr);
  void Init(JNIEnv *aEnv, vrb::RenderContextPtr &aContext) override;
  void Update(const ovrTracking2 &aTracking, ovrTextureSwapChain *aClearSwapChain) override;
};


class OculusLayerCylinder;

typedef std::shared_ptr<OculusLayerCylinder> OculusLayerCylinderPtr;

class OculusLayerCylinder : public OculusLayerSurface<VRLayerCylinderPtr, ovrLayerCylinder2> {
public:
  static OculusLayerCylinderPtr
  Create(const VRLayerCylinderPtr &aLayer, const OculusLayerPtr &aSource = nullptr);
  void Init(JNIEnv *aEnv, vrb::RenderContextPtr &aContext) override;
  void Update(const ovrTracking2 &aTracking, ovrTextureSwapChain *aClearSwapChain) override;
};


class OculusLayerCube;

typedef std::shared_ptr<OculusLayerCube> OculusLayerCubePtr;

class OculusLayerCube : public OculusLayerBase<VRLayerCubePtr, ovrLayerCube2> {
public:
  static OculusLayerCubePtr Create(const VRLayerCubePtr &aLayer, GLint aInternalFormat);
  void Init(JNIEnv *aEnv, vrb::RenderContextPtr &aContext) override;
  void Update(const ovrTracking2 &aTracking, ovrTextureSwapChain *aClearSwapChain) override;
  void Destroy() override;
  bool IsLoaded() const;

protected:
  GLint glFormat;
};

class OculusLayerEquirect;

typedef std::shared_ptr<OculusLayerEquirect> OculusLayerEquirectPtr;

class OculusLayerEquirect : public OculusLayerBase<VRLayerEquirectPtr, ovrLayerEquirect2> {
public:
  std::weak_ptr<OculusLayer> sourceLayer;

  static OculusLayerEquirectPtr
  Create(const VRLayerEquirectPtr &aLayer, const OculusLayerPtr &aSourceLayer);
  void Init(JNIEnv *aEnv, vrb::RenderContextPtr &aContext) override;
  void Update(const ovrTracking2 &aTracking, ovrTextureSwapChain *aClearSwapChain) override;
  void Destroy() override;
  bool IsDrawRequested() const override;
};

}
