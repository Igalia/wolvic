/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "ExternalVR.h"
#include "VRBrowser.h"

#include "vrb/Matrix.h"
#include "vrb/Quaternion.h"
#include "vrb/Vector.h"
#include "moz_external_vr.h"
#include <pthread.h>
#include <unistd.h>

namespace {

const float SecondsToNanoseconds = 1e9f;
const int SecondsToNanosecondsI32 = int(1e9);
const int MicrosecondsToNanoseconds = 1000;

class Lock {
  pthread_mutex_t* mMutex;
  bool mLocked;
public:
  Lock() = delete;
  explicit Lock(pthread_mutex_t* aMutex) : mMutex(aMutex), mLocked(false) {
    if (pthread_mutex_lock(mMutex) == 0) {
      mLocked = true;
    }
  }

  ~Lock() {
    if (mLocked) {
      pthread_mutex_unlock(mMutex);
    }
  }

  bool IsLocked() {
    return mLocked;
  }

private:
  VRB_NO_DEFAULTS(Lock)
  VRB_NO_NEW_DELETE
};

class Wait {
  pthread_mutex_t* mMutex;
  pthread_cond_t* mCond;
  bool mLocked;
public:
  Wait() = delete;
  Wait(pthread_mutex_t* aMutex, pthread_cond_t* aCond)
      : mMutex(aMutex)
      , mCond(aCond)
      , mLocked(false)
  {}

  ~Wait() {
    if (mLocked) {
      pthread_mutex_unlock(mMutex);
    }
  }

  bool DoWait(const float aWait) {
    if (mLocked || pthread_mutex_lock(mMutex) == 0) {
      mLocked = true;
      if (aWait == 0.0f) {
        return pthread_cond_wait(mCond, mMutex) == 0;
      } else {
        float sec = 0;
        float nsec = modff(aWait, &sec);
        struct timeval tv = {};
        struct timespec ts = {};
        gettimeofday(&tv, nullptr);
        ts.tv_sec = tv.tv_sec + int(sec);
        ts.tv_nsec = (tv.tv_usec * MicrosecondsToNanoseconds) + int(SecondsToNanoseconds * nsec);
        if (ts.tv_nsec >= SecondsToNanosecondsI32) {
          ts.tv_nsec -= SecondsToNanosecondsI32;
          ts.tv_sec++;
        }
        return pthread_cond_timedwait(mCond, mMutex, &ts) == 0;
      }
    }
    return false;
  }

  bool IsLocked() {
    return mLocked;
  }

  void Lock() {
    if (mLocked) {
      return;
    }

    if (pthread_mutex_lock(mMutex) == 0) {
      mLocked = true;
    }
  }
  void Unlock() {
    if (mLocked) {
      mLocked = false;
      pthread_mutex_unlock(mMutex);
    }
  }

private:
  VRB_NO_DEFAULTS(Wait)
  VRB_NO_NEW_DELETE
};

} // namespace

namespace crow {

struct ExternalVR::State {
  static ExternalVR::State* sState;
  pthread_mutex_t* browserMutex = nullptr;
  pthread_cond_t* browserCond = nullptr;
  mozilla::gfx::VRBrowserState* sourceBrowserState = nullptr;
  mozilla::gfx::VRExternalShmem data = {};
  mozilla::gfx::VRSystemState system = {};
  mozilla::gfx::VRBrowserState browser = {};
  // device::CapabilityFlags deviceCapabilities = 0;
  vrb::Vector eyeOffsets[device::EyeCount];
  uint64_t lastFrameId = 0;
  bool firstPresentingFrame = false;
  bool compositorEnabled = false;
  bool waitingForExit = false;

  State() {
    pthread_mutex_init(&data.systemMutex, nullptr);
    pthread_mutex_init(&data.geckoMutex, nullptr);
    pthread_mutex_init(&data.servoMutex, nullptr);
    pthread_cond_init(&data.systemCond, nullptr);
    pthread_cond_init(&data.geckoCond, nullptr);
    pthread_cond_init(&data.servoCond, nullptr);
  }

  ~State() {
    pthread_mutex_destroy(&(data.systemMutex));
    pthread_mutex_destroy(&(data.geckoMutex));
    pthread_mutex_destroy(&(data.servoMutex));
    pthread_cond_destroy(&(data.systemCond));
    pthread_cond_destroy(&(data.geckoCond));
    pthread_cond_destroy(&(data.servoCond));
  }

