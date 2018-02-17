/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_BROWSERWINDOW_H
#define VRBROWSER_BROWSERWINDOW_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"

#include <memory>
#include <string>

namespace crow {

class BrowserWindow;
typedef std::shared_ptr<BrowserWindow> BrowserWindowPtr;

class BrowserWindow {
public:
  static BrowserWindowPtr Create(vrb::ContextWeak aContext);
  const std::string& GetSurfaceTextureName() const;
  void GetSurfaceTextureSize(int32_t& aWidth, int32_t& aHeight) const;
  void GetWindowMinAndMax(vrb::Vector& aMin, vrb::Vector& aMax) const;
  bool TestControllerIntersection(const vrb::Matrix& aController, vrb::Vector& aResult, bool& aIsInWindow) const;
  void ConvertToBrowserCoordinates(const vrb::Vector& point, int32_t& aX, int32_t& aY) const;
  const vrb::Matrix GetTransform() const;
  void SetTransform(const vrb::Matrix& aTransform);
  vrb::NodePtr GetRoot();
protected:
  struct State;
  BrowserWindow(State& aState, vrb::ContextWeak& aContext);
  ~BrowserWindow();
private:
  State& m;
  BrowserWindow() = delete;
  VRB_NO_DEFAULTS(BrowserWindow)
};

} // namespace crow

#endif //VRBROWSER_BROWSERWINDOW_H
