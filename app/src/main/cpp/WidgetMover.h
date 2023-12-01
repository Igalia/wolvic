/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_WIDGET_MOVER_DOT_H
#define VRBROWSER_WIDGET_MOVER_DOT_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"

#include <memory>
#include <string>
#include <vector>
#include <functional>

namespace crow {

class Widget;
class WidgetPlacement;
class WidgetMover;
typedef std::shared_ptr<Widget> WidgetPtr;
typedef std::shared_ptr<WidgetPlacement> WidgetPlacementPtr;
typedef std::shared_ptr<WidgetMover> WidgetMoverPtr;

// Should match the values defined in WidgetManagerDelegate.WidgetMoveBehaviourFlags
enum class WidgetMoveBehaviour {
    GENERAL = 0,
    KEYBOARD = 1,
    WINDOW = 2
};

class WidgetMover {
public:
  static WidgetMoverPtr Create();
  bool IsMoving(const int aControllerIndex) const;
  WidgetPlacementPtr HandleMove(const vrb::Vector& aStart, const vrb::Vector& aDirection);
  void StartMoving(const WidgetPtr& aWidget, const WidgetPtr& aParentWidget, const int32_t aMoveBehavour, const int32_t aControllerIndex,
                   const vrb::Vector& aStart, const vrb::Vector& aDirection, const vrb::Vector& aAnchorPoint);
  void EndMoving();
  WidgetPtr GetWidget() const;
protected:
  struct State;
  WidgetMover(State& aState);
  ~WidgetMover() = default;
private:
  State& m;
  WidgetMover() = delete;
  VRB_NO_DEFAULTS(WidgetMover)
};

} // namespace crow

#endif // VRBROWSER_WIDGET_MOVER_DOT_H
