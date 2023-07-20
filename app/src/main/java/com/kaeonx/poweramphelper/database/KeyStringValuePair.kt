package com.kaeonx.poweramphelper.database

import androidx.room.Entity
import androidx.room.PrimaryKey

// KSVP is KeyStringValuePair
internal const val M3U8_DIR_URI_KSVP_KEY = "m3u8DirUri"
internal const val MUSIC_DIR_URI_KSVP_KEY = "musicDirUri"
internal const val LAST_ANALYSIS_MILLIS_KSVP_KEY = "lastAnalysisTs"
internal const val LANG_SCR_SORT_OPTION_KSVP_KEY = "langScrSortOption"
internal const val LANG_SCR_PEND_FIRST_SORT_KSVP_KEY = "langScrPendFirstSort"
internal const val LANG_SCR_DESC_SORT_KSVP_KEY = "langScrDescSort"

@Entity
internal data class KeyStringValuePair(
    @PrimaryKey internal val key: String,
    internal val value: String
)