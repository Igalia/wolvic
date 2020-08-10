package org.mozilla.vrbrowser.browser

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import mozilla.components.concept.storage.Login
import mozilla.components.service.fxa.SyncEngine
import mozilla.components.service.fxa.sync.GlobalSyncableStoreProvider
import mozilla.components.service.sync.logins.GeckoLoginStorageDelegate
import org.mozilla.vrbrowser.VRBrowserApplication
import org.mozilla.vrbrowser.browser.components.GeckoLoginDelegateWrapper
import org.mozilla.vrbrowser.browser.engine.EngineProvider
import org.mozilla.vrbrowser.ui.widgets.AppServicesProvider
import java.util.concurrent.CompletableFuture

class LoginStorage(
        val context: Context
) {

    private val places = (context as AppServicesProvider).places
    private var storage = places.logins

    init {
        EngineProvider.getOrCreateRuntime(context).loginStorageDelegate = GeckoLoginDelegateWrapper(
                GeckoLoginStorageDelegate(places.logins))
        GlobalScope.launch(Dispatchers.IO) {
            places.logins.value.warmUp()
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