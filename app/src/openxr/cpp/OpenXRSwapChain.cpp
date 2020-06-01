#include "OpenXRSwapChain.h"
#include "OpenXRHelpers.h"
#include "OpenXRExtensions.h"
#include "vrb/FBO.h"
#include "vrb/GLError.h"
#include "vrb/Logger.h"

namespace crow {

OpenXRSwapChainPtr
OpenXRSwapChain::create() {
  return std::make_shared<OpenXRSwapChain>();
}

void
OpenXRSwapChain::InitFBO(vrb::RenderContextPtr &aContext, XrSession aSession, const XrSwapchainCreateInfo& aInfo, vrb::FBO::Attributes aAttributes) {
  Destroy();
  info = aInfo;
  context = aContext;
  attributes = aAttributes;
  session = aSession;

  CHECK(aInfo.faceCount == 1);
  CHECK(aSession != XR_NULL_HANDLE);
  CHECK_XRCMD(xrCreateSwapchain(aSession, &info, &swapchain));
  CHECK(swapchain != XR_NULL_HANDLE);

  uint32_t imageCount;
  CHECK_XRCMD(xrEnumerateSwapchainImages(swapchain, 0, &imageCount, nullptr));
  CHECK(imageCount > 0);
  imageBuffer.resize(imageCount);
  fbos.resize(imageCount);
  for (XrSwapchainImageOpenGLESKHR& image : imageBuffer) {
    image.type = XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR;
    images.push_back(reinterpret_cast<XrSwapchainImageBaseHeader*>(&image));
  }
  CHECK_XRCMD(xrEnumerateSwapchainImages(swapchain, imageCount, &imageCount, images[0]));
}

void
OpenXRSwapChain::InitAndroidSurface(JNIEnv* aEnv, XrSession aSession, const XrSwapchainCreateInfo& aInfo) {
  Destroy();
  info = aInfo;
  env = aEnv;
  session = aSession;
  CHECK(aSession != XR_NULL_HANDLE);
  CHECK_MSG(env, "JNIEnv must be not null");
  CHECK_XRCMD(OpenXRExtensions::sXrCreateSwapchainAndroidSurfaceKHR(aSession, &info, &swapchain, &surface));
  CHECK(surface);
  CHECK(swapchain != XR_NULL_HANDLE);
  surface = env->NewGlobalRef(surface);
}

void OpenXRSwapChain::InitCubemap(vrb::RenderContextPtr &aContext, XrSession aSession, const XrSwapchainCreateInfo &aInfo) {
  Destroy();
  info = aInfo;
  context = aContext;
  session = aSession;
  // XR_SWAPCHAIN_CREATE_STATIC_IMAGE_BIT is used to hint that we only acquire the image once
  info.createFlags = XR_SWAPCHAIN_CREATE_STATIC_IMAGE_BIT;

  CHECK(aInfo.faceCount == 6);
  CHECK(aSession != XR_NULL_HANDLE);
  CHECK_XRCMD(xrCreateSwapchain(aSession, &info, &swapchain));
  CHECK(swapchain != XR_NULL_HANDLE);

  // Initialize image structs
  uint32_t imageCount;
  CHECK_XRCMD(xrEnumerateSwapchainImages(swapchain, 0, &imageCount, nullptr));
  CHECK(imageCount > 0);
  imageBuffer.resize(imageCount);
  for (XrSwapchainImageOpenGLESKHR& image : imageBuffer) {
    image.type = XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR;
    images.push_back(reinterpret_cast<XrSwapchainImageBaseHeader*>(&image));
  }
  CHECK_XRCMD(xrEnumerateSwapchainImages(swapchain, imageCount, &imageCount, images[0]));

  // Acquire image and get cube texture
  XrSwapchainImageAcquireInfo acquireInfo{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO};
  uint32_t swapchainImageIndex = 0;
  CHECK_XRCMD(xrAcquireSwapchainImage(swapchain, &acquireInfo, &swapchainImageIndex));
  CHECK(swapchainImageIndex < imageBuffer.size());

  XrSwapchainImageWaitInfo waitInfo{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO};
  waitInfo.timeout = XR_INFINITE_DURATION;
  CHECK_XRCMD(xrWaitSwapchainImage(swapchain, &waitInfo));

  // Assert that cubeTexture has a value
  cubeTexture = imageBuffer[swapchainImageIndex].image;
  CHECK(cubeTexture != 0);

  // Release image
  XrSwapchainImageReleaseInfo releaseInfo{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO};
  CHECK_XRCMD(xrReleaseSwapchainImage(swapchain, &releaseInfo));
}

void
OpenXRSwapChain::AcquireImage() {
  CHECK_MSG(!surface, "AcquireImage must not be called for Android Surfaces");
  CHECK_MSG(!acquiredFBO, "Expected no acquired FBOs. ReleaseImage not called?");
  CHECK_MSG(!cubeTexture, "AcquireImage must not be called for cubemap textures");

  XrSwapchainImageAcquireInfo acquireInfo{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO};
  uint32_t swapchainImageIndex = 0;
  CHECK_XRCMD(xrAcquireSwapchainImage(swapchain, &acquireInfo, &swapchainImageIndex));
  CHECK(swapchainImageIndex < imageBuffer.size());
  CHECK(swapchainImageIndex < fbos.size());

  XrSwapchainImageWaitInfo waitInfo{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO};
  waitInfo.timeout = XR_INFINITE_DURATION;
  CHECK_XRCMD(xrWaitSwapchainImage(swapchain, &waitInfo));

  if (!fbos[swapchainImageIndex]) {
    vrb::FBOPtr fbo = vrb::FBO::Create(context);
    fbos[swapchainImageIndex] = fbo;
    uint32_t texture = imageBuffer[swapchainImageIndex].image;
    VRB_GL_CHECK(glBindTexture(GL_TEXTURE_2D, texture));
    VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE));
    VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE));
    VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR));
    VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR));

    VRB_GL_CHECK(fbo->SetTextureHandle(texture, info.width, info.height, attributes));
    if (!fbo->IsValid()) {
      VRB_ERROR("OpenXR XrSwapchainImageOpenGLESKHR texture FBO is not valid");
    } else{
      VRB_DEBUG("OpenXR succesfully created FBO for swapChainImageIndex: %d", swapchainImageIndex);
    }
  }

  acquiredFBO = fbos[swapchainImageIndex];
}

