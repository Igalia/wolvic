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
OpenXRLayerQuad::Update(XrSpace aSpace, const XrPosef &aReorientPose, XrSwapchain aClearSwapChain)  {
  OpenXRLayerSurface<VRLayerQuadPtr, XrCompositionLayerQuad>::Update(aSpace, aReorientPose, aClearSwapChain);

  const uint numXRLayers = GetNumXRLayers();
  for (int i = 0; i < numXRLayers; ++i) {
    device::Eye eye = i == 0 ? device::Eye::Left : device::Eye::Right;
    xrLayers[i].eyeVisibility = GetEyeVisibility(i);
#if PICOXR
    // Seems like Pico does not properly use the layerSpace.
    xrLayers[i].pose =  MatrixToXrPose(layer->GetModelTransform(eye).Translate(-kAverageHeight));
#else
    xrLayers[i].pose =  MatrixToXrPose(layer->GetModelTransform(eye));
#endif
    xrLayers[i].size.width = layer->GetWorldWidth();
    xrLayers[i].size.height = layer->GetWorldHeight();
    device::EyeRect rect = layer->GetTextureRect(eye);
    xrLayers[i].subImage.swapchain = swapchain->SwapChain();
    xrLayers[i].subImage.imageArrayIndex = 0;
    xrLayers[i].subImage.imageRect = GetRect(swapchain->Width(), swapchain->Height(), rect);
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
OpenXRLayerCylinder::Update(XrSpace aSpace, const XrPosef &aReorientPose, XrSwapchain aClearSwapChain)  {
  OpenXRLayerSurface<VRLayerCylinderPtr, XrCompositionLayerCylinderKHR>::Update(aSpace, aReorientPose, aClearSwapChain);

  const uint numXRLayers = GetNumXRLayers();
  for (int i = 0; i < numXRLayers; ++i) {
    device::Eye eye = i == 0 ? device::Eye::Left : device::Eye::Right;

    auto scale = layer->GetModelTransform(eye).GetScale();
    auto model = layer->GetModelTransform(eye).Scale({1/scale.x(), 1/scale.y(), 1/scale.z()});
#if PICOXR
    // Seems like Pico does not properly use the layerSpace.
    model = model.Translate(-kAverageHeight);
#endif
    xrLayers[i].eyeVisibility = GetEyeVisibility(i);
    xrLayers[i].pose.position = MatrixToXrPose(model).position;
    xrLayers[i].pose.orientation = MatrixToXrPose(layer->GetRotation()).orientation;

    xrLayers[i].radius = layer->GetRadius();
    // See Cylinder.cpp: texScaleX = M_PI / theta;
    xrLayers[i].centralAngle = (float) M_PI / layer->GetUVTransform(eye).GetScale().x();
    xrLayers[i].aspectRatio = (float) layer->GetWidth() / layer->GetHeight();
    device::EyeRect rect = layer->GetTextureRect(device::Eye::Left);
    xrLayers[i].subImage.swapchain = swapchain->SwapChain();
    xrLayers[i].subImage.imageArrayIndex = 0;
    xrLayers[i].subImage.imageRect = GetRect(swapchain->Width(), swapchain->Height(), rect);
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
OpenXRLayerCube::Update(XrSpace aSpace, const XrPosef &aReorientPose, XrSwapchain aClearSwapChain)  {
  OpenXRLayerBase<VRLayerCubePtr, XrCompositionLayerCubeKHR>::Update(aSpace, aReorientPose, aClearSwapChain);

  const uint numXRLayers = GetNumXRLayers();
  for (uint i = 0; i < numXRLayers; ++i) {
    xrLayers[i].layerFlags = 0;
    xrLayers[i].eyeVisibility = GetEyeVisibility(i);
    xrLayers[i].swapchain = swapchain->SwapChain();
    xrLayers[i].imageArrayIndex = 0;
    xrLayers[i].orientation = XrQuaternionf {0.0f, 0.0f, 0.0f, 1.0f};
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

  for (auto& xrLayer: xrLayers)
    xrLayer = { XR_TYPE_COMPOSITION_LAYER_EQUIRECT_KHR };

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
OpenXRLayerEquirect::Update(XrSpace aSpace, const XrPosef &aReorientPose, XrSwapchain aClearSwapChain) {
  OpenXRLayerPtr source = sourceLayer.lock();
  if (source) {
    swapchain = source->GetSwapChain();
  }
  OpenXRLayerBase<VRLayerEquirectPtr, XrCompositionLayerEquirectKHR>::Update(aSpace, aReorientPose, aClearSwapChain);

  const uint numXRLayers = GetNumXRLayers();
  for (int i = 0; i < numXRLayers; ++i) {
    device::Eye eye = i == 0 ? device::Eye::Left : device::Eye::Right;
    xrLayers[i].pose = aReorientPose;
    xrLayers[i].eyeVisibility = GetEyeVisibility(i);
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

#if defined(OCULUSVR) || defined(PFDMXR)
    if (mLayerImageLayout != XR_NULL_HANDLE)
      PushNextXrStructureInChain((XrBaseInStructure&)xrLayers[i], (XrBaseInStructure&)*mLayerImageLayout);
#endif
  }
}

// OpenXRLayerPassthrough;

OpenXRLayerPassthroughPtr
OpenXRLayerPassthrough::Create(XrPassthroughFB passthroughInstance) {
  auto result = std::make_shared<OpenXRLayerPassthrough>();
  result->mPassthroughInstance = passthroughInstance;

  return result;
}

void
OpenXRLayerPassthrough::Init(XrSession session) {
  XrPassthroughLayerCreateInfoFB layerCreateInfo = {
    .type = XR_TYPE_PASSTHROUGH_LAYER_CREATE_INFO_FB,
    .passthrough = mPassthroughInstance,
    .purpose = XR_PASSTHROUGH_LAYER_PURPOSE_RECONSTRUCTION_FB
  };
  CHECK_XRCMD(OpenXRExtensions::sXrCreatePassthroughLayerFB(session, &layerCreateInfo, &mPassthroughLayerHandle));
  xrCompositionLayer = {
    .type = XR_TYPE_COMPOSITION_LAYER_PASSTHROUGH_FB,
    .next = XR_NULL_HANDLE,
    .flags = XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT,
    .space = XR_NULL_HANDLE,
    .layerHandle = mPassthroughLayerHandle,
  };
}

OpenXRLayerPassthrough::~OpenXRLayerPassthrough() {
  if (mPassthroughLayerHandle == XR_NULL_HANDLE)
    return;

  CHECK_XRCMD(OpenXRExtensions::sXrDestroyPassthroughLayerFB(mPassthroughLayerHandle));
  mPassthroughLayerHandle = XR_NULL_HANDLE;
}

}
