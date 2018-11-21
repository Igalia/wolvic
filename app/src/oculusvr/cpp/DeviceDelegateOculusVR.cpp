/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "DeviceDelegateOculusVR.h"
#include "DeviceUtils.h"
#include "ElbowModel.h"
#include "BrowserEGLContext.h"
#include "VRLayer.h"

#include <android_native_app_glue.h>
#include <android/native_window_jni.h>
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

#include "VRBrowser.h"

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

template <class T, class U>
class OculusLayer {
public:
  ovrTextureSwapChain * swapChain = nullptr;
  T layer;
  U ovrLayer;

  void Init() {
    layer->SetInitialized(true);
  }

  virtual void Update(const ovrTracking2& aTracking) {
    vrb::Color tintColor = layer->GetTintColor();
    ovrLayer.Header.ColorScale.x = tintColor.Red();
    ovrLayer.Header.ColorScale.y = tintColor.Green();
    ovrLayer.Header.ColorScale.z = tintColor.Blue();
    ovrLayer.Header.ColorScale.w = tintColor.Alpha();
  }

  virtual const ovrLayerHeader2 * Header() const = 0;

  void SetCurrentEye(device::Eye aEye) {
    layer->SetCurrentEye(aEye);
  }

  bool IsDrawRequested() const {
    return swapChain && layer->IsDrawRequested();
  }

  void ClearRequestDraw() const {
    layer->ClearRequestDraw();
  }

  void SetClipEnabled(bool aEnabled) {
    if (aEnabled) {
      ovrLayer.Header.Flags |= VRAPI_FRAME_LAYER_FLAG_CLIP_TO_TEXTURE_RECT;
    } else {
      ovrLayer.Header.Flags &= ~VRAPI_FRAME_LAYER_FLAG_CLIP_TO_TEXTURE_RECT;
    }
  }


  void Destroy() {
    if (swapChain != nullptr) {
      vrapi_DestroyTextureSwapChain(swapChain);
      swapChain = nullptr;
    }
    layer->SetInitialized(false);
  }

  virtual ~OculusLayer() {
    Destroy();
  }

};

class OculusLayerQuad;
typedef std::shared_ptr<OculusLayerQuad> OculusLayerQuadPtr;

class OculusLayerQuad: public OculusLayer<VRLayerQuadPtr, ovrLayerProjection2> {
public:
  ANativeWindow * nativeWindow = nullptr;
  jobject surface = nullptr;
  vrb::FBOPtr fbo;

  static OculusLayerQuadPtr Create(const VRLayerQuadPtr& aLayer) {
    auto result = std::make_shared<OculusLayerQuad>();
    result->layer = aLayer;
    return result;
  }

