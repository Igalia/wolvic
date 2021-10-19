/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "BrowserWorld.h"
#include "Controller.h"
#include "ControllerContainer.h"
#include "FadeAnimation.h"
#include "Device.h"
#include "DeviceDelegate.h"
#include "ExternalBlitter.h"
#include "ExternalVR.h"
#include "GeckoSurfaceTexture.h"
#include "Skybox.h"
#include "SplashAnimation.h"
#include "Pointer.h"
#include "Widget.h"
#include "WidgetMover.h"
#include "WidgetResizer.h"
#include "WidgetPlacement.h"
#include "Cylinder.h"
#include "Quad.h"
#include "VRBrowser.h"
#include "VRVideo.h"
#include "VRLayer.h"
#include "VRLayerNode.h"
#include "vrb/CameraSimple.h"
#include "vrb/Color.h"
#include "vrb/ConcreteClass.h"
#include "vrb/CreationContext.h"
#include "vrb/CullVisitor.h"
#include "vrb/DataCache.h"
#include "vrb/DrawableList.h"
#include "vrb/Geometry.h"
#include "vrb/GLError.h"
#include "vrb/Group.h"
#include "vrb/Light.h"
#include "vrb/Logger.h"
#include "vrb/Matrix.h"
#include "vrb/ModelLoaderAndroid.h"
#include "vrb/NodeFactoryObj.h"
#include "vrb/ParserObj.h"
#include "vrb/PerformanceMonitor.h"
#include "vrb/ProgramFactory.h"
#include "vrb/RenderContext.h"
#include "vrb/RenderState.h"
#include "vrb/SurfaceTextureFactory.h"
#include "vrb/TextureCache.h"
#include "vrb/TextureSurface.h"
#include "vrb/TextureCubeMap.h"
#include "vrb/ThreadUtils.h"
#include "vrb/Toggle.h"
#include "vrb/Transform.h"
#include "vrb/VertexArray.h"
#include "vrb/Vector.h"

#include <array>
#include <functional>
#include <fstream>
#include <unordered_map>

#define ASSERT_ON_RENDER_THREAD(X)                                          \
  if (m.context && !m.context->IsOnRenderThread()) {                        \
    VRB_ERROR("Function: '%s' not called on render thread.", __FUNCTION__); \
    return X;                                                               \
  }


#define INJECT_SKYBOX_PATH "skybox"

using namespace vrb;

namespace {

const int GestureSwipeLeft = 0;
const int GestureSwipeRight = 1;

const float kScrollFactor = 20.0f; // Just picked what fell right.
const double kHoverRate = 1.0 / 10.0;

class SurfaceObserver;
typedef std::shared_ptr<SurfaceObserver> SurfaceObserverPtr;

class SurfaceObserver : public SurfaceTextureObserver {
public:
  explicit SurfaceObserver(crow::BrowserWorldWeakPtr& aWorld);
  ~SurfaceObserver() override = default;
  void SurfaceTextureCreated(const std::string& aName, GLuint aHandle, jobject aSurfaceTexture) override;
  void SurfaceTextureHandleUpdated(const std::string aName, GLuint aHandle) override;
  void SurfaceTextureDestroyed(const std::string& aName) override;
  void SurfaceTextureCreationError(const std::string& aName, const std::string& aReason) override;

protected:
  crow::BrowserWorldWeakPtr mWorld;
};

SurfaceObserver::SurfaceObserver(crow::BrowserWorldWeakPtr& aWorld) : mWorld(aWorld) {}

void
SurfaceObserver::SurfaceTextureCreated(const std::string& aName, GLuint, jobject aSurfaceTexture) {
  crow::BrowserWorldPtr world = mWorld.lock();
  if (world) {
    world->SetSurfaceTexture(aName, aSurfaceTexture);
  }
}

void
SurfaceObserver::SurfaceTextureHandleUpdated(const std::string, GLuint) {}

void
SurfaceObserver::SurfaceTextureDestroyed(const std::string& aName) {
  crow::BrowserWorldPtr world = mWorld.lock();
  if (world) {
    jobject nullObject = nullptr;
    world->SetSurfaceTexture(aName, nullObject);
  }
}

void
SurfaceObserver::SurfaceTextureCreationError(const std::string& aName, const std::string& aReason) {
  
}

class PerformanceObserver;
typedef std::shared_ptr<PerformanceObserver> PerformanceObserverPtr;

class PerformanceObserver : public PerformanceMonitorObserver {
public:
  void PoorPerformanceDetected(const double& aTargetFrameRate, const double& aAverageFrameRate) override;
  void PerformanceRestored(const double& aTargetFrameRate, const double& aAverageFrameRate) override;
};

void
PerformanceObserver::PoorPerformanceDetected(const double& aTargetFrameRate, const double& aAverageFrameRate)  {
  crow::VRBrowser::HandlePoorPerformance();
}

void
PerformanceObserver::PerformanceRestored(const double& aTargetFrameRate, const double& aAverageFrameRate)  {

}

} // namespace

namespace crow {
struct BrowserWorld::State {
  BrowserWorldWeakPtr self;
  std::vector<WidgetPtr> widgets;
  SurfaceObserverPtr surfaceObserver;
  DeviceDelegatePtr device;
  bool paused;
  bool glInitialized;
  bool modelsLoaded;
  RenderContextPtr context;
  CreationContextPtr create;
  ModelLoaderAndroidPtr loader;
  GroupPtr rootOpaqueParent;
  TransformPtr rootOpaque;
  TransformPtr rootTransparent;
  TransformPtr rootWebXRInterstitial;
  TransformPtr rootEnvironment;
  VRLayerProjectionPtr layerEnvironment;
  GroupPtr rootController;
  LightPtr light;
  ControllerContainerPtr controllers;
  CullVisitorPtr cullVisitor;
  DrawableListPtr drawList;
  CameraPtr leftCamera;
  CameraPtr rightCamera;
  float cylinderDensity;
  float nearClip;
  float farClip;
  JNIEnv* env;
  jobject activity;
  GestureDelegateConstPtr gestures;
  ExternalVRPtr externalVR;
  ExternalBlitterPtr blitter;
  bool windowsInitialized;
  SkyboxPtr skybox;
  FadeAnimationPtr fadeAnimation;
  uint32_t loaderDelay;
  bool exitImmersiveRequested;
  WidgetPtr resizingWidget;
  SplashAnimationPtr splashAnimation;
  VRVideoPtr vrVideo;
  PerformanceMonitorPtr monitor;
  WidgetMoverPtr movingWidget;
  WidgetResizerPtr widgetResizer;
  std::unordered_map<vrb::Node*, std::pair<Widget*, float>> depthSorting;
  std::function<void(device::Eye)> drawHandler;
  std::function<void()> frameEndHandler;
  bool wasInGazeMode = false;
  WebXRInterstialState webXRInterstialState;
  vrb::Matrix widgetsYaw;
  bool wasWebXRRendering = false;
  double lastBatteryLevelUpdate = -1.0;

  State() : paused(true), glInitialized(false), modelsLoaded(false), env(nullptr), cylinderDensity(0.0f), nearClip(0.1f),
            farClip(300.0f), activity(nullptr), windowsInitialized(false), exitImmersiveRequested(false), loaderDelay(0) {
    context = RenderContext::Create();
    create = context->GetRenderThreadCreationContext();
    loader = ModelLoaderAndroid::Create(context);
    context->GetProgramFactory()->SetLoaderThread(loader);
    rootOpaque = Transform::Create(create);
    rootTransparent = Transform::Create(create);
    rootController = Group::Create(create);
    light = Light::Create(create);
    rootOpaqueParent = Group::Create(create);
    rootOpaqueParent->AddNode(rootOpaque);
    rootWebXRInterstitial = Transform::Create(create);
    //rootOpaque->AddLight(light);
    //rootTransparent->AddLight(light);
    cullVisitor = CullVisitor::Create(create);
    drawList = DrawableList::Create(create);
    controllers = ControllerContainer::Create(create, rootTransparent, loader);
    externalVR = ExternalVR::Create();
    blitter = ExternalBlitter::Create(create);
    fadeAnimation = FadeAnimation::Create(create);
    splashAnimation = SplashAnimation::Create(create);
    monitor = PerformanceMonitor::Create(create);
    monitor->AddPerformanceMonitorObserver(std::make_shared<PerformanceObserver>());
    wasInGazeMode = false;
    webXRInterstialState = WebXRInterstialState::FORCED;
    widgetsYaw = vrb::Matrix::Identity();
#if defined(WAVEVR)
    monitor->SetPerformanceDelta(15.0);
#endif
  }

