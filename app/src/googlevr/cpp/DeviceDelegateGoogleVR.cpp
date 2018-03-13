/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "DeviceDelegateGoogleVR.h"
#include "ElbowModel.h"
#include "GestureDelegate.h"

#include "vrb/CameraEye.h"
#include "vrb/Color.h"
#include "vrb/ConcreteClass.h"
#include "vrb/GLError.h"
#include "vrb/Matrix.h"
#include "vrb/Quaternion.h"
#include "vrb/Vector.h"

#include "vr/gvr/capi/include/gvr.h"
#include "vr/gvr/capi/include/gvr_controller.h"
#include "vr/gvr/capi/include/gvr_gesture.h"

#include <vector>

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
  gvr_buffer_viewport_list* viewportList;
  gvr_buffer_viewport* leftViewport;
  gvr_buffer_viewport* rightViewport;
  gvr_mat4f gvrHeadMatrix;
  vrb::Matrix headMatrix;
  gvr_swap_chain* swapChain;
  gvr_frame* frame;
  gvr_sizei frameBufferSize;
  vrb::ContextWeak context;
  float near;
  float far;
  vrb::Color clearColor;
  vrb::CameraEyePtr cameras[2];
  vrb::Matrix controller;
  bool clicked;
  ElbowModel::HandEnum hand;
  ElbowModelPtr elbow;
  GestureDelegatePtr gestures;
  State()
      : gvr(nullptr)
      , controllerContext(nullptr)
      , controllerState(nullptr)
      , gestureContext(nullptr)
      , viewportList(nullptr)
      , leftViewport(nullptr)
      , rightViewport(nullptr)
      , swapChain(nullptr)
      , frame(nullptr)
      , near(0.1f)
      , far(100.f)
      , clicked(false)
      , controller(vrb::Matrix::Identity())
      , hand(ElbowModel::HandEnum::Right)
  {
    frameBufferSize = {0,0};
    gestures = GestureDelegate::Create();
  }

  gvr_context* GetContext() { return gvr; }

  int32_t cameraIndex(CameraEnum aWhich) {
    if (CameraEnum::Left == aWhich) { return 0; }
    else if (CameraEnum::Right == aWhich) { return 1; }
    return -1;
  }

  void Initialize() {
    cameras[cameraIndex(CameraEnum::Left)] = vrb::CameraEye::Create(context);
    cameras[cameraIndex(CameraEnum::Right)] = vrb::CameraEye::Create(context);
    elbow = ElbowModel::Create(ElbowModel::HandEnum::Right);
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
      hand = ((gvr_user_prefs_get_controller_handedness(prefs) == GVR_CONTROLLER_RIGHT_HANDED) ?
              ElbowModel::HandEnum::Right : ElbowModel::HandEnum::Left);
    }
    elbow = ElbowModel::Create(hand);
    gestureContext = gvr_gesture_context_create();
  }

  void Shutdown() {
  }

  void
  CreateSwapChain()  {
    if (swapChain) {
      // gvr_swap_chain_destroy will set the pointer to null
      GVR_CHECK(gvr_swap_chain_destroy(&swapChain));
    }
    gvr_buffer_spec* spec = GVR_CHECK(gvr_buffer_spec_create(gvr));
    gvr_sizei size = GVR_CHECK(gvr_get_maximum_effective_render_target_size(gvr));
    GVR_CHECK(gvr_buffer_spec_set_size(spec, size));
    GVR_CHECK(gvr_buffer_spec_set_samples(spec, 4));
    GVR_CHECK(gvr_buffer_spec_set_color_format(spec, GVR_COLOR_FORMAT_RGBA_8888));
    GVR_CHECK(gvr_buffer_spec_set_depth_stencil_format(spec, GVR_DEPTH_STENCIL_FORMAT_DEPTH_16));
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
  }

  void
  UpdateCameras() {
    for (uint32_t eyeIndex = 0; eyeIndex < 2; eyeIndex++) {
      gvr_mat4f eye = GVR_CHECK(gvr_get_eye_from_head_matrix(gvr, eyeIndex));
      cameras[eyeIndex]->SetEyeTransform(vrb::Matrix::Translation(vrb::Vector(-eye.m[0][3], -eye.m[1][3], -eye.m[2][3])));
      cameras[eyeIndex]->SetHeadTransform(headMatrix);
    }
    if (!viewportList || !leftViewport || !rightViewport) {
      VRB_LOG("ERROR: view port lists not created");
      return;
    }
    GVR_CHECK(gvr_get_recommended_buffer_viewports(gvr, viewportList));
    GVR_CHECK(gvr_buffer_viewport_list_get_item(viewportList, 0, leftViewport));
    GVR_CHECK(gvr_buffer_viewport_list_get_item(viewportList, 1, rightViewport));

    gvr_rectf fov = GVR_CHECK(gvr_buffer_viewport_get_source_fov(leftViewport));
    cameras[cameraIndex(CameraEnum::Left)]->SetPerspective(
        vrb::Matrix::PerspectiveMatrixFromDegrees(fov.left, fov.right, fov.top, fov.bottom, near, far));
    //VRB_LOG("FOV:L top:%f right:%f bottom:%f left:%f", fov.top, fov.right, fov.bottom, fov.left);

    fov = GVR_CHECK(gvr_buffer_viewport_get_source_fov(rightViewport));
    cameras[cameraIndex(CameraEnum::Right)]->SetPerspective(
        vrb::Matrix::PerspectiveMatrixFromDegrees(fov.left, fov.right, fov.top, fov.bottom, near, far));
    //VRB_LOG("FOV:R top:%f right:%f bottom:%f left:%f",fov.top, fov.right, fov.bottom, fov.left);
  }

  void
  UpdateControllers() {
    GVR_CHECK(gvr_controller_state_update(controllerContext, 0, controllerState));
    if (gvr_controller_state_get_connection_state(controllerState) != GVR_CONTROLLER_CONNECTED) {
      VRB_LOG("Controller not connected.");
      return;
    }
    gvr_quatf ori = gvr_controller_state_get_orientation(controllerState);
    vrb::Quaternion quat(ori.qx, ori.qy, ori.qz, ori.qw);
    controller = vrb::Matrix::Rotation(vrb::Quaternion(ori.qx, ori.qy, ori.qz, ori.qw));
    if (elbow) {
      controller = elbow->GetTransform(headMatrix, controller);
    }

    // Index 0 is the dummy button so skip it.
    for (int ix = 1; ix < GVR_CONTROLLER_BUTTON_COUNT; ix++) {
      const uint64_t buttonMask = (uint64_t)0x01 << (ix - 1);
      bool pressed = gvr_controller_state_get_button_state(controllerState, ix);
      bool touched = pressed;
      if (ix == GVR_CONTROLLER_BUTTON_CLICK) {
        touched = gvr_controller_state_is_touching(controllerState);
        double xAxis = 0.0;
        double yAxis = 0.0;
        if (touched) {
          gvr_vec2f axes = gvr_controller_state_get_touch_pos(controllerState);
        }
        clicked = pressed;
      }
      if (pressed) {
      }
      if (touched) {
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
          // Handle an update in a sequence of scroll gestures.
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
DeviceDelegateGoogleVR::Create(vrb::ContextWeak aContext, void* aGVRContext) {
  DeviceDelegateGoogleVRPtr result = std::make_shared<vrb::ConcreteClass<DeviceDelegateGoogleVR, DeviceDelegateGoogleVR::State> >();
  result->m.context = aContext;
  result->m.gvr = (gvr_context*)aGVRContext;
  result->m.Initialize();
  return result;
}

GestureDelegateConstPtr
DeviceDelegateGoogleVR::GetGestureDelegate() {
  return m.gestures;
}
vrb::CameraPtr
DeviceDelegateGoogleVR::GetCamera(const CameraEnum aWhich) {
  const int32_t index = m.cameraIndex(aWhich);
  if (index < 0) { return nullptr; }
  return m.cameras[index];
}

const vrb::Matrix&
DeviceDelegateGoogleVR::GetHeadTransform() const {
  return m.cameras[0]->GetHeadTransform();
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

int32_t
DeviceDelegateGoogleVR::GetControllerCount() const {
  return 1;
}

const std::string
DeviceDelegateGoogleVR::GetControllerModelName(const int32_t) const {
  static const std::string name("vr_controller_daydream.obj");
  return name;
}

void
DeviceDelegateGoogleVR::ProcessEvents() {
  static const vrb::Vector kAverageHeight(0.0f, 1.7f, 0.0f);
  gvr_clock_time_point when = GVR_CHECK(gvr_get_time_point_now());
  // 50ms into the future is what GVR docs recommends using for head rotation prediction.
  when.monotonic_system_time_nanos += 50000000;
  m.gvrHeadMatrix = GVR_CHECK(gvr_get_head_space_from_start_space_rotation(m.gvr, when));
  m.gvrHeadMatrix = GVR_CHECK(gvr_apply_neck_model(m.gvr, m.gvrHeadMatrix, 1.0));
  m.headMatrix = vrb::Matrix::FromRowMajor(m.gvrHeadMatrix.m);
  m.headMatrix.TranslateInPlace(kAverageHeight);
  m.UpdateCameras();
  m.UpdateControllers();
}

const vrb::Matrix&
DeviceDelegateGoogleVR::GetControllerTransform(const int32_t aWhichController) {
  return m.controller;
}

bool
DeviceDelegateGoogleVR::GetControllerButtonState(const int32_t aWhichController, const int32_t aWhichButton, bool& aChangedState) {
  return m.clicked;
}

void
DeviceDelegateGoogleVR::StartFrame() {

  m.frame = GVR_CHECK(gvr_swap_chain_acquire_frame(m.swapChain));
  if (!m.frame) {
    // Sometimes the swap chain seems to not initialized correctly so that
    // frames can not be acquired. Recreating the swap chain seems to fix the
    // issue.
    VRB_LOG("Unable to acquire GVR frame. Recreating swap chain.");
    m.CreateSwapChain();
    m.frame = GVR_CHECK(gvr_swap_chain_acquire_frame(m.swapChain));
    if (!m.frame) {
      VRB_LOG("Unable to acquire GVR frame. Recreating swap chain failed.");
      return;
    }
  }

  GVR_CHECK(gvr_frame_bind_buffer(m.frame, 0));
  VRB_CHECK(glClearColor(m.clearColor.Red(), m.clearColor.Green(), m.clearColor.Blue(), m.clearColor.Alpha()));
  VRB_CHECK(glEnable(GL_BLEND));
}

static void
SetUpViewportAndScissor(const gvr_sizei& framebuf_size,
                        const gvr_buffer_viewport* params) {
  const gvr::Rectf& rect = gvr_buffer_viewport_get_source_uv(params);
  int left = static_cast<int>(rect.left * framebuf_size.width);
  int bottom = static_cast<int>(rect.bottom * framebuf_size.width);
  int width = static_cast<int>((rect.right - rect.left) * framebuf_size.width);
  int height = static_cast<int>((rect.top - rect.bottom) * framebuf_size.height);
  VRB_CHECK(glViewport(left, bottom, width, height));
  VRB_CHECK(glEnable(GL_SCISSOR_TEST));
  VRB_CHECK(glScissor(left, bottom, width, height));
  VRB_CHECK(glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT));
}

void
DeviceDelegateGoogleVR::BindEye(const CameraEnum aWhich) {
  if (aWhich == CameraEnum::Left) {
    SetUpViewportAndScissor(m.frameBufferSize, m.leftViewport);
  } else if (aWhich == CameraEnum::Right) {
    SetUpViewportAndScissor(m.frameBufferSize, m.rightViewport);
  } else {
    VRB_LOG("Unable to bind unknown eye type");
  }

}

void
DeviceDelegateGoogleVR::EndFrame() {
  if (!m.frame) {
    VRB_LOG("Unable to submit null frame");
  }
  GVR_CHECK(gvr_frame_unbind(m.frame));
  GVR_CHECK(gvr_frame_submit(&m.frame, m.viewportList, m.gvrHeadMatrix));
}

void
DeviceDelegateGoogleVR::SetGVRContext(void* aGVRContext) {

}

void
DeviceDelegateGoogleVR::InitializeGL() {
  gvr_initialize_gl(m.gvr);
  m.CreateSwapChain();
  m.InitializeControllers();
  VRB_CHECK(glEnable(GL_DEPTH_TEST));
  VRB_CHECK(glEnable(GL_CULL_FACE));
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
}

DeviceDelegateGoogleVR::DeviceDelegateGoogleVR(State& aState) : m(aState) {}
DeviceDelegateGoogleVR::~DeviceDelegateGoogleVR() { m.Shutdown(); }

} // namespace crow
