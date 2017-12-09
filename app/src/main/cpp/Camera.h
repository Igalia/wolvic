#ifndef VRBROWSER_CAMERA_H
#define VRBROWSER_CAMERA_H

#include "vrb/Matrix.h"
#include "vrb/Vector.h"

class Camera {
public:
  Camera();
  ~Camera();
  void SetPosition(const vrb::Vector& aPosition);
  void SetHeading(const float aHeading);
  void SetPitch(const float aPitch);
  vrb::Matrix GetView();

protected:
  vrb::Vector mPosition;
  float mHeading;
  float mPitch;
};


#endif //VRBROWSER_CAMERA_H
