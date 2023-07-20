package com.kaeonx.poweramphelper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kaeonx.poweramphelper.ui.PHDestination
import com.kaeonx.poweramphelper.ui.bottomNavBarItems
import com.kaeonx.poweramphelper.ui.screens.home.HomeScreen
import com.kaeonx.poweramphelper.ui.screens.language.LanguageScreen
import com.kaeonx.poweramphelper.ui.screens.rating.RatingScreen

// To pass the snackbarHostState into the hierarchy without manual "prop drilling"
internal val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided")
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
internal fun PowerampHelperApp() {
    val navController = rememberNavController()
    // From https://developer.android.com/jetpack/compose/navigation#bottom-nav
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // For animation
    val density = LocalDensity.current

    // For snackbars
    val snackbarHostState = remember { SnackbarHostState() }
    // To pass the snackbarHostState into the hierarchy without manual "prop drilling",
    // we use CompositionLocal. Initial hint from: https://stackoverflow.com/a/69905470
    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            topBar = {
                // Top App Bar hiding strategy courtesy of https://stackoverflow.com/a/71011124
                AnimatedVisibility(
                    visible = currentDestination?.route == PHDestination.Home.route,
                    enter = slideInVertically() + expandVertically() + fadeIn(),
                    exit = slideOutVertically() + shrinkVertically() + fadeOut()
                ) {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.app_name),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            },
            bottomBar = {
                NavigationBar {
                    bottomNavBarItems.forEach { phDestination ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = phDestination.icon(),
                                    contentDescription = null
                                )
                            },
                            label = { Text(text = phDestination.bottomBarDisplayName) },
                            selected = currentDestination?.hierarchy?.any { it.route == phDestination.route } == true,
                            onClick = {
                                navController.navigate(phDestination.route) {
                                    // Pop up to the start destination of the graph to avoid building up
                                    // a large stack of destinations on the back stack as users select items
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        // saveState = true
                                    }
                                    // Avoid multiple copies of the same destination when reselecting the same item
                                    launchSingleTop = true
                                    // Restore state when reselecting a previously selected item
                                    // restoreState = true
                                }
                            }
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            NavHost(
                modifier = Modifier.padding(innerPadding),
                navController = navController,
                startDestination = PHDestination.Home.route
            ) {
                composable(route = PHDestination.Home.route) { HomeScreen() }
                composable(route = PHDestination.Language.route) { LanguageScreen() }
                composable(route = PHDestination.Rating.route) { RatingScreen() }
            }
        }
    }
}