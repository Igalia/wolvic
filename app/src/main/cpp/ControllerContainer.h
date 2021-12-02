/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_CONTROLLER_CONTAINER_H
#define VRBROWSER_CONTROLLER_CONTAINER_H

#include "ControllerDelegate.h"
#include "Controller.h"

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"
#include "vrb/Matrix.h"
#include "vrb/LoaderThread.h"
#include <memory>
#include <string>
#include <vector>

namespace crow {

class ControllerContainer;
typedef std::shared_ptr<ControllerContainer> ControllerContainerPtr;

class ControllerContainer : public crow::ControllerDelegate  {
public:
  enum class HandEnum { Left, Right };
  static ControllerContainerPtr Create(vrb::CreationContextPtr& aContext, const vrb::GroupPtr& aPointerContainer, const vrb::ModelLoaderAndroidPtr& aLoader);
  vrb::TogglePtr GetRoot() const;
  void LoadControllerModel(const int32_t aModelIndex, const vrb::ModelLoaderAndroidPtr& aLoader, const std::string& aFileName);
  void LoadControllerModel(const int32_t aModelIndex);
  void SetControllerModelTask(const int32_t aModelIndex, const vrb::LoadTask& aTask);
  void InitializeBeam();
  void Reset();
  std::vector<Controller>& GetControllers();
  const std::vector<Controller>& GetControllers() const;
  // crow::ControllerDelegate interface
  uint32_t GetControllerCount() override;
  void CreateController(const int32_t aControllerIndex, const int32_t aModelIndex, const std::string& aImmersiveName) override;
  void CreateController(const int32_t aControllerIndex, const int32_t aModelIndex, const std::string& aImmersiveName, const vrb::Matrix& aBeamTransform) override;
  void SetImmersiveBeamTransform(const int32_t aControllerIndex, const vrb::Matrix& aImmersiveBeamTransform) override;
  void SetBeamTransform(const int32_t aControllerIndex, const vrb::Matrix& aBeamTransform) override;
  void SetFocused(const int32_t aControllerIndex) override;
  void DestroyController(const int32_t aControllerIndex) override;
  void SetCapabilityFlags(const int32_t aControllerIndex, const device::CapabilityFlags aFlags) override;
  void SetEnabled(const int32_t aControllerIndex, const bool aEnabled) override;
  void SetControllerType(const int32_t aControllerIndex, device::DeviceType aType) override;
  void SetTargetRayMode(const int32_t aControllerIndex, device::TargetRayMode aMode) override;
  void SetTransform(const int32_t aControllerIndex, const vrb::Matrix& aTransform) override;
  void SetButtonCount(const int32_t aControllerIndex, const uint32_t aNumButtons) override;
  void SetButtonState(const int32_t aControllerIndex, const Button aWhichButton, const int32_t aImmersiveIndex, const bool aPressed, const bool aTouched, const float aImmersiveTrigger = -1.0f) override;
  void SetAxes(const int32_t aControllerIndex, const float* aData, const uint32_t aLength) override;
  void SetHapticCount(const int32_t aControllerIndex, const uint32_t aNumHaptics) override;
  uint32_t GetHapticCount(const int32_t aControllerIndex) override;
  void SetHapticFeedback(const int32_t aControllerIndex, const uint64_t aInputFrameID, const float aPulseDuration, const float aPulseIntensity) override;
  void GetHapticFeedback(const int32_t aControllerIndex, uint64_t &aInputFrameID, float& aPulseDuration, float& aPulseIntensity) override;
  void SetSelectActionStart(const int32_t aControllerIndex) override;
  void SetSelectActionStop(const int32_t aControllerIndex) override;
  void SetSqueezeActionStart(const int32_t aControllerIndex) override;
  void SetSqueezeActionStop(const int32_t aControllerIndex) override;
  void SetLeftHanded(const int32_t aControllerIndex, const bool aLeftHanded) override;
  void SetTouchPosition(const int32_t aControllerIndex, const float aTouchX, const float aTouchY) override;
  void EndTouch(const int32_t aControllerIndex) override;
  void SetScrolledDelta(const int32_t aControllerIndex, const float aScrollDeltaX, const float aScrollDeltaY) override;
  void SetBatteryLevel(const int32_t aControllerIndex, const int32_t aBatteryLevel) override;
  void SetPointerColor(const vrb::Color& color) const;
  bool IsVisible() const override;
  void SetVisible(const bool aVisible) override;
  void SetGazeModeIndex(const int32_t aControllerIndex) override;
  void SetFrameId(const uint64_t aFrameId);
protected:
  struct State;
  ControllerContainer(State& aState, vrb::CreationContextPtr& aContext);
  ~ControllerContainer();
private:
  State& m;
  ControllerContainer() = delete;
  VRB_NO_DEFAULTS(ControllerContainer)
};

} // namespace crow

#endif //VRBROWSER_CONTROLLER_CONTAINER_H
