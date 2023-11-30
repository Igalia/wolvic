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
class Widget;
typedef std::shared_ptr<Widget> WidgetPtr;

class BrowserWorld : public DeviceDelegate::ReorientClient {
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
  bool IsGLInitialized() const;
  void ShutdownJava();
  void ShutdownGL();
  void Draw(){
    StartFrame();
    Draw(device::Eye::Left);
    Draw(device::Eye::Right);
    EndFrame();
  }
  void StartFrame();
  void Draw(device::Eye aEye);
  void EndFrame();
  void TriggerHapticFeedback(const float aPulseDuration, const float aPulseIntensity);
  void TogglePassthrough();
  void SetHeadLockEnabled(const bool isEnabled);
  void SetTemporaryFilePath(const std::string& aPath);
  void UpdateEnvironment();
  void UpdatePointerColor();
  void SetSurfaceTexture(const std::string& aName, jobject& aSurface);
  void AddWidget(int32_t aHandle, const WidgetPlacementPtr& placement);
  void UpdateWidget(int32_t aHandle, const WidgetPlacementPtr& aPlacement);
  void UpdateWidgetRecursive(int32_t aHandle, const WidgetPlacementPtr& aPlacement);
  void RemoveWidget(int32_t aHandle);
  void RecreateWidgetSurface(int32_t aHandle);
  void StartWidgetResize(int32_t aHandle, const vrb::Vector& aMaxSize, const vrb::Vector& aMinSize);
  void FinishWidgetResize(int32_t aHandle);
  void StartWidgetMove(int32_t aHandle, const int32_t aMoveBehavour);
  void FinishWidgetMove();
  void UpdateVisibleWidgets();
  void LayoutWidget(int32_t aHandle);
  void SetBrightness(const float aBrightness);
  void ExitImmersive();
  void ShowVRVideo(const int aWindowHandle, const int aVideoProjection);
  void HideVRVideo();
  void SetControllersVisible(const bool aVisible);
  enum class YawTarget { ALL, WIDGETS };
  void RecenterUIYaw(const YawTarget aTarget);
  void SetCylinderDensity(const float aDensity);
  enum class WebXRInterstialState { FORCED, ALLOW_DISMISS, HIDDEN };
  void SetWebXRInterstitalState(const WebXRInterstialState aState);
  void SetIsServo(const bool aIsServo);
  void SetCPULevel(const device::CPULevel aLevel);
  JNIEnv* GetJNIEnv() const;
  void OnReorient() override;
#if HVR
  bool WasButtonAppPressed();
#endif
protected:
  struct State;
  static BrowserWorldPtr Create();
  BrowserWorld(State& aState);
  ~BrowserWorld() = default;
  void TickWorld();
  void TickImmersive();
  void TickSplashAnimation();
  void TickWebXRInterstitial();
  void DrawWorld(device::Eye aEye);
  void DrawImmersive(device::Eye aEye);
  void DrawWebXRInterstitial(device::Eye aEye);
  void DrawSplashAnimation(device::Eye aEye);
  void CreateSkyBox(const std::string& aBasePath, const std::string& aExtension);
private:
#if defined(OCULUSVR) && defined(STORE_BUILD)
  void ProcessOVRPlatformEvents();
#endif
  State& m;
  BrowserWorld() = delete;
  VRB_NO_DEFAULTS(BrowserWorld)
};

} // namespace crow

#endif // BROWSERWORLD_H
