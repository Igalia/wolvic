/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_CYLINDER_DOT_H
#define VRBROWSER_CYLINDER_DOT_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"
#include "Device.h"

#include <memory>
#include <string>
#include <vector>
#include <functional>

namespace crow {


class VRLayerCylinder;
typedef std::shared_ptr<VRLayerCylinder> VRLayerCylinderPtr;

class Cylinder;
typedef std::shared_ptr<Cylinder> CylinderPtr;

class Cylinder {
public:
  static CylinderPtr Create(vrb::CreationContextPtr aContext, const float aRadius, const float aHeight, const VRLayerCylinderPtr& aLayer = nullptr);
  static CylinderPtr Create(vrb::CreationContextPtr aContext, const float aRadius, const float aHeight, const vrb::Color& aSolidColor, const float kBorder, const vrb::Color& aBorderColor);
  static CylinderPtr Create(vrb::CreationContextPtr aContext, const VRLayerCylinderPtr& aLayer = nullptr);
  static CylinderPtr Create(vrb::CreationContextPtr aContext, const Cylinder& aCylinder);
  static float kWorldDensityRatio;
  void UpdateProgram(const std::string& aCustomFragmentShader);
  void GetTextureSize(int32_t& aWidth, int32_t& aHeight) const;
  void SetTextureSize(int32_t aWidth, int32_t aHeight);
  void RecreateSurface();
  void SetTexture(const vrb::TexturePtr& aTexture, int32_t aWidth, int32_t aHeight);
  void SetTextureScale(const float aScaleX, const float aScaleY);
  void SetMaterial(const vrb::Color& aAmbient, const vrb::Color& aDiffuse, const vrb::Color& aSpecular, const float aSpecularExponent);
  void SetLightsEnabled(const bool aEnabled);
  float GetCylinderRadius() const;
  float GetCylinderHeight() const;
  float GetCylinderTheta() const;
  vrb::RenderStatePtr GetRenderState() const;
  void SetCylinderTheta(const float aAngleLength);
  void SetTintColor(const vrb::Color& aColor);
  vrb::NodePtr GetRoot() const;
  VRLayerCylinderPtr GetLayer() const;
  vrb::TransformPtr GetTransformNode() const;
  void SetTransform(const vrb::Matrix& aTransform);
  bool TestIntersection(const vrb::Vector& aStartPoint, const vrb::Vector& aDirection, const vrb::Vector& aScale, vrb::Vector& aResult, vrb::Vector& aNormal, bool aClamp, bool& aIsInside, float& aDistance) const;
  void ConvertToQuadCoordinates(const vrb::Vector& point, float& aX, float& aY, bool aClamp) const;
  void ConvertFromQuadCoordinates(const float aX, const float aY, vrb::Vector& aWorldPoint, vrb::Vector& aNormal);
  float DistanceToBackPlane(const vrb::Vector& aStartPoint, const vrb::Vector& aDirection) const;
  float GetCylinderAngle(const vrb::Vector& aLocalPoint) const;
  vrb::Vector ProjectPointToQuad(const vrb::Vector& aWorldPoint, const float aAnchorX, const float aDensity, const vrb::Vector& aMin, const vrb::Vector& aMax) const;
protected:
  struct State;
  Cylinder(State& aState, vrb::CreationContextPtr& aContext);
  ~Cylinder();
private:
  State& m;
  Cylinder() = delete;
  VRB_NO_DEFAULTS(Cylinder)
};

} // namespace crow

#endif // VRBROWSER_CYLINDER_DOT_H
