/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "ControllerContainer.h"
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

struct ControllerContainer::State {
  std::vector<Controller> list;
  CreationContextWeak context;
  TogglePtr root;
  GroupPtr pointerContainer;
  std::vector<GroupPtr> models;
  GeometryPtr beamModel;
  bool visible = false;
  vrb::Color pointerColor;
  int gazeIndex = -1;
  uint64_t immersiveFrameId;
  uint64_t lastImmersiveFrameId;
  ModelLoaderAndroidPtr loader;
  std::vector<vrb::LoadTask> loadTask;

  void Initialize(vrb::CreationContextPtr& aContext) {
    context = aContext;
    root = Toggle::Create(aContext);
    visible = true;
    pointerColor = vrb::Color(1.0f, 1.0f, 1.0f, 1.0f);
    immersiveFrameId = 0;
    lastImmersiveFrameId = 0;
  }

  bool Contains(const int32_t aControllerIndex) {
    return (aControllerIndex >= 0) && (aControllerIndex < list.size());
  }

  void SetUpModelsGroup(const int32_t aModelIndex) {
    if (aModelIndex >= models.size()) {
      models.resize((size_t)(aModelIndex + 1));
    }
    if (!models[aModelIndex]) {
      CreationContextPtr create = context.lock();
      models[aModelIndex] = std::move(Group::Create(create));
    }
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

  void
  SetVisible(Controller& controller, const bool aVisible) {
    if (controller.transform && visible) {
      root->ToggleChild(*controller.transform, aVisible);
    }
    if (controller.pointer && !aVisible) {
      controller.pointer->SetVisible(false);
    }
  }
};

ControllerContainerPtr
ControllerContainer::Create(vrb::CreationContextPtr& aContext, const vrb::GroupPtr& aPointerContainer, const ModelLoaderAndroidPtr& aLoader) {
  auto result = std::make_shared<vrb::ConcreteClass<ControllerContainer, ControllerContainer::State> >(aContext);
  result->m.pointerContainer = aPointerContainer;
  result->m.loader = aLoader;
  return result;
}

TogglePtr
ControllerContainer::GetRoot() const {
  return m.root;
}

void
ControllerContainer::LoadControllerModel(const int32_t aModelIndex, const ModelLoaderAndroidPtr& aLoader, const std::string& aFileName) {
  m.SetUpModelsGroup(aModelIndex);
  aLoader->LoadModel(aFileName, m.models[aModelIndex]);
}

void
ControllerContainer::LoadControllerModel(const int32_t aModelIndex) {
  m.SetUpModelsGroup(aModelIndex);
  if (m.loadTask[aModelIndex]) {
    m.loader->LoadModel(m.loadTask[aModelIndex], m.models[aModelIndex]);
  } else {
    VRB_ERROR("No model load task fork model: %d", aModelIndex)
  }
}

void ControllerContainer::SetControllerModelTask(const int32_t aModelIndex, const vrb::LoadTask& aTask) {
  m.SetUpModelsGroup(aModelIndex);
  m.loadTask.resize(aModelIndex + 1, aTask);
}

void
ControllerContainer::InitializeBeam() {
  if (m.beamModel) {
    return;
  }
  CreationContextPtr create = m.context.lock();
  VertexArrayPtr array = VertexArray::Create(create);
  const float kLength = -1.0f;
  const float kHeight = 0.002f;

  array->AppendVertex(Vector(-kHeight, -kHeight, 0.0f)); // Bottom left
  array->AppendVertex(Vector(kHeight, -kHeight, 0.0f)); // Bottom right
  array->AppendVertex(Vector(kHeight, kHeight, 0.0f)); // Top right
  array->AppendVertex(Vector(-kHeight, kHeight, 0.0f)); // Top left
  array->AppendVertex(Vector(0.0f, 0.0f, kLength)); // Tip

  array->AppendNormal(Vector(-1.0f, -1.0f, 0.0f).Normalize()); // Bottom left
  array->AppendNormal(Vector(1.0f, -1.0f, 0.0f).Normalize()); // Bottom right
  array->AppendNormal(Vector(1.0f, 1.0f, 0.0f).Normalize()); // Top right
  array->AppendNormal(Vector(-1.0f, 1.0f, 0.0f).Normalize()); // Top left
  array->AppendNormal(Vector(0.0f, 0.0f, -1.0f).Normalize()); // in to the screen

  ProgramPtr program = create->GetProgramFactory()->CreateProgram(create, 0);
  RenderStatePtr state = RenderState::Create(create);
  state->SetProgram(program);
  state->SetMaterial(Color(1.0f, 1.0f, 1.0f), Color(1.0f, 1.0f, 1.0f), Color(0.0f, 0.0f, 0.0f), 0.0f);
  state->SetLightsEnabled(false);
  GeometryPtr geometry = Geometry::Create(create);
  geometry->SetVertexArray(array);
  geometry->SetRenderState(state);

  std::vector<int> index;
  std::vector<int> uvIndex;

  index.push_back(2);
  index.push_back(1);
  index.push_back(5);
  geometry->AddFace(index, uvIndex, index);

  index.clear();
  index.push_back(3);
  index.push_back(2);
  index.push_back(5);
  geometry->AddFace(index, uvIndex, index);

  index.clear();
  index.push_back(4);
  index.push_back(3);
  index.push_back(5);
  geometry->AddFace(index, uvIndex, index);

  index.clear();
  index.push_back(1);
  index.push_back(4);
  index.push_back(5);
  geometry->AddFace(index, uvIndex, index);

  m.beamModel = std::move(geometry);
  for (Controller& controller: m.list) {
    if (controller.beamParent) {
      controller.beamParent->AddNode(m.beamModel);
    }
  }
}

void
ControllerContainer::Reset() {
  for (Controller& controller: m.list) {
    controller.DetachRoot();
    controller.Reset();
  }
}

std::vector<Controller>&
ControllerContainer::GetControllers() {
  return m.list;
}

const std::vector<Controller>&
ControllerContainer::GetControllers() const {
  return m.list;
}

// crow::ControllerDelegate interface
uint32_t
ControllerContainer::GetControllerCount() {
  return (uint32_t)m.list.size();
}

void
ControllerContainer::CreateController(const int32_t aControllerIndex, const int32_t aModelIndex, const std::string& aImmersiveName) {
  CreateController(aControllerIndex, aModelIndex, aImmersiveName, vrb::Matrix::Identity());
}

void
ControllerContainer::CreateController(const int32_t aControllerIndex, const int32_t aModelIndex, const std::string& aImmersiveName, const vrb::Matrix& aBeamTransform) {
  if ((size_t)aControllerIndex >= m.list.size()) {
    m.list.resize((size_t)aControllerIndex + 1);
  }
  Controller& controller = m.list[aControllerIndex];
  controller.DetachRoot();
  controller.Reset();
  controller.index = aControllerIndex;
  controller.immersiveName = aImmersiveName;
  controller.beamTransformMatrix = aBeamTransform;
  controller.immersiveBeamTransform = aBeamTransform;
  if (aModelIndex < 0) {
    return;
  }
  m.SetUpModelsGroup(aModelIndex);
  CreationContextPtr create = m.context.lock();
  controller.transform = Transform::Create(create);
  controller.pointer = Pointer::Create(create);
  controller.pointer->SetVisible(true);

  if (aControllerIndex != m.gazeIndex) {
    if ((m.models.size() >= aModelIndex) && m.models[aModelIndex]) {
      controller.transform->AddNode(m.models[aModelIndex]);
      controller.beamToggle = vrb::Toggle::Create(create);
      controller.beamToggle->ToggleAll(true);
      vrb::TransformPtr beamTransform = Transform::Create(create);
      beamTransform->SetTransform(aBeamTransform);
      controller.beamParent = beamTransform;
      controller.beamToggle->AddNode(beamTransform);
      controller.transform->AddNode(controller.beamToggle);
      if (m.beamModel && controller.beamParent) {
        controller.beamParent->AddNode(m.beamModel);
      }

      // If the model is not yet loaded we trigger the load task
      if (m.models[aModelIndex]->GetNodeCount() == 0  &&
          m.loadTask.size() > aControllerIndex + 1 && m.loadTask[aModelIndex]) {
        m.loader->LoadModel(m.loadTask[aModelIndex], m.models[aModelIndex]);
      }
    } else {
      VRB_ERROR("Failed to add controller model")
    }
  }

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
ControllerContainer::SetImmersiveBeamTransform(const int32_t aControllerIndex,
        const vrb::Matrix& aImmersiveBeamTransform) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  m.list[aControllerIndex].immersiveBeamTransform = aImmersiveBeamTransform;
}

void
ControllerContainer::SetBeamTransform(const int32_t aControllerIndex, const vrb::Matrix& aBeamTransform) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  if (m.list[aControllerIndex].beamParent) {
    m.list[aControllerIndex].beamParent->SetTransform(aBeamTransform);
  }
  m.list[aControllerIndex].beamTransformMatrix = aBeamTransform;
}

