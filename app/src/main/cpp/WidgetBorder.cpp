/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "WidgetBorder.h"
#include "WidgetPlacement.h"
#include "Widget.h"
#include "Cylinder.h"
#include "Quad.h"
#include "vrb/ConcreteClass.h"

#include "vrb/Color.h"
#include "vrb/CreationContext.h"
#include "vrb/Matrix.h"
#include "vrb/Geometry.h"
#include "vrb/RenderState.h"
#include "vrb/SurfaceTextureFactory.h"
#include "vrb/TextureGL.h"
#include "vrb/TextureSurface.h"
#include "vrb/Toggle.h"
#include "vrb/Transform.h"
#include "vrb/Vector.h"
#include "vrb/VertexArray.h"
#include "WidgetBorder.h"
#include <vector>
#include <vrb/include/vrb/ProgramFactory.h>

namespace crow {

struct WidgetBorder::State {
  CylinderPtr cylinder;
  vrb::GeometryPtr geometry;
  vrb::TransformPtr transform;

  template<typename T>
  void UpdateMaterial(const T &aTarget, const vrb::Color &aDiffuse) {
    vrb::Color ambient(0.5f, 0.5f, 0.5f, 1.0f);
    aTarget->SetMaterial(ambient, aDiffuse, vrb::Color(0.0f, 0.0f, 0.0f), 0.0f);
  }

  void AppendBorder(const vrb::GeometryPtr &geometry, GLint index1, GLint index2,
                    const vrb::Vector &offset, const vrb::Color &aColor, GLint &currentIndex) {
    vrb::VertexArrayPtr array = geometry->GetVertexArray();
    vrb::Vector offset1 = array->GetVertex(index1 - 1) + offset;
    vrb::Vector offset2 = array->GetVertex(index2 - 1) + offset;
    array->AppendVertex(offset1);
    array->AppendVertex(offset2);
    array->AppendColor(aColor);
    array->AppendColor(aColor);

    GLint index3 = currentIndex++;
    GLint index4 = currentIndex++;
    std::vector<int> index;
    if (offset.y() > 0.0f) {
      index = {index1, index2, index4, index3};
    } else if (offset.y() < 0.0f) {
      index = {index3, index4, index2, index1};
    } else if (offset.x() > 0.0f) {
      index = {index1, index3, index4, index2};
    } else {
      index = {index3, index1, index2, index4};
    }

    std::vector<int> normalIndex;
    normalIndex.push_back(1);
    normalIndex.push_back(1);
    normalIndex.push_back(1);
    normalIndex.push_back(1);
    geometry->AddFace(index, index, normalIndex);
  }

