package com.igalia.wolvic.browser.api.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mozilla.components.concept.storage.Login
import mozilla.components.concept.storage.LoginsStorage
import mozilla.components.service.sync.logins.GeckoLoginStorageDelegate
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult

class GeckoAutocompleteDelegateWrapper(private val storageDelegate: GeckoLoginStorageDelegate) :
    Autocomplete.StorageDelegate {

    override fun onLoginSave(login: Autocomplete.LoginEntry) {
        storageDelegate.onLoginSave(login.toLogin())
    }

    override fun onLoginFetch(domain: String): GeckoResult<Array<Autocomplete.LoginEntry>>? {
        val result = GeckoResult<Array<Autocomplete.LoginEntry>>()

        GlobalScope.launch(Dispatchers.IO) {
            val storedLogins = storageDelegate.onLoginFetch(domain)

            val logins = storedLogins.await()
                .map { it.toLoginEntry()}
                .toTypedArray()

            result.complete(logins)
        }

        return result
    }

    override fun onLoginUsed(login: Autocomplete.LoginEntry, useFields: Int) {
        storageDelegate.onLoginSave(login.toLogin())
    }

    companion object {
        /**
         * Converts a GeckoView [LoginStorage.LoginEntry] to an Android Components [Login]
         */
        @JvmStatic
        fun Autocomplete.LoginEntry.toLogin() = Login(
            guid = guid,
            origin = origin.orEmpty(),
            formActionOrigin = formActionOrigin,
            httpRealm = httpRealm,
            username = username,
            password = password
        )

        /**
         * Converts an Android Components [Login] to a GeckoView [LoginStorage.LoginEntry]
         */
        @JvmStatic
        fun Login.toLoginEntry() = Autocomplete.LoginEntry.Builder()
            .guid(guid)
            .origin(origin)
            .formActionOrigin(formActionOrigin)
            .httpRealm(httpRealm)
            .username(username)
            .password(password)
            .build()

        @JvmStatic
        fun create(storage: Lazy<LoginsStorage>) : GeckoAutocompleteDelegateWrapper {
            return GeckoAutocompleteDelegateWrapper(GeckoLoginStorageDelegate(storage))
        }
    }
}