  void Init(JNIEnv * aEnv, vrb::RenderContextPtr& aContext) {
    if (swapChain) {
      return;
    }

    ovrLayer = vrapi_DefaultLayerProjection2();
    ovrLayer.Header.SrcBlend = VRAPI_FRAME_LAYER_BLEND_SRC_ALPHA;
    ovrLayer.Header.DstBlend = VRAPI_FRAME_LAYER_BLEND_ONE_MINUS_SRC_ALPHA;

    if (layer->GetSurfaceType() == VRLayerQuad::SurfaceType::AndroidSurface) {
      swapChain = vrapi_CreateAndroidSurfaceSwapChain(layer->GetWidth(), layer->GetHeight());
      surface = vrapi_GetTextureSwapChainAndroidSurface(swapChain);
      surface = aEnv->NewGlobalRef(surface);
      nativeWindow = ANativeWindow_fromSurface(aEnv, surface);
      layer->SetSurface(surface);
    } else {
      swapChain = vrapi_CreateTextureSwapChain(VRAPI_TEXTURE_TYPE_2D, VRAPI_TEXTURE_FORMAT_8888,
                                               layer->GetWidth(), layer->GetHeight(), 1, false);
      fbo = vrb::FBO::Create(aContext);
      GLuint texture = vrapi_GetTextureSwapChainHandle(swapChain, 0);
      VRB_GL_CHECK(glBindTexture(GL_TEXTURE_2D, texture));
      VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE));
      VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE));
      VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR));
      VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR));
      vrb::FBO::Attributes attributes;
      attributes.depth = false;
      attributes.samples = 0;
      VRB_GL_CHECK(fbo->SetTextureHandle(texture, layer->GetWidth(), layer->GetHeight(), attributes));
      if (fbo->IsValid()) {
        fbo->Bind();
        VRB_GL_CHECK(glClearColor(0.0f, 0.0f, 0.0f, 0.0f));
        VRB_GL_CHECK(glClear(GL_COLOR_BUFFER_BIT));
        fbo->Unbind();
      } else {
        VRB_WARN("FAILED to make valid FBO for OculusLayerQuad");
      }
    }

    layer->SetResizeDelegate([=]{
      Resize();
    });
    OculusLayer::Init();
  }

  void Destroy(JNIEnv * aEnv) {
    fbo = nullptr;
    if (surface) {
      aEnv->DeleteGlobalRef(surface);
      surface = nullptr;
      layer->SetSurface(nullptr);
    }
    OculusLayer::Destroy();
  }

  void Update(const ovrTracking2& aTracking) override {
    OculusLayer::Update(aTracking);
    const float w = layer->GetWorldWidth();
    const float h = layer->GetWorldHeight();

    vrb::Matrix scale = vrb::Matrix::Identity();
    scale.ScaleInPlace(vrb::Vector(w * 0.5f, h * 0.5f, 1.0f));

    bool clip = false;

    for (int i = 0; i < VRAPI_FRAME_LAYER_EYE_MAX; ++i) {
      device::Eye eye = i == 0 ? device::Eye::Left : device::Eye::Right;
      vrb::Matrix matrix = layer->GetModelView(eye);
      matrix.PostMultiplyInPlace(scale);
      ovrMatrix4f modelView = ovrMatrixFrom(matrix);

      device::EyeRect textureRect = layer->GetTextureRect(eye);

      ovrLayer.Textures[i].ColorSwapChain = swapChain;
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

  void Resize() {
    if (nativeWindow && swapChain) {
      ANativeWindow_setBuffersGeometry(nativeWindow, layer->GetWidth(), layer->GetHeight(), 0 /* Format unchanged */);
    }
  }

  const ovrLayerHeader2 * Header() const override {
    return &ovrLayer.Header;
  }
};

class OculusLayerCube;
typedef std::shared_ptr<OculusLayerCube> OculusLayerCubePtr;

class OculusLayerCube: public OculusLayer<VRLayerCubePtr, ovrLayerCube2> {
public:
  static OculusLayerCubePtr Create(const VRLayerCubePtr& aLayer) {
    auto result = std::make_shared<OculusLayerCube>();
    result->layer = aLayer;
    return result;
  }

  void Init() {
    if (swapChain) {
      return;
    }

    ovrLayer = vrapi_DefaultLayerCube2();
    ovrLayer.Offset.x = 0.0f;
    ovrLayer.Offset.y = 0.0f;
    ovrLayer.Offset.z = 0.0f;
    swapChain = vrapi_CreateTextureSwapChain(VRAPI_TEXTURE_TYPE_CUBE, VRAPI_TEXTURE_FORMAT_8888, layer->GetWidth(), layer->GetHeight(), 1, false);
    layer->SetTextureHandle(vrapi_GetTextureSwapChainHandle(swapChain, 0));
    OculusLayer::Init();
  }

  void Destroy() {
    if (swapChain == nullptr) {
      return;
    }
    layer->SetTextureHandle(0);
    layer->SetLoaded(false);
    OculusLayer::Destroy();
  }

  bool IsLoaded() const {
    return layer->IsLoaded();
  }

  void Update(const ovrTracking2& aTracking) override {
    OculusLayer::Update(aTracking);
    const ovrMatrix4f centerEyeViewMatrix = vrapi_GetViewMatrixFromPose(&aTracking.HeadPose.Pose);
    const ovrMatrix4f cubeMatrix = ovrMatrix4f_TanAngleMatrixForCubeMap(&centerEyeViewMatrix);
    ovrLayer.HeadPose = aTracking.HeadPose;
    ovrLayer.TexCoordsFromTanAngles = cubeMatrix;

    for (int i = 0; i < VRAPI_FRAME_LAYER_EYE_MAX; ++i) {
      ovrLayer.Textures[i].ColorSwapChain = swapChain;
      ovrLayer.Textures[i].SwapChainIndex = 0;
    }
  }

  const ovrLayerHeader2 * Header() const override {
    return &ovrLayer.Header;
  }
};


class OculusLayerEquirect;
typedef std::shared_ptr<OculusLayerEquirect> OculusLayerEquirectPtr;

class OculusLayerEquirect: public OculusLayer<VRLayerEquirectPtr, ovrLayerEquirect2> {
public:
  std::weak_ptr<OculusLayerQuad> sourceLayer;

  static OculusLayerEquirectPtr Create(const VRLayerEquirectPtr& aLayer, const OculusLayerQuadPtr& aSourceLayer = nullptr) {
    auto result = std::make_shared<OculusLayerEquirect>();
    result->layer = aLayer;
    result->sourceLayer = aSourceLayer;
    return result;
  }

  void Init() {
    OculusLayerQuadPtr source = sourceLayer.lock();
    if (!source) {
      return;
    }

    swapChain = source->swapChain;
    ovrLayer = vrapi_DefaultLayerEquirect2();
    ovrLayer.HeadPose.Pose.Position.x = 0.0f;
    ovrLayer.HeadPose.Pose.Position.y = 0.0f;
    ovrLayer.HeadPose.Pose.Position.z = 0.0f;
    ovrLayer.HeadPose.Pose.Orientation.x  = 0.0f;
    ovrLayer.HeadPose.Pose.Orientation.y  = 0.0f;
    ovrLayer.HeadPose.Pose.Orientation.z  = 0.0f;
    ovrLayer.HeadPose.Pose.Orientation.w  = 1.0f;
    ovrLayer.TexCoordsFromTanAngles = ovrMatrix4f_CreateIdentity();
    OculusLayer::Init();
  }

  void Destroy() {
    swapChain = nullptr;
    OculusLayer::Destroy();
  }

  void Update(const ovrTracking2& aTracking) override {
    OculusLayer::Update(aTracking);

    vrb::Quaternion q(layer->GetModelTransform(device::Eye::Left));
    ovrLayer.HeadPose.Pose.Orientation.x  = q.x();
    ovrLayer.HeadPose.Pose.Orientation.y  = q.y();
    ovrLayer.HeadPose.Pose.Orientation.z  = q.z();
    ovrLayer.HeadPose.Pose.Orientation.w  = q.w();

    bool clip = false;
    for (int i = 0; i < VRAPI_FRAME_LAYER_EYE_MAX; ++i) {
      const device::Eye eye = i == 0 ? device::Eye::Left : device::Eye::Right;
      ovrLayer.Textures[i].ColorSwapChain = swapChain;
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

  const ovrLayerHeader2 * Header() const override {
    return &ovrLayer.Header;
  }
};

struct DeviceDelegateOculusVR::State {
  vrb::RenderContextWeak context;
  android_app* app = nullptr;
  bool initialized = false;
  bool layersEnabled = true;
  ovrJava java = {};
  ovrMobile* ovr = nullptr;
  OculusEyeSwapChainPtr eyeSwapChains[VRAPI_EYE_COUNT];
  OculusLayerCubePtr cubeLayer;
  OculusLayerEquirectPtr equirectLayer;
  std::vector<OculusLayerQuadPtr> uiLayers;
  device::RenderMode renderMode = device::RenderMode::StandAlone;
  vrb::FBOPtr currentFBO;
  vrb::FBOPtr previousFBO;
  vrb::CameraEyePtr cameras[2];
  uint32_t frameIndex = 0;
  double predictedDisplayTime = 0;
  ovrTracking2 predictedTracking = {};
  uint32_t renderWidth = 0;
  uint32_t renderHeight = 0;
  vrb::Color clearColor;
  float near = 0.1f;
  float far = 100.f;
  ovrDeviceID controllerID = ovrDeviceIdType_Invalid;
  ovrInputTrackedRemoteCapabilities controllerCapabilities;
  vrb::Matrix controllerTransform = vrb::Matrix::Identity();
  ovrInputStateTrackedRemote controllerState = {};
  crow::ElbowModelPtr elbow;
  ElbowModel::HandEnum hand = ElbowModel::HandEnum::Right;
  ControllerDelegatePtr controller;
  ImmersiveDisplayPtr immersiveDisplay;
  int reorientCount = -1;
  vrb::Matrix reorientMatrix = vrb::Matrix::Identity();


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
  }

  void GetImmersiveRenderSize(uint32_t& aWidth, uint32_t& aHeight) {
    aWidth = (uint32_t)(vrapi_GetSystemPropertyInt(&java, VRAPI_SYS_PROP_SUGGESTED_EYE_TEXTURE_WIDTH));
    aHeight = (uint32_t)(vrapi_GetSystemPropertyInt(&java, VRAPI_SYS_PROP_SUGGESTED_EYE_TEXTURE_HEIGHT));
  }

  void GetStandaloneRenderSize(uint32_t& aWidth, uint32_t& aHeight) {
    const float scale = layersEnabled ? 1.0 : 1.5f;
    aWidth = scale * (uint32_t)(vrapi_GetSystemPropertyInt(&java, VRAPI_SYS_PROP_SUGGESTED_EYE_TEXTURE_WIDTH));
    aHeight = scale * (uint32_t)(vrapi_GetSystemPropertyInt(&java, VRAPI_SYS_PROP_SUGGESTED_EYE_TEXTURE_HEIGHT));
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
    }

    // Release activity reference
    if (java.ActivityObject) {
      java.Env->DeleteGlobalRef(java.ActivityObject);
      java = {};
    }
  }

  void UpdateControllerID() {
    if (!controller || !ovr) {
      return;
    }

    int index = 0;
    while (true) {
      ovrInputCapabilityHeader capsHeader = {};
      if (vrapi_EnumerateInputDevices(ovr, index++, &capsHeader) < 0) {
        // No more input devices to enumerate
        controller->SetEnabled(0, false);
        break;
      }

      if (capsHeader.Type == ovrControllerType_TrackedRemote) {
        // We are only interested in the remote controller input device
        controllerCapabilities.Header = capsHeader;
        ovrResult result = vrapi_GetInputDeviceCapabilities(ovr, &controllerCapabilities.Header);
        if (result != ovrSuccess) {
          VRB_LOG("vrapi_GetInputDeviceCapabilities failed with error: %d", result);
          continue;
        }
        controllerID = capsHeader.DeviceID;
        if (controllerCapabilities.ControllerCapabilities & ovrControllerCaps_LeftHand) {
          hand = ElbowModel::HandEnum::Left;
        } else {
          hand = ElbowModel::HandEnum::Right;
        }
        controller->SetLeftHanded(0, hand == ElbowModel::HandEnum::Left);
        controller->SetEnabled(0, true);
        controller->SetVisible(0, true);
        return;
      }
    }
  }

  void UpdateControllers(const vrb::Matrix & head) {
    UpdateControllerID();
    if (!controller) {
      return;
    }
    if (controllerID == ovrDeviceIdType_Invalid) {
      return;
    }
    ovrTracking tracking = {};
    if (vrapi_GetInputTrackingState(ovr, controllerID, 0, &tracking) != ovrSuccess) {
      VRB_LOG("Failed to read controller tracking state");
      return;
    }

    if (controllerCapabilities.ControllerCapabilities & ovrControllerCaps_HasOrientationTracking) {
      auto &orientation = tracking.HeadPose.Pose.Orientation;
      vrb::Quaternion quat(orientation.x, orientation.y, orientation.z, orientation.w);
      controllerTransform = vrb::Matrix::Rotation(quat);
    }

    if (controllerCapabilities.ControllerCapabilities & ovrControllerCaps_HasPositionTracking) {
      auto & position = tracking.HeadPose.Pose.Position;
      controllerTransform.TranslateInPlace(vrb::Vector(position.x, position.y, position.z));
    } else {
      controllerTransform = elbow->GetTransform(hand, head, controllerTransform);
    }

    controller->SetTransform(0, controllerTransform);

    controllerState.Header.ControllerType = ovrControllerType_TrackedRemote;
    vrapi_GetCurrentInputState(ovr, controllerID, &controllerState.Header);

    reorientCount = controllerState.RecenterCount;
    const bool triggerPressed = (controllerState.Buttons & ovrButton_A) != 0;
    const bool trackpadPressed = (controllerState.Buttons & ovrButton_Enter) != 0;
    const bool trackpadTouched = (bool) controllerState.TrackpadStatus;
    const bool backPressed = (controllerState.Buttons & ovrButton_Back) != 0;
    controller->SetButtonState(0, ControllerDelegate::BUTTON_TRIGGER, 1, triggerPressed, triggerPressed);
    controller->SetButtonState(0, ControllerDelegate::BUTTON_TOUCHPAD, 0, trackpadPressed, trackpadTouched);
    // By setting vrapi_SetPropertyInt(&java, VRAPI_EAT_NATIVE_GAMEPAD_EVENTS, 0);
    // The app will receive onBackPressed when the back button is pressed on the controller.
    // So there is no need to check for it here. Leaving code commented out for reference
    // in the case that the back button stops working again due to Oculus Mobile API change.
    // controller->SetButtonState(0, ControllerDelegate::BUTTON_APP, -1, backPressed, backPressed);

    const float trackpadX = controllerState.TrackpadPosition.x / (float)controllerCapabilities.TrackpadMaxX;
    const float trackpadY = controllerState.TrackpadPosition.y / (float)controllerCapabilities.TrackpadMaxY;
    float scrollX = trackpadX;
    float scrollY = trackpadY;
    if (trackpadTouched && !trackpadPressed) {
      controller->SetTouchPosition(0, scrollX, scrollY);
    } else {
      controller->SetTouchPosition(0, scrollX, scrollY);
      controller->EndTouch(0);
    }

    const int32_t kNumAxes = 2;
    float axes[kNumAxes] = { trackpadTouched ? trackpadX * 2.0f - 1.0f : 0.0f,
                             trackpadTouched ? trackpadY * 2.0f - 1.0f : 0.0f };
    controller->SetAxes(0, axes, kNumAxes);
  }

  void HandleQuadLayerBind(const OculusLayerQuadPtr& aLayer, GLenum aTarget, bool bound) {
    if (!bound) {
      if (currentFBO && currentFBO == aLayer->fbo) {
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

    if (currentFBO == aLayer->fbo) {
      // Layer already bound
      return;
    }

    if (currentFBO) {
      currentFBO->Unbind();
    }
    previousFBO = currentFBO;
    aLayer->fbo->Bind(aTarget);
    currentFBO = aLayer->fbo;
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

  m.immersiveDisplay->SetDeviceName("Oculus");
  m.immersiveDisplay->SetCapabilityFlags(device::Orientation | device::Present);
  uint32_t width, height;
  m.GetImmersiveRenderSize(width, height);
  m.immersiveDisplay->SetEyeResolution(width, height);
  m.immersiveDisplay->CompleteEnumeration();

  m.UpdatePerspective();
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
  m.controller->CreateController(0, 0, "Gear VR Controller");
  m.controller->SetButtonCount(0, 2);
}

void
DeviceDelegateOculusVR::ReleaseControllerDelegate() {
  m.controller = nullptr;
}

int32_t
DeviceDelegateOculusVR::GetControllerModelCount() const {
  return 1;
}

const std::string
DeviceDelegateOculusVR::GetControllerModelName(const int32_t aModelIndex) const {
  // FIXME: Need Oculus based controller
  static const std::string name("vr_controller_oculusgo.obj");
  return aModelIndex == 0 ? name : "";
}

void
DeviceDelegateOculusVR::ProcessEvents() {

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

  static const vrb::Vector kAverageHeight(0.0f, 1.7f, 0.0f);
  if (m.renderMode == device::RenderMode::StandAlone) {
    head.TranslateInPlace(kAverageHeight);
  }

  m.cameras[VRAPI_EYE_LEFT]->SetHeadTransform(head);
  m.cameras[VRAPI_EYE_RIGHT]->SetHeadTransform(head);

  if (m.immersiveDisplay) {
    m.immersiveDisplay->SetEyeOffset(device::Eye::Left, -ipd * 0.5f, 0.f, 0.f);
    m.immersiveDisplay->SetEyeOffset(device::Eye::Right, ipd * 0.5f, 0.f, 0.f);
    device::CapabilityFlags caps = device::Orientation | device::Present;
    if (m.predictedTracking.Status & VRAPI_TRACKING_STATUS_POSITION_TRACKED) {
      caps |= device::Position;
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

  for (const OculusLayerQuadPtr& layer: m.uiLayers) {
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
    return;
  }

  uint32_t layerCount = 0;
  const ovrLayerHeader2* layers[ovrMaxLayerCount] = {};

  if (m.cubeLayer && m.cubeLayer->IsLoaded() && m.cubeLayer->IsDrawRequested()) {
    m.cubeLayer->Update(m.predictedTracking);
    layers[layerCount++] = m.cubeLayer->Header();
    m.cubeLayer->ClearRequestDraw();
  }

  if (m.equirectLayer && m.equirectLayer->IsDrawRequested()) {
    m.equirectLayer->Update(m.predictedTracking);
    layers[layerCount++] = m.equirectLayer->Header();
    m.equirectLayer->ClearRequestDraw();
  }

  // Sort quad layers by draw priority
  std::sort(m.uiLayers.begin(), m.uiLayers.end(), [](const OculusLayerQuadPtr & a, OculusLayerQuadPtr & b) -> bool {
    return a->layer->ShouldDrawBefore(*b->layer);
  });

  for (const OculusLayerQuadPtr& layer: m.uiLayers) {
    if (layer->IsDrawRequested() && layerCount < ovrMaxLayerCount) {
      layer->Update(m.predictedTracking);
      layers[layerCount++] = layer->Header();
      layer->ClearRequestDraw();
    }
  }

  // Add main eye buffer layer
  ovrLayerProjection2 projection = vrapi_DefaultLayerProjection2();
  projection.HeadPose = m.predictedTracking.HeadPose;
  projection.Header.SrcBlend = VRAPI_FRAME_LAYER_BLEND_SRC_ALPHA;
  projection.Header.DstBlend = VRAPI_FRAME_LAYER_BLEND_ONE_MINUS_SRC_ALPHA;
  for (int i = 0; i < VRAPI_FRAME_LAYER_EYE_MAX; ++i) {
    const auto &eyeSwapChain = m.eyeSwapChains[i];
    int swapChainIndex = m.frameIndex % eyeSwapChain->swapChainLength;
    // Set up OVR layer textures
    projection.Textures[i].ColorSwapChain = eyeSwapChain->ovrSwapChain;
    projection.Textures[i].SwapChainIndex = swapChainIndex;
    projection.Textures[i].TexCoordsFromTanAngles = ovrMatrix4f_TanAngleMatrixFromProjection(
        &m.predictedTracking.Eye[i].ProjectionMatrix);
  }

  layers[layerCount++] = &projection.Header;

  // Submit all layers to TimeWarp
  ovrSubmitFrameDescription2 frameDesc = {};
  frameDesc.Flags = 0;
  if (m.renderMode == device::RenderMode::Immersive) {
    frameDesc.Flags |= VRAPI_FRAME_FLAG_INHIBIT_VOLUME_LAYER;
  }
  frameDesc.SwapInterval = 1;
  frameDesc.FrameIndex = m.frameIndex;
  frameDesc.DisplayTime = m.predictedDisplayTime;
  frameDesc.CompletionFence = 0;

  frameDesc.LayerCount = layerCount;
  frameDesc.Layers = layers;

  vrapi_SubmitFrame2(m.ovr, &frameDesc);
}

VRLayerQuadPtr
DeviceDelegateOculusVR::CreateLayerQuad(int32_t aWidth, int32_t aHeight,
                                        VRLayerQuad::SurfaceType aSurfaceType) {
  if (!m.layersEnabled) {
    return nullptr;
  }
  VRLayerQuadPtr layer = VRLayerQuad::Create(aWidth, aHeight, aSurfaceType);
  OculusLayerQuadPtr oculusLayer = OculusLayerQuad::Create(layer);
  if (m.ovr) {
    vrb::RenderContextPtr context = m.context.lock();
    oculusLayer->Init(m.java.Env, context);
  }
  m.uiLayers.push_back(oculusLayer);
  if (aSurfaceType == VRLayerQuad::SurfaceType::FBO) {
    std::weak_ptr<OculusLayerQuad> weakLayer = oculusLayer;
    layer->SetBindDelegate([=](GLenum aTarget, bool bound){
      OculusLayerQuadPtr layer = weakLayer.lock();
      if (layer) {
        m.HandleQuadLayerBind(layer, aTarget, bound);
      }
    });
    if (m.currentFBO) {
      m.currentFBO->Bind();
    }
  }
  return layer;
}

VRLayerCubePtr
DeviceDelegateOculusVR::CreateLayerCube(int32_t aWidth, int32_t aHeight) {
  if (!m.layersEnabled) {
    return nullptr;
  }
  if (m.cubeLayer) {
    m.cubeLayer->Destroy();
  }
  VRLayerCubePtr layer = VRLayerCube::Create(aWidth, aHeight);
  m.cubeLayer = OculusLayerCube::Create(layer);
  if (m.ovr) {
    m.cubeLayer->Init();
  }
  return layer;
}

VRLayerEquirectPtr
DeviceDelegateOculusVR::CreateLayerEquirect(const VRLayerQuadPtr &aSource) {
  VRLayerEquirectPtr result = VRLayerEquirect::Create();
  OculusLayerQuadPtr source;
  for (const OculusLayerQuadPtr& layer: m.uiLayers) {
    if (layer->layer == aSource) {
      source = layer;
      break;
    }
  }
  if (m.equirectLayer) {
    m.equirectLayer->Destroy();
  }
  m.equirectLayer = OculusLayerEquirect::Create(result, source);
  if (m.ovr) {
    m.equirectLayer->Init();
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
  for (int i = 0; i < m.uiLayers.size(); ++i) {
    if (m.uiLayers[i]->layer.get() == aLayer.get()) {
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

  vrb::RenderContextPtr render = m.context.lock();
  for (int i = 0; i < VRAPI_EYE_COUNT; ++i) {
    m.eyeSwapChains[i]->Init(render, m.renderMode, m.renderWidth, m.renderHeight);
  }
  vrb::RenderContextPtr context = m.context.lock();
  for (OculusLayerQuadPtr& layer: m.uiLayers) {
    layer->Init(m.java.Env, context);
  }
  if (m.cubeLayer) {
    m.cubeLayer->Init();
  }
  if (m.equirectLayer) {
    m.equirectLayer->Init();
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
    vrapi_SetClockLevels(m.ovr, 4, 4);
    vrapi_SetPerfThread(m.ovr, VRAPI_PERF_THREAD_TYPE_MAIN, gettid());
    vrapi_SetPerfThread(m.ovr, VRAPI_PERF_THREAD_TYPE_RENDERER, gettid());
  }

  // Reset reorientation after Enter VR
  m.reorientMatrix = vrb::Matrix::Identity();
  vrapi_SetRemoteEmulation(m.ovr, true);
}

void
DeviceDelegateOculusVR::LeaveVR() {
  m.currentFBO = nullptr;
  m.previousFBO = nullptr;
  if (m.ovr) {
    vrapi_LeaveVrMode(m.ovr);
    m.ovr = nullptr;
  }

  for (int i = 0; i < VRAPI_EYE_COUNT; ++i) {
    m.eyeSwapChains[i]->Destroy();
  }
  for (OculusLayerQuadPtr& layer: m.uiLayers) {
    layer->Destroy(m.java.Env);
  }
  if (m.cubeLayer) {
    m.cubeLayer->Destroy();
  }
  if (m.equirectLayer) {
    m.equirectLayer->Destroy();
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
