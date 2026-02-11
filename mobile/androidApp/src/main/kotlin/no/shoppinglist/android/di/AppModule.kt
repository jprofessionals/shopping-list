package no.shoppinglist.android.di

import no.shoppinglist.android.i18n.I18n
import no.shoppinglist.android.notification.AppNotificationManager
import no.shoppinglist.android.viewmodel.AuthViewModel
import no.shoppinglist.android.viewmodel.CommentsViewModel
import no.shoppinglist.android.viewmodel.HouseholdDetailViewModel
import no.shoppinglist.android.viewmodel.HouseholdsViewModel
import no.shoppinglist.android.viewmodel.ListDetailViewModel
import no.shoppinglist.android.viewmodel.ListsViewModel
import no.shoppinglist.android.viewmodel.RecurringItemsViewModel
import no.shoppinglist.android.viewmodel.SettingsViewModel
import no.shoppinglist.android.viewmodel.ShareViewModel
import no.shoppinglist.shared.api.ApiClient
import no.shoppinglist.shared.api.TokenStore
import no.shoppinglist.shared.api.routes.AuthApi
import no.shoppinglist.shared.api.routes.CommentApi
import no.shoppinglist.shared.api.routes.HouseholdApi
import no.shoppinglist.shared.api.routes.ListApi
import no.shoppinglist.shared.api.routes.PreferencesApi
import no.shoppinglist.shared.api.routes.RecurringItemApi
import no.shoppinglist.shared.api.routes.ShareApi
import no.shoppinglist.shared.cache.ShoppingListDatabase
import no.shoppinglist.shared.db.DatabaseDriverFactory
import no.shoppinglist.shared.repository.AuthRepository
import no.shoppinglist.shared.repository.CommentRepository
import no.shoppinglist.shared.repository.HouseholdRepository
import no.shoppinglist.shared.repository.ListRepository
import no.shoppinglist.shared.repository.PreferencesRepository
import no.shoppinglist.shared.repository.RecurringItemRepository
import no.shoppinglist.shared.repository.ShareRepository
import no.shoppinglist.shared.sync.ConnectivityMonitor
import no.shoppinglist.shared.sync.SyncManager
import no.shoppinglist.shared.websocket.WebSocketClient
import no.shoppinglist.shared.websocket.WebSocketEventHandler
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    // Platform infrastructure
    single { DatabaseDriverFactory(androidContext()) }
    single { ShoppingListDatabase(get<DatabaseDriverFactory>().createDriver()) }
    single { TokenStore(androidContext()) }
    single { ApiClient(baseUrl = "http://10.0.2.2:8080", tokenStore = get()) }
    single { ConnectivityMonitor(androidContext()) }
    single { I18n(androidContext()) }

    // API route classes
    single { AuthApi(get()) }
    single { ListApi(get()) }
    single { HouseholdApi(get()) }
    single { ShareApi(get()) }
    single { CommentApi(get()) }
    single { PreferencesApi(get()) }
    single { RecurringItemApi(get()) }

    // Repositories
    single { AuthRepository(get(), get(), get()) }
    single { ListRepository(get(), get()) }
    single { HouseholdRepository(get(), get()) }
    single { ShareRepository(get()) }
    single { CommentRepository(get()) }
    single { PreferencesRepository(get()) }
    single { RecurringItemRepository(get(), get()) }

    // Sync & WebSocket
    single { SyncManager(get(), get(), get()) }
    single { WebSocketClient(baseUrl = "ws://10.0.2.2:8080", tokenStore = get()) }
    single { WebSocketEventHandler(get(), get(), get()) }

    // Notifications
    single { AppNotificationManager(androidContext(), get(), get()) }

    // ViewModels
    viewModel { AuthViewModel(get()) }
    viewModel { ListsViewModel(get(), get(), get()) }
    viewModel { ListDetailViewModel(get(), get()) }
    viewModel { HouseholdsViewModel(get()) }
    viewModel { HouseholdDetailViewModel(get(), get(), get()) }
    viewModel { ShareViewModel(get()) }
    viewModel { CommentsViewModel(get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { RecurringItemsViewModel(get(), get()) }
}
