/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "DeviceDelegateGoogleVR.h"
#include "DeviceUtils.h"
#include "ElbowModel.h"
#include "GestureDelegate.h"

#include "vrb/CameraEye.h"
#include "vrb/Color.h"
#include "vrb/ConcreteClass.h"
#include "vrb/GLError.h"
#include "vrb/Matrix.h"
#include "vrb/Quaternion.h"
#include "vrb/RenderContext.h"
#include "vrb/Vector.h"

#include "vr/gvr/capi/include/gvr.h"
#include "vr/gvr/capi/include/gvr_controller.h"
#include "vr/gvr/capi/include/gvr_gesture.h"

#include <array>
#include <vector>
#include <EGL/egl.h>

namespace {

const vrb::Vector kAverageHeight(0.0f, 1.7f, 0.0f);
const int32_t kMaxControllerCount = 2;

struct Controller {
  vrb::Matrix transform;
  bool enabled;
  bool clicked;
  bool touched;
  crow::ElbowModel::HandEnum hand;
  Controller()
      : transform(vrb::Matrix::Identity())
      , enabled(false)
      , clicked(false)
      , touched(false)
  {}
};

}

namespace crow {

#define GET_GVR_CONTEXT() GetContext()
#define GVR_CHECK(X) X; \
{ \
  gvr_context* context = GET_GVR_CONTEXT(); \
  if (context && (gvr_get_error(context) != GVR_ERROR_NONE)) { \
     VRB_LOG("GVR ERROR: %s at:%s:%s:%d", gvr_get_error_string(gvr_get_error(context)), \
             __FILE__, __FUNCTION__, __LINE__); \
    gvr_clear_error(context); \
  } else if (!context) { \
    VRB_LOG("UNABLE TO CHECK GVR ERROR: NO CONTEXT"); \
  } \
}

struct DeviceDelegateGoogleVR::State {
  gvr_context* gvr;
  gvr_controller_context* controllerContext;
  gvr_controller_state* controllerState;
  gvr_gesture_context* gestureContext;
  device::RenderMode renderMode;
  gvr_buffer_viewport_list* viewportList;
  gvr_buffer_viewport* leftViewport;
  gvr_buffer_viewport* rightViewport;
  gvr_mat4f gvrHeadMatrix;
  vrb::Matrix headMatrix;
  gvr_swap_chain* swapChain;
  gvr_frame* frame;
  gvr_sizei maxRenderSize;
  gvr_sizei webvrSize;
  gvr_sizei frameBufferSize;
  vrb::RenderContextWeak context;
  float near;
  float far;
  bool sixDofHead;
  vrb::Color clearColor;
  vrb::CameraEyePtr cameras[2];
  ElbowModelPtr elbow;
  GestureDelegatePtr gestures;
  crow::ControllerDelegatePtr controllerDelegate;
  std::array<Controller, kMaxControllerCount> controllers;
  ImmersiveDisplayPtr immersiveDisplay;
  bool lastSubmitDiscarded;
  vrb::Matrix reorientMatrix;
  bool recentered;

  State()
      : gvr(nullptr)
      , controllerContext(nullptr)
      , controllerState(nullptr)
      , gestureContext(nullptr)
      , renderMode(device::RenderMode::StandAlone)
      , viewportList(nullptr)
      , leftViewport(nullptr)
      , rightViewport(nullptr)
      , swapChain(nullptr)
      , frame(nullptr)
      , near(0.1f)
      , far(100.f)
      , sixDofHead(false)
      , lastSubmitDiscarded(false)
      , recentered(false)
  {
    frameBufferSize = {0,0};
    maxRenderSize = {0,0};
    gestures = GestureDelegate::Create();
    reorientMatrix = vrb::Matrix::Identity();
  }

  gvr_context* GetContext() { return gvr; }


  void Initialize() {
    vrb::RenderContextPtr render = context.lock();
    if (!render) {
      return;
    }
    vrb::CreationContextPtr create = render->GetRenderThreadCreationContext();
    cameras[device::EyeIndex(device::Eye::Left)] = vrb::CameraEye::Create(create);
    cameras[device::EyeIndex(device::Eye::Right)] = vrb::CameraEye::Create(create);
    elbow = ElbowModel::Create();
    GVR_CHECK(gvr_refresh_viewer_profile(gvr));
    viewportList = GVR_CHECK(gvr_buffer_viewport_list_create(gvr));
    leftViewport = GVR_CHECK(gvr_buffer_viewport_create(gvr));
    rightViewport = GVR_CHECK(gvr_buffer_viewport_create(gvr));
  }