void
ControllerContainer::SetFocused(const int32_t aControllerIndex) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  for (Controller& controller: m.list) {
    controller.focused = controller.index == aControllerIndex;
  }
}

void
ControllerContainer::DestroyController(const int32_t aControllerIndex) {
  if (m.Contains(aControllerIndex)) {
    m.list[aControllerIndex].DetachRoot();
    m.list[aControllerIndex].Reset();
  }
}

void
ControllerContainer::SetCapabilityFlags(const int32_t aControllerIndex, const device::CapabilityFlags aFlags) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }

  m.list[aControllerIndex].deviceCapabilities = aFlags;
}

void
ControllerContainer::SetEnabled(const int32_t aControllerIndex, const bool aEnabled) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  m.list[aControllerIndex].enabled = aEnabled;
  if (!aEnabled) {
    m.list[aControllerIndex].focused = false;
  }
  m.SetVisible(m.list[aControllerIndex], aEnabled);
}


void
ControllerContainer::SetControllerType(const int32_t aControllerIndex, device::DeviceType aType) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  Controller& controller = m.list[aControllerIndex];
  controller.type = aType;
}

void
ControllerContainer::SetTargetRayMode(const int32_t aControllerIndex, device::TargetRayMode aMode) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  Controller& controller = m.list[aControllerIndex];
  controller.targetRayMode = aMode;
}

