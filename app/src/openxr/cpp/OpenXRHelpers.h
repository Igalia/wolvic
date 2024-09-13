#pragma once

#include <array>
#include <EGL/egl.h>
#include "jni.h"
#include "Assertions.h"
#include <openxr/openxr.h>
#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>
#include <openxr/openxr_reflection.h>
#include "SystemUtils.h"
#include "vrb/Matrix.h"
#include "Device.h"

namespace crow {

#if defined(HVR)
  const vrb::Vector kAverageHeight(0.0f, 1.6f, 0.0f);
#else
  const vrb::Vector kAverageHeight(0.0f, 1.7f, 0.0f);
#endif

inline std::string GetXrVersionString(XrVersion ver) {
    return Fmt("%d.%d.%d", XR_VERSION_MAJOR(ver), XR_VERSION_MINOR(ver), XR_VERSION_PATCH(ver));
}

// Macro to generate stringify functions for OpenXR enumerations based data provided in openxr_reflection.h
// clang-format off
#define ENUM_CASE_STR(name, val) case name: return #name;
#define MAKE_TO_STRING_FUNC(enumType)                  \
    inline const char* to_string(enumType e) {         \
        switch (e) {                                   \
            XR_LIST_ENUM_##enumType(ENUM_CASE_STR)     \
            default: return "Unknown " #enumType;      \
        }                                              \
    }
// clang-format on

MAKE_TO_STRING_FUNC(XrReferenceSpaceType);
MAKE_TO_STRING_FUNC(XrViewConfigurationType);
MAKE_TO_STRING_FUNC(XrEnvironmentBlendMode);
MAKE_TO_STRING_FUNC(XrSessionState);
MAKE_TO_STRING_FUNC(XrResult);
MAKE_TO_STRING_FUNC(XrFormFactor);

[[noreturn]] inline void ThrowXrResult(XrResult res, const char* originator = nullptr, const char* sourceLocation = nullptr) {
    Throw(Fmt("XrResult failure [%s]", to_string(res)), originator, sourceLocation);
}

inline XrResult CheckXrResult(XrResult res, const char* originator = nullptr, const char* sourceLocation = nullptr) {
    if (XR_FAILED(res)) {
        ThrowXrResult(res, originator, sourceLocation);
    }

    return res;
}

inline XrResult MessageXrResult(XrResult res, const char* originator = nullptr, const char* sourceLocation = nullptr) {
    if (XR_FAILED(res)) {
        VRB_ERROR("XrResult failure [%s] %s %s", to_string(res), originator, sourceLocation);
    }

    return res;
}

#define CHECK_XRCMD(cmd) CheckXrResult(cmd, #cmd, FILE_AND_LINE);

#define RETURN_IF_XR_FAILED(cmd, ...)                                                                            \
    {                                                                                                            \
        auto res = cmd;                                                                                          \
        if (XR_FAILED(res)) {                                                                                    \
            VRB_ERROR("XrResult failure [%s] %s: %d", to_string(res), __FILE__, __LINE__);                       \
            return res;                                                                                          \
        }                                                                                                        \
    }


inline XrPosef XrPoseIdentity() {
  XrPosef t{};
  t.orientation.w = 1;
  return t;
}

inline vrb::Matrix XrPoseToMatrix(const XrPosef& aPose) {
  vrb::Matrix matrix = vrb::Matrix::Rotation(vrb::Quaternion(aPose.orientation.x, aPose.orientation.y, aPose.orientation.z, aPose.orientation.w));
  matrix.TranslateInPlace(vrb::Vector(aPose.position.x, aPose.position.y, aPose.position.z));
  return matrix;
}

inline XrPosef MatrixToXrPose(const vrb::Matrix& aMatrix) {
    vrb::Quaternion q;
    q.SetFromRotationMatrix(aMatrix);
    q = q.Normalize();
    vrb::Vector p = aMatrix.GetTranslation();
    XrPosef result;
    result.orientation = XrQuaternionf{-q.x(), -q.y(), -q.z(), q.w()};
    result.position = XrVector3f{p.x(), p.y(), p.z()};
    return result;
}

typedef std::array<XrHandJointLocationEXT, XR_HAND_JOINT_COUNT_EXT> HandJointsArray;
inline bool IsHandJointPositionValid(const enum XrHandJointEXT aJoint, const HandJointsArray& handJoints) {
    if (aJoint >= handJoints.size())
        return false;
#if SPACES
    // A bug in spaces leaves the locationFlags always empty. The best we can do is to check that
    // all positions are not 0.0 (which is what the runtime returns when they aren't tracked).
    // https://gitlab.freedesktop.org/monado/monado/-/issues/264
    auto pose = handJoints[aJoint].pose;
    return pose.position.x != 0.0 && pose.position.y != 0.0 && pose.position.z != 0.0;
#endif
    return (handJoints[aJoint].locationFlags & XR_SPACE_LOCATION_POSITION_VALID_BIT) != 0;
}

inline XrEnvironmentBlendMode toOpenXRBlendMode(device::BlendMode blendMode) {
    switch (blendMode) {
        case device::BlendMode::Opaque:
            return XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
        case device::BlendMode::Additive:
            return XR_ENVIRONMENT_BLEND_MODE_ADDITIVE;
        case device::BlendMode::AlphaBlend:
            return XR_ENVIRONMENT_BLEND_MODE_ALPHA_BLEND;
        default:
            THROW(Fmt("Unknown blend mode %d", blendMode));
            return XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
    }
}

}  // namespace crow