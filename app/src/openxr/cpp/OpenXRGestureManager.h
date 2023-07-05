//
// Created by sergio on 05/07/23.
//

#pragma once

#include <memory>
#include <openxr/openxr.h>
#include "OneEuroFilter.h"
#include "OpenXRHelpers.h"
#include "OpenXRInputMappings.h"

namespace crow {

class OpenXRGestureManager;
typedef std::unique_ptr<OpenXRGestureManager> OpenXRGesturePtr;

class OpenXRGestureManager {
public:
virtual ~OpenXRGestureManager() = default;

virtual void populateNextStructureIfNeeded(XrHandJointLocationsEXT& handJointLocations) {};
virtual bool hasAim() const = 0;
virtual XrPosef aimPose(const XrHandJointLocationsEXT&, const XrTime predictedDisplayTime, const OpenXRHandFlags, const vrb::Matrix& head) const = 0;
};

class OpenXRGestureManagerFBHandTrackingAim : public OpenXRGestureManager {
private:
void populateNextStructureIfNeeded(XrHandJointLocationsEXT& handJointLocations) override;
bool hasAim() const override;
XrPosef aimPose(const XrHandJointLocationsEXT&, const XrTime predictedDisplayTime, const OpenXRHandFlags, const vrb::Matrix& head) const override;

XrHandTrackingAimStateFB mFBAimState;
};

class OpenXRGestureManagerHandJoints : public OpenXRGestureManager {
public:
OpenXRGestureManagerHandJoints(HandJointsArray& handJoints);
private:
bool hasAim() const override;
XrPosef aimPose(const XrHandJointLocationsEXT&, const XrTime predictedDisplayTime, const OpenXRHandFlags, const vrb::Matrix& head) const override;
HandJointsArray& mHandJoints;
std::unique_ptr<OneEuroFilterVector> mOneEuroFilterPosition;
};

} // namespace crow


