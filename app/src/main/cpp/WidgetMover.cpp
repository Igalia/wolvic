/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "WidgetMover.h"
#include "Widget.h"
#include "WidgetPlacement.h"
#include "VRBrowser.h"
#include "Cylinder.h"
#include "vrb/ConcreteClass.h"
#include "vrb/Matrix.h"
#include "vrb/Transform.h"

namespace crow {

struct WidgetMover::State {
  WidgetPtr widget;
  WidgetPtr parentWidget;
  int attachedController;
  vrb::Vector initialPoint;
  vrb::Vector anchorPoint;
  vrb::Matrix initialTransform;
  WidgetPlacementPtr initialPlacement;
  WidgetPlacementPtr movePlacement;
  WidgetMoveBehaviour moveBehaviour;
  vrb::Vector endDelta;
  float endRotation;

  State()
      : widget(nullptr)
      , attachedController(-1)
      , moveBehaviour(WidgetMoveBehaviour::GENERAL)
      , endRotation(0)
  {}


  vrb::Vector ProjectPoint(const WidgetPtr& aWidget, const vrb::Vector& aWorldPoint) const {
    if (aWidget->GetCylinder()) {
      // For a cylinder project the point to the virtual quad.
      vrb::Vector min, max;
      aWidget->GetWidgetMinAndMax(min, max);
      vrb::Vector point =  aWidget->GetCylinder()->ProjectPointToQuad(aWorldPoint, 0.5f, aWidget->GetCylinderDensity(), min, max);
      return aWidget->GetTransformNode()->GetWorldTransform().MultiplyPosition(point);
    } else {
      return aWorldPoint;
    }
  }

  vrb::Vector GetMovePoint(const vrb::Vector& aStart, const vrb::Vector& aDirection) const {
    float hitDistance = -1;
    vrb::Vector hitPoint;
    vrb::Vector hitNormal;
    bool isInWidget = false;
    widget->TestControllerIntersection(aStart, aDirection, hitPoint, hitNormal, false, isInWidget, hitDistance);

    vrb::Vector result = ProjectPoint(widget, hitPoint);

    if (parentWidget && moveBehaviour == WidgetMoveBehaviour::KEYBOARD) {
      if (widget->GetCylinder()) {
        // For Cylindrical keyboard move we want to use the x translation from the parent widget (the window)
        parentWidget->TestControllerIntersection(aStart, aDirection, hitPoint, hitNormal, false, isInWidget, hitDistance);
        if (hitDistance >= 0) {
          vrb::Vector point = ProjectPoint(parentWidget, hitPoint);
          result.x() = point.x();
          result.z() = point.z();
        }
      }

      // Convert the world point to a point relative to the window.
      result = parentWidget->GetTransformNode()->GetWorldTransform().AfineInverse().MultiplyPosition(result);
    }

    return result;
  }