  vrb::GeometryPtr
  CreateGeometry(vrb::CreationContextPtr aContext, const vrb::Vector &aMin, const vrb::Vector &aMax,
                 const device::EyeRect &aBorder) {
    vrb::VertexArrayPtr array = vrb::VertexArray::Create(aContext);
    vrb::Color solid(1.0f, 1.0f, 1.0f, 1.0f);
    vrb::Color border(1.0f, 1.0f, 1.0f, 0.0f);

    const vrb::Vector bottomRight(aMax.x(), aMin.y(), aMin.z());
    array->AppendVertex(aMin); // Bottom left
    array->AppendVertex(bottomRight); // Bottom right
    array->AppendVertex(aMax); // Top right
    array->AppendVertex(vrb::Vector(aMin.x(), aMax.y(), aMax.z())); // Top left
    for (int i = 0; i < 4; ++i) {
      array->AppendColor(solid);
    }

    vrb::Vector normal = (bottomRight - aMin).Cross(aMax - aMin).Normalize();
    array->AppendNormal(normal);

    vrb::RenderStatePtr state = vrb::RenderState::Create(aContext);
    vrb::GeometryPtr geometry = vrb::Geometry::Create(aContext);
    geometry->SetVertexArray(array);
    geometry->SetRenderState(state);

    std::vector<int> index;
    index.push_back(1);
    index.push_back(2);
    index.push_back(3);
    index.push_back(4);
    std::vector<int> normalIndex;
    normalIndex.push_back(1);
    normalIndex.push_back(1);
    normalIndex.push_back(1);
    normalIndex.push_back(1);

    geometry->AddFace(index, index, normalIndex);

    GLint currentIndex = 5;

    if (aBorder.mX > 0.0f) {
      AppendBorder(geometry, 1, 4, vrb::Vector(-aBorder.mX, 0.0f, 0.0f), border, currentIndex);
    }
    if (aBorder.mWidth > 0.0f) {
      AppendBorder(geometry, 2, 3, vrb::Vector(aBorder.mWidth, 0.0f, 0.0f), border, currentIndex);
    }
    if (aBorder.mY > 0.0f) {
      AppendBorder(geometry, 1, 2, vrb::Vector(0.0f, -aBorder.mY, 0.0f), border, currentIndex);
    }
    if (aBorder.mHeight > 0.0f) {
      AppendBorder(geometry, 4, 3, vrb::Vector(0.0f, aBorder.mHeight, 0.0f), border, currentIndex);
    }

    return geometry;
  }
}; // struct WidgetBorder::State

WidgetBorderPtr WidgetBorder::Create(vrb::CreationContextPtr& aContext, const vrb::Vector& aBarSize,
                                     const float aBorderSize, const device::EyeRect& aBorderRect,
                                     const WidgetBorder::Mode aMode) {
  auto result = std::make_shared<vrb::ConcreteClass<WidgetBorder, WidgetBorder::State>>(aContext);
  vrb::Vector max(aBarSize.x() * 0.5f, aBarSize.y() * 0.5f, 0.0f);
  result->m.transform = vrb::Transform::Create(aContext);
  if (aMode == WidgetBorder::Mode::Cylinder) {
    result->m.cylinder = Cylinder::Create(aContext, 1.0f, aBarSize.y(), vrb::Color(1.0f, 1.0f, 1.0f, 1.0f), aBorderSize, vrb::Color(1.0f, 1.0f, 1.0f, 0.0f));
    result->m.cylinder->SetLightsEnabled(false);
    // Fix sticking out borders
    // Sometimes there is no handle to hide it (e.g. bottom bar and anchor points != 0.5f)
    vrb::TextureGLPtr defaultTexture = aContext->GetDefaultTexture();
    result->m.cylinder->SetTexture(defaultTexture, defaultTexture->GetWidth(), defaultTexture->GetHeight());
    const std::string customFragment =
#include "shaders/clear_color.fs"
    ;
    result->m.cylinder->UpdateProgram(customFragment);
    result->m.transform->AddNode(result->m.cylinder->GetRoot());
  } else {
    result->m.geometry = result->m.CreateGeometry(aContext, -max, max, aBorderRect);
    result->m.geometry->GetRenderState()->SetLightsEnabled(false);
    vrb::ProgramPtr program = aContext->GetProgramFactory()->CreateProgram(aContext, vrb::FeatureVertexColor);
    result->m.geometry->GetRenderState()->SetProgram(program);
    result->m.transform->AddNode(result->m.geometry);
  }

  return result;
}

void
WidgetBorder::SetColor(const vrb::Color& aColor) {
  if (m.cylinder) {
    m.UpdateMaterial(m.cylinder, aColor);
  } else {
    m.UpdateMaterial(m.geometry->GetRenderState(), aColor);
  }
}

const vrb::TransformPtr&
WidgetBorder::GetTransformNode() const {
  return m.transform;
}

const CylinderPtr&
WidgetBorder::GetCylinder() const {
  return m.cylinder;
}

std::vector<WidgetBorderPtr>
WidgetBorder::CreateFrame(vrb::CreationContextPtr &aContext, const Widget& aTarget, const float aFrameSize, const float aBorderSize) {

  device::EyeRect horizontalBorder(0.0f, aBorderSize, 0.0f, aBorderSize);
  device::EyeRect verticalBorder(aBorderSize, 0.0f, aBorderSize, 0.0f);

  float w;
  float h;
  aTarget.GetWorldSize(w, h);
  WidgetBorder::Mode mode = aTarget.GetCylinder() ? WidgetBorder::Mode::Cylinder : WidgetBorder::Mode::Quad;

  WidgetBorderPtr left = WidgetBorder::Create(aContext, vrb::Vector(aFrameSize, h + aFrameSize, 0.0f), aBorderSize, verticalBorder, WidgetBorder::Mode::Quad);
  WidgetBorderPtr right = WidgetBorder::Create(aContext, vrb::Vector(aFrameSize, h + aFrameSize , 0.0f), aBorderSize, verticalBorder, WidgetBorder::Mode::Quad);
  WidgetBorderPtr top = WidgetBorder::Create(aContext, vrb::Vector(w - aFrameSize, aFrameSize, 0.0f), aBorderSize, horizontalBorder, mode);
  WidgetBorderPtr bottom = WidgetBorder::Create(aContext, vrb::Vector(w - aFrameSize, aFrameSize, 0.0f), aBorderSize, horizontalBorder, mode);

  if (mode == WidgetBorder::Mode::Quad) {
    left->m.transform->SetTransform(vrb::Matrix::Translation(vrb::Vector(-w * 0.5f, 0.0f, 0.0f)));
    right->m.transform->SetTransform(vrb::Matrix::Translation(vrb::Vector(w * 0.5f, 0.0f, 0.0f)));
    top->m.transform->SetTransform(vrb::Matrix::Translation(vrb::Vector(0.0f, h * 0.5f, 0.0f)));
    bottom->m.transform->SetTransform(vrb::Matrix::Translation(vrb::Vector(0.0f, -h * 0.5f, 0.0f)));
  } else {
    const float theta = aTarget.GetCylinder()->GetCylinderTheta();
    const float radius = aTarget.GetCylinder()->GetTransformNode()->GetTransform().GetScale().x();
    // Place top and bottom cylinders
    top->m.cylinder->SetCylinderTheta(theta);
    bottom->m.cylinder->SetCylinderTheta(theta);
    vrb::Matrix cylinderScale = vrb::Matrix::Identity();
    cylinderScale.ScaleInPlace(vrb::Vector(radius, 1.0f, radius));
    top->m.transform->SetTransform(vrb::Matrix::Translation(vrb::Vector(0.0f, h * 0.5f, radius)).PostMultiply(cylinderScale));
    bottom->m.transform->SetTransform(vrb::Matrix::Translation(vrb::Vector(0.0f, -h * 0.5f, radius)).PostMultiply(cylinderScale));
    // Place left and right borders on the cylinder sides
    auto placeBorder = [=](WidgetBorderPtr& aBorder, float aAngle) {
      vrb::Matrix rotation = vrb::Matrix::Rotation(vrb::Vector(-cosf(aAngle), 0.0f, sinf(aAngle)));
      vrb::Matrix translation = vrb::Matrix::Position(vrb::Vector(radius * cosf(aAngle), 0.0f,
                                                                  radius - radius * sinf(aAngle)));
      aBorder->m.transform->SetTransform(translation.PostMultiply(rotation));
    };

    placeBorder(left, (float)M_PI * 0.5f + theta * 0.5f);
    placeBorder(right, (float)M_PI * 0.5f - theta * 0.5f);
  }


  return {left, right, top, bottom};
}

WidgetBorder::WidgetBorder(State& aState, vrb::CreationContextPtr& aContext) : m(aState) {
}

} // namespace crow
