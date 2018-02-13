#include "DeviceDelegateWaveVR.h"

#include "vrb/CameraSimple.h"
#include "vrb/ConcreteClass.h"
#include "vrb/FBO.h"
#include "vrb/Matrix.h"
#include "vrb/Vector.h"

#include <vector>

#include <wvr/wvr.h>
#include <wvr/wvr_render.h>
#include <wvr/wvr_device.h>
#include <wvr/wvr_projection.h>
#include <wvr/wvr_overlay.h>
#include <wvr/wvr_system.h>
#include <wvr/wvr_events.h>

struct DeviceDelegateWaveVR::State {
  vrb::ContextWeak context;
  bool isRunning;
  float near;
  float far;
  void* leftTextureQueue;
  void* rightTextureQueue;
  std::vector<vrb::FBOPtr> leftFBOQueue;
  std::vector<vrb::FBOPtr> rightFBOQueue;
  vrb::CameraSimplePtr cameras[2];
  vrb::Matrix controller;
  uint32_t renderWidth;
  uint32_t renderHeight;
  State()
      : isRunning(true)
      , near(0.1f)
      , far(100.f)
      , controller(vrb::Matrix::Identity())
      , leftTextureQueue(nullptr)
      , rightTextureQueue(nullptr)
      , renderWidth(0)
      , renderHeight(0)
  {}

  int32_t cameraIndex(DeviceDelegate::CameraEnum aWhich) {
    if (DeviceDelegate::CameraEnum::Left == aWhich) { return 0; }
    else if (DeviceDelegate::CameraEnum::Right == aWhich) { return 1; }
    return -1;
  }

  void FillFBOQueue(void* aTextureQueue, std::vector<vrb::FBOPtr>& aFBOQueue) {
    for (int ix = 0; ix < WVR_GetTextureQueueLength(aTextureQueue); ix++) {
      vrb::FBOPtr fbo = vrb::FBO::Create(context);
      fbo->SetTextureHandle((GLuint)WVR_GetTexture(leftTextureQueue, ix).id, renderWidth, renderHeight);
      if (fbo->IsValid()) {
        aFBOQueue.push_back(fbo);
      } else {
        VRB_LOG("FAILED to make valid FBO");
      }
    }
  }

  void InitializeCameras() {
    vrb::Matrix leftProjection = vrb::Matrix::FromColumnMajor(
        WVR_GetProjection(WVR_Eye_Left, near, far).m);
    vrb::Matrix rightProjection = vrb::Matrix::FromColumnMajor(
        WVR_GetProjection(WVR_Eye_Right, near, far).m);

    vrb::Matrix leftEyeOffset = vrb::Matrix::FromColumnMajor(
        WVR_GetTransformFromEyeToHead(WVR_Eye_Left).m).Inverse();
    vrb::Matrix rightEyeOffset = vrb::Matrix::FromColumnMajor(
        WVR_GetTransformFromEyeToHead(WVR_Eye_Right).m).Inverse();
  }

  void Initialize() {
    vrb::ContextPtr localContext = context.lock();
    cameras[cameraIndex(DeviceDelegate::CameraEnum::Left)] = vrb::CameraSimple::Create(context);
    cameras[cameraIndex(DeviceDelegate::CameraEnum::Right)] = vrb::CameraSimple::Create(context);
    InitializeCameras();
    WVR_GetRenderTargetSize(&renderWidth, &renderHeight);
    VRB_LOG("Recommended size is %ux%u", renderWidth, renderHeight);
    if (renderWidth == 0 || renderHeight == 0) {
      VRB_LOG("Please check Wave server configuration");
      return;
    }
    leftTextureQueue = WVR_ObtainTextureQueue(WVR_TextureTarget_2D, WVR_TextureFormat_RGBA, WVR_TextureType_UnsignedByte, renderWidth, renderHeight, 0);
    FillFBOQueue(leftTextureQueue, leftFBOQueue);
    rightTextureQueue = WVR_ObtainTextureQueue(WVR_TextureTarget_2D, WVR_TextureFormat_RGBA, WVR_TextureType_UnsignedByte, renderWidth, renderHeight, 0);
    FillFBOQueue(rightTextureQueue, rightFBOQueue);
  }

  void Shutdown() {

  }
};

DeviceDelegateWaveVRPtr
DeviceDelegateWaveVR::Create(vrb::ContextWeak aContext) {
  DeviceDelegateWaveVRPtr result = std::make_shared<vrb::ConcreteClass<DeviceDelegateWaveVR, DeviceDelegateWaveVR::State> >();
  result->m.context = aContext;
  result->m.Initialize();
  return result;
}

vrb::CameraPtr
DeviceDelegateWaveVR::GetCamera(const CameraEnum aWhich) {
  const int32_t index = m.cameraIndex(aWhich);
  if (index < 0) { return nullptr; }
  return m.cameras[index];
}

void
DeviceDelegateWaveVR::SetClipPlanes(const float aNear, const float aFar) {
  m.near = aNear;
  m.far = aFar;
  m.InitializeCameras();
}

int32_t
DeviceDelegateWaveVR::GetControllerCount() const {
  return 1;
}

const std::string
DeviceDelegateWaveVR::GetControllerModelName(const int32_t) const {
  // FIXME: Need Focus based controller
  static const std::string name("vr_controller_daydream.obj");
  return name;
}

void
DeviceDelegateWaveVR::ProcessEvents() {

}

const vrb::Matrix&
DeviceDelegateWaveVR::GetControllerTransform(const int32_t aWhichController) {
  m.controller = vrb::Matrix::Identity();
  if (aWhichController != 0) { return m.controller; }

  return m.controller;
}

void
DeviceDelegateWaveVR::StartFrame() {

}

void
DeviceDelegateWaveVR::BindEye(const CameraEnum aWhich) {

}

void
DeviceDelegateWaveVR::EndFrame() {

}

bool
DeviceDelegateWaveVR::IsRunning() {
  return m.isRunning;
}

DeviceDelegateWaveVR::DeviceDelegateWaveVR(State& aState) : m(aState) {}
DeviceDelegateWaveVR::~DeviceDelegateWaveVR() { m.Shutdown(); }
