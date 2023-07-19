package com.kaeonx.poweramphelper.ui.screens.home

import androidx.compose.runtime.Immutable

// Explanation courtesy of https://stackoverflow.com/a/74441615;
// used in Android documentations without explanation for UIState objects
@Immutable
data class HomeScreenState(
    val m3u8DirName: String?,
    val m3u8Count: Int,
    val musicDirName: String?
)