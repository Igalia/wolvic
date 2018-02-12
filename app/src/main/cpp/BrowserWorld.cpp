/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "BrowserWorld.h"
#include "vrb/CameraSimple.h"
#include "vrb/Color.h"
#include "vrb/ConcreteClass.h"
#include "vrb/Context.h"
#include "vrb/CullVisitor.h"
#include "vrb/DrawableList.h"
#include "vrb/Geometry.h"
#include "vrb/Group.h"
#include "vrb/Light.h"
#include "vrb/Logger.h"
#include "vrb/Matrix.h"
#include "vrb/NodeFactoryObj.h"
#include "vrb/ParserObj.h"
#include "vrb/RenderState.h"
#include "vrb/SurfaceTextureFactory.h"
#include "vrb/TextureSurface.h"
#include "vrb/Transform.h"
#include "vrb/VertexArray.h"
#include "vrb/Vector.h"

using namespace vrb;

namespace {

static const char *kSetSurfaceTextureName = "setSurfaceTexture";
static const char *kSetSurfaceTextureSignature = "(Ljava/lang/String;Landroid/graphics/SurfaceTexture;II)V";
class SurfaceObserver;
typedef std::shared_ptr<SurfaceObserver> SurfaceObserverPtr;

class SurfaceObserver : public SurfaceTextureObserver {
public:
  SurfaceObserver(BrowserWorldWeakPtr& aWorld);
  ~SurfaceObserver();
  void SurfaceTextureCreated(const std::string& aName, GLuint aHandle, jobject aSurfaceTexture) override;
  void SurfaceTextureHandleUpdated(const std::string aName, GLuint aHandle) override;
  void SurfaceTextureDestroyed(const std::string& aName) override;
  void SurfaceTextureCreationError(const std::string& aName, const std::string& aReason) override;

protected:
  BrowserWorldWeakPtr mWorld;
};

SurfaceObserver::SurfaceObserver(BrowserWorldWeakPtr& aWorld) : mWorld(aWorld) {}
SurfaceObserver::~SurfaceObserver() {}

void
SurfaceObserver::SurfaceTextureCreated(const std::string& aName, GLuint aHandle, jobject aSurfaceTexture) {
  BrowserWorldPtr world = mWorld.lock();
  if (world) {
    world->SetSurfaceTexture(aName, aSurfaceTexture);
  }
}

void
SurfaceObserver::SurfaceTextureHandleUpdated(const std::string aName, GLuint aHandle) {}

void
SurfaceObserver::SurfaceTextureDestroyed(const std::string& aName) {
  BrowserWorldPtr world = mWorld.lock();
  if (world) {
    jobject nullObject = nullptr;
    world->SetSurfaceTexture(aName, nullObject);
  }
}

void
SurfaceObserver::SurfaceTextureCreationError(const std::string& aName, const std::string& aReason) {
  
}

struct ControllerRecord {
  int32_t index;
  TransformPtr controller;
  ControllerRecord(const int32_t aIndex) : index(aIndex) {}
  ControllerRecord(const ControllerRecord& aRecord) : index(aRecord.index), controller(aRecord.controller) {}
  ControllerRecord(ControllerRecord&& aRecord) : index(aRecord.index), controller(std::move(aRecord.controller)) {}
  ControllerRecord& operator=(const ControllerRecord& aRecord) {
    index = aRecord.index;
    controller = aRecord.controller;
    return *this;
  }

  ControllerRecord& operator=(ControllerRecord&& aRecord) {
    index = aRecord.index;
    controller = std::move(aRecord.controller);
    return *this;
  }
private:
  ControllerRecord() = delete;
};

} // namespace


