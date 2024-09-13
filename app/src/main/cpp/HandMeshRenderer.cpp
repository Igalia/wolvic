/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "DeviceUtils.h"
#include "HandMeshRenderer.h"
#include "tiny_gltf.h"

#include "vrb/Camera.h"
#include "vrb/Color.h"
#include "vrb/ConcreteClass.h"
#include "vrb/GeometryDrawable.h"
#include "vrb/GLError.h"
#include "vrb/Matrix.h"
#include "vrb/ProgramFactory.h"
#include "vrb/Quaternion.h"
#include "vrb/RenderBuffer.h"
#include "vrb/RenderState.h"
#include "vrb/ShaderUtil.h"
#include "vrb/Toggle.h"
#include "vrb/Transform.h"
#include "vrb/gl.h"

#define XR_EXT_HAND_TRACKING_NUM_JOINTS 26

namespace crow {

vrb::RenderStatePtr GetHandMeshDefaultRenderState(vrb::CreationContextPtr create) {
    auto program = create->GetProgramFactory()->CreateProgram(create, 0);
    auto state = vrb::RenderState::Create(create);
    state->SetProgram(program);
    state->SetMaterial(vrb::Color(0.5f, 0.5f, 0.5f),
                       vrb::Color(0.5f, 0.5f, 0.5f),
                       vrb::Color(0.5f, 0.5f, 0.5f), 0.75f);
    state->SetLightsEnabled(true);

    return state;
}

// HandMeshRendererGeometry

struct HandMeshGeometry {
    vrb::TogglePtr toggle;
    vrb::GeometryDrawablePtr geometry;
};

struct HandMeshRendererGeometry::State {
    std::vector<HandMeshGeometry> handMeshState;
};

HandMeshRendererPtr HandMeshRendererGeometry::Create(vrb::CreationContextPtr& aContext) {
    return std::make_unique<vrb::ConcreteClass<HandMeshRendererGeometry, HandMeshRendererGeometry::State> >(aContext);
}

HandMeshRendererGeometry::HandMeshRendererGeometry(State& aState, vrb::CreationContextPtr& aContext)
        : m(aState) {
    context = aContext;
}

void HandMeshRendererGeometry::Initialize(HandMeshGeometry& handMesh, const vrb::GroupPtr& aRoot) {
    vrb::CreationContextPtr create = context.lock();
    handMesh.toggle = vrb::Toggle::Create(create);
    aRoot->AddNode(handMesh.toggle);

    handMesh.geometry = vrb::GeometryDrawable::Create(create);
    handMesh.toggle->AddNode(handMesh.geometry);

    auto renderBuffer = vrb::RenderBuffer::Create(create);
    renderBuffer->DefinePosition(offsetof(HandMeshVertexMSFT, position), 3);
    renderBuffer->DefineNormal(offsetof(HandMeshVertexMSFT, normal), 3);
    handMesh.geometry->SetRenderBuffer(renderBuffer);

    auto state = GetHandMeshDefaultRenderState(create);
    handMesh.geometry->SetRenderState(state);
}

void HandMeshRendererGeometry::Update(const uint32_t aControllerIndex, const std::vector<vrb::Matrix>& handJointTransforms,
                                      const vrb::GroupPtr& aRoot, HandMeshBufferPtr& aBuffer, const bool aEnabled, const bool leftHanded) {
    if (aControllerIndex >= m.handMeshState.size())
        m.handMeshState.resize(aControllerIndex + 1);
    auto& handMesh = m.handMeshState.at(aControllerIndex);

    // We need to call ToggleAll() even if aEnabled is false, to be able to hide the
    // hand mesh.
    if (handMesh.toggle)
        handMesh.toggle->ToggleAll(aEnabled);

    if (!aEnabled)
        return;

    // Lazily initialize toggle, transform and geometry nodes, and render buffers
    if (!handMesh.toggle)
        Initialize(handMesh, aRoot);

    if (!aBuffer)
        return;

    assert(handMesh.geometry);
    auto& renderBuffer = handMesh.geometry->GetRenderBuffer();

    HandMeshBufferMSFTPtr buffer = std::dynamic_pointer_cast<HandMeshBufferMSFT>(aBuffer);

    // @FIXME: VRB's GeometryDrawable assumes unsigned shorts (16 bits) for the indices, but we got
    //         32 bits indices from XR_MSFT_hand_tracking_mesh; so we need to manually convert them.
    std::vector<uint16_t> indices16;
    indices16.resize(buffer->indices.size());
    for (uint32_t i = 0; i < buffer->indices.size(); i++)
        indices16[i] = (uint16_t) buffer->indices[i];

    // Upload the new geometry to the render buffers
    renderBuffer->SetIndexObject(buffer->ibo, buffer->indices.size());
    renderBuffer->SetVertexObject(buffer->vbo, buffer->vertices.size());
    renderBuffer->Bind();
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, buffer->indices.size() * sizeof(uint16_t),
                 indices16.data(), GL_STATIC_DRAW);
    glBufferData(GL_ARRAY_BUFFER, buffer->vertices.size() * sizeof(HandMeshVertexMSFT),
                 buffer->vertices.data(), GL_STATIC_DRAW);
    renderBuffer->Unbind();
}


