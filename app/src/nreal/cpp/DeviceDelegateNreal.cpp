/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "DeviceDelegateNreal.h"
#include "ElbowModel.h"
#include "BrowserEGLContext.h"

#include <android_native_app_glue.h>
#include <EGL/egl.h>
#include "vrb/CameraEye.h"
#include "vrb/Color.h"
#include "vrb/ConcreteClass.h"
#include "vrb/FBO.h"
#include "vrb/GLError.h"
#include "vrb/Matrix.h"
#include "vrb/RenderContext.h"
#include "vrb/Vector.h"
#include "vrb/Quaternion.h"

#include <vector>
#include <cstdlib>
#include <unistd.h>

#include "nr_hmd.h"
#include "nr_rendering.h"
#include "nr_tracking.h"


#define NR_CHECK(X) { \
NRResult result = X;  \
  if (result != NR_RESULT_SUCCESS) { \
    __android_log_print(ANDROID_LOG_ERROR, "VRB", "Nreal error running %s: %d", #X, result); \
  } \
}

#define NR_CHECK_OR_EXIT(X) { \
NRResult result = X;  \
  if (result != NR_RESULT_SUCCESS) { \
    __android_log_print(ANDROID_LOG_ERROR, "VRB", "Nreal error running %s: %d", #X, result); \
      exit(result); \
  } \
}

namespace {
  jobject GetActivitySurface(JNIEnv *aEnv, jobject aActivity) {
    jclass clazz = aEnv->GetObjectClass(aActivity);
    jmethodID method = aEnv->GetMethodID(clazz, "getSurface", "()Landroid/view/Surface;");
    jobject result = aEnv->CallObjectMethod(aActivity, method);
    if (!result) {
      VRB_ERROR("Failed to get Surface from NativeActivity!");
    }
    return result;
  }
}

namespace crow {

const int32_t kSwapChainLength = 2;
const float kIPD = 0.064f;
const int32_t kNumEyes = 2;

class NrealEyeSwapChain;
typedef std::shared_ptr<NrealEyeSwapChain> NrealEyeSwapChainPtr;

struct NrealEyeSwapChain {
  int swapChainLength = 0;
  std::vector<GLuint> textures;
  std::vector<vrb::FBOPtr> fbos;

  static NrealEyeSwapChainPtr create() {
    return std::make_shared<NrealEyeSwapChain>();
  }

  void Init(vrb::RenderContextPtr& aContext, device::RenderMode aRenderMode, uint32_t aSwapChainLength, uint32_t aWidth, uint32_t aHeight) {
    Destroy();
    swapChainLength = aSwapChainLength;

    for (int i = 0; i < swapChainLength; ++i) {
      vrb::FBOPtr fbo = vrb::FBO::Create(aContext);
      GLuint textureId;
      VRB_GL_CHECK(glGenTextures(1, &textureId));
      VRB_GL_CHECK(glBindTexture(GL_TEXTURE_2D, textureId));
      VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE));
      VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE));
      VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR));
      VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR));
      VRB_GL_CHECK(glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, aWidth, aHeight, 0,
                             GL_RGBA, GL_UNSIGNED_BYTE, nullptr));

      vrb::FBO::Attributes attributes;
      if (aRenderMode == device::RenderMode::Immersive) {
        attributes.samples = 0;
        attributes.depth = false;
      } else {
        attributes.samples = 2;
        attributes.depth = true;
      }
      VRB_GL_CHECK(fbo->SetTextureHandle(textureId, aWidth, aHeight, attributes));
      if (fbo->IsValid()) {
        textures.push_back(textureId);
        fbos.push_back(fbo);
      } else {
        VRB_LOG("FAILED to make valid FBO");
      }
    }
  }

  void Destroy() {
    fbos.clear();
    for(GLuint textureId: textures) {
      VRB_GL_CHECK(glDeleteTextures(1, &textureId));
    }
    textures.clear();
    swapChainLength = 0;
  }
};