  void CheckBackButton();
  bool CheckExitImmersive();
  void EnsureControllerFocused();
  void ChangeControllerFocus(const Controller& aController);
  void UpdateGazeModeState();
  void UpdateControllers(bool& aRelayoutWidgets);
  void ClearWebXRControllerData();
  WidgetPtr GetWidget(int32_t aHandle) const;
  WidgetPtr FindWidget(const std::function<bool(const WidgetPtr&)>& aCondition) const;
  bool IsParent(const Widget& aChild, const Widget& aParent) const;
  int ParentCount(const WidgetPtr& aWidget) const;
  float ComputeNormalizedZ(const Widget& aWidget) const;
  void SortWidgets();
  void UpdateWidgetCylinder(const WidgetPtr& aWidget, const float aDensity);
};

void
BrowserWorld::State::CheckBackButton() {
  for (Controller& controller: controllers->GetControllers()) {
      if (!controller.enabled || (controller.index < 0)) {
          continue;
      }

      if (!(controller.lastButtonState & ControllerDelegate::BUTTON_APP) &&
          (controller.buttonState & ControllerDelegate::BUTTON_APP)) {
          VRBrowser::HandleBack();
          webXRInterstialState = WebXRInterstialState::HIDDEN;
      } else if (webXRInterstialState == WebXRInterstialState::ALLOW_DISMISS
                 && controller.lastButtonState && controller.buttonState == 0) {
          VRBrowser::OnDismissWebXRInterstitial();
          webXRInterstialState = WebXRInterstialState::HIDDEN;
      }
      controller.lastButtonState = controller.buttonState;
  }
}

bool
BrowserWorld::State::CheckExitImmersive() {
  if (exitImmersiveRequested && externalVR->IsPresenting()) {
    webXRInterstialState = WebXRInterstialState::HIDDEN;
    if (wasWebXRRendering) {
      VRBrowser::OnWebXRRenderStateChange(false);
      wasWebXRRendering = false;
    }
    externalVR->StopPresenting();
    blitter->StopPresenting();
    exitImmersiveRequested = false;
    return true;
  }
  return false;
}

static bool
OutOfDeadZone(Controller& aController, const float aX, const float aY) {
  if (!aController.inDeadZone) {
    return true;
  }
  const float kDeadZone = 20.0f;
  const float xDistance = aX - aController.pointerX;
  const float yDistance = aY - aController.pointerY;
  aController.inDeadZone = sqrtf((xDistance * xDistance) + (yDistance * yDistance)) < kDeadZone;

  // VRB_ERROR("Out of DEAD ZONE[%f]: %s", sqrtf((xDistance * xDistance) + (yDistance * yDistance)), (!aController.inDeadZone ? "TRUE" : "FALSE"));

  return !aController.inDeadZone;
}

static bool
ThrottleHoverEvent(Controller& aController, const double aTimestamp, const bool aIsPressed, const bool aWasPressed) {
  if (aIsPressed || aWasPressed) {
    return false;
  }

  if ((aTimestamp - aController.lastHoverEvent) < kHoverRate) {
    return true;
  }

  aController.lastHoverEvent = aTimestamp;
  return false;
}

void
BrowserWorld::State::EnsureControllerFocused() {
  Controller* right = nullptr;
  Controller* first = nullptr;
  bool focused = false;
  for (Controller& controller: controllers->GetControllers()) {
    focused = controller.focused;
    if (focused) {
      break;
    }

    if (!controller.leftHanded && controller.enabled) {
      right = &controller;
    }

    if (!first && controller.enabled) {
      first = &controller;
    }
  }
  if (!focused) {
    if (right) {
      ChangeControllerFocus(*right);
    } else if (first) {
      ChangeControllerFocus(*first);
    }
  }
}

void
BrowserWorld::State::ChangeControllerFocus(const Controller& aController) {
  controllers->SetFocused(aController.index);
}

static inline float
ScaleScrollDelta(const float aValue, const double aStartTime, const double aCurrentTime) {
  const float kMaxDelta = 2.0f; // in seconds
  const float kMinScale = 0.15f;
  const double deltaTime = aCurrentTime - aStartTime;
  auto scale = float(deltaTime / kMaxDelta);
  if (deltaTime > kMaxDelta) {
    scale = 1.0f;
  } else if (scale < kMinScale) {
    scale = kMinScale;
  }
  return aValue * scale;
}

void
BrowserWorld::State::UpdateGazeModeState() {
  bool isInGazeMode = device->IsInGazeMode();
  if (isInGazeMode != wasInGazeMode) {
    int32_t gazeIndex = device->GazeModeIndex();
    if (isInGazeMode && gazeIndex >= 0) {
      VRB_LOG("Gaze mode ON")
      controllers->SetEnabled(gazeIndex, true);

    } else {
      VRB_LOG("Gaze mode OFF")
      controllers->SetEnabled(gazeIndex, false);
    }
    wasInGazeMode = isInGazeMode;
  }
}

void
BrowserWorld::State::UpdateControllers(bool& aRelayoutWidgets) {
  EnsureControllerFocused();
  int leftBatteryLevel = -1;
  int rightBatteryLevel = -1;
  for (Controller& controller: controllers->GetControllers()) {
    if (!controller.enabled || (controller.index < 0)) {
      continue;
    }
    if (controller.index != device->GazeModeIndex()) {
      if (controller.leftHanded) {
        leftBatteryLevel = controller.batteryLevel;
      } else {
        rightBatteryLevel = controller.batteryLevel;
      }
    }
    if (controller.pointer && !controller.pointer->IsLoaded()) {
      controller.pointer->Load(device);
    }

    if (!(controller.lastButtonState & ControllerDelegate::BUTTON_APP) && (controller.buttonState & ControllerDelegate::BUTTON_APP)) {
      VRBrowser::HandleBack();
    }


    const bool pressed = controller.buttonState & ControllerDelegate::BUTTON_TRIGGER ||
                         controller.buttonState & ControllerDelegate::BUTTON_TOUCHPAD;
    const bool wasPressed = controller.lastButtonState & ControllerDelegate::BUTTON_TRIGGER ||
                            controller.lastButtonState & ControllerDelegate::BUTTON_TOUCHPAD;

    if (!controller.focused) {
      const bool focusRequested =
          (pressed && !wasPressed) ||
              ((controller.buttonState & ControllerDelegate::BUTTON_A) && (controller.lastButtonState & ControllerDelegate::BUTTON_A) == 0) ||
              ((controller.buttonState & ControllerDelegate::BUTTON_B) && (controller.lastButtonState & ControllerDelegate::BUTTON_B) == 0) ||
              ((controller.buttonState & ControllerDelegate::BUTTON_X) && (controller.lastButtonState & ControllerDelegate::BUTTON_X) == 0) ||
              ((controller.buttonState & ControllerDelegate::BUTTON_Y) && (controller.lastButtonState & ControllerDelegate::BUTTON_Y) == 0);
      if (focusRequested) {
        ChangeControllerFocus(controller);
      }
    }

    const vrb::Vector start = controller.StartPoint();
    const vrb::Vector direction = controller.Direction();

    WidgetPtr hitWidget;
    float hitDistance = farClip;
    vrb::Vector hitPoint;
    vrb::Vector hitNormal;

    bool isResizing = resizingWidget && resizingWidget->IsResizingActive();
    WidgetPtr previousWidget = controller.widget ? GetWidget(controller.widget) : nullptr;
    bool isDragging = pressed && wasPressed && previousWidget && !isResizing;
    if (isDragging) {
      vrb::Vector result;
      vrb::Vector normal;
      float distance = 0.0f;
      bool isInWidget = false;
      if (previousWidget->TestControllerIntersection(start, direction, result, normal, false,
                                                     isInWidget, distance)) {
        hitWidget = previousWidget;
        hitPoint = result;
        hitNormal = normal;
      }
    } else if (controllers->IsVisible()){
      for (const WidgetPtr& widget: widgets) {
        if (controller.focused) {
          if (isResizing && resizingWidget != widget) {
            // Don't interact with other widgets when resizing gesture is active.
            continue;
          }
          if (movingWidget && movingWidget->GetWidget() != widget) {
            // Don't interact with other widgets when moving gesture is active.
            continue;
          }
        }
        vrb::Vector result;
        vrb::Vector normal;
        float distance = 0.0f;
        bool isInWidget = false;
        const bool clamp = !widget->IsResizing() && !movingWidget;
        if (widget->TestControllerIntersection(start, direction, result, normal, clamp, isInWidget, distance)) {
          if (isInWidget && (distance < hitDistance)) {
            hitWidget = widget;
            hitDistance = distance;
            hitPoint = result;
            hitNormal = normal;
          }
        }
      }
    }

    if (controller.focused && (!hitWidget || !hitWidget->IsResizing()) && resizingWidget) {
      resizingWidget->HoverExitResize();
      resizingWidget.reset();
    }

    if (controller.pointer) {
      controller.pointer->SetVisible(hitWidget.get() != nullptr);
      controller.pointer->SetHitWidget(hitWidget);
      if (hitWidget) {
        vrb::Matrix translation = vrb::Matrix::Translation(hitPoint);
        vrb::Matrix localRotation = vrb::Matrix::Rotation(hitNormal);
        vrb::Matrix reorient = rootTransparent->GetTransform();
        controller.pointer->SetTransform(reorient.AfineInverse().PostMultiply(translation).PostMultiply(localRotation));
        controller.pointer->SetScale(hitPoint, device->GetHeadTransform());
      }
    }

    if (controller.focused && movingWidget && movingWidget->IsMoving(controller.index)) {
      if (!pressed && wasPressed) {
        movingWidget->EndMoving();
      } else {
        WidgetPlacementPtr updatedPlacement = movingWidget->HandleMove(start, direction);
        if (updatedPlacement) {
          movingWidget->GetWidget()->SetPlacement(updatedPlacement);
          aRelayoutWidgets = true;
        }
      }
    } else if (controller.focused && hitWidget && hitWidget->IsResizing()) {
      bool aResized = false, aResizeEnded = false;
      hitWidget->HandleResize(hitPoint, pressed, aResized, aResizeEnded);

      resizingWidget = hitWidget;
      if (aResized) {
        aRelayoutWidgets = true;

        std::shared_ptr<BrowserWorld> world = self.lock();
        if (world) {
          world->LayoutWidget(hitWidget->GetHandle());
        }
      }

      if (aResizeEnded) {
        float width, height;
        hitWidget->GetWorldSize(width, height);
        VRBrowser::HandleResize(hitWidget->GetHandle(), width, height);
      }
    } else if (hitWidget) {
      float theX = 0.0f, theY = 0.0f;
      hitWidget->ConvertToWidgetCoordinates(hitPoint, theX, theY, !isDragging);
      const uint32_t handle = hitWidget->GetHandle();
      if (!pressed && wasPressed) {
        controller.inDeadZone = true;
      }
      controller.pointerWorldPoint = hitPoint;
      const bool moved = pressed ? OutOfDeadZone(controller, theX, theY)
          : (controller.pointerX != theX) || (controller.pointerY != theY);
      const bool throttled = ThrottleHoverEvent(controller, context->GetTimestamp(), pressed, wasPressed);

      if ((!throttled && moved) || (controller.widget != handle) || (pressed != wasPressed)) {
        controller.widget = handle;
        controller.pointerX = theX;
        controller.pointerY = theY;
        VRBrowser::HandleMotionEvent(handle, controller.index, jboolean(controller.focused), jboolean(pressed), controller.pointerX, controller.pointerY);
      }
      if ((controller.scrollDeltaX != 0.0f) || controller.scrollDeltaY != 0.0f) {
        if (controller.scrollStart < 0.0) {
          controller.scrollStart = context->GetTimestamp();
        }
        const double ctime = context->GetTimestamp();
        VRBrowser::HandleScrollEvent(controller.widget, controller.index,
                            ScaleScrollDelta(controller.scrollDeltaX, controller.scrollStart, ctime),
                            ScaleScrollDelta(controller.scrollDeltaY, controller.scrollStart, ctime));
        controller.scrollDeltaX = 0.0f;
        controller.scrollDeltaY = 0.0f;
      } else {
        controller.scrollStart = -1.0;
      }
      if (!pressed) {
        if (controller.touched) {
          if (!controller.wasTouched) {
            controller.wasTouched = controller.touched;
          } else {
            VRBrowser::HandleScrollEvent(controller.widget,
                                controller.index,
                                (controller.touchX - controller.lastTouchX) * kScrollFactor,
                                (controller.touchY - controller.lastTouchY) * kScrollFactor);
          }
          controller.lastTouchX = controller.touchX;
          controller.lastTouchY = controller.touchY;
        } else {
          controller.wasTouched = false;
          controller.lastTouchX = controller.lastTouchY = 0.0f;
        }
      }
    } else if (controller.widget) {
      VRBrowser::HandleMotionEvent(0, controller.index, jboolean(controller.focused), (jboolean) pressed, 0.0f, 0.0f);
      controller.widget = 0;

    } else if (wasPressed != pressed) {
      VRBrowser::HandleMotionEvent(0, controller.index, jboolean(controller.focused), (jboolean) pressed, 0.0f, 0.0f);
    } else if (vrVideo != nullptr) {
      const bool togglePressed = controller.buttonState & ControllerDelegate::BUTTON_X ||
                                 controller.buttonState & ControllerDelegate::BUTTON_A;
      const bool toggleWasPressed = controller.lastButtonState & ControllerDelegate::BUTTON_X ||
                                    controller.lastButtonState & ControllerDelegate::BUTTON_A;
      if (togglePressed != toggleWasPressed) {
        VRBrowser::HandleMotionEvent(0, controller.index, jboolean(controller.focused), (jboolean) togglePressed, 0.0f, 0.0f);
      }
    }
    controller.lastButtonState = controller.buttonState;
  }
  if ((context->GetTimestamp() - lastBatteryLevelUpdate) > 1.0) {
    VRBrowser::UpdateControllerBatteryLevels(leftBatteryLevel, rightBatteryLevel);
    lastBatteryLevelUpdate = context->GetTimestamp();
  }
  if (gestures) {
    const int32_t gestureCount = gestures->GetGestureCount();
    for (int32_t count = 0; count < gestureCount; count++) {
      const GestureType type = gestures->GetGestureType(count);
      jint javaType = -1;
      if (type == GestureType::SwipeLeft) {
        javaType = GestureSwipeLeft;
      } else if (type == GestureType::SwipeRight) {
        javaType = GestureSwipeRight;
      }
      if (javaType >= 0) {
        VRBrowser::HandleGesture(javaType);
      }
    }
  }
}

void
BrowserWorld::State::ClearWebXRControllerData() {
    for (Controller& controller: controllers->GetControllers()) {
        if (!controller.enabled || (controller.index < 0)) {
            continue;
        };
        controller.immersiveTouchedState = 0;
        controller.immersivePressedState = 0;
        controller.selectActionStartFrameId = 0;
        controller.selectActionStopFrameId = 0;
        controller.squeezeActionStartFrameId = 0;
        controller.squeezeActionStopFrameId = 0;
        for (int i = 0; i < controller.numAxes; ++i) {
            controller.immersiveAxes[i] = 0;
        }
    }
}

WidgetPtr
BrowserWorld::State::GetWidget(int32_t aHandle) const {
  return FindWidget([=](const WidgetPtr& aWidget){
    return aWidget->GetHandle() == aHandle;
  });
}

WidgetPtr
BrowserWorld::State::FindWidget(const std::function<bool(const WidgetPtr&)>& aCondition) const {
  for (const WidgetPtr & widget: widgets) {
    if (aCondition(widget)) {
      return widget;
    }
  }
  return {};
}

bool
BrowserWorld::State::IsParent(const Widget& aChild, const Widget& aParent) const {
  if (aChild.GetPlacement()->parentHandle == aParent.GetHandle()) {
    return true;
  }

  if (aChild.GetPlacement()->parentHandle > 0) {
    WidgetPtr next = GetWidget(aChild.GetPlacement()->parentHandle);
    if (next) {
      return IsParent(*next, aParent);
    }
  }

  return false;
}

int
BrowserWorld::State::ParentCount(const WidgetPtr& aWidget) const {
  int result = 0;
  WidgetPtr current = aWidget;
  while (current && current->GetPlacement()->parentHandle > 0) {
    current = GetWidget(current->GetPlacement()->parentHandle);
    if (current) {
      result++;
    }
  }
  return result;
}

float
BrowserWorld::State::ComputeNormalizedZ(const Widget& aWidget) const {
  const vrb::Vector headPosition = device->GetHeadTransform().GetTranslation();
  const vrb::Vector headDirection = device->GetHeadTransform().MultiplyDirection(vrb::Vector(0.0f, 0.0f, -1.0f));

  vrb::Vector hitPoint;
  vrb::Vector normal;
  bool inside = false;
  float distance;
  if (aWidget.GetQuad()) {
    aWidget.GetQuad()->TestIntersection(headPosition, headDirection, hitPoint, normal, true, inside, distance);
  } else if (aWidget.GetCylinder()) {
    aWidget.GetCylinder()->TestIntersection(headPosition, headDirection, hitPoint, normal, true, inside, distance);
  }

  const vrb::Matrix& projection = device->GetCamera(device::Eye::Left)->GetPerspective();
  const vrb::Matrix& view = device->GetCamera(device::Eye::Left)->GetView();
  vrb::Matrix viewProjection = projection.PostMultiply(view);

  vrb::Vector ndc = viewProjection.MultiplyPosition(hitPoint);

  return ndc.z();
}

void
BrowserWorld::State::SortWidgets() {
  depthSorting.clear();

  // Compute normalized z for each widget
  for (int i = 0; i < rootTransparent->GetNodeCount(); ++i) {
    vrb::NodePtr node = rootTransparent->GetNode(i);
    Widget * target = nullptr;
    float zDelta = 0.0f;
    for (const auto & widget: widgets) {
      if (widget->GetRoot() == node) {
        target = widget.get();
        break;
      }
    }
    if (!target) {
      for (Controller& controller: controllers->GetControllers()) {
        if (controller.pointer && controller.pointer->GetRoot() == node) {
          target = controller.pointer->GetHitWidget().get();
          zDelta = 0.02f;
          break;
        }
      }
    }

    if (!target && widgetResizer && widgetResizer->GetRoot() == node) {
      target = widgetResizer->GetWidget();
      zDelta = 0.01f;
    }

    if (!target || !target->IsVisible()) {
      depthSorting.emplace(node.get(), std::make_pair(target, 1.0f));
      continue;
    }

    const float z = ComputeNormalizedZ(*target) - zDelta;

    depthSorting.emplace(node.get(), std::make_pair(target, z));
  }

  // Sort nodes based on cached depth values
  rootTransparent->SortNodes([=](const NodePtr& a, const NodePtr& b) {
    auto da = depthSorting.find(a.get());
    auto db = depthSorting.find(b.get());
    Widget* wa = da->second.first;
    Widget* wb = db->second.first;

    // Parenting or layer priority sort
    if (wa && wb && wa->IsVisible() && wb->IsVisible()) {
      if (IsParent(*wa, *wb)) {
        return true;
      } else if (IsParent(*wb, *wa)) {
        return false;
      } else if (wa->GetPlacement()->layerPriority != wb->GetPlacement()->layerPriority) {
        return wa->GetPlacement()->layerPriority > wb->GetPlacement()->layerPriority;
      }
    }

    // Depth sort
    return da->second.second < db->second.second;
  });
}

void
BrowserWorld::State::UpdateWidgetCylinder(const WidgetPtr& aWidget, const float aDensity) {
  const bool useCylinder = aDensity > 0 && aWidget->GetPlacement()->cylinder;
  if (useCylinder && aWidget->GetCylinder()) {
    aWidget->SetCylinderDensity(aDensity);
  } else if (useCylinder && !aWidget->GetCylinder()) {
    VRLayerSurfacePtr moveLayer = aWidget->GetLayer();
    VRLayerCylinderPtr layer = device->CreateLayerCylinder(moveLayer);
    CylinderPtr cylinder = Cylinder::Create(create, layer);
    aWidget->SetCylinder(cylinder);
    aWidget->SetCylinderDensity(aDensity);
  } else if (aWidget->GetCylinder()) {
    float w = 0, h = 0;
    aWidget->GetWorldSize(w, h);
    VRLayerSurfacePtr moveLayer = aWidget->GetLayer();
    VRLayerQuadPtr layer = device->CreateLayerQuad(moveLayer);
    QuadPtr quad = Quad::Create(create, w, h, layer);
    aWidget->SetQuad(quad);
  }
}

static BrowserWorldPtr sWorldInstance;

BrowserWorld&
BrowserWorld::Instance() {
  if (!sWorldInstance) {
    sWorldInstance = Create();
  }
  return *sWorldInstance;
}

void
BrowserWorld::Destroy() {
  sWorldInstance = nullptr;
}

vrb::RenderContextPtr&
BrowserWorld::GetRenderContext() {
  return m.context;
}

void
BrowserWorld::RegisterDeviceDelegate(DeviceDelegatePtr aDelegate) {
  ASSERT_ON_RENDER_THREAD();
  DeviceDelegatePtr previousDevice = std::move(m.device);
  m.device = std::move(aDelegate);
  if (m.device) {
    m.device->RegisterImmersiveDisplay(m.externalVR);
    m.device->SetClearColor(vrb::Color(0.0f, 0.0f, 0.0f, 0.0f));
    m.leftCamera = m.device->GetCamera(device::Eye::Left);
    m.rightCamera = m.device->GetCamera(device::Eye::Right);
    ControllerDelegatePtr delegate = m.controllers;
    delegate->SetGazeModeIndex(m.device->GazeModeIndex());
    m.device->SetClipPlanes(m.nearClip, m.farClip);
    m.device->SetControllerDelegate(delegate);
    m.gestures = m.device->GetGestureDelegate();
  } else if (previousDevice) {
    m.leftCamera = m.rightCamera = nullptr;
    m.controllers->Reset();
    m.gestures = nullptr;
    previousDevice->ReleaseControllerDelegate();
  }
}

void
BrowserWorld::Pause() {
  ASSERT_ON_RENDER_THREAD();
  m.paused = true;
  m.externalVR->OnPause();
  m.monitor->Pause();
}

void
BrowserWorld::Resume() {
  ASSERT_ON_RENDER_THREAD();
  m.paused = false;
  m.externalVR->OnResume();
  m.monitor->Resume();
}

bool
BrowserWorld::IsPaused() const {
  ASSERT_ON_RENDER_THREAD(m.paused);
  return m.paused;
}

void
BrowserWorld::InitializeJava(JNIEnv* aEnv, jobject& aActivity, jobject& aAssetManager) {
  ASSERT_ON_RENDER_THREAD();
  VRB_LOG("BrowserWorld::InitializeJava");
  if (m.context) {
    m.context->InitializeJava(aEnv, aActivity, aAssetManager);
  }
  m.env = aEnv;
  if (!m.env) {
    return;
  }
  m.activity = m.env->NewGlobalRef(aActivity);
  if (!m.activity) {
    return;
  }
  jclass clazz = m.env->GetObjectClass(m.activity);
  if (!clazz) {
    return;
  }

  VRBrowser::InitializeJava(m.env, m.activity);
  GeckoSurfaceTexture::InitializeJava(m.env, m.activity);
  m.loader->InitializeJava(aEnv, aActivity, aAssetManager);
  VRBrowser::RegisterExternalContext((jlong)m.externalVR->GetSharedData());
  VRBrowser::SetDeviceType(m.device->GetDeviceType());

  if (!m.modelsLoaded) {
    m.device->OnControllersReady([this](){
      const int32_t modelCount = m.device->GetControllerModelCount();
      for (int32_t index = 0; index < modelCount; index++) {
        vrb::LoadTask task = m.device->GetControllerModelTask(index);
        if (task) {
          // If there is a task we set the task to lazy load the models when the controller is created
          // (In some platforms if the controller is not available the SDK doesn't return the model so
          // we need to do the model load right when the controller becomes available)
          m.controllers->SetControllerModelTask(index, task);
          m.controllers->LoadControllerModel(index);
        } else {
          const std::string fileName = m.device->GetControllerModelName(index);
          if (!fileName.empty()) {
            m.controllers->LoadControllerModel(index, m.loader, fileName);
          }
        }
      }
      m.controllers->InitializeBeam();
      m.controllers->SetPointerColor(vrb::Color(VRBrowser::GetPointerColor()));
      m.rootController->AddNode(m.controllers->GetRoot());
      if (m.device->IsControllerLightEnabled()) {
        m.rootController->AddLight(m.light);
      }
    });

    UpdateEnvironment();

    m.fadeAnimation->SetFadeChangeCallback([=](const vrb::Color& aTintColor) {
      if (m.skybox) {
        m.skybox->SetTintColor(aTintColor);
      }
    });
    m.modelsLoaded = true;
  }
  SetThreadName("VRB Render");
}

void
BrowserWorld::InitializeGL() {
  ASSERT_ON_RENDER_THREAD();
  VRB_LOG("BrowserWorld::InitializeGL");
  if (m.context) {
    if (!m.glInitialized) {
      m.glInitialized = m.context->InitializeGL();
      VRB_GL_CHECK(glEnable(GL_BLEND));
      VRB_GL_CHECK(glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA));
      VRB_GL_CHECK(glEnable(GL_DEPTH_TEST));
      VRB_GL_CHECK(glEnable(GL_CULL_FACE));
      if (!m.glInitialized) {
        return;
      }
      if (m.splashAnimation) {
        m.splashAnimation->Load(m.context, m.device);
      }
      // delay the m.loader->InitializeGL() call to fix some issues with Daydream activities
      m.loaderDelay = 3;
      SurfaceTextureFactoryPtr factory = m.context->GetSurfaceTextureFactory();
      for (WidgetPtr& widget: m.widgets) {
        const std::string name = widget->GetSurfaceTextureName();
        jobject surface = factory->LookupSurfaceTexture(name);
        if (surface) {
          SetSurfaceTexture(name, surface);
        }
      }
    }
  }
}