// HandMeshRendererSkinned

namespace {
const char* sVertexShader = R"SHADER(
precision highp float;

#define MAX_JOINTS 26

uniform mat4 u_perspective;
uniform mat4 u_view;
uniform mat4 u_model;
uniform mat4 u_jointMatrices[MAX_JOINTS];

attribute vec3 a_position;
attribute vec3 a_normal;
attribute vec4 a_jointIndices;
attribute vec4 a_jointWeights;

varying vec4 v_color;

struct Light {
  vec3 direction;
  vec4 ambient;
  vec4 diffuse;
  vec4 specular;
};

struct Material {
  vec4 ambient;
  vec4 diffuse;
  vec4 specular;
  float specularExponent;
};

const Light u_lights = Light(
  vec3(1.0, -1.0, 0.0),
  vec4(0.25, 0.25, 0.25, 1.0),
  vec4(0.25, 0.25, 0.25, 1.0),
  vec4(0.4, 0.4, 0.4, 1.0)
);

const Material u_material = Material(
  vec4(0.5, 0.5, 0.5, 1.0),
  vec4(0.5, 0.5, 0.5, 1.0),
  vec4(0.5, 0.5, 0.5, 1.0),
  0.75
);

vec4 calculate_light(vec4 norm, Light light, Material material) {
  vec4 result = vec4(0.0, 0.0, 0.0, 0.0);
  vec4 direction = -normalize(u_view * vec4(light.direction.xyz, 0.0));
  vec4 hvec;
  float ndotl;
  float ndoth;
  result += light.ambient * material.ambient;
  ndotl = max(0.0, dot(norm, direction));
  result += (ndotl * light.diffuse * material.diffuse);
  hvec = normalize(direction + vec4(0.0, 0.0, 1.0, 0.0));
  ndoth = dot(norm, hvec);
  if (ndoth > 0.0) {
    result += (pow(ndoth, material.specularExponent) * material.specular * light.specular);
  }
  return result;
}

void main(void) {
  vec4 pos = vec4(a_position, 1.0);
  vec4 localPos1 = u_jointMatrices[int(a_jointIndices.x)] * pos;
  vec4 localPos2 = u_jointMatrices[int(a_jointIndices.y)] * pos;
  vec4 localPos3 = u_jointMatrices[int(a_jointIndices.z)] * pos;
  vec4 localPos4 = u_jointMatrices[int(a_jointIndices.w)] * pos;
  vec4 localPos = localPos1 * a_jointWeights.x
                + localPos2 * a_jointWeights.y
                + localPos3 * a_jointWeights.z
                + localPos4 * a_jointWeights.w;
  gl_Position = u_perspective * u_view * u_model * localPos;

  vec4 normal = vec4(a_normal, 0.0);
  vec4 localNorm1 = u_jointMatrices[int(a_jointIndices.x)] * normal;
  vec4 localNorm2 = u_jointMatrices[int(a_jointIndices.y)] * normal;
  vec4 localNorm3 = u_jointMatrices[int(a_jointIndices.z)] * normal;
  vec4 localNorm4 = u_jointMatrices[int(a_jointIndices.w)] * normal;
  vec4 localNorm = localNorm1 * a_jointWeights.x
                 + localNorm2 * a_jointWeights.y
                 + localNorm3 * a_jointWeights.z
                 + localNorm4 * a_jointWeights.w;
  normal = normalize(u_model * localNorm);

  v_color = calculate_light(normal, u_lights, u_material);
}
)SHADER";

        const char* sFragmentShader = R"SHADER(
precision mediump float;

varying vec4 v_color;

void main() {
  gl_FragColor = vec4(v_color.xyz, 1.0);
}
)SHADER";

}

