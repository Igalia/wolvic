/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "DeviceUtils.h"
#include "TrackedKeyboardRenderer.h"

#include "vrb/Camera.h"
#include "vrb/ConcreteClass.h"
#include "vrb/GLError.h"
#include "vrb/Matrix.h"
#include "vrb/ProgramFactory.h"
#include "vrb/ShaderUtil.h"
#include "vrb/Transform.h"
#include "vrb/gl.h"

#include <ktx.h>

namespace crow {

namespace {
const char* sVertexShader = R"SHADER(
precision highp float;

uniform mat4 u_perspective;
uniform mat4 u_view;
uniform mat4 u_model;

attribute vec3 a_position;
attribute vec3 a_normal;
attribute vec2 a_texcoord;

varying vec4 v_color;
varying vec2 v_texcoord;

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
  gl_Position = u_perspective * u_view * u_model * pos;

  vec4 normal = vec4(a_normal, 0.0);
  normal = normalize(u_model * normal);
  v_color = calculate_light(normal, u_lights, u_material);

  v_texcoord = a_texcoord;
}
)SHADER";

const char* sFragmentShader = R"SHADER(
precision mediump float;

uniform sampler2D u_sampler;

varying vec4 v_color;
varying vec2 v_texcoord;

void main() {
  vec4 tex = texture2D(u_sampler, v_texcoord);
  gl_FragColor = vec4(mix(v_color.xyz, tex.xyz, 0.65), 1.0);
}
)SHADER";
}

struct GLTFModel {
    tinygltf::Model gltf;
    ktxTexture* ktxTex { nullptr };
    int indicesAttrIndex { -1 };
    int positionAttrIndex { -1 };
    int normalAttrIndex { -1 };
    int texcoordAttrIndex { -1 };

    ~GLTFModel() {
        for (auto& bufferObj: bufferObjs) {
            if (bufferObj)
                glDeleteBuffers(1, &bufferObj);
        }
        if (tex)
            glDeleteTextures(1, &tex);
        if (ktxTex)
            ktxTexture_Destroy(ktxTex);
    }
    void Draw(GLint positionAttrLocation, GLint normalAttrLocation, GLint texcoordAttrLocation);
private:
    GLuint tex { 0 };
    GLuint texTarget { 0 };
    std::vector<GLuint> bufferObjs;

    void InitializeGL();
    void UploadAttributeBuffer(GLuint targetBuffer, int attrIndex);
    void SetupAttribute(GLint location, int attrIndex);
};

void GLTFModel::UploadAttributeBuffer(GLuint targetBuffer, int attrIndex) {
    auto& accessor = gltf.accessors[attrIndex];
    auto& bufferView = gltf.bufferViews[accessor.bufferView];
    auto& buffer = gltf.buffers[bufferView.buffer];

    auto stride = accessor.ByteStride(gltf.bufferViews[accessor.bufferView]);
    auto attrSize = accessor.byteOffset + accessor.count * stride;

    auto& bufferObj = bufferObjs[bufferView.buffer];
    if (bufferObj == 0) {
        VRB_GL_CHECK(glGenBuffers(1, &bufferObj));
        glBindBuffer(targetBuffer, bufferObj);
        glBufferData(targetBuffer, buffer.data.size(), buffer.data.data(), GL_STATIC_DRAW);
    }
}

static int GetNumComponentsInType(const int type) {
    switch (type) {
        case TINYGLTF_TYPE_VEC2: return 2;
        case TINYGLTF_TYPE_VEC3: return 3;
        default: {
            break;
        }
    }
    return -1;
}

static int GetGLComponentType(const int componentType) {
    switch (componentType) {
        case TINYGLTF_COMPONENT_TYPE_FLOAT: return GL_FLOAT;
        default: {
            break;
        }
    }
    return GL_NONE;
}

void GLTFModel::SetupAttribute(GLint location, int attrIndex) {
    auto& accessor = gltf.accessors[attrIndex];
    auto& bufferView = gltf.bufferViews[accessor.bufferView];
    auto& bufferObj = bufferObjs[bufferView.buffer];
    auto stride = accessor.ByteStride(gltf.bufferViews[accessor.bufferView]);
    auto offset = bufferView.byteOffset + accessor.byteOffset;

    GLuint componentType = GetGLComponentType(accessor.componentType);
    GLuint numComponents = GetNumComponentsInType(accessor.type);

    VRB_GL_CHECK(glBindBuffer(GL_ARRAY_BUFFER, bufferObj));
    VRB_GL_CHECK(glEnableVertexAttribArray((GLuint) location));
    VRB_GL_CHECK(glVertexAttribPointer((GLuint) location, numComponents, componentType,
                                       GL_FALSE, stride, (void*) offset));
}

