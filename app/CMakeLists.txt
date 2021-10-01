# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_subdirectory(src/main/cpp/vrb/src)

add_library( # Sets the name of the library.
             native-lib

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             src/main/cpp/BrowserWorld.cpp
             src/main/cpp/Cylinder.cpp
             src/main/cpp/Controller.cpp
             src/main/cpp/ControllerContainer.cpp
             src/main/cpp/DeviceUtils.cpp
             src/main/cpp/ElbowModel.cpp
             src/main/cpp/FadeAnimation.cpp
             src/main/cpp/Quad.cpp
             src/main/cpp/ExternalBlitter.cpp
             src/main/cpp/ExternalVR.cpp
             src/main/cpp/GeckoSurfaceTexture.cpp
             src/main/cpp/GestureDelegate.cpp
             src/main/cpp/JNIUtil.cpp
             src/main/cpp/Pointer.cpp
             src/main/cpp/Skybox.cpp
             src/main/cpp/SplashAnimation.cpp
             src/main/cpp/VRBrowser.cpp
             src/main/cpp/VRVideo.cpp
             src/main/cpp/VRLayer.cpp
             src/main/cpp/VRLayerNode.cpp
             src/main/cpp/Widget.cpp
             src/main/cpp/WidgetBorder.cpp
             src/main/cpp/WidgetMover.cpp
             src/main/cpp/WidgetPlacement.cpp
             src/main/cpp/WidgetResizer.cpp
           )

if(WAVEVR)
target_sources(
    native-lib
    PUBLIC
    src/wavevr/cpp/native-lib.cpp
    src/wavevr/cpp/DeviceDelegateWaveVR.cpp
    )
elseif(PICOVR)
target_sources(
        native-lib
        PUBLIC
        src/picovr/cpp/native-lib.cpp
        src/picovr/cpp/DeviceDelegatePicoVR.cpp
        src/picovr/cpp/VRBrowserPico.cpp
)
elseif(NOAPI)
target_sources(
    native-lib
    PUBLIC
    src/noapi/cpp/native-lib.cpp
    src/noapi/cpp/DeviceDelegateNoAPI.cpp
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


if(OPENXR)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -DXR_USE_PLATFORM_ANDROID -DXR_USE_GRAPHICS_API_OPENGL_ES")
include_directories(
        ${CMAKE_SOURCE_DIR}/../third_party/OpenXR-SDK/include
        ${CMAKE_SOURCE_DIR}/../third_party/ovr_openxr_mobile_sdk/OpenXR/Include
        ${CMAKE_SOURCE_DIR}/../app/src/openxr/cpp
)
target_sources(
        native-lib
        PUBLIC
        src/openxr/cpp/DeviceDelegateOpenXR.cpp
        src/openxr/cpp/OpenXRSwapChain.cpp
        src/openxr/cpp/OpenXRLayers.cpp
        src/openxr/cpp/OpenXRInput.cpp
        src/openxr/cpp/OpenXRExtensions.cpp
)
add_custom_command(TARGET native-lib POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy
        ${CMAKE_SOURCE_DIR}/../third_party/ovr_openxr_mobile_sdk/OpenXR/Libs/Android/${ANDROID_ABI}/Release/libopenxr_loader.so
        ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libopenxr_loader.so
        )
elseif(OCULUSVR)
include_directories(
        ${CMAKE_SOURCE_DIR}/../third_party/ovr_mobile/VrApi/Include
        ${CMAKE_SOURCE_DIR}/../third_party/OVRPlatformSDK/Include
        ${CMAKE_SOURCE_DIR}/../app/src/oculusvr/cpp
)
target_sources(
    native-lib
    PUBLIC
    src/oculusvr/cpp/DeviceDelegateOculusVR.cpp
    src/oculusvr/cpp/OculusSwapChain.cpp
    src/oculusvr/cpp/OculusVRLayers.cpp
    )
add_custom_command(TARGET native-lib POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy
        ${CMAKE_SOURCE_DIR}/../third_party/ovr_mobile/VrApi/Libs/Android/${ANDROID_ABI}/Release/libvrapi.so
        ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libvrapi.so
        )
endif()

if(OCULUSVR)
add_custom_command(TARGET native-lib POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy
        ${CMAKE_SOURCE_DIR}/../third_party/OVRPlatformSDK/Android/libs/${ANDROID_ABI}/libovrplatformloader.so
        ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libovrplatformloader.so
        )
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
if(OPENXR)
set_target_properties(oculusvr-lib PROPERTIES IMPORTED_LOCATION
                      ${CMAKE_SOURCE_DIR}/../third_party/ovr_openxr_mobile_sdk/OpenXR/Libs/Android/${ANDROID_ABI}/Release/libopenxr_loader.so )
else()
set_target_properties(oculusvr-lib PROPERTIES IMPORTED_LOCATION
                      ${CMAKE_SOURCE_DIR}/../third_party/ovr_mobile/VrApi/Libs/Android/${ANDROID_ABI}/Release/libvrapi.so )
endif()


add_library(ovrplatform-lib SHARED IMPORTED)
set_target_properties(ovrplatform-lib PROPERTIES IMPORTED_LOCATION
                      ${CMAKE_SOURCE_DIR}/../third_party/OVRPlatformSDK/Android/libs/${ANDROID_ABI}/libovrplatformloader.so )

add_library(wavevr-lib SHARED IMPORTED)
set_target_properties(wavevr-lib PROPERTIES IMPORTED_LOCATION
                      ${CMAKE_SOURCE_DIR}/../third_party/wavesdk/build/wvr_client-${WAVE_SDK_VERSION}/jni/${ANDROID_ABI}/libwvr_api.so )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
                       native-lib
                       vrb

                       # Link VR_SDK_LIB exported from gradle flavors
                       ${VR_SDK_LIB}
                       ${VR_SDK_EXTRA_LIB}

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib}
                       ${android-lib}
                       EGL
                       GLESv3
                      )