#include "Camera.h"

Camera::Camera() : mHeading(0.0f), mPitch(0.0) {

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

void
Camera::SetPitch(const float aPitch) {
  mPitch = aPitch;
}

vrb::Matrix
Camera::GetView() {
  return vrb::Matrix::Rotation(vrb::Vector(0.0f, 1.0f, 0.0f), mHeading).PreMultiply(vrb::Matrix::Position(mPosition));
  //return vrb::Matrix::Rotation(vrb::Vector(1.0f, 0.0f, 0.0f), mPitch).PreMultiply(vrb::Matrix::Position(mPosition));
}