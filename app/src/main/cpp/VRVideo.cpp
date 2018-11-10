/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "VRVideo.h"
#include "vrb/ConcreteClass.h"
#include "vrb/Color.h"
#include "vrb/CreationContext.h"
#include "vrb/Geometry.h"
#include "vrb/Matrix.h"
#include "vrb/ModelLoaderAndroid.h"
#include "vrb/RenderState.h"
#include "vrb/RenderContext.h"
#include "vrb/TextureGL.h"
#include "vrb/TextureSurface.h"
#include "vrb/Toggle.h"
#include "vrb/Transform.h"
#include "vrb/VertexArray.h"

#include "Quad.h"
#include "Widget.h"

namespace crow {

struct VRVideo::State {
  vrb::CreationContextWeak context;
  WidgetPtr window;
  VRVideoProjection projection;
  vrb::TogglePtr root;
  vrb::TogglePtr leftEye;
  vrb::TogglePtr rightEye;
  State()
  {
  }

  void Initialize(const WidgetPtr& aWindow, const VRVideoProjection aProjection) {
    vrb::CreationContextPtr create = context.lock();
    window = aWindow;
    projection = aProjection;
    root = vrb::Toggle::Create(create);
    updateProjection();
    root->AddNode(leftEye);
    if (rightEye) {
      root->AddNode(rightEye);
    }
  }

  void updateProjection() {
    switch (projection) {
      case VRVideoProjection::VIDEO_PROJECTION_3D_SIDE_BY_SIDE:
        leftEye = createQuadProjection(device::EyeRect(0.0f, 0.0f, 0.5f, 1.0f));
        rightEye = createQuadProjection(device::EyeRect(0.5f, 0.0f, 0.5f, 1.0f));
        break;
      case VRVideoProjection::VIDEO_PROJECTION_360:
        leftEye = createSphereProjection(false, device::EyeRect(0.0f, 0.0f, 1.0f, 1.0f));
        break;
      case VRVideoProjection::VIDEO_PROJECTION_360_STEREO:
        leftEye = createSphereProjection(false, device::EyeRect(0.0f, 0.5f, 1.0f, 0.5f));
        rightEye = createSphereProjection(false, device::EyeRect(0.0f, 0.0f, 1.0f, 0.5f));
        break;
      case VRVideoProjection::VIDEO_PROJECTION_180:
        leftEye = createSphereProjection(true, device::EyeRect(0.0f, 0.0f, 1.0f, 1.0f));
        break;
      case VRVideoProjection::VIDEO_PROJECTION_180_STEREO_LEFT_RIGHT:
        leftEye = createSphereProjection(true, device::EyeRect(0.0f, 0.0f, 0.5f, 1.0f));
        rightEye = createSphereProjection(true, device::EyeRect(0.5f, 0.0f, 0.5f, 1.0f));
        break;
      case VRVideoProjection::VIDEO_PROJECTION_180_STEREO_TOP_BOTTOM:
        leftEye = createSphereProjection(true, device::EyeRect(0.0f, 0.5f, 1.0f, 0.5f));
        rightEye = createSphereProjection(true, device::EyeRect(0.0f, 0.0f, 1.0f, 0.5f));
        break;
    }
  }

