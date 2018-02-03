#include <jni.h>
#include <string>
#include <GLES2/gl2.h>

#include "BrowserWorld.h"
#include "vrb/Logger.h"
#include "vrb/GLError.h"

static BrowserWorldPtr sWorld;

#define JNI_METHOD(return_type, method_name) \
  JNIEXPORT return_type JNICALL              \
    Java_org_mozilla_vrbrowser_VRBrowserActivity_##method_name

extern "C" {

JNI_METHOD(void, activityPaused)
(JNIEnv*, jobject) {
  if (sWorld) {
    sWorld->Pause();
  }
}

JNI_METHOD(void, activityResumed)
(JNIEnv*, jobject) {
  if (sWorld) {
    sWorld->Resume();
  }
}

JNI_METHOD(void, activityCreated)
(JNIEnv* aEnv, jobject aActivity, jobject aAssetManager) {
  if (!sWorld) {
    sWorld = BrowserWorld::Create();
  }

  sWorld->InitializeJava(aEnv, aActivity, aAssetManager);
}

JNI_METHOD(void, activityDestroyed)
(JNIEnv* env, jobject) {
  if (sWorld) {
    sWorld->ShutdownJava();
  }
}

JNI_METHOD(void, initializeGL)
(JNIEnv*, jobject) {
  if (!sWorld) {
    sWorld = BrowserWorld::Create();
  }

  sWorld->InitializeGL();
}

JNI_METHOD(void, updateGL)
(JNIEnv*, jobject, int width, int height) {
  VRB_CHECK(glViewport(0, 0, width, height));
  VRB_CHECK(glClearColor(0.0, 0.0, 0.0, 1.0));
  VRB_CHECK(glEnable(GL_DEPTH_TEST));
  VRB_CHECK(glEnable(GL_CULL_FACE));
  // VRB_CHECK(glDisable(GL_CULL_FACE));
  sWorld->SetViewport(width, height);
}



JNI_METHOD(void, drawGL)
(JNIEnv*, jobject) {
  VRB_CHECK(glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT));
  sWorld->Draw();
}

JNI_METHOD(void, shutdownGL)
(JNIEnv*, jobject) {
  if (sWorld) {
    sWorld->ShutdownGL();
  }
}

} // extern "C"