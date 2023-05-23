#pragma once

#include <cstdint>

namespace crow {

// This is an implementation of the 1€ filter https://gery.casiez.net/1euro/ by Géry Casiez, Nicolas
// Roussel, and Daniel Vogel. The 1€ filter is a simple algorithm to filter noisy signals for high
// precision and responsiveness. It uses a first order low-pass filter with an adaptive cutoff
// frequency: at low speeds, a low cutoff stabilizes the signal by reducing jitter, but as speed
// increases, the cutoff is increased to reduce lag. The algorithm is easy to implement, uses very
// few resources, and with two easily understood parameters, it is easy to tune. In a comparison
// with other filters, the 1 € filter has less lag using a reference amount of jitter reduction.

// This code was inspired by the template implementation https://gery.casiez.net/1euro/1efilter.cc
// and specific code for quaternions https://github.com/vrpn/vrpn/blob/master/vrpn_OneEuroFilter.h

template<typename Filterable, int SIZE>
class OneEuroFilter {
public:

OneEuroFilter(float mincutoff, float beta, float dcutoff)
    : mincutoff(mincutoff)
    , dcutoff(dcutoff)
    , beta(beta) {};

OneEuroFilter()
    : mincutoff(1)
    , dcutoff(1)
    , beta(0.5) {};

float* filter(int64_t timestamp, const float x[SIZE]) {
    float dx[SIZE];

    // Timestamp is expressed in nanoseconds in OpenXR, the algorithm expects seconds.
    float dt = float(timestamp - mPreviousDt) / float(1000000000);
    mPreviousDt = timestamp;

    if (firstTime) {
        firstTime = false;
        Filterable::setDxIdentity(dx);
        dt = 0.000000001;
    } else {
        Filterable::computeDerivate(dx, xfilt.hatxprev(), x, dt);
    }

    auto computeAlpha = [](float dt, float cutoff) {
        float tau = 1.0 / (2.0 * M_PI * cutoff);
        return 1.0 / (1.0 + tau / dt);
    };

    float derivateMagnitude = Filterable::computeDerivateMagnitude(
            dxfilt.filter(dx, computeAlpha(dt, dcutoff)));
    float cutoff = mincutoff + beta * derivateMagnitude;

    return xfilt.filter(x, computeAlpha(dt, cutoff));
}

private:
int64_t mPreviousDt { 0 };
bool firstTime {true };
float mincutoff;
float dcutoff;
float beta;
typename Filterable::Filter xfilt;
typename Filterable::Filter dxfilt;
};

// This is not the low pass filter defined in the paper, but a different version suitable for
// quaternions in which low pass filtering is reframed in terms of interpolation using SLERP
// https://github.com/vrpn/vrpn/blob/master/vrpn_OneEuroFilter.h
class LowPassFilterQuaternion {
public:
float* filter(const float x[4], float alpha);
float* hatxprev() { return mHatxprev; }

private:
bool firstTime {true };
float mHatxprev[4];
};

class LowPassFilterVector {
public:
float* filter(const float x[3], float alpha);
float* hatxprev() { return mHatxprev; }

private:
bool firstTime {true };
float mHatxprev[3];
};

class VectorFilterable {
public:
typedef LowPassFilterVector Filter;

static void setDxIdentity(float dx[3]);
static void computeDerivate(float dx[3], const float prev[3], const float current[3], float dt);
static float computeDerivateMagnitude(const float dx[3]);

};
typedef OneEuroFilter<VectorFilterable, 3> OneEuroFilterVector;

class QuaternionFilterable {
public:
typedef LowPassFilterQuaternion Filter;

static void setDxIdentity(float dx[4]);
static void computeDerivate(float dx[4], const float prev[4], const float current[4], float dt);
static float computeDerivateMagnitude(const float dx[4]);

};
typedef OneEuroFilter<QuaternionFilterable, 4> OneEuroFilterQuaternion;

} // namespace crow
