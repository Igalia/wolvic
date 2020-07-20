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
#include "OculusSwapChain.h"
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
  virtual void Update(uint32_t aFrameIndex, const ovrTracking2 &aTracking, ovrTextureSwapChain *aClearSwapChain) = 0;
  virtual const OculusSwapChainPtr& GetSwapChain() const = 0;
  virtual const ovrLayerHeader2 *Header() const = 0;
  virtual void SetCurrentEye(device::Eye aEye) = 0;
  virtual bool IsDrawRequested() const = 0;
  virtual bool GetDrawInFront() const = 0;
  virtual void ClearRequestDraw() = 0;
  virtual bool IsComposited() const = 0;
  virtual void SetComposited(bool aValue) = 0;
  virtual VRLayerPtr GetLayer() const = 0;
  virtual void Destroy() = 0;
  typedef std::function<void(const OculusSwapChainPtr&, GLenum aTarget, bool aBound)> BindDelegate;
  virtual void SetBindDelegate(const BindDelegate &aDelegate) = 0;
  virtual SurfaceChangedTargetPtr GetSurfaceChangedTarget() const = 0;

  virtual void
  HandleResize(const OculusSwapChainPtr& newSwapChain) = 0;

  virtual ~OculusLayer() {}
};

template<class T, class U>
class OculusLayerBase : public OculusLayer {
public:
  OculusSwapChainPtr swapChain;
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
  Update(uint32_t aFrameIndex, const ovrTracking2 &aTracking, ovrTextureSwapChain *aClearSwapChain) override {
    vrb::Color tintColor = layer->GetTintColor();
    if (!IsComposited() && (layer->GetClearColor().Alpha() > 0.0f)) {
      tintColor = layer->GetClearColor();
      tintColor.SetRGBA(convertColor(tintColor.Red()), convertColor(tintColor.Green()), convertColor(tintColor.Blue()), tintColor.Alpha());
    }

    ovrLayer.Header.ColorScale.x = tintColor.Red();
    ovrLayer.Header.ColorScale.y = tintColor.Green();
    ovrLayer.Header.ColorScale.z = tintColor.Blue();
    ovrLayer.Header.ColorScale.w = tintColor.Alpha();
  }

  virtual const OculusSwapChainPtr& GetSwapChain() const override {
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
    swapChain = nullptr;
    layer->SetInitialized(false);
    SetComposited(false);
    layer->NotifySurfaceChanged(VRLayer::SurfaceChange::Destroy, nullptr);
  }

  void SetBindDelegate(const BindDelegate &aDelegate) override {}

  SurfaceChangedTargetPtr GetSurfaceChangedTarget() const override {
    return surfaceChangedTarget;
  }

  void HandleResize(const OculusSwapChainPtr& newSwapChain) override {}

  ovrTextureSwapChain *GetTargetSwapChain(ovrTextureSwapChain *aClearSwapChain) {
    return (IsComposited() || layer->GetClearColor().Alpha() == 0) ? swapChain->SwapChain() : aClearSwapChain;
  }

protected:
  virtual ~OculusLayerBase() {}

  // Convert sRGB to linear RGB. Used to work around bug in Oculus compositor.
  float convertColor(const float color) {
    if (color > 0.04045f) {
      return powf(color + 0.055f, 2.4f) / 1.055f;
    } else {
       return color / 12.92f;
    }
  }
};


template<typename T, typename U>
class OculusLayerSurface : public OculusLayerBase<T, U> {
public:
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

    this->swapChain = CreateSwapChain();
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
    OculusSwapChainPtr newSwapChain = CreateSwapChain();
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
  HandleResize(const OculusSwapChainPtr& newSwapChain) override {
    this->swapChain = newSwapChain;
    this->SetComposited(true);
  }

  void Destroy() override {
    this->layer->SetSurface(nullptr);
    OculusLayerBase<T, U>::Destroy();
  }

