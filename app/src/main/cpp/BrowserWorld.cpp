#include "BrowserWorld.h"
#include "vrb/Logger.h"

#include <vector>

using namespace vrb;

struct BrowserWorld::State {
  std::vector<RenderObjectPtr> objects;
};

BrowserWorld::BrowserWorld() : m(*(new State)) {

}

BrowserWorld::~BrowserWorld() {
  delete &m;
}

void
BrowserWorld::AddRenderObject(vrb::RenderObjectPtr& aObject) {
  m.objects.push_back(aObject);
  VRLOG("m.object.size=%d",m.objects.size());

}

void
BrowserWorld::Init() {
  for (RenderObjectPtr object: m.objects) {
    VRLOG("Init object!");
    object->Init();
  }
}

static float sHeading = 0.0f;
void
BrowserWorld::Draw(const vrb::Matrix& aPerspective, const vrb::Matrix& aView) {
  for (RenderObjectPtr object: m.objects) {
    object->SetTransform(vrb::Matrix::Rotation(vrb::Vector(0.0f, 1.0f, 0.0f), sHeading));
    object->Draw(aView.Inverse().PreMultiply(aPerspective));
  }
  sHeading += M_PI / 60.0f;
  if (sHeading > (2.0f * M_PI)) { sHeading = 0.0f; }
}