/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "HandModels.h"
#include "vrb/Camera.h"
#include "vrb/ConcreteClass.h"
#include "vrb/private/ResourceGLState.h"
#include "vrb/gl.h"
#include "vrb/GLError.h"
#include "vrb/Logger.h"
#include "vrb/ShaderUtil.h"

#define XR_EXT_HAND_TRACKING_NUM_JOINTS 26

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

namespace crow {

struct HandGLState {
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

struct HandModels::State : public vrb::ResourceGL::State {
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
  std::vector<struct HandGLState> handGLState = { };
};

HandModelsPtr
HandModels::Create(vrb::CreationContextPtr& aContext) {
  return std::make_shared<vrb::ConcreteClass<HandModels, HandModels::State> >(aContext);
}

void
HandModels::Draw(const vrb::Camera& aCamera, const Controller& aController) {
  assert(aController.handMesh != nullptr);

  assert(m.program);
  VRB_GL_CHECK(glUseProgram(m.program));

  assert(aController.index < m.handGLState.size());
  const HandGLState& state = m.handGLState.at(aController.index);

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

  assert(aController.meshJointTransforms.size() == state.jointCount);
  std::vector<vrb::Matrix> jointMatrices;
  jointMatrices.resize(state.jointCount);
  for (int i = 0; i < state.jointCount; i++) {
    jointMatrices[i] = aController.meshJointTransforms[i].PostMultiply(state.bindMatrices[i]);
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
HandModels::UpdateHandModel(const Controller& aController) {
  if (aController.index >= m.handGLState.size())
    m.handGLState.resize(aController.index + 1);

  const ControllerDelegate::HandMesh& mesh = *aController.handMesh;

  HandGLState& state = m.handGLState.at(aController.index);
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

HandModels::HandModels(State& aState, vrb::CreationContextPtr& aContext)
    : vrb::ResourceGL(aState, aContext)
    , m(aState)
{}

void
HandModels::InitializeGL() {
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

void
HandModels::ShutdownGL() {
  for (HandGLState& state: m.handGLState) {
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

} // namespace crow
