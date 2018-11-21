/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VR_LAYER_DOT_H
#define VR_LAYER_DOT_H

#include "vrb/gl.h"
#include "vrb/MacroUtils.h"
#include "vrb/Forward.h"

#include "Device.h"

#include <memory>
#include <functional>
#include <jni.h>

namespace crow {

class VRLayer;
typedef std::shared_ptr<VRLayer> VRLayerPtr;

class VRLayer {
public:
  enum class LayerType {
    QUAD,
    CUBEMAP,
    EQUIRECTANGULAR
  };

  typedef std::function<void(const VRLayer& aLayer)> InitializeDelegate;

  VRLayer::LayerType GetLayerType() const;
  bool IsInitialized() const;
  bool IsDrawRequested() const;
  const vrb::Matrix& GetModelTransform(device::Eye aEye) const;
  const vrb::Matrix& GetModelView(device::Eye aEye) const;
  device::Eye GetCurrentEye() const;
  int32_t GetPriority() const;
  const vrb::Color& GetTintColor() const;
  const device::EyeRect& GetTextureRect(device::Eye aEye) const;
  bool GetDrawInFront() const;

  bool ShouldDrawBefore(const VRLayer& aLayer);
  void SetInitialized(bool aInitialized);
  void RequestDraw();
  void ClearRequestDraw();
  void SetModelTransform(device::Eye aEye, const vrb::Matrix& aModelTransform);
  void SetModelView(device::Eye aEye, const vrb::Matrix& aModelView);
  void SetCurrentEye(device::Eye aEye);
  void SetPriority(int32_t aPriority);
  void SetTintColor(const vrb::Color& aTintColor);
  void SetTextureRect(device::Eye aEye, const device::EyeRect& aTextureRect);
  void SetInitializeDelegate(const InitializeDelegate& aDelegate);
  void SetDrawInFront(bool aDrawInFront);
protected:
  struct State;
  VRLayer(State& aState, LayerType aLayerType);
  virtual ~VRLayer() {};
private:
  State& m;
  VRB_NO_DEFAULTS(VRLayer)
};


class VRLayerQuad;
typedef std::shared_ptr<VRLayerQuad> VRLayerQuadPtr;

class VRLayerQuad: public VRLayer {
public:
  typedef std::function<void()> ResizeDelegate;
  typedef std::function<void(GLenum aTarget, bool aBind)> BindDelegate;

  enum class SurfaceType {
    AndroidSurface,
    FBO,
  };

  static VRLayerQuadPtr Create(const int32_t aWidth, const int32_t aHeight, VRLayerQuad::SurfaceType aSurfaceType);

  SurfaceType GetSurfaceType() const;
  int32_t GetWidth() const;
  int32_t GetHeight() const;
  float GetWorldWidth() const;
  float GetWorldHeight() const;
  jobject GetSurface() const;

  // Only works with SurfaceType::FBO
  void Bind(GLenum aTarget = GL_FRAMEBUFFER);
  // Only works with SurfaceType::FBO
  void Unbind();

  void SetWorldSize(const float aWidth, const float aHeight);
  void Resize(const int32_t aWidth, const int32_t aHeight);
  void SetResizeDelegate(const ResizeDelegate& aDelegate);
  void SetBindDelegate(const BindDelegate& aDelegate);
  void SetSurface(jobject aSurface);
protected:
  struct State;
  VRLayerQuad(State& aState);
  virtual ~VRLayerQuad();
private:
  State& m;
  VRB_NO_DEFAULTS(VRLayerQuad)
};

class VRLayerCube;
typedef std::shared_ptr<VRLayerCube> VRLayerCubePtr;

class VRLayerCube: public VRLayer {
public:
  static VRLayerCubePtr Create(const int32_t aWidth, const int32_t aHeight);

  int32_t GetWidth() const;
  int32_t GetHeight() const;
  GLuint GetTextureHandle() const;
  bool IsLoaded() const;


  void SetTextureHandle(uint32_t aTextureHandle);
  void SetLoaded(bool aReady);
protected:
  struct State;
  VRLayerCube(State& aState);
  virtual ~VRLayerCube();
private:
  State& m;
  VRB_NO_DEFAULTS(VRLayerCube)
};

class VRLayerEquirect;
typedef std::shared_ptr<VRLayerEquirect> VRLayerEquirectPtr;

class VRLayerEquirect: public VRLayer {
public:
  static VRLayerEquirectPtr Create();
  const vrb::Matrix& GetUVTransform(device::Eye aEye) const;
  void SetUVTransform(device::Eye aEye, const vrb::Matrix& aTransform);
protected:
  struct State;
  VRLayerEquirect(State& aState);
  virtual ~VRLayerEquirect();
private:
  State& m;
  VRB_NO_DEFAULTS(VRLayerEquirect)
};

} // namespace crow

#endif //  VR_LAYER_DOT_H
