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
typedef std::shared_ptr<WidgetPlacement> WidgetPlacementPtr;

class BrowserWorld {
public:
  static BrowserWorld& Instance();
  static void Destroy();
  vrb::RenderContextPtr& GetRenderContext();
  void RegisterDeviceDelegate(DeviceDelegatePtr aDelegate);
  void Pause();
  void Resume();
  bool IsPaused() const;
  void InitializeJava(JNIEnv* aEnv, jobject& aActivity, jobject& aAssetManager);
  void InitializeGL();
  void ShutdownJava();
  void ShutdownGL();
  void Draw();
  void SetTemporaryFilePath(const std::string& aPath);
  void UpdateEnvironment();
  void UpdatePointerColor();
  void SetSurfaceTexture(const std::string& aName, jobject& aSurface);
  void AddWidget(int32_t aHandle, const WidgetPlacementPtr& placement);
  void UpdateWidget(int32_t aHandle, const WidgetPlacementPtr& aPlacement);
  void RemoveWidget(int32_t aHandle);
  void StartWidgetResize(int32_t aHandle);
  void FinishWidgetResize(int32_t aHandle);
  void UpdateVisibleWidgets();
  void LayoutWidget(int32_t aHandle);
  void FadeOut();
  void FadeIn();
  void ExitImmersive();
  JNIEnv* GetJNIEnv() const;
protected:
  struct State;
  static BrowserWorldPtr Create();
  BrowserWorld(State& aState);
  ~BrowserWorld();
  void DrawWorld();
  void DrawImmersive();
  void DrawLoadingAnimation();
  void DrawSplashAnimation();
  vrb::TransformPtr CreateSkyBox(const std::string& basePath);
  void LoadSkybox(const vrb::TransformPtr transform, const std::string& basePath);
  void CreateFloor();
  float DistanceToNode(const vrb::NodePtr& aNode, const vrb::Vector& aPosition) const;
private:
  State& m;
  BrowserWorld() = delete;
  VRB_NO_DEFAULTS(BrowserWorld)
};

} // namespace crow

#endif // BROWSERWORLD_H