void GLTFModel::InitializeGL() {
    bufferObjs.resize(gltf.buffers.size(), 0);

    // Lazily upload the KTX texture to the GPU
    if (ktxTex && !tex) {
        GLenum glError;
        auto result = ktxTexture_GLUpload(ktxTex, &tex, &texTarget, &glError);
        if (result != KTX_SUCCESS) {
            VRB_LOG("XR_FB_keyboard_tracking: Error uploading KTX texture: (%u) %s", result,
                    ktxErrorString(result));
            assert(tex == 0);
            return;
        }
        VRB_LOG("XR_FB_keyboard_tracking: KTX texture loaded successfully");
    }

    UploadAttributeBuffer(GL_ELEMENT_ARRAY_BUFFER, indicesAttrIndex);
    UploadAttributeBuffer(GL_ARRAY_BUFFER, positionAttrIndex);
    UploadAttributeBuffer(GL_ARRAY_BUFFER, normalAttrIndex);
    UploadAttributeBuffer(GL_ARRAY_BUFFER, texcoordAttrIndex);

    glFlush();
}

void GLTFModel::Draw(GLint positionAttrLocation, GLint normalAttrLocation, GLint texcoordAttrLocation) {
    if (bufferObjs.size() == 0)
        InitializeGL();

    if (tex > 0) {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(texTarget, tex);
    }

    SetupAttribute(positionAttrLocation, positionAttrIndex);
    SetupAttribute(normalAttrLocation, normalAttrIndex);
    SetupAttribute(texcoordAttrLocation, texcoordAttrIndex);

    auto& accessor = gltf.accessors[indicesAttrIndex];
    auto& bufferView = gltf.bufferViews[accessor.bufferView];
    auto& bufferObj = bufferObjs[bufferView.buffer];
    auto offset = bufferView.byteOffset + accessor.byteOffset;
    VRB_GL_CHECK(glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, bufferObj));
    VRB_GL_CHECK(glDrawElements(GL_TRIANGLES, accessor.count, GL_UNSIGNED_SHORT, (void*) offset));

    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
}


struct TrackedKeyboardRenderer::State {
    bool visible { false };
    vrb::Matrix transform { vrb::Matrix::Identity() };

    GLuint vertexShader { 0 };
    GLuint fragmentShader { 0 };
    GLuint program { 0 };
    GLint aPosition { -1 };
    GLint aNormal { -1 };
    GLint aTexcoord { -1 };
    GLint uPerspective { -1 };
    GLint uView { -1 };
    GLint uModel { -1 };
    GLint uSampler { -1 };

    std::unique_ptr<GLTFModel> model { nullptr };
};

TrackedKeyboardRenderer::TrackedKeyboardRenderer(State& aState, vrb::CreationContextPtr& aContext)
    : m(aState) {
}

TrackedKeyboardRendererPtr TrackedKeyboardRenderer::Create(vrb::CreationContextPtr& aContext) {
    return std::make_unique<vrb::ConcreteClass<TrackedKeyboardRenderer, TrackedKeyboardRenderer::State> >(aContext);
}

TrackedKeyboardRenderer::~TrackedKeyboardRenderer() {
    if (m.model != nullptr)
        m.model.reset();

    if (m.program)
        VRB_GL_CHECK(glDeleteProgram(m.program));
    if (m.vertexShader)
        VRB_GL_CHECK(glDeleteShader(m.vertexShader));
    if (m.vertexShader)
        VRB_GL_CHECK(glDeleteShader(m.fragmentShader));
}

void TrackedKeyboardRenderer::SetTransform(const vrb::Matrix& transform) {
    m.transform = transform;
}

bool TrackedKeyboardRenderer::ImageLoaderCallback(tinygltf::Image*, const int, std::string*, std::string*,
                                                  int, int, const unsigned char* data, int dataSize,
                                                  void* user_pointer)
{
    ktxTexture** ktxTex = (ktxTexture**) user_pointer;

    assert(*ktxTex == nullptr);

    auto result = ktxTexture_CreateFromMemory(data, dataSize, KTX_TEXTURE_CREATE_NO_FLAGS,
                                              ktxTex);
    if (result != KTX_SUCCESS) {
        VRB_LOG("XR_FB_keyboard_tracking: Error creating KTX texture: (%u) %s", result,
                ktxErrorString(result));
        return false;
    }

    if (ktxTexture_NeedsTranscoding(*ktxTex)) {
        result = ktxTexture2_TranscodeBasis((ktxTexture2*)*ktxTex, KTX_TTF_ETC, 0);
        if (result != KTX_SUCCESS) {
            VRB_LOG("XR_FB_keyboard_tracking: Error transcoding KTX texture: (%u) %s", result,
                    ktxErrorString(result));
            ktxTexture_Destroy(*ktxTex);
            *ktxTex = nullptr;
            return false;
        }
    }

    VRB_LOG("XR_FB_keyboard_tracking: KTX texture created successfully");

    return true;
}

