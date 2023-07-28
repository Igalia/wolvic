/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#pragma once

#include "vrb/CreationContext.h"

namespace crow {

struct Controller;

class HandMeshRenderer;
typedef std::unique_ptr<HandMeshRenderer> HandMeshRendererPtr;

class HandMeshRenderer {
protected:
    vrb::CreationContextWeak context;
public:
    virtual ~HandMeshRenderer() = default;
    virtual void Update(Controller&, const vrb::GroupPtr& aRoot, const bool aEnabled) = 0;
    virtual void Draw(Controller&, const vrb::Camera&) { };
};

class HandMeshRendererSpheres: public HandMeshRenderer {
protected:
    struct State;
    State& m;
    HandMeshRendererSpheres(State&, vrb::CreationContextPtr&);
public:
    static HandMeshRendererPtr Create(vrb::CreationContextPtr&);
private:
    void Update(Controller&, const vrb::GroupPtr& aRoot, const bool aEnabled) override;
};

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
    void Update(Controller&, const vrb::GroupPtr& aRoot, const bool aEnabled) override;
    void Draw(Controller&, const vrb::Camera&) override;
    bool LoadHandMeshFromAssets(Controller&, HandMeshSkinned&);
    void UpdateHandModel(const Controller&);
};

};
