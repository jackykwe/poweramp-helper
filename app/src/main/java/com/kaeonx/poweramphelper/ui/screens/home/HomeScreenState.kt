package com.kaeonx.poweramphelper.ui.screens.home

import androidx.compose.runtime.Immutable

// Explanation courtesy of https://stackoverflow.com/a/74441615;
// used in Android documentations without explanation for UIState objects
@Immutable
internal data class HomeScreenState(
    internal val m3u8DirName: String?,
    internal val m3u8Count: Int,
    internal val musicDirName: String?,
    internal val lastAnalysisDateString: String?
)