package com.kaeonx.poweramphelper.ui.screens.ratingFolder

import androidx.compose.runtime.Immutable
import com.kaeonx.poweramphelper.database.MusicFileUI

@Immutable
internal data class RatingFolderScreenState(
    internal val dirName: String,
    internal val musicFiles: List<MusicFileUI>
)