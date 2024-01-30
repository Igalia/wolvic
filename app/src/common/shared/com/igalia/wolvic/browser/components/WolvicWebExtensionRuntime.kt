/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.browser.components

import android.content.Context
import android.util.AttributeSet
import android.util.JsonReader
import com.igalia.wolvic.browser.api.*
import com.igalia.wolvic.browser.engine.Session
import com.igalia.wolvic.browser.engine.SessionStore
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate
import mozilla.components.concept.base.profiler.Profiler
import mozilla.components.concept.engine.*
import mozilla.components.concept.engine.utils.EngineVersion
import mozilla.components.concept.engine.webextension.*
import mozilla.components.support.ktx.kotlin.isResourceUrl
import org.json.JSONObject

class WolvicWebExtensionRuntime(
        private val context: Context,
        private val runtime: WRuntime
) : WebExtensionRuntime, Engine {

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
            session.setUaMode(WSessionSettings.USER_AGENT_MODE_DESKTOP, true)
            val engineSession = WolvicEngineSession(session)
            (context as WidgetManagerDelegate).windows.onTabSelect(session)
            return webExtensionDelegate?.onToggleActionPopup(extension, engineSession, action)
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
            url: String,
            installationMethod: InstallationMethod?,
            onSuccess: (WebExtension) -> Unit,
            onError: (Throwable) -> Unit
    ): CancellableOperation {

        val onInstallSuccess: ((WebExtension) -> Unit) = {
            webExtensionDelegate?.onInstalled(it)
            it.registerActionHandler(webExtensionActionHandler)
            it.registerTabHandler(webExtensionTabHandler, null)
            onSuccess(it)
        }

        val result = runtime.webExtensionController.install(url).apply {
            then({
                onInstallSuccess(it!!)
                WResult.create<Void>()
            }, { throwable ->
                onError(throwable)
                WResult.create<Void>()
            })
        }
        return result.asCancellableOperation()
    }

    /**
     * See [Engine.uninstallWebExtension].
     */
    override fun uninstallWebExtension(
            ext: WebExtension,
            onSuccess: () -> Unit,
            onError: (String, Throwable) -> Unit
    ) {
        runtime.webExtensionController.uninstall(ext).then({
            webExtensionDelegate?.onUninstalled(ext)
            onSuccess()
            WResult.create<Void>()
        }, { throwable ->
            onError(ext.id, throwable)
            WResult.create<Void>()
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
        runtime.webExtensionController.update(extension).then({ updatedExtension ->
            if (updatedExtension != null) {
                updatedExtension.registerActionHandler(webExtensionActionHandler)
                updatedExtension.registerTabHandler(webExtensionTabHandler, null)
            }
            onSuccess(updatedExtension)
            WResult.create<Void>()
        }, { throwable ->
            onError(extension.id, throwable)
            WResult.create<Void>()
        })
    }

    /**
     * See [Engine.registerWebExtensionDelegate].
     */
    override fun registerWebExtensionDelegate(
            webExtensionDelegate: WebExtensionDelegate
    ) {
        this.webExtensionDelegate = webExtensionDelegate

        val promptDelegate = object : WWebExtensionController.PromptDelegate {
            override fun onInstallPrompt(extension: WebExtension): WResult<WAllowOrDeny>? {
                val result = WResult.allow()
                webExtensionDelegate.onInstallPermissionRequest(
                        extension
                ) {
                    allow -> if (allow) result.complete(WAllowOrDeny.ALLOW) else result.complete(
                    WAllowOrDeny.DENY)
                }
                return result
            }

            override fun onUpdatePrompt(
                    current: WebExtension,
                    updated: WebExtension,
                    newPermissions: Array<out String>,
                    newOrigins: Array<out String>
            ): WResult<WAllowOrDeny>? {
                // NB: We don't have a user flow for handling updated origins so we ignore them for now.
                val result = WResult.create<WAllowOrDeny>()
                webExtensionDelegate.onUpdatePermissionRequest(
                        current,
                        updated,
                        newPermissions.toList()
                ) {
                    allow -> if (allow) result.complete(WAllowOrDeny.ALLOW) else result.complete(
                    WAllowOrDeny.DENY)
                }
                return result
            }
        }

        val debuggerDelegate = object : WWebExtensionController.DebuggerDelegate {
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
            val extensions: List<WebExtension> = it ?: emptyList()

            extensions.forEach { extension ->
                extension.registerActionHandler(webExtensionActionHandler)
                extension.registerTabHandler(webExtensionTabHandler, null)
            }

            onSuccess(extensions)
            WResult.create<Void>()
        }, { throwable ->
            onError(throwable)
            WResult.create<Void>()
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
        runtime.webExtensionController.enable(extension, source.id).then({
            if (it != null) {
                webExtensionDelegate?.onEnabled(it)
                onSuccess(it)
            }
            WResult.create<Void>()
        }, { throwable ->
            onError(throwable)
            WResult.create<Void>()
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
        runtime.webExtensionController.disable(extension, source.id).then({
            if (it != null) {
                webExtensionDelegate?.onDisabled(it)
                onSuccess(it)
            }
            WResult.create<Void>()
        }, { throwable ->
            onError(throwable)
            WResult.create<Void>()
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
                extension,
                allowed
        ).then({
            val ext = it!!
            webExtensionDelegate?.onAllowedInPrivateBrowsingChanged(ext)
            onSuccess(ext)
            WResult.create<Void>()
        }, { throwable ->
            onError(throwable)
            WResult.create<Void>()
        })
    }

    // Functionality specific to the Engine interface.

    override val profiler: Profiler?
        get() = TODO("Not yet implemented")
    override val settings: Settings
        get() = TODO("Not yet implemented")
    override val version: EngineVersion
        get() = TODO("Not yet implemented")

    override fun name(): String {
        TODO("Not yet implemented")
    }

    override fun createSession(private: Boolean, contextId: String?): EngineSession {
        TODO("Not yet implemented")
    }

    override fun createSessionState(json: JSONObject): EngineSessionState {
        TODO("Not yet implemented")
    }

    override fun createSessionStateFrom(reader: JsonReader): EngineSessionState {
        TODO("Not yet implemented");
    }

    override fun createView(context: Context, attrs: AttributeSet?): EngineView {
        TODO("Not yet implemented")
    }

    override fun speculativeConnect(url: String) {
        TODO("Not yet implemented")
    }

}