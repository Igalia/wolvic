#include "BrowserWorld.h"
#include "vrb/CameraSimple.h"
#include "vrb/Color.h"
#include "vrb/ConcreteClass.h"
#include "vrb/Context.h"
#include "vrb/CullVisitor.h"
#include "vrb/DrawableList.h"
#include "vrb/Group.h"
#include "vrb/Light.h"
#include "vrb/Logger.h"
#include "vrb/Matrix.h"
#include "vrb/NodeFactoryObj.h"
#include "vrb/ParserObj.h"
#include "vrb/Transform.h"
#include "vrb/Vector.h"

using namespace vrb;

struct BrowserWorld::State {
  float heading;
  ContextPtr context;
  ContextWeak contextWeak;
  NodeFactoryObjPtr factory;
  ParserObjPtr parser;
  GroupPtr root;
  LightPtr light;
  TransformPtr model;
  CullVisitorPtr cullVisitor;
  DrawableListPtr drawList;
  CameraSimplePtr camera;


  State() : heading(0.0f) {
    context = Context::Create();
    contextWeak = context;
    factory = NodeFactoryObj::Create(contextWeak);
    parser = ParserObj::Create(contextWeak);
    parser->SetObserver(factory);
    root = Group::Create(contextWeak);
    light = Light::Create(contextWeak);
    root->AddLight(light);
    cullVisitor = CullVisitor::Create(contextWeak);
    drawList = DrawableList::Create(contextWeak);
    camera = CameraSimple::Create(contextWeak);
  }
};


BrowserWorldPtr
BrowserWorld::Create() {
  return std::make_shared<vrb::ConcreteClass<BrowserWorld, BrowserWorld::State> >();
}

vrb::ContextPtr
BrowserWorld::GetContext() {
  return m.context;
}

void
BrowserWorld::SetViewport(const float aWidth, const float aHight) {
  m.camera->SetViewport(aWidth, aHight);
  m.camera->SetTransform(Matrix::Position(Vector(0.0f, 0.0f, 0.2f)));
}

void
BrowserWorld::InitializeJava(JNIEnv* aEnv, jobject& aAssetManager) {
  if (m.context) {
    m.context->InitializeJava(aEnv, aAssetManager);
  }
}

void
BrowserWorld::InitializeGL() {
VRB_LINE;
  if (m.context) {
    if (!m.model) {
      m.model = Transform::Create(m.contextWeak);
      m.factory->SetModelRoot(m.model);
      m.parser->LoadModel("vr_controller_daydream.obj");
      //m.parser->LoadModel("teapot.obj");
      m.root->AddNode(m.model);
    }
    m.context->InitializeGL();
  }
}

void
BrowserWorld::Shutdown() {
  if (m.context) {
    m.context->Shutdown();
  }
}

void
BrowserWorld::Draw() {
  m.context->Update();
  m.model->SetTransform(vrb::Matrix::Rotation(vrb::Vector(0.0f, 0.0f, 1.0f), m.heading).PreMultiply(vrb::Matrix::Rotation(vrb::Vector(0.0f, 1.0f, 0.0f), m.heading)));
  //m.model->SetTransform(vrb::Matrix::Rotation(vrb::Vector(0.0f, 1.0f, 0.0f), m.heading));
  m.drawList->Reset();
  m.root->Cull(*m.cullVisitor, *m.drawList);
  m.drawList->Draw(*m.camera);
  m.heading += M_PI / 60.0f;
  if (m.heading > (2.0f * M_PI)) { m.heading = 0.0f; }
}

BrowserWorld::BrowserWorld(State& aState) : m(aState) {}

BrowserWorld::~BrowserWorld() {}