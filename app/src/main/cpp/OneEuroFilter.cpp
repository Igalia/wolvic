#include <cstring>
#include <vrb/Quaternion.h>
#include "OneEuroFilter.h"

namespace crow {

float* LowPassFilterQuaternion::filter(const float x[4], float alpha) {
    if (firstTime) {
        firstTime = false;
        memcpy(mHatxprev, x, sizeof(float) * 4);
    }

    auto q = vrb::Quaternion::Slerp({mHatxprev[0],mHatxprev[1],mHatxprev[2],mHatxprev[3]}, {x[0],x[1],x[2],x[3]}, alpha).Normalize();
    memcpy(mHatxprev, q.Data(), sizeof(float) * 4);
    return mHatxprev;
}

float* LowPassFilterVector::filter(const float x[3], float alpha) {
    if (firstTime) {
        firstTime = false;
        memcpy(mHatxprev, x, sizeof(float) * 3);
    }

    float hatx[3];
    for (int i = 0; i < 3; ++i)
        hatx[i] = alpha * x[i] + (1 - alpha) * mHatxprev[i];

    memcpy(mHatxprev, hatx, sizeof(float) * 3);
    return mHatxprev;
}

void
VectorFilterable::setDxIdentity(float dx[3]) {
    dx[0] = dx[1] = dx[2] = 0;
}

void
VectorFilterable::computeDerivate(float dx[3], const float prev[3], const float current[3], float dt)
{
    for (int i = 0; i < 3; ++i)
        dx[i] = (current[i] - prev[i]) / dt;
}

float
VectorFilterable::computeDerivateMagnitude(const float dx[3]) {
    float sqnorm = 0;
    for (int i = 0; i < 3; ++i)
        sqnorm += dx[i] * dx[i];
    return sqrt(sqnorm);
}

void
QuaternionFilterable::setDxIdentity(float dx[4]) {
    dx[0] = dx[1] = dx[2] = 0;
    dx[3] = 1;
}

void
QuaternionFilterable::computeDerivate(float dx[4], const float prev[4], const float current[4], float dt) {
    vrb::Quaternion qPrev(prev);
    vrb::Quaternion qCurrent(current);
    auto qdx = qCurrent * qPrev.Inverse();

    // nlerp instead of slerp
    float rate = 1.0 / dt;
    qdx.x() *= rate;
    qdx.y() *= rate;
    qdx.z() *= rate;
    qdx.w() = qdx.w() * rate + (1.0 - rate);
    qdx = qdx.Normalize();

    memcpy(dx, qdx.Data(), sizeof(float) * 4);
}

float
QuaternionFilterable::computeDerivateMagnitude(const float dx[4]) {
    /// Should be safe since the quaternion we're given has been normalized.
    return 2.0 * acos(static_cast<float>(dx[3]));
}


} // namespace crow