struct DeviceDelegateNreal::State {
  vrb::RenderContextWeak context;
  android_app* app = nullptr;
  JNIEnv* jniEnv = nullptr;
  bool initialized = false;
  NRHandle deviceTracking = NRHandleNull;
  NRHandle headTracking = NRHandleNull;
  NRHandle hmd = NRHandleNull;
  NRHandle renderHandle = NRHandleNull;
  NRMat4f predictedPose;
  jobject surface = nullptr;
  bool isInVRMode = false;
  NrealEyeSwapChainPtr eyeSwapChains[kNumEyes];
  device::RenderMode renderMode = device::RenderMode::StandAlone;
  vrb::FBOPtr currentFBO;
  vrb::CameraEyePtr cameras[2];
  uint32_t frameIndex = 0;
  uint32_t renderWidth = 0;
  uint32_t renderHeight = 0;
  vrb::Color clearColor;
  float near = 0.1f;
  float far = 100.f;;
  ControllerDelegatePtr controller;
  ImmersiveDisplayPtr immersiveDisplay;
  vrb::Matrix reorientMatrix = vrb::Matrix::Identity();

  void Initialize() {
    vrb::RenderContextPtr localContext = context.lock();

    // Initialize the API.
    NRResult result = NRTrackingCreate(&deviceTracking);
    if (result != NR_RESULT_SUCCESS) {
      VRB_ERROR("Failed to initialize Nreal Api!. Error: %d", result);
      exit(result);
    }
    NR_CHECK_OR_EXIT(NRTrackingInitSetTrackingMode(deviceTracking, NRTrackingMode::NR_TRACKING_MODE_6DOF));
    NR_CHECK_OR_EXIT(NRHMDCreate(&hmd));

    NRSize2i eyeResolution;
    NR_CHECK_OR_EXIT(NRHMDGetEyeResolution(result, NREye::NR_EYE_LEFT, &eyeResolution));

    initialized = true;

    renderWidth = (uint32_t) eyeResolution.width;
    renderHeight = (uint32_t) eyeResolution.height;

    for (int i = 0; i < kNumEyes; ++i) {
      cameras[i] = vrb::CameraEye::Create(localContext->GetRenderThreadCreationContext());
      eyeSwapChains[i] = NrealEyeSwapChain::create();
    }

    UpdateEyeInfo();
  }

  void UpdateEyeInfo() {
    UpdateEyeTransform();
    UpdatePerspective();
  }

  void UpdateEyeTransform() {
    for (int eye = 0; eye < 2; ++eye) {
      NRMat4f eyeToHead;
      NR_CHECK(NRHMDGetEyePoseFromHead(hmd, eye == 0 ? NREye::NR_EYE_LEFT : NREye::NR_EYE_RIGHT, &eyeToHead));
      cameras[eye]->SetEyeTransform(ConvertMatrix(eyeToHead));
      if (immersiveDisplay) {
        device::Eye displayEye = eye == 0 ? device::Eye::Left : device::Eye::Right;
        immersiveDisplay->SetEyeOffset(displayEye, eyeToHead.column3.x, eyeToHead.column3.y, eyeToHead.column3.z);
      }
    }
  }

  void UpdatePerspective() {
    for (int eye = 0; eye < 2; ++eye) {
      NRFov4f eyeFov;
      NR_CHECK(NRHMDGetEyeFov(hmd, eye == 0 ? NREye::NR_EYE_LEFT : NREye::NR_EYE_RIGHT, &eyeFov));
      cameras[eye]->SetPerspective(GetProjectionMatrixFromFov(eyeFov, near, far));

      if (immersiveDisplay) {
        auto ToDegrees = [](float aTanAngle) -> float {
          return atanf(aTanAngle) * 180.0f / (float) M_PI;
        };

        device::Eye displayEye = eye == 0 ? device::Eye::Left : device::Eye::Right;
        immersiveDisplay->SetFieldOfView(displayEye, ToDegrees(eyeFov.left_tan), ToDegrees(eyeFov.right_tan),
            ToDegrees(eyeFov.top_tan), ToDegrees(eyeFov.bottom_tan));
      }
    }
  }

  void Shutdown() {
    // Shutdown Nreal mobile SDK
    if (hmd) {
      NR_CHECK(NRHeadTrackingDestroy(deviceTracking, headTracking));
      headTracking = NRHandleNull;
    }

    if (deviceTracking) {
      NR_CHECK(NRTrackingDestroy(deviceTracking));
      deviceTracking = NRHandleNull;
    }
  }

  vrb::Matrix ConvertMatrix(const NRMat4f& aMatrix) {
    return vrb::Matrix::FromColumnMajor(&aMatrix.column0.x);
  }

