package com.kaeonx.poweramphelper.ui.screens.languageFolder

import androidx.compose.runtime.Immutable
import com.kaeonx.poweramphelper.database.MusicFileUI

@Immutable
internal data class LanguageFolderScreenState(
    internal val dirName: String,
    internal val musicFiles: List<MusicFileUI>
)