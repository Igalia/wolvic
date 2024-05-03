/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "VRVideo.h"
#include "DeviceDelegate.h"
#include "VRLayer.h"
#include "VRLayerNode.h"
#include "vrb/ConcreteClass.h"
#include "vrb/Color.h"
#include "vrb/CreationContext.h"
#include "vrb/Geometry.h"
#include "vrb/Matrix.h"
#include "vrb/ModelLoaderAndroid.h"
#include "vrb/Program.h"
#include "vrb/ProgramFactory.h"
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
  std::weak_ptr<DeviceDelegate> deviceWeak;
  WidgetPtr window;
  VRVideoProjection projection;
  vrb::TransformPtr root;
  vrb::TogglePtr leftEye;
  vrb::TogglePtr rightEye;
  VRLayerPtr layer;
  device::EyeRect layerTextureBackup[2];
  bool mUseSameLayerForBothEyesBackup;
  float mWorldWidthBackup;
  float mWorlHeightBackup;
  State()
    : mWorldWidthBackup(0)
    , mWorlHeightBackup(0)
    , mUseSameLayerForBothEyesBackup(true)
  {
  }

  void Initialize(const WidgetPtr& aWindow, const VRVideoProjection aProjection) {
    vrb::CreationContextPtr create = context.lock();
    window = aWindow;
    projection = aProjection;
    root = vrb::Transform::Create(create);
    VRLayerSurfacePtr windowLayer = aWindow->GetLayer();
    if (windowLayer) {
      layerTextureBackup[0] = windowLayer->GetTextureRect(device::Eye::Left);
      layerTextureBackup[1] = windowLayer->GetTextureRect(device::Eye::Right);
      mWorldWidthBackup = windowLayer->GetWorldWidth();
      mWorlHeightBackup = windowLayer->GetWorldHeight();
      updateProjectionLayer();
    } else {
      updateProjection();
    }
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
      case VRVideoProjection::VIDEO_PROJECTION_3D_TOP_BOTTOM:
        leftEye = createQuadProjection(device::EyeRect(0.0f, 0.0f, 1.0f, 0.5f));
        rightEye = createQuadProjection(device::EyeRect(0.0f, 0.5f, 1.0f, 0.5f));
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

  void updateProjectionLayer() {
    switch (projection) {
      case VRVideoProjection::VIDEO_PROJECTION_3D_SIDE_BY_SIDE:
        leftEye = createQuadProjectionLayer(device::EyeRect(0.0f, 0.0f, 0.5f, 1.0f), device::EyeRect(0.5f, 0.0f, 0.5f, 1.0f));
        break;
      case VRVideoProjection::VIDEO_PROJECTION_3D_TOP_BOTTOM:
        leftEye = createQuadProjectionLayer(device::EyeRect(0.0f, 0.0f, 1.0f, 0.5f), device::EyeRect(0.0f, 0.5f, 1.0f, 0.5f));
        break;
      case VRVideoProjection::VIDEO_PROJECTION_360:
        create360ProjectionLayer();
        break;
      case VRVideoProjection::VIDEO_PROJECTION_360_STEREO:
        create360StereoProjectionLayer();
        break;
      case VRVideoProjection::VIDEO_PROJECTION_180:
        create180ProjectionLayer();
        break;
      case VRVideoProjection::VIDEO_PROJECTION_180_STEREO_LEFT_RIGHT:
        create180LRProjectionLayer();
        break;
      case VRVideoProjection::VIDEO_PROJECTION_180_STEREO_TOP_BOTTOM:
        create180TBProjectionLayer();
        break;
    }
  }

  vrb::TogglePtr createSphereProjection(bool half, device::EyeRect aUVRect) {
    const int kCols = 70;
    const int kRows = 70;
    const float kRadius = 10.0f;

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

    vrb::ProgramPtr program = create->GetProgramFactory()->CreateProgram(create, vrb::FeatureSurfaceTexture | vrb::FeatureHighPrecision);
    vrb::RenderStatePtr state = vrb::RenderState::Create(create);
    state->SetProgram(program);
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

  void create360ProjectionLayer() {
    vrb::CreationContextPtr create = context.lock();
    DeviceDelegatePtr device = deviceWeak.lock();
    VRLayerEquirectPtr equirect = device->CreateLayerEquirect(window->GetLayer());
    layer = equirect;

    leftEye = vrb::Toggle::Create(create);
    leftEye->AddNode(VRLayerNode::Create(create, equirect));
  }

  void create360StereoProjectionLayer() {
    vrb::CreationContextPtr create = context.lock();
    DeviceDelegatePtr device = deviceWeak.lock();
    VRLayerEquirectPtr equirect = device->CreateLayerEquirect(window->GetLayer());
    layer = equirect;

    vrb::Matrix leftTransform = vrb::Matrix::Identity();
    leftTransform.ScaleInPlace(vrb::Vector(1.0f, 0.5f, 1.0f));
    equirect->SetUVTransform(device::Eye::Left, leftTransform);

    vrb::Matrix rightTransform =  vrb::Matrix::Position(vrb::Vector(0.0f, 0.5f, 0.0f));
    rightTransform.ScaleInPlace(vrb::Vector(1.0f, 0.5f, 1.0f));
    equirect->SetUVTransform(device::Eye::Right, rightTransform);

    leftEye = vrb::Toggle::Create(create);
    leftEye->AddNode(VRLayerNode::Create(create, equirect));
    rightEye = vrb::Toggle::Create(create);
    rightEye->AddNode(VRLayerNode::Create(create, equirect));
  }

  vrb::TogglePtr create180LayerToggle(const VRLayerEquirectPtr& aLayer) {
    vrb::CreationContextPtr create = context.lock();
    vrb::TogglePtr result = vrb::Toggle::Create(create);
    vrb::Matrix rotation = vrb::Matrix::Rotation(vrb::Vector(0.0f, 1.0f, 0.0f), -(float)M_PI * 0.5f);
    vrb::TransformPtr transform = vrb::Transform::Create(create);
    transform->AddNode(VRLayerNode::Create(create, aLayer));
    transform->SetTransform(rotation);
    result->AddNode(transform);
    return result;
  }

  void create180ProjectionLayer() {
    vrb::CreationContextPtr create = context.lock();
    DeviceDelegatePtr device = deviceWeak.lock();
    VRLayerEquirectPtr equirect = device->CreateLayerEquirect(window->GetLayer());
    layer = equirect;

    vrb::Matrix uvTransform = vrb::Matrix::Identity();
    uvTransform.ScaleInPlace(vrb::Vector(2.0f, 1.0f, 1.0f));

    equirect->SetUVTransform(device::Eye::Left, uvTransform);
    equirect->SetUVTransform(device::Eye::Right, uvTransform);

    leftEye = create180LayerToggle(equirect);
  }

#ifdef OPENXR
  void create180LRProjectionLayer() {
    vrb::CreationContextPtr create = context.lock();
    DeviceDelegatePtr device = deviceWeak.lock();
    VRLayerEquirectPtr equirect = device->CreateLayerEquirect(window->GetLayer());
    layer = equirect;

    equirect->SetTextureRect(device::Eye::Left, device::EyeRect(0.0f, 0.0f, 0.5f, 1.0f));
    equirect->SetTextureRect(device::Eye::Right, device::EyeRect(0.5f, 0.0f, 0.5f, 1.0f));
    auto UVtransform = vrb::Matrix::Identity().Scale(vrb::Vector(2.0f, 1.0f, 1.0f)).Translate(vrb::Vector(-0.5, 0.0, 0.0));
    equirect->SetUVTransform(device::Eye::Left, UVtransform);
    equirect->SetUVTransform(device::Eye::Right, UVtransform);
    equirect->SetUseSameLayerForBothEyes(false);

    leftEye = create180LayerToggle(equirect);
  }
#else
  void create180LRProjectionLayer() {
    vrb::CreationContextPtr create = context.lock();
    DeviceDelegatePtr device = deviceWeak.lock();
    VRLayerEquirectPtr equirect = device->CreateLayerEquirect(window->GetLayer());
    layer = equirect;

    equirect->SetTextureRect(device::Eye::Left, device::EyeRect(0.0f, 0.0f, 0.5f, 1.0f));
    equirect->SetTextureRect(device::Eye::Right, device::EyeRect(0.5f, 0.0f, 0.5f, 1.0f));
    equirect->SetUVTransform(device::Eye::Right, vrb::Matrix::Position(vrb::Vector(0.5f, 0.0f, 0.0f)));

    leftEye = create180LayerToggle(equirect);
    rightEye = create180LayerToggle(equirect);
  }
#endif

  void create180TBProjectionLayer() {
    vrb::CreationContextPtr create = context.lock();
    DeviceDelegatePtr device = deviceWeak.lock();
    VRLayerEquirectPtr equirect = device->CreateLayerEquirect(window->GetLayer());
    layer = equirect;

    equirect->SetTextureRect(device::Eye::Right, device::EyeRect(0.0f, 0.5f, 1.0f, 0.5f));
    equirect->SetTextureRect(device::Eye::Left, device::EyeRect(0.0f, 0.0f, 1.0f, 0.5f));

    vrb::Matrix uvTransform = vrb::Matrix::Identity();
    uvTransform.ScaleInPlace(vrb::Vector(2.0f, 0.5f, 1.0f));
    equirect->SetUVTransform(device::Eye::Left, uvTransform);
    uvTransform.TranslateInPlace(vrb::Vector(0.0f, 0.5f, 0.0f));
    equirect->SetUVTransform(device::Eye::Right, uvTransform);

    leftEye = create180LayerToggle(equirect);
    rightEye = create180LayerToggle(equirect);
  }

  vrb::TogglePtr createQuadProjectionLayer(const device::EyeRect& aLeftUVRect, const device::EyeRect& aRightUVRect) {
    vrb::CreationContextPtr create = context.lock();
    VRLayerSurfacePtr windowLayer = window->GetLayer();
    windowLayer->SetTextureRect(device::Eye::Left, aLeftUVRect);
    windowLayer->SetTextureRect(device::Eye::Right, aRightUVRect);
    mUseSameLayerForBothEyesBackup = windowLayer->GetUseSameLayerForBothEyes();
    windowLayer->SetUseSameLayerForBothEyes(false);
    layer = windowLayer;

    vrb::TransformPtr transform = vrb::Transform::Create(create);
    transform->SetTransform(window->GetTransform());
    transform->AddNode(VRLayerNode::Create(create, layer));
    vrb::TogglePtr result = vrb::Toggle::Create(create);
    result->AddNode(transform);

    return result;
  }

  vrb::TogglePtr createQuadProjection(device::EyeRect aUVRect) {
    vrb::CreationContextPtr create = context.lock();
    vrb::Vector min, max;
    window->GetWidgetMinAndMax(min, max);
    vrb::GeometryPtr geometry = Quad::CreateGeometry(create, min, max, aUVRect);
    vrb::ProgramPtr program = create->GetProgramFactory()->CreateProgram(create, vrb::FeatureSurfaceTexture);
    vrb::RenderStatePtr state = vrb::RenderState::Create(create);
    state->SetProgram(program);
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
  if (m.layer) {
    m.layer->SetCurrentEye(aEye);
  }

  if (m.leftEye) {
    m.leftEye->ToggleAll(aEye == device::Eye::Left || !m.rightEye);
  }

  if (m.rightEye) {
    m.rightEye->ToggleAll(aEye == device::Eye::Right);
  }
}

vrb::NodePtr
VRVideo::GetRoot() const {
  return m.root;
}

void
VRVideo::Exit() {
  VRLayerSurfacePtr windowLayer = m.window->GetLayer();
  if (windowLayer) {
    windowLayer->SetTextureRect(device::Eye::Left, m.layerTextureBackup[0]);
    windowLayer->SetTextureRect(device::Eye::Right, m.layerTextureBackup[1]);
    windowLayer->SetWorldSize(m.mWorldWidthBackup, m.mWorlHeightBackup);
    windowLayer->SetUseSameLayerForBothEyes(m.mUseSameLayerForBothEyesBackup);
  }
  if (m.layer && m.layer != windowLayer) {
    DeviceDelegatePtr device = m.deviceWeak.lock();
    device->DeleteLayer(m.layer);
  }
}

void
VRVideo::SetReorientTransform(const vrb::Matrix& transform) {
  m.root->SetTransform(transform);
}

VRVideoPtr
VRVideo::Create(vrb::CreationContextPtr aContext,
                const WidgetPtr& aWindow,
                const VRVideoProjection aProjection,
                const DeviceDelegatePtr& aDevice) {
  VRVideoPtr result = std::make_shared<vrb::ConcreteClass<VRVideo, VRVideo::State> >(aContext);
  result->m.deviceWeak = aDevice;
  result->m.Initialize(aWindow, aProjection);
  return result;
}


VRVideo::VRVideo(State& aState, vrb::CreationContextPtr& aContext) : m(aState) {
  m.context = aContext;
}

} // namespace crow
