/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */


#include "Tray.h"
#include "vrb/ConcreteClass.h"
#include "vrb/Color.h"
#include "vrb/Geometry.h"
#include "vrb/Matrix.h"
#include "vrb/NodeFactoryObj.h"
#include "vrb/ParserObj.h"
#include "vrb/RenderState.h"
#include "vrb/Transform.h"
#include "vrb/Toggle.h"
#include "vrb/VertexArray.h"

using namespace vrb;
static const float kEpsilon = 0.00000001f;

namespace {
  struct TrayIcon;
  typedef std::shared_ptr<TrayIcon> TrayIconPtr;

  struct TrayIcon {
    enum class IconState {
      Normal,
      Hovered,
      Pressed,
    };
    std::string iconName;
    int32_t iconType;
    std::string colliderName;
    vrb::GeometryPtr icon;
    vrb::Vector colliderMin;
    vrb::Vector colliderMax;
    vrb::Vector colliderNormal;
    vrb::Color ambient;
    vrb::Color diffuse;
    vrb::Color specular;
    float specularExponent;
    IconState iconState;

    static TrayIconPtr Create(const std::string& aIconName, int32_t aIconType, const std::string& aColliderName) {
      TrayIconPtr result(new TrayIcon());
      result->iconName = aIconName;
      result->iconType = aIconType;
      result->colliderName = aColliderName;
      return result;
    }

    void SetCollider(const GeometryPtr &aCollider) {
      vrb::VertexArrayPtr vertexArray = aCollider->GetVertexArray();
      const int faceCount = aCollider->GetFaceCount();
      if (vertexArray->GetVertexCount() < 3 || faceCount < 1) {
        VRB_LOG("Tray collider for the '%s' has not enough vertices", iconName.c_str());
        return;
      }
      const vrb::Geometry::Face& face = aCollider->GetFace(0);
      colliderMin = colliderMax = vertexArray->GetVertex(face.vertices[0] - 1);
      for (int i = 1; i < 3; ++i) {
        vrb::Vector vertex = vertexArray->GetVertex(face.vertices[i] - 1);
        colliderMin.x() = fminf(colliderMin.x(), vertex.x());
        colliderMin.y() = fminf(colliderMin.y(), vertex.y());
        colliderMin.z() = fminf(colliderMin.z(), vertex.z());
        colliderMax.x() = fmaxf(colliderMax.x(), vertex.x());
        colliderMax.y() = fmaxf(colliderMax.y(), vertex.y());
        colliderMax.z() = fmaxf(colliderMax.z(), vertex.z());
      }

      vrb::Vector bottomRight(colliderMax.x(), colliderMin.y(), colliderMin.z());
      colliderNormal = -(bottomRight - colliderMin).Cross(colliderMax - colliderMin).Normalize();
    }

    void SetIconGeometry(const GeometryPtr& aGeometry) {
      icon = aGeometry;
      aGeometry->GetRenderState()->GetMaterial(ambient, diffuse, specular, specularExponent);
    }

    void SetIconState(IconState aIconState) {
      if (iconState != aIconState) {
        iconState = aIconState;
        vrb::Color color = ambient;
        if (iconState == IconState::Hovered) {
          color = Color(0xEE513FFF);
        } else if (iconState == IconState::Pressed) {
          color = Color(0xF7CE4DFF);
        }
        vrb::RenderStatePtr renderState = icon->GetRenderState();
        if (renderState) {
          renderState->SetMaterial(color, diffuse, specular, specularExponent);
        }
      }
    }

    bool Intersect(const vrb::Vector& aStartPoint, const vrb::Vector& aDirection, vrb::Vector& aResult, bool& aInside) {
      const float dotNormals = aDirection.Dot(colliderNormal);
      if (dotNormals > -kEpsilon) {
        // Not pointed at the plane
        return false;
      }
      const float dotV = (colliderMin - aStartPoint).Dot(colliderNormal);

      if ((dotV < kEpsilon) && (dotV > -kEpsilon)) {
        return false;
      }

      const float length = dotV / dotNormals;
      vrb::Vector result = aStartPoint + (aDirection * length);

      if ((result.x() >= colliderMin.x()) && (result.y() >= colliderMin.y()) &&(result.z() >= (colliderMin.z() - 0.0000001f)) &&
        (result.x() <= colliderMax.x()) && (result.y() <= colliderMax.y()) &&(result.z() <= (colliderMax.z() + 0.00000001f))) {
        aInside = true;
      }

      aResult = result;
      return true;
    }

