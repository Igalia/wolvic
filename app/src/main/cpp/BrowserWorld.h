#ifndef BROWSERWORLD_H
#define BROWSERWORLD_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"

#include <jni.h>
#include <memory>

class BrowserWorld;
typedef std::shared_ptr<BrowserWorld> BrowserWorldPtr;

class BrowserWorld {
public:
  static BrowserWorldPtr Create();
  vrb::ContextPtr GetContext();
  void SetViewport(const float aWidth, const float aHight);
  void InitializeJava(JNIEnv* aEnv, jobject& aAssetManager);
  void InitializeGL();
  void Shutdown();
  //void AddRenderObject(vrb::RenderObjectPtr& aObject);
  void Draw();

protected:
  struct State;
  BrowserWorld(State& aState);
  ~BrowserWorld();

private:
  State& m;
  BrowserWorld() = delete;
  VRB_NO_DEFAULTS(BrowserWorld)
};

#endif // BROWSERWORLD_H
