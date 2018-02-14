#include "DeviceDelegateWaveVR.h"

#include "vrb/CameraEye.h"
#include "vrb/ConcreteClass.h"
#include "vrb/FBO.h"
#include "vrb/GLError.h"
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
  int32_t leftFBOIndex;
  int32_t rightFBOIndex;
  vrb::FBOPtr currentFBO;
  std::vector<vrb::FBOPtr> leftFBOQueue;
  std::vector<vrb::FBOPtr> rightFBOQueue;
  vrb::CameraEyePtr cameras[2];
  vrb::Matrix controller;
  uint32_t renderWidth;
  uint32_t renderHeight;
  WVR_DevicePosePair_t devicePairs[WVR_DEVICE_COUNT_LEVEL_1];
  State()
      : isRunning(true)
      , near(0.1f)
      , far(100.f)
      , leftFBOIndex(0)
      , rightFBOIndex(0)
      , controller(vrb::Matrix::Identity())
      , leftTextureQueue(nullptr)
      , rightTextureQueue(nullptr)
      , renderWidth(0)
      , renderHeight(0)
  {
    memset((void*)devicePairs, 0, sizeof(WVR_DevicePosePair_t) * WVR_DEVICE_COUNT_LEVEL_1);
  }

  int32_t cameraIndex(CameraEnum aWhich) {
    if (CameraEnum::Left == aWhich) { return 0; }
    else if (CameraEnum::Right == aWhich) { return 1; }
    return -1;
  }

  void FillFBOQueue(void* aTextureQueue, std::vector<vrb::FBOPtr>& aFBOQueue) {
    for (int ix = 0; ix < WVR_GetTextureQueueLength(aTextureQueue); ix++) {
      vrb::FBOPtr fbo = vrb::FBO::Create(context);
      fbo->SetTextureHandle((GLuint)WVR_GetTexture(aTextureQueue, ix).id, renderWidth, renderHeight);
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
    cameras[cameraIndex(CameraEnum::Left)]->SetPerspective(leftProjection);

    vrb::Matrix rightProjection = vrb::Matrix::FromColumnMajor(
        WVR_GetProjection(WVR_Eye_Right, near, far).m);
    cameras[cameraIndex(CameraEnum::Right)]->SetPerspective(rightProjection);


    vrb::Matrix leftEyeOffset = vrb::Matrix::FromColumnMajor(
        WVR_GetTransformFromEyeToHead(WVR_Eye_Left, WVR_NumDoF_6DoF).m); //.Inverse();
    cameras[cameraIndex(CameraEnum::Left)]->SetEyeTransform(leftEyeOffset);

    vrb::Matrix rightEyeOffset = vrb::Matrix::FromColumnMajor(
        WVR_GetTransformFromEyeToHead(WVR_Eye_Right, WVR_NumDoF_6DoF).m); //.Inverse();
    cameras[cameraIndex(CameraEnum::Right)]->SetEyeTransform(rightEyeOffset);
  }

  void Initialize() {
    vrb::ContextPtr localContext = context.lock();
    cameras[cameraIndex(CameraEnum::Left)] = vrb::CameraEye::Create(context);
    cameras[cameraIndex(CameraEnum::Right)] = vrb::CameraEye::Create(context);
    InitializeCameras();
    WVR_GetRenderTargetSize(&renderWidth, &renderHeight);
    glViewport(0, 0, renderWidth, renderHeight);
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
  WVR_Event_t event;
  while(WVR_PollEventQueue(&event)) {
    WVR_EventType type = event.common.type;
    switch (type) {
      case WVR_EventType_Quit:
        {
          VRB_LOG("WVR_EventType_Quit");
          m.isRunning = false;
        }
        break;
      case WVR_EventType_DeviceConnected:
        {
          VRB_LOG("WVR_EventType_DeviceConnected");
        }
        break;
      case WVR_EventType_DeviceDisconnected:
        {
          VRB_LOG("WVR_EventType_DeviceDisconnected");
        }
        break;
      case WVR_EventType_DeviceStatusUpdate:
        {
          VRB_LOG("WVR_EventType_DeviceStatusUpdate");
        }
        break;
      case WVR_EventType_IpdChanged:
        {
          VRB_LOG("WVR_EventType_IpdChanged");
          m.InitializeCameras();
        }
        break;
      case WVR_EventType_DeviceSuspend:
        {
          VRB_LOG("WVR_EventType_DeviceSuspend");
        }
        break;
      case WVR_EventType_DeviceResume:
        {
          VRB_LOG("WVR_EventType_DeviceResume");
        }
        break;
      case WVR_EventType_DeviceRoleChanged:
        {
          VRB_LOG("WVR_EventType_DeviceRoleChanged");
        }
        break;
      case WVR_EventType_BatteryStatus_Update:
        {
          VRB_LOG("WVR_EventType_BatteryStatus_Update");
        }
        break;
      case WVR_EventType_ChargeStatus_Update:
        {
          VRB_LOG("WVR_EventType_ChargeStatus_Update");
        }
        break;
      case WVR_EventType_DeviceErrorStatus_Update:
        {
          VRB_LOG("WVR_EventType_DeviceErrorStatus_Update");
        }
        break;
      case WVR_EventType_BatteryTemperatureStatus_Update:
        {
          VRB_LOG("WVR_EventType_BatteryTemperatureStatus_Update");
        }
        break;
      case WVR_EventType_RecenterSuccess:
        {
          VRB_LOG("WVR_EventType_RecenterSuccess");
        }
        break;
      case WVR_EventType_RecenterFail:
        {
          VRB_LOG("WVR_EventType_RecenterFail");
        }
        break;
      case WVR_EventType_RecenterSuccess_3DoF:
        {
          VRB_LOG("WVR_EventType_RecenterSuccess_3DoF");
        }
        break;
      case WVR_EventType_RecenterFail_3DoF:
        {
          VRB_LOG("WVR_EventType_RecenterFail_3DoF");
        }
        break;
      case WVR_EventType_TouchpadSwipe_LeftToRight:
        {
          VRB_LOG("WVR_EventType_TouchpadSwipe_LeftToRight");
        }
        break;
      case WVR_EventType_TouchpadSwipe_RightToLeft:
        {
          VRB_LOG("WVR_EventType_TouchpadSwipe_RightToLeft");
        }
        break;
      case WVR_EventType_TouchpadSwipe_DownToUp:
        {
          VRB_LOG("WVR_EventType_TouchpadSwipe_DownToUp");
        }
        break;
      case WVR_EventType_TouchpadSwipe_UpToDown:
        {
          VRB_LOG("WVR_EventType_TouchpadSwipe_UpToDown");
        }
        break;
      case WVR_EventType_Settings_ControllerRoleChange:
        {
          VRB_LOG("WVR_EventType_Settings_ControllerRoleChange");
        }
        break;
      case WVR_EventType_OutOfWall:
        {
          VRB_LOG("WVR_EventType_OutOfWall");
        }
        break;
      case WVR_EventType_BackWithinWall:
        {
          VRB_LOG("WVR_EventType_BackWithinWall");
        }
        break;
      case WVR_EventType_DeviceLoading:
        {
          VRB_LOG("WVR_EventType_DeviceLoading");
        }
        break;
      case WVR_EventType_DeviceLoadingDone:
        {
          VRB_LOG("WVR_EventType_DeviceLoadingDone");
        }
        break;
      case WVR_EventType_ButtonPressed:
        {
          VRB_LOG("WVR_EventType_ButtonPressed");
        }
        break;
      case WVR_EventType_ButtonUnpressed:
        {
          VRB_LOG("WVR_EventType_ButtonUnpressed");
        }
        break;
      case WVR_EventType_TouchTapped:
        {
          VRB_LOG("WVR_EventType_TouchTapped");
        }
        break;
      case WVR_EventType_TouchUntapped:
        {
          VRB_LOG("WVR_EventType_TouchUntapped");
        }
        break;
      case WVR_EventType_ApplicationPause:
        {
          VRB_LOG("WVR_EventType_ApplicationPause");
        }
        break;
      case WVR_EventType_ApplicationResume:
        {
          VRB_LOG("WVR_EventType_ApplicationResume");
        }
        break;
      case WVR_EventType_BackKeyPressed:
        {
          VRB_LOG("WVR_EventType_BackKeyPressed");
        }
        break;
      case WVR_EventType_SurfaceChanged:
        {
          VRB_LOG("WVR_EventType_SurfaceChanged");
        }
        break;
      case WVR_EventType_SurfaceDestroyed:
        {
          VRB_LOG("WVR_EventType_SurfaceDestroyed");
        }
        break;
      default:
        {
          VRB_LOG("Unknown WVR_EventType");
        }
        break;
    }
  }
}

const vrb::Matrix&
DeviceDelegateWaveVR::GetControllerTransform(const int32_t aWhichController) {
  return m.controller;
}

void
DeviceDelegateWaveVR::StartFrame() {
  m.leftFBOIndex = WVR_GetAvailableTextureIndex(m.leftTextureQueue);
  m.rightFBOIndex = WVR_GetAvailableTextureIndex(m.rightTextureQueue);
  // Update cameras
  WVR_GetSyncPose(WVR_PoseOriginModel_OriginOnHead, m.devicePairs, WVR_DEVICE_COUNT_LEVEL_1);
  if (m.devicePairs[WVR_DEVICE_HMD].pose.isValidPose) {
    vrb::Matrix hmd = vrb::Matrix::FromColumnMajor(m.devicePairs[WVR_DEVICE_HMD].pose.poseMatrix.m);
    m.cameras[m.cameraIndex(CameraEnum::Left)]->SetHeadTransform(hmd);
    m.cameras[m.cameraIndex(CameraEnum::Right)]->SetHeadTransform(hmd);
  } else {
    VRB_LOG("Invalid pose returned");
  }

  for (uint32_t id = WVR_DEVICE_HMD + 1; id < WVR_DEVICE_COUNT_LEVEL_1; id++) {
    if ((m.devicePairs[id].type != WVR_DeviceType_Controller_Right) &&
        (m.devicePairs[id].type != WVR_DeviceType_Controller_Left)) {
      continue;
    }

    if (!WVR_IsDeviceConnected(m.devicePairs[id].type)) {
      continue;
    }

    const WVR_PoseState_t &pose = m.devicePairs[id].pose;
    if (!pose.isValidPose) {
      continue;
    }

    m.controller = vrb::Matrix::FromColumnMajor(pose.poseMatrix.m);
  }
}

void
DeviceDelegateWaveVR::BindEye(const CameraEnum aWhich) {
  if (m.currentFBO) {
    m.currentFBO->Unbind();
  }
  if (aWhich == CameraEnum::Left) {
    m.currentFBO = m.leftFBOQueue[m.leftFBOIndex];
  } else if (aWhich == CameraEnum::Right) {
    m.currentFBO = m.rightFBOQueue[m.rightFBOIndex];
  } else {
    m.currentFBO = nullptr;
  }
  if (m.currentFBO) {
    m.currentFBO->Bind();
    VRB_CHECK(glViewport(0, 0, m.renderWidth, m.renderHeight));
    VRB_CHECK(glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT));
  } else {
    VRB_LOG("No FBO found");
  }
}

void
DeviceDelegateWaveVR::EndFrame() {
  if (m.currentFBO) {
    m.currentFBO->Unbind();
    m.currentFBO = nullptr;
  }
  // Left eye
  WVR_TextureParams_t leftEyeTexture = WVR_GetTexture(m.leftTextureQueue, m.leftFBOIndex);
  WVR_SubmitError result = WVR_SubmitFrame(WVR_Eye_Left, &leftEyeTexture);
  if (result != WVR_SubmitError_None) {
    VRB_LOG("Failed to submit left eye frame");
  }

  // Right eye
  WVR_TextureParams_t rightEyeTexture = WVR_GetTexture(m.rightTextureQueue, m.rightFBOIndex);
  result = WVR_SubmitFrame(WVR_Eye_Right, &rightEyeTexture);
  if (result != WVR_SubmitError_None) {
    VRB_LOG("Failed to submit right eye frame");
  }
}

bool
DeviceDelegateWaveVR::IsRunning() {
  return m.isRunning;
}

DeviceDelegateWaveVR::DeviceDelegateWaveVR(State& aState) : m(aState) {}
DeviceDelegateWaveVR::~DeviceDelegateWaveVR() { m.Shutdown(); }
