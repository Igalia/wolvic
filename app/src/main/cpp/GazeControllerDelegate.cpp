/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "GazeControllerDelegate.h"
#include "Controller.h"
#include "Pointer.h"

#include "vrb/ConcreteClass.h"
#include "vrb/Color.h"
#include "vrb/CreationContext.h"
#include "vrb/Geometry.h"
#include "vrb/Group.h"
#include "vrb/Matrix.h"
#include "vrb/ModelLoaderAndroid.h"
#include "vrb/Program.h"
#include "vrb/ProgramFactory.h"
#include "vrb/RenderState.h"
#include "vrb/Toggle.h"
#include "vrb/Transform.h"
#include "vrb/Vector.h"
#include "vrb/VertexArray.h"

using namespace vrb;

namespace crow {

struct GazeControllerDelegate::State {
  std::vector<Controller> list;
  CreationContextWeak context;
  TogglePtr root;
  GroupPtr pointerContainer;
  bool visible = false;
  vrb::Color pointerColor;

  void Initialize(vrb::CreationContextPtr& aContext) {
    context = aContext;
    root = Toggle::Create(aContext);
    visible = true;
    pointerColor = vrb::Color(1.0f, 1.0f, 1.0f, 1.0f);
  }

  bool Contains(const int32_t aControllerIndex) {
    return (aControllerIndex >= 0) && (aControllerIndex < list.size());
  }

  void updatePointerColor(Controller& aController) {
    if (aController.beamParent && aController.beamParent->GetNodeCount() > 0) {
      GeometryPtr geometry = std::dynamic_pointer_cast<vrb::Geometry>(aController.beamParent->GetNode(0));
      if (geometry) {
        geometry->GetRenderState()->SetMaterial(pointerColor, pointerColor, vrb::Color(0.0f, 0.0f, 0.0f), 0.0f);
      }
    }
    if (aController.pointer) {
      aController.pointer->SetPointerColor(pointerColor);
    }
  }
};

GazeControllerDelegatePtr
GazeControllerDelegate::Create(vrb::CreationContextPtr& aContext, const vrb::GroupPtr& aPointerContainer) {
  auto result = std::make_shared<vrb::ConcreteClass<GazeControllerDelegate, GazeControllerDelegate::State> >(aContext);
  result->m.pointerContainer = aPointerContainer;
  return result;
}

TogglePtr
GazeControllerDelegate::GetRoot() const {
  return m.root;
}

void
GazeControllerDelegate::Reset() {
  for (Controller& controller: m.list) {
    controller.DetachRoot();
    controller.Reset();
  }
}

std::vector<Controller>&
GazeControllerDelegate::GetControllers() {
  return m.list;
}

const std::vector<Controller>&
GazeControllerDelegate::GetControllers() const {
  return m.list;
}

// crow::ControllerDelegate interface
uint32_t
  GazeControllerDelegate::GetControllerCount() {
  return (uint32_t)m.list.size();
}

void
GazeControllerDelegate::CreateController(const int32_t aControllerIndex, const int32_t aModelIndex, const std::string& aImmersiveName) {
  CreateController(aControllerIndex, aModelIndex, aImmersiveName, vrb::Matrix::Identity());
}

void
GazeControllerDelegate::CreateController(const int32_t aControllerIndex, const int32_t aModelIndex, const std::string& aImmersiveName, const vrb::Matrix& aBeamTransform) {
  VRB_LOG("Gaze controller created!")
  if ((size_t)aControllerIndex >= m.list.size()) {
    m.list.resize((size_t)aControllerIndex + 1);
  }
  Controller& controller = m.list[aControllerIndex];
  controller.DetachRoot();
  controller.Reset();
  controller.index = aControllerIndex;
  controller.immersiveName = aImmersiveName;
  controller.beamTransformMatrix = aBeamTransform;
  CreationContextPtr create = m.context.lock();
  controller.transform = Transform::Create(create);
  controller.pointer = Pointer::Create(create);
  if (m.root) {
    m.root->AddNode(controller.transform);
    m.root->ToggleChild(*controller.transform, false);
  }
  if (m.pointerContainer) {
    m.pointerContainer->AddNode(controller.pointer->GetRoot());
  }
  m.updatePointerColor(controller);
}

void
GazeControllerDelegate::SetFocused(const int32_t aControllerIndex) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  for (Controller& controller: m.list) {
    bool show = false;
    if (controller.index == aControllerIndex) {
      controller.focused = true;
      show = true;
    } else  {
      controller.focused = false;
    }

    if (controller.beamToggle) {
      controller.beamToggle->ToggleAll(show);
    }
    if (controller.pointer) {
      controller.pointer->SetVisible(show);
    }
  }
}

