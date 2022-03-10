package com.igalia.wolvic.browser.api;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import mozilla.components.concept.engine.webextension.WebExtension;

public interface WWebExtensionController {
    /**
     * This delegate will be called whenever an extension is about to be installed or it needs new
     * permissions, e.g during an update or because it called <code>permissions.request</code>
     */
    @UiThread
    interface PromptDelegate {
        /**
         * Called whenever a new extension is being installed. This is intended as an opportunity for
         * the app to prompt the user for the permissions required by this extension.
         *
         * @param extension The {@link WebExtension} that is about to be installed. You can use {@link
         *                  WebExtension#metaData} to gather information about this extension when building the user
         *                  prompt dialog.
         * @return A {@link WResult} that completes to either {@link WAllowOrDeny#ALLOW ALLOW} if
         * this extension should be installed or {@link WAllowOrDeny#DENY DENY} if this extension
         * should not be installed. A null value will be interpreted as {@link WAllowOrDeny#DENY
         * DENY}.
         */
        @Nullable
        default WResult<WAllowOrDeny> onInstallPrompt(final @NonNull WebExtension extension) {
            return null;
        }

        /**
         * Called whenever an updated extension has new permissions. This is intended as an opportunity
         * for the app to prompt the user for the new permissions required by this extension.
         *
         * @param currentlyInstalled The {@link WebExtension} that is currently installed.
         * @param updatedExtension   The {@link WebExtension} that will replace the previous extension.
         * @param newPermissions     The new permissions that are needed.
         * @param newOrigins         The new origins that are needed.
         * @return A {@link WResult} that completes to either {@link WAllowOrDeny#ALLOW ALLOW} if
         * this extension should be update or {@link WAllowOrDeny#DENY DENY} if this extension should
         * not be update. A null value will be interpreted as {@link WAllowOrDeny#DENY DENY}.
         */
        @Nullable
        default WResult<WAllowOrDeny> onUpdatePrompt(
                @NonNull final WebExtension currentlyInstalled,
                @NonNull final WebExtension updatedExtension,
                @NonNull final String[] newPermissions,
                @NonNull final String[] newOrigins) {
            return null;
        }
    }


    interface DebuggerDelegate {
        /**
         * Called whenever the list of installed extensions has been modified using the debugger with
         * tools like web-ext.
         *
         * <p>This is intended as an opportunity to refresh the list of installed extensions using
         * {@link WWebExtensionController#list} and to set delegates on the new {@link WebExtension}
         * objects, e.g. using {@link com.igalia.wolvic.browser.api.WebExtension#setActionDelegate} and {@link
         * WebExtension#setMessageDelegate}.
         *
         * @see <a
         *     href="https://extensionworkshop.com/documentation/develop/getting-started-with-web-ext">
         *     Getting started with web-ext</a>
         */
        @UiThread
        default void onExtensionListUpdated() {}
    }


    /**
     * @return the current {@link .PromptDelegate} instance.
     * @see WWebExtensionController.PromptDelegate
     */
    @UiThread
    @Nullable
    WWebExtensionController.PromptDelegate getPromptDelegate();
    /**
     * Set the {@link WWebExtensionController.PromptDelegate} for this instance. This delegate will be used to be notified
     * whenever an extension is being installed or needs new permissions.
     *
     * @param delegate the delegate instance.
     * @see WWebExtensionController.PromptDelegate
     */
    @UiThread
    void setPromptDelegate(final @Nullable PromptDelegate delegate);

    /**
     * Set the {@link WWebExtensionController.DebuggerDelegate} for this instance. This delegate will receive updates about
     * extension changes using developer tools.
     *
     * @param delegate the Delegate instance
     */
    @UiThread
    void setDebuggerDelegate(final @NonNull DebuggerDelegate delegate);
    

    /**
     * Install an extension.
     *
     * <p>An installed extension will persist and will be available even when restarting the {@link
     * WRuntime}.
     *
     * <p>Installed extensions through this method need to be signed by Mozilla, see <a
     * href="https://extensionworkshop.com/documentation/publish/signing-and-distribution-overview/#distributing-your-addon">
     * Distributing your add-on </a>.
     *
     * <p>When calling this method, the Browser library will download the extension, validate its
     * manifest and signature, and give you an opportunity to verify its permissions through {@link
     * WWebExtensionController.PromptDelegate#installPrompt}, you can use this method to prompt the user if appropriate.
     *
     * @param uri URI to the extension's <code>.xpi</code> package. This can be a remote <code>https:
     *     </code> URI or a local <code>file:</code> or <code>resource:</code> URI. Note: the app
     *     needs the appropriate permissions for local URIs.
     * @return A {@link WResult} that will complete when the installation process finishes. For
     *     successful installations, the IResult will return the {@link WebExtension} object that
     *     you can use to set delegates and retrieve information about the WebExtension using {@link
     *     WebExtension#metaData}.
     *     <p>If an error occurs during the installation process, the IResult will complete
     *     exceptionally with a {@link WebExtension.InstallException InstallException} that will
     *     contain the relevant error code in {@link WebExtension.InstallException#code
     *     InstallException#code}.
     * @see WWebExtensionController.PromptDelegate#installPrompt
     * @see WebExtension.InstallException.ErrorCodes
     * @see WebExtension#metaData
     */
    @NonNull
    @AnyThread
    WResult<WebExtension> install(final @NonNull String uri);

    /**
     * Set whether an extension should be allowed to run in private browsing or not.
     *
     * @param extension the {@link WebExtension} instance to modify.
     * @param allowed true if this extension should be allowed to run in private browsing pages, false
     *     otherwise.
     * @return the updated {@link WebExtension} instance.
     */
    @NonNull
    @AnyThread
    WResult<WebExtension> setAllowedInPrivateBrowsing(final @NonNull WebExtension extension, final boolean allowed);

