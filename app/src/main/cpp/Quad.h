/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRBROWSER_QUAD_DOT_H
#define VRBROWSER_QUAD_DOT_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"
#include "Device.h"

#include <memory>
#include <string>
#include <vector>
#include <functional>

namespace crow {


class VRLayerQuad;
typedef std::shared_ptr<VRLayerQuad> VRLayerQuadPtr;

class Quad;
typedef std::shared_ptr<Quad> QuadPtr;

class Quad {
public:
  enum class ScaleMode {
    Fill,
    AspectFit,
    AspectFill,
  };
  static QuadPtr Create(vrb::CreationContextPtr aContext, const vrb::Vector& aMin, const vrb::Vector& aMax, const VRLayerQuadPtr& aLayer = nullptr);
  static QuadPtr Create(vrb::CreationContextPtr aContext, const float aWorldWidth, const float aWorldHeight, const VRLayerQuadPtr& aLayer = nullptr);
  static QuadPtr Create(vrb::CreationContextPtr aContext, const Quad& aQuad);
  static vrb::GeometryPtr CreateGeometry(vrb::CreationContextPtr aContext, const vrb::Vector& aMin, const vrb::Vector& aMax);
  static vrb::GeometryPtr CreateGeometry(vrb::CreationContextPtr aContext, const float aWorldWidth, const float aWorldHeight);
  static vrb::GeometryPtr CreateGeometry(vrb::CreationContextPtr aContext, const vrb::Vector& aMin, const vrb::Vector& aMax, const device::EyeRect& aRect);
  void UpdateProgram(const std::string& aCustomFragmentShader);
  void SetTexture(const vrb::TexturePtr& aTexture, int32_t aWidth, int32_t aHeight);
  void SetMaterial(const vrb::Color& aAmbient, const vrb::Color& aDiffuse, const vrb::Color& aSpecular, const float aSpecularExponent);
  void SetScaleMode(ScaleMode aScaleMode);
  void SetBackgroundColor(const vrb::Color& aColor);
  void GetTextureSize(int32_t& aWidth, int32_t& aHeight) const;
  void SetTextureSize(int32_t aWidth, int32_t aHeight);
  void RecreateSurface();
  void GetWorldMinAndMax(vrb::Vector& aMin, vrb::Vector& aMax) const;
  const vrb::Vector& GetWorldMin() const;
  const vrb::Vector& GetWorldMax() const;
  float GetWorldWidth() const;
  float GetWorldHeight() const;
  vrb::RenderStatePtr GetRenderState() const;
  void GetWorldSize(float& aWidth, float& aHeight) const;
  void SetWorldSize(const float aWidth, const float aHeight) const;
  void SetWorldSize(const vrb::Vector& aMin, const vrb::Vector& aMax) const;
  void SetTintColor(const vrb::Color& aColor);
  vrb::Vector GetNormal() const;
  vrb::NodePtr GetRoot() const;
  vrb::TransformPtr GetTransformNode() const;
  VRLayerQuadPtr GetLayer() const;
  bool TestIntersection(const vrb::Vector& aStartPoint, const vrb::Vector& aDirection, const vrb::Vector& aScale, vrb::Vector& aResult, vrb::Vector& aNormal, bool aClamp, bool& aIsInside, float& aDistance) const;
  void ConvertToQuadCoordinates(const vrb::Vector& point, float& aX, float& aY, bool aClamp) const;
protected:
  struct State;
  Quad(State& aState, vrb::CreationContextPtr& aContext);
  ~Quad() = default;
private:
  State& m;
  Quad() = delete;
  VRB_NO_DEFAULTS(Quad)
};

} // namespace crow

#endif // VRBROWSER_QUAD_DOT_H
