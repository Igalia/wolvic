package com.igalia.wolvic.browser

import android.content.Context
import com.igalia.wolvic.browser.engine.EngineProvider
import com.igalia.wolvic.ui.widgets.AppServicesProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import mozilla.components.concept.storage.Login
import mozilla.components.service.fxa.SyncEngine
import mozilla.components.service.fxa.sync.GlobalSyncableStoreProvider
import java.util.concurrent.CompletableFuture

class LoginStorage(
        val context: Context
) {

    private val places = (context.applicationContext as AppServicesProvider).places
    private var storage = places.logins
    private val passwordsKeyProvider by lazy { storage.value.crypto }

    init {
        EngineProvider.getOrCreateRuntime(context).setUpLoginPersistence(places.logins)
        GlobalScope.launch(Dispatchers.IO) {
            storage.value.warmUp()
        }

        GlobalSyncableStoreProvider.configureStore(
            SyncEngine.Passwords to storage,
            keyProvider = lazy { passwordsKeyProvider }
        )
    }

    fun getLogins(): CompletableFuture<List<Login>> = GlobalScope.future {
        storage.value.list()
    }

    fun deleteEverything() = GlobalScope.future {
        storage.value.wipeLocal()
    }

    fun delete(login: Login) = GlobalScope.future {
        storage.value.delete(login.guid);
    }

    fun update(login: Login) = GlobalScope.future {
        storage.value.update(login.guid, login.toEntry());
    }

}