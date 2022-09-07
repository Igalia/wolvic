/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include <vrb/include/vrb/ConcreteClass.h>
#include <vrb/include/vrb/GLError.h>
#include "GeckoSurfaceTexture.h"
#include "JNIUtil.h"

#include "vrb/ClassLoaderAndroid.h"
#include "vrb/Logger.h"

namespace {

vrb::ClassLoaderAndroidPtr sClassLoader;
JNIEnv* sEnv;
jobject sActivity;
jclass sGeckoSurfaceTextureClass;
jmethodID sLookup;
jmethodID sAttachToGLContext;
jmethodID sIsAttachedToGLContext;
jmethodID sDetachFromGLContext;
jmethodID sUpdateTexImage;
jmethodID sReleaseTexImage;
jmethodID sIncrementUse;
jmethodID sDecrementUse;

const char* kClassName = "org/mozilla/gecko/gfx/GeckoSurfaceTexture";
const char* kLookupName = "lookup";
const char* kLookupSignature = "(I)Lorg/mozilla/gecko/gfx/GeckoSurfaceTexture;";
const char* kAttachToGLContextName = "attachToGLContext";
const char* kAttachToGLContextSignature = "(JI)V";
const char* kIsAttachedToGLContextName = "isAttachedToGLContext";
const char* kIsAttachedToGLContextSignature = "(J)Z";
const char* kDetachFromGLContextName = "detachFromGLContext";
const char* kDetachFromGLContextSignature = "()V";
const char* kUpdateTexImageName = "updateTexImage";
const char* kUpdateTexImageSignature= "()V";
const char* kReleaseTexImageName = "releaseTexImage";
const char* kReleaseTexImageSignature = "()V";
const char* kIncrementUseName = "incrementUse";
const char* kIncrementUseSignature = "()V";
const char* kDecrementUseName = "decrementUse";
const char* kDecrementUseSignature = "()V";

}

namespace crow {

struct GeckoSurfaceTexture::State {
  jobject surface;
  GLuint texture;
  State()
      : surface(nullptr), texture(0)
  {}
  ~State() = default;
  void Shutdown() {
    if (surface && sEnv) {
      sEnv->DeleteGlobalRef(surface);
      surface = nullptr;
    }
    if (texture) {
      VRB_GL_CHECK(glDeleteTextures(1, &texture));
    }
  }
};

void
GeckoSurfaceTexture::InitializeJava(JNIEnv* aEnv, jobject aActivity) {
  if (aEnv == sEnv) {
    return;
  }
  sEnv = aEnv;
  if (!sEnv) {
    return;
  }
  sClassLoader = vrb::ClassLoaderAndroid::Create();
  sClassLoader->Init(aEnv, aActivity);
  sActivity = sEnv->NewGlobalRef(aActivity);
  jclass foundClass = sClassLoader->FindClass(kClassName);
  if (!foundClass) {
    return;
  }
  sGeckoSurfaceTextureClass = (jclass)sEnv->NewGlobalRef(foundClass);
  sEnv->DeleteLocalRef(foundClass);
  sLookup = FindJNIMethodID(sEnv, sGeckoSurfaceTextureClass, kLookupName, kLookupSignature, /*aIsStatic*/ true);
  sAttachToGLContext = FindJNIMethodID(sEnv, sGeckoSurfaceTextureClass, kAttachToGLContextName, kAttachToGLContextSignature);
  sIsAttachedToGLContext = FindJNIMethodID(sEnv, sGeckoSurfaceTextureClass, kIsAttachedToGLContextName, kIsAttachedToGLContextSignature);
  sDetachFromGLContext = FindJNIMethodID(sEnv, sGeckoSurfaceTextureClass, kDetachFromGLContextName, kDetachFromGLContextSignature);
  sUpdateTexImage = FindJNIMethodID(sEnv, sGeckoSurfaceTextureClass, kUpdateTexImageName, kUpdateTexImageSignature);
  sReleaseTexImage = FindJNIMethodID(sEnv, sGeckoSurfaceTextureClass, kReleaseTexImageName, kReleaseTexImageSignature);
  sIncrementUse = FindJNIMethodID(sEnv, sGeckoSurfaceTextureClass, kIncrementUseName, kIncrementUseSignature);
  sDecrementUse = FindJNIMethodID(sEnv, sGeckoSurfaceTextureClass, kDecrementUseName, kDecrementUseSignature);
}

void
GeckoSurfaceTexture::ShutdownJava() {
  if (sEnv) {
    if (sClassLoader) {
      sClassLoader->Shutdown();
      sClassLoader = nullptr;
    }
    if (sActivity) {
      sEnv->DeleteGlobalRef(sActivity);
      sActivity = nullptr;
    }
    if (sGeckoSurfaceTextureClass) {
      sEnv->DeleteGlobalRef(sGeckoSurfaceTextureClass);
      sGeckoSurfaceTextureClass = nullptr;
    }
    sLookup = nullptr;
    sAttachToGLContext = nullptr;
    sReleaseTexImage = nullptr;
    sUpdateTexImage = nullptr;
    sIncrementUse = nullptr;
    sDecrementUse = nullptr;
    sEnv = nullptr;
  }
}

GeckoSurfaceTexturePtr
GeckoSurfaceTexture::Create(const int32_t aHandle) {
  GeckoSurfaceTexturePtr result;
  if (!sEnv) {
    VRB_ERROR("Unable to create GeckoSurfaceTexture. Java not initialized?");
    return result;
  }
  if (!sLookup) {
    VRB_ERROR("GeckoSurfaceTexture.lookup method missing");
    return result;
  }
  jobject surface = sEnv->CallStaticObjectMethod(sGeckoSurfaceTextureClass, sLookup, aHandle);
  if (!surface) {
    VRB_ERROR("Unable to find GeckoSurfaceTexture with handle: %d", aHandle);
    return result;
  }
  result = std::make_shared<vrb::ConcreteClass<GeckoSurfaceTexture, GeckoSurfaceTexture::State> >();
  result->m.surface = sEnv->NewGlobalRef(surface);
  sEnv->DeleteLocalRef(surface);
  result->IncrementUse();
  return result;
}

GLuint
GeckoSurfaceTexture::GetTextureName() {
  return m.texture;
}

void
GeckoSurfaceTexture::AttachToGLContext(EGLContext aContext) {
  if (!ValidateMethodID(sEnv, m.surface, sAttachToGLContext, __FUNCTION__)) { return; }
  if (m.texture == 0) {
    VRB_GL_CHECK(glGenTextures(1, &(m.texture)));
    VRB_GL_CHECK(glBindTexture(GL_TEXTURE_EXTERNAL_OES, m.texture));
    VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR));
    VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR));
    VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE));
    VRB_GL_CHECK(glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE));
  }
  sEnv->CallVoidMethod(m.surface, sAttachToGLContext, (jlong)aContext, (jint)m.texture);
  CheckJNIException(sEnv, __FUNCTION__);
}

