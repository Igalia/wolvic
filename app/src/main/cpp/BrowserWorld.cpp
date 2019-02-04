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
#include "LoadingAnimation.h"
#include "Skybox.h"
#include "SplashAnimation.h"
#include "Pointer.h"
#include "Widget.h"
#include "WidgetPlacement.h"
#include "Cylinder.h"
#include "Quad.h"
#include "VRBrowser.h"
#include "VRVideo.h"
#include "VRLayer.h"
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
#include "vrb/RenderContext.h"
#include "vrb/RenderState.h"
#include "vrb/SurfaceTextureFactory.h"
#include "vrb/TextureCache.h"
#include "vrb/TextureSurface.h"
#include "vrb/TextureCubeMap.h"
#include "vrb/Toggle.h"
#include "vrb/Transform.h"
#include "vrb/VertexArray.h"
#include "vrb/Vector.h"

#include <array>
#include <functional>
#include <fstream>

#define ASSERT_ON_RENDER_THREAD(X)                                          \
  if (m.context && !m.context->IsOnRenderThread()) {                        \
    VRB_ERROR("Function: '%s' not called on render thread.", __FUNCTION__); \
    return X;                                                               \
  }


#define INJECT_ENVIRONMENT_PATH "environment/environment.obj"
#define INJECT_SKYBOX_PATH "skybox"
#define SPACE_THEME 0

using namespace vrb;

namespace {

static const int GestureSwipeLeft = 0;
static const int GestureSwipeRight = 1;

static const float kScrollFactor = 20.0f; // Just picked what fell right.
static const float kWorldDPIRatio = 2.0f/720.0f;
static const double kHoverRate = 1.0 / 10.0;

#if SPACE_THEME == 1
  static const std::string CubemapDay = "cubemap/space";
#else
  static const std::string CubemapDay = "cubemap/day";
#endif

class SurfaceObserver;
typedef std::shared_ptr<SurfaceObserver> SurfaceObserverPtr;

class SurfaceObserver : public SurfaceTextureObserver {
public:
  SurfaceObserver(crow::BrowserWorldWeakPtr& aWorld);
  ~SurfaceObserver();
  void SurfaceTextureCreated(const std::string& aName, GLuint aHandle, jobject aSurfaceTexture) override;
  void SurfaceTextureHandleUpdated(const std::string aName, GLuint aHandle) override;
  void SurfaceTextureDestroyed(const std::string& aName) override;
  void SurfaceTextureCreationError(const std::string& aName, const std::string& aReason) override;

protected:
  crow::BrowserWorldWeakPtr mWorld;
};

SurfaceObserver::SurfaceObserver(crow::BrowserWorldWeakPtr& aWorld) : mWorld(aWorld) {}
SurfaceObserver::~SurfaceObserver() {}

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
  LoadingAnimationPtr loadingAnimation;
  SplashAnimationPtr splashAnimation;
  VRVideoPtr vrVideo;

  State() : paused(true), glInitialized(false), modelsLoaded(false), env(nullptr), cylinderDensity(0.0f), nearClip(0.1f),
            farClip(300.0f), activity(nullptr), windowsInitialized(false), exitImmersiveRequested(false), loaderDelay(0) {
    context = RenderContext::Create();
    create = context->GetRenderThreadCreationContext();
    loader = ModelLoaderAndroid::Create(context);
    rootOpaque = Transform::Create(create);
    rootTransparent = Transform::Create(create);
    rootController = Group::Create(create);
    light = Light::Create(create);
    rootOpaqueParent = Group::Create(create);
    rootOpaqueParent->AddNode(rootOpaque);
    //rootOpaque->AddLight(light);
    //rootTransparent->AddLight(light);
    rootController->AddLight(light);
    cullVisitor = CullVisitor::Create(create);
    drawList = DrawableList::Create(create);
    controllers = ControllerContainer::Create(create, rootTransparent);
    externalVR = ExternalVR::Create();
    blitter = ExternalBlitter::Create(create);
    fadeAnimation = FadeAnimation::Create(create);
    loadingAnimation = LoadingAnimation::Create(create);
    splashAnimation = SplashAnimation::Create(create);
  }

