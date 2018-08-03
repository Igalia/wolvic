/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "ExternalVR.h"
#include "VRBrowser.h"

#include "vrb/ConcreteClass.h"
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
  pthread_mutex_t& mMutex;
  bool mLocked;
public:
  Lock(pthread_mutex_t& aMutex) : mMutex(aMutex), mLocked(false) {
    if (pthread_mutex_lock(&mMutex) == 0) {
      mLocked = true;
    }
  }

  ~Lock() {
    if (mLocked) {
      pthread_mutex_unlock(&mMutex);
    }
  }

  bool IsLocked() {
    return mLocked;
  }

private:
  Lock() = delete;
  VRB_NO_DEFAULTS(Lock)
  VRB_NO_NEW_DELETE
};

class Wait {
  pthread_mutex_t& mMutex;
  pthread_cond_t& mCond;
  bool mLocked;
public:
  Wait(pthread_mutex_t& aMutex, pthread_cond_t& aCond)
      : mMutex(aMutex)
      , mCond(aCond)
      , mLocked(false)
  {}

  ~Wait() {
    if (mLocked) {
      pthread_mutex_unlock(&mMutex);
    }
  }

  bool DoWait(const float aWait) {
    if (mLocked || pthread_mutex_lock(&mMutex) == 0) {
      mLocked = true;
      if (aWait == 0.0f) {
        return pthread_cond_wait(&mCond, &mMutex) == 0;
      } else {
        float sec = 0;
        float nsec = modff(aWait, &sec);
        struct timeval tv;
        struct timespec ts;
        gettimeofday(&tv, NULL);
        ts.tv_sec = tv.tv_sec + int(sec);
        ts.tv_nsec = (tv.tv_usec * MicrosecondsToNanoseconds) + int(SecondsToNanoseconds * nsec);
        if (ts.tv_nsec >= SecondsToNanosecondsI32) {
          ts.tv_nsec -= SecondsToNanosecondsI32;
          ts.tv_sec++;
        }
        return pthread_cond_timedwait(&mCond, &mMutex, &ts) == 0;
      }
    }
    return false;
  }

  bool DoWait() {
    return DoWait(0.0f);
  }

  bool IsLocked() {
    return mLocked;
  }

  void Lock() {
    if (mLocked) {
      return;
    }

    if (pthread_mutex_lock(&mMutex) == 0) {
      mLocked = true;
    }
  }
  void Unlock() {
    if (mLocked) {
      mLocked = false;
      pthread_mutex_unlock(&mMutex);
    }
  }

private:
  Wait() = delete;
  VRB_NO_DEFAULTS(Wait)
  VRB_NO_NEW_DELETE
};

} // namespace

namespace crow {

struct ExternalVR::State {
  mozilla::gfx::VRExternalShmem data;
  mozilla::gfx::VRSystemState system;
  mozilla::gfx::VRBrowserState browser;
  device::CapabilityFlags deviceCapabilities;
  vrb::Vector eyeOffsets[device::EyeCount];
  uint64_t lastFrameId;
  bool firstPresentingFrame;

  State() : deviceCapabilities(0) {
    memset(&data, 0, sizeof(mozilla::gfx::VRExternalShmem));
    memset(&system, 0, sizeof(mozilla::gfx::VRSystemState));
    memset(&browser, 0, sizeof(mozilla::gfx::VRBrowserState));
    data.version = mozilla::gfx::kVRExternalVersion;
    data.size = sizeof(mozilla::gfx::VRExternalShmem);
    pthread_mutex_init(&(data.systemMutex), nullptr);
    pthread_mutex_init(&(data.browserMutex), nullptr);
    pthread_cond_init(&(data.systemCond), nullptr);
    pthread_cond_init(&(data.browserCond), nullptr);

    system.displayState.mIsConnected = true;
    system.displayState.mIsMounted = true;
    const vrb::Matrix identity = vrb::Matrix::Identity();
    memcpy(&(system.sensorState.leftViewMatrix), identity.Data(), sizeof(system.sensorState.leftViewMatrix));
    memcpy(&(system.sensorState.rightViewMatrix), identity.Data(), sizeof(system.sensorState.rightViewMatrix));
    lastFrameId = 0;
    firstPresentingFrame = false;
  }