struct Vector4s1 {
    uint16_t x;
    uint16_t y;
    uint16_t z;
    uint16_t w;
};

struct HandMeshSkinned {
    uint32_t jointCount;
    std::vector<vrb::Matrix> jointTransforms;

    uint32_t vertexCount;
    std::vector<vrb::Vector> positions;
    std::vector<vrb::Vector> normals;
    std::vector<Vector4s1> jointIndices;
    std::vector<vrb::Quaternion> jointWeights;

    uint32_t indexCount;
    std::vector<uint16_t> indices;

    bool leftHanded;
};

struct HandMeshGLState {
    uint32_t jointCount;
    std::vector<vrb::Matrix> bindMatrices;

    GLuint vertexCount;
    GLuint vboPosition;
    GLuint vboNormal;
    GLuint vboJointIndices;
    GLuint vboJointWeights;

    GLuint indexCount;
    GLuint iboIndices;
};

struct HandMeshRendererSkinned::State {
    GLuint vertexShader { 0 };
    GLuint fragmentShader { 0 };
    GLuint program { 0 };
    GLint aPosition { -1 };
    GLint aNormal { -1 };
    GLint aJointIndices { -1 };
    GLint aJointWeights { - 1};
    GLint uJointMatrices { -1 };
    GLint uPerspective { -1 };
    GLint uView { -1 };
    GLint uModel { -1 };
    std::vector<HandMeshSkinned> handMeshState = { };
    std::vector<HandMeshGLState> handGLState = { };
};

HandMeshRendererSkinned::HandMeshRendererSkinned(State& aState, vrb::CreationContextPtr& aContext)
    : m(aState) {
    m.vertexShader = vrb::LoadShader(GL_VERTEX_SHADER, sVertexShader);
    m.fragmentShader = vrb::LoadShader(GL_FRAGMENT_SHADER, sFragmentShader);
    if (m.vertexShader && m.fragmentShader)
        m.program = vrb::CreateProgram(m.vertexShader, m.fragmentShader);

    assert(m.program);

    m.aPosition = vrb::GetAttributeLocation(m.program, "a_position");
    m.aNormal = vrb::GetAttributeLocation(m.program, "a_normal");
    m.aJointIndices = vrb::GetAttributeLocation(m.program, "a_jointIndices");
    m.aJointWeights = vrb::GetAttributeLocation(m.program, "a_jointWeights");

    m.uJointMatrices = glGetUniformLocation(m.program, "u_jointMatrices");
    m.uPerspective = vrb::GetUniformLocation(m.program, "u_perspective");
    m.uView = vrb::GetUniformLocation(m.program, "u_view");
    m.uModel = vrb::GetUniformLocation(m.program, "u_model");
}

HandMeshRendererPtr HandMeshRendererSkinned::Create(vrb::CreationContextPtr& aContext) {
    return std::make_unique<vrb::ConcreteClass<HandMeshRendererSkinned, HandMeshRendererSkinned::State> >(aContext);
}

HandMeshRendererSkinned::~HandMeshRendererSkinned() {
    for (HandMeshGLState& state: m.handGLState) {
        if (state.vboPosition)
            glDeleteBuffers(1, &state.vboPosition);
        if (state.vboNormal)
            glDeleteBuffers(1, &state.vboNormal);
        if (state.vboJointIndices)
            glDeleteBuffers(1, &state.vboJointIndices);
        if (state.vboJointWeights)
            glDeleteBuffers(1, &state.vboJointWeights);
        if (state.iboIndices)
            glDeleteBuffers(1, &state.iboIndices);
    }
    if (m.program) {
        VRB_GL_CHECK(glDeleteProgram(m.program));
        m.program = 0;
    }
    if (m.vertexShader) {
        VRB_GL_CHECK(glDeleteShader(m.vertexShader));
        m.vertexShader = 0;
    }
    if (m.vertexShader) {
        VRB_GL_CHECK(glDeleteShader(m.fragmentShader));
        m.fragmentShader = 0;
    }
}

template <typename GenericVector>
static bool loadHandMeshAttribute(tinygltf::Model& model,
                                  int accessorIndex, int expectedType, int expectedComponentType,
                                  size_t typeByteSize, int numComponents,
                                  std::vector<GenericVector>& vector) {
    auto& accessor = model.accessors[accessorIndex];

    if (accessor.type != expectedType || accessor.componentType != expectedComponentType)
        return false;
    if (accessor.bufferView >= model.bufferViews.size())
        return false;
    auto& bufferView = model.bufferViews[accessor.bufferView];
    if (bufferView.buffer >= model.buffers.size())
        return false;
    auto& buffer = model.buffers[bufferView.buffer];

    vector.resize(accessor.count);
    memcpy(vector.data(), buffer.data.data() + bufferView.byteOffset + accessor.byteOffset,
           accessor.count * typeByteSize * numComponents);

    return true;
}

