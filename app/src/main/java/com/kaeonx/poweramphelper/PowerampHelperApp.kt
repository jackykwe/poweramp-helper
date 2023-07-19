package com.kaeonx.poweramphelper

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
internal fun PowerampHelperApp() {
    val navController = rememberNavController()
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
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
        }
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