  void Reset() {
    memset(&data, 0, sizeof(mozilla::gfx::VRExternalShmem));
    memset(&system, 0, sizeof(mozilla::gfx::VRSystemState));
    memset(&browser, 0, sizeof(mozilla::gfx::VRBrowserState));
    data.version = mozilla::gfx::kVRExternalVersion;
    data.size = sizeof(mozilla::gfx::VRExternalShmem);
    system.displayState.isConnected = true;
    system.displayState.isMounted = true;
    const vrb::Matrix identity = vrb::Matrix::Identity();
    memcpy(&(system.sensorState.leftViewMatrix), identity.Data(), sizeof(system.sensorState.leftViewMatrix));
    memcpy(&(system.sensorState.rightViewMatrix), identity.Data(), sizeof(system.sensorState.rightViewMatrix));
    system.sensorState.pose.orientation[3] = 1.0f;
    lastFrameId = 0;
    firstPresentingFrame = false;
    waitingForExit = false;
    SetSourceBrowser(VRBrowserType::Gecko);
  }

  static ExternalVR::State& Instance() {
    if (!sState) {
      sState = new State();
    }

    return *sState;
  }

  void PullBrowserStateWhileLocked() {
    const bool wasPresenting = IsPresenting();
    memcpy(&browser, sourceBrowserState, sizeof(mozilla::gfx::VRBrowserState));


    if ((!wasPresenting && IsPresenting()) || browser.navigationTransitionActive) {
      firstPresentingFrame = true;
    }
    if (wasPresenting && !IsPresenting()) {
      lastFrameId = browser.layerState[0].layer_stereo_immersive.frameId;
      waitingForExit = false;
    }
  }

  bool IsPresenting() const {
    return browser.presentationActive || browser.navigationTransitionActive || browser.layerState[0].type == mozilla::gfx::VRLayerType::LayerType_Stereo_Immersive;
  }

