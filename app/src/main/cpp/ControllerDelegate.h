#ifndef CONTROLLER_DELEGATE_DOT_H
#define CONTROLLER_DELEGATE_DOT_H

#include "vrb/MacroUtils.h"
#include "vrb/Forward.h"
#include "GestureDelegate.h"

#include <memory>

namespace crow {

class ControllerDelegate;
typedef std::shared_ptr<ControllerDelegate> ControllerDelegatePtr;

class ControllerDelegate {
public:
  enum Button {
    BUTTON_TRIGGER   = 1 << 0,
    BUTTON_TOUCHPAD  = 1 << 1,
    BUTTON_MENU      = 1 << 2,
  };
  virtual void CreateController(const int32_t aControllerIndex, const int32_t aModelIndex) = 0;
  virtual void DestroyController(const int32_t aControllerIndex) = 0;
  virtual void SetEnabled(const int32_t aControllerIndex, const bool aEnabled) = 0;
  virtual void SetVisible(const int32_t aControllerIndex, const bool aVisible) = 0;
  virtual void SetTransform(const int32_t aControllerIndex, const vrb::Matrix& aTransform) = 0;
  virtual void SetButtonState(const int32_t aControllerIndex, const int32_t aWhichButton, const bool aPressed) = 0;
  virtual void SetTouchPosition(const int32_t aControllerIndex, const float aTouchX, const float aTouchY) = 0;
  virtual void EndTouch(const int32_t aControllerIndex) = 0;
  virtual void SetScrolledDelta(const int32_t aControllerIndex, const float aScrollDeltaX, const float aScrollDeltaY) = 0;
protected:
  ControllerDelegate() {}
private:
  VRB_NO_DEFAULTS(ControllerDelegate)
};

}

#endif // CONTROLLER_DELEGATE_DOT_H