void
GazeControllerDelegate::DestroyController(const int32_t aControllerIndex) {
  if (m.Contains(aControllerIndex)) {
    m.list[aControllerIndex].DetachRoot();
    m.list[aControllerIndex].Reset();
  }
}

void
GazeControllerDelegate::SetCapabilityFlags(const int32_t aControllerIndex, const device::CapabilityFlags aFlags) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }

  m.list[aControllerIndex].deviceCapabilities = aFlags;
}

void
GazeControllerDelegate::SetEnabled(const int32_t aControllerIndex, const bool aEnabled) {
  VRB_LOG("Gaze Enabled: %d", aEnabled)
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  m.list[aControllerIndex].enabled = aEnabled;
  if (!aEnabled) {
    m.list[aControllerIndex].focused = false;
    SetVisible(aControllerIndex, false);
  }
}

void
GazeControllerDelegate::SetVisible(const int32_t aControllerIndex, const bool aVisible) {
  VRB_LOG("Gaze Visible: %d", aVisible)
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  Controller& controller = m.list[aControllerIndex];
  if (controller.transform && m.visible) {
    m.root->ToggleChild(*controller.transform, aVisible);
  }
  if (controller.pointer && !aVisible) {
    controller.pointer->SetVisible(false);
  }
}

void
GazeControllerDelegate::SetTransform(const int32_t aControllerIndex, const vrb::Matrix& aTransform) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  Controller& controller = m.list[aControllerIndex];
  controller.transformMatrix = aTransform;
  if (controller.transform) {
    controller.transform->SetTransform(aTransform);
  }
}

void
GazeControllerDelegate::SetButtonCount(const int32_t aControllerIndex, const uint32_t aNumButtons) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  m.list[aControllerIndex].numButtons = aNumButtons;
}

void
GazeControllerDelegate::SetButtonState(const int32_t aControllerIndex, const Button aWhichButton, const int32_t aImmersiveIndex, const bool aPressed, const bool aTouched, const float aImmersiveTrigger) {
  assert(kControllerMaxButtonCount > aImmersiveIndex
         && "Button index must < kControllerMaxButtonCount.");

  if (!m.Contains(aControllerIndex)) {
    return;
  }

  const int32_t immersiveButtonMask = 1 << aImmersiveIndex;

  if (aPressed) {
    m.list[aControllerIndex].buttonState |= aWhichButton;
  } else {
    m.list[aControllerIndex].buttonState &= ~aWhichButton;
  }

  if (aImmersiveIndex >= 0) {
    if (aPressed) {
      m.list[aControllerIndex].immersivePressedState |= immersiveButtonMask;
    } else {
      m.list[aControllerIndex].immersivePressedState &= ~immersiveButtonMask;
    }

    if (aTouched) {
      m.list[aControllerIndex].immersiveTouchedState |= immersiveButtonMask;
    } else {
      m.list[aControllerIndex].immersiveTouchedState &= ~immersiveButtonMask;
    }

    float trigger = aImmersiveTrigger;
    if (trigger < 0.0f) {
      trigger = aPressed ? 1.0f : 0.0f;
    }
    m.list[aControllerIndex].immersiveTriggerValues[aImmersiveIndex] = trigger;
  }
}

