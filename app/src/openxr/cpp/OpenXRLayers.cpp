#include "OpenXRLayers.h"
#include "vrb/RenderContext.h"

namespace crow {

static XrRect2Di GetRect(int32_t width, int32_t height, device::EyeRect rect) {
  XrRect2Di result;
  result.offset.x = (int32_t)(rect.mX * (float)width);
  result.offset.y = (int32_t)(rect.mY * (float)height);
  result.extent.width = (int32_t)(rect.mWidth * (float)width);
  result.extent.height = (int32_t)(rect.mHeight * (float)height);
  return result;
}

// OpenXRLayerQuad

OpenXRLayerQuadPtr
OpenXRLayerQuad::Create(JNIEnv *aEnv, const VRLayerQuadPtr& aLayer, const OpenXRLayerPtr& aSource) {
  auto result = std::make_shared<OpenXRLayerQuad>();
  result->layer = aLayer;
  if (aSource) {
    result->TakeSurface(aEnv, aSource);
  }
  return result;
}

void
OpenXRLayerQuad::Init(JNIEnv * aEnv, XrSession session, vrb::RenderContextPtr& aContext) {
  for (auto& xrLayer: xrLayers) {
    xrLayer = {XR_TYPE_COMPOSITION_LAYER_QUAD};
  }
  OpenXRLayerSurface<VRLayerQuadPtr, XrCompositionLayerQuad>::Init(aEnv, session, aContext);
}

void
OpenXRLayerQuad::Update(XrSpace aSpace, const XrPosef &aPose, XrSwapchain aClearSwapChain)  {
  OpenXRLayerSurface<VRLayerQuadPtr, XrCompositionLayerQuad>::Update(aSpace, aPose, aClearSwapChain);

  for (int i = 0; i < xrLayers.size(); ++i) {
    device::Eye eye = i == 0 ? device::Eye::Left : device::Eye::Right;
    xrLayers[i].pose =  MatrixToXrPose(layer->GetModelTransform(eye));
    xrLayers[i].size.width = layer->GetWorldWidth();
    xrLayers[i].size.height = -layer->GetWorldHeight();
    device::EyeRect rect = layer->GetTextureRect(eye);
    xrLayers[i].subImage.swapchain = swapchain->SwapChain();
    xrLayers[i].subImage.imageArrayIndex = 0;
    xrLayers[i].subImage.imageRect = GetRect(layer->GetWidth(), layer->GetHeight(), rect);
  }
}

// OpenXRLayerCylinder
OpenXRLayerCylinderPtr
OpenXRLayerCylinder::Create(JNIEnv *aEnv, const VRLayerCylinderPtr& aLayer, const OpenXRLayerPtr& aSource) {
  auto result = std::make_shared<OpenXRLayerCylinder>();
  result->layer = aLayer;
  if (aSource) {
    result->TakeSurface(aEnv, aSource);
  }
  return result;
}

void
OpenXRLayerCylinder::Init(JNIEnv * aEnv, XrSession session, vrb::RenderContextPtr& aContext) {
  for (auto& xrLayer: xrLayers) {
    xrLayer = {XR_TYPE_COMPOSITION_LAYER_CYLINDER_KHR};
  }
  OpenXRLayerSurface<VRLayerCylinderPtr, XrCompositionLayerCylinderKHR>::Init(aEnv, session, aContext);
}

void
OpenXRLayerCylinder::Update(XrSpace aSpace, const XrPosef &aPose, XrSwapchain aClearSwapChain)  {
  OpenXRLayerSurface<VRLayerCylinderPtr, XrCompositionLayerCylinderKHR>::Update(aSpace, aPose, aClearSwapChain);

  for (int i = 0; i < xrLayers.size(); ++i) {
    device::Eye eye = i == 0 ? device::Eye::Left : device::Eye::Right;
    vrb::Matrix matrix = XrPoseToMatrix(aPose).PostMultiply(layer->GetModelTransform(eye));
    xrLayers[i].pose = MatrixToXrPose(matrix);
    xrLayers[i].radius = layer->GetRadius();
    // See Cylinder.cpp: texScaleX = M_PI / theta;
    xrLayers[i].centralAngle = (float) M_PI / layer->GetUVTransform(eye).GetScale().x();
    xrLayers[i].aspectRatio = layer->GetWorldWidth() / layer->GetWorldHeight();
    device::EyeRect rect = layer->GetTextureRect(device::Eye::Left);
    xrLayers[i].subImage.swapchain = swapchain->SwapChain();
    xrLayers[i].subImage.imageArrayIndex = 0;
    xrLayers[i].subImage.imageRect = GetRect(layer->GetWidth(), layer->GetHeight(), rect);
  }
}


// OpenXRLayerCube

OpenXRLayerCubePtr
OpenXRLayerCube::Create(const VRLayerCubePtr& aLayer, GLint aInternalFormat) {
  auto result = std::make_shared<OpenXRLayerCube>();
  result->layer = aLayer;
  result->glFormat = aInternalFormat;
  return result;
}

void
OpenXRLayerCube::Init(JNIEnv * aEnv, XrSession session, vrb::RenderContextPtr& aContext) {
  if (this->IsSwapChainReady()) {
    return;
  }

  for (auto& xrLayer: xrLayers) {
    xrLayer = {XR_TYPE_COMPOSITION_LAYER_CUBE_KHR};
  }

  XrSwapchainCreateInfo info{XR_TYPE_SWAPCHAIN_CREATE_INFO};
  info.width = (uint32_t) layer->GetWidth();
  info.height = (uint32_t) layer->GetHeight();
  info.format = glFormat;
  info.mipCount = 1;
  info.faceCount = 6;
  info.sampleCount = 1;
  info.arraySize = 1;
  swapchain = OpenXRSwapChain::create();
  swapchain->InitCubemap(aContext, session, info);
  layer->SetTextureHandle(swapchain->CubemapTexture());

  OpenXRLayerBase<VRLayerCubePtr, XrCompositionLayerCubeKHR>::Init(aEnv, session, aContext);
}

void
OpenXRLayerCube::Destroy() {
  if (!swapchain) {
    return;
  }
  layer->SetTextureHandle(0);
  layer->SetLoaded(false);
  OpenXRLayerBase<VRLayerCubePtr, XrCompositionLayerCubeKHR>::Destroy();
}

bool
OpenXRLayerCube::IsLoaded() const {
  return layer->IsLoaded();
}

void
OpenXRLayerCube::Update(XrSpace aSpace, const XrPosef &aPose, XrSwapchain aClearSwapChain)  {
  OpenXRLayerBase<VRLayerCubePtr, XrCompositionLayerCubeKHR>::Update(aSpace, aPose, aClearSwapChain);

  for (auto& xrLayer: xrLayers) {
    xrLayer.layerFlags = 0;
    xrLayer.swapchain = swapchain->SwapChain();
    xrLayer.imageArrayIndex = 0;
    xrLayer.orientation = XrQuaternionf {0.0f, 0.0f, 0.0f, 1.0f};
  }
}

// OpenXRLayerEquirect;

OpenXRLayerEquirectPtr
OpenXRLayerEquirect::Create(const VRLayerEquirectPtr& aLayer, const OpenXRLayerPtr& aSourceLayer) {
  auto result = std::make_shared<OpenXRLayerEquirect>();
  result->layer = aLayer;
  result->sourceLayer = aSourceLayer;
  return result;
}

void
OpenXRLayerEquirect::Init(JNIEnv * aEnv, XrSession session, vrb::RenderContextPtr& aContext) {
  OpenXRLayerPtr source = sourceLayer.lock();
  if (!source) {
    return;
  }
  swapchain = source->GetSwapChain();
  for (auto& xrLayer: xrLayers) {
    xrLayer = {XR_TYPE_COMPOSITION_LAYER_EQUIRECT_KHR};
  }
  OpenXRLayerBase<VRLayerEquirectPtr, XrCompositionLayerEquirectKHR>::Init(aEnv, session, aContext);
}

void
OpenXRLayerEquirect::Destroy() {
  swapchain = nullptr;
  OpenXRLayerBase<VRLayerEquirectPtr, XrCompositionLayerEquirectKHR>::Destroy();
}

bool
OpenXRLayerEquirect::IsDrawRequested() const {
  OpenXRLayerPtr source = sourceLayer.lock();
  return source && source->GetSwapChain() && source->IsComposited() && layer->IsDrawRequested();
}

void
OpenXRLayerEquirect::Update(XrSpace aSpace, const XrPosef &aPose, XrSwapchain aClearSwapChain) {
  OpenXRLayerPtr source = sourceLayer.lock();
  if (source) {
    swapchain = source->GetSwapChain();
  }
  OpenXRLayerBase<VRLayerEquirectPtr, XrCompositionLayerEquirectKHR>::Update(aSpace, aPose, aClearSwapChain);

  for (int i = 0; i < xrLayers.size(); ++i) {
    device::Eye eye = i == 0 ? device::Eye::Left : device::Eye::Right;
    // Map video orientation
    vrb::Matrix transform = XrPoseToMatrix(aPose).PostMultiply(layer->GetModelTransform(eye));
    xrLayers[i].pose =  XrPoseIdentity(); //MatrixToXrPose(transform);

    // Map surface and rect
    device::EyeRect rect = layer->GetTextureRect(eye);
    xrLayers[i].subImage.swapchain = swapchain->SwapChain();
    xrLayers[i].subImage.imageArrayIndex = 0;
    xrLayers[i].subImage.imageRect = GetRect(swapchain->Width(), swapchain->Height(), rect);

    // Zero radius value is treated as an infinite sphere
    xrLayers[i].radius = 0;

    // Map video projection UV transform
    const vrb::Vector scale = layer->GetUVTransform(eye).GetScale();
    const vrb::Vector translation = layer->GetUVTransform(eye).GetTranslation();
    xrLayers[i].scale.x = scale.x();
    xrLayers[i].scale.y = scale.y();
    xrLayers[i].bias.x = translation.x();
    xrLayers[i].bias.y = translation.y();
  }
}


}
