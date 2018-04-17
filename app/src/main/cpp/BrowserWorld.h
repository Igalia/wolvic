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

namespace crow {

class BrowserWorld;
typedef std::shared_ptr<BrowserWorld> BrowserWorldPtr;
typedef std::weak_ptr<BrowserWorld> BrowserWorldWeakPtr;
class WidgetPlacement;

class BrowserWorld {
public:
  static BrowserWorldPtr Create();
  static BrowserWorldPtr& Instance();
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
  void AddWidget(const WidgetPlacement& placement, int32_t aCallbackId);
  void SetWidgetVisible(int32_t aHandle, bool aVisible);
  void RemoveWidget(int32_t aHandle);
  JNIEnv* GetJNIEnv() const;
protected:
  struct State;
  BrowserWorld(State& aState);
  ~BrowserWorld();
  void CreateFloor();
  void AddControllerPointer();
private:
  State& m;
  BrowserWorld() = delete;
  VRB_NO_DEFAULTS(BrowserWorld)
};

} // namespace crow

#endif // BROWSERWORLD_H