    /**
     * Install a built-in extension.
     *
     * <p>Built-in extensions have access to native messaging, don't need to be signed and are
     * installed from a folder in the APK instead of a .xpi bundle.
     *
     * <p>Example:
     *
     * <p><code>
     *    controller.installBuiltIn("resource://android/assets/example/");
     * </code> Will install the built-in extension located at <code>/assets/example/</code> in the
     * app's APK.
     *
     * @param uri Folder where the extension is located. To ensure this folder is inside the APK, only
     *     <code>resource://android</code> URIs are allowed.
     * @see WebExtension.MessageDelegate
     * @return A {@link WResult} that completes with the extension once it's installed.
     */
    @NonNull
    @AnyThread
    WResult<WebExtension> installBuiltIn(final @NonNull String uri);

    /**
     * Ensure that a built-in extension is installed.
     *
     * <p>Similar to {@link #installBuiltIn}, except the extension is not re-installed if it's already
     * present and it has the same version.
     *
     * <p>Example:
     *
     * <p><code>
     *    controller.ensureBuiltIn("resource://android/assets/example/", "example@example.com");
     * </code> Will install the built-in extension located at <code>/assets/example/</code> in the
     * app's APK.
     *
     * @param uri Folder where the extension is located. To ensure this folder is inside the APK, only
     *     <code>resource://android</code> URIs are allowed.
     * @param id Extension ID as present in the manifest.json file.
     * @see WebExtension.MessageDelegate
     * @return A {@link WResult} that completes with the extension once it's installed.
     */
    @NonNull
    @AnyThread
    WResult<WebExtension> ensureBuiltIn(final @NonNull String uri, final @Nullable String id);

    /**
     * Uninstall an extension.
     *
     * <p>Uninstalling an extension will remove it from the current {@link WRuntime} instance,
     * delete all its data and trigger a request to close all extension pages currently open.
     *
     * @param extension The {@link WebExtension} to be uninstalled.
     * @return A {@link WResult} that will complete when the uninstall process is completed.
     */
    @NonNull
    @AnyThread
    WResult<Void> uninstall(final @NonNull WebExtension extension);


    @Retention(RetentionPolicy.SOURCE)
    @IntDef({WWebExtensionController.EnableSource.USER, WWebExtensionController.EnableSource.APP})
    @interface EnableSources {}

    /**
     * Contains the possible values for the <code>source</code> parameter in {@link #enable} and
     * {@link #disable}.
     */
    class EnableSource {
        /** Action has been requested by the user. */
        public static final int USER = 1;

        /**
         * Action requested by the app itself, e.g. to disable an extension that is not supported in
         * this version of the app.
         */
        public static final int APP = 2;
    }

    /**
     * Enable an extension that has been disabled. If the extension is already enabled, this method
     * has no effect.
     *
     * @param extension The {@link WebExtension} to be enabled.
     * @param source The agent that initiated this action, e.g. if the action has been initiated by
     *     the user,use {@link WWebExtensionController.EnableSource#USER}.
     * @return the new {@link WebExtension} instance, updated to reflect the enablement.
     */
    @AnyThread
    @NonNull
    WResult<WebExtension> enable(final @NonNull WebExtension extension, final @EnableSources int source);

    /**
     * Disable an extension that is enabled. If the extension is already disabled, this method has no
     * effect.
     *
     * @param extension The {@link WebExtension} to be disabled.
     * @param source The agent that initiated this action, e.g. if the action has been initiated by
     *     the user, use {@link WWebExtensionController.EnableSource#USER}.
     * @return the new {@link WebExtension} instance, updated to reflect the disablement.
     */
    @AnyThread
    @NonNull
    WResult<WebExtension> disable(final @NonNull WebExtension extension, final @EnableSources int source);

    /**
     * List installed extensions for this {@link WRuntime}.
     *
     * <p>The returned list can be used to set delegates on the {@link WebExtension} objects using
     * {@link WebExtension#setActionDelegate}, {@link WebExtension#setMessageDelegate}.
     *
     * @return a {@link WResult} that will resolve when the list of extensions is available.
     */
    @AnyThread
    @NonNull
    WResult<List<WebExtension>> list();

    /**
     * Update a web extension.
     *
     * <p>When checking for an update, Browser will download the update manifest that is defined by
     * the web extension's manifest property <a
     * href="https://extensionworkshop.com/documentation/manage/updating-your-extension/">browser_specific_settings.gecko.update_url</a>.
     * If an update is found it will be downloaded and installed. If the extension needs any new
     * permissions the {@link WWebExtensionController.PromptDelegate#updatePrompt} will be triggered.
     *
     * <p>More information about the update manifest format is available <a
     * href="https://extensionworkshop.com/documentation/manage/updating-your-extension/#manifest-structure">here</a>.
     *
     * @param extension The extension to update.
     * @return A {@link WResult} that will complete when the update process finishes. If an update
     *     is found and installed successfully, the IResult will return the updated {@link
     *     WebExtension}. If no update is available, null will be returned. If the updated extension
     *     requires new permissions, the {@link WWebExtensionController.PromptDelegate#installPrompt} will be called.
     * @see WWebExtensionController.PromptDelegate#updatePrompt
     */
    @AnyThread
    @NonNull
    WResult<WebExtension> update(final @NonNull WebExtension extension);


    /**
     * Notifies extensions about a active tab change over the `tabs.onActivated` event.
     *
     * @param session The {@link WSession} of the newly selected session/tab.
     * @param active true if the tab became active, false if the tab became inactive.
     */
    @AnyThread
    void setTabActive(@NonNull final WSession session, final boolean active);

}
