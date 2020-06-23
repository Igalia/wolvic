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
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtensionController

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
            return webExtensionDelegate?.onToggleActionPopup(extension, GeckoEngineSession(), action)
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
            allowContentMessaging: Boolean,
            supportActions: Boolean,
            onSuccess: ((WebExtension) -> Unit),
            onError: ((String, Throwable) -> Unit)
    ): CancellableOperation {
        val ext = GeckoWebExtension(id, url, runtime, allowContentMessaging, supportActions)
        return installWebExtension(ext, onSuccess, onError)
    }

    internal fun installWebExtension(
            ext: GeckoWebExtension,
            onSuccess: ((WebExtension) -> Unit) = { },
            onError: ((String, Throwable) -> Unit) = { _, _ -> }
    ): CancellableOperation {
        val geckoResult = if (ext.isBuiltIn()) {
            if (ext.supportActions) {
                // We currently have to install the global action handler before we
                // install the extension which is why this is done here directly.
                // This code can be removed from the engine once the new GV addon
                // management API (specifically installBuiltIn) lands. Then the
                // global handlers will be invoked with the latest state whenever
                // they are registered:
                // https://bugzilla.mozilla.org/show_bug.cgi?id=1599897
                // https://bugzilla.mozilla.org/show_bug.cgi?id=1582185
                ext.registerActionHandler(webExtensionActionHandler)
                ext.registerTabHandler(webExtensionTabHandler)
            }

            // For now we have to use registerWebExtension for builtin extensions until we get the
            // new installBuiltIn call on the controller: https://bugzilla.mozilla.org/show_bug.cgi?id=1601067
            runtime.registerWebExtension(ext.nativeExtension).apply {
                then({
                    webExtensionDelegate?.onInstalled(ext)
                    onSuccess(ext)
                    GeckoResult<Void>()
                }, { throwable ->
                    onError(ext.id, throwable)
                    GeckoResult<Void>()
                })
            }
        } else {
            runtime.webExtensionController.install(ext.url).apply {
                then({
                    val installedExtension = GeckoWebExtension(it!!, runtime)
                    webExtensionDelegate?.onInstalled(installedExtension)
                    installedExtension.registerActionHandler(webExtensionActionHandler)
                    installedExtension.registerTabHandler(webExtensionTabHandler)
                    onSuccess(installedExtension)
                    GeckoResult<Void>()
                }, { throwable ->
                    onError(ext.id, throwable)
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
    override fun registerWebExtensionDelegate(
            webExtensionDelegate: WebExtensionDelegate
    ) {
        this.webExtensionDelegate = webExtensionDelegate

        val promptDelegate = object : WebExtensionController.PromptDelegate {
            override fun onInstallPrompt(ext: org.mozilla.geckoview.WebExtension): GeckoResult<AllowOrDeny>? {
                val extension = GeckoWebExtension(ext, runtime)
                return if (webExtensionDelegate.onInstallPermissionRequest(extension)) {
                    GeckoResult.ALLOW
                } else {
                    GeckoResult.DENY
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
                // As a workaround for https://bugzilla.mozilla.org/show_bug.cgi?id=1621385,
                // we set all installed extensions to be allowed in private browsing mode.
                // We need to revert back to false which is now the default.
                if (!extension.isBuiltIn() && extension.isAllowedInPrivateBrowsing()) {
                    setAllowedInPrivateBrowsing(extension, false)
                }

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

}