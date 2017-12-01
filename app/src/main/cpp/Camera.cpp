#include "Camera.h"

Camera::Camera() {

}

Camera::~Camera() {

}

void
Camera::SetPosition(const vrb::Vector& aPosition) {
  mPosition = aPosition;
}

void
Camera::SetHeading(const float aHeading) {
  mHeading = aHeading;
}

vrb::Matrix
Camera::GetView() {
  return vrb::Matrix::Rotation(vrb::Vector(0.0f, 1.0f, 0.0f), mHeading).PreMultiply(vrb::Matrix::Position(mPosition));
}