  ~State() {
    pthread_mutex_destroy(&(data.systemMutex));
    pthread_mutex_destroy(&(data.browserMutex));
    pthread_cond_destroy(&(data.systemCond));
    pthread_cond_destroy(&(data.browserCond));
  }
};

ExternalVRPtr
ExternalVR::Create() {
  return std::make_shared<vrb::ConcreteClass<ExternalVR, ExternalVR::State> >();
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
  strncpy(m.system.displayState.mDisplayName, aName.c_str(),
          mozilla::gfx::kVRDisplayNameMaxLen - 1);
  m.system.displayState.mDisplayName[mozilla::gfx::kVRDisplayNameMaxLen - 1] = '\0';
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
  m.deviceCapabilities = aFlags;
  m.system.displayState.mCapabilityFlags = static_cast<mozilla::gfx::VRDisplayCapabilityFlags>(result);
  m.system.sensorState.flags = m.system.displayState.mCapabilityFlags;
}

void
ExternalVR::SetFieldOfView(const device::Eye aEye, const double aLeftDegrees,
                           const double aRightDegrees,
                           const double aTopDegrees,
                           const double aBottomDegrees) {
  mozilla::gfx::VRDisplayState::Eye which = (aEye == device::Eye::Right
                                             ? mozilla::gfx::VRDisplayState::Eye_Right
                                             : mozilla::gfx::VRDisplayState::Eye_Left);
  m.system.displayState.mEyeFOV[which].upDegrees = aTopDegrees;
  m.system.displayState.mEyeFOV[which].rightDegrees = aRightDegrees;
  m.system.displayState.mEyeFOV[which].downDegrees = aBottomDegrees;
  m.system.displayState.mEyeFOV[which].leftDegrees = aLeftDegrees;
}

void
ExternalVR::SetEyeOffset(const device::Eye aEye, const float aX, const float aY, const float aZ) {
  mozilla::gfx::VRDisplayState::Eye which = (aEye == device::Eye::Right
                                             ? mozilla::gfx::VRDisplayState::Eye_Right
                                             : mozilla::gfx::VRDisplayState::Eye_Left);
  m.system.displayState.mEyeTranslation[which].x = aX;
  m.system.displayState.mEyeTranslation[which].y = aY;
  m.system.displayState.mEyeTranslation[which].z = aZ;
  m.eyeOffsets[device::EyeIndex(aEye)].Set(aX, aY, aZ);
}

void
ExternalVR::SetEyeResolution(const int32_t aWidth, const int32_t aHeight) {
  m.system.displayState.mEyeResolution.width = aWidth;
  m.system.displayState.mEyeResolution.height = aHeight;
}

void
ExternalVR::PushSystemState() {
  Lock lock(m.data.systemMutex);
  if (lock.IsLocked()) {
    memcpy(&(m.data.state), &(m.system), sizeof(mozilla::gfx::VRSystemState));
    pthread_cond_signal(&m.data.systemCond);
  }
}

void
ExternalVR::PullBrowserState(bool aBlock) {
  const bool wasPresenting = IsPresenting();
  if (aBlock) {
    Lock lock(m.data.browserMutex);
    if (lock.IsLocked()) {
      memcpy(&(m.browser), &(m.data.browserState), sizeof(mozilla::gfx::VRBrowserState));
    }
  } else {
    memcpy(&(m.browser), &(m.data.browserState), sizeof(mozilla::gfx::VRBrowserState));
  }

  m.firstPresentingFrame = !wasPresenting && IsPresenting();
  if (wasPresenting && !IsPresenting()) {
    m.lastFrameId = m.browser.layerState[0].layer_stereo_immersive.mFrameId;
    VRBrowser::ResumeCompositor();
  }
}

bool
ExternalVR::IsFirstPresentingFrame() const {
  return IsPresenting() && m.firstPresentingFrame;
}

void
ExternalVR::HandleFirstPresentingFrame() {
  // Set mSuppressFrames to avoid a deadlock between the compositor sync pause call and the gfxVRExternal SubmitFrame result wait.
  m.system.displayState.mSuppressFrames = true;
  m.system.displayState.mLastSubmittedFrameId = 0;
  m.lastFrameId = 0;
  PushSystemState();
  VRBrowser::PauseCompositor();
  m.system.displayState.mSuppressFrames = false;
  PushSystemState();
}

bool
ExternalVR::IsPresenting() const {
  // VRB_LOG("IsPresenting=%s",((m.browser.layerState[0].type == mozilla::gfx::VRLayerType::LayerType_Stereo_Immersive)?"TRUE":"FALSE"));
  return (m.browser.layerState[0].type == mozilla::gfx::VRLayerType::LayerType_Stereo_Immersive);
}

void
ExternalVR::RequestFrame(const vrb::Matrix& aHeadTransform, const std::vector<Controller>& aControllers) {
  const vrb::Matrix inverseHeadTransform = aHeadTransform.Inverse();
  vrb::Quaternion quaternion(inverseHeadTransform);
  vrb::Vector translation = aHeadTransform.GetTranslation();
  memcpy(&(m.system.sensorState.pose.orientation), quaternion.Data(),
         sizeof(m.system.sensorState.pose.orientation));
  memcpy(&(m.system.sensorState.pose.position), translation.Data(),
         sizeof(m.system.sensorState.pose.position));
  m.system.sensorState.inputFrameID++;
  m.system.displayState.mLastSubmittedFrameId = m.lastFrameId;

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
    immersiveController.hand = controller.leftHanded ? mozilla::gfx::ControllerHand::Left : mozilla::gfx::ControllerHand::Right;

    immersiveController.flags = mozilla::gfx::ControllerCapabilityFlags::Cap_Orientation;
    immersiveController.isOrientationValid = true;
    vrb::Quaternion quaternion(controller.transformMatrix);
    quaternion = quaternion.Inverse();
    memcpy(&(immersiveController.pose.orientation), quaternion.Data(), sizeof(immersiveController.pose.orientation));
  }

