#include <android/log.h>
#include <android/native_window_jni.h>
#include "BrowserWorld.h"
#include "DeviceDelegateVisionGlass.h"
#include "VRBrowser.h"
#include "JNIUtil.h"

using namespace crow;

#define JNI_METHOD(return_type, method_name) \
  JNIEXPORT return_type JNICALL              \
    Java_com_igalia_wolvic_PlatformActivity_##method_name

namespace {
    struct AppContext {
        crow::DeviceDelegateVRGlassPtr mDevice;
        JavaContext mJavaContext;
    };
    typedef std::shared_ptr<AppContext> AppContextPtr;

    AppContextPtr sAppContext;
}

extern "C" {

JNI_METHOD(void, activityPaused)
(JNIEnv*, jobject) {
    if (sAppContext->mDevice) {
        sAppContext->mDevice->Pause();
    }
    BrowserWorld::Instance().Pause();
    BrowserWorld::Instance().ShutdownGL();
}

JNI_METHOD(void, activityResumed)
(JNIEnv*, jobject) {
    if (sAppContext->mDevice) {
        sAppContext->mDevice->Resume();
    }
    BrowserWorld::Instance().InitializeGL();
    BrowserWorld::Instance().Resume();
}

JNI_METHOD(void, activityCreated)
(JNIEnv* aEnv, jobject aActivity, jobject aAssetManager) {
    sAppContext->mJavaContext.activity = aEnv->NewGlobalRef(aActivity);
    sAppContext->mJavaContext.env = aEnv;
    sAppContext->mJavaContext.vm->AttachCurrentThread(&sAppContext->mJavaContext.env, nullptr);

    crow::VRBrowser::InitializeJava(aEnv, aActivity);

    sAppContext->mDevice = crow::DeviceDelegateVisionGlass::Create(BrowserWorld::Instance().GetRenderContext());
    sAppContext->mDevice->Resume();
    sAppContext->mDevice->InitializeJava(aEnv, aActivity);

    BrowserWorld::Instance().RegisterDeviceDelegate(sAppContext->mDevice);
    BrowserWorld::Instance().InitializeJava(aEnv, aActivity, aAssetManager);
    BrowserWorld::Instance().InitializeGL();
}

JNI_METHOD(void, updateViewport)
(JNIEnv*, jobject, jint aWidth, jint aHeight) {
    if (sAppContext->mDevice) {
        sAppContext->mDevice->SetViewport(aWidth, aHeight);
    } else {
        VRB_LOG("FAILED TO SET VIEWPORT");
    }
}

JNI_METHOD(void, activityDestroyed)
(JNIEnv*, jobject) {
    BrowserWorld::Instance().ShutdownJava();
    BrowserWorld::Instance().RegisterDeviceDelegate(nullptr);
    BrowserWorld::Destroy();
    if (sAppContext->mDevice) {
        sAppContext->mDevice->ShutdownJava();
        sAppContext->mDevice = nullptr;
    }
}

JNI_METHOD(void, drawGL)
(JNIEnv*, jobject) {
    BrowserWorld::Instance().Draw();
}

JNI_METHOD(void, touchEvent)
(JNIEnv*, jobject, jboolean aDown, jfloat aX, jfloat aY) {
    sAppContext->mDevice->ControllerButtonPressed(aDown);
}

JNI_METHOD(void, setHead)
(JNIEnv*, jobject, jdouble aX, jdouble aY, jdouble aZ, jdouble aW) {
    if (!sAppContext || !sAppContext->mDevice) {
        return;
    }
    sAppContext->mDevice->setHead(aX, aY, aZ, aW);
}

JNI_METHOD(void, setControllerOrientation)
(JNIEnv*, jobject, jdouble aX, jdouble aY, jdouble aZ, jdouble aW) {
    if (!sAppContext || !sAppContext->mDevice) {
        return;
    }
    sAppContext->mDevice->setControllerOrientation(aX, aY, aZ, aW);
}

JNI_METHOD(void, calibrateController)
(JNIEnv*, jobject) {
    if (!sAppContext || !sAppContext->mDevice)
        return;
    sAppContext->mDevice->CalibrateController();
}

jint JNI_OnLoad(JavaVM* aVm, void*) {
    if (sAppContext) {
        return JNI_VERSION_1_6;
    }
    sAppContext = std::make_shared<AppContext>();
    sAppContext->mJavaContext.vm = aVm;
    return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM*, void*) {
    sAppContext.reset();
}

} // extern "C"