void
BrowserWorld::ShutdownJava() {
  ASSERT_ON_RENDER_THREAD();
  VRB_LOG("BrowserWorld::ShutdownJava");
  GeckoSurfaceTexture::ShutdownJava();
  VRBrowser::ShutdownJava();
  if (m.env) {
    m.env->DeleteGlobalRef(m.activity);
  }
  m.activity = nullptr;
  m.env = nullptr;
}

void
BrowserWorld::ShutdownGL() {
  ASSERT_ON_RENDER_THREAD();
  VRB_LOG("BrowserWorld::ShutdownGL");
  if (!m.glInitialized) {
    return;
  }
  if (m.loader) {
    m.loader->ShutdownGL();
  }
  if (m.context) {
    m.context->ShutdownGL();
  }
  m.glInitialized = false;
}

void
BrowserWorld::StartFrame() {
  ASSERT_ON_RENDER_THREAD();
  if (!m.device) {
    VRB_WARN("No device");
    return;
  }
  if (m.paused) {
    VRB_LOG("BrowserWorld Paused");
    return;
  }
  if (!m.glInitialized) {
    m.glInitialized = m.context->InitializeGL();
    if (!m.glInitialized) {
      VRB_LOG("Failed to initialize GL");
      return;
    }
  }
  if (m.loaderDelay > 0) {
    m.loaderDelay--;
    if (m.loaderDelay == 0) {
      m.loader->InitializeGL();
    }
  }

  m.device->ProcessEvents();
  m.context->Update();
  m.externalVR->PullBrowserState();
  m.externalVR->SetHapticState(m.controllers);

  const uint64_t frameId = m.externalVR->GetFrameId();
  m.controllers->SetFrameId(frameId);
  m.CheckExitImmersive();

  if (m.splashAnimation) {
    TickSplashAnimation();
  } else if (m.externalVR->IsPresenting()) {
    m.CheckBackButton();
    TickImmersive();
  } else {
    bool relayoutWidgets = false;
    m.UpdateGazeModeState();
    m.UpdateControllers(relayoutWidgets);
    if (relayoutWidgets) {
      UpdateVisibleWidgets();
    }
    TickWorld();
    m.externalVR->PushSystemState();
  }
}

