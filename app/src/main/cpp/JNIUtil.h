/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_JNIUTIL_H
#define VRBROWSER_JNIUTIL_H

#include <jni.h>

namespace crow {

jmethodID FindJNIMethodID(JNIEnv* aEnv, jclass aClass, const char* aName, const char* aSignature, const bool aIsStatic = false);
bool ValidateMethodID(JNIEnv* aEnv, jobject aObject, jmethodID aMethod, const char* aName);
bool ValidateStaticMethodID(JNIEnv* aEnv, jclass aClass, jmethodID aMethod, const char* aName);
void CheckJNIException(JNIEnv* aEnv, const char* aName);

struct JavaContext {
  jobject activity { nullptr };
  JavaVM* vm { nullptr };
  JNIEnv* env { nullptr };
};

} // namespace crow

#endif //VRBROWSER_JNIUTIL_H
