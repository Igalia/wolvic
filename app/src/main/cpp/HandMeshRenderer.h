/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#pragma once

#include "vrb/CreationContext.h"
#include "vrb/Vector.h"
#include "vrb/gl.h"
#include <vector>

namespace crow {

struct Controller;

// HandMeshRenderer

struct HandMeshBuffer {
    virtual ~HandMeshBuffer() { };
};
typedef std::shared_ptr<HandMeshBuffer> HandMeshBufferPtr;

class HandMeshRenderer;
typedef std::unique_ptr<HandMeshRenderer> HandMeshRendererPtr;

class HandMeshRenderer {
protected:
    vrb::CreationContextWeak context;
public:
    virtual ~HandMeshRenderer() = default;
    virtual void Update(const uint32_t aControllerIndex, const std::vector<vrb::Matrix>& handJointTransforms,
                        const vrb::GroupPtr& aRoot, HandMeshBufferPtr& aBuffer, const bool aEnabled, const bool leftHanded) { };
    virtual void Draw(const uint32_t aControllerIndex, const vrb::Camera&) { };
};


// HandMeshRendererSkinned

struct HandMeshSkinned;

class HandMeshRendererSkinned: public HandMeshRenderer {
protected:
    struct State;
    State& m;
    HandMeshRendererSkinned(State&, vrb::CreationContextPtr&);
    ~HandMeshRendererSkinned();
public:
    static HandMeshRendererPtr Create(vrb::CreationContextPtr&);
private:
    void Update(const uint32_t aControllerIndex, const std::vector<vrb::Matrix>& handJointTransforms,
                const vrb::GroupPtr& aRoot, HandMeshBufferPtr& aBuffer, const bool aEnabled, const bool leftHanded) override;
    void Draw(const uint32_t aControllerIndex, const vrb::Camera&) override;
    bool LoadHandMeshFromAssets(const bool leftHanded, HandMeshSkinned&);
    void UpdateHandModel(const uint32_t aControllerIndex);
};


// HandMeshRendererGeometry

struct HandMeshVertexMSFT {
    vrb::Vector position;
    vrb::Vector normal;
};
struct HandMeshBufferMSFT: public HandMeshBuffer {
    std::vector<uint32_t> indices;
    std::vector<HandMeshVertexMSFT> vertices;
    uint32_t ibo;
    uint32_t vbo;
    HandMeshBufferMSFT() {
        glGenBuffers(1, &this->ibo);
        glGenBuffers(1, &this->vbo);
    }
    ~HandMeshBufferMSFT() {
        glDeleteBuffers(1, &this->ibo);
        glDeleteBuffers(1, &this->vbo);
    }
};

struct HandMeshBufferMSFT;
typedef std::shared_ptr<HandMeshBufferMSFT> HandMeshBufferMSFTPtr;

struct HandMeshGeometry;

class HandMeshRendererGeometry: public HandMeshRenderer {
protected:
    struct State;
    State& m;
    HandMeshRendererGeometry(State&, vrb::CreationContextPtr&);
public:
    static HandMeshRendererPtr Create(vrb::CreationContextPtr&);
private:
    void Initialize(HandMeshGeometry& state, const vrb::GroupPtr& aRoot);
    void Update(const uint32_t aControllerIndex, const std::vector<vrb::Matrix>& handJointTransforms,
                const vrb::GroupPtr& aRoot, HandMeshBufferPtr&, const bool aEnabled, const bool leftHanded) override;
};

};
