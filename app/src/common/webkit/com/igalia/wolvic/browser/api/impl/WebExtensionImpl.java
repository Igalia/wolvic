package com.igalia.wolvic.browser.api.impl;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import kotlin.coroutines.Continuation;
import mozilla.components.concept.engine.EngineSession;
import mozilla.components.concept.engine.webextension.ActionHandler;
import mozilla.components.concept.engine.webextension.MessageHandler;
import mozilla.components.concept.engine.webextension.Metadata;
import mozilla.components.concept.engine.webextension.Port;
import mozilla.components.concept.engine.webextension.TabHandler;
import mozilla.components.concept.engine.webextension.WebExtension;

public class WebExtensionImpl extends WebExtension {
    public WebExtensionImpl(@NonNull String id, @NonNull String url, boolean supportActions) {
        super(id, url, supportActions);
    }

    @Override
    public void disconnectPort(@NonNull String s, @Nullable EngineSession engineSession) {

    }

    @Nullable
    @Override
    public Port getConnectedPort(@NonNull String s, @Nullable EngineSession engineSession) {
        return null;
    }

    @Nullable
    @Override
    public Metadata getMetadata() {
        return null;
    }

    @Override
    public boolean hasActionHandler(@NonNull EngineSession engineSession) {
        return false;
    }

    @Override
    public boolean hasContentMessageHandler(@NonNull EngineSession engineSession, @NonNull String s) {
        return false;
    }

    @Override
    public boolean hasTabHandler(@NonNull EngineSession engineSession) {
        return false;
    }

    @Override
    public boolean isAllowedInPrivateBrowsing() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Nullable
    @Override
    public Object loadIcon(int i, @NonNull Continuation<? super Bitmap> continuation) {
        return null;
    }

    @Override
    public void registerActionHandler(@NonNull EngineSession engineSession, @NonNull ActionHandler actionHandler) {

    }

    @Override
    public void registerActionHandler(@NonNull ActionHandler actionHandler) {

    }

    @Override
    public void registerBackgroundMessageHandler(@NonNull String s, @NonNull MessageHandler messageHandler) {

    }

    @Override
    public void registerContentMessageHandler(@NonNull EngineSession engineSession, @NonNull String s, @NonNull MessageHandler messageHandler) {

    }

    @Override
    public void registerTabHandler(@NonNull EngineSession engineSession, @NonNull TabHandler tabHandler) {

    }

    @Override
    public void registerTabHandler(@NonNull TabHandler tabHandler) {

    }
}
