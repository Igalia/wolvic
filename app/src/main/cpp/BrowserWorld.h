#ifndef VRBROWSER_BROWSERWORLD_H
#define VRBROWSER_BROWSERWORLD_H

#include "vrb/Matrix.h"
#include "vrb/RenderObject.h"
#include "vrb/Vector.h"

class BrowserWorld {
public:
  BrowserWorld();
  ~BrowserWorld();

  void Init();
  void AddRenderObject(vrb::RenderObjectPtr& aObject);
  void Draw(const vrb::Matrix& aPerspective, const vrb::Matrix& aView);

protected:
  struct State;
  State& m;
};

#endif //VRBROWSER_BROWSERWORLD_H