  void InitializeControllers() {
    if (!gvr) {
      VRB_LOG("Failed to initialize controllers. No GVR context.");
      return;
    }
    int32_t options = GVR_CHECK(gvr_controller_get_default_options());
    options |= GVR_CONTROLLER_ENABLE_GYRO | GVR_CONTROLLER_ENABLE_ACCEL | GVR_CONTROLLER_ENABLE_ARM_MODEL;
    controllerContext = GVR_CHECK(gvr_controller_create_and_init(options, gvr));
    GVR_CHECK(gvr_controller_resume(controllerContext));
    controllerState = GVR_CHECK(gvr_controller_state_create());
    const gvr_user_prefs* prefs = GVR_CHECK(gvr_get_user_prefs(gvr));
    if (prefs) {
      controllers[0].hand = ((gvr_user_prefs_get_controller_handedness(prefs) == GVR_CONTROLLER_RIGHT_HANDED) ?
              ElbowModel::HandEnum::Right : ElbowModel::HandEnum::Left);
      controllers[1].hand = (controllers[0].hand == ElbowModel::HandEnum::Right ? ElbowModel::HandEnum::Left : ElbowModel::HandEnum::Right);
    } else {
      controllers[0].hand = ElbowModel::HandEnum::Right;
      controllers[1].hand = ElbowModel::HandEnum::Left;
    }
    gestureContext = gvr_gesture_context_create();
  }

  void Shutdown() {
  }

  void
  SetRenderMode(const device::RenderMode aMode) {
    if (aMode != renderMode) {
      renderMode = aMode;
      reorientMatrix = vrb::Matrix::Identity();
      webvrSize = GetRecommendedImmersiveModeSize();
      CreateSwapChain();
    }
  }

  gvr_sizei GetRecommendedImmersiveModeSize() {
    // GVR SDK states that thee maximum effective render target size can be very large.
    // Most applications need to scale down to compensate.
    // Half pixel sizes are used by scaling each dimension by sqrt(2)/2 ~= 7/10ths.
    gvr_sizei result;
    result.width = (7 * maxRenderSize.width) / 10;
    result.height = (7 * maxRenderSize.height) / 10;
    return result;
  }

  void
  CreateSwapChain()  {
    if (swapChain) {
      // gvr_swap_chain_destroy will set the pointer to null
      GVR_CHECK(gvr_swap_chain_destroy(&swapChain));
    }
    gvr_buffer_spec* spec = GVR_CHECK(gvr_buffer_spec_create(gvr));
    gvr_sizei size = maxRenderSize;
    if (renderMode == device::RenderMode::Immersive) {
      size = webvrSize;
      GVR_CHECK(gvr_buffer_spec_set_size(spec, size));
      GVR_CHECK(gvr_buffer_spec_set_samples(spec, 0));
      GVR_CHECK(gvr_buffer_spec_set_color_format(spec, GVR_COLOR_FORMAT_RGBA_8888));
      GVR_CHECK(gvr_buffer_spec_set_depth_stencil_format(spec, GVR_DEPTH_STENCIL_FORMAT_DEPTH_16));
    } else {
      GVR_CHECK(gvr_buffer_spec_set_size(spec, size));
      GVR_CHECK(gvr_buffer_spec_set_samples(spec, 2));
      GVR_CHECK(gvr_buffer_spec_set_color_format(spec, GVR_COLOR_FORMAT_RGBA_8888));
      GVR_CHECK(gvr_buffer_spec_set_depth_stencil_format(spec, GVR_DEPTH_STENCIL_FORMAT_DEPTH_24));
    }

    swapChain = GVR_CHECK(gvr_swap_chain_create(gvr, (const gvr_buffer_spec**)&spec, 1));
    GVR_CHECK(gvr_buffer_spec_destroy(&spec));
    GVR_CHECK(gvr_get_recommended_buffer_viewports(gvr, viewportList));
    size = GVR_CHECK(gvr_swap_chain_get_buffer_size(swapChain, 0))
    if ((size.width != frameBufferSize.width) || (size.height != frameBufferSize.height)) {
      frameBufferSize.width = size.width;
      frameBufferSize.height = size.height;
      GVR_CHECK(gvr_swap_chain_resize_buffer(swapChain, 0, frameBufferSize));
      VRB_LOG("Resize Swap Chain %d,%d", frameBufferSize.width, frameBufferSize.height);
    }

    if (immersiveDisplay) {
      immersiveDisplay->SetEyeResolution(frameBufferSize.width / 2, frameBufferSize.height);
    }
  }

