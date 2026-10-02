package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WResult;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.api.WWebExtensionController;

import java.util.ArrayList;
import java.util.List;

import kotlin.NotImplementedError;
import mozilla.components.concept.engine.webextension.WebExtension;

public class WebExtensionControllerImpl implements WWebExtensionController {
    private PromptDelegate mPromptDelegate;

    @Nullable
    @Override
    public PromptDelegate getPromptDelegate() {
        return mPromptDelegate;
    }

    @Override
    public void setPromptDelegate(@Nullable PromptDelegate delegate) {
        // TODO: Implement
        mPromptDelegate = delegate;
    }

    @Override
    public void setDebuggerDelegate(@NonNull DebuggerDelegate delegate) {
        // TODO: Implement
    }

    @NonNull
    @Override
    public WResult<WebExtension> install(@NonNull String uri) {
        // TODO: Implement
        return WResult.fromValue(new WebExtensionImpl(uri, uri, false));
    }

    @NonNull
    @Override
    public WResult<WebExtension> setAllowedInPrivateBrowsing(@NonNull WebExtension extension, boolean allowed) {
        // TODO: Implement
        return WResult.fromValue(extension);
    }

    @NonNull
    @Override
    public WResult<WebExtension> installBuiltIn(@NonNull String uri) {
        // TODO: Implement
        return WResult.fromValue(new WebExtensionImpl(uri, uri, false));
    }

    @NonNull
    @Override
    public WResult<WebExtension> ensureBuiltIn(@NonNull String uri, @Nullable String id) {
        // TODO: Implement
        return WResult.fromValue(new WebExtensionImpl(id == null ? uri : id, uri, false));
    }

    @NonNull
    @Override
    public WResult<Void> uninstall(@NonNull WebExtension extension) {
        // TODO: Implement
        return WResult.fromValue(null);
    }

    @NonNull
    @Override
    public WResult<WebExtension> enable(@NonNull WebExtension extension, int source) {
        // TODO: Implement
        return WResult.fromValue(extension);
    }

    @NonNull
    @Override
    public WResult<WebExtension> disable(@NonNull WebExtension extension, int source) {
        // TODO: Implement
        return WResult.fromValue(extension);
    }

    @NonNull
    @Override
    public WResult<List<WebExtension>> list() {
        // TODO: Implement
        return WResult.fromValue(new ArrayList<>());
    }

    @NonNull
    @Override
    public WResult<WebExtension> update(@NonNull WebExtension extension) {
        // TODO: Implement
        return WResult.fromValue(extension);
    }

    @Override
    public void setTabActive(@NonNull WSession session, boolean active) {
        // TODO: Implement
    }
}
