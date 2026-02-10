package no.shoppinglist.android

import android.app.Application
import no.shoppinglist.android.di.appModule
import no.shoppinglist.shared.api.ApiClient
import no.shoppinglist.shared.repository.AuthRepository
import no.shoppinglist.shared.websocket.WebSocketEventHandler
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class ShoppingListApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@ShoppingListApp)
            modules(appModule)
        }

        // Connect session expiry to auth state
        val apiClient = get<ApiClient>()
        val authRepository = get<AuthRepository>()
        apiClient.onSessionExpired = { authRepository.onSessionExpired() }

        get<WebSocketEventHandler>().start()
    }
}
