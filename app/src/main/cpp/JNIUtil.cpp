/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "JNIUtil.h"
#include "vrb/Logger.h"

namespace crow {
jmethodID
FindJNIMethodID(JNIEnv* aEnv, jclass aClass, const char* aName, const char* aSignature, const bool aIsStatic) {
  if (!aEnv) {
    VRB_ERROR("Null JNIEnv, unable to find JNI method: %s", aName);
    return nullptr;
  }
  if (!aClass) {
    VRB_ERROR("Null java class. Unable to find JNI method: %s", aName);
    return nullptr;
  }
  jmethodID result = nullptr;
  if (aIsStatic) {
    result = aEnv->GetStaticMethodID(aClass, aName, aSignature);
  } else {
    result = aEnv->GetMethodID(aClass, aName, aSignature);
  }
  if (aEnv->ExceptionCheck() == JNI_TRUE) {
    VRB_ERROR("Failed to find JNI method: %s %s", aName, aSignature);
    aEnv->ExceptionClear();
    return nullptr;
  }
  return result;
}

bool
ValidateMethodID(JNIEnv* aEnv, jobject aObject, jmethodID aMethod, const char* aName) {
  if (!aEnv) {
    VRB_ERROR("JNI::%s failed. Java not initialized.", aName);
    return false;
  }
  if (!aMethod) {
    VRB_ERROR("JNI::%s failed. Java method is null", aName);
    return false;
  }
  if (!aObject) {
    VRB_ERROR("JNI::%s failed. Java object is null", aName);
    return false;
  }
  return true;
}

bool
ValidateStaticMethodID(JNIEnv* aEnv, jclass aClass, jmethodID aMethod, const char* aName) {
  if (!aEnv) {
    VRB_ERROR("JNI::%s failed. Java not initialized.", aName);
    return false;
  }
  if (!aMethod) {
    VRB_ERROR("JNI::%s failed. Java method is null", aName);
    return false;
  }
  if (!aClass) {
    VRB_ERROR("JNI::%s failed. Java class is null", aName);
    return false;
  }
  return true;
}

void
CheckJNIException(JNIEnv* aEnv, const char* aName) {
  if (!aEnv) {
    return;
  }
  if (aEnv->ExceptionCheck() == JNI_TRUE) {
    aEnv->ExceptionClear();
    VRB_ERROR("Java exception encountered when calling %s", aName);
  }
}

}
