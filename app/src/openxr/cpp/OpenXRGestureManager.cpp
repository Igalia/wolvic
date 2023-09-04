//
// Created by sergio on 05/07/23.
//

#include <cstring>
#include "OpenXRGestureManager.h"

namespace crow {

// This threshold is used to detect when palm is facing the head,
// and enable the left hand action gesture. The higher the threshold
// the more aligned objects should be to be considered facing each other.
// 0.7 is generally accepted as good for objects facing each other.
const float kPalmHeadThreshold = 0.7;

bool
OpenXRGestureManager::palmFacesHead(const vrb::Matrix &palm, const vrb::Matrix &head) const {
    // For the hand we take the Y axis because that corresponds to head's Z axis when
    // the hand is in upright position facing head (the gesture we want to detect).
    auto vectorPalm = palm.MultiplyDirection({0, 1, 0});
#ifdef PICOXR
    // Axis are inverted in Pico system versions prior to 5.7.1
    if (CompareBuildIdString(kPicoVersionHandTrackingUpdate))
        vectorPalm = palm.MultiplyDirection({0, 0, -1});
#endif
    auto vectorHead = head.MultiplyDirection({0, 0, -1});
    return vectorPalm.Dot(vectorHead) > kPalmHeadThreshold;
}

void
OpenXRGestureManagerFBHandTrackingAim::populateNextStructureIfNeeded(XrHandJointLocationsEXT& handJointLocations) {
    memset(&mFBAimState, 0, sizeof(XrHandTrackingAimStateFB));
    mFBAimState = { XR_TYPE_HAND_TRACKING_AIM_STATE_FB, handJointLocations.next, 0  };
    handJointLocations.next = &mFBAimState;
}

bool
OpenXRGestureManagerFBHandTrackingAim::hasAim() const {
    return mFBAimState.status & XR_HAND_TRACKING_AIM_VALID_BIT_FB;
}

XrPosef
OpenXRGestureManagerFBHandTrackingAim::aimPose(const XrTime predictedDisplayTime, const OpenXRHandFlags handeness, const vrb::Matrix& head) const {
    ASSERT(hasAim());
    return mFBAimState.aimPose;
}

bool
OpenXRGestureManagerFBHandTrackingAim::systemGestureDetected(const vrb::Matrix& palm, const vrb::Matrix& head) const {
#ifdef PICOXR
    // Pico does not correctly implement the SYSTEM_GESTURE_BIT_FB flags.
    return palmFacesHead(palm, head) && !hasAim();
#else
    return mFBAimState.status & XR_HAND_TRACKING_AIM_SYSTEM_GESTURE_BIT_FB;
#endif
}

OpenXRGestureManagerHandJoints::OpenXRGestureManagerHandJoints(HandJointsArray& handJoints)
    : mHandJoints(handJoints)
    , mOneEuroFilterPosition(std::make_unique<OneEuroFilterVector>(0.25, 0.1, 1)) {
}

bool
OpenXRGestureManagerHandJoints::hasAim() const {
    return IsHandJointPositionValid(XR_HAND_JOINT_MIDDLE_PROXIMAL_EXT, mHandJoints);
}

XrPosef
OpenXRGestureManagerHandJoints::aimPose(const XrTime predictedDisplayTime, const OpenXRHandFlags handeness, const vrb::Matrix& head) const {
    ASSERT(hasAim());
    auto lookAt = [](const vrb::Vector& sourcePoint, const vrb::Vector& destPoint) -> vrb::Quaternion {
        const float EPSILON = 0.000001f;
        vrb::Vector worldForward = { 0, 0, 1 };
        vrb::Vector forwardVector = (destPoint - sourcePoint).Normalize();
        float dot = worldForward.Dot(forwardVector);

        // Vectors pointing to opposite directions -> 180 turn around up direction.
        if (abs(dot - (-1.0f)) < EPSILON) {
            return {0, 1, 0, M_PI };
        }
        // Vectors pointing in the same direction -> identity quaternion (no rotation)
        if (abs(dot - (1.0f)) < EPSILON) {
            return {0, 0, 0, 1 };
        }

        auto quaternionFromAxisAndAngle = [](const vrb::Vector& axis, float angle) -> vrb::Quaternion {
            float halfAngle = angle * .5f;
            float s = sin(halfAngle);
            return { axis.x() * s, axis.y() * s, axis.z() * s, cos(halfAngle) };
        };

        float rotationAngle = acos(dot);
        auto rotationAxis = worldForward.Cross(forwardVector);
        return quaternionFromAxisAndAngle(rotationAxis.Normalize(), rotationAngle);
    };

    auto aimPose = mHandJoints[XR_HAND_JOINT_MIDDLE_PROXIMAL_EXT].pose;
    auto pos = vrb::Vector(aimPose.position.x, aimPose.position.y, aimPose.position.z);
    float* filteredPos = mOneEuroFilterPosition->filter(predictedDisplayTime, pos.Data());

    auto shoulder = head.MultiplyDirection({handeness == Right ? 0.15f : -0.15f,-0.25,0});
    auto q = lookAt({filteredPos[0], filteredPos[1], filteredPos[2]}, shoulder);
    aimPose.orientation = {q.x(), q.y(), q.z(), q.w() };
    return aimPose;
}

bool
OpenXRGestureManagerHandJoints::systemGestureDetected(const vrb::Matrix& palm, const vrb::Matrix& head) const {
    return palmFacesHead(palm, head);
}

}