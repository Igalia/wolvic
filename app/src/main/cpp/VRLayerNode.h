/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VR_LAYER_NODE_H
#define VR_LAYER_NODE_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"
#include "vrb/Drawable.h"
#include "vrb/Node.h"
#include "vrb/gl.h"

#include <vector>

namespace crow {

class VRLayer;
typedef std::shared_ptr<VRLayer> VRLayerPtr;

class VRLayerNode;
typedef std::shared_ptr<VRLayerNode> VRLayerNodePtr;

class VRLayerNode : public vrb::Node, public vrb::Drawable {
public:
  static VRLayerNodePtr Create(vrb::CreationContextPtr& aContext, const VRLayerPtr& aLayer);

  // Node interface
  void Cull(vrb::CullVisitor& aVisitor, vrb::DrawableList& aDrawables) override;

  // From Drawable
  vrb::RenderStatePtr& GetRenderState() override;
  void SetRenderState(const vrb::RenderStatePtr& aRenderState) override;
  void Draw(const vrb::Camera& aCamera, const vrb::Matrix& aModelTransform) override;
protected:
  struct State;
  VRLayerNode(State& aState, vrb::CreationContextPtr& aContext);
  ~VRLayerNode();

private:
  State& m;
  VRLayerNode() = delete;
  VRB_NO_DEFAULTS(VRLayerNode)
};

}

#endif //  VR_LAYER_NODE_H
