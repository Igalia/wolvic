package com.igalia.wolvic.browser

import android.content.Context
import com.igalia.wolvic.browser.engine.EngineProvider
import com.igalia.wolvic.ui.widgets.AppServicesProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import mozilla.appservices.logins.InvalidKeyException
import mozilla.components.concept.storage.Login
import mozilla.components.service.fxa.SyncEngine
import mozilla.components.service.fxa.sync.GlobalSyncableStoreProvider
import java.util.concurrent.CompletableFuture

class LoginStorage(
        val context: Context
) {

    private val places = (context as AppServicesProvider).places
    private var storage = places.logins

    init {
        EngineProvider.getOrCreateRuntime(context).setUpLoginPersistence(places.logins)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                storage.value.warmUp()
            } catch (e: InvalidKeyException) {
                storage.value.wipeLocal()
            }
        }

        GlobalSyncableStoreProvider.configureStore(SyncEngine.Passwords to storage)
    }

    fun getLogins(): CompletableFuture<List<Login>> = GlobalScope.future {
        storage.value.list()
    }

    fun deleteEverything() = GlobalScope.future {
        storage.value.wipeLocal()
    }

    fun delete(login: Login) = GlobalScope.future {
        storage.value.delete(login.guid!!);
    }

    fun update(login: Login) = GlobalScope.future {
        storage.value.update(login);
    }

}