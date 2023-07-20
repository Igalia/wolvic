/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#pragma once

#include "vrb/CreationContext.h"

namespace crow {

enum class HandMeshStrategy { None, Skinned, Spheres };

struct Controller;

class HandMeshRenderer;
typedef std::shared_ptr<HandMeshRenderer> HandMeshRendererPtr;

class HandMeshRenderer {
protected:
    vrb::CreationContextWeak context;
    bool enabled = false;
    virtual void Shutdown() { };
public:
    virtual void Initialize(vrb::CreationContextPtr& aContext) { context = aContext; };
    virtual void Update(Controller& aController, const vrb::GroupPtr& aRoot, const bool aEnabled) = 0;
    virtual void Draw(Controller& aController, const vrb::Camera& aCamera) { };
};

class HandMeshRendererSpheres: public HandMeshRenderer {
protected:
    struct State;
public:
    HandMeshRendererSpheres(State& aState, vrb::CreationContextPtr& aContext);
    ~HandMeshRendererSpheres();
    static HandMeshRendererPtr Create(vrb::CreationContextPtr& aContext);
    void Update(Controller& aController, const vrb::GroupPtr& aRoot, const bool aEnabled) override;
private:
    State& m;
};

struct HandMeshSkinned;

class HandMeshRendererSkinned: public HandMeshRenderer {
protected:
    struct State;
public:
    HandMeshRendererSkinned(State& aState, vrb::CreationContextPtr& aContext);
    ~HandMeshRendererSkinned();
    static HandMeshRendererPtr Create(vrb::CreationContextPtr& aContext);
    void Update(Controller& aController, const vrb::GroupPtr& aRoot, const bool aEnabled) override;
    void Draw(Controller& aController, const vrb::Camera& aCamera) override;
private:
    State& m;
    void Initialize(vrb::CreationContextPtr& aContext) override;
    void Shutdown() override;
    bool LoadHandMeshFromAssets(Controller& aController, HandMeshSkinned& aHandMeshSkinned);
    void UpdateHandModel(const Controller& aController);
};

};