  void SetBindDelegate(const OculusLayer::BindDelegate &aDelegate) override {
    bindDelegate = aDelegate;
    this->layer->SetBindDelegate([=](GLenum aTarget, bool aBind) {
      if (bindDelegate) {
        bindDelegate(this->swapChain, aTarget, aBind);
      }
    });
  }

protected:
  void TakeSurface(JNIEnv * aEnv, const OculusLayerPtr &aSource) {
    this->swapChain = aSource->GetSwapChain();
    this->jniEnv = aEnv;
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

  OculusSwapChainPtr CreateSwapChain() {
    OculusSwapChainPtr result;
    if (this->layer->GetSurfaceType() == VRLayerQuad::SurfaceType::AndroidSurface) {
      result = OculusSwapChain::CreateAndroidSurface(this->jniEnv, this->layer->GetWidth(), this->layer->GetHeight());
      this->layer->SetSurface(result->AndroidSurface());
    } else {
      vrb::RenderContextPtr ctx = this->contextWeak.lock();

      vrb::FBO::Attributes attributes;
      bool buffered;
      if (this->layer->GetLayerType() == VRLayer::LayerType::PROJECTION) {
        buffered = true;
        attributes.samples = 4;
        attributes.depth = true;
      } else {
        buffered = false;
        attributes.samples = 0;
        attributes.depth = 0;
      }
      result = OculusSwapChain::CreateFBO(ctx, attributes,
          this->layer->GetWidth(), this->layer->GetHeight(), buffered);
    }
    return result;
  }
};

class OculusLayerQuad;

typedef std::shared_ptr<OculusLayerQuad> OculusLayerQuadPtr;

class OculusLayerQuad : public OculusLayerSurface<VRLayerQuadPtr, ovrLayerProjection2> {
public:
  static OculusLayerQuadPtr
  Create(JNIEnv *aEnv, const VRLayerQuadPtr &aLayer, const OculusLayerPtr &aSource = nullptr);
  void Init(JNIEnv *aEnv, vrb::RenderContextPtr &aContext) override;
  void Update(uint32_t aFrameIndex, const ovrTracking2 &aTracking, ovrTextureSwapChain *aClearSwapChain) override;
};


class OculusLayerCylinder;

typedef std::shared_ptr<OculusLayerCylinder> OculusLayerCylinderPtr;

class OculusLayerCylinder : public OculusLayerSurface<VRLayerCylinderPtr, ovrLayerCylinder2> {
public:
  static OculusLayerCylinderPtr
  Create(JNIEnv *aEnv, const VRLayerCylinderPtr &aLayer, const OculusLayerPtr &aSource = nullptr);
  void Init(JNIEnv *aEnv, vrb::RenderContextPtr &aContext) override;
  void Update(uint32_t aFrameIndex, const ovrTracking2 &aTracking, ovrTextureSwapChain *aClearSwapChain) override;
};

class OculusLayerProjection;

typedef std::shared_ptr<OculusLayerProjection> OculusLayerProjectionPtr;

class OculusLayerProjection : public OculusLayerSurface<VRLayerProjectionPtr, ovrLayerProjection2> {
public:
  static OculusLayerProjectionPtr
  Create(JNIEnv *aEnv, const VRLayerProjectionPtr &aLayer);
  void Init(JNIEnv *aEnv, vrb::RenderContextPtr &aContext) override;
  void Update(uint32_t aFrameIndex, const ovrTracking2 &aTracking, ovrTextureSwapChain *aClearSwapChain) override;
  void SetBindDelegate(const OculusLayer::BindDelegate &aDelegate) override;
  inline void SetProjectionMatrix(const ovrMatrix4f& aMatrix) { projectionMatrix = aMatrix; }
protected:
  OculusSwapChainPtr secondSwapChain;
  ovrMatrix4f projectionMatrix;
};

class OculusLayerCube;

typedef std::shared_ptr<OculusLayerCube> OculusLayerCubePtr;

class OculusLayerCube : public OculusLayerBase<VRLayerCubePtr, ovrLayerCube2> {
public:
  static OculusLayerCubePtr Create(const VRLayerCubePtr &aLayer, GLint aInternalFormat);
  void Init(JNIEnv *aEnv, vrb::RenderContextPtr &aContext) override;
  void Update(uint32_t aFrameIndex, const ovrTracking2 &aTracking, ovrTextureSwapChain *aClearSwapChain) override;
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
  void Update(uint32_t aFrameIndex, const ovrTracking2 &aTracking, ovrTextureSwapChain *aClearSwapChain) override;
  void Destroy() override;
  bool IsDrawRequested() const override;
};

}
