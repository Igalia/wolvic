#ifndef BROWSERWORLD_H
#define BROWSERWORLD_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"

#include <jni.h>
#include <memory>

class BrowserWorld;
typedef std::shared_ptr<BrowserWorld> BrowserWorldPtr;
typedef std::weak_ptr<BrowserWorld> BrowserWorldWeakPtr;

class BrowserWorld {
public:
  static BrowserWorldPtr Create();
  vrb::ContextPtr GetContext();
  void Pause();
  void Resume();
  void SetViewport(const float aWidth, const float aHeight);
  void InitializeJava(JNIEnv* aEnv, jobject& aActivity, jobject& aAssetManager);
  void InitializeGL();
  void Shutdown();
  void Draw();

  void SetSurfaceTexture(const std::string& aName, jobject& aSurface);
  void CreateBrowser();

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
