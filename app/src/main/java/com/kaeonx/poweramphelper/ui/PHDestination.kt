package com.kaeonx.poweramphelper.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.kaeonx.poweramphelper.R

internal abstract class PHDestinationBase(
    internal open val route: String,
    internal open val bottomBarRoute: String,  // Highlight this icon in the bottom bar
)

internal sealed class PHDestinationWithIcon(
    override val route: String,
    internal val bottomBarDisplayName: String,
    internal val icon: @Composable () -> ImageVector,
) : PHDestinationBase(route, route) {
    internal object Home : PHDestinationWithIcon(
        route = "home",
        bottomBarDisplayName = "Home",
        icon = { Icons.Default.Home }
    )

    internal object Language : PHDestinationWithIcon(
        route = "language",
        bottomBarDisplayName = "Language",
        icon = { ImageVector.vectorResource(R.drawable.baseline_language_24) }
    )

    internal object Rating : PHDestinationWithIcon(
        route = "rating",
        bottomBarDisplayName = "Rating",
        icon = { Icons.Default.Star }
    )

    internal companion object {
        internal val items
            get() = listOf(Home, Language, Rating)
    }
}

internal sealed class PHDestinationHidden(
    override val route: String,
    override val bottomBarRoute: String,  // Highlight this icon in the bottom bar
) : PHDestinationBase(route, bottomBarRoute) {
    internal object RatingFolder : PHDestinationHidden(
        route = "ratingFolder/{folderEncodedUri}",
        bottomBarRoute = PHDestinationWithIcon.Rating.route
    ) {
        internal fun resolveRoute(folderEncodedUri: String): String =
            "ratingFolder/$folderEncodedUri"
    }

    internal companion object {
        internal val items
            get() = listOf(RatingFolder)
    }
}