void
ControllerContainer::SetTransform(const int32_t aControllerIndex, const vrb::Matrix& aTransform) {
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
ControllerContainer::SetButtonCount(const int32_t aControllerIndex, const uint32_t aNumButtons) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  m.list[aControllerIndex].numButtons = aNumButtons;
}

void
ControllerContainer::SetButtonState(const int32_t aControllerIndex, const Button aWhichButton, const int32_t aImmersiveIndex, const bool aPressed, const bool aTouched, const float aImmersiveTrigger) {
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
ControllerContainer::SetAxes(const int32_t aControllerIndex, const float* aData, const uint32_t aLength) {
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
ControllerContainer::SetHapticCount(const int32_t aControllerIndex, const uint32_t aNumHaptics) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  m.list[aControllerIndex].numHaptics = aNumHaptics;
}

uint32_t
ControllerContainer::GetHapticCount(const int32_t aControllerIndex) {
  if (!m.Contains(aControllerIndex)) {
    return 0;
  }

  return m.list[aControllerIndex].numHaptics;
}

void
ControllerContainer::SetHapticFeedback(const int32_t aControllerIndex, const uint64_t aInputFrameID,
                                        const float aPulseDuration, const float aPulseIntensity) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  m.list[aControllerIndex].inputFrameID = aInputFrameID;
  m.list[aControllerIndex].pulseDuration = aPulseDuration;
  m.list[aControllerIndex].pulseIntensity = aPulseIntensity;
}

void
ControllerContainer::GetHapticFeedback(const int32_t aControllerIndex, uint64_t & aInputFrameID,
                                        float& aPulseDuration, float& aPulseIntensity) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  aInputFrameID = m.list[aControllerIndex].inputFrameID;
  aPulseDuration = m.list[aControllerIndex].pulseDuration;
  aPulseIntensity = m.list[aControllerIndex].pulseIntensity;
}

void
ControllerContainer::SetSelectActionStart(const int32_t aControllerIndex) {
  if (!m.Contains(aControllerIndex) || !m.immersiveFrameId) {
    return;
  }

  if (m.list[aControllerIndex].selectActionStopFrameId >=
      m.list[aControllerIndex].selectActionStartFrameId) {
    m.list[aControllerIndex].selectActionStartFrameId = m.immersiveFrameId;
  }
}

