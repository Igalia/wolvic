/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "VRLayer.h"
#include "vrb/ConcreteClass.h"
#include "vrb/Color.h"
#include "vrb/Matrix.h"
#include "VRBrowser.h"

namespace crow {

static uint64_t sIndex = 0;

struct VRLayer::State {
  bool initialized;
  int32_t priority;
  uint64_t drawIndex;
  bool drawRequested;
  bool drawInFront;
  VRLayer::LayerType layerType;
  vrb::Matrix modelTransform[2];
  vrb::Matrix modelView[2];
  device::Eye currentEye;
  vrb::Color clearColor;
  vrb::Color tintColor;
  device::EyeRect textureRect[2];
  SurfaceChangedDelegate surfaceChangedDelegate;
  std::function<void()> pendingEvent;
  std::string name;
  bool composited;
  State():
      initialized(false),
      priority(0),
      drawIndex(0),
      drawRequested(false),
      drawInFront(false),
      composited(false),
      currentEye(device::Eye::Left),
      clearColor(0),
      tintColor(1.0f, 1.0f, 1.0f, 1.0f)
  {
    for (int i = 0; i < 2; ++i) {
      modelTransform[i] = vrb::Matrix::Identity();
      modelView[i] = vrb::Matrix::Identity();
      textureRect[i] = device::EyeRect(0.0f, 0.0f, 1.0f, 1.0f);
    }
  }
};

VRLayer::LayerType
VRLayer::GetLayerType() const {
  return m.layerType;
}

bool
VRLayer::IsInitialized() const {
  return m.initialized;
}

bool
VRLayer::IsDrawRequested() const {
  return m.drawRequested;
}

const vrb::Matrix&
VRLayer::GetModelTransform(device::Eye aEye) const {
  return m.modelTransform[device::EyeIndex(aEye)];
}


const vrb::Matrix&
VRLayer::GetView(device::Eye aEye) const {
  return m.modelView[device::EyeIndex(aEye)];
}

device::Eye
VRLayer::GetCurrentEye() const {
  return m.currentEye;
}

int32_t
VRLayer::GetPriority() const {
  return m.priority;
}


const vrb::Color&
VRLayer::GetClearColor() const {
  return m.clearColor;
}

const vrb::Color&
VRLayer::GetTintColor() const {
  return m.tintColor;
}

const device::EyeRect&
VRLayer::GetTextureRect(crow::device::Eye aEye) const {
  return m.textureRect[device::EyeIndex(aEye)];
}

bool
VRLayer::GetDrawInFront() const {
  return m.drawInFront;
}

std::string
VRLayer::GetName() const {
  return m.name;
}

bool VRLayer::IsComposited() const {
  return m.composited;
}

bool
VRLayer::ShouldDrawBefore(const VRLayer& aLayer) {
  if (m.layerType == VRLayer::LayerType::CUBEMAP || m.layerType == VRLayer::LayerType::EQUIRECTANGULAR) {
    return true;
  }

  if (aLayer.m.layerType == VRLayer::LayerType::CUBEMAP || aLayer.m.layerType == VRLayer::LayerType::EQUIRECTANGULAR) {
    return false;
  }

  if (m.priority != aLayer.m.priority) {
    return m.priority > aLayer.m.priority;
  }

  return m.drawIndex < aLayer.m.drawIndex;
}

void
VRLayer::SetInitialized(bool aInitialized) {
  m.initialized = aInitialized;
}

void
VRLayer::RequestDraw() {
  if (!m.drawRequested) {
    m.drawIndex = ++sIndex;
  }
  m.drawRequested = true;
}

void
VRLayer::ClearRequestDraw() {
  m.drawRequested = false;
}

void
VRLayer::SetModelTransform(device::Eye aEye, const vrb::Matrix& aModelTransform) {
  m.modelTransform[device::EyeIndex(aEye)] = aModelTransform;
}

void
VRLayer::SetView(device::Eye aEye, const vrb::Matrix& aModelView) {
  m.modelView[device::EyeIndex(aEye)] = aModelView;
}

void
VRLayer::SetCurrentEye(crow::device::Eye aEye) {
  m.currentEye = aEye;
}

void
VRLayer::SetPriority(int32_t aPriority) {
  m.priority = aPriority;
}

VRLayer::VRLayer(State& aState, LayerType aLayerType): m(aState) {
  m.layerType = aLayerType;
}


void
VRLayer::SetClearColor(const vrb::Color& aClearColor) {
  m.clearColor = aClearColor;
}

void
VRLayer::SetTintColor(const vrb::Color& aTintColor) {
  m.tintColor = aTintColor;
}

void
VRLayer::SetTextureRect(device::Eye aEye, const crow::device::EyeRect &aTextureRect) {
  m.textureRect[device::EyeIndex(aEye)] = aTextureRect;
}

void
VRLayer::SetSurfaceChangedDelegate(const crow::VRLayer::SurfaceChangedDelegate &aDelegate){
  m.surfaceChangedDelegate = aDelegate;
  if (m.pendingEvent && m.surfaceChangedDelegate) {
    m.pendingEvent();
    m.pendingEvent = nullptr;
  }
}

void
VRLayer::SetDrawInFront(bool aDrawInFront) {
  m.drawInFront = aDrawInFront;
}

void
VRLayer::SetName(const std::string &aName) {
  m.name = aName;
}

void
VRLayer::SetComposited(bool aComposited) {
  m.composited = aComposited;
}

void VRLayer::NotifySurfaceChanged(SurfaceChange aChange, const std::function<void()>& aFirstCompositeCallback) {
  if (m.surfaceChangedDelegate) {
    m.surfaceChangedDelegate(*this, aChange, aFirstCompositeCallback);
  } else {
    m.pendingEvent = [=](){
      NotifySurfaceChanged(aChange, aFirstCompositeCallback);
    };
  }
}

// Layer Quad

struct VRLayerSurface::State: public VRLayer::State {
  VRLayerQuad::SurfaceType surfaceType;
  int32_t width;
  int32_t height;
  int32_t priority;
  float worldWidth;
  float worldHeight;
  GLenum boundTarget;
  VRLayerQuad::ResizeDelegate resizeDelegate;
  VRLayerQuad::BindDelegate bindDelegate;
  jobject surface;
  State():
      surfaceType(VRLayerQuad::SurfaceType::AndroidSurface),
      width(0),
      height(0),
      worldWidth(0),
      worldHeight(0),
      boundTarget(GL_FRAMEBUFFER),
      priority(0),
      surface(nullptr)
  {}
};

VRLayerSurface::SurfaceType
VRLayerSurface::GetSurfaceType() const {
  return m.surfaceType;
}

int32_t
VRLayerSurface::GetWidth() const {
  return m.width;
}

int32_t
VRLayerSurface::GetHeight() const {
  return m.height;
}
float
VRLayerSurface::GetWorldWidth() const {
  return m.worldWidth;
}

float
VRLayerSurface::GetWorldHeight() const {
  return m.worldHeight;
}

jobject
VRLayerSurface::GetSurface() const {
  return m.surface;
}

void
VRLayerSurface::Bind(GLenum aTarget) {
 m.boundTarget = aTarget;
 if (m.bindDelegate) {
   m.bindDelegate(aTarget, true);
 }
}

void
VRLayerSurface::Unbind(){
  if (m.bindDelegate) {
    m.bindDelegate(m.boundTarget, false);
  }
}


void
VRLayerSurface::SetWorldSize(const float aWidth, const float aHeight) {
  m.worldWidth = aWidth;
  m.worldHeight = aHeight;
}

void
VRLayerSurface::Resize(const int32_t aWidth, const int32_t aHeight) {
  if (m.width == aWidth && m.height == aHeight) {
    return;
  }
  m.width = aWidth;
  m.height = aHeight;
  if (m.resizeDelegate) {
    m.resizeDelegate();
  }
}

void
VRLayerSurface::SetResizeDelegate(const ResizeDelegate& aDelegate) {
  m.resizeDelegate = aDelegate;
}

void
VRLayerSurface::SetBindDelegate(const BindDelegate& aDelegate) {
  m.bindDelegate = aDelegate;
}

void
VRLayerSurface::SetSurface(jobject aSurface) {
  auto oldSurface = m.surface;
  m.surface =  aSurface ? VRBrowser::Env()->NewGlobalRef(aSurface) : nullptr;
  if (oldSurface) {
    VRBrowser::Env()->DeleteGlobalRef(oldSurface);
  }
}

VRLayerSurface::VRLayerSurface(State& aState, LayerType aLayerType): VRLayer(aState, aLayerType), m(aState) {
}

VRLayerSurface::~VRLayerSurface() {
  if (m.surface) {
    VRBrowser::Env()->DeleteGlobalRef(m.surface);
  }
}

// Layer Quad

struct VRLayerQuad::State: public VRLayerSurface::State {
  State() {}
};

VRLayerQuadPtr
VRLayerQuad::Create(const int32_t aWidth, const int32_t aHeight, VRLayerSurface::SurfaceType aSurfaceType) {
  auto result = std::make_shared<vrb::ConcreteClass<VRLayerQuad, VRLayerQuad::State>>();
  result->m.width = aWidth;
  result->m.height = aHeight;
  result->m.surfaceType = aSurfaceType;
  return result;
}

VRLayerQuad::VRLayerQuad(State& aState): VRLayerSurface(aState, LayerType::QUAD), m(aState) {

}

VRLayerQuad::~VRLayerQuad() {}

// Layer Cylinder

struct VRLayerCylinder::State: public VRLayerSurface::State {
  float radius;
  vrb::Matrix uvTransform[2];
  State():
      radius(1.0f)
  {
    uvTransform[0] = vrb::Matrix::Identity();
    uvTransform[1] = vrb::Matrix::Identity();
  }
};


VRLayerCylinderPtr
VRLayerCylinder::Create(const int32_t aWidth, const int32_t aHeight, VRLayerSurface::SurfaceType aSurfaceType) {
  auto result = std::make_shared<vrb::ConcreteClass<VRLayerCylinder, VRLayerCylinder::State>>();
  result->m.width = aWidth;
  result->m.height = aHeight;
  result->m.surfaceType = aSurfaceType;
  return result;
}

float
VRLayerCylinder::GetRadius() const {
  return m.radius;
}

const vrb::Matrix&
VRLayerCylinder::GetUVTransform(device::Eye aEye) const {
  return m.uvTransform[device::EyeIndex(aEye)];
}

void
VRLayerCylinder::SetUVTransform(device::Eye aEye, const vrb::Matrix& aTransform) {
  m.uvTransform[device::EyeIndex(aEye)] = aTransform;
}

void
VRLayerCylinder::SetRadius(const float aRadius) {
  m.radius = aRadius;
}

VRLayerCylinder::VRLayerCylinder(State& aState): VRLayerSurface(aState, LayerType::QUAD), m(aState) {

}

VRLayerCylinder::~VRLayerCylinder() {}

// Layer Projection

struct VRLayerProjection::State: public VRLayerSurface::State {
  State() {}
};

VRLayerProjectionPtr
VRLayerProjection::Create(const int32_t aWidth, const int32_t aHeight, VRLayerSurface::SurfaceType aSurfaceType) {
  auto result = std::make_shared<vrb::ConcreteClass<VRLayerProjection, VRLayerProjection::State>>();
  result->m.width = aWidth;
  result->m.height = aHeight;
  result->m.surfaceType = aSurfaceType;
  return result;
}


VRLayerProjection::VRLayerProjection(State& aState): VRLayerSurface(aState, LayerType::PROJECTION), m(aState) {
}

VRLayerProjection::~VRLayerProjection() {
}

// Layer Cube

struct VRLayerCube::State: public VRLayer::State {
  int32_t width;
  int32_t height;
  bool loaded;
  uint32_t textureHandle;
  GLuint  glFormat;
  State():
      width(0),
      height(0),
      loaded(false),
      textureHandle(0),
      glFormat(GL_RGBA8)
  {}
};

VRLayerCubePtr
VRLayerCube::Create(const int32_t aWidth, const int32_t aHeight, const GLuint aGLFormat) {
  auto result = std::make_shared<vrb::ConcreteClass<VRLayerCube, VRLayerCube::State>>();
  result->m.width = aWidth;
  result->m.height = aHeight;
  result->m.glFormat = aGLFormat;
  return result;
}

int32_t
VRLayerCube::GetWidth() const {
  return m.width;
}

int32_t
VRLayerCube::GetHeight() const {
  return m.height;
}

bool
VRLayerCube::IsLoaded() const {
  return m.loaded;
}

GLuint
VRLayerCube::GetTextureHandle() const {
  return m.textureHandle;
}

void
VRLayerCube::SetTextureHandle(uint32_t aTextureHandle){
  m.textureHandle = aTextureHandle;
}

GLuint
VRLayerCube::GetFormat() const {
  return m.glFormat;
}

void
VRLayerCube::SetLoaded(bool aLoaded) {
  m.loaded = aLoaded;
}

VRLayerCube::VRLayerCube(State& aState): VRLayer(aState, LayerType::CUBEMAP), m(aState) {

}

VRLayerCube::~VRLayerCube() {}


// Layer Equirect

struct VRLayerEquirect::State: public VRLayer::State {
  vrb::Matrix uvTransform[2];
  State() {
    uvTransform[0] = vrb::Matrix::Identity();
    uvTransform[1] = vrb::Matrix::Identity();
  }
};

VRLayerEquirectPtr
VRLayerEquirect::Create() {
  auto result = std::make_shared<vrb::ConcreteClass<VRLayerEquirect, VRLayerEquirect::State>>();
  return result;
}


const vrb::Matrix&
VRLayerEquirect::GetUVTransform(device::Eye aEye) const {
  return m.uvTransform[device::EyeIndex(aEye)];
}


void
VRLayerEquirect::SetUVTransform(device::Eye aEye, const vrb::Matrix& aTransform) {
  m.uvTransform[device::EyeIndex(aEye)] = aTransform;
}


VRLayerEquirect::VRLayerEquirect(State& aState): VRLayer(aState, LayerType::EQUIRECTANGULAR), m(aState) {

}

VRLayerEquirect::~VRLayerEquirect() {}


} // namespace crow
