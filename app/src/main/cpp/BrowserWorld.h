#ifndef VRBROWSER_BROWSERWORLD_H
#define VRBROWSER_BROWSERWORLD_H

#include "vrb/Matrix.h"
#include "vrb/Vector.h"

class BrowserWorld {
public:
  BrowserWorld();
  ~BrowserWorld();

  void Init();
  void Draw(const vrb::Matrix& aPerspective, const vrb::Matrix& aView);

protected:
};

#endif //VRBROWSER_BROWSERWORLD_H
