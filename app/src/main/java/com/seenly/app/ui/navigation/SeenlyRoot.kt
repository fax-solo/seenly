package com.seenly.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.seenly.app.AppContainer
import com.seenly.app.R
import com.seenly.app.ui.backup.BackupScreen
import com.seenly.app.ui.calendar.CalendarScreen
import com.seenly.app.ui.details.DetailsScreen
import com.seenly.app.ui.discover.DiscoverScreen
import com.seenly.app.ui.goals.GoalsScreen
import com.seenly.app.ui.home.HomeScreen
import com.seenly.app.ui.library.LibraryScreen
import com.seenly.app.ui.lists.ListDetailScreen
import com.seenly.app.ui.lists.ListsScreen
import com.seenly.app.ui.manual.ManualAddScreen
import com.seenly.app.ui.more.MoreScreen
import com.seenly.app.ui.recommendations.RecommendationsScreen
import com.seenly.app.ui.reviews.ReviewsScreen
import com.seenly.app.ui.settings.SettingsScreen
import com.seenly.app.ui.stats.StatsScreen

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val DISCOVER = "discover"
    const val STATS = "stats"
    const val MORE = "more"
    const val LISTS = "lists"
    const val LIST_DETAIL = "lists/{listId}"
    const val CALENDAR = "calendar"
    const val GOALS = "goals"
    const val REVIEWS = "reviews"
    const val BACKUP = "backup"
    const val SETTINGS = "settings"
    const val MANUAL_ADD = "manual"
    const val MEDIA_DETAIL = "media/{mediaId}"
    const val RECOMMENDATIONS = "recommendations"

    fun listDetail(listId: Long): String = "lists/$listId"
    fun mediaDetail(mediaId: Long): String = "media/$mediaId"
}

private data class BottomTab(
    val route: String,
    val icon: ImageVector,
    @param:StringRes val label: Int,
)

private val bottomTabs = listOf(
    BottomTab(Routes.HOME, Icons.Rounded.Home, R.string.nav_home),
    BottomTab(Routes.LIBRARY, Icons.Rounded.VideoLibrary, R.string.nav_library),
    BottomTab(Routes.DISCOVER, Icons.Rounded.Explore, R.string.nav_discover),
    BottomTab(Routes.STATS, Icons.Rounded.BarChart, R.string.nav_stats),
    BottomTab(Routes.MORE, Icons.Rounded.MoreHoriz, R.string.nav_more),
)

@Composable
fun SeenlyRoot(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomTabs.map { it.route }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navController.navigateToTab(tab.route) },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    container = container,
                    onOpenMedia = { id -> navController.navigate(Routes.mediaDetail(id)) },
                    onOpenDiscover = { navController.navigateToTab(Routes.DISCOVER) },
                    onOpenCalendar = { navController.navigate(Routes.CALENDAR) },
                    onOpenRecommendations = { navController.navigate(Routes.RECOMMENDATIONS) },
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    container = container,
                    onOpenMedia = { id -> navController.navigate(Routes.mediaDetail(id)) },
                    onAddManual = { navController.navigate(Routes.MANUAL_ADD) },
                    onDiscover = { navController.navigateToTab(Routes.DISCOVER) },
                )
            }
            composable(Routes.DISCOVER) {
                DiscoverScreen(
                    container = container,
                    onOpenTitle = { id -> navController.navigate(Routes.mediaDetail(id)) },
                )
            }
            composable(Routes.STATS) {
                StatsScreen(
                    container = container,
                    onOpenMedia = { id -> navController.navigate(Routes.mediaDetail(id)) },
                    onOpenGoals = { navController.navigate(Routes.GOALS) },
                )
            }
            composable(Routes.MORE) {
                MoreScreen(
                    onOpenLists = { navController.navigate(Routes.LISTS) },
                    onOpenCalendar = { navController.navigate(Routes.CALENDAR) },
                    onOpenGoals = { navController.navigate(Routes.GOALS) },
                    onOpenReviews = { navController.navigate(Routes.REVIEWS) },
                    onOpenBackup = { navController.navigate(Routes.BACKUP) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(
                route = Routes.MEDIA_DETAIL,
                arguments = listOf(navArgument("mediaId") { type = NavType.LongType }),
            ) { entry ->
                val mediaId = entry.arguments?.getLong("mediaId") ?: return@composable
                DetailsScreen(
                    container = container,
                    mediaId = mediaId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.LISTS) {
                ListsScreen(
                    container = container,
                    onOpenList = { id -> navController.navigate(Routes.listDetail(id)) },
                )
            }
            composable(
                route = Routes.LIST_DETAIL,
                arguments = listOf(navArgument("listId") { type = NavType.LongType }),
            ) { entry ->
                val listId = entry.arguments?.getLong("listId") ?: return@composable
                ListDetailScreen(
                    container = container,
                    listId = listId,
                    onBack = { navController.popBackStack() },
                    onOpenMedia = { id -> navController.navigate(Routes.mediaDetail(id)) },
                )
            }
            composable(Routes.CALENDAR) {
                CalendarScreen(
                    container = container,
                    onOpenMedia = { id -> navController.navigate(Routes.mediaDetail(id)) },
                )
            }
            composable(Routes.GOALS) {
                GoalsScreen(container = container)
            }
            composable(Routes.REVIEWS) {
                ReviewsScreen(
                    container = container,
                    onOpenMedia = { id -> navController.navigate(Routes.mediaDetail(id)) },
                )
            }
            composable(Routes.BACKUP) {
                BackupScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    container = container,
                    onOpenBackup = { navController.navigate(Routes.BACKUP) },
                )
            }
            composable(Routes.MANUAL_ADD) {
                ManualAddScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onSaved = { id ->
                        navController.navigate(Routes.mediaDetail(id)) {
                            popUpTo(Routes.MANUAL_ADD) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.RECOMMENDATIONS) {
                RecommendationsScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
