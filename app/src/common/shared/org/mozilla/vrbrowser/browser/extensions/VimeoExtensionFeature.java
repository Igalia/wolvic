package org.mozilla.vrbrowser.browser.extensions;

import org.mozilla.vrbrowser.browser.engine.SessionStore;

import mozilla.components.support.base.feature.LifecycleAwareFeature;

public class VimeoExtensionFeature implements LifecycleAwareFeature {

    private static final String EXTENSION_ID = "mozacVimeo";
    private static final String EXTENSION_URL = "resource://android/assets/web_extensions/webcompat_vimeo/";

    private WebExtensionController mExtensionController = new WebExtensionController(EXTENSION_ID, EXTENSION_URL);

    @Override
    public void start() {
        mExtensionController.install(SessionStore.get());
    }

    @Override
    public void stop() {

    }
}
