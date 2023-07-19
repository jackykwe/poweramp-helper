package com.kaeonx.poweramphelper.ui.screens.language

import androidx.compose.runtime.Immutable
import com.kaeonx.poweramphelper.database.MusicFolder

// Explanation courtesy of https://stackoverflow.com/a/74441615;
// used in Android documentations without explanation for UIState objects
@Immutable
internal data class LanguageScreenState(
    internal val musicFolders: List<MusicFolder>
)