void
OpenXRSwapChain::ReleaseImage() {
  CHECK_MSG(!surface, "ReleaseImage must not be called for Android Surfaces");
  CHECK_MSG(acquiredFBO, "Expected a valid acquired FBO. AcquireImage not called?");
  CHECK_MSG(!cubeTexture, "ReleaseImage must not be called for cubemap textures");

  XrSwapchainImageReleaseInfo releaseInfo{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO};
  CHECK_XRCMD(xrReleaseSwapchainImage(swapchain, &releaseInfo));
  acquiredFBO = nullptr;
}

void
OpenXRSwapChain::BindFBO(GLenum target) {
  CHECK_MSG(!surface, "BindFBO must not be called for Android Surfaces");
  CHECK_MSG(acquiredFBO, "Expected a valid acquired FBO. AcquireImage not called?");
  CHECK_MSG(!cubeTexture, "BindFBO must not be called for cubemap textures");
  acquiredFBO->Bind(target);
}

void
OpenXRSwapChain::Destroy() {
  if (acquiredFBO) {
    ReleaseImage();
  }
  fbos.clear();
  imageBuffer.clear();
  images.clear();
  if (swapchain != XR_NULL_HANDLE) {
    xrDestroySwapchain(swapchain);
    swapchain = XR_NULL_HANDLE;
  }
  if (surface) {
    CHECK_MSG(env, "JNIEnv must be non null to release the AndroidSurface reference");
    env->DeleteGlobalRef(surface);
    surface = nullptr;
  }
  session = XR_NULL_HANDLE;
  env = nullptr;
}

OpenXRSwapChain::~OpenXRSwapChain() {
  Destroy();
}

}