void
BrowserWorld::EndFrame() {
  ASSERT_ON_RENDER_THREAD();

  if (m.frameEndHandler) {
    m.frameEndHandler();
    m.frameEndHandler = nullptr;
  } else {
    m.device->EndFrame();
  }
  m.drawHandler = nullptr;

  // Update the 3d audio engine with the most recent head rotation.
  const vrb::Matrix &head = m.device->GetHeadTransform();
  const vrb::Vector p = head.GetTranslation();
  const vrb::Quaternion q(head);
  VRBrowser::HandleAudioPose(q.x(), q.y(), q.z(), q.w(), p.x(), p.y(), p.z());
}

void
BrowserWorld::Draw(device::Eye aEye) {
  ASSERT_ON_RENDER_THREAD();
  if (m.drawHandler) {
    m.drawHandler(aEye);
  }
}

void
BrowserWorld::SetTemporaryFilePath(const std::string& aPath) {
  ASSERT_ON_RENDER_THREAD();
  VRB_LOG("Got temp path: %s", aPath.c_str());
  m.context->GetDataCache()->SetCachePath(aPath);
}

void
BrowserWorld::UpdateEnvironment() {
  ASSERT_ON_RENDER_THREAD();
  std::string skyboxPath = VRBrowser::GetActiveEnvironment();
  std::string extension = Skybox::ValidateCustomSkyboxAndFindFileExtension(skyboxPath);
  if (VRBrowser::isOverrideEnvPathEnabled()) {
    std::string storagePath = VRBrowser::GetStorageAbsolutePath(INJECT_SKYBOX_PATH);
    if (std::ifstream(storagePath)) {
      skyboxPath = storagePath;
      extension = Skybox::ValidateCustomSkyboxAndFindFileExtension(storagePath);
      if (!extension.empty()) {
        skyboxPath = storagePath;
        VRB_DEBUG("Found custom skybox file extension: %s", extension.c_str());
      } else {
        VRB_ERROR("Failed to find custom skybox files.");
      }
    } else {
      VRB_ERROR("Failed to find override skybox storage path %s", storagePath.c_str());
    }
  }

  VRB_LOG("Setting environment: %s", skyboxPath.c_str());
  CreateSkyBox(skyboxPath, extension);
}