  vrb::TogglePtr createSphereProjection(bool half, device::EyeRect aUVRect) {
    const int kCols = 30;
    const int kRows = 30;
    const float kRadius = 20.0f;

    vrb::CreationContextPtr create = context.lock();
    vrb::VertexArrayPtr array = vrb::VertexArray::Create(create);
    

    for (float row = 0; row <= kRows; row+= 1.0f) {
      const float alpha = row * (float)M_PI / kRows;
      const float sinAlpha = sinf(alpha);
      const float cosAlpha = cosf(alpha);

      for (float col = 0; col <= kCols; col++) {
        const float beta = col * (half ? 1.0f : 2.0f) * (float)M_PI / kCols;
        const float sinBeta = sinf(beta);
        const float cosBeta = cosf(beta);

        vrb::Vector vertex;
        vrb::Vector uv;
        vrb::Vector normal;
        normal.x() = cosBeta * sinAlpha;
        normal.y() = cosAlpha;
        normal.z() = sinBeta * sinAlpha;
        uv.x() = aUVRect.mX + (col / kCols) * aUVRect.mWidth;  // u
        uv.y() = aUVRect.mY + (row / kRows) * aUVRect.mHeight; // v
        vertex.x() = kRadius * normal.x();
        vertex.y() = kRadius * normal.y();
        vertex.z() = kRadius * normal.z();

        array->AppendVertex(vertex);
        array->AppendUV(uv);
        array->AppendNormal(vertex.Normalize());
      }
    }

    std::vector<int> indices;

    vrb::RenderStatePtr state = vrb::RenderState::Create(create);
    state->SetLightsEnabled(false);
    vrb::TexturePtr texture = std::dynamic_pointer_cast<vrb::Texture>(window->GetSurfaceTexture());
    state->SetTexture(texture);
    vrb::GeometryPtr geometry = vrb::Geometry::Create(create);
    geometry->SetVertexArray(array);
    geometry->SetRenderState(state);

    for (int row = 0; row < kRows; row++) {
      for (int col = 0; col < kCols; col++) {
        int first = 1 + (row * (kCols + 1)) + col;
        int second = first + kCols + 1;

        indices.clear();
        indices.push_back(first);
        indices.push_back(second);
        indices.push_back(first + 1);

        indices.push_back(second);
        indices.push_back(second + 1);
        indices.push_back(first + 1);
        geometry->AddFace(indices, indices, indices);
      }
    }

    vrb::TransformPtr transform = vrb::Transform::Create(create);
    if (half) {
      vrb::Matrix matrix = vrb::Matrix::Rotation(vrb::Vector(0.0f, 1.0f, 0.0f), (float) M_PI);
      transform->SetTransform(matrix);
    } else {
      transform->SetTransform(vrb::Matrix::Rotation(vrb::Vector(0.0f, 1.0f, 0.0f), (float) M_PI * -0.5f));
    }
    transform->AddNode(geometry);

    vrb::TogglePtr result = vrb::Toggle::Create(create);
    result->AddNode(transform);
    return result;
  }

  vrb::TogglePtr createQuadProjection(device::EyeRect aUVRect) {
    vrb::CreationContextPtr create = context.lock();
    vrb::Vector min, max;
    window->GetWidgetMinAndMax(min, max);
    vrb::GeometryPtr geometry = Quad::CreateGeometry(create, min, max, aUVRect);
    vrb::RenderStatePtr state = vrb::RenderState::Create(create);
    state->SetLightsEnabled(false);
    vrb::TexturePtr texture = std::dynamic_pointer_cast<vrb::Texture>(window->GetSurfaceTexture());
    state->SetTexture(texture);
    geometry->SetRenderState(state);

    vrb::TransformPtr transform = vrb::Transform::Create(create);
    transform->SetTransform(window->GetTransform());
    transform->AddNode(geometry);
    vrb::TogglePtr result = vrb::Toggle::Create(create);
    result->AddNode(transform);
    return result;
  }
};

void
VRVideo::SelectEye(device::Eye aEye) {
  if (!m.rightEye) {
    // Not stereo projection, always show the left eye.
    return;
  }
  m.leftEye->ToggleAll(aEye == device::Eye::Left);
  m.rightEye->ToggleAll(aEye == device::Eye::Right);
}

vrb::NodePtr
VRVideo::GetRoot() const {
  return m.root;
}

VRVideoPtr
VRVideo::Create(vrb::CreationContextPtr aContext, const WidgetPtr& aWindow, const VRVideoProjection aProjection) {
  VRVideoPtr result = std::make_shared<vrb::ConcreteClass<VRVideo, VRVideo::State> >(aContext);
  result->m.Initialize(aWindow, aProjection);
  return result;
}


VRVideo::VRVideo(State& aState, vrb::CreationContextPtr& aContext) : m(aState) {
  m.context = aContext;
}

VRVideo::~VRVideo() {}

} // namespace crow
