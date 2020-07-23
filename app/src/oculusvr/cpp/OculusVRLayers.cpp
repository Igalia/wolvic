#include "OculusVRLayers.h"

namespace crow {

static ovrMatrix4f ovrMatrixFrom(const vrb::Matrix &aMatrix) {
  ovrMatrix4f m;
  for (int32_t i = 0; i < 16; ++i) {
    const int32_t row = i / 4;
    const int32_t col = i % 4;
    m.M[row][col] = aMatrix.At(col, row);
  }
  return m;
}

bool OculusLayer::sForceClip = false;

// OculusLayerQuad

OculusLayerQuadPtr
OculusLayerQuad::Create(JNIEnv *aEnv, const VRLayerQuadPtr& aLayer, const OculusLayerPtr& aSource) {
  auto result = std::make_shared<OculusLayerQuad>();
  result->layer = aLayer;
  if (aSource) {
    result->TakeSurface(aEnv, aSource);
  }
  return result;
}

void
OculusLayerQuad::Init(JNIEnv * aEnv, vrb::RenderContextPtr& aContext) {
  ovrLayer = vrapi_DefaultLayerProjection2();
  OculusLayerSurface<VRLayerQuadPtr, ovrLayerProjection2>::Init(aEnv, aContext);
}

void
OculusLayerQuad::Update(uint32_t aFrameIndex, const ovrTracking2& aTracking, ovrTextureSwapChain* aClearSwapChain)  {
  OculusLayerSurface<VRLayerQuadPtr, ovrLayerProjection2>::Update(aFrameIndex, aTracking, aClearSwapChain);
  const float w = layer->GetWorldWidth();
  const float h = layer->GetWorldHeight();

  vrb::Matrix scale = vrb::Matrix::Identity();
  scale.ScaleInPlace(vrb::Vector(w * 0.5f, h * 0.5f, 1.0f));

  bool clip = sForceClip;

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

// OculusLayerCylinder

OculusLayerCylinderPtr
OculusLayerCylinder::Create(JNIEnv *aEnv, const VRLayerCylinderPtr& aLayer, const OculusLayerPtr& aSource) {
  auto result = std::make_shared<OculusLayerCylinder>();
  result->layer = aLayer;
  if (aSource) {
    result->TakeSurface(aEnv, aSource);
  }
  return result;
}

void
OculusLayerCylinder::Init(JNIEnv * aEnv, vrb::RenderContextPtr& aContext) {
  ovrLayer = vrapi_DefaultLayerCylinder2();
  OculusLayerSurface<VRLayerCylinderPtr, ovrLayerCylinder2>::Init(aEnv, aContext);
}

void
OculusLayerCylinder::Update(uint32_t aFrameIndex, const ovrTracking2& aTracking, ovrTextureSwapChain* aClearSwapChain)  {
  OculusLayerSurface<VRLayerCylinderPtr, ovrLayerCylinder2>::Update(aFrameIndex, aTracking, aClearSwapChain);
  ovrLayer.HeadPose = aTracking.HeadPose;
  ovrLayer.Header.SrcBlend = VRAPI_FRAME_LAYER_BLEND_SRC_ALPHA;
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

  if (sForceClip) {
    SetClipEnabled(true);
  }
}


// OculusLayerProjection

OculusLayerProjectionPtr
OculusLayerProjection::Create(JNIEnv *aEnv, const VRLayerProjectionPtr& aLayer) {
  auto result = std::make_shared<OculusLayerProjection>();
  result->layer = aLayer;
  return result;
}

void
OculusLayerProjection::Init(JNIEnv * aEnv, vrb::RenderContextPtr& aContext) {
  ovrLayer = vrapi_DefaultLayerProjection2();
  this->jniEnv = aEnv;
  this->contextWeak = aContext;
  this->ovrLayer.Header.SrcBlend = VRAPI_FRAME_LAYER_BLEND_SRC_ALPHA;
  this->ovrLayer.Header.DstBlend = VRAPI_FRAME_LAYER_BLEND_ONE_MINUS_SRC_ALPHA;
  if (this->swapChain && this->secondSwapChain) {
    return;
  }

  this->swapChain = CreateSwapChain();
  this->secondSwapChain = CreateSwapChain();
  OculusLayerBase<VRLayerProjectionPtr, ovrLayerProjection2>::Init(aEnv, aContext);
}

void
OculusLayerProjection::Update(uint32_t aFrameIndex, const ovrTracking2& aTracking, ovrTextureSwapChain* aClearSwapChain)  {
  OculusLayerSurface<VRLayerProjectionPtr, ovrLayerProjection2>::Update(aFrameIndex, aTracking, aClearSwapChain);

  ovrLayer.HeadPose = aTracking.HeadPose;
  ovrLayer.Header.SrcBlend = VRAPI_FRAME_LAYER_BLEND_SRC_ALPHA;
  ovrLayer.Header.DstBlend = VRAPI_FRAME_LAYER_BLEND_ONE_MINUS_SRC_ALPHA;
  for (int i = 0; i < VRAPI_FRAME_LAYER_EYE_MAX; ++i) {
    const auto &eyeSwapChain = i == 0 ? this->swapChain : this->secondSwapChain;
    const int swapChainIndex = aFrameIndex % eyeSwapChain->SwapChainLength();
    // Set up OVR layer textures
    ovrLayer.Textures[i].ColorSwapChain = eyeSwapChain->SwapChain();
    ovrLayer.Textures[i].SwapChainIndex = swapChainIndex;
    ovrLayer.Textures[i].TexCoordsFromTanAngles = ovrMatrix4f_TanAngleMatrixFromProjection(&projectionMatrix);
  }
}

void
OculusLayerProjection::SetBindDelegate(const OculusLayer::BindDelegate &aDelegate) {
  bindDelegate = aDelegate;
  this->layer->SetBindDelegate([=](GLenum aTarget, bool aBind) {
    if (bindDelegate) {
      this->layer->SetComposited(true);
      OculusSwapChainPtr surface = this->layer->GetCurrentEye() == device::Eye::Left ? this->swapChain : this->secondSwapChain;
      bindDelegate(surface, aTarget, aBind);
    }
  });
}

// OculusLayerCube

OculusLayerCubePtr
OculusLayerCube::Create(const VRLayerCubePtr& aLayer, GLint aInternalFormat) {
  auto result = std::make_shared<OculusLayerCube>();
  result->layer = aLayer;
  result->glFormat = aInternalFormat;
  return result;
}

void
OculusLayerCube::Init(JNIEnv * aEnv, vrb::RenderContextPtr& aContext) {
  if (swapChain) {
    return;
  }

  ovrLayer = vrapi_DefaultLayerCube2();
  ovrLayer.Offset.x = 0.0f;
  ovrLayer.Offset.y = 0.0f;
  ovrLayer.Offset.z = 0.0f;
  swapChain = OculusSwapChain::CreateCubemap(glFormat, layer->GetWidth(), layer->GetHeight());
  layer->SetTextureHandle(swapChain->TextureHandle(0));
  OculusLayerBase<VRLayerCubePtr, ovrLayerCube2>::Init(aEnv, aContext);
}

void
OculusLayerCube::Destroy() {
  if (swapChain == nullptr) {
    return;
  }
  layer->SetTextureHandle(0);
  layer->SetLoaded(false);
  OculusLayerBase<VRLayerCubePtr, ovrLayerCube2>::Destroy();
}

bool
OculusLayerCube::IsLoaded() const {
  return layer->IsLoaded();
}

void
OculusLayerCube::Update(uint32_t aFrameIndex, const ovrTracking2& aTracking, ovrTextureSwapChain* aClearSwapChain)  {
  OculusLayerBase<VRLayerCubePtr, ovrLayerCube2>::Update(aFrameIndex, aTracking, aClearSwapChain);
  const ovrMatrix4f centerEyeViewMatrix = vrapi_GetViewMatrixFromPose(&aTracking.HeadPose.Pose);
  const ovrMatrix4f cubeMatrix = ovrMatrix4f_TanAngleMatrixForCubeMap(&centerEyeViewMatrix);
  ovrLayer.HeadPose = aTracking.HeadPose;
  ovrLayer.TexCoordsFromTanAngles = cubeMatrix;

  for (int i = 0; i < VRAPI_FRAME_LAYER_EYE_MAX; ++i) {
    ovrLayer.Textures[i].ColorSwapChain = GetTargetSwapChain(aClearSwapChain);
    ovrLayer.Textures[i].SwapChainIndex = 0;
  }
}

// OculusLayerEquirect;

OculusLayerEquirectPtr
OculusLayerEquirect::Create(const VRLayerEquirectPtr& aLayer, const OculusLayerPtr& aSourceLayer) {
  auto result = std::make_shared<OculusLayerEquirect>();
  result->layer = aLayer;
  result->sourceLayer = aSourceLayer;
  return result;
}

void
OculusLayerEquirect::Init(JNIEnv * aEnv, vrb::RenderContextPtr& aContext) {
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

void
OculusLayerEquirect::Destroy() {
  swapChain = nullptr;
  OculusLayerBase<VRLayerEquirectPtr, ovrLayerEquirect2>::Destroy();
}

bool
OculusLayerEquirect::IsDrawRequested() const {
  OculusLayerPtr source = sourceLayer.lock();
  return source && source->GetSwapChain() && source->IsComposited() && layer->IsDrawRequested();
}

void
OculusLayerEquirect::Update(uint32_t aFrameIndex, const ovrTracking2& aTracking, ovrTextureSwapChain* aClearSwapChain) {
  OculusLayerPtr source = sourceLayer.lock();
  if (source) {
    swapChain = source->GetSwapChain();
  }
  OculusLayerBase<VRLayerEquirectPtr, ovrLayerEquirect2>::Update(aFrameIndex, aTracking, aClearSwapChain);

  vrb::Quaternion q(layer->GetModelTransform(device::Eye::Left));
  q = q.Inverse();
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


}
