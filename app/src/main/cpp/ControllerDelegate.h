/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef CONTROLLER_DELEGATE_DOT_H
#define CONTROLLER_DELEGATE_DOT_H

#include "vrb/MacroUtils.h"
#include "vrb/Forward.h"
#include "vrb/Quaternion.h"
#include "vrb/Vector.h"
#include "Device.h"
#include "GestureDelegate.h"

#include <memory>
#include <string>
#include <vector>

namespace crow {

class ControllerDelegate;
typedef std::shared_ptr<ControllerDelegate> ControllerDelegatePtr;

enum class ControllerMode { None, Device, Hand };



// Follows XR_EXT_hand_tracking' XrHandJointEXT enum
enum class HandTrackingJoints {
  Palm, Wrist,
  ThumbMetacarpal, ThumbProximal, ThumbDistal, ThumbTip,
  IndexMetacarpal, IndexProximal, IndexIntermediate, IndexDistal, IndexTip,
  MiddleMetacarpal, MiddleProximal, MiddleIntermediate, MiddleDistal, MiddleTip,
  RingMetacarpal, RingProximal, RingIntermediate, RingDistal, RingTip,
  LittleMetacarpal, LittleProximal, LittleIntermediate, LittleDistal, LittleTip };

class ControllerDelegate {
public:
  enum Button {
    BUTTON_TRIGGER   = 1u << 0u,
    BUTTON_SQUEEZE   = 1u << 1u,
    BUTTON_TOUCHPAD  = 1u << 2u,
    BUTTON_APP       = 1u << 3u,
    BUTTON_A         = 1u << 4u,
    BUTTON_B         = 1u << 5u,
    BUTTON_X         = 1u << 6u,
    BUTTON_Y         = 1u << 7u,
    BUTTON_OTHERS    = 1u << 8u,  // Other buttons only for the immersive mode.
  };

  virtual void CreateController(const int32_t aControllerIndex, const int32_t aModelIndex, const std::string& aImmersiveName) = 0;
  virtual void CreateController(const int32_t aControllerIndex, const int32_t aModelIndex, const std::string& aImmersiveName, const vrb::Matrix& aBeamTransform) = 0;
  virtual void SetImmersiveBeamTransform(const int32_t aControllerIndex, const vrb::Matrix& aImmersiveBeamTransform) = 0;
  virtual void SetBeamTransform(const int32_t aControllerIndex, const vrb::Matrix& aBeamTransform) = 0;
  virtual void SetFocused(const int32_t aControllerIndex) = 0;
  virtual void DestroyController(const int32_t aControllerIndex) = 0;
  virtual uint32_t GetControllerCount() = 0;
  virtual void SetCapabilityFlags(const int32_t aControllerIndex, const device::CapabilityFlags aFlags) = 0;
  virtual void SetEnabled(const int32_t aControllerIndex, const bool aEnabled) = 0;
  virtual void SetControllerType(const int32_t aControllerIndex, device::DeviceType aType) = 0;
  virtual void SetTargetRayMode(const int32_t aControllerIndex, device::TargetRayMode aMode) = 0;
  virtual void SetTransform(const int32_t aControllerIndex, const vrb::Matrix& aTransform) = 0;
  virtual void TranslateTransform(const int32_t aControllerIndex, const vrb::Vector& aTranslation) = 0;
  virtual void SetButtonCount(const int32_t aControllerIndex, const uint32_t aNumButtons) = 0;
  virtual void SetButtonState(const int32_t aControllerIndex, const Button aWhichButton, const int32_t aImmersiveIndex, const bool aPressed, const bool aTouched, const float aImmersiveTrigger = -1.0f) = 0;
  virtual void SetAxes(const int32_t aControllerIndex, const float* aData, const uint32_t aLength) = 0;
  virtual void SetHapticCount(const int32_t aControllerIndex, const uint32_t aNumHaptics) = 0;
  virtual uint32_t GetHapticCount(const int32_t aControllerIndex) = 0;
  virtual void SetHapticFeedback(const int32_t aControllerIndex, const uint64_t aInputFrameID, const float aPulseDuration, const float aPulseIntensity) = 0;
  virtual void GetHapticFeedback(const int32_t aControllerIndex, uint64_t & aInputFrameID, float& aPulseDuration, float& aPulseIntensity) = 0;
  virtual void SetSelectActionStart(const int32_t aControllerIndex) = 0;
  virtual void SetSelectActionStop(const int32_t aControllerIndex) = 0;
  virtual void SetSqueezeActionStart(const int32_t aControllerIndex) = 0;
  virtual void SetSqueezeActionStop(const int32_t aControllerIndex) = 0;
  virtual void SetLeftHanded(const int32_t aControllerIndex, const bool aLeftHanded) = 0;
  virtual void SetTouchPosition(const int32_t aControllerIndex, const float aTouchX, const float aTouchY) = 0;
  virtual void EndTouch(const int32_t aControllerIndex) = 0;
  virtual void SetScrolledDelta(const int32_t aControllerIndex, const float aScrollDeltaX, const float aScrollDeltaY) = 0;
  virtual void SetBatteryLevel(const int32_t aControllerIndex, const int32_t aBatteryLevel) = 0;
  virtual bool IsVisible() const = 0;
  virtual void SetVisible(const bool aVisible) = 0;
  virtual void SetGazeModeIndex(const int32_t aControllerIndex) = 0;
  virtual void SetHandJointLocations(const int32_t aControllerIndex, std::vector<vrb::Matrix> jointTransforms, std::vector<float> jointRadii) = 0;
  virtual void SetAimEnabled(const int32_t aControllerIndex, bool aEnabled = true) = 0;
  virtual void SetHandActionEnabled(const int32_t aControllerIndex, bool aEnabled = false) = 0;
  virtual void SetMode(const int32_t aControllerIndex, ControllerMode aMode = ControllerMode::None) = 0;
  virtual void SetSelectFactor(const int32_t aControllerIndex, float aFactor = 1.0f) = 0;
protected:
  ControllerDelegate() {}
private:
  VRB_NO_DEFAULTS(ControllerDelegate)
};

}

#endif // CONTROLLER_DELEGATE_DOT_H