bool HandMeshRendererSkinned::LoadHandMeshFromAssets(const bool leftHanded, HandMeshSkinned& handMesh) {
    tinygltf::TinyGLTF modelLoader;
    tinygltf::Model model;
    std::string err;
    std::string warn;
    handMesh.leftHanded = leftHanded;
    if (!modelLoader.LoadBinaryFromFile(&model, &err, &warn,
                                        leftHanded ? "hand-model-left.glb" : "hand-model-right.glb")) {
        VRB_ERROR("Error loading hand mesh asset: %s", err.c_str());
        return false;
    }
    if (!warn.empty())
        VRB_WARN("%s", warn.c_str());

    if (model.meshes.size() == 0)
        return false;
    // Assume the first mesh
    auto& mesh = model.meshes[0];

    if (mesh.primitives.size() == 0)
        return false;
    // Assume the first primitive of the mesh
    auto& primitive = mesh.primitives[0];

    // Load indices
    if (primitive.indices < 0 || primitive.indices >= model.accessors.size())
        return false;
    if (!loadHandMeshAttribute(model, primitive.indices,
                               TINYGLTF_TYPE_SCALAR, TINYGLTF_COMPONENT_TYPE_UNSIGNED_SHORT,
                               sizeof(uint16_t), 1, handMesh.indices)) {
        return false;
    }
    handMesh.indexCount = handMesh.indices.size();

    // Load vertex attributes
    for (auto& attr: primitive.attributes) {
        if (attr.second >= model.accessors.size())
            return false;

        bool loadedOk = true;

        if (attr.first == "POSITION") {
            loadedOk = loadHandMeshAttribute(model, attr.second,
                                             TINYGLTF_TYPE_VEC3, TINYGLTF_COMPONENT_TYPE_FLOAT,
                                             sizeof(float), 3, handMesh.positions);
        } else if (attr.first == "NORMAL") {
            loadedOk = loadHandMeshAttribute(model, attr.second,
                                             TINYGLTF_TYPE_VEC3, TINYGLTF_COMPONENT_TYPE_FLOAT,
                                             sizeof(float), 3, handMesh.normals);
        } else if (attr.first == "JOINTS_0") {
            // For joint indices the helper is not used because we can't copy the buffer
            // directly.
            auto& accessor = model.accessors[attr.second];
            if (accessor.bufferView >= model.bufferViews.size())
                return false;
            auto& bufferView = model.bufferViews[accessor.bufferView];
            if (bufferView.buffer >= model.buffers.size())
                return false;
            auto& buffer = model.buffers[bufferView.buffer];
            if (accessor.type != TINYGLTF_TYPE_VEC4 || accessor.componentType != TINYGLTF_COMPONENT_TYPE_UNSIGNED_BYTE)
                return false;
            if (bufferView.byteLength != accessor.count * sizeof(uint8_t) * 4)
                return false;
            handMesh.jointIndices.resize(accessor.count);
            for (int i = 0; i < accessor.count; i++) {
                unsigned char* ptr = buffer.data.data() + bufferView.byteOffset + accessor.byteOffset + i * 4;
                handMesh.jointIndices[i].x = ptr[0];
                handMesh.jointIndices[i].y = ptr[1];
                handMesh.jointIndices[i].z = ptr[2];
                handMesh.jointIndices[i].w = ptr[3];
            }
        } else if (attr.first == "WEIGHTS_0") {
            loadedOk = loadHandMeshAttribute(model, attr.second,
                                             TINYGLTF_TYPE_VEC4, TINYGLTF_COMPONENT_TYPE_FLOAT,
                                             sizeof(float), 4, handMesh.jointWeights);
        }

        if (!loadedOk)
            return false;
    }
    handMesh.vertexCount = handMesh.positions.size();

    // Load joints' inverse bind matrices
    if (model.skins.size() == 0)
        return false;
    auto& skin = model.skins[0];
    if (skin.inverseBindMatrices >= model.accessors.size())
        return false;
    if (!loadHandMeshAttribute(model, skin.inverseBindMatrices,
                               TINYGLTF_TYPE_MAT4, TINYGLTF_COMPONENT_TYPE_FLOAT,
                               sizeof(vrb::Matrix), 1, handMesh.jointTransforms)) {
        return false;
    }
    handMesh.jointCount = handMesh.jointTransforms.size();

    return true;
}

