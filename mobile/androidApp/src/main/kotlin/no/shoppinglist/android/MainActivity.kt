package no.shoppinglist.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import no.shoppinglist.android.i18n.I18n
import no.shoppinglist.android.i18n.LocalI18n
import no.shoppinglist.android.ui.AppNavigation
import no.shoppinglist.android.ui.theme.ShoppingListTheme
import no.shoppinglist.shared.repository.PreferencesRepository
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val i18n: I18n by inject()
    private val preferencesRepository: PreferencesRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by preferencesRepository.preferences.collectAsStateWithLifecycle()
            val darkTheme = when (prefs?.theme?.lowercase()) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            CompositionLocalProvider(LocalI18n provides i18n) {
                ShoppingListTheme(darkTheme = darkTheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        AppNavigation()
                    }
                }
            }
        }
    }
}
