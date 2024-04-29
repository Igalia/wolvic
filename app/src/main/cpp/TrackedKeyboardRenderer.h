/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#pragma once

#include "vrb/CreationContext.h"
#include "vrb/Vector.h"
#include "vrb/gl.h"
#include <vector>
#include "tiny_gltf.h"

namespace crow {

class TrackedKeyboardRenderer;
typedef std::unique_ptr<TrackedKeyboardRenderer> TrackedKeyboardRendererPtr;

class TrackedKeyboardRenderer {
protected:
    struct State;
    State& m;
    TrackedKeyboardRenderer(State&, vrb::CreationContextPtr&);
public:
    static TrackedKeyboardRendererPtr Create(vrb::CreationContextPtr&);
    bool LoadKeyboardMesh(const std::vector<uint8_t>& modelBuffer);
    void SetTransform(const vrb::Matrix&);
    void SetVisible(const bool);
    void Draw(const vrb::Camera& aCamera);
    ~TrackedKeyboardRenderer();
private:
    bool InitializeGL();
    static bool ImageLoaderCallback(tinygltf::Image*, const int, std::string*, std::string*, int, int,
                                    const unsigned char* data, int dataSize, void* user_pointer);
};

};
