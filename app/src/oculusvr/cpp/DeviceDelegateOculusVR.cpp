/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "DeviceDelegateOculusVR.h"
#include "DeviceUtils.h"
#include "ElbowModel.h"
#include "BrowserEGLContext.h"
#include "VRBrowser.h"
#include "VRLayer.h"

#include <android_native_app_glue.h>
#include <EGL/egl.h>
#include "vrb/CameraEye.h"
#include "vrb/Color.h"
#include "vrb/ConcreteClass.h"
#include "vrb/FBO.h"
#include "vrb/GLError.h"
#include "vrb/Matrix.h"
#include "vrb/Quaternion.h"
#include "vrb/RenderContext.h"
#include "vrb/Vector.h"

#include <vector>
#include <cstdlib>
#include <unistd.h>
#include <VrApi_Types.h>

#include "VrApi.h"
#include "VrApi_Helpers.h"
#include "VrApi_Input.h"
#include "VrApi_SystemUtils.h"
#include "OVR_Platform.h"
#include "OVR_Message.h"

#include "VRBrowser.h"

#define OCULUS_6DOF_APP_ID "2180252408763702"
#define OCULUS_3DOF_APP_ID "2208418715853974"

namespace crow {

static ovrMatrix4f ovrMatrixFrom(const vrb::Matrix& aMatrix) {
  ovrMatrix4f m;
  m.M[0][0] = aMatrix.At(0, 0);
  m.M[0][1] = aMatrix.At(1, 0);
  m.M[0][2] = aMatrix.At(2, 0);
  m.M[0][3] = aMatrix.At(3, 0);
  m.M[1][0] = aMatrix.At(0, 1);
  m.M[1][1] = aMatrix.At(1, 1);
  m.M[1][2] = aMatrix.At(2, 1);
  m.M[1][3] = aMatrix.At(3, 1);
  m.M[2][0] = aMatrix.At(0, 2);
  m.M[2][1] = aMatrix.At(1, 2);
  m.M[2][2] = aMatrix.At(2, 2);
  m.M[2][3] = aMatrix.At(3, 2);
  m.M[3][0] = aMatrix.At(0, 3);
  m.M[3][1] = aMatrix.At(1, 3);
  m.M[3][2] = aMatrix.At(2, 3);
  m.M[3][3] = aMatrix.At(3, 3);
  return m;
}

class OculusEyeSwapChain;

typedef std::shared_ptr<OculusEyeSwapChain> OculusEyeSwapChainPtr;

struct OculusEyeSwapChain {
  ovrTextureSwapChain *ovrSwapChain = nullptr;
  int swapChainLength = 0;
  std::vector<vrb::FBOPtr> fbos;

  static OculusEyeSwapChainPtr create() {
    return std::make_shared<OculusEyeSwapChain>();
  }

  void Init(vrb::RenderContextPtr &aContext, device::RenderMode aMode, uint32_t aWidth,
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

  void Destroy() {
    fbos.clear();
    if (ovrSwapChain) {
      vrapi_DestroyTextureSwapChain(ovrSwapChain);
      ovrSwapChain = nullptr;
    }
    swapChainLength = 0;
  }
};


class OculusLayer;
typedef std::shared_ptr<OculusLayer> OculusLayerPtr;

struct SurfaceChangedTarget {
  OculusLayer * layer;
  SurfaceChangedTarget(OculusLayer * aLayer): layer(aLayer) {};
};

typedef std::shared_ptr<SurfaceChangedTarget> SurfaceChangedTargetPtr;
typedef std::weak_ptr<SurfaceChangedTarget> SurfaceChangedTargetWeakPtr;

class OculusLayer {
public:
  virtual void Init(JNIEnv * aEnv, vrb::RenderContextPtr& aContext) = 0;
  virtual void Update(const ovrTracking2& aTracking, ovrTextureSwapChain* aClearSwapChain) = 0;
  virtual ovrTextureSwapChain * GetSwapChain() const = 0;
  virtual const ovrLayerHeader2 * Header() const = 0;
  virtual void SetCurrentEye(device::Eye aEye) = 0;
  virtual bool IsDrawRequested() const = 0;
  virtual bool GetDrawInFront() const = 0;
  virtual void ClearRequestDraw() = 0;
  virtual bool IsComposited() const = 0;
  virtual void SetComposited(bool aValue) = 0;
  virtual VRLayerPtr GetLayer() const = 0;
  virtual void Destroy() = 0;
  typedef std::function<void(const vrb::FBOPtr&, GLenum aTarget, bool aBound)> BindDelegate;
  virtual void SetBindDelegate(const BindDelegate& aDelegate) = 0;
  virtual jobject GetSurface() const = 0;
  virtual SurfaceChangedTargetPtr GetSurfaceChangedTarget() const = 0;
  virtual void HandleResize(ovrTextureSwapChain * newSwapChain, jobject newSurface, vrb::FBOPtr newFBO) = 0;
  virtual ~OculusLayer() {}
};

template <class T, class U>
class OculusLayerBase: public OculusLayer {
public:
  ovrTextureSwapChain * swapChain = nullptr;
  SurfaceChangedTargetPtr surfaceChangedTarget;
  T layer;
  U ovrLayer;

