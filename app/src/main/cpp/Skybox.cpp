/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "Skybox.h"
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
#include "vrb/TextureCubeMap.h"
#include "vrb/Toggle.h"
#include "vrb/Transform.h"
#include "vrb/VertexArray.h"

#include <array>
#include <list>
#include <sys/stat.h>

using namespace vrb;

namespace crow {

static const std::string sPosx = "posx";
static const std::string sNegx = "negx";
static const std::string sPosy = "posy";
static const std::string sNegy = "negy";
static const std::string sPosz = "posz";
static const std::string sNegz = "negz";
static const std::list<std::string> sBaseNameList = std::list<std::string>({
    sPosx, sNegx, sPosy, sNegy, sPosz, sNegz
});
static const std::list<std::string> sFileExt = std::list<std::string>({
    ".ktx", ".jpg", ".png"
});

static TextureCubeMapPtr LoadTextureCube(vrb::CreationContextPtr& aContext, const std::string& aBasePath,
                                         const std::string& aExtension, bool srgb, GLuint targetTexture = 0) {
  TextureCubeMapPtr cubemap = vrb::TextureCubeMap::Create(aContext, targetTexture);
  cubemap->SetTextureParameter(GL_TEXTURE_MAG_FILTER, GL_LINEAR);
  cubemap->SetTextureParameter(GL_TEXTURE_MIN_FILTER, GL_LINEAR);
  cubemap->SetTextureParameter(GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
  cubemap->SetTextureParameter(GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
  cubemap->SetTextureParameter(GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);

  auto path = [&](const std::string &name) {
    return aBasePath + "/" + name + (srgb ? "_srgb" : "") + aExtension;
  };

  vrb::TextureCubeMap::Load(aContext, cubemap, path(sPosx), path(sNegx), path(sPosy),
                            path(sNegy), path(sPosz), path(sNegz));
  return cubemap;
}

struct Skybox::State {
  vrb::CreationContextWeak context;
  vrb::TogglePtr root;
  VRLayerCubePtr layer;
  GLuint layerTextureHandle;
  vrb::TransformPtr transform;
  vrb::GeometryPtr geometry;
  vrb::ModelLoaderAndroidPtr loader;
  std::string basePath;
  std::string extension;
  TextureCubeMapPtr texture;
  vrb::Color tintColor;
  State():
      layerTextureHandle(0),
      tintColor(1.0f, 1.0f, 1.0f, 1.0f)
  {}

  void Initialize() {
    vrb::CreationContextPtr create = context.lock();
    root = vrb::Toggle::Create(create);
    transform = vrb::Transform::Create(create);
    if (layer) {
      root->AddNode(VRLayerNode::Create(create, layer));
      layer->SetSurfaceChangedDelegate([=](const VRLayer& aLayer, VRLayer::SurfaceChange aChange, const std::function<void()>& aCallback) {
        this->layerTextureHandle = layer->GetTextureHandle();
        LoadLayer();
        if (aCallback) {
          aCallback();
        }
      });
    } else {
      root->AddNode(transform);
    }
  }

  void LoadGeometry() {
    VRB_LOG("Skybox::LoadGeometry() - %s", basePath.c_str());
    LoadTask task = [=](CreationContextPtr &aContext) -> GroupPtr {
      std::array<GLfloat, 24> cubeVertices{
          -1.0f, 1.0f, 1.0f, // 0
          -1.0f, -1.0f, 1.0f, // 1
          1.0f, -1.0f, 1.0f, // 2
          1.0f, 1.0f, 1.0f, // 3
          -1.0f, 1.0f, -1.0f, // 4
          -1.0f, -1.0f, -1.0f, // 5
          1.0f, -1.0f, -1.0f, // 6
          1.0f, 1.0f, -1.0f, // 7
      };

      std::array<GLushort, 24> cubeIndices{
          0, 1, 2, 3,
          3, 2, 6, 7,
          7, 6, 5, 4,
          4, 5, 1, 0,
          0, 3, 7, 4,
          1, 5, 6, 2
      };

      VertexArrayPtr array = VertexArray::Create(aContext);
      array->SetUVLength(3);
      const float kLength = 140.0f;
      for (int i = 0; i < cubeVertices.size(); i += 3) {
        array->AppendVertex(Vector(-kLength * cubeVertices[i], -kLength * cubeVertices[i + 1],
                                   -kLength * cubeVertices[i + 2]));
        array->AppendUV(Vector(-kLength * cubeVertices[i], -kLength * cubeVertices[i + 1],
                               -kLength * cubeVertices[i + 2]));
      }

      geometry = vrb::Geometry::Create(aContext);
      geometry->SetVertexArray(array);

      for (int i = 0; i < cubeIndices.size(); i += 4) {
        std::vector<int> indices = {cubeIndices[i] + 1, cubeIndices[i + 1] + 1,
                                    cubeIndices[i + 2] + 1, cubeIndices[i + 3] + 1};
        geometry->AddFace(indices, indices, {});
      }
      ProgramPtr program = aContext->GetProgramFactory()->CreateProgram(aContext, FeatureCubeTexture);
      RenderStatePtr state = RenderState::Create(aContext);
      state->SetProgram(program);
      geometry->SetRenderState(state);

      bool srgb = false;
      texture = LoadTextureCube(aContext, basePath, extension, srgb);
      state->SetTexture(texture);
      state->SetMaterial(Color(1.0f, 1.0f, 1.0f), Color(1.0f, 1.0f, 1.0f), Color(0.0f, 0.0f, 0.0f),
                         0.0f);
      geometry->SetRenderState(state);
      vrb::GroupPtr group = vrb::Transform::Create(aContext);
      group->AddNode(geometry);
      return group;
    };

    vrb::GeometryPtr oldGeometry = geometry;
    LoadFinishedCallback loadedCallback = [=](GroupPtr&) {
      if (geometry) {
        geometry->GetRenderState()->SetTintColor(tintColor);
      }
      if (oldGeometry) {
        oldGeometry->RemoveFromParents();
      }
    };

    loader->RunLoadTask(transform, task, loadedCallback);
  }

  void LoadLayer() {
    VRB_LOG("Skybox::LoadLayer() - %s %d", basePath.c_str(), layerTextureHandle);
    if (basePath.empty() || layerTextureHandle == 0) {
      return;
    }
    vrb::CreationContextPtr create = context.lock();
    bool srgb = layer->GetFormat() == GL_SRGB8_ALPHA8 || layer->GetFormat() == GL_COMPRESSED_SRGB8_ETC2;
    texture = LoadTextureCube(create, basePath, extension, srgb, layerTextureHandle);
    texture->Bind();
    layer->SetLoaded(true);
  }
};

void
Skybox::Load(const vrb::ModelLoaderAndroidPtr& aLoader, const std::string& aBasePath, const std::string& aExtension) {
  VRB_LOG("Skybox::Load() - %s (current) %s (new)", m.basePath.c_str(), aBasePath.c_str());
  if (m.basePath == aBasePath) {
    return;
  }
  m.loader = aLoader;
  m.basePath = aBasePath;
  m.extension = aExtension;
  if (m.layer) {
    m.LoadLayer();
  } else {
    m.LoadGeometry();
  }
}

VRLayerCubePtr
Skybox::GetLayer() const {
  return m.layer;
}

void
Skybox::SetLayer(const VRLayerCubePtr& aLayer) {
  m.basePath = "";
  m.layerTextureHandle = 0;
  if (m.root->GetNodeCount() > 0) {
    vrb::NodePtr layerNode = m.root->GetNode(0);
    m.root->RemoveNode(*layerNode);
  }
  m.layer = aLayer;
  m.layer->SetTintColor(m.tintColor);
  vrb::CreationContextPtr create = m.context.lock();
  m.root->AddNode(VRLayerNode::Create(create, m.layer));
  m.layer->SetSurfaceChangedDelegate([=](const VRLayer& aLayer, VRLayer::SurfaceChange aChange, const std::function<void()>& aCallback) {
    m.layerTextureHandle = m.layer->GetTextureHandle();
    m.LoadLayer();
    if (aCallback) {
      aCallback();
    }
  });
}

void
Skybox::SetVisible(bool aVisible) {
  m.root->ToggleAll(aVisible);
}

void
Skybox::SetTransform(const vrb::Matrix& aTransform) {
  if (m.transform) {
    m.transform->SetTransform(aTransform);
  }
}

void
Skybox::SetTintColor(const vrb::Color &aTintColor) {
  m.tintColor = aTintColor;
  if (m.layer) {
    m.layer->SetTintColor(aTintColor);
  } else if (m.geometry) {
    m.geometry->GetRenderState()->SetTintColor(aTintColor);
  }

}

vrb::NodePtr
Skybox::GetRoot() const {
  return m.root;
}

static bool
FileDoesNotExist (const std::string& aName) {
  struct stat buffer;
  return (stat(aName.c_str(), &buffer) != 0);
}

std::string
Skybox::ValidateCustomSkyboxAndFindFileExtension(const std::string& aBasePath) {
#if defined(PICOXR) || defined(OCULUSVR)
  const std::string& colorSpace = "_srgb";
#else
  const std::string& colorSpace = "";
#endif
  auto path = [&](const std::string& name, const std::string& extension) {
      return aBasePath + "/" + name + colorSpace + extension;
  };
  for (const std::string& ext: sFileExt) {
     int32_t fileCount = 0;
     for (const std::string& baseName: sBaseNameList) {
       const std::string file = path(baseName, ext);
       if (FileDoesNotExist(file)) {
         if (fileCount > 0) {
           VRB_ERROR("Custom skybox file missing: %s", file.c_str());
         }
         break;
       }
       fileCount++;
     }
     if (fileCount == sBaseNameList.size()) {
       return ext;
     }
  }

  return std::string();
}

SkyboxPtr
Skybox::Create(vrb::CreationContextPtr aContext, const VRLayerCubePtr& aLayer) {
  VRB_LOG("Skybox::Create()");
  if (aLayer == nullptr) {
    VRB_ERROR("Skybox::Create() - Layer is null");
  }
  SkyboxPtr result = std::make_shared<vrb::ConcreteClass<Skybox, Skybox::State> >(aContext);
  result->m.layer = aLayer;
  result->m.Initialize();
  return result;
}


Skybox::Skybox(State& aState, vrb::CreationContextPtr& aContext) : m(aState) {
  m.context = aContext;
}

} // namespace crow