struct BrowserWorld::State {
  BrowserWorldWeakPtr self;
  SurfaceObserverPtr surfaceObserver;
  DeviceDelegatePtr device;
  bool paused;
  bool glInitialized;
  float heading;
  ContextPtr context;
  ContextWeak contextWeak;
  NodeFactoryObjPtr factory;
  ParserObjPtr parser;
  GroupPtr root;
  LightPtr light;
  TransformPtr browser;
  int32_t controllerCount;
  std::vector<ControllerRecord> controllers;
  TextureSurfacePtr browserSurface;
  CullVisitorPtr cullVisitor;
  DrawableListPtr drawList;
  CameraPtr leftCamera;
  CameraPtr rightCamera;
  JNIEnv* env;
  jobject activity;
  jmethodID setSurfaceTextureMethod;


  State() : paused(true), glInitialized(false), heading(0.0f), controllerCount(0), env(nullptr), activity(nullptr), setSurfaceTextureMethod(nullptr) {
    context = Context::Create();
    contextWeak = context;
    factory = NodeFactoryObj::Create(contextWeak);
    parser = ParserObj::Create(contextWeak);
    parser->SetObserver(factory);
    root = Group::Create(contextWeak);
    light = Light::Create(contextWeak);
    root->AddLight(light);
    browserSurface = TextureSurface::Create(contextWeak, "browser");
    cullVisitor = CullVisitor::Create(contextWeak);
    drawList = DrawableList::Create(contextWeak);
  }
};


BrowserWorldPtr
BrowserWorld::Create() {
  BrowserWorldPtr result = std::make_shared<vrb::ConcreteClass<BrowserWorld, BrowserWorld::State> >();
  result->m.self = result;
  result->m.surfaceObserver = std::make_shared<SurfaceObserver>(result->m.self);
  result->m.context->GetSurfaceTextureFactory()->AddGlobalObserver(result->m.surfaceObserver);
  return result;
}

vrb::ContextPtr
BrowserWorld::GetContext() {
  return m.context;
}

void
BrowserWorld::RegisterDeviceDelegate(DeviceDelegatePtr aDelegate) {
  m.device = aDelegate;
  if (m.device) {
    m.leftCamera = m.device->GetCamera(DeviceDelegate::CameraEnum::Left);
    m.rightCamera = m.device->GetCamera(DeviceDelegate::CameraEnum::Right);
    m.controllerCount = m.device->GetControllerCount();
  } else {
    m.leftCamera = m.rightCamera = nullptr;
    m.controllers.clear();
    m.controllerCount = 0;
  }
}

void
BrowserWorld::Pause() {
  m.paused = true;
}

void
BrowserWorld::Resume() {
  m.paused = false;
}

