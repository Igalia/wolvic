# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)



# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
             native-lib

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             src/main/cpp/BrowserWindow.cpp
             src/main/cpp/BrowserWorld.cpp
             src/main/cpp/ElbowModel.cpp
             src/main/cpp/vrb/src/CameraEye.cpp
             src/main/cpp/vrb/src/CameraSimple.cpp
             src/main/cpp/vrb/src/Context.cpp
             src/main/cpp/vrb/src/CullVisitor.cpp
             src/main/cpp/vrb/src/Drawable.cpp
             src/main/cpp/vrb/src/DrawableList.cpp
             src/main/cpp/vrb/src/FBO.cpp
             src/main/cpp/vrb/src/FileReaderAndroid.cpp
             src/main/cpp/vrb/src/GLError.cpp
             src/main/cpp/vrb/src/Geometry.cpp
             src/main/cpp/vrb/src/Group.cpp
             src/main/cpp/vrb/src/Light.cpp
             src/main/cpp/vrb/src/ParserObj.cpp
             src/main/cpp/vrb/src/Node.cpp
             src/main/cpp/vrb/src/NodeFactoryObj.cpp
             src/main/cpp/vrb/src/Quaternion.cpp
             src/main/cpp/vrb/src/RenderState.cpp
             src/main/cpp/vrb/src/ResourceGL.cpp
             src/main/cpp/vrb/src/RunnableQueue.cpp
             src/main/cpp/vrb/src/SurfaceTextureFactory.cpp
             src/main/cpp/vrb/src/Texture.cpp
             src/main/cpp/vrb/src/TextureCache.cpp
             src/main/cpp/vrb/src/TextureGL.cpp
             src/main/cpp/vrb/src/TextureSurface.cpp
             src/main/cpp/vrb/src/Transform.cpp
             src/main/cpp/vrb/src/Updatable.cpp
             src/main/cpp/vrb/src/VertexArray.cpp
           )

if(WAVEVR)
target_sources(
    native-lib
    PUBLIC
    src/wavevr/cpp/native-lib.cpp
    src/wavevr/cpp/DeviceDelegateWaveVR.cpp
    )
else()
target_sources(
    native-lib
    PUBLIC
    src/main/cpp/native-lib.cpp
    src/main/cpp/BrowserEGLContext.cpp
    )

include(AndroidNdkModules)
android_ndk_import_module_native_app_glue()
target_link_libraries(native-lib native_app_glue)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -u ANativeActivity_onCreate")
endif()

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib
              log )

find_library( # Sets the name of the path variable.
              android-lib
              android )

add_library(oculusvr-lib SHARED IMPORTED)
set_target_properties(oculusvr-lib PROPERTIES IMPORTED_LOCATION
                      ${CMAKE_SOURCE_DIR}/../third_party/ovr_mobile/VrApi/Libs/${ANDROID_ABI}/libvrapi.so)

add_library(googlevr-lib SHARED IMPORTED)
set_target_properties(googlevr-lib PROPERTIES IMPORTED_LOCATION
                      ${CMAKE_SOURCE_DIR}/../gvr-android-sdk/libraries/jni/${ANDROID_ABI}/libgvr.so)

add_library(svr-lib SHARED IMPORTED)
set_target_properties(svr-lib PROPERTIES IMPORTED_LOCATION
                      ${CMAKE_SOURCE_DIR}/../third_party/svr/libs/${ANDROID_ABI}/libsvrapi.so )

add_library(wavevr-lib SHARED IMPORTED)
set_target_properties(wavevr-lib PROPERTIES IMPORTED_LOCATION
                      ${CMAKE_SOURCE_DIR}/../third_party/wavesdk/build/wvr_client/jni/${ANDROID_ABI}/libwvr_api.so )


# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
                       native-lib

                       # Link VR_SDK_LIB exported from gradle flavors
                       ${VR_SDK_LIB}

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib}
                       ${android-lib}
                       EGL
                       GLESv2
                      )