  private:
    TrayIcon() {};
    VRB_NO_DEFAULTS(TrayIcon)
  };
}

namespace crow {

  struct Tray::State {
    vrb::CreationContextWeak context;
    vrb::TogglePtr root;
    vrb::TransformPtr transform;
    std::vector<TrayIconPtr> icons;
    TrayIconPtr activeIcon;

    State() {}

    void Initialize() {
      vrb::CreationContextPtr create = context.lock();
      if (!create) {
        return;
      }
      transform = vrb::Transform::Create(create);
      root = vrb::Toggle::Create(create);
      root->AddNode(transform);
    }
  };

  TrayPtr
  Tray::Create(vrb::CreationContextPtr& aContext) {
    TrayPtr result = std::make_shared<vrb::ConcreteClass<Tray, Tray::State> >(aContext);
    result->m.Initialize();
    return result;
  }

  void
  Tray::Load(const vrb::NodeFactoryObjPtr& aFactory, const vrb::ParserObjPtr& aParser) {
    aFactory->SetModelRoot(m.transform);
    aParser->LoadModel("tray.obj");

    m.icons = {
       TrayIcon::Create("Help", IconHelp, "Button1"),
       TrayIcon::Create("Settings", IconSettings, "Button2"),
       TrayIcon::Create("Private", IconPrivate, "Button3"),
       TrayIcon::Create("New", IconNew, "Button4"),
       TrayIcon::Create("Notification", IconNotification, "Button5"),
       TrayIcon::Create("Hide", IconHide, "Button6"),
       TrayIcon::Create("Exit", IconExit, "Button7")
    };

    Node::Traverse(m.transform, [&](const NodePtr& aNode, const GroupPtr& fromParent) {
      std::string nodeName = aNode->GetName();
      for (TrayIconPtr& icon: m.icons) {
        if (nodeName.find(icon->iconName) == 0) {
          icon->SetIconGeometry(std::dynamic_pointer_cast<Geometry>(aNode));
          break;
        }
        else if (nodeName == icon->colliderName) {
          icon->SetCollider(std::dynamic_pointer_cast<Geometry>(aNode));
          break;
        }
      }
      return false;
    });
  }

  bool
  Tray::TestControllerIntersection(const vrb::Vector& aStartPoint, const vrb::Vector& aDirection, vrb::Vector& aHitPoint, bool& aInside, float& aDistance) const {
    aDistance = -1.0f;
    if (!m.root->IsEnabled(*m.transform)) {
      return false;
    }

    vrb::Matrix modelView = m.transform->GetWorldTransform().AfineInverse();
    vrb::Vector point = modelView.MultiplyPosition(aStartPoint);
    vrb::Vector direction = modelView.MultiplyDirection(aDirection);

    m.activeIcon.reset();
    for (const TrayIconPtr& icon: m.icons) {
      aInside = false;
      if (icon->Intersect(point, direction, aHitPoint, aInside)) {
        if (aInside) {
          m.activeIcon = icon;
          break;
        }
      }
    }
    if (m.activeIcon) {
      aDistance = (aHitPoint - point).Magnitude();
    }

    return m.activeIcon.get() != nullptr;
  }

  int32_t
  Tray::ProcessEvents(bool aTrayActive, bool aPressed) {
    int32_t result = -1;

    for (const TrayIconPtr& icon: m.icons) {
      if (aTrayActive && icon == m.activeIcon) {
        if (icon->iconState == TrayIcon::IconState::Pressed && !aPressed) {
          // Click event
          result = icon->iconType;
        }
        icon->SetIconState(aPressed ? TrayIcon::IconState::Pressed : TrayIcon::IconState::Hovered);
      } else {
        icon->SetIconState(TrayIcon::IconState::Normal);
      }
    }

    return result;
  }


  const vrb::Matrix&
  Tray::GetTransform() const {
    return m.transform->GetTransform();
  }

  void
  Tray::SetTransform(const vrb::Matrix& aTransform) {
    m.transform->SetTransform(aTransform);
  }

  void
  Tray::Toggle(const bool aEnabled) {
    m.root->ToggleAll(aEnabled);
  }

  vrb::NodePtr
  Tray::GetRoot() const {
    return m.root;
  }

  Tray::Tray(State& aState, vrb::CreationContextPtr& aContext) : m(aState) {
    m.context = aContext;
  }

  Tray::~Tray() {}

} // namespace crow