void
BrowserWorld::InitializeJava(JNIEnv* aEnv, jobject& aActivity, jobject& aAssetManager) {
  if (m.context) {
    m.context->InitializeJava(aEnv, aAssetManager);
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
  m.setSurfaceTextureMethod = m.env->GetMethodID(clazz, kSetSurfaceTextureName, kSetSurfaceTextureSignature);
  if (!m.setSurfaceTextureMethod) {
    VRB_LOG("Failed to find Java method: %s %s", kSetSurfaceTextureName, kSetSurfaceTextureSignature);
  }
}

void
BrowserWorld::InitializeGL() {
  if (m.context) {
    if (!m.browser) {
      CreateBrowser();
    }
    if ((m.controllers.size() == 0) && (m.controllerCount > 0)) {
      for (int32_t ix = 0; ix < m.controllerCount; ix++) {
        ControllerRecord record(ix);
        record.controller = Transform::Create(m.contextWeak);
        m.factory->SetModelRoot(record.controller);
        m.parser->LoadModel(m.device->GetControllerModelName(ix));
        m.root->AddNode(record.controller);
        m.controllers.push_back(std::move(record));
      }
    }
    if (!m.glInitialized) {
      m.glInitialized = m.context->InitializeGL();
    }
  }
}

void
BrowserWorld::ShutdownJava() {
  if (m.env) {
    m.env->DeleteGlobalRef(m.activity);
  }
  m.activity = nullptr;
  m.setSurfaceTextureMethod = nullptr;
}

void
BrowserWorld::ShutdownGL() {
  if (m.context) {
    m.context->ShutdownGL();
  }
  m.glInitialized = false;
}

void
BrowserWorld::Draw() {
  if (!m.device) {
    return;
  }
  if (m.paused) {
    return;
  }
  if (!m.glInitialized) {
    m.glInitialized = m.context->InitializeGL();
    if (!m.glInitialized) {
      return;
    }
  }
  m.device->ProcessEvents();
  m.context->Update();
  for (ControllerRecord& record: m.controllers) {
    record.controller->SetTransform(m.device->GetControllerTransform(record.index));
  }
  m.browser->SetTransform(Matrix::Rotation(Vector(0.0f, 1.0f, 0.0f), m.heading));
  m.drawList->Reset();
  m.root->Cull(*m.cullVisitor, *m.drawList);
  m.device->StartFrame();
  m.device->BindEye(DeviceDelegate::CameraEnum::Left);
  m.drawList->Draw(*m.leftCamera);
  m.device->BindEye(DeviceDelegate::CameraEnum::Right);
  m.drawList->Draw(*m.rightCamera);
  m.device->EndFrame();
  m.heading += M_PI / 120.0f;
  if (m.heading > (2.0f * M_PI)) { m.heading = 0.0f; }
}

void
BrowserWorld::SetSurfaceTexture(const std::string& aName, jobject& aSurface) {
  if (m.env && m.activity && m.setSurfaceTextureMethod) {
    jstring name = m.env->NewStringUTF(aName.c_str());
    m.env->CallVoidMethod(m.activity, m.setSurfaceTextureMethod, name, aSurface, 1024, 1024);
    m.env->DeleteLocalRef(name);
  }
}

void
BrowserWorld::CreateBrowser() {
  VertexArrayPtr array = VertexArray::Create(m.contextWeak);
  const float kLength = 5.0f;
  array->AppendVertex(Vector(-kLength, -kLength, 0.0f)); // Bottom left
  array->AppendVertex(Vector(kLength, -kLength, 0.0f)); // Bottom right
  array->AppendVertex(Vector(kLength, kLength, 0.0f)); // Top right
  array->AppendVertex(Vector(-kLength, kLength, 0.0f)); // Top left

  array->AppendUV(Vector(0.0f, 1.0f, 0.0f));
  array->AppendUV(Vector(1.0f, 1.0f, 0.0f));
  array->AppendUV(Vector(1.0f, 0.0f, 0.0f));
  array->AppendUV(Vector(0.0f, 0.0f, 0.0f));

  const Vector kNormal(0.0f, 0.0f, 1.0f); // out of the screen
  array->AppendNormal(kNormal);

  RenderStatePtr state = RenderState::Create(m.contextWeak);
  state->SetTexture(m.browserSurface);
  state->SetMaterial(Color(0.4f, 0.4f, 0.4f), Color(1.0f, 1.0f, 1.0f), Color(0.0f, 0.0f, 0.0f), 0.0f);
  GeometryPtr geometry = Geometry::Create(m.contextWeak);
  geometry->SetVertexArray(array);
  geometry->SetRenderState(state);

  std::vector<int> index;
  index.push_back(1);
  index.push_back(2);
  index.push_back(3);
  index.push_back(4);
  std::vector<int> normalIndex;
  normalIndex.push_back(1);
  normalIndex.push_back(1);
  normalIndex.push_back(1);
  normalIndex.push_back(1);
  geometry->AddFace(index, index, normalIndex);

  // Draw the back for now
  index.clear();
  index.push_back(1);
  index.push_back(4);
  index.push_back(3);
  index.push_back(2);

  array->AppendNormal(-kNormal);
  normalIndex.clear();
  normalIndex.push_back(2);
  normalIndex.push_back(2);
  normalIndex.push_back(2);
  normalIndex.push_back(2);
  geometry->AddFace(index, index, normalIndex);

  m.browser = Transform::Create(m.contextWeak);
  m.browser->AddNode(geometry);
  m.root->AddNode(m.browser);
}

BrowserWorld::BrowserWorld(State& aState) : m(aState) {}

BrowserWorld::~BrowserWorld() {}