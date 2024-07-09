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
virtual XrPosef aimPose(const XrTime predictedDisplayTime, const OpenXRHandFlags, const vrb::Matrix& head) const = 0;
virtual bool systemGestureDetected(const vrb::Matrix& palm, const vrb::Matrix& head) const = 0;
virtual void getTriggerPinchStatusAndFactor(const HandJointsArray& handJoints, bool& isPinching, double& pinchFactor);

static bool handFacesHead(const vrb::Matrix& hand, const vrb::Matrix& head);

private:
double mSmoothIndexThumbDistance { 0 };
};

class OpenXRGestureManagerFBHandTrackingAim : public OpenXRGestureManager {
private:
void populateNextStructureIfNeeded(XrHandJointLocationsEXT& handJointLocations) override;
bool hasAim() const override;
XrPosef aimPose(const XrTime predictedDisplayTime, const OpenXRHandFlags, const vrb::Matrix& head) const override;
bool systemGestureDetected(const vrb::Matrix& hand, const vrb::Matrix& head) const override;
void getTriggerPinchStatusAndFactor(const HandJointsArray& handJoints, bool& isPinching, double& pinchFactor) override;

XrHandTrackingAimStateFB mFBAimState;
};

class OpenXRGestureManagerHandJoints : public OpenXRGestureManager {
public:
typedef struct OneEuroFilterParams {
float mincutoff;
float beta;
float dcutoff;
} OneEuroFilterParams;
OpenXRGestureManagerHandJoints(HandJointsArray&, OneEuroFilterParams* = nullptr);
private:
bool hasAim() const override;
XrPosef aimPose(const XrTime predictedDisplayTime, const OpenXRHandFlags, const vrb::Matrix& head) const override;
bool systemGestureDetected(const vrb::Matrix& palm, const vrb::Matrix& head) const override;

HandJointsArray& mHandJoints;
std::unique_ptr<OneEuroFilterVector> mOneEuroFilterPosition;
};

} // namespace crow


