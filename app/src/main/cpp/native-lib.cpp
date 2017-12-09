#include <jni.h>
#include <string>
#include <GLES2/gl2.h>

#include "BrowserWorld.h"
#include "BrowserWorldFileReader.h"
#include "vrb/Logger.h"
#include "Camera.h"
#include "vrb/Matrix.h"
#include "vrb/Parser_obj.h"
#include "vrb/RenderObjectFactory_obj.h"
#include "vrb/RenderObject.h"

static BrowserWorld sWorld;
static Camera sCamera;
static vrb::Matrix sPerspective;
static vrb::Parser_obj sParser;
static vrb::RenderObjectFactory_obj sFactory;
static BrowserWorldFileReaderPtr sFileReader;

#define JNI_METHOD(return_type, method_name) \
  JNIEXPORT return_type JNICALL              \
    Java_org_mozilla_vrbrowser_VRBrowserActivity_##method_name

extern "C" {

JNI_METHOD(void, activityPaused)
(JNIEnv* env, jobject) {
}

JNI_METHOD(void, activityResumed)
(JNIEnv* env, jobject) {
}

JNI_METHOD(void, activityCreated)
(JNIEnv* env, jobject aActivity) {
  if (!sFileReader) {
    sFileReader = BrowserWorldFileReader::Create();
  }
  sFileReader->SetActivity(env, aActivity);
}

JNI_METHOD(void, activityDestroyed)
(JNIEnv* env, jobject) {
  if (sFileReader) {
    sFileReader->ClearActivity();
  }
}

JNI_METHOD(void, startModel)
(JNIEnv* env, jobject, jstring aName) {
  const char *nativeString = env->GetStringUTFChars(aName, 0);
  const std::string fileName = nativeString;
  env->ReleaseStringUTFChars(aName, nativeString);
  sParser.SetObserver(&sFactory);
  sFactory.CreateRenderObject(fileName);
  sParser.Start();
}

JNI_METHOD(void, parseChunk)
(JNIEnv* env, jobject, jbyteArray array, jint size) {
  jbyte* buffer = env->GetByteArrayElements(array, NULL);
  sParser.ParseChunk((const char*)buffer, size_t(size));
  env->ReleaseByteArrayElements(array, buffer, 0);
}

JNI_METHOD(void, finishModel)
(JNIEnv*, jobject) {
  sParser.Finish();
  sFactory.FinishRenderObject();
}

JNI_METHOD(void, initGL)
(JNIEnv*, jobject) {
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
  sCamera.SetPosition(vrb::Vector(0.0f, 1.0f, 10.0f));
  sCamera.SetHeading(0.0f); // M_PI);
  sCamera.SetPitch(-M_PI * 0.5);
  VRLOG("View: %s", sCamera.GetView().ToString().c_str());
  VRLOG("Perspective: %s", sPerspective.ToString().c_str());
}

JNI_METHOD(void, drawGL)
(JNIEnv*, jobject) {
  glClear(GL_COLOR_BUFFER_BIT);
  std::vector<vrb::RenderObjectPtr> models;
  sFactory.GetLoadedRenderObjects(models);
  for (auto object: models) {
    object->Init();
    // object->Dump();
    sWorld.AddRenderObject(object);
  }
  sWorld.Draw(sPerspective, sCamera.GetView());
}

} // extern "C"