  WidgetPlacementPtr& HandleKeyboardMove(const vrb::Vector& aDelta) {
    float x = initialPlacement->translation.x() * WidgetPlacement::kWorldDPIRatio + aDelta.x();
    float y = initialPlacement->translation.y() * WidgetPlacement::kWorldDPIRatio + aDelta.y();
    const float windowZ = -initialPlacement->cylinderMapRadius; // Happen to be the negative number of the changing windowZ
    const float maxX = 4.0f; // Relative to 0.5f anchor point.
    const float minX = -maxX;
    const float maxY = 1.8f;  // Relative to 0.0f anchor point.
    const float minY = -1.3f; // Relative to 0.0f anchor point.
    const float maxAngle = -35.0f * (float)M_PI / 180.0f;
    const float angleStartY = 0.8f;
    const float minZ = -2.5f - windowZ;
    const float maxZ = -3.2f - windowZ;
    const float thresholdZ = 1.45f;

    float w, h;
    widget->GetWorldSize(w, h);
    const float dx = w * (anchorPoint.x() - 0.5f);
    const float dy = h * anchorPoint.y();

    x = fmax(fmin(x, maxX + dx), minX - dx);
    y = fmax(fmin(y, maxY + dy), minY - dy);

    movePlacement->translation.x() = x / WidgetPlacement::kWorldDPIRatio;
    movePlacement->translation.y() = y / WidgetPlacement::kWorldDPIRatio;

    float angle = 0.0f;
    if (y < angleStartY) {
      const float t = 1.0f - (y - minY) / (angleStartY - minY);
      angle = t * maxAngle;
    }

    float t;
    if (y > 1.45f) {
      t = 1.0f;
    } else {
      t = (y - minY) / (1.45f - minY);
    }

    movePlacement->translation.z() = (minZ + t * (maxZ - minZ)) / WidgetPlacement::kWorldDPIRatio;
    movePlacement->rotation = angle;
    endDelta = movePlacement->translation - initialPlacement->translation;
    endRotation = angle;

    return movePlacement;
  }
};

WidgetMoverPtr
WidgetMover::Create() {
  return std::make_shared<vrb::ConcreteClass<WidgetMover, WidgetMover::State> >();
}

bool
WidgetMover::IsMoving(const int aControllerIndex) const {
  return m.widget != nullptr && m.attachedController == aControllerIndex;
}

WidgetPlacementPtr
WidgetMover::HandleMove(const vrb::Vector& aStart, const vrb::Vector& aDirection) {
  float hitDistance = -1;
  vrb::Vector hitPoint;
  vrb::Vector hitNormal;
  bool isInWidget = false;
  m.widget->TestControllerIntersection(aStart, aDirection, hitPoint, hitNormal, false, isInWidget, hitDistance);
  if (hitDistance < 0) {
    return nullptr;
  };
  hitPoint = m.GetMovePoint(aStart, aDirection);

  vrb::Vector delta = hitPoint - m.initialPoint;
  delta.x() = hitPoint.x() - m.initialPoint.x();
  delta.y() = hitPoint.y() - m.initialPoint.y();

  if (m.moveBehaviour == WidgetMoveBehaviour::KEYBOARD) {
    return m.HandleKeyboardMove(delta);
  } else {
    // Windows only move in the horizontal direction, and only when positioned on a cylinder.
    if (m.moveBehaviour == WidgetMoveBehaviour::WINDOW) {
      delta.y() = 0.0f;
    }

    // General case
    const float maxX = 1.5f;
    const float minX = -maxX;
    const float maxY = 1.0f;
    const float minY = -maxY;
    vrb::Vector translation = m.initialPlacement->translation;
    float x = translation.x() * WidgetPlacement::kWorldDPIRatio + delta.x();
    float y = translation.y() * WidgetPlacement::kWorldDPIRatio + delta.y();

    float w, h;
    m.widget->GetWorldSize(w, h);
    const float dx = w * (m.anchorPoint.x() - 0.5f);
    const float dy = h * m.anchorPoint.y();

    x = fmax(fmin(x, maxX + dx), minX - dx);
    y = fmax(fmin(y, maxY + dy), minY - dy);

    m.movePlacement->translation.x() = x / WidgetPlacement::kWorldDPIRatio;
    m.movePlacement->translation.y() = y / WidgetPlacement::kWorldDPIRatio;
    m.endDelta = m.movePlacement->translation - translation;
    m.endRotation = 0.0f;

    return m.movePlacement;
  }
}

void
WidgetMover::StartMoving(const WidgetPtr& aWidget, const WidgetPtr& aParentWidget, const int32_t aMoveBehaviour, const int32_t aControllerIndex,
                         const vrb::Vector& aStart, const vrb::Vector& aDirection, const vrb::Vector& aAnchorPoint) {
  m.widget = aWidget;
  m.parentWidget = aParentWidget;
  m.attachedController = aControllerIndex;
  m.initialTransform = aWidget->GetTransform();
  m.anchorPoint = aAnchorPoint;
  m.initialPlacement = aWidget->GetPlacement();
  m.movePlacement = WidgetPlacement::Create(*m.initialPlacement);
  m.moveBehaviour = (WidgetMoveBehaviour) aMoveBehaviour;
  m.initialPoint = m.GetMovePoint(aStart, aDirection);
}

void
WidgetMover::EndMoving() {
  m.attachedController = -1;
  if (m.widget) {
    VRBrowser::HandleMoveEnd(m.widget->GetHandle(), m.endDelta.x(), m.endDelta.y(), m.endDelta.z(), m.endRotation);
  }
  m.widget = nullptr;
  m.parentWidget = nullptr;
}

WidgetPtr
WidgetMover::GetWidget() const {
  return m.widget;
}

WidgetMover::WidgetMover(State& aState) : m(aState) {
}

} // namespace crow