  vrb::Matrix GetProjectionMatrixFromFov(const NRFov4f &fov, float z_near, float z_far) {
    NRMat4f pm;
    memset(&pm, 0, sizeof(pm));

    float l = -fov.left_tan;
    float r = fov.right_tan;
    float t = fov.top_tan;
    float b = -fov.bottom_tan;

    pm.column0.x = 2.f / (r - l);
    pm.column1.y = 2.f / (t - b);

    pm.column2.x = (r + l) / (r - l);
    pm.column2.y = (t + b) / (t - b);
    pm.column2.z = (z_near + z_far) / (z_near - z_far);
    pm.column2.w = -1.f;

    pm.column3.z = (2 * z_near * z_far) / (z_near - z_far);
    pm.column3.w = 0.f;

    return ConvertMatrix(pm);
  }
};

DeviceDelegateNrealPtr
DeviceDelegateNreal::Create(vrb::RenderContextPtr& aContext, android_app *aApp) {
  DeviceDelegateNrealPtr result = std::make_shared<vrb::ConcreteClass<DeviceDelegateNreal, DeviceDelegateNreal::State> >();
  result->m.context = aContext;
  result->m.app = aApp;
  result->m.Initialize();
  return result;
}


void
DeviceDelegateNreal::SetRenderMode(const device::RenderMode aMode) {
  if (aMode == m.renderMode) {
    return;
  }
  m.renderMode = aMode;
  vrb::RenderContextPtr render = m.context.lock();
  for (int i = 0; i < kSwapChainLength; ++i) {
    m.eyeSwapChains[i]->Init(render, m.renderMode, kSwapChainLength, m.renderWidth, m.renderHeight);
  }
}

device::RenderMode
DeviceDelegateNreal::GetRenderMode() {
  return m.renderMode;
}

void
DeviceDelegateNreal::RegisterImmersiveDisplay(ImmersiveDisplayPtr aDisplay) {
  m.immersiveDisplay = std::move(aDisplay);

  if (!m.immersiveDisplay) {
    return;
  }

  m.immersiveDisplay->SetDeviceName("NREAL");
  m.immersiveDisplay->SetCapabilityFlags(device::Position | device::Orientation | device::Present);
  m.immersiveDisplay->SetEyeResolution(m.renderWidth, m.renderHeight);
  m.immersiveDisplay->CompleteEnumeration();
  m.UpdateEyeInfo();
}

vrb::CameraPtr
DeviceDelegateNreal::GetCamera(const device::Eye aWhich) {
  const int32_t index = device::EyeIndex(aWhich);
  if (index < 0) { return nullptr; }
  return m.cameras[index];
}

const vrb::Matrix&
DeviceDelegateNreal::GetHeadTransform() const {
  return m.cameras[0]->GetHeadTransform();
}

const vrb::Matrix&
DeviceDelegateNreal::GetReorientTransform() const {
  return m.reorientMatrix;
}

void
DeviceDelegateNreal::SetReorientTransform(const vrb::Matrix& aMatrix) {
  m.reorientMatrix = aMatrix;
}

void
DeviceDelegateNreal::SetClearColor(const vrb::Color& aColor) {
  m.clearColor = aColor;
}

void
DeviceDelegateNreal::SetClipPlanes(const float aNear, const float aFar) {
  m.near = aNear;
  m.far = aFar;
  m.UpdatePerspective();
}

void
DeviceDelegateNreal::SetControllerDelegate(ControllerDelegatePtr& aController) {
  m.controller = aController;
}

void
DeviceDelegateNreal::ReleaseControllerDelegate() {
  m.controller = nullptr;
}

int32_t
DeviceDelegateNreal::GetControllerModelCount() const {
  return 1;
}

const std::string
DeviceDelegateNreal::GetControllerModelName(const int32_t aModelIndex) const {
  // FIXME: Need Nreal based controller
  static const std::string name("vr_controller_daydream.obj");
  return aModelIndex == 0 ? name : "";
}

void
DeviceDelegateNreal::ProcessEvents() {

}