void
BrowserWorld::UpdatePointerColor() {
  ASSERT_ON_RENDER_THREAD();
  int32_t color = VRBrowser::GetPointerColor();
  VRB_LOG("Setting pointer color to: %d:", color);

  if (m.controllers) {
    m.controllers->SetPointerColor(vrb::Color(color));
  }
}

void
BrowserWorld::SetSurfaceTexture(const std::string& aName, jobject& aSurface) {
  ASSERT_ON_RENDER_THREAD();
  VRB_LOG("SetSurfaceTexture: %s", aName.c_str());
  WidgetPtr widget = m.FindWidget([=](const WidgetPtr& aWidget) -> bool {
    return aName == aWidget->GetSurfaceTextureName();
  });
  if (widget) {
    int32_t width = 0, height = 0;
    widget->GetSurfaceTextureSize(width, height);
    VRBrowser::DispatchCreateWidget(widget->GetHandle(), aSurface, width, height);
  }
}

void
BrowserWorld::AddWidget(int32_t aHandle, const WidgetPlacementPtr& aPlacement) {
  ASSERT_ON_RENDER_THREAD();
  if (m.GetWidget(aHandle)) {
    VRB_LOG("Widget with handle %d already added, updating it.", aHandle);
    UpdateWidget(aHandle, aPlacement);
    return;
  }
  float worldWidth = aPlacement->worldWidth;
  if (worldWidth <= 0.0f) {
    worldWidth = aPlacement->width * WidgetPlacement::kWorldDPIRatio;
  }

  const int32_t textureWidth = aPlacement->GetTextureWidth();
  const int32_t textureHeight = aPlacement->GetTextureHeight();

  const float aspect = (float)textureWidth / (float)textureHeight;
  const float worldHeight = worldWidth / aspect;

  WidgetPtr widget;
  if (aPlacement->cylinder && m.cylinderDensity > 0) {
    VRLayerCylinderPtr layer = m.device->CreateLayerCylinder(textureWidth, textureHeight, VRLayerQuad::SurfaceType::AndroidSurface);
    CylinderPtr cylinder = Cylinder::Create(m.create, layer);
    widget = Widget::Create(m.context, aHandle, aPlacement, textureWidth, textureHeight, (int32_t)worldWidth, (int32_t)worldHeight, cylinder);
  }

  if (!widget) {
    VRLayerQuadPtr layer;
    if (aPlacement->layer) {
      layer = m.device->CreateLayerQuad(textureWidth, textureHeight, VRLayerQuad::SurfaceType::AndroidSurface);
    }

    QuadPtr quad = Quad::Create(m.create, worldWidth, worldHeight, layer);
    widget = Widget::Create(m.context, aHandle, aPlacement, textureWidth, textureHeight, quad);
  }

  switch (aPlacement->GetScene()) {
      case WidgetPlacement::Scene::ROOT_TRANSPARENT:
        m.rootTransparent->AddNode(widget->GetRoot());
        break;
      case WidgetPlacement::Scene::ROOT_OPAQUE:
        m.rootOpaque->AddNode(widget->GetRoot());
        break;
      case WidgetPlacement::Scene::WEBXR_INTERSTITIAL:
        m.rootWebXRInterstitial->AddNode(widget->GetRoot());
        break;
  }

  m.widgets.push_back(widget);
  UpdateWidget(widget->GetHandle(), aPlacement);
}

void
BrowserWorld::UpdateWidget(int32_t aHandle, const WidgetPlacementPtr& aPlacement) {
  ASSERT_ON_RENDER_THREAD();
  WidgetPtr widget = m.GetWidget(aHandle);
  if (!widget) {
      VRB_ERROR("Can't find Widget with handle: %d", aHandle);
      return;
  }

  int32_t oldWidth = 0;
  int32_t oldHeight = 0;
  if (widget->GetPlacement()) {
      oldWidth = widget->GetPlacement()->width;
      oldHeight = widget->GetPlacement()->height;
  }

  widget->SetPlacement(aPlacement);
  m.UpdateWidgetCylinder(widget, m.cylinderDensity);
  widget->ToggleWidget(aPlacement->visible);
  widget->SetSurfaceTextureSize(aPlacement->GetTextureWidth(), aPlacement->GetTextureHeight());

  float worldWidth = 0.0f, worldHeight = 0.0f;
  widget->GetWorldSize(worldWidth, worldHeight);

  float newWorldWidth = aPlacement->worldWidth;
  if (newWorldWidth <= 0.0f) {
    newWorldWidth = aPlacement->width * WidgetPlacement::kWorldDPIRatio;
  }

  if (newWorldWidth != worldWidth || oldWidth != aPlacement->width || oldHeight != aPlacement->height) {
    widget->SetWorldWidth(newWorldWidth);
  }

  widget->SetBorderColor(vrb::Color(aPlacement->borderColor));
  widget->SetProxifyLayer(aPlacement->proxifyLayer);
  LayoutWidget(aHandle);
}

void
BrowserWorld::UpdateWidgetRecursive(int32_t aHandle, const WidgetPlacementPtr& aPlacement) {
  UpdateWidget(aHandle, aPlacement);
  for (WidgetPtr& widget: m.widgets) {
    if (widget->GetPlacement() && widget->GetPlacement()->parentHandle == aHandle) {
      UpdateWidgetRecursive(widget->GetHandle(), widget->GetPlacement());
    }
  }
}

void
BrowserWorld::RemoveWidget(int32_t aHandle) {
  ASSERT_ON_RENDER_THREAD();
  WidgetPtr widget = m.GetWidget(aHandle);
  if (widget) {
    widget->ResetFirstDraw();
    widget->GetRoot()->RemoveFromParents();
    auto it = std::find(m.widgets.begin(), m.widgets.end(), widget);
    if (it != m.widgets.end()) {
      m.widgets.erase(it);
    }
    if (widget->GetLayer()) {
      m.device->DeleteLayer(widget->GetLayer());
    }
  }
}