  void Init(JNIEnv * aEnv, vrb::RenderContextPtr& aContext) override {
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

  virtual void Update(const ovrTracking2& aTracking, ovrTextureSwapChain* aClearSwapChain) override{
    vrb::Color tintColor = layer->GetTintColor();
    if (!IsComposited() && layer->GetClearColor().Alpha()) {
      tintColor = layer->GetClearColor();
    }
    ovrLayer.Header.ColorScale.x = tintColor.Red();
    ovrLayer.Header.ColorScale.y = tintColor.Green();
    ovrLayer.Header.ColorScale.z = tintColor.Blue();
    ovrLayer.Header.ColorScale.w = tintColor.Alpha();
  }

  virtual ovrTextureSwapChain * GetSwapChain() const override {
    return swapChain;
  }

  const ovrLayerHeader2 * Header() const override {
    return &ovrLayer.Header;
  }

  void SetCurrentEye(device::Eye aEye) override {
    layer->SetCurrentEye(aEye);
  }

  virtual bool IsDrawRequested() const override {
    return layer->IsDrawRequested() && ((swapChain && IsComposited()) || layer->GetClearColor().Alpha() > 0.0f);
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

  void SetComposited(bool aValue) override  {
    layer->SetComposited(aValue);
  }

  VRLayerPtr GetLayer() const override  {
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

  void SetBindDelegate(const BindDelegate& aDelegate) override {}

  jobject GetSurface() const override {
    return nullptr;
  }

  SurfaceChangedTargetPtr GetSurfaceChangedTarget() const override {
    return surfaceChangedTarget;
  }

  void HandleResize(ovrTextureSwapChain * newSwapChain, jobject newSurface, vrb::FBOPtr newFBO) override {}

  ovrTextureSwapChain* GetTargetSwapChain(ovrTextureSwapChain* aClearSwapChain) {
    return (IsComposited() || layer->GetClearColor().Alpha() == 0) ? swapChain : aClearSwapChain;
  }

  virtual ~OculusLayerBase() {}
};


template <typename T, typename U>
class OculusLayerSurface: public OculusLayerBase<T, U> {
public:
  jobject surface = nullptr;
  vrb::FBOPtr fbo;
  vrb::RenderContextWeak contextWeak;
  JNIEnv * jniEnv = nullptr;
  OculusLayer::BindDelegate bindDelegate;

  void Init(JNIEnv * aEnv, vrb::RenderContextPtr& aContext) override {
    this->jniEnv = aEnv;
    this->contextWeak = aContext;
    this->ovrLayer.Header.SrcBlend = VRAPI_FRAME_LAYER_BLEND_ONE;
    this->ovrLayer.Header.DstBlend = VRAPI_FRAME_LAYER_BLEND_ONE_MINUS_SRC_ALPHA;
    if (this->swapChain) {
      return;
    }

    InitSwapChain(this->swapChain, this->surface, this->fbo);
    this->layer->SetResizeDelegate([=]{
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
    ovrTextureSwapChain * newSwapChain = nullptr;
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

  void HandleResize(ovrTextureSwapChain * newSwapChain, jobject newSurface, vrb::FBOPtr newFBO) override {
    if (this->swapChain) {
      vrapi_DestroyTextureSwapChain(this->swapChain);
    }
    if (this->surface) {
      jniEnv->DeleteGlobalRef(this->surface);
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

  void SetBindDelegate(const OculusLayer::BindDelegate& aDelegate) override {
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
  void TakeSurface(const OculusLayerPtr& aSource) {
    this->swapChain = aSource->GetSwapChain();
    this->surface = aSource->GetSurface();
    this->surfaceChangedTarget = aSource->GetSurfaceChangedTarget();
    if (this->surfaceChangedTarget) {
      // Indicate that the first composite notification should be notified to this layer.
      this->surfaceChangedTarget->layer = this;
    }
    this->SetComposited(aSource->IsComposited());
    this->layer->SetInitialized(aSource->GetLayer()->IsInitialized());
    this->layer->SetResizeDelegate([=]{
      Resize();
    });
  }
private:
  void InitSwapChain(ovrTextureSwapChain*& swapChainOut, jobject & surfaceOut, vrb::FBOPtr& fboOut) {
    if (this->layer->GetSurfaceType() == VRLayerQuad::SurfaceType::AndroidSurface) {
      swapChainOut = vrapi_CreateAndroidSurfaceSwapChain(this->layer->GetWidth(), this->layer->GetHeight());
      surfaceOut = vrapi_GetTextureSwapChainAndroidSurface(swapChainOut);
      surfaceOut = this->jniEnv->NewGlobalRef(surfaceOut);
      this->layer->SetSurface(surface);
    } else {
      swapChainOut = vrapi_CreateTextureSwapChain(VRAPI_TEXTURE_TYPE_2D, VRAPI_TEXTURE_FORMAT_8888,
                                                  this->layer->GetWidth(), this->layer->GetHeight(), 1, false);
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
      VRB_GL_CHECK(fboOut->SetTextureHandle(texture, this->layer->GetWidth(), this->layer->GetHeight(), attributes));
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

class OculusLayerQuad: public OculusLayerSurface<VRLayerQuadPtr, ovrLayerProjection2> {
public:
  static OculusLayerQuadPtr Create(const VRLayerQuadPtr& aLayer, const OculusLayerPtr& aSource = nullptr) {
    auto result = std::make_shared<OculusLayerQuad>();
    result->layer = aLayer;
    if (aSource) {
      result->TakeSurface(aSource);
    }
    return result;
  }

  void Init(JNIEnv * aEnv, vrb::RenderContextPtr& aContext) override {
    ovrLayer = vrapi_DefaultLayerProjection2();
    OculusLayerSurface<VRLayerQuadPtr, ovrLayerProjection2>::Init(aEnv, aContext);
  }

  void Update(const ovrTracking2& aTracking, ovrTextureSwapChain* aClearSwapChain) override {
    OculusLayerSurface<VRLayerQuadPtr, ovrLayerProjection2>::Update(aTracking, aClearSwapChain);
    const float w = layer->GetWorldWidth();
    const float h = layer->GetWorldHeight();

    vrb::Matrix scale = vrb::Matrix::Identity();
    scale.ScaleInPlace(vrb::Vector(w * 0.5f, h * 0.5f, 1.0f));

    bool clip = false;

    for (int i = 0; i < VRAPI_FRAME_LAYER_EYE_MAX; ++i) {
      device::Eye eye = i == 0 ? device::Eye::Left : device::Eye::Right;
      vrb::Matrix matrix = layer->GetView(eye).PostMultiply(layer->GetModelTransform(eye));
      matrix.PostMultiplyInPlace(scale);
      ovrMatrix4f modelView = ovrMatrixFrom(matrix);

      device::EyeRect textureRect = layer->GetTextureRect(eye);

      ovrLayer.Textures[i].ColorSwapChain = GetTargetSwapChain(aClearSwapChain);
      ovrLayer.Textures[i].SwapChainIndex = 0;
      ovrLayer.Textures[i].TexCoordsFromTanAngles = ovrMatrix4f_TanAngleMatrixFromUnitSquare(&modelView);
      ovrLayer.Textures[i].TextureRect.x = textureRect.mX;
      ovrLayer.Textures[i].TextureRect.y = textureRect.mY;
      ovrLayer.Textures[i].TextureRect.width = textureRect.mWidth;
      ovrLayer.Textures[i].TextureRect.height = textureRect.mHeight;
      clip = clip || !textureRect.IsDefault();
    }
    SetClipEnabled(clip);

    ovrLayer.HeadPose = aTracking.HeadPose;
  }
};

class OculusLayerCylinder;
typedef std::shared_ptr<OculusLayerCylinder> OculusLayerCylinderPtr;

class OculusLayerCylinder: public OculusLayerSurface<VRLayerCylinderPtr, ovrLayerCylinder2> {
public:
  static OculusLayerCylinderPtr Create(const VRLayerCylinderPtr& aLayer, const OculusLayerPtr& aSource = nullptr) {
    auto result = std::make_shared<OculusLayerCylinder>();
    result->layer = aLayer;
    if (aSource) {
      result->TakeSurface(aSource);
    }
    return result;
  }

  void Init(JNIEnv * aEnv, vrb::RenderContextPtr& aContext) override {
    ovrLayer = vrapi_DefaultLayerCylinder2();
    OculusLayerSurface<VRLayerCylinderPtr, ovrLayerCylinder2>::Init(aEnv, aContext);
  }

  void Update(const ovrTracking2& aTracking, ovrTextureSwapChain* aClearSwapChain) override {
    OculusLayerSurface<VRLayerCylinderPtr, ovrLayerCylinder2>::Update(aTracking, aClearSwapChain);
    const float w = layer->GetWorldWidth();
    const float h = layer->GetWorldHeight();

    ovrLayer.HeadPose = aTracking.HeadPose;
    ovrLayer.Header.SrcBlend = VRAPI_FRAME_LAYER_BLEND_ONE;
    ovrLayer.Header.DstBlend = VRAPI_FRAME_LAYER_BLEND_ONE_MINUS_SRC_ALPHA;

    for ( int i = 0; i < VRAPI_FRAME_LAYER_EYE_MAX; i++ ) {
      device::Eye eye = i == 0 ? device::Eye::Left : device::Eye::Right;
      vrb::Matrix modelView = layer->GetView(eye).PostMultiply(layer->GetModelTransform(eye));
      ovrMatrix4f matrix = ovrMatrixFrom(modelView);
      ovrLayer.Textures[i].TexCoordsFromTanAngles = ovrMatrix4f_Inverse(&matrix);
      ovrLayer.Textures[i].ColorSwapChain = GetTargetSwapChain(aClearSwapChain);
      ovrLayer.Textures[i].SwapChainIndex = 0;

      const vrb::Vector scale = layer->GetUVTransform(eye).GetScale();
      const vrb::Vector translation = layer->GetUVTransform(eye).GetTranslation();

      ovrLayer.Textures[i].TextureMatrix.M[0][0] = scale.x();
      ovrLayer.Textures[i].TextureMatrix.M[1][1] = scale.y();
      ovrLayer.Textures[i].TextureMatrix.M[0][2] = translation.x();
      ovrLayer.Textures[i].TextureMatrix.M[1][2] = translation.y();

      ovrLayer.Textures[i].TextureRect.width = 1.0f;
      ovrLayer.Textures[i].TextureRect.height = 1.0f;
    }
  }
};

class OculusLayerCube;
typedef std::shared_ptr<OculusLayerCube> OculusLayerCubePtr;

class OculusLayerCube: public OculusLayerBase<VRLayerCubePtr, ovrLayerCube2> {
public:
  static OculusLayerCubePtr Create(const VRLayerCubePtr& aLayer, GLint aInternalFormat) {
    auto result = std::make_shared<OculusLayerCube>();
    result->layer = aLayer;
    result->glFormat = aInternalFormat;
    return result;
  }

  void Init(JNIEnv * aEnv, vrb::RenderContextPtr& aContext) override {
    if (swapChain) {
      return;
    }

    ovrLayer = vrapi_DefaultLayerCube2();
    ovrLayer.Offset.x = 0.0f;
    ovrLayer.Offset.y = 0.0f;
    ovrLayer.Offset.z = 0.0f;
    swapChain = vrapi_CreateTextureSwapChain3(VRAPI_TEXTURE_TYPE_CUBE, glFormat, layer->GetWidth(), layer->GetHeight(), 1, 1);
    layer->SetTextureHandle(vrapi_GetTextureSwapChainHandle(swapChain, 0));
    OculusLayerBase<VRLayerCubePtr, ovrLayerCube2>::Init(aEnv, aContext);
  }

  void Destroy() override {
    if (swapChain == nullptr) {
      return;
    }
    layer->SetTextureHandle(0);
    layer->SetLoaded(false);
    OculusLayerBase<VRLayerCubePtr, ovrLayerCube2>::Destroy();
  }

  bool IsLoaded() const {
    return layer->IsLoaded();
  }

  void Update(const ovrTracking2& aTracking, ovrTextureSwapChain* aClearSwapChain) override {
    OculusLayerBase<VRLayerCubePtr, ovrLayerCube2>::Update(aTracking, aClearSwapChain);
    const ovrMatrix4f centerEyeViewMatrix = vrapi_GetViewMatrixFromPose(&aTracking.HeadPose.Pose);
    const ovrMatrix4f cubeMatrix = ovrMatrix4f_TanAngleMatrixForCubeMap(&centerEyeViewMatrix);
    ovrLayer.HeadPose = aTracking.HeadPose;
    ovrLayer.TexCoordsFromTanAngles = cubeMatrix;

    for (int i = 0; i < VRAPI_FRAME_LAYER_EYE_MAX; ++i) {
      ovrLayer.Textures[i].ColorSwapChain = GetTargetSwapChain(aClearSwapChain);
      ovrLayer.Textures[i].SwapChainIndex = 0;
    }
  }

protected:
  GLint glFormat;
};


class OculusLayerEquirect;
typedef std::shared_ptr<OculusLayerEquirect> OculusLayerEquirectPtr;

class OculusLayerEquirect: public OculusLayerBase<VRLayerEquirectPtr, ovrLayerEquirect2> {
public:
  std::weak_ptr<OculusLayer> sourceLayer;

  static OculusLayerEquirectPtr Create(const VRLayerEquirectPtr& aLayer, const OculusLayerPtr& aSourceLayer) {
    auto result = std::make_shared<OculusLayerEquirect>();
    result->layer = aLayer;
    result->sourceLayer = aSourceLayer;
    return result;
  }

  void Init(JNIEnv * aEnv, vrb::RenderContextPtr& aContext) override {
    OculusLayerPtr source = sourceLayer.lock();
    if (!source) {
      return;
    }

    swapChain = source->GetSwapChain();
    ovrLayer = vrapi_DefaultLayerEquirect2();
    ovrLayer.HeadPose.Pose.Position.x = 0.0f;
    ovrLayer.HeadPose.Pose.Position.y = 0.0f;
    ovrLayer.HeadPose.Pose.Position.z = 0.0f;
    ovrLayer.HeadPose.Pose.Orientation.x  = 0.0f;
    ovrLayer.HeadPose.Pose.Orientation.y  = 0.0f;
    ovrLayer.HeadPose.Pose.Orientation.z  = 0.0f;
    ovrLayer.HeadPose.Pose.Orientation.w  = 1.0f;
    ovrLayer.TexCoordsFromTanAngles = ovrMatrix4f_CreateIdentity();
    OculusLayerBase<VRLayerEquirectPtr, ovrLayerEquirect2>::Init(aEnv, aContext);
  }

  void Destroy() override {
    swapChain = nullptr;
    OculusLayerBase<VRLayerEquirectPtr, ovrLayerEquirect2>::Destroy();
  }

  bool IsDrawRequested() const override {
    OculusLayerPtr source = sourceLayer.lock();
    return source && source->GetSwapChain() && source->IsComposited() && layer->IsDrawRequested();
  }

  void Update(const ovrTracking2& aTracking, ovrTextureSwapChain* aClearSwapChain) override {
    OculusLayerPtr source = sourceLayer.lock();
    if (source) {
      swapChain = source->GetSwapChain();
    }
    OculusLayerBase<VRLayerEquirectPtr, ovrLayerEquirect2>::Update(aTracking, aClearSwapChain);

    vrb::Quaternion q(layer->GetModelTransform(device::Eye::Left));
    ovrLayer.HeadPose.Pose.Orientation.x  = q.x();
    ovrLayer.HeadPose.Pose.Orientation.y  = q.y();
    ovrLayer.HeadPose.Pose.Orientation.z  = q.z();
    ovrLayer.HeadPose.Pose.Orientation.w  = q.w();

    bool clip = false;
    for (int i = 0; i < VRAPI_FRAME_LAYER_EYE_MAX; ++i) {
      const device::Eye eye = i == 0 ? device::Eye::Left : device::Eye::Right;
      ovrLayer.Textures[i].ColorSwapChain = GetTargetSwapChain(aClearSwapChain);
      ovrLayer.Textures[i].SwapChainIndex = 0;
      const vrb::Vector scale = layer->GetUVTransform(eye).GetScale();
      const vrb::Vector translation = layer->GetUVTransform(eye).GetTranslation();

      ovrLayer.Textures[i].TextureMatrix.M[0][0] = scale.x();
      ovrLayer.Textures[i].TextureMatrix.M[1][1] = scale.y();
      ovrLayer.Textures[i].TextureMatrix.M[0][2] = translation.x();
      ovrLayer.Textures[i].TextureMatrix.M[1][2] = translation.y();

      device::EyeRect textureRect = layer->GetTextureRect(eye);
      ovrLayer.Textures[i].TextureRect.x = textureRect.mX;
      ovrLayer.Textures[i].TextureRect.y = textureRect.mY;
      ovrLayer.Textures[i].TextureRect.width = textureRect.mWidth;
      ovrLayer.Textures[i].TextureRect.height = textureRect.mHeight;
      clip = clip || !textureRect.IsDefault();
    }
    SetClipEnabled(clip);
  }
};

const vrb::Vector kAverageHeight(0.0f, 1.7f, 0.0f);
// Height used to match Oculus default in WebVR
const vrb::Vector kAverageOculusHeight(0.0f, 1.65f, 0.0f);

struct DeviceDelegateOculusVR::State {
  struct ControllerState {
    const int32_t index;
    const ElbowModel::HandEnum hand;
    bool enabled = false;
    bool created = false;
    ovrDeviceID deviceId = ovrDeviceIdType_Invalid;
    ovrInputTrackedRemoteCapabilities capabilities = {};
    vrb::Matrix transform = vrb::Matrix::Identity();
    ovrInputStateTrackedRemote inputState = {};
    uint64_t inputFrameID = 0;
    float remainingVibrateTime = 0.0f;
    double lastHapticUpdateTimeStamp = 0.0f;

    bool Is6DOF() const {
      return (capabilities.ControllerCapabilities & ovrControllerCaps_HasPositionTracking) &&
             (capabilities.ControllerCapabilities & ovrControllerCaps_HasOrientationTracking);
    }
    ControllerState(const int32_t aIndex, const ElbowModel::HandEnum aHand) : index(aIndex), hand(aHand) {}
    ControllerState(const ControllerState& controllerStateList) = default;
    ControllerState() = delete;
    ControllerState& operator=(const ControllerState&) = delete;
  };

  vrb::RenderContextWeak context;
  android_app* app = nullptr;
  bool initialized = false;
  bool applicationEntitled = false;
  bool layersEnabled = true;
  ovrJava java = {};
  ovrMobile* ovr = nullptr;
  OculusEyeSwapChainPtr eyeSwapChains[VRAPI_EYE_COUNT];
  OculusLayerCubePtr cubeLayer;
  OculusLayerEquirectPtr equirectLayer;
  std::vector<OculusLayerPtr> uiLayers;
  ovrTextureSwapChain* clearColorSwapChain = nullptr;
  device::RenderMode renderMode = device::RenderMode::StandAlone;
  vrb::FBOPtr currentFBO;
  vrb::FBOPtr previousFBO;
  vrb::CameraEyePtr cameras[2];
  uint32_t frameIndex = 0;
  double predictedDisplayTime = 0;
  ovrTracking2 predictedTracking = {};
  ovrTracking2 discardPredictedTracking = {};
  uint32_t discardedFrameIndex = 0;
  int discardCount = 0;
  uint32_t renderWidth = 0;
  uint32_t renderHeight = 0;
  int32_t standaloneFoveatedLevel = 0;
  vrb::Color clearColor;
  float near = 0.1f;
  float far = 100.f;
  std::vector<ControllerState> controllerStateList;
  crow::ElbowModelPtr elbow;
  ControllerDelegatePtr controller;
  ImmersiveDisplayPtr immersiveDisplay;
  int reorientCount = -1;
  vrb::Matrix reorientMatrix = vrb::Matrix::Identity();
  device::CPULevel minCPULevel = device::CPULevel::Normal;
  device::DeviceType deviceType = device::UnknownType;

  void UpdatePerspective() {
    float fovX = vrapi_GetSystemPropertyFloat(&java, VRAPI_SYS_PROP_SUGGESTED_EYE_FOV_DEGREES_X);
    float fovY = vrapi_GetSystemPropertyFloat(&java, VRAPI_SYS_PROP_SUGGESTED_EYE_FOV_DEGREES_Y);

    ovrMatrix4f projection = ovrMatrix4f_CreateProjectionFov(fovX, fovY, 0.0, 0.0, near, far);
    auto matrix = vrb::Matrix::FromRowMajor(projection.M);
    for (int i = 0; i < VRAPI_EYE_COUNT; ++i) {
      cameras[i]->SetPerspective(matrix);
    }

    if (immersiveDisplay) {
      const float fovXHalf = fovX * 0.5f;
      const float fovYHalf = fovY * 0.5f;

      immersiveDisplay->SetFieldOfView(device::Eye::Left, fovXHalf, fovXHalf, fovYHalf, fovYHalf);
      immersiveDisplay->SetFieldOfView(device::Eye::Right, fovXHalf, fovXHalf, fovYHalf, fovYHalf);
    }
  }

  void Initialize() {
    elbow = ElbowModel::Create();
    layersEnabled = VRBrowser::AreLayersEnabled();
    vrb::RenderContextPtr localContext = context.lock();

    java.Vm = app->activity->vm;
    (*app->activity->vm).AttachCurrentThread(&java.Env, NULL);
    java.ActivityObject = java.Env->NewGlobalRef(app->activity->clazz);

    // Initialize the API.
    auto parms = vrapi_DefaultInitParms(&java);
    auto status = vrapi_Initialize(&parms);
    if (status != VRAPI_INITIALIZE_SUCCESS) {
      VRB_LOG("Failed to initialize VrApi!. Error: %d", status);
      exit(status);
      return;
    }
    initialized = true;
    SetRenderSize(device::RenderMode::StandAlone);

    for (int i = 0; i < VRAPI_EYE_COUNT; ++i) {
      cameras[i] = vrb::CameraEye::Create(localContext->GetRenderThreadCreationContext());
      eyeSwapChains[i] = OculusEyeSwapChain::create();
    }
    UpdatePerspective();

    reorientCount = vrapi_GetSystemStatusInt(&java, VRAPI_SYS_STATUS_RECENTER_COUNT);

    vrapi_SetPropertyInt(&java, VRAPI_BLOCK_REMOTE_BUTTONS_WHEN_NOT_EMULATING_HMT, 0);
    // This needs to be set to 0 so that the volume buttons work. I'm not sure why since the
    // docs in the header indicate that setting this to false (0) means you have to
    // handle the gamepad events yourself.
    vrapi_SetPropertyInt(&java, VRAPI_EAT_NATIVE_GAMEPAD_EVENTS, 0);
    // Reorient the headset after controller recenter.
    vrapi_SetPropertyInt(&java, VRAPI_REORIENT_HMD_ON_CONTROLLER_RECENTER, 1);

    const char * appId = OCULUS_6DOF_APP_ID;

    const int type = vrapi_GetSystemPropertyInt(&java, VRAPI_SYS_PROP_DEVICE_TYPE);
    if ((type >= VRAPI_DEVICE_TYPE_OCULUSGO_START ) && (type <= VRAPI_DEVICE_TYPE_OCULUSGO_END)) {
      VRB_DEBUG("Detected Oculus Go");
      deviceType = device::OculusGo;
      appId = OCULUS_3DOF_APP_ID;
    } else if ((type >= VRAPI_DEVICE_TYPE_OCULUSQUEST_START) && (type <= VRAPI_DEVICE_TYPE_OCULUSQUEST_END)) {
      VRB_DEBUG("Detected Oculus Quest");
      deviceType = device::OculusQuest;
    } else if ((type >= VRAPI_DEVICE_TYPE_GEARVR_START) && (type <= VRAPI_DEVICE_TYPE_GEARVR_END)) {
      VRB_DEBUG("Detected Gear VR");
      appId = OCULUS_3DOF_APP_ID;
    } else {
      VRB_DEBUG("Detected Unknown Oculus device");
    }

    if (!ovr_IsPlatformInitialized()) {
      ovrRequest result = ovr_PlatformInitializeAndroidAsynchronous(appId, java.ActivityObject,
                                                                    java.Env);

      if (invalidRequestID == result) {
        // Initialization failed which means either the oculus service isn’t on the machine or they’ve hacked their DLL.
        VRB_LOG("ovr_PlatformInitializeAndroidAsynchronous failed: %d", (int32_t) result);
#if STORE_BUILD == 1
        VRBrowser::HaltActivity(0);
#endif
      } else {
        VRB_LOG("ovr_PlatformInitializeAndroidAsynchronous succeeded");
        ovr_Entitlement_GetIsViewerEntitled();
      }
    } else if (!applicationEntitled) {
      ovr_Entitlement_GetIsViewerEntitled();
    }
  }

  void UpdateTrackingMode() {
    if (ovr) {
      vrapi_SetTrackingSpace(ovr, VRAPI_TRACKING_SPACE_LOCAL);
    }
  }

  void UpdateFoveatedLevel() {
    if (!ovr) {
      return;
    }
    vrapi_SetPropertyInt(&java, VRAPI_FOVEATION_LEVEL, standaloneFoveatedLevel);
  }

  void UpdateClockLevels() {
    if (!ovr) {
      return;
    }

    if (renderMode == device::RenderMode::StandAlone && minCPULevel == device::CPULevel::Normal) {
      vrapi_SetClockLevels(ovr, 2, 2);
    } else {
      vrapi_SetClockLevels(ovr, 4, 4);
    }
  }

  void UpdateDisplayRefreshRate() {
    if (!ovr || !IsOculusGo()) {
      return;
    }
    if (renderMode == device::RenderMode::StandAlone) {
      vrapi_SetDisplayRefreshRate(ovr, 72.0f);
    } else {
      vrapi_SetDisplayRefreshRate(ovr, 60.0f);
    }
  }

  void AddUILayer(const OculusLayerPtr& aLayer, VRLayerSurface::SurfaceType aSurfaceType) {
    if (ovr) {
      vrb::RenderContextPtr ctx = context.lock();
      aLayer->Init(java.Env, ctx);
    }
    uiLayers.push_back(aLayer);
    if (aSurfaceType == VRLayerSurface::SurfaceType::FBO) {
      aLayer->SetBindDelegate([=](const vrb::FBOPtr& aFBO, GLenum aTarget, bool bound){
        if (aFBO) {
          HandleQuadLayerBind(aFBO, aTarget, bound);
        }
      });
      if (currentFBO) {
        currentFBO->Bind();
      }
    }
  }

  void GetImmersiveRenderSize(uint32_t& aWidth, uint32_t& aHeight) {
    aWidth = (uint32_t)(vrapi_GetSystemPropertyInt(&java, VRAPI_SYS_PROP_SUGGESTED_EYE_TEXTURE_WIDTH));
    aHeight = (uint32_t)(vrapi_GetSystemPropertyInt(&java, VRAPI_SYS_PROP_SUGGESTED_EYE_TEXTURE_HEIGHT));
  }

  void GetStandaloneRenderSize(uint32_t& aWidth, uint32_t& aHeight) {
    const float scale = layersEnabled ? 1.0f : 1.5f;
    aWidth = (uint32_t)(scale * vrapi_GetSystemPropertyInt(&java, VRAPI_SYS_PROP_SUGGESTED_EYE_TEXTURE_WIDTH));
    aHeight = (uint32_t)(scale * vrapi_GetSystemPropertyInt(&java, VRAPI_SYS_PROP_SUGGESTED_EYE_TEXTURE_HEIGHT));
  }

  bool IsOculusQuest() const {
    return deviceType == device::OculusQuest;
  }

  bool IsOculusGo() const {
    return deviceType == device::OculusGo;
  }

  bool Is6DOF() const {
    // ovrInputHeadsetCapabilities is unavailable in Oculus mobile SDK,
    // the current workaround is checking if it is Quest.
    return IsOculusQuest();
  }

  void SetRenderSize(device::RenderMode aRenderMode) {
    if (renderMode == device::RenderMode::StandAlone) {
      GetStandaloneRenderSize(renderWidth, renderHeight);
    } else {
      GetImmersiveRenderSize(renderWidth, renderHeight);
    }
  }

  void Shutdown() {
    // Shutdown Oculus mobile SDK
    if (initialized) {
      vrapi_Shutdown();
      initialized = false;
      controllerStateList.clear();
    }

    // Release activity reference
    if (java.ActivityObject) {
      java.Env->DeleteGlobalRef(java.ActivityObject);
      java = {};
    }
  }

  int32_t FindControllerIndex(const ElbowModel::HandEnum aHand) {
    int32_t found = -1;
    for (ControllerState& controller: controllerStateList) {
      if (controller.hand == aHand) {
        found = controller.index;
      }
    }

    if (found < 0) {
      found = (int32_t)controllerStateList.size();
      controllerStateList.emplace_back(ControllerState(found, aHand));
    }
    return found;
  }

  void UpdateDeviceId() {
    if (!controller || !ovr) {
      return;
    }

    for (ControllerState& controllerState: controllerStateList) {
      controllerState.enabled = false;
    }

    uint32_t count = 0;
    ovrInputCapabilityHeader capsHeader = {};
    while (vrapi_EnumerateInputDevices(ovr, count++, &capsHeader) >= 0) {
      // We are only interested in the remote controller input device
      if (capsHeader.Type == ovrControllerType_TrackedRemote) {
        ovrInputTrackedRemoteCapabilities caps = {};
        caps.Header = capsHeader;
        ovrResult result = vrapi_GetInputDeviceCapabilities(ovr, &caps.Header);
        if (result != ovrSuccess) {
          VRB_LOG("vrapi_GetInputDeviceCapabilities failed with error: %d", result);
          continue;
        }
        const int32_t index = FindControllerIndex(caps.ControllerCapabilities & ovrControllerCaps_LeftHand ? ElbowModel::HandEnum::Left : ElbowModel::HandEnum::Right);
        if ((index < 0) || index >= (controllerStateList.size())) {
          continue;
        }
        ControllerState& controllerState = controllerStateList[index];
        if ((controllerState.deviceId != ovrDeviceIdType_Invalid) &&
            (controllerState.deviceId != capsHeader.DeviceID)) {
          VRB_DEBUG("%s handed controller DeviceID has changed from %u to %u",
                    (controllerState.hand == ElbowModel::HandEnum::Left ? "Left" : "Right"),
                    controllerState.deviceId, capsHeader.DeviceID);
        }
        controllerState.deviceId = capsHeader.DeviceID;
        controllerState.capabilities = caps;
        controllerState.enabled = true;

        if (!controllerState.created) {
          if (controllerState.capabilities.ControllerCapabilities &
              ovrControllerCaps_ModelOculusTouch) {
            std::string controllerName;
            vrb::Matrix beamTransform(vrb::Matrix::Identity());
            if (controllerState.hand == ElbowModel::HandEnum::Left) {
              beamTransform.TranslateInPlace(vrb::Vector(-0.011f, -0.007f, 0.0f));
              controllerName = "Oculus Touch (Left)";
            } else {
              beamTransform.TranslateInPlace(vrb::Vector(0.011f, -0.007f, 0.0f));
              controllerName = "Oculus Touch (Right)";
            }
            controller->CreateController(controllerState.index, int32_t(controllerState.hand),
                                         controllerName, beamTransform);
            controller->SetButtonCount(controllerState.index, 6);
            controller->SetHapticCount(controllerState.index, 1);
          } else {
            // Oculus Go only has one kind of controller model.
            controller->CreateController(controllerState.index, 0, "Oculus Go Controller");
            controller->SetButtonCount(controllerState.index, 2);
            // Oculus Go has no haptic feedback.
            controller->SetHapticCount(controllerState.index, 0);
          }
          controllerState.created = true;
        }
      }
    }
    for (ControllerState& controllerState: controllerStateList) {
      controller->SetLeftHanded(controllerState.index, controllerState.hand == ElbowModel::HandEnum::Left);
      controller->SetEnabled(controllerState.index, controllerState.enabled);
      controller->SetVisible(controllerState.index, controllerState.enabled);
    }
  }

  void UpdateControllers(const vrb::Matrix & head) {
    UpdateDeviceId();
    if (!controller) {
      return;
    }

    for (ControllerState& controllerState: controllerStateList) {
      if (controllerState.deviceId == ovrDeviceIdType_Invalid) {
        return;
      }
      ovrTracking tracking = {};
      if (vrapi_GetInputTrackingState(ovr, controllerState.deviceId, 0, &tracking) != ovrSuccess) {
        VRB_LOG("Failed to read controller tracking controllerStateList");
        return;
      }

      device::CapabilityFlags flags = 0;
      if (controllerState.capabilities.ControllerCapabilities & ovrControllerCaps_HasOrientationTracking) {
        auto &orientation = tracking.HeadPose.Pose.Orientation;
        vrb::Quaternion quat(orientation.x, orientation.y, orientation.z, orientation.w);
        controllerState.transform = vrb::Matrix::Rotation(quat);
        flags |= device::Orientation;
      }

      if (controllerState.capabilities.ControllerCapabilities & ovrControllerCaps_HasPositionTracking) {
        auto & position = tracking.HeadPose.Pose.Position;
        vrb::Vector headPos(position.x, position.y, position.z);
        if (renderMode == device::RenderMode::StandAlone) {
          headPos += kAverageHeight;
        }
        controllerState.transform.TranslateInPlace(headPos);
        flags |= device::Position;
      } else {
        controllerState.transform = elbow->GetTransform(controllerState.hand, head, controllerState.transform);
      }

      controller->SetCapabilityFlags(controllerState.index, flags);
      controller->SetTransform(controllerState.index, controllerState.transform);

      controllerState.inputState.Header.ControllerType = ovrControllerType_TrackedRemote;
      vrapi_GetCurrentInputState(ovr, controllerState.deviceId, &controllerState.inputState.Header);

      reorientCount = controllerState.inputState.RecenterCount;
      const int32_t kNumAxes = 2;
      bool triggerPressed = false, triggerTouched = false;
      bool trackpadPressed = false, trackpadTouched = false;
      float axes[kNumAxes];
      float trackpadX = 0.0f, trackpadY = 0.0f;
      if (controllerState.Is6DOF()) {
        triggerPressed = (controllerState.inputState.Buttons & ovrButton_Trigger) != 0;
        triggerTouched = (controllerState.inputState.Touches & ovrTouch_IndexTrigger) != 0;
        trackpadPressed = (controllerState.inputState.Buttons & ovrButton_Joystick) != 0;
        trackpadTouched = (controllerState.inputState.Touches & ovrTouch_Joystick) != 0;
        trackpadX = controllerState.inputState.Joystick.x;
        trackpadY = controllerState.inputState.Joystick.y;
        axes[0] = trackpadX;
        axes[1] = -trackpadY; // We did y axis intentionally inverted in FF desktop as well.
        controller->SetScrolledDelta(controllerState.index, -trackpadX, trackpadY);

        const bool gripPressed = (controllerState.inputState.Buttons & ovrButton_GripTrigger) != 0;
        controller->SetButtonState(controllerState.index, ControllerDelegate::BUTTON_OTHERS, 2, gripPressed, gripPressed,
                                   controllerState.inputState.GripTrigger);
        if (controllerState.hand == ElbowModel::HandEnum::Left) {
          const bool xPressed = (controllerState.inputState.Buttons & ovrButton_X) != 0;
          const bool xTouched = (controllerState.inputState.Touches & ovrTouch_X) != 0;
          const bool yPressed = (controllerState.inputState.Buttons & ovrButton_Y) != 0;
          const bool yTouched = (controllerState.inputState.Touches & ovrTouch_Y) != 0;
          const bool menuPressed = (controllerState.inputState.Buttons & ovrButton_Enter) != 0;

          controller->SetButtonState(controllerState.index, ControllerDelegate::BUTTON_X, 3, xPressed, xTouched);
          controller->SetButtonState(controllerState.index, ControllerDelegate::BUTTON_Y, 4, yPressed, yTouched);

          if (renderMode != device::RenderMode::Immersive) {
            controller->SetButtonState(controllerState.index, ControllerDelegate::BUTTON_APP, -1, yPressed, yTouched);
          } else {
            controller->SetButtonState(controllerState.index, ControllerDelegate::BUTTON_APP, -1, menuPressed, menuPressed);
          }
        } else if (controllerState.hand == ElbowModel::HandEnum::Right) {
          const bool aPressed = (controllerState.inputState.Buttons & ovrButton_A) != 0;
          const bool aTouched = (controllerState.inputState.Touches & ovrTouch_A) != 0;
          const bool bPressed = (controllerState.inputState.Buttons & ovrButton_B) != 0;
          const bool bTouched = (controllerState.inputState.Touches & ovrTouch_B) != 0;

          controller->SetButtonState(controllerState.index, ControllerDelegate::BUTTON_A, 3, aPressed, aTouched);
          controller->SetButtonState(controllerState.index, ControllerDelegate::BUTTON_Y, 4, bPressed, bTouched);

          if (renderMode != device::RenderMode::Immersive) {
            controller->SetButtonState(controllerState.index, ControllerDelegate::BUTTON_APP, -1, bPressed, bTouched);
          }
        } else {
          VRB_WARN("Undefined hand type in DeviceDelegateOculusVR.");
        }

        // This is always false in Oculus Browser.
        const bool thumbRest = false;
        controller->SetButtonState(controllerState.index, ControllerDelegate::BUTTON_OTHERS, 5, thumbRest, thumbRest);
      } else {
        triggerPressed = (controllerState.inputState.Buttons & ovrButton_A) != 0;
        triggerTouched = triggerPressed;
        trackpadPressed = (controllerState.inputState.Buttons & ovrButton_Enter) != 0;
        trackpadTouched = (bool)controllerState.inputState.TrackpadStatus;

        // For Oculus Go, by setting vrapi_SetPropertyInt(&java, VRAPI_EAT_NATIVE_GAMEPAD_EVENTS, 0);
        // The app will receive onBackPressed when the back button is pressed on the controller.
        // So there is no need to check for it here. Leaving code commented out for reference
        // in the case that the back button stops working again due to Oculus Mobile API change.
        // const bool backPressed = (inputState.Buttons & ovrButton_Back) != 0;
        // controller->SetButtonState(0, ControllerDelegate::BUTTON_APP, -1, backPressed, backPressed);
        trackpadX = controllerState.inputState.TrackpadPosition.x / (float)controllerState.capabilities.TrackpadMaxX;
        trackpadY = controllerState.inputState.TrackpadPosition.y / (float)controllerState.capabilities.TrackpadMaxY;

        if (trackpadTouched && !trackpadPressed) {
          controller->SetTouchPosition(controllerState.index, trackpadX, trackpadY);
        } else {
          controller->SetTouchPosition(controllerState.index, trackpadX, trackpadY);
          controller->EndTouch(controllerState.index);
        }
        axes[0] = trackpadTouched ? trackpadX * 2.0f - 1.0f : 0.0f;
        axes[1] = trackpadTouched ? trackpadY * 2.0f - 1.0f : 0.0f;
      }
      controller->SetButtonState(controllerState.index, ControllerDelegate::BUTTON_TRIGGER, 1, triggerPressed, triggerTouched,
                                 controllerState.inputState.IndexTrigger);
      controller->SetButtonState(controllerState.index, ControllerDelegate::BUTTON_TOUCHPAD, 0, trackpadPressed, trackpadTouched);

      controller->SetAxes(controllerState.index, axes, kNumAxes);
      if (controller->GetHapticCount(controllerState.index)) {
        UpdateHaptics(controllerState);
      }
    }
  }

  void UpdateHaptics(ControllerState& controllerState) {
    vrb::RenderContextPtr renderContext = context.lock();
    if (!renderContext) {
      return;
    }
    if (!controller || !ovr) {
      return;
    }

    uint64_t inputFrameID = 0;
    float pulseDuration = 0.0f, pulseIntensity = 0.0f;
    controller->GetHapticFeedback(controllerState.index, inputFrameID, pulseDuration, pulseIntensity);
    if (inputFrameID > 0 && pulseIntensity > 0.0f && pulseDuration > 0) {
      if (controllerState.inputFrameID != inputFrameID) {
        // When there is a new input frame id from haptic vibration,
        // that means we start a new session for a vibration.
        controllerState.inputFrameID = inputFrameID;
        controllerState.remainingVibrateTime = pulseDuration;
        controllerState.lastHapticUpdateTimeStamp = renderContext->GetTimestamp();
      } else {
        // We are still running the previous vibration.
        // So, it needs to reduce the delta time from the last vibration.
        const double timeStamp = renderContext->GetTimestamp();
        controllerState.remainingVibrateTime -= (timeStamp - controllerState.lastHapticUpdateTimeStamp);
        controllerState.lastHapticUpdateTimeStamp = timeStamp;
      }

      if (controllerState.remainingVibrateTime > 0.0f && renderMode == device::RenderMode::Immersive) {
        if (vrapi_SetHapticVibrationSimple(ovr, controllerState.deviceId, pulseIntensity > 1.0f ? 1.0f : pulseIntensity)
            == ovrError_InvalidOperation) {
          VRB_ERROR("vrapi_SetHapticVibrationBuffer failed.");
        }
      } else {
        // The remaining time is zero or exiting the immersive mode, stop the vibration.
        if (vrapi_SetHapticVibrationSimple(ovr, controllerState.deviceId, 0.0f) == ovrError_InvalidOperation) {
          VRB_ERROR("vrapi_SetHapticVibrationBuffer failed.");
        }
        controllerState.remainingVibrateTime = 0.0f;
      }
    } else if (controllerState.remainingVibrateTime > 0.0f) {
      // While the haptic feedback is terminated from the client side,
      // but it still have remaining time, we need to ask for stopping vibration.
      if (vrapi_SetHapticVibrationSimple(ovr, controllerState.deviceId, 0.0f) == ovrError_InvalidOperation) {
        VRB_ERROR("vrapi_SetHapticVibrationBuffer failed.");
      }
      controllerState.remainingVibrateTime = 0.0f;
    }
  }

  void HandleQuadLayerBind(const vrb::FBOPtr& aFBO, GLenum aTarget, bool bound) {
    if (!bound) {
      if (currentFBO && currentFBO == aFBO) {
        currentFBO->Unbind();
        currentFBO = nullptr;
      }
      if (previousFBO) {
        previousFBO->Bind();
        currentFBO = previousFBO;
        previousFBO = nullptr;
      }
      return;
    }

    if (currentFBO == aFBO) {
      // Layer already bound
      return;
    }

    if (currentFBO) {
      currentFBO->Unbind();
    }
    previousFBO = currentFBO;
    aFBO->Bind(aTarget);
    currentFBO = aFBO;
  }

  ovrTextureSwapChain* CreateClearColorSwapChain(const float aWidth, const float aHeight) {
    ovrTextureSwapChain* result = vrapi_CreateTextureSwapChain(VRAPI_TEXTURE_TYPE_2D, VRAPI_TEXTURE_FORMAT_8888, aWidth, aHeight, 1, false);
    vrb::RenderContextPtr ctx = context.lock();
    vrb::FBOPtr fbo = vrb::FBO::Create(ctx);
    GLuint texture = vrapi_GetTextureSwapChainHandle(result, 0);
    VRB_GL_CHECK(glBindTexture(GL_TEXTURE_2D, texture));
    VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER_EXT));
    VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER_EXT));
    float border[] = { 0.0f, 0.0f, 0.0f, 0.0f };
    glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR_EXT, border);
    VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR));
    VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR));
    vrb::FBO::Attributes attributes;
    attributes.depth = false;
    attributes.samples = 0;
    VRB_GL_CHECK(fbo->SetTextureHandle(texture, aWidth, aHeight, attributes));
    if (fbo->IsValid()) {
      fbo->Bind();
      VRB_GL_CHECK(glClearColor(1.0f, 1.0f, 1.0f, 1.0f));
      VRB_GL_CHECK(glClear(GL_COLOR_BUFFER_BIT));
      fbo->Unbind();
    } else {
      VRB_WARN("FAILED to make valid FBO for ClearColorSwapChain");
    }
    return result;
  }
};

DeviceDelegateOculusVRPtr
DeviceDelegateOculusVR::Create(vrb::RenderContextPtr& aContext, android_app *aApp) {
  DeviceDelegateOculusVRPtr result = std::make_shared<vrb::ConcreteClass<DeviceDelegateOculusVR, DeviceDelegateOculusVR::State> >();
  result->m.context = aContext;
  result->m.app = aApp;
  result->m.Initialize();
  return result;
}

device::DeviceType
DeviceDelegateOculusVR::GetDeviceType() {
  return m.deviceType;
}

void
DeviceDelegateOculusVR::SetRenderMode(const device::RenderMode aMode) {
  if (aMode == m.renderMode) {
    return;
  }
  m.renderMode = aMode;
  m.SetRenderSize(aMode);
  vrb::RenderContextPtr render = m.context.lock();
  for (int i = 0; i < VRAPI_EYE_COUNT; ++i) {
    m.eyeSwapChains[i]->Init(render, m.renderMode, m.renderWidth, m.renderHeight);
  }

  m.UpdateTrackingMode();
  m.UpdateFoveatedLevel();
  m.UpdateDisplayRefreshRate();
  m.UpdateClockLevels();

  // Reset reorient when exiting or entering immersive
  m.reorientMatrix = vrb::Matrix::Identity();
}

device::RenderMode
DeviceDelegateOculusVR::GetRenderMode() {
  return m.renderMode;
}

void
DeviceDelegateOculusVR::RegisterImmersiveDisplay(ImmersiveDisplayPtr aDisplay) {
  m.immersiveDisplay = std::move(aDisplay);

  if (!m.immersiveDisplay) {
    return;
  }

  if (m.IsOculusQuest()) {
    m.immersiveDisplay->SetDeviceName("Oculus Quest");
  } else {
    m.immersiveDisplay->SetDeviceName("Oculus Go");
  }
  uint32_t width, height;
  m.GetImmersiveRenderSize(width, height);
  m.immersiveDisplay->SetEyeResolution(width, height);
  m.immersiveDisplay->SetSittingToStandingTransform(vrb::Matrix::Translation(kAverageOculusHeight));
  m.immersiveDisplay->CompleteEnumeration();

  m.UpdatePerspective();
}

void
DeviceDelegateOculusVR::SetImmersiveSize(const uint32_t aEyeWidth, const uint32_t aEyeHeight) {
  uint32_t recommendedWidth, recommendedHeight;
  m.GetImmersiveRenderSize(recommendedWidth, recommendedHeight);

  uint32_t targetWidth = m.renderWidth;
  uint32_t targetHeight = m.renderHeight;

  DeviceUtils::GetTargetImmersiveSize(aEyeWidth, aEyeHeight, recommendedWidth, recommendedHeight, targetWidth, targetHeight);
  if (targetWidth != m.renderWidth || targetHeight != m.renderHeight) {
    m.renderWidth = targetWidth;
    m.renderHeight = targetHeight;
    vrb::RenderContextPtr render = m.context.lock();
    for (int i = 0; i < VRAPI_EYE_COUNT; ++i) {
      m.eyeSwapChains[i]->Init(render, m.renderMode, m.renderWidth, m.renderHeight);
    }
    VRB_LOG("Resize immersive mode swapChain: %dx%d", targetWidth, targetHeight);
  }
}

vrb::CameraPtr
DeviceDelegateOculusVR::GetCamera(const device::Eye aWhich) {
  const int32_t index = device::EyeIndex(aWhich);
  if (index < 0) { return nullptr; }
  return m.cameras[index];
}

const vrb::Matrix&
DeviceDelegateOculusVR::GetHeadTransform() const {
  return m.cameras[0]->GetHeadTransform();
}

const vrb::Matrix&
DeviceDelegateOculusVR::GetReorientTransform() const {
  return m.reorientMatrix;
}

void
DeviceDelegateOculusVR::SetReorientTransform(const vrb::Matrix& aMatrix) {
  m.reorientMatrix = aMatrix;
}

void
DeviceDelegateOculusVR::SetClearColor(const vrb::Color& aColor) {
  m.clearColor = aColor;
}

void
DeviceDelegateOculusVR::SetClipPlanes(const float aNear, const float aFar) {
  m.near = aNear;
  m.far = aFar;
  m.UpdatePerspective();
}

void
DeviceDelegateOculusVR::SetControllerDelegate(ControllerDelegatePtr& aController) {
  m.controller = aController;
}

void
DeviceDelegateOculusVR::ReleaseControllerDelegate() {
  m.controller = nullptr;
}

void
DeviceDelegateOculusVR::SetFoveatedLevel(const int32_t aAppLevel) {
  m.standaloneFoveatedLevel = aAppLevel;
  m.UpdateFoveatedLevel();
}

int32_t
DeviceDelegateOculusVR::GetControllerModelCount() const {
  if (m.IsOculusQuest()) {
    return 2;
  } else {
    return 1;
  }
}

const std::string
DeviceDelegateOculusVR::GetControllerModelName(const int32_t aModelIndex) const {
  static std::string name = "";

  if (m.IsOculusQuest()) {
    switch (aModelIndex) {
      case 0:
        name = "vr_controller_oculusquest_left.obj";
        break;
      case 1:
        name = "vr_controller_oculusquest_right.obj";
        break;
      default:
        VRB_WARN("GetControllerModelName() failed.");
        name = "";
        break;
    }
  } else {
    name = "vr_controller_oculusgo.obj";
  }

  return name;
}


void
DeviceDelegateOculusVR::SetCPULevel(const device::CPULevel aLevel) {
  m.minCPULevel = aLevel;
  m.UpdateClockLevels();
};

void
DeviceDelegateOculusVR::ProcessEvents() {
  if (m.applicationEntitled) {
    return;
  }

  ovrMessageHandle message;
  while ((message = ovr_PopMessage()) != nullptr) {
    switch (ovr_Message_GetType(message)) {
      case ovrMessage_PlatformInitializeAndroidAsynchronous: {
        ovrPlatformInitializeHandle handle = ovr_Message_GetPlatformInitialize(message);
        ovrPlatformInitializeResult result = ovr_PlatformInitialize_GetResult(handle);
        if (result == ovrPlatformInitialize_Success) {
          VRB_DEBUG("OVR Platform Initialized.");
        } else {
          VRB_ERROR("OVR Platform Initialize failed: %s", ovrPlatformInitializeResult_ToString(result));
#if STORE_BUILD == 1
          VRBrowser::HaltActivity(0);
#endif
        }
      }
        break;
      case ovrMessage_Entitlement_GetIsViewerEntitled:
        if (ovr_Message_IsError(message)) {
          VRB_LOG("User is not entitled");
#if STORE_BUILD == 1
          VRBrowser::HaltActivity(0);
#else
          // No need to process events anymore.
          m.applicationEntitled = true;
#endif
        }
        else {
          VRB_LOG("User is entitled");
          m.applicationEntitled = true;
        }
        break;
      default:
        break;
    }
  }
}

void
DeviceDelegateOculusVR::StartFrame() {
  if (!m.ovr) {
    VRB_LOG("StartFrame called while not in VR mode");
    return;
  }

  m.frameIndex++;
  m.predictedDisplayTime = vrapi_GetPredictedDisplayTime(m.ovr, m.frameIndex);
  m.predictedTracking = vrapi_GetPredictedTracking2(m.ovr, m.predictedDisplayTime);

  float ipd = vrapi_GetInterpupillaryDistance(&m.predictedTracking);
  m.cameras[VRAPI_EYE_LEFT]->SetEyeTransform(vrb::Matrix::Translation(vrb::Vector(-ipd * 0.5f, 0.f, 0.f)));
  m.cameras[VRAPI_EYE_RIGHT]->SetEyeTransform(vrb::Matrix::Translation(vrb::Vector(ipd * 0.5f, 0.f, 0.f)));

  if (!(m.predictedTracking.Status & VRAPI_TRACKING_STATUS_HMD_CONNECTED)) {
    VRB_LOG("HMD not connected");
    return;
  }

  ovrMatrix4f matrix = vrapi_GetTransformFromPose(&m.predictedTracking.HeadPose.Pose);
  vrb::Matrix head = vrb::Matrix::FromRowMajor(matrix.M[0]);



  if (m.renderMode == device::RenderMode::StandAlone) {
    head.TranslateInPlace(kAverageHeight);
  }

  m.cameras[VRAPI_EYE_LEFT]->SetHeadTransform(head);
  m.cameras[VRAPI_EYE_RIGHT]->SetHeadTransform(head);

  if (m.immersiveDisplay) {
    m.immersiveDisplay->SetEyeOffset(device::Eye::Left, -ipd * 0.5f, 0.f, 0.f);
    m.immersiveDisplay->SetEyeOffset(device::Eye::Right, ipd * 0.5f, 0.f, 0.f);
    device::CapabilityFlags caps = device::Orientation | device::Present | device::StageParameters |
                                   device::InlineSession | device::ImmersiveVRSession;
    if (m.predictedTracking.Status & VRAPI_TRACKING_STATUS_POSITION_TRACKED) {
      caps |= device::Position;
    } else {
      caps |= device::PositionEmulated;
    }
    m.immersiveDisplay->SetCapabilityFlags(caps);
  }

  int lastReorientCount = m.reorientCount;
  m.UpdateControllers(head);
  bool reoriented = lastReorientCount != m.reorientCount && lastReorientCount > 0 && m.reorientCount > 0;
  if (reoriented && m.renderMode == device::RenderMode::StandAlone) {
    m.reorientMatrix = DeviceUtils::CalculateReorientationMatrix(head, kAverageHeight);
  }

  VRB_GL_CHECK(glClearColor(m.clearColor.Red(), m.clearColor.Green(), m.clearColor.Blue(), m.clearColor.Alpha()));
}

void
DeviceDelegateOculusVR::BindEye(const device::Eye aWhich) {
  if (!m.ovr) {
    VRB_LOG("BindEye called while not in VR mode");
    return;
  }

  int32_t index = device::EyeIndex(aWhich);
  if (index < 0) {
    VRB_LOG("No eye found");
    return;
  }

  if (m.currentFBO) {
    m.currentFBO->Unbind();
  }

  const auto &swapChain = m.eyeSwapChains[index];
  int swapChainIndex = m.frameIndex % swapChain->swapChainLength;
  m.currentFBO = swapChain->fbos[swapChainIndex];

  if (m.currentFBO) {
    m.currentFBO->Bind();
    VRB_GL_CHECK(glViewport(0, 0, m.renderWidth, m.renderHeight));
    VRB_GL_CHECK(glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT));
  } else {
    VRB_LOG("No Swap chain FBO found");
  }

  for (const OculusLayerPtr& layer: m.uiLayers) {
    layer->SetCurrentEye(aWhich);
  }
}

void
DeviceDelegateOculusVR::EndFrame(const bool aDiscard) {
  if (!m.ovr) {
    VRB_LOG("EndFrame called while not in VR mode");
    return;
  }
  if (m.currentFBO) {
    m.currentFBO->Unbind();
    m.currentFBO.reset();
  }

  if (aDiscard) {
    // Reuse the last frame when a frame is discarded.
    // The last frame is timewarped by the VR compositor.
    if (m.discardCount == 0) {
      m.discardPredictedTracking = m.predictedTracking;
      m.discardedFrameIndex = m.frameIndex;
    }
    m.discardCount++;
    m.frameIndex = m.discardedFrameIndex;
    m.predictedTracking = m.discardPredictedTracking;
  } else {
    m.discardCount = 0;
  }

  uint32_t layerCount = 0;
  const ovrLayerHeader2* layers[ovrMaxLayerCount] = {};

  if (m.cubeLayer && m.cubeLayer->IsLoaded() && m.cubeLayer->IsDrawRequested()) {
    m.cubeLayer->Update(m.predictedTracking, m.clearColorSwapChain);
    layers[layerCount++] = m.cubeLayer->Header();
    m.cubeLayer->ClearRequestDraw();
  }

  if (m.equirectLayer && m.equirectLayer->IsDrawRequested()) {
    m.equirectLayer->Update(m.predictedTracking, m.clearColorSwapChain);
    layers[layerCount++] = m.equirectLayer->Header();
    m.equirectLayer->ClearRequestDraw();
  }

  // Sort quad layers by draw priority
  std::sort(m.uiLayers.begin(), m.uiLayers.end(), [](const OculusLayerPtr & a, OculusLayerPtr & b) -> bool {
    return a->GetLayer()->ShouldDrawBefore(*b->GetLayer());
  });

  // Draw back layers
  for (const OculusLayerPtr& layer: m.uiLayers) {
    if (!layer->GetDrawInFront() && layer->IsDrawRequested() && (layerCount < ovrMaxLayerCount - 1)) {
      layer->Update(m.predictedTracking, m.clearColorSwapChain);
      layers[layerCount++] = layer->Header();
      layer->ClearRequestDraw();
    }
  }

  // Add main eye buffer layer
  const float fovX = vrapi_GetSystemPropertyFloat(&m.java, VRAPI_SYS_PROP_SUGGESTED_EYE_FOV_DEGREES_X);
  const float fovY = vrapi_GetSystemPropertyFloat(&m.java, VRAPI_SYS_PROP_SUGGESTED_EYE_FOV_DEGREES_Y);
  const ovrMatrix4f projectionMatrix = ovrMatrix4f_CreateProjectionFov(fovX, fovY, 0.0f, 0.0f, VRAPI_ZNEAR, 0.0f);

  ovrLayerProjection2 projection = vrapi_DefaultLayerProjection2();
  projection.HeadPose = m.predictedTracking.HeadPose;
  projection.Header.SrcBlend = VRAPI_FRAME_LAYER_BLEND_ONE;
  projection.Header.DstBlend = VRAPI_FRAME_LAYER_BLEND_ONE_MINUS_SRC_ALPHA;
  for (int i = 0; i < VRAPI_FRAME_LAYER_EYE_MAX; ++i) {
    const auto &eyeSwapChain = m.eyeSwapChains[i];
    const int swapChainIndex = m.frameIndex % eyeSwapChain->swapChainLength;
    // Set up OVR layer textures
    projection.Textures[i].ColorSwapChain = eyeSwapChain->ovrSwapChain;
    projection.Textures[i].SwapChainIndex = swapChainIndex;
    projection.Textures[i].TexCoordsFromTanAngles = ovrMatrix4f_TanAngleMatrixFromProjection(&projectionMatrix);
  }
  layers[layerCount++] = &projection.Header;

  // Draw front layers
  for (const OculusLayerPtr& layer: m.uiLayers) {
    if (layer->GetDrawInFront() && layer->IsDrawRequested() && layerCount < ovrMaxLayerCount) {
      layer->Update(m.predictedTracking, m.clearColorSwapChain);
      layers[layerCount++] = layer->Header();
      layer->ClearRequestDraw();
    }
  }


  // Submit all layers to TimeWarp
  ovrSubmitFrameDescription2 frameDesc = {};
  frameDesc.Flags = 0;
  if (m.renderMode == device::RenderMode::Immersive) {
    frameDesc.Flags |= VRAPI_FRAME_FLAG_INHIBIT_VOLUME_LAYER;
  }
  frameDesc.SwapInterval = 1;
  frameDesc.FrameIndex = m.frameIndex;
  frameDesc.DisplayTime = m.predictedDisplayTime;

  frameDesc.LayerCount = layerCount;
  frameDesc.Layers = layers;

  vrapi_SubmitFrame2(m.ovr, &frameDesc);
}

VRLayerQuadPtr
DeviceDelegateOculusVR::CreateLayerQuad(int32_t aWidth, int32_t aHeight,
                                        VRLayerSurface::SurfaceType aSurfaceType) {
  if (!m.layersEnabled) {
    return nullptr;
  }
  VRLayerQuadPtr layer = VRLayerQuad::Create(aWidth, aHeight, aSurfaceType);
  OculusLayerQuadPtr oculusLayer = OculusLayerQuad::Create(layer);
  m.AddUILayer(oculusLayer, aSurfaceType);
  return layer;
}

VRLayerQuadPtr
DeviceDelegateOculusVR::CreateLayerQuad(const VRLayerSurfacePtr& aMoveLayer) {
  if (!m.layersEnabled) {
    return nullptr;
  }

  VRLayerQuadPtr layer = VRLayerQuad::Create(aMoveLayer->GetWidth(), aMoveLayer->GetHeight(), aMoveLayer->GetSurfaceType());
  OculusLayerQuadPtr oculusLayer;

  for (int i = 0; i < m.uiLayers.size(); ++i) {
    if (m.uiLayers[i]->GetLayer() == aMoveLayer) {
      oculusLayer = OculusLayerQuad::Create(layer, m.uiLayers[i]);
      m.uiLayers.erase(m.uiLayers.begin() + i);
      break;
    }
  }
  if (oculusLayer) {
    m.AddUILayer(oculusLayer, aMoveLayer->GetSurfaceType());
  }
  return layer;
}

VRLayerCylinderPtr
DeviceDelegateOculusVR::CreateLayerCylinder(int32_t aWidth, int32_t aHeight,
                                            VRLayerSurface::SurfaceType aSurfaceType) {
  if (!m.layersEnabled) {
    return nullptr;
  }
  VRLayerCylinderPtr layer = VRLayerCylinder::Create(aWidth, aHeight, aSurfaceType);
  OculusLayerCylinderPtr oculusLayer = OculusLayerCylinder::Create(layer);
  m.AddUILayer(oculusLayer, aSurfaceType);
  return layer;
}

VRLayerCylinderPtr
DeviceDelegateOculusVR::CreateLayerCylinder(const VRLayerSurfacePtr& aMoveLayer) {
  if (!m.layersEnabled) {
    return nullptr;
  }

  VRLayerCylinderPtr layer = VRLayerCylinder::Create(aMoveLayer->GetWidth(), aMoveLayer->GetHeight(), aMoveLayer->GetSurfaceType());
  OculusLayerCylinderPtr oculusLayer;

  for (int i = 0; i < m.uiLayers.size(); ++i) {
    if (m.uiLayers[i]->GetLayer() == aMoveLayer) {
      oculusLayer = OculusLayerCylinder::Create(layer, m.uiLayers[i]);
      m.uiLayers.erase(m.uiLayers.begin() + i);
      break;
    }
  }
  if (oculusLayer) {
    m.AddUILayer(oculusLayer, aMoveLayer->GetSurfaceType());
  }
  return layer;
}


VRLayerCubePtr
DeviceDelegateOculusVR::CreateLayerCube(int32_t aWidth, int32_t aHeight, GLint aInternalFormat) {
  if (!m.layersEnabled) {
    return nullptr;
  }
  if (m.cubeLayer) {
    m.cubeLayer->Destroy();
  }
  VRLayerCubePtr layer = VRLayerCube::Create(aWidth, aHeight, aInternalFormat);
  m.cubeLayer = OculusLayerCube::Create(layer, aInternalFormat);
  if (m.ovr) {
    vrb::RenderContextPtr context = m.context.lock();
    m.cubeLayer->Init(m.java.Env, context);
  }
  return layer;
}

VRLayerEquirectPtr
DeviceDelegateOculusVR::CreateLayerEquirect(const VRLayerPtr &aSource) {
  VRLayerEquirectPtr result = VRLayerEquirect::Create();
  OculusLayerPtr source;
  for (const OculusLayerPtr& layer: m.uiLayers) {
    if (layer->GetLayer() == aSource) {
      source = layer;
      break;
    }
  }
  if (m.equirectLayer) {
    m.equirectLayer->Destroy();
  }
  m.equirectLayer = OculusLayerEquirect::Create(result, source);
  if (m.ovr) {
    vrb::RenderContextPtr context = m.context.lock();
    m.equirectLayer->Init(m.java.Env, context);
  }
  return result;
}

void
DeviceDelegateOculusVR::DeleteLayer(const VRLayerPtr& aLayer) {
  if (m.cubeLayer && m.cubeLayer->layer == aLayer) {
    m.cubeLayer->Destroy();
    m.cubeLayer = nullptr;
    return;
  }
  if (m.equirectLayer && m.equirectLayer->layer == aLayer) {
    m.equirectLayer->Destroy();
    m.equirectLayer = nullptr;
    return;
  }
  for (int i = 0; i < m.uiLayers.size(); ++i) {
    if (m.uiLayers[i]->GetLayer() == aLayer) {
      m.uiLayers[i]->Destroy();
      m.uiLayers.erase(m.uiLayers.begin() + i);
      return;
    }
  }
}

void
DeviceDelegateOculusVR::EnterVR(const crow::BrowserEGLContext& aEGLContext) {
  if (m.ovr) {
    return;
  }

  m.clearColorSwapChain = m.CreateClearColorSwapChain(800, 450);

  vrb::RenderContextPtr render = m.context.lock();
  for (int i = 0; i < VRAPI_EYE_COUNT; ++i) {
    m.eyeSwapChains[i]->Init(render, m.renderMode, m.renderWidth, m.renderHeight);
  }
  vrb::RenderContextPtr context = m.context.lock();
  for (OculusLayerPtr& layer: m.uiLayers) {
    layer->Init(m.java.Env, context);
  }
  if (m.cubeLayer) {
    m.cubeLayer->Init(m.java.Env, context);
  }
  if (m.equirectLayer) {
    m.equirectLayer->Init(m.java.Env, context);
  }

  ovrModeParms modeParms = vrapi_DefaultModeParms(&m.java);
  modeParms.Flags |= VRAPI_MODE_FLAG_NATIVE_WINDOW;
  // No need to reset the FLAG_FULLSCREEN window flag when using a View
  modeParms.Flags &= ~VRAPI_MODE_FLAG_RESET_WINDOW_FULLSCREEN;
  modeParms.Display = reinterpret_cast<unsigned long long>(aEGLContext.Display());
  modeParms.WindowSurface = reinterpret_cast<unsigned long long>(m.app->window);
  modeParms.ShareContext = reinterpret_cast<unsigned long long>(aEGLContext.Context());

  m.ovr = vrapi_EnterVrMode(&modeParms);

  if (!m.ovr) {
    VRB_LOG("Entering VR mode failed");
  } else {
    vrapi_SetPerfThread(m.ovr, VRAPI_PERF_THREAD_TYPE_MAIN, gettid());
    vrapi_SetPerfThread(m.ovr, VRAPI_PERF_THREAD_TYPE_RENDERER, gettid());
    m.UpdateDisplayRefreshRate();
    m.UpdateClockLevels();
    m.UpdateTrackingMode();
    m.UpdateFoveatedLevel();
  }

  // Reset reorientation after Enter VR
  m.reorientMatrix = vrb::Matrix::Identity();
  vrapi_SetRemoteEmulation(m.ovr, true);
}

void
DeviceDelegateOculusVR::LeaveVR() {
  for (OculusLayerPtr& layer: m.uiLayers) {
    layer->Destroy();
  }
  for (int i = 0; i < VRAPI_EYE_COUNT; ++i) {
    m.eyeSwapChains[i]->Destroy();
  }
  if (m.cubeLayer) {
    m.cubeLayer->Destroy();
  }
  if (m.equirectLayer) {
    m.equirectLayer->Destroy();
  }

  if (m.clearColorSwapChain) {
    vrapi_DestroyTextureSwapChain(m.clearColorSwapChain);
    m.clearColorSwapChain = nullptr;
  }
  m.currentFBO = nullptr;
  m.previousFBO = nullptr;

  if (m.ovr) {
    vrapi_LeaveVrMode(m.ovr);
    m.ovr = nullptr;
  }
}

bool
DeviceDelegateOculusVR::IsInVRMode() const {
  return m.ovr != nullptr;
}

bool
DeviceDelegateOculusVR::ExitApp() {
  vrapi_ShowSystemUI(&m.java, VRAPI_SYS_UI_CONFIRM_QUIT_MENU);
  return true;
}

DeviceDelegateOculusVR::DeviceDelegateOculusVR(State &aState) : m(aState) {}

DeviceDelegateOculusVR::~DeviceDelegateOculusVR() { m.Shutdown(); }

} // namespace crow