void
DeviceDelegateNreal::StartFrame() {
  if (!m.isInVRMode) {
    VRB_LOG("StartFrame called while not in VR mode");
    return;
  }

  m.frameIndex++;
  uint64_t timeNanos = 0;
  uint64_t predictedTime = 0;

  NR_CHECK(NRTrackingGetHMDTimeNanos(m.deviceTracking, &timeNanos));
  NR_CHECK(NRHeadTrackingGetRecommendPredictTime(m.deviceTracking, m.headTracking, &predictedTime));

  NRHandle poseHandle = NRHandleNull;
  NR_CHECK(NRHeadTrackingAcquireTrackingPose(m.deviceTracking, m.headTracking, timeNanos + predictedTime, &poseHandle));

  NRTrackingReason reason;
  NR_CHECK(NRTrackingPoseGetTrackingReason(m.deviceTracking, poseHandle, &reason));

  if (reason != NR_TRACKING_REASON_NULL) {
    VRB_ERROR("Tracking is not working properly. Reason: %d", reason);
    return;
  }
  
  if (NR_RESULT_SUCCESS != NRTrackingPoseGetPose(m.deviceTracking, poseHandle, &m.predictedPose)) {
    VRB_ERROR("NRTrackingPoseGetPose failed");
    return;
  }

  vrb::Matrix head = m.ConvertMatrix(m.predictedPose);

  static const vrb::Vector kAverageHeight(0.0f, 1.7f, 0.0f);
  if (m.renderMode == device::RenderMode::StandAlone) {
    head.TranslateInPlace(kAverageHeight);
  }

  m.cameras[0]->SetHeadTransform(head);
  m.cameras[1]->SetHeadTransform(head);

  VRB_GL_CHECK(glClearColor(m.clearColor.Red(), m.clearColor.Green(), m.clearColor.Blue(), m.clearColor.Alpha()));
}

void
DeviceDelegateNreal::BindEye(const device::Eye aWhich) {
  if (!m.isInVRMode) {
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
}

void
DeviceDelegateNreal::EndFrame(const bool aDiscard) {
  if (!m.isInVRMode) {
    VRB_LOG("EndFrame called while not in VR mode");
    return;
  }

  if (m.currentFBO) {
    m.currentFBO->Unbind();
    m.currentFBO.reset();
  }

  if (aDiscard) {
    return;
  }

  uint32_t leftSwapChainIndex = m.frameIndex % m.eyeSwapChains[0]->swapChainLength;
  uint32_t leftTexture = m.eyeSwapChains[0]->textures[leftSwapChainIndex];
  uint32_t rightSwapChainIndex = m.frameIndex % m.eyeSwapChains[1]->swapChainLength;
  uint32_t rightTexture = m.eyeSwapChains[1]->textures[rightSwapChainIndex];


  NRRenderingDoRender(m.renderHandle, (void *) leftTexture, (void *) rightTexture, &m.predictedPose);
}

void
DeviceDelegateNreal::EnterVR(const crow::BrowserEGLContext& aEGLContext) {
  if (m.isInVRMode) {
    return;
  }

  vrb::RenderContextPtr render = m.context.lock();
  for (int i = 0; i < kNumEyes; ++i) {
    m.eyeSwapChains[i]->Init(render, m.renderMode, kSwapChainLength, m.renderWidth, m.renderHeight);
  }


  NR_CHECK(NRTrackingStart(m.deviceTracking));
  NR_CHECK(NRRenderingCreate(&m.renderHandle));

  (*m.app->activity->vm).AttachCurrentThread(&m.jniEnv, NULL);
  m.surface = GetActivitySurface(m.jniEnv, m.app->activity->clazz);
  m.surface = m.jniEnv->NewGlobalRef(m.surface);
  NR_CHECK(NRRenderingInitSetAndroidSurface(m.renderHandle, m.surface));
  m.UpdateEyeInfo();

  m.isInVRMode = true;
}

void
DeviceDelegateNreal::LeaveVR() {
  if (!m.isInVRMode) {
    return;
  }

  if (m.renderHandle) {
    NR_CHECK(NRRenderingStop(m.renderHandle));
    NR_CHECK(NRRenderingDestroy(m.renderHandle));
    m.renderHandle = NRHandleNull;
  }

  NR_CHECK(NRTrackingStop(m.deviceTracking));

  if (m.surface) {
    m.jniEnv->DeleteGlobalRef(m.surface);
    m.surface = nullptr;
  }

  for (int i = 0; i < kNumEyes; ++i) {
    m.eyeSwapChains[i]->Destroy();
  }
}

bool
DeviceDelegateNreal::IsInVRMode() const {
  return m.isInVRMode;
}

bool
DeviceDelegateNreal::ExitApp() {
    return false;
}


DeviceDelegateNreal::DeviceDelegateNreal(State &aState) : m(aState) {}

DeviceDelegateNreal::~DeviceDelegateNreal() { m.Shutdown(); }

} // namespace crow