  void
  UpdateCameras() {
    gvr_mat4f eyes[GVR_NUM_EYES];
    for (int32_t index = 0; index < GVR_NUM_EYES; index++) {
      eyes[index] = GVR_CHECK(gvr_get_eye_from_head_matrix(gvr, index));
      cameras[index]->SetEyeTransform(vrb::Matrix::Translation(
          vrb::Vector(-eyes[index].m[0][3], -eyes[index].m[1][3], -eyes[index].m[2][3])));
      cameras[index]->SetHeadTransform(headMatrix);
    }

    if (!viewportList || !leftViewport || !rightViewport) {
      VRB_LOG("ERROR: view port lists not created");
      return;
    }

    GVR_CHECK(gvr_get_recommended_buffer_viewports(gvr, viewportList));
    GVR_CHECK(gvr_buffer_viewport_list_get_item(viewportList, 0, leftViewport));
    GVR_CHECK(gvr_buffer_viewport_list_get_item(viewportList, 1, rightViewport));

    gvr_rectf fovs[GVR_NUM_EYES];
    fovs[GVR_LEFT_EYE] = GVR_CHECK(gvr_buffer_viewport_get_source_fov(leftViewport));
    fovs[GVR_RIGHT_EYE] = GVR_CHECK(gvr_buffer_viewport_get_source_fov(rightViewport));

    for (int32_t index = 0; index < GVR_NUM_EYES; index++) {
      cameras[index]->SetPerspective(
          vrb::Matrix::PerspectiveMatrixFromDegrees(fovs[index].left, fovs[index].right,
                                                    fovs[index].top, fovs[index].bottom,
                                                    near, far));
    }

    if (!immersiveDisplay) {
      return;
    }

    for (int32_t index = 0; index < GVR_NUM_EYES; index++) {
      const device::Eye which = (index == GVR_LEFT_EYE ? device::Eye::Left : device::Eye::Right);
      immersiveDisplay->SetEyeOffset(which,
                                     -eyes[index].m[0][3], -eyes[index].m[1][3],
                                     -eyes[index].m[2][3]);
      immersiveDisplay->SetFieldOfView(which, fovs[index].left, fovs[index].right, fovs[index].top,
                                       fovs[index].bottom);
    }
  }