bool
GeckoSurfaceTexture::IsAttachedToGLContext(EGLContext aContext) const {
  if (!ValidateMethodID(sEnv, m.surface, sIsAttachedToGLContext, __FUNCTION__)) { return false; }
  bool result = sEnv->CallBooleanMethod(m.surface, sIsAttachedToGLContext, (jlong)aContext);
  CheckJNIException(sEnv, __FUNCTION__);
  return result;
}

void
GeckoSurfaceTexture::DetachFromGLContext() {
  if (!ValidateMethodID(sEnv, m.surface, sDetachFromGLContext, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(m.surface, sDetachFromGLContext);
  CheckJNIException(sEnv, __FUNCTION__);
}

void
GeckoSurfaceTexture::UpdateTexImage() {
  if (!ValidateMethodID(sEnv, m.surface, sUpdateTexImage, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(m.surface, sUpdateTexImage);
  CheckJNIException(sEnv, __FUNCTION__);
}

void
GeckoSurfaceTexture::ReleaseTexImage() {
  if (!ValidateMethodID(sEnv, m.surface, sReleaseTexImage, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(m.surface, sReleaseTexImage);
  CheckJNIException(sEnv, __FUNCTION__);
}

void
GeckoSurfaceTexture::IncrementUse() {
  if (!ValidateMethodID(sEnv, m.surface, sIncrementUse, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(m.surface, sIncrementUse);
  CheckJNIException(sEnv, __FUNCTION__);
}

void
GeckoSurfaceTexture::DecrementUse() {
  if (!ValidateMethodID(sEnv, m.surface, sDecrementUse, __FUNCTION__)) { return; }
  sEnv->CallVoidMethod(m.surface, sDecrementUse);
  CheckJNIException(sEnv, __FUNCTION__);
}

GeckoSurfaceTexture::GeckoSurfaceTexture(State& aState) : m(aState) {}
GeckoSurfaceTexture::~GeckoSurfaceTexture() {
  if (m.surface) {
    VRB_LOG("Destroy GeckoSurfaceTexture");
    ReleaseTexImage();
    if (IsAttachedToGLContext(eglGetCurrentContext())) {
      DetachFromGLContext();
    }
    DecrementUse();
  }
  m.Shutdown();
}

} // namespace crow