void
BrowserWorld::StartWidgetResize(int32_t aHandle, const vrb::Vector& aMaxSize, const vrb::Vector& aMinSize) {
  ASSERT_ON_RENDER_THREAD();
  WidgetPtr widget = m.GetWidget(aHandle);
  if (widget) {
    m.widgetResizer = widget->StartResize(aMaxSize, aMinSize);
    m.rootTransparent->AddNode(m.widgetResizer->GetRoot());
  }
}

void
BrowserWorld::FinishWidgetResize(int32_t aHandle) {
  ASSERT_ON_RENDER_THREAD();
  WidgetPtr widget = m.GetWidget(aHandle);
  if (!widget) {
    return;
  }
  widget->FinishResize();
  if (m.widgetResizer) {
    m.widgetResizer->GetRoot()->RemoveFromParents();
    m.widgetResizer = nullptr;
  }
}

void
BrowserWorld::StartWidgetMove(int32_t aHandle, int32_t aMoveBehavour) {
  ASSERT_ON_RENDER_THREAD();
  WidgetPtr widget = m.GetWidget(aHandle);
  if (!widget) {
    return;
  }

  vrb::Vector anchorPoint;
  vrb::Vector start;
  vrb::Vector direction;
  int controllerIndex = -1;

  for (Controller& controller: m.controllers->GetControllers()) {
    if (!controller.enabled || (controller.index < 0)) {
      continue;
    }

    if (controller.pointer && controller.focused && controller.pointer->GetHitWidget() == widget) {
      controllerIndex = controller.index;
      start = controller.StartPoint();
      direction = controller.Direction();
      int32_t w, h;
      widget->GetSurfaceTextureSize(w, h);
      anchorPoint.x() = controller.pointerX / (float)w;
      anchorPoint.y() = controller.pointerY / (float)h;
      break;
    }
  }

  if (controllerIndex < 0) {
    return;
  }
  
  m.movingWidget = WidgetMover::Create();
  WidgetPtr parent;
  if (widget->GetPlacement()->parentHandle) {
    parent = m.GetWidget(widget->GetPlacement()->parentHandle);
  }
  m.movingWidget->StartMoving(widget, parent, aMoveBehavour, controllerIndex, start, direction, anchorPoint);
}

void
BrowserWorld::FinishWidgetMove() {
  ASSERT_ON_RENDER_THREAD();
  if (m.movingWidget) {
    m.movingWidget->EndMoving();
  }
  m.movingWidget = nullptr;
}

void
BrowserWorld::UpdateVisibleWidgets() {
  ASSERT_ON_RENDER_THREAD();

  std::vector<WidgetPtr> widgets = m.widgets;
  // Sort by parent before updating.
  std::sort(widgets.begin(), widgets.end(), [=](const WidgetPtr& a, const WidgetPtr& b) {
    int parentsA = m.ParentCount(a);
    int parentsB = m.ParentCount(b);
    if (parentsA != parentsB) {
      return parentsA < parentsB;
    }
    return m.IsParent(*b, *a);
  });

  for (const WidgetPtr& widget: widgets) {
    if (widget->IsVisible() && !widget->IsResizing()) {
      UpdateWidget(widget->GetHandle(), widget->GetPlacement());
    }
  }
}

void
BrowserWorld::LayoutWidget(int32_t aHandle) {
  WidgetPtr widget = m.GetWidget(aHandle);
  WidgetPlacementPtr aPlacement = widget->GetPlacement();

  WidgetPtr parent = m.GetWidget(aPlacement->parentHandle);

  int32_t parentWidth = 0, parentHeight = 0;
  float parentWorldWith = 0.0f, parentWorldHeight = 0.0f;

  if (parent) {
    parent->GetSurfaceTextureSize(parentWidth, parentHeight);
    parent->GetWorldSize(parentWorldWith, parentWorldHeight);
  }

  float worldWidth = 0.0f, worldHeight = 0.0f;
  widget->GetWorldSize(worldWidth, worldHeight);

  vrb::Matrix transform = vrb::Matrix::Identity();

  vrb::Vector translation = vrb::Vector(aPlacement->translation.x() * WidgetPlacement::kWorldDPIRatio,
                                        aPlacement->translation.y() * WidgetPlacement::kWorldDPIRatio,
                                        aPlacement->translation.z() * WidgetPlacement::kWorldDPIRatio);

  const float anchorX = (aPlacement->anchor.x() - 0.5f) * worldWidth;
  const float anchorY = (aPlacement->anchor.y() - 0.5f) * worldHeight;

  if (aPlacement->rotationAxis.Magnitude() > std::numeric_limits<float>::epsilon()) {
    transform = vrb::Matrix::Rotation(aPlacement->rotationAxis, aPlacement->rotation);
    // Rotate from anchor point
    transform.PreMultiplyInPlace(vrb::Matrix::Translation(vrb::Vector(anchorX, anchorY, 0.0f)));
    transform.PostMultiplyInPlace(vrb::Matrix::Translation(vrb::Vector(-anchorX, -anchorY, 0.0f)));
  }

  // Widget anchor point
  translation -= vrb::Vector(anchorX, anchorY, 0.0f);

  // Parent anchor point
  if (parent) {
    translation += vrb::Vector(
        parentWorldWith * aPlacement->parentAnchor.x() - parentWorldWith * 0.5f,
        parentWorldHeight * aPlacement->parentAnchor.y() - parentWorldHeight * 0.5f,
        0.0f);
  }

  transform.TranslateInPlace(translation);
  if (!parent) {
    translation = transform.GetTranslation();
  }
  widget->SetTransform(parent ? parent->GetTransform().PostMultiply(transform) : transform);

  if (!widget->GetCylinder()) {
    widget->LayoutQuadWithCylinderParent(parent);
  }
}

void
BrowserWorld::SetBrightness(const float aBrightness) {
  ASSERT_ON_RENDER_THREAD();
  m.fadeAnimation->SetBrightness(aBrightness);
}

void
BrowserWorld::ExitImmersive() {
  ASSERT_ON_RENDER_THREAD();
  m.exitImmersiveRequested = true;
}

void
BrowserWorld::ShowVRVideo(const int aWindowHandle, const int aVideoProjection) {
  WidgetPtr widget = m.GetWidget(aWindowHandle);
  if (!widget) {
    VRB_ERROR("Can't find Widget for VRVideo with handle: %d", aWindowHandle);
    return;
  }

  if (m.vrVideo) {
    m.vrVideo->Exit();
  }
  auto projection = static_cast<VRVideo::VRVideoProjection>(aVideoProjection);
  m.vrVideo = VRVideo::Create(m.create, widget, projection, m.device);
  if (m.skybox && projection != VRVideo::VRVideoProjection::VIDEO_PROJECTION_3D_SIDE_BY_SIDE) {
    m.skybox->SetVisible(false);
  }
  if (m.fadeAnimation && projection != VRVideo::VRVideoProjection::VIDEO_PROJECTION_3D_SIDE_BY_SIDE) {
    m.fadeAnimation->SetVisible(false);
  }
}

void
BrowserWorld::HideVRVideo() {
  if (m.vrVideo) {
    m.vrVideo->Exit();
  }
  m.vrVideo = nullptr;
  if (m.skybox) {
    m.skybox->SetVisible(true);
  }
  if (m.fadeAnimation) {
    m.fadeAnimation->SetVisible(true);
  }
}

void
BrowserWorld::SetControllersVisible(const bool aVisible) {
  m.controllers->SetVisible(aVisible);
}

void
BrowserWorld::RecenterUIYaw(const YawTarget aTarget) {
  vrb::Matrix head = m.device->GetHeadTransform();

  if (aTarget == YawTarget::ALL) {
    vrb::Vector vector = head.MultiplyDirection(vrb::Vector(1.0f, 0.0f, 0.0f));
    float yaw = atan2(vector.z(), vector.x());
    vrb::Matrix matrix = vrb::Matrix::Rotation(vrb::Vector(0.0f, 1.0f, 0.0f), -yaw);
    m.device->SetReorientTransform(matrix);
    m.widgetsYaw = vrb::Matrix::Identity();
  } else {
    vrb::Vector vector = m.device->GetReorientTransform().AfineInverse().PostMultiply(head).MultiplyDirection(vrb::Vector(1.0f, 0.0f, 0.0f));
    const float yaw = atan2(vector.z(), vector.x());
    m.widgetsYaw = vrb::Matrix::Rotation(vrb::Vector(0.0f, 1.0f, 0.0f), -yaw);
  }
}

void
BrowserWorld::SetCylinderDensity(const float aDensity) {
  m.cylinderDensity = aDensity;
  for (WidgetPtr& widget: m.widgets) {
    m.UpdateWidgetCylinder(widget, aDensity);
  }
}

void
BrowserWorld::SetCPULevel(const device::CPULevel aLevel) {
  m.device->SetCPULevel(aLevel);
}

void
BrowserWorld::SetWebXRInterstitalState(const WebXRInterstialState aState) {
  m.webXRInterstialState = aState;
}

void
BrowserWorld::SetIsServo(const bool aIsServo) {
  m.externalVR->SetSourceBrowser(aIsServo ? ExternalVR::VRBrowserType::Servo : ExternalVR::VRBrowserType::Gecko);
}

