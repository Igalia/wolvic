package com.igalia.wolvic.browser.api.impl;

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import mozilla.components.concept.storage.Login
import mozilla.components.concept.storage.LoginEntry
import mozilla.components.concept.storage.LoginStorageDelegate
import mozilla.components.concept.storage.LoginsStorage

/**
 * [LoginStorageDelegate] implementation referred from GeckoLoginStorageDelegate.kt
 **/

class ChromiumLoginStorageDelegate(
    private val loginStorage: Lazy<LoginsStorage>,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : LoginStorageDelegate {

    override fun onLoginUsed(login: Login) {
        scope.launch {
            loginStorage.value.touch(login.guid)
        }
    }

    override fun onLoginFetch(domain: String): Deferred<List<Login>> {
        return scope.async {
            loginStorage.value.getByBaseDomain(domain)
        }
    }

    fun onLoginFetch(): Deferred<List<Login>> {
        return scope.async {
            loginStorage.value.list()
        }
    }

    fun findLoginByGuid(guid: String): Deferred<Login?> {
        return scope.async {
            loginStorage.value.get(guid)
        }
    }

    @Synchronized
    override fun onLoginSave(login: LoginEntry) {
        scope.launch {
            loginStorage.value.addOrUpdate(login)
        }
    }
}
