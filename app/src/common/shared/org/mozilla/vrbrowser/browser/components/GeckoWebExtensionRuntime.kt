/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.browser.components

import android.content.Context
import mozilla.components.concept.engine.CancellableOperation
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.*
import mozilla.components.concept.engine.webextension.WebExtension
import mozilla.components.support.ktx.kotlin.isResourceUrl
import org.mozilla.geckoview.*
import org.mozilla.vrbrowser.browser.engine.Session
import org.mozilla.vrbrowser.browser.engine.SessionStore
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate

class GeckoWebExtensionRuntime(
        private val context: Context,
        private val runtime: GeckoRuntime
): WebExtensionRuntime {

    private var webExtensionDelegate: WebExtensionDelegate? = null
    private val webExtensionActionHandler = object : ActionHandler {
        override fun onBrowserAction(extension: WebExtension, session: EngineSession?, action: Action) {
            webExtensionDelegate?.onBrowserActionDefined(extension, action)
        }

        override fun onPageAction(extension: WebExtension, session: EngineSession?, action: Action) {
            webExtensionDelegate?.onPageActionDefined(extension, action)
        }

        override fun onToggleActionPopup(extension: WebExtension, action: Action): EngineSession? {
            val activeSession = SessionStore.get().activeSession
            val session: Session = SessionStore.get().createWebExtensionSession(activeSession.isPrivateMode);
            session.setParentSession(activeSession)
            session.uaMode = GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            val geckoEngineSession = GeckoEngineSession(session)
            (context as WidgetManagerDelegate).windows.onTabSelect(session)
            return webExtensionDelegate?.onToggleActionPopup(extension, geckoEngineSession, action)
        }
    }
    private val webExtensionTabHandler = object : TabHandler {
        override fun onNewTab(webExtension: WebExtension, engineSession: EngineSession, active: Boolean, url: String) {
            webExtensionDelegate?.onNewTab(webExtension, engineSession, active, url)
        }
    }

    /**
     * See [Engine.installWebExtension].
     */
    override fun installWebExtension(
            id: String,
            url: String,
            onSuccess: ((WebExtension) -> Unit),
            onError: ((String, Throwable) -> Unit)
    ): CancellableOperation {

        val onInstallSuccess: ((org.mozilla.geckoview.WebExtension) -> Unit) = {
            val installedExtension = GeckoWebExtension(it, runtime)
            webExtensionDelegate?.onInstalled(installedExtension)
            installedExtension.registerActionHandler(webExtensionActionHandler)
            installedExtension.registerTabHandler(webExtensionTabHandler)
            onSuccess(installedExtension)
        }

        val geckoResult = if (url.isResourceUrl()) {
            runtime.webExtensionController.ensureBuiltIn(url, id).apply {
                then({
                    onInstallSuccess(it!!)
                    GeckoResult<Void>()
                }, { throwable ->
                    onError(id, throwable)
                    GeckoResult<Void>()
                })
            }
        } else {
            runtime.webExtensionController.install(url).apply {
                then({
                    onInstallSuccess(it!!)
                    GeckoResult<Void>()
                }, { throwable ->
                    onError(id, throwable)
                    GeckoResult<Void>()
                })
            }
        }
        return geckoResult.asCancellableOperation()
    }

    /**
     * See [Engine.uninstallWebExtension].
     */
    override fun uninstallWebExtension(
            ext: WebExtension,
            onSuccess: () -> Unit,
            onError: (String, Throwable) -> Unit
    ) {
        runtime.webExtensionController.uninstall((ext as GeckoWebExtension).nativeExtension).then({
            webExtensionDelegate?.onUninstalled(ext)
            onSuccess()
            GeckoResult<Void>()
        }, { throwable ->
            onError(ext.id, throwable)
            GeckoResult<Void>()
        })
    }

    /**
     * See [Engine.updateWebExtension].
     */
    override fun updateWebExtension(
            extension: WebExtension,
            onSuccess: (WebExtension?) -> Unit,
            onError: (String, Throwable) -> Unit
    ) {
        runtime.webExtensionController.update((extension as GeckoWebExtension).nativeExtension).then({ geckoExtension ->
            val updatedExtension = if (geckoExtension != null) {
                GeckoWebExtension(geckoExtension, runtime).also {
                    it.registerActionHandler(webExtensionActionHandler)
                    it.registerTabHandler(webExtensionTabHandler)
                }
            } else {
                null
            }
            onSuccess(updatedExtension)
            GeckoResult<Void>()
        }, { throwable ->
            onError(extension.id, throwable)
            GeckoResult<Void>()
        })
    }

    /**
     * See [Engine.registerWebExtensionDelegate].
     */
    @Suppress("Deprecation")
    override fun registerWebExtensionDelegate(
            webExtensionDelegate: WebExtensionDelegate
    ) {
        this.webExtensionDelegate = webExtensionDelegate

        val promptDelegate = object : WebExtensionController.PromptDelegate {
            override fun onInstallPrompt(ext: org.mozilla.geckoview.WebExtension): GeckoResult<AllowOrDeny>? {
                val extension = GeckoWebExtension(ext, runtime)
                return if (webExtensionDelegate.onInstallPermissionRequest(extension)) {
                    GeckoResult.allow()
                } else {
                    GeckoResult.deny()
                }
            }

            override fun onUpdatePrompt(
                    current: org.mozilla.geckoview.WebExtension,
                    updated: org.mozilla.geckoview.WebExtension,
                    newPermissions: Array<out String>,
                    newOrigins: Array<out String>
            ): GeckoResult<AllowOrDeny>? {
                // NB: We don't have a user flow for handling updated origins so we ignore them for now.
                val result = GeckoResult<AllowOrDeny>()
                webExtensionDelegate.onUpdatePermissionRequest(
                        GeckoWebExtension(current, runtime),
                        GeckoWebExtension(updated, runtime),
                        newPermissions.toList()
                ) {
                    allow -> if (allow) result.complete(AllowOrDeny.ALLOW) else result.complete(AllowOrDeny.DENY)
                }
                return result
            }
        }

        val debuggerDelegate = object : WebExtensionController.DebuggerDelegate {
            override fun onExtensionListUpdated() {
                webExtensionDelegate.onExtensionListUpdated()
            }
        }

        runtime.webExtensionController.promptDelegate = promptDelegate
        runtime.webExtensionController.setDebuggerDelegate(debuggerDelegate)
    }

    /**
     * See [Engine.listInstalledWebExtensions].
     */
    override fun listInstalledWebExtensions(onSuccess: (List<WebExtension>) -> Unit, onError: (Throwable) -> Unit) {
        runtime.webExtensionController.list().then({
            val extensions = it?.map {
                extension ->
                GeckoWebExtension(extension, runtime)
            } ?: emptyList()

            extensions.forEach { extension ->
                extension.registerActionHandler(webExtensionActionHandler)
                extension.registerTabHandler(webExtensionTabHandler)
            }

            onSuccess(extensions)
            GeckoResult<Void>()
        }, { throwable ->
            onError(throwable)
            GeckoResult<Void>()
        })
    }

    /**
     * See [Engine.enableWebExtension].
     */
    override fun enableWebExtension(
            extension: WebExtension,
            source: EnableSource,
            onSuccess: (WebExtension) -> Unit,
            onError: (Throwable) -> Unit
    ) {
        runtime.webExtensionController.enable((extension as GeckoWebExtension).nativeExtension, source.id).then({
            val enabledExtension = GeckoWebExtension(it!!, runtime)
            webExtensionDelegate?.onEnabled(enabledExtension)
            onSuccess(enabledExtension)
            GeckoResult<Void>()
        }, { throwable ->
            onError(throwable)
            GeckoResult<Void>()
        })
    }

    /**
     * See [Engine.disableWebExtension].
     */
    override fun disableWebExtension(
            extension: WebExtension,
            source: EnableSource,
            onSuccess: (WebExtension) -> Unit,
            onError: (Throwable) -> Unit
    ) {
        runtime.webExtensionController.disable((extension as GeckoWebExtension).nativeExtension, source.id).then({
            val disabledExtension = GeckoWebExtension(it!!, runtime)
            webExtensionDelegate?.onDisabled(disabledExtension)
            onSuccess(disabledExtension)
            GeckoResult<Void>()
        }, { throwable ->
            onError(throwable)
            GeckoResult<Void>()
        })
    }

    /**
     * See [Engine.setAllowedInPrivateBrowsing].
     */
    override fun setAllowedInPrivateBrowsing(
            extension: WebExtension,
            allowed: Boolean,
            onSuccess: (WebExtension) -> Unit,
            onError: (Throwable) -> Unit
    ) {
        runtime.webExtensionController.setAllowedInPrivateBrowsing(
                (extension as GeckoWebExtension).nativeExtension,
                allowed
        ).then({
            val ext = GeckoWebExtension(it!!, runtime)
            webExtensionDelegate?.onAllowedInPrivateBrowsingChanged(ext)
            onSuccess(ext)
            GeckoResult<Void>()
        }, { throwable ->
            onError(throwable)
            GeckoResult<Void>()
        })
    }

}