  void
  UpdateControllers() {
    int32_t controllerCount = GVR_CHECK(gvr_controller_get_count(controllerContext));
    if (controllerCount > kMaxControllerCount) {
      controllerCount = kMaxControllerCount;
    }
    if (!controllerDelegate) {
      return;
    }
    for (int32_t index = 0; index < controllerCount; index++) {
      Controller& controller = controllers[index];
      GVR_CHECK(gvr_controller_state_update(controllerContext, index, controllerState));
      if (gvr_controller_state_get_connection_state(controllerState) != GVR_CONTROLLER_CONNECTED) {
        if (controller.enabled) {
          VRB_ERROR("Controller not connected.");
          controller.enabled = false;
          controllerDelegate->SetEnabled(index, false);
        }
        continue;
      } else if (!controller.enabled) {
        VRB_LOG("Controller Connected");
        controller.enabled = true;
        controllerDelegate->SetEnabled(index, true);
        controllerDelegate->SetVisible(index, true);
        controllerDelegate->SetCapabilityFlags(index, device::Orientation);
      }

      recentered = recentered || gvr_controller_state_get_recentered(controllerState);

      gvr_quatf ori = gvr_controller_state_get_orientation(controllerState);
      vrb::Quaternion quat(ori.qx, ori.qy, ori.qz, ori.qw);
      controller.transform = vrb::Matrix::Rotation(vrb::Quaternion(ori.qx, ori.qy, ori.qz, ori.qw));
      if (elbow) {
        controller.transform = elbow->GetTransform(controller.hand, headMatrix, controller.transform);
      }
      controllerDelegate->SetTransform(index, controller.transform);
      controllerDelegate->SetLeftHanded(index, controller.hand == ElbowModel::HandEnum::Left);
      controllerDelegate->SetButtonCount(index, 1); // For immersive mode
      // Index 0 is the dummy button so skip it.
      for (int ix = 1; ix < GVR_CONTROLLER_BUTTON_COUNT; ix++) {
        const uint64_t buttonMask = (uint64_t) 0x01 << (ix - 1);
        const bool clicked = gvr_controller_state_get_button_state(controllerState, ix);
        if (ix == GVR_CONTROLLER_BUTTON_CLICK) {
          const bool touched = gvr_controller_state_is_touching(controllerState);
          const int kNumImmersiveAxes = 2;
          float immersiveAxes[kNumImmersiveAxes] = { 0.0f, 0.0f };
          if (touched) {
            gvr_vec2f axes = gvr_controller_state_get_touch_pos(controllerState);
            immersiveAxes[0] = axes.x * 2.0f - 1.0f;
            immersiveAxes[1] = axes.y * 2.0f - 1.0f;
            controllerDelegate->SetTouchPosition(index, axes.x, axes.y);
          } else if (touched != controller.touched){
            controllerDelegate->EndTouch(index);
          }
          controller.touched = touched;
          controller.clicked = clicked;
          controllerDelegate->SetButtonState(index, ControllerDelegate::BUTTON_TOUCHPAD, 0, clicked, touched);
          controllerDelegate->SetAxes(index, immersiveAxes, kNumImmersiveAxes);
        }
        else if (ix == GVR_CONTROLLER_BUTTON_APP) {
          controllerDelegate->SetButtonState(index, ControllerDelegate::BUTTON_APP, -1, clicked, clicked);
        }
      }
    }
    if (!gestures) {
      return;
    }
    // Detect the gestures.
    gvr_gesture_update(controllerState, gestureContext);

    // Get the number of detected gestures.
    const int32_t gestureCount = gvr_gesture_get_count(gestureContext);
    gestures->Reset();
    for (int count = 0; count < gestureCount; count++) {
      const gvr_gesture* gesture = gvr_gesture_get(gestureContext, count);
      switch (gvr_gesture_get_type(gesture)) {
        case GVR_GESTURE_SWIPE:
          {
            // Handle swipe gesture.
            gvr_gesture_direction direction = gvr_gesture_get_direction(gesture);
            if (direction == GVR_GESTURE_DIRECTION_LEFT) {
              gestures->AddGesture(GestureType::SwipeLeft);
            } else if (direction == GVR_GESTURE_DIRECTION_RIGHT) {
              gestures->AddGesture(GestureType::SwipeRight);
            }
          }
          break;
        case GVR_GESTURE_SCROLL_START:
          // Handle the start of a sequence of scroll gestures.
          break;
        case GVR_GESTURE_SCROLL_UPDATE:
          // Handle an initialize in a sequence of scroll gestures.
          break;
        case GVR_GESTURE_SCROLL_END:
          // Handle the end of a sequence of scroll gestures.
          break;
        default:
          // Unexpected gesture type.
          break;
      }
    }
  }
};

#undef GET_GVR_CONTEXT
#define GET_GVR_CONTEXT() m.GetContext()

DeviceDelegateGoogleVRPtr
DeviceDelegateGoogleVR::Create(vrb::RenderContextPtr& aContext, void* aGVRContext) {
  DeviceDelegateGoogleVRPtr result = std::make_shared<vrb::ConcreteClass<DeviceDelegateGoogleVR, DeviceDelegateGoogleVR::State> >();
  result->m.context = aContext;
  result->m.gvr = (gvr_context*)aGVRContext;
  result->m.Initialize();
  return result;
}

void
DeviceDelegateGoogleVR::SetRenderMode(const device::RenderMode aMode) {
  m.SetRenderMode(aMode);
}

device::RenderMode
DeviceDelegateGoogleVR::GetRenderMode() {
  return m.renderMode;
}

void
DeviceDelegateGoogleVR::RegisterImmersiveDisplay(ImmersiveDisplayPtr aDisplay) {
  m.immersiveDisplay = std::move(aDisplay);

  if (!m.immersiveDisplay) {
    return;
  }

  m.immersiveDisplay->SetDeviceName("Daydream");
  m.immersiveDisplay->SetCapabilityFlags(device::Position | device::Orientation | device::Present | device::StageParameters |
                                         device::InlineSession | device::ImmersiveVRSession);
  m.immersiveDisplay->SetSittingToStandingTransform(vrb::Matrix::Translation(kAverageHeight));
  gvr_sizei size = m.GetRecommendedImmersiveModeSize();
  m.immersiveDisplay->SetEyeResolution(size.width / 2, size.height);
  m.immersiveDisplay->CompleteEnumeration();
  m.UpdateCameras();
}

void
DeviceDelegateGoogleVR::SetImmersiveSize(const uint32_t aEyeWidth, const uint32_t aEyeHeight) {
  gvr_sizei recommendedSize = m.GetRecommendedImmersiveModeSize();

  auto targetWidth = (uint32_t)  m.frameBufferSize.width;
  auto targetHeight = (uint32_t) m.frameBufferSize.height;

  DeviceUtils::GetTargetImmersiveSize(aEyeWidth, aEyeHeight, (uint32_t) recommendedSize.width, (uint32_t) recommendedSize.height,
                                      (uint32_t) m.maxRenderSize.width, (uint32_t) m.maxRenderSize.height, targetWidth, targetHeight);

  // The new swapChain is recreated in the next StartFrame call.
  m.webvrSize.width = targetWidth;
  m.webvrSize.height = targetHeight;
}

GestureDelegateConstPtr
DeviceDelegateGoogleVR::GetGestureDelegate() {
  return m.gestures;
}
vrb::CameraPtr
DeviceDelegateGoogleVR::GetCamera(const device::Eye aWhich) {
  const int32_t index = device::EyeIndex(aWhich);
  if (index < 0) { return nullptr; }
  return m.cameras[index];
}

const vrb::Matrix&
DeviceDelegateGoogleVR::GetHeadTransform() const {
  return m.cameras[0]->GetHeadTransform();
}

const vrb::Matrix&
DeviceDelegateGoogleVR::GetReorientTransform() const {
  return m.reorientMatrix;
}

void
DeviceDelegateGoogleVR::SetReorientTransform(const vrb::Matrix& aMatrix) {
  m.reorientMatrix = aMatrix;
}

void
DeviceDelegateGoogleVR::SetClearColor(const vrb::Color& aColor) {
  m.clearColor = aColor;
}

void
DeviceDelegateGoogleVR::SetClipPlanes(const float aNear, const float aFar) {
  m.near = aNear;
  m.far = aFar;
}

void
DeviceDelegateGoogleVR::SetControllerDelegate(ControllerDelegatePtr& aController) {
  m.controllerDelegate = aController;
  if (!m.controllerDelegate) {
    return;
  }
  for (int32_t index = 0; index < kMaxControllerCount; index++) {
    m.controllerDelegate->CreateController(index, 0, "Daydream Controller");
  }
}

void
DeviceDelegateGoogleVR::ReleaseControllerDelegate() {
  m.controllerDelegate = nullptr;
}

int32_t
DeviceDelegateGoogleVR::GetControllerModelCount() const {
  return 1;
}

const std::string
DeviceDelegateGoogleVR::GetControllerModelName(const int32_t aModelIndex) const {
  static const std::string name("vr_controller_daydream.obj");
  return aModelIndex == 0 ? name : "";
}

void
DeviceDelegateGoogleVR::ProcessEvents() {
  m.UpdateControllers();
}

void
DeviceDelegateGoogleVR::StartFrame() {
  if (m.renderMode == device::RenderMode::Immersive &&
      (m.webvrSize.width != m.frameBufferSize.width || m.webvrSize.height != m.frameBufferSize.height)) {
      m.CreateSwapChain();
  }
  gvr_clock_time_point when = GVR_CHECK(gvr_get_time_point_now());
  // 50ms into the future is what GVR docs recommends using for head rotation prediction.
  when.monotonic_system_time_nanos += 50000000;
  m.gvrHeadMatrix = GVR_CHECK(gvr_get_head_space_from_start_space_transform(m.gvr, when));
  if (!m.sixDofHead) {
    m.gvrHeadMatrix = GVR_CHECK(gvr_apply_neck_model(m.gvr, m.gvrHeadMatrix, 1.0));
  }
  m.headMatrix = vrb::Matrix::FromRowMajor(m.gvrHeadMatrix.m);
  m.headMatrix = m.headMatrix.Inverse();
  if (m.renderMode == device::RenderMode::StandAlone) {
    if (m.recentered) {
      m.reorientMatrix = DeviceUtils::CalculateReorientationMatrix(m.headMatrix, kAverageHeight);
    }
    m.headMatrix.TranslateInPlace(kAverageHeight);
  }
  m.UpdateCameras();
  m.recentered = false;

  if (!m.lastSubmitDiscarded) {
    m.frame = GVR_CHECK(gvr_swap_chain_acquire_frame(m.swapChain));
    if (!m.frame) {
      // Sometimes the swap chain seems to not initialized correctly so that
      // frames can not be acquired. Recreating the swap chain seems to fix the
      // issue.
      VRB_LOG("Unable to acquire GVR frame. Recreating swap chain.");
      m.CreateSwapChain();
      VRB_GL_CHECK(glEnable(GL_BLEND));
    }
  }

  GVR_CHECK(gvr_frame_bind_buffer(m.frame, 0));
  VRB_GL_CHECK(glClearColor(m.clearColor.Red(), m.clearColor.Green(), m.clearColor.Blue(), m.clearColor.Alpha()));
  VRB_GL_CHECK(glEnable(GL_BLEND));
}

static void
SetUpViewportAndScissor(const gvr_sizei& framebuf_size,
                        const gvr_buffer_viewport* params) {
  const gvr::Rectf& rect = gvr_buffer_viewport_get_source_uv(params);
  int left = static_cast<int>(rect.left * framebuf_size.width);
  int bottom = static_cast<int>(rect.bottom * framebuf_size.width);
  int width = static_cast<int>((rect.right - rect.left) * framebuf_size.width);
  int height = static_cast<int>((rect.top - rect.bottom) * framebuf_size.height);
  VRB_GL_CHECK(glViewport(left, bottom, width, height));
  VRB_GL_CHECK(glEnable(GL_SCISSOR_TEST));
  VRB_GL_CHECK(glScissor(left, bottom, width, height));
  VRB_GL_CHECK(glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT));
}

