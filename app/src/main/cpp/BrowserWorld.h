/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef BROWSERWORLD_H
#define BROWSERWORLD_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"

#include "DeviceDelegate.h"

#include <jni.h>
#include <memory>

class BrowserWorld;
typedef std::shared_ptr<BrowserWorld> BrowserWorldPtr;
typedef std::weak_ptr<BrowserWorld> BrowserWorldWeakPtr;

class BrowserWorld {
public:
  static BrowserWorldPtr Create();
  vrb::ContextWeak GetWeakContext();
  void RegisterDeviceDelegate(DeviceDelegatePtr aDelegate);
  void Pause();
  void Resume();
  bool IsPaused() const;
  void InitializeJava(JNIEnv* aEnv, jobject& aActivity, jobject& aAssetManager);
  void InitializeGL();
  void ShutdownJava();
  void ShutdownGL();
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