void
GazeControllerDelegate::SetAxes(const int32_t aControllerIndex, const float* aData, const uint32_t aLength) {
  assert(kControllerMaxAxes >= aLength
         && "Axis length must <= kControllerMaxAxes.");

  if (!m.Contains(aControllerIndex)) {
    return;
  }

  m.list[aControllerIndex].numAxes = aLength;
  for (int i = 0; i < aLength; ++i) {
    m.list[aControllerIndex].immersiveAxes[i] = aData[i];
  }
}

void
GazeControllerDelegate::SetHapticCount(const int32_t aControllerIndex, const uint32_t aNumHaptics) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  m.list[aControllerIndex].numHaptics = aNumHaptics;
}

uint32_t
GazeControllerDelegate::GetHapticCount(const int32_t aControllerIndex) {
  if (!m.Contains(aControllerIndex)) {
    return 0;
  }

  return m.list[aControllerIndex].numHaptics;
}

void
GazeControllerDelegate::SetHapticFeedback(const int32_t aControllerIndex, const uint64_t aInputFrameID,
                                       const float aPulseDuration, const float aPulseIntensity) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  m.list[aControllerIndex].inputFrameID = aInputFrameID;
  m.list[aControllerIndex].pulseDuration = aPulseDuration;
  m.list[aControllerIndex].pulseIntensity = aPulseIntensity;
}

void
GazeControllerDelegate::GetHapticFeedback(const int32_t aControllerIndex, uint64_t & aInputFrameID,
                                       float& aPulseDuration, float& aPulseIntensity) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  aInputFrameID = m.list[aControllerIndex].inputFrameID;
  aPulseDuration = m.list[aControllerIndex].pulseDuration;
  aPulseIntensity = m.list[aControllerIndex].pulseIntensity;
}

void
GazeControllerDelegate::SetLeftHanded(const int32_t aControllerIndex, const bool aLeftHanded) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }

  m.list[aControllerIndex].leftHanded = aLeftHanded;
}

void
GazeControllerDelegate::SetTouchPosition(const int32_t aControllerIndex, const float aTouchX, const float aTouchY) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  Controller& controller = m.list[aControllerIndex];
  controller.touched = true;
  controller.touchX = aTouchX;
  controller.touchY = aTouchY;
}

void
GazeControllerDelegate::EndTouch(const int32_t aControllerIndex) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  m.list[aControllerIndex].touched = false;
}

void
GazeControllerDelegate::SetScrolledDelta(const int32_t aControllerIndex, const float aScrollDeltaX, const float aScrollDeltaY) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  Controller& controller = m.list[aControllerIndex];
  controller.scrollDeltaX = aScrollDeltaX;
  controller.scrollDeltaY = aScrollDeltaY;
}

void
GazeControllerDelegate::SetPointerColor(const vrb::Color& aColor) const {
  m.pointerColor = aColor;
  for (Controller& controller: m.list) {
    m.updatePointerColor(controller);
  }
}

void
GazeControllerDelegate::SetVisible(const bool aVisible) {
  VRB_LOG("Gaze Visible: %d", aVisible)
  if (m.visible == aVisible) {
    return;
  }
  m.visible = aVisible;
  if (aVisible) {
    for (int i = 0; i < m.list.size(); ++i) {
      if (m.list[i].enabled) {
        m.root->ToggleChild(*m.list[i].transform, true);
      }
    }
  } else {
    m.root->ToggleAll(false);
  }
}

GazeControllerDelegate::GazeControllerDelegate(State& aState, vrb::CreationContextPtr& aContext) : m(aState) {
  m.Initialize(aContext);
}

GazeControllerDelegate::~GazeControllerDelegate() {
  if (m.root) {
    m.root->RemoveFromParents();
    m.root = nullptr;
  }
}

} // namespace crow