  void SetSourceBrowser(VRBrowserType aBrowser) {
    if (aBrowser == VRBrowserType::Gecko) {
      browserCond = &data.geckoCond;
      browserMutex = &data.geckoMutex;
      sourceBrowserState = &data.geckoState;
    } else {
      browserCond = &data.servoCond;
      browserMutex = &data.servoMutex;
      sourceBrowserState = &data.servoState;
    }
  }
};

ExternalVR::State * ExternalVR::State::sState = nullptr;

ExternalVRPtr
ExternalVR::Create() {
  return std::make_shared<ExternalVR>();
}

mozilla::gfx::VRExternalShmem*
ExternalVR::GetSharedData() {
  return &(m.data);
}

void
ExternalVR::SetDeviceName(const std::string& aName) {
  if (aName.length() == 0) {
    return;
  }
  strncpy(m.system.displayState.displayName, aName.c_str(),
          mozilla::gfx::kVRDisplayNameMaxLen - 1);
  m.system.displayState.displayName[mozilla::gfx::kVRDisplayNameMaxLen - 1] = '\0';
}

void
ExternalVR::SetCapabilityFlags(const device::CapabilityFlags aFlags) {
  uint16_t result = 0;
  if (device::Position & aFlags) {
    result |= static_cast<uint16_t>(mozilla::gfx::VRDisplayCapabilityFlags::Cap_Position);
  }
  if (device::Orientation & aFlags) {
    result |= static_cast<uint16_t>(mozilla::gfx::VRDisplayCapabilityFlags::Cap_Orientation);
  }
  if (device::Present & aFlags) {
    result |= static_cast<uint16_t>(mozilla::gfx::VRDisplayCapabilityFlags::Cap_Present);
  }
  if (device::AngularAcceleration & aFlags) {
    result |= static_cast<uint16_t>(mozilla::gfx::VRDisplayCapabilityFlags::Cap_AngularAcceleration);
  }
  if (device::LinearAcceleration & aFlags) {
    result |= static_cast<uint16_t>(mozilla::gfx::VRDisplayCapabilityFlags::Cap_LinearAcceleration);
  }
  if (device::StageParameters & aFlags) {
    result |= static_cast<uint16_t>(mozilla::gfx::VRDisplayCapabilityFlags::Cap_StageParameters);
  }
  if (device::MountDetection & aFlags) {
    result |= static_cast<uint16_t>(mozilla::gfx::VRDisplayCapabilityFlags::Cap_MountDetection);
  }
  if (device::PositionEmulated & aFlags) {
    result |= static_cast<uint16_t>(mozilla::gfx::VRDisplayCapabilityFlags::Cap_PositionEmulated);
  }
  if (device::InlineSession & aFlags) {
    result |= static_cast<uint16_t>(mozilla::gfx::VRDisplayCapabilityFlags::Cap_Inline);
  }
  if (device::ImmersiveVRSession & aFlags) {
    result |= static_cast<uint16_t>(mozilla::gfx::VRDisplayCapabilityFlags::Cap_ImmersiveVR);
  }
  if (device::ImmersiveARSession & aFlags) {
    result |= static_cast<uint16_t>(mozilla::gfx::VRDisplayCapabilityFlags::Cap_ImmersiveAR);
  }
  //m.deviceCapabilities = aFlags;
  m.system.displayState.capabilityFlags = static_cast<mozilla::gfx::VRDisplayCapabilityFlags>(result);
  m.system.sensorState.flags = m.system.displayState.capabilityFlags;
}

void
ExternalVR::SetFieldOfView(const device::Eye aEye, const double aLeftDegrees,
                           const double aRightDegrees,
                           const double aTopDegrees,
                           const double aBottomDegrees) {
  mozilla::gfx::VRDisplayState::Eye which = (aEye == device::Eye::Right
                                             ? mozilla::gfx::VRDisplayState::Eye_Right
                                             : mozilla::gfx::VRDisplayState::Eye_Left);
  m.system.displayState.eyeFOV[which].upDegrees = aTopDegrees;
  m.system.displayState.eyeFOV[which].rightDegrees = aRightDegrees;
  m.system.displayState.eyeFOV[which].downDegrees = aBottomDegrees;
  m.system.displayState.eyeFOV[which].leftDegrees = aLeftDegrees;
}

void
ExternalVR::SetEyeOffset(const device::Eye aEye, const float aX, const float aY, const float aZ) {
  mozilla::gfx::VRDisplayState::Eye which = (aEye == device::Eye::Right
                                             ? mozilla::gfx::VRDisplayState::Eye_Right
                                             : mozilla::gfx::VRDisplayState::Eye_Left);
  m.system.displayState.eyeTranslation[which].x = aX;
  m.system.displayState.eyeTranslation[which].y = aY;
  m.system.displayState.eyeTranslation[which].z = aZ;
  m.eyeOffsets[device::EyeIndex(aEye)].Set(aX, aY, aZ);
}

void
ExternalVR::SetEyeResolution(const int32_t aWidth, const int32_t aHeight) {
  m.system.displayState.eyeResolution.width = aWidth;
  m.system.displayState.eyeResolution.height = aHeight;
}

void
ExternalVR::SetSittingToStandingTransform(const vrb::Matrix& aTransform) {
  memcpy(&(m.system.displayState.sittingToStandingTransform), aTransform.Data(), sizeof(m.system.displayState.sittingToStandingTransform));
}

void
ExternalVR::PushSystemState() {
  Lock lock(&(m.data.systemMutex));
  if (lock.IsLocked()) {
    memcpy(&(m.data.state), &(m.system), sizeof(mozilla::gfx::VRSystemState));
    pthread_cond_signal(&m.data.systemCond);
  }
}

void
ExternalVR::PullBrowserState() {
  Lock lock(m.browserMutex);
  if (lock.IsLocked()) {
   m.PullBrowserStateWhileLocked();
  }
}

void
ExternalVR::SetSourceBrowser(VRBrowserType aBrowser) {
  m.SetSourceBrowser(aBrowser);
}

void
ExternalVR::SetCompositorEnabled(bool aEnabled) {
  if (aEnabled == m.compositorEnabled) {
    return;
  }
  m.compositorEnabled = aEnabled;
  if (aEnabled) {
    VRBrowser::ResumeCompositor();
  } else {
    // Set suppressFrames to avoid a deadlock between the compositor sync pause call and the gfxVRExternal SubmitFrame result wait.
    m.system.displayState.suppressFrames = true;
    m.system.displayState.lastSubmittedFrameId = 0;
    m.lastFrameId = 0;
    PushSystemState();
    VRBrowser::PauseCompositor();
    m.system.displayState.suppressFrames = false;
    PushSystemState();
  }
}

bool
ExternalVR::IsPresenting() const {
  return m.IsPresenting();
}

uint16_t
ExternalVR::GetControllerCapabilityFlags(device::CapabilityFlags aFlags) {
  uint16_t result = 0;
  if (device::Position & aFlags) {
    result |= static_cast<uint16_t>(mozilla::gfx::ControllerCapabilityFlags::Cap_Position);
  }
  if (device::Orientation & aFlags) {
    result |= static_cast<uint16_t>(mozilla::gfx::ControllerCapabilityFlags::Cap_Orientation);
  }
  if (device::AngularAcceleration & aFlags) {
    result |= static_cast<uint16_t>(mozilla::gfx::ControllerCapabilityFlags::Cap_AngularAcceleration);
  }
  if (device::LinearAcceleration & aFlags) {
    result |= static_cast<uint16_t>(mozilla::gfx::ControllerCapabilityFlags::Cap_LinearAcceleration);
  }

  return result;
}

ExternalVR::VRState
ExternalVR::GetVRState() const {
  if (!IsPresenting()) {
    return VRState::NotPresenting;
  } else if (m.browser.navigationTransitionActive) {
    return VRState::LinkTraversal;
  } else if (m.firstPresentingFrame || m.waitingForExit || m.browser.layerState[0].type != mozilla::gfx::VRLayerType::LayerType_Stereo_Immersive) {
    return VRState::Loading;
  }

  return VRState::Rendering;
}

void
ExternalVR::PushFramePoses(const vrb::Matrix& aHeadTransform, const std::vector<Controller>& aControllers, const double aTimestamp) {
  const vrb::Matrix inverseHeadTransform = aHeadTransform.Inverse();
  vrb::Quaternion quaternion(inverseHeadTransform);
  vrb::Vector translation = aHeadTransform.GetTranslation();
  memcpy(&(m.system.sensorState.pose.orientation), quaternion.Data(),
         sizeof(m.system.sensorState.pose.orientation));
  memcpy(&(m.system.sensorState.pose.position), translation.Data(),
         sizeof(m.system.sensorState.pose.position));
  m.system.sensorState.inputFrameID++;
  m.system.displayState.lastSubmittedFrameId = m.lastFrameId;

  vrb::Matrix leftView = vrb::Matrix::Position(-m.eyeOffsets[device::EyeIndex(device::Eye::Left)]).PostMultiply(inverseHeadTransform);
  vrb::Matrix rightView = vrb::Matrix::Position(-m.eyeOffsets[device::EyeIndex(device::Eye::Right)]).PostMultiply(inverseHeadTransform);
  memcpy(&(m.system.sensorState.leftViewMatrix), leftView.Data(),
         sizeof(m.system.sensorState.leftViewMatrix));
  memcpy(&(m.system.sensorState.rightViewMatrix), rightView.Data(),
         sizeof(m.system.sensorState.rightViewMatrix));


  memset(m.system.controllerState, 0, sizeof(m.system.controllerState));
  for (int i = 0; i < aControllers.size(); ++i) {
    const Controller& controller = aControllers[i];
    if (controller.immersiveName.empty() || !controller.enabled) {
      continue;
    }
    mozilla::gfx::VRControllerState& immersiveController = m.system.controllerState[i];
    memcpy(immersiveController.controllerName, controller.immersiveName.c_str(), controller.immersiveName.size() + 1);
    immersiveController.numButtons = controller.numButtons;
    immersiveController.buttonPressed = controller.immersivePressedState;
    immersiveController.buttonTouched = controller.immersiveTouchedState;
    for (int i = 0; i< controller.numButtons; ++i) {
      immersiveController.triggerValue[i] = controller.immersiveTriggerValues[i];
    }
    immersiveController.numAxes = controller.numAxes;
    for (int i = 0; i< controller.numAxes; ++i) {
      immersiveController.axisValue[i] = controller.immersiveAxes[i];
    }
    immersiveController.numHaptics = controller.numHaptics;
    immersiveController.hand = controller.leftHanded ? mozilla::gfx::ControllerHand::Left : mozilla::gfx::ControllerHand::Right;

    const uint16_t flags = GetControllerCapabilityFlags(controller.deviceCapabilities);
    immersiveController.flags = static_cast<mozilla::gfx::ControllerCapabilityFlags>(flags);

    if (flags & static_cast<uint16_t>(mozilla::gfx::ControllerCapabilityFlags::Cap_Orientation)) {
      immersiveController.isOrientationValid = true;

      vrb::Quaternion quaternion(controller.transformMatrix);
      quaternion = quaternion.Inverse();
      memcpy(&(immersiveController.pose.orientation), quaternion.Data(), sizeof(immersiveController.pose.orientation));
    }
    if (flags & static_cast<uint16_t>(mozilla::gfx::ControllerCapabilityFlags::Cap_Position)) {
      immersiveController.isPositionValid = true;

      vrb::Vector position(controller.transformMatrix.GetTranslation());
      memcpy(&(immersiveController.pose.position), position.Data(), sizeof(immersiveController.pose.position));
    }
  }

  m.system.sensorState.timestamp = aTimestamp;

  PushSystemState();
}

bool
ExternalVR::WaitFrameResult() {
  Wait wait(m.browserMutex, m.browserCond);
  wait.Lock();
  // browserMutex is locked in wait.lock().
  m.PullBrowserStateWhileLocked();
  while (true) {
    if (!IsPresenting() || m.browser.layerState[0].layer_stereo_immersive.frameId != m.lastFrameId) {
      m.firstPresentingFrame = false;
      m.system.displayState.lastSubmittedFrameSuccessful = true;
      m.system.displayState.lastSubmittedFrameId = m.browser.layerState[0].layer_stereo_immersive.frameId;
      // VRB_LOG("RequestFrame BREAK %llu",  m.browser.layerState[0].layer_stereo_immersive.frameId);
      break;
    }
    if (m.firstPresentingFrame) {
      return true; // Do not block to show loading screen until the first frame arrives.
    }
    // VRB_LOG("RequestFrame ABOUT TO WAIT FOR FRAME %llu %llu",m.browser.layerState[0].layer_stereo_immersive.frameId, m.lastFrameId);
    const float kConditionTimeout = 0.1f;
    // Wait causes the current thread to block until the condition variable is notified or the timeout happens.
    // Waiting for the condition variable releases the mutex atomically. So GV can modify the browser data.
    if (!wait.DoWait(kConditionTimeout)) {
      return false;
    }
    // VRB_LOG("RequestFrame DONE TO WAIT FOR FRAME");

    // browserMutex lock is reacquired again after the condition variable wait exits.
    m.PullBrowserStateWhileLocked();
  }
  m.lastFrameId = m.browser.layerState[0].layer_stereo_immersive.frameId;
  return true;
}

void
ExternalVR::CompleteEnumeration()
{
  m.system.enumerationCompleted = true;
}


void
ExternalVR::GetFrameResult(int32_t& aSurfaceHandle, int32_t& aTextureWidth, int32_t& aTextureHeight,
    device::EyeRect& aLeftEye, device::EyeRect& aRightEye) const {
  aSurfaceHandle = (int32_t)m.browser.layerState[0].layer_stereo_immersive.textureHandle;
  mozilla::gfx::VRLayerEyeRect& left = m.browser.layerState[0].layer_stereo_immersive.leftEyeRect;
  mozilla::gfx::VRLayerEyeRect& right = m.browser.layerState[0].layer_stereo_immersive.rightEyeRect;
  aLeftEye = device::EyeRect(left.x, left.y, left.width, left.height);
  aRightEye = device::EyeRect(right.x, right.y, right.width, right.height);
  aTextureWidth = (int32_t)m.browser.layerState[0].layer_stereo_immersive.textureSize.width;
  aTextureHeight = (int32_t)m.browser.layerState[0].layer_stereo_immersive.textureSize.height;
}

void
ExternalVR::SetHapticState(ControllerContainerPtr aControllerContainer) const {
  const uint32_t count = aControllerContainer->GetControllerCount();
  uint32_t i = 0, j = 0;
  for (i = 0; i < count; ++i) {
    for (j = 0; j < mozilla::gfx::kVRHapticsMaxCount; ++j) {
      if (m.browser.hapticState[j].controllerIndex == i && m.browser.hapticState[j].inputFrameID) {
        aControllerContainer->SetHapticFeedback(i, m.browser.hapticState[j].inputFrameID,
                m.browser.hapticState[j].pulseDuration + m.browser.hapticState[j].pulseStart,
                m.browser.hapticState[j].pulseIntensity);
        break;
      }
    }
    // All hapticState has already been reset to zero, so it can't be match.
    if (j == mozilla::gfx::kVRHapticsMaxCount) {
      aControllerContainer->SetHapticFeedback(i, 0, 0.0f, 0.0f);
    }
  }
}

void
ExternalVR::StopPresenting() {
  m.system.displayState.presentingGeneration++;
  PushSystemState();
  m.waitingForExit = true;
}

ExternalVR::ExternalVR(): m(State::Instance()) {
  m.Reset();
  PushSystemState();
}

}
