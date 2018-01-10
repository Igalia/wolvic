#include <jni.h>
#include <string>
#include <GLES2/gl2.h>

#include "BrowserWorld.h"
#include "vrb/Logger.h"

static BrowserWorldPtr sWorld;

#define JNI_METHOD(return_type, method_name) \
  JNIEXPORT return_type JNICALL              \
    Java_org_mozilla_vrbrowser_VRBrowserActivity_##method_name

extern "C" {

JNI_METHOD(void, activityPaused)
(JNIEnv*, jobject) {
}

JNI_METHOD(void, activityResumed)
(JNIEnv*, jobject) {
}

JNI_METHOD(void, activityCreated)
(JNIEnv* aEnv, jobject, jobject aAssetManager) {
  if (!sWorld) {
    sWorld = BrowserWorld::Create();
  }

  sWorld->InitializeJava(aEnv, aAssetManager);
}

JNI_METHOD(void, activityDestroyed)
(JNIEnv* env, jobject) {
  if (sWorld) {
    sWorld->Shutdown();
  }
}

JNI_METHOD(void, initGL)
(JNIEnv*, jobject) {
  if (!sWorld) {
    sWorld = BrowserWorld::Create();
  }

  sWorld->InitializeGL();
}

JNI_METHOD(void, updateGL)
(JNIEnv*, jobject, int width, int height) {
  glViewport(0, 0, width, height);
  glClearColor(0.0, 0.0, 0.0, 1.0);
  sWorld->SetViewport(width, height);
}

JNI_METHOD(void, drawGL)
(JNIEnv*, jobject) {
  glClear(GL_COLOR_BUFFER_BIT);
  sWorld->Draw();
}

} // extern "C"