JNIEnv*
BrowserWorld::GetJNIEnv() const {
  ASSERT_ON_RENDER_THREAD(nullptr);
  return m.env;
}

BrowserWorldPtr
BrowserWorld::Create() {
  BrowserWorldPtr result = std::make_shared<vrb::ConcreteClass<BrowserWorld, BrowserWorld::State> >();
  result->m.self = result;
  result->m.surfaceObserver = std::make_shared<SurfaceObserver>(result->m.self);
  result->m.context->GetSurfaceTextureFactory()->AddGlobalObserver(result->m.surfaceObserver);
  return result;
}

BrowserWorld::BrowserWorld(State& aState) : m(aState) {}


void
BrowserWorld::TickWorld() {
  m.externalVR->SetCompositorEnabled(true);
  m.device->SetRenderMode(device::RenderMode::StandAlone);
  if (m.fadeAnimation) {
    m.fadeAnimation->UpdateAnimation();
  }
  const vrb::Vector headPosition = m.device->GetHeadTransform().GetTranslation();
  if (m.skybox) {
    m.skybox->SetTransform(vrb::Matrix::Translation(headPosition));
  }

  m.SortWidgets();
  m.device->StartFrame();
  m.rootOpaque->SetTransform(m.device->GetReorientTransform());
  m.rootTransparent->SetTransform(m.device->GetReorientTransform().PostMultiply(m.widgetsYaw));
  if (m.rootEnvironment) {
    m.rootEnvironment->SetTransform(m.device->GetReorientTransform());
  }
  if (m.vrVideo) {
    m.vrVideo->SetReorientTransform(m.device->GetReorientTransform());
  }

  m.drawHandler = [=](device::Eye aEye) {
    DrawWorld(aEye);
  };
}

void
BrowserWorld::DrawWorld(device::Eye aEye) {
  const CameraPtr camera = aEye == device::Eye::Left ? m.leftCamera : m.rightCamera;
  m.device->BindEye(aEye);

  // Draw skybox
  m.drawList->Reset();
  m.rootOpaqueParent->Cull(*m.cullVisitor, *m.drawList);
  m.drawList->Draw(*camera);

  // Draw environment if available
  if (m.layerEnvironment) {
    m.layerEnvironment->SetCurrentEye(aEye);
    m.layerEnvironment->Bind();
    VRB_GL_CHECK(glViewport(0, 0, m.layerEnvironment->GetWidth(), m.layerEnvironment->GetHeight()));
    VRB_GL_CHECK(glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT));

  }
  if (m.rootEnvironment) {
    m.drawList->Reset();
    m.rootEnvironment->Cull(*m.cullVisitor, *m.drawList);
    m.drawList->Draw(*camera);
  }
  if (m.layerEnvironment) {
    m.layerEnvironment->Unbind();
  }

  // Draw equirect video
  if (m.vrVideo) {
    m.vrVideo->SelectEye(aEye);
    m.drawList->Reset();
    m.vrVideo->GetRoot()->Cull(*m.cullVisitor, *m.drawList);
    m.drawList->Draw(*camera);
  }

  // Draw controllers
  m.drawList->Reset();
  m.rootController->Cull(*m.cullVisitor, *m.drawList);
  m.drawList->Draw(*camera);
  VRB_GL_CHECK(glDepthMask(GL_FALSE));
  m.drawList->Reset();
  m.rootTransparent->Cull(*m.cullVisitor, *m.drawList);
  m.drawList->Draw(*camera);
  VRB_GL_CHECK(glDepthMask(GL_TRUE));
}

void
BrowserWorld::TickImmersive() {
  m.externalVR->SetCompositorEnabled(false);
  m.device->SetRenderMode(device::RenderMode::Immersive);

  const bool supportsFrameAhead = m.device->SupportsFramePrediction(DeviceDelegate::FramePrediction::ONE_FRAME_AHEAD);
  auto framePrediction = DeviceDelegate::FramePrediction::ONE_FRAME_AHEAD;
  if (!supportsFrameAhead || (m.externalVR->GetVRState() != ExternalVR::VRState::Rendering) || m.webXRInterstialState != WebXRInterstialState::HIDDEN) {
      // Do not use one frame ahead prediction if not supported or we are rendering the spinner.
      framePrediction = DeviceDelegate::FramePrediction::NO_FRAME_AHEAD;
      m.device->StartFrame(framePrediction);
      if (m.webXRInterstialState != WebXRInterstialState::HIDDEN) {
          // Hide controller input until the interstitial is hidden.
          m.ClearWebXRControllerData();
      }
      m.externalVR->PushFramePoses(m.device->GetHeadTransform(), m.controllers->GetControllers(),
                                   m.context->GetTimestamp());
  }
  int32_t surfaceHandle, textureWidth, textureHeight = 0;
  device::EyeRect leftEye, rightEye;
  bool aDiscardFrame = !m.externalVR->WaitFrameResult();
  m.externalVR->GetFrameResult(surfaceHandle, textureWidth, textureHeight, leftEye, rightEye);
  ExternalVR::VRState state = m.externalVR->GetVRState();
  if (supportsFrameAhead) {
      if (framePrediction != DeviceDelegate::FramePrediction::ONE_FRAME_AHEAD) {
          // StartFrame() has been already called to render the spinner, do not call it again.
          // Instead, repeat the XR frame and render the spinner while we transition
          // to one frame ahead prediction.
          state = ExternalVR::VRState::Loading;
      } else {
          // Predict poses for one frame ahead and push the data to shmem so Gecko
          // can start the next XR RAF ASAP.
          m.device->StartFrame(framePrediction);
      }
      m.externalVR->PushFramePoses(m.device->GetHeadTransform(), m.controllers->GetControllers(),
              m.context->GetTimestamp());
  }
  if (state == ExternalVR::VRState::Rendering) {
    if (!aDiscardFrame) {
      if (textureWidth > 0 && textureHeight > 0) {
        m.device->SetImmersiveSize((uint32_t) textureWidth/2, (uint32_t) textureHeight);
      }
      m.blitter->StartFrame(surfaceHandle, leftEye, rightEye);
      if (m.webXRInterstialState != WebXRInterstialState::HIDDEN) {
        TickWebXRInterstitial();
      } else {
        if (!m.wasWebXRRendering) {
          // Disable Spinner animation in Java to avoid triggering superfluous Android Draw calls.
          VRBrowser::OnWebXRRenderStateChange(true);
          m.wasWebXRRendering = true;
        }
        m.drawHandler = [=](device::Eye aEye) {
            DrawImmersive(aEye);
        };
      }
    }
    m.frameEndHandler = [=]() {
      m.device->EndFrame(aDiscardFrame ? DeviceDelegate::FrameEndMode::DISCARD : DeviceDelegate::FrameEndMode::APPLY);
      m.blitter->EndFrame();
    };
  } else {
    if (surfaceHandle != 0) {
      m.blitter->CancelFrame(surfaceHandle);
    }
    TickWebXRInterstitial();
  }
}

void
BrowserWorld::DrawImmersive(device::Eye aEye) {
  m.device->BindEye(aEye);
  m.blitter->Draw(aEye);
}

void
BrowserWorld::TickWebXRInterstitial() {
  m.rootWebXRInterstitial->SetTransform(m.device->GetReorientTransform());
  m.drawHandler = [=](device::Eye eye) {
      DrawWebXRInterstitial(eye);
  };
}

void
BrowserWorld::DrawWebXRInterstitial(crow::device::Eye aEye) {
  const CameraPtr camera = aEye == device::Eye::Left ? m.leftCamera : m.rightCamera;
  m.device->BindEye(aEye);
  VRB_GL_CHECK(glDepthMask(GL_FALSE));
  m.drawList->Reset();
  m.rootWebXRInterstitial->Cull(*m.cullVisitor, *m.drawList);
  m.drawList->Draw(*camera);
  VRB_GL_CHECK(glDepthMask(GL_TRUE));
}

void
BrowserWorld::TickSplashAnimation() {
  if (!m.splashAnimation) {
    return;
  }
  m.device->StartFrame();
  const bool animationFinished = m.splashAnimation->Update(m.device->GetHeadTransform());
  m.drawHandler = [=](device::Eye aEye) {
    DrawSplashAnimation(aEye);
  };
  if (animationFinished) {
    m.frameEndHandler = [=]() {
      if (m.splashAnimation && m.splashAnimation->GetLayer()) {
        m.device->DeleteLayer(m.splashAnimation->GetLayer());
      }
      m.splashAnimation = nullptr;
      if (m.fadeAnimation) {
        m.fadeAnimation->FadeIn();
      }
      m.device->EndFrame();
    };
  }
}

void
BrowserWorld::DrawSplashAnimation(device::Eye aEye) {
  m.drawList->Reset();
  m.splashAnimation->GetRoot()->Cull(*m.cullVisitor, *m.drawList);

  m.device->BindEye(aEye);
  m.drawList->Draw(aEye == device::Eye::Left ? *m.leftCamera : *m.rightCamera);
}