  PushSystemState();
  Wait wait(m.data.browserMutex, m.data.browserCond);
  wait.Lock();
  PullBrowserState(false);
  while (true) {
    if (!IsPresenting() || m.browser.layerState[0].layer_stereo_immersive.mFrameId != m.lastFrameId) {
      m.system.displayState.mLastSubmittedFrameSuccessful = m.browser.layerState[0].layer_stereo_immersive.mFrameId != 0;
      m.system.displayState.mLastSubmittedFrameId = m.browser.layerState[0].layer_stereo_immersive.mFrameId;
      // VRB_LOG("RequestFrame BREAK %llu",  m.browser.layerState[0].layer_stereo_immersive.mFrameId);
      break;
    }
    // VRB_LOG("RequestFrame ABOUT TO WAIT FOR FRAME %llu %llu",m.browser.layerState[0].layer_stereo_immersive.mFrameId, m.lastFrameId);
    wait.DoWait();
    // VRB_LOG("RequestFrame DONE TO WAIT FOR FRAME");
    PullBrowserState(false);
    // VRB_LOG("RequestFrame m.browser.layerState[0].layer_stereo_immersive.mInputFrameId=%llu m.system.sensorState.inputFrameID=%llu", m.browser.layerState[0].layer_stereo_immersive.mInputFrameId, m.system.sensorState.inputFrameID);
    // VRB_LOG("RequestFrame m.browser.layerState[0].layer_stereo_immersive.mFrameId=%llu", m.browser.layerState[0].layer_stereo_immersive.mFrameId);
  }
  m.lastFrameId = m.browser.layerState[0].layer_stereo_immersive.mFrameId;
}

void
ExternalVR::CompleteEnumeration()
{
  m.system.enumerationCompleted = true;
}


void
ExternalVR::GetFrameResult(int32_t& aSurfaceHandle, device::EyeRect& aLeftEye, device::EyeRect& aRightEye) const {
  aSurfaceHandle = (int32_t)m.browser.layerState[0].layer_stereo_immersive.mTextureHandle;
  mozilla::gfx::VRLayerEyeRect& left = m.browser.layerState[0].layer_stereo_immersive.mLeftEyeRect;
  mozilla::gfx::VRLayerEyeRect& right = m.browser.layerState[0].layer_stereo_immersive.mRightEyeRect;
  aLeftEye = device::EyeRect(left.x, left.y, left.width, left.height);
  aRightEye = device::EyeRect(right.x, right.y, right.width, right.height);
}

void
ExternalVR::StopPresenting() {
  m.system.displayState.mPresentingGeneration++;
  PushSystemState();
}

ExternalVR::ExternalVR(State& aState) : m(aState) {
  PushSystemState();
}

ExternalVR::~ExternalVR() {}

}