bool TrackedKeyboardRenderer::LoadKeyboardMesh(const std::vector<uint8_t>& modelBuffer) {
    // Destroy any previous model
    if (m.model != nullptr) {
        m.model.reset();
        m.model = nullptr;
    }

    GLTFModel gltfModel;
    tinygltf::TinyGLTF modelLoader;
    auto& model = gltfModel.gltf;
    std::string err;
    std::string warn;
    ktxTexture* ktxTex = nullptr;

    modelLoader.SetImageLoader(&TrackedKeyboardRenderer::ImageLoaderCallback, &ktxTex);

    if (!modelLoader.LoadBinaryFromMemory(&model, &err, &warn, modelBuffer.data(), modelBuffer.size())) {
        VRB_ERROR("XR_FB_keyboard_tracking: Error loading keyboard model: %s", err.c_str());
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
    gltfModel.indicesAttrIndex = primitive.indices;

    // Load vertex attributes
    for (auto& attr: primitive.attributes) {
        if (attr.second >= model.accessors.size())
            return false;

        if (attr.first == "POSITION") {
            gltfModel.positionAttrIndex = attr.second;
        } else if (attr.first == "NORMAL") {
            gltfModel.normalAttrIndex = attr.second;
        } else if (attr.first == "TEXCOORD_0") {
            gltfModel.texcoordAttrIndex = attr.second;
        }
    }

    if (gltfModel.positionAttrIndex == -1 || gltfModel.normalAttrIndex == -1 || gltfModel.texcoordAttrIndex == -1)
        return false;

    m.model = std::make_unique<GLTFModel>(gltfModel);
    m.model->ktxTex = ktxTex;

    VRB_LOG("XR_FB_keyboard_tracking: Keyboard model loaded successfully");

    return true;
}

void TrackedKeyboardRenderer::SetVisible(const bool aVisible) {
    m.visible = aVisible;
}

bool TrackedKeyboardRenderer::InitializeGL() {
    m.vertexShader = vrb::LoadShader(GL_VERTEX_SHADER, sVertexShader);
    m.fragmentShader = vrb::LoadShader(GL_FRAGMENT_SHADER, sFragmentShader);
    assert(m.vertexShader && m.fragmentShader);
    m.program = vrb::CreateProgram(m.vertexShader, m.fragmentShader);
    if (!m.program) {
        VRB_ERROR("XR_FB_keyboard_tracking: Error creating GL program");
        return false;
    }

    m.aPosition = vrb::GetAttributeLocation(m.program, "a_position");
    m.aNormal = vrb::GetAttributeLocation(m.program, "a_normal");
    m.aTexcoord = vrb::GetAttributeLocation(m.program, "a_texcoord");

    m.uPerspective = vrb::GetUniformLocation(m.program, "u_perspective");
    m.uView = vrb::GetUniformLocation(m.program, "u_view");
    m.uModel = vrb::GetUniformLocation(m.program, "u_model");
    m.uSampler = vrb::GetUniformLocation(m.program, "u_sampler");
    VRB_GL_CHECK(glUniform1i(m.uSampler, 0));

    glFlush();

    return true;
}

void TrackedKeyboardRenderer::Draw(const vrb::Camera& aCamera) {
    if (!m.visible || m.model == nullptr)
        return;

    if (!m.program) {
        if (!InitializeGL())
            return;
    }

    VRB_GL_CHECK(glUseProgram(m.program));

    const auto depthTestEnabled = glIsEnabled(GL_DEPTH_TEST);
    if (!depthTestEnabled)
        VRB_GL_CHECK(glEnable(GL_DEPTH_TEST));

    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glEnable(GL_BLEND);

    VRB_GL_CHECK(glUniformMatrix4fv(m.uPerspective, 1, GL_FALSE, aCamera.GetPerspective().Data()));
    VRB_GL_CHECK(glUniformMatrix4fv(m.uView, 1, GL_FALSE, aCamera.GetView().Data()));
    VRB_GL_CHECK(glUniformMatrix4fv(m.uModel, 1, GL_FALSE, m.transform.Data()));

    m.model->Draw(m.aPosition, m.aNormal, m.aTexcoord);

    if (!depthTestEnabled)
        VRB_GL_CHECK(glDisable(GL_DEPTH_TEST));
}

};
