#include <jni.h>
#include <string>
#include <GLES2/gl2.h>

#include "BrowserWorld.h"
#include "Logger.h"
#include "Camera.h"
#include "vrb/Matrix.h"

static BrowserWorld sWorld;
static Camera sCamera;
static vrb::Matrix sPerspective;

#define JNI_METHOD(return_type, method_name) \
  JNIEXPORT return_type JNICALL              \
    Java_org_mozilla_vrbrowser_VRBrowserActivity_##method_name

extern "C" {

JNI_METHOD(void, initGL)
(JNIEnv*, jobject) {
  // Nothing to do yet
  sWorld.Init();
}

JNI_METHOD(void, updateGL)
(JNIEnv*, jobject, int width, int height) {
  glViewport(0, 0, width, height);
  glClearColor(0.0, 0.0, 0.0, 1.0);
  float fovX = width > height ? 60.0f : -1.0f;
  float fovY = width > height ? -1.0f : 60.0f;
  VRLOG("FOV:%f, %f (%dx%d)", fovX, fovY, width, height);
  sPerspective = vrb::Matrix::PerspectiveMatrixWithResolutionDegrees(width, height, fovX, fovY, 1.0f, 100.0f);
}

JNI_METHOD(void, drawGL)
(JNIEnv*, jobject) {
  glClear(GL_COLOR_BUFFER_BIT);
  sWorld.Draw(sPerspective, sCamera.GetView());
}

} // extern "C"