void
ControllerContainer::SetSelectActionStop(const int32_t aControllerIndex) {
  if (!m.Contains(aControllerIndex) || !m.lastImmersiveFrameId) {
    return;
  }

  if (m.list[aControllerIndex].selectActionStartFrameId >
      m.list[aControllerIndex].selectActionStopFrameId) {
    m.list[aControllerIndex].selectActionStopFrameId = m.lastImmersiveFrameId;
  }
}

void
ControllerContainer::SetSqueezeActionStart(const int32_t aControllerIndex) {
  if (!m.Contains(aControllerIndex) || !m.immersiveFrameId) {
    return;
  }

  if (m.list[aControllerIndex].squeezeActionStopFrameId >=
      m.list[aControllerIndex].squeezeActionStartFrameId) {
    m.list[aControllerIndex].squeezeActionStartFrameId = m.immersiveFrameId;
  }
}

void
ControllerContainer::SetSqueezeActionStop(const int32_t aControllerIndex) {
  if (!m.Contains(aControllerIndex) || !m.lastImmersiveFrameId) {
    return;
  }

  if (m.list[aControllerIndex].squeezeActionStartFrameId >
      m.list[aControllerIndex].squeezeActionStopFrameId) {
    m.list[aControllerIndex].squeezeActionStopFrameId = m.lastImmersiveFrameId;
  }
}

void
ControllerContainer::SetLeftHanded(const int32_t aControllerIndex, const bool aLeftHanded) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }

  m.list[aControllerIndex].leftHanded = aLeftHanded;
}

void
ControllerContainer::SetTouchPosition(const int32_t aControllerIndex, const float aTouchX, const float aTouchY) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  Controller& controller = m.list[aControllerIndex];
  controller.touched = true;
  controller.touchX = aTouchX;
  controller.touchY = aTouchY;
}

void
ControllerContainer::EndTouch(const int32_t aControllerIndex) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  m.list[aControllerIndex].touched = false;
}

void
ControllerContainer::SetScrolledDelta(const int32_t aControllerIndex, const float aScrollDeltaX, const float aScrollDeltaY) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  Controller& controller = m.list[aControllerIndex];
  controller.scrollDeltaX = aScrollDeltaX;
  controller.scrollDeltaY = aScrollDeltaY;
}

void
ControllerContainer::SetBatteryLevel(const int32_t aControllerIndex, const int32_t aBatteryLevel) {
  if (!m.Contains(aControllerIndex)) {
    return;
  }
  m.list[aControllerIndex].batteryLevel = aBatteryLevel;
}
void ControllerContainer::SetPointerColor(const vrb::Color& aColor) const {
  m.pointerColor = aColor;
  for (Controller& controller: m.list) {
    m.updatePointerColor(controller);
  }
}

bool
ControllerContainer::IsVisible() const {
  return m.visible;
}

void
ControllerContainer::SetVisible(const bool aVisible) {
  VRB_LOG("[ControllerContainer] SetVisible %d", aVisible)
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

void
ControllerContainer::SetGazeModeIndex(const int32_t aControllerIndex) {
  m.gazeIndex = aControllerIndex;
}

void
ControllerContainer::SetFrameId(const uint64_t aFrameId) {
  if (m.immersiveFrameId) {
    m.lastImmersiveFrameId = aFrameId ? aFrameId : m.immersiveFrameId;
  } else {
    m.lastImmersiveFrameId = 0;
    for (Controller& controller: m.list) {
      controller.selectActionStartFrameId = controller.selectActionStopFrameId = 0;
      controller.squeezeActionStartFrameId = controller.squeezeActionStopFrameId = 0;
    }
  }
  m.immersiveFrameId = aFrameId;
}

ControllerContainer::ControllerContainer(State& aState, vrb::CreationContextPtr& aContext) : m(aState) {
  m.Initialize(aContext);
}

ControllerContainer::~ControllerContainer() {
  if (m.root) {
    m.root->RemoveFromParents();
    m.root = nullptr;
  }
}

} // namespace crow