void HandMeshRendererSkinned::Update(const uint32_t aControllerIndex, const std::vector<vrb::Matrix>& handJointTransforms,
                                     const vrb::GroupPtr& aRoot, HandMeshBufferPtr& buffer, const bool aEnabled, const bool leftHanded) {
    assert(!buffer);

    if (aControllerIndex >= m.handMeshState.size())
        m.handMeshState.resize(aControllerIndex + 1);
    auto& mesh = m.handMeshState.at(aControllerIndex);

    // Lazily load the hand mesh asset
    if (mesh.jointCount == 0) {
        if (!LoadHandMeshFromAssets(leftHanded, mesh))
            return;
        UpdateHandModel(aControllerIndex);
    }

    mesh.jointTransforms = handJointTransforms;
}

void HandMeshRendererSkinned::Draw(const uint32_t aControllerIndex, const vrb::Camera& aCamera) {
    // Bail if the mesh or GL state has not yet been setup (e.g, by a call to Update())
    if (aControllerIndex >= m.handGLState.size() || aControllerIndex >= m.handMeshState.size())
        return;
    const HandMeshGLState& state = m.handGLState.at(aControllerIndex);
    auto& mesh = m.handMeshState.at(aControllerIndex);

    // Bail if we don't have valid hand joints info (e.g, from a previous call to Update())
    if (mesh.jointTransforms.size() == 0)
        return;

    assert(m.program);
    VRB_GL_CHECK(glUseProgram(m.program));

    const GLboolean enabled = glIsEnabled(GL_DEPTH_TEST);
    if (!enabled)
    VRB_GL_CHECK(glEnable(GL_DEPTH_TEST));

    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glEnable(GL_BLEND);

    VRB_GL_CHECK(glUniformMatrix4fv(m.uPerspective, 1, GL_FALSE, aCamera.GetPerspective().Data()));
    VRB_GL_CHECK(glUniformMatrix4fv(m.uView, 1, GL_FALSE, aCamera.GetView().Data()));
    // @FIXME: We don't apply any model transform for now, but eventually we could
    // use `controller.transformMatrix`, if there is need.
    vrb::Matrix modelMatrix = vrb::Matrix::Identity();
    VRB_GL_CHECK(glUniformMatrix4fv(m.uModel, 1, GL_FALSE, modelMatrix.Data()));

    auto jointMatrices = mesh.jointTransforms;

    // We ignore the first matrix, corresponding to the palm, because the
    // models we are currently using don't include a palm joint.
    assert(jointMatrices.size() == state.jointCount + 1);
    jointMatrices.erase(jointMatrices.begin());

    // The hand model we are currently using for the left hand has the
    // bind matrices of the joints in reverse order with respect to that
    // of the XR_EXT_hand_tracking extension, hence we correct it here
    // before assigning.
    if (mesh.leftHanded) {
        for (int i = 0; i < jointMatrices.size() / 2; i++)
            std::swap(jointMatrices[i], jointMatrices[jointMatrices.size() - i - 1]);
    }

    assert(jointMatrices.size() == state.jointCount);
    for (int i = 0; i < state.jointCount; i++) {
        jointMatrices[i].PostMultiplyInPlace(state.bindMatrices[i]);
        VRB_GL_CHECK(glUniformMatrix4fv(m.uJointMatrices + i, 1, GL_FALSE, jointMatrices[i].Data()));
    }

    VRB_GL_CHECK(glBindBuffer(GL_ARRAY_BUFFER, state.vboPosition));
    VRB_GL_CHECK(glEnableVertexAttribArray((GLuint) m.aPosition));
    VRB_GL_CHECK(glVertexAttribPointer((GLuint) m.aPosition, 3, GL_FLOAT, GL_FALSE, 0, nullptr));

    VRB_GL_CHECK(glBindBuffer(GL_ARRAY_BUFFER, state.vboNormal));
    VRB_GL_CHECK(glEnableVertexAttribArray((GLuint) m.aNormal));
    VRB_GL_CHECK(glVertexAttribPointer((GLuint) m.aNormal, 3, GL_FLOAT, GL_FALSE, 0, nullptr));

    VRB_GL_CHECK(glBindBuffer(GL_ARRAY_BUFFER, state.vboJointIndices));
    VRB_GL_CHECK(glEnableVertexAttribArray((GLuint) m.aJointIndices));
    VRB_GL_CHECK(glVertexAttribPointer((GLuint) m.aJointIndices, 4, GL_FLOAT, GL_FALSE, 0, nullptr));

    VRB_GL_CHECK(glBindBuffer(GL_ARRAY_BUFFER, state.vboJointWeights));
    VRB_GL_CHECK(glEnableVertexAttribArray((GLuint) m.aJointWeights));
    VRB_GL_CHECK(glVertexAttribPointer((GLuint) m.aJointWeights, 4, GL_FLOAT, GL_FALSE, 0, nullptr));

    VRB_GL_CHECK(glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, state.iboIndices));
    VRB_GL_CHECK(glDrawElements(GL_TRIANGLES, state.indexCount, GL_UNSIGNED_SHORT, nullptr));

    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);

    if (!enabled)
    VRB_GL_CHECK(glDisable(GL_DEPTH_TEST));
}

void
HandMeshRendererSkinned::UpdateHandModel(const uint32_t aControllerIndex) {
    assert(aControllerIndex < m.handMeshState.size());
    auto& mesh = m.handMeshState.at(aControllerIndex);

    if (aControllerIndex >= m.handGLState.size())
        m.handGLState.resize(aControllerIndex + 1);
    auto& state = m.handGLState.at(aControllerIndex);

    state.jointCount = mesh.jointCount;
    state.vertexCount = mesh.vertexCount;
    state.indexCount = mesh.indexCount;

    if (state.iboIndices == 0) {
        VRB_GL_CHECK(glGenBuffers(1, &state.vboPosition));
        VRB_GL_CHECK(glGenBuffers(1, &state.vboNormal));
        VRB_GL_CHECK(glGenBuffers(1, &state.vboJointIndices));
        VRB_GL_CHECK(glGenBuffers(1, &state.vboJointWeights));
        VRB_GL_CHECK(glGenBuffers(1, &state.iboIndices));
    }

    // Positions VBO
    VRB_GL_CHECK(glBindBuffer(GL_ARRAY_BUFFER, state.vboPosition));
    VRB_GL_CHECK(glBufferData(GL_ARRAY_BUFFER, state.vertexCount * 3 * sizeof(float), mesh.positions.data(), GL_STATIC_DRAW));

    // Normals VBO
    VRB_GL_CHECK(glBindBuffer(GL_ARRAY_BUFFER, state.vboNormal));
    VRB_GL_CHECK(glBufferData(GL_ARRAY_BUFFER, state.vertexCount * 3 * sizeof(float), mesh.normals.data(), GL_STATIC_DRAW));

    // Joint indices VBO
    std::vector<float> jointIndices;
    jointIndices.resize(state.vertexCount * 4);
    for (int i = 0; i < state.vertexCount; i++) {
        jointIndices[i * 4 + 0] = mesh.jointIndices[i].x;
        jointIndices[i * 4 + 1] = mesh.jointIndices[i].y;
        jointIndices[i * 4 + 2] = mesh.jointIndices[i].z;
        jointIndices[i * 4 + 3] = mesh.jointIndices[i].w;
    }
    VRB_GL_CHECK(glBindBuffer(GL_ARRAY_BUFFER, state.vboJointIndices));
    VRB_GL_CHECK(glBufferData(GL_ARRAY_BUFFER, state.vertexCount * 4 * sizeof(float), jointIndices.data(), GL_STATIC_DRAW));

    // Joint weights VBO
    VRB_GL_CHECK(glBindBuffer(GL_ARRAY_BUFFER, state.vboJointWeights));
    VRB_GL_CHECK(glBufferData(GL_ARRAY_BUFFER, state.vertexCount * 4 * sizeof(float), mesh.jointWeights.data(), GL_STATIC_DRAW));

    // Indices IBO
    VRB_GL_CHECK(glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, state.iboIndices));
    VRB_GL_CHECK(glBufferData(GL_ELEMENT_ARRAY_BUFFER, state.indexCount * 1 * sizeof(uint16_t), mesh.indices.data(), GL_STATIC_DRAW));

    // Joint bind matrices
    state.bindMatrices.resize(XR_EXT_HAND_TRACKING_NUM_JOINTS);
    for (int i = 0; i < state.jointCount; i++)
        state.bindMatrices[i] = mesh.jointTransforms[i];

    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
}

};
