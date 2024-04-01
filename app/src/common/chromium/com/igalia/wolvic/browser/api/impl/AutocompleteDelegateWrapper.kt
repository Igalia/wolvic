package com.igalia.wolvic.browser.api.impl;

import com.igalia.wolvic.browser.api.WAutocomplete
import com.igalia.wolvic.browser.api.WResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mozilla.components.concept.storage.Login
import mozilla.components.concept.storage.LoginEntry
import mozilla.components.concept.storage.LoginsStorage

class AutocompleteDelegateWrapper(private val storageDelegate: ChromiumLoginStorageDelegate) :
    WAutocomplete.StorageDelegate {

    override fun onLoginSave(login: WAutocomplete.LoginEntry) {
        storageDelegate.onLoginSave(login.toLoginEntry())
    }

    private fun onLoginFetchInternal(domain: String?) : WResult<Array<WAutocomplete.LoginEntry>>? {
        val result = WResult.create<Array<WAutocomplete.LoginEntry>>()

        GlobalScope.launch(Dispatchers.IO) {
            val storedLogins = domain?. let { storageDelegate.onLoginFetch(domain) }
                                     ?: let { storageDelegate.onLoginFetch() }

            val logins = storedLogins.await()
                .map { it.toLoginEntry()}
                .toTypedArray()

            result.complete(logins)
        }

        return result
    }

    override fun onLoginFetch(domain: String): WResult<Array<WAutocomplete.LoginEntry>>? {
        return onLoginFetchInternal(domain)
    }
    override fun onLoginFetch(): WResult<Array<WAutocomplete.LoginEntry>>? {
        return onLoginFetchInternal(null)
    }

    override fun onLoginUsed(login: WAutocomplete.LoginEntry, useFields: Int) {
        storageDelegate.onLoginSave(login.toLoginEntry())
    }

    fun findLoginByGuid(guid: String): WResult<WAutocomplete.LoginEntry>? {
        val result = WResult.create<WAutocomplete.LoginEntry>()
        GlobalScope.launch(Dispatchers.IO) {
            val storedLogin = storageDelegate.findLoginByGuid(guid)

            val login = storedLogin.await()
            result.complete(login?.toLoginEntry())
        }
        return result
    }


    companion object {
        @JvmStatic
        fun WAutocomplete.LoginEntry.toLoginEntry() = LoginEntry(
            origin = origin.orEmpty(),
            formActionOrigin = formActionOrigin,
            httpRealm = httpRealm,
            username = username,
            password = password
        )

        @JvmStatic
        fun Login.toLoginEntry() = WAutocomplete.LoginEntry.Builder()
            .guid(guid)
            .origin(origin)
            .formActionOrigin(formActionOrigin)
            .httpRealm(httpRealm)
            .username(username)
            .password(password)
            .build()

        @JvmStatic
        fun create(storage: Lazy<LoginsStorage>) : AutocompleteDelegateWrapper {
            return AutocompleteDelegateWrapper(ChromiumLoginStorageDelegate(storage))
        }
    }
}