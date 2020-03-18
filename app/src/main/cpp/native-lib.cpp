/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include <jni.h>
#include <string>
#include <GLES2/gl2.h>

#include "BrowserWorld.h"
#include "vrb/Logger.h"
#include "vrb/GLError.h"
#include "BrowserEGLContext.h"
#include <android_native_app_glue.h>
#include <cstdlib>
#include <vrb/RunnableQueue.h>
#if defined(OCULUSVR)
#include "DeviceDelegateOculusVR.h"
#endif

#include <android/looper.h>
#include <unistd.h>
#include "VRBrowser.h"

#define JNI_METHOD(return_type, method_name) \
  JNIEXPORT return_type JNICALL              \
    Java_org_mozilla_vrbrowser_PlatformActivity_##method_name

using namespace crow;

#if defined(OCULUSVR)
typedef DeviceDelegateOculusVR PlatformDeviceDelegate;
typedef DeviceDelegateOculusVRPtr PlatformDeviceDelegatePtr;
#endif

namespace {

jobject
GetAssetManager(JNIEnv *aEnv, jobject aActivity) {
  jclass clazz = aEnv->GetObjectClass(aActivity);
  jmethodID method = aEnv->GetMethodID(clazz, "getAssets", "()Landroid/content/res/AssetManager;");
  jobject result = aEnv->CallObjectMethod(aActivity, method);
  if (!result) {
    VRB_ERROR("Failed to get AssetManager instance!");
  }
  return result;
}


struct AppContext {
  vrb::RunnableQueuePtr mQueue;
  BrowserEGLContextPtr mEgl;
  PlatformDeviceDelegatePtr mDevice;
};
typedef std::shared_ptr<AppContext> AppContextPtr;

AppContextPtr sAppContext;
}

void
CommandCallback(android_app *aApp, int32_t aCmd) {
  AppContext *ctx = (AppContext *) aApp->userData;

  switch (aCmd) {
    // A new ANativeWindow is ready for use. Upon receiving this command,
    // android_app->window will contain the new window surface.
    case APP_CMD_INIT_WINDOW:
      VRB_LOG("APP_CMD_INIT_WINDOW %p", aApp->window);
      if (!ctx->mEgl) {
        ctx->mEgl = BrowserEGLContext::Create();
        ctx->mEgl->Initialize(aApp->window);
        ctx->mEgl->MakeCurrent();
        VRB_GL_CHECK(glEnable(GL_DEPTH_TEST));
        VRB_GL_CHECK(glEnable(GL_CULL_FACE));
        BrowserWorld::Instance().InitializeGL();
      } else {
        ctx->mEgl->UpdateNativeWindow(aApp->window);
        ctx->mEgl->MakeCurrent();
      }

      if (!BrowserWorld::Instance().IsPaused() && !ctx->mDevice->IsInVRMode()) {
        ctx->mDevice->EnterVR(*ctx->mEgl);
      }

      break;

    // The existing ANativeWindow needs to be terminated.  Upon receiving this command,
    // android_app->window still contains the existing window;
    // after calling android_app_exec_cmd it will be set to NULL.
    case APP_CMD_TERM_WINDOW:
      VRB_LOG("APP_CMD_TERM_WINDOW");
      if (ctx->mDevice->IsInVRMode()) {
         ctx->mDevice->LeaveVR();
      }
      if (ctx->mEgl) {
        ctx->mEgl->UpdateNativeWindow(nullptr);
      }
      break;
    // The app's activity has been paused.
    case APP_CMD_PAUSE:
      VRB_LOG("APP_CMD_PAUSE");
      BrowserWorld::Instance().Pause();
      if (ctx->mDevice->IsInVRMode()) {
        ctx->mDevice->LeaveVR();
      }
      break;

    // The app's activity has been resumed.
    case APP_CMD_RESUME:
      VRB_LOG("APP_CMD_RESUME");
      BrowserWorld::Instance().Resume();
      if (!ctx->mDevice->IsInVRMode() && ctx->mEgl && ctx->mEgl->IsSurfaceReady() ) {
         ctx->mDevice->EnterVR(*ctx->mEgl);
      }
      break;

    // the app's activity is being destroyed,
    // and waiting for the app thread to clean up and exit before proceeding.
    case APP_CMD_DESTROY:
      VRB_LOG("APP_CMD_DESTROY");
      break;

    default:
      break;
  }
}

#if defined(SNAPDRAGONVR)
int32_t
InputCallback(struct android_app *aApp, AInputEvent *aEvent) {
  AppContext *ctx = (AppContext *) aApp->userData;
  if (!ctx->mDevice) {
    return 0;
  }
	const int type = AInputEvent_getType(aEvent);

	if (type == AINPUT_EVENT_TYPE_KEY) {
		const int keyCode = AKeyEvent_getKeyCode(aEvent);
		const int action = AKeyEvent_getAction(aEvent);
		if (action == AKEY_EVENT_ACTION_MULTIPLE) {
		  return 0;
		}

		if (action == AKEY_EVENT_ACTION_UP && (keyCode == AKEYCODE_DPAD_CENTER  || keyCode == AKEYCODE_ENTER)) {
      ctx->mDevice->UpdateButtonState(ControllerDelegate::BUTTON_TRIGGER, false);
      return 1;
		}
    else if (action == AKEY_EVENT_ACTION_DOWN && (keyCode == AKEYCODE_DPAD_CENTER  || keyCode == AKEYCODE_ENTER)) {
      ctx->mDevice->UpdateButtonState(ControllerDelegate::BUTTON_TRIGGER, true);
      return 1;
    }
		else if (action == AKEY_EVENT_ACTION_UP && keyCode == AKEYCODE_MENU) {
      ctx->mDevice->UpdateButtonState(ControllerDelegate::BUTTON_APP, false);
      return 1;
		}
    else if (action == AKEY_EVENT_ACTION_DOWN && keyCode == AKEYCODE_MENU) {
      ctx->mDevice->UpdateButtonState(ControllerDelegate::BUTTON_APP,  true);
      return 1;
    }
		else if (keyCode == AKEYCODE_DPAD_LEFT) {
		  // Wheel moved: simulate scroll
		  ctx->mDevice->WheelScroll(-2.0);
		  return 1;
		}
		else if (keyCode == AKEYCODE_DPAD_RIGHT) {
		  // Wheel moved: simulate scroll
		  ctx->mDevice->WheelScroll(2.0);
		  return 1;
		}
	}
	else if (type == AINPUT_EVENT_TYPE_MOTION) {
	  const int source = AInputEvent_getSource(aEvent);
    // Disable trackball scroll for now because the scroll it's not very stable
	  /*if (source == AINPUT_SOURCE_TRACKBALL) {
	    const float x = AMotionEvent_getX(aEvent, 0);
	    const float y = AMotionEvent_getY(aEvent, 0);
	    ctx->mDevice->UpdateTrackpad(x, y);
	  }*/
	}

	return 0;
}
#endif // defined(SNAPDRAGONVR)

extern "C" {

void
android_main(android_app *aAppState) {

  if (!ALooper_forThread()) {
    ALooper_prepare(0);
  }

  // Attach JNI thread
  JNIEnv *jniEnv;
  (*aAppState->activity->vm).AttachCurrentThread(&jniEnv, nullptr);
  sAppContext->mQueue->AttachToThread();

  // Create Browser context
  crow::VRBrowser::InitializeJava(jniEnv, aAppState->activity->clazz);

  // Create device delegate
  sAppContext->mDevice = PlatformDeviceDelegate::Create(BrowserWorld::Instance().GetRenderContext(),
                                                        aAppState);
  BrowserWorld::Instance().RegisterDeviceDelegate(sAppContext->mDevice);

  // Initialize java
  auto assetManager = GetAssetManager(jniEnv, aAppState->activity->clazz);
  BrowserWorld::Instance().InitializeJava(jniEnv, aAppState->activity->clazz, assetManager);
  jniEnv->DeleteLocalRef(assetManager);

  // Set up activity & SurfaceView life cycle callbacks
  aAppState->userData = sAppContext.get();
  aAppState->onAppCmd = CommandCallback;
#if defined(SNAPDRAGONVR)
  aAppState->onInputEvent = InputCallback;
#endif

  // Main render loop
  while (true) {
    int events;
    android_poll_source *pSource;

    // Loop until all events are read
    // If the activity is paused use a blocking call to read events.
    while (ALooper_pollAll(BrowserWorld::Instance().IsPaused() ? -1 : 0,
                           nullptr,
                           &events,
                           (void **) &pSource) >= 0) {
      // Process event.
      if (pSource) {
        pSource->process(aAppState, pSource);
      }



      // Check if we are exiting.
      if (aAppState->destroyRequested != 0) {
        sAppContext->mEgl->MakeCurrent();
        sAppContext->mQueue->ProcessRunnables();
        BrowserWorld::Instance().ShutdownGL();
        BrowserWorld::Instance().ShutdownJava();
        BrowserWorld::Destroy();
        sAppContext->mEgl->Destroy();
        sAppContext->mEgl.reset();
        sAppContext->mDevice.reset();
        aAppState->activity->vm->DetachCurrentThread();
        return;
      }
    }
    if (sAppContext->mEgl) {
      sAppContext->mEgl->MakeCurrent();
    }
    sAppContext->mQueue->ProcessRunnables();
    if (!BrowserWorld::Instance().IsPaused() && sAppContext->mDevice->IsInVRMode()) {
      VRB_GL_CHECK(glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT));
      BrowserWorld::Instance().Draw();
    }
  }
}

JNI_METHOD(void, queueRunnable)
(JNIEnv *aEnv, jobject, jobject aRunnable) {
  if (sAppContext) {
    sAppContext->mQueue->AddRunnable(aEnv, aRunnable);
  } else {
    VRB_ERROR("Failed to queue Runnable from UI thread. Render thread AppContext has not been initialized.")
  }
}

JNI_METHOD(jboolean, platformExit)
(JNIEnv *, jobject) {
  if (sAppContext && sAppContext->mDevice) {
    return (jboolean) sAppContext->mDevice->ExitApp();
  }
  return (jboolean) false;
}

jint JNI_OnLoad(JavaVM* aVm, void*) {
  sAppContext = std::make_shared<AppContext>();
  sAppContext->mQueue = vrb::RunnableQueue::Create(aVm);
  return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM* vm, void* reserved) {
  sAppContext = nullptr;
}


} // extern "C"
