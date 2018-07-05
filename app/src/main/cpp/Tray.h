/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_TRAY_DOT_H
#define VRBROWSER_TRAY_DOT_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"

#include <memory>
#include <string>
#include <vector>
#include <functional>

namespace crow {

  class Tray;
  typedef std::shared_ptr<Tray> TrayPtr;

  class Tray {
  public:
    static TrayPtr Create(vrb::CreationContextPtr& aContext);
    static const int32_t IconHelp = 0;
    static const int32_t IconSettings = 1;
    static const int32_t IconPrivate = 2;
    static const int32_t IconNew = 3;
    static const int32_t IconNotification = 4;
    static const int32_t IconHide = 5;
    static const int32_t IconExit = 6;

    void Load(const vrb::ModelLoaderAndroidPtr& aLoader);
    bool TestControllerIntersection(const vrb::Vector& aStartPoint, const vrb::Vector& aDirection, vrb::Vector& aResult, bool& aIsInWidget, float& aDistance) const;
    int32_t ProcessEvents(bool aTrayActive, bool aPressed);
    const vrb::Matrix& GetTransform() const;
    void SetTransform(const vrb::Matrix& aTransform);
    void Toggle(const bool aEnabled);
    bool IsLoaded() const;
    vrb::NodePtr GetRoot() const;
  protected:
    struct State;
    Tray(State& aState, vrb::CreationContextPtr& aContext);
    ~Tray();
  private:
    State& m;
    Tray() = delete;
    VRB_NO_DEFAULTS(Tray)
  };

} // namespace crow

#endif //VRBROWSER_TRAY_DOT_H