  void CheckBackButton();
  bool CheckExitImmersive();
  void UpdateControllers(bool& aRelayoutWidgets);
  WidgetPtr GetWidget(int32_t aHandle) const;
  WidgetPtr FindWidget(const std::function<bool(const WidgetPtr&)>& aCondition) const;
};

void
BrowserWorld::State::CheckBackButton() {
  for (Controller& controller: controllers->GetControllers()) {
    if (!controller.enabled || (controller.index < 0)) {
      continue;
    }

    if (!(controller.lastButtonState & ControllerDelegate::BUTTON_APP) &&
        (controller.buttonState & ControllerDelegate::BUTTON_APP)) {
      controller.lastButtonState = controller.buttonState;
      VRBrowser::HandleBack();
    }
  }
}

bool
BrowserWorld::State::CheckExitImmersive() {
  if (exitImmersiveRequested && externalVR->IsPresenting()) {
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
BrowserWorld::State::UpdateControllers(bool& aRelayoutWidgets) {
  for (Controller& controller: controllers->GetControllers()) {
    if (!controller.enabled || (controller.index < 0)) {
      continue;
    }
    if (controller.pointer && !controller.pointer->IsLoaded()) {
      controller.pointer->Load(device);
    }

    if (!(controller.lastButtonState & ControllerDelegate::BUTTON_APP) && (controller.buttonState & ControllerDelegate::BUTTON_APP)) {
      VRBrowser::HandleBack();
    }

    vrb::Vector start = controller.transformMatrix.MultiplyPosition(vrb::Vector());
    vrb::Vector direction = controller.transformMatrix.MultiplyDirection(vrb::Vector(0.0f, 0.0f, -1.0f));
    WidgetPtr hitWidget;
    float hitDistance = farClip;
    vrb::Vector hitPoint;
    vrb::Vector hitNormal;
    for (const WidgetPtr& widget: widgets) {
      vrb::Vector result;
      vrb::Vector normal;
      float distance = 0.0f;
      bool isInWidget = false;
      if (widget->TestControllerIntersection(start, direction, result, normal, isInWidget, distance)) {
        if (isInWidget && (distance < hitDistance)) {
          hitWidget = widget;
          hitDistance = distance;
          hitPoint = result;
          hitNormal = normal;
        }
      }
    }

    if ((!hitWidget || !hitWidget->IsResizing()) && resizingWidget) {
      resizingWidget->HoverExitResize();
      resizingWidget.reset();
    }

    if (controller.pointer) {
      controller.pointer->SetVisible(hitWidget.get() != nullptr);
      controller.pointer->SetHitWidget(hitWidget);
      if (hitWidget) {
        vrb::Matrix translation = vrb::Matrix::Translation(hitPoint);
        vrb::Matrix localRotation = vrb::Matrix::Rotation(hitNormal);
        vrb::Matrix reorient = device->GetReorientTransform();
        controller.pointer->SetTransform(reorient.AfineInverse().PostMultiply(translation).PostMultiply(localRotation));
      }
    }

    const bool pressed = controller.buttonState & ControllerDelegate::BUTTON_TRIGGER ||
                         controller.buttonState & ControllerDelegate::BUTTON_TOUCHPAD;
    const bool wasPressed = controller.lastButtonState & ControllerDelegate::BUTTON_TRIGGER ||
                              controller.lastButtonState & ControllerDelegate::BUTTON_TOUCHPAD;
    if (hitWidget && hitWidget->IsResizing()) {
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
    }
    else if (hitWidget) {
      float theX = 0.0f, theY = 0.0f;
      hitWidget->ConvertToWidgetCoordinates(hitPoint, theX, theY);
      const uint32_t handle = hitWidget->GetHandle();
      if (!pressed && wasPressed) {
        controller.inDeadZone = true;
      }
      const bool moved = pressed ? OutOfDeadZone(controller, theX, theY)
          : (controller.pointerX != theX) || (controller.pointerY != theY);
      const bool throttled = ThrottleHoverEvent(controller, context->GetTimestamp(), pressed, wasPressed);

      if ((!throttled && moved) || (controller.widget != handle) || (pressed != wasPressed)) {
        controller.widget = handle;
        controller.pointerX = theX;
        controller.pointerY = theY;
        VRBrowser::HandleMotionEvent(handle, controller.index, jboolean(pressed),
                                     controller.pointerX, controller.pointerY);
      }
      if ((controller.scrollDeltaX != 0.0f) || controller.scrollDeltaY != 0.0f) {
        VRBrowser::HandleScrollEvent(controller.widget, controller.index,
                            controller.scrollDeltaX, controller.scrollDeltaY);
        controller.scrollDeltaX = 0.0f;
        controller.scrollDeltaY = 0.0f;
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
      VRBrowser::HandleMotionEvent(0, controller.index, pressed, 0.0f, 0.0f);
      controller.widget = 0;

    } else if (wasPressed != pressed) {
      VRBrowser::HandleMotionEvent(0, controller.index, pressed, 0.0f, 0.0f);
    }
    controller.lastButtonState = controller.buttonState;
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
  m.device = aDelegate;
  if (m.device) {
    m.device->RegisterImmersiveDisplay(m.externalVR);
    m.device->SetClearColor(vrb::Color(0.0f, 0.0f, 0.0f, 0.0f));
    m.leftCamera = m.device->GetCamera(device::Eye::Left);
    m.rightCamera = m.device->GetCamera(device::Eye::Right);
    ControllerDelegatePtr delegate = m.controllers;
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
}

void
BrowserWorld::Resume() {
  ASSERT_ON_RENDER_THREAD();
  m.paused = false;
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

  if (!m.modelsLoaded) {
    const int32_t modelCount = m.device->GetControllerModelCount();
    for (int32_t index = 0; index < modelCount; index++) {
      const std::string fileName = m.device->GetControllerModelName(index);
      if (!fileName.empty()) {
        m.controllers->LoadControllerModel(index, m.loader, fileName);
      }
    }
    m.controllers->InitializeBeam();
    m.controllers->SetPointerColor(vrb::Color(VRBrowser::GetPointerColor()));
    m.loadingAnimation->LoadModels(m.loader);
    m.rootController->AddNode(m.controllers->GetRoot());
    std::string skyboxPath = VRBrowser::GetActiveEnvironment();
    std::string extension;
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
      }
    }
#if !defined(SNAPDRAGONVR)
    CreateSkyBox(skyboxPath.c_str(), extension);
    // Don't load the env model, we are going for skyboxes in v1.0
//    CreateFloor();
#endif
    m.fadeAnimation->SetFadeChangeCallback([=](const vrb::Color& aTintColor) {
      if (m.skybox) {
        m.skybox->SetTintColor(aTintColor);
      }
    });
    m.modelsLoaded = true;
  }
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
BrowserWorld::Draw() {
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

  m.CheckExitImmersive();
  if (m.splashAnimation) {
    DrawSplashAnimation();
  }
  else if (m.externalVR->IsPresenting()) {
    m.CheckBackButton();
    DrawImmersive();
  } else {
    bool relayoutWidgets = false;
    m.UpdateControllers(relayoutWidgets);
    if (relayoutWidgets) {
      UpdateVisibleWidgets();
    }
    DrawWorld();
    m.externalVR->PushSystemState();
  }
  // Update the 3d audio engine with the most recent head rotation.
  const vrb::Matrix &head = m.device->GetHeadTransform();
  const vrb::Vector p = head.GetTranslation();
  const vrb::Quaternion q(head);
  VRBrowser::HandleAudioPose(q.x(), q.y(), q.z(), q.w(), p.x(), p.y(), p.z());

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
  std::string env = VRBrowser::GetActiveEnvironment();
  VRB_LOG("Setting environment: %s", env.c_str());
  CreateSkyBox(env.c_str(), "");
}

void
BrowserWorld::UpdatePointerColor() {
  ASSERT_ON_RENDER_THREAD();
  int32_t color = VRBrowser::GetPointerColor();
  VRB_LOG("Setting pointer color to: %d:", color);

  if (m.controllers)
    m.controllers->SetPointerColor(vrb::Color(color));
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
    worldWidth = aPlacement->width * kWorldDPIRatio;
  }

  int32_t textureWidth = (int32_t)(ceilf(aPlacement->width * aPlacement->density));
  int32_t textureHeight = (int32_t)(ceilf(aPlacement->height * aPlacement->density));

  const float aspect = (float)textureWidth / (float)textureHeight;
  const float worldHeight = worldWidth / aspect;

  WidgetPtr widget;
  if (aPlacement->cylinder && m.cylinderDensity > 0 && m.device) {
    VRLayerCylinderPtr layer = m.device->CreateLayerCylinder(textureWidth, textureHeight, VRLayerQuad::SurfaceType::AndroidSurface);
    CylinderPtr cylinder = Cylinder::Create(m.create, layer);
    widget = Widget::Create(m.context, aHandle, textureWidth, textureHeight, worldWidth, worldHeight, cylinder);
  }

  if (!widget) {
    VRLayerQuadPtr layer;
    if (aPlacement->layer && m.device) {
      layer = m.device->CreateLayerQuad(textureWidth, textureHeight, VRLayerQuad::SurfaceType::AndroidSurface);
    }

    QuadPtr quad = Quad::Create(m.create, worldWidth, worldHeight, layer);
    widget = Widget::Create(m.context, aHandle, textureWidth, textureHeight, quad);
  }

  if (aPlacement->opaque) {
    m.rootOpaque->AddNode(widget->GetRoot());
  } else {
    m.rootTransparent->AddNode(widget->GetRoot());
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
  widget->SetCylinderDensity(m.cylinderDensity);
  widget->ToggleWidget(aPlacement->visible);
  widget->SetSurfaceTextureSize((int32_t)(ceilf(aPlacement->width * aPlacement->density)),
                                (int32_t)(ceilf(aPlacement->height * aPlacement->density)));

  float worldWidth = 0.0f, worldHeight = 0.0f;
  widget->GetWorldSize(worldWidth, worldHeight);

  float newWorldWidth = aPlacement->worldWidth;
  if (newWorldWidth <= 0.0f) {
    newWorldWidth = aPlacement->width * kWorldDPIRatio;
  }

  if (newWorldWidth != worldWidth || oldWidth != aPlacement->width || oldHeight != aPlacement->height) {
    widget->SetWorldWidth(newWorldWidth);
  }

  LayoutWidget(aHandle);
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
BrowserWorld::StartWidgetResize(int32_t aHandle) {
  ASSERT_ON_RENDER_THREAD();
  WidgetPtr widget = m.GetWidget(aHandle);
  if (widget) {
    widget->StartResize();
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
}

void
BrowserWorld::UpdateVisibleWidgets() {
  ASSERT_ON_RENDER_THREAD();
  for (const WidgetPtr& widget: m.widgets) {
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
  if (aPlacement->rotationAxis.Magnitude() > std::numeric_limits<float>::epsilon()) {
    transform = vrb::Matrix::Rotation(aPlacement->rotationAxis, aPlacement->rotation);
  }

  vrb::Vector translation = vrb::Vector(aPlacement->translation.x() * kWorldDPIRatio,
                                        aPlacement->translation.y() * kWorldDPIRatio,
                                        aPlacement->translation.z() * kWorldDPIRatio);

  // Widget anchor point
  translation -= vrb::Vector((aPlacement->anchor.x() - 0.5f) * worldWidth,
                             (aPlacement->anchor.y() - 0.5f) * worldHeight,
                             0.0f);
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
BrowserWorld::ResetUIYaw() {
  vrb::Matrix head = m.device->GetHeadTransform();
  vrb::Vector vector = head.MultiplyDirection(vrb::Vector(1.0f, 0.0f, 0.0f));
  const float yaw = atan2(vector.z(), vector.x());

  vrb::Matrix matrix = vrb::Matrix::Rotation(vrb::Vector(0.0f, 1.0f, 0.0f), -yaw);
  m.device->SetReorientTransform(matrix);
}

void
BrowserWorld::SetCylinderDensity(const float aDensity) {
  m.cylinderDensity = aDensity;
  for (WidgetPtr& widget: m.widgets) {
    widget->SetCylinderDensity(aDensity);
  }
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

BrowserWorld::~BrowserWorld() {}

void
BrowserWorld::DrawWorld() {
  m.externalVR->SetCompositorEnabled(true);
  m.device->SetRenderMode(device::RenderMode::StandAlone);
  if (m.fadeAnimation) {
    m.fadeAnimation->UpdateAnimation();
  }
  vrb::Vector headPosition = m.device->GetHeadTransform().GetTranslation();
  vrb::Vector headDirection = m.device->GetHeadTransform().MultiplyDirection(vrb::Vector(0.0f, 0.0f, -1.0f));
  if (m.skybox) {
    m.skybox->SetTransform(vrb::Matrix::Translation(headPosition));
  }
  m.rootTransparent->SortNodes([=](const NodePtr& a, const NodePtr& b) {
    float da = DistanceToPlane(a, headPosition, headDirection);
    float db = DistanceToPlane(b, headPosition, headDirection);
    if (da < 0.0f) {
      da = std::numeric_limits<float>::max();
    }
    if (db < 0.0f) {
      db = std::numeric_limits<float>::max();
    }
    return da < db;
  });
  m.device->StartFrame();
  m.rootOpaque->SetTransform(m.device->GetReorientTransform());
  m.rootTransparent->SetTransform(m.device->GetReorientTransform());

  m.device->BindEye(device::Eye::Left);
  m.drawList->Reset();
  m.rootOpaqueParent->Cull(*m.cullVisitor, *m.drawList);
  m.drawList->Draw(*m.leftCamera);
  if (m.vrVideo) {
    m.vrVideo->SelectEye(device::Eye::Left);
    m.drawList->Reset();
    m.vrVideo->GetRoot()->Cull(*m.cullVisitor, *m.drawList);
    m.drawList->Draw(*m.leftCamera);
  }
  m.drawList->Reset();
  m.rootController->Cull(*m.cullVisitor, *m.drawList);
  m.drawList->Draw(*m.leftCamera);
  VRB_GL_CHECK(glDepthMask(GL_FALSE));
  m.drawList->Reset();
  m.rootTransparent->Cull(*m.cullVisitor, *m.drawList);
  m.drawList->Draw(*m.leftCamera);
  VRB_GL_CHECK(glDepthMask(GL_TRUE));
  // When running the noapi flavor, we only want to render one eye.
#if !defined(VRBROWSER_NO_VR_API)
  m.device->BindEye(device::Eye::Right);
  m.drawList->Reset();
  m.rootOpaqueParent->Cull(*m.cullVisitor, *m.drawList);
  m.drawList->Draw(*m.rightCamera);
  if (m.vrVideo) {
    m.vrVideo->SelectEye(device::Eye::Right);
    m.drawList->Reset();
    m.vrVideo->GetRoot()->Cull(*m.cullVisitor, *m.drawList);
    m.drawList->Draw(*m.leftCamera);
  }
  m.drawList->Reset();
  m.rootController->Cull(*m.cullVisitor, *m.drawList);
  m.drawList->Draw(*m.rightCamera);
  VRB_GL_CHECK(glDepthMask(GL_FALSE));
  m.drawList->Reset();
  m.rootTransparent->Cull(*m.cullVisitor, *m.drawList);
  m.drawList->Draw(*m.rightCamera);
  VRB_GL_CHECK(glDepthMask(GL_TRUE));
#endif // !defined(VRBROWSER_NO_VR_API)

  m.device->EndFrame(false);
}

void
BrowserWorld::DrawImmersive() {
  m.externalVR->SetCompositorEnabled(false);
  m.device->SetRenderMode(device::RenderMode::Immersive);

  m.device->StartFrame();
  VRB_GL_CHECK(glDepthMask(GL_FALSE));
  m.externalVR->PushFramePoses(m.device->GetHeadTransform(), m.controllers->GetControllers());
  int32_t surfaceHandle = 0;
  device::EyeRect leftEye, rightEye;
  bool aDiscardFrame = !m.externalVR->WaitFrameResult();
  m.externalVR->GetFrameResult(surfaceHandle, leftEye, rightEye);
  ExternalVR::VRState state = m.externalVR->GetVRState();
  if (state == ExternalVR::VRState::Rendering) {
    if (!aDiscardFrame) {
      m.blitter->StartFrame(surfaceHandle, leftEye, rightEye);
      m.device->BindEye(device::Eye::Left);
      m.blitter->Draw(device::Eye::Left);
#if !defined(VRBROWSER_NO_VR_API)
      m.device->BindEye(device::Eye::Right);
      m.blitter->Draw(device::Eye::Right);
#endif // !defined(VRBROWSER_NO_VR_API)
    }
    m.device->EndFrame(aDiscardFrame);
    m.blitter->EndFrame();
  } else {
    if (surfaceHandle != 0) {
      m.blitter->CancelFrame(surfaceHandle);
    }
    DrawLoadingAnimation();
    m.device->EndFrame(false);
  }
}

void
BrowserWorld::DrawLoadingAnimation() {
  VRB_GL_CHECK(glDepthMask(GL_TRUE));
  m.loadingAnimation->Update();
  m.drawList->Reset();
  m.loadingAnimation->GetRoot()->Cull(*m.cullVisitor, *m.drawList);

  m.device->BindEye(device::Eye::Left);
  m.drawList->Draw(*m.leftCamera);
#if !defined(VRBROWSER_NO_VR_API)
  m.device->BindEye(device::Eye::Right);
  m.drawList->Draw(*m.rightCamera);
#endif // !defined(VRBROWSER_NO_VR_API)
}


void
BrowserWorld::DrawSplashAnimation() {
  if (!m.splashAnimation) {
    return;
  }
  m.device->StartFrame();
  const bool animationFinished = m.splashAnimation->Update(m.device->GetHeadTransform());
  m.drawList->Reset();
  m.splashAnimation->GetRoot()->Cull(*m.cullVisitor, *m.drawList);

  m.device->BindEye(device::Eye::Left);
  m.drawList->Draw(*m.leftCamera);
#if !defined(VRBROWSER_NO_VR_API)
  m.device->BindEye(device::Eye::Right);
  m.drawList->Draw(*m.rightCamera);
#endif // !defined(VRBROWSER_NO_VR_API)
  m.device->EndFrame();
  if (animationFinished) {
    if (m.splashAnimation && m.splashAnimation->GetLayer()) {
      m.device->DeleteLayer(m.splashAnimation->GetLayer());
    }
    m.splashAnimation = nullptr;
    if (m.fadeAnimation) {
      m.fadeAnimation->FadeIn();
    }
  }
}

void
BrowserWorld::CreateSkyBox(const std::string& aBasePath, const std::string& aExtension) {
  ASSERT_ON_RENDER_THREAD();
  const bool empty = aBasePath == "cubemap/void";
  const std::string extension = aExtension.empty() ? ".ktx" : aExtension;
  if (m.skybox && empty) {
    m.skybox->SetVisible(false);
    return;
  } else if (m.skybox) {
    m.skybox->SetVisible(true);
    m.skybox->Load(m.loader, aBasePath, extension);
    return;
  } else if (!empty) {
    GLenum glFormat = extension == ".ktx" ? GL_COMPRESSED_RGB8_ETC2 : GL_RGB8;
    VRLayerCubePtr layer = m.device->CreateLayerCube(1024, 1024, glFormat);
    m.skybox = Skybox::Create(m.create, layer);
    m.rootOpaqueParent->AddNode(m.skybox->GetRoot());
    m.skybox->Load(m.loader, aBasePath, extension);
  }
}

void
BrowserWorld::CreateFloor() {
  ASSERT_ON_RENDER_THREAD();
  vrb::TransformPtr model = Transform::Create(m.create);
#if SPACE_THEME == 1
  std::string environmentPath = "FirefoxPlatform2_low.obj";
#else
  std::string environmentPath = "meadow_v4.obj";
#endif
  if (VRBrowser::isOverrideEnvPathEnabled()) {
    std::string injectPath = VRBrowser::GetStorageAbsolutePath(INJECT_ENVIRONMENT_PATH);
    if (std::ifstream(injectPath)) {
      environmentPath = injectPath;
    }
  }
  m.loader->LoadModel(environmentPath, model);
  m.rootOpaque->AddNode(model);
  vrb::Matrix transform = vrb::Matrix::Identity();
#if SPACE_THEME == 1
  transform.ScaleInPlace(Vector(40.0, 40.0, 40.0));
  transform.TranslateInPlace(Vector(0.0, -2.5f, 1.0));
  transform.PostMultiplyInPlace(vrb::Matrix::Rotation(Vector(1.0, 0.0, 0.0), float(M_PI * 0.5)));
#endif
  model->SetTransform(transform);
}

float
BrowserWorld::DistanceToPlane(const vrb::NodePtr& aNode, const vrb::Vector& aPosition, const vrb::Vector& aDirection) const {
  WidgetPtr target;
  bool pointer = false;
  for (const auto & widget: m.widgets) {
    if (widget->GetRoot() == aNode) {
      target = widget;
      break;
    }
  }
  if (!target) {
    for (Controller& controller: m.controllers->GetControllers()) {
      if (controller.pointer && controller.pointer->GetRoot() == aNode) {
        target = controller.pointer->GetHitWidget();
        pointer = true;
        break;
      }
    }
  }

  if (!target) {
    return -1.0f;
  }
  vrb::Vector result;
  vrb::Vector normal;
  bool inside = false;
  float distance = -1.0f;
  if (target->GetQuad()) {
    target->GetQuad()->TestIntersection(aPosition, aDirection, result, normal, false, inside, distance);
  } else if (target->GetCylinder()) {
    distance = target->GetCylinder()->DistanceToBackPlane(aPosition, aDirection);
  }
  if (pointer) {
    distance-= 0.001f;
  }
  return distance;
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
    crow::BrowserWorld::Instance().UpdateWidget(aHandle, placement);
  }
}

JNI_METHOD(void, removeWidgetNative)
(JNIEnv*, jobject, jint aHandle) {
  crow::BrowserWorld::Instance().RemoveWidget(aHandle);
}

JNI_METHOD(void, startWidgetResizeNative)
(JNIEnv*, jobject, jint aHandle) {
  crow::BrowserWorld::Instance().StartWidgetResize(aHandle);
}

JNI_METHOD(void, finishWidgetResizeNative)
(JNIEnv*, jobject, jint aHandle) {
  crow::BrowserWorld::Instance().FinishWidgetResize(aHandle);
}

JNI_METHOD(void, setWorldBrightnessNative)
(JNIEnv*, jobject, jfloat aBrightness) {
  crow::BrowserWorld::Instance().SetBrightness(aBrightness);
}

JNI_METHOD(void, setTemporaryFilePath)
(JNIEnv* aEnv, jobject, jstring aPath) {
  const char *nativeString = aEnv->GetStringUTFChars(aPath, 0);
  std::string path = nativeString;
  aEnv->ReleaseStringUTFChars(aPath, nativeString);
  crow::BrowserWorld::Instance().SetTemporaryFilePath(path);
}

JNI_METHOD(void, exitImmersiveNative)
(JNIEnv* aEnv, jobject) {
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
(JNIEnv* aEnv, jobject) {
  crow::BrowserWorld::Instance().UpdateEnvironment();
}

JNI_METHOD(void, updatePointerColorNative)
(JNIEnv* aEnv, jobject) {
  crow::BrowserWorld::Instance().UpdatePointerColor();
}

JNI_METHOD(void, showVRVideoNative)
(JNIEnv* aEnv, jobject, jint aWindowHandle, jint aVideoProjection) {
  crow::BrowserWorld::Instance().ShowVRVideo(aWindowHandle, aVideoProjection);
}

JNI_METHOD(void, hideVRVideoNative)
(JNIEnv* aEnv, jobject) {
  crow::BrowserWorld::Instance().HideVRVideo();
}

JNI_METHOD(void, setControllersVisibleNative)
(JNIEnv* aEnv, jobject, jboolean aVisible) {
  crow::BrowserWorld::Instance().SetControllersVisible(aVisible);
}

JNI_METHOD(void, resetUIYawNative)
(JNIEnv* aEnv, jobject) {
  crow::BrowserWorld::Instance().ResetUIYaw();
}

JNI_METHOD(void, setCylinderDensityNative)
(JNIEnv* aEnv, jobject, jfloat aDensity) {
  crow::BrowserWorld::Instance().SetCylinderDensity(aDensity);
}


JNI_METHOD(void, runCallbackNative)
(JNIEnv* aEnv, jobject, jlong aCallback) {
  if (aCallback) {
    auto func = reinterpret_cast<std::function<void()> *>((uintptr_t)aCallback);
    (*func)();
    delete func;
  }
}


} // extern "C"