void
BrowserWorld::CreateSkyBox(const std::string& aBasePath, const std::string& aExtension) {
  ASSERT_ON_RENDER_THREAD();
  vrb::PausePerformanceMonitor pauseMonitor(*m.monitor);
  const bool empty = aBasePath == "cubemap/void";
  if (empty) {
    if (m.skybox) {
      VRLayerCubePtr layer = m.skybox->GetLayer();
      if (layer) {
        m.device->DeleteLayer(layer);
      }
      m.skybox->GetRoot()->RemoveFromParents();
      m.skybox = nullptr;
    }
    return;
  }
  const std::string extension = aExtension.empty() ? ".ktx" : aExtension;
  const GLenum glFormat = extension == ".ktx" ? GL_COMPRESSED_RGB8_ETC2 : GL_RGBA8;
  const int32_t size = 1024;
  if (m.skybox) {
    m.skybox->SetVisible(true);
    if (m.skybox->GetLayer() && (m.skybox->GetLayer()->GetWidth() != size || m.skybox->GetLayer()->GetFormat() != glFormat)) {
      VRLayerCubePtr oldLayer = m.skybox->GetLayer();
      VRLayerCubePtr newLayer = m.device->CreateLayerCube(size, size, glFormat);
      m.skybox->SetLayer(newLayer);
      m.device->DeleteLayer(oldLayer);
    }
    m.skybox->Load(m.loader, aBasePath, extension);
  } else {
    VRLayerCubePtr layer = m.device->CreateLayerCube(size, size, glFormat);
    m.skybox = Skybox::Create(m.create, layer);
    m.rootOpaqueParent->AddNode(m.skybox->GetRoot());
    m.skybox->Load(m.loader, aBasePath, extension);
  }
}

void BrowserWorld::CreateEnvironment() {
  ASSERT_ON_RENDER_THREAD();
  m.rootEnvironment = Transform::Create(m.create);
  m.rootEnvironment->AddLight(Light::Create(m.create));

  vrb::TransformPtr model = Transform::Create(m.create);
  m.loader->LoadModel("FirefoxPlatform2_low.obj", model);
  m.rootEnvironment->AddNode(model);
  vrb::Matrix transform = vrb::Matrix::Identity();
  model->SetTransform(transform);

  m.layerEnvironment = m.device->CreateLayerProjection(VRLayerSurface::SurfaceType::FBO);
  if (m.layerEnvironment) {
    m.rootEnvironment->AddNode(VRLayerNode::Create(m.create, m.layerEnvironment));
  }
}

} // namespace crow


#define JNI_METHOD(return_type, method_name) \
  JNIEXPORT return_type JNICALL              \
    Java_org_mozilla_vrbrowser_VRBrowserActivity_##method_name

extern "C" {

JNI_METHOD(void, addWidgetNative)
(JNIEnv* aEnv, jobject, jint aHandle, jobject aPlacement) {
  crow::WidgetPlacementPtr placement = crow::WidgetPlacement::FromJava(aEnv, aPlacement);
  if (placement) {
    crow::BrowserWorld::Instance().AddWidget(aHandle, placement);
  }
}

JNI_METHOD(void, updateWidgetNative)
(JNIEnv* aEnv, jobject, jint aHandle, jobject aPlacement) {
  crow::WidgetPlacementPtr placement = crow::WidgetPlacement::FromJava(aEnv, aPlacement);
  if (placement) {
    crow::BrowserWorld::Instance().UpdateWidgetRecursive(aHandle, placement);
  }
}

JNI_METHOD(void, updateVisibleWidgetsNative)
(JNIEnv* aEnv, jobject) {
  crow::BrowserWorld::Instance().UpdateVisibleWidgets();
}


JNI_METHOD(void, removeWidgetNative)
(JNIEnv*, jobject, jint aHandle) {
  crow::BrowserWorld::Instance().RemoveWidget(aHandle);
}

JNI_METHOD(void, startWidgetResizeNative)
(JNIEnv*, jobject, jint aHandle, jfloat aMaxWidth, jfloat aMaxHeight, jfloat aMinWidth, jfloat aMinHeight) {
  crow::BrowserWorld::Instance().StartWidgetResize(aHandle,
      vrb::Vector(aMaxWidth, aMaxHeight, 0.0f), vrb::Vector(aMinWidth, aMinHeight, 0.0f));
}

JNI_METHOD(void, finishWidgetResizeNative)
(JNIEnv*, jobject, jint aHandle) {
  crow::BrowserWorld::Instance().FinishWidgetResize(aHandle);
}

JNI_METHOD(void, startWidgetMoveNative)
(JNIEnv*, jobject, jint aHandle, jint aMoveBehaviour) {
  crow::BrowserWorld::Instance().StartWidgetMove(aHandle, aMoveBehaviour);
}

JNI_METHOD(void, finishWidgetMoveNative)
(JNIEnv*, jobject) {
  crow::BrowserWorld::Instance().FinishWidgetMove();
}

JNI_METHOD(void, setWorldBrightnessNative)
(JNIEnv*, jobject, jfloat aBrightness) {
  crow::BrowserWorld::Instance().SetBrightness(aBrightness);
}

JNI_METHOD(void, setTemporaryFilePath)
(JNIEnv* aEnv, jobject, jstring aPath) {
  const char *nativeString = aEnv->GetStringUTFChars(aPath, nullptr);
  std::string path = nativeString;
  aEnv->ReleaseStringUTFChars(aPath, nativeString);
  crow::BrowserWorld::Instance().SetTemporaryFilePath(path);
}

JNI_METHOD(void, exitImmersiveNative)
(JNIEnv*, jobject) {
  crow::BrowserWorld::Instance().ExitImmersive();
}

JNI_METHOD(void, workaroundGeckoSigAction)
(JNIEnv*, jobject) {
  if (putenv(strdup("MOZ_DISABLE_SIG_HANDLER=1")) == 0) {
    VRB_DEBUG("Successfully set MOZ_DISABLE_SIG_HANDLER");
  } else {
    VRB_ERROR("Failed to set MOZ_DISABLE_SIG_HANDLER");
  }
  if (putenv(strdup("MOZ_DISABLE_EXCEPTION_HANDLER_SIGILL=1")) == 0) {
    VRB_DEBUG("Successfully set MOZ_DISABLE_EXCEPTION_HANDLER_SIGILL");
  } else {
    VRB_ERROR("Failed to set MOZ_DISABLE_EXCEPTION_HANDLER_SIGILL");
  }
}

JNI_METHOD(void, updateEnvironmentNative)
(JNIEnv*, jobject) {
  crow::BrowserWorld::Instance().UpdateEnvironment();
}

JNI_METHOD(void, updatePointerColorNative)
(JNIEnv*, jobject) {
  crow::BrowserWorld::Instance().UpdatePointerColor();
}

JNI_METHOD(void, showVRVideoNative)
(JNIEnv*, jobject, jint aWindowHandle, jint aVideoProjection) {
  crow::BrowserWorld::Instance().ShowVRVideo(aWindowHandle, aVideoProjection);
}

JNI_METHOD(void, hideVRVideoNative)
(JNIEnv*, jobject) {
  crow::BrowserWorld::Instance().HideVRVideo();
}

JNI_METHOD(void, setControllersVisibleNative)
(JNIEnv*, jobject, jboolean aVisible) {
  crow::BrowserWorld::Instance().SetControllersVisible(aVisible);
}

JNI_METHOD(void, recenterUIYawNative)
(JNIEnv*, jobject, jint aTarget) {
  crow::BrowserWorld::YawTarget value = crow::BrowserWorld::YawTarget::ALL;
  if (aTarget == 1) {
    value = crow::BrowserWorld::YawTarget::WIDGETS;
  }
  crow::BrowserWorld::Instance().RecenterUIYaw(value);
}

JNI_METHOD(void, setCylinderDensityNative)
(JNIEnv*, jobject, jfloat aDensity) {
  crow::BrowserWorld::Instance().SetCylinderDensity(aDensity);
}

JNI_METHOD(void, runCallbackNative)
(JNIEnv*, jobject, jlong aCallback) {
  if (aCallback) {
    auto func = reinterpret_cast<std::function<void()> *>((uintptr_t)aCallback);
    (*func)();
    delete func;
  }
}

JNI_METHOD(void, setCPULevelNative)
(JNIEnv*, jobject, jint aCPULevel) {
  crow::BrowserWorld::Instance().SetCPULevel(static_cast<crow::device::CPULevel>(aCPULevel));
}

JNI_METHOD(void, setWebXRIntersitialStateNative)
(JNIEnv*, jobject, jint aState) {
  crow::BrowserWorld::WebXRInterstialState value;
  if (aState == 0) {
    value = crow::BrowserWorld::WebXRInterstialState::FORCED;
  } else if (aState == 1) {
    value = crow::BrowserWorld::WebXRInterstialState::ALLOW_DISMISS;
  } else {
    value = crow::BrowserWorld::WebXRInterstialState::HIDDEN;
  }
  crow::BrowserWorld::Instance().SetWebXRInterstitalState(value);
}

JNI_METHOD(void, setIsServo)
(JNIEnv*, jobject, jboolean aIsServo) {
  crow::BrowserWorld::Instance().SetIsServo(aIsServo);
}



} // extern "C"
