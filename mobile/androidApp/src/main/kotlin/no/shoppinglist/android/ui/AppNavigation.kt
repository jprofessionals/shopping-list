package no.shoppinglist.android.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import no.shoppinglist.android.i18n.t
import no.shoppinglist.android.ui.auth.LoginScreen
import no.shoppinglist.android.ui.auth.RegisterScreen
import no.shoppinglist.android.ui.common.LoadingScreen
import no.shoppinglist.android.ui.households.HouseholdDetailScreen
import no.shoppinglist.android.ui.households.HouseholdsScreen
import no.shoppinglist.android.ui.lists.ListDetailScreen
import no.shoppinglist.android.ui.lists.ListsScreen
import no.shoppinglist.android.ui.settings.SettingsScreen
import no.shoppinglist.android.viewmodel.AuthViewModel
import org.koin.androidx.compose.koinViewModel

private sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object Lists : Screen("lists")
    data object ListDetail : Screen("lists/{listId}") {
        fun createRoute(listId: String): String = "lists/$listId"
    }
    data object Households : Screen("households")
    data object HouseholdDetail : Screen("households/{householdId}") {
        fun createRoute(householdId: String): String = "households/$householdId"
    }
    data object Settings : Screen("settings")
    data object SharedListView : Screen("shared/{token}")
}

private data class BottomNavItem(
    val label: String,
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val bottomNavItems = listOf(
    BottomNavItem(
        label = "nav.lists",
        route = Screen.Lists.route,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    BottomNavItem(
        label = "nav.households",
        route = Screen.Households.route,
        selectedIcon = Icons.Filled.People,
        unselectedIcon = Icons.Outlined.People,
    ),
    BottomNavItem(
        label = "settings.title",
        route = Screen.Settings.route,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
)

private val screensWithBottomBar = setOf(
    Screen.Lists.route,
    Screen.Households.route,
    Screen.Settings.route,
)

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel = koinViewModel(),
) {
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    when {
        authState.isLoading && authState.user == null -> {
            LoadingScreen()
        }
        authState.isLoggedIn -> {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            Scaffold(
                bottomBar = {
                    if (currentRoute in screensWithBottomBar) {
                        NavigationBar {
                            bottomNavItems.forEach { item ->
                                val selected = currentRoute == item.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = t(item.label),
                                        )
                                    },
                                    label = { Text(t(item.label)) },
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = Screen.Lists.route,
                    modifier = Modifier.padding(innerPadding),
                ) {
                    composable(Screen.Lists.route) {
                        ListsScreen(
                            onNavigateToDetail = { listId ->
                                navController.navigate(Screen.ListDetail.createRoute(listId))
                            },
                        )
                    }

                    composable(
                        route = Screen.ListDetail.route,
                        arguments = listOf(navArgument("listId") { type = NavType.StringType }),
                    ) { backStackEntry ->
                        val listId = backStackEntry.arguments?.getString("listId") ?: return@composable
                        ListDetailScreen(
                            listId = listId,
                            onNavigateBack = { navController.popBackStack() },
                        )
                    }

                    composable(Screen.Households.route) {
                        HouseholdsScreen(
                            onNavigateToDetail = { householdId ->
                                navController.navigate(Screen.HouseholdDetail.createRoute(householdId))
                            },
                        )
                    }

                    composable(
                        route = Screen.HouseholdDetail.route,
                        arguments = listOf(navArgument("householdId") { type = NavType.StringType }),
                    ) { backStackEntry ->
                        val householdId = backStackEntry.arguments?.getString("householdId") ?: return@composable
                        HouseholdDetailScreen(
                            householdId = householdId,
                            onNavigateBack = { navController.popBackStack() },
                        )
                    }

                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            onLoggedOut = {
                                authViewModel.logout()
                            },
                        )
                    }

                    composable(
                        route = Screen.SharedListView.route,
                        arguments = listOf(navArgument("token") { type = NavType.StringType }),
                    ) {
                        // Placeholder for future shared list view via deep link
                        Text(t("shared.comingSoon"))
                    }
                }
            }
        }
        else -> {
            val authNavController = rememberNavController()
            NavHost(
                navController = authNavController,
                startDestination = Screen.Login.route,
            ) {
                composable(Screen.Login.route) {
                    LoginScreen(
                        onNavigateToRegister = {
                            authNavController.navigate(Screen.Register.route)
                        },
                        authViewModel = authViewModel,
                    )
                }
                composable(Screen.Register.route) {
                    RegisterScreen(
                        onNavigateToLogin = {
                            authNavController.popBackStack()
                        },
                        authViewModel = authViewModel,
                    )
                }
            }
        }
    }
}