void
DeviceDelegateGoogleVR::BindEye(const device::Eye aWhich) {
  if (aWhich == device::Eye::Left) {
    SetUpViewportAndScissor(m.frameBufferSize, m.leftViewport);
  } else if (aWhich == device::Eye::Right) {
    SetUpViewportAndScissor(m.frameBufferSize, m.rightViewport);
  } else {
    VRB_LOG("Unable to bind unknown eye type");
  }
}

void
DeviceDelegateGoogleVR::EndFrame(const bool aDiscard) {
  if (!m.frame) {
    VRB_LOG("Unable to submit null frame");
  }
  GVR_CHECK(gvr_frame_unbind(m.frame));
  m.lastSubmitDiscarded = aDiscard;
  if (!aDiscard) {
    GVR_CHECK(gvr_frame_submit(&m.frame, m.viewportList, m.gvrHeadMatrix));
  }
}


void
DeviceDelegateGoogleVR::InitializeGL() {
  gvr_initialize_gl(m.gvr);
  m.maxRenderSize =  GVR_CHECK(gvr_get_maximum_effective_render_target_size(m.gvr));
  m.webvrSize = m.GetRecommendedImmersiveModeSize();
  m.CreateSwapChain();
  m.InitializeControllers();
  VRB_GL_CHECK(glEnable(GL_DEPTH_TEST));
  VRB_GL_CHECK(glEnable(GL_CULL_FACE));
  m.sixDofHead = GVR_CHECK(gvr_is_feature_supported(m.gvr, GVR_FEATURE_HEAD_POSE_6DOF));
  VRB_LOG("6DoF head tracking supported: %s", (m.sixDofHead ? "True" : "False"));
}

void
DeviceDelegateGoogleVR::Pause() {
  if (m.controllerContext) {
    VRB_LOG("Pause GVR controller");
    GVR_CHECK(gvr_controller_pause(m.controllerContext));
  }

  if (m.gvr) {
    VRB_LOG("Pause GVR tracking");
    GVR_CHECK(gvr_pause_tracking(m.gvr));
  }

  for (Controller& controller: m.controllers) {
    controller.enabled = false;
  }
}

void
DeviceDelegateGoogleVR::Resume() {
  if (m.gvr) {
    VRB_LOG("Resume GVR tracking");
    GVR_CHECK(gvr_refresh_viewer_profile(m.gvr));
    GVR_CHECK(gvr_resume_tracking(m.gvr));
  }
  if (m.controllerContext) {
    VRB_LOG("Resume GVR controller");
    GVR_CHECK(gvr_controller_resume(m.controllerContext));
  }
  m.reorientMatrix = vrb::Matrix::Identity();
}

DeviceDelegateGoogleVR::DeviceDelegateGoogleVR(State& aState) : m(aState) {}
DeviceDelegateGoogleVR::~DeviceDelegateGoogleVR() { m.Shutdown(); }

} // namespace crow
