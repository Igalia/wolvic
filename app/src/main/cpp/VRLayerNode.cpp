/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "VRLayerNode.h"
#include "VRLayer.h"

#include "vrb/private/DrawableState.h"
#include "vrb/private/NodeState.h"
#include "vrb/private/ResourceGLState.h"

#include "vrb/Camera.h"
#include "vrb/ConcreteClass.h"
#include "vrb/CullVisitor.h"
#include "vrb/DrawableList.h"
#include "vrb/GLError.h"
#include "vrb/Logger.h"
#include "vrb/Matrix.h"
#include "vrb/RenderState.h"

#include <vector>

using namespace vrb;

namespace crow {

struct VRLayerNode::State : public Node::State, public Drawable::State {
  RenderStatePtr renderState;
  VRLayerPtr layer;

  State() {}
};

VRLayerNodePtr
VRLayerNode::Create(CreationContextPtr& aContext, const VRLayerPtr& aLayer) {
  auto result = std::make_shared<ConcreteClass<VRLayerNode, VRLayerNode::State> >(aContext);
  result->m.layer = aLayer;
  result->m.renderState = vrb::RenderState::Create(aContext);
  return result;
}

// Node interface
void
VRLayerNode::Cull(CullVisitor& aVisitor, DrawableList& aDrawables) {
  aDrawables.AddDrawable(std::move(CreateDrawablePtr()), aVisitor.GetTransform());
}

// Drawable interface
RenderStatePtr&
VRLayerNode::GetRenderState() {
  return m.renderState;
}

void
VRLayerNode::SetRenderState(const RenderStatePtr& aRenderState) {
  m.renderState = aRenderState;
}

void
VRLayerNode::Draw(const Camera& aCamera, const Matrix& aModelTransform) {
  m.layer->RequestDraw();
  device::Eye eye = m.layer->GetCurrentEye();
  m.layer->SetModelTransform(eye, aModelTransform);
  m.layer->SetView(eye, aCamera.GetView());
}

VRLayerNode::VRLayerNode(State& aState, CreationContextPtr& aContext) :
    Node(aState, aContext),
    Drawable(aState, aContext),
    m(aState)
{}
VRLayerNode::~VRLayerNode() {}

}

