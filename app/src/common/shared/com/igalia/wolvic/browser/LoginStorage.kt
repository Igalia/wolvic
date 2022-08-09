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
                // Login database sometimes gets corrupted for unknown reasons. This has been reported
                // to mozilla components in the past (https://github.com/mozilla-mobile/android-components/issues/6681
                // or https://github.com/mozilla-mobile/fenix/issues/15597) but it was never really
                // fixed so clients have to deal with that. The only thing we could do is to delete
                // the database file. We cannot just use wipe()/wipeLocal() because those calls also
                // try to use the connection to the DB and thus they'll crash as well.
                places.clearLoginsDatabaseUglyHack()
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