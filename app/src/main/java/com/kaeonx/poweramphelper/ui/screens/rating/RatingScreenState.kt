package com.kaeonx.poweramphelper.ui.screens.rating

import androidx.compose.runtime.Immutable
import com.kaeonx.poweramphelper.database.MusicFolderWithRatingStatsUI

// @Immutable: Explanation courtesy of https://stackoverflow.com/a/74441615;
// used in Android documentations without explanation for UIState objects
@Immutable
internal data class RatingScreenState(
    internal val musicFoldersWithStatistics: List<MusicFolderWithRatingStatsUI>,
    internal val sortOption: RatingScreenSortOption,
    internal val descendingSort: Boolean,
    internal val sortString: String
)