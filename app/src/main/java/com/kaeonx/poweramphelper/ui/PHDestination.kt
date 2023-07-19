package com.kaeonx.poweramphelper.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.kaeonx.poweramphelper.R

internal sealed class PHDestination(
    val route: String,
    val bottomBarDisplayName: String,
    val icon: @Composable () -> ImageVector,
) {
    internal object Home : PHDestination("home", "Home", { Icons.Default.Home })
    internal object Rating : PHDestination("rating", "Rating", { Icons.Default.Star })
    internal object Language : PHDestination(
        "language",
        "Language",
        { ImageVector.vectorResource(R.drawable.baseline_language_24) })
}

internal val bottomNavBarItems = listOf(
    PHDestination.Home, PHDestination.